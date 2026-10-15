package com.dominiqueherbrigpersonalteam.lademonitor.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.dominiqueherbrigpersonalteam.lademonitor.R
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.Vehicle
import com.dominiqueherbrigpersonalteam.lademonitor.data.repo.AppRepository
import com.dominiqueherbrigpersonalteam.lademonitor.ui.common.SectionCard

/**
 * Einstieg aus dem Einstellungs-Hauptmenue in einen Bereich, der an einem Fahrzeug haengt
 * (Kosten, Erinnerungen): mit einem Fahrzeug direkt hinein, mit mehreren zuerst die Auswahl.
 * Gegenstueck zu `VehicleScopedView` der iOS-App.
 *
 * [target] ist "costs" oder "reminders" - das Ziel liegt unter `settings/vehicles/<target>/<id>`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehiclePickerScreen(navController: NavController, target: String) {
    var vehicles by remember { mutableStateOf<List<Vehicle>?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val title = stringResource(if (target == "costs") R.string.settings_nav_costs else R.string.settings_nav_reminders)

    fun open(vehicle: Vehicle, replace: Boolean) {
        navController.navigate("settings/vehicles/$target/${vehicle.id}") {
            // Mit nur einem Fahrzeug ersetzt das Ziel die (unsichtbare) Auswahl, damit "Zurueck"
            // wieder in den Einstellungen landet.
            if (replace) popUpTo("settings/pick-vehicle/$target") { inclusive = true }
        }
    }

    LaunchedEffect(target) {
        try {
            val all = AppRepository.fetchVehicles()
            // Aktive Fahrzeuge zuerst, sonst in der gewohnten Reihenfolge.
            val sorted = all.filter { it.isActive } + all.filter { !it.isActive }
            vehicles = sorted
            errorMessage = null
            if (sorted.size == 1) open(sorted.first(), replace = true)
        } catch (e: Exception) {
            errorMessage = e.localizedMessage
            vehicles = emptyList()
        }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(title) },
            navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
            }
        )
    }) { padding ->
        val list = vehicles
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                list == null || list.size == 1 -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                errorMessage != null -> Text(
                    errorMessage!!,
                    Modifier.align(Alignment.Center).padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                list.isEmpty() -> Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.vehicle_scoped_empty_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.vehicle_scoped_empty_message),
                        Modifier.padding(top = 4.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
                    SectionCard {
                        SettingsHeader(stringResource(R.string.vehicle_picker_title))
                        list.forEachIndexed { index, vehicle ->
                            if (index > 0) HorizontalDivider()
                            SettingsNavRow(vehicle.name) { open(vehicle, replace = false) }
                        }
                    }
                }
            }
        }
    }
}
