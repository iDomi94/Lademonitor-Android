package com.dominiqueherbrigpersonalteam.lademonitor.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.dominiqueherbrigpersonalteam.lademonitor.R
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.Provider
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ProviderPayload
import com.dominiqueherbrigpersonalteam.lademonitor.data.repo.AppRepository
import com.dominiqueherbrigpersonalteam.lademonitor.data.settings.AppSettings
import com.dominiqueherbrigpersonalteam.lademonitor.ui.common.Fmt
import com.dominiqueherbrigpersonalteam.lademonitor.ui.common.FullScreenModal
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProvidersSettingsScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    var providers by remember { mutableStateOf<List<Provider>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Provider?>(null) }
    var pendingDelete by remember { mutableStateOf<Provider?>(null) }
    val serverAddressRequiredMessage = stringResource(R.string.error_server_address_required)

    suspend fun load() {
        if (!AppSettings.isReadyForDataAccess) { errorMessage = serverAddressRequiredMessage; return }
        try { providers = AppRepository.fetchProviders(); errorMessage = null }
        catch (e: Exception) { if (providers.isEmpty()) errorMessage = e.localizedMessage }
    }
    LaunchedEffect(Unit) { load() }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.settings_nav_providers)) },
            navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
            actions = { IconButton(onClick = { showAdd = true }) { Icon(Icons.Filled.Add, stringResource(R.string.sessions_add_content_description)) } }
        )
    }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            errorMessage?.let { item { Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            items(providers, key = { it.id }) { provider ->
                Row(Modifier.fillMaxWidth().clickable { editing = provider }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(provider.name, style = MaterialTheme.typography.bodyLarge)
                        val prices = listOfNotNull(
                            provider.lastPriceAcPerKwh?.let { Fmt.n("AC %.3f €", it) },
                            provider.lastPriceDcPerKwh?.let { Fmt.n("DC %.3f €", it) }
                        ).joinToString(" · ")
                        if (prices.isNotEmpty()) Text(prices, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { pendingDelete = provider }) { Icon(Icons.Filled.Delete, stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.error) }
                }
                HorizontalDivider()
            }
        }
    }

    if (showAdd) AddEditProviderModal(null, onDismiss = { showAdd = false }) { showAdd = false; scope.launch { load() } }
    editing?.let { p ->
        AddEditProviderModal(
            p,
            onDismiss = { editing = null },
            onOpenTariffCalculator = { editing = null; navController.navigate("tools/tariff?providerId=${p.id}") }
        ) { editing = null; scope.launch { load() } }
    }

    pendingDelete?.let { p ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.provider_delete_confirm_title)) },
            text = { Text(stringResource(R.string.provider_delete_confirm_message, p.name)) },
            confirmButton = {
                TextButton(onClick = {
                    val target = p; pendingDelete = null
                    scope.launch { runCatching { AppRepository.deleteProvider(target.id) }; load() }
                }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditProviderModal(
    provider: Provider?,
    onDismiss: () -> Unit,
    /** Sprung in den Tarifrechner mit den Werten dieses Anbieters (nur beim Bearbeiten). */
    onOpenTariffCalculator: (() -> Unit)? = null,
    onSaved: (Provider) -> Unit
) {
    val scope = rememberCoroutineScope()
    val isEditing = provider != null
    var name by remember { mutableStateOf(provider?.name ?: "") }
    var priceAc by remember { mutableStateOf(provider?.lastPriceAcPerKwh?.let { Fmt.n("%.4f", it) } ?: "") }
    var priceDc by remember { mutableStateOf(provider?.lastPriceDcPerKwh?.let { Fmt.n("%.4f", it) } ?: "") }
    var notes by remember { mutableStateOf(provider?.notes ?: "") }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val canSave = name.trim().isNotEmpty()

    fun save() {
        scope.launch {
            isSaving = true; errorMessage = null
            val payload = ProviderPayload(
                name = name.trim(),
                lastPriceAcPerKwh = priceAc.replace(",", ".").toDoubleOrNull(),
                lastPriceDcPerKwh = priceDc.replace(",", ".").toDoubleOrNull(),
                notes = notes.trim().ifEmpty { null }
            )
            try {
                val saved = if (provider != null) AppRepository.updateProvider(provider.id, payload)
                else AppRepository.createProvider(payload)
                onSaved(saved)
            } catch (e: Exception) { errorMessage = e.localizedMessage }
            isSaving = false
        }
    }

    FullScreenModal(onDismiss = onDismiss) {
        Scaffold(topBar = {
            TopAppBar(
                title = { Text(if (isEditing) stringResource(R.string.provider_edit_title) else stringResource(R.string.provider_add_title)) },
                navigationIcon = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
                actions = { TextButton(onClick = { save() }, enabled = !isSaving && canSave) { Text(if (isSaving) stringResource(R.string.action_saving) else stringResource(R.string.action_save)) } }
            )
        }) { padding ->
            Column(Modifier.padding(padding).padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.field_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = priceAc, onValueChange = { priceAc = it }, label = { Text(stringResource(R.string.provider_field_price_ac)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = priceDc, onValueChange = { priceDc = it }, label = { Text(stringResource(R.string.provider_field_price_dc)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.provider_price_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text(stringResource(R.string.field_notes)) }, modifier = Modifier.fillMaxWidth())
                if (onOpenTariffCalculator != null) {
                    TextButton(onClick = onOpenTariffCalculator) { Text(stringResource(R.string.provider_tariff_link)) }
                    Text(stringResource(R.string.provider_tariff_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}
