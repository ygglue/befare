package app.befare.android

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Colours checked for WCAG AA (4.5:1 for text) on the overlay surface they sit on:
 * text on [Overlay] ≈ 14:1, muted text ≈ 8:1, [Accent] ≈ 7:1, white on [ActionBlue] ≈ 5.6:1,
 * [Warning] ≈ 8:1.
 */
object Palette {
    val Surround = Color(0xFF0E0F12)      // the "nothing" the map fades into
    val Overlay = Color(0xFF26272B)       // chips, card, zoom: opaque so text never sits on the map
    val OverlayBorder = Color(0x33FFFFFF)
    val SheetRow = Color(0xFF2E3035)
    val Text = Color(0xFFF2F2F7)
    val TextMuted = Color(0xFFB8BCC6)
    val Accent = Color(0xFF8AB4FF)        // text buttons, highlights
    val ActionBlue = Color(0xFF1E5FD9)    // filled button
    val Warning = Color(0xFFFFB340)
    val StartGreen = Color(0xFF34C759)
    val DestinationRed = Color(0xFFFF3B30)
}

val OverlayBorderStroke = BorderStroke(1.dp, Palette.OverlayBorder)

@Composable
fun BeFareTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Palette.ActionBlue,
            onPrimary = Color.White,
            secondary = Palette.Accent,
            background = Palette.Surround,
            onBackground = Palette.Text,
            surface = Palette.Overlay,
            onSurface = Palette.Text,
            onSurfaceVariant = Palette.TextMuted,
            surfaceContainerLow = Palette.Overlay,
            surfaceContainer = Palette.Overlay,
            surfaceContainerHigh = Palette.SheetRow,
            surfaceContainerHighest = Palette.SheetRow,
            outlineVariant = Palette.OverlayBorder,
        ),
        content = content,
    )
}
