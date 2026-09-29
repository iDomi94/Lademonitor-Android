package com.dominiqueherbrigpersonalteam.lademonitor.ui.tools

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.dominiqueherbrigpersonalteam.lademonitor.R
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ChargingSession
import com.dominiqueherbrigpersonalteam.lademonitor.data.repo.AppRepository
import com.dominiqueherbrigpersonalteam.lademonitor.data.repo.CombustionComparison
import com.dominiqueherbrigpersonalteam.lademonitor.data.repo.CombustionComparisonSettings
import com.dominiqueherbrigpersonalteam.lademonitor.data.settings.AppSettings
import com.dominiqueherbrigpersonalteam.lademonitor.ui.common.Fmt
import com.dominiqueherbrigpersonalteam.lademonitor.ui.common.SectionCard
import com.dominiqueherbrigpersonalteam.lademonitor.ui.theme.Green
import kotlin.math.abs

private val ComparisonRed = Color(0xFFFF3B30)
private val CONSUMPTION_RANGE = 3.0..15.0
private val FUEL_PRICE_RANGE = 1.00..2.50
private val GRID_RANGE = 0.0..0.60
private val LIFETIME_RANGE = 50_000.0..400_000.0
private val BATTERY_KWH_RANGE = 30.0..120.0
private val BATTERY_CO2_RANGE = 40.0..150.0
private const val YEAR_MS = 365L * 24 * 60 * 60 * 1000

private enum class CombustionField { CONSUMPTION, PRICE, GRID, LIFETIME, BATTERY_KWH, BATTERY_CO2 }

/** Umschalter oben im Tool, wie der segmentierte Picker in der iOS-App. */
private enum class CombustionMode(val raw: String, val label: Int) {
    TOTAL("total", R.string.combustion_mode_total),
    PER_100KM("per100km", R.string.combustion_mode_per100km),
    LIFECYCLE("lifecycle", R.string.combustion_mode_lifecycle);

    companion object {
        fun from(raw: String): CombustionMode = entries.firstOrNull { it.raw == raw } ?: PER_100KM
    }
}

private fun money(v: Double) = Fmt.n("%.2f", v) + " €"
private fun km(v: Double) = Fmt.n("%,.0f", v) + " km"
private fun tons(kg: Double) = Fmt.n("%.1f", abs(kg) / 1000) + " t"

