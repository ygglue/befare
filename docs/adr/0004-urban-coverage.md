# ADR 0004: Coverage limited to urban Puerto Princesa (city proper → Sta. Lourdes)

- **Status:** Accepted (exact polygon to be drawn)
- **Date:** 2026-10-16

## Context

Puerto Princesa City is very large (about 2,380 km², up to Sabang). Most tricycle trips happen in the urban area. A smaller area means smaller tiles, a smaller graph and faster routing.

## Decision

v1 covers the **urban area from the city proper north to Sta. Lourdes**. The boundary is a hand-drawn polygon in `data/coverage/coverage.geojson`, ideally following barangay borders so it's easy to explain to users ("BeFare covers these barangays").

## To do

- Agree on the list of barangays included (city proper barangays, plus those along the north highway up to and including Sta. Lourdes; also check the airport and the south side).
- Get barangay boundaries (OSM `admin_level=10` if good enough, otherwise PSA/NAMRIA data).
- Pins outside the polygon are rejected. The map outside is faded.

## Consequences

- ✅ Lean data and fast routing.
- ⚠️ Trips to the outer barangays aren't supported in v1. Grow the polygon later; the pipeline needs no changes for that.
