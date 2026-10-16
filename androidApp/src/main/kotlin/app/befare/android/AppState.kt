package app.befare.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.befare.core.Endpoint
import app.befare.core.EstimateResult
import app.befare.core.EstimateService
import app.befare.core.FareQuote
import app.befare.core.FareResult
import app.befare.core.FuelBand
import app.befare.core.LatLng

/** UI state around the shared Kotlin [EstimateService]. Mirrors iosApp/BeFare/AppModel.swift. */
class AppState {
    enum class Pin { START, DESTINATION }

    sealed interface Outcome {
        data class Quote(val route: List<LatLng>, val quote: FareQuote) : Outcome
        data class RouteOnly(val route: List<LatLng>, val message: String) : Outcome
        data class Message(val text: String) : Outcome
    }

    var service by mutableStateOf<EstimateService?>(null)
        private set
    var offline by mutableStateOf<OfflineData?>(null)
        private set
    var start by mutableStateOf<LatLng?>(null)
        private set
    var destination by mutableStateOf<LatLng?>(null)
        private set
    var placing by mutableStateOf(Pin.START)
    var bandId by mutableStateOf("")
        private set
    var outcome by mutableStateOf<Outcome?>(null)
        private set

    val bands: List<FuelBand> get() = service?.fares?.bands.orEmpty()
    val isCurrentBandSet: Boolean get() = service?.fares?.isCurrentBandSet ?: false

    val route: List<LatLng>
        get() = when (val o = outcome) {
            is Outcome.Quote -> o.route
            is Outcome.RouteOnly -> o.route
            else -> emptyList()
        }

    fun loaded(data: OfflineData, service: EstimateService) {
        this.offline = data
        this.service = service
        this.bandId = service.fares.defaultBandId
    }

    fun place(p: LatLng) {
        when (placing) {
            Pin.START -> {
                start = p
                if (destination == null) placing = Pin.DESTINATION
            }
            Pin.DESTINATION -> destination = p
        }
        recompute()
    }

    fun swap() {
        val s = start
        start = destination
        destination = s
        recompute()
    }

    fun reset() {
        start = null
        destination = null
        placing = Pin.START
        outcome = null
    }

    fun selectBand(id: String) {
        bandId = id
        recompute()
    }

    private fun recompute() {
        val service = service
        val s = start
        val d = destination
        if (service == null || s == null || d == null) {
            outcome = null
            return
        }
        outcome = when (val r = service.estimate(s, d, bandId)) {
            is EstimateResult.Ok -> when (val fare = r.fare) {
                is FareResult.Ok -> Outcome.Quote(r.route.points, fare.quote)
                is FareResult.OverTable -> Outcome.RouteOnly(
                    r.route.points,
                    "This trip is ${fare.chargedKm} km. The fare matrix only goes up to ${fare.maxKm} km.",
                )
            }
            is EstimateResult.OutsideCoverage ->
                Outcome.Message("The ${r.which.label} is outside BeFare's area (city proper to Sta. Lourdes).")
            is EstimateResult.NoRoadNearby ->
                Outcome.Message("No road near the ${r.which.label} pin. Move it closer to a street.")
            EstimateResult.NoRoute -> Outcome.Message("No route found between these points.")
        }
    }

    private val Endpoint.label get() = if (this == Endpoint.START) "start" else "destination"
}
