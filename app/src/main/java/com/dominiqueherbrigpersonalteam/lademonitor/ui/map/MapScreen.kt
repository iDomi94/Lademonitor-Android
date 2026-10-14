package com.dominiqueherbrigpersonalteam.lademonitor.ui.map

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.Color as AndroidColor
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.EvStation
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dominiqueherbrigpersonalteam.lademonitor.R
import com.dominiqueherbrigpersonalteam.lademonitor.data.location.CurrentLocationProvider
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ChargingLocation
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ChargingSession
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
import com.dominiqueherbrigpersonalteam.lademonitor.ui.sessions.SessionDetailScreen
import com.dominiqueherbrigpersonalteam.lademonitor.ui.settings.AddEditLocationModal
import com.dominiqueherbrigpersonalteam.lademonitor.ui.theme.Blue
import com.dominiqueherbrigpersonalteam.lademonitor.ui.theme.Orange
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.osmdroid.events.DelayedMapListener
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import kotlin.math.abs
import kotlin.math.max

/**
 * Eine oder mehrere raeumlich nahe beieinanderliegende Ladevorgaenge (siehe [clusterSessions]).
 * Bei einem einzelnen Mitglied entspricht das Cluster einem normalen Einzel-Pin.
 */
private data class SessionCluster(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val sessions: List<ChargingSession>
) {
    val needsReview: Boolean get() = sessions.any { it.needsReview }
}

/**
 * Gruppiert Ladevorgaenge, die naeher beieinanderliegen als 6 % der sichtbaren Kantenlaenge
 * (einfaches Single-Linkage-Clustering) - dieselbe Rechnung wie `sessionClusters` in der iOS-App
 * und im Web, neu gebildet nach jedem Verschieben/Zoomen.
 */
private fun clusterSessions(pins: List<ChargingSession>, visibleSpan: Double): List<SessionCluster> {
    val threshold = visibleSpan * 0.06
    val remaining = pins.toMutableList()
    val clusters = mutableListOf<SessionCluster>()
    while (remaining.isNotEmpty()) {
        val first = remaining.removeAt(0)
        val group = mutableListOf(first)
        val iterator = remaining.iterator()
        while (iterator.hasNext()) {
            val pin = iterator.next()
            if (abs(pin.latitude!! - first.latitude!!) < threshold && abs(pin.longitude!! - first.longitude!!) < threshold) {
                group += pin
                iterator.remove()
            }
        }
        clusters += SessionCluster(
            id = group.map { it.id }.sorted().joinToString("|"),
            latitude = group.map { it.latitude!! }.average(),
            longitude = group.map { it.longitude!! }.average(),
            sessions = group
        )
    }
    return clusters
}

/** Runder Marker: Zahl fuer ein Cluster, ohne Zahl fuer einen einzelnen Ladevorgang. */
private fun circleIcon(context: Context, fill: Int, sizeDp: Float, label: String?, strokeDp: Float): BitmapDrawable {
    val density = context.resources.displayMetrics.density
    val size = (sizeDp * density).toInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val radius = size / 2f
    val stroke = strokeDp * density
    canvas.drawCircle(radius, radius, radius - stroke / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill })
    canvas.drawCircle(radius, radius, radius - stroke / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = stroke
        color = AndroidColor.WHITE
    })
    if (label != null) {
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.WHITE
            textSize = 12f * density
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(label, radius, radius - (text.descent() + text.ascent()) / 2f, text)
    }
    return BitmapDrawable(context.resources, bitmap)
}

