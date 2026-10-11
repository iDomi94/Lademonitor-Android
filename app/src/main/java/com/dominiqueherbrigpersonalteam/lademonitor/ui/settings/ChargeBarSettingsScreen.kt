package com.dominiqueherbrigpersonalteam.lademonitor.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.dominiqueherbrigpersonalteam.lademonitor.R
import com.dominiqueherbrigpersonalteam.lademonitor.data.settings.ChargeBarSettings
import com.dominiqueherbrigpersonalteam.lademonitor.ui.common.SectionCard
import com.dominiqueherbrigpersonalteam.lademonitor.ui.sessions.ChargeBar
import kotlin.math.roundToInt

/** Einstellungen des Ladebalkens in der Ladevorgangsliste (nur lokal auf diesem Geraet). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChargeBarSettingsScreen(navController: NavController) {
    val config by ChargeBarSettings.config.collectAsStateWithLifecycle()

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.settings_nav_charge_bar)) },
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
                Text(
                    stringResource(R.string.charge_bar_preview),
                    style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 12.dp)
                )
                ChargeBar(socStart = 30, socEnd = 70, lowerMarker = config.lower, upperMarker = config.upper)
            }

            SectionCard {
                MarkerControls(
                    label = stringResource(R.string.charge_bar_lower_marker),
                    enabled = config.lowerMarkerEnabled,
                    value = config.lowerMarker,
                    onEnabledChange = ChargeBarSettings::setLowerMarkerEnabled,
                    onValueChange = ChargeBarSettings::setLowerMarker
                )
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                MarkerControls(
                    label = stringResource(R.string.charge_bar_upper_marker),
                    enabled = config.upperMarkerEnabled,
                    value = config.upperMarker,
                    onEnabledChange = ChargeBarSettings::setUpperMarkerEnabled,
                    onValueChange = ChargeBarSettings::setUpperMarker
                )
            }

            Text(
                stringResource(R.string.charge_bar_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
private fun MarkerControls(
    label: String,
    enabled: Boolean,
    value: Int,
    onEnabledChange: (Boolean) -> Unit,
    onValueChange: (Int) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = enabled, onCheckedChange = onEnabledChange)
    }
    if (enabled) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Slider(
                value = value.toFloat(),
                // 0..100 in 5er-Schritten: 21 Werte, also 19 Zwischenschritte.
                onValueChange = { onValueChange(((it / 5f).roundToInt() * 5).coerceIn(0, 100)) },
                valueRange = 0f..100f,
                steps = 19,
                modifier = Modifier.weight(1f)
            )
            Text(
                stringResource(R.string.charge_bar_marker_value, value),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.End,
                modifier = Modifier.widthIn(min = 52.dp).padding(start = 8.dp)
            )
        }
    }
}
