# ADR 0003: Supabase (Postgres) for the phase 2 backend

- **Status:** Accepted (for phase 2)
- **Date:** 2026-10-16
- **Replaces:** an earlier draft of this ADR that chose Turso (libSQL); never implemented

## Context

Plate lookup and reviews (phase 2) need a hosted backend for an offline-first app. The data is relational: users, vehicles, reviews, reports and moderation actions, all linked. It needs fuzzy plate search, abuse detection, end-user sign-in and per-user access rules.

Options considered: **Supabase (Postgres)** and **Turso (libSQL)**.

## Decision

Use **Supabase**: Postgres, Supabase Auth, row-level security (RLS), Storage and Edge Functions. Host in the region closest to the Philippines (Singapore).

## Why

- **Relational integrity and auditing.** Foreign keys, constraints and triggers suit reviews → reports → moderation actions.
- **Fuzzy plate search.** `pg_trgm` handles typos and look-alike characters (O/0, I/1) without custom code.
- **Abuse and moderation queries.** Window functions and SQL views can catch review bursts per plate, new-account limits, and so on.
- **Auth + RLS built in.** Phone OTP sign-in, and policies such as "users can only edit their own pending reviews" are enforced in the database. Much less server code to write and secure.
- **Partner-friendly.** Postgres is a widely known standard for government data handling, exports and audits.
- Turso's main advantage, embedded replicas that sync the database to devices, doesn't fit: phones should only cache plates they've looked up and queue their own reviews. SQLDelight on the device does that with any backend.

## How it fits together

- **Device:** SQLDelight (SQLite) in `shared/` for the lookup cache and the review outbox. It talks to Supabase through the REST/RPC API using the user's session token. Use supabase-kt in `shared/` if it works well on both targets; otherwise use plain Ktor calls to the REST endpoints.
- **Logic:** Postgres functions/triggers for validation, rate limits and moderation state. Edge Functions for things that need secrets or outside calls (signing QR tokens, notifying moderators, partner data imports).
- **Web portal:** Next.js + mapcn, signed in through Supabase Auth with a moderator/partner role checked by RLS.
- **Schema changes:** Supabase CLI migrations checked into `backend/supabase/migrations/`, with local development on the Supabase local stack.

## Consequences

- ✅ Much less custom backend code than a bespoke API.
- ✅ Security rules live next to the data (RLS) and can be tested.
- ⚠️ Every table needs RLS turned on with explicit policies. Policies get tested in CI, including "an anonymous user cannot write" and "a user cannot see others' pending reviews".
- ⚠️ The service-role key is used only in Edge Functions and CI, never in the apps or the web client.
- ⚠️ Some dependence on Supabase. Underneath it's standard Postgres, so we can move to self-hosted Supabase or plain Postgres later if needed.
