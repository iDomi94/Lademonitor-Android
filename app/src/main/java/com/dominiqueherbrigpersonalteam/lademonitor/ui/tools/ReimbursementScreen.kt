package com.dominiqueherbrigpersonalteam.lademonitor.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.dominiqueherbrigpersonalteam.lademonitor.R
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ReimbursementMode
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ReimbursementRate
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ReimbursementRatePayload
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.Vehicle
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.VehiclePayload
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.VehicleReimbursement
import com.dominiqueherbrigpersonalteam.lademonitor.data.repo.AppRepository
import com.dominiqueherbrigpersonalteam.lademonitor.data.repo.LocalReimbursementCalculator
import com.dominiqueherbrigpersonalteam.lademonitor.data.repo.LocalReimbursementCalculator.KwhSource
import com.dominiqueherbrigpersonalteam.lademonitor.data.repo.ReimbursementBasis
import com.dominiqueherbrigpersonalteam.lademonitor.data.settings.AppMode
import com.dominiqueherbrigpersonalteam.lademonitor.data.settings.AppSettings
import com.dominiqueherbrigpersonalteam.lademonitor.ui.common.Fmt
import com.dominiqueherbrigpersonalteam.lademonitor.ui.common.SectionCard
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class PeriodKind { MONTH, RANGE }

private enum class DateField { FROM, TO, RATE }

private fun epochMillis(day: LocalDate): Long = day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

