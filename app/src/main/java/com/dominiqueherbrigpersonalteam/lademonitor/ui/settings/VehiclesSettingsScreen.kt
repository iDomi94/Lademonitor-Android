package com.dominiqueherbrigpersonalteam.lademonitor.ui.settings

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.dominiqueherbrigpersonalteam.lademonitor.R
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.Ownership
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.Vehicle
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.VehicleOwnership
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.VehiclePayload
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehiclesSettingsScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    var vehicles by remember { mutableStateOf<List<Vehicle>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Vehicle?>(null) }
    var pendingDelete by remember { mutableStateOf<Vehicle?>(null) }
    val serverAddressRequiredMessage = stringResource(R.string.error_server_address_required)

    suspend fun load() {
        if (!AppSettings.isReadyForDataAccess) { errorMessage = serverAddressRequiredMessage; return }
        try { vehicles = AppRepository.fetchVehicles(); errorMessage = null }
        catch (e: Exception) { if (vehicles.isEmpty()) errorMessage = e.localizedMessage }
    }
    LaunchedEffect(Unit) { load() }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.settings_nav_vehicles)) },
            navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
            actions = { IconButton(onClick = { showAdd = true }) { Icon(Icons.Filled.Add, stringResource(R.string.sessions_add_content_description)) } }
        )
    }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            errorMessage?.let { item { Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            items(vehicles, key = { it.id }) { vehicle ->
                Row(
                    Modifier.fillMaxWidth().clickable { editing = vehicle }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(vehicle.name, style = MaterialTheme.typography.bodyLarge)
                            if (!vehicle.isActive) {
                                Spacer(Modifier.width(6.dp))
                                Box(Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                    Text(stringResource(R.string.vehicle_inactive_badge), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        val subtitle = listOfNotNull(vehicle.brand, vehicle.model).joinToString(" · ")
                        if (subtitle.isNotEmpty()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    vehicle.batteryCapacityKwh?.let {
                        Text(Fmt.n("%.0f kWh", it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { pendingDelete = vehicle }) { Icon(Icons.Filled.Delete, stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.error) }
                }
                HorizontalDivider()
            }
        }
    }

    if (showAdd) AddEditVehicleModal(null, onDismiss = { showAdd = false }) { showAdd = false; scope.launch { load() } }
    editing?.let { v ->
        AddEditVehicleModal(
            v,
            onDismiss = { editing = null },
            onOpenCosts = { id -> editing = null; navController.navigate("settings/vehicles/costs/$id") }
        ) { editing = null; scope.launch { load() } }
    }

    pendingDelete?.let { v ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.vehicle_delete_confirm_title)) },
            text = { Text(stringResource(R.string.vehicle_delete_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    val target = v; pendingDelete = null
                    scope.launch { runCatching { AppRepository.deleteVehicle(target.id) }; load() }
                }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }
}

private enum class VehicleDateField { PURCHASE, REGISTRATION, LEASING_START, LEASING_END }

/**
 * Fahrzeug anlegen/bearbeiten, inkl. Kauf/Leasing (Server ab 0.32.0). Die Kennzahlen darunter
 * (geschaetzter Wert, Wertverlust-Vorschlag, Leasing je Monat) rechnet die App selbst
 * ([LocalVehicleCostCalculator]) - live aus den Formularwerten, damit man die Wirkung einer
 * Eingabe sofort sieht. [onOpenCosts] fuehrt zur Kostenliste des Fahrzeugs (nur beim Bearbeiten).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditVehicleModal(
    vehicle: Vehicle?,
    onDismiss: () -> Unit,
    onOpenCosts: ((String) -> Unit)? = null,
    onSaved: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val zone = ZoneId.systemDefault()
    val isEditing = vehicle != null
    var externalId by remember { mutableStateOf(vehicle?.externalId ?: "") }
    var name by remember { mutableStateOf(vehicle?.name ?: "") }
    var brand by remember { mutableStateOf(vehicle?.brand ?: "") }
    var model by remember { mutableStateOf(vehicle?.model ?: "") }
    var battery by remember { mutableStateOf(vehicle?.batteryCapacityKwh?.let { Fmt.n("%.1f", it) } ?: "") }
    var isActive by remember { mutableStateOf(vehicle?.isActive ?: true) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Kauf/Leasing - Betraege als Text wie die uebrigen Felder, Daten als Kalendertag.
    fun money(v: Double?) = v?.let { Fmt.n("%.2f", it) } ?: ""
    var ownership by remember { mutableStateOf(vehicle?.ownershipValue ?: Ownership.NONE) }
    var purchasePrice by remember { mutableStateOf(money(vehicle?.purchasePrice)) }
    var purchaseDate by remember { mutableStateOf(vehicle?.purchaseDate?.let { LocalFeeAllocator.day(it) }) }
    var firstRegistration by remember { mutableStateOf(vehicle?.firstRegistration?.let { LocalFeeAllocator.day(it) }) }
    var purchaseOdometer by remember { mutableStateOf(vehicle?.purchaseOdometerKm?.toString() ?: "") }
    var depreciationOverride by remember { mutableStateOf(money(vehicle?.depreciationMonthlyOverride)) }
    var leasingRate by remember { mutableStateOf(money(vehicle?.leasingRateMonthly)) }
    var leasingStart by remember { mutableStateOf(vehicle?.leasingStart?.let { LocalFeeAllocator.day(it) }) }
    var leasingEnd by remember { mutableStateOf(vehicle?.leasingEnd?.let { LocalFeeAllocator.day(it) }) }
    var leasingDown by remember { mutableStateOf(money(vehicle?.leasingDownPayment)) }
    var ownershipMenu by remember { mutableStateOf(false) }
    var pickingDate by remember { mutableStateOf<VehicleDateField?>(null) }
    // km-Staende aus den Ladevorgaengen dieses Fahrzeugs - fuer das "wirksame Alter" der Kurve.
    var odometer by remember { mutableStateOf<List<Pair<LocalDate, Int>>>(emptyList()) }

    LaunchedEffect(vehicle?.id) {
        val id = vehicle?.id ?: return@LaunchedEffect
        runCatching {
            val basis = AppRepository.vehicleCostBasis()
            odometer = LocalVehicleCostCalculator.odometerPoints(basis.sessions.filter { it.vehicleId == id })
        }
    }

    fun decimal(text: String) = text.trim().replace(",", ".").toDoubleOrNull()
    fun millis(d: LocalDate?) = d?.atStartOfDay(zone)?.toInstant()?.toEpochMilli()

    val ownershipFields = VehicleOwnership(
        ownership = ownership.raw,
        purchasePrice = decimal(purchasePrice),
        purchaseDate = millis(purchaseDate),
        firstRegistration = millis(firstRegistration),
        purchaseOdometerKm = purchaseOdometer.trim().toIntOrNull(),
        depreciationMonthlyOverride = decimal(depreciationOverride),
        leasingRateMonthly = decimal(leasingRate),
        leasingStart = millis(leasingStart),
        leasingEnd = millis(leasingEnd),
        leasingDownPayment = decimal(leasingDown)
    )
    val info = LocalVehicleCostCalculator.ownershipInfo(
        Vehicle(
            id = vehicle?.id ?: "",
            externalId = vehicle?.externalId ?: "",
            name = name,
            ownership = ownershipFields.ownership,
            purchasePrice = ownershipFields.purchasePrice,
            purchaseDate = ownershipFields.purchaseDate,
            firstRegistration = ownershipFields.firstRegistration,
            purchaseOdometerKm = ownershipFields.purchaseOdometerKm,
            depreciationMonthlyOverride = ownershipFields.depreciationMonthlyOverride,
            leasingRateMonthly = ownershipFields.leasingRateMonthly,
            leasingStart = ownershipFields.leasingStart,
            leasingEnd = ownershipFields.leasingEnd,
            leasingDownPayment = ownershipFields.leasingDownPayment
        ),
        odometer
    )

    val leasingDatesOk = run {
        val start = leasingStart
        val end = leasingEnd
        start == null || end == null || !end.isBefore(start)
    }
    val canSave = name.trim().isNotEmpty() && (isEditing || externalId.trim().isNotEmpty()) &&
        (ownership != Ownership.LEASING || leasingDatesOk)

    fun save() {
        scope.launch {
            isSaving = true; errorMessage = null
            val payload = VehiclePayload(
                externalId = if (isEditing) null else externalId.trim(),
                name = name.trim(),
                brand = brand.trim().ifEmpty { null },
                model = model.trim().ifEmpty { null },
                batteryCapacityKwh = battery.replace(",", ".").toDoubleOrNull(),
                isActive = isActive,
                ownership = ownershipFields
            )
            try {
                if (vehicle != null) AppRepository.updateVehicle(vehicle.id, payload)
                else AppRepository.createVehicle(payload)
                onSaved()
            } catch (e: Exception) { errorMessage = e.localizedMessage }
            isSaving = false
        }
    }

    FullScreenModal(onDismiss = onDismiss) {
        Scaffold(topBar = {
            TopAppBar(
                title = { Text(if (isEditing) stringResource(R.string.vehicle_edit_title) else stringResource(R.string.vehicle_add_title)) },
                navigationIcon = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
                actions = { TextButton(onClick = { save() }, enabled = !isSaving && canSave) { Text(if (isSaving) stringResource(R.string.action_saving) else stringResource(R.string.action_save)) } }
            )
        }) { padding ->
            Column(
                Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = externalId, onValueChange = { externalId = it }, label = { Text(stringResource(R.string.vehicle_field_external_id)) },
                    singleLine = true, enabled = !isEditing, modifier = Modifier.fillMaxWidth()
                )
                if (isEditing) Text(stringResource(R.string.vehicle_external_id_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.field_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = brand, onValueChange = { brand = it }, label = { Text(stringResource(R.string.vehicle_field_brand)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = model, onValueChange = { model = it }, label = { Text(stringResource(R.string.vehicle_field_model)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = battery, onValueChange = { battery = it }, label = { Text(stringResource(R.string.vehicle_field_battery_capacity)) },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth()
                )
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.field_active), Modifier.weight(1f))
                    Switch(checked = isActive, onCheckedChange = { isActive = it })
                }

                // ---- Kauf / Leasing ----
                Text(
                    stringResource(R.string.vehicle_section_ownership),
                    Modifier.padding(top = 12.dp),
                    style = MaterialTheme.typography.titleSmall
                )
                Box {
                    OutlinedButton(onClick = { ownershipMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(ownership.labelRes))
                    }
                    DropdownMenu(expanded = ownershipMenu, onDismissRequest = { ownershipMenu = false }) {
                        Ownership.entries.forEach { option ->
                            DropdownMenuItem(text = { Text(stringResource(option.labelRes)) }, onClick = { ownership = option; ownershipMenu = false })
                        }
                    }
                }
                when (ownership) {
                    Ownership.NONE -> Text(
                        stringResource(R.string.vehicle_ownership_none_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Ownership.PURCHASE -> {
                        DecimalField(purchasePrice, { purchasePrice = it }, stringResource(R.string.vehicle_field_purchase_price))
                        DateRow(stringResource(R.string.vehicle_field_purchase_date), purchaseDate, zone,
                            onPick = { pickingDate = VehicleDateField.PURCHASE }, onClear = { purchaseDate = null })
                        DateRow(stringResource(R.string.vehicle_field_first_registration), firstRegistration, zone,
                            onPick = { pickingDate = VehicleDateField.REGISTRATION }, onClear = { firstRegistration = null })
                        Text(
                            stringResource(R.string.vehicle_first_registration_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = purchaseOdometer, onValueChange = { purchaseOdometer = it },
                            label = { Text(stringResource(R.string.vehicle_field_purchase_odometer)) },
                            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                        InfoLine(
                            stringResource(R.string.vehicle_estimated_value),
                            info.estimatedValue?.let { Fmt.n("%.2f €", it) } ?: "–"
                        )
                        InfoLine(
                            stringResource(R.string.vehicle_suggested_depreciation),
                            info.suggestedMonthly?.let { Fmt.n("%.2f €", it) + " " + stringResource(R.string.fee_per_month) } ?: "–"
                        )
                        OutlinedTextField(
                            value = depreciationOverride, onValueChange = { depreciationOverride = it },
                            label = { Text(stringResource(R.string.vehicle_field_depreciation_override)) },
                            placeholder = { Text(stringResource(R.string.vehicle_depreciation_override_placeholder)) },
                            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            stringResource(R.string.vehicle_depreciation_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Ownership.LEASING -> {
                        DecimalField(leasingRate, { leasingRate = it }, stringResource(R.string.vehicle_field_leasing_rate))
                        DateRow(stringResource(R.string.vehicle_field_leasing_start), leasingStart, zone,
                            onPick = { pickingDate = VehicleDateField.LEASING_START }, onClear = { leasingStart = null })
                        DateRow(stringResource(R.string.vehicle_field_leasing_end), leasingEnd, zone,
                            onPick = { pickingDate = VehicleDateField.LEASING_END }, onClear = { leasingEnd = null })
                        DecimalField(leasingDown, { leasingDown = it }, stringResource(R.string.vehicle_field_leasing_down_payment))
                        InfoLine(
                            stringResource(R.string.vehicle_leasing_monthly),
                            info.ownershipMonthly?.let { Fmt.n("%.2f €", it) + " " + stringResource(R.string.fee_per_month) } ?: "–"
                        )
                        Text(
                            stringResource(R.string.vehicle_leasing_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (leasingDatesOk) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                        )
                    }
                }

                if (vehicle != null && onOpenCosts != null) {
                    OutlinedButton(onClick = { onOpenCosts(vehicle.id) }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                        Text(stringResource(R.string.vehicle_costs_open))
                    }
                    Text(
                        stringResource(R.string.vehicle_costs_open_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
    }

    pickingDate?.let { field ->
        // Der Picker rechnet in UTC-Mitternacht - hin und zurueck ueber UTC, sonst rutscht der Tag
        // je nach Zeitzone um eins (wie bei den Grundgebuehren).
        val current = when (field) {
            VehicleDateField.PURCHASE -> purchaseDate
            VehicleDateField.REGISTRATION -> firstRegistration ?: purchaseDate
            VehicleDateField.LEASING_START -> leasingStart
            VehicleDateField.LEASING_END -> leasingEnd ?: leasingStart?.plusYears(3)?.minusDays(1)
        } ?: LocalDate.now()
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
                            VehicleDateField.PURCHASE -> purchaseDate = date
                            VehicleDateField.REGISTRATION -> firstRegistration = date
                            VehicleDateField.LEASING_START -> leasingStart = date
                            VehicleDateField.LEASING_END -> leasingEnd = date
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
private fun DecimalField(value: String, onValueChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange, label = { Text(label) },
        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth()
    )
}

/** Datumsknopf mit Leeren-Knopf daneben - alle Daten hier sind optional. */
@Composable
private fun DateRow(label: String, date: LocalDate?, zone: ZoneId, onPick: () -> Unit, onClear: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = onPick, modifier = Modifier.weight(1f)) {
            Text("$label: " + (date?.let { Fmt.dateMedium(it.atStartOfDay(zone).toInstant().toEpochMilli()) } ?: "–"))
        }
        if (date != null) {
            IconButton(onClick = onClear) { Icon(Icons.Filled.Clear, stringResource(R.string.action_clear_date)) }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}
