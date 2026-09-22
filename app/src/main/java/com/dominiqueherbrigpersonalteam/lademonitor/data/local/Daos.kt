package com.dominiqueherbrigpersonalteam.lademonitor.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface VehicleDao {
    @Query("SELECT * FROM vehicles WHERE pendingDelete = 0 ORDER BY createdAt")
    suspend fun getAllUndeleted(): List<LocalVehicle>

    @Query("SELECT * FROM vehicles")
    suspend fun getAll(): List<LocalVehicle>

    @Query("SELECT * FROM vehicles WHERE serverId = :id OR localId = :id LIMIT 1")
    suspend fun find(id: String): LocalVehicle?

    @Query("SELECT * FROM vehicles WHERE serverId = :serverId LIMIT 1")
    suspend fun findByServerId(serverId: String): LocalVehicle?

    @Query("SELECT * FROM vehicles WHERE isDirty = 1")
    suspend fun getDirty(): List<LocalVehicle>

    @Upsert
    suspend fun upsert(vehicle: LocalVehicle)

    @Delete
    suspend fun delete(vehicle: LocalVehicle)

    /** Includes rows only marked for deletion - those matter on an account switch too. */
    @Query("SELECT COUNT(*) FROM vehicles")
    suspend fun countAll(): Int

    @Query("DELETE FROM vehicles")
    suspend fun clear()
}

@Dao
interface ProviderDao {
    @Query("SELECT * FROM providers WHERE pendingDelete = 0 ORDER BY createdAt")
    suspend fun getAllUndeleted(): List<LocalProvider>

    @Query("SELECT * FROM providers WHERE serverId = :id OR localId = :id LIMIT 1")
    suspend fun find(id: String): LocalProvider?

    @Query("SELECT * FROM providers WHERE serverId = :serverId LIMIT 1")
    suspend fun findByServerId(serverId: String): LocalProvider?

    @Query("SELECT * FROM providers")
    suspend fun getAll(): List<LocalProvider>

    @Query("SELECT * FROM providers WHERE isDirty = 1")
    suspend fun getDirty(): List<LocalProvider>

    @Upsert
    suspend fun upsert(provider: LocalProvider)

    @Delete
    suspend fun delete(provider: LocalProvider)

    /** Includes rows only marked for deletion - those matter on an account switch too. */
    @Query("SELECT COUNT(*) FROM providers")
    suspend fun countAll(): Int

    @Query("DELETE FROM providers")
    suspend fun clear()
}

@Dao
interface LocationDao {
    @Query("SELECT * FROM locations WHERE pendingDelete = 0 ORDER BY createdAt")
    suspend fun getAllUndeleted(): List<LocalChargingLocation>

    @Query("SELECT * FROM locations WHERE serverId = :id OR localId = :id LIMIT 1")
    suspend fun find(id: String): LocalChargingLocation?

    @Query("SELECT * FROM locations WHERE serverId = :serverId LIMIT 1")
    suspend fun findByServerId(serverId: String): LocalChargingLocation?

    @Query("SELECT * FROM locations")
    suspend fun getAll(): List<LocalChargingLocation>

    @Query("SELECT * FROM locations WHERE isDirty = 1")
    suspend fun getDirty(): List<LocalChargingLocation>

    @Upsert
    suspend fun upsert(location: LocalChargingLocation)

    @Delete
    suspend fun delete(location: LocalChargingLocation)

    /** Includes rows only marked for deletion - those matter on an account switch too. */
    @Query("SELECT COUNT(*) FROM locations")
    suspend fun countAll(): Int

    @Query("DELETE FROM locations")
    suspend fun clear()
}

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions WHERE pendingDelete = 0")
    suspend fun getAllUndeleted(): List<LocalChargingSession>

    @Query("SELECT * FROM sessions WHERE serverId = :id OR localId = :id LIMIT 1")
    suspend fun find(id: String): LocalChargingSession?

    @Query("SELECT * FROM sessions WHERE serverId = :serverId LIMIT 1")
    suspend fun findByServerId(serverId: String): LocalChargingSession?

    @Query("SELECT * FROM sessions WHERE vehicleId = :vehicleId")
    suspend fun getByVehicleId(vehicleId: String): List<LocalChargingSession>

    @Query("SELECT * FROM sessions")
    suspend fun getAll(): List<LocalChargingSession>

    @Query("SELECT * FROM sessions WHERE isDirty = 1")
    suspend fun getDirty(): List<LocalChargingSession>

    @Upsert
    suspend fun upsert(session: LocalChargingSession)

    @Delete
    suspend fun delete(session: LocalChargingSession)

    /** Includes rows only marked for deletion - those matter on an account switch too. */
    @Query("SELECT COUNT(*) FROM sessions")
    suspend fun countAll(): Int

    @Query("DELETE FROM sessions")
    suspend fun clear()
}

@Dao
interface ProviderFeeDao {
    @Query("SELECT * FROM provider_fees WHERE pendingDelete = 0 ORDER BY startDate DESC")
    suspend fun getAllUndeleted(): List<LocalProviderFee>

    @Query("SELECT * FROM provider_fees WHERE serverId = :id OR localId = :id LIMIT 1")
    suspend fun find(id: String): LocalProviderFee?

    @Query("SELECT * FROM provider_fees WHERE serverId = :serverId LIMIT 1")
    suspend fun findByServerId(serverId: String): LocalProviderFee?

    @Query("SELECT * FROM provider_fees")
    suspend fun getAll(): List<LocalProviderFee>

    @Query("SELECT * FROM provider_fees WHERE isDirty = 1")
    suspend fun getDirty(): List<LocalProviderFee>

    @Upsert
    suspend fun upsert(fee: LocalProviderFee)

    @Delete
    suspend fun delete(fee: LocalProviderFee)

    @Query("SELECT COUNT(*) FROM provider_fees")
    suspend fun countAll(): Int

    @Query("DELETE FROM provider_fees")
    suspend fun clear()
}
