package app.befare.android

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.befare.core.EstimateService
import app.befare.core.FareQuote
import app.befare.core.LatLng
import androidx.core.content.ContextCompat
import app.befare.core.formatPeso
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent { BeFareTheme { BeFareApp() } }
    }
}

@Composable
fun BeFareApp() {
    val context = LocalContext.current
    val state: AppState = viewModel()
    val mapController = remember { MapController() }
    var showVerify by remember { mutableStateOf(false) }
    var cardTopPx by remember { mutableIntStateOf(0) }

    // ---- GPS (offline) ----
    val locationPermissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    fun granted() = locationPermissions.any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    var hasLocationPermission by remember { mutableStateOf(granted()) }
    var permissionDenied by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        hasLocationPermission = result.values.any { it }
        permissionDenied = !hasLocationPermission
    }
    LaunchedEffect(Unit) { if (!hasLocationPermission) permissionLauncher.launch(locationPermissions) }
    val tracker = remember { LocationTracker(context.applicationContext) }
    DisposableEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            tracker.start { fix ->
                state.locationUpdated(LatLng(fix.latitude, fix.longitude))
                mapController.showLocation(fix)
            }
        }
        onDispose { tracker.stop() }
    }
    val onLocate: () -> Unit = {
        val here = state.myLocation
        when {
            here == null && !hasLocationPermission && !permissionDenied -> permissionLauncher.launch(locationPermissions)
            here == null -> state.showLocationUnavailable(permissionDenied)
            !state.isInCoverage(here) -> state.useMyLocationAsStart() // explains; doesn't fly into the empty surround
            else -> {
                mapController.center(here)
                if (state.start == null) state.useMyLocationAsStart()
            }
        }
        Unit
    }

    LaunchedEffect(Unit) {
        if (state.service != null) return@LaunchedEffect
        val (data, service) = withContext(Dispatchers.IO) {
            val data = OfflineData.install(context.applicationContext)
            data to EstimateService.fromFiles(data.graph.path, data.fares.path, data.coverage.path)
        }
        state.loaded(data, service)
    }

    // Surface (not Box) so everything inside inherits light content colour.
    Surface(Modifier.fillMaxSize(), color = Palette.Surround, contentColor = Palette.Text) {
        Box(Modifier.fillMaxSize().onGloballyPositioned { mapController.screenHeightPx = it.size.height }) {
            state.offline?.let { offline ->
                BeFareMap(
                    offline = offline,
                    start = state.start,
                    destination = state.destination,
                    route = state.route,
                    controller = mapController,
                    onTap = state::place,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Column(
                Modifier.fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp)
            ) {
                PinBar(state)
                Spacer(Modifier.weight(1f))
                Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.End) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        LocateButton(hasFix = state.myLocation != null, startSet = state.start != null, onClick = onLocate)
                        ZoomButtons(mapController)
                    }
                }
                Card(
                    state,
                    onVerify = { showVerify = true },
                    // Keep MapLibre's logo and attribution visible above the card.
                    modifier = Modifier.onGloballyPositioned { coords ->
                        val top = coords.localToRoot(androidx.compose.ui.geometry.Offset.Zero).y.toInt()
                        if (top != cardTopPx) {
                            cardTopPx = top
                            mapController.keepOrnamentsAbove(top)
                        }
                    },
                )
            }

            val outcome = state.outcome
            if (showVerify && outcome is AppState.Outcome.Quote) {
                VerifySheet(
                    quote = outcome.quote,
                    isCurrentBandSet = state.isCurrentBandSet,
                    sourceImage = state.offline?.sourceImage,
                    onDismiss = { showVerify = false },
                )
            }
        }
    }
}

// ---- Top: which pin a tap places, swap, clear (same layout as iOS) -----------------------------------

/**
 * Start and Destination chips with Swap and Clear on the right. Visual sizes match iOS; every control
 * still has a 48dp touch area. With large text, Swap and Clear wrap to the next line instead of
 * squeezing the chips.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PinBar(state: AppState) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        PinChip(state, AppState.Pin.START, "Start", Icons.Person, Palette.StartGreen, state.start != null)
        PinChip(state, AppState.Pin.DESTINATION, "Destination", Icons.Flag, Palette.DestinationRed, state.destination != null)
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
            if (state.start != null && state.destination != null) RoundIcon(Icons.Swap, "Swap start and destination") { state.swap() }
            if (state.start != null || state.destination != null) RoundIcon(Icons.Close, "Clear pins") { state.reset() }
        }
    }
}

/** A radio-style chip: which pin the next map tap places. 40dp tall, 48dp touch area. */
@Composable
private fun PinChip(state: AppState, pin: AppState.Pin, title: String, icon: ImageVector, color: Color, set: Boolean) {
    val active = state.placing == pin
    Box(
        Modifier
            .heightIn(min = 48.dp)
            .selectable(selected = active, role = Role.RadioButton, onClick = { state.placing = pin })
            .semantics { contentDescription = "$title pin. " + if (set) "Placed" else "Not placed" },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = Palette.Overlay,
            border = if (active) androidx.compose.foundation.BorderStroke(2.dp, color) else OverlayBorderStroke,
            shadowElevation = 4.dp,
            modifier = Modifier.height(40.dp),
        ) {
            Row(Modifier.padding(start = 7.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(24.dp).background(color, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(icon, null, Modifier.size(15.dp), tint = Color.White)
                }
                Spacer(Modifier.width(6.dp))
                Text(title, fontSize = 15.sp, maxLines = 1, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
                if (set) {
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Check, null, Modifier.size(15.dp))
                }
            }
        }
    }
}

