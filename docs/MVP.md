# MVP (iOS and Android)

A first runnable version: just enough to see the idea working.

## What it does

- **Fully offline map** of Puerto Princesa: vector tiles (2.7 MB), style, fonts and icons all ship with the app. No network needed.
- **Dark mode** map (Protomaps "dark" flavor) and dark UI.
- Outside the coverage area the map fades to nothing: a smooth curve from clear to solid near-black over about 1.8 km. The outline is a smooth curve with no corners, and a pin is accepted exactly where the map is clear. The camera stays centred over the city.
- Zoom in / zoom out buttons.
- **GPS start, offline:** on launch the app asks for location permission (when in use). The first GPS position inside the coverage area becomes the start, unless you've already placed one. Fixes from outside the area show one note and don't end the wait. A blue dot shows where you are, and a **My location** button centres on you (and sets the start if none is set). GPS needs no internet. Android uses the platform `LocationManager`, with no Google Play services.
- Tap to place the **start** (green) and **destination** (red). The chips at the top choose which pin the next tap places. You can swap or clear the pins.
- A road route drawn between the pins, using **on-device routing** (A* in the Kotlin shared core).
- Tricycle fare from **Ordinance 1185**: regular and 20% discounted fare. You can change the gas-price band.
- A **Verify** sheet with the step-by-step breakdown, the matrix row across every band, the source document and photo (zoomable), and the assumptions.

## How it differs from the plan

| Area | Plan | MVP | Why |
|---|---|---|---|
| Base map tiles | Bundled PMTiles built with Planetiler | Bundled PMTiles **cut from a Protomaps daily build** (`pmtiles extract`), styled with `@protomaps/basemaps` | Same outcome (offline, about 3 MB) without running Planetiler on the whole Philippines. Switch when we need custom layers. |
| Coverage polygon | Union of barangay boundaries | Hand-drawn polygon | Accepted as good enough for v1 (2026-10-16) |
| Road data | Geofabrik extract → Kotlin pipeline | Overpass download → `pipeline/build_graph.py` | Faster to get going. Same graph format idea. |
| Graph | Degree-2 contraction, turn restrictions, built-in spatial index | Every OSM node kept, no turn restrictions, spatial grid built when the app loads | The graph is small (~21k nodes, ~700 KB) |
| Gas band | Default band shipped in the data | Defaults to "Regular fare" with a warning | `currentBand` is still unset |

## Offline map pieces

| File (in `data/build/`) | Made by | What it is |
|---|---|---|
| `befare.pmtiles` | `pmtiles extract` (in `scripts/build_mvp.sh`) | Vector tiles z0–15 for `118.55,9.55,118.92,10.03` |
| `style/befare-style.json`, `style/fonts/`, `style/sprites/` | `pipeline/style/build_style.mjs` | Protomaps "dark" style with `{TILES}`/`{ASSETS}` placeholders, Noto Sans glyphs (Latin + ₱), sprites |
| `coverage-smooth.geojson` | `pipeline/build_mask.py` | The hand-drawn coverage padded by 350 m and smoothed with Chaikin corner cutting. The script checks key barangays are still inside. The app uses it for the coverage check. |
| `mask.geojson` | `pipeline/build_mask.py` | 32 stacked masks around the smoothed outline, each grown about 56 m more than the last. Each has its own opacity so together they follow a smoothstep curve to solid `#0e0f12`. |

The app fills in the style placeholders with file URLs from its bundle when it starts (`OfflineStyle` in `MapView.swift`).

## Android

`androidApp/` is a Jetpack Compose app using MapLibre Android 13.7. It has the same features as iOS: offline dark map and fade, person and flag pins, zoom buttons, fare card, gas-band picker and Verify sheet. It uses the same shared Kotlin core and the same offline data. Gradle's `prepareOfflineAssets` task copies `data/build/` (plus the fares and the matrix photo) into the APK's assets. On first launch the app copies them to app storage, because MapLibre and the core read ordinary files (`OfflineData.kt`).

The SDK location goes in `local.properties` (`sdk.dir=…`, not committed). Tested on the `Medium_Phone_API_37.0` emulator.

```bash
./gradlew :androidApp:installDebug
```

## Testing GPS on the emulators

```bash
xcrun simctl location booted set 9.7943,118.7487
```

```bash
adb emu geo fix 118.7487 9.7943
```

Note that `adb emu geo fix` takes **longitude first**. Positions to try: San Jose (inside the area) `9.7943,118.7487`, Tiniguiban (inside) `9.7712,118.7357`, Sabang (outside) `10.197,118.89`. The Android emulator only updates its cached GPS position while an app is listening, so a fix you send while BeFare is closed shows up only after the next launch.

## Build & run

Requirements: Xcode, Homebrew `openjdk@21`, `xcodegen` and `pmtiles`, Node, Python 3. Building needs internet (OSM roads, tiles, fonts). The app doesn't.

```bash
scripts/build_mvp.sh
```

```bash
open iosApp/BeFare.xcodeproj
```

Then run the **BeFare** scheme on an iPhone simulator. For Android, install the APK with the command above.

## Tests

```bash
./gradlew :shared:jvmTest
```

`FareCalculatorTest` covers the fare table math. `EstimateServiceTest` routes over the real Puerto Princesa graph.
