#!/usr/bin/env bash
# Builds everything the iOS MVP needs: road graph, fare bundle, Kotlin framework, Xcode project.
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@21}"

if [ ! -f data/osm/roads-overpass.json ]; then
  echo "Downloading roads from Overpass…"
  mkdir -p data/osm
  curl -sf -A "BeFare-dev/0.1" --data-urlencode "data@pipeline/roads.overpassql" \
    https://overpass-api.de/api/interpreter -o data/osm/roads-overpass.json
fi

python3 -I pipeline/build_graph.py
python3 -I scripts/build_fares.py
./gradlew :shared:jvmTest :shared:assembleBeFareCoreDebugXCFramework --console=plain -q
(cd iosApp && xcodegen generate --quiet)
echo "Done. Open iosApp/BeFare.xcodeproj and run the BeFare scheme on an iOS simulator."