/** A 40dp round button with a 48dp touch area. */
@Composable
private fun RoundIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clickable(onClickLabel = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Surface(shape = CircleShape, color = Palette.Overlay, border = OverlayBorderStroke, shadowElevation = 4.dp, modifier = Modifier.size(40.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(20.dp)) }
        }
    }
}

/** Centres on the user's GPS position and uses it as the start if none is set. */
@Composable
private fun LocateButton(hasFix: Boolean, startSet: Boolean, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(14.dp), color = Palette.Overlay, border = OverlayBorderStroke, shadowElevation = 4.dp) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(52.dp).semantics {
                onClick(label = if (startSet) "centre the map on you" else "centre the map on you and set your start") { onClick(); true }
            },
        ) {
            Icon(Icons.MyLocation, "My location", tint = if (hasFix) Palette.Accent else Palette.Text)
        }
    }
}

@Composable
private fun ZoomButtons(controller: MapController) {
    Surface(shape = RoundedCornerShape(14.dp), color = Palette.Overlay, border = OverlayBorderStroke, shadowElevation = 4.dp) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(onClick = { controller.zoomBy(1.0) }, Modifier.size(52.dp)) { Icon(Icons.Add, "Zoom in") }
            HorizontalDivider(Modifier.width(32.dp), color = Palette.OverlayBorder)
            IconButton(onClick = { controller.zoomBy(-1.0) }, Modifier.size(52.dp)) { Icon(Icons.Remove, "Zoom out") }
        }
    }
}

// ---- Bottom card -----------------------------------------------------------------------------------

@Composable
private fun Card(state: AppState, onVerify: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(24.dp), color = Palette.Overlay, border = OverlayBorderStroke,
        shadowElevation = 8.dp,
        // Announce fares and errors as they appear.
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
            when (val o = state.outcome) {
                null -> Hint(state)
                is AppState.Outcome.Message -> Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Warning, "Warning", Modifier.size(22.dp), tint = Palette.Warning)
                    Spacer(Modifier.width(10.dp))
                    Text(o.text, color = Palette.Warning, fontSize = 15.sp, lineHeight = 21.sp)
                }
                is AppState.Outcome.RouteOnly -> Text(o.message, fontSize = 15.sp, lineHeight = 21.sp)
                is AppState.Outcome.Quote -> QuoteView(state, o.quote, onVerify)
            }
        }
    }
}

@Composable
private fun Hint(state: AppState) {
    Text("BeFare", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (state.service == null) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Palette.Accent)
            Spacer(Modifier.width(10.dp))
        }
        Text(
            when {
                state.service == null -> "Loading Puerto Princesa roads…"
                state.placing == AppState.Pin.START -> "Tap the map to set where you're starting."
                else -> "Now tap where you're going."
            },
            color = Palette.TextMuted, fontSize = 15.sp, lineHeight = 21.sp,
        )
    }
}

@Composable
private fun QuoteView(state: AppState, q: FareQuote, onVerify: () -> Unit) {
    val km = "%.1f".format(q.distanceM / 1000)
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.clearAndSetSemantics {
            contentDescription = "Tricycle fare ${formatPeso(q.fareCentavos)}. $km kilometres, charged as ${q.chargedKm}."
        },
    ) {
        Column(Modifier.weight(1f)) {
            Text("Tricycle fare", fontSize = 13.sp, color = Palette.TextMuted)
            Text(formatPeso(q.fareCentavos), fontSize = 40.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold)
        }
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(top = 2.dp)) {
            Text("$km km", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text("charged as ${q.chargedKm} km", fontSize = 13.sp, color = Palette.TextMuted)
        }
    }
    val discounted = q.discountedCentavos
    val label = q.discountLabel
    if (discounted != null && label != null) {
        Spacer(Modifier.height(10.dp))
        Text(buildAnnotatedString {
            withStyle(SpanStyle(color = Palette.TextMuted)) { append("$label  ") }
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(formatPeso(discounted)) }
        }, fontSize = 15.sp, lineHeight = 21.sp)
    }
    Spacer(Modifier.height(14.dp))
    HorizontalDivider(color = Palette.OverlayBorder)
    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        BandMenu(state, q, Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Button(onClick = onVerify, modifier = Modifier.heightIn(min = 48.dp)) {
            Icon(Icons.Verified, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Verify", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        }
    }
}

@Composable
private fun BandMenu(state: AppState, q: FareQuote, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        TextButton(
            onClick = { open = true },
            contentPadding = PaddingValues(start = 0.dp, end = 8.dp),
            modifier = Modifier.heightIn(min = 48.dp).semantics(mergeDescendants = true) {
                contentDescription = "Gas price band: ${q.band.label}"
                onClick(label = "change gas price band") { open = true; true }
            },
        ) {
            Icon(Icons.Fuel, null, Modifier.size(18.dp), tint = Palette.Accent)
            Spacer(Modifier.width(6.dp))
            Text(q.band.label, color = Palette.Accent, fontSize = 15.sp, maxLines = 1)
            Icon(Icons.Expand, null, Modifier.size(20.dp), tint = Palette.Accent)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = Palette.SheetRow) {
            state.bands.forEach { band ->
                val selected = band.id == state.bandId
                DropdownMenuItem(
                    text = { Text(band.label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
                    leadingIcon = { Box(Modifier.size(20.dp)) { if (selected) Icon(Icons.Check, null, tint = Palette.Accent) } },
                    onClick = { state.selectBand(band.id); open = false },
                    modifier = Modifier.semantics { stateDescription = if (selected) "Selected" else "" },
                )
            }
        }
    }
}
