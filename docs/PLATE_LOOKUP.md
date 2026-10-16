# Plate Lookup & Reviews (Phase 2)

> **Gate:** this feature does not launch without written permission / an agreement with the right authorities. For tricycles, that's the **City Government of Puerto Princesa** (tricycle franchising office), since tricycles are LGU-regulated. For PUVs, it's **LTFRB**. We should also consult LTO about plate data.

## What it does

- A commuter enters a **plate number** (or, for tricycles, the city-issued **body / franchise number**, which is usually easier to see) and sees:
  - Whether the vehicle is registered with a valid franchise (only if the authority gives us that data).
  - An average rating and recent reviews from other commuters.
- A signed-in commuter can leave **one review per vehicle per trip/day**: a star rating, preset tags ("correct fare", "overcharged", "safe driving", "rude", …), and an optional short comment.
- Later: **scan a QR code or NFC tag** on the vehicle to open its page directly. This avoids typos and makes fake plates harder.

## Connectivity

The rest of BeFare is offline, but this feature needs the network to sync:
- **Lookups**: online first. Results are cached on the device so plates you've looked up before still show offline (marked with when they were last updated).
- **Reviews**: can be written offline. They're queued in the local database and sent when the phone is back online.

## Backend

See [ADR 0003](adr/0003-supabase-backend.md).

```
 App (shared/ SQLDelight cache + outbox)
        │ HTTPS, Supabase Auth session (JWT)
        ▼
 Supabase
   ├─ Auth            phone OTP / social sign-in
   ├─ Postgres + RLS  vehicles, reviews, profiles, reports, moderation_actions
   ├─ RPC functions   submit_review, search_vehicle (validation + rate limits)
   ├─ Edge Functions  QR token signing, partner data import, moderator alerts
   └─ Storage         (later) QR assets, evidence attachments
        ▲
 Web portal (Next.js + mapcn): moderators & authority partners, role checked by RLS
```

The apps only ever hold the **anon key** plus the user's session. The **service-role key** is used only in Edge Functions and CI. The app writes reviews through the `submit_review` RPC, not by inserting into tables directly, so validation and rate limits can't be skipped.

### Tables (first draft)

```sql
create extension if not exists pg_trgm;

create table vehicles (
  id               uuid primary key default gen_random_uuid(),
  plate_normalized text unique,
  body_number      text,                       -- city-issued tricycle body / franchise no.
  mode             text not null check (mode in ('tricycle','jeepney','uv','taxi')),
  franchise_status text,                       -- only if provided by the authority
  franchise_source text,
  updated_at       timestamptz not null default now()
);
create index vehicles_plate_trgm on vehicles using gin (plate_normalized gin_trgm_ops);

create table profiles (                        -- 1:1 with auth.users
  id          uuid primary key references auth.users on delete cascade,
  role        text not null default 'commuter' check (role in ('commuter','moderator','partner')),
  trust_level int  not null default 0,
  banned_at   timestamptz,
  created_at  timestamptz not null default now()
);

create table reviews (
  id         uuid primary key default gen_random_uuid(),
  vehicle_id uuid not null references vehicles,
  user_id    uuid not null references profiles,
  rating     smallint not null check (rating between 1 and 5),
  tags       text[] not null default '{}',
  comment    text check (char_length(comment) <= 500),
  trip_date  date not null,
  status     text not null default 'pending'
             check (status in ('pending','published','hidden','removed')),
  created_at timestamptz not null default now(),
  unique (vehicle_id, user_id, trip_date)      -- one review per vehicle per user per day
);

create table reports (
  id uuid primary key default gen_random_uuid(),
  review_id uuid not null references reviews, reporter_id uuid not null references profiles,
  reason text not null, created_at timestamptz not null default now(), resolved_at timestamptz
);

create table moderation_actions (
  id uuid primary key default gen_random_uuid(),
  review_id uuid not null references reviews, moderator_id uuid not null references profiles,
  action text not null, reason text, created_at timestamptz not null default now()
);
```

### Row-level security (outline)

| Table | Anonymous | Commuter | Moderator / partner |
|---|---|---|---|
| `vehicles` | read | read | read; partner can update franchise fields |
| `reviews` | read `published` | read `published` + own; write only via `submit_review` | read all; change status via `moderate_review` |
| `reports` | — | insert own | read / resolve |
| `moderation_actions` | — | — | insert / read |
| `profiles` | — | read / update own (not `role`) | read |

Policies are tested with pgTAP (or SQL tests run by the Supabase CLI) in CI.

Plates are normalized before storing and searching: upper case, no spaces or dashes. Look-alike characters (O/0, I/1) are handled at search time only, using `pg_trgm` similarity plus a normalized "confusable" form.

## Trust, safety and legal

- **Data Privacy Act (RA 10173)**: reviews are about a **vehicle and its service**, not the driver as a person. Don't collect or show driver names or photos unless the authority agreement allows it. Prepare a privacy notice and a Privacy Impact Assessment, and register with the NPC if required.
- **Defamation risk**: preset tags first; free-text comments are moderated (filter → queue → human review). Drivers and operators can respond to or dispute reviews through a channel the authority approves.
- **Abuse**: rate limits per user and per device, a minimum account age before reviews are published, detection of review bursts (many reviews of one plate in a short time), and a "report" button.
- **Accounts**: phone number (OTP) or social sign-in. Store as little as possible.
- **Escalation**: link to the authority's official complaint channel. BeFare reviews are not official complaints, and the app should say so.

## QR / NFC (later)

- The tag holds a URL like `https://befare.app/v/<id>?sig=<signature>`, signed by our backend, so copies of a QR made for a different vehicle don't verify.
- Ideally the authority issues or approves tags as part of franchise renewal.
- NFC: NDEF URL record, read with the platform's NFC APIs (Android: NfcAdapter; iOS: Core NFC).
