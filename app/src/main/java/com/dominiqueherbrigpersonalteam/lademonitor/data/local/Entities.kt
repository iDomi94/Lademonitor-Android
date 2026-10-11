package com.dominiqueherbrigpersonalteam.lademonitor.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ChargingLocation
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ChargingSession
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ChargingPlace
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.EnergyMeter
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.Ownership
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.Provider
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ProviderFee
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ReimbursementMode
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ReimbursementRate
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.Reminder
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.Vehicle
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.VehicleCost
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.VehicleOwnership
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.VehicleReimbursement
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
    var pendingDelete: Boolean = false,
    /**
     * Kauf/Leasing (Room 6 -> 7, Server ab 0.32.0), siehe Models.Ownership. Alle nullable ohne
     * Default in der Tabelle; `ownership == null` gilt als keine Angabe. Datumsfelder sind wie
     * bei den Grundgebuehren lokale Mitternacht in Epoch-Millis.
     */
    var ownership: String? = null,
    var purchasePrice: Double? = null,
    var purchaseDate: Long? = null,
    var firstRegistration: Long? = null,
    var purchaseOdometerKm: Int? = null,
    var depreciationMonthlyOverride: Double? = null,
    var leasingRateMonthly: Double? = null,
    var leasingStart: Long? = null,
    var leasingEnd: Long? = null,
    var leasingDownPayment: Double? = null,
    /**
     * Dienstwagen und Erstattungsbericht (Room 8 -> 9, Server ab 0.35.0), siehe
     * Models.VehicleReimbursement. Wie die Kauf-/Leasingfelder alle nullable ohne Default in der
     * Tabelle; `null` gilt als aus bzw. pauschal.
     */
    var isCompanyCar: Boolean? = null,
    var reimbursementMode: String? = null,
    var reimbursementIncludeFees: Boolean? = null,
    var employeeName: String? = null,
    var employeeNumber: String? = null,
    var licensePlate: String? = null,
    var reimbursementMonthlyMail: Boolean? = null
) {
    fun asDTO() = Vehicle(
        id = serverId ?: localId,
        externalId = externalId,
        name = name,
        brand = brand,
        model = model,
        batteryCapacityKwh = batteryCapacityKwh,
        isActive = isActive,
        ownership = Ownership.from(ownership).raw,
        purchasePrice = purchasePrice,
        purchaseDate = purchaseDate,
        firstRegistration = firstRegistration,
        purchaseOdometerKm = purchaseOdometerKm,
        depreciationMonthlyOverride = depreciationMonthlyOverride,
        leasingRateMonthly = leasingRateMonthly,
        leasingStart = leasingStart,
        leasingEnd = leasingEnd,
        leasingDownPayment = leasingDownPayment,
        isCompanyCar = isCompanyCar ?: false,
        reimbursementMode = ReimbursementMode.from(reimbursementMode).raw,
        reimbursementIncludeFees = reimbursementIncludeFees ?: false,
        employeeName = employeeName,
        employeeNumber = employeeNumber,
        licensePlate = licensePlate,
        reimbursementMonthlyMail = reimbursementMonthlyMail ?: false
    )

    /** Uebernimmt alle Dienstwagen-Angaben (lokales Speichern wie Pull vom Server). */
    fun applyReimbursement(r: VehicleReimbursement) {
        isCompanyCar = r.isCompanyCar
        reimbursementMode = ReimbursementMode.from(r.reimbursementMode).raw
        reimbursementIncludeFees = r.reimbursementIncludeFees
        employeeName = r.employeeName
        employeeNumber = r.employeeNumber
        licensePlate = r.licensePlate
        reimbursementMonthlyMail = r.reimbursementMonthlyMail
    }

    fun reimbursementFields() = asDTO().reimbursementFields

    /** Uebernimmt alle Kauf-/Leasingangaben (lokales Speichern wie Pull vom Server). */
    fun applyOwnership(o: VehicleOwnership) {
        ownership = Ownership.from(o.ownership).raw
        purchasePrice = o.purchasePrice
        purchaseDate = o.purchaseDate
        firstRegistration = o.firstRegistration
        purchaseOdometerKm = o.purchaseOdometerKm
        depreciationMonthlyOverride = o.depreciationMonthlyOverride
        leasingRateMonthly = o.leasingRateMonthly
        leasingStart = o.leasingStart
        leasingEnd = o.leasingEnd
        leasingDownPayment = o.leasingDownPayment
    }

    fun ownershipFields() = asDTO().ownershipFields
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
    var pendingDelete: Boolean = false,
    /**
     * Messort der kWh bei diesem Anbieter ("charger" | "vehicle", Room 4 -> 5). Bewusst nullable
     * ohne Default in der Tabelle: `null` gilt als Ladesaeule, und die Migration braucht so keinen
     * DEFAULT, der exakt zu Rooms Schemapruefung passen muesste.
     */
    var energyMeter: String? = "charger",
    /**
     * Ladeort-Art ("home" | "work" | "public", Room 5 -> 6), siehe Models.ChargingPlace. Wie
     * [energyMeter] nullable ohne Default in der Tabelle: `null` gilt als unterwegs.
     */
    var chargingPlace: String? = "public"
) {
    fun asDTO() = Provider(
        id = serverId ?: localId,
        name = name,
        lastPriceAcPerKwh = lastPriceAcPerKwh,
        lastPriceDcPerKwh = lastPriceDcPerKwh,
        notes = notes,
        energyMeter = energyMeter ?: EnergyMeter.CHARGER.raw,
        chargingPlace = ChargingPlace.from(chargingPlace).raw
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
    /**
     * Herkunft der Temperatur ("vehicle" | "manual" | "weather", siehe
     * Models.TemperatureSource). Im Server-Modus gespiegelt, im Local-Only-Modus hier selbst
     * gesetzt - ein von Hand eingetragener Wert ist "manual".
     */
    var outsideTempSource: String? = null,
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
    var pendingDelete: Boolean = false,
    /**
     * Uebersteuerung des Messorts der kWh (Room 4 -> 5), roh wie vom Server: `null` = folgt dem
     * Anbieter (siehe Models.EnergyMeter).
     */
    var energyMeter: String? = null
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
        outsideTempSource = outsideTempSource,
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
        externalSessionId = externalSessionId,
        energyMeter = energyMeter
    )
}

