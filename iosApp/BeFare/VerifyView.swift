import BeFareCore
import SwiftUI

/// Shows how the estimate was calculated and the official source behind it.
struct VerifyView: View {
    let quote: FareQuote
    let isCurrentBandSet: Bool
    @Environment(\.dismiss) private var dismiss
    @State private var showSource = false

    var body: some View {
        NavigationStack {
            List {
                Section("How this fare was calculated") {
                    ForEach(Array(quote.lines.enumerated()), id: \.offset) { _, line in
                        HStack(alignment: .firstTextBaseline) {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(line.label)
                                Text(line.detail).font(.caption).foregroundStyle(.secondary)
                            }
                            Spacer()
                            if let amount = line.amountCentavos {
                                Text(peso(amount.int64Value)).monospacedDigit().fontWeight(.semibold)
                            }
                        }
                    }
                }

                Section {
                    ForEach(quote.row, id: \.bandId) { cell in
                        let current = cell.bandId == quote.band.id
                        HStack {
                            Text(cell.label)
                            Spacer()
                            Text(peso(cell.centavos)).monospacedDigit()
                        }
                        .fontWeight(current ? .bold : .regular)
                        .listRowBackground(current ? Color.accentColor.opacity(0.15) : nil)
                    }
                } header: {
                    Text("Matrix row: \(quote.chargedKm) km")
                } footer: {
                    Text(isCurrentBandSet
                         ? "The highlighted band is the one in use."
                         : "The fare depends on today's gas price. Agree on the band with your driver.")
                }

                Section("Source") {
                    LabeledContent("Issued by", value: quote.source.issuer)
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Document").font(.caption).foregroundStyle(.secondary)
                        Text(quote.source.document)
                    }
                    LabeledContent("Effective", value: quote.source.effectiveDate ?? "Not confirmed")
                    LabeledContent("Fare data version", value: quote.dataVersion)
                    if let image = sourceImage {
                        Button { showSource = true } label: {
                            Image(uiImage: image).resizable().scaledToFit()
                                .clipShape(RoundedRectangle(cornerRadius: 8))
                                .overlay(alignment: .bottomTrailing) {
                                    Label("Open", systemImage: "arrow.up.left.and.arrow.down.right")
                                        .font(.caption.weight(.semibold))
                                        .padding(6).background(.thinMaterial, in: Capsule()).padding(8)
                                }
                        }
                        .buttonStyle(.plain)
                    }
                }

                Section("Assumptions") {
                    ForEach(quote.assumptions, id: \.self) { text in
                        Label(text, systemImage: "info.circle").font(.subheadline)
                    }
                }
            }
            .navigationTitle("Verify estimate")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } } }
            .fullScreenCover(isPresented: $showSource) {
                if let image = sourceImage { SourceImageView(image: image, row: quote.chargedKm) }
            }
        }
    }

    private var sourceImage: UIImage? {
        guard let file = quote.source.assets.first?.file else { return nil }
        let name = (file as NSString).deletingPathExtension
        let ext = (file as NSString).pathExtension
        return Bundle.main.path(forResource: name, ofType: ext).flatMap(UIImage.init(contentsOfFile:))
    }
}

/// Full-screen, zoomable view of the original fare matrix.
private struct SourceImageView: View {
    let image: UIImage
    let row: Int32
    @Environment(\.dismiss) private var dismiss
    @State private var scale: CGFloat = 1
    @GestureState private var pinch: CGFloat = 1

    var body: some View {
        NavigationStack {
            ScrollView([.horizontal, .vertical]) {
                Image(uiImage: image).resizable().scaledToFit()
                    .frame(width: UIScreen.main.bounds.width * scale * pinch)
            }
            .gesture(MagnifyGesture()
                .updating($pinch) { value, state, _ in state = value.magnification }
                .onEnded { scale = min(4, max(1, scale * $0.magnification)) })
            .background(Color.black)
            .navigationTitle("Look for the \(row) km row")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } } }
        }
    }
}
