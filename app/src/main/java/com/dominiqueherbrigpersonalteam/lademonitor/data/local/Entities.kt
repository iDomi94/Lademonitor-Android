package com.dominiqueherbrigpersonalteam.lademonitor.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ChargingLocation
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ChargingSession
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.Provider
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.Vehicle
import java.util.UUID

/**
 * Room entities — the Kotlin/Room port of the SwiftData `LocalModels.swift`. Alongside the domain
 * fields each row carries sync metadata:
 *  - [localId]: stable local identity (exists before any server contact); also the primary key.
 *  - serverId: set only after the row has been uploaded once.
 *  - updatedAt/isDirty/pendingDelete: drive the [com.dominiqueherbrigpersonalteam.lademonitor.data.repo.SyncService].
 *
 * The DTO `id` is resolved as `serverId ?: localId`, exactly like the iOS `asDTO` mapping, so views
 * never need to know whether a row has been synced yet.
 */

@Entity(tableName = "vehicles")
data class LocalVehicle(
    @PrimaryKey val localId: String = UUID.randomUUID().toString(),
    var serverId: String? = null,
    var externalId: String = "",
    var name: String = "",
    var brand: String? = null,
    var model: String? = null,
    var batteryCapacityKwh: Double? = null,
    var isActive: Boolean = true,
    var createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),
    var isDirty: Boolean = true,
    var pendingDelete: Boolean = false
) {
    fun asDTO() = Vehicle(
        id = serverId ?: localId,
        externalId = externalId,
        name = name,
        brand = brand,
        model = model,
        batteryCapacityKwh = batteryCapacityKwh,
        isActive = isActive
    )
}

@Entity(tableName = "providers")
data class LocalProvider(
    @PrimaryKey val localId: String = UUID.randomUUID().toString(),
    var serverId: String? = null,
    var name: String = "",
    var lastPriceAcPerKwh: Double? = null,
    var lastPriceDcPerKwh: Double? = null,
    var notes: String? = null,
    var createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),
    var isDirty: Boolean = true,
    var pendingDelete: Boolean = false
) {
    fun asDTO() = Provider(
        id = serverId ?: localId,
        name = name,
        lastPriceAcPerKwh = lastPriceAcPerKwh,
        lastPriceDcPerKwh = lastPriceDcPerKwh,
        notes = notes
    )
}

@Entity(tableName = "locations")
data class LocalChargingLocation(
    @PrimaryKey val localId: String = UUID.randomUUID().toString(),
    var serverId: String? = null,
    var name: String = "",
    var latitude: Double = 0.0,
    var longitude: Double = 0.0,
    var radiusM: Int = 100,
    var defaultProviderId: String? = null,
    var createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),
    var isDirty: Boolean = true,
    var pendingDelete: Boolean = false
) {
    fun asDTO() = ChargingLocation(
        id = serverId ?: localId,
        name = name,
        latitude = latitude,
        longitude = longitude,
        radiusM = radiusM,
        defaultProviderId = defaultProviderId
    )
}

@Entity(tableName = "sessions")
data class LocalChargingSession(
    @PrimaryKey val localId: String = UUID.randomUUID().toString(),
    var serverId: String? = null,
    var vehicleId: String = "",
    var providerId: String? = null,
    var locationId: String? = null,
    var startTime: Long = System.currentTimeMillis(),
    var endTime: Long? = null,
    var chargingType: String? = null,
    var socStart: Int? = null,
    var socEnd: Int? = null,
    var energyKwh: Double? = null,
    var energyIsEstimated: Boolean = false,
    var odometerKm: Int? = null,
    /** Aussentemperatur beim Ladebeginn - siehe Models.ChargingSession.outsideTempC. */
    var outsideTempC: Double? = null,
    var priceTotal: Double? = null,
    var pricePerKwh: Double? = null,
    var latitude: Double? = null,
    var longitude: Double? = null,
    var geocodedPlace: String? = null,
    var notes: String? = null,
    var source: String = "manual",
    var needsReview: Boolean = false,
    var externalSessionId: String? = null,
    var createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),
    var isDirty: Boolean = true,
    var pendingDelete: Boolean = false
) {
    /** Consumption fields are decorated later by the LocalConsumptionCalculator, hence null here. */
    fun asDTO() = ChargingSession(
        id = serverId ?: localId,
        vehicleId = vehicleId,
        providerId = providerId,
        locationId = locationId,
        startTime = startTime,
        endTime = endTime,
        chargingType = chargingType,
        socStart = socStart,
        socEnd = socEnd,
        energyKwh = energyKwh,
        energyIsEstimated = energyIsEstimated,
        odometerKm = odometerKm,
        outsideTempC = outsideTempC,
        priceTotal = priceTotal,
        pricePerKwh = pricePerKwh,
        latitude = latitude,
        longitude = longitude,
        geocodedPlace = geocodedPlace,
        consumptionKwhPer100km = null,
        consumptionMethod = null,
        notes = notes,
        source = source,
        needsReview = needsReview,
        externalSessionId = externalSessionId
    )
}
