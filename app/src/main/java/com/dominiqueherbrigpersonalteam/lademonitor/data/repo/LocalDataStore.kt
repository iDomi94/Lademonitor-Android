package com.dominiqueherbrigpersonalteam.lademonitor.data.repo

import com.dominiqueherbrigpersonalteam.lademonitor.LademonitorApp
import com.dominiqueherbrigpersonalteam.lademonitor.R
import com.dominiqueherbrigpersonalteam.lademonitor.data.local.LocalChargingLocation
import com.dominiqueherbrigpersonalteam.lademonitor.data.local.LocalChargingSession
import com.dominiqueherbrigpersonalteam.lademonitor.data.local.LocalProvider
import com.dominiqueherbrigpersonalteam.lademonitor.data.local.LocalProviderFee
import com.dominiqueherbrigpersonalteam.lademonitor.data.local.LocalStore
import com.dominiqueherbrigpersonalteam.lademonitor.data.local.LocalVehicle
import com.dominiqueherbrigpersonalteam.lademonitor.data.local.LocalVehicleCost
import com.dominiqueherbrigpersonalteam.lademonitor.data.local.LocalReminder
import com.dominiqueherbrigpersonalteam.lademonitor.data.local.LocalReimbursementRate
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ChargingLocation
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ChargingSession
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ChargingType
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ChargingPlace
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.EnergyMeter
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.LocationPayload
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.Provider
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ProviderFee
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ProviderFeePayload
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ProviderPayload
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.TemperatureSource
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.Vehicle
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.VehicleCost
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.VehicleCostPayload
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ReimbursementRate
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ReimbursementRatePayload
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.Reminder
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ReminderPayload
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.VehiclePayload
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ChargingSessionPayload

class LocalStoreException(message: String) : Exception(message) {
    companion object {
        /** Standard "not found" error, localized to the device's current language. */
        fun notFound(): LocalStoreException =
            LocalStoreException(LademonitorApp.appContext.getString(R.string.local_store_error_not_found))
    }
}

/**
 * Room CRUD for both modes — the port of the SwiftData `LocalDataStore`. Works purely with the DTOs
 * from Models.kt so the UI (and the remote branch in [AppRepository]) sees no difference between
 * local and server-backed data. IDs are resolved as `serverId ?: localId`.
 */
object LocalDataStore {

    private val vehicles get() = LocalStore.vehicles
    private val providers get() = LocalStore.providers
    private val locations get() = LocalStore.locations
    private val sessions get() = LocalStore.sessions
    private val fees get() = LocalStore.fees
    private val vehicleCosts get() = LocalStore.vehicleCosts
    private val reminders get() = LocalStore.reminders
    private val reimbursementRates get() = LocalStore.reimbursementRates

    // MARK: - Reset

    /**
     * Whether there is any local data at all - including the rows only marked for deletion, since
     * those would have an effect on an account switch too (see the account-switch reset in
     * [SyncService]). Needed to ask about the handling of local data on sign-in only when there
     * really is something to decide.
     */
    suspend fun hasAnyData(): Boolean =
        sessions.countAll() > 0 || vehicles.countAll() > 0 ||
            providers.countAll() > 0 || locations.countAll() > 0 || fees.countAll() > 0 ||
            vehicleCosts.countAll() > 0 || reminders.countAll() > 0 ||
            reimbursementRates.countAll() > 0

    /** Short summary for the sign-in question ("3 vehicles, 128 charging sessions"). */
    suspend fun localDataSummary(): String {
        val context = LademonitorApp.appContext
        val parts = buildList {
            vehicles.countAll().takeIf { it > 0 }
                ?.let { add(context.getString(R.string.local_data_summary_vehicles, it)) }
            providers.countAll().takeIf { it > 0 }
                ?.let { add(context.getString(R.string.local_data_summary_providers, it)) }
            locations.countAll().takeIf { it > 0 }
                ?.let { add(context.getString(R.string.local_data_summary_locations, it)) }
            sessions.countAll().takeIf { it > 0 }
                ?.let { add(context.getString(R.string.local_data_summary_sessions, it)) }
            fees.countAll().takeIf { it > 0 }
                ?.let { add(context.getString(R.string.local_data_summary_fees, it)) }
            vehicleCosts.countAll().takeIf { it > 0 }
                ?.let { add(context.getString(R.string.local_data_summary_vehicle_costs, it)) }
            reminders.countAll().takeIf { it > 0 }
                ?.let { add(context.getString(R.string.local_data_summary_reminders, it)) }
            reimbursementRates.countAll().takeIf { it > 0 }
                ?.let { add(context.getString(R.string.local_data_summary_reimbursement_rates, it)) }
        }
        return parts.joinToString(", ")
    }

