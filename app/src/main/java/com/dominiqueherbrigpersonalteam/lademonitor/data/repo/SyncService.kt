package com.dominiqueherbrigpersonalteam.lademonitor.data.repo

import android.content.Context
import android.content.SharedPreferences
import com.dominiqueherbrigpersonalteam.lademonitor.LademonitorApp
import com.dominiqueherbrigpersonalteam.lademonitor.R
import com.dominiqueherbrigpersonalteam.lademonitor.data.local.LocalChargingLocation
import com.dominiqueherbrigpersonalteam.lademonitor.data.local.LocalChargingSession
import com.dominiqueherbrigpersonalteam.lademonitor.data.local.LocalProvider
import com.dominiqueherbrigpersonalteam.lademonitor.data.local.LocalProviderFee
import com.dominiqueherbrigpersonalteam.lademonitor.data.local.LocalStore
import com.dominiqueherbrigpersonalteam.lademonitor.data.local.LocalVehicle
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ChargingSession
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ChargingSessionPayload
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.LocationPayload
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ProviderFeePayload
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ProviderPayload
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.VehiclePayload
import com.dominiqueherbrigpersonalteam.lademonitor.data.net.NetworkMonitor
import com.dominiqueherbrigpersonalteam.lademonitor.data.remote.ApiClient
import com.dominiqueherbrigpersonalteam.lademonitor.data.remote.ApiException
import com.dominiqueherbrigpersonalteam.lademonitor.data.session.SessionManager
import com.dominiqueherbrigpersonalteam.lademonitor.data.settings.AppMode
import com.dominiqueherbrigpersonalteam.lademonitor.data.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Bidirectional sync between the Room buffer and the server — the port of the iOS `SyncService`.
 * Only active in server mode.
 *
 * Each pass PUSHes local changes/deletes first (in FK order Vehicles -> Providers -> Locations ->
 * Sessions), then PULLs the server state and merges. Conflict strategy: local-dirty wins. Because
 * every freshly created local row starts isDirty=true / serverId=null, the "upload everything on
 * switch to server" case is just the first normal sync pass — not a special case.
 */
object SyncService {

    private const val PREFS = "lademonitor_sync"
    private const val KEY_LAST_USER = "lastSyncedUserId"

    /** Cursor fuer GET /api/sync/deletions, siehe applyServerDeletions(). */
    private const val KEY_DELETION_CURSOR = "lastDeletionCursor"
    private const val MIN_AUTO_SYNC_INTERVAL_MS = 10_000L

    private lateinit var prefs: SharedPreferences
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing

    private val _lastSyncDate = MutableStateFlow<Long?>(null)
    val lastSyncDate: StateFlow<Long?> = _lastSyncDate

    private val _lastSyncError = MutableStateFlow<String?>(null)
    val lastSyncError: StateFlow<String?> = _lastSyncError

    private val itemErrors = mutableListOf<String>()

    /**
     * Drives the dialog "What should happen to the data on this device?". It lives here and not in
     * the AuthScreen, because that one is already gone at that moment: `completeAuthentication`
     * flips `isAuthenticated` and [com.dominiqueherbrigpersonalteam.lademonitor.ui.LademonitorRoot]
     * switches to the main app right away. So the dialog is shown by LademonitorRoot, which exists
     * in both states.
     */
    private val _pendingLocalDataDecision = MutableStateFlow(false)
    val pendingLocalDataDecision: StateFlow<Boolean> = _pendingLocalDataDecision

    private val _pendingLocalDataSummary = MutableStateFlow("")
    val pendingLocalDataSummary: StateFlow<String> = _pendingLocalDataSummary

    /**
     * What should happen to the data already on the device when somebody signs in to an account
     * this device has never synced with.
     */
    enum class LocalDataDecision {
        /** Upload into the account signed in to now (the previous behaviour). */
        UPLOAD,

        /** Delete from the device and load the state of the account instead. */
        DISCARD
    }

    private val gate = Mutex()
    private var inFlight: Deferred<Unit>? = null

