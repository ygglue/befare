# BeFare

Offline-first fare estimator for Puerto Princesa City. Drop a pin for where you are, drop a pin for where you're going, and BeFare routes you over the city's roads and calculates the official fare. No internet needed.

Every estimate can be **verified**: one tap shows the exact fare rules, the distance used, and the source document (ordinance or LTFRB circular) behind each number.

## Status

MVP running on iOS and Android, fully offline:
- dark vector map of urban Puerto Princesa that fades out past the coverage area
- GPS start point
- on-device road routing
- Ordinance 1185 tricycle fares with a Verify screen

See [docs/MVP.md](docs/MVP.md) for what's in it and how it differs from the plan.

## Setup

### 1. Install the tools (macOS)

| Tool | For | Install |
|---|---|---|
| Xcode 16+ (tested on 27) and an iOS simulator | iOS app | App Store |
| Android Studio, Android SDK (API 37) and an emulator | Android app, **and** the shared Kotlin module (it has an Android target) | [developer.android.com/studio](https://developer.android.com/studio) |
| JDK 21 | Gradle / Kotlin | `brew install openjdk@21` |
| XcodeGen | generates `iosApp/BeFare.xcodeproj` | `brew install xcodegen` |
| pmtiles | cuts the offline map tiles | `brew install pmtiles` |
| Node 20+ | builds the map style | `brew install node` (or nvm) |
| Python 3 | road graph, fare bundle, fade mask | comes with macOS / Xcode |

### 2. Point Gradle at your Android SDK

Create `local.properties` in the repo root. It isn't committed:

```properties
sdk.dir=/Users/<you>/Library/Android/sdk
```

Use your own path if the SDK lives somewhere else, such as an external drive.

### 3. Build everything

The first run needs internet. It downloads the roads (Overpass), the map tiles (Protomaps), fonts and Gradle dependencies, and takes a few minutes. After that, the apps themselves need no network.

```bash
scripts/build_mvp.sh
```

The script:
1. builds the offline data into `data/build/` (tiles, style, road graph, fade mask)
2. builds the fare bundle
3. runs the core tests
4. builds the iOS framework and Xcode project
5. builds the Android APK

### 4. Run

**iOS:** open `iosApp/BeFare.xcodeproj` and run the **BeFare** scheme on an iPhone simulator.

**Android:** start an emulator (or plug in a phone with USB debugging), then:

```bash
./gradlew :androidApp:installDebug
```

**Core tests** (routing and fares, on the JVM):

```bash
./gradlew :shared:jvmTest
```

**Fake GPS position** (San Jose, inside the area). Note `adb` takes longitude first:

```bash
xcrun simctl location booted set 9.7943,118.7487
```

```bash
adb emu geo fix 118.7487 9.7943
```

### Rebuilding after changes

| You changed | Run |
|---|---|
| Kotlin in `shared/` (iOS) | `./gradlew :shared:assembleBeFareCoreDebugXCFramework`, then build in Xcode |
| Kotlin in `shared/` or `androidApp/` (Android) | `./gradlew :androidApp:installDebug` |
| `iosApp/project.yml`, or added/removed Swift files | `cd iosApp && xcodegen generate` |
| Fare transcription (`docs/sources/*.md`) | `python3 scripts/build_fares.py` |
| Coverage polygon, fade settings | `.venv/bin/python pipeline/build_mask.py` |
| Map style / label colours | `cd pipeline/style && node build_style.mjs` |
| Fresh roads or tiles | delete `data/osm/` or `data/build/befare.pmtiles`, then `scripts/build_mvp.sh` |

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

- [AGENTS.md](AGENTS.md): orientation for AI agents and new contributors
- [MVP: build, run, differences from the plan](docs/MVP.md)
- [Product requirements](docs/PRD.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Map & routing data pipeline](docs/DATA_PIPELINE.md)
- [Fare engine & provenance](docs/FARE_ENGINE.md)
- [Plate lookup (phase 2)](docs/PLATE_LOOKUP.md)
- [Architecture decision records](docs/adr/)
- [Fare sources](docs/sources/)
