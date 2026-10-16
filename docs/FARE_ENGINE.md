# Fare Engine & Provenance

## Who sets the fares

- **Tricycles** are franchised and regulated by the **city government** under the Local Government Code (RA 7160). Their fares come from a **City ordinance / Sangguniang Panlungsod resolution**, not from LTFRB. This is v1's only mode.
- **Jeepneys, UV Express, taxis, buses** are regulated by **LTFRB** (memorandum circulars and regional board orders). These come in phase 2.

The engine is built for both. Every rule records which body issued it.

## Source of truth for v1

**City Ordinance No. 1185 (2021)**, *The New Tricycle Fare Ordinance of Puerto Princesa City*. The source photo and a hand transcription are in [sources/pp-city-tricycle-ord1185-2021.md](sources/pp-city-tricycle-ord1185-2021.md). `scripts/build_fares.py` reads the transcription, checks it against the expected pattern (to catch typos), and writes `data/fares/fares.json`. The photo is bundled with the app so Verify can show it.

### How Puerto Princesa tricycle fares work

- A **table by whole kilometre (1–20 km)**, not a formula.
- **Seven columns:** "Regular fare" plus six **gas-price bands** (₱60–70 up to ₱110–120 per litre). The fare goes up ₱2 for each band.
- Regular fare: ₱12 for 1–2 km, then +₱2 per km (₱48 at 20 km).
- 20% discount for senior citizens, PWDs and students.

**So the fare depends on the current gas price, which the app can't know while offline.** How we handle that:

1. The fare bundle includes a **current band** (`currentBand`, with an "as of" date), chosen by us from published pump prices. Phase 2 updates this over the network.
2. The estimate screen shows which band is in use ("Gas ₱70–80 · as of Oct 2026"). The user can **change the band** if pump prices have moved. The choice is saved on the device.
3. Verify shows the whole row for the trip's km across all bands, with the band in use highlighted. That way driver and passenger can agree on the band themselves.

### Still to confirm from the ordinance text

Details are in the source file. They affect calculation:
- **Partial km.** Assumed **round up** (2.1 km → km 3). Flagged `kmRoundingVerified: false`.
- **Discount rounding.** Assumed **none** (₱11.20 shown as is).
- **Band edges** (exactly ₱70.00), gas outside ₱60–120, and when "Regular fare" applies instead of the ₱60–70 column.
- Trips over 20 km. Not reachable within v1 coverage, so the engine returns "over table" rather than guessing.

## Data model

```jsonc
{
  "version": "2026.10.0",
  "currency": "PHP",
  "sources": [
    {
      "id": "pp-trike-matrix",
      "issuer": "City Government of Puerto Princesa",       // or "LTFRB"
      "document": "Ordinance No. ____ / Tricycle Fare Matrix", // exact title & number from the photo
      "effectiveDate": "YYYY-MM-DD",
      "assets": [{ "file": "pp-trike-matrix.jpg", "regions": { "base": [x, y, w, h] } }],
      "notes": "Photographed by … on …; location where posted"
    }
  ],
  "modes": [
    {
      "mode": "tricycle",
      "rules": [ /* see rule types */ ],
      "discounts": [
        { "id": "student-senior-pwd", "label": "Student / Senior / PWD", "type": "percent",
          "value": 20, "sourceId": "pp-trike-matrix" }
      ],
      "rounding": { "type": "nearest", "step": 0.25, "sourceId": "pp-trike-matrix" }
    }
  ]
}
```

### Rule types

| Type | Meaning | Fields |
|---|---|---|
| `base_plus_distance` | Base fare for the first N km, then a rate per km (or per part of a km) | `baseFare`, `baseKm`, `perKm`, `increment` (`"per_km"` \| `"per_500m"` …), `chargePartial` (bool) |
| `distance_table` | A lookup table of distance bands → fare | `bands: [{ upToKm, fare }]` |
| `distance_table_by_fuel_band` | **(v1, Puerto Princesa)** Fare by whole km, one column per fuel-price band | `kmRounding`, `maxKm`, `fuel`, `bands: [{ id, label, gasMinCentavos, gasMaxCentavos, minimumFare, fares[] }]` |
| `zone_matrix` | Fare by origin zone × destination zone (zones = polygons such as barangays) | `zones: [{ id, polygon }]`, `matrix: { "A→B": fare }` |
| `flat` | One fare inside an area | `fare`, `area` (polygon id) |
| `surcharge` | Added on when a condition applies (night, special trip) | `amount` or `percent`, `when` (time window / flag) |

Each rule has `id`, `label` (shown to users), `sourceId`, and optional `sourceRegion` (which part of the image to highlight).

## Calculation

```
input:  route distance (m), start/end points, mode, passengerType, fuel band (bundle default or user choice),
        departure time, flags (e.g. special trip)
1. Find the mode's main rule. Use zone/flat rules if start and end fall inside the areas they cover; otherwise use a distance rule.
2. Work out the base amount, keeping each step as a BreakdownLine.
3. Apply surcharges that match the time and flags.
4. Apply the discount for the passenger type (if any).
5. Apply rounding.
output: FareQuote { amount, regularAmount, lines[], sources[], dataVersion, assumptions[] }
```

All money math uses **integer centavos**, never floating point. Distance is rounded to whole metres before any rule runs. The rounding policy is part of the data (above), because ordinances differ.

### Example breakdown (Ord. 1185, gas band ₱70–80)

```
Route distance                         5.3 km   (road distance, BeFare routing)
Charged as                             6 km     (partial km rounded up — assumption, unverified)
Fare, 6 km, gas ₱70–80                ₱ 22.00   [City Ord. 1185 matrix, row 6]
Student / Senior / PWD −20%           ₱ −4.40   [City Ord. 1185]
Discounted fare                       ₱ 17.60   (no rounding rule stated — unverified)
```

## Verify screen

1. **Summary**: fare, distance, data version, effective date.
2. **Breakdown**: the lines above, each with a source chip.
3. **Sources**: tap a chip → full-screen image of the matrix with the relevant part highlighted, plus the issuer, document number and date.
4. **Assumptions** (always shown):
   - Distance is the shortest legal road route. The driver's actual route may differ.
   - Special trips / "pakyaw" are priced differently (if the ordinance covers this).
   - Fare data version X, effective date Y. Check for updates when online (phase 2).

## Testing

- Unit tests for each rule type, including edge cases: exactly at `baseKm`, 1 m past it, zero distance, zone borders.
- **Golden trips** (`shared/src/commonTest/resources/golden-trips.json`): real start/end pairs with the expected fare worked out by hand from the matrix photo.
- A fares.json schema check runs in CI. Every rule must point to an existing source.
