package com.dominiqueherbrigpersonalteam.lademonitor.ui.sessions

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dominiqueherbrigpersonalteam.lademonitor.R
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ChargingLocation
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ChargingSession
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ChargingSessionPayload
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ChargingType
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.Provider
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.Vehicle
import com.dominiqueherbrigpersonalteam.lademonitor.data.repo.AppRepository
import com.dominiqueherbrigpersonalteam.lademonitor.data.settings.AppSettings
import com.dominiqueherbrigpersonalteam.lademonitor.ui.common.EmptyState
import com.dominiqueherbrigpersonalteam.lademonitor.ui.common.ErrorState
import com.dominiqueherbrigpersonalteam.lademonitor.ui.common.Fmt
import com.dominiqueherbrigpersonalteam.lademonitor.ui.common.FullScreenModal
import com.dominiqueherbrigpersonalteam.lademonitor.ui.filter.FilterIconButton
import com.dominiqueherbrigpersonalteam.lademonitor.ui.filter.FilterSheet
import com.dominiqueherbrigpersonalteam.lademonitor.ui.filter.SessionFilter
import com.dominiqueherbrigpersonalteam.lademonitor.ui.settings.AddEditVehicleModal
import com.dominiqueherbrigpersonalteam.lademonitor.ui.theme.Blue
import com.dominiqueherbrigpersonalteam.lademonitor.ui.theme.Green
import com.dominiqueherbrigpersonalteam.lademonitor.ui.theme.Orange
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsListScreen() {
    val scope = rememberCoroutineScope()
    val dateRange by SessionFilter.dateRange.collectAsStateWithLifecycle()

    val sessions = remember { mutableStateListOf<ChargingSession>() }
    var vehicles by remember { mutableStateOf<List<Vehicle>>(emptyList()) }
    var providers by remember { mutableStateOf<List<Provider>>(emptyList()) }
    var locations by remember { mutableStateOf<List<ChargingLocation>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    var showAdd by remember { mutableStateOf(false) }
    var showFilter by remember { mutableStateOf(false) }
    var showNoVehicleAlert by remember { mutableStateOf(false) }
    var showAddVehicle by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<ChargingSession?>(null) }
    val serverUrlRequiredMessage = stringResource(R.string.error_server_url_required)

    suspend fun load() {
        if (!AppSettings.isReadyForDataAccess) {
            errorMessage = serverUrlRequiredMessage
            return
        }
        isLoading = true
        errorMessage = null
        try {
            coroutineScope {
                val s = async { AppRepository.fetchSessions(dateRange = dateRange) }
                val v = async { AppRepository.fetchVehicles() }
                val p = async { AppRepository.fetchProviders() }
                val l = async { AppRepository.fetchLocations() }
                sessions.clear(); sessions.addAll(s.await())
                vehicles = v.await(); providers = p.await(); locations = l.await()
            }
        } catch (e: Exception) {
            errorMessage = e.localizedMessage
        }
        isLoading = false
    }

    LaunchedEffect(dateRange) { load() }

    fun confirm(session: ChargingSession) {
        scope.launch {
            val updated = runCatching {
                AppRepository.updateSession(session.id, ChargingSessionPayload(needsReview = false))
            }.getOrNull() ?: return@launch
            val index = sessions.indexOfFirst { it.id == session.id }
            if (index >= 0) sessions[index] = updated
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.dashboard_stat_sessions)) },
                actions = {
                    IconButton(onClick = {
                        if (vehicles.isEmpty()) showNoVehicleAlert = true else showAdd = true
                    }) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.sessions_add_content_description)) }
                    FilterIconButton(onClick = { showFilter = true })
                }
            )
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                errorMessage != null ->
                    ErrorState(errorMessage!!, onRetry = { scope.launch { load() } })
                sessions.isEmpty() && !isLoading ->
                    EmptyState(stringResource(R.string.sessions_empty_state))
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(sessions, key = { it.id }) { session ->
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                if (value == SwipeToDismissBoxValue.EndToStart) {
                                    sessions.remove(session)
                                    scope.launch { runCatching { AppRepository.deleteSession(session.id) } }
                                    true
                                } else false
                            }
                        )
                        SwipeToDismissBox(
                            state = dismissState,
                            enableDismissFromStartToEnd = false,
                            backgroundContent = {
                                Box(
                                    Modifier.fillMaxSize()
                                        .background(MaterialTheme.colorScheme.errorContainer)
                                        .padding(horizontal = 20.dp),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    Icon(
                                        Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete),
                                        tint = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        ) {
                            SessionRow(
                                session = session,
                                vehicleName = vehicles.firstOrNull { it.id == session.vehicleId }?.name,
                                providerName = providers.firstOrNull { it.id == session.providerId }?.name,
                                locationName = locations.firstOrNull { it.id == session.locationId }?.name,
                                onClick = { selected = session },
                                onConfirm = { confirm(session) }
                            )
                        }
                    }
                }
            }
            if (isLoading && sessions.isEmpty() && errorMessage == null) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
        }
    }

    if (showNoVehicleAlert) {
        AlertDialog(
            onDismissRequest = { showNoVehicleAlert = false },
            title = { Text(stringResource(R.string.sessions_no_vehicle_title)) },
            text = { Text(stringResource(R.string.sessions_no_vehicle_message)) },
            confirmButton = {
                TextButton(onClick = { showNoVehicleAlert = false; showAddVehicle = true }) {
                    Text(stringResource(R.string.vehicle_create_action))
                }
            },
            dismissButton = { TextButton(onClick = { showNoVehicleAlert = false }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }

    if (showAddVehicle) {
        AddEditVehicleModal(vehicle = null, onDismiss = { showAddVehicle = false }) {
            showAddVehicle = false
            scope.launch { load() }
        }
    }

    if (showAdd) {
        FullScreenModal(onDismiss = { showAdd = false }) {
            AddEditSessionScreen(
                vehicles = vehicles, providers = providers, locations = locations, session = null,
                onDismiss = { showAdd = false },
                onSaved = { showAdd = false; scope.launch { load() } }
            )
        }
    }

    selected?.let { session ->
        FullScreenModal(onDismiss = { selected = null }) {
            SessionDetailScreen(
                session = session, vehicles = vehicles, providers = providers, locations = locations,
                onDismiss = { selected = null },
                onChanged = { selected = null; scope.launch { load() } }
            )
        }
    }

    if (showFilter) FilterSheet(onDismiss = { showFilter = false })
}

@Composable
private fun SessionRow(
    session: ChargingSession,
    vehicleName: String?,
    providerName: String?,
    locationName: String?,
    onClick: () -> Unit,
    onConfirm: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    Fmt.dateTimeMedium(session.startTime),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                session.chargingTypeValue?.let {
                    Spacer(Modifier.width(6.dp))
                    ChargingTypeBadge(it)
                }
                if (session.needsReview) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = stringResource(R.string.sessions_needs_review_content_description),
                        tint = Orange,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            val place = session.geocodedPlace ?: locationName
            val subtitle = listOfNotNull(vehicleName, providerName, place).joinToString(" · ")
            val hasCoords = session.latitude != null && session.longitude != null
            if (subtitle.isNotEmpty() || hasCoords) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    if (subtitle.isNotEmpty()) {
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (hasCoords) {
                        Icon(Icons.Filled.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                    }
                }
            }
            val details = buildString {
                when {
                    session.socStart != null && session.socEnd != null ->
                        append(stringResource(R.string.session_soc_range, session.socStart!!, session.socEnd!!))
                    session.socStart != null -> append(stringResource(R.string.session_soc_from, session.socStart!!))
                    session.socEnd != null -> append(stringResource(R.string.session_soc_to, session.socEnd!!))
                }
                session.odometerKm?.let {
                    if (isNotEmpty()) append(" · ")
                    append(Fmt.km(it))
                }
            }
            if (details.isNotEmpty()) {
                Text(details, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val method = session.consumptionMethodValue
            if (session.consumptionKwhPer100km != null && method != null && method != com.dominiqueherbrigpersonalteam.lademonitor.data.model.ConsumptionMethod.UNAVAILABLE) {
                Text(
                    (if (method.marker.isNotEmpty()) method.marker + " " else "") +
                        Fmt.n("%.1f kWh/100km", session.consumptionKwhPer100km!!),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            session.energyKwh?.let { Text(Fmt.n("%.1f kWh", it), style = MaterialTheme.typography.bodyMedium) }
            session.priceTotal?.let { Text(Fmt.n("%.2f €", it), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            session.pricePerKwh?.let { Text(Fmt.n("%.3f €/kWh", it), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            session.feeShare?.let {
                Text(stringResource(R.string.sessions_fee_share, Fmt.n("%.2f €", it)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (session.needsReview) {
                IconButton(onClick = onConfirm, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.action_confirm), tint = Green)
                }
            }
        }
    }
}

@Composable
fun ChargingTypeBadge(type: ChargingType) {
    val color = if (type == ChargingType.DC) Orange else Blue
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.15f)).padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(type.raw, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
    }
}
