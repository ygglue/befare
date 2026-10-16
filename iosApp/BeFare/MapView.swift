import MapLibre
import SwiftUI

/// MapLibre map with the coverage fade, the route line and the two pins.
struct MapView: UIViewRepresentable {
    // MVP: online OpenFreeMap tiles. Bundled offline tiles replace this (ADR 0002).
    static let styleURL = URL(string: "https://tiles.openfreemap.org/styles/liberty")!
    static let center = CLLocationCoordinate2D(latitude: 9.7700, longitude: 118.7400)

    var coverage: [CLLocationCoordinate2D]
    var start: CLLocationCoordinate2D?
    var destination: CLLocationCoordinate2D?
    var route: [CLLocationCoordinate2D]
    var onTap: (CLLocationCoordinate2D) -> Void

    func makeUIView(context: Context) -> MLNMapView {
        let map = MLNMapView(frame: .zero, styleURL: Self.styleURL)
        map.delegate = context.coordinator
        map.setCenter(Self.center, zoomLevel: 12.3, animated: false)
        map.minimumZoomLevel = 11
        map.maximumZoomLevel = 18
        map.allowsRotating = false
        map.allowsTilting = false
        map.logoView.isHidden = false

        let tap = UITapGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.tapped(_:)))
        for case let other as UITapGestureRecognizer in map.gestureRecognizers ?? [] where other.numberOfTapsRequired == 2 {
            tap.require(toFail: other)
        }
        tap.delegate = context.coordinator
        map.addGestureRecognizer(tap)
        return map
    }

    func updateUIView(_ map: MLNMapView, context: Context) {
        context.coordinator.parent = self
        context.coordinator.apply()
    }

    func makeCoordinator() -> Coordinator { Coordinator(parent: self) }

    final class Coordinator: NSObject, MLNMapViewDelegate, UIGestureRecognizerDelegate {
        var parent: MapView
        private weak var map: MLNMapView?
        private var maskSource: MLNShapeSource?
        private var outlineSource: MLNShapeSource?
        private var routeSource: MLNShapeSource?
        private var startSource: MLNShapeSource?
        private var endSource: MLNShapeSource?
        private var coverageCount = 0

        init(parent: MapView) { self.parent = parent }

        // Let our tap run alongside MapLibre's own single-tap recognizer.
        func gestureRecognizer(_ g: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer) -> Bool {
            true
        }

        @objc func tapped(_ g: UITapGestureRecognizer) {
            guard let map = g.view as? MLNMapView else { return }
            parent.onTap(map.convert(g.location(in: map), toCoordinateFrom: map))
        }

        func mapView(_ map: MLNMapView, didFinishLoading style: MLNStyle) {
            self.map = map
            let mask = MLNShapeSource(identifier: "coverage-mask", shape: nil)
            let outline = MLNShapeSource(identifier: "coverage-outline", shape: nil)
            let route = MLNShapeSource(identifier: "route", shape: nil)
            let start = MLNShapeSource(identifier: "pin-start", shape: nil)
            let end = MLNShapeSource(identifier: "pin-end", shape: nil)
            [mask, outline, route, start, end].forEach(style.addSource)

            let fade = MLNFillStyleLayer(identifier: "coverage-mask", source: mask)
            fade.fillColor = NSExpression(forConstantValue: UIColor(white: 0.12, alpha: 1))
            fade.fillOpacity = NSExpression(forConstantValue: 0.55)
            style.addLayer(fade)

            let border = MLNLineStyleLayer(identifier: "coverage-outline", source: outline)
            border.lineColor = NSExpression(forConstantValue: UIColor.systemTeal)
            border.lineWidth = NSExpression(forConstantValue: 2)
            border.lineDashPattern = NSExpression(forConstantValue: [2, 1.5])
            style.addLayer(border)

            let casing = MLNLineStyleLayer(identifier: "route-casing", source: route)
            casing.lineColor = NSExpression(forConstantValue: UIColor.white)
            casing.lineWidth = NSExpression(forConstantValue: 9)
            casing.lineCap = NSExpression(forConstantValue: "round")
            casing.lineJoin = NSExpression(forConstantValue: "round")
            style.addLayer(casing)

            let line = MLNLineStyleLayer(identifier: "route", source: route)
            line.lineColor = NSExpression(forConstantValue: UIColor.systemBlue)
            line.lineWidth = NSExpression(forConstantValue: 5)
            line.lineCap = NSExpression(forConstantValue: "round")
            line.lineJoin = NSExpression(forConstantValue: "round")
            style.addLayer(line)

            for (source, color) in [(start, UIColor.systemGreen), (end, UIColor.systemRed)] {
                let pin = MLNCircleStyleLayer(identifier: source.identifier, source: source)
                pin.circleColor = NSExpression(forConstantValue: color)
                pin.circleRadius = NSExpression(forConstantValue: 9)
                pin.circleStrokeColor = NSExpression(forConstantValue: UIColor.white)
                pin.circleStrokeWidth = NSExpression(forConstantValue: 3)
                style.addLayer(pin)
            }

            (maskSource, outlineSource, routeSource, startSource, endSource) = (mask, outline, route, start, end)
            coverageCount = 0
            apply()
        }

        /// Keep the camera near the covered area.
        func mapView(_ map: MLNMapView, shouldChangeFrom old: MLNMapCamera, to new: MLNMapCamera) -> Bool {
            let c = new.centerCoordinate
            return (9.66...9.92).contains(c.latitude) && (118.62...118.85).contains(c.longitude)
        }

        func apply() {
            guard maskSource != nil else { return }
            var ring = parent.coverage
            if ring.count != coverageCount, ring.count > 2 {
                coverageCount = ring.count
                var world = [
                    CLLocationCoordinate2D(latitude: -85, longitude: -180),
                    CLLocationCoordinate2D(latitude: -85, longitude: 180),
                    CLLocationCoordinate2D(latitude: 85, longitude: 180),
                    CLLocationCoordinate2D(latitude: 85, longitude: -180),
                ]
                let hole = MLNPolygon(coordinates: &ring, count: UInt(ring.count))
                maskSource?.shape = MLNPolygon(coordinates: &world, count: UInt(world.count), interiorPolygons: [hole])
                outlineSource?.shape = MLNPolyline(coordinates: &ring, count: UInt(ring.count))
            }
            var route = parent.route
            routeSource?.shape = route.count > 1 ? MLNPolyline(coordinates: &route, count: UInt(route.count)) : nil
            startSource?.shape = parent.start.map(point)
            endSource?.shape = parent.destination.map(point)
        }

        private func point(_ c: CLLocationCoordinate2D) -> MLNPointFeature {
            let p = MLNPointFeature()
            p.coordinate = c
            return p
        }
    }
}
