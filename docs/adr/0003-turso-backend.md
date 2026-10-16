# ADR 0003: Turso (libSQL) for the phase 2 backend

- **Status:** Accepted (for phase 2)
- **Date:** 2026-10-16

## Context

Plate lookup and reviews (phase 2) need a hosted database that syncs with an offline-first app. Options considered: Supabase (Postgres + auth + row-level security) and Turso (libSQL, a SQLite fork, with edge replicas and embedded replicas).

## Decision

Use **Turso** as the database, behind a **small API service** that handles auth, validation, rate limiting and moderation.

## Why

- The device already uses SQLite (SQLDelight in `shared/`). Using SQLite on the server too keeps schemas and queries alike.
- Embedded replicas / offline sync suit read-mostly public data (for example, a cache of vehicle summaries).
- Low cost and simple to run at this scale.

## Consequences

- ⚠️ Turso doesn't come with end-user auth or row-level security like Supabase does. We need:
  - an auth provider (phone OTP or social sign-in), and
  - an API layer that holds the database credentials. **Clients never get a Turso write token.**
- ⚠️ Moderation, rate limiting and audit logs are ours to build in the API.
- Open choice: where the API runs (Ktor on a small server, so it can share Kotlin models with `shared/`, or an edge runtime). Decide when phase 2 starts.
