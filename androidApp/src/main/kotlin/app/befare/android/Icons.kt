package app.befare.android

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import androidx.core.graphics.PathParser as AndroidPathParser

/** Material icon paths (24×24 viewport), so the app needs no icon library. */
object Icons {
    private const val PERSON = "M12,12c2.21,0 4,-1.79 4,-4s-1.79,-4 -4,-4 -4,1.79 -4,4 1.79,4 4,4zM12,14c-2.67,0 -8,1.34 -8,4v2h16v-2c0,-2.66 -5.33,-4 -8,-4z"
    private const val FLAG = "M14.4,6L14,4H5v17h2v-7h5.6l0.4,2h7V6z"
    private const val ADD = "M19,13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z"
    private const val REMOVE = "M19,13H5v-2h14v2z"
    private const val CLOSE = "M19,6.41L17.59,5 12,10.59 6.41,5 5,6.41 10.59,12 5,17.59 6.41,19 12,13.41 17.59,19 19,17.59 13.41,12z"
    private const val SWAP = "M16,17.01V10h-2v7.01h-3L15,21l4,-3.99h-3zM9,3L5,6.99h3V14h2V6.99h3L9,3z"
    private const val CHECK = "M9,16.17L4.83,12l-1.42,1.41L9,19 21,7l-1.41,-1.41z"
    private const val VERIFIED = "M23,12l-2.44,-2.79l0.34,-3.69l-3.61,-0.82L15.4,1.5L12,2.96L8.6,1.5L6.71,4.69L3.1,5.5L3.44,9.2L1,12l2.44,2.79l-0.34,3.7l3.61,0.82L8.6,22.5l3.4,-1.47l3.4,1.46l1.89,-3.19l3.61,-0.82l-0.34,-3.69L23,12zM10.09,16.72l-3.8,-3.81l1.48,-1.48l2.32,2.33l5.85,-5.87l1.48,1.48L10.09,16.72z"
    private const val FUEL = "M19.77,7.23l0.01,-0.01 -3.72,-3.72L15,4.56l2.11,2.11c-0.94,0.36 -1.61,1.26 -1.61,2.33 0,1.38 1.12,2.5 2.5,2.5 0.36,0 0.69,-0.08 1,-0.21v7.21c0,0.55 -0.45,1 -1,1s-1,-0.45 -1,-1V14c0,-1.1 -0.9,-2 -2,-2h-1V5c0,-1.1 -0.9,-2 -2,-2H6c-1.1,0 -2,0.9 -2,2v16h10v-7.5h1.5v5c0,1.38 1.12,2.5 2.5,2.5s2.5,-1.12 2.5,-2.5V9c0,-0.69 -0.28,-1.32 -0.73,-1.77zM12,10H6V5h6v5zM18,10c-0.55,0 -1,-0.45 -1,-1s0.45,-1 1,-1 1,0.45 1,1 -0.45,1 -1,1z"
    private const val EXPAND = "M7,10l5,5 5,-5z"

    val Person = vector("person", PERSON)
    val Flag = vector("flag", FLAG)
    val Add = vector("add", ADD)
    val Remove = vector("remove", REMOVE)
    val Close = vector("close", CLOSE)
    val Swap = vector("swap", SWAP)
    val Check = vector("check", CHECK)
    val Verified = vector("verified", VERIFIED)
    val Fuel = vector("fuel", FUEL)
    val Expand = vector("expand", EXPAND)

    private fun vector(name: String, path: String) = ImageVector.Builder(
        name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f,
    ).addPath(PathParser().parsePathString(path).toNodes(), fill = SolidColor(Color.White)).build()

    // ---- Map pins -------------------------------------------------------------------------------

    enum class Marker(val path: String, val color: Int, val imageName: String) {
        START(PERSON, 0xFF34C759.toInt(), "marker-start"),
        DESTINATION(FLAG, 0xFFFF3B30.toInt(), "marker-destination"),
    }

    /**
     * A map pin: a coloured circle with a white icon and a small tail. The tail tip is the exact
     * location, so the map layer anchors it at the bottom. Matches iosApp/BeFare/Markers.swift.
     */
    fun markerBitmap(marker: Marker, density: Float): Bitmap {
        val d = 38f * density
        val tail = 9f * density
        val stroke = 2.5f * density
        val w = d + stroke * 2
        val h = d + tail + stroke * 2
        val bitmap = Bitmap.createBitmap(w.toInt(), h.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val circle = RectF(stroke, stroke, stroke + d, stroke + d)

        val outline = Path().apply {
            addOval(circle, Path.Direction.CW)
            moveTo(circle.centerX() - 8 * density, circle.bottom - 5 * density)
            lineTo(w / 2, h - stroke)
            lineTo(circle.centerX() + 8 * density, circle.bottom - 5 * density)
            close()
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = android.graphics.Color.WHITE
        paint.setShadowLayer(3 * density, 0f, 1 * density, 0x80000000.toInt())
        canvas.drawPath(outline, paint)
        paint.clearShadowLayer()

        paint.color = marker.color
        val inner = RectF(circle).apply { inset(stroke, stroke) }
        canvas.drawOval(inner, paint)

        val icon = AndroidPathParser.createPathFromPathData(marker.path)
        val size = 20f * density
        icon.transform(Matrix().apply {
            setScale(size / 24f, size / 24f)
            postTranslate(circle.centerX() - size / 2, circle.centerY() - size / 2)
        })
        paint.color = android.graphics.Color.WHITE
        canvas.drawPath(icon, paint)
        return bitmap
    }
}
