package app.befare.core

enum class Endpoint { START, END }

sealed interface EstimateResult {
    data class Ok(val route: Route, val fare: FareResult) : EstimateResult
    data class OutsideCoverage(val which: Endpoint) : EstimateResult
    data class NoRoadNearby(val which: Endpoint) : EstimateResult
    data object NoRoute : EstimateResult
}

/** The one entry point the UI uses: two pins in, route + fare + breakdown out. */
class EstimateService(
    private val graph: RoadGraph,
    val coverage: Coverage,
    val fares: FareCalculator,
) {
    private val router = Router(graph)

    fun estimate(start: LatLng, end: LatLng, bandId: String): EstimateResult {
        if (!coverage.contains(start)) return EstimateResult.OutsideCoverage(Endpoint.START)
        if (!coverage.contains(end)) return EstimateResult.OutsideCoverage(Endpoint.END)
        val s = graph.snap(start) ?: return EstimateResult.NoRoadNearby(Endpoint.START)
        val e = graph.snap(end) ?: return EstimateResult.NoRoadNearby(Endpoint.END)
        val route = router.route(s, e) ?: return EstimateResult.NoRoute
        return EstimateResult.Ok(route, fares.quote(route.distanceM, bandId))
    }

    companion object {
        fun fromFiles(graphPath: String, faresPath: String, coveragePath: String) = EstimateService(
            graph = RoadGraph.parse(readFileBytes(graphPath)),
            coverage = Coverage.fromGeoJson(readFileBytes(coveragePath).decodeToString()),
            fares = FareCalculator.fromJson(readFileBytes(faresPath).decodeToString()),
        )
    }
}

expect fun readFileBytes(path: String): ByteArray
