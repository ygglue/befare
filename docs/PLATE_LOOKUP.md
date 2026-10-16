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

See [ADR 0003](adr/0003-turso-backend.md).

```
 App (shared/ SQLDelight cache + outbox)
        │ HTTPS, JWT
        ▼
 API (auth, validation, rate limits, moderation hooks)
        │ libSQL
        ▼
 Turso   — vehicles, reviews, users, reports, moderation_actions
        ▲
 Web portal (Next.js + mapcn) — moderators & authority partners
```

The app **never** holds a Turso database token. All writes go through the API.

### Tables (first draft)

```sql
vehicles(id, plate_normalized, body_number, mode, franchise_status, franchise_source, updated_at)
reviews(id, vehicle_id, user_id, rating, tags_json, comment, trip_date, status, created_at)
  -- status: pending | published | hidden | removed
users(id, auth_provider_id, created_at, trust_level, banned_at)
reports(id, review_id, reporter_id, reason, created_at, resolved_at)
moderation_actions(id, review_id, moderator_id, action, reason, created_at)
```

Plates are normalized before storing and searching: upper case, no spaces or dashes, and common character mix-ups (O/0, I/1) handled at search time only.

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