    private val vehicles get() = LocalStore.vehicles
    private val providers get() = LocalStore.providers
    private val locations get() = LocalStore.locations
    private val sessions get() = LocalStore.sessions
    private val fees get() = LocalStore.fees

    /**
     * Ob der Server Grundgebuehren kennt (ab 0.27.0). Wird in jedem Durchlauf neu ermittelt
     * ([pushFees]), damit ein Server-Update ohne App-Neustart greift.
     */
    private var serverSupportsFees = false

    private class UnresolvedReferenceException(what: String) :
        Exception(LademonitorApp.appContext.getString(R.string.sync_error_unresolved_reference, what))

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // When connectivity returns after an offline phase, push buffered changes automatically.
        scope.launch {
            var previous = NetworkMonitor.isOnline.value
            NetworkMonitor.isOnline.collect { online ->
                if (online && !previous) syncNow()
                previous = online
            }
        }
    }

    /** For screens: syncs only if the last sync is older than the auto interval. */
    suspend fun syncIfNeeded() {
        if (AppSettings.appMode.value != AppMode.SERVER || !SessionManager.isAuthenticated.value) return
        val last = _lastSyncDate.value
        if (last != null && System.currentTimeMillis() - last < MIN_AUTO_SYNC_INTERVAL_MS) return
        syncNow()
    }

    /** Forces a sync; concurrent callers coalesce onto the same in-flight run. */
    suspend fun syncNow() {
        val run = gate.withLock {
            inFlight ?: scope.async { performSync() }.also { inFlight = it }
        }
        try {
            run.await()
        } finally {
            gate.withLock { if (inFlight === run) inFlight = null }
        }
    }

    // MARK: - Handling of local data on sign-in

    /**
     * Whether a decision by the user is needed before the first sync.
     *
     * Without this question the local data silently moved into the account just signed in to - so
     * when switching from account A to account B also A's charging locations including the GPS
     * coordinates of the home address. That is exactly what the per-user separation protects
     * against server-side, and the app should not undercut it by accident.
     *
     * It only asks when there really is something to decide: in server mode, with local data
     * present, and when this device has never synced with EXACTLY THIS account. So a normal
     * re-sign-in to the usual account asks nothing.
     */
    private suspend fun needsLocalDataDecision(): Boolean {
        if (AppSettings.appMode.value != AppMode.SERVER) return false
        val userId = SessionManager.currentUser.value?.id ?: return false
        if (prefs.getString(KEY_LAST_USER, null) == userId) return false
        return runCatching { LocalDataStore.hasAnyData() }.getOrDefault(false)
    }

    /**
     * To be called after a successful sign-in. Whether it syncs or asks first is decided by the
     * gate in [performSync] - so this is just the normal nudge.
     */
    fun startAfterLogin() {
        scope.launch { syncNow() }
    }

    /**
     * Applies the decision and syncs afterwards.
     *
     * Both branches note the account as "seen" - only that lets the gate in [performSync] through
     * for the following sync.
     */
    suspend fun applyLocalDataDecision(decision: LocalDataDecision) {
        _pendingLocalDataDecision.value = false
        when (decision) {
            // Exactly what used to happen automatically on the first sync: mark every row as
            // "freshly local, must be pushed". Sets lastSyncedUserId along the way.
            LocalDataDecision.UPLOAD -> resetSyncStateIfAccountChanged()
            LocalDataDecision.DISCARD -> {
                try {
                    LocalDataStore.resetAllData()
                    // Note the account as "already seen" BEFORE syncing: otherwise the next pass
                    // would run the account-switch detection on the freshly downloaded server data
                    // and mark it as locally changed.
                    SessionManager.currentUser.value?.id?.let {
                        prefs.edit().putString(KEY_LAST_USER, it).apply()
                    }
                } catch (e: Exception) {
                    _lastSyncError.value = e.localizedMessage ?: e.toString()
                    return
                }
            }
        }
        syncNow()
    }

    /**
     * Cancel in the dialog: sign out again. Otherwise one would stand there signed in without the
     * question being answered - and the next arbitrary sync (tab switch, network back) would
     * silently answer it with "upload". The local data stays untouched.
     */
    suspend fun cancelLocalDataDecision() {
        _pendingLocalDataDecision.value = false
        SessionManager.logout()
    }

    private suspend fun performSync() {
        if (AppSettings.appMode.value != AppMode.SERVER || !SessionManager.isAuthenticated.value) return
        if (!NetworkMonitor.isOnline.value) {
            _lastSyncError.value = LademonitorApp.appContext.getString(R.string.sync_error_offline)
            return
        }
        // Gate against the silent path: a sync that does NOT come through startAfterLogin() either
        // (app restart after a cancelled sign-in, network returning, tab switch) must not answer
        // the question silently with "upload". It is asked here instead. applyLocalDataDecision()
        // notes the account as seen in both branches, so the sync following it gets through here.
        if (needsLocalDataDecision()) {
            _pendingLocalDataSummary.value =
                runCatching { LocalDataStore.localDataSummary() }.getOrDefault("")
            _pendingLocalDataDecision.value = true
            return
        }
        _isSyncing.value = true
        itemErrors.clear()
        try {
            resetSyncStateIfAccountChanged()
            pushVehicles()
            pushProviders()
            pushFees()
            pushLocations()
            pushSessions()
            pullVehicles()
            pullProviders()
            pullFees()
            pullLocations()
            pullSessions()
            // Zum Schluss, nach den Pulls: die legen fehlende Zeilen an, und eine gerade erst
            // angelegte Zeile darf ein Grabstein aus demselben Durchlauf gleich wieder entfernen.
            applyServerDeletions()
            _lastSyncDate.value = System.currentTimeMillis()
            _lastSyncError.value = if (itemErrors.isEmpty()) null else itemErrors.joinToString(" · ")
        } catch (e: Exception) {
            val summary = if (itemErrors.isEmpty()) "" else itemErrors.joinToString(" · ") + " · "
            _lastSyncError.value = summary + (e.localizedMessage ?: e.toString())
        } finally {
            _isSyncing.value = false
        }
    }

    // MARK: - Account change

    private suspend fun resetSyncStateIfAccountChanged() {
        val currentUserId = SessionManager.currentUser.value?.id ?: return
        val last = prefs.getString(KEY_LAST_USER, null)
        if (last != null && last != currentUserId) {
            // Gebuehren kennen ihren Anbieter nach dem ersten Sync nur noch ueber dessen serverId.
            // Die verfaellt gleich - ohne Umschreiben auf die localId liesse sich die Gebuehr fuer
            // das neue Konto nie mehr zuordnen und bliebe fuer immer ungepusht.
            val localRef = providers.getAll().mapNotNull { p -> p.serverId?.let { it to p.localId } }.toMap()
            for (f in fees.getAll()) {
                localRef[f.providerId]?.let { f.providerId = it; fees.upsert(f) }
            }
            for (v in vehicles.getAll()) resetRow(v.pendingDelete, { vehicles.delete(v) }, {
                v.serverId = null; v.isDirty = true; vehicles.upsert(v)
            })
            for (p in providers.getAll()) resetRow(p.pendingDelete, { providers.delete(p) }, {
                p.serverId = null; p.isDirty = true; providers.upsert(p)
            })
            for (l in locations.getAll()) resetRow(l.pendingDelete, { locations.delete(l) }, {
                l.serverId = null; l.isDirty = true; locations.upsert(l)
            })
            for (s in sessions.getAll()) resetRow(s.pendingDelete, { sessions.delete(s) }, {
                s.serverId = null; s.isDirty = true; sessions.upsert(s)
            })
            for (f in fees.getAll()) resetRow(f.pendingDelete, { fees.delete(f) }, {
                f.serverId = null; f.isDirty = true; fees.upsert(f)
            })
            // Grabsteine gelten pro Konto — ein Cursor aus Konto A sagt ueber Konto B nichts
            // aus. Zurueckgesetzt holt der naechste Abruf dessen vollstaendige Liste.
            prefs.edit().remove(KEY_DELETION_CURSOR).apply()
        }
        prefs.edit().putString(KEY_LAST_USER, currentUserId).apply()
    }

    private suspend fun resetRow(pendingDelete: Boolean, delete: suspend () -> Unit, reset: suspend () -> Unit) {
        if (pendingDelete) delete() else reset()
    }

    // MARK: - Push

    private suspend fun pushVehicles() {
        for (vehicle in vehicles.getDirty()) {
            try {
                if (vehicle.pendingDelete) {
                    vehicle.serverId?.let { runCatching { ApiClient.deleteVehicle(it) } }
                    val ref = vehicle.serverId ?: vehicle.localId
                    sessions.getByVehicleId(ref).forEach { sessions.delete(it) }
                    vehicles.delete(vehicle)
                    continue
                }
                val payload = VehiclePayload(
                    externalId = if (vehicle.serverId == null) vehicle.externalId else null,
                    name = vehicle.name, brand = vehicle.brand, model = vehicle.model,
                    batteryCapacityKwh = vehicle.batteryCapacityKwh, isActive = vehicle.isActive
                )
                if (vehicle.serverId != null) {
                    ApiClient.updateVehicle(vehicle.serverId!!, payload)
                } else {
                    vehicle.serverId = ApiClient.createVehicle(payload).id
                }
                vehicle.isDirty = false
                vehicles.upsert(vehicle)
            } catch (e: Exception) {
                itemErrors.add(
                    LademonitorApp.appContext.getString(
                        R.string.sync_error_item_vehicle, vehicle.name, e.localizedMessage.orEmpty()
                    )
                )
            }
        }
    }

    private suspend fun pushProviders() {
        for (provider in providers.getDirty()) {
            try {
                if (provider.pendingDelete) {
                    provider.serverId?.let { runCatching { ApiClient.deleteProvider(it) } }
                    providers.delete(provider)
                    continue
                }
                val payload = ProviderPayload(
                    name = provider.name, lastPriceAcPerKwh = provider.lastPriceAcPerKwh,
                    lastPriceDcPerKwh = provider.lastPriceDcPerKwh, notes = provider.notes
                )
                if (provider.serverId != null) {
                    ApiClient.updateProvider(provider.serverId!!, payload)
                } else {
                    provider.serverId = ApiClient.createProvider(payload).id
                }
                provider.isDirty = false
                providers.upsert(provider)
            } catch (e: Exception) {
                itemErrors.add(
                    LademonitorApp.appContext.getString(
                        R.string.sync_error_item_provider, provider.name, e.localizedMessage.orEmpty()
                    )
                )
            }
        }
    }

    /**
     * Grundgebuehren nach den Anbietern, damit eine neue Gebuehr die serverId ihres gerade erst
     * hochgeladenen Anbieters schon kennt. Gegen einen Server aelter als 0.27.0 (404 auf die
     * Liste) bleibt alles lokal und dirty - nach dem Server-Update geht es beim naechsten Sync
     * von selbst hoch, und der uebrige Sync gilt deswegen nicht als gescheitert.
     */
    private suspend fun pushFees() {
        serverSupportsFees = try {
            ApiClient.fetchProviderFees()
            true
        } catch (e: ApiException.Server) {
            if (e.statusCode == 404) false else throw e
        }
        if (!serverSupportsFees) return
        for (fee in fees.getDirty()) {
            try {
                if (fee.pendingDelete) {
                    fee.serverId?.let { runCatching { ApiClient.deleteProviderFee(it) } }
                    fees.delete(fee)
                    continue
                }
                val providerId = resolvedProviderServerId(fee.providerId)
                    ?: throw UnresolvedReferenceException(LademonitorApp.appContext.getString(R.string.entity_provider))
                val payload = ProviderFeePayload(
                    providerId = providerId, amount = fee.amount, interval = fee.interval,
                    startDate = fee.startDate, endDate = fee.endDate, label = fee.label, notes = fee.notes
                )
                if (fee.serverId != null) {
                    ApiClient.updateProviderFee(fee.serverId!!, payload)
                } else {
                    fee.serverId = ApiClient.createProviderFee(payload).id
                }
                fee.providerId = providerId
                fee.isDirty = false
                fees.upsert(fee)
            } catch (e: Exception) {
                itemErrors.add(
                    LademonitorApp.appContext.getString(
                        R.string.sync_error_item_fee, fee.label ?: String.format("%.2f €", fee.amount),
                        e.localizedMessage.orEmpty()
                    )
                )
            }
        }
    }

    private suspend fun pushLocations() {
        for (location in locations.getDirty()) {
            try {
                if (location.pendingDelete) {
                    location.serverId?.let { runCatching { ApiClient.deleteLocation(it) } }
                    locations.delete(location)
                    continue
                }
                val resolvedProviderId = resolvedProviderServerId(location.defaultProviderId)
                val payload = LocationPayload(
                    name = location.name, latitude = location.latitude, longitude = location.longitude,
                    radiusM = location.radiusM, defaultProviderId = resolvedProviderId
                )
                if (location.serverId != null) {
                    ApiClient.updateLocation(location.serverId!!, payload)
                } else {
                    location.serverId = ApiClient.createLocation(payload).id
                }
                location.defaultProviderId = resolvedProviderId
                location.isDirty = false
                locations.upsert(location)
            } catch (e: Exception) {
                itemErrors.add(
                    LademonitorApp.appContext.getString(
                        R.string.sync_error_item_location, location.name, e.localizedMessage.orEmpty()
                    )
                )
            }
        }
    }

    private suspend fun pushSessions() {
        for (session in sessions.getDirty()) {
            try {
                if (session.pendingDelete) {
                    session.serverId?.let { runCatching { ApiClient.deleteSession(it) } }
                    sessions.delete(session)
                    continue
                }
                val resolvedVehicleId = resolvedVehicleServerId(session.vehicleId)
                val resolvedProviderId = resolvedProviderServerId(session.providerId)
                val payload = ChargingSessionPayload(
                    vehicleId = if (session.serverId == null) resolvedVehicleId else null,
                    providerId = resolvedProviderId,
                    startTime = session.startTime,
                    chargingType = session.chargingType,
                    socStart = session.socStart,
                    socEnd = session.socEnd,
                    energyKwh = session.energyKwh,
                    pricePerKwh = session.pricePerKwh,
                    priceTotal = session.priceTotal,
                    odometerKm = session.odometerKm,
                    outsideTempC = session.outsideTempC,
                    latitude = session.latitude,
                    longitude = session.longitude,
                    geocodedPlace = session.geocodedPlace,
                    notes = session.notes,
                    needsReview = session.needsReview
                )
                if (session.serverId != null) {
                    ApiClient.updateSession(session.serverId!!, payload)
                } else {
                    session.serverId = ApiClient.createSession(payload).id
                }
                session.vehicleId = resolvedVehicleId
                session.providerId = resolvedProviderId
                session.isDirty = false
                sessions.upsert(session)
            } catch (e: Exception) {
                itemErrors.add(
                    LademonitorApp.appContext.getString(
                        R.string.sync_error_item_session, e.localizedMessage.orEmpty()
                    )
                )
            }
        }
    }

    private suspend fun resolvedVehicleServerId(ref: String): String {
        val vehicle = vehicles.find(ref)
        return vehicle?.serverId
            ?: throw UnresolvedReferenceException(LademonitorApp.appContext.getString(R.string.entity_vehicle))
    }

    private suspend fun resolvedProviderServerId(ref: String?): String? {
        if (ref == null) return null
        return providers.find(ref)?.serverId
    }

    // MARK: - Pull

    private suspend fun pullVehicles() {
        for (sv in ApiClient.fetchVehicles()) {
            val existing = vehicles.findByServerId(sv.id)
            if (existing != null) {
                if (!existing.isDirty) {
                    existing.externalId = sv.externalId
                    existing.name = sv.name
                    existing.brand = sv.brand
                    existing.model = sv.model
                    existing.batteryCapacityKwh = sv.batteryCapacityKwh
                    existing.isActive = sv.isActive
                    vehicles.upsert(existing)
                }
            } else {
                vehicles.upsert(
                    LocalVehicle(
                        serverId = sv.id, externalId = sv.externalId, name = sv.name, brand = sv.brand,
                        model = sv.model, batteryCapacityKwh = sv.batteryCapacityKwh, isActive = sv.isActive,
                        isDirty = false
                    )
                )
            }
        }
    }

    private suspend fun pullProviders() {
        for (sp in ApiClient.fetchProviders()) {
            val existing = providers.findByServerId(sp.id)
            if (existing != null) {
                if (!existing.isDirty) {
                    existing.name = sp.name
                    existing.lastPriceAcPerKwh = sp.lastPriceAcPerKwh
                    existing.lastPriceDcPerKwh = sp.lastPriceDcPerKwh
                    existing.notes = sp.notes
                    providers.upsert(existing)
                }
            } else {
                providers.upsert(
                    LocalProvider(
                        serverId = sp.id, name = sp.name, lastPriceAcPerKwh = sp.lastPriceAcPerKwh,
                        lastPriceDcPerKwh = sp.lastPriceDcPerKwh, notes = sp.notes, isDirty = false
                    )
                )
            }
        }
    }

    private suspend fun pullFees() {
        if (!serverSupportsFees) return
        for (sf in ApiClient.fetchProviderFees()) {
            val existing = fees.findByServerId(sf.id)
            if (existing != null) {
                if (!existing.isDirty) {
                    existing.providerId = sf.providerId
                    existing.amount = sf.amount
                    existing.interval = sf.interval
                    existing.startDate = sf.startDate
                    existing.endDate = sf.endDate
                    existing.label = sf.label
                    existing.notes = sf.notes
                    fees.upsert(existing)
                }
            } else {
                fees.upsert(
                    LocalProviderFee(
                        serverId = sf.id, providerId = sf.providerId, amount = sf.amount,
                        interval = sf.interval, startDate = sf.startDate, endDate = sf.endDate,
                        label = sf.label, notes = sf.notes, isDirty = false
                    )
                )
            }
        }
    }

    private suspend fun pullLocations() {
        for (sl in ApiClient.fetchLocations()) {
            val existing = locations.findByServerId(sl.id)
            if (existing != null) {
                if (!existing.isDirty) {
                    existing.name = sl.name
                    existing.latitude = sl.latitude
                    existing.longitude = sl.longitude
                    existing.radiusM = sl.radiusM
                    existing.defaultProviderId = sl.defaultProviderId
                    locations.upsert(existing)
                }
            } else {
                locations.upsert(
                    LocalChargingLocation(
                        serverId = sl.id, name = sl.name, latitude = sl.latitude, longitude = sl.longitude,
                        radiusM = sl.radiusM, defaultProviderId = sl.defaultProviderId, isDirty = false
                    )
                )
            }
        }
    }

    private suspend fun pullSessions() {
        for (ss in ApiClient.fetchSessions()) {
            val existing = sessions.findByServerId(ss.id)
            if (existing != null) {
                if (!existing.isDirty) {
                    apply(ss, existing)
                    sessions.upsert(existing)
                }
            } else {
                sessions.upsert(
                    LocalChargingSession(
                        serverId = ss.id, vehicleId = ss.vehicleId, providerId = ss.providerId,
                        locationId = ss.locationId, startTime = ss.startTime, endTime = ss.endTime,
                        chargingType = ss.chargingType, socStart = ss.socStart, socEnd = ss.socEnd,
                        energyKwh = ss.energyKwh, energyIsEstimated = ss.energyIsEstimated,
                        odometerKm = ss.odometerKm, outsideTempC = ss.outsideTempC,
                        outsideTempSource = ss.outsideTempSource,
                        priceTotal = ss.priceTotal, pricePerKwh = ss.pricePerKwh,
                        latitude = ss.latitude, longitude = ss.longitude, geocodedPlace = ss.geocodedPlace,
                        notes = ss.notes, source = ss.source, needsReview = ss.needsReview,
                        externalSessionId = ss.externalSessionId, isDirty = false
                    )
                )
            }
        }
    }

    private fun apply(dto: ChargingSession, session: LocalChargingSession) {
        session.vehicleId = dto.vehicleId
        session.providerId = dto.providerId
        session.locationId = dto.locationId
        session.startTime = dto.startTime
        session.endTime = dto.endTime
        session.chargingType = dto.chargingType
        session.socStart = dto.socStart
        session.socEnd = dto.socEnd
        session.energyKwh = dto.energyKwh
        session.energyIsEstimated = dto.energyIsEstimated
        session.odometerKm = dto.odometerKm
        session.outsideTempC = dto.outsideTempC
        session.outsideTempSource = dto.outsideTempSource
        session.priceTotal = dto.priceTotal
        session.pricePerKwh = dto.pricePerKwh
        session.latitude = dto.latitude
        session.longitude = dto.longitude
        session.geocodedPlace = dto.geocodedPlace
        session.notes = dto.notes
        session.source = dto.source
        session.needsReview = dto.needsReview
        session.externalSessionId = dto.externalSessionId
    }

    // NOTE: like the iOS version, rows that vanish from a pull are intentionally NOT auto-deleted
    // locally — a gap in a pull response (server error, empty response, failed id resolution) would
    // otherwise silently and irreversibly drop local sessions. Absence is not proof.
    //
    // Server-side deletions arrive through applyServerDeletions() instead: an explicit "this id is
    // gone" from the server, which an incomplete response cannot invent. The "ghost rows" this app
    // had to live with until then are therefore history.

    // MARK: - Server-side deletions

    /**
     * Loescht lokal, was auf dem Server geloescht wurde.
     *
     * Der Grabstein gewinnt — auch gegen eine lokal noch ungespeicherte Aenderung (`isDirty`).
     * Das ist Absicht: die Zeile existiert auf dem Server nicht mehr, ein Push darauf liefe ins
     * Leere (404), und sie stehenzulassen brachte genau die Geisterzeilen zurueck, wegen derer
     * es diesen Mechanismus gibt. Der Fall verlangt ohnehin zwei Geraete gleichzeitig: eines
     * loescht, das andere bearbeitet denselben Datensatz, bevor es synchronisiert.
     *
     * Der Cursor wird erst NACH dem erfolgreichen Anwenden gespeichert. Bricht der Durchlauf
     * vorher ab, kommen dieselben Grabsteine beim naechsten Mal erneut — eine bereits geloeschte
     * Zeile noch einmal zu loeschen ist folgenlos, eine verpasste Loeschung waere dauerhaft.
     */
    private suspend fun applyServerDeletions() {
        val response = try {
            ApiClient.fetchDeletions(prefs.getString(KEY_DELETION_CURSOR, null))
        } catch (e: ApiException.Server) {
            // Server aelter als 0.22.0 — den Endpunkt gibt es dort noch nicht. Dann bleibt es
            // beim bisherigen Verhalten (serverseitige Loeschungen bleiben als Geisterzeilen
            // stehen); der Rest des Syncs soll deswegen aber nicht als fehlgeschlagen gelten.
            if (e.statusCode == 404) return else throw e
        }
        for (record in response.deletions) {
            when (record.entityType) {
                "vehicle" -> vehicles.findByServerId(record.entityId)?.let { vehicles.delete(it) }
                "provider" -> providers.findByServerId(record.entityId)?.let { providers.delete(it) }
                "location" -> locations.findByServerId(record.entityId)?.let { locations.delete(it) }
                "session" -> sessions.findByServerId(record.entityId)?.let { sessions.delete(it) }
                "provider_fee" -> fees.findByServerId(record.entityId)?.let { fees.delete(it) }
                // Unbekannter Typ aus einem neueren Server: ueberspringen statt zu raten —
                // eine aeltere App soll an einem neueren Server nicht scheitern.
                else -> Unit
            }
        }
        prefs.edit().putString(KEY_DELETION_CURSOR, response.serverTime).apply()
    }
}
