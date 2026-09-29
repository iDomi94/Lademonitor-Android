package com.dominiqueherbrigpersonalteam.lademonitor.ui.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Euro
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.dominiqueherbrigpersonalteam.lademonitor.R
import com.dominiqueherbrigpersonalteam.lademonitor.data.settings.AppMode
import com.dominiqueherbrigpersonalteam.lademonitor.data.settings.AppSettings
import com.dominiqueherbrigpersonalteam.lademonitor.ui.common.SectionCard

/**
 * Reiter "Tools": eine Liste kleiner Rechner - Gegenstueck zu `ToolsView.swift`. Weitere Tools
 * kommen hier als Zeilen dazu, nicht als weitere Reiter.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(navController: NavController) {
    val mode by AppSettings.appMode.collectAsState()
    val isServerMode = mode == AppMode.SERVER
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.tab_tools)) }) }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SectionCard {
                ToolRow(Icons.Filled.Euro, R.string.tools_tariff_title, R.string.tools_tariff_subtitle) {
                    navController.navigate("tools/tariff")
                }
                ToolRow(Icons.Filled.LocalGasStation, R.string.tools_combustion_title, R.string.tools_combustion_subtitle) {
                    navController.navigate("tools/combustion")
                }
                // Nur im Server-Modus, wie die Reifen in den Einstellungen: die Auswertung
                // rechnet allein der Server (battery.py).
                if (isServerMode) {
                    ToolRow(Icons.Filled.BatteryChargingFull, R.string.tools_battery_title, R.string.tools_battery_subtitle) {
                        navController.navigate("tools/battery")
                    }
                }
            }
        }
    }
}


@Composable
private fun ToolRow(icon: ImageVector, title: Int, subtitle: Int, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
