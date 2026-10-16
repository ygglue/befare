package app.befare.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.double
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class LatLng(val lat: Double, val lon: Double)

private const val EARTH_RADIUS_M = 6371008.8
private const val DEG = kotlin.math.PI / 180.0

fun haversineM(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Double {
    val dLat = (bLat - aLat) * DEG
    val dLon = (bLon - aLon) * DEG
    val h = sin(dLat / 2).let { it * it } +
        cos(aLat * DEG) * cos(bLat * DEG) * sin(dLon / 2).let { it * it }
    return 2 * EARTH_RADIUS_M * asin(sqrt(h))
}

/** The area BeFare covers. Points outside are rejected. */
class Coverage(val ring: List<LatLng>) {

    fun contains(p: LatLng): Boolean {
        var inside = false
        var j = ring.size - 1
        for (i in ring.indices) {
            val a = ring[i]
            val b = ring[j]
            if ((a.lat > p.lat) != (b.lat > p.lat) &&
                p.lon < (b.lon - a.lon) * (p.lat - a.lat) / (b.lat - a.lat) + a.lon
            ) inside = !inside
            j = i
        }
        return inside
    }

    companion object {
        /** Reads the outer ring of the first polygon in a GeoJSON FeatureCollection. */
        fun fromGeoJson(text: String): Coverage {
            val root = Json.parseToJsonElement(text).jsonObject
            val geometry = root["features"]!!.jsonArray[0].jsonObject["geometry"]!!.jsonObject
            val ring = geometry["coordinates"]!!.jsonArray[0].jsonArray.map {
                val pair = it.jsonArray
                LatLng(lat = pair[1].jsonPrimitive.double, lon = pair[0].jsonPrimitive.double)
            }
            return Coverage(ring)
        }
    }
}
