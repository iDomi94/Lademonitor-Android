package com.dominiqueherbrigpersonalteam.lademonitor.ui.tools

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.Provider
import com.dominiqueherbrigpersonalteam.lademonitor.data.repo.AppRepository
import com.dominiqueherbrigpersonalteam.lademonitor.data.repo.TariffCalculator
import com.dominiqueherbrigpersonalteam.lademonitor.data.repo.TariffCalculatorSettings
import com.dominiqueherbrigpersonalteam.lademonitor.data.settings.AppSettings
import com.dominiqueherbrigpersonalteam.lademonitor.ui.common.Fmt

/**
 * Welche Anbieter zum automatischen Vergleichspreis des Tarifrechners zaehlen - Gegenstueck zu
 * `PublicProvidersView.swift`. Gespeichert wird lokal ([TariffCalculatorSettings]), bewusst ohne
 * Sync. Neben jedem Anbieter steht, was dort im letzten Jahr im Schnitt bezahlt wurde - die eigene
 * Wallbox faellt zwischen den oeffentlichen Preisen meist sofort auf.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublicProvidersScreen(navController: NavController) {
    var providers by remember { mutableStateOf<List<Provider>>(emptyList()) }
    var excluded by remember { mutableStateOf<Set<String>>(emptySet()) }
    var paid by remember { mutableStateOf<Map<String, TariffCalculator.PaidPrice>>(emptyMap()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val serverAddressRequiredMessage = stringResource(R.string.error_server_address_required)

    LaunchedEffect(Unit) {
        if (!AppSettings.isReadyForDataAccess) { errorMessage = serverAddressRequiredMessage; return@LaunchedEffect }
        try {
            val basis = AppRepository.tariffBasis()
            providers = basis.providers.sortedBy { it.name.lowercase() }
            // Nur die IDs vorhandener Anbieter behalten - so faellt beim naechsten Speichern auch
            // eine alte lokale ID heraus.
            excluded = basis.excludedProviderIds intersect providers.map { it.id }.toSet()
            paid = providers.mapNotNull { p ->
                TariffCalculator.paidPrice(basis.sessions) { it.providerId == p.id }?.let { p.id to it }
            }.toMap()
            errorMessage = null
        } catch (e: Exception) {
            errorMessage = e.localizedMessage
        }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.public_providers_title)) },
            navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                }
            }
        )
    }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            errorMessage?.let { item { Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            items(providers, key = { it.id }) { provider ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(provider.name, style = MaterialTheme.typography.bodyLarge)
                        val price = paid[provider.id]
                        Text(
                            if (price != null) stringResource(
                                R.string.public_providers_paid,
                                Fmt.n("%.2f", price.pricePerKwh) + " €/kWh",
                                price.sessionCount
                            ) else stringResource(R.string.public_providers_no_data),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = provider.id !in excluded,
                        onCheckedChange = { isOn ->
                            excluded = if (isOn) excluded - provider.id else excluded + provider.id
                            TariffCalculatorSettings.setExcluded(excluded)
                        }
                    )
                }
                HorizontalDivider()
            }
            item {
                Text(
                    stringResource(R.string.public_providers_footer),
                    Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