    /** Irreversibly deletes ALL local data. */
    suspend fun resetAllData() {
        reimbursementRates.clear()
        reminders.clear()
        vehicleCosts.clear()
        fees.clear()
        sessions.clear()
        locations.clear()
        providers.clear()
        vehicles.clear()
        ReminderNotifier.rescheduleAsync()
    }

    // MARK: - Vehicles

    suspend fun fetchVehicles(): List<Vehicle> = vehicles.getAllUndeleted().map { it.asDTO() }

    suspend fun createVehicle(payload: VehiclePayload): Vehicle {
        val vehicle = LocalVehicle(
            externalId = payload.externalId ?: "",
            name = payload.name ?: "",
            brand = payload.brand,
            model = payload.model,
            batteryCapacityKwh = payload.batteryCapacityKwh,
            isActive = payload.isActive ?: true
        )
        payload.ownership?.let { vehicle.applyOwnership(it) }
        payload.reimbursement?.let { vehicle.applyReimbursement(it) }
        vehicles.upsert(vehicle)
        return vehicle.asDTO()
    }

    suspend fun updateVehicle(id: String, payload: VehiclePayload): Vehicle {
        val vehicle = vehicles.find(id) ?: throw LocalStoreException.notFound()
        payload.name?.let { vehicle.name = it }
        payload.brand?.let { vehicle.brand = it }
        payload.model?.let { vehicle.model = it }
        payload.batteryCapacityKwh?.let { vehicle.batteryCapacityKwh = it }
        payload.isActive?.let { vehicle.isActive = it }
        // Kauf/Leasing immer als Ganzes: ein geleertes Feld soll auch geleert werden.
        payload.ownership?.let { vehicle.applyOwnership(it) }
        // Dienstwagen-Angaben ebenso als Ganzes (null = unveraendert, z.B. aus dem Fahrzeugformular).
        payload.reimbursement?.let { vehicle.applyReimbursement(it) }
        vehicle.updatedAt = System.currentTimeMillis()
        vehicle.isDirty = true
        vehicles.upsert(vehicle)
        return vehicle.asDTO()
    }

    /**
     * Cascades to the vehicle's sessions, matching the server. Die Fahrzeugkosten gehen sofort und
     * hart mit (wie die Grundgebuehren beim Anbieter): der Server loescht sie beim Loeschen des
     * Fahrzeugs selbst, samt Grabsteinen. Dasselbe gilt fuer die Erinnerungen und die
     * Erstattungssaetze.
     */
    suspend fun deleteVehicle(id: String) {
        val vehicle = vehicles.find(id) ?: throw LocalStoreException.notFound()
        val refs = setOfNotNull(vehicle.localId, vehicle.serverId)
        vehicleCosts.getAll().filter { it.vehicleId in refs }.forEach { vehicleCosts.delete(it) }
        reminders.getAll().filter { it.vehicleId in refs }.forEach { reminders.delete(it) }
        reimbursementRates.getAll().filter { it.vehicleId in refs }.forEach { reimbursementRates.delete(it) }
        val childSessions = sessions.getByVehicleId(id)
        if (vehicle.serverId != null) {
            vehicle.pendingDelete = true
            vehicle.isDirty = true
            vehicles.upsert(vehicle)
            childSessions.forEach {
                it.pendingDelete = true; it.isDirty = true; sessions.upsert(it)
            }
        } else {
            childSessions.forEach { sessions.delete(it) }
            vehicles.delete(vehicle)
        }
    }

    // MARK: - Providers

    suspend fun fetchProviders(): List<Provider> = providers.getAllUndeleted().map { it.asDTO() }

    suspend fun createProvider(payload: ProviderPayload): Provider {
        val provider = LocalProvider(
            name = payload.name ?: "",
            lastPriceAcPerKwh = payload.lastPriceAcPerKwh,
            lastPriceDcPerKwh = payload.lastPriceDcPerKwh,
            notes = payload.notes,
            energyMeter = EnergyMeter.from(payload.energyMeter)?.raw ?: EnergyMeter.CHARGER.raw,
            chargingPlace = ChargingPlace.from(payload.chargingPlace).raw
        )
        providers.upsert(provider)
        return provider.asDTO()
    }

