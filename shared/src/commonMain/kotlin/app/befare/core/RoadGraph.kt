package app.befare.core

import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * Directed road graph in CSR form, read from graph.bin (format v1, see pipeline/build_graph.py).
 * Coordinates are stored as E7 integers; edge lengths in centimetres.
 */
class RoadGraph private constructor(
    val latE7: IntArray,
    val lonE7: IntArray,
    val firstEdge: IntArray,
    val target: IntArray,
    val lengthCm: IntArray,
) {
    val nodeCount get() = latE7.size

    fun lat(node: Int) = latE7[node] / 1e7
    fun lon(node: Int) = lonE7[node] / 1e7

    /** Directed edge id from [from] to [to], or -1. */
    fun edge(from: Int, to: Int): Int {
        for (e in firstEdge[from] until firstEdge[from + 1]) if (target[e] == to) return e
        return -1
    }

    // Source node of each edge, for snapping.
    private val source = IntArray(target.size).also { src ->
        for (n in 0 until nodeCount) for (e in firstEdge[n] until firstEdge[n + 1]) src[e] = n
    }

    fun source(edge: Int) = source[edge]

    // ---- Snapping ---------------------------------------------------------------------------

    private val cellDeg = 0.001 // ~110 m
    private val grid = HashMap<Long, MutableList<Int>>().also { grid ->
        for (e in target.indices) {
            val a = source[e]
            val b = target[e]
            if (edge(b, a) in 0 until e) continue // index each two-way segment once
            val minLat = minOf(lat(a), lat(b)); val maxLat = maxOf(lat(a), lat(b))
            val minLon = minOf(lon(a), lon(b)); val maxLon = maxOf(lon(a), lon(b))
            for (cy in cell(minLat)..cell(maxLat)) for (cx in cell(minLon)..cell(maxLon)) {
                grid.getOrPut(key(cy, cx)) { mutableListOf() }.add(e)
            }
        }
    }

    private fun cell(deg: Double) = floor(deg / cellDeg).toInt()
    private fun key(cy: Int, cx: Int) = (cy.toLong() shl 32) or (cx.toLong() and 0xffffffffL)

    /** A point on a road: directed edge [edge] at fraction [t] from its source. */
    data class Snap(val edge: Int, val t: Double, val point: LatLng, val distanceM: Double)

    fun snap(p: LatLng, maxDistanceM: Double = 250.0): Snap? {
        val mPerDegLat = 110_574.0
        val mPerDegLon = 111_320.0 * cos(p.lat * kotlin.math.PI / 180)
        val radius = (maxDistanceM / 110_000 / cellDeg).toInt() + 1
        val cy0 = cell(p.lat); val cx0 = cell(p.lon)
        var best: Snap? = null
        val seen = HashSet<Int>()
        for (cy in cy0 - radius..cy0 + radius) for (cx in cx0 - radius..cx0 + radius) {
            for (e in grid[key(cy, cx)] ?: continue) {
                if (!seen.add(e)) continue
                val a = source[e]; val b = target[e]
                val ax = (lon(a) - p.lon) * mPerDegLon; val ay = (lat(a) - p.lat) * mPerDegLat
                val bx = (lon(b) - p.lon) * mPerDegLon; val by = (lat(b) - p.lat) * mPerDegLat
                val dx = bx - ax; val dy = by - ay
                val len2 = dx * dx + dy * dy
                val t = if (len2 == 0.0) 0.0 else (-(ax * dx + ay * dy) / len2).coerceIn(0.0, 1.0)
                val qx = ax + t * dx; val qy = ay + t * dy
                val d = sqrt(qx * qx + qy * qy)
                if (d <= maxDistanceM && (best == null || d < best.distanceM)) {
                    best = Snap(
                        edge = e, t = t, distanceM = d,
                        point = LatLng(lat(a) + t * (lat(b) - lat(a)), lon(a) + t * (lon(b) - lon(a))),
                    )
                }
            }
        }
        return best
    }

    companion object {
        fun parse(bytes: ByteArray): RoadGraph {
            val r = LeReader(bytes)
            require(r.ascii(4) == "BFGR") { "not a BeFare graph" }
            val version = r.u16()
            require(version == 1) { "unsupported graph version $version" }
            val n = r.i32()
            val m = r.i32()
            val lat = IntArray(n); val lon = IntArray(n)
            for (i in 0 until n) { lat[i] = r.i32(); lon[i] = r.i32() }
            val first = IntArray(n + 1) { r.i32() }
            val tgt = IntArray(m); val len = IntArray(m)
            for (i in 0 until m) { tgt[i] = r.i32(); len[i] = r.i32(); r.skip(2) }
            return RoadGraph(lat, lon, first, tgt, len)
        }
    }
}

private class LeReader(private val b: ByteArray) {
    private var pos = 0
    fun skip(n: Int) { pos += n }
    fun ascii(n: Int) = (0 until n).map { b[pos + it].toInt().toChar() }.joinToString("").also { pos += n }
    fun u16(): Int = ((b[pos].toInt() and 0xff) or ((b[pos + 1].toInt() and 0xff) shl 8)).also { pos += 2 }
    fun i32(): Int = ((b[pos].toInt() and 0xff) or
        ((b[pos + 1].toInt() and 0xff) shl 8) or
        ((b[pos + 2].toInt() and 0xff) shl 16) or
        ((b[pos + 3].toInt() and 0xff) shl 24)).also { pos += 4 }
}
