# BeFare — Product Requirements

## Problem

Commuters in Puerto Princesa, especially visitors, often don't know the correct tricycle fare and can't check it on the spot. The official fare matrix exists, but it's hard to find, hard to read, and nobody carries a copy. Mobile data is patchy, so an app that needs to be online doesn't help.

## Goal

Give any commuter a trustworthy fare estimate in seconds, fully offline, and let them **show where the number comes from**.

## Users

- **Residents** who want to confirm a fare or settle a disagreement politely.
- **Tourists and visitors** who don't know local fares or landmarks.
- **Drivers** (secondary) who can point to the same official source.

## v1 scope (offline)

### Must have

1. **Offline map** of urban Puerto Princesa, from the city proper north to Sta. Lourdes (exact boundary in [ADR 0004](adr/0004-urban-coverage.md)).
   - The area outside coverage is shown faded and you can't pan far into it.
   - Works on first launch with no network (data is bundled with the app).
2. **Pin start and destination.**
   - Defaults to the user's GPS location as the start, if permission is given and they're inside coverage.
   - Long-press or drag to set or move either pin. Pins outside coverage are rejected with a clear message.
   - Swap start and destination.
3. **Road route** between the pins, drawn on the map, with distance.
   - Pins snap to the nearest road that tricycles can use.
4. **Tricycle fare estimate** using City Ordinance No. 1185 (2021), the city's tricycle fare matrix.
   - The fare depends on the **current gas-price band**. The app ships with a default band and an "as of" date, and the user can change it.
   - Regular fare plus the 20% discounted fare (student / senior / PWD).
   - Shows the fare data version and effective date.
5. **Verify button** that opens a breakdown:
   - Each step of the calculation (for example, base fare for the first N km, then a per-km rate times the extra distance, then rounding).
   - The source for each rule: issuing body, document title/number, effective date, and the bundled image/PDF of the matrix, zoomed to the relevant part where possible.
   - A plain note that this is an estimate based on road distance, and the route actually taken may differ.

### Should have

- Search for landmarks (offline, from OSM place/POI names plus a curated list of common local landmarks).
- Recent trips.
- English and Filipino UI.

### Out of scope for v1

- Jeepney, multicab, van, taxi fares (needs route data and LTFRB matrices; see phase 2).
- Turn-by-turn navigation.
- Accounts, sync, or any network feature.

## Phase 2 (online)

- **Plate lookup and reviews**: see [PLATE_LOOKUP.md](PLATE_LOOKUP.md). Only launches with permission from the right authorities (the City government for tricycles, LTFRB for PUVs).
- **Fare data updates** downloaded when online, signed and versioned.
- **More modes**: jeepney (requires route geometry and stops), taxi, UV Express.
- **QR / NFC** scanning to open a vehicle's page.
- **Web portal** (mapcn) for admins to maintain fare data and moderate reviews.

## Success measures

- Fare shown within 2 seconds of placing the second pin, offline, on a mid-range Android phone.
- App download under ~40 MB.
- The fare BeFare shows matches a hand calculation from the matrix for a set of known test trips (100% of the test set).
- Users open "Verify" on a meaningful share of estimates. That suggests the trust feature is being used.

## Open questions

- ~~Distance, zone or flat fare?~~ Answered: a table by whole km × gas-price band (Ord. 1185).
- Ordinance details the matrix graphic doesn't state: partial-km rounding, discount rounding, band edges, effective date. See [the source notes](sources/pp-city-tricycle-ord1185-2021.md). Getting the full ordinance text from the City Council (Sangguniang Panlungsod) office answers all of them.
- Who keeps the current gas band up to date, and from what price source?
- Are tricycles barred from any roads (for example, parts of the national highway)? If so, routing must avoid them.
- Special-trip / "pakyaw" fares: show them as a separate line?
- Night surcharge, baggage, or extra-passenger rules?