    suspend fun updateProvider(id: String, payload: ProviderPayload): Provider {
        val provider = providers.find(id) ?: throw LocalStoreException.notFound()
        payload.name?.let { provider.name = it }
        payload.lastPriceAcPerKwh?.let { provider.lastPriceAcPerKwh = it }
        payload.lastPriceDcPerKwh?.let { provider.lastPriceDcPerKwh = it }
        payload.notes?.let { provider.notes = it }
        EnergyMeter.from(payload.energyMeter)?.let { provider.energyMeter = it.raw }
        ChargingPlace.fromOrNull(payload.chargingPlace)?.let { provider.chargingPlace = it.raw }
        provider.updatedAt = System.currentTimeMillis()
        provider.isDirty = true
        providers.upsert(provider)
        return provider.asDTO()
    }

    /**
     * Die Grundgebuehren des Anbieters gehen sofort und hart mit: der Server loescht sie beim
     * Loeschen des Anbieters selbst (mit Grabstein), ein eigener Push waere ueberfluessig.
     */
    suspend fun deleteProvider(id: String) {
        val provider = providers.find(id) ?: throw LocalStoreException.notFound()
        val refs = setOfNotNull(provider.localId, provider.serverId)
        fees.getAll().filter { it.providerId in refs }.forEach { fees.delete(it) }
        if (provider.serverId != null) {
            provider.pendingDelete = true
            provider.isDirty = true
            providers.upsert(provider)
        } else {
            providers.delete(provider)
        }
    }

    // MARK: - Locations

    suspend fun fetchLocations(): List<ChargingLocation> = locations.getAllUndeleted().map { it.asDTO() }

    suspend fun createLocation(payload: LocationPayload): ChargingLocation {
        val location = LocalChargingLocation(
            name = payload.name ?: "",
            latitude = payload.latitude ?: 0.0,
            longitude = payload.longitude ?: 0.0,
            radiusM = payload.radiusM ?: 100,
            defaultProviderId = payload.defaultProviderId
        )
        locations.upsert(location)
        return location.asDTO()
    }

    suspend fun updateLocation(id: String, payload: LocationPayload): ChargingLocation {
        val location = locations.find(id) ?: throw LocalStoreException.notFound()
        payload.name?.let { location.name = it }
        payload.latitude?.let { location.latitude = it }
        payload.longitude?.let { location.longitude = it }
        payload.radiusM?.let { location.radiusM = it }
        // defaultProviderId may be intentionally cleared, so assign directly.
        location.defaultProviderId = payload.defaultProviderId
        location.updatedAt = System.currentTimeMillis()
        location.isDirty = true
        locations.upsert(location)
        return location.asDTO()
    }

    suspend fun deleteLocation(id: String) {
        val location = locations.find(id) ?: throw LocalStoreException.notFound()
        if (location.serverId != null) {
            location.pendingDelete = true
            location.isDirty = true
            locations.upsert(location)
        } else {
            locations.delete(location)
        }
    }

    // MARK: - Grundgebuehren

    /**
     * Alle Anbieter-Referenzen (localId ODER serverId) auf die kanonische DTO-ID abbilden.
     * Gebuehren und Ladevorgaenge koennen ihren Anbieter noch ueber die localId kennen, obwohl er
     * inzwischen eine serverId hat - ohne diese Vereinheitlichung fiele die Umlage fuer genau diese
     * Vorgaenge still aus.
     */
    suspend fun canonicalProviderIds(): Map<String, String> {
        val map = HashMap<String, String>()
        for (provider in providers.getAll()) {
            val canonical = provider.serverId ?: provider.localId
            map[provider.localId] = canonical
            provider.serverId?.let { map[it] = canonical }
        }
        return map
    }

    suspend fun fetchFees(providerId: String? = null): List<ProviderFee> {
        val canonical = canonicalProviderIds()
        val list = fees.getAllUndeleted().map { row ->
            val dto = row.asDTO()
            dto.copy(providerId = canonical[dto.providerId] ?: dto.providerId)
        }
        if (providerId == null) return list
        val wanted = canonical[providerId] ?: providerId
        return list.filter { it.providerId == wanted }
    }

    /** Umlage ueber ALLE Ladevorgaenge, wie load_allocation() im Server; Filter greifen danach. */
    suspend fun feeAllocation(): LocalFeeAllocator.Allocation {
        val feeList = fetchFees()
        if (feeList.isEmpty()) return LocalFeeAllocator.Allocation()
        val canonical = canonicalProviderIds()
        val all = allUndeletedSessions().map { s ->
            s.copy(providerId = s.providerId?.let { canonical[it] ?: it })
        }
        return LocalFeeAllocator.allocate(feeList, all)
    }

