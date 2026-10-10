package com.dominiqueherbrigpersonalteam.lademonitor.ui.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.dominiqueherbrigpersonalteam.lademonitor.R
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.Reminder
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ReminderKind
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ReminderPayload
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ReminderStatus
import com.dominiqueherbrigpersonalteam.lademonitor.data.repo.AppRepository
import com.dominiqueherbrigpersonalteam.lademonitor.data.repo.LocalReminderCalculator
import com.dominiqueherbrigpersonalteam.lademonitor.data.repo.ReminderNotifier
import com.dominiqueherbrigpersonalteam.lademonitor.data.settings.AppSettings
import com.dominiqueherbrigpersonalteam.lademonitor.ui.common.Fmt
import com.dominiqueherbrigpersonalteam.lademonitor.ui.common.FullScreenModal
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.abs

/**
 * Erinnerungen eines Fahrzeugs (HU, Inspektion, Reifenwechsel ...), erreichbar ueber
 * Einstellungen -> Fahrzeuge -> Fahrzeug -> Erinnerungen und ueber die Karte oben im Dashboard.
 * In beiden Modi verfuegbar: Status, km-Prognose und "Erledigt" rechnet
 * [LocalReminderCalculator] - wie `reminders.py` im Server.
 *
 * Dringendste zuerst (ueberfaellig, bald faellig, dann nach naechstem Datum). "Erledigt" schreibt
 * die naechste Faelligkeit fort und bietet danach an, die Kosten gleich einzutragen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleRemindersScreen(navController: NavController, vehicleId: String) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var vehicleName by remember { mutableStateOf<String?>(null) }
    var entries by remember { mutableStateOf<List<LocalReminderCalculator.Evaluated>>(emptyList()) }
    var mileage by remember { mutableStateOf(LocalReminderCalculator.Mileage(null, null)) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Reminder?>(null) }
    var pendingDelete by remember { mutableStateOf<Reminder?>(null) }
    var completing by remember { mutableStateOf<LocalReminderCalculator.Evaluated?>(null) }
    // Nach "Erledigt": Angebot, die Kosten einzutragen, und danach das vorbelegte Formular.
    var costOffer by remember { mutableStateOf<VehicleCostPrefill?>(null) }
    var costForm by remember { mutableStateOf<VehicleCostPrefill?>(null) }
    val serverAddressRequiredMessage = stringResource(R.string.error_server_address_required)

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        // Egal wie entschieden: neu planen (ohne Berechtigung zeigt der Receiver einfach nichts an).
        ReminderNotifier.rescheduleAsync()
    }

    suspend fun load() {
        if (!AppSettings.isReadyForDataAccess) { errorMessage = serverAddressRequiredMessage; return }
        try {
            entries = AppRepository.fetchEvaluatedReminders(vehicleId)
            vehicleName = AppRepository.fetchVehicles().firstOrNull { it.id == vehicleId }?.name
            mileage = AppRepository.vehicleMileage(vehicleId)
            errorMessage = null
        } catch (e: Exception) { if (entries.isEmpty()) errorMessage = e.localizedMessage }
        loaded = true
    }
    LaunchedEffect(vehicleId) { load() }

    /** Beim ersten Speichern einer Erinnerung nach der Mitteilungs-Berechtigung fragen (Android 13+). */
    fun afterSave() {
        if (ReminderNotifier.shouldRequestPermission(context)) {
            ReminderNotifier.markPermissionRequested()
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        scope.launch { load() }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = {
                Text(vehicleName?.let { stringResource(R.string.reminders_title_named, it) } ?: stringResource(R.string.reminders_title))
            },
            navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
            actions = {
                IconButton(onClick = { showAdd = true }, enabled = loaded && vehicleName != null) {
                    Icon(Icons.Filled.Add, stringResource(R.string.reminders_add_title))
                }
            }
        )
    }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            errorMessage?.let { item { Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            if (loaded && entries.isEmpty() && errorMessage == null) {
                item {
                    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.reminders_empty_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.reminders_empty_message),
                            Modifier.padding(top = 4.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            items(entries, key = { it.reminder.id }) { item ->
                Row(
                    Modifier.fillMaxWidth().clickable { editing = item.reminder }.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ReminderRow(item, Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        TextButton(onClick = { completing = item }) { Text(stringResource(R.string.reminder_action_done)) }
                        IconButton(onClick = { pendingDelete = item.reminder }) {
                            Icon(Icons.Filled.Delete, stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                HorizontalDivider()
            }
            item {
                Text(
                    stringResource(R.string.reminders_footer),
                    Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showAdd) {
        AddEditReminderModal(null, vehicleId, mileage.currentKm, onDismiss = { showAdd = false }) { showAdd = false; afterSave() }
    }
    editing?.let { r ->
        AddEditReminderModal(r, vehicleId, mileage.currentKm, onDismiss = { editing = null }) { editing = null; afterSave() }
    }

    completing?.let { item ->
        CompleteReminderDialog(
            item = item,
            onDismiss = { completing = null },
            onConfirm = { day, km ->
                completing = null
                scope.launch {
                    try {
                        AppRepository.completeReminder(item.reminder, day, km)
                        item.reminder.kindValue.costCategory?.let { category ->
                            costOffer = VehicleCostPrefill(category, day, km, item.reminder.displayTitle)
                        }
                    } catch (e: Exception) { errorMessage = e.localizedMessage }
                    load()
                }
            }
        )
    }

    costOffer?.let { prefill ->
        AlertDialog(
            onDismissRequest = { costOffer = null },
            title = { Text(stringResource(R.string.reminder_cost_offer_title)) },
            text = { Text(stringResource(R.string.reminder_cost_offer_message, prefill.label ?: "")) },
            confirmButton = {
                TextButton(onClick = { costOffer = null; costForm = prefill }) { Text(stringResource(R.string.reminder_cost_offer_action)) }
            },
            dismissButton = { TextButton(onClick = { costOffer = null }) { Text(stringResource(R.string.reminder_cost_offer_skip)) } }
        )
    }
    costForm?.let { prefill ->
        AddEditVehicleCostModal(null, vehicleId, prefill = prefill, onDismiss = { costForm = null }) { costForm = null }
    }

    pendingDelete?.let { r ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.reminder_delete_confirm_title)) },
            text = { Text(stringResource(R.string.reminder_delete_confirm_message, r.displayTitle)) },
            confirmButton = {
                TextButton(onClick = {
                    val target = r; pendingDelete = null
                    scope.launch {
                        try { AppRepository.deleteReminder(target.id) } catch (e: Exception) { errorMessage = e.localizedMessage }
                        load()
                    }
                }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }
}

/** Farbe je Status: ok gruen, bald faellig orange, ueberfaellig rot, erledigt grau. */
fun reminderStatusColor(status: ReminderStatus): Color = when (status) {
    ReminderStatus.OK -> Color(0xFF2EA87F)
    ReminderStatus.DUE_SOON -> Color(0xFFE8912D)
    ReminderStatus.OVERDUE -> Color(0xFFE5534B)
    ReminderStatus.DONE -> Color(0xFF8B98A9)
}

@Composable
fun ReminderStatusChip(status: ReminderStatus) {
    val color = reminderStatusColor(status)
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.18f))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(stringResource(status.labelRes), style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * Faelligkeit in Worten: Datum ("in 12 Tagen", "heute", "seit 3 Tagen"), km ("noch 800 km",
 * "200 km drueber") und die km-Prognose. Ohne jede Faelligkeit "erledigt am" bzw. ein Hinweis.
 */
@Composable
fun reminderDueLines(item: LocalReminderCalculator.Evaluated): List<String> {
    val r = item.reminder
    val s = item.state
    val lines = mutableListOf<String>()
    val dueDate = r.dueDate
    val days = s.daysRemaining
    if (dueDate != null && days != null) {
        val date = Fmt.dateMedium(dueDate)
        lines += when {
            days > 0 -> pluralStringResource(R.plurals.reminder_due_in_days, days.toInt(), days.toInt(), date)
            days == 0L -> stringResource(R.string.reminder_due_today, date)
            else -> pluralStringResource(R.plurals.reminder_overdue_days, abs(days).toInt(), abs(days).toInt(), date)
        }
    }
    val dueKm = r.dueKm
    if (dueKm != null) {
        val remaining = s.kmRemaining
        lines += when {
            remaining == null -> stringResource(R.string.reminder_due_km, Fmt.km(dueKm))
            remaining > 0 -> stringResource(R.string.reminder_due_km_remaining, Fmt.km(dueKm), Fmt.km(remaining))
            remaining == 0 -> stringResource(R.string.reminder_due_km_reached, Fmt.km(dueKm))
            else -> stringResource(R.string.reminder_due_km_over, Fmt.km(dueKm), Fmt.km(-remaining))
        }
        val forecast = s.kmForecastDate
        if (forecast != null && remaining != null && remaining > 0) {
            lines += stringResource(R.string.reminder_km_forecast, Fmt.dateMedium(LocalReminderCalculator.epochMillis(forecast)))
        }
    }
    if (dueDate == null && dueKm == null) {
        lines += if (s.status == ReminderStatus.DONE) stringResource(R.string.reminder_done_no_repeat)
        else stringResource(R.string.reminder_no_due)
    }
    return lines
}

@Composable
private fun reminderRepeatText(r: Reminder): String? {
    val parts = buildList {
        r.repeatMonths?.let { add(pluralStringResource(R.plurals.reminder_repeat_months, it, it)) }
        r.repeatKm?.let { add(stringResource(R.string.reminder_repeat_km, Fmt.km(it))) }
    }
    if (parts.isEmpty()) return null
    return stringResource(R.string.reminder_repeat, parts.joinToString(stringResource(R.string.reminder_repeat_or)))
}

@Composable
private fun ReminderRow(item: LocalReminderCalculator.Evaluated, modifier: Modifier = Modifier) {
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    val r = item.reminder
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(r.displayTitle, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(8.dp))
            ReminderStatusChip(item.state.status)
        }
        reminderDueLines(item).forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = secondary) }
        reminderRepeatText(r)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = secondary) }
        r.lastDoneDate?.let { done ->
            val text = r.lastDoneKm?.let { stringResource(R.string.reminder_last_done_km, Fmt.dateMedium(done), Fmt.km(it)) }
                ?: stringResource(R.string.reminder_last_done, Fmt.dateMedium(done))
            Text(text, style = MaterialTheme.typography.bodySmall, color = secondary)
        }
        r.notes?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = secondary) }
    }
}

