package com.dominiqueherbrigpersonalteam.lademonitor.ui.settings

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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.dominiqueherbrigpersonalteam.lademonitor.R
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.TireComparison
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.TireKind
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.TireMounting
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.TireOverview
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.TireSet
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.TireSetPayload
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.TireSetSummary
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.Vehicle
import com.dominiqueherbrigpersonalteam.lademonitor.data.remote.ApiClient
import com.dominiqueherbrigpersonalteam.lademonitor.data.repo.AppRepository
import com.dominiqueherbrigpersonalteam.lademonitor.data.settings.AppSettings
import com.dominiqueherbrigpersonalteam.lademonitor.ui.common.Fmt
import com.dominiqueherbrigpersonalteam.lademonitor.ui.common.FullScreenModal
import com.dominiqueherbrigpersonalteam.lademonitor.ui.common.SectionCard
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * Reifenwechsel erfassen und sehen, was auf welchem Satz gelaufen ist.
 *
 * **Nur im Server-Modus erreichbar** (SettingsScreen blendet den Punkt sonst
 * aus): Zuordnung der Fahrten und temperaturbereinigter Vergleich liegen
 * komplett auf dem Server. Siehe Models.kt, Abschnitt "Reifen".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TiresSettingsScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    var sets by remember { mutableStateOf<List<TireSet>>(emptyList()) }
    var vehicles by remember { mutableStateOf<List<Vehicle>>(emptyList()) }
    var overview by remember { mutableStateOf<TireOverview?>(null) }
    var comparison by remember { mutableStateOf<TireComparison?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<TireSet?>(null) }
    var pendingDelete by remember { mutableStateOf<TireMounting?>(null) }
    val serverAddressRequiredMessage = stringResource(R.string.error_server_address_required)

    suspend fun load() {
        if (!AppSettings.isReadyForDataAccess) { errorMessage = serverAddressRequiredMessage; return }
        try {
            sets = ApiClient.fetchTireSets()
            vehicles = AppRepository.fetchVehicles()
            errorMessage = null
        } catch (e: Exception) {
            errorMessage = e.localizedMessage
        }
        // Die Auswertungen einzeln und fehlertolerant: gegen einen Server ohne
        // diese Endpunkte bleibt die Verwaltung trotzdem benutzbar.
        overview = runCatching { ApiClient.fetchTireOverview() }.getOrNull()
        comparison = runCatching { ApiClient.fetchTireComparison() }.getOrNull()
    }
    LaunchedEffect(Unit) { load() }

    // Die Wechsel-Liste kommt aus der Uebersicht, weil nur sie Zeitraum und
    // Laufleistung kennt. Fehlt sie, werden die reinen Stammdaten gezeigt
    // statt gar nichts.
    val mountings: List<TireMounting> = overview?.mountings?.takeIf { it.isNotEmpty() }
        ?: sets.map {
            TireMounting(
                tireSetId = it.id, vehicleId = it.vehicleId, kind = it.kind,
                label = it.label, installedOn = it.installedOn
            )
        }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.settings_nav_tires)) },
            navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                }
            },
            actions = {
                IconButton(onClick = { showAdd = true }, enabled = vehicles.isNotEmpty()) {
                    Icon(Icons.Filled.Add, stringResource(R.string.sessions_add_content_description))
                }
            }
        )
    }) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            errorMessage?.let {
                item { Text(it, Modifier.padding(top = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }

            comparison?.winterVsSummerPct?.let { value ->
                item { ComparisonCard(comparison!!, value) }
            }

            overview?.sets?.takeIf { it.isNotEmpty() }?.let { summaries ->
                item {
                    SectionCard {
                        SectionHeader(stringResource(R.string.tires_section_sets))
                        summaries.forEachIndexed { index, summary ->
                            if (index > 0) HorizontalDivider()
                            TireSetSummaryRow(summary)
                        }
                    }
                }
            }

            item {
                SectionCard {
                    SectionHeader(stringResource(R.string.tires_section_changes))
                    if (mountings.isEmpty()) {
                        Text(
                            stringResource(R.string.tires_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            items(mountings, key = { it.tireSetId }) { mounting ->
                SectionCard {
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { editing = sets.firstOrNull { it.id == mounting.tireSetId } },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) { TireMountingRow(mounting, vehicles) }
                        IconButton(onClick = { pendingDelete = mounting }) {
                            Icon(Icons.Filled.Delete, stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            overview?.let { data ->
                if (data.drivesWithoutSet + data.drivesSpanningChange > 0) {
                    item {
                        Text(
                            stringResource(R.string.tires_excluded, data.drivesWithoutSet, data.drivesSpanningChange),
                            Modifier.padding(bottom = 16.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddEditTireSetModal(null, vehicles, onDismiss = { showAdd = false }) {
            showAdd = false; scope.launch { load() }
        }
    }
    editing?.let { set ->
        AddEditTireSetModal(set, vehicles, onDismiss = { editing = null }) {
            editing = null; scope.launch { load() }
        }
    }

    pendingDelete?.let { mounting ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.tires_delete_confirm_title)) },
            text = { Text(stringResource(R.string.tires_delete_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    val target = mounting; pendingDelete = null
                    scope.launch { runCatching { ApiClient.deleteTireSet(target.tireSetId) }; load() }
                }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }
}

@Composable
private fun ComparisonCard(comparison: TireComparison, winterVsSummer: Double) {
    SectionCard {
        SectionHeader(stringResource(R.string.tires_comparison_title))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                (if (winterVsSummer >= 0) "+" else "") + Fmt.n("%.1f", winterVsSummer) + " %",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.tertiary
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(stringResource(R.string.tires_hero_label), style = MaterialTheme.typography.bodyMedium)
                comparison.referenceTempC?.let {
                    Text(
                        stringResource(R.string.tires_reference_temp, Fmt.n("%.0f", it)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        // Ohne gemeinsamen Temperaturbereich ist die Bereinigung eine
        // Hochrechnung - das gehoert an die Zahl, nicht in eine Fussnote.
        if (!comparison.overlapOk) {
            val span = comparison.overlapSpanC
            Text(
                if (span == null || span <= 0) stringResource(R.string.tires_overlap_missing)
                else stringResource(R.string.tires_overlap_warning, Fmt.n("%.1f", span)),
                Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary
            )
        }
        Text(
            stringResource(R.string.tires_comparison_hint),
            Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TireSetSummaryRow(summary: TireSetSummary) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                summary.label.ifBlank { stringResource(summary.tireKind.labelRes()) },
                style = MaterialTheme.typography.bodyLarge
            )
            if (summary.isCurrent) {
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier.clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        stringResource(R.string.tires_badge_current),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
        Text(
            stringResource(R.string.tires_stats_set, summary.km.roundToInt(), summary.drives) +
                (summary.avgConsumptionKwhPer100km?.let { " · " + Fmt.n("%.1f kWh/100 km", it) } ?: ""),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        // Alter und montierte Zeit nebeneinander: Gummi altert auch im Keller,
        // die Laufleistung tut es nicht.
        Text(
            stringResource(
                R.string.tires_stats_age,
                tireDuration(summary.daysMounted),
                tireDuration(summary.ageDays),
                summary.mountings
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TireMountingRow(mounting: TireMounting, vehicles: List<Vehicle>) {
    val vehicleName = vehicles.firstOrNull { it.id == mounting.vehicleId }?.name
    // Das Ende einer Montage steht nicht am Datensatz - es ist der naechste
    // Wechsel, und solange keiner folgt, liegt der Satz noch drauf.
    val until = mounting.removedOn?.let { stringResource(R.string.tires_until, Fmt.dateMedium(it)) }
        ?: stringResource(R.string.tires_until_today)

    Column(Modifier.padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                mounting.label.ifBlank { stringResource(mounting.tireKind.labelRes()) },
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                Fmt.dateMedium(mounting.installedOn),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            listOfNotNull(stringResource(mounting.tireKind.labelRes()), vehicleName, until).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (mounting.drives > 0) {
            Text(
                stringResource(
                    R.string.tires_stats_mounting,
                    mounting.km.roundToInt(),
                    mounting.drives,
                    tireDuration(mounting.days)
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Ein Reifen laeuft ueber Jahre - in Tagen ist das ab einem gewissen Punkt
 * keine ablesbare Zahl mehr.
 */
@Composable
private fun tireDuration(days: Int): String =
    if (days >= 365) stringResource(R.string.tires_unit_years, Fmt.n("%.1f", days / 365.0))
    else stringResource(R.string.tires_unit_days, days)

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

private fun TireKind.labelRes(): Int = when (this) {
    TireKind.SUMMER -> R.string.tires_kind_summer
    TireKind.WINTER -> R.string.tires_kind_winter
    TireKind.ALL_SEASON -> R.string.tires_kind_all_season
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditTireSetModal(
    tireSet: TireSet?,
    vehicles: List<Vehicle>,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val zone = ZoneId.systemDefault()
    val isEditing = tireSet != null

    var vehicleId by remember { mutableStateOf(tireSet?.vehicleId ?: vehicles.firstOrNull()?.id ?: "") }
    var kind by remember { mutableStateOf(tireSet?.tireKind ?: TireKind.SUMMER) }
    var installedOn by remember { mutableStateOf(tireSet?.installedOn ?: System.currentTimeMillis()) }
    var size by remember { mutableStateOf(tireSet?.size ?: "") }
    var brand by remember { mutableStateOf(tireSet?.brand ?: "") }
    var model by remember { mutableStateOf(tireSet?.model ?: "") }
    var notes by remember { mutableStateOf(tireSet?.notes ?: "") }
    var vehicleMenu by remember { mutableStateOf(false) }
    var kindMenu by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun save() {
        scope.launch {
            isSaving = true; errorMessage = null
            // Der Server rechnet in naiven datetimes; die Uhrzeit des Wechsels
            // weiss ohnehin niemand mehr, deshalb Tagesbeginn.
            val day = Instant.ofEpochMilli(installedOn).atZone(zone).toLocalDate()
                .atStartOfDay(zone).toInstant().toEpochMilli()
            val payload = TireSetPayload(
                vehicleId = if (isEditing) null else vehicleId,
                kind = kind.wire,
                installedOn = day,
                size = size.trim().ifEmpty { null },
                brand = brand.trim().ifEmpty { null },
                model = model.trim().ifEmpty { null },
                notes = notes.trim().ifEmpty { null }
            )
            try {
                if (tireSet != null) ApiClient.updateTireSet(tireSet.id, payload)
                else ApiClient.createTireSet(payload)
                onSaved()
            } catch (e: Exception) { errorMessage = e.localizedMessage }
            isSaving = false
        }
    }

    FullScreenModal(onDismiss = onDismiss) {
        Scaffold(topBar = {
            TopAppBar(
                title = {
                    Text(if (isEditing) stringResource(R.string.tires_edit_title) else stringResource(R.string.tires_add_title))
                },
                navigationIcon = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
                actions = {
                    TextButton(onClick = { save() }, enabled = !isSaving && vehicleId.isNotEmpty()) {
                        Text(if (isSaving) stringResource(R.string.action_saving) else stringResource(R.string.action_save))
                    }
                }
            )
        }) { padding ->
            Column(
                Modifier.padding(padding).padding(16.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box {
                    OutlinedButton(onClick = { vehicleMenu = true }, enabled = !isEditing, modifier = Modifier.fillMaxWidth()) {
                        Text(vehicles.firstOrNull { it.id == vehicleId }?.name ?: stringResource(R.string.entity_vehicle))
                    }
                    DropdownMenu(expanded = vehicleMenu, onDismissRequest = { vehicleMenu = false }) {
                        vehicles.forEach { vehicle ->
                            DropdownMenuItem(
                                text = { Text(vehicle.name) },
                                onClick = { vehicleId = vehicle.id; vehicleMenu = false }
                            )
                        }
                    }
                }
                Box {
                    OutlinedButton(onClick = { kindMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(kind.labelRes()))
                    }
                    DropdownMenu(expanded = kindMenu, onDismissRequest = { kindMenu = false }) {
                        TireKind.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(stringResource(option.labelRes())) },
                                onClick = { kind = option; kindMenu = false }
                            )
                        }
                    }
                }
                OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.tires_field_installed_on) + ": " + Fmt.dateMedium(installedOn))
                }
                Text(
                    stringResource(R.string.tires_form_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(value = size, onValueChange = { size = it }, label = { Text(stringResource(R.string.tires_field_size)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = brand, onValueChange = { brand = it }, label = { Text(stringResource(R.string.vehicle_field_brand)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = model, onValueChange = { model = it }, label = { Text(stringResource(R.string.vehicle_field_model)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text(stringResource(R.string.tires_field_notes)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = installedOn)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    // Der Picker liefert UTC-Mitternacht - als lokales Datum
                    // lesen, sonst rutscht der Tag je nach Zeitzone.
                    state.selectedDateMillis?.let { picked ->
                        val date: LocalDate = Instant.ofEpochMilli(picked).atZone(ZoneId.of("UTC")).toLocalDate()
                        installedOn = date.atStartOfDay(zone).toInstant().toEpochMilli()
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.action_cancel)) } }
        ) { DatePicker(state = state) }
    }
}
