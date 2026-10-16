package app.befare.android

import android.content.Context
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.befare.core.LatLng
import java.net.URI
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.fillAntialias
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.fillOpacity
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconAnchor
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.android.geometry.LatLng as MapLatLng

/** Lets Compose controls (zoom buttons) drive the map. */
class MapController {
    internal var map: MapLibreMap? = null
        set(value) {
            field = value
            applyOrnamentMargins()
        }
    var screenHeightPx = 0
    private var ornamentsAbovePx = 0

    fun zoomBy(delta: Double) {
        map?.animateCamera(CameraUpdateFactory.zoomBy(delta), 250)
    }

    /** Moves MapLibre's logo and attribution button so they sit just above [topPx] (the card's top edge). */
    fun keepOrnamentsAbove(topPx: Int) {
        ornamentsAbovePx = topPx
        applyOrnamentMargins()
    }

    private fun applyOrnamentMargins() {
        val map = map ?: return
        if (screenHeightPx == 0 || ornamentsAbovePx == 0) return
        val dp = android.content.res.Resources.getSystem().displayMetrics.density
        val bottom = screenHeightPx - ornamentsAbovePx + (8 * dp).toInt()
        val left = (16 * dp).toInt()
        map.uiSettings.setLogoMargins(left, 0, 0, bottom)
        map.uiSettings.setAttributionMargins(left + (96 * dp).toInt(), 0, 0, bottom) // just right of the logo
    }
}

/**
 * MapLibre map with the coverage fade, route line and the two pins. Fully offline: tiles, style,
 * fonts, sprites and fade mask come from [OfflineData]. Mirrors iosApp/BeFare/MapView.swift.
 */
@Composable
fun BeFareMap(
    offline: OfflineData,
    start: LatLng?,
    destination: LatLng?,
    route: List<LatLng>,
    controller: MapController,
    onTap: (LatLng) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val holder = remember { MapHolder() }
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).also { view ->
            view.onCreate(Bundle())
            view.getMapAsync { map -> holder.setUp(context, map, offline, controller) }
        }
    }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            controller.map = null
            mapView.onDestroy()
        }
    }

    holder.onTap = onTap
    AndroidView(
        factory = { mapView.apply { setBackgroundColor(offline.maskColor) } },
        modifier = modifier,
        update = { holder.update(start, destination, route) },
    )
}

private class MapHolder {
    var onTap: (LatLng) -> Unit = {}
    private var routeSource: GeoJsonSource? = null
    private var startSource: GeoJsonSource? = null
    private var endSource: GeoJsonSource? = null
    private var pending: Triple<LatLng?, LatLng?, List<LatLng>>? = null

    fun setUp(context: Context, map: MapLibreMap, offline: OfflineData, controller: MapController) {
        controller.map = map
        map.uiSettings.isRotateGesturesEnabled = false
        map.uiSettings.isTiltGesturesEnabled = false
        map.setMinZoomPreference(11.0)
        map.setMaxZoomPreference(18.0)
        // Keep the camera centred over the covered area; the tiles extend past it.
        map.setLatLngBoundsForCameraTarget(LatLngBounds.from(9.870, 118.790, 9.718, 118.675))
        map.cameraPosition = CameraPosition.Builder().target(MapLatLng(9.77, 118.74)).zoom(12.3).build()
        map.addOnMapClickListener { p ->
            onTap(LatLng(p.latitude, p.longitude))
            true
        }

        map.setStyle(Style.Builder().fromUri(offline.styleUri)) { style ->
            // Fade to nothing outside coverage: stacked masks with per-mask opacity (pipeline/build_mask.py).
            style.addSource(GeoJsonSource("coverage-mask", URI("file://" + offline.mask.absolutePath)))
            style.addLayer(
                FillLayer("coverage-mask", "coverage-mask").withProperties(
                    fillColor(offline.maskColor),
                    fillOpacity(Expression.get("opacity")),
                    fillAntialias(false),
                )
            )

            val route = GeoJsonSource("route")
            val start = GeoJsonSource("pin-start")
            val end = GeoJsonSource("pin-end")
            listOf(route, start, end).forEach(style::addSource)

            style.addLayer(
                LineLayer("route-casing", "route").withProperties(
                    lineColor(android.graphics.Color.WHITE), lineWidth(9f),
                    lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
                )
            )
            style.addLayer(
                LineLayer("route", "route").withProperties(
                    lineColor(0xFF0A84FF.toInt()), lineWidth(5f),
                    lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
                )
            )

            val density = context.resources.displayMetrics.density
            for ((source, marker) in listOf(start to Icons.Marker.START, end to Icons.Marker.DESTINATION)) {
                style.addImage(marker.imageName, Icons.markerBitmap(marker, density))
                style.addLayer(
                    SymbolLayer(source.id, source.id).withProperties(
                        iconImage(marker.imageName),
                        iconAnchor(Property.ICON_ANCHOR_BOTTOM), // tail tip = the location
                        iconAllowOverlap(true),
                        iconIgnorePlacement(true),
                    )
                )
            }

            routeSource = route
            startSource = start
            endSource = end
            pending?.let { (s, d, r) -> update(s, d, r) }
        }
    }

    fun update(start: LatLng?, destination: LatLng?, route: List<LatLng>) {
        if (routeSource == null) {
            pending = Triple(start, destination, route)
            return
        }
        routeSource?.setGeoJson(
            features(if (route.size > 1) Feature.fromGeometry(LineString.fromLngLats(route.map(::point))) else null)
        )
        startSource?.setGeoJson(features(start?.let { Feature.fromGeometry(point(it)) }))
        endSource?.setGeoJson(features(destination?.let { Feature.fromGeometry(point(it)) }))
    }

    private fun features(f: Feature?): FeatureCollection = FeatureCollection.fromFeatures(listOfNotNull(f))

    private fun point(p: LatLng) = Point.fromLngLat(p.lon, p.lat)
}
