package com.dominiqueherbrigpersonalteam.lademonitor.data.repo

import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ChargingLocation
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ChargingSession
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ChargingSessionPayload
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.GeocodeResult
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.LocationPayload
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.Provider
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ProviderFee
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ProviderFeePayload
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ProviderPayload
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.StatsSummary
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.Vehicle
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
        LocalDataStore.createSession(payload).also { syncAfterWrite() }

    suspend fun updateSession(id: String, payload: ChargingSessionPayload): ChargingSession =
        LocalDataStore.updateSession(id, payload).also { syncAfterWrite() }

    suspend fun deleteSession(id: String) {
        LocalDataStore.deleteSession(id)
        syncAfterWrite()
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
        return LocalStatsCalculator.compute(sessions, vehicles, providers, unallocated)
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
}
