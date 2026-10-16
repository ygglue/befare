# Fare Engine & Provenance

## Who sets the fares

- **Tricycles** are franchised and regulated by the **city government** under the Local Government Code (RA 7160). Their fares come from a **City ordinance / Sangguniang Panlungsod resolution**, not from LTFRB. This is v1's only mode.
- **Jeepneys, UV Express, taxis, buses** are regulated by **LTFRB** (memorandum circulars and regional board orders). These come in phase 2.

The engine is built for both. Every rule records which body issued it.

## Source of truth for v1

The tricycle fare matrix photo goes in `docs/sources/` (see [sources/README.md](sources/README.md)). It gets transcribed **by hand** into `data/fares/fares.json`, then checked by a second person against the photo. The photo is bundled with the app so Verify can show it.

> **Not yet known:** the shape of Puerto Princesa's tricycle fare (flat within the city proper? base + per km? zones by barangay?). The model below covers all three, so we can transcribe whatever the matrix says without changing the engine.

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
| `zone_matrix` | Fare by origin zone × destination zone (zones = polygons such as barangays) | `zones: [{ id, polygon }]`, `matrix: { "A→B": fare }` |
| `flat` | One fare inside an area | `fare`, `area` (polygon id) |
| `surcharge` | Added on when a condition applies (night, special trip) | `amount` or `percent`, `when` (time window / flag) |

Each rule has `id`, `label` (shown to users), `sourceId`, and optional `sourceRegion` (which part of the image to highlight).

## Calculation

```
input:  route distance (m), start/end points, mode, passengerType, departure time, flags (e.g. special trip)
1. Find the mode's main rule. Use zone/flat rules if start and end fall inside the areas they cover; otherwise use a distance rule.
2. Work out the base amount, keeping each step as a BreakdownLine.
3. Apply surcharges that match the time and flags.
4. Apply the discount for the passenger type (if any).
5. Apply rounding.
output: FareQuote { amount, regularAmount, lines[], sources[], dataVersion, assumptions[] }
```

All money math uses **integer centavos**, never floating point. Distance is rounded to whole metres before any rule runs. The rounding policy is part of the data (above), because ordinances differ.

### Example breakdown (illustrative numbers only; not the real Puerto Princesa rates)

```
Route distance                         5.3 km   (road distance, BeFare routing)
Base fare, first 2 km                 ₱ 15.00   [City Ordinance __, p.1]
Extra 3.3 km × ₱ 2.00 /km (per km)    ₱  8.00   [City Ordinance __, p.1]   ← partial km charged as full km
Subtotal                              ₱ 23.00
Student/Senior/PWD discount −20%      ₱ −4.60   [City Ordinance __; RA 9994 / RA 10754]
Rounded to nearest ₱ 0.25             ₱ 18.50
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
