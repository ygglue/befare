import BeFareCore
import SwiftUI

struct ContentView: View {
    @Bindable var model: AppModel
    @State private var showVerify = false
    @State private var mapController = MapController()

    var body: some View {
        ZStack {
            MapView(
                start: model.start,
                destination: model.destination,
                route: routeLine,
                controller: mapController,
                onTap: { model.place($0) }
            )
            .ignoresSafeArea()

            VStack(spacing: 0) {
                pinPicker
                Spacer()
                HStack {
                    Spacer()
                    zoomButtons
                }
                .padding(.bottom, 12)
                card
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 8)
        }
        .sheet(isPresented: $showVerify) {
            if case let .quote(_, quote) = model.outcome {
                VerifyView(quote: quote, isCurrentBandSet: model.isCurrentBandSet)
            }
        }
    }

    private var routeLine: [CLLocationCoordinate2D] {
        switch model.outcome {
        case let .quote(route, _), let .route(route, _): route
        default: []
        }
    }

    // MARK: - Zoom

    private var zoomButtons: some View {
        VStack(spacing: 0) {
            Button { mapController.zoom(by: 1) } label: {
                Image(systemName: "plus").frame(width: 44, height: 44)
            }
            .accessibilityLabel("Zoom in")
            Divider().frame(width: 28)
            Button { mapController.zoom(by: -1) } label: {
                Image(systemName: "minus").frame(width: 44, height: 44)
            }
            .accessibilityLabel("Zoom out")
        }
        .font(.body.weight(.semibold))
        .foregroundStyle(.primary)
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
    }

    // MARK: - Top: which pin a tap places

    private var pinPicker: some View {
        HStack(spacing: 8) {
            pinChip(.start, title: "Start", color: .green, set: model.start != nil)
            pinChip(.destination, title: "Destination", color: .red, set: model.destination != nil)
            Spacer()
            if model.start != nil && model.destination != nil {
                iconButton("arrow.up.arrow.down", label: "Swap") { model.swap() }
            }
            if model.start != nil || model.destination != nil {
                iconButton("xmark", label: "Clear") { model.reset() }
            }
        }
        .padding(.top, 8)
    }

    private func pinChip(_ pin: AppModel.Pin, title: String, color: Color, set: Bool) -> some View {
        let active = model.placing == pin
        return Button { model.placing = pin } label: {
            HStack(spacing: 6) {
                Circle().fill(color).frame(width: 10, height: 10)
                Text(title).font(.subheadline.weight(active ? .semibold : .regular))
                if set { Image(systemName: "checkmark").font(.caption2.weight(.bold)) }
            }
            .padding(.horizontal, 12).padding(.vertical, 8)
            .background(.regularMaterial, in: Capsule())
            .overlay(Capsule().strokeBorder(active ? color : .clear, lineWidth: 2))
        }
        .buttonStyle(.plain)
    }

    private func iconButton(_ symbol: String, label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: symbol).font(.subheadline.weight(.semibold))
                .frame(width: 36, height: 36)
                .background(.regularMaterial, in: Circle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }

    // MARK: - Bottom card

    @ViewBuilder private var card: some View {
        VStack(alignment: .leading, spacing: 12) {
            switch model.outcome {
            case nil:
                hint
            case let .message(text):
                Label(text, systemImage: "exclamationmark.triangle.fill")
                    .foregroundStyle(.orange)
                    .font(.subheadline)
            case let .route(_, text):
                Label(text, systemImage: "info.circle").font(.subheadline)
            case let .quote(_, quote):
                quoteView(quote)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
    }

    private var hint: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("BeFare").font(.title3.bold())
            Text(model.service == nil
                 ? "Loading Puerto Princesa roads…"
                 : model.placing == .start ? "Tap the map to set where you're starting."
                                           : "Now tap where you're going.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
        }
    }

    private func quoteView(_ q: FareQuote) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .firstTextBaseline) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Tricycle fare").font(.caption).foregroundStyle(.secondary)
                    Text(peso(q.fareCentavos)).font(.system(size: 40, weight: .bold, design: .rounded))
                }
                Spacer()
                VStack(alignment: .trailing, spacing: 2) {
                    Text(String(format: "%.1f km", q.distanceM / 1000)).font(.headline)
                    Text("charged as \(q.chargedKm) km").font(.caption).foregroundStyle(.secondary)
                }
            }
            if let d = q.discountedCentavos, let label = q.discountLabel {
                Text("\(label): **\(peso(d.int64Value))**").font(.subheadline)
            }
            HStack {
                bandMenu(q)
                Spacer()
                Button {
                    showVerify = true
                } label: {
                    Label("Verify", systemImage: "checkmark.seal")
                        .font(.subheadline.weight(.semibold))
                }
                .buttonStyle(.borderedProminent)
            }
        }
    }

    private func bandMenu(_ q: FareQuote) -> some View {
        Menu {
            Picker("Gas price", selection: Binding(get: { model.bandId }, set: { model.selectBand($0) })) {
                ForEach(model.bands, id: \.id) { band in Text(band.label).tag(band.id) }
            }
        } label: {
            HStack(spacing: 4) {
                Image(systemName: "fuelpump")
                Text(q.band.label)
                Image(systemName: "chevron.up.chevron.down").font(.caption2)
            }
            .font(.subheadline)
        }
        .accessibilityLabel("Gas price band")
    }
}
