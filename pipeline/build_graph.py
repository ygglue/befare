#!/usr/bin/env python3
"""MVP routing-graph builder: Overpass road JSON -> data/build/graph.bin.

Temporary Python stand-in for the Kotlin/JVM pipeline described in
docs/DATA_PIPELINE.md. Writes graph format v1 (MVP subset):

  magic "BFGR" | u16 version=1 | u32 nodeCount | u32 edgeCount
  nodes   : i32 latE7, i32 lonE7                      x nodeCount
  offsets : u32 firstEdge (CSR, outgoing)             x (nodeCount + 1)
  edges   : u32 target, u32 lengthCm, u8 roadClass, u8 reserved   x edgeCount

All little-endian. Edges are directed; two-way roads get one edge each way.
Only the largest strongly connected component is kept.
"""
import json
import math
import pathlib
import struct
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = ROOT / "data/osm/roads-overpass.json"
OUT = ROOT / "data/build/graph.bin"

ROAD_CLASS = {
    "trunk": 1, "trunk_link": 1, "primary": 2, "primary_link": 2,
    "secondary": 3, "secondary_link": 3, "tertiary": 4, "tertiary_link": 4,
    "unclassified": 5, "road": 5, "residential": 6, "living_street": 6,
    "service": 7, "track": 8,
}


def haversine_m(a, b):
    r = 6371008.8
    la1, lo1, la2, lo2 = map(math.radians, (a[0], a[1], b[0], b[1]))
    h = math.sin((la2 - la1) / 2) ** 2 + math.cos(la1) * math.cos(la2) * math.sin((lo2 - lo1) / 2) ** 2
    return 2 * r * math.asin(math.sqrt(h))


def oneway_dir(tags):
    ow = tags.get("oneway", "")
    if ow in ("yes", "true", "1"):
        return 1
    if ow == "-1":
        return -1
    if ow == "no":
        return 0
    if tags.get("junction") in ("roundabout", "circular"):
        return 1
    return 0


def largest_scc(n, adj):
    """Iterative Tarjan; returns the set of nodes in the largest SCC."""
    index = [-1] * n
    low = [0] * n
    on_stack = [False] * n
    stack, best, counter = [], [], 0
    for root in range(n):
        if index[root] != -1:
            continue
        work = [(root, 0)]
        while work:
            v, i = work.pop()
            if i == 0:
                index[v] = low[v] = counter
                counter += 1
                stack.append(v)
                on_stack[v] = True
            recurse = False
            nbrs = adj[v]
            while i < len(nbrs):
                w = nbrs[i][0]
                i += 1
                if index[w] == -1:
                    work.append((v, i))
                    work.append((w, 0))
                    recurse = True
                    break
                if on_stack[w]:
                    low[v] = min(low[v], index[w])
            if recurse:
                continue
            if low[v] == index[v]:
                comp = []
                while True:
                    w = stack.pop()
                    on_stack[w] = False
                    comp.append(w)
                    if w == v:
                        break
                if len(comp) > len(best):
                    best = comp
            if work:
                parent = work[-1][0]
                low[parent] = min(low[parent], low[v])
    return set(best)


def main():
    data = json.loads(SRC.read_text())
    coords = {e["id"]: (e["lat"], e["lon"]) for e in data["elements"] if e["type"] == "node"}
    ways = [e for e in data["elements"] if e["type"] == "way"]

    ids, idx = [], {}

    def node(nid):
        if nid not in idx:
            idx[nid] = len(ids)
            ids.append(nid)
        return idx[nid]

    # For the MVP every OSM node becomes a graph node (graph is small); the
    # spec's degree-2 contraction comes with the Kotlin pipeline.
    adj_raw = {}
    for w in ways:
        tags = w.get("tags", {})
        rc = ROAD_CLASS.get(tags.get("highway"), 9)
        d = oneway_dir(tags)
        nodes = [n for n in w["nodes"] if n in coords]
        for a, b in zip(nodes, nodes[1:]):
            if a == b:
                continue
            ia, ib = node(a), node(b)
            length_cm = max(1, round(haversine_m(coords[a], coords[b]) * 100))
            if d >= 0:
                adj_raw.setdefault(ia, []).append((ib, length_cm, rc))
            if d <= 0:
                adj_raw.setdefault(ib, []).append((ia, length_cm, rc))

    n = len(ids)
    adj = [adj_raw.get(i, []) for i in range(n)]
    keep = largest_scc(n, adj)
    remap = {old: new for new, old in enumerate(sorted(keep))}

    nodes_out = [coords[ids[old]] for old in sorted(keep)]
    offsets, edges = [0], []
    for old in sorted(keep):
        seen = {}
        for (t, length, rc) in adj[old]:
            if t in remap:
                # Collapse duplicate parallel edges, keeping the shortest.
                k = remap[t]
                if k not in seen or seen[k][0] > length:
                    seen[k] = (length, rc)
        for k, (length, rc) in sorted(seen.items()):
            edges.append((k, length, rc))
        offsets.append(len(edges))

    OUT.parent.mkdir(parents=True, exist_ok=True)
    with OUT.open("wb") as f:
        f.write(b"BFGR")
        f.write(struct.pack("<HII", 1, len(nodes_out), len(edges)))
        for lat, lon in nodes_out:
            f.write(struct.pack("<ii", round(lat * 1e7), round(lon * 1e7)))
        f.write(struct.pack(f"<{len(offsets)}I", *offsets))
        for t, length, rc in edges:
            f.write(struct.pack("<IIBB", t, length, rc, 0))

    total_km = sum(e[1] for e in edges) / 100 / 1000 / 2
    print(f"nodes {len(nodes_out)} (of {n}), directed edges {len(edges)}, ~{total_km:.0f} km of road, "
          f"{OUT.stat().st_size / 1024:.0f} KiB -> {OUT.relative_to(ROOT)}")


if __name__ == "__main__":
    sys.exit(main())
