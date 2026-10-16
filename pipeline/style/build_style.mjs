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
  layers: layers("protomaps", namedFlavor(FLAVOR), { lang: "en" }),
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
