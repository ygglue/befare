# City Ordinance No. 1185 — Tricycle Fare Matrix (Puerto Princesa City, 2021)

- **File:** [pp-city-tricycle-ord1185-2021.jpg](pp-city-tricycle-ord1185-2021.jpg)
- **Issuer:** City Government of Puerto Princesa (the graphic shows the city seal and a second city office logo)
- **Document:** City Ordinance No. 1185, *"The New Tricycle Fare Ordinance of Puerto Princesa City of 2021"*
- **Effective date:** unknown. The graphic doesn't say. We need the ordinance text.
- **Obtained:** photo of the published fare matrix graphic, provided by the project owner on 2026-10-16. Original location and date: _to fill in_.
- **Transcribed by:** Claude, 2026-10-16. **Checked by:** _pending (second person)_

## Header text (as printed, typos kept)

> The following shall be fixed fares for motorcycle in the City of Puerto Princesa. The minimum fare shall be automatically adjusted based on th prevailing price of gasoline, subject to the mandatory twenty percent (20%) discount laws fo senior citizen, person with disability and student.

## Matrix (fares in PHP)

| KM | Regular fare | Gas ₱60–70 (min ₱12) | Gas ₱70–80 (min ₱14) | Gas ₱80–90 (min ₱16) | Gas ₱90–100 (min ₱18) | Gas ₱100–110 (min ₱20) | Gas ₱110–120 (min ₱22) |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 1 | 12.00 | 12.00 | 14.00 | 16.00 | 18.00 | 20.00 | 22.00 |
| 2 | 12.00 | 12.00 | 14.00 | 16.00 | 18.00 | 20.00 | 22.00 |
| 3 | 14.00 | 14.00 | 16.00 | 18.00 | 20.00 | 22.00 | 24.00 |
| 4 | 16.00 | 16.00 | 18.00 | 20.00 | 22.00 | 24.00 | 26.00 |
| 5 | 18.00 | 18.00 | 20.00 | 22.00 | 24.00 | 26.00 | 28.00 |
| 6 | 20.00 | 20.00 | 22.00 | 24.00 | 26.00 | 28.00 | 30.00 |
| 7 | 22.00 | 22.00 | 24.00 | 26.00 | 28.00 | 30.00 | 32.00 |
| 8 | 24.00 | 24.00 | 26.00 | 28.00 | 30.00 | 32.00 | 34.00 |
| 9 | 26.00 | 26.00 | 28.00 | 30.00 | 32.00 | 34.00 | 36.00 |
| 10 | 28.00 | 28.00 | 30.00 | 32.00 | 34.00 | 36.00 | 38.00 |
| 11 | 30.00 | 30.00 | 32.00 | 34.00 | 36.00 | 38.00 | 40.00 |
| 12 | 32.00 | 32.00 | 34.00 | 36.00 | 38.00 | 40.00 | 42.00 |
| 13 | 34.00 | 34.00 | 36.00 | 38.00 | 40.00 | 42.00 | 44.00 |
| 14 | 36.00 | 36.00 | 38.00 | 40.00 | 42.00 | 44.00 | 46.00 |
| 15 | 38.00 | 38.00 | 40.00 | 42.00 | 44.00 | 46.00 | 48.00 |
| 16 | 40.00 | 40.00 | 42.00 | 44.00 | 46.00 | 48.00 | 50.00 |
| 17 | 42.00 | 42.00 | 44.00 | 46.00 | 48.00 | 50.00 | 52.00 |
| 18 | 44.00 | 44.00 | 46.00 | 48.00 | 50.00 | 52.00 | 54.00 |
| 19 | 46.00 | 46.00 | 48.00 | 50.00 | 52.00 | 54.00 | 56.00 |
| 20 | 48.00 | 48.00 | 50.00 | 52.00 | 54.00 | 56.00 | 58.00 |

## Pattern (checked by `scripts/build_fares.py`, not used as the source of truth)

- Regular fare: ₱12 for 1–2 km, then +₱2 for each extra km.
- Each gas band is ₱2 more than the band below it at every distance. The ₱60–70 band equals the regular fare.

## Not stated in the graphic: confirm from the ordinance text

1. **Partial km:** does 2.1 km count as km 3 (round up), as km 2 (round down), or rounded to the nearest km?
2. **Over 20 km:** is it +₱2 per km, or by agreement? (This doesn't affect v1: coverage to Sta. Lourdes stays well under 20 km of road.)
3. **Gas below ₱60 or above ₱120:** which fare applies? And when exactly does "Regular fare" apply, given it's the same as the ₱60–70 column?
4. **Which gas price, and who announces it:** regular unleaded? Based on DOE price monitoring for Puerto Princesa? Is there an official current band?
5. **Discount rounding:** 20% off ₱14 is ₱11.20. Is it paid as ₱11.20, ₱11, or ₱11.25?
6. **Band edges:** at exactly ₱70.00, is it the ₱60–70 band or the ₱70–80 band?
7. Night surcharge, special trip / "pakyaw", baggage, number of passengers: are any covered?
8. **Effective date**, and whether a later ordinance has changed this one.