    suspend fun createFee(payload: ProviderFeePayload): ProviderFee {
        val fee = LocalProviderFee(
            providerId = payload.providerId,
            amount = payload.amount,
            interval = payload.interval,
            startDate = payload.startDate,
            endDate = payload.endDate,
            label = payload.label,
            notes = payload.notes
        )
        fees.upsert(fee)
        return fee.asDTO()
    }

    /** Ersetzt alle Felder: ein leeres Enddatum hebt eine Kuendigung wieder auf. */
    suspend fun updateFee(id: String, payload: ProviderFeePayload): ProviderFee {
        val fee = fees.find(id) ?: throw LocalStoreException.notFound()
        fee.providerId = payload.providerId
        fee.amount = payload.amount
        fee.interval = payload.interval
        fee.startDate = payload.startDate
        fee.endDate = payload.endDate
        fee.label = payload.label
        fee.notes = payload.notes
        fee.updatedAt = System.currentTimeMillis()
        fee.isDirty = true
        fees.upsert(fee)
        return fee.asDTO()
    }

    suspend fun deleteFee(id: String) {
        val fee = fees.find(id) ?: throw LocalStoreException.notFound()
        if (fee.serverId != null) {
            fee.pendingDelete = true
            fee.isDirty = true
            fees.upsert(fee)
        } else {
            fees.delete(fee)
        }
    }

    // MARK: - Fahrzeugkosten

    /** Wie [canonicalProviderIds], nur fuer Fahrzeuge (Kosten und Ladevorgaenge -> `Vehicle.id`). */
    suspend fun canonicalVehicleIds(): Map<String, String> {
        val map = HashMap<String, String>()
        for (vehicle in vehicles.getAll()) {
            val canonical = vehicle.serverId ?: vehicle.localId
            map[vehicle.localId] = canonical
            vehicle.serverId?.let { map[it] = canonical }
        }
        return map
    }

    /** Neueste zuerst; `vehicleId` in kanonischer Form ([canonicalVehicleIds]). */
    suspend fun fetchVehicleCosts(vehicleId: String? = null): List<VehicleCost> {
        val canonical = canonicalVehicleIds()
        val list = vehicleCosts.getAllUndeleted().map { row ->
            val dto = row.asDTO()
            dto.copy(vehicleId = canonical[dto.vehicleId] ?: dto.vehicleId)
        }
        if (vehicleId == null) return list
        val wanted = canonical[vehicleId] ?: vehicleId
        return list.filter { it.vehicleId == wanted }
    }

    /**
     * Alles, was [LocalVehicleCostCalculator] braucht, mit EINER Fahrzeug-ID-Form. Die
     * Ladevorgaenge tragen schon den Grundgebuehr-Anteil ([fetchSessions]) - die Ladekosten je
     * Jahr enthalten ihn wie auf dem Server.
     */
    suspend fun vehicleCostBasis(): VehicleCostBasis {
        val canonical = canonicalVehicleIds()
        return VehicleCostBasis(
            vehicles = fetchVehicles(),
            costs = fetchVehicleCosts(),
            sessions = fetchSessions().map { s -> s.copy(vehicleId = canonical[s.vehicleId] ?: s.vehicleId) }
        )
    }

    /** Ein leerer Eintrag ohne Enddatum bei "einmalig" - wie der Server (_validate). */
    private fun normalizedEndDate(payload: VehicleCostPayload): Long? =
        if (payload.interval == "monthly" || payload.interval == "yearly") payload.endDate else null

    suspend fun createVehicleCost(payload: VehicleCostPayload): VehicleCost {
        val cost = LocalVehicleCost(
            vehicleId = payload.vehicleId,
            amount = payload.amount,
            category = payload.category,
            interval = payload.interval,
            startDate = payload.startDate,
            endDate = normalizedEndDate(payload),
            odometerKm = payload.odometerKm,
            label = payload.label,
            notes = payload.notes
        )
        vehicleCosts.upsert(cost)
        return cost.asDTO()
    }

    /** Ersetzt alle Felder, wie bei den Grundgebuehren. */
    suspend fun updateVehicleCost(id: String, payload: VehicleCostPayload): VehicleCost {
        val cost = vehicleCosts.find(id) ?: throw LocalStoreException.notFound()
        cost.vehicleId = payload.vehicleId
        cost.amount = payload.amount
        cost.category = payload.category
        cost.interval = payload.interval
        cost.startDate = payload.startDate
        cost.endDate = normalizedEndDate(payload)
        cost.odometerKm = payload.odometerKm
        cost.label = payload.label
        cost.notes = payload.notes
        cost.updatedAt = System.currentTimeMillis()
        cost.isDirty = true
        vehicleCosts.upsert(cost)
        return cost.asDTO()
    }

