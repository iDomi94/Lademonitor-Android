package com.dominiqueherbrigpersonalteam.lademonitor.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.dominiqueherbrigpersonalteam.lademonitor.R
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.BatteryLossGroup
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.BatteryStats
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.BatteryVehicleStats
import com.dominiqueherbrigpersonalteam.lademonitor.data.remote.ApiClient
import com.dominiqueherbrigpersonalteam.lademonitor.data.settings.AppMode
import com.dominiqueherbrigpersonalteam.lademonitor.data.settings.AppSettings
import com.dominiqueherbrigpersonalteam.lademonitor.ui.common.Fmt
import com.dominiqueherbrigpersonalteam.lademonitor.ui.common.SectionCard
import com.dominiqueherbrigpersonalteam.lademonitor.ui.dashboard.VerticalBarChart

private fun signedPercent(v: Double) = (if (v > 0) "+" else "") + Fmt.n("%.1f", v) + " %"

/**
 * "Akku und Ladeverluste" - Gegenstueck zu `BatteryHealthView.swift`. Nur im Server-Modus: die
 * Auswertung kommt fertig aus `/api/stats/battery` (siehe [BatteryStats]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatteryHealthScreen(navController: NavController) {
    val isServerMode = AppSettings.appMode.value == AppMode.SERVER
    val unavailableMessage = stringResource(R.string.battery_unavailable)
    var stats by remember { mutableStateOf<BatteryStats?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var vehicleId by remember { mutableStateOf<String?>(null) }
    var vehicleMenu by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!isServerMode) { loading = false; return@LaunchedEffect }
        try {
            stats = ApiClient.fetchBatteryStats()
            error = null
        } catch (e: Exception) {
            // Aelterer Server ohne den Endpunkt oder keine Verbindung.
            error = unavailableMessage
        }
        loading = false
    }

    val vehicles = stats?.vehicles?.filter { it.points.isNotEmpty() } ?: emptyList()
    val selected = vehicles.firstOrNull { it.vehicleId == vehicleId } ?: vehicles.firstOrNull()

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.tools_battery_title)) },
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
            when {
                !isServerMode -> Text(stringResource(R.string.battery_server_only), color = MaterialTheme.colorScheme.onSurfaceVariant)
                loading -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                error != null -> Text(error ?: "", color = MaterialTheme.colorScheme.onSurfaceVariant)
                selected == null -> Text(stringResource(R.string.battery_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> {
                    if (vehicles.size > 1) {
                        Box {
                            OutlinedButton(onClick = { vehicleMenu = true }, modifier = Modifier.fillMaxWidth()) {
                                Text(selected.vehicleName)
                            }
                            DropdownMenu(expanded = vehicleMenu, onDismissRequest = { vehicleMenu = false }) {
                                vehicles.forEach { v ->
                                    DropdownMenuItem(text = { Text(v.vehicleName) }, onClick = {
                                        vehicleMenu = false
                                        vehicleId = v.vehicleId
                                    })
                                }
                            }
                        }
                    }
                    HealthCard(selected)
                    LossCard(stringResource(R.string.battery_by_type), selected.lossesByType, selected.nominalCapacityKwh)
                    LossCard(stringResource(R.string.battery_by_provider), selected.lossesByProvider, selected.nominalCapacityKwh)
                    Footnote(stringResource(R.string.battery_explanation, selected.minSocDelta))
                    if (selected.excluded.total > 0) {
                        Footnote(stringResource(R.string.battery_excluded, selected.excluded.total, selected.excluded.estimatedEnergy))
                    }
                }
            }
        }
    }
}

@Composable
private fun HealthCard(v: BatteryVehicleStats) {
    SectionCard {
        SectionHeader(stringResource(R.string.battery_health))
        val index = v.healthLatestIndexPct
        if (index == null) {
            Text(stringResource(R.string.battery_health_none), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text(Fmt.n("%.0f", index) + " %", fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.battery_health_label), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            v.healthTrendPctPerYear?.let {
                Text(stringResource(R.string.battery_health_trend, signedPercent(it)),
                    style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            }
            VerticalBarChart(v.healthPeriods.map { it.label to it.indexPct }, Modifier.padding(top = 12.dp))
        }
        Footnote(stringResource(R.string.battery_health_footer))
    }
}

@Composable
private fun LossCard(title: String, groups: List<BatteryLossGroup>, nominal: Double?) {
    if (groups.isEmpty()) return
    SectionCard {
        SectionHeader(title)
        groups.forEach { g ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        when (g.key) {
                            "" -> stringResource(R.string.battery_without_provider)
                            "unknown" -> stringResource(R.string.battery_unknown_type)
                            else -> g.key
                        }
                    )
                    Text(
                        stringResource(R.string.battery_group_detail, g.sessionCount, Fmt.n("%.1f", g.apparentCapacityKwh)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(g.lossPct?.let { signedPercent(it) } ?: "–", fontWeight = FontWeight.SemiBold)
            }
        }
        if (nominal == null) Footnote(stringResource(R.string.battery_no_nominal))
    }
}
