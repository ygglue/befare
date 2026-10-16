package app.befare.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.befare.core.EstimateService
import app.befare.core.FareQuote
import app.befare.core.formatPeso
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val StartGreen = Color(0xFF34C759)
val DestinationRed = Color(0xFFFF3B30)
private val CardColor = Color(0xF21C1C1E)
private val ChipColor = Color(0xE62C2C2E)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF0A84FF), onPrimary = Color.White)) {
                BeFareApp()
            }
        }
    }
}

@Composable
fun BeFareApp() {
    val context = LocalContext.current
    val state = remember { AppState() }
    val mapController = remember { MapController() }
    var showVerify by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val (data, service) = withContext(Dispatchers.IO) {
            val data = OfflineData.install(context.applicationContext)
            data to EstimateService.fromFiles(data.graph.path, data.fares.path, data.coverage.path)
        }
        state.loaded(data, service)
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF0E0F12))) {
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
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            PinPicker(state)
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.End) {
                ZoomButtons(mapController)
            }
            Card(state, onVerify = { showVerify = true })
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

// ---- Top: which pin a tap places -----------------------------------------------------------------

@Composable
private fun PinPicker(state: AppState) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PinChip(state, AppState.Pin.START, "Start", Icons.Person, StartGreen, state.start != null)
        PinChip(state, AppState.Pin.DESTINATION, "Destination", Icons.Flag, DestinationRed, state.destination != null)
        Spacer(Modifier.weight(1f))
        if (state.start != null && state.destination != null) RoundIcon(Icons.Swap, "Swap") { state.swap() }
        if (state.start != null || state.destination != null) RoundIcon(Icons.Close, "Clear") { state.reset() }
    }
}

@Composable
private fun PinChip(state: AppState, pin: AppState.Pin, title: String, icon: ImageVector, color: Color, set: Boolean) {
    val active = state.placing == pin
    Surface(
        onClick = { state.placing = pin },
        shape = CircleShape,
        color = ChipColor,
        border = if (active) BorderStroke(2.dp, color) else null,
    ) {
        Row(
            Modifier.padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(24.dp).background(color, CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(15.dp), tint = Color.White)
            }
            Spacer(Modifier.width(6.dp))
            Text(title, fontSize = 15.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
            if (set) {
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Check, "set", Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun RoundIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = ChipColor, modifier = Modifier.size(40.dp)) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, label, Modifier.size(20.dp)) }
    }
}

@Composable
private fun ZoomButtons(controller: MapController) {
    Surface(shape = RoundedCornerShape(12.dp), color = ChipColor) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(onClick = { controller.zoomBy(1.0) }) { Icon(Icons.Add, "Zoom in") }
            HorizontalDivider(Modifier.width(28.dp))
            IconButton(onClick = { controller.zoomBy(-1.0) }) { Icon(Icons.Remove, "Zoom out") }
        }
    }
}

// ---- Bottom card -----------------------------------------------------------------------------------

@Composable
private fun Card(state: AppState, onVerify: () -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = CardColor, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            when (val o = state.outcome) {
                null -> {
                    Text("BeFare", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        when {
                            state.service == null -> "Loading Puerto Princesa roads…"
                            state.placing == AppState.Pin.START -> "Tap the map to set where you're starting."
                            else -> "Now tap where you're going."
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp,
                    )
                }
                is AppState.Outcome.Message -> Text("⚠ ${o.text}", color = Color(0xFFFF9F0A), fontSize = 15.sp)
                is AppState.Outcome.RouteOnly -> Text(o.message, fontSize = 15.sp)
                is AppState.Outcome.Quote -> QuoteView(state, o.quote, onVerify)
            }
        }
    }
}

@Composable
private fun QuoteView(state: AppState, q: FareQuote, onVerify: () -> Unit) {
    Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text("Tricycle fare", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatPeso(q.fareCentavos), fontSize = 40.sp, fontWeight = FontWeight.Bold)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("%.1f km".format(q.distanceM / 1000), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text("charged as ${q.chargedKm} km", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    val discounted = q.discountedCentavos
    val label = q.discountLabel
    if (discounted != null && label != null) {
        Spacer(Modifier.height(8.dp))
        Text(buildAnnotatedString {
            append("$label: ")
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(formatPeso(discounted)) }
        }, fontSize = 15.sp)
    }
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        BandMenu(state, q)
        Spacer(Modifier.weight(1f))
        Button(onClick = onVerify) {
            Icon(Icons.Verified, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Verify", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun BandMenu(state: AppState, q: FareQuote) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) {
            Icon(Icons.Fuel, null, Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(q.band.label)
            Icon(Icons.Expand, "Gas price band", Modifier.size(18.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            state.bands.forEach { band ->
                DropdownMenuItem(
                    text = { Text(band.label, fontWeight = if (band.id == state.bandId) FontWeight.Bold else FontWeight.Normal) },
                    onClick = { state.selectBand(band.id); open = false },
                )
            }
        }
    }
}
