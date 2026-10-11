package com.dominiqueherbrigpersonalteam.lademonitor.ui.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.dominiqueherbrigpersonalteam.lademonitor.R
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ChargingPlace
import com.dominiqueherbrigpersonalteam.lademonitor.data.repo.AppRepository
import com.dominiqueherbrigpersonalteam.lademonitor.data.repo.CombustionComparison
import com.dominiqueherbrigpersonalteam.lademonitor.data.repo.ComparisonVehicleStore
import com.dominiqueherbrigpersonalteam.lademonitor.data.repo.LocalReviewCalculator
import com.dominiqueherbrigpersonalteam.lademonitor.data.repo.LocalReviewCalculator.Period
import com.dominiqueherbrigpersonalteam.lademonitor.data.repo.ReviewBasis
import com.dominiqueherbrigpersonalteam.lademonitor.data.settings.AppSettings
import com.dominiqueherbrigpersonalteam.lademonitor.ui.common.Fmt
import com.dominiqueherbrigpersonalteam.lademonitor.ui.common.SectionCard
import com.dominiqueherbrigpersonalteam.lademonitor.ui.dashboard.AcDcBar
import com.dominiqueherbrigpersonalteam.lademonitor.ui.dashboard.HorizontalBarChart
import com.dominiqueherbrigpersonalteam.lademonitor.ui.dashboard.PlaceHomeColor
import com.dominiqueherbrigpersonalteam.lademonitor.ui.dashboard.PlacePublicColor
import com.dominiqueherbrigpersonalteam.lademonitor.ui.dashboard.PlaceWorkColor
import com.dominiqueherbrigpersonalteam.lademonitor.ui.dashboard.ShareBar
import com.dominiqueherbrigpersonalteam.lademonitor.ui.theme.Green
import com.dominiqueherbrigpersonalteam.lademonitor.ui.theme.Teal
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

private val ReviewRed = Color(0xFFFF3B30)

private fun money(v: Double) = Fmt.n("%,.2f", v) + " €"

private fun monthName(month: Int, style: TextStyle = TextStyle.FULL_STANDALONE): String =
    java.time.Month.of(month).getDisplayName(style, Locale.getDefault())

private fun placeColor(place: ChargingPlace): Color = when (place) {
    ChargingPlace.HOME -> PlaceHomeColor
    ChargingPlace.WORK -> PlaceWorkColor
    ChargingPlace.PUBLIC -> PlacePublicColor
}

