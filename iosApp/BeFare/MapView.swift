import MapLibre
import SwiftUI

/// MapLibre map with the coverage fade, the route line and the two pins. Fully offline:
/// tiles, style, fonts, sprites and the fade mask are all bundled (see pipeline/).
struct MapView: UIViewRepresentable {
    static let center = CLLocationCoordinate2D(latitude: 9.7700, longitude: 118.7400)

    var start: CLLocationCoordinate2D?
    var destination: CLLocationCoordinate2D?
    var route: [CLLocationCoordinate2D]
    var controller: MapController
    var onTap: (CLLocationCoordinate2D) -> Void

    func makeUIView(context: Context) -> MLNMapView {
        let map = MLNMapView(frame: .zero, styleURL: OfflineStyle.url)
        map.delegate = context.coordinator
        map.setCenter(Self.center, zoomLevel: 12.3, animated: false)
        map.minimumZoomLevel = 11
        map.maximumZoomLevel = 18
        map.allowsRotating = false
        map.allowsTilting = false
        map.backgroundColor = OfflineStyle.maskColor

        let tap = UITapGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.tapped(_:)))
        for case let other as UITapGestureRecognizer in map.gestureRecognizers ?? [] where other.numberOfTapsRequired == 2 {
            tap.require(toFail: other)
        }
        tap.delegate = context.coordinator
        map.addGestureRecognizer(tap)
        controller.map = map
        return map
    }

    func updateUIView(_ map: MLNMapView, context: Context) {
        context.coordinator.parent = self
        context.coordinator.apply()
    }

    func makeCoordinator() -> Coordinator { Coordinator(parent: self) }

    final class Coordinator: NSObject, MLNMapViewDelegate, UIGestureRecognizerDelegate {
        var parent: MapView
        private var routeSource: MLNShapeSource?
        private var startSource: MLNShapeSource?
        private var endSource: MLNShapeSource?

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
            // Fade to nothing outside coverage: stacked masks, each slightly larger, each with its own
            // opacity so together they follow a smooth curve to solid (pipeline/build_mask.py).
            let mask = MLNShapeSource(identifier: "coverage-mask", url: OfflineStyle.maskURL)
            style.addSource(mask)
            let fade = MLNFillStyleLayer(identifier: "coverage-mask", source: mask)
            fade.fillColor = NSExpression(forConstantValue: OfflineStyle.maskColor)
            fade.fillOpacity = NSExpression(forKeyPath: "opacity")
            fade.fillAntialiased = NSExpression(forConstantValue: false)
            style.addLayer(fade)

            let route = MLNShapeSource(identifier: "route", shape: nil)
            let start = MLNShapeSource(identifier: "pin-start", shape: nil)
            let end = MLNShapeSource(identifier: "pin-end", shape: nil)
            [route, start, end].forEach(style.addSource)

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

            for (source, marker) in [(start, Marker.start), (end, Marker.destination)] {
                style.setImage(marker.image, forName: marker.imageName)
                let pin = MLNSymbolStyleLayer(identifier: source.identifier, source: source)
                pin.iconImageName = NSExpression(forConstantValue: marker.imageName)
                pin.iconAnchor = NSExpression(forConstantValue: "bottom") // tail tip = the location
                pin.iconAllowsOverlap = NSExpression(forConstantValue: true)
                pin.iconIgnoresPlacement = NSExpression(forConstantValue: true)
                style.addLayer(pin)
            }

            (routeSource, startSource, endSource) = (route, start, end)
            apply()
            parent.controller.applyOrnamentMargins() // the map has its real size by now
        }

        /// Keep the camera centred over the covered area. The bundled tiles extend far enough past it
        /// (pipeline: --bbox=118.55,9.55,118.92,10.03) to fill the screen at the minimum zoom.
        func mapView(_ map: MLNMapView, shouldChangeFrom old: MLNMapCamera, to new: MLNMapCamera) -> Bool {
            let c = new.centerCoordinate
            return (9.718...9.870).contains(c.latitude) && (118.675...118.790).contains(c.longitude)
        }

        func apply() {
            guard routeSource != nil else { return }
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

/// Lets SwiftUI controls (zoom buttons) drive the map.
final class MapController {
    weak var map: MLNMapView? {
        didSet { applyOrnamentMargins() }
    }
    private var cardTop: CGFloat = 0

    /// Moves MapLibre's logo and attribution button so they sit just above the fare card,
    /// whose top edge is at [y] in screen coordinates (the map fills the screen).
    func keepOrnamentsAbove(y: CGFloat) {
        cardTop = y
        applyOrnamentMargins()
    }

    func applyOrnamentMargins() {
        guard let map, cardTop > 0, map.bounds.height > 0 else { return }
        // Margins are measured from the safe area, so take the bottom inset back out.
        let bottom = map.bounds.height - cardTop - map.safeAreaInsets.bottom + 8
        map.logoViewPosition = .bottomLeft
        map.logoViewMargins = CGPoint(x: 20, y: bottom)
        map.attributionButtonPosition = .bottomLeft
        map.attributionButtonMargins = CGPoint(x: 20 + map.logoView.bounds.width + 8, y: bottom)
    }

    func zoom(by delta: Double) {
        guard let map else { return }
        let level = min(max(map.zoomLevel + delta, map.minimumZoomLevel), map.maximumZoomLevel)
        map.setZoomLevel(level, animated: true)
    }
}

/// The bundled offline style. Its JSON has {TILES} and {ASSETS} placeholders (pipeline/style/build_style.mjs)
/// that are replaced with file URLs inside the app bundle, then written to Caches for MapLibre to load.
enum OfflineStyle {
    static let url: URL = {
        let bundle = Bundle.main
        guard let styleDir = bundle.url(forResource: "style", withExtension: nil),
              let tiles = bundle.url(forResource: "befare", withExtension: "pmtiles"),
              var json = try? String(contentsOf: styleDir.appendingPathComponent("befare-style.json"), encoding: .utf8)
        else { fatalError("offline map data missing from bundle; run scripts/build_mvp.sh") }

        var assets = styleDir.absoluteString
        if assets.hasSuffix("/") { assets.removeLast() }
        json = json
            .replacingOccurrences(of: "{TILES}", with: tiles.absoluteString)
            .replacingOccurrences(of: "{ASSETS}", with: assets)
        let out = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("befare-style.json")
        try? json.write(to: out, atomically: true, encoding: .utf8)
        return out
    }()

    static let maskURL = Bundle.main.url(forResource: "mask", withExtension: "geojson")!

    /// The colour the map fades into, written by pipeline/build_mask.py.
    static let maskColor: UIColor = {
        guard let data = try? Data(contentsOf: maskURL),
              let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let props = obj["properties"] as? [String: Any],
              let hex = (props["color"] as? String)?.dropFirst(), hex.count == 6,
              let rgb = UInt32(hex, radix: 16)
        else { return .black }
        return UIColor(red: CGFloat((rgb >> 16) & 0xff) / 255,
                       green: CGFloat((rgb >> 8) & 0xff) / 255,
                       blue: CGFloat(rgb & 0xff) / 255, alpha: 1)
    }()
}
