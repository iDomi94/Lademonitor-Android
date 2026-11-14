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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.unit.dp
import com.dominiqueherbrigpersonalteam.lademonitor.R
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ActiveCharging
import com.dominiqueherbrigpersonalteam.lademonitor.data.remote.ApiClient
import com.dominiqueherbrigpersonalteam.lademonitor.data.settings.AppMode
import com.dominiqueherbrigpersonalteam.lademonitor.data.settings.AppSettings
import com.dominiqueherbrigpersonalteam.lademonitor.data.settings.ChargeBarSettings
import com.dominiqueherbrigpersonalteam.lademonitor.data.settings.ServerCapabilities
import com.dominiqueherbrigpersonalteam.lademonitor.data.settings.ServerFeature
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
 * Aufbau wie eine Zeile der Ladevorgangsliste (`SessionRow`): links Kopfzeile, Fahrzeug/Anbieter/
 * Ort, SoC, Ladebalken mit den roten Markierungen aus den Ladebalken-Einstellungen; rechts kWh,
 * Gesamtpreis und Preis je kWh - mit "~" als Schaetzung markiert. Nicht antippbar - der Vorgang
 * ist noch kein Datensatz, es gibt nichts zu bearbeiten.
 */
@Composable
fun ActiveChargingCard(item: ActiveCharging, modifier: Modifier = Modifier) {
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    val chargeBarConfig by ChargeBarSettings.config.collectAsStateWithLifecycle()
    // Beobachtet, damit die Sperre nach dem ersten Sync verschwindet.
    val serverVersion by ServerCapabilities.serverVersion.collectAsStateWithLifecycle()
    val priceLocked = remember(serverVersion) { !ServerCapabilities.supports(ServerFeature.ACTIVE_CHARGING_PRICE) }
    val sourceLabel = when (item.source) {
        "myskoda" -> "MyŠkoda"
        "vw_data_act" -> stringResource(R.string.active_source_vw_data_act)
        "renault" -> "Renault"
        "home_assistant" -> "Home Assistant"
        else -> item.source
    }
    val subtitle = listOfNotNull(item.vehicleName, item.providerName, item.locationName).joinToString(" · ")
    val details = buildList {
        val start = item.socStart
        val now = item.socCurrent
        when {
            start != null && now != null -> add(stringResource(R.string.session_soc_range, start, now))
            start != null -> add(stringResource(R.string.session_soc_from, start))
            now != null -> add(stringResource(R.string.session_soc_to, now))
        }
        item.odometerKm?.let { add(Fmt.km(it)) }
        item.maxPowerKw?.takeIf { it > 0 }?.let { add(stringResource(R.string.active_max_power, Fmt.n("%.1f", it))) }
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
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).alpha(pulse).clip(CircleShape).background(Green))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.active_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = Green,
                        fontWeight = FontWeight.Bold
                    )
                    item.startTime?.let {
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.active_since, whenText(it)), style = MaterialTheme.typography.titleSmall)
                    }
                    item.chargingTypeValue?.let {
                        Spacer(Modifier.width(6.dp))
                        ChargingTypeBadge(it)
                    }
                }
                if (subtitle.isNotEmpty()) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = secondary)
                }
                if (details.isNotEmpty()) {
                    Text(details, style = MaterialTheme.typography.labelSmall, color = secondary)
                }
                val start = item.socStart
                val now = item.socCurrent
                if (start != null && now != null && now >= start) {
                    ChargeBar(
                        socStart = start,
                        socEnd = now,
                        lowerMarker = chargeBarConfig.lower,
                        upperMarker = chargeBarConfig.upper,
                        modifier = Modifier.padding(vertical = 3.dp)
                    )
                }
                Text(meta, style = MaterialTheme.typography.labelSmall, color = secondary)
            }
            Column(horizontalAlignment = Alignment.End) {
                item.energyKwh?.let {
                    Text((if (item.energyIsEstimated) "~" else "") + Fmt.n("%.1f kWh", it), style = MaterialTheme.typography.bodyMedium)
                }
                if (priceLocked) {
                    Text(
                        stringResource(R.string.server_feature_requires, ServerFeature.ACTIVE_CHARGING_PRICE.minimumServerVersion),
                        style = MaterialTheme.typography.labelSmall,
                        color = secondary,
                        textAlign = TextAlign.End,
                        modifier = Modifier.widthIn(max = 120.dp)
                    )
                } else {
                    item.priceTotal?.let { Text("~" + Fmt.n("%.2f €", it), style = MaterialTheme.typography.labelSmall, color = secondary) }
                    item.pricePerKwh?.let { Text("~" + Fmt.n("%.3f €/kWh", it), style = MaterialTheme.typography.labelSmall, color = secondary) }
                    if (item.pricePerKwh != null) {
                        Text(
                            stringResource(if (item.priceIsAverage) R.string.active_price_average else R.string.active_price_estimated),
                            style = MaterialTheme.typography.labelSmall,
                            color = secondary
                        )
                    }
                }
            }
        }
    }
}
