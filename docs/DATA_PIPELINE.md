# Map & Routing Data Pipeline

Everything here runs at **build time**. The phone only receives the outputs.

## Inputs

| Input | Source | Notes |
|---|---|---|
| OSM extract | Geofabrik Philippines `.osm.pbf` | Record the download date; it goes into the manifest |
| Coverage polygon | `data/coverage/coverage.geojson` | Hand-maintained; see [ADR 0004](adr/0004-urban-coverage.md) |
| Road access rules | `data/routing/tricycle-profile.yaml` | Which roads tricycles can use |
| Curated landmarks | `data/places/landmarks.csv` | Local names OSM may lack |

## Outputs

| Output | Approx. size target | Used by |
|---|---|---|
| `befare.pmtiles` | ≤ 15 MB | MapLibre (Android, iOS, web) |
| `graph.bin` | ≤ 3 MB | `shared/` routing |
| `places.bin` (or SQLite FTS) | ≤ 1 MB | offline search |
| `mask.geojson` | small | coverage fade layer |
| `manifest.json` | — | versions + checksums |

## Steps

### 1. Clip

Clip the Philippines extract to the coverage polygon **plus a buffer** (about 1 km). The buffer means roads that leave and re-enter the area aren't cut, and the map edge doesn't look chopped off under the fade.

Tool: `osmium extract -p coverage-buffered.geojson`.

### 2. Vector tiles

- Build with **Planetiler** (Java, fast, writes PMTiles/MBTiles directly), using a cut-down OpenMapTiles-style schema: roads, buildings, water, landuse, place labels, POIs.
- Zoom range: about z10–z16. MapLibre overzooms past z16, so we don't need z17+ tiles.
- Drop layers and attributes we don't draw, to keep the file small.

### 3. Routing graph

1. Filter OSM ways using the tricycle profile:
   - Include: `highway=primary|secondary|tertiary|unclassified|residential|service|living_street|*_link|trunk` (trunk only if tricycles are allowed on it; check local rules).
   - Exclude: `access=no|private`, `motor_vehicle=no`, `highway=footway|path|steps|cycleway|track` (track is configurable).
   - Respect `oneway=yes|-1` and `junction=roundabout`.
   - Turn restrictions (`type=restriction`): parse them; using them in routing is a v1.1 item.
2. Split ways at junctions → nodes and edges. Remove degree-2 nodes, but keep their shape points for drawing the route.
3. Keep only the largest strongly connected component. Log the rest so map errors can be fixed upstream in OSM.
4. Clip edges to the coverage polygon (with buffer).
5. Write `graph.bin`.

### `graph.bin` format (v1)

Little-endian, read with memory mapping or a single buffer read.

```
Header      magic "BFGR", formatVersion u16, nodeCount u32, edgeCount u32, bbox (4 × i32 E7)
Nodes       lat i32 E7, lon i32 E7                                  × nodeCount
Offsets     firstEdge u32                                           × (nodeCount + 1)   // CSR
Edges       target u32, lengthCm u32, roadClass u8, flags u8,
            geomOffset u32, geomCount u16                           × edgeCount
Geometry    packed delta-encoded shape points (i32 E7)
Reverse     CSR of incoming edges (for the backward half of bidirectional search)
SpatialIdx  uniform grid: cell → list of edge ids
Footer      sha256 of everything above
```

The reader and writer both live in `shared/graph` and share the format constants.

### 4. Places / search

Collect named nodes and ways (amenity, shop, tourism, place, school, church, barangay hall, …) inside coverage, plus `landmarks.csv`. Output a small prefix index or an SQLite FTS table. Also store barangay names so users can search "Sta. Lourdes" and so on.

### 5. Mask

`mask.geojson` = world rectangle with `coverage.geojson` cut out as a hole. Styled as a semi-opaque fill above the base layers and below the labels and pins.

### 6. Manifest

Write versions, OSM date and sha256 for each output. App startup checks the checksums in debug builds.

## Updating

- Rebuild every quarter, or when big road changes appear in OSM.
- Fixing OSM itself (missing roads, wrong one-ways) helps everyone. Local mapping fixes should go upstream to OSM, not into a private patch.

## Licensing

OSM data is **ODbL**. The app must show "© OpenStreetMap contributors" on the map and in About. Tiles and the graph are derived databases; if we distribute them, keep a note on how they were made.
