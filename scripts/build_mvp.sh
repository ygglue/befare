#!/usr/bin/env bash
# Builds everything the MVP apps need: road graph, fare bundle, offline map (tiles, style,
# fonts, sprites, fade mask), the Kotlin framework + Xcode project (iOS) and the debug APK (Android).
#
# Needs: Xcode, Homebrew openjdk@21, xcodegen, pmtiles; Node; Python 3; for Android, the SDK path
# in local.properties (sdk.dir=...) or ANDROID_HOME.
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@21}"

# Protomaps daily planet build to cut the city's tiles from (https://maps.protomaps.com/builds/).
PROTOMAPS_BUILD="${PROTOMAPS_BUILD:-20261015}"
# Wider than the coverage area so the faded surroundings are drawn at the minimum zoom.
TILES_BBOX="118.55,9.55,118.92,10.03"

if [ ! -f data/osm/roads-overpass.json ]; then
  echo "Downloading roads from Overpass…"
  mkdir -p data/osm
  curl -sf -A "BeFare-dev/0.1" --data-urlencode "data@pipeline/roads.overpassql" \
    https://overpass-api.de/api/interpreter -o data/osm/roads-overpass.json
fi

if [ ! -f data/build/befare.pmtiles ]; then
  echo "Extracting map tiles from Protomaps build $PROTOMAPS_BUILD…"
  mkdir -p data/build
  pmtiles extract "https://build.protomaps.com/$PROTOMAPS_BUILD.pmtiles" data/build/befare.pmtiles \
    --bbox="$TILES_BBOX" --maxzoom=15
fi

if [ ! -x .venv/bin/python ]; then
  python3 -m venv .venv
  .venv/bin/pip install -q -r pipeline/requirements.txt
fi

python3 -I pipeline/build_graph.py
python3 -I scripts/build_fares.py
.venv/bin/python pipeline/build_mask.py
(cd pipeline/style && npm ci --silent --no-fund --no-audit && node build_style.mjs)
./gradlew :shared:jvmTest :shared:assembleBeFareCoreDebugXCFramework --console=plain -q
(cd iosApp && xcodegen generate --quiet)
if [ -f local.properties ] || [ -n "${ANDROID_HOME:-}" ]; then
  ./gradlew :androidApp:assembleDebug --console=plain -q
  echo "Android: androidApp/build/outputs/apk/debug/androidApp-debug.apk"
fi
echo "Done. iOS: open iosApp/BeFare.xcodeproj and run the BeFare scheme on a simulator."