/** Datum (Standard heute) und km-Stand (Standard der juengste aus den Ladevorgaengen). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompleteReminderDialog(
    item: LocalReminderCalculator.Evaluated,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, Int?) -> Unit
) {
    var day by remember { mutableStateOf(LocalDate.now()) }
    var km by remember { mutableStateOf(item.mileage.currentKm?.toString() ?: "") }
    var picking by remember { mutableStateOf(false) }
    val parsedKm = km.trim().toIntOrNull()
    val kmOk = km.isBlank() || (parsedKm != null && parsedKm >= 0)
    val (nextDay, nextKm) = LocalReminderCalculator.nextDue(item.reminder, day, if (kmOk) parsedKm else null)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reminder_complete_title, item.reminder.displayTitle)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.reminder_complete_date) + ": " + Fmt.dateMedium(LocalReminderCalculator.epochMillis(day)))
                }
                OutlinedTextField(
                    value = km, onValueChange = { km = it }, label = { Text(stringResource(R.string.reminder_complete_km)) },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = !kmOk, modifier = Modifier.fillMaxWidth()
                )
                val next = listOfNotNull(
                    nextDay?.let { Fmt.dateMedium(LocalReminderCalculator.epochMillis(it)) },
                    nextKm?.let { Fmt.km(it) }
                ).joinToString(stringResource(R.string.reminder_repeat_or))
                Text(
                    if (next.isEmpty()) stringResource(R.string.reminder_complete_no_next)
                    else stringResource(R.string.reminder_complete_next, next),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(day, parsedKm) }, enabled = kmOk) { Text(stringResource(R.string.reminder_action_done)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )

    if (picking) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { day = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    picking = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.action_cancel)) } }
        ) { DatePicker(state = state) }
    }
}

/**
 * Erinnerung anlegen/bearbeiten. Beim Anlegen fuellt die Vorlage (Art) Wiederholung und - bei
 * Reifenwechseln - den festen ersten Faelligkeitstag vor; alles bleibt aenderbar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditReminderModal(
    reminder: Reminder?,
    vehicleId: String,
    currentKm: Int?,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val isEditing = reminder != null
    val initialKind = reminder?.kindValue ?: ReminderKind.HU

    /** Vorschlag fuer das Datum einer neuen Erinnerung: fester Tag, sonst heute plus Wiederholung. */
    fun suggestedDate(kind: ReminderKind): LocalDate =
        LocalReminderCalculator.firstDueDate(kind) ?: LocalDate.now().plusMonths((kind.repeatMonths ?: 1).toLong())

    var kind by remember { mutableStateOf(initialKind) }
    var title by remember { mutableStateOf(reminder?.title ?: "") }
    var hasDueDate by remember { mutableStateOf(reminder?.let { it.dueDate != null } ?: true) }
    var dueDate by remember {
        mutableStateOf(reminder?.dueDate?.let { LocalReminderCalculator.day(it) } ?: suggestedDate(initialKind))
    }
    var dueKm by remember {
        mutableStateOf(
            reminder?.dueKm?.toString()
                ?: initialKind.repeatKm?.let { rk -> currentKm?.let { (it + rk).toString() } } ?: ""
        )
    }
    var repeatMonths by remember { mutableStateOf((reminder?.repeatMonths ?: if (reminder == null) initialKind.repeatMonths else null)?.toString() ?: "") }
    var repeatKm by remember { mutableStateOf((reminder?.repeatKm ?: if (reminder == null) initialKind.repeatKm else null)?.toString() ?: "") }
    var leadDays by remember { mutableStateOf((reminder?.leadDays ?: 30).toString()) }
    var leadKm by remember { mutableStateOf((reminder?.leadKm ?: 1000).toString()) }
    var notes by remember { mutableStateOf(reminder?.notes ?: "") }
    var kindMenu by remember { mutableStateOf(false) }
    var pickingDate by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun applyTemplate(newKind: ReminderKind) {
        kind = newKind
        if (isEditing) return
        // Nur beim Anlegen: Vorlage uebernehmen, inkl. Datum und km-Vorschlag.
        repeatMonths = newKind.repeatMonths?.toString() ?: ""
        repeatKm = newKind.repeatKm?.toString() ?: ""
        dueDate = suggestedDate(newKind)
        hasDueDate = true
        dueKm = newKind.repeatKm?.let { rk -> currentKm?.let { (it + rk).toString() } } ?: ""
    }

    fun parseOptional(text: String): Int? = text.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()
    fun optionalOk(text: String, min: Int, max: Int = Int.MAX_VALUE): Boolean {
        if (text.isBlank()) return true
        val v = text.trim().toIntOrNull() ?: return false
        return v in min..max
    }

    val parsedLeadDays = leadDays.trim().toIntOrNull()
    val parsedLeadKm = leadKm.trim().toIntOrNull()
    val canSave = optionalOk(dueKm, 0) && optionalOk(repeatMonths, 1, 240) && optionalOk(repeatKm, 1) &&
        parsedLeadDays != null && parsedLeadDays in 0..365 && parsedLeadKm != null && parsedLeadKm >= 0

    fun save() {
        val ld = parsedLeadDays ?: return
        val lk = parsedLeadKm ?: return
        scope.launch {
            isSaving = true; errorMessage = null
            val payload = ReminderPayload(
                vehicleId = reminder?.vehicleId ?: vehicleId,
                kind = kind.raw,
                title = title.trim().ifEmpty { null },
                dueDate = if (hasDueDate) LocalReminderCalculator.epochMillis(dueDate) else null,
                dueKm = parseOptional(dueKm),
                repeatMonths = parseOptional(repeatMonths),
                repeatKm = parseOptional(repeatKm),
                leadDays = ld,
                leadKm = lk,
                notes = notes.trim().ifEmpty { null },
                lastDoneDate = reminder?.lastDoneDate,
                lastDoneKm = reminder?.lastDoneKm
            )
            try {
                if (reminder != null) AppRepository.updateReminder(reminder.id, payload)
                else AppRepository.createReminder(payload)
                onSaved()
            } catch (e: Exception) { errorMessage = e.localizedMessage }
            isSaving = false
        }
    }

    FullScreenModal(onDismiss = onDismiss) {
        Scaffold(topBar = {
            TopAppBar(
                title = { Text(if (isEditing) stringResource(R.string.reminders_edit_title) else stringResource(R.string.reminders_add_title)) },
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
                    OutlinedButton(onClick = { kindMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.reminder_field_kind) + ": " + stringResource(kind.labelRes))
                    }
                    DropdownMenu(expanded = kindMenu, onDismissRequest = { kindMenu = false }) {
                        ReminderKind.entries.forEach { option ->
                            DropdownMenuItem(text = { Text(stringResource(option.labelRes)) }, onClick = { applyTemplate(option); kindMenu = false })
                        }
                    }
                }
                if (!isEditing) {
                    Text(stringResource(R.string.reminder_template_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text(stringResource(R.string.reminder_field_title)) },
                    placeholder = { Text(stringResource(kind.labelRes)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )

                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.reminder_field_has_due_date), Modifier.weight(1f))
                    Switch(checked = hasDueDate, onCheckedChange = { hasDueDate = it })
                }
                if (hasDueDate) {
                    OutlinedButton(onClick = { pickingDate = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.reminder_field_due_date) + ": " + Fmt.dateMedium(LocalReminderCalculator.epochMillis(dueDate)))
                    }
                }
                NumberField(dueKm, { dueKm = it }, stringResource(R.string.reminder_field_due_km), optionalOk(dueKm, 0))
                currentKm?.let {
                    Text(stringResource(R.string.reminder_current_km, Fmt.km(it)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                NumberField(repeatMonths, { repeatMonths = it }, stringResource(R.string.reminder_field_repeat_months), optionalOk(repeatMonths, 1, 240))
                NumberField(repeatKm, { repeatKm = it }, stringResource(R.string.reminder_field_repeat_km), optionalOk(repeatKm, 1))
                NumberField(leadDays, { leadDays = it }, stringResource(R.string.reminder_field_lead_days), parsedLeadDays != null && parsedLeadDays in 0..365)
                NumberField(leadKm, { leadKm = it }, stringResource(R.string.reminder_field_lead_km), parsedLeadKm != null && parsedLeadKm >= 0)
                Text(stringResource(R.string.reminder_lead_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text(stringResource(R.string.field_notes)) }, modifier = Modifier.fillMaxWidth())
                errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
    }

    if (pickingDate) {
        // Der Picker rechnet in UTC-Mitternacht - hin und zurueck ueber UTC (wie bei den Kosten).
        val state = rememberDatePickerState(
            initialSelectedDateMillis = dueDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { dueDate = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    pickingDate = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text(stringResource(R.string.action_cancel)) } }
        ) { DatePicker(state = state) }
    }
}

@Composable
private fun NumberField(value: String, onChange: (String) -> Unit, label: String, valid: Boolean) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) },
        singleLine = true, isError = !valid,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
    )
}
