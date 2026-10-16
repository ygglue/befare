# Architecture

## Overview

```
                ┌──────────────────────── build time (CI / dev machine) ────────────────────────┐
 OSM extract ──►│ pipeline/  clip to coverage → vector tiles (.pmtiles) + routing graph (.bin)  │
 Fare matrix ──►│            transcribed fare rules (fares.json) + source images                 │
                └──────────────────────────────────────┬─────────────────────────────────────────┘
                                                       │ bundled assets (versioned)
                ┌──────────────────────────────────────▼─────────────────────────────────────────┐
                │ shared/  (Kotlin Multiplatform, commonMain)                                     │
                │   geo      — coordinates, haversine, coverage polygon, point-in-polygon        │
                │   graph    — graph.bin reader, spatial index, snap-to-road                     │
                │   routing  — bidirectional A*                                                  │
                │   fare     — rule model, calculator, breakdown + provenance                    │
                │   data     — asset loading, versions, (phase 2) updates & local DB             │
                │   app      — EstimateService: pins in → route + fare + breakdown out           │
                └───────────────┬───────────────────────────────────────────┬────────────────────┘
                                │                                           │
               ┌────────────────▼──────────────┐            ┌───────────────▼───────────────┐
               │ androidApp/  Jetpack Compose  │            │ iosApp/  SwiftUI              │
               │ MapLibre Native Android       │            │ MapLibre Native iOS           │
               └───────────────────────────────┘            └───────────────────────────────┘

 Phase 2:  backend/ (API in front of Turso)   web/ (Next.js + mapcn admin & moderation portal)
```

## Principles

1. **Offline is the default, not a fallback.** Everything in v1 runs from bundled files. The network is only for optional updates (phase 2).
2. **One source of truth for fare logic.** Routing and fare calculation live only in `shared/`. Platform code never does fare math.
3. **Data, not code, for fares.** Fare rules are versioned data with provenance attached. Changing a fare should not need a new app release (phase 2 updates), and must never need a code change.
4. **Lean data.** Coverage is limited to urban Puerto Princesa, so the tiles, graph and search index stay small.
5. **Deterministic and testable.** Same inputs and same data version give the same fare. A golden test set of trips is checked in CI.

## Repository layout (planned)

```
befare/
  shared/            KMP module: geo, graph, routing, fare, data, app
  androidApp/        Compose UI, MapLibre Native Android
  iosApp/            SwiftUI UI, MapLibre Native iOS (consumes shared as XCFramework)
  pipeline/          Kotlin/JVM CLI that builds tiles, graph and fare bundle
  data/
    coverage/        coverage polygon (GeoJSON) — the source of truth for "inside the city"
    fares/           fares.json + source images/PDFs
    styles/          MapLibre style JSON (shared across Android, iOS, web)
  docs/
  backend/           phase 2
  web/               phase 2 (mapcn)
```

The pipeline is a Kotlin/JVM app so it can reuse `shared/`'s graph format code. That way the writer and the reader of `graph.bin` can't drift apart.

## Map rendering

- **MapLibre Native** on both platforms, reading a bundled **PMTiles** file (fallback: MBTiles if PMTiles support in MapLibre Native turns out to be unreliable; to be confirmed in the first spike).
- One **style JSON** in `data/styles/`, used on Android, iOS and the web portal. Glyphs (fonts) and sprites are bundled too. Nothing loads from the internet.
- **Coverage fade**: a GeoJSON layer with a world-sized polygon that has the coverage polygon cut out as a hole, filled at about 60–70% opacity. Camera bounds are limited to the coverage box plus a small margin.

## Routing

- The graph is built offline from OSM roads that tricycles may use (see [DATA_PIPELINE.md](DATA_PIPELINE.md)).
- **Snap**: each pin is projected onto the nearest usable road segment, using a grid/R-tree index stored in `graph.bin`. Both endpoints become virtual nodes partway along their segments.
- **Search**: bidirectional A* with a haversine heuristic, using edge length as the cost. The urban graph is small (tens of thousands of edges), so this should take a few milliseconds. Contraction Hierarchies are an option if profiling ever says we need them.
- **Output**: polyline, total distance, and the list of edges. Fares are based on **distance**, not time.

## Fare engine

See [FARE_ENGINE.md](FARE_ENGINE.md). In short: the engine takes `(distance, mode, passenger type, time)` plus the fare dataset and returns the fare **and** a breakdown, where every line links to a `Source`.

## Shared ↔ platform boundary

The UI calls one main entry point:

```kotlin
class EstimateService {
    fun estimate(start: LatLng, end: LatLng, options: EstimateOptions): EstimateResult
}

sealed interface EstimateResult {
    data class Ok(val route: Route, val fare: FareQuote) : EstimateResult
    data class OutsideCoverage(val which: Endpoint) : EstimateResult
    data class NoRoute(val reason: String) : EstimateResult
}
```

`FareQuote` carries the amounts, the breakdown lines and the sources, so the Verify screen is just a view of that data.

iOS uses `shared` as an XCFramework (SKIE recommended so Swift gets nicer types: sealed classes, suspend functions, flows).

## Data versioning

Each bundled asset has a manifest entry:

```json
{ "tiles":  { "version": "2026.10.0", "osmDate": "2026-10-01", "sha256": "…" },
  "graph":  { "version": "2026.10.0", "osmDate": "2026-10-01", "sha256": "…" },
  "fares":  { "version": "2026.10.0", "effective": "…",        "sha256": "…" } }
```

The fare version is shown on every estimate and in Verify. In phase 2, the app can download a newer **signed** fare bundle and use it if the signature checks out.

## Phase 2 backend

See [PLATE_LOOKUP.md](PLATE_LOOKUP.md) and [ADR 0003](adr/0003-turso-backend.md). Short version: a small API (auth, rate limiting, moderation) in front of **Turso**. On the device, a local SQLite database (SQLDelight in `shared/`) caches lookups and holds reviews waiting to be sent.

## Testing

- `shared/` unit tests: geo, snapping, routing on small hand-made graphs, fare rules.
- **Golden trips**: a checked-in list of `(start, end) → expected distance range, expected fare`. Each fare is checked by hand against the matrix. CI fails if the result changes without a deliberate data-version bump.
- Pipeline tests: the graph is connected inside the coverage area (one main component), and there are no edges outside the coverage area.