    suspend fun deleteVehicleCost(id: String) {
        val cost = vehicleCosts.find(id) ?: throw LocalStoreException.notFound()
        if (cost.serverId != null) {
            cost.pendingDelete = true
            cost.isDirty = true
            vehicleCosts.upsert(cost)
        } else {
            vehicleCosts.delete(cost)
        }
    }

    // MARK: - Erstattung Dienstwagen

    /** Neueste zuerst; `vehicleId` in kanonischer Form ([canonicalVehicleIds]). */
    suspend fun fetchReimbursementRates(vehicleId: String? = null): List<ReimbursementRate> {
        val canonical = canonicalVehicleIds()
        val list = reimbursementRates.getAllUndeleted().map { row ->
            val dto = row.asDTO()
            dto.copy(vehicleId = canonical[dto.vehicleId] ?: dto.vehicleId)
        }
        if (vehicleId == null) return list
        val wanted = canonical[vehicleId] ?: vehicleId
        return list.filter { it.vehicleId == wanted }
    }

    /**
     * Alles, was [LocalReimbursementCalculator] braucht, mit EINER Fahrzeug- und Anbieter-ID-Form.
     * Die Ladevorgaenge tragen schon den Grundgebuehr-Anteil ([fetchSessions]), umgelegt ueber
     * ALLE Vorgaenge wie auf dem Server.
     */
    suspend fun reimbursementBasis(): ReimbursementBasis {
        val canonicalVehicles = canonicalVehicleIds()
        val canonicalProviders = canonicalProviderIds()
        return ReimbursementBasis(
            vehicles = fetchVehicles(),
            providers = fetchProviders(),
            rates = fetchReimbursementRates(),
            sessions = fetchSessions().map { s ->
                s.copy(
                    vehicleId = canonicalVehicles[s.vehicleId] ?: s.vehicleId,
                    providerId = s.providerId?.let { canonicalProviders[it] ?: it }
                )
            }
        )
    }

    /**
     * Alles, was [LocalReviewCalculator] braucht: Ladevorgaenge mit kanonischer Fahrzeug-,
     * Anbieter- und Ladeort-ID und Grundgebuehr-Anteil (umgelegt ueber ALLE Vorgaenge), dazu die
     * nicht umgelegten Gebuehrenperioden (ungefiltert - der Rechner filtert nach Zeitraum).
     */
    suspend fun reviewBasis(): ReviewBasis {
        val canonicalVehicles = canonicalVehicleIds()
        val canonicalProviders = canonicalProviderIds()
        val canonicalLocations = HashMap<String, String>()
        for (location in locations.getAll()) {
            val canonical = location.serverId ?: location.localId
            canonicalLocations[location.localId] = canonical
            location.serverId?.let { canonicalLocations[it] = canonical }
        }
        return ReviewBasis(
            vehicles = fetchVehicles(),
            providers = fetchProviders(),
            locations = fetchLocations(),
            sessions = fetchSessions().map { s ->
                s.copy(
                    vehicleId = canonicalVehicles[s.vehicleId] ?: s.vehicleId,
                    providerId = s.providerId?.let { canonicalProviders[it] ?: it },
                    locationId = s.locationId?.let { canonicalLocations[it] ?: it }
                )
            },
            unallocatedFees = feeAllocation().unallocated
        )
    }

    suspend fun createReimbursementRate(payload: ReimbursementRatePayload): ReimbursementRate {
        val rate = LocalReimbursementRate(
            vehicleId = payload.vehicleId,
            validFrom = payload.validFrom,
            ratePerKwh = payload.ratePerKwh
        )
        reimbursementRates.upsert(rate)
        return rate.asDTO()
    }

    suspend fun updateReimbursementRate(id: String, payload: ReimbursementRatePayload): ReimbursementRate {
        val rate = reimbursementRates.find(id) ?: throw LocalStoreException.notFound()
        rate.vehicleId = payload.vehicleId
        rate.validFrom = payload.validFrom
        rate.ratePerKwh = payload.ratePerKwh
        rate.updatedAt = System.currentTimeMillis()
        rate.isDirty = true
        reimbursementRates.upsert(rate)
        return rate.asDTO()
    }

    suspend fun deleteReimbursementRate(id: String) {
        val rate = reimbursementRates.find(id) ?: throw LocalStoreException.notFound()
        if (rate.serverId != null) {
            rate.pendingDelete = true
            rate.isDirty = true
            reimbursementRates.upsert(rate)
        } else {
            reimbursementRates.delete(rate)
        }
    }

    // MARK: - Erinnerungen

