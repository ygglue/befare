# ADR 0002: MapLibre with bundled OSM vector tiles

- **Status:** Accepted (PMTiles vs MBTiles to be confirmed in a spike)
- **Date:** 2026-10-16

## Context

The map must work fully offline, look the same on Android, iOS and the web portal, and stay small. We want to use mapcn (React + MapLibre GL JS) for the web portal.

## Decision

- **MapLibre** everywhere: MapLibre Native on Android and iOS, MapLibre GL JS (through mapcn) on the web.
- Tiles are built from **OpenStreetMap** with **Planetiler**, clipped to the coverage area, and bundled with the app as a single file.
- One shared **style JSON**, with glyphs and sprites bundled.
- Format: **PMTiles** preferred (one file, also easy to host for the web). Fall back to **MBTiles** if PMTiles support in MapLibre Native isn't solid enough on both platforms.

## Consequences

- ✅ No tile server, no API keys, no per-request costs, works offline.
- ✅ mapcn can show the same data in the admin portal.
- ⚠️ We own the tile build and map style.
- ⚠️ ODbL attribution required.
