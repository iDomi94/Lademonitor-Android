package com.dominiqueherbrigpersonalteam.lademonitor.data.repo

import com.dominiqueherbrigpersonalteam.lademonitor.LademonitorApp
import com.dominiqueherbrigpersonalteam.lademonitor.R
import com.dominiqueherbrigpersonalteam.lademonitor.data.local.LocalChargingLocation
import com.dominiqueherbrigpersonalteam.lademonitor.data.local.LocalChargingSession
import com.dominiqueherbrigpersonalteam.lademonitor.data.local.LocalProvider
import com.dominiqueherbrigpersonalteam.lademonitor.data.local.LocalStore
import com.dominiqueherbrigpersonalteam.lademonitor.data.local.LocalVehicle
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ChargingLocation
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ChargingSession
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ChargingType
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.LocationPayload
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.Provider
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ProviderPayload
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.Vehicle
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

    // MARK: - Reset

    /**
     * Whether there is any local data at all - including the rows only marked for deletion, since
     * those would have an effect on an account switch too (see the account-switch reset in
     * [SyncService]). Needed to ask about the handling of local data on sign-in only when there
     * really is something to decide.
     */
    suspend fun hasAnyData(): Boolean =
        sessions.countAll() > 0 || vehicles.countAll() > 0 ||
            providers.countAll() > 0 || locations.countAll() > 0

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
        }
        return parts.joinToString(", ")
    }

    /** Irreversibly deletes ALL local data. */
    suspend fun resetAllData() {
        sessions.clear()
        locations.clear()
        providers.clear()
        vehicles.clear()
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
        vehicle.updatedAt = System.currentTimeMillis()
        vehicle.isDirty = true
        vehicles.upsert(vehicle)
        return vehicle.asDTO()
    }

    /** Cascades to the vehicle's sessions, matching the server. */
    suspend fun deleteVehicle(id: String) {
        val vehicle = vehicles.find(id) ?: throw LocalStoreException.notFound()
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
            notes = payload.notes
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
        provider.updatedAt = System.currentTimeMillis()
        provider.isDirty = true
        providers.upsert(provider)
        return provider.asDTO()
    }

    suspend fun deleteProvider(id: String) {
        val provider = providers.find(id) ?: throw LocalStoreException.notFound()
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
        var list = decoratedWithConsumption(all).sortedByDescending { it.startTime }
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
            startTime = payload.startTime ?: System.currentTimeMillis(),
            chargingType = payload.chargingType,
            socStart = payload.socStart,
            socEnd = payload.socEnd,
            energyKwh = payload.energyKwh,
            odometerKm = payload.odometerKm,
            priceTotal = payload.priceTotal,
            pricePerKwh = payload.pricePerKwh,
            latitude = payload.latitude,
            longitude = payload.longitude,
            geocodedPlace = payload.geocodedPlace,
            notes = payload.notes,
            source = "manual",
            needsReview = payload.needsReview ?: false
        )
        sessions.upsert(session)
        updateProviderPriceMemory(payload.providerId, ChargingType.from(payload.chargingType), payload.pricePerKwh)
        return decorateOne(session.asDTO())
    }

    suspend fun updateSession(id: String, payload: ChargingSessionPayload): ChargingSession {
        val session = sessions.find(id) ?: throw LocalStoreException.notFound()
        payload.providerId?.let { session.providerId = it }
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
        payload.latitude?.let { session.latitude = it }
        payload.longitude?.let { session.longitude = it }
        payload.geocodedPlace?.let { session.geocodedPlace = it }
        payload.notes?.let { session.notes = it }
        payload.needsReview?.let { session.needsReview = it }
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
        return decoratedWithConsumption(siblings).firstOrNull { it.id == session.id } ?: session
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