    /** Alle Erinnerungen (ohne Reihenfolge); `vehicleId` in kanonischer Form ([canonicalVehicleIds]). */
    suspend fun fetchReminders(vehicleId: String? = null): List<Reminder> {
        val canonical = canonicalVehicleIds()
        val list = reminders.getAllUndeleted().map { row ->
            val dto = row.asDTO()
            dto.copy(vehicleId = canonical[dto.vehicleId] ?: dto.vehicleId)
        }
        if (vehicleId == null) return list
        val wanted = canonical[vehicleId] ?: vehicleId
        return list.filter { it.vehicleId == wanted }
    }

    /**
     * Alle Erinnerungen ausgewertet (Status, Prognose), dringendste zuerst - mit EINER
     * Fahrzeug-ID-Form fuer Erinnerungen und Ladevorgaenge. Ohne Zeitraumfilter.
     */
    suspend fun evaluatedReminders(vehicleId: String? = null): List<LocalReminderCalculator.Evaluated> {
        val canonical = canonicalVehicleIds()
        val list = fetchReminders(vehicleId)
        if (list.isEmpty()) return emptyList()
        val wanted = list.map { it.vehicleId }.toSet()
        val sessionsOfVehicles = sessions.getAllUndeleted()
            .map { it.asDTO() }
            .map { s -> s.copy(vehicleId = canonical[s.vehicleId] ?: s.vehicleId) }
            .filter { it.vehicleId in wanted }
        val names = fetchVehicles().associate { it.id to it.name }
        return LocalReminderCalculator.evaluateAll(list, sessionsOfVehicles, names)
    }

    /** km-Stand und Fahrleistung eines Fahrzeugs (Vorschlag fuer "Erledigt" und neue km-Faelligkeiten). */
    suspend fun vehicleMileage(vehicleId: String): LocalReminderCalculator.Mileage {
        val canonical = canonicalVehicleIds()
        val wanted = canonical[vehicleId] ?: vehicleId
        val list = sessions.getAllUndeleted().map { it.asDTO() }
            .filter { (canonical[it.vehicleId] ?: it.vehicleId) == wanted }
        return LocalReminderCalculator.mileage(list)
    }

    private fun LocalReminder.applyPayload(payload: ReminderPayload) {
        vehicleId = payload.vehicleId
        kind = payload.kind
        title = payload.title
        dueDate = payload.dueDate
        dueKm = payload.dueKm
        repeatMonths = payload.repeatMonths
        repeatKm = payload.repeatKm
        leadDays = payload.leadDays
        leadKm = payload.leadKm
        notes = payload.notes
        lastDoneDate = payload.lastDoneDate
        lastDoneKm = payload.lastDoneKm
    }

    suspend fun createReminder(payload: ReminderPayload): Reminder {
        val reminder = LocalReminder()
        reminder.applyPayload(payload)
        reminders.upsert(reminder)
        return reminder.asDTO()
    }

    /** Ersetzt alle Felder, wie bei den Fahrzeugkosten. */
    suspend fun updateReminder(id: String, payload: ReminderPayload): Reminder {
        val reminder = reminders.find(id) ?: throw LocalStoreException.notFound()
        reminder.applyPayload(payload)
        reminder.updatedAt = System.currentTimeMillis()
        reminder.isDirty = true
        reminders.upsert(reminder)
        return reminder.asDTO()
    }

    suspend fun deleteReminder(id: String) {
        val reminder = reminders.find(id) ?: throw LocalStoreException.notFound()
        if (reminder.serverId != null) {
            reminder.pendingDelete = true
            reminder.isDirty = true
            reminders.upsert(reminder)
        } else {
            reminders.delete(reminder)
        }
    }

    // MARK: - Sessions

    suspend fun fetchSessions(
        vehicleId: String? = null,
        needsReview: Boolean? = null,
        dateRange: LongRange? = null
    ): List<ChargingSession> {
        // Consumption must be computed over the full per-vehicle history (predecessor comparison),
        // so decorate first, then filter/sort — otherwise a date/needs_review filter would drop the
        // chronological predecessor from the calculation.
        val all = allUndeletedSessions()
        var list = decoratedWithFees(decoratedWithConsumption(all)).sortedByDescending { it.startTime }
        if (vehicleId != null) list = list.filter { it.vehicleId == vehicleId }
        if (needsReview != null) list = list.filter { it.needsReview == needsReview }
        if (dateRange != null) list = list.filter { it.startTime in dateRange }
        return list
    }

    private suspend fun allUndeletedSessions(): List<ChargingSession> =
        sessions.getAllUndeleted().map { it.asDTO() }