/**
 * Grundgebuehr/Abo eines Anbieters (Room 3 -> 4). [providerId] zeigt wie bei den Ladevorgaengen
 * auf `serverId ?: localId` des Anbieters.
 */
@Entity(tableName = "provider_fees")
data class LocalProviderFee(
    @PrimaryKey val localId: String = UUID.randomUUID().toString(),
    var serverId: String? = null,
    var providerId: String = "",
    var amount: Double = 0.0,
    var interval: String = "monthly",
    var startDate: Long = System.currentTimeMillis(),
    var endDate: Long? = null,
    var label: String? = null,
    var notes: String? = null,
    var createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),
    var isDirty: Boolean = true,
    var pendingDelete: Boolean = false
) {
    fun asDTO() = ProviderFee(
        id = serverId ?: localId,
        providerId = providerId,
        amount = amount,
        interval = interval,
        startDate = startDate,
        endDate = endDate,
        label = label,
        notes = notes
    )
}

/**
 * Sonstige Kosten eines Fahrzeugs (Room 6 -> 7, Server ab 0.32.0). [vehicleId] zeigt wie bei den
 * Ladevorgaengen auf `serverId ?: localId` des Fahrzeugs.
 */
@Entity(tableName = "vehicle_costs")
data class LocalVehicleCost(
    @PrimaryKey val localId: String = UUID.randomUUID().toString(),
    var serverId: String? = null,
    var vehicleId: String = "",
    var amount: Double = 0.0,
    var category: String = "other",
    var interval: String = "once",
    var startDate: Long = System.currentTimeMillis(),
    var endDate: Long? = null,
    var odometerKm: Int? = null,
    var label: String? = null,
    var notes: String? = null,
    var createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),
    var isDirty: Boolean = true,
    var pendingDelete: Boolean = false
) {
    fun asDTO() = VehicleCost(
        id = serverId ?: localId,
        vehicleId = vehicleId,
        amount = amount,
        category = category,
        interval = interval,
        startDate = startDate,
        endDate = endDate,
        odometerKm = odometerKm,
        label = label,
        notes = notes
    )
}

/**
 * Erinnerung eines Fahrzeugs (Room 7 -> 8, Server ab 0.33.0). [vehicleId] zeigt wie bei den
 * Fahrzeugkosten auf `serverId ?: localId` des Fahrzeugs. Datumsfelder sind lokale Mitternacht in
 * Epoch-Millis. Status und Prognose werden nie gespeichert (LocalReminderCalculator).
 */
@Entity(tableName = "reminders")
data class LocalReminder(
    @PrimaryKey val localId: String = UUID.randomUUID().toString(),
    var serverId: String? = null,
    var vehicleId: String = "",
    var kind: String = "custom",
    var title: String? = null,
    var dueDate: Long? = null,
    var dueKm: Int? = null,
    var repeatMonths: Int? = null,
    var repeatKm: Int? = null,
    var leadDays: Int = 30,
    var leadKm: Int = 1000,
    var notes: String? = null,
    var lastDoneDate: Long? = null,
    var lastDoneKm: Int? = null,
    var createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),
    var isDirty: Boolean = true,
    var pendingDelete: Boolean = false
) {
    fun asDTO() = Reminder(
        id = serverId ?: localId,
        vehicleId = vehicleId,
        kind = kind,
        title = title,
        dueDate = dueDate,
        dueKm = dueKm,
        repeatMonths = repeatMonths,
        repeatKm = repeatKm,
        leadDays = leadDays,
        leadKm = leadKm,
        notes = notes,
        lastDoneDate = lastDoneDate,
        lastDoneKm = lastDoneKm
    )

    /** Uebernimmt alle fachlichen Felder (lokales Speichern wie Pull vom Server). */
    fun applyFields(r: Reminder) {
        vehicleId = r.vehicleId
        kind = r.kind
        title = r.title
        dueDate = r.dueDate
        dueKm = r.dueKm
        repeatMonths = r.repeatMonths
        repeatKm = r.repeatKm
        leadDays = r.leadDays
        leadKm = r.leadKm
        notes = r.notes
        lastDoneDate = r.lastDoneDate
        lastDoneKm = r.lastDoneKm
    }
}

/**
 * Erstattungssatz eines Dienstwagens (Room 8 -> 9, Server ab 0.35.0). [vehicleId] zeigt wie bei
 * den Fahrzeugkosten auf `serverId ?: localId` des Fahrzeugs, [validFrom] ist lokale Mitternacht
 * in Epoch-Millis.
 */
@Entity(tableName = "reimbursement_rates")
data class LocalReimbursementRate(
    @PrimaryKey val localId: String = UUID.randomUUID().toString(),
    var serverId: String? = null,
    var vehicleId: String = "",
    var validFrom: Long = System.currentTimeMillis(),
    var ratePerKwh: Double = 0.0,
    var createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),
    var isDirty: Boolean = true,
    var pendingDelete: Boolean = false
) {
    fun asDTO() = ReimbursementRate(
        id = serverId ?: localId,
        vehicleId = vehicleId,
        validFrom = validFrom,
        ratePerKwh = ratePerKwh
    )
}
