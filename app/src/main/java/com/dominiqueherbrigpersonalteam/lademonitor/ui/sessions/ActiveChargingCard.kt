package com.dominiqueherbrigpersonalteam.lademonitor.ui.sessions

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dominiqueherbrigpersonalteam.lademonitor.R
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ActiveCharging
import com.dominiqueherbrigpersonalteam.lademonitor.data.remote.ApiClient
import com.dominiqueherbrigpersonalteam.lademonitor.data.settings.AppMode
import com.dominiqueherbrigpersonalteam.lademonitor.data.settings.AppSettings
import com.dominiqueherbrigpersonalteam.lademonitor.ui.common.Fmt
import com.dominiqueherbrigpersonalteam.lademonitor.ui.theme.Green
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Laedt die laufenden Ladevorgaenge - nur im Server-Modus. Ein Fehler (Server aelter als 0.45.0,
 * Netz weg) liefert einfach eine leere Liste: die Anzeige ist eine Ergaenzung, kein Grund,
 * Dashboard oder Liste scheitern zu lassen.
 */
object ActiveChargingLoader {
    suspend fun load(): List<ActiveCharging> {
        if (AppSettings.appMode.value != AppMode.SERVER) return emptyList()
        return runCatching { ApiClient.fetchActiveSessions() }.getOrDefault(emptyList())
    }
}

private fun whenText(epochMillis: Long): String {
    val zone = ZoneId.systemDefault()
    val time = Instant.ofEpochMilli(epochMillis).atZone(zone)
    return if (time.toLocalDate() == LocalDate.now(zone)) {
        time.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault()))
    } else {
        Fmt.dateTimeShort(epochMillis)
    }
}

/**
 * "Lädt gerade": ganz oben im Dashboard und als markierter Eintrag ueber der Ladevorgangsliste.
 * Nicht antippbar - der Vorgang ist noch kein Datensatz, es gibt nichts zu bearbeiten.
 */
@Composable
fun ActiveChargingCard(item: ActiveCharging, modifier: Modifier = Modifier) {
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    val sourceLabel = when (item.source) {
        "myskoda" -> "MyŠkoda"
        "vw_data_act" -> stringResource(R.string.active_source_vw_data_act)
        "renault" -> "Renault"
        "home_assistant" -> "Home Assistant"
        else -> item.source
    }
    val details = buildList {
        item.startTime?.let { add(stringResource(R.string.active_since, whenText(it))) }
        val start = item.socStart
        val now = item.socCurrent
        when {
            start != null && now != null && now != start -> add("$start % → $now %")
            start != null -> add("$start %")
            now != null -> add("→ $now %")
        }
        item.energyKwh?.let { add((if (item.energyIsEstimated) "~" else "") + Fmt.n("%.1f kWh", it)) }
        item.maxPowerKw?.takeIf { it > 0 }?.let { add(stringResource(R.string.active_max_power, Fmt.n("%.1f", it))) }
        item.odometerKm?.let { add(Fmt.km(it)) }
        item.outsideTempC?.let { add(Fmt.temperature(it)) }
    }.joinToString(" · ")
    val meta = listOfNotNull(
        sourceLabel,
        item.updatedAt?.let { stringResource(R.string.active_as_of, whenText(it)) }
    ).joinToString(" · ")

    val pulse by rememberInfiniteTransition(label = "active-pulse").animateFloat(
        initialValue = 1f, targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "active-dot"
    )

    Row(
        modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(12.dp))
            .background(Green.copy(alpha = 0.08f))
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(Green))
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).alpha(pulse).clip(CircleShape).background(Green))
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(R.string.active_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = Green,
                    fontWeight = FontWeight.Bold
                )
                item.vehicleName?.let {
                    Spacer(Modifier.width(8.dp))
                    Text(it, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
                item.chargingTypeValue?.let {
                    Spacer(Modifier.width(6.dp))
                    ChargingTypeBadge(it)
                }
            }
            if (details.isNotEmpty()) {
                Text(details, style = MaterialTheme.typography.bodyMedium)
            }
            val start = item.socStart
            val now = item.socCurrent
            if (start != null && now != null && now >= start) {
                ChargeBar(socStart = start, socEnd = now, lowerMarker = null, upperMarker = null,
                    modifier = Modifier.padding(vertical = 2.dp))
            }
            Text(meta, style = MaterialTheme.typography.bodySmall, color = secondary)
        }
    }
}