    suspend fun createSession(payload: ChargingSessionPayload): ChargingSession {
        val vehicleId = payload.vehicleId ?: throw LocalStoreException.notFound()
        val session = LocalChargingSession(
            vehicleId = vehicleId,
            providerId = payload.providerId,
            locationId = payload.locationId,
            startTime = payload.startTime ?: System.currentTimeMillis(),
            chargingType = payload.chargingType,
            socStart = payload.socStart,
            socEnd = payload.socEnd,
            energyKwh = payload.energyKwh,
            odometerKm = payload.odometerKm,
            outsideTempC = payload.outsideTempC,
            // Im Local-Only-Modus gibt es nur eine Quelle: den Menschen, der sie eintippt.
            outsideTempSource = payload.outsideTempC?.let { TemperatureSource.MANUAL.raw },
            priceTotal = payload.priceTotal,
            pricePerKwh = payload.pricePerKwh,
            latitude = payload.latitude,
            longitude = payload.longitude,
            geocodedPlace = payload.geocodedPlace,
            notes = payload.notes,
            source = "manual",
            needsReview = payload.needsReview ?: false,
            energyMeter = EnergyMeter.normalizedOverride(payload.energyMeter, providerEnergyMeter(payload.providerId)),
            avgSpeedKmh = payload.avgSpeedKmh
        )
        sessions.upsert(session)
        updateProviderPriceMemory(payload.providerId, ChargingType.from(payload.chargingType), payload.pricePerKwh)
        return decorateOne(session.asDTO())
    }

    /** Standard-Messort des Anbieters (lokale oder Server-ID), null ohne Anbieter. */
    private suspend fun providerEnergyMeter(providerRef: String?): String? =
        providerRef?.let { providers.find(it)?.energyMeter }

    suspend fun updateSession(id: String, payload: ChargingSessionPayload): ChargingSession {
        val session = sessions.find(id) ?: throw LocalStoreException.notFound()
        val previousTotal = session.priceTotal
        payload.providerId?.let { session.providerId = it }
        payload.locationId?.let { session.locationId = it }
        payload.startTime?.let { session.startTime = it }
        payload.chargingType?.let { session.chargingType = it }
        payload.socStart?.let { session.socStart = it }
        payload.socEnd?.let { session.socEnd = it }
        payload.energyKwh?.let {
            // Korrigierte Energiemenge (z.B. aus der App des Ladeanbieters abgelesen)
            // ist nicht mehr geschaetzt - identisch zur Regel in update_session() im
            // Backend: nur eine tatsaechliche Wertaenderung zaehlt als Korrektur,
            // reines Oeffnen + Speichern laesst das Flag stehen.
            val previous = session.energyKwh
            if (session.energyIsEstimated && (previous == null || kotlin.math.abs(it - previous) > 1e-6)) {
                session.energyIsEstimated = false
            }
            session.energyKwh = it
        }
        payload.pricePerKwh?.let { session.pricePerKwh = it }
        payload.priceTotal?.let { session.priceTotal = it }
        payload.odometerKm?.let { session.odometerKm = it }
        payload.outsideTempC?.let {
            // Wie beim energyIsEstimated-Flag und wie in update_session() im Backend: das
            // Formular schickt die Temperatur bei JEDEM Speichern mit, deshalb zaehlt nur eine
            // tatsaechliche Wertaenderung als Handeintrag. Sonst wuerde ein vom Wetterdienst
            // geholter Wert nach einmal Oeffnen-und-Speichern als "von Hand" dastehen.
            val previous = session.outsideTempC
            if (previous == null || kotlin.math.abs(it - previous) > 1e-6) {
                session.outsideTempSource = TemperatureSource.MANUAL.raw
            }
            session.outsideTempC = it
        }
        payload.avgSpeedKmh?.let { session.avgSpeedKmh = it }
        payload.latitude?.let { session.latitude = it }
        payload.longitude?.let { session.longitude = it }
        payload.geocodedPlace?.let { session.geocodedPlace = it }
        payload.notes?.let { session.notes = it }
        payload.needsReview?.let { session.needsReview = it }
        // Nach dem Anbieter: normalisiert wird gegen den Standard des (ggf. neuen) Anbieters.
        payload.energyMeter?.let {
            session.energyMeter = EnergyMeter.normalizedOverride(it, providerEnergyMeter(session.providerId))
        }
        // Preise wie update_session() im Backend: ein GEAENDERTER Gesamtpreis gewinnt, der
        // Preis je kWh wird daraus gerechnet. Das Formular schickt ein geleertes Feld als null
        // (= "nicht aendern"), der alte kWh-Preis bliebe sonst stehen und wuerde den
        // Gesamtpreis beim Sync auf dem Server wieder zurueckrechnen.
        val totalChanged = payload.priceTotal?.let { new ->
            previousTotal == null || kotlin.math.abs(new - previousTotal) > 1e-6
        } ?: false
        val kwh = session.energyKwh
        if (kwh != null && kwh > 0) {
            val total = session.priceTotal
            val perKwh = session.pricePerKwh
            if (total != null && total > 0 && (totalChanged || perKwh == null || perKwh == 0.0)) {
                session.pricePerKwh = Math.round(total / kwh * 10_000) / 10_000.0
            } else if (perKwh != null && perKwh > 0) {
                session.priceTotal = Math.round(perKwh * kwh * 100) / 100.0
            }
        }
        session.updatedAt = System.currentTimeMillis()
        session.isDirty = true
        sessions.upsert(session)
        updateProviderPriceMemory(session.providerId, ChargingType.from(payload.chargingType), payload.pricePerKwh)
        return decorateOne(session.asDTO())
    }

