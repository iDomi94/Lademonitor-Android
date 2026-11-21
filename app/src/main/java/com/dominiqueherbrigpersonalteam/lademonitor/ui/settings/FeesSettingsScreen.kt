package com.dominiqueherbrigpersonalteam.lademonitor.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.dominiqueherbrigpersonalteam.lademonitor.R
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.FeeInterval
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.Provider
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ProviderFee
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ProviderFeePayload
import com.dominiqueherbrigpersonalteam.lademonitor.data.repo.AppRepository
import com.dominiqueherbrigpersonalteam.lademonitor.data.repo.LocalFeeAllocator
import com.dominiqueherbrigpersonalteam.lademonitor.data.settings.AppSettings
import com.dominiqueherbrigpersonalteam.lademonitor.data.settings.ServerCapabilities
import com.dominiqueherbrigpersonalteam.lademonitor.data.settings.ServerFeature
import com.dominiqueherbrigpersonalteam.lademonitor.ui.common.Fmt
import com.dominiqueherbrigpersonalteam.lademonitor.ui.common.FullScreenModal
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Grundgebuehren und Abos der Anbieter (z.B. Ionity Powerpass 15 EUR im Monat).
 *
 * Anders als die Reifen in beiden Modi erreichbar: die Umlage rechnet die App selbst
 * ([LocalFeeAllocator]), weil sie in die lokal berechneten Dashboard-Kosten eingeht.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeesSettingsScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    var fees by remember { mutableStateOf<List<ProviderFee>>(emptyList()) }
    var providers by remember { mutableStateOf<List<Provider>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ProviderFee?>(null) }
    var pendingDelete by remember { mutableStateOf<ProviderFee?>(null) }
    val serverAddressRequiredMessage = stringResource(R.string.error_server_address_required)
    val needProviderMessage = stringResource(R.string.fees_need_provider)
    val unknownProvider = stringResource(R.string.fees_unknown_provider)

    suspend fun load() {
        if (!AppSettings.isReadyForDataAccess) { errorMessage = serverAddressRequiredMessage; return }
        try {
            providers = AppRepository.fetchProviders()
            fees = AppRepository.fetchFees()
            errorMessage = if (providers.isEmpty()) needProviderMessage else null
        } catch (e: Exception) { if (fees.isEmpty()) errorMessage = e.localizedMessage }
        loaded = true
    }
    LaunchedEffect(Unit) { load() }

    fun providerName(id: String) = providers.firstOrNull { it.id == id }?.name ?: unknownProvider

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.settings_nav_fees)) },
            navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
            actions = {
                IconButton(onClick = { showAdd = true }, enabled = providers.isNotEmpty()) {
                    Icon(Icons.Filled.Add, stringResource(R.string.sessions_add_content_description))
                }
            }
        )
    }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            errorMessage?.let { item { Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            if (loaded && fees.isEmpty() && errorMessage == null) {
                item {
                    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.fees_empty_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.fees_empty_message),
                            Modifier.padding(top = 4.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            items(fees, key = { it.id }) { fee ->
                Row(Modifier.fillMaxWidth().clickable { editing = fee }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    FeeRow(fee, providerName(fee.providerId), Modifier.weight(1f))
                    IconButton(onClick = { pendingDelete = fee }) { Icon(Icons.Filled.Delete, stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.error) }
                }
                HorizontalDivider()
            }
            item {
                Text(
                    stringResource(R.string.fees_footer),
                    Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showAdd) AddEditFeeModal(null, providers, onDismiss = { showAdd = false }) { showAdd = false; scope.launch { load() } }
    editing?.let { f -> AddEditFeeModal(f, providers, onDismiss = { editing = null }) { editing = null; scope.launch { load() } } }

    pendingDelete?.let { f ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.fees_delete_confirm_title)) },
            text = { Text(stringResource(R.string.fees_delete_confirm_message, f.label ?: providerName(f.providerId))) },
            confirmButton = {
                TextButton(onClick = {
                    val target = f; pendingDelete = null
                    scope.launch {
                        try { AppRepository.deleteFee(target.id) } catch (e: Exception) { errorMessage = e.localizedMessage }
                        load()
                    }
                }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }
}

@Composable
private fun FeeRow(fee: ProviderFee, providerName: String, modifier: Modifier = Modifier) {
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                fee.label?.let { "$providerName · $it" } ?: providerName,
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                Fmt.n("%.2f €", fee.amount) + " " +
                    stringResource(if (fee.isCredit) R.string.fees_per_credit else fee.intervalValue.perRes),
                style = MaterialTheme.typography.bodyLarge,
                color = if (fee.isCredit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }
        val start = Fmt.dateMedium(fee.startDate)
        val once = fee.intervalValue == FeeInterval.ONCE
        Text(
            when {
                once && fee.singleSession -> stringResource(R.string.fees_next_session_since, start)
                once && fee.spreadMonths != null && fee.endDate != null ->
                    "$start – ${Fmt.dateMedium(fee.endDate)} · " +
                        pluralStringResource(R.plurals.fees_months_count, fee.spreadMonths, fee.spreadMonths)
                else -> fee.endDate?.let { "$start – ${Fmt.dateMedium(it)}" } ?: stringResource(R.string.fees_since, start)
            },
            style = MaterialTheme.typography.bodySmall,
            color = secondary
        )
        Text(
            stringResource(R.string.fees_charged_to_date, Fmt.n("%.2f €", LocalFeeAllocator.chargedToDate(fee))),
            style = MaterialTheme.typography.bodySmall,
            color = secondary
        )
    }
}

private enum class FeeDateField { START, END }

/** Gebuehr oder Gutschrift - gespeichert wird nur das Vorzeichen des Betrags. */
private enum class FeeKind(val labelRes: Int) {
    FEE(R.string.fees_kind_fee),
    CREDIT(R.string.fees_kind_credit)
}

/** Worauf ein einmaliger Betrag verteilt wird (Server ab 0.56.0). */
private enum class FeeSpread(val labelRes: Int) {
    SESSION(R.string.fees_spread_session),
    MONTHS(R.string.fees_spread_months),
    RANGE(R.string.fees_spread_range)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditFeeModal(fee: ProviderFee?, providers: List<Provider>, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val scope = rememberCoroutineScope()
    val zone = ZoneId.systemDefault()
    val isEditing = fee != null
    val initialStart = fee?.let { LocalFeeAllocator.day(it.startDate) } ?: LocalDate.now()

    var providerId by remember { mutableStateOf(fee?.providerId ?: providers.firstOrNull()?.id ?: "") }
    var label by remember { mutableStateOf(fee?.label ?: "") }
    var kind by remember { mutableStateOf(if (fee?.isCredit == true) FeeKind.CREDIT else FeeKind.FEE) }
    var amount by remember { mutableStateOf(fee?.amount?.let { Fmt.n("%.2f", kotlin.math.abs(it)) } ?: "") }
    var interval by remember { mutableStateOf(fee?.intervalValue ?: FeeInterval.MONTHLY) }
    var spread by remember {
        mutableStateOf(
            when {
                fee?.singleSession == true -> FeeSpread.SESSION
                fee?.spreadMonths != null -> FeeSpread.MONTHS
                else -> FeeSpread.RANGE
            }
        )
    }
    var months by remember { mutableStateOf(fee?.spreadMonths ?: 1) }
    var kindMenu by remember { mutableStateOf(false) }
    var spreadMenu by remember { mutableStateOf(false) }
    var startDate by remember { mutableStateOf(initialStart) }
    var hasEndDate by remember { mutableStateOf(fee?.endDate != null) }
    // Vorschlag: ein Monat ab Start, letzter Tag inklusive (03.05. -> 02.06.).
    var endDate by remember {
        mutableStateOf(fee?.endDate?.let { LocalFeeAllocator.day(it) } ?: initialStart.plusMonths(1).minusDays(1))
    }
    var notes by remember { mutableStateOf(fee?.notes ?: "") }
    var providerMenu by remember { mutableStateOf(false) }
    var intervalMenu by remember { mutableStateOf(false) }
    var pickingDate by remember { mutableStateOf<FeeDateField?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Gutschriften und "eine Ladung"/"x Monate" brauchen Server 0.56.0 - sonst sichtbar, aber gesperrt.
    val serverVersion by ServerCapabilities.serverVersion.collectAsState()
    val creditLocked = remember(serverVersion) { !ServerCapabilities.supports(ServerFeature.PROVIDER_CREDIT) }
    val effectiveSpread = if (creditLocked) FeeSpread.RANGE else spread
    val once = interval == FeeInterval.ONCE

    // Einmalige Betraege auf einen Zeitraum brauchen ein Enddatum - sonst waere der Zeitraum ein
    // einziger Tag. Der Server verlangt das genauso (422).
    val needsEndDate = once && effectiveSpread == FeeSpread.RANGE
    val usesEndDate = (hasEndDate && !once) || needsEndDate
    val parsedAmount = amount.replace(",", ".").toDoubleOrNull()
    val canSave = providerId.isNotEmpty() && parsedAmount != null && parsedAmount > 0 &&
        !(kind == FeeKind.CREDIT && creditLocked) &&
        !(usesEndDate && endDate.isBefore(startDate))

    fun save() {
        val value = parsedAmount ?: return
        scope.launch {
            isSaving = true; errorMessage = null
            val payload = ProviderFeePayload(
                providerId = providerId,
                amount = if (kind == FeeKind.CREDIT) -kotlin.math.abs(value) else kotlin.math.abs(value),
                interval = interval.wire,
                startDate = startDate.atStartOfDay(zone).toInstant().toEpochMilli(),
                endDate = if (usesEndDate) endDate.atStartOfDay(zone).toInstant().toEpochMilli() else null,
                label = label.trim().ifEmpty { null },
                notes = notes.trim().ifEmpty { null },
                singleSession = once && effectiveSpread == FeeSpread.SESSION,
                spreadMonths = if (once && effectiveSpread == FeeSpread.MONTHS) months else null
            ).normalized()
            try {
                if (fee != null) AppRepository.updateFee(fee.id, payload)
                else AppRepository.createFee(payload)
                onSaved()
            } catch (e: Exception) { errorMessage = e.localizedMessage }
            isSaving = false
        }
    }

    FullScreenModal(onDismiss = onDismiss) {
        Scaffold(topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            when {
                                kind == FeeKind.CREDIT && isEditing -> R.string.fees_credit_edit_title
                                kind == FeeKind.CREDIT -> R.string.fees_credit_add_title
                                isEditing -> R.string.fees_edit_title
                                else -> R.string.fees_add_title
                            }
                        )
                    )
                },
                navigationIcon = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
                actions = {
                    TextButton(onClick = { save() }, enabled = !isSaving && canSave) {
                        Text(if (isSaving) stringResource(R.string.action_saving) else stringResource(R.string.action_save))
                    }
                }
            )
        }) { padding ->
            Column(
                Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box {
                    OutlinedButton(onClick = { providerMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(providers.firstOrNull { it.id == providerId }?.name ?: stringResource(R.string.entity_provider))
                    }
                    DropdownMenu(expanded = providerMenu, onDismissRequest = { providerMenu = false }) {
                        providers.forEach { provider ->
                            DropdownMenuItem(text = { Text(provider.name) }, onClick = { providerId = provider.id; providerMenu = false })
                        }
                    }
                }
                OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text(stringResource(R.string.fees_field_label)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Box {
                    OutlinedButton(
                        onClick = { kindMenu = true },
                        enabled = !(creditLocked && kind == FeeKind.FEE),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(kind.labelRes))
                    }
                    DropdownMenu(expanded = kindMenu, onDismissRequest = { kindMenu = false }) {
                        FeeKind.entries.forEach { option ->
                            DropdownMenuItem(text = { Text(stringResource(option.labelRes)) }, onClick = {
                                kind = option; kindMenu = false
                                // Eine Gutschrift ist fast immer einmalig.
                                if (option == FeeKind.CREDIT && !isEditing) interval = FeeInterval.ONCE
                            })
                        }
                    }
                }
                if (creditLocked) {
                    Text(
                        stringResource(R.string.fees_credit_locked_prefix) + " " +
                            stringResource(R.string.server_feature_requires, ServerFeature.PROVIDER_CREDIT.minimumServerVersion),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedTextField(
                    value = amount, onValueChange = { amount = it }, label = { Text(stringResource(R.string.fees_field_amount)) },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth()
                )
                Box {
                    OutlinedButton(onClick = { intervalMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(interval.labelRes))
                    }
                    DropdownMenu(expanded = intervalMenu, onDismissRequest = { intervalMenu = false }) {
                        FeeInterval.entries.forEach { option ->
                            DropdownMenuItem(text = { Text(stringResource(option.labelRes)) }, onClick = { interval = option; intervalMenu = false })
                        }
                    }
                }
                if (once) {
                    Box {
                        OutlinedButton(onClick = { spreadMenu = true }, enabled = !creditLocked, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.fees_field_spread) + ": " + stringResource(effectiveSpread.labelRes))
                        }
                        DropdownMenu(expanded = spreadMenu, onDismissRequest = { spreadMenu = false }) {
                            FeeSpread.entries.forEach { option ->
                                DropdownMenuItem(text = { Text(stringResource(option.labelRes)) }, onClick = { spread = option; spreadMenu = false })
                            }
                        }
                    }
                    if (effectiveSpread == FeeSpread.MONTHS) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(pluralStringResource(R.plurals.fees_months_count, months, months), Modifier.weight(1f))
                            OutlinedButton(onClick = { if (months > 1) months-- }, enabled = months > 1) { Text("−") }
                            OutlinedButton(onClick = { if (months < 120) months++ }, enabled = months < 120, modifier = Modifier.padding(start = 8.dp)) { Text("+") }
                        }
                    }
                }
                Text(
                    stringResource(
                        when (interval) {
                            FeeInterval.MONTHLY -> R.string.fees_hint_monthly
                            FeeInterval.YEARLY -> R.string.fees_hint_yearly
                            FeeInterval.ONCE -> when (effectiveSpread) {
                                FeeSpread.SESSION -> R.string.fees_hint_once_session
                                FeeSpread.MONTHS -> R.string.fees_hint_once_months
                                FeeSpread.RANGE -> R.string.fees_hint_once
                            }
                        }
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val startLabel = stringResource(
                    when {
                        once && effectiveSpread == FeeSpread.SESSION -> R.string.fees_field_since_next_session
                        needsEndDate -> R.string.fees_field_from
                        else -> R.string.fees_field_since
                    }
                )
                OutlinedButton(onClick = { pickingDate = FeeDateField.START }, modifier = Modifier.fillMaxWidth()) {
                    Text("$startLabel: ${Fmt.dateMedium(startDate.atStartOfDay(zone).toInstant().toEpochMilli())}")
                }
                if (!once) {
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.fees_field_cancelled), Modifier.weight(1f))
                        Switch(checked = hasEndDate, onCheckedChange = { hasEndDate = it })
                    }
                }
                if (usesEndDate) {
                    val endLabel = stringResource(if (needsEndDate) R.string.fees_field_until else R.string.fees_field_last_day)
                    OutlinedButton(onClick = { pickingDate = FeeDateField.END }, modifier = Modifier.fillMaxWidth()) {
                        Text("$endLabel: ${Fmt.dateMedium(endDate.atStartOfDay(zone).toInstant().toEpochMilli())}")
                    }
                }
                if (once && effectiveSpread == FeeSpread.MONTHS) {
                    val zoneMillis = startDate.atStartOfDay(zone).toInstant().toEpochMilli()
                    Text(
                        stringResource(R.string.fees_until_inclusive, Fmt.dateMedium(ProviderFee.spreadEndDate(zoneMillis, months))),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!once) {
                    Text(
                        stringResource(R.string.fees_hint_price_change),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text(stringResource(R.string.field_notes)) }, modifier = Modifier.fillMaxWidth())
                errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
    }

    pickingDate?.let { field ->
        // Der Picker rechnet in UTC-Mitternacht - hin und zurueck ueber UTC, sonst rutscht der Tag
        // je nach Zeitzone um eins.
        val current = if (field == FeeDateField.START) startDate else endDate
        val state = rememberDatePickerState(
            initialSelectedDateMillis = current.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = null },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { picked ->
                        val date = Instant.ofEpochMilli(picked).atZone(ZoneOffset.UTC).toLocalDate()
                        if (field == FeeDateField.START) startDate = date else endDate = date
                    }
                    pickingDate = null
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { pickingDate = null }) { Text(stringResource(R.string.action_cancel)) } }
        ) { DatePicker(state = state) }
    }
}
