package com.dominiqueherbrigpersonalteam.lademonitor.ui.tools

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.dominiqueherbrigpersonalteam.lademonitor.R
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.RangeEstimate
import com.dominiqueherbrigpersonalteam.lademonitor.data.remote.ApiClient
import com.dominiqueherbrigpersonalteam.lademonitor.data.repo.AppRepository
import com.dominiqueherbrigpersonalteam.lademonitor.data.repo.RangeBasis
import com.dominiqueherbrigpersonalteam.lademonitor.data.repo.RangeCalculator
import com.dominiqueherbrigpersonalteam.lademonitor.data.settings.AppMode
import com.dominiqueherbrigpersonalteam.lademonitor.data.settings.AppSettings
import com.dominiqueherbrigpersonalteam.lademonitor.ui.common.Fmt
import com.dominiqueherbrigpersonalteam.lademonitor.ui.common.SectionCard
import kotlin.math.roundToInt

private val SOC_RANGE = 0.0..100.0
private val TEMP_RANGE = -20.0..40.0
private val CONSUMPTION_RANGE = 10.0..35.0
private val CAPACITY_RANGE = 30.0..120.0

private enum class RangeField { SOC_START, SOC_END, TEMP, CONSUMPTION, CAPACITY }

/** Woher der Vorschlag fuer den Verbrauch stammt - bestimmt die graue Herkunftszeile. */
private enum class ConsumptionSource { SERVER_CURVE, YEARLY_AVERAGE, FALLBACK }

private fun pct(v: Double) = Fmt.n("%.0f", v) + " %"
private fun celsius(v: Double) = Fmt.n("%.0f", v) + " °C"
private fun kwh100(v: Double) = Fmt.n("%.1f", v) + " kWh/100 km"
private fun kwh(v: Double) = Fmt.n("%.1f", v) + " kWh"