/**
 * "Vergleich mit Verbrenner" - Gegenstueck zu `CombustionComparisonView.swift`: was dieselben
 * Kilometer mit Benzin oder Diesel gekostet haetten und wie viel CO2 dabei entstanden waere.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CombustionComparisonScreen(navController: NavController) {
    val serverAddressRequiredMessage = stringResource(R.string.error_server_address_required)
    var sessions by remember { mutableStateOf<List<ChargingSession>>(emptyList()) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var lastYearOnly by remember { mutableStateOf(CombustionComparisonSettings.lastYearOnly) }
    var fuel by remember { mutableStateOf(CombustionComparisonSettings.fuel) }
    var consumption by remember { mutableStateOf(CombustionComparisonSettings.consumption(fuel)) }
    var price by remember { mutableStateOf(CombustionComparisonSettings.price(fuel)) }
    var gridCo2 by remember { mutableStateOf(CombustionComparisonSettings.gridCo2) }
    var mode by remember { mutableStateOf(CombustionMode.from(CombustionComparisonSettings.mode)) }
    var lifetimeKm by remember { mutableStateOf(CombustionComparisonSettings.lifetimeKm) }
    var batteryKwhOverride by remember { mutableStateOf(CombustionComparisonSettings.batteryKwh) }
    var batteryCo2 by remember { mutableStateOf(CombustionComparisonSettings.batteryCo2) }
    // Akkukapazitaet des Fahrzeugs als Vorschlag fuer den Lebenszyklus
    var vehicleBatteryKwh by remember { mutableStateOf<Double?>(null) }
    var editing by remember { mutableStateOf<CombustionField?>(null) }
    var editText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        if (!AppSettings.isReadyForDataAccess) { loadError = serverAddressRequiredMessage; return@LaunchedEffect }
        try {
            sessions = AppRepository.fetchSessions()
            vehicleBatteryKwh = AppRepository.fetchVehicles().firstNotNullOfOrNull { it.batteryCapacityKwh }
            loadError = null
        } catch (e: Exception) {
            loadError = e.localizedMessage
        }
    }

    fun persist() = CombustionComparisonSettings.save(consumption, price)

    val basis = remember(sessions, lastYearOnly) {
        CombustionComparison.basis(sessions, if (lastYearOnly) System.currentTimeMillis() - YEAR_MS else null)
    }
    val input = CombustionComparison.Input(consumption, price, fuel.co2KgPerLiter, fuel.upstreamCo2KgPerLiter, gridCo2)
    val result = CombustionComparison.compute(basis, input)
    val batteryKwh = batteryKwhOverride ?: vehicleBatteryKwh ?: CombustionComparison.DEFAULT_BATTERY_KWH
    val lifecycle = CombustionComparison.lifecycle(
        basis, input, CombustionComparison.LifecycleInput(lifetimeKm, batteryKwh, batteryCo2)
    )

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.tools_combustion_title)) },
            navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                }
            }
        )
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth()) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp)) {
                    CombustionMode.entries.forEachIndexed { index, m ->
                        SegmentedButton(
                            selected = mode == m,
                            onClick = { mode = m; CombustionComparisonSettings.mode = m.raw },
                            shape = SegmentedButtonDefaults.itemShape(index, CombustionMode.entries.size)
                        ) { Text(stringResource(m.label), maxLines = 1) }
                    }
                }
            }
            if (basis.km > 0) {
                CombustionResultCard(mode, result, lifecycle, basis, consumption, fuel, gridCo2, lifetimeKm)
            }
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                loadError?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }

                SectionCard {
                    SectionHeader(stringResource(R.string.combustion_section_data))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf(true to R.string.combustion_period_year, false to R.string.combustion_period_all)
                            .forEachIndexed { index, (value, label) ->
                                SegmentedButton(
                                    selected = lastYearOnly == value,
                                    onClick = { lastYearOnly = value; CombustionComparisonSettings.lastYearOnly = value },
                                    shape = SegmentedButtonDefaults.itemShape(index, 2)
                                ) { Text(stringResource(label)) }
                            }
                    }
                    Spacer(Modifier.size(8.dp))
                    if (basis.km > 0) {
                        ValueLine(stringResource(R.string.combustion_driven), Fmt.n("%,.0f", basis.km) + " km")
                        ValueLine(stringResource(R.string.combustion_charged), Fmt.n("%,.0f", basis.kwh) + " kWh")
                        ValueLine(stringResource(R.string.combustion_paid), money(basis.cost))
                    } else {
                        Text(stringResource(R.string.combustion_no_data), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Footnote(stringResource(R.string.combustion_data_footer))
                }

                SectionCard {
                    SectionHeader(stringResource(R.string.combustion_section_vehicle))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        CombustionComparison.Fuel.entries.forEachIndexed { index, f ->
                            SegmentedButton(
                                selected = fuel == f,
                                onClick = {
                                    fuel = f
                                    CombustionComparisonSettings.fuel = f
                                    consumption = f.defaultConsumption
                                    price = f.defaultPrice
                                    persist()
                                },
                                shape = SegmentedButtonDefaults.itemShape(index, CombustionComparison.Fuel.entries.size)
                            ) {
                                Text(stringResource(if (f == CombustionComparison.Fuel.PETROL) R.string.combustion_petrol else R.string.combustion_diesel))
                            }
                        }
                    }
                    Spacer(Modifier.size(8.dp))
                    SliderRow(
                        title = stringResource(R.string.combustion_consumption),
                        valueText = Fmt.n("%.1f", consumption) + " l/100 km",
                        value = consumption,
                        onValueChange = { consumption = it; persist() },
                        range = CONSUMPTION_RANGE,
                        step = 0.1,
                        marker = fuel.defaultConsumption,
                        caption = null,
                        onReset = resetTo(consumption, fuel.defaultConsumption) { consumption = it; persist() },
                        onEdit = { editText = Fmt.n("%.1f", consumption); editing = CombustionField.CONSUMPTION }
                    )
                    SliderRow(
                        title = stringResource(R.string.combustion_price),
                        valueText = money(price),
                        value = price,
                        onValueChange = { price = it; persist() },
                        range = FUEL_PRICE_RANGE,
                        step = 0.01,
                        marker = fuel.defaultPrice,
                        caption = null,
                        onReset = resetTo(price, fuel.defaultPrice) { price = it; persist() },
                        onEdit = { editText = Fmt.n("%.2f", price); editing = CombustionField.PRICE }
                    )
                    Footnote(stringResource(R.string.combustion_vehicle_footer))
                }

                SectionCard {
                    SectionHeader(stringResource(R.string.combustion_section_co2))
                    SliderRow(
                        title = stringResource(R.string.combustion_grid),
                        valueText = Fmt.n("%.0f", gridCo2 * 1000) + " g",
                        value = gridCo2,
                        onValueChange = { gridCo2 = it; CombustionComparisonSettings.gridCo2 = it },
                        range = GRID_RANGE,
                        step = 0.01,
                        marker = CombustionComparison.DEFAULT_GRID_CO2_KG_PER_KWH,
                        caption = null,
                        onReset = resetTo(gridCo2, CombustionComparison.DEFAULT_GRID_CO2_KG_PER_KWH) {
                            gridCo2 = it; CombustionComparisonSettings.gridCo2 = it
                        },
                        onEdit = { editText = Fmt.n("%.0f", gridCo2 * 1000); editing = CombustionField.GRID }
                    )
                    Footnote(stringResource(R.string.combustion_co2_footer, Fmt.n("%.2f", fuel.co2KgPerLiter)))
                }

                if (mode == CombustionMode.LIFECYCLE) {
                    SectionCard {
                        SectionHeader(stringResource(R.string.combustion_section_lifecycle))
                        SliderRow(
                            title = stringResource(R.string.combustion_lifetime),
                            valueText = km(lifetimeKm),
                            value = lifetimeKm,
                            onValueChange = { lifetimeKm = it; CombustionComparisonSettings.lifetimeKm = it },
                            range = LIFETIME_RANGE,
                            step = 10_000.0,
                            marker = CombustionComparison.DEFAULT_LIFETIME_KM,
                            caption = null,
                            onReset = resetTo(lifetimeKm, CombustionComparison.DEFAULT_LIFETIME_KM) {
                                lifetimeKm = it; CombustionComparisonSettings.lifetimeKm = it
                            },
                            onEdit = { editText = Fmt.n("%.0f", lifetimeKm); editing = CombustionField.LIFETIME }
                        )
                        SliderRow(
                            title = stringResource(R.string.combustion_battery_kwh),
                            valueText = Fmt.n("%.0f", batteryKwh) + " kWh",
                            value = batteryKwh,
                            onValueChange = { batteryKwhOverride = it; CombustionComparisonSettings.batteryKwh = it },
                            range = BATTERY_KWH_RANGE,
                            step = 1.0,
                            marker = vehicleBatteryKwh,
                            caption = if (vehicleBatteryKwh != null) stringResource(R.string.combustion_battery_marker) else null,
                            onReset = if (batteryKwhOverride == null) null else ({
                                batteryKwhOverride = null; CombustionComparisonSettings.batteryKwh = null
                            }),
                            onEdit = { editText = Fmt.n("%.0f", batteryKwh); editing = CombustionField.BATTERY_KWH }
                        )
                        SliderRow(
                            title = stringResource(R.string.combustion_battery_co2),
                            valueText = Fmt.n("%.0f", batteryCo2) + " kg",
                            value = batteryCo2,
                            onValueChange = { batteryCo2 = it; CombustionComparisonSettings.batteryCo2 = it },
                            range = BATTERY_CO2_RANGE,
                            step = 5.0,
                            marker = CombustionComparison.DEFAULT_BATTERY_CO2_KG_PER_KWH,
                            caption = null,
                            onReset = resetTo(batteryCo2, CombustionComparison.DEFAULT_BATTERY_CO2_KG_PER_KWH) {
                                batteryCo2 = it; CombustionComparisonSettings.batteryCo2 = it
                            },
                            onEdit = { editText = Fmt.n("%.0f", batteryCo2); editing = CombustionField.BATTERY_CO2 }
                        )
                        Footnote(stringResource(R.string.combustion_lifecycle_footer))
                    }
                }
            }
        }
    }

    editing?.let { field ->
        val title = when (field) {
            CombustionField.CONSUMPTION -> R.string.combustion_edit_consumption
            CombustionField.PRICE -> R.string.combustion_edit_price
            CombustionField.GRID -> R.string.combustion_edit_grid
            CombustionField.LIFETIME -> R.string.combustion_edit_lifetime
            CombustionField.BATTERY_KWH -> R.string.combustion_edit_battery_kwh
            CombustionField.BATTERY_CO2 -> R.string.combustion_edit_battery_co2
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
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    editText.trim().replace(",", ".").toDoubleOrNull()?.takeIf { it >= 0 }?.let { v ->
                        when (field) {
                            CombustionField.CONSUMPTION -> { consumption = v; persist() }
                            CombustionField.PRICE -> { price = v; persist() }
                            CombustionField.GRID -> { gridCo2 = v / 1000; CombustionComparisonSettings.gridCo2 = v / 1000 }
                            CombustionField.LIFETIME -> { lifetimeKm = v; CombustionComparisonSettings.lifetimeKm = v }
                            CombustionField.BATTERY_KWH -> { batteryKwhOverride = v; CombustionComparisonSettings.batteryKwh = v }
                            CombustionField.BATTERY_CO2 -> { batteryCo2 = v; CombustionComparisonSettings.batteryCo2 = v }
                        }
                    }
                    editing = null
                }) { Text(stringResource(R.string.tariff_apply)) }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }
}

private fun resetTo(value: Double, default: Double, apply: (Double) -> Unit): (() -> Unit)? =
    if (abs(value - default) <= 0.0001) null else ({ apply(default) })

@Composable
private fun ValueLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CombustionResultCard(
    mode: CombustionMode,
    r: CombustionComparison.Result,
    l: CombustionComparison.Lifecycle,
    basis: CombustionComparison.Basis,
    consumption: Double,
    fuel: CombustionComparison.Fuel,
    gridCo2: Double,
    lifetimeKm: Double
) {
    // Positiv = das E-Auto liegt vorn (Geld bzw. beim Lebenszyklus CO2)
    val savings = when (mode) {
        CombustionMode.TOTAL -> r.savings
        CombustionMode.PER_100KM -> (r.combustionCostPer100km ?: 0.0) - (r.electricCostPer100km ?: 0.0)
        CombustionMode.LIFECYCLE -> l.combustionTotalCo2Kg - l.electricTotalCo2Kg
    }
    val tint = when {
        savings > 0.005 -> Green
        savings < -0.005 -> ComparisonRed
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val title = when (mode) {
        CombustionMode.TOTAL -> stringResource(if (savings >= 0) R.string.combustion_saved else R.string.combustion_extra)
        CombustionMode.PER_100KM ->
            stringResource(if (savings >= 0) R.string.combustion_cheaper_per_100km else R.string.combustion_dearer_per_100km)
        CombustionMode.LIFECYCLE ->
            stringResource(if (savings >= 0) R.string.combustion_co2_less_over else R.string.combustion_co2_more_over, km(lifetimeKm))
    }
    val figure = if (mode == CombustionMode.LIFECYCLE) tons(savings) else money(abs(savings))
    val breakEvenLine = r.breakEvenPricePerLiter?.let { stringResource(R.string.combustion_break_even, money(it)) }
    val lines: List<String> = when (mode) {
        CombustionMode.TOTAL -> listOfNotNull(
            stringResource(R.string.combustion_detail, money(r.combustionCost), Fmt.n("%,.0f", r.liters), money(r.electricCost)),
            stringResource(R.string.combustion_co2_line, Fmt.n("%,.0f", r.electricCo2Kg), Fmt.n("%,.0f", r.combustionCo2Kg)),
            breakEvenLine
        )
        CombustionMode.PER_100KM -> {
            val kwh = CombustionComparison.kwhPerKm(basis) * 100
            listOfNotNull(
                stringResource(
                    R.string.combustion_per100_energy, Fmt.n("%.1f", kwh), Fmt.n("%.1f", consumption),
                    stringResource(if (fuel == CombustionComparison.Fuel.PETROL) R.string.combustion_petrol else R.string.combustion_diesel)
                ),
                stringResource(R.string.combustion_per100_cost, money(r.electricCostPer100km ?: 0.0), money(r.combustionCostPer100km ?: 0.0)),
                stringResource(R.string.combustion_co2_line, Fmt.n("%.1f", kwh * gridCo2), Fmt.n("%.1f", consumption * fuel.co2KgPerLiter)),
                breakEvenLine
            )
        }
        CombustionMode.LIFECYCLE -> listOf(
            stringResource(
                R.string.combustion_lifecycle_total, tons(l.electricTotalCo2Kg), tons(l.combustionTotalCo2Kg),
                tons(l.electricProductionCo2Kg), tons(l.combustionProductionCo2Kg)
            ),
            stringResource(R.string.combustion_lifecycle_per100, Fmt.n("%.1f", l.electricCo2Per100km), Fmt.n("%.1f", l.combustionCo2Per100km)),
            stringResource(R.string.combustion_lifecycle_energy_cost, money(l.electricEnergyCost), money(l.combustionEnergyCost)),
            l.co2BreakEvenKm?.let { stringResource(R.string.combustion_lifecycle_break_even, km(Math.round(it / 1000) * 1000.0)) }
                ?: stringResource(R.string.combustion_lifecycle_never)
        )
    }
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth()
                .background(tint.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
                .border(BorderStroke(1.dp, tint.copy(alpha = 0.45f)), RoundedCornerShape(14.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(figure, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                }
                Icon(
                    if (savings >= 0) Icons.Filled.CheckCircle else Icons.Filled.Cancel, null,
                    tint = tint, modifier = Modifier.size(24.dp)
                )
            }
            lines.forEachIndexed { index, line ->
                Text(
                    line,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (index == 0) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
                    fontWeight = if (index == lines.lastIndex && index > 1) FontWeight.SemiBold else null
                )
            }
        }
    }
}
