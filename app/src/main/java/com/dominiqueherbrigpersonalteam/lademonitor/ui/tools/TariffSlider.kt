package com.dominiqueherbrigpersonalteam.lademonitor.ui.tools

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Zweigeteilte Spur: links von [at] in [below], rechts davon in [above]. */
data class SliderSplit(val at: Double, val below: Color, val above: Color)

/**
 * Schieberegler des Tarifrechners - Gegenstueck zu `TariffSlider.swift`.
 *
 * Eigener Regler statt Material-`Slider`, weil der Rechner zwei Dinge braucht, die der nicht kann:
 * eine Markierung fuer den Vorschlagswert (damit man nach dem Verschieben sieht, wo man herkam)
 * und eine zweigeteilte Spur (km-Regler: links vom Break-even rot, rechts gruen). Gezogen wird
 * erst nach horizontalem Touch-Slop, damit ein senkrechtes Scrollen ueber den Regler hinweg den
 * Wert nicht verstellt; ein Tipp setzt ihn direkt.
 */
@Composable
fun TariffSlider(
    value: Double,
    onValueChange: (Double) -> Unit,
    range: ClosedFloatingPointRange<Double>,
    step: Double,
    label: String,
    stateText: String,
    modifier: Modifier = Modifier,
    marker: Double? = null,
    split: SliderSplit? = null
) {
    val currentValue by rememberUpdatedState(value)
    val currentOnChange by rememberUpdatedState(onValueChange)
    val primary = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val markerColor = MaterialTheme.colorScheme.onSurfaceVariant
    val outline = MaterialTheme.colorScheme.outlineVariant
    val span = range.endInclusive - range.start

    fun snapped(v: Double): Double =
        (((v - range.start) / step).roundToInt() * step + range.start).coerceIn(range.start, range.endInclusive)

    /** Ein von Hand eingetippter Wert darf ausserhalb liegen - der Regler steht dann am Rand. */
    fun fraction(v: Double): Float =
        if (span <= 0) 0f else ((v.coerceIn(range.start, range.endInclusive) - range.start) / span).toFloat()

    fun valueAt(x: Float, width: Float, radius: Float): Double {
        val usable = (width - 2 * radius).coerceAtLeast(1f)
        val f = ((x - radius) / usable).coerceIn(0f, 1f)
        return snapped(range.start + f * span)
    }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(32.dp)
            .pointerInput(range, step) {
                val radius = 13.dp.toPx()
                detectTapGestures { offset ->
                    val new = valueAt(offset.x, size.width.toFloat(), radius)
                    if (new != currentValue) currentOnChange(new)
                }
            }
            .pointerInput(range, step) {
                val radius = 13.dp.toPx()
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    val new = valueAt(change.position.x, size.width.toFloat(), radius)
                    if (new != currentValue) currentOnChange(new)
                }
            }
            .semantics {
                contentDescription = label
                stateDescription = stateText
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = value.coerceIn(range.start, range.endInclusive).toFloat(),
                    range = range.start.toFloat()..range.endInclusive.toFloat(),
                    steps = ((span / step).roundToInt() - 1).coerceAtLeast(0)
                )
                setProgress { target ->
                    currentOnChange(snapped(target.toDouble()))
                    true
                }
            }
    ) {
        val radius = 13.dp.toPx()
        val trackHeight = 6.dp.toPx()
        val usable = (size.width - 2 * radius).coerceAtLeast(1f)
        val cy = size.height / 2
        fun x(v: Double) = radius + fraction(v) * usable
        val trackTopLeft = Offset(radius, cy - trackHeight / 2)
        val trackSize = Size(usable, trackHeight)
        val corner = CornerRadius(trackHeight / 2)

        if (split != null) {
            drawRoundRect(split.below, trackTopLeft, trackSize, corner)
            clipRect(left = x(split.at)) { drawRoundRect(split.above, trackTopLeft, trackSize, corner) }
        } else {
            drawRoundRect(trackColor, trackTopLeft, trackSize, corner)
            clipRect(right = x(value)) { drawRoundRect(primary, trackTopLeft, trackSize, corner) }
        }
        if (marker != null) {
            val mx = x(marker)
            drawLine(
                markerColor, Offset(mx, cy - 8.dp.toPx()), Offset(mx, cy + 8.dp.toPx()),
                strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round
            )
        }
        val thumb = Offset(x(value), cy)
        drawCircle(Color.Black.copy(alpha = 0.18f), radius, thumb.copy(y = cy + 1.dp.toPx()))
        drawCircle(Color.White, radius, thumb)
        drawCircle(outline, radius, thumb, style = Stroke(width = 1.dp.toPx()))
    }
}