    suspend fun deleteSession(id: String) {
        val session = sessions.find(id) ?: throw LocalStoreException.notFound()
        if (session.serverId != null) {
            session.pendingDelete = true
            session.isDirty = true
            sessions.upsert(session)
        } else {
            sessions.delete(session)
        }
    }

    /** Provider "price memory": carry last_price_ac/dc_per_kwh, matching apply_provider_price() server-side. */
    private suspend fun updateProviderPriceMemory(
        providerId: String?,
        chargingType: ChargingType?,
        pricePerKwh: Double?
    ) {
        if (providerId == null || pricePerKwh == null) return
        val provider = providers.find(providerId) ?: return
        when (chargingType) {
            ChargingType.AC -> provider.lastPriceAcPerKwh = pricePerKwh
            ChargingType.DC -> provider.lastPriceDcPerKwh = pricePerKwh
            null -> return
        }
        providers.upsert(provider)
    }

    private suspend fun decorateOne(session: ChargingSession): ChargingSession {
        val siblings = allUndeletedSessions().filter { it.vehicleId == session.vehicleId }
        val decorated = decoratedWithConsumption(siblings).firstOrNull { it.id == session.id } ?: session
        return decorated.copy(feeShare = feeAllocation().shares[session.id])
    }

    private suspend fun decoratedWithFees(list: List<ChargingSession>): List<ChargingSession> {
        val shares = feeAllocation().shares
        if (shares.isEmpty()) return list
        return list.map { it.copy(feeShare = shares[it.id]) }
    }

    /** Enriches sessions with consumption, computed per-vehicle (see LocalConsumptionCalculator). */
    private suspend fun decoratedWithConsumption(list: List<ChargingSession>): List<ChargingSession> {
        val vehicleIds = list.map { it.vehicleId }.toSet()
        val byId = HashMap<String, LocalConsumptionCalculator.Result>()
        for (vehicleId in vehicleIds) {
            val capacity = vehicles.find(vehicleId)?.batteryCapacityKwh
            val vehicleSessions = list.filter { it.vehicleId == vehicleId }
            byId.putAll(LocalConsumptionCalculator.compute(vehicleSessions, capacity))
        }
        return list.map { session ->
            val result = byId[session.id]
            if (result != null) {
                session.copy(
                    consumptionKwhPer100km = result.value,
                    consumptionMethod = result.method
                )
            } else session
        }
    }
}

data class VehicleCostBasis(
    val vehicles: List<Vehicle>,
    val costs: List<VehicleCost>,
    /** Alle Ladevorgaenge mit kanonischer Fahrzeug-ID und Grundgebuehr-Anteil. */
    val sessions: List<ChargingSession>
)

data class ReimbursementBasis(
    val vehicles: List<Vehicle>,
    val providers: List<Provider>,
    val rates: List<ReimbursementRate>,
    /** Alle Ladevorgaenge mit kanonischer Fahrzeug- und Anbieter-ID und Grundgebuehr-Anteil. */
    val sessions: List<ChargingSession>
)

data class ReviewBasis(
    val vehicles: List<Vehicle>,
    val providers: List<Provider>,
    val locations: List<ChargingLocation>,
    /** Alle Ladevorgaenge mit kanonischen IDs und Grundgebuehr-Anteil. */
    val sessions: List<ChargingSession>,
    /** Perioden ohne Ladevorgang, kanonische Anbieter-ID, noch ungefiltert. */
    val unallocatedFees: List<LocalFeeAllocator.Period>
)
