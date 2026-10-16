package app.befare.core

data class Route(val points: List<LatLng>, val distanceM: Double)

/** A* over [RoadGraph] between two snapped points, with edge length as cost. */
class Router(private val g: RoadGraph) {

    fun route(start: RoadGraph.Snap, end: RoadGraph.Snap): Route? {
        val n = g.nodeCount
        val endLat = end.point.lat
        val endLon = end.point.lon
        fun h(node: Int) = haversineM(g.lat(node), g.lon(node), endLat, endLon)

        // Leaving the start point: along its edge forwards, and backwards if the road is two-way.
        val su = g.source(start.edge); val sv = g.target[start.edge]
        val sLen = g.lengthCm[start.edge] / 100.0
        val sBack = g.edge(sv, su)
        // Arriving at the end point: from its edge's source, or from its target if two-way.
        val eu = g.source(end.edge); val ev = g.target[end.edge]
        val eLen = g.lengthCm[end.edge] / 100.0
        val eBack = g.edge(ev, eu)
        val tail = HashMap<Int, Double>().apply {
            put(eu, end.t * eLen)
            if (eBack >= 0) put(ev, minOf(get(ev) ?: Double.POSITIVE_INFINITY, (1 - end.t) * eLen))
        }

        var best = Double.POSITIVE_INFINITY
        var bestVia = -1 // -2 = direct along the shared segment

        // Both points on the same road segment.
        if (setOf(su, sv) == setOf(eu, ev)) {
            val ps = start.t                                  // position from su
            val pe = if (eu == su) end.t else 1 - end.t       // end position from su
            if (pe >= ps) {
                best = (pe - ps) * sLen; bestVia = -2
            } else if (sBack >= 0) {
                best = (ps - pe) * sLen; bestVia = -2
            }
        }

        val dist = DoubleArray(n) { Double.POSITIVE_INFINITY }
        val prev = IntArray(n) { -1 }
        val heap = MinHeap()
        fun push(node: Int, d: Double) {
            if (d < dist[node]) { dist[node] = d; heap.push(node, d + h(node)) }
        }
        push(sv, (1 - start.t) * sLen)
        if (sBack >= 0) push(su, start.t * sLen)

        while (heap.isNotEmpty()) {
            val f = heap.peekKey()
            val x = heap.pop()
            if (f >= best) break
            val gx = dist[x]
            if (f > gx + h(x) + 1e-6) continue // stale entry
            tail[x]?.let { if (gx + it < best) { best = gx + it; bestVia = x } }
            for (e in g.firstEdge[x] until g.firstEdge[x + 1]) {
                val y = g.target[e]
                val d = gx + g.lengthCm[e] / 100.0
                if (d < dist[y]) { prev[y] = x; push(y, d) }
            }
        }

        if (bestVia == -1) return null
        val pts = ArrayList<LatLng>()
        pts += start.point
        if (bestVia >= 0) {
            val path = ArrayList<Int>()
            var x = bestVia
            while (x != -1) { path += x; x = prev[x] }
            path.reverse()
            path.forEach { pts += LatLng(g.lat(it), g.lon(it)) }
        }
        pts += end.point
        return Route(pts, best)
    }
}

/** Binary min-heap of (node, key). Kotlin common has no PriorityQueue. */
private class MinHeap {
    private var nodes = IntArray(64)
    private var keys = DoubleArray(64)
    private var size = 0

    fun isNotEmpty() = size > 0
    fun peekKey() = keys[0]

    fun push(node: Int, key: Double) {
        if (size == nodes.size) { nodes = nodes.copyOf(size * 2); keys = keys.copyOf(size * 2) }
        var i = size++
        while (i > 0) {
            val p = (i - 1) / 2
            if (keys[p] <= key) break
            nodes[i] = nodes[p]; keys[i] = keys[p]; i = p
        }
        nodes[i] = node; keys[i] = key
    }

    fun pop(): Int {
        val top = nodes[0]
        val lastNode = nodes[--size]; val lastKey = keys[size]
        var i = 0
        while (true) {
            var c = 2 * i + 1
            if (c >= size) break
            if (c + 1 < size && keys[c + 1] < keys[c]) c++
            if (keys[c] >= lastKey) break
            nodes[i] = nodes[c]; keys[i] = keys[c]; i = c
        }
        nodes[i] = lastNode; keys[i] = lastKey
        return top
    }
}