/**
 * "Erstattungsbericht" - Gegenstueck zu `ReimbursementView.swift`: Heimladungen eines Dienstwagens
 * als Nachweis fuer den Arbeitgeber, je Vorgang kWh, Herkunft der kWh und Betrag, dazu Summen je
 * Monat, als PDF oder CSV teilbar.
 *
 * Rechnet in beiden Modi LOKAL ([LocalReimbursementCalculator]), auch im Server-Modus - so sieht
 * der Bericht immer gleich aus und funktioniert offline. Die Dienstwagen-Angaben und die Saetze
 * werden hier gepflegt und synchronisiert wie die uebrigen Fahrzeugdaten. Der Bericht folgt den
 * Einstellungen schon vor dem Speichern, damit man sieht, was ein Schalter bewirkt.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReimbursementScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val serverAddressRequiredMessage = stringResource(R.string.error_server_address_required)
    val mode by AppSettings.appMode.collectAsState()
    val isServerMode = mode == AppMode.SERVER

    var basis by remember { mutableStateOf<ReimbursementBasis?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var vehicleId by rememberSaveable { mutableStateOf<String?>(null) }
    var vehicleMenu by remember { mutableStateOf(false) }

    // Bearbeitete Dienstwagen-Angaben; null = noch nicht vom Fahrzeug uebernommen.
    var draft by remember { mutableStateOf<VehicleReimbursement?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var savedHint by remember { mutableStateOf(false) }
    var modeMenu by remember { mutableStateOf(false) }

    var periodKind by rememberSaveable { mutableStateOf(PeriodKind.MONTH) }
    var month by remember { mutableStateOf(YearMonth.now().minusMonths(1)) }
    var rangeFrom by remember { mutableStateOf(YearMonth.now().minusMonths(1).atDay(1)) }
    var rangeTo by remember { mutableStateOf(LocalDate.now()) }

    var pickingDate by remember { mutableStateOf<DateField?>(null) }
    var showAddRate by remember { mutableStateOf(false) }
    var rateDay by remember { mutableStateOf(LocalDate.now()) }
    var rateText by remember { mutableStateOf("") }
    var pendingDeleteRate by remember { mutableStateOf<ReimbursementRate?>(null) }
    var actionError by remember { mutableStateOf<String?>(null) }

    suspend fun load() {
        if (!AppSettings.isReadyForDataAccess) { loadError = serverAddressRequiredMessage; return }
        try {
            val loaded = AppRepository.reimbursementBasis()
            basis = loaded
            loadError = null
            if (vehicleId == null || loaded.vehicles.none { it.id == vehicleId }) {
                vehicleId = (
                    loaded.vehicles.firstOrNull { it.isCompanyCar == true }
                        ?: loaded.vehicles.firstOrNull { it.isActive }
                        ?: loaded.vehicles.firstOrNull()
                    )?.id
            }
        } catch (e: Exception) {
            loadError = e.localizedMessage
        }
    }
    LaunchedEffect(Unit) { load() }

    // Dienstwagen zuerst, sonst in der gewohnten Reihenfolge.
    val vehicles = basis?.vehicles.orEmpty().sortedByDescending { it.isCompanyCar == true }
    val vehicle: Vehicle? = vehicles.firstOrNull { it.id == vehicleId }

    // Beim Fahrzeugwechsel (und nach dem ersten Laden) die gespeicherten Angaben uebernehmen.
    LaunchedEffect(vehicle?.id) {
        draft = vehicle?.reimbursementFields
        savedHint = false
    }

    val settings = draft ?: vehicle?.reimbursementFields
    val isDirty = vehicle != null && settings != null && settings != vehicle.reimbursementFields
    val reimbursementMode = ReimbursementMode.from(settings?.reimbursementMode)

    val rates = basis?.rates.orEmpty().filter { it.vehicleId == vehicleId }.sortedByDescending { it.validFrom }

    val (dateFrom, dateTo) = when (periodKind) {
        PeriodKind.MONTH -> month.atDay(1) to month.atEndOfMonth()
        PeriodKind.RANGE -> rangeFrom to rangeTo
    }
    val periodValid = !dateTo.isBefore(dateFrom)

    val report = remember(basis, vehicle, settings, dateFrom, dateTo) {
        val b = basis
        val v = vehicle
        val s = settings
        if (b == null || v == null || s == null || !s.isCompanyCar || dateTo.isBefore(dateFrom)) null
        else LocalReimbursementCalculator.build(
            vehicle = v.copy(
                isCompanyCar = s.isCompanyCar,
                reimbursementMode = s.reimbursementMode,
                reimbursementIncludeFees = s.reimbursementIncludeFees,
                employeeName = s.employeeName,
                employeeNumber = s.employeeNumber,
                licensePlate = s.licensePlate
            ),
            sessions = b.sessions,
            providers = b.providers,
            rates = b.rates,
            dateFrom = dateFrom,
            dateTo = dateTo
        )
    }

    fun saveSettings() {
        val v = vehicle ?: return
        val s = settings ?: return
        scope.launch {
            isSaving = true; actionError = null
            try {
                AppRepository.updateVehicle(
                    v.id,
                    VehiclePayload(
                        reimbursement = s.copy(
                            employeeName = s.employeeName?.trim()?.ifEmpty { null },
                            employeeNumber = s.employeeNumber?.trim()?.ifEmpty { null },
                            licensePlate = s.licensePlate?.trim()?.ifEmpty { null }
                        )
                    )
                )
                load()
                draft = basis?.vehicles?.firstOrNull { it.id == v.id }?.reimbursementFields
                savedHint = true
            } catch (e: Exception) {
                actionError = e.localizedMessage
            }
            isSaving = false
        }
    }

    fun openAddRate() {
        // Erster Satz: Vorschlag 0,34 EUR ab dem 1.1. des laufenden Jahres; danach ab heute mit
        // dem zuletzt gueltigen Satz als Ausgangswert.
        val latest = rates.firstOrNull()
        rateDay = if (latest == null) LocalDate.now().withDayOfYear(1) else LocalDate.now()
        rateText = Fmt.n("%.2f", latest?.ratePerKwh ?: LocalReimbursementCalculator.DEFAULT_RATE_PER_KWH)
        showAddRate = true
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.tools_reimbursement_title)) },
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
            loadError?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            actionError?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            if (basis != null && vehicles.isEmpty()) {
                Text(stringResource(R.string.reimbursement_no_vehicle), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // MARK: Fahrzeug
            if (vehicles.size > 1) {
                SectionCard {
                    SectionHeader(stringResource(R.string.reimbursement_section_vehicle))
                    Box {
                        OutlinedButton(onClick = { vehicleMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(vehicle?.let { vehicleLabel(it) } ?: "–")
                        }
                        DropdownMenu(expanded = vehicleMenu, onDismissRequest = { vehicleMenu = false }) {
                            vehicles.forEach { v ->
                                DropdownMenuItem(
                                    text = { Text(vehicleLabel(v)) },
                                    onClick = { vehicleMenu = false; vehicleId = v.id }
                                )
                            }
                        }
                    }
                }
            }

            // MARK: Dienstwagen-Einstellungen
            if (vehicle != null && settings != null) {
                SectionCard {
                    SectionHeader(stringResource(R.string.reimbursement_section_settings))
                    if (!settings.isCompanyCar) {
                        Text(
                            stringResource(R.string.reimbursement_not_company_car),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    SwitchRow(stringResource(R.string.reimbursement_is_company_car), settings.isCompanyCar) {
                        draft = settings.copy(isCompanyCar = it); savedHint = false
                    }
                    if (settings.isCompanyCar) {
                        Box(Modifier.padding(top = 8.dp)) {
                            OutlinedButton(onClick = { modeMenu = true }, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.reimbursement_field_mode) + ": " + stringResource(reimbursementMode.labelRes))
                            }
                            DropdownMenu(expanded = modeMenu, onDismissRequest = { modeMenu = false }) {
                                ReimbursementMode.entries.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(stringResource(option.labelRes)) },
                                        onClick = {
                                            modeMenu = false
                                            draft = settings.copy(reimbursementMode = option.raw); savedHint = false
                                        }
                                    )
                                }
                            }
                        }
                        if (reimbursementMode == ReimbursementMode.ACTUAL) {
                            SwitchRow(stringResource(R.string.reimbursement_include_fees), settings.reimbursementIncludeFees) {
                                draft = settings.copy(reimbursementIncludeFees = it); savedHint = false
                            }
                            Footnote(stringResource(R.string.reimbursement_include_fees_hint))
                        }
                        OutlinedTextField(
                            value = settings.employeeName ?: "",
                            onValueChange = { draft = settings.copy(employeeName = it.ifEmpty { null }); savedHint = false },
                            label = { Text(stringResource(R.string.reimbursement_field_employee_name)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        )
                        OutlinedTextField(
                            value = settings.employeeNumber ?: "",
                            onValueChange = { draft = settings.copy(employeeNumber = it.ifEmpty { null }); savedHint = false },
                            label = { Text(stringResource(R.string.reimbursement_field_employee_number)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = settings.licensePlate ?: "",
                            onValueChange = { draft = settings.copy(licensePlate = it.ifEmpty { null }); savedHint = false },
                            label = { Text(stringResource(R.string.reimbursement_field_license_plate)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        // Die Monatsmail verschickt allein der Server - im Local-Only-Modus gibt es
                        // niemanden, der sie senden koennte.
                        if (isServerMode) {
                            SwitchRow(stringResource(R.string.reimbursement_monthly_mail), settings.reimbursementMonthlyMail) {
                                draft = settings.copy(reimbursementMonthlyMail = it); savedHint = false
                            }
                            Footnote(stringResource(R.string.reimbursement_monthly_mail_hint))
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (savedHint && !isDirty) {
                            Text(
                                stringResource(R.string.reimbursement_settings_saved),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Box(Modifier.weight(1f))
                        Button(onClick = { saveSettings() }, enabled = isDirty && !isSaving) {
                            Text(stringResource(if (isSaving) R.string.action_saving else R.string.action_save))
                        }
                    }
                }
            }

            // MARK: Saetze (nur pauschal)
            if (vehicle != null && settings?.isCompanyCar == true && reimbursementMode == ReimbursementMode.FLAT) {
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { SectionHeader(stringResource(R.string.reimbursement_section_rates)) }
                        IconButton(onClick = { openAddRate() }) {
                            Icon(Icons.Filled.Add, stringResource(R.string.reimbursement_rates_add))
                        }
                    }
                    if (rates.isEmpty()) {
                        Text(stringResource(R.string.reimbursement_rates_empty), style = MaterialTheme.typography.bodyMedium)
                    }
                    rates.forEach { rate ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.reimbursement_rate_valid_from, Fmt.dateMedium(rate.validFrom)),
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(Fmt.n("%.4f €/kWh", rate.ratePerKwh), style = MaterialTheme.typography.bodyLarge)
                            IconButton(onClick = { pendingDeleteRate = rate }) {
                                Icon(Icons.Filled.Delete, stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    Footnote(stringResource(R.string.reimbursement_rates_help))
                }
            }

            // MARK: Zeitraum und Bericht
            if (vehicle != null && settings?.isCompanyCar == true) {
                SectionCard {
                    SectionHeader(stringResource(R.string.reimbursement_section_period))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = periodKind == PeriodKind.MONTH,
                            onClick = { periodKind = PeriodKind.MONTH },
                            label = { Text(stringResource(R.string.reimbursement_period_month)) }
                        )
                        FilterChip(
                            selected = periodKind == PeriodKind.RANGE,
                            onClick = { periodKind = PeriodKind.RANGE },
                            label = { Text(stringResource(R.string.reimbursement_period_range)) }
                        )
                    }
                    when (periodKind) {
                        PeriodKind.MONTH -> Row(
                            Modifier.fillMaxWidth().padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { month = month.minusMonths(1) }) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.reimbursement_period_previous))
                            }
                            Text(
                                month.format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale.getDefault())),
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.titleMedium
                            )
                            IconButton(onClick = { month = month.plusMonths(1) }) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.reimbursement_period_next))
                            }
                        }
                        PeriodKind.RANGE -> Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { pickingDate = DateField.FROM }, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.reimbursement_period_from, Fmt.dateMedium(epochMillis(rangeFrom))))
                            }
                            OutlinedButton(onClick = { pickingDate = DateField.TO }, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.reimbursement_period_to, Fmt.dateMedium(epochMillis(rangeTo))))
                            }
                            if (!periodValid) {
                                Text(
                                    stringResource(R.string.reimbursement_period_invalid),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }

                report?.let { r ->
                    ReportCard(r)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = {
                                try { ReimbursementExport.sharePdf(context, r) } catch (e: Exception) {
                                    actionError = context.getString(R.string.reimbursement_share_error, e.localizedMessage.orEmpty())
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text(stringResource(R.string.reimbursement_share_pdf)) }
                        OutlinedButton(
                            onClick = {
                                try { ReimbursementExport.shareCsv(context, r) } catch (e: Exception) {
                                    actionError = context.getString(R.string.reimbursement_share_error, e.localizedMessage.orEmpty())
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text(stringResource(R.string.reimbursement_share_csv)) }
                    }
                    if (isDirty) Footnote(stringResource(R.string.reimbursement_unsaved_hint))
                }
            }
        }
    }

    // MARK: Dialoge

    if (showAddRate) {
        val parsed = rateText.trim().replace(",", ".").toDoubleOrNull()
        val valid = parsed != null && parsed >= 0 && parsed <= 10
        AlertDialog(
            onDismissRequest = { showAddRate = false },
            title = { Text(stringResource(R.string.reimbursement_rate_add_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pickingDate = DateField.RATE }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.reimbursement_rate_valid_from, Fmt.dateMedium(epochMillis(rateDay))))
                    }
                    OutlinedTextField(
                        value = rateText,
                        onValueChange = { rateText = it },
                        label = { Text(stringResource(R.string.reimbursement_rate_field_rate)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = valid, onClick = {
                    val v = vehicle
                    val value = parsed
                    showAddRate = false
                    if (v != null && value != null) {
                        scope.launch {
                            try {
                                AppRepository.createReimbursementRate(
                                    ReimbursementRatePayload(vehicleId = v.id, validFrom = epochMillis(rateDay), ratePerKwh = value)
                                )
                            } catch (e: Exception) { actionError = e.localizedMessage }
                            load()
                        }
                    }
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = { TextButton(onClick = { showAddRate = false }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }

    pendingDeleteRate?.let { rate ->
        AlertDialog(
            onDismissRequest = { pendingDeleteRate = null },
            title = { Text(stringResource(R.string.reimbursement_rate_delete_title)) },
            text = {
                Text(
                    stringResource(R.string.reimbursement_rate_valid_from, Fmt.dateMedium(rate.validFrom)) +
                        " · " + Fmt.n("%.4f €/kWh", rate.ratePerKwh)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = rate
                    pendingDeleteRate = null
                    scope.launch {
                        try { AppRepository.deleteReimbursementRate(target.id) } catch (e: Exception) { actionError = e.localizedMessage }
                        load()
                    }
                }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDeleteRate = null }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }

    pickingDate?.let { field ->
        // Der Picker rechnet in UTC-Mitternacht - hin und zurueck ueber UTC, sonst rutscht der Tag
        // je nach Zeitzone um eins (wie bei den Fahrzeugkosten).
        val current = when (field) {
            DateField.FROM -> rangeFrom
            DateField.TO -> rangeTo
            DateField.RATE -> rateDay
        }
        val state = rememberDatePickerState(
            initialSelectedDateMillis = current.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = null },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { picked ->
                        val date = Instant.ofEpochMilli(picked).atZone(ZoneOffset.UTC).toLocalDate()
                        when (field) {
                            DateField.FROM -> rangeFrom = date
                            DateField.TO -> rangeTo = date
                            DateField.RATE -> rateDay = date
                        }
                    }
                    pickingDate = null
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { pickingDate = null }) { Text(stringResource(R.string.action_cancel)) } }
        ) { DatePicker(state = state) }
    }
}

@Composable
private fun vehicleLabel(vehicle: Vehicle): String =
    if (vehicle.isCompanyCar == true) stringResource(R.string.reimbursement_vehicle_company_car, vehicle.name)
    else vehicle.name

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Kopf, eine Zeile je Vorgang, Monatssummen, Summe und die Hinweise - wie das PDF. */
@Composable
private fun ReportCard(report: LocalReimbursementCalculator.Report) {
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    SectionCard {
        SectionHeader(stringResource(R.string.reimbursement_section_report))
        val header = buildList {
            add(stringResource(R.string.reimbursement_csv_vehicle) to report.vehicleName)
            report.licensePlate?.takeIf { it.isNotEmpty() }?.let { add(stringResource(R.string.reimbursement_csv_license_plate) to it) }
            report.employeeName?.takeIf { it.isNotEmpty() }?.let { add(stringResource(R.string.reimbursement_csv_employee_name) to it) }
            report.employeeNumber?.takeIf { it.isNotEmpty() }?.let { add(stringResource(R.string.reimbursement_csv_employee_number) to it) }
            add(
                stringResource(R.string.reimbursement_csv_period) to
                    Fmt.dateMedium(epochMillis(report.dateFrom)) + " – " + Fmt.dateMedium(epochMillis(report.dateTo))
            )
            val modeText = stringResource(report.mode.labelRes) +
                if (report.includeFees) " (" + stringResource(R.string.reimbursement_csv_with_fees) + ")" else ""
            add(stringResource(R.string.reimbursement_csv_mode) to modeText)
        }
        header.forEach { (label, value) ->
            Row(Modifier.fillMaxWidth()) {
                Text(label, Modifier.weight(0.4f), style = MaterialTheme.typography.bodySmall, color = secondary)
                Text(value, Modifier.weight(0.6f), style = MaterialTheme.typography.bodySmall)
            }
        }

        if (report.rows.isEmpty()) {
            Text(
                stringResource(R.string.reimbursement_report_empty),
                Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.bodyMedium
            )
            Footnote(stringResource(R.string.reimbursement_report_home_hint))
        } else {
            ReportBody(report)
        }
    }
}

