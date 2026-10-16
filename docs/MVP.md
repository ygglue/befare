# MVP (iOS simulator)

A first runnable version: just enough to see the idea working.

## What it does

- Map of Puerto Princesa with everything outside the coverage area faded, and the camera kept near the city.
- Tap to place the **start** (green) and **destination** (red). The chips at the top choose which pin the next tap places. You can swap or clear the pins.
- A road route drawn between the pins, using **on-device routing** (A* in the Kotlin shared core).
- Tricycle fare from **Ordinance 1185**: regular and 20% discounted fare. You can change the gas-price band.
- A **Verify** sheet with the step-by-step breakdown, the matrix row across every band, the source document and photo (zoomable), and the assumptions.

## How it differs from the plan

| Area | Plan | MVP | Why |
|---|---|---|---|
| Base map tiles | Bundled offline PMTiles | **Online** OpenFreeMap tiles | Building tiles needs the full pipeline. Routing and fares already work offline. |
| Coverage polygon | Union of barangay boundaries | Hand-drawn sketch | Placeholder until the barangay list is agreed (ADR 0004) |
| Road data | Geofabrik extract → Kotlin pipeline | Overpass download → `pipeline/build_graph.py` | Faster to get going. Same graph format idea. |
| Graph | Degree-2 contraction, turn restrictions, built-in spatial index | Every OSM node kept, no turn restrictions, spatial grid built when the app loads | The graph is small (~21k nodes, ~700 KB) |
| Android | Compose + MapLibre | Not yet | No Android SDK on the dev machine |
| Gas band | Default band shipped in the data | Defaults to "Regular fare" with a warning | `currentBand` is still unset |

## Build & run

Requirements: Xcode, Homebrew `openjdk@21` and `xcodegen`, Python 3.

```bash
scripts/build_mvp.sh
```

```bash
open iosApp/BeFare.xcodeproj
```

Then run the **BeFare** scheme on an iPhone simulator.

## Tests

```bash
./gradlew :shared:jvmTest
```

`FareCalculatorTest` covers the fare table math. `EstimateServiceTest` routes over the real Puerto Princesa graph.