/**
 * "Rueckblick" - Gegenstueck zu `ReviewView.swift`: ein Kalenderjahr oder -monat auf einen Blick
 * (grosse Zahlen mit Vergleich zur Vorperiode, Ersparnis gegenueber dem Verbrenner, Rekorde,
 * Verteilung, kWh je Monat).
 *
 * Rechnet in beiden Modi LOKAL ([LocalReviewCalculator]) nach denselben Regeln wie
 * `GET /api/stats/review` - so geht es auch im Local-Only-Modus und offline. Zeilen ohne Daten
 * fallen weg, statt "–" zu zeigen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(navController: NavController) {
    val serverAddressRequiredMessage = stringResource(R.string.error_server_address_required)
    var basis by remember { mutableStateOf<ReviewBasis?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    val comparison by ComparisonVehicleStore.values.collectAsState()

    val today = LocalDate.now()
    var period by rememberSaveable { mutableStateOf(Period.YEAR) }
    // Wie die Kachel im Web: ab Dezember das laufende Jahr, sonst das Vorjahr.
    var year by rememberSaveable { mutableStateOf(if (today.monthValue == 12) today.year else today.year - 1) }
    var month by remember { mutableStateOf(YearMonth.from(today).minusMonths(1)) }
    var vehicleId by rememberSaveable { mutableStateOf<String?>(null) }
    var vehicleMenu by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!AppSettings.isReadyForDataAccess) { loadError = serverAddressRequiredMessage; return@LaunchedEffect }
        try {
            basis = AppRepository.reviewBasis()
            loadError = null
        } catch (e: Exception) {
            loadError = e.localizedMessage
        }
    }

    val vehicles = basis?.vehicles.orEmpty()
    // Ein inzwischen geloeschtes Fahrzeug faellt auf "alle" zurueck.
    val selectedVehicleId = vehicleId?.takeIf { id -> vehicles.any { it.id == id } }

    val review = remember(basis, period, year, month, selectedVehicleId, comparison) {
        val b = basis ?: return@remember null
        when (period) {
            Period.YEAR -> LocalReviewCalculator.build(b, Period.YEAR, year, null, selectedVehicleId, comparison)
            Period.MONTH -> LocalReviewCalculator.build(b, Period.MONTH, month.year, month.monthValue, selectedVehicleId, comparison)
        }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.tools_review_title)) },
            navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                }
            }
        )
    }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            loadError?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }

            // MARK: Zeitraum und Fahrzeug
            SectionCard {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(Period.YEAR to R.string.review_period_year, Period.MONTH to R.string.review_period_month)
                        .forEachIndexed { index, (value, label) ->
                            SegmentedButton(
                                selected = period == value,
                                onClick = { period = value },
                                shape = SegmentedButtonDefaults.itemShape(index, 2)
                            ) { Text(stringResource(label)) }
                        }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        if (period == Period.YEAR) year -= 1 else month = month.minusMonths(1)
                    }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.review_previous))
                    }
                    Text(
                        if (period == Period.YEAR) year.toString()
                        else month.format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale.getDefault())),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    IconButton(onClick = {
                        if (period == Period.YEAR) year += 1 else month = month.plusMonths(1)
                    }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.review_next))
                    }
                }
                if (vehicles.size > 1) {
                    Box(Modifier.padding(top = 4.dp)) {
                        OutlinedButton(onClick = { vehicleMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(vehicles.firstOrNull { it.id == selectedVehicleId }?.name ?: stringResource(R.string.review_all_vehicles))
                        }
                        DropdownMenu(expanded = vehicleMenu, onDismissRequest = { vehicleMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.review_all_vehicles)) },
                                onClick = { vehicleMenu = false; vehicleId = null }
                            )
                            vehicles.forEach { v ->
                                DropdownMenuItem(
                                    text = { Text(v.name) },
                                    onClick = { vehicleMenu = false; vehicleId = v.id }
                                )
                            }
                        }
                    }
                }
            }

            val r = review
            if (r != null && r.totals.sessions == 0) {
                Text(stringResource(R.string.review_no_sessions), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (r != null) {
                ReviewContent(r, showVehicle = selectedVehicleId == null && vehicles.size > 1) {
                    navController.navigate("settings/comparison-vehicle")
                }
            }
        }
    }
}

/** Alles unterhalb der Auswahl, sobald der Zeitraum mindestens einen Ladevorgang hat. */
@Composable
private fun ReviewContent(
    r: LocalReviewCalculator.Review,
    showVehicle: Boolean,
    onConfigureComparison: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        val previousLabel = stringResource(
            if (r.period == Period.YEAR) R.string.review_vs_previous_year else R.string.review_vs_previous_month
        )

        // MARK: Die grossen Zahlen
        SectionCard {
            SectionHeader(stringResource(R.string.review_section_totals))
            val t = r.totals
            val p = r.previous
            val tiles = buildList<Tile> {
                add(Tile(stringResource(R.string.review_kwh), Fmt.n("%,.0f kWh", t.kwh), delta(t.kwh, p?.kwh)))
                add(Tile(stringResource(R.string.review_cost), money(t.cost), delta(t.cost, p?.cost)))
                if (t.km > 0) add(Tile(stringResource(R.string.review_km), Fmt.n("%,.0f km", t.km), delta(t.km, p?.km)))
                add(Tile(stringResource(R.string.review_sessions), t.sessions.toString(),
                    delta(t.sessions.toDouble(), p?.sessions?.toDouble())))
                t.avgPricePerKwh?.let {
                    add(Tile(stringResource(R.string.review_avg_price), Fmt.n("%.2f €/kWh", it), delta(it, p?.avgPricePerKwh)))
                }
                t.avgConsumptionKwhPer100km?.let {
                    add(Tile(stringResource(R.string.review_avg_consumption), Fmt.n("%.1f kWh/100 km", it),
                        delta(it, p?.avgConsumptionKwhPer100km)))
                }
                t.pricePer100km?.let {
                    add(Tile(stringResource(R.string.review_price_per_100km), money(it), delta(it, p?.pricePer100km)))
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                tiles.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { tile -> TileView(tile, previousLabel, Modifier.weight(1f)) }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        // MARK: Ersparnis
        r.savings?.let { s ->
            SectionCard {
                SectionHeader(stringResource(R.string.review_section_savings))
                val tint = if (s.savedEur >= 0) Green else ReviewRed
                Text(
                    stringResource(if (s.savedEur >= 0) R.string.review_saved else R.string.review_extra_cost),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(money(abs(s.savedEur)), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = tint)
                Text(
                    stringResource(
                        if (s.savedCo2Kg >= 0) R.string.review_co2_saved else R.string.review_co2_more,
                        Fmt.n("%,.0f", abs(s.savedCo2Kg))
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
                Footnote(
                    stringResource(
                        R.string.review_savings_detail,
                        Fmt.n("%,.0f", s.fuelLiters),
                        stringResource(if (s.fuel == CombustionComparison.Fuel.PETROL) R.string.combustion_petrol else R.string.combustion_diesel),
                        money(s.fuelCost),
                        money(s.chargingCost)
                    )
                )
                Footnote(
                    stringResource(
                        R.string.review_savings_basis,
                        Fmt.n("%.1f", s.consumptionLPer100km),
                        Fmt.n("%.2f", s.fuelPricePerL),
                        Fmt.n("%.0f", s.gridCo2KgPerKwh * 1000)
                    )
                )
                if (!s.configured) {
                    Footnote(stringResource(R.string.review_savings_unconfigured))
                    TextButton(onClick = onConfigureComparison) {
                        Text(stringResource(R.string.review_savings_configure))
                    }
                }
            }
        }

        // MARK: Rekorde
        val rec = r.records
        val recordRows = buildList<RecordRow> {
            fun sessionSub(rs: LocalReviewCalculator.SessionRecord): String =
                listOfNotNull(Fmt.dateMedium(rs.startTime), rs.place, if (showVehicle) rs.vehicleName else null)
                    .joinToString(" · ")
            rec.cheapestPerKwh?.let { add(RecordRow(R.string.review_record_cheapest, Fmt.n("%.2f €/kWh", it.value), sessionSub(it))) }
            rec.mostExpensivePerKwh?.let { add(RecordRow(R.string.review_record_most_expensive, Fmt.n("%.2f €/kWh", it.value), sessionSub(it))) }
            rec.biggestCharge?.let { add(RecordRow(R.string.review_record_biggest, Fmt.n("%.1f kWh", it.value), sessionSub(it))) }
            rec.longestDistance?.let {
                val from = it.previousStartTime?.let { prev -> Fmt.dateMedium(prev) }
                val sub = listOfNotNull(
                    from?.let { f -> f + " – " + Fmt.dateMedium(it.startTime) } ?: Fmt.dateMedium(it.startTime),
                    if (showVehicle) it.vehicleName else null
                ).joinToString(" · ")
                add(RecordRow(R.string.review_record_longest, Fmt.n("%,.0f km", it.value), sub))
            }
            rec.coldest?.let { add(RecordRow(R.string.review_record_coldest, Fmt.temperature(it.value), sessionSub(it))) }
            rec.warmest?.let { add(RecordRow(R.string.review_record_warmest, Fmt.temperature(it.value), sessionSub(it))) }
            rec.topLocation?.let { add(RecordRow(R.string.review_record_top_location, it.name, null, it.sessions, it.kwh)) }
            rec.topProvider?.let { add(RecordRow(R.string.review_record_top_provider, it.name, null, it.sessions, it.kwh)) }
            rec.lowestConsumptionMonth?.let {
                add(RecordRow(R.string.review_record_lowest_month, Fmt.n("%.1f kWh/100 km", it.value), monthName(it.month)))
            }
            rec.highestConsumptionMonth?.let {
                add(RecordRow(R.string.review_record_highest_month, Fmt.n("%.1f kWh/100 km", it.value), monthName(it.month)))
            }
        }
        if (recordRows.isNotEmpty()) {
            SectionCard {
                SectionHeader(stringResource(R.string.review_section_records))
                recordRows.forEachIndexed { index, row ->
                    if (index > 0) HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    RecordRowView(row)
                }
            }
        }

        // MARK: Verteilung
        val split = r.split
        val hasAcDc = split.acKwh + split.dcKwh > 0
        if (hasAcDc || split.byPlace.isNotEmpty()) {
            SectionCard {
                SectionHeader(stringResource(R.string.review_section_split))
                if (split.byPlace.isNotEmpty()) {
                    val totalKwh = split.byPlace.sumOf { it.totalKwh }
                    ShareBar(split.byPlace.map { it.totalKwh to placeColor(it.placeValue) })
                    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        split.byPlace.forEach { stat ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(10.dp).clip(CircleShape).background(placeColor(stat.placeValue)))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(stat.placeValue.labelRes), Modifier.weight(1f))
                                val share = if (totalKwh > 0) stat.totalKwh / totalKwh * 100 else 0.0
                                Text(
                                    Fmt.n("%,.0f kWh", stat.totalKwh) + Fmt.n(" · %.0f %%", share),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
                if (hasAcDc) {
                    if (split.byPlace.isNotEmpty()) Spacer(Modifier.size(16.dp))
                    AcDcBar(split.acKwh, split.dcKwh)
                }
            }
        }

        // MARK: kWh je Monat
        if (r.monthly.any { it.kwh > 0 }) {
            SectionCard {
                SectionHeader(stringResource(R.string.review_section_monthly))
                HorizontalBarChart(
                    data = r.monthly.map { monthName(it.month, TextStyle.SHORT_STANDALONE) to it.kwh },
                    color = Teal,
                    unit = " kWh"
                )
            }
        }

        Footnote(stringResource(R.string.review_footer))
    }
}

private data class Tile(val label: String, val value: String, val deltaPct: Double?)

private data class RecordRow(
    val label: Int,
    val value: String,
    val subtitle: String?,
    val sessions: Int? = null,
    val kwh: Double? = null
)

/** Veraenderung in Prozent gegenueber der Vorperiode; null ohne (positiven) Vorwert. */
private fun delta(current: Double, previous: Double?): Double? =
    if (previous == null || previous <= 0) null else (current - previous) / previous * 100

@Composable
private fun TileView(tile: Tile, previousLabel: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(tile.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(tile.value, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        tile.deltaPct?.let { d ->
            Text(
                Fmt.n("%+.0f %%", d) + " " + previousLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RecordRowView(row: RecordRow) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(row.label), style = MaterialTheme.typography.bodyMedium)
            val sub = row.subtitle ?: row.sessions?.let { n ->
                pluralStringResource(R.plurals.dashboard_place_sessions, n, n) + Fmt.n(" · %,.0f kWh", row.kwh ?: 0.0)
            }
            sub?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(row.value, fontWeight = FontWeight.SemiBold)
    }
}