/** Groesste Kantenlaenge (Grad) des sichtbaren Ausschnitts - Grundlage der Cluster-Schwelle. */
private fun visibleSpan(map: MapView): Double {
    val box = map.boundingBox
    return max(box.latitudeSpan, box.longitudeSpan)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen() {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val dateRange by SessionFilter.dateRange.collectAsStateWithLifecycle()

    var locations by remember { mutableStateOf<List<ChargingLocation>>(emptyList()) }
    var sessions by remember { mutableStateOf<List<ChargingSession>>(emptyList()) }
    var vehicles by remember { mutableStateOf<List<Vehicle>>(emptyList()) }
    var providers by remember { mutableStateOf<List<Provider>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var showLocations by remember { mutableStateOf(true) }
    var showSessions by remember { mutableStateOf(true) }
    var showFilter by remember { mutableStateOf(false) }
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var didFit by remember { mutableStateOf(false) }
    // Sichtbare Kantenlaenge in Grad; steuert das Clustering und wird nach jedem Verschieben/Zoomen
    // nachgezogen.
    var span by remember { mutableStateOf(1.0) }

    // Eigener Standort fuer den Knopf "Auf aktuellen Standort zoomen".
    var userLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var isLocating by remember { mutableStateOf(false) }
    var locationError by remember { mutableStateOf<String?>(null) }

    var locationToEdit by remember { mutableStateOf<ChargingLocation?>(null) }
    var sessionToPreview by remember { mutableStateOf<ChargingSession?>(null) }
    // Ladevorgaenge eines Clusters, das sich durch Zoomen nicht weiter auftrennen laesst.
    var clusterList by remember { mutableStateOf<List<ChargingSession>?>(null) }

    val sessionPins = sessions.filter { it.latitude != null && it.longitude != null }
    val serverUrlRequiredMessage = stringResource(R.string.error_server_url_required)

    suspend fun load() {
        if (!AppSettings.isReadyForDataAccess) {
            errorMessage = serverUrlRequiredMessage
            return
        }
        isLoading = true; errorMessage = null
        try {
            coroutineScope {
                val l = async { AppRepository.fetchLocations() }
                val s = async { AppRepository.fetchSessions(dateRange = dateRange) }
                val v = async { AppRepository.fetchVehicles() }
                val p = async { AppRepository.fetchProviders() }
                locations = l.await(); sessions = s.await(); vehicles = v.await(); providers = p.await()
            }
            didFit = false
        } catch (e: Exception) {
            errorMessage = e.localizedMessage
        }
        isLoading = false
    }

    LaunchedEffect(dateRange) { load() }

    /**
     * Tipp auf ein Cluster: bei genau einem Ladevorgang direkt die Vorschau. Bei mehreren wird
     * hineingezoomt, solange sie geografisch auseinanderliegen; liegen sie praktisch am selben
     * Punkt (mehrmals an derselben Wallbox), oeffnet sich stattdessen eine Liste - wie in iOS.
     */
    fun handleClusterTap(cluster: SessionCluster) {
        if (cluster.sessions.size == 1) {
            sessionToPreview = cluster.sessions.first()
            return
        }
        val lats = cluster.sessions.map { it.latitude!! }
        val lons = cluster.sessions.map { it.longitude!! }
        val latSpan = lats.max() - lats.min()
        val lonSpan = lons.max() - lons.min()
        // ~11 m - innerhalb dieser Distanz trennt Zoomen die Punkte nicht mehr sichtbar.
        val sameSpotEpsilon = 0.0001
        val map = mapView
        if ((latSpan < sameSpotEpsilon && lonSpan < sameSpotEpsilon) || map == null) {
            clusterList = cluster.sessions
            return
        }
        val centerLat = (lats.max() + lats.min()) / 2
        val centerLon = (lons.max() + lons.min()) / 2
        val halfLat = max(latSpan * 2.5, 0.001) / 2
        val halfLon = max(lonSpan * 2.5, 0.001) / 2
        val box = BoundingBox(centerLat + halfLat, centerLon + halfLon, centerLat - halfLat, centerLon - halfLon)
        map.post { runCatching { map.zoomToBoundingBox(box, true, 48) } }
    }

    fun locateUser() {
        scope.launch {
            isLocating = true
            try {
                val c = CurrentLocationProvider.requestCurrentLocation(context)
                val point = GeoPoint(c.latitude, c.longitude)
                userLocation = point
                mapView?.let { map ->
                    // Etwa 1 km Kantenlaenge, wie der Ausschnitt in iOS.
                    val box = BoundingBox(c.latitude + 0.005, c.longitude + 0.005, c.latitude - 0.005, c.longitude - 0.005)
                    map.post { runCatching { map.zoomToBoundingBox(box, true, 0) } }
                    span = 0.01
                }
            } catch (e: Exception) {
                locationError = e.localizedMessage
            }
            isLocating = false
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) locateUser() else locationError = CurrentLocationProvider.LocationException.Denied.message
    }

    // Overlays neu aufbauen, sobald sich Daten, Schalter, Ausschnitt oder Standort aendern.
    LaunchedEffect(mapView, locations, sessions, showLocations, showSessions, span, userLocation) {
        val map = mapView ?: return@LaunchedEffect
        map.overlays.clear()
        if (showLocations) {
            locations.forEach { loc ->
                val center = GeoPoint(loc.latitude, loc.longitude)
                // Matching-Radius als Kreis: so nah muss ein Ladevorgang liegen, um diesem Ort
                // automatisch zugeordnet zu werden. Vor dem Marker gezeichnet, damit der Pin oben liegt.
                val circle = Polygon(map).apply {
                    points = Polygon.pointsAsCircle(center, loc.radiusM.toDouble())
                    fillPaint.color = AndroidColor.argb(30, 10, 132, 255)
                    outlinePaint.color = AndroidColor.argb(128, 10, 132, 255)
                    outlinePaint.strokeWidth = 2f
                }
                map.overlays.add(circle)
                map.overlays.add(Marker(map).apply {
                    position = center
                    title = loc.name
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    setOnMarkerClickListener { _, _ -> locationToEdit = loc; true }
                })
            }
        }
        if (showSessions) {
            clusterSessions(sessionPins, span).forEach { cluster ->
                val fill = if (cluster.needsReview) Orange.toArgb()
                else if (cluster.sessions.size > 1) Blue.toArgb()
                else AndroidColor.GRAY
                map.overlays.add(Marker(map).apply {
                    position = GeoPoint(cluster.latitude, cluster.longitude)
                    icon = if (cluster.sessions.size > 1) circleIcon(context, fill, 30f, "${cluster.sessions.size}", 2f)
                    else circleIcon(context, fill, 18f, null, 1.5f)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    title = if (cluster.sessions.size == 1) Fmt.dateTimeShort(cluster.sessions.first().startTime) else null
                    setOnMarkerClickListener { _, _ -> handleClusterTap(cluster); true }
                })
            }
        }
        userLocation?.let { point ->
            // Blauer Punkt fuer die eigene Position, erst nach einem Tipp auf den Standort-Knopf.
            map.overlays.add(Marker(map).apply {
                position = point
                icon = circleIcon(context, Blue.toArgb(), 16f, null, 3f)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                setOnMarkerClickListener { _, _ -> true }
            })
        }
        map.invalidate()

        if (!didFit) {
            val points = locations.map { GeoPoint(it.latitude, it.longitude) } +
                sessionPins.map { GeoPoint(it.latitude!!, it.longitude!!) }
            if (points.isNotEmpty()) {
                val lats = points.map { it.latitude }
                val lons = points.map { it.longitude }
                val box = BoundingBox(lats.max(), lons.max(), lats.min(), lons.min())
                map.post { runCatching { map.zoomToBoundingBox(box.increaseByScale(1.4f), false, 48) } }
                didFit = true
                // Clustering-Schwelle gleich mitziehen; der Listener meldet sich erst nach dem Zoom.
                span = max(max((lats.max() - lats.min()) * 1.4, (lons.max() - lons.min()) * 1.4), 0.02)
            }
        }
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.tab_map)) }, actions = { FilterIconButton(onClick = { showFilter = true }) })
    }) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                errorMessage != null -> ErrorState(errorMessage!!, onRetry = { scope.launch { load() } })
                locations.isEmpty() && sessionPins.isEmpty() && !isLoading ->
                    EmptyState(stringResource(R.string.map_empty_state))
                else -> {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            MapView(ctx).apply {
                                setTileSource(TileSourceFactory.MAPNIK)
                                setMultiTouchControls(true)
                                controller.setZoom(6.0)
                                addMapListener(DelayedMapListener(object : MapListener {
                                    override fun onScroll(event: ScrollEvent?): Boolean {
                                        span = visibleSpan(this@apply); return false
                                    }
                                    override fun onZoom(event: ZoomEvent?): Boolean {
                                        span = visibleSpan(this@apply); return false
                                    }
                                }, 250))
                                mapView = this
                            }
                        },
                        onRelease = { it.onDetach() }
                    )
                    // Legend / toggles
                    Row(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 16.dp)
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        LegendToggle(stringResource(R.string.map_legend_locations), Icons.Filled.EvStation, Blue, showLocations) { showLocations = !showLocations }
                        LegendToggle(stringResource(R.string.dashboard_stat_sessions), Icons.Filled.Bolt, Color.Gray, showSessions) { showSessions = !showSessions }
                    }
                    // Standort-Knopf unten rechts, oberhalb der Legende, damit sich beide nie ueberlappen.
                    SmallFloatingActionButton(
                        onClick = { if (!isLocating) permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 64.dp)
                    ) {
                        if (isLocating) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Filled.MyLocation, contentDescription = stringResource(R.string.map_locate_me))
                    }
                }
            }
        }
    }

    locationToEdit?.let { loc ->
        AddEditLocationModal(location = loc, providers = providers, onDismiss = { locationToEdit = null }) {
            locationToEdit = null; scope.launch { load() }
        }
    }

    clusterList?.let { list ->
        FullScreenModal(onDismiss = { clusterList = null }) {
            ClusterSessionList(
                sessions = list, vehicles = vehicles, providers = providers,
                onDismiss = { clusterList = null },
                onSelect = { clusterList = null; sessionToPreview = it }
            )
        }
    }

    sessionToPreview?.let { s ->
        FullScreenModal(onDismiss = { sessionToPreview = null }) {
            SessionDetailScreen(
                session = s, vehicles = vehicles, providers = providers, locations = locations,
                onDismiss = { sessionToPreview = null },
                onChanged = { sessionToPreview = null; scope.launch { load() } }
            )
        }
    }

    locationError?.let { message ->
        AlertDialog(
            onDismissRequest = { locationError = null },
            title = { Text(stringResource(R.string.map_location_error_title)) },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { locationError = null }) { Text(stringResource(R.string.action_ok)) } }
        )
    }

    if (showFilter) FilterSheet(onDismiss = { showFilter = false })
}

