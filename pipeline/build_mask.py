#!/usr/bin/env python3
"""Builds data/build/coverage-smooth.geojson and data/build/mask.geojson: the soft fade outside coverage.

MapLibre can't blur a fill, so the fade is a stack of masks. Each mask is the
whole world minus the coverage area grown by a bit more. Every mask gets its own
opacity, chosen so that the stacked result follows a smoothstep curve: fully
clear at the coverage edge, fully solid MASK_COLOR at FADE_WIDTH_M outside.
The map fades away into an empty surround.

The hand-drawn coverage polygon is padded by PAD_M and then smoothed into a
curve (Chaikin corner cutting), so the fade has no corners or straight runs.
The app uses that smoothed outline for both the fade and the "inside coverage"
check, so a pin is accepted exactly where the map is clear.

Run with the pipeline venv: .venv/bin/python pipeline/build_mask.py
"""
import json
import math
import pathlib

from shapely.geometry import Polygon, box, mapping, shape
from shapely.ops import transform

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = ROOT / "data/coverage/coverage.geojson"
OUT = ROOT / "data/build/mask.geojson"
OUT_COVERAGE = ROOT / "data/build/coverage-smooth.geojson"

FADE_WIDTH_M = 1800.0
STEPS = 32
PAD_M = 350.0          # grow the hand-drawn polygon before smoothing so corner cutting doesn't lose places
CHAIKIN_ITERATIONS = 6

# Places that must stay inside the smoothed coverage.
MUST_INCLUDE = {
    "Santa Lourdes": (118.7255, 9.8345), "Tagburos": (118.7410, 9.8198), "Irawan": (118.6923, 9.8012),
    "Sicsican": (118.7129, 9.7950), "San Jose": (118.7487, 9.7943), "San Manuel": (118.7569, 9.7793),
    "San Pedro": (118.7471, 9.7601), "Mandaragat": (118.7397, 9.7488), "Bancao-Bancao": (118.7619, 9.7335),
    "Airport": (118.7584, 9.7424), "Baywalk": (118.7310, 9.7395),
}
MASK_COLOR = "#0e0f12"  # the "nothing" the map fades into; the app uses it as the surround colour

coverage = shape(json.loads(SRC.read_text())["features"][0]["geometry"])
lat0 = coverage.centroid.y
m_per_deg_lat = 110_574.0
m_per_deg_lon = 111_320.0 * math.cos(math.radians(lat0))


def to_m(x, y, z=None):
    return x * m_per_deg_lon, y * m_per_deg_lat


def to_deg(x, y, z=None):
    return x / m_per_deg_lon, y / m_per_deg_lat


def smoothstep(x):
    return x * x * (3 - 2 * x)


def chaikin(coords, iterations):
    """Corner cutting on a closed ring; converges to a smooth quadratic B-spline curve."""
    pts = list(coords[:-1])
    for _ in range(iterations):
        out = []
        for (x0, y0), (x1, y1) in zip(pts, pts[1:] + pts[:1]):
            out.append((0.75 * x0 + 0.25 * x1, 0.75 * y0 + 0.25 * y1))
            out.append((0.25 * x0 + 0.75 * x1, 0.25 * y0 + 0.75 * y1))
        pts = out
    return pts + pts[:1]


padded = transform(to_m, coverage).buffer(PAD_M, resolution=4, join_style=2)  # mitre: keep corners for Chaikin
rounded = Polygon(chaikin(list(padded.exterior.coords), CHAIKIN_ITERATIONS)).buffer(0)

missing = [name for name, (lon, lat) in MUST_INCLUDE.items()
           if not rounded.contains(transform(to_m, Polygon([(lon, lat), (lon + 1e-6, lat), (lon, lat + 1e-6)])))]
if missing:
    raise SystemExit(f"smoothed coverage lost: {', '.join(missing)}; increase PAD_M")

smooth_deg = transform(to_deg, rounded.simplify(5))
OUT_COVERAGE.parent.mkdir(parents=True, exist_ok=True)
OUT_COVERAGE.write_text(json.dumps({"type": "FeatureCollection", "features": [{
    "type": "Feature",
    "properties": {"name": "BeFare coverage (smoothed)", "source": "data/coverage/coverage.geojson", "padM": PAD_M},
    "geometry": mapping(smooth_deg),
}]}, separators=(",", ":")))

world = box(-180, -85, 180, 85)

features = []
previous = 0.0  # stacked opacity reached by the masks so far
for i in range(STEPS):
    target = smoothstep((i + 1) / STEPS)
    opacity = 1.0 if target >= 1.0 else 1 - (1 - target) / (1 - previous)
    previous = target
    grown = transform(to_deg, rounded.buffer(FADE_WIDTH_M * i / STEPS, resolution=32).simplify(4))
    hole = grown.exterior.coords if isinstance(grown, Polygon) else grown.convex_hull.exterior.coords
    features.append({
        "type": "Feature",
        "properties": {"step": i, "opacity": round(opacity, 5)},
        "geometry": mapping(Polygon(world.exterior.coords, [hole])),
    })

OUT.parent.mkdir(parents=True, exist_ok=True)
OUT.write_text(json.dumps({
    "type": "FeatureCollection",
    "properties": {"color": MASK_COLOR, "fadeWidthM": FADE_WIDTH_M, "steps": STEPS},
    "features": features,
}, separators=(",", ":")))
print(f"smoothed coverage: {len(smooth_deg.exterior.coords)} vertices -> {OUT_COVERAGE.relative_to(ROOT)}")
print(f"{STEPS} masks over {FADE_WIDTH_M:.0f} m, fading to {MASK_COLOR} "
      f"-> {OUT.relative_to(ROOT)} ({OUT.stat().st_size / 1024:.0f} KiB)")
