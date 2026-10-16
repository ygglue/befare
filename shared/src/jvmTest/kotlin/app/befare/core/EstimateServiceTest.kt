package app.befare.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Runs against the real bundled data (run pipeline/build_graph.py and scripts/build_fares.py first). */
class EstimateServiceTest {

    private val root = generateSequence(java.io.File("").absoluteFile) { it.parentFile }
        .first { java.io.File(it, "data/fares/fares.json").exists() }

    private val service = EstimateService.fromFiles(
        graphPath = "$root/data/build/graph.bin",
        faresPath = "$root/data/fares/fares.json",
        coveragePath = "$root/data/coverage/coverage.geojson",
    )

    // Robinsons Place Palawan (north) → Puerto Princesa Baywalk (city proper).
    private val robinsons = LatLng(9.7856, 118.7437)
    private val baywalk = LatLng(9.7395, 118.7310)

    @Test fun routesAcrossTheCity() {
        val ok = assertIs<EstimateResult.Ok>(service.estimate(robinsons, baywalk, "regular"))
        val crow = haversineM(robinsons.lat, robinsons.lon, baywalk.lat, baywalk.lon)
        assertTrue(ok.route.distanceM > crow, "road distance should exceed straight line")
        assertTrue(ok.route.distanceM < crow * 2, "road distance ${ok.route.distanceM} is implausibly long (crow $crow)")
        assertIs<FareResult.Ok>(ok.fare)
    }

    @Test fun reverseTripIsSimilar() {
        val a = assertIs<EstimateResult.Ok>(service.estimate(robinsons, baywalk, "regular")).route.distanceM
        val b = assertIs<EstimateResult.Ok>(service.estimate(baywalk, robinsons, "regular")).route.distanceM
        assertTrue(kotlin.math.abs(a - b) < 1500, "one-ways differ too much: $a vs $b")
    }

    @Test fun rejectsPointsOutsideCoverage() {
        val sabang = LatLng(10.197, 118.89)
        assertEquals(EstimateResult.OutsideCoverage(Endpoint.END), service.estimate(baywalk, sabang, "regular"))
    }

    @Test fun samePointIsZeroDistance() {
        val ok = assertIs<EstimateResult.Ok>(service.estimate(baywalk, baywalk, "regular"))
        assertTrue(ok.route.distanceM < 1.0)
    }
}
