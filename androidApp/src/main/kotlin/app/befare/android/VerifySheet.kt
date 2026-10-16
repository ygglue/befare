package app.befare.android

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.befare.core.FareQuote
import app.befare.core.formatPeso
import java.io.File

/** How the estimate was calculated and the official source behind it. Mirrors iosApp/BeFare/VerifyView.swift. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerifySheet(quote: FareQuote, isCurrentBandSet: Boolean, sourceImage: File?, onDismiss: () -> Unit) {
    val image = remember(sourceImage) { sourceImage?.let { BitmapFactory.decodeFile(it.path)?.asImageBitmap() } }
    var showSource by remember { mutableStateOf(false) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            item { Text("Verify estimate", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp)) }

            item { SectionTitle("How this fare was calculated") }
            item {
                Section {
                    quote.lines.forEachIndexed { i, line ->
                        if (i > 0) HorizontalDivider()
                        Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.Top) {
                            Column(Modifier.weight(1f)) {
                                Text(line.label)
                                Text(line.detail, fontSize = 12.sp, color = muted)
                            }
                            line.amountCentavos?.let { Text(formatPeso(it), fontWeight = FontWeight.SemiBold) }
                        }
                    }
                }
            }

            item { SectionTitle("Matrix row: ${quote.chargedKm} km") }
            item {
                Section {
                    quote.row.forEachIndexed { i, cell ->
                        if (i > 0) HorizontalDivider()
                        val current = cell.bandId == quote.band.id
                        Row(
                            Modifier.fillMaxWidth()
                                .background(if (current) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent)
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                        ) {
                            Text(cell.label, Modifier.weight(1f), fontWeight = if (current) FontWeight.Bold else FontWeight.Normal)
                            Text(formatPeso(cell.centavos), fontWeight = if (current) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
                Text(
                    if (isCurrentBandSet) "The highlighted band is the one in use."
                    else "The fare depends on today's gas price. Agree on the band with your driver.",
                    fontSize = 12.sp, color = muted, modifier = Modifier.padding(top = 6.dp),
                )
            }

            item { SectionTitle("Source") }
            item {
                Section {
                    Field("Issued by", quote.source.issuer)
                    HorizontalDivider()
                    Field("Document", quote.source.document)
                    HorizontalDivider()
                    Field("Effective", quote.source.effectiveDate ?: "Not confirmed")
                    HorizontalDivider()
                    Field("Fare data version", quote.dataVersion)
                    if (image != null) {
                        Spacer(Modifier.height(8.dp))
                        Image(
                            image, "Fare matrix, City Ordinance No. 1185",
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { showSource = true },
                            contentScale = ContentScale.FillWidth,
                        )
                        Text("Tap to open", fontSize = 12.sp, color = muted, modifier = Modifier.padding(vertical = 6.dp))
                    }
                }
            }

            item { SectionTitle("Assumptions") }
            item {
                Section {
                    quote.assumptions.forEachIndexed { i, text ->
                        if (i > 0) HorizontalDivider()
                        Text("ⓘ  $text", fontSize = 14.sp, modifier = Modifier.padding(vertical = 10.dp))
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showSource && image != null) {
        Dialog(onDismissRequest = { showSource = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            var scale by remember { mutableFloatStateOf(1f) }
            var offset by remember { mutableStateOf(Offset.Zero) }
            val transform = rememberTransformableState { zoom, pan, _ ->
                scale = (scale * zoom).coerceIn(1f, 5f)
                offset += pan
            }
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                Image(
                    image, "Fare matrix, City Ordinance No. 1185",
                    Modifier.fillMaxSize().transformable(transform).graphicsLayer {
                        scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y
                    },
                    contentScale = ContentScale.Fit,
                )
                Column(Modifier.align(Alignment.TopCenter).padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Look for the ${quote.chargedKm} km row", fontWeight = FontWeight.SemiBold)
                }
                TextButton(onClick = { showSource = false }, Modifier.align(Alignment.TopEnd).padding(top = 36.dp, end = 8.dp)) {
                    Text("Done")
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
}

@Composable
private fun Section(content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp), verticalArrangement = Arrangement.Top) { content() }
    }
}

@Composable
private fun Field(label: String, value: String) {
    Row(Modifier.padding(vertical = 10.dp)) {
        Text(label, Modifier.width(150.dp).padding(end = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(1f))
    }
}