/**
 * "Reichweitenrechner" - Gegenstueck zu `RangeCalculatorView.swift`: wie weit komme ich von X auf
 * Y % bei dieser Aussentemperatur?
 *
 * Der Verbrauch folgt im Server-Modus der Kurve aus `range.curve` (`GET /api/stats/temperature`,
 * Verbrauch AB AKKU) beim gewaehlten Grad - bis man den Regler selbst bewegt; dann erscheint
 * "Zurueck zum Datenwert". Im Local-Only-Modus oder ohne Kurve ist der Vorschlag der
 * Jahresdurchschnitt ohne Temperatur. Nichts davon wird gespeichert.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RangeCalculatorScreen(navController: NavController, initialVehicleId: String? = null) {
    val serverAddressRequiredMessage = stringResource(R.string.error_server_address_required)
    val mode by AppSettings.appMode.collectAsState()

    var basis by remember { mutableStateOf<RangeBasis?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var vehicleId by rememberSaveable { mutableStateOf(initialVehicleId) }
    var socStart by rememberSaveable { mutableStateOf(RangeCalculator.DEFAULT_SOC_START) }
    var socEnd by rememberSaveable { mutableStateOf(RangeCalculator.DEFAULT_SOC_END) }
    var tempC by rememberSaveable { mutableStateOf(RangeCalculator.DEFAULT_TEMP_C) }
    // Solange null, folgt der Verbrauch dem Datenwert (und damit dem Temperaturregler).
    var manualConsumption by rememberSaveable { mutableStateOf<Double?>(null) }
    var capacityOverride by rememberSaveable { mutableStateOf<Double?>(null) }
    // Server-Kurve je Fahrzeug; null = keine (Local-Only, aelterer Server, Abruf gescheitert).
    var serverRange by remember { mutableStateOf<RangeEstimate?>(null) }
    var vehicleMenu by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<RangeField?>(null) }
    var editText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        if (!AppSettings.isReadyForDataAccess) { loadError = serverAddressRequiredMessage; return@LaunchedEffect }
        try {
            val loaded = AppRepository.rangeBasis()
            basis = loaded
            loadError = null
            if (vehicleId == null || loaded.vehicles.none { it.id == vehicleId }) {
                vehicleId = (loaded.vehicles.firstOrNull { it.isActive } ?: loaded.vehicles.firstOrNull())?.id
            }
        } catch (e: Exception) {
            loadError = e.localizedMessage
        }
    }

    // Die Kurve kommt fertig vom Server - je Fahrzeug, denn `range` gibt es nur fuer genau eines.
    LaunchedEffect(vehicleId, mode) {
        serverRange = null
        val id = vehicleId ?: return@LaunchedEffect
        if (mode != AppMode.SERVER || !AppSettings.isReadyForDataAccess) return@LaunchedEffect
        serverRange = try {
            ApiClient.fetchTemperatureStats(vehicleId = id).range?.takeIf { it.curve.isNotEmpty() }
        } catch (e: Exception) {
            null
        }
    }

    val vehicles = basis?.vehicles.orEmpty()
    val vehicle = vehicles.firstOrNull { it.id == vehicleId }
    val yearlyAverage = remember(basis, vehicleId) {
        val b = basis
        val id = vehicleId
        if (b == null || id == null) null else RangeCalculator.yearlyAverageConsumption(b.sessions, id)
    }
    val curveValue = serverRange?.let { RangeCalculator.interpolate(it.curve, tempC) }
    val (dataConsumption, source) = when {
        curveValue != null -> curveValue to ConsumptionSource.SERVER_CURVE
        yearlyAverage != null -> yearlyAverage to ConsumptionSource.YEARLY_AVERAGE
        else -> RangeCalculator.FALLBACK_CONSUMPTION to ConsumptionSource.FALLBACK
    }
    val consumption = manualConsumption ?: dataConsumption

    val vehicleCapacity = vehicle?.batteryCapacityKwh ?: serverRange?.capacityKwh
    val capacity = vehicleCapacity ?: capacityOverride ?: RangeCalculator.DEFAULT_CAPACITY_KWH
    val result = RangeCalculator.compute(capacity, socStart, socEnd, consumption)

    fun setStart(v: Double) {
        // Ende muss unter dem Start bleiben - deshalb mindestens 1 % Start.
        socStart = v.coerceIn(1.0, 100.0)
        if (socEnd >= socStart) socEnd = (socStart - 1).coerceAtLeast(0.0)
    }
    fun setEnd(v: Double) {
        socEnd = v.coerceIn(0.0, (socStart - 1).coerceAtLeast(0.0))
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.tools_range_title)) },
            navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                }
            }
        )
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            RangeResultCard(result, socStart, socEnd, consumption)
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                loadError?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }

                if (vehicles.size > 1) {
                    SectionCard {
                        SectionHeader(stringResource(R.string.range_section_vehicle))
                        Box {
                            OutlinedButton(onClick = { vehicleMenu = true }, modifier = Modifier.fillMaxWidth()) {
                                Text(vehicle?.name ?: "–")
                            }
                            DropdownMenu(expanded = vehicleMenu, onDismissRequest = { vehicleMenu = false }) {
                                vehicles.forEach { v ->
                                    DropdownMenuItem(
                                        text = { Text(v.name) },
                                        onClick = {
                                            vehicleMenu = false
                                            if (v.id != vehicleId) {
                                                vehicleId = v.id
                                                // Ein Wert fuers alte Fahrzeug passt nicht zum neuen.
                                                manualConsumption = null
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                SectionCard {
                    SectionHeader(stringResource(R.string.range_section_trip))
                    SliderRow(
                        title = stringResource(R.string.range_label_soc_start),
                        valueText = pct(socStart),
                        value = socStart,
                        onValueChange = { setStart(it) },
                        range = SOC_RANGE,
                        step = 1.0,
                        marker = RangeCalculator.DEFAULT_SOC_START,
                        caption = null,
                        onReset = null,
                        onEdit = { editText = Fmt.n("%.0f", socStart); editing = RangeField.SOC_START }
                    )
                    SliderRow(
                        title = stringResource(R.string.range_label_soc_end),
                        valueText = pct(socEnd),
                        value = socEnd,
                        onValueChange = { setEnd(it) },
                        range = SOC_RANGE,
                        step = 1.0,
                        marker = RangeCalculator.DEFAULT_SOC_END,
                        caption = null,
                        onReset = null,
                        onEdit = { editText = Fmt.n("%.0f", socEnd); editing = RangeField.SOC_END }
                    )
                    SliderRow(
                        title = stringResource(R.string.range_label_temp),
                        valueText = celsius(tempC),
                        value = tempC,
                        onValueChange = { tempC = it },
                        range = TEMP_RANGE,
                        step = 1.0,
                        marker = RangeCalculator.DEFAULT_TEMP_C,
                        caption = if (source == ConsumptionSource.SERVER_CURVE) null
                        else stringResource(R.string.range_caption_temp_unused),
                        onReset = null,
                        onEdit = { editText = Fmt.n("%.0f", tempC); editing = RangeField.TEMP }
                    )
                }

                SectionCard {
                    SectionHeader(stringResource(R.string.range_section_vehicle_data))
                    val lossPct = serverRange?.lossPct
                    val consumptionCaption = when {
                        manualConsumption != null -> stringResource(R.string.range_caption_consumption_manual, kwh100(dataConsumption))
                        source == ConsumptionSource.SERVER_CURVE && lossPct != null ->
                            stringResource(R.string.range_caption_consumption_curve_loss, celsius(tempC), Fmt.n("%.1f", lossPct))
                        source == ConsumptionSource.SERVER_CURVE ->
                            stringResource(R.string.range_caption_consumption_curve_no_loss, celsius(tempC))
                        source == ConsumptionSource.YEARLY_AVERAGE -> stringResource(R.string.range_caption_consumption_average)
                        else -> stringResource(R.string.range_caption_consumption_fallback)
                    }
                    SliderRow(
                        title = stringResource(R.string.range_label_consumption),
                        valueText = kwh100(consumption),
                        value = consumption,
                        onValueChange = { manualConsumption = it },
                        range = CONSUMPTION_RANGE,
                        step = 0.1,
                        marker = dataConsumption,
                        caption = consumptionCaption,
                        onReset = null,
                        onEdit = { editText = Fmt.n("%.1f", consumption); editing = RangeField.CONSUMPTION }
                    )
                    if (manualConsumption != null) {
                        TextButton(onClick = { manualConsumption = null }) {
                            Text(stringResource(R.string.range_back_to_data))
                        }
                    }

                    if (vehicleCapacity != null) {
                        Footnote(stringResource(R.string.range_capacity_from_vehicle, kwh(vehicleCapacity)))
                    } else {
                        Footnote(stringResource(R.string.range_capacity_missing))
                        SliderRow(
                            title = stringResource(R.string.range_label_capacity),
                            valueText = Fmt.n("%.0f", capacity) + " kWh",
                            value = capacity,
                            onValueChange = { capacityOverride = it },
                            range = CAPACITY_RANGE,
                            step = 1.0,
                            marker = RangeCalculator.DEFAULT_CAPACITY_KWH,
                            caption = null,
                            onReset = if (capacityOverride == null) null else ({ capacityOverride = null }),
                            onEdit = { editText = Fmt.n("%.0f", capacity); editing = RangeField.CAPACITY }
                        )
                    }
                    Footnote(stringResource(R.string.range_footer))
                }
            }
        }
    }

    editing?.let { field ->
        val title = when (field) {
            RangeField.SOC_START -> R.string.range_edit_soc_start
            RangeField.SOC_END -> R.string.range_edit_soc_end
            RangeField.TEMP -> R.string.range_edit_temp
            RangeField.CONSUMPTION -> R.string.range_edit_consumption
            RangeField.CAPACITY -> R.string.range_edit_capacity
        }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(stringResource(title)) },
            text = {
                OutlinedTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    label = { Text(stringResource(R.string.tariff_value_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        // Die Temperatur darf negativ sein - dafuer braucht es die volle Tastatur.
                        keyboardType = if (field == RangeField.TEMP) KeyboardType.Text else KeyboardType.Decimal
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val v = editText.trim().replace(",", ".").replace("−", "-").toDoubleOrNull()
                    if (v != null) {
                        when (field) {
                            RangeField.SOC_START -> setStart(v.roundToInt().toDouble())
                            RangeField.SOC_END -> setEnd(v.roundToInt().toDouble())
                            RangeField.TEMP -> tempC = v.coerceIn(-50.0, 60.0)
                            RangeField.CONSUMPTION -> if (v > 0) manualConsumption = v
                            RangeField.CAPACITY -> if (v > 0) capacityOverride = v
                        }
                    }
                    editing = null
                }) { Text(stringResource(R.string.tariff_apply)) }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }
}

@Composable
private fun RangeResultCard(result: RangeCalculator.Result, socStart: Double, socEnd: Double, consumption: Double) {
    val tint = MaterialTheme.colorScheme.primary
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth()
                .background(tint.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                .border(BorderStroke(1.dp, tint.copy(alpha = 0.40f)), RoundedCornerShape(14.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                stringResource(R.string.range_result_label, pct(socStart), pct(socEnd)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                result.rangeKm?.let { "≈ " + Fmt.n("%,.0f", it) + " km" } ?: "–",
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                stringResource(R.string.range_result_energy, kwh(result.usedKwh)),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                stringResource(R.string.range_result_consumption, kwh100(consumption)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
