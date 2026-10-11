package com.dominiqueherbrigpersonalteam.lademonitor.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dominiqueherbrigpersonalteam.lademonitor.LademonitorApp
import com.dominiqueherbrigpersonalteam.lademonitor.R
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ChargingPlace
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.CostCategory
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.PlaceStat
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.RangeEstimate
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.StatsSummary
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.TemperatureStats
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.VehicleCostSummary
import com.dominiqueherbrigpersonalteam.lademonitor.data.remote.ApiClient
import com.dominiqueherbrigpersonalteam.lademonitor.data.repo.AppRepository
import com.dominiqueherbrigpersonalteam.lademonitor.data.repo.LocalDataStore
import com.dominiqueherbrigpersonalteam.lademonitor.data.repo.LocalReminderCalculator
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ReminderStatus
import com.dominiqueherbrigpersonalteam.lademonitor.ui.settings.ReminderStatusChip
import com.dominiqueherbrigpersonalteam.lademonitor.ui.settings.reminderDueLines
import com.dominiqueherbrigpersonalteam.lademonitor.data.repo.WidgetSnapshotWriter
import com.dominiqueherbrigpersonalteam.lademonitor.data.settings.AppMode
import com.dominiqueherbrigpersonalteam.lademonitor.data.settings.AppSettings
import com.dominiqueherbrigpersonalteam.lademonitor.ui.common.ErrorState
import com.dominiqueherbrigpersonalteam.lademonitor.ui.common.Fmt
import com.dominiqueherbrigpersonalteam.lademonitor.ui.common.SectionCard
import com.dominiqueherbrigpersonalteam.lademonitor.ui.filter.FilterIconButton
import com.dominiqueherbrigpersonalteam.lademonitor.ui.filter.FilterSheet
import com.dominiqueherbrigpersonalteam.lademonitor.ui.filter.SessionFilter
import com.dominiqueherbrigpersonalteam.lademonitor.ui.theme.Blue
import com.dominiqueherbrigpersonalteam.lademonitor.ui.theme.Green
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(onOpenReminders: (String) -> Unit = {}, onOpenRangeCalculator: (String?) -> Unit = {}) {
    val scope = rememberCoroutineScope()
    val dateRange by SessionFilter.dateRange.collectAsStateWithLifecycle()

    var stats by remember { mutableStateOf<StatsSummary?>(null) }
    // Nur im Server-Modus gefuellt — siehe TemperatureSection.
    var temperature by remember { mutableStateOf<TemperatureStats?>(null) }
    // Bald faellige/ueberfaellige Erinnerungen aller Fahrzeuge - bewusst ohne Zeitraumfilter.
    var dueReminders by remember { mutableStateOf<List<LocalReminderCalculator.Evaluated>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var showFilter by remember { mutableStateOf(false) }
    val serverUrlRequiredMessage = stringResource(R.string.error_server_url_required)

    suspend fun load() {
        if (!AppSettings.isReadyForDataAccess) {
            errorMessage = serverUrlRequiredMessage
            return
        }
        isLoading = true
        try {
            stats = AppRepository.fetchStatsSummary(dateRange = dateRange)
            errorMessage = null
            WidgetSnapshotWriter.refresh()
        } catch (e: Exception) {
            if (stats == null) errorMessage = e.localizedMessage
        }
        // Lokal gerechnet (der Sync lief gerade schon); ein Fehler hier blendet nur die Karte aus.
        dueReminders = try {
            LocalDataStore.evaluatedReminders().filter {
                it.state.status == ReminderStatus.DUE_SOON || it.state.status == ReminderStatus.OVERDUE
            }
        } catch (e: Exception) {
            emptyList()
        }
        // Temperaturauswertung ausschliesslich im Server-Modus. Scheitert der Abruf (aelterer
        // Server ohne den Endpunkt, Netzfehler), bleibt der Abschnitt einfach weg: er ist eine
        // Ergaenzung, kein Grund, das ganze Dashboard als fehlgeschlagen zu melden.
        temperature = if (AppSettings.appMode.value == AppMode.SERVER) {
            try {
                val zone = ZoneId.systemDefault()
                ApiClient.fetchTemperatureStats(
                    startDate = dateRange?.first?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() },
                    endDate = dateRange?.last?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
                )
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
        isLoading = false
    }

    LaunchedEffect(dateRange) { load() }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.tab_dashboard)) },
            actions = {
                IconButton(onClick = { scope.launch { load() } }) {
                    Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.action_refresh))
                }
                FilterIconButton(onClick = { showFilter = true })
            }
        )
    }) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            val current = stats
            when {
                errorMessage != null && current == null ->
                    ErrorState(errorMessage!!, onRetry = { scope.launch { load() } })
                current != null -> DashboardContent(current, temperature, dueReminders, onOpenReminders, onOpenRangeCalculator)
                isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }

    if (showFilter) FilterSheet(onDismiss = { showFilter = false })
}

