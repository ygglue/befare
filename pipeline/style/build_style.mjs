// Builds data/build/style/: an offline MapLibre style, plus the fonts and sprites it needs.
//
// The style uses placeholders the app fills in at runtime with bundle file URLs:
//   {TILES}  -> file URL of befare.pmtiles
//   {ASSETS} -> file URL of the style directory (fonts/, sprites/)
import { layers, namedFlavor } from "@protomaps/basemaps";
import { mkdir, writeFile, access } from "node:fs/promises";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "../..");
const OUT = join(ROOT, "data/build/style");

// protomaps/basemaps-assets, pinned.
const ASSETS_COMMIT = "028c18f713baecad011301ff7a69acc39bcc2ae7";
const ASSETS_BASE = `https://raw.githubusercontent.com/protomaps/basemaps-assets/${ASSETS_COMMIT}`;
const FONTS = ["Noto Sans Regular", "Noto Sans Medium", "Noto Sans Italic"];
// Basic Latin + Latin-1 (incl. ñ), Latin Extended-A, and General Punctuation/currency (₱ is U+20B1).
const RANGES = ["0-255", "256-511", "8192-8447"];
const FLAVOR = "dark";
const SPRITES = [`${FLAVOR}.json`, `${FLAVOR}.png`, `${FLAVOR}@2x.json`, `${FLAVOR}@2x.png`];

// Protomaps' dark labels are ~2:1 against the land colour (#1f1f1f). Brighten them to ≥4.5:1 (WCAG AA).
const flavor = {
  ...namedFlavor(FLAVOR),
  roads_label_minor: "#8e8e8e",    // 5.0:1
  roads_label_major: "#a8a8a8",    // 6.9:1
  subplace_label: "#9c9c9c",       // 6.0:1
  address_label: "#8a8a8a",        // 4.7:1
  city_label: "#c8c8c8",           // 10:1
  ocean_label: "#9ea6b5",          // 4.9:1 on water
  pois: { ...namedFlavor(FLAVOR).pois, lapis: "#7C9BF7" }, // was 2.9:1
};

const style = {
  version: 8,
  name: "BeFare (offline)",
  glyphs: "{ASSETS}/fonts/{fontstack}/{range}.pbf",
  sprite: `{ASSETS}/sprites/${FLAVOR}`,
  sources: {
    protomaps: {
      type: "vector",
      url: "pmtiles://{TILES}",
      attribution: "© OpenStreetMap contributors · Protomaps",
    },
  },
  layers: layers("protomaps", flavor, { lang: "en" }),
};

async function fetchTo(url, path) {
  try { await access(path); return; } catch {}
  const res = await fetch(url);
  if (!res.ok) throw new Error(`${res.status} ${url}`);
  await mkdir(dirname(path), { recursive: true });
  await writeFile(path, Buffer.from(await res.arrayBuffer()));
}

await mkdir(OUT, { recursive: true });
await writeFile(join(OUT, "befare-style.json"), JSON.stringify(style));

for (const font of FONTS) {
  for (const range of RANGES) {
    await fetchTo(`${ASSETS_BASE}/fonts/${encodeURIComponent(font)}/${range}.pbf`, join(OUT, "fonts", font, `${range}.pbf`));
  }
}
for (const file of SPRITES) {
  await fetchTo(`${ASSETS_BASE}/sprites/v4/${file}`, join(OUT, "sprites", file));
}
await fetchTo(`${ASSETS_BASE}/fonts/OFL.txt`, join(OUT, "fonts", "OFL.txt"));

const fontStacks = new Set(JSON.stringify(style.layers).match(/Noto Sans [A-Za-z]+/g));
console.log(`style: ${style.layers.length} layers; fonts used: ${[...fontStacks].join(", ")} -> ${OUT}`);
