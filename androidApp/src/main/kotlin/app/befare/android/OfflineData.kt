package app.befare.android

import android.content.Context
import java.io.File
import org.json.JSONObject

/**
 * The bundled offline data (see androidApp/build.gradle.kts: prepareOfflineAssets), copied out of the
 * APK into app storage so MapLibre and the shared core can read it as ordinary files.
 */
class OfflineData private constructor(val dir: File) {
    val graph get() = File(dir, "graph.bin")
    val fares get() = File(dir, "fares.json")
    val coverage get() = File(dir, "coverage-smooth.geojson")
    val mask get() = File(dir, "mask.geojson")
    val sourceImage get() = File(dir, "pp-city-tricycle-ord1185-2021.jpg")

    /** The style with its {TILES}/{ASSETS} placeholders filled in with file URLs. */
    val styleUri: String
        get() {
            val out = File(dir, "befare-style.resolved.json")
            if (!out.exists()) {
                val template = File(dir, "style/befare-style.json").readText()
                out.writeText(
                    template
                        .replace("{TILES}", "file://" + File(dir, "befare.pmtiles").absolutePath)
                        .replace("{ASSETS}", "file://" + File(dir, "style").absolutePath)
                )
            }
            return "file://" + out.absolutePath
        }

    /** The colour the map fades into, written by pipeline/build_mask.py. */
    val maskColor: Int by lazy {
        runCatching {
            val hex = JSONObject(mask.readText()).getJSONObject("properties").getString("color")
            android.graphics.Color.parseColor(hex)
        }.getOrDefault(android.graphics.Color.BLACK)
    }

    companion object {
        private const val ASSET_ROOT = "offline"

        /** Copies the assets once per installed app version. Call off the main thread. */
        fun install(context: Context): OfflineData {
            @Suppress("DEPRECATION")
            val version = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
            val dir = File(context.filesDir, "offline-$version")
            val done = File(dir, ".complete")
            if (!done.exists()) {
                context.filesDir.listFiles { f -> f.name.startsWith("offline-") }?.forEach { it.deleteRecursively() }
                copyAssetDir(context, ASSET_ROOT, dir)
                done.createNewFile()
            }
            return OfflineData(dir)
        }

        private fun copyAssetDir(context: Context, path: String, target: File) {
            val children = context.assets.list(path).orEmpty()
            if (children.isEmpty()) {
                target.parentFile?.mkdirs()
                context.assets.open(path).use { input -> target.outputStream().use { input.copyTo(it) } }
            } else {
                target.mkdirs()
                children.forEach { copyAssetDir(context, "$path/$it", File(target, it)) }
            }
        }
    }
}
