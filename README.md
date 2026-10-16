# BeFare

Offline-first fare estimator for Puerto Princesa City. Drop a pin for where you are, drop a pin for where you're going, and BeFare routes you over the city's roads and calculates the official fare. No internet needed.

Every estimate can be **verified**: one tap shows the exact fare rules, the distance used, and the source document (ordinance or LTFRB circular) behind each number.

## Status

MVP: an iOS simulator build with on-device routing and Ordinance 1185 tricycle fares. See [docs/MVP.md](docs/MVP.md) for how to build it and how it differs from the plan.

## Scope at a glance

| | v1 (offline) | Phase 2 (online) |
|---|---|---|
| Map | Offline vector map of urban Puerto Princesa (to Sta. Lourdes); fades outside coverage | — |
| Routing | On-device road routing between two pins | — |
| Fares | Tricycle fares from the city fare matrix, with provenance | More modes (jeepney, taxi, van) |
| Verify | Step-by-step breakdown + source documents | — |
| Plate lookup | — | Look up a plate, read/leave reviews (with LGU/LTFRB permission) |
| Scan | — | QR / NFC on vehicles |

## Stack

- **Kotlin Multiplatform** shared core (routing, fare engine, data), native UIs: **Jetpack Compose** (Android), **SwiftUI** (iOS)
- **MapLibre Native** with bundled offline vector tiles (PMTiles/MBTiles) built from OpenStreetMap
- **Supabase (Postgres)** backend for phase 2 plate lookup
- **mapcn** (React + MapLibre GL JS) for the web admin/moderation portal

## Docs

- [MVP: build, run, differences from the plan](docs/MVP.md)
- [Product requirements](docs/PRD.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Map & routing data pipeline](docs/DATA_PIPELINE.md)
- [Fare engine & provenance](docs/FARE_ENGINE.md)
- [Plate lookup (phase 2)](docs/PLATE_LOOKUP.md)
- [Architecture decision records](docs/adr/)
- [Fare sources](docs/sources/)
