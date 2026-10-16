# ADR 0001: Kotlin Multiplatform with native UIs

- **Status:** Accepted
- **Date:** 2026-10-16

## Context

BeFare runs on Android and iOS. Its value is in routing and fare calculation that must give **the same answer on both platforms**. The map needs MapLibre with offline vector tiles.

Options considered: fully native (Kotlin + Swift), Flutter, Kotlin Multiplatform (KMP), and a PWA/Capacitor web app with mapcn.

## Decision

Use **Kotlin Multiplatform** for all non-UI logic (`shared/`: geo, graph, routing, fare, data). Build the UIs natively: **Jetpack Compose** on Android, **SwiftUI** on iOS. Both use **MapLibre Native**.

## Consequences

- ✅ Fare and routing logic is written and tested once. No drift between platforms.
- ✅ Native look and feel, and full access to MapLibre Native features.
- ✅ The build-time pipeline (Kotlin/JVM) can reuse `shared/`'s graph format code.
- ⚠️ Two UI codebases. Keep screens simple and push logic into `shared/`.
- ⚠️ The Swift side needs care with KMP types. Use SKIE for sealed classes, suspend functions and flows.
- ⚠️ iOS builds need a Mac and Xcode in CI.