@Composable
private fun DashboardContent(
    stats: StatsSummary,
    temperature: TemperatureStats?,
    dueReminders: List<LocalReminderCalculator.Evaluated>,
    onOpenReminders: (String) -> Unit,
    onOpenRangeCalculator: (String?) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Ganz oben und nur, wenn etwas bald faellig oder ueberfaellig ist.
        if (dueReminders.isNotEmpty()) {
            Column {
                SectionHeader(stringResource(R.string.dashboard_section_reminders))
                SectionCard { DueRemindersSection(dueReminders, onOpenReminders) }
            }
        }

        // Stat grid
        val cards = buildList {
            add(stringResource(R.string.dashboard_stat_sessions) to "${stats.totalSessions}")
            add(stringResource(R.string.dashboard_stat_total_kwh) to Fmt.n("%.1f kWh", stats.totalKwh))
            add(stringResource(R.string.dashboard_stat_total_cost) to Fmt.n("%.2f €", stats.totalCost))
            add(stringResource(R.string.dashboard_stat_avg_price) to (stats.avgPricePerKwh?.let { Fmt.n("%.3f €", it) } ?: "–"))
            add(stringResource(R.string.dashboard_stat_avg_consumption) to (stats.avgConsumptionKwhPer100km?.let { Fmt.n("%.1f kWh", it) } ?: "–"))
            add(stringResource(R.string.dashboard_stat_price_per_100km) to (stats.pricePer100km?.let { Fmt.n("%.2f €", it) } ?: "–"))
            add(stringResource(R.string.dashboard_stat_km_driven) to (stats.totalKmDriven?.let { Fmt.km(it) } ?: "–"))
        }
        // Kleine Zusatzzeile unter einzelnen Kennzahlen, z.B. der Grundgebuehren-Anteil.
        val totalCostLabel = stringResource(R.string.dashboard_stat_total_cost)
        val details = buildMap {
            stats.totalFees?.takeIf { it > 0 }?.let {
                put(totalCostLabel, stringResource(R.string.dashboard_total_cost_fees, Fmt.n("%.2f €", it)))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            cards.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { (label, value) ->
                        StatCard(label, value, Modifier.weight(1f), detail = details[label])
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        val acKwh = stats.acKwh
        val dcKwh = stats.dcKwh
        if (acKwh != null && dcKwh != null && acKwh + dcKwh > 0) {
            Column {
                SectionHeader(stringResource(R.string.dashboard_section_ac_dc))
                SectionCard { AcDcBar(acKwh, dcKwh) }
            }
        }

        // Leer, solange kein Vorgang zu Hause oder bei der Arbeit lag - dann fehlt der Abschnitt.
        if (stats.byPlace.isNotEmpty()) {
            Column {
                SectionHeader(stringResource(R.string.dashboard_section_by_place))
                SectionCard { PlaceSection(stats.byPlace) }
            }
        }

        // Nur, wenn eines der betrachteten Fahrzeuge Kosten oder eine Kauf-/Leasingangabe hat.
        // Die Kennzahlen oben bleiben reine Ladekosten.
        stats.vehicleCosts?.let { costs ->
            Column {
                SectionHeader(stringResource(R.string.dashboard_section_total_costs))
                SectionCard { VehicleCostsSection(costs) }
            }
        }

        if (stats.byProvider.isNotEmpty()) {
            val entries = providerEntries(stats)
            SectionCard {
                SectionHeader(stringResource(R.string.dashboard_section_kwh_by_provider))
                DonutChart(entries.map { PieEntry(it.name, it.kwh, it.color) }, unit = "kWh")
            }
            Spacer(Modifier.height(4.dp))
            SectionCard {
                SectionHeader(stringResource(R.string.dashboard_section_paid_by_provider))
                DonutChart(entries.map { PieEntry(it.name, it.cost, it.color) }, unit = "€")
            }
        }

        val consumptionPoints = stats.monthly
            .reversed()
            .mapNotNull { m -> m.avgConsumptionKwhPer100km?.let { m.shortMonth to it } }
        if (consumptionPoints.isNotEmpty()) {
            Column {
                SectionHeader(stringResource(R.string.dashboard_stat_avg_consumption))
                SectionCard { VerticalBarChart(consumptionPoints) }
            }
        }

        if (stats.monthly.isNotEmpty()) {
            Column {
                SectionHeader(stringResource(R.string.dashboard_section_cost_by_month))
                SectionCard {
                    HorizontalBarChart(stats.monthly.map { it.displayMonth to it.totalCost }, Blue, "€")
                }
            }
            Column {
                SectionHeader(stringResource(R.string.dashboard_section_kwh_by_month))
                SectionCard {
                    HorizontalBarChart(stats.monthly.map { it.displayMonth to it.totalKwh }, Green, " kWh")
                }
            }
        }

        if (temperature != null) TemperatureSection(temperature, onOpenRangeCalculator)
    }
}

/**
 * Verbrauch nach Aussentemperatur — Gegenstueck zum gleichnamigen Abschnitt des
 * Web-Dashboards und der iOS-App. Nur im Server-Modus sichtbar: die Auswertung kommt fertig
 * aus `/api/stats/temperature`, damit sie nicht ein drittes Mal nachgebaut werden muss
 * (siehe [TemperatureStats]).
 */
@Composable
private fun TemperatureSection(stats: TemperatureStats, onOpenRangeCalculator: (String?) -> Unit) {
    Column {
        SectionHeader(stringResource(R.string.dashboard_section_temperature))
        SectionCard {
            if (stats.points.isEmpty()) {
                Text(
                    stringResource(R.string.dashboard_temperature_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val trend = stats.trend
                if (trend != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            Fmt.n("%+.1f %%", trend.extraPctAt0c),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = TempTrendColor
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                stringResource(R.string.dashboard_temperature_hero_label),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            // r2 und der 0-Grad-Wert gehoeren zu dem Modell, das
                            // der Server benutzt hat - deshalb wird es hier auch
                            // benannt, sonst passte die gezeichnete Linie beim
                            // Knickmodell nicht mehr zu den Zahlen daneben.
                            val modelText = if (trend.model == "breakpoint" && trend.breakpointC != null) {
                                stringResource(
                                    R.string.dashboard_temperature_model_breakpoint,
                                    Fmt.n("%.0f", trend.breakpointC)
                                )
                            } else {
                                stringResource(R.string.dashboard_temperature_model_linear)
                            }
                            Text(
                                stringResource(
                                    R.string.dashboard_temperature_hero_detail,
                                    Fmt.n("%.1f", trend.consumptionAt0c),
                                    Fmt.n("%.1f", trend.consumptionAt20c),
                                    modelText,
                                    Fmt.n("%.2f", trend.r2)
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            // Wer erst im Fruehjahr angefangen hat zu messen,
                            // sieht hier eine Hochrechnung auf einen Winter, den
                            // es in den Daten gar nicht gibt.
                            if (trend.at0cIsExtrapolated) {
                                Text(
                                    stringResource(R.string.dashboard_temperature_hero_extrapolated),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                // Die Kurve wird nur ueber den Bereich gezeichnet, in dem es auch Messpunkte
                // gibt — bis 0 Grad verlaengert ohne Winterdaten waere sie eine Behauptung.
                //
                // Der Server liefert ab 0.25.0 `curve`: zwei Eckpunkte bei der Geraden, drei
                // beim Knickmodell. Ein aelterer Server kennt das Feld nicht — dann wird die
                // Gerade wie bisher aus slope/intercept gebaut.
                val temps = stats.points.map { it.tempC }
                val curve = trend?.curve
                val trendLine = when {
                    curve != null && curve.size > 1 -> curve.map { it.tempC to it.consumption }
                    trend != null && temps.isNotEmpty() && temps.min() < temps.max() ->
                        listOf(temps.min(), temps.max()).map { it to (trend.intercept + trend.slope * it) }
                    else -> emptyList()
                }
                TemperatureScatterChart(
                    points = stats.points.map { it.tempC to it.consumptionKwhPer100km },
                    buckets = stats.buckets.map { it.centerC to it.avgConsumptionKwhPer100km },
                    trendLine = trendLine
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    ChartLegendDot(TempPointColor, stringResource(R.string.dashboard_temperature_legend_drives))
                    ChartLegendDot(
                        TempBucketColor,
                        stringResource(R.string.dashboard_temperature_legend_buckets, stats.bucketWidthC)
                    )
                    if (trend != null) {
                        ChartLegendDot(TempTrendColor, stringResource(R.string.dashboard_temperature_legend_trend))
                    }
                }
                if (stats.sessionsWithoutTemp > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.dashboard_temperature_missing, stats.sessionsWithoutTemp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        stats.range?.let { range ->
            if (range.rows.any { it.rangeKm != null }) {
                Spacer(Modifier.height(20.dp))
                SectionHeader(stringResource(R.string.dashboard_section_range))
                SectionCard { RangeTile(range) }
                TextButton(onClick = { onOpenRangeCalculator(range.vehicleId) }) {
                    Text(stringResource(R.string.tools_range_title))
                }
            }
        }

        if (stats.seasons.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            SectionHeader(stringResource(R.string.dashboard_section_seasons))
            SectionCard {
                // Nullbasiert wie im Web: der Unterschied zwischen 15,4 und 17,9 ist klein,
                // eine abgeschnittene Achse wuerde ihn kuenstlich vergroessern.
                HorizontalBarChart(
                    stats.seasons.map { it.displayName to it.avgConsumptionKwhPer100km },
                    TempBucketColor,
                    " kWh",
                    decimals = 1
                )
            }
        }
    }
}

/**
 * Reale Reichweite 100 -> 0 % je Temperatur (Server ab 0.34.0, `range_estimate.py`) - ein Balken
 * je Zeile. Hochgerechnete Temperaturen (ausserhalb der gemessenen) tragen ein Sternchen, damit
 * eine Hochrechnung nicht wie eine Messung aussieht.
 */
@Composable
private fun RangeTile(range: RangeEstimate) {
    val rows = range.rows.filter { it.rangeKm != null }.sortedBy { it.tempC }
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalBarChart(
            rows.map { row ->
                (Fmt.n("%.0f", row.tempC) + " °C" + if (row.extrapolated) "*" else "") to (row.rangeKm ?: 0.0)
            },
            TempPointColor,
            " km"
        )
        if (rows.any { it.extrapolated }) {
            Text(stringResource(R.string.dashboard_range_extrapolated), style = MaterialTheme.typography.labelSmall, color = secondary)
        }
        val from = range.measuredFromC
        val to = range.measuredToC
        Text(
            if (from != null && to != null) {
                stringResource(
                    R.string.dashboard_range_basis,
                    Fmt.n("%.0f", range.capacityKwh), Fmt.n("%.0f", from), Fmt.n("%.0f", to)
                )
            } else {
                stringResource(R.string.dashboard_range_basis_no_span, Fmt.n("%.0f", range.capacityKwh))
            },
            style = MaterialTheme.typography.labelSmall,
            color = secondary
        )
        Text(
            range.lossPct?.let { stringResource(R.string.dashboard_range_loss, Fmt.n("%.1f", it)) }
                ?: stringResource(R.string.dashboard_range_loss_unknown),
            style = MaterialTheme.typography.labelSmall,
            color = secondary
        )
    }
}

private fun placeColor(place: ChargingPlace): Color = when (place) {
    ChargingPlace.HOME -> PlaceHomeColor
    ChargingPlace.WORK -> PlaceWorkColor
    ChargingPlace.PUBLIC -> PlacePublicColor
}

/**
 * Zuhause/Arbeit/unterwegs: ein nach kWh geteilter Balken, darunter je Ort Anzahl, kWh mit
 * Anteil, Kosten (inkl. Grundgebuehren) und - hervorgehoben - der Preis je kWh. Die Werte kommen
 * aus [com.dominiqueherbrigpersonalteam.lademonitor.data.repo.LocalStatsCalculator], gerechnet
 * wie `build_by_place()` auf dem Server.
 */
@Composable
private fun PlaceSection(places: List<PlaceStat>) {
    val totalKwh = places.sumOf { it.totalKwh }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ShareBar(places.map { it.totalKwh to placeColor(it.placeValue) })
        places.forEach { stat ->
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    Modifier.padding(top = 5.dp).size(10.dp).clip(CircleShape)
                        .background(placeColor(stat.placeValue))
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(stat.placeValue.labelRes), fontWeight = FontWeight.SemiBold)
                    Text(
                        pluralStringResource(R.plurals.dashboard_place_sessions, stat.sessionCount, stat.sessionCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    val share = if (totalKwh > 0) stat.totalKwh / totalKwh * 100 else 0.0
                    Text(
                        Fmt.n("%.1f kWh", stat.totalKwh) + Fmt.n(" · %.0f %%", share),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        Fmt.n("%.2f €", stat.totalCost),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    stat.avgPricePerKwh?.let {
                        Text(
                            stringResource(R.string.dashboard_place_price_per_kwh, Fmt.n("%.3f", it)),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

/**
 * Gesamtkosten im Zeitraum: Laden, sonstige Kosten und Wertverlust bzw. Leasing - gerechnet von
 * [com.dominiqueherbrigpersonalteam.lademonitor.data.repo.LocalVehicleCostCalculator] wie
 * `build_vehicle_costs()` auf dem Server. Ein Balken je Kategorie, groesster Posten zuerst, jede
 * Kategorie mit fester Farbe (dieselbe wie im Web), damit sie ueber Zeitraeume wiedererkennbar
 * bleibt.
 */
@Composable
private fun VehicleCostsSection(costs: VehicleCostSummary) {
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(Fmt.n("%.2f €", costs.totalCost), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.dashboard_total_costs_per_km), style = MaterialTheme.typography.labelSmall, color = secondary)
                Text(costs.costPerKm?.let { Fmt.n("%.2f €", it) } ?: "–", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.dashboard_total_costs_charging_share), style = MaterialTheme.typography.labelSmall, color = secondary)
                val share = if (costs.totalCost > 0) costs.chargingCost / costs.totalCost * 100 else null
                Text(share?.let { Fmt.n("%.0f %%", it) } ?: "–", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
        }
        val max = costs.byCategory.maxOfOrNull { it.amount } ?: 0.0
        costs.byCategory.sortedByDescending { it.amount }.forEach { stat ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(CostCategory.labelResFor(stat.category)), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(Fmt.n("%.2f €", stat.amount), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
                val fraction = if (max > 0) (stat.amount / max).toFloat().coerceIn(0f, 1f) else 0f
                Box(
                    Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    if (fraction > 0f) {
                        Box(
                            Modifier.fillMaxWidth(fraction).height(8.dp).clip(RoundedCornerShape(4.dp))
                                .background(costCategoryColor(stat.category))
                        )
                    }
                }
            }
        }
        Text(stringResource(R.string.dashboard_total_costs_hint), style = MaterialTheme.typography.bodySmall, color = secondary)
    }
}

/** Die bald faelligen und ueberfaelligen Erinnerungen; ein Tipp oeffnet die Liste des Fahrzeugs. */
@Composable
private fun DueRemindersSection(items: List<LocalReminderCalculator.Evaluated>, onOpen: (String) -> Unit) {
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEach { item ->
            Column(
                Modifier.fillMaxWidth().clickable { onOpen(item.reminder.vehicleId) }.padding(vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        listOfNotNull(item.reminder.displayTitle, item.vehicleName).joinToString(" · "),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    ReminderStatusChip(item.state.status)
                }
                reminderDueLines(item).forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = secondary) }
            }
        }
    }
}

/** Feste Farbe je Kostenkategorie, wie im Web-Dashboard. */
private fun costCategoryColor(category: String): Color = when (category) {
    "charging" -> Color(0xFF5B8DEF)
    "depreciation", "leasing" -> Color(0xFFA78BFA)
    "insurance" -> Color(0xFF4FD1A5)
    "tax" -> Color(0xFFF2B84B)
    "maintenance" -> Color(0xFFF08A5D)
    "repair" -> Color(0xFFE5534B)
    "tires" -> Color(0xFF8B98A9)
    "wallbox" -> Color(0xFF2EA87F)
    "parking" -> Color(0xFFD48AC8)
    "toll" -> Color(0xFFC9A227)
    "cleaning" -> Color(0xFF6CC4E0)
    else -> Color(0xFF6B7280)
}

private data class ProviderEntry(val name: String, val kwh: Double, val cost: Double, val color: androidx.compose.ui.graphics.Color)

/** Port of the iOS provider grouping: top 5 named providers + "Other" (overflow + "no provider"). */
private fun providerEntries(stats: StatsSummary): List<ProviderEntry> {
    val context = LademonitorApp.appContext
    // The "no provider" placeholder may come back from a German-only server (untranslated) or
    // from the local calculator (already localized), so match either form.
    val noProviderLabel = context.getString(R.string.stats_no_provider)
    fun isNoProvider(name: String) = name == noProviderLabel || name == "Ohne Anbieter"

    val named = stats.byProvider.filterNot { isNoProvider(it.providerName) }
    val noName = stats.byProvider.filter { isNoProvider(it.providerName) }
    val top = named.take(5)
    val overflow = named.drop(5)
    val otherItems = overflow + noName

    val result = top.mapIndexed { i, p ->
        ProviderEntry(p.providerName, p.totalKwh, p.totalCost, ChartPalette[i % ChartPalette.size])
    }.toMutableList()

    if (otherItems.isNotEmpty()) {
        result.add(
            ProviderEntry(
                context.getString(R.string.dashboard_provider_other),
                otherItems.sumOf { it.totalKwh },
                otherItems.sumOf { it.totalCost },
                androidx.compose.ui.graphics.Color.Gray
            )
        )
    }
    return result
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier, detail: String? = null) {
    SectionCard(modifier = modifier) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        detail?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
