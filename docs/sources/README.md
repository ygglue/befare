# Fare Sources

Original fare documents BeFare relies on. Each file here gets an entry in `data/fares/fares.json` → `sources[]`.

## How to add a source

1. Put the original file here unchanged, named `<issuer>-<mode>-<yyyy-mm-dd>.<ext>`, e.g. `pp-city-tricycle-2025-01-15.jpg`.
2. Next to it, create a `.md` with the same name containing:
   - Issuer (e.g. City Government of Puerto Princesa / LTFRB)
   - Document title and number (ordinance / MC number), as printed
   - Effective date
   - Where and when it was obtained (e.g. "photo of matrix posted at …, taken by … on …")
   - A plain-text transcription of the matrix
3. A second person checks the transcription against the image.
4. Run `python3 scripts/build_fares.py` to regenerate `data/fares/fares.json`.

## Sources

| File | Issuer | Document | Effective | Status |
|---|---|---|---|---|
| [pp-city-tricycle-ord1185-2021.jpg](pp-city-tricycle-ord1185-2021.jpg) ([transcription](pp-city-tricycle-ord1185-2021.md)) | City Government of Puerto Princesa | City Ordinance No. 1185 (2021) | Unknown | Transcribed; second-person check pending; ordinance text needed |
