package com.dominiqueherbrigpersonalteam.lademonitor.data.repo

import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ChargingLocation
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ChargingPlace
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ChargingSession
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ChargingSessionPayload
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.GeocodeResult
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.LocationPayload
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.Provider
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ProviderFee
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ProviderFeePayload
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ProviderPayload
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ReimbursementRate
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ReimbursementRatePayload
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.Reminder
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ReminderPayload
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.StatsSummary
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.Vehicle
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.VehicleCost
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.VehicleCostPayload
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.VehiclePayload
import com.dominiqueherbrigpersonalteam.lademonitor.data.remote.ApiClient
import com.dominiqueherbrigpersonalteam.lademonitor.data.settings.AppMode
import com.dominiqueherbrigpersonalteam.lademonitor.data.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The single entry point for the UI — port of the iOS `AppRepository`. Views always see the local
 * Room copy; in server mode a sync is attempted before every read (syncIfNeeded) and pushed after
 * every write (fire-and-forget). This is at once the offline buffer and the migration mechanism for
 * switching from local-only to server (all local rows are simply "dirty" on the first sync pass).
 */
object AppRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val isServerMode: Boolean get() = AppSettings.appMode.value == AppMode.SERVER

    private suspend fun syncBeforeRead() {
        if (isServerMode) SyncService.syncIfNeeded()
    }

    private fun syncAfterWrite() {
        if (!isServerMode) return
        scope.launch { SyncService.syncNow() }
    }

    // MARK: - Vehicles

    suspend fun fetchVehicles(): List<Vehicle> {
        syncBeforeRead()
        return LocalDataStore.fetchVehicles()
    }

    suspend fun createVehicle(payload: VehiclePayload): Vehicle =
        LocalDataStore.createVehicle(payload).also { syncAfterWrite() }

    suspend fun updateVehicle(id: String, payload: VehiclePayload): Vehicle =
        LocalDataStore.updateVehicle(id, payload).also { syncAfterWrite() }

    suspend fun deleteVehicle(id: String) {
        LocalDataStore.deleteVehicle(id)
        syncAfterWrite()
        // Seine Erinnerungen sind mit weg - ihre Mitteilungen auch.
        ReminderNotifier.rescheduleAsync()
    }

    // MARK: - Providers

    suspend fun fetchProviders(): List<Provider> {
        syncBeforeRead()
        return LocalDataStore.fetchProviders()
    }

    suspend fun createProvider(payload: ProviderPayload): Provider =
        LocalDataStore.createProvider(payload).also { syncAfterWrite() }

    suspend fun updateProvider(id: String, payload: ProviderPayload): Provider =
        LocalDataStore.updateProvider(id, payload).also { syncAfterWrite() }

    suspend fun deleteProvider(id: String) {
        LocalDataStore.deleteProvider(id)
        syncAfterWrite()
    }

    // MARK: - Locations

    suspend fun fetchLocations(): List<ChargingLocation> {
        syncBeforeRead()
        return LocalDataStore.fetchLocations()
    }

    suspend fun createLocation(payload: LocationPayload): ChargingLocation =
        LocalDataStore.createLocation(payload).also { syncAfterWrite() }

    suspend fun updateLocation(id: String, payload: LocationPayload): ChargingLocation =
        LocalDataStore.updateLocation(id, payload).also { syncAfterWrite() }

    suspend fun deleteLocation(id: String) {
        LocalDataStore.deleteLocation(id)
        syncAfterWrite()
    }

    // MARK: - Geocoding

    suspend fun forwardGeocode(query: String): List<GeocodeResult> =
        if (AppSettings.appMode.value == AppMode.LOCAL_ONLY) LocalGeocoder.search(query)
        else ApiClient.forwardGeocode(query)

    // MARK: - Sessions

    suspend fun fetchSessions(
        vehicleId: String? = null,
        needsReview: Boolean? = null,
        dateRange: LongRange? = null
    ): List<ChargingSession> {
        syncBeforeRead()
        return LocalDataStore.fetchSessions(vehicleId, needsReview, dateRange)
    }

    suspend fun createSession(payload: ChargingSessionPayload): ChargingSession =
        LocalDataStore.createSession(payload).also { syncAfterWrite(); WidgetSnapshotWriter.refreshAsync() }

    suspend fun updateSession(id: String, payload: ChargingSessionPayload): ChargingSession =
        LocalDataStore.updateSession(id, payload).also { syncAfterWrite(); WidgetSnapshotWriter.refreshAsync() }

    suspend fun deleteSession(id: String) {
        LocalDataStore.deleteSession(id)
        syncAfterWrite()
        WidgetSnapshotWriter.refreshAsync()
    }

    // MARK: - Stats (always computed locally, so the dashboard is never empty offline)

    suspend fun fetchStatsSummary(
        vehicleId: String? = null,
        dateRange: LongRange? = null
    ): StatsSummary {
        syncBeforeRead()
        val sessions = LocalDataStore.fetchSessions(vehicleId, null, dateRange)
        val vehicles = LocalDataStore.fetchVehicles()
        val providers = LocalDataStore.fetchProviders()
        // Perioden ohne Ladevorgang gehoeren zu keinem Fahrzeug (ein Abo gilt fuers Konto) und
        // zaehlen beim Zeitraum nach ihrem Beginn - wie stats.py.
        var unallocated: List<LocalFeeAllocator.Period> = emptyList()
        if (vehicleId == null) {
            unallocated = LocalDataStore.feeAllocation().unallocated
            if (dateRange != null) {
                val from = LocalFeeAllocator.day(dateRange.first)
                val to = LocalFeeAllocator.day(dateRange.last)
                unallocated = unallocated.filter { !it.start.isBefore(from) && !it.start.isAfter(to) }
            }
        }
        val stats = LocalStatsCalculator.compute(
            sessions, vehicles, providers, unallocated, LocalDataStore.canonicalProviderIds()
        )
        // Gesamtkosten (Laden + sonstige Kosten + Wertverlust/Leasing), wie build_vehicle_costs()
        // auf dem Server: gleicher Fahrzeug- und Zeitraumfilter, Laden = total_cost von oben.
        val basis = LocalDataStore.vehicleCostBasis()
        val canonicalVehicle = vehicleId?.let { LocalDataStore.canonicalVehicleIds()[it] ?: it }
        val vehicleCosts = LocalVehicleCostCalculator.summary(
            basis = basis,
            vehicleId = canonicalVehicle,
            startDate = dateRange?.let { LocalFeeAllocator.day(it.first) },
            endDate = dateRange?.let { LocalFeeAllocator.day(it.last) },
            chargingCost = stats.totalCost,
            kmDriven = stats.totalKmDriven
        )
        return stats.copy(vehicleCosts = vehicleCosts)
    }

    // MARK: - Reichweitenrechner

    /** Fahrzeuge und Ladevorgaenge mit EINER Fahrzeug-ID-Form (= `Vehicle.id`). */
    suspend fun rangeBasis(): RangeBasis {
        syncBeforeRead()
        val canonical = LocalDataStore.canonicalVehicleIds()
        val sessions = LocalDataStore.fetchSessions(null, null, null).map { s ->
            s.copy(vehicleId = canonical[s.vehicleId] ?: s.vehicleId)
        }
        return RangeBasis(vehicles = LocalDataStore.fetchVehicles(), sessions = sessions)
    }

    // MARK: - Tarifrechner

    /**
     * Alles, was der Tarifrechner fuer seine Vorschlagswerte braucht, mit EINER Anbieter-ID-Form
     * (= `Provider.id`): Ladevorgaenge koennen einen Anbieter noch ueber seine lokale ID kennen,
     * obwohl er inzwischen eine Server-ID hat.
     */
    suspend fun tariffBasis(): TariffBasis {
        syncBeforeRead()
        val canonical = LocalDataStore.canonicalProviderIds()
        val sessions = LocalDataStore.fetchSessions(null, null, null).map { s ->
            s.copy(providerId = s.providerId?.let { canonical[it] ?: it })
        }
        val providers = LocalDataStore.fetchProviders()
        return TariffBasis(
            sessions = sessions,
            providers = providers,
            fees = LocalDataStore.fetchFees(),
            nonPublicProviderIds = providers
                .filter { ChargingPlace.from(it.chargingPlace) != ChargingPlace.PUBLIC }
                .map { it.id }
                .toSet()
        )
    }

    /**
     * Einmalige Uebernahme der frueheren, nur lokal gespeicherten Abwahl "nicht oeffentlicher"
     * Anbieter des Tarifrechners: jeder dort abgewaehlte Anbieter wird "Zuhause". Laeuft ueber den
     * normalen Aenderungsweg, damit die Zeile dirty wird und mitsynchronisiert. IDs, die es nicht
     * mehr gibt, werden uebergangen; danach wird die alte Liste geloescht und die Uebernahme als
     * erledigt gemerkt, ein zweiter Lauf tut also nichts mehr.
     */
    fun migrateLegacyTariffExclusionsAsync() {
        scope.launch { runCatching { migrateLegacyTariffExclusions() } }
    }

    suspend fun migrateLegacyTariffExclusions() {
        val ids = TariffCalculatorSettings.legacyExcludedProviderIds ?: return
        var changed = false
        for (id in ids) {
            try {
                LocalDataStore.updateProvider(id, ProviderPayload(chargingPlace = ChargingPlace.HOME.raw))
                changed = true
            } catch (e: Exception) {
                // Anbieter inzwischen geloescht - nichts zu uebernehmen.
            }
        }
        TariffCalculatorSettings.markLegacyExclusionsMigrated()
        if (changed) syncAfterWrite()
    }

    // MARK: - Vergleichsfahrzeug

    /** Speichert das Vergleichsfahrzeug lokal (dirty) und stoesst im Server-Modus den Sync an. */
    fun saveComparisonVehicle(fuel: CombustionComparison.Fuel, consumption: Double, price: Double, gridCo2: Double) {
        ComparisonVehicleStore.save(fuel, consumption, price, gridCo2)
        syncAfterWrite()
    }

    // MARK: - Rueckblick

    /** Alles fuer [LocalReviewCalculator], mit EINER Fahrzeug-, Anbieter- und Ladeort-ID-Form. */
    suspend fun reviewBasis(): ReviewBasis {
        syncBeforeRead()
        return LocalDataStore.reviewBasis()
    }

    // MARK: - Grundgebuehren

    suspend fun fetchFees(providerId: String? = null): List<ProviderFee> {
        syncBeforeRead()
        return LocalDataStore.fetchFees(providerId)
    }

    suspend fun createFee(payload: ProviderFeePayload): ProviderFee =
        LocalDataStore.createFee(payload).also { syncAfterWrite() }

    suspend fun updateFee(id: String, payload: ProviderFeePayload): ProviderFee =
        LocalDataStore.updateFee(id, payload).also { syncAfterWrite() }

    suspend fun deleteFee(id: String) {
        LocalDataStore.deleteFee(id)
        syncAfterWrite()
    }

    // MARK: - Fahrzeugkosten

    suspend fun fetchVehicleCosts(vehicleId: String? = null): List<VehicleCost> {
        syncBeforeRead()
        return LocalDataStore.fetchVehicleCosts(vehicleId)
    }

    /** Grundlage fuer Jahresuebersicht und Wertverlust-Vorschlag, eine Fahrzeug-ID-Form. */
    suspend fun vehicleCostBasis(): VehicleCostBasis {
        syncBeforeRead()
        return LocalDataStore.vehicleCostBasis()
    }

    suspend fun createVehicleCost(payload: VehicleCostPayload): VehicleCost =
        LocalDataStore.createVehicleCost(payload).also { syncAfterWrite() }

    suspend fun updateVehicleCost(id: String, payload: VehicleCostPayload): VehicleCost =
        LocalDataStore.updateVehicleCost(id, payload).also { syncAfterWrite() }

    suspend fun deleteVehicleCost(id: String) {
        LocalDataStore.deleteVehicleCost(id)
        syncAfterWrite()
    }

    // MARK: - Erstattung Dienstwagen
    //
    // In beiden Modi gleich: der Bericht entsteht immer lokal (LocalReimbursementCalculator), auch
    // im Server-Modus - `/api/reimbursement/report` wird bewusst nicht benutzt.

    suspend fun reimbursementBasis(): ReimbursementBasis {
        syncBeforeRead()
        return LocalDataStore.reimbursementBasis()
    }

    suspend fun createReimbursementRate(payload: ReimbursementRatePayload): ReimbursementRate =
        LocalDataStore.createReimbursementRate(payload).also { syncAfterWrite() }

    suspend fun updateReimbursementRate(id: String, payload: ReimbursementRatePayload): ReimbursementRate =
        LocalDataStore.updateReimbursementRate(id, payload).also { syncAfterWrite() }

    suspend fun deleteReimbursementRate(id: String) {
        LocalDataStore.deleteReimbursementRate(id)
        syncAfterWrite()
    }

    // MARK: - Erinnerungen
    //
    // In beiden Modi gleich: Status und Prognose rechnet LocalReminderCalculator, "Erledigt" wird
    // lokal fortgeschrieben und als normale Aenderung synchronisiert. Nach jeder Aenderung werden
    // die lokalen Mitteilungen neu geplant.

    /** Ausgewertet, dringendste zuerst; ohne [vehicleId] alle Fahrzeuge. */
    suspend fun fetchEvaluatedReminders(vehicleId: String? = null): List<LocalReminderCalculator.Evaluated> {
        syncBeforeRead()
        return LocalDataStore.evaluatedReminders(vehicleId)
    }

    suspend fun vehicleMileage(vehicleId: String): LocalReminderCalculator.Mileage =
        LocalDataStore.vehicleMileage(vehicleId)

    suspend fun createReminder(payload: ReminderPayload): Reminder =
        LocalDataStore.createReminder(payload).also { syncAfterWrite(); ReminderNotifier.rescheduleAsync() }

    suspend fun updateReminder(id: String, payload: ReminderPayload): Reminder =
        LocalDataStore.updateReminder(id, payload).also { syncAfterWrite(); ReminderNotifier.rescheduleAsync() }

    suspend fun deleteReminder(id: String) {
        LocalDataStore.deleteReminder(id)
        syncAfterWrite()
        ReminderNotifier.rescheduleAsync()
    }

    /**
     * "Erledigt": Merker setzen und die naechste Faelligkeit aus der Wiederholung fortschreiben,
     * wie `routers/reminders.py::complete()` - aber lokal gerechnet und als PATCH aller Felder
     * gesendet, damit Local-Only- und Server-Modus dasselbe tun. Ohne Wiederholung bleiben Datum
     * und km leer, die Erinnerung steht dann als erledigt da.
     */
    suspend fun completeReminder(reminder: Reminder, doneDay: java.time.LocalDate, doneKm: Int?): Reminder {
        val (nextDay, nextKm) = LocalReminderCalculator.nextDue(reminder, doneDay, doneKm)
        val payload = reminder.toPayload().copy(
            lastDoneDate = LocalReminderCalculator.epochMillis(doneDay),
            lastDoneKm = doneKm,
            dueDate = nextDay?.let { LocalReminderCalculator.epochMillis(it) },
            dueKm = nextKm
        )
        return updateReminder(reminder.id, payload)
    }
}

data class TariffBasis(
    val sessions: List<ChargingSession>,
    val providers: List<Provider>,
    val fees: List<ProviderFee>,
    /**
     * Anbieter, die nicht "Unterwegs" sind (Zuhause/Arbeit) - sie zaehlen nicht zum
     * Vergleichspreis. Anbieter, die es nicht (mehr) gibt, gelten wie serverseitig als unterwegs.
     */
    val nonPublicProviderIds: Set<String>
)
