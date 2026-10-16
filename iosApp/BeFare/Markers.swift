import SwiftUI
import UIKit

/// The two map pins: a person where you start, a flag where you're going.
enum Marker: CaseIterable {
    case start, destination

    var symbol: String {
        switch self {
        case .start: "figure.stand"
        case .destination: "flag.fill"
        }
    }

    var color: UIColor {
        switch self {
        case .start: .systemGreen
        case .destination: .systemRed
        }
    }

    var imageName: String {
        switch self {
        case .start: "marker-start"
        case .destination: "marker-destination"
        }
    }

    /// A map pin: a coloured circle with a white symbol and a small tail. The tip of the tail
    /// is the exact location, so the map layer anchors it at the bottom.
    var image: UIImage {
        let diameter: CGFloat = 38
        let tail: CGFloat = 9
        let stroke: CGFloat = 2.5
        let size = CGSize(width: diameter + stroke * 2, height: diameter + tail + stroke * 2)
        return UIGraphicsImageRenderer(size: size).image { ctx in
            let circle = CGRect(x: stroke, y: stroke, width: diameter, height: diameter)
            let path = UIBezierPath(ovalIn: circle)
            let tip = CGPoint(x: size.width / 2, y: size.height - stroke)
            let tailPath = UIBezierPath()
            tailPath.move(to: CGPoint(x: circle.midX - 8, y: circle.maxY - 5))
            tailPath.addLine(to: tip)
            tailPath.addLine(to: CGPoint(x: circle.midX + 8, y: circle.maxY - 5))
            tailPath.close()
            path.append(tailPath)

            ctx.cgContext.setShadow(offset: CGSize(width: 0, height: 1), blur: 3, color: UIColor.black.withAlphaComponent(0.5).cgColor)
            UIColor.white.setFill()
            path.fill()
            ctx.cgContext.setShadow(offset: .zero, blur: 0)

            color.setFill()
            UIBezierPath(ovalIn: circle.insetBy(dx: stroke, dy: stroke)).fill()

            let config = UIImage.SymbolConfiguration(pointSize: 17, weight: .bold)
            if let glyph = UIImage(systemName: symbol, withConfiguration: config)?.withTintColor(.white, renderingMode: .alwaysOriginal) {
                glyph.draw(at: CGPoint(x: circle.midX - glyph.size.width / 2, y: circle.midY - glyph.size.height / 2))
            }
        }
    }
}