@Composable
private fun ReportBody(report: LocalReimbursementCalculator.Report) {
    val context = LocalContext.current
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    Column {
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        report.rows.forEach { row ->
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(Fmt.dateTimeShort(row.startTime), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        row.amount?.let { Fmt.n("%.2f €", it) } ?: "–",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                val details = listOfNotNull(
                    row.energyKwh?.let { Fmt.n("%.2f kWh", it) } ?: "– kWh",
                    ReimbursementExport.sourceLabel(context, row.kwhSource) + if (row.kwhSource == KwhSource.ESTIMATED) "*" else "",
                    row.odometerKm?.let { Fmt.km(it) },
                    row.ratePerKwh?.let { Fmt.n("%.4f €/kWh", it) },
                    row.feeShare.takeIf { it != 0.0 }?.let { stringResource(R.string.reimbursement_report_fee, Fmt.n("%.2f €", it)) }
                )
                Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = secondary)
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text(
            stringResource(R.string.reimbursement_report_months),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = secondary
        )
        report.months.forEach { m ->
            val ym = YearMonth.parse(m.month)
            MonthLine(
                ym.format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale.getDefault())),
                m.sessions, m.energyKwh, m.estimatedKwh, m.amount, bold = false
            )
        }
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        MonthLine(
            stringResource(R.string.reimbursement_report_total), report.rows.size,
            report.totalKwh, report.estimatedKwh, report.totalAmount, bold = true
        )

        ReimbursementExport.notes(context, report).forEach { Footnote(it) }
    }
}

@Composable
private fun MonthLine(label: String, sessions: Int, kwh: Double, estimated: Double, amount: Double, bold: Boolean) {
    val weight = if (bold) FontWeight.SemiBold else FontWeight.Normal
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = weight)
            Text(Fmt.n("%.2f €", amount), style = MaterialTheme.typography.bodyMedium, fontWeight = weight)
        }
        val parts = buildList {
            add(stringResource(R.string.reimbursement_report_sessions, sessions))
            add(Fmt.n("%.2f kWh", kwh))
            if (estimated > 0) add(stringResource(R.string.reimbursement_report_estimated_kwh, Fmt.n("%.2f", estimated)))
        }
        Text(
            parts.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
