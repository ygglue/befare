import BeFareCore
import CoreLocation
import Observation

/// UI state around the shared Kotlin `EstimateService`. All routing and fare math happens in Kotlin.
@Observable
final class AppModel {
    enum Pin { case start, destination }

    enum Outcome {
        case quote(route: [CLLocationCoordinate2D], quote: FareQuote)
        case route(route: [CLLocationCoordinate2D], message: String)
        case message(String)
    }

    private(set) var service: EstimateService?
    var start: CLLocationCoordinate2D?
    var destination: CLLocationCoordinate2D?
    var placing: Pin = .start
    var bandId: String = ""
    private(set) var outcome: Outcome?
    /// Latest GPS fix (works offline). Nil until the user allows location access and a fix arrives.
    private(set) var myLocation: CLLocationCoordinate2D?
    private var autoStartDone = false
    private var outsideNoticeShown = false

    init() {
        Task.detached(priority: .userInitiated) {
            let b = Bundle.main
            let service = EstimateService.companion.fromFiles(
                graphPath: b.path(forResource: "graph", ofType: "bin")!,
                faresPath: b.path(forResource: "fares", ofType: "json")!,
                coveragePath: b.path(forResource: "coverage-smooth", ofType: "geojson")!
            )
            await MainActor.run {
                self.service = service
                self.bandId = service.fares.defaultBandId
                self.autoStartIfReady()
            }
        }
    }

    var bands: [FuelBand] { service?.fares.bands ?? [] }
    var isCurrentBandSet: Bool { service?.fares.isCurrentBandSet ?? false }

    func place(_ c: CLLocationCoordinate2D) {
        switch placing {
        case .start:
            start = c
            if destination == nil { placing = .destination }
        case .destination:
            destination = c
        }
        recompute()
    }

    // MARK: - Location

    func locationUpdated(_ c: CLLocationCoordinate2D) {
        myLocation = c
        autoStartIfReady()
    }

    /// The first fix inside coverage becomes the start, unless the user has placed one. Fixes outside
    /// coverage don't end the wait; the user is told once.
    private func autoStartIfReady() {
        guard !autoStartDone, service != nil, let c = myLocation else { return }
        if start != nil {
            autoStartDone = true
        } else if isInCoverage(c) {
            autoStartDone = true
            useMyLocationAsStart()
        } else if !outsideNoticeShown {
            outsideNoticeShown = true
            outcome = .message("You're outside BeFare's area (city proper to Sta. Lourdes). Tap the map to choose a start.")
        }
    }

    func showLocationUnavailable() {
        let status = CLLocationManager().authorizationStatus
        outcome = .message(status == .denied || status == .restricted
            ? "Location access is off for BeFare. Turn it on in Settings › Privacy › Location Services."
            : "Waiting for a GPS fix. Make sure Location Services is on; it works without internet.")
    }

    func isInCoverage(_ c: CLLocationCoordinate2D) -> Bool {
        service?.coverage.contains(p: LatLng(lat: c.latitude, lon: c.longitude)) ?? false
    }

    /// Sets the start to the current GPS position. Returns false (and says why) if it can't.
    @discardableResult
    func useMyLocationAsStart() -> Bool {
        guard let c = myLocation, let service else { return false }
        guard isInCoverage(c) else {
            outcome = .message("You're outside BeFare's area (city proper to Sta. Lourdes), so your location can't be the start.")
            return false
        }
        start = c
        if destination == nil { placing = .destination }
        recompute()
        return true
    }

    func swap() {
        (start, destination) = (destination, start)
        recompute()
    }

    func reset() {
        start = nil
        destination = nil
        placing = .start
        outcome = nil
    }

    func selectBand(_ id: String) {
        bandId = id
        recompute()
    }

    private func recompute() {
        guard let service, let s = start, let d = destination else { outcome = nil; return }
        let result = service.estimate(
            start: LatLng(lat: s.latitude, lon: s.longitude),
            end: LatLng(lat: d.latitude, lon: d.longitude),
            bandId: bandId
        )
        switch result {
        case let ok as EstimateResultOk:
            let line = ok.route.points.map { CLLocationCoordinate2D(latitude: $0.lat, longitude: $0.lon) }
            if let fare = ok.fare as? FareResultOk {
                outcome = .quote(route: line, quote: fare.quote)
            } else if let over = ok.fare as? FareResultOverTable {
                outcome = .route(route: line, message: "This trip is \(over.chargedKm) km. The fare matrix only goes up to \(over.maxKm) km.")
            }
        case let out as EstimateResultOutsideCoverage:
            outcome = .message("The \(out.which == .start ? "start" : "destination") is outside BeFare's area (city proper to Sta. Lourdes).")
        case let none as EstimateResultNoRoadNearby:
            outcome = .message("No road near the \(none.which == .start ? "start" : "destination") pin. Move it closer to a street.")
        default:
            outcome = .message("No route found between these points.")
        }
    }
}

func peso(_ centavos: Int64) -> String { FareKt.formatPeso(centavos: centavos) }
