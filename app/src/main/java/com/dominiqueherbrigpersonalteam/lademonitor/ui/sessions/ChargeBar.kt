package com.dominiqueherbrigpersonalteam.lademonitor.ui.sessions

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dominiqueherbrigpersonalteam.lademonitor.R

/** "Elektrisches" Gruen fuer den geladenen Bereich - einmal definiert fuer Liste und Vorschau. */
val ChargeBarGreen = Color(0xFF00D26A)

private val ChargeBarMarkerRed = Color(0xFFFF3B30)
// Dezentes Grau fuer die Spur, je nach Hell/Dunkel (entspricht systemGray4 auf iOS).
private val ChargeBarTrackLight = Color(0xFFD1D1D6)
private val ChargeBarTrackDark = Color(0xFF3A3A3C)

/**
 * Duenner Balken 0-100 %: graue Spur, der geladene Bereich [socStart]..[socEnd]
 * in Gruen, dazu optional zwei rote Markierungen ([lowerMarker], [upperMarker]).
 * Alle Werte werden auf 0..100 begrenzt.
 */
@Composable
fun ChargeBar(
    socStart: Int,
    socEnd: Int,
    lowerMarker: Int?,
    upperMarker: Int?,
    modifier: Modifier = Modifier
) {
    val start = socStart.coerceIn(0, 100)
    val end = socEnd.coerceIn(0, 100).coerceAtLeast(start)
    // Hell/Dunkel an der tatsaechlichen Flaeche ablesen statt am System,
    // damit es auch bei einem erzwungenen Theme stimmt.
    val track = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) ChargeBarTrackDark else ChargeBarTrackLight
    val markers = listOfNotNull(lowerMarker, upperMarker).map { it.coerceIn(0, 100) }.distinct()
    val description = stringResource(R.string.charge_bar_content_description, start, end)

    Canvas(
        modifier
            .fillMaxWidth()
            .height(10.dp)
            .semantics { contentDescription = description }
    ) {
        val barHeight = 6.dp.toPx()
        val markerWidth = 2.dp.toPx()
        val width = size.width
        val barTop = (size.height - barHeight) / 2
        val radius = CornerRadius(barHeight / 2, barHeight / 2)

        drawRoundRect(track, topLeft = Offset(0f, barTop), size = Size(width, barHeight), cornerRadius = radius)
        val fillStart = width * start / 100f
        val fillWidth = width * (end - start) / 100f
        if (fillWidth > 0f) {
            drawRoundRect(
                ChargeBarGreen,
                topLeft = Offset(fillStart, barTop),
                size = Size(fillWidth, barHeight),
                cornerRadius = radius
            )
        }
        for (value in markers) {
            // Auf [0, Breite - Markierungsbreite] begrenzt, damit eine Markierung
            // bei 0 oder 100 % nicht abgeschnitten wird.
            val left = (width * value / 100f - markerWidth / 2)
                .coerceIn(0f, (width - markerWidth).coerceAtLeast(0f))
            drawRect(ChargeBarMarkerRed, topLeft = Offset(left, 0f), size = Size(markerWidth, size.height))
        }
    }
}
