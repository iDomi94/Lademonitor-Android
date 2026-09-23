package com.dominiqueherbrigpersonalteam.lademonitor.ui.sessions

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dominiqueherbrigpersonalteam.lademonitor.R
import com.dominiqueherbrigpersonalteam.lademonitor.data.location.CurrentLocationProvider
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ChargingLocation
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ChargingSession
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ChargingSessionPayload
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ChargingType
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.GeocodeResult
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.Provider
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.Vehicle
import com.dominiqueherbrigpersonalteam.lademonitor.data.repo.AppRepository
import com.dominiqueherbrigpersonalteam.lademonitor.ui.common.Fmt
import com.dominiqueherbrigpersonalteam.lademonitor.ui.common.SectionCard
import com.dominiqueherbrigpersonalteam.lademonitor.ui.settings.AddEditLocationModal
import com.dominiqueherbrigpersonalteam.lademonitor.ui.settings.AddEditProviderModal
import com.dominiqueherbrigpersonalteam.lademonitor.ui.settings.LocationPrefill
import com.dominiqueherbrigpersonalteam.lademonitor.ui.theme.Green
import com.dominiqueherbrigpersonalteam.lademonitor.ui.theme.Orange
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditSessionScreen(
    vehicles: List<Vehicle>,
    providers: List<Provider>,
    locations: List<ChargingLocation>,
    session: ChargingSession?,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val zone = ZoneId.systemDefault()
    val isEditing = session != null

    var providerList by remember { mutableStateOf(providers) }
    var locationList by remember { mutableStateOf(locations) }
    // Zugeordneter Ladeort. Wird nur gesendet, wenn gesetzt - die App kann eine Zuordnung
    // herstellen, aber (wie bisher) nicht aufheben.
    var locationId by remember { mutableStateOf(session?.locationId) }
    var showCreateLocation by remember { mutableStateOf(false) }
    var vehicleId by remember { mutableStateOf(session?.vehicleId ?: vehicles.firstOrNull()?.id ?: "") }
    var providerId by remember { mutableStateOf(session?.providerId) }
    var startTime by remember { mutableStateOf(session?.startTime ?: System.currentTimeMillis()) }
    var chargingType by remember { mutableStateOf(session?.chargingTypeValue ?: ChargingType.AC) }
    var socEnabled by remember { mutableStateOf(session?.socStart != null || session?.socEnd != null) }
    var socStart by remember { mutableStateOf((session?.socStart ?: 20).toFloat()) }
    var socEnd by remember { mutableStateOf((session?.socEnd ?: 80).toFloat()) }
    var energyKwh by remember { mutableStateOf(session?.energyKwh?.let { Fmt.n("%.2f", it) } ?: "") }
    var pricePerKwh by remember { mutableStateOf(session?.pricePerKwh?.let { Fmt.n("%.4f", it) } ?: "") }
    var priceTotal by remember { mutableStateOf(session?.priceTotal?.let { Fmt.n("%.2f", it) } ?: "") }
    var odometerKm by remember { mutableStateOf(session?.odometerKm?.toString() ?: "") }
    var outsideTempC by remember { mutableStateOf(session?.outsideTempC?.toString() ?: "") }
    var geocodedPlace by remember { mutableStateOf(session?.geocodedPlace ?: "") }
    var latitude by remember { mutableStateOf(session?.latitude?.let { Fmt.n("%.6f", it) } ?: "") }
    var longitude by remember { mutableStateOf(session?.longitude?.let { Fmt.n("%.6f", it) } ?: "") }

    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showAddProvider by remember { mutableStateOf(false) }

    var addressQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<GeocodeResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var searchMessage by remember { mutableStateOf<String?>(null) }
    var isLocating by remember { mutableStateOf(false) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var providerMenu by remember { mutableStateOf(false) }
    var locationMenu by remember { mutableStateOf(false) }
    val searchNoResultsMessage = stringResource(R.string.add_session_search_no_results)
    val searchFailedMessage = stringResource(R.string.location_search_failed)

    fun suggestPrice() {
        if (isEditing || pricePerKwh.isNotEmpty()) return
        val p = providerList.firstOrNull { it.id == providerId } ?: return
        val price = if (chargingType == ChargingType.DC) p.lastPriceDcPerKwh else p.lastPriceAcPerKwh
        price?.let { pricePerKwh = Fmt.n("%.4f", it) }
    }

    fun fetchLocation() {
        scope.launch {
            isLocating = true; errorMessage = null
            try {
                val c = CurrentLocationProvider.requestCurrentLocation(context)
                latitude = Fmt.n("%.6f", c.latitude); longitude = Fmt.n("%.6f", c.longitude)
            } catch (e: Exception) {
                errorMessage = e.localizedMessage
            }
            isLocating = false
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) fetchLocation() else errorMessage = CurrentLocationProvider.LocationException.Denied.message }

    fun search() {
        val q = addressQuery.trim()
        if (q.isEmpty()) return
        scope.launch {
            isSearching = true; searchMessage = null
            try {
                val results = AppRepository.forwardGeocode(q)
                searchResults = results
                if (results.isEmpty()) searchMessage = searchNoResultsMessage
            } catch (e: Exception) {
                searchResults = emptyList(); searchMessage = searchFailedMessage
            }
            isSearching = false
        }
    }

    // Vorbelegung fuer "Als Ladeort anlegen": die Koordinaten dieses Vorgangs, als Name der
    // Ortstext bzw. der zuletzt gewaehlte Suchtreffer, als Standard-Anbieter der Anbieter
    // dieses Vorgangs. null ohne Koordinaten.
    fun locationPrefill(): LocationPrefill? {
        val lat = latitude.replace(",", ".").toDoubleOrNull() ?: return null
        val lon = longitude.replace(",", ".").toDoubleOrNull() ?: return null
        return LocationPrefill(
            name = geocodedPlace.trim().ifEmpty { addressQuery.trim() },
            latitude = lat,
            longitude = lon,
            defaultProviderId = providerId
        )
    }

    fun save() {
        scope.launch {
            isSaving = true; errorMessage = null
            val payload = ChargingSessionPayload(
                vehicleId = if (isEditing) null else vehicleId,
                providerId = providerId,
                locationId = locationId,
                startTime = startTime,
                chargingType = chargingType.raw,
                socStart = if (socEnabled) socStart.toInt() else null,
                socEnd = if (socEnabled) socEnd.toInt() else null,
                energyKwh = energyKwh.replace(",", ".").toDoubleOrNull(),
                pricePerKwh = pricePerKwh.replace(",", ".").toDoubleOrNull(),
                priceTotal = priceTotal.replace(",", ".").toDoubleOrNull(),
                odometerKm = odometerKm.toIntOrNull(),
                outsideTempC = outsideTempC.replace(",", ".").toDoubleOrNull(),
                latitude = latitude.replace(",", ".").toDoubleOrNull(),
                longitude = longitude.replace(",", ".").toDoubleOrNull(),
                geocodedPlace = geocodedPlace.trim().ifEmpty { null },
                notes = null,
                needsReview = if (isEditing) false else null
            )
            try {
                if (session != null) AppRepository.updateSession(session.id, payload)
                else AppRepository.createSession(payload)
                onSaved()
            } catch (e: Exception) {
                errorMessage = e.localizedMessage
            }
            isSaving = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditing) stringResource(R.string.action_edit) else stringResource(R.string.session_add_title)) },
                navigationIcon = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
                actions = {
                    TextButton(onClick = { save() }, enabled = !isSaving && vehicleId.isNotEmpty()) {
                        Text(if (isSaving) stringResource(R.string.action_saving) else stringResource(R.string.action_save))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Vehicle & time
            SectionCard {
                FieldLabel(stringResource(R.string.session_detail_section_vehicle_time))
                Box {
                    var vehicleMenu by remember { mutableStateOf(false) }
                    OutlinedButton(onClick = { vehicleMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(vehicles.firstOrNull { it.id == vehicleId }?.name ?: stringResource(R.string.session_select_vehicle_placeholder))
                    }
                    DropdownMenu(expanded = vehicleMenu, onDismissRequest = { vehicleMenu = false }) {
                        vehicles.forEach { v ->
                            DropdownMenuItem(text = { Text(v.name) }, onClick = { vehicleId = v.id; vehicleMenu = false })
                        }
                    }
                }
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.weight(1f)) {
                        Text(Fmt.dateTimeMedium(startTime).substringBefore(","))
                    }
                    OutlinedButton(onClick = { showTimePicker = true }, modifier = Modifier.weight(1f)) {
                        Text(Fmt.dateTimeMedium(startTime).substringAfter(", "))
                    }
                }
            }

            // Provider & type
            SectionCard {
                FieldLabel(stringResource(R.string.add_session_section_provider_type))
                Box {
                    OutlinedButton(onClick = { providerMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(providerList.firstOrNull { it.id == providerId }?.name ?: stringResource(R.string.provider_none_selected))
                    }
                    DropdownMenu(expanded = providerMenu, onDismissRequest = { providerMenu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.provider_none_selected)) }, onClick = { providerId = null; providerMenu = false; suggestPrice() })
                        providerList.forEach { p ->
                            DropdownMenuItem(text = { Text(p.name) }, onClick = { providerId = p.id; providerMenu = false; suggestPrice() })
                        }
                        DropdownMenuItem(text = { Text(stringResource(R.string.provider_add_new_ellipsis)) }, onClick = { providerMenu = false; showAddProvider = true })
                    }
                }
                Spacer(Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ChargingType.entries.forEachIndexed { index, type ->
                        SegmentedButton(
                            selected = chargingType == type,
                            onClick = { chargingType = type; suggestPrice() },
                            shape = SegmentedButtonDefaults.itemShape(index, ChargingType.entries.size)
                        ) { Text(type.raw) }
                    }
                }
            }

            // SoC
            SectionCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(stringResource(R.string.add_session_soc_toggle_label), Modifier.weight(1f))
                    Switch(checked = socEnabled, onCheckedChange = { socEnabled = it })
                }
                if (socEnabled) {
                    SoCSlider(stringResource(R.string.session_detail_label_start), socStart, Orange) { socStart = it }
                    SoCSlider(stringResource(R.string.add_session_soc_end_label), socEnd, Green) { socEnd = it }
                }
            }

            // Energy & price
            SectionCard {
                FieldLabel(stringResource(R.string.add_session_section_energy_price))
                DecimalField(stringResource(R.string.session_detail_label_kwh), energyKwh, stringResource(R.string.add_session_kwh_placeholder)) { energyKwh = it }
                DecimalField(stringResource(R.string.add_session_field_price_per_kwh), pricePerKwh, stringResource(R.string.field_optional_placeholder)) { pricePerKwh = it }
                DecimalField(stringResource(R.string.add_session_field_total_price), priceTotal, stringResource(R.string.field_optional_placeholder)) { priceTotal = it }
            }

            // Misc
            SectionCard {
                FieldLabel(stringResource(R.string.add_session_section_misc))
                OutlinedTextField(
                    value = geocodedPlace, onValueChange = { geocodedPlace = it },
                    label = { Text(stringResource(R.string.add_session_field_location_optional)) }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = odometerKm, onValueChange = { odometerKm = it.filter { c -> c.isDigit() } },
                    label = { Text(stringResource(R.string.add_session_field_odometer_optional)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                // Aussentemperatur BEIM LADEBEGINN - Grundlage der Verbrauchsauswertung nach
                // Temperatur im Dashboard. Wer Home Assistant nutzt, bekommt den Wert
                // automatisch; hier steht er zum Nachtragen. Minuszeichen und Komma muessen
                // durch, deshalb kein reiner Ziffernfilter wie beim Kilometerstand.
                OutlinedTextField(
                    value = outsideTempC,
                    onValueChange = { input -> outsideTempC = input.filter { it.isDigit() || it in "-,." } },
                    label = { Text(stringResource(R.string.add_session_field_outside_temp_optional)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }

            // Position
            SectionCard {
                FieldLabel(stringResource(R.string.add_session_section_position))
                Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = addressQuery, onValueChange = { addressQuery = it },
                        label = { Text(stringResource(R.string.location_field_address_search)) }, singleLine = true, modifier = Modifier.weight(1f)
                    )
                    if (isSearching) CircularProgressIndicator(Modifier.padding(8.dp).height(24.dp), strokeWidth = 2.dp)
                    else IconButton(onClick = { search() }, enabled = addressQuery.isNotBlank()) {
                        Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.action_search))
                    }
                }
                searchMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                searchResults.forEach { r ->
                    TextButton(onClick = {
                        latitude = Fmt.n("%.6f", r.latitude); longitude = Fmt.n("%.6f", r.longitude)
                        addressQuery = r.displayName; searchResults = emptyList(); searchMessage = null
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text(r.displayName, Modifier.fillMaxWidth())
                    }
                }
                OutlinedButton(
                    onClick = { permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
                    enabled = !isLocating, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Icon(Icons.Filled.LocationOn, contentDescription = null)
                    Spacer(Modifier.height(0.dp))
                    Text(stringResource(R.string.location_current_location_action))
                    if (isLocating) { Spacer(Modifier.weight(1f)); CircularProgressIndicator(Modifier.height(20.dp), strokeWidth = 2.dp) }
                }
                if (locationList.isNotEmpty()) {
                    Box {
                        OutlinedButton(onClick = { locationMenu = true }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            Text(stringResource(R.string.add_session_use_location_action))
                        }
                        DropdownMenu(expanded = locationMenu, onDismissRequest = { locationMenu = false }) {
                            locationList.forEach { l ->
                                DropdownMenuItem(text = { Text(l.name) }, onClick = {
                                    latitude = Fmt.n("%.6f", l.latitude); longitude = Fmt.n("%.6f", l.longitude)
                                    addressQuery = l.name; locationId = l.id; locationMenu = false
                                })
                            }
                        }
                    }
                }
                if (latitude.isNotEmpty() || longitude.isNotEmpty()) {
                    Text(stringResource(R.string.add_session_coordinates_format, latitude, longitude), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                }
                val assignedLocation = locationList.firstOrNull { it.id == locationId }
                if (assignedLocation != null) {
                    Text(stringResource(R.string.add_session_assigned_location_format, assignedLocation.name),
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                } else if (locationPrefill() != null) {
                    OutlinedButton(onClick = { showCreateLocation = true }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Icon(Icons.Filled.AddLocationAlt, contentDescription = null)
                        Text(stringResource(R.string.add_session_create_location_action))
                    }
                }
            }

            errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }

    if (showAddProvider) {
        AddEditProviderModal(provider = null, onDismiss = { showAddProvider = false }) { newProvider ->
            providerList = providerList + newProvider
            providerId = newProvider.id
            showAddProvider = false
        }
    }

    if (showCreateLocation) {
        locationPrefill()?.let { prefill ->
            AddEditLocationModal(
                location = null,
                providers = providerList,
                onDismiss = { showCreateLocation = false },
                prefill = prefill,
                onCreated = { created ->
                    locationList = locationList + created
                    locationId = created.id
                }
            ) { showCreateLocation = false }
        }
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = startTime)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { picked ->
                        val time = Instant.ofEpochMilli(startTime).atZone(zone).toLocalTime()
                        val date = Instant.ofEpochMilli(picked).atZone(ZoneId.of("UTC")).toLocalDate()
                        startTime = date.atTime(time).atZone(zone).toInstant().toEpochMilli()
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.action_cancel)) } }
        ) { DatePicker(state = state) }
    }

    if (showTimePicker) {
        val cur = Instant.ofEpochMilli(startTime).atZone(zone).toLocalTime()
        val state = rememberTimePickerState(initialHour = cur.hour, initialMinute = cur.minute, is24Hour = true)
        DatePickerDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val date = Instant.ofEpochMilli(startTime).atZone(zone).toLocalDate()
                    startTime = date.atTime(LocalTime.of(state.hour, state.minute)).atZone(zone).toInstant().toEpochMilli()
                    showTimePicker = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text(stringResource(R.string.action_cancel)) } }
        ) {
            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                TimePicker(state = state)
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun DecimalField(label: String, value: String, placeholder: String, onValue: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onValue,
        label = { Text(label) }, placeholder = { Text(placeholder) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
    )
}

@Composable
private fun SoCSlider(title: String, value: Float, tint: androidx.compose.ui.graphics.Color, onValue: (Float) -> Unit) {
    Column(Modifier.padding(top = 8.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text("${value.toInt()} %", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            value = value, onValueChange = onValue, valueRange = 0f..100f, steps = 99,
            colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = tint, activeTrackColor = tint)
        )
    }
}
