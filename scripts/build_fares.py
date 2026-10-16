#!/usr/bin/env python3
"""Build data/fares/fares.json from the transcribed matrix in docs/sources/.

The markdown transcription is the source of truth. This script parses it,
checks it against the expected pattern (to catch transcription typos), and
writes the fare bundle the app uses.
"""
import hashlib
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC_MD = ROOT / "docs/sources/pp-city-tricycle-ord1185-2021.md"
SRC_IMG = "pp-city-tricycle-ord1185-2021.jpg"
OUT = ROOT / "data/fares/fares.json"

BANDS = [
    # id, label, gas min (inclusive, centavos), gas max (exclusive, centavos), minimum fare
    ("regular", "Regular fare", None, None, 1200),
    ("gas-60-70", "Gas ₱60–₱70", 6000, 7000, 1200),
    ("gas-70-80", "Gas ₱70–₱80", 7000, 8000, 1400),
    ("gas-80-90", "Gas ₱80–₱90", 8000, 9000, 1600),
    ("gas-90-100", "Gas ₱90–₱100", 9000, 10000, 1800),
    ("gas-100-110", "Gas ₱100–₱110", 10000, 11000, 2000),
    ("gas-110-120", "Gas ₱110–₱120", 11000, 12000, 2200),
]


def parse_matrix(md: str) -> dict[int, list[int]]:
    rows = {}
    for line in md.splitlines():
        m = re.match(r"^\|\s*(\d+)\s*\|(.*)\|\s*$", line)
        if not m:
            continue
        km = int(m.group(1))
        cells = [c.strip() for c in m.group(2).split("|")]
        rows[km] = [round(float(c) * 100) for c in cells]
    return rows


def check_pattern(rows: dict[int, list[int]]) -> list[str]:
    errors = []
    if sorted(rows) != list(range(1, 21)):
        errors.append(f"expected km 1..20, got {sorted(rows)}")
    for km, fares in rows.items():
        if len(fares) != len(BANDS):
            errors.append(f"km {km}: expected {len(BANDS)} columns, got {len(fares)}")
            continue
        regular = 1200 + 200 * max(0, km - 2)
        expected = [regular] + [regular + 200 * i for i in range(len(BANDS) - 1)]
        if fares != expected:
            errors.append(f"km {km}: {fares} != expected {expected}")
    return errors


def main() -> int:
    rows = parse_matrix(SRC_MD.read_text())
    errors = check_pattern(rows)
    if errors:
        print("Transcription does not match expected pattern:", *errors, sep="\n  ")
        return 1

    img_sha = hashlib.sha256((SRC_MD.parent / SRC_IMG).read_bytes()).hexdigest()
    bundle = {
        "version": "2026.10.0-draft",
        "currency": "PHP",
        "amountsIn": "centavos",
        "sources": [
            {
                "id": "pp-ord-1185-2021",
                "issuer": "City Government of Puerto Princesa",
                "document": "City Ordinance No. 1185 — The New Tricycle Fare Ordinance of Puerto Princesa City of 2021",
                "effectiveDate": None,
                "assets": [{"file": SRC_IMG, "sha256": img_sha}],
                "verified": False,
                "notes": "Transcribed from the published fare matrix graphic. Effective date and edge-case rules pending the ordinance text.",
            }
        ],
        "modes": [
            {
                "mode": "tricycle",
                "rules": [
                    {
                        "id": "trike-km-table",
                        "type": "distance_table_by_fuel_band",
                        "label": "Tricycle fare by kilometre and gas price",
                        "sourceId": "pp-ord-1185-2021",
                        "kmRounding": "ceil",
                        "kmRoundingVerified": False,
                        "maxKm": 20,
                        "fuel": "gasoline",
                        # Set from published pump prices before release; the user can override in-app.
                        "currentBand": {"id": None, "asOf": None, "basis": None},
                        "bands": [
                            {
                                "id": bid,
                                "label": label,
                                "gasMinCentavos": gmin,
                                "gasMaxCentavos": gmax,
                                "minimumFare": minfare,
                                "fares": [rows[km][i] for km in range(1, 21)],
                            }
                            for i, (bid, label, gmin, gmax, minfare) in enumerate(BANDS)
                        ],
                    }
                ],
                "discounts": [
                    {
                        "id": "senior-pwd-student",
                        "label": "Senior citizen / PWD / Student",
                        "type": "percent",
                        "value": 20,
                        "sourceId": "pp-ord-1185-2021",
                    }
                ],
                "rounding": {"type": "none", "verified": False, "sourceId": "pp-ord-1185-2021"},
            }
        ],
    }
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(bundle, indent=2, ensure_ascii=False) + "\n")
    print(f"OK: 20 km × {len(BANDS)} bands → {OUT.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
