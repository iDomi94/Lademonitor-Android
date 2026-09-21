package com.dominiqueherbrigpersonalteam.lademonitor.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dominiqueherbrigpersonalteam.lademonitor.ui.common.Fmt
import com.dominiqueherbrigpersonalteam.lademonitor.ui.theme.Blue
import com.dominiqueherbrigpersonalteam.lademonitor.ui.theme.Green
import com.dominiqueherbrigpersonalteam.lademonitor.ui.theme.Orange
import com.dominiqueherbrigpersonalteam.lademonitor.ui.theme.Teal
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min

val ChartPalette = listOf(
    Blue, Orange, Green, Color(0xFFAF52DE), Color(0xFFFF3B30),
    Teal, Color(0xFFFF2D55), Color(0xFF5856D6)
)

data class PieEntry(val name: String, val value: Double, val color: Color)

/** Interactive donut chart: tap a segment to highlight it and show name + value in the centre. */
@Composable
fun DonutChart(
    entries: List<PieEntry>,
    unit: String,
    modifier: Modifier = Modifier
) {
    val total = entries.sumOf { it.value }.takeIf { it > 0 } ?: 1.0
    var selected by remember { mutableStateOf<String?>(null) }

    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .aspectRatio(1f)
                .pointerInput(entries) {
                    detectTapGestures { tap ->
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val dx = tap.x - center.x
                        val dy = tap.y - center.y
                        val r = hypot(dx, dy)
                        val outer = min(size.width, size.height) / 2f
                        if (r > outer || r < outer * 0.35f) { selected = null; return@detectTapGestures }
                        // Angle measured clockwise from 12 o'clock.
                        var angle = Math.toDegrees(atan2(dx.toDouble(), -dy.toDouble()))
                        if (angle < 0) angle += 360.0
                        val fraction = angle / 360.0 * total
                        var cumulative = 0.0
                        var hit: String? = entries.lastOrNull()?.name
                        for (e in entries) {
                            cumulative += e.value
                            if (fraction < cumulative) { hit = e.name; break }
                        }
                        selected = if (hit == selected) null else hit
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
                val diameter = min(size.width, size.height)
                val stroke = diameter * 0.15f
                val arcSize = Size(diameter - stroke, diameter - stroke)
                val topLeft = Offset(
                    (size.width - (diameter - stroke)) / 2f,
                    (size.height - (diameter - stroke)) / 2f
                )
                var startAngle = -90f
                entries.forEach { e ->
                    val sweep = (e.value / total * 360.0).toFloat()
                    val dim = selected != null && selected != e.name
                    drawArc(
                        color = if (dim) e.color.copy(alpha = 0.4f) else e.color,
                        startAngle = startAngle + 1f,
                        sweepAngle = (sweep - 2f).coerceAtLeast(0f),
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke)
                    )
                    startAngle += sweep
                }
            }
            selected?.let { name ->
                val value = entries.firstOrNull { it.name == name }?.value ?: 0.0
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Text(
                        formatValue(value, unit),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        PieLegend(entries)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PieLegend(entries: List<PieEntry>) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        entries.forEach { e ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(e.color))
                Spacer(Modifier.width(4.dp))
                Text(e.name, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private fun formatValue(v: Double, unit: String): String =
    if (unit == "€") Fmt.n("%.2f €", v) else Fmt.n("%.1f kWh", v)

/** Vertical bar chart (monthly consumption), tinted teal, matching the iOS chart. */
@Composable
fun VerticalBarChart(points: List<Pair<String, Double>>, modifier: Modifier = Modifier) {
    val maxValue = (points.maxOfOrNull { it.second } ?: 0.0).coerceAtLeast(0.0001)
    val barColor = Teal
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(160.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            points.forEach { (_, value) ->
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Text(Fmt.n("%.1f", value), style = MaterialTheme.typography.labelSmall)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height((120 * (value / maxValue)).dp.coerceAtLeast(2.dp))
                            .clip(RoundedCornerShape(4.dp))
                            .background(barColor)
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            points.forEach { (label, _) ->
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

/** Horizontal bar rows (monthly cost / kWh), matching the iOS `MonthlyBarChart`. */
@Composable
fun HorizontalBarChart(
    data: List<Pair<String, Double>>,
    color: Color,
    unit: String,
    modifier: Modifier = Modifier,
    /**
     * Nachkommastellen der Beschriftung. Kosten/kWh pro Monat lesen sich ohne (Vorgabe), beim
     * Verbrauch je Jahreszeit ginge der ganze Unterschied verloren (15,4 und 17,9 waeren
     * beide "16").
     */
    decimals: Int = 0
) {
    val maxValue = (data.maxOfOrNull { it.second } ?: 0.0).coerceAtLeast(0.0001)
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        data.forEach { (label, value) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.width(92.dp)
                )
                Box(
                    Modifier
                        .weight(1f)
                        .height(20.dp)
                        .padding(horizontal = 4.dp)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth((value / maxValue).toFloat().coerceIn(0.02f, 1f))
                            .height(20.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(color)
                    )
                }
                Text(
                    Fmt.n("%.${decimals}f", value) + unit,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.width(64.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End
                )
            }
        }
    }
}

/** AC/DC split bar with legend, matching the iOS `AcDcBar`. */
@Composable
fun AcDcBar(acKwh: Double, dcKwh: Double, modifier: Modifier = Modifier) {
    val total = (acKwh + dcKwh).coerceAtLeast(0.0001)
    val acFraction = (acKwh / total).toFloat()
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().height(24.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (acFraction > 0f) {
                Box(
                    Modifier.weight(acFraction).fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp)).background(Blue)
                )
            }
            if (acFraction < 1f) {
                Box(
                    Modifier.weight(1f - acFraction).fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp)).background(Orange)
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(Blue))
                Spacer(Modifier.width(6.dp))
                Text(Fmt.n("AC  %.0f%%", acFraction * 100.0) + Fmt.n(" · %.1f kWh", acKwh),
                    style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Fmt.n("DC  %.0f%%", (1 - acFraction) * 100.0) + Fmt.n(" · %.1f kWh", dcKwh),
                    style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.width(6.dp))
                Box(Modifier.size(10.dp).clip(CircleShape).background(Orange))
            }
        }
    }
}

// ---------- Verbrauch nach Aussentemperatur ----------

/**
 * Dieselben drei Farbtoene wie im Web-Dashboard (`#3d8ee0`/`#2ea87f`/`#bd8a26`): je eine Stufe
 * dunkler als die uebrigen Akzentfarben, weil ein Feld aus ueber hundert Punkten in den hellen
 * Toenen blendet.
 */
val TempPointColor = Color(0xFF3D8EE0)
val TempBucketColor = Color(0xFF2EA87F)
val TempTrendColor = Color(0xFFBD8A26)

/**
 * Streudiagramm Verbrauch ueber Aussentemperatur: ein Punkt je Fahrt (die Streuung), das Mittel
 * je Temperaturklasse als Linie (das, was man ablesen soll) und die Ausgleichsgerade gestrichelt
 * (die Zusammenfassung) — die drei Marken des Web-Dashboards.
 *
 * Die Achsenbeschriftung liegt bewusst als normale [Text]-Elemente um die Zeichenflaeche herum
 * statt im Canvas: Text im Canvas braeuchte den nativen Paint-Umweg und wuerde die Schriftgroesse
 * des Systems nicht mitnehmen.
 */
@Composable
fun TemperatureScatterChart(
    points: List<Pair<Double, Double>>,
    buckets: List<Pair<Double, Double>>,
    trendLine: List<Pair<Double, Double>>,
    modifier: Modifier = Modifier
) {
    if (points.isEmpty()) return
    val xs = points.map { it.first } + buckets.map { it.first } + trendLine.map { it.first }
    val ys = points.map { it.second } + buckets.map { it.second } + trendLine.map { it.second }
    val minX = xs.min()
    val maxX = xs.max()
    // Die y-Achse startet bei 0: ein abgeschnittener Nullpunkt vergroessert den Unterschied
    // zwischen den Klassen kuenstlich (gleiche Ueberlegung wie bei den Jahreszeiten-Balken).
    val maxY = ys.max()
    val spanX = (maxX - minX).takeIf { it > 0.0001 } ?: 1.0
    val spanY = maxY.takeIf { it > 0.0001 } ?: 1.0
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height(200.dp)) {
            Column(
                modifier = Modifier.width(52.dp).fillMaxHeight(),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                AxisLabel(Fmt.n("%.0f", maxY))
                AxisLabel("0")
            }
            Canvas(Modifier.weight(1f).fillMaxHeight().padding(start = 6.dp)) {
                fun px(x: Double) = (((x - minX) / spanX) * size.width).toFloat()
                fun py(y: Double) = (size.height - (y / spanY) * size.height).toFloat()

                // Grundlinie
                drawLine(
                    gridColor,
                    Offset(0f, size.height),
                    Offset(size.width, size.height),
                    strokeWidth = 1f
                )
                // 0-Grad-Marke, sofern im Bild - macht den Winter ablesbar.
                if (minX < 0 && maxX > 0) {
                    val zero = px(0.0)
                    drawLine(gridColor, Offset(zero, 0f), Offset(zero, size.height), strokeWidth = 1f)
                }
                points.forEach { (x, y) ->
                    drawCircle(TempPointColor.copy(alpha = 0.6f), radius = 4f, center = Offset(px(x), py(y)))
                }
                if (trendLine.size >= 2) {
                    drawLine(
                        TempTrendColor,
                        Offset(px(trendLine.first().first), py(trendLine.first().second)),
                        Offset(px(trendLine.last().first), py(trendLine.last().second)),
                        strokeWidth = 4f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
                    )
                }
                buckets.zipWithNext().forEach { (a, b) ->
                    drawLine(
                        TempBucketColor,
                        Offset(px(a.first), py(a.second)),
                        Offset(px(b.first), py(b.second)),
                        strokeWidth = 5f
                    )
                }
                buckets.forEach { (x, y) ->
                    drawCircle(TempBucketColor, radius = 7f, center = Offset(px(x), py(y)))
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 58.dp, top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            AxisLabel(Fmt.n("%.0f °C", minX))
            AxisLabel(Fmt.n("%.0f °C", maxX))
        }
    }
}

@Composable
private fun AxisLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** Farbpunkt + Beschriftung, wie in der Legende der Kuchendiagramme. */
@Composable
fun ChartLegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(5.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
