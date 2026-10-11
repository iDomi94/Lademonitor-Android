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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.dominiqueherbrigpersonalteam.lademonitor.R
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.CostCategory
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.FeeInterval
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.VehicleCost
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.VehicleCostPayload
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.YearCostStat
import com.dominiqueherbrigpersonalteam.lademonitor.data.repo.AppRepository
import com.dominiqueherbrigpersonalteam.lademonitor.data.repo.LocalFeeAllocator
import com.dominiqueherbrigpersonalteam.lademonitor.data.repo.LocalVehicleCostCalculator
import com.dominiqueherbrigpersonalteam.lademonitor.data.settings.AppSettings
import com.dominiqueherbrigpersonalteam.lademonitor.ui.common.Fmt
import com.dominiqueherbrigpersonalteam.lademonitor.ui.common.FullScreenModal
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Sonstige Kosten eines Fahrzeugs (Versicherung, Steuer, Wartung ...), erreichbar ueber
 * Einstellungen -> Fahrzeuge -> Fahrzeug -> Kosten. In beiden Modi verfuegbar: die Rechnung
 * liegt in [LocalVehicleCostCalculator], weil die Kosten in die lokal berechnete
 * Dashboard-Uebersicht "Gesamtkosten" eingehen.
 *
 * Oben die Kosten je Kalenderjahr (inkl. Laden und Wertverlust/Leasing), darunter die Eintraege,
 * neueste zuerst.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleCostsScreen(navController: NavController, vehicleId: String) {
    val scope = rememberCoroutineScope()
    var vehicleName by remember { mutableStateOf<String?>(null) }
    var costs by remember { mutableStateOf<List<VehicleCost>>(emptyList()) }
    var years by remember { mutableStateOf<List<YearCostStat>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<VehicleCost?>(null) }
    var pendingDelete by remember { mutableStateOf<VehicleCost?>(null) }
    val serverAddressRequiredMessage = stringResource(R.string.error_server_address_required)

    suspend fun load() {
        if (!AppSettings.isReadyForDataAccess) { errorMessage = serverAddressRequiredMessage; return }
        try {
            val basis = AppRepository.vehicleCostBasis()
            vehicleName = basis.vehicles.firstOrNull { it.id == vehicleId }?.name
            costs = basis.costs.filter { it.vehicleId == vehicleId }.sortedByDescending { it.startDate }
            years = LocalVehicleCostCalculator.yearSummary(basis, vehicleId)
            errorMessage = null
        } catch (e: Exception) { if (costs.isEmpty()) errorMessage = e.localizedMessage }
        loaded = true
    }
    LaunchedEffect(vehicleId) { load() }

    Scaffold(topBar = {
        TopAppBar(
            title = {
                Text(vehicleName?.let { stringResource(R.string.vehicle_costs_title_named, it) } ?: stringResource(R.string.vehicle_costs_title))
            },
            navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
            actions = {
                IconButton(onClick = { showAdd = true }, enabled = loaded && vehicleName != null) {
                    Icon(Icons.Filled.Add, stringResource(R.string.sessions_add_content_description))
                }
            }
        )
    }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            errorMessage?.let { item { Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) } }

            if (years.isNotEmpty()) {
                item { ListHeader(stringResource(R.string.vehicle_costs_per_year)) }
                items(years, key = { "year-${it.year}" }) { year ->
                    YearRow(year)
                    HorizontalDivider()
                }
            }

            item { ListHeader(stringResource(R.string.vehicle_costs_entries)) }
            if (loaded && costs.isEmpty() && errorMessage == null) {
                item {
                    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.vehicle_costs_empty_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.vehicle_costs_empty_message),
                            Modifier.padding(top = 4.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            items(costs, key = { it.id }) { cost ->
                Row(Modifier.fillMaxWidth().clickable { editing = cost }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    CostRow(cost, Modifier.weight(1f))
                    IconButton(onClick = { pendingDelete = cost }) { Icon(Icons.Filled.Delete, stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.error) }
                }
                HorizontalDivider()
            }
            item {
                Text(
                    stringResource(R.string.vehicle_costs_footer),
                    Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showAdd) AddEditVehicleCostModal(null, vehicleId, onDismiss = { showAdd = false }) { showAdd = false; scope.launch { load() } }
    editing?.let { c -> AddEditVehicleCostModal(c, vehicleId, onDismiss = { editing = null }) { editing = null; scope.launch { load() } } }

    pendingDelete?.let { c ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.vehicle_costs_delete_confirm_title)) },
            text = { Text(stringResource(R.string.vehicle_costs_delete_confirm_message, c.label ?: stringResource(c.categoryValue.labelRes))) },
            confirmButton = {
                TextButton(onClick = {
                    val target = c; pendingDelete = null
                    scope.launch {
                        try { AppRepository.deleteVehicleCost(target.id) } catch (e: Exception) { errorMessage = e.localizedMessage }
                        load()
                    }
                }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }
}

@Composable
private fun ListHeader(text: String) {
    Text(
        text,
        Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun YearRow(year: YearCostStat) {
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(year.year.toString(), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(Fmt.n("%.2f €", year.total), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        }
        val km = year.kmDriven
        val perKm = year.costPerKm
        if (km != null && perKm != null) {
            Text(
                Fmt.km(km) + " · " + Fmt.n("%.2f €/km", perKm),
                style = MaterialTheme.typography.bodySmall,
                color = secondary
            )
        }
        year.byCategory.forEach { stat ->
            Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
                Text(stringResource(CostCategory.labelResFor(stat.category)), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = secondary)
                Text(Fmt.n("%.2f €", stat.amount), style = MaterialTheme.typography.bodySmall, color = secondary)
            }
        }
    }
}

@Composable
private fun CostRow(cost: VehicleCost, modifier: Modifier = Modifier) {
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    val interval = cost.intervalValue
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                Fmt.dateMedium(cost.startDate) + " · " + stringResource(cost.categoryValue.labelRes),
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(Fmt.n("%.2f €", cost.amount), style = MaterialTheme.typography.bodyLarge)
        }
        val text = listOfNotNull(cost.label, cost.notes).joinToString(" · ")
        if (text.isNotEmpty()) {
            Text(text, style = MaterialTheme.typography.bodySmall, color = secondary)
        }
        if (interval != FeeInterval.ONCE) {
            val rhythm = stringResource(if (interval == FeeInterval.MONTHLY) R.string.vehicle_cost_monthly else R.string.vehicle_cost_yearly)
            val until = cost.endDate?.let { " · " + stringResource(R.string.vehicle_cost_until, Fmt.dateMedium(it)) } ?: ""
            Text(rhythm + until, style = MaterialTheme.typography.bodySmall, color = secondary)
            Text(
                stringResource(R.string.vehicle_cost_paid_to_date, Fmt.n("%.2f €", LocalVehicleCostCalculator.paidToDate(cost))),
                style = MaterialTheme.typography.bodySmall,
                color = secondary
            )
        }
        cost.odometerKm?.let { Text(Fmt.km(it), style = MaterialTheme.typography.bodySmall, color = secondary) }
    }
}

private enum class CostDateField { START, END }

/** Reihenfolge im Auswahlmenue: die einzelne Zahlung ist der haeufigste Fall. */
private val costIntervals = listOf(FeeInterval.ONCE, FeeInterval.MONTHLY, FeeInterval.YEARLY)

/**
 * Vorbelegung eines NEUEN Kosteneintrags, z.B. nach "Erledigt" an einer Erinnerung (Kategorie,
 * Datum, km-Stand und die Erinnerung als Bezeichnung). Beim Bearbeiten ohne Wirkung.
 */
data class VehicleCostPrefill(
    val category: CostCategory,
    val date: LocalDate,
    val odometerKm: Int?,
    val label: String?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditVehicleCostModal(
    cost: VehicleCost?,
    vehicleId: String,
    prefill: VehicleCostPrefill? = null,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val zone = ZoneId.systemDefault()
    val isEditing = cost != null
    val initialStart = cost?.let { LocalFeeAllocator.day(it.startDate) } ?: prefill?.date ?: LocalDate.now()

    var category by remember { mutableStateOf(cost?.categoryValue ?: prefill?.category ?: CostCategory.MAINTENANCE) }
    var amount by remember { mutableStateOf(cost?.amount?.let { Fmt.n("%.2f", it) } ?: "") }
    var interval by remember { mutableStateOf(cost?.intervalValue ?: FeeInterval.ONCE) }
    var startDate by remember { mutableStateOf(initialStart) }
    var hasEndDate by remember { mutableStateOf(cost?.endDate != null) }
    var endDate by remember {
        mutableStateOf(cost?.endDate?.let { LocalFeeAllocator.day(it) } ?: initialStart.plusYears(1).minusDays(1))
    }
    var odometer by remember { mutableStateOf((if (cost != null) cost.odometerKm else prefill?.odometerKm)?.toString() ?: "") }
    var label by remember { mutableStateOf(if (cost != null) cost.label ?: "" else prefill?.label ?: "") }
    var notes by remember { mutableStateOf(cost?.notes ?: "") }
    var categoryMenu by remember { mutableStateOf(false) }
    var intervalMenu by remember { mutableStateOf(false) }
    var pickingDate by remember { mutableStateOf<CostDateField?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val recurring = interval != FeeInterval.ONCE
    val usesEndDate = recurring && hasEndDate
    val parsedAmount = amount.trim().replace(",", ".").toDoubleOrNull()
    val parsedOdometer = odometer.trim().toIntOrNull()
    val odometerOk = odometer.isBlank() || (parsedOdometer != null && parsedOdometer >= 0)
    val canSave = parsedAmount != null && parsedAmount >= 0 && odometerOk &&
        !(usesEndDate && endDate.isBefore(startDate))

    fun save() {
        val value = parsedAmount ?: return
        scope.launch {
            isSaving = true; errorMessage = null
            val payload = VehicleCostPayload(
                vehicleId = cost?.vehicleId ?: vehicleId,
                amount = value,
                category = category.raw,
                interval = interval.wire,
                startDate = startDate.atStartOfDay(zone).toInstant().toEpochMilli(),
                endDate = if (usesEndDate) endDate.atStartOfDay(zone).toInstant().toEpochMilli() else null,
                odometerKm = parsedOdometer,
                label = label.trim().ifEmpty { null },
                notes = notes.trim().ifEmpty { null }
            )
            try {
                if (cost != null) AppRepository.updateVehicleCost(cost.id, payload)
                else AppRepository.createVehicleCost(payload)
                onSaved()
            } catch (e: Exception) { errorMessage = e.localizedMessage }
            isSaving = false
        }
    }

    FullScreenModal(onDismiss = onDismiss) {
        Scaffold(topBar = {
            TopAppBar(
                title = { Text(if (isEditing) stringResource(R.string.vehicle_costs_edit_title) else stringResource(R.string.vehicle_costs_add_title)) },
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
                    OutlinedButton(onClick = { categoryMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.vehicle_cost_field_category) + ": " + stringResource(category.labelRes))
                    }
                    DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                        CostCategory.entries.forEach { option ->
                            DropdownMenuItem(text = { Text(stringResource(option.labelRes)) }, onClick = { category = option; categoryMenu = false })
                        }
                    }
                }
                OutlinedTextField(
                    value = amount, onValueChange = { amount = it }, label = { Text(stringResource(R.string.vehicle_cost_field_amount)) },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth()
                )
                Box {
                    OutlinedButton(onClick = { intervalMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.vehicle_cost_field_interval) + ": " + stringResource(interval.labelRes))
                    }
                    DropdownMenu(expanded = intervalMenu, onDismissRequest = { intervalMenu = false }) {
                        costIntervals.forEach { option ->
                            DropdownMenuItem(text = { Text(stringResource(option.labelRes)) }, onClick = { interval = option; intervalMenu = false })
                        }
                    }
                }
                if (interval == FeeInterval.YEARLY) {
                    Text(
                        stringResource(R.string.vehicle_cost_hint_yearly),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                val startLabel = stringResource(if (recurring) R.string.vehicle_cost_field_first_payment else R.string.vehicle_cost_field_date)
                OutlinedButton(onClick = { pickingDate = CostDateField.START }, modifier = Modifier.fillMaxWidth()) {
                    Text("$startLabel: ${Fmt.dateMedium(startDate.atStartOfDay(zone).toInstant().toEpochMilli())}")
                }
                if (recurring) {
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.vehicle_cost_field_has_last_payment), Modifier.weight(1f))
                        Switch(checked = hasEndDate, onCheckedChange = { hasEndDate = it })
                    }
                    if (usesEndDate) {
                        OutlinedButton(onClick = { pickingDate = CostDateField.END }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.vehicle_cost_field_last_payment) + ": " + Fmt.dateMedium(endDate.atStartOfDay(zone).toInstant().toEpochMilli()))
                        }
                        if (endDate.isBefore(startDate)) {
                            Text(stringResource(R.string.vehicle_cost_end_before_start), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                OutlinedTextField(
                    value = odometer, onValueChange = { odometer = it }, label = { Text(stringResource(R.string.vehicle_cost_field_odometer)) },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text(stringResource(R.string.vehicle_cost_field_label)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text(stringResource(R.string.field_notes)) }, modifier = Modifier.fillMaxWidth())
                errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
    }

    pickingDate?.let { field ->
        // Der Picker rechnet in UTC-Mitternacht - hin und zurueck ueber UTC, sonst rutscht der Tag
        // je nach Zeitzone um eins.
        val current = if (field == CostDateField.START) startDate else endDate
        val state = rememberDatePickerState(
            initialSelectedDateMillis = current.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = null },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { picked ->
                        val date = Instant.ofEpochMilli(picked).atZone(ZoneOffset.UTC).toLocalDate()
                        if (field == CostDateField.START) startDate = date else endDate = date
                    }
                    pickingDate = null
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { pickingDate = null }) { Text(stringResource(R.string.action_cancel)) } }
        ) { DatePicker(state = state) }
    }
}
