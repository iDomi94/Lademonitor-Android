package com.dominiqueherbrigpersonalteam.lademonitor.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.dominiqueherbrigpersonalteam.lademonitor.R
import com.dominiqueherbrigpersonalteam.lademonitor.data.repo.AppRepository
import com.dominiqueherbrigpersonalteam.lademonitor.data.repo.CombustionComparison
import com.dominiqueherbrigpersonalteam.lademonitor.data.repo.ComparisonVehicleStore
import com.dominiqueherbrigpersonalteam.lademonitor.data.settings.AppMode
import com.dominiqueherbrigpersonalteam.lademonitor.data.settings.AppSettings
import com.dominiqueherbrigpersonalteam.lademonitor.ui.common.Fmt
import com.dominiqueherbrigpersonalteam.lademonitor.ui.common.SectionCard

private fun parse(text: String): Double? = text.trim().replace(",", ".").toDoubleOrNull()

/**
 * Vergleichsfahrzeug (Verbrenner) fuer die Ersparnis im Rueckblick - Gegenstueck zum Abschnitt
 * "Vergleichsfahrzeug" der iOS-Einstellungen. Gespeichert in [ComparisonVehicleStore]; im
 * Server-Modus beim naechsten Sync hochgeladen, damit Web und Jahresmail dieselben Werte nehmen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComparisonVehicleSettingsScreen(navController: NavController) {
    val stored by ComparisonVehicleStore.values.collectAsState()
    val mode by AppSettings.appMode.collectAsState()

    var fuel by remember { mutableStateOf(stored.fuel) }
    var consumptionText by remember { mutableStateOf(Fmt.n("%.1f", stored.consumption)) }
    var priceText by remember { mutableStateOf(Fmt.n("%.2f", stored.price)) }
    // In g/kWh wie im Verbrenner-Tool, gespeichert in kg/kWh.
    var gridText by remember { mutableStateOf(Fmt.n("%.0f", stored.gridCo2 * 1000)) }
    var savedHint by remember { mutableStateOf(false) }

    val consumption = parse(consumptionText)
    val price = parse(priceText)
    val grid = parse(gridText)?.let { it / 1000 }
    val consumptionValid = consumption != null && consumption in ComparisonVehicleStore.CONSUMPTION_RANGE
    val priceValid = price != null && price in ComparisonVehicleStore.PRICE_RANGE
    val gridValid = grid != null && grid in ComparisonVehicleStore.GRID_RANGE

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.settings_nav_comparison_vehicle)) },
            navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
            }
        )
    }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SectionCard {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    CombustionComparison.Fuel.entries.forEachIndexed { index, f ->
                        SegmentedButton(
                            selected = fuel == f,
                            onClick = {
                                if (fuel != f) {
                                    fuel = f
                                    // Richtwerte des anderen Kraftstoffs vorschlagen, wie im Verbrenner-Tool.
                                    consumptionText = Fmt.n("%.1f", f.defaultConsumption)
                                    priceText = Fmt.n("%.2f", f.defaultPrice)
                                    savedHint = false
                                }
                            },
                            shape = SegmentedButtonDefaults.itemShape(index, CombustionComparison.Fuel.entries.size)
                        ) {
                            Text(stringResource(if (f == CombustionComparison.Fuel.PETROL) R.string.combustion_petrol else R.string.combustion_diesel))
                        }
                    }
                }
                OutlinedTextField(
                    value = consumptionText,
                    onValueChange = { consumptionText = it; savedHint = false },
                    label = { Text(stringResource(R.string.comparison_vehicle_consumption)) },
                    isError = !consumptionValid,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it; savedHint = false },
                    label = { Text(stringResource(R.string.comparison_vehicle_price)) },
                    isError = !priceValid,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = gridText,
                    onValueChange = { gridText = it; savedHint = false },
                    label = { Text(stringResource(R.string.comparison_vehicle_grid)) },
                    isError = !gridValid,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        if (consumption != null && price != null && grid != null) {
                            AppRepository.saveComparisonVehicle(fuel, consumption, price, grid)
                            savedHint = true
                        }
                    },
                    enabled = consumptionValid && priceValid && gridValid,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                ) { Text(stringResource(R.string.action_save)) }
                if (savedHint) {
                    Text(
                        stringResource(R.string.comparison_vehicle_saved),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            Text(
                stringResource(
                    if (mode == AppMode.SERVER) R.string.comparison_vehicle_hint_server else R.string.comparison_vehicle_hint_local
                ) + if (!stored.configured) "\n\n" + stringResource(R.string.comparison_vehicle_hint_defaults) else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}