/**
 * Ladevorgaenge eines Clusters, das sich nicht weiter auftrennen laesst - ein Tipp auf eine Zeile
 * oeffnet die normale Detail-Vorschau. Gegenstueck zu `ClusterSessionListSheet` in iOS.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClusterSessionList(
    sessions: List<ChargingSession>,
    vehicles: List<Vehicle>,
    providers: List<Provider>,
    onDismiss: () -> Unit,
    onSelect: (ChargingSession) -> Unit
) {
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(pluralStringResource(R.plurals.map_cluster_sessions, sessions.size, sessions.size)) },
            actions = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) } }
        )
    }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            items(sessions.sortedByDescending { it.startTime }, key = { it.id }) { session ->
                Row(
                    Modifier.fillMaxWidth().clickable { onSelect(session) }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(Fmt.dateTimeMedium(session.startTime), style = MaterialTheme.typography.bodyMedium)
                        val subtitle = listOfNotNull(
                            vehicles.firstOrNull { it.id == session.vehicleId }?.name,
                            providers.firstOrNull { it.id == session.providerId }?.name
                        ).joinToString(" · ")
                        if (subtitle.isNotEmpty()) {
                            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    session.energyKwh?.let {
                        Text(Fmt.n("%.1f kWh", it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun LegendToggle(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    isOn: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier.clickable(onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = if (isOn) color else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Box(Modifier.size(0.dp).clip(CircleShape))
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            color = if (isOn) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            textDecoration = if (isOn) TextDecoration.None else TextDecoration.LineThrough
        )
    }
}
