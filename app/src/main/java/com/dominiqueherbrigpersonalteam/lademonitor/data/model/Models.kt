package com.dominiqueherbrigpersonalteam.lademonitor.data.model

import androidx.annotation.StringRes
import com.dominiqueherbrigpersonalteam.lademonitor.LademonitorApp
import com.dominiqueherbrigpersonalteam.lademonitor.R
import com.dominiqueherbrigpersonalteam.lademonitor.data.remote.ServerDate
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Kotlin/Moshi port of the iOS `Models.swift` Codable structs. These types are shared by
 * the REST layer (server mode) and the UI, exactly like the iOS Codable structs.
 *
 * Timestamps are carried as epoch-millis [Long]; the [com.dominiqueherbrigpersonalteam.lademonitor.data.remote.ServerDate]
 * qualifier tells Moshi to (de)serialise them against the server's naive/ISO datetime
 * strings the same way the iOS APIClient does.
 */

enum class ChargingType(val raw: String) {
    AC("AC"),
    DC("DC");

    companion object {
        fun from(raw: String?): ChargingType? = entries.firstOrNull { it.raw == raw }
    }
}

enum class SessionSource(val raw: String, @param:StringRes val labelRes: Int) {
    MANUAL("manual", R.string.session_source_manual),
    AUTOMATIC("automatic", R.string.session_source_automatic),
    IMPORT("import", R.string.session_source_import);

    companion object {
        fun from(raw: String?): SessionSource = entries.firstOrNull { it.raw == raw } ?: MANUAL
    }
}

/**
 * How the server (or [com.dominiqueherbrigpersonalteam.lademonitor.data.repo.LocalConsumptionCalculator])
 * computed the kWh/100km for a session. Drives the marker + explanation text in the list,
 * mirroring `ConsumptionMethod` in the iOS app.
 */
/**
 * Woher die Aussentemperatur eines Ladevorgangs stammt (Server ab 0.24.0).
 *
 * Nicht nur Buchhaltung: ein Wert vom Wetterdienst ist eine Rekonstruktion am Ladeort, kein
 * Messwert aus dem Auto - der Fahrzeugsensor liest je nach Restwaerme und Sonne gern ein bis
 * zwei Grad hoeher. Bestandsvorgaenge tragen keine Angabe (`null`), und die wird bewusst nicht
 * geraten.
 */
enum class TemperatureSource(val raw: String, @param:StringRes val labelRes: Int) {
    VEHICLE("vehicle", R.string.temp_source_vehicle),
    MANUAL("manual", R.string.temp_source_manual),
    WEATHER("weather", R.string.temp_source_weather),

    /**
     * Vom Wetterdienst als Mittel der Tagstunden (6-20 Uhr) ueber den Zeitraum seit dem
     * vorherigen Ladevorgang statt zu einem Zeitpunkt - ab Server 0.24.1 der Normalfall fuer
     * JEDEN geholten Wert ([WEATHER] gibt es nur noch bei Bestandsdaten). Eigener Wert, weil
     * ein Fahrzeugsensor zwangslaeufig punktuell misst und nicht mitteln kann; ohne die
     * Unterscheidung laegen zwei Messgroessen in derselben Spalte.
     */
    WEATHER_DAILY("weather_daily", R.string.temp_source_weather_daily);

    companion object {
        fun from(raw: String?): TemperatureSource? = entries.firstOrNull { it.raw == raw }
    }
}

/**
 * Wo die kWh eines Ladevorgangs abgelesen wurden (Server ab 0.29.0): an der Ladesaeule bzw.
 * Wallbox (enthaelt die Ladeverluste) oder im Fahrzeug (ohne Ladeverluste). Der Anbieter traegt
 * den Standard, ein Ladevorgang kann ihn uebersteuern (`null` = folgt dem Anbieter). Die
 * Akku-Auswertung laesst im Fahrzeug abgelesene Vorgaenge bei den Ladeverlusten weg, zaehlt sie
 * aber im Akku-Index mit.
 */
enum class EnergyMeter(val raw: String) {
    CHARGER("charger"),
    VEHICLE("vehicle");

    companion object {
        fun from(raw: String?): EnergyMeter? = entries.firstOrNull { it.raw == raw }

        /** Uebersteuerung am Vorgang, sonst Standard des Anbieters, sonst Ladesaeule. */
        fun effective(sessionOverride: String?, providerDefault: String?): EnergyMeter =
            from(sessionOverride) ?: from(providerDefault) ?: CHARGER

        /**
         * Was am Vorgang gespeichert wird - dieselbe Regel wie serverseitig: eine Wahl, die dem
         * Standard des Anbieters entspricht, wird als `null` abgelegt, damit der Vorgang einer
         * spaeteren Aenderung am Anbieter folgt.
         */
        fun normalizedOverride(choice: String?, providerDefault: String?): String? {
            val c = from(choice) ?: return null
            return if (c == (from(providerDefault) ?: CHARGER)) null else c.raw
        }
    }
}

/**
 * Wo der Anbieter laedt (Server ab 0.31.0): zu Hause, bei der Arbeit oder unterwegs. Haengt am
 * Anbieter wie [EnergyMeter]. Unbekannte oder fehlende Werte gelten als unterwegs - genau wie
 * serverseitig fuer Bestandszeilen und fuer Vorgaenge ohne Anbieter.
 */
enum class ChargingPlace(val raw: String, @param:StringRes val labelRes: Int) {
    HOME("home", R.string.charging_place_home),
    WORK("work", R.string.charging_place_work),
    PUBLIC("public", R.string.charging_place_public);

    companion object {
        fun from(raw: String?): ChargingPlace = entries.firstOrNull { it.raw == raw } ?: PUBLIC

        /** Nur bekannte Werte, sonst null - fuer "unveraendert lassen" beim Speichern/Sync. */
        fun fromOrNull(raw: String?): ChargingPlace? = entries.firstOrNull { it.raw == raw }
    }
}

enum class ConsumptionMethod(
    val raw: String,
    val marker: String,
    @param:StringRes val shortLabelRes: Int,
    @param:StringRes val explanationRes: Int
) {
    FULL_CHARGE_INTERVAL(
        "full_charge_interval", "🎯", R.string.consumption_method_full_charge_interval_label,
        R.string.consumption_method_full_charge_interval_explanation
    ),
    SOC_CORRECTED(
        "soc_corrected", "✓", R.string.consumption_method_soc_corrected_label,
        R.string.consumption_method_soc_corrected_explanation
    ),
    NAIVE(
        "naive", "~", R.string.consumption_method_naive_label,
        R.string.consumption_method_naive_explanation
    ),
    ESTIMATED_ENERGY(
        "estimated_energy", "~", R.string.consumption_method_estimated_energy_label,
        R.string.consumption_method_estimated_energy_explanation
    ),
    UNAVAILABLE(
        "unavailable", "", R.string.consumption_method_unavailable_label,
        R.string.consumption_method_unavailable_explanation
    );

    companion object {
        fun from(raw: String?): ConsumptionMethod? = entries.firstOrNull { it.raw == raw }
    }
}

@JsonClass(generateAdapter = true)
data class Vehicle(
    val id: String,
    @Json(name = "external_id") val externalId: String,
    val name: String,
    val brand: String? = null,
    val model: String? = null,
    @Json(name = "battery_capacity_kwh") val batteryCapacityKwh: Double? = null,
    @Json(name = "is_active") val isActive: Boolean = true,
    /**
     * Kauf/Leasing (Server ab 0.32.0): "none" | "purchase" | "leasing", siehe [Ownership].
     * Bewusst nullable: ein aelterer Server schickt das Feld nicht, und der Sync darf die lokalen
     * Angaben dann nicht leeren (siehe SyncService.pullVehicles). Die vom Server zusaetzlich
     * berechneten Felder (estimated_value usw.) werden nicht uebernommen - die App rechnet sie
     * selbst ([com.dominiqueherbrigpersonalteam.lademonitor.data.repo.LocalVehicleCostCalculator]).
     */
    val ownership: String? = null,
    @Json(name = "purchase_price") val purchasePrice: Double? = null,
    /** Datumsfelder sind reine Kalendertage (lokale Mitternacht), wie bei den Grundgebuehren. */
    @ServerDate @Json(name = "purchase_date") val purchaseDate: Long? = null,
    @ServerDate @Json(name = "first_registration") val firstRegistration: Long? = null,
    @Json(name = "purchase_odometer_km") val purchaseOdometerKm: Int? = null,
    @Json(name = "depreciation_monthly_override") val depreciationMonthlyOverride: Double? = null,
    @Json(name = "leasing_rate_monthly") val leasingRateMonthly: Double? = null,
    @ServerDate @Json(name = "leasing_start") val leasingStart: Long? = null,
    @ServerDate @Json(name = "leasing_end") val leasingEnd: Long? = null,
    @Json(name = "leasing_down_payment") val leasingDownPayment: Double? = null,
    /**
     * Dienstwagen und Erstattungsbericht (Server ab 0.35.0), siehe [VehicleReimbursement].
     * Bewusst nullable: ein aelterer Server schickt die Felder nicht, und der Sync darf die lokalen
     * Angaben dann nicht leeren - fehlt [isCompanyCar], gelten alle sieben als nicht geliefert
     * (siehe SyncService.pullVehicles).
     */
    @Json(name = "is_company_car") val isCompanyCar: Boolean? = null,
    @Json(name = "reimbursement_mode") val reimbursementMode: String? = null,
    @Json(name = "reimbursement_include_fees") val reimbursementIncludeFees: Boolean? = null,
    @Json(name = "employee_name") val employeeName: String? = null,
    @Json(name = "employee_number") val employeeNumber: String? = null,
    @Json(name = "license_plate") val licensePlate: String? = null,
    @Json(name = "reimbursement_monthly_mail") val reimbursementMonthlyMail: Boolean? = null
) {
    val ownershipValue: Ownership get() = Ownership.from(ownership)

    val reimbursementModeValue: ReimbursementMode get() = ReimbursementMode.from(reimbursementMode)

    /** Die Dienstwagen-Angaben als eigenes Paket, z.B. zum Vorbefuellen der Einstellungen. */
    val reimbursementFields: VehicleReimbursement
        get() = VehicleReimbursement(
            isCompanyCar = isCompanyCar ?: false,
            reimbursementMode = reimbursementModeValue.raw,
            reimbursementIncludeFees = reimbursementIncludeFees ?: false,
            employeeName = employeeName,
            employeeNumber = employeeNumber,
            licensePlate = licensePlate,
            reimbursementMonthlyMail = reimbursementMonthlyMail ?: false
        )

    /** Die Kauf-/Leasingangaben als eigenes Paket, z.B. zum Vorbefuellen des Formulars. */
    val ownershipFields: VehicleOwnership
        get() = VehicleOwnership(
            ownership = ownershipValue.raw,
            purchasePrice = purchasePrice,
            purchaseDate = purchaseDate,
            firstRegistration = firstRegistration,
            purchaseOdometerKm = purchaseOdometerKm,
            depreciationMonthlyOverride = depreciationMonthlyOverride,
            leasingRateMonthly = leasingRateMonthly,
            leasingStart = leasingStart,
            leasingEnd = leasingEnd,
            leasingDownPayment = leasingDownPayment
        )
}

/** Kauf oder Leasing am Fahrzeug (Server ab 0.32.0). Fehlend oder unbekannt = keine Angabe. */
enum class Ownership(val raw: String, @param:StringRes val labelRes: Int) {
    NONE("none", R.string.ownership_none),
    PURCHASE("purchase", R.string.ownership_purchase),
    LEASING("leasing", R.string.ownership_leasing);

    companion object {
        fun from(raw: String?): Ownership = entries.firstOrNull { it.raw == raw } ?: NONE
    }
}

/** Erstattungsart eines Dienstwagens (Server ab 0.35.0). Fehlend oder unbekannt = pauschal. */
enum class ReimbursementMode(val raw: String, @param:StringRes val labelRes: Int) {
    FLAT("flat", R.string.reimbursement_mode_flat),
    ACTUAL("actual", R.string.reimbursement_mode_actual);

    companion object {
        fun from(raw: String?): ReimbursementMode = entries.firstOrNull { it.raw == raw } ?: FLAT
    }
}

/**
 * Dienstwagen-Angaben eines Fahrzeugs (Server ab 0.35.0). Wie [VehicleOwnership] an
 * [VehiclePayload] gehaengt und im ApiClient mit ALLEN Feldern inkl. `null` gesendet - so lassen
 * sich Name, Personalnummer und Kennzeichen wieder leeren. Die Schalter und die Art sind nie null
 * (null hiesse beim Server "unveraendert").
 */
@JsonClass(generateAdapter = true)
data class VehicleReimbursement(
    @Json(name = "is_company_car") val isCompanyCar: Boolean = false,
    @Json(name = "reimbursement_mode") val reimbursementMode: String = ReimbursementMode.FLAT.raw,
    @Json(name = "reimbursement_include_fees") val reimbursementIncludeFees: Boolean = false,
    @Json(name = "employee_name") val employeeName: String? = null,
    @Json(name = "employee_number") val employeeNumber: String? = null,
    @Json(name = "license_plate") val licensePlate: String? = null,
    /** Monatsmail mit dem Bericht des Vormonats - reine Serverfunktion. */
    @Json(name = "reimbursement_monthly_mail") val reimbursementMonthlyMail: Boolean = false
)

/**
 * Kauf-/Leasingangaben eines Fahrzeugs. Wird an [VehiclePayload] gehaengt und im ApiClient mit
 * ALLEN Feldern inkl. `null` gesendet - der Server leert ein Feld nur, wenn es ausdruecklich als
 * null kommt (z.B. einen wieder entfernten festen Wertverlust). [ownership] ist immer gesetzt.
 */
@JsonClass(generateAdapter = true)
data class VehicleOwnership(
    val ownership: String = Ownership.NONE.raw,
    @Json(name = "purchase_price") val purchasePrice: Double? = null,
    @ServerDate @Json(name = "purchase_date") val purchaseDate: Long? = null,
    @ServerDate @Json(name = "first_registration") val firstRegistration: Long? = null,
    @Json(name = "purchase_odometer_km") val purchaseOdometerKm: Int? = null,
    @Json(name = "depreciation_monthly_override") val depreciationMonthlyOverride: Double? = null,
    @Json(name = "leasing_rate_monthly") val leasingRateMonthly: Double? = null,
    @ServerDate @Json(name = "leasing_start") val leasingStart: Long? = null,
    @ServerDate @Json(name = "leasing_end") val leasingEnd: Long? = null,
    @Json(name = "leasing_down_payment") val leasingDownPayment: Double? = null
)

@JsonClass(generateAdapter = true)
data class Provider(
    val id: String,
    val name: String,
    @Json(name = "last_price_ac_per_kwh") val lastPriceAcPerKwh: Double? = null,
    @Json(name = "last_price_dc_per_kwh") val lastPriceDcPerKwh: Double? = null,
    val notes: String? = null,
    /**
     * Wo die kWh bei diesem Anbieter standardmaessig abgelesen werden ("charger" | "vehicle",
     * siehe [EnergyMeter]). Ein Server vor 0.29.0 schickt das Feld nicht - dann Ladesaeule.
     */
    @Json(name = "energy_meter") val energyMeter: String = EnergyMeter.CHARGER.raw,
    /**
     * Ladeort-Art ("home" | "work" | "public", siehe [ChargingPlace]). Bewusst nullable: ein
     * Server vor 0.31.0 schickt das Feld nicht, und der Sync darf den lokalen Wert dann nicht
     * auf "unterwegs" zuruecksetzen.
     */
    @Json(name = "charging_place") val chargingPlace: String? = null
)

@JsonClass(generateAdapter = true)
data class ChargingLocation(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    @Json(name = "radius_m") val radiusM: Int,
    @Json(name = "default_provider_id") val defaultProviderId: String? = null
)

@JsonClass(generateAdapter = true)
data class ChargingSession(
    val id: String,
    @Json(name = "vehicle_id") val vehicleId: String,
    @Json(name = "provider_id") val providerId: String? = null,
    @Json(name = "location_id") val locationId: String? = null,
    @ServerDate @Json(name = "start_time") val startTime: Long,
    @ServerDate @Json(name = "end_time") val endTime: Long? = null,
    @Json(name = "charging_type") val chargingType: String? = null,
    @Json(name = "soc_start") val socStart: Int? = null,
    @Json(name = "soc_end") val socEnd: Int? = null,
    @Json(name = "energy_kwh") val energyKwh: Double? = null,
    @Json(name = "energy_is_estimated") val energyIsEstimated: Boolean = false,
    /**
     * Davon aus dem Netz bezogene kWh (Server ab 0.41.0), der Rest von [energyKwh] kam von der
     * eigenen PV-Anlage bzw. dem Hausakku. `null` = unbekannt (kein Anteil ausweisen). Home
     * Assistant (Integration ab 0.7.0) fuellt es selbst; [priceTotal] sind dann die gemessenen
     * Kosten des Netzanteils.
     */
    @Json(name = "grid_energy_kwh") val gridEnergyKwh: Double? = null,
    @Json(name = "odometer_km") val odometerKm: Int? = null,
    /**
     * Aussentemperatur in Grad Celsius BEIM LADEBEGINN (Server ab 0.23.0). Der Zeitpunkt ist
     * entscheidend: der Verbrauch, den der Server diesem Vorgang zurechnet, stammt von der Fahrt
     * davor - und die endet im Moment des Einsteckens.
     */
    @Json(name = "outside_temp_c") val outsideTempC: Double? = null,
    /**
     * Herkunft der Temperatur daneben - siehe [TemperatureSource]. Bewusst NUR lesend: das Feld
     * fehlt in [ChargingSessionPayload]. Der Server leitet "manual" aus einer echten
     * Wertaenderung ab; schickte die App den alten Wert einfach zurueck, bliebe eine von Hand
     * korrigierte Temperatur faelschlich als "vom Wetterdienst" stehen.
     */
    @Json(name = "outside_temp_source") val outsideTempSource: String? = null,
    @Json(name = "price_total") val priceTotal: Double? = null,
    @Json(name = "price_per_kwh") val pricePerKwh: Double? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    @Json(name = "geocoded_place") val geocodedPlace: String? = null,
    @Json(name = "consumption_kwh_per_100km") val consumptionKwhPer100km: Double? = null,
    @Json(name = "consumption_method") val consumptionMethod: String? = null,
    val notes: String? = null,
    val source: String = "manual",
    @Json(name = "needs_review") val needsReview: Boolean = false,
    @Json(name = "external_session_id") val externalSessionId: String? = null,
    /**
     * Anteil an den Grundgebuehren/Abos des Anbieters in EUR (Server ab 0.27.0), nach kWh auf die
     * Vorgaenge der Periode umgelegt. Steckt NICHT in [priceTotal] - das bleibt der Saeulenpreis.
     * Nur lesend und lokal immer frisch berechnet ([com.dominiqueherbrigpersonalteam.lademonitor.data.repo.LocalFeeAllocator]).
     */
    @Json(name = "fee_share") val feeShare: Double? = null,
    /**
     * Uebersteuerung des Messorts der kWh (Server ab 0.29.0) - roh, `null` = folgt dem Anbieter.
     * Das vom Server zusaetzlich gelieferte `energy_meter_effective` wird bewusst nicht
     * uebernommen: gespiegelt wird nur der Rohwert, wirksam ist [effectiveEnergyMeter].
     */
    @Json(name = "energy_meter") val energyMeter: String? = null,
    /**
     * Durchschnittsgeschwindigkeit seit dem letzten Laden in km/h, aus dem Bordcomputer abgelesen
     * (Server ab 0.47.0). Beschreibt dieselbe Strecke wie der Verbrauch dieses Vorgangs und teilt
     * dessen Mehrverbrauch in Tempo und Temperatur auf ([DriveBreakdownStats]).
     */
    @Json(name = "avg_speed_kmh") val avgSpeedKmh: Double? = null
) {
    /** Uebersteuerung am Vorgang, sonst Standard des Anbieters aus [providers], sonst Ladesaeule. */
    fun effectiveEnergyMeter(providers: List<Provider>): EnergyMeter =
        EnergyMeter.effective(energyMeter, providers.firstOrNull { it.id == providerId }?.energyMeter)

    /** Saeulenpreis plus Grundgebuehranteil; `null` nur, wenn beides fehlt. */
    val effectiveTotal: Double?
        get() = if (priceTotal == null && feeShare == null) null else (priceTotal ?: 0.0) + (feeShare ?: 0.0)

    /**
     * Kosten je 100 km der Strecke seit dem vorherigen Ladevorgang: Verbrauch x effektiver Preis je
     * kWh dieses Vorgangs (Saeulenpreis plus Grundgebuehranteil, verteilt auf die geladenen kWh).
     * Live gerechnet, nichts gespeichert; `null`, wenn Verbrauch, Preis oder Energie fehlen. Gleiche
     * Regel wie `ChargingSession.costPer100km` in der iOS-App.
     */
    val costPer100km: Double?
        get() {
            val consumption = consumptionKwhPer100km ?: return null
            if (consumptionMethodValue == ConsumptionMethod.UNAVAILABLE) return null
            val energy = energyKwh?.takeIf { it > 0 } ?: return null
            val perKwh = when {
                priceTotal != null -> (priceTotal + (feeShare ?: 0.0)) / energy
                pricePerKwh != null -> pricePerKwh + (feeShare ?: 0.0) / energy
                else -> return null
            }
            return consumption * perKwh / 100
        }

    /**
     * Sonnen-/Hausakku-Anteil, sobald Energie und Netzbezug bekannt sind; sonst `null`.
     * Ein Netzbezug ueber der Energiemenge (Messungenauigkeit) zaehlt als 0 % Sonne.
     */
    val solarShare: SolarShare?
        get() {
            val energy = energyKwh ?: return null
            val grid = gridEnergyKwh ?: return null
            if (energy <= 0.0 || grid < 0.0) return null
            val gridClamped = grid.coerceAtMost(energy)
            return SolarShare(solarKwh = energy - gridClamped, gridKwh = gridClamped)
        }

    val chargingTypeValue: ChargingType? get() = ChargingType.from(chargingType)
    val sourceValue: SessionSource get() = SessionSource.from(source)
    val consumptionMethodValue: ConsumptionMethod? get() = ConsumptionMethod.from(consumptionMethod)
}

/** Aufteilung der geladenen Energie in Sonne/Hausakku und Netz (siehe [ChargingSession.solarShare]). */
data class SolarShare(val solarKwh: Double, val gridKwh: Double) {
    val totalKwh: Double get() = solarKwh + gridKwh

    /** Sonnenanteil 0..1 (0 bei 0 kWh). */
    val solarFraction: Double get() = if (totalKwh > 0.0) solarKwh / totalKwh else 0.0

    /** Sonnenanteil in ganzen Prozent; der Netzanteil ist der Rest, damit beide 100 ergeben. */
    val solarPercent: Int get() = kotlin.math.round(solarFraction * 100).toInt().coerceIn(0, 100)
    val gridPercent: Int get() = 100 - solarPercent
}

// MARK: - Payloads (create/update). Null fields are omitted by Moshi, matching the iOS
// JSONEncoder behaviour where nil optionals are dropped.

@JsonClass(generateAdapter = true)
data class VehiclePayload(
    @Json(name = "external_id") val externalId: String? = null,
    val name: String? = null,
    val brand: String? = null,
    val model: String? = null,
    @Json(name = "battery_capacity_kwh") val batteryCapacityKwh: Double? = null,
    @Json(name = "is_active") val isActive: Boolean? = null,
    /**
     * Kauf/Leasing; null = unveraendert. NICHT Teil des JSON dieser Klasse (`@Transient`): der
     * ApiClient haengt es mit allen Feldern inkl. null an, waehrend die uebrigen Felder hier wie
     * bisher ohne null gesendet werden (null hiesse dort "unveraendert", nicht "leeren").
     */
    @Transient val ownership: VehicleOwnership? = null,
    /** Dienstwagen-Angaben; null = unveraendert. Wie [ownership] vom ApiClient angehaengt. */
    @Transient val reimbursement: VehicleReimbursement? = null
)

@JsonClass(generateAdapter = true)
data class ProviderPayload(
    val name: String? = null,
    @Json(name = "last_price_ac_per_kwh") val lastPriceAcPerKwh: Double? = null,
    @Json(name = "last_price_dc_per_kwh") val lastPriceDcPerKwh: Double? = null,
    val notes: String? = null,
    /** "charger" | "vehicle"; null = unveraendert. */
    @Json(name = "energy_meter") val energyMeter: String? = null,
    /** "home" | "work" | "public"; null = unveraendert (ein Server vor 0.31.0 ignoriert es). */
    @Json(name = "charging_place") val chargingPlace: String? = null
)

@JsonClass(generateAdapter = true)
data class LocationPayload(
    val name: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    @Json(name = "radius_m") val radiusM: Int? = null,
    @Json(name = "default_provider_id") val defaultProviderId: String? = null
)

@JsonClass(generateAdapter = true)
data class ChargingSessionPayload(
    @Json(name = "vehicle_id") val vehicleId: String? = null,
    @Json(name = "provider_id") val providerId: String? = null,
    /**
     * Nur gesetzt, wenn die App die Zuordnung selbst herstellt ("Als Ladeort anlegen").
     * null wird nicht gesendet (`encode`, nicht `encodeKeepingNulls`), eine bestehende
     * Zuordnung bleibt beim normalen Speichern also stehen.
     */
    @Json(name = "location_id") val locationId: String? = null,
    @ServerDate @Json(name = "start_time") val startTime: Long? = null,
    @Json(name = "charging_type") val chargingType: String? = null,
    @Json(name = "soc_start") val socStart: Int? = null,
    @Json(name = "soc_end") val socEnd: Int? = null,
    @Json(name = "energy_kwh") val energyKwh: Double? = null,
    /** Davon aus dem Netz (kWh, Server ab 0.41.0); null wird nicht gesendet = unveraendert. */
    @Json(name = "grid_energy_kwh") val gridEnergyKwh: Double? = null,
    @Json(name = "price_per_kwh") val pricePerKwh: Double? = null,
    @Json(name = "price_total") val priceTotal: Double? = null,
    @Json(name = "odometer_km") val odometerKm: Int? = null,
    @Json(name = "outside_temp_c") val outsideTempC: Double? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    @Json(name = "geocoded_place") val geocodedPlace: String? = null,
    val notes: String? = null,
    @Json(name = "needs_review") val needsReview: Boolean? = null,
    /**
     * Messort der kWh als ausdrueckliche Wahl ("charger" | "vehicle"), null = unveraendert.
     * Bewusst immer ein Wert statt `null` fuer "folgt dem Anbieter": null wird nicht gesendet
     * (`encode`), liesse sich also nie zuruecksetzen. Der Server wie auch der lokale Speicher
     * legen eine Wahl, die dem Standard des Anbieters entspricht, selbst als null ab.
     */
    @Json(name = "energy_meter") val energyMeter: String? = null,
    /** Ø km/h seit dem letzten Laden, null = unveraendert (wird nicht gesendet). */
    @Json(name = "avg_speed_kmh") val avgSpeedKmh: Double? = null
)

// MARK: - Auth

/**
 * How often the server collects sessions that still need a review into one mail.
 * Raw values lower-case as the server sends them (`models.ReviewDigestFrequency`).
 */
enum class ReviewDigestFrequency(val raw: String, @param:StringRes val labelRes: Int) {
    @Json(name = "off")
    OFF("off", R.string.review_digest_off),

    @Json(name = "daily")
    DAILY("daily", R.string.review_digest_daily),

    @Json(name = "weekly")
    WEEKLY("weekly", R.string.review_digest_weekly);

    companion object {
        fun from(raw: String?): ReviewDigestFrequency = entries.firstOrNull { it.raw == raw } ?: OFF
    }
}

@JsonClass(generateAdapter = true)
data class AuthUser(
    val id: String,
    val username: String,
    @Json(name = "is_admin") val isAdmin: Boolean = false,

    // Server 0.14.0 and newer. ALL new fields are nullable so an updated app keeps working
    // against an older server - were they non-null, decoding the login response would already
    // fail and make signing in impossible.
    val email: String? = null,
    @Json(name = "email_verified_at") @ServerDate val emailVerifiedAt: Long? = null,
    val language: String? = null,
    @Json(name = "notify_backup_failed") val notifyBackupFailed: Boolean? = null,
    @Json(name = "notify_myskoda_error") val notifyMyskodaError: Boolean? = null,
    @Json(name = "notify_monthly_report") val notifyMonthlyReport: Boolean? = null,
    @Json(name = "notify_new_registration") val notifyNewRegistration: Boolean? = null,
    @Json(name = "review_digest") val reviewDigest: ReviewDigestFrequency? = null,
    // Server 0.36.0 und neuer (Jahresrueckblick per Mail, Standard an).
    @Json(name = "notify_yearly_review") val notifyYearlyReview: Boolean? = null
) {
    /** Whether the stored address is confirmed. Only a confirmed one can reset a password. */
    val isEmailVerified: Boolean get() = emailVerifiedAt != null

    /**
     * Whether the server knows the account features from 0.14.0 at all. An older server does not
     * send `review_digest` - the corresponding settings sections are then hidden instead of
     * offering buttons that answer with a 404.
     */
    val supportsAccountFeatures: Boolean get() = reviewDigest != null
}

@JsonClass(generateAdapter = true)
data class AuthResponse(
    val token: String,
    val user: AuthUser
)

/**
 * Request body for the login. The field is still called `username` server-side but also accepts
 * the e-mail address.
 */
@JsonClass(generateAdapter = true)
data class AuthCredentials(
    val username: String,
    val password: String
)

/**
 * Request body for the registration - like [AuthCredentials] plus an optional address. A separate
 * type on purpose: with `null` the field should not appear in the JSON at all.
 */
@JsonClass(generateAdapter = true)
data class RegisterCredentials(
    val username: String,
    val password: String,
    val email: String? = null
)

/** Body for `PUT /api/auth/password`. */
@JsonClass(generateAdapter = true)
data class PasswordChangePayload(
    @Json(name = "current_password") val currentPassword: String,
    @Json(name = "new_password") val newPassword: String
)

/**
 * Body for `PUT /api/auth/email`. `email == null` removes the address. The server demands the
 * current password: whoever can change the address can afterwards have the password reset.
 */
@JsonClass(generateAdapter = true)
data class EmailUpdatePayload(
    val email: String?,
    @Json(name = "current_password") val currentPassword: String
)

/**
 * Body for `DELETE /api/auth/me`. As with /email and /password the server demands the current
 * password - a stolen bearer token alone must not be able to destroy the account.
 */
@JsonClass(generateAdapter = true)
data class AccountDeletePayload(
    @Json(name = "current_password") val currentPassword: String
)

/** Body for `PUT /api/auth/notifications` - the server expects all fields. */
@JsonClass(generateAdapter = true)
data class NotificationSettingsPayload(
    @Json(name = "notify_backup_failed") val notifyBackupFailed: Boolean,
    @Json(name = "notify_myskoda_error") val notifyMyskodaError: Boolean,
    @Json(name = "notify_monthly_report") val notifyMonthlyReport: Boolean,
    @Json(name = "notify_new_registration") val notifyNewRegistration: Boolean,
    @Json(name = "review_digest") val reviewDigest: ReviewDigestFrequency,
    /** Ab Server 0.36.0; ein aelterer Server ignoriert das Feld. */
    @Json(name = "notify_yearly_review") val notifyYearlyReview: Boolean = true
)

/**
 * Vergleichsfahrzeug fuer die Ersparnis im Rueckblick (`GET /api/combustion-vehicle`, Server ab
 * 0.36.0). `configured == false` heisst: Standardwerte, nie gespeichert. `updated_at` braucht die
 * App nicht.
 */
@JsonClass(generateAdapter = true)
data class CombustionVehicleOut(
    @Json(name = "fuel_type") val fuelType: String = "petrol",
    @Json(name = "consumption_l_per_100km") val consumptionLPer100km: Double,
    @Json(name = "fuel_price_per_l") val fuelPricePerL: Double,
    @Json(name = "grid_co2_kg_per_kwh") val gridCo2KgPerKwh: Double,
    val configured: Boolean = false
)

/** Body fuer `PUT /api/combustion-vehicle` - der Server erwartet alle vier Felder. */
@JsonClass(generateAdapter = true)
data class CombustionVehiclePayload(
    @Json(name = "fuel_type") val fuelType: String,
    @Json(name = "consumption_l_per_100km") val consumptionLPer100km: Double,
    @Json(name = "fuel_price_per_l") val fuelPricePerL: Double,
    @Json(name = "grid_co2_kg_per_kwh") val gridCo2KgPerKwh: Double
)

/** Body for `POST /api/auth/password-reset/request`. Username OR address. */
@JsonClass(generateAdapter = true)
data class PasswordResetRequestPayload(
    val identifier: String
)

// MARK: - Geocoding

@JsonClass(generateAdapter = true)
data class GeocodeResult(
    @Json(name = "display_name") val displayName: String,
    val latitude: Double,
    val longitude: Double
)

// MARK: - Stats

@JsonClass(generateAdapter = true)
data class ProviderStat(
    @Json(name = "provider_name") val providerName: String,
    @Json(name = "total_kwh") val totalKwh: Double,
    /** Inklusive Grundgebuehren (ab Server 0.27.0). */
    @Json(name = "total_cost") val totalCost: Double,
    @Json(name = "total_fees") val totalFees: Double? = null,
    // Die folgenden Felder setzt nur LocalStatsCalculator (das Dashboard entsteht immer
    // lokal), der Server liefert sie nicht.
    val sessionCount: Int? = null,
    /** Vorgaenge ohne Preis, zaehlen mit 0 EUR (wie im Ø Preis/kWh oben). */
    val sessionsWithoutPrice: Int? = null,
    /** Ø Preis je kWh inkl. Grundgebuehren, auch der Perioden ohne Vorgang. */
    val avgPricePerKwh: Double? = null,
    // Je Lade-Art nur Saeulenpreis plus umgelegter Anteil - Perioden ohne Vorgang gehoeren
    // zu keiner Lade-Art.
    val acKwh: Double? = null,
    val acAvgPricePerKwh: Double? = null,
    val dcKwh: Double? = null,
    val dcAvgPricePerKwh: Double? = null
)

@JsonClass(generateAdapter = true)
data class MonthlyStat(
    val month: String,
    @Json(name = "total_cost") val totalCost: Double,
    @Json(name = "total_kwh") val totalKwh: Double,
    @Json(name = "session_count") val sessionCount: Int,
    @Json(name = "avg_consumption_kwh_per_100km") val avgConsumptionKwhPer100km: Double? = null,
    /** Davon Grundgebuehren/Abos (in [totalCost] enthalten). */
    @Json(name = "total_fees") val totalFees: Double? = null
) {
    /**
     * "YYYY-MM" -> "August 2026" (or "August 2026" -> "August 2026" in English), matching the
     * iOS displayMonth. Uses the device's current locale so it follows the system language
     * automatically, the same way the rest of the app's localized strings do.
     */
    val displayMonth: String
        get() {
            val parsed = runCatching { java.time.YearMonth.parse(month) }.getOrNull() ?: return month
            val formatter = java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy", java.util.Locale.getDefault())
            return parsed.format(formatter)
        }

    /** "YYYY-MM" -> "Aug '26" for chart axes, localized to the device's current locale. */
    val shortMonth: String
        get() {
            val parsed = runCatching { java.time.YearMonth.parse(month) }.getOrNull() ?: return month
            val formatter = java.time.format.DateTimeFormatter.ofPattern("MMM", java.util.Locale.getDefault())
            val yearSuffix = (parsed.year % 100).toString().padStart(2, '0')
            return "${parsed.format(formatter)} '$yearSuffix"
        }
}

@JsonClass(generateAdapter = true)
data class StatsSummary(
    @Json(name = "total_sessions") val totalSessions: Int,
    @Json(name = "total_kwh") val totalKwh: Double,
    @Json(name = "total_cost") val totalCost: Double,
    @Json(name = "avg_price_per_kwh") val avgPricePerKwh: Double? = null,
    @Json(name = "avg_consumption_kwh_per_100km") val avgConsumptionKwhPer100km: Double? = null,
    @Json(name = "price_per_100km") val pricePer100km: Double? = null,
    @Json(name = "ac_share_pct") val acSharePct: Double? = null,
    @Json(name = "dc_share_pct") val dcSharePct: Double? = null,
    @Json(name = "ac_kwh") val acKwh: Double? = null,
    @Json(name = "dc_kwh") val dcKwh: Double? = null,
    @Json(name = "total_km_driven") val totalKmDriven: Int? = null,
    @Json(name = "by_provider") val byProvider: List<ProviderStat> = emptyList(),
    val monthly: List<MonthlyStat> = emptyList(),
    /**
     * Grundgebuehren/Abos im Zeitraum, in [totalCost], [avgPricePerKwh] und [pricePer100km]
     * bereits enthalten (Server ab 0.27.0).
     */
    @Json(name = "total_fees") val totalFees: Double? = null,
    /** Davon Perioden, in denen es keinen Ladevorgang des Anbieters gab. */
    @Json(name = "unallocated_fees") val unallocatedFees: Double? = null,
    /**
     * Aufteilung nach Zuhause/Arbeit/unterwegs (Server ab 0.31.0), Reihenfolge HOME, WORK,
     * PUBLIC. Leer, solange kein Vorgang zu Hause oder bei der Arbeit lag.
     */
    @Json(name = "by_place") val byPlace: List<PlaceStat> = emptyList(),
    /**
     * Gesamtkosten inkl. sonstiger Kosten und Wertverlust/Leasing (Server ab 0.32.0). null,
     * solange keines der betrachteten Fahrzeuge Kosten oder eine Kauf-/Leasingangabe hat - dann
     * fehlt der Abschnitt im Dashboard.
     */
    @Json(name = "vehicle_costs") val vehicleCosts: VehicleCostSummary? = null,
    /**
     * Sonne/Hausakku gegen Netz, summiert ueber die Vorgaenge mit bekanntem Netzbezug
     * ([ChargingSession.solarShare]); null, solange keiner einen hat. Nur lokal gerechnet
     * ([com.dominiqueherbrigpersonalteam.lademonitor.data.repo.LocalStatsCalculator]), deshalb
     * nicht Teil des JSON (`@Transient`, wie [VehiclePayload.ownership]).
     */
    @Transient val solarShare: SolarShare? = null
)

@JsonClass(generateAdapter = true)
data class PlaceStat(
    /** "home" | "work" | "public", siehe [ChargingPlace]. */
    val place: String,
    @Json(name = "session_count") val sessionCount: Int,
    @Json(name = "total_kwh") val totalKwh: Double,
    /** Inklusive Grundgebuehren. */
    @Json(name = "total_cost") val totalCost: Double,
    @Json(name = "avg_price_per_kwh") val avgPricePerKwh: Double? = null
) {
    val placeValue: ChargingPlace get() = ChargingPlace.from(place)
}

// ---------- Sync: serverseitig geloeschte Datensaetze ----------

/**
 * Eine Loeschung, die auf dem Server stattgefunden hat (Web-UI, zweites Geraet, ein anderer
 * Client). Gegenstueck zu `models.DeletedRecord` im Backend — siehe
 * `SyncService.applyServerDeletions()` fuer das Warum.
 */
@JsonClass(generateAdapter = true)
data class DeletedRecord(
    @Json(name = "entity_type") val entityType: String,
    @Json(name = "entity_id") val entityId: String
)

@JsonClass(generateAdapter = true)
data class DeletionsResponse(
    /**
     * Cursor fuer den naechsten Abruf. Bewusst ein ROHER STRING und kein Zeitstempel: der
     * Server schickt naive UTC-Werte, die der [ServerDate]-Adapter dieser App als lokale Zeit
     * liest — hin- und zurueckgewandelt waere der Cursor um den Zeitzonen-Offset verschoben
     * und wuerde Loeschungen ueberspringen. Unveraendert zurueckgegeben kann das nicht
     * passieren.
     */
    @Json(name = "server_time") val serverTime: String,
    val deletions: List<DeletedRecord> = emptyList()
)

// ---------- Verbrauch nach Aussentemperatur ----------

/**
 * Antwort von `GET /api/stats/drive-breakdown` (Server ab 0.47.0): Mehrverbrauch je Fahrt nach
 * Tempo und Temperatur. Wie die Temperaturauswertung nur im Server-Modus - das Modell
 * (`drive_breakdown.py`) wird je Fahrzeug aus allen Fahrten mit Tempo angepasst.
 */
@JsonClass(generateAdapter = true)
data class DriveBreakdownStats(
    @Json(name = "vehicle_id") val vehicleId: String? = null,
    @Json(name = "needs_vehicle") val needsVehicle: Boolean = false,
    val model: DriveBreakdownModel? = null,
    val drives: List<DriveBreakdownPoint> = emptyList(),
    @Json(name = "drives_without_speed") val drivesWithoutSpeed: Int = 0,
    @Json(name = "min_drives") val minDrives: Int = 5
)

@JsonClass(generateAdapter = true)
data class DriveBreakdownModel(
    /** Verbrauch bei Bezugstempo ohne Heizen/Kuehlen - "dein Normal". */
    @Json(name = "normal_consumption") val normalConsumption: Double,
    @Json(name = "reference_speed_kmh") val referenceSpeedKmh: Double,
    @Json(name = "drive_count") val driveCount: Int
)

/** Eine Fahrt: base + speed + temperature + rest = Verbrauch. */
@JsonClass(generateAdapter = true)
data class DriveBreakdownPoint(
    @Json(name = "session_id") val sessionId: String,
    val base: Double,
    val speed: Double,
    val temperature: Double,
    val rest: Double
)

/**
 * Antwort von `GET /api/stats/temperature`.
 *
 * Wird bewusst NICHT lokal nachgerechnet (anders als [StatsSummary], die im Local-Only-Modus
 * aus `LocalStatsCalculator` kommt): die Auswertung in `temperature.py` haengt an der
 * Verbrauchskette, den km-Gewichten und den Schwellen fuer die Ausgleichsgerade — ein dritter
 * Nachbau davon (nach `LocalConsumptionCalculator`) wuerde frueher oder spaeter andere Zahlen
 * zeigen als das Web-Dashboard. Die Ansicht gibt es deshalb nur im Server-Modus.
 */
@JsonClass(generateAdapter = true)
data class TemperatureStats(
    val points: List<TempPoint> = emptyList(),
    val buckets: List<TempBucket> = emptyList(),
    val seasons: List<SeasonStat> = emptyList(),
    /** `null`, wenn zu wenige Fahrten oder ein zu schmaler Temperaturbereich vorliegen. */
    val trend: TempTrend? = null,
    /** Vorgaenge mit berechenbarem Verbrauch, aber ohne Temperatur. */
    @Json(name = "sessions_without_temp") val sessionsWithoutTemp: Int = 0,
    @Json(name = "bucket_width_c") val bucketWidthC: Int = 5,
    /**
     * Reale Reichweite je Temperatur (Server ab 0.34.0, `range_estimate.py`). `null` bei einem
     * aelteren Server, ohne Akkukapazitaet oder wenn nicht genau EIN Fahrzeug betrachtet wird.
     */
    val range: RangeEstimate? = null
)

/**
 * Reichweite 100 -> 0 % = Nennkapazitaet / Verbrauch AB AKKU. Der Ladeverlust ist bereits
 * herausgerechnet, wenn [lossPct] bekannt ist.
 */
@JsonClass(generateAdapter = true)
data class RangeEstimate(
    @Json(name = "vehicle_id") val vehicleId: String? = null,
    @Json(name = "capacity_kwh") val capacityKwh: Double,
    @Json(name = "loss_pct") val lossPct: Double? = null,
    @Json(name = "measured_from_c") val measuredFromC: Double? = null,
    @Json(name = "measured_to_c") val measuredToC: Double? = null,
    /** 5-Grad-Schritte im gemessenen Bereich plus immer 0 und 20 Grad. */
    val rows: List<RangeRow> = emptyList(),
    /** Verbrauch ab Akku je ganzem Grad -20..40 (an den Raendern ggf. lueckenhaft). */
    val curve: List<RangeCurvePoint> = emptyList()
)

@JsonClass(generateAdapter = true)
data class RangeRow(
    @Json(name = "temp_c") val tempC: Double,
    @Json(name = "consumption_kwh_per_100km") val consumptionKwhPer100km: Double? = null,
    @Json(name = "range_km") val rangeKm: Double? = null,
    /** Ausserhalb der gemessenen Temperaturen - eine Hochrechnung. */
    val extrapolated: Boolean = false
)

@JsonClass(generateAdapter = true)
data class RangeCurvePoint(
    @Json(name = "temp_c") val tempC: Double,
    val consumption: Double
)

@JsonClass(generateAdapter = true)
data class TempPoint(
    @Json(name = "session_id") val sessionId: String,
    @ServerDate @Json(name = "start_time") val startTime: Long,
    @Json(name = "temp_c") val tempC: Double,
    @Json(name = "consumption_kwh_per_100km") val consumptionKwhPer100km: Double,
    val km: Double,
    @Json(name = "consumption_method") val consumptionMethod: String,
    val season: String
)

@JsonClass(generateAdapter = true)
data class TempBucket(
    @Json(name = "from_c") val fromC: Double,
    @Json(name = "to_c") val toC: Double,
    @Json(name = "avg_consumption_kwh_per_100km") val avgConsumptionKwhPer100km: Double,
    @Json(name = "session_count") val sessionCount: Int,
    val km: Double
) {
    /** Mitte der Klasse — der x-Wert, an dem der Klassenmittelwert im Streudiagramm sitzt. */
    val centerC: Double get() = (fromC + toC) / 2
}

@JsonClass(generateAdapter = true)
data class SeasonStat(
    /** winter | spring | summer | autumn */
    val season: String,
    @Json(name = "avg_consumption_kwh_per_100km") val avgConsumptionKwhPer100km: Double,
    @Json(name = "session_count") val sessionCount: Int,
    val km: Double
) {
    /**
     * Uebersetzter Name. Die Rohwerte sind die englischen Schluessel aus
     * `temperature.py::SEASONS` und bleiben unangetastet (sie sind Daten, keine Anzeige).
     */
    val displayName: String
        get() {
            val context = LademonitorApp.appContext
            return when (season) {
                "winter" -> context.getString(R.string.season_winter)
                "spring" -> context.getString(R.string.season_spring)
                "summer" -> context.getString(R.string.season_summer)
                "autumn" -> context.getString(R.string.season_autumn)
                else -> season
            }
        }
}

/** Ein Eckpunkt der Ausgleichskurve - die Punkte ergeben einen Streckenzug. */
@JsonClass(generateAdapter = true)
data class TempCurveVertex(
    @Json(name = "temp_c") val tempC: Double,
    val consumption: Double
)

@JsonClass(generateAdapter = true)
data class TempTrend(
    /**
     * Beschreiben IMMER die einfache Ausgleichsgerade, auch beim Knickmodell -
     * deshalb bleiben sie der Rueckfallweg fuer [curve] (aelterer Server).
     */
    val slope: Double,
    val intercept: Double,
    /**
     * Wieviel der Streuung die Temperatur ueberhaupt erklaert (0..1). Gehoert
     * zu dem Modell, das der Server tatsaechlich benutzt hat.
     */
    val r2: Double,
    @Json(name = "consumption_at_0c") val consumptionAt0c: Double,
    @Json(name = "consumption_at_20c") val consumptionAt20c: Double,
    @Json(name = "extra_pct_at_0c") val extraPctAt0c: Double,
    /**
     * "linear" oder "breakpoint" (Server ab 0.25.0). Der Verbrauch ueber der
     * Temperatur ist eine Wanne: unterhalb der Komfortgrenze heizt das
     * Fahrzeug, oberhalb kuehlt es. Hat der Server dafuer genug Daten, legt er
     * je eine Gerade nach kalt und nach warm. Default fuer aeltere Server.
     */
    val model: String? = null,
    /**
     * Streckenzug des benutzten Modells: zwei Eckpunkte bei der Geraden, drei
     * beim Knickmodell. Nur ueber den gemessenen Bereich.
     */
    val curve: List<TempCurveVertex>? = null,
    /** Temperatur des geringsten Verbrauchs (nur beim Knickmodell). */
    @Json(name = "breakpoint_c") val breakpointC: Double? = null,
    @Json(name = "slope_cold") val slopeCold: Double? = null,
    @Json(name = "slope_warm") val slopeWarm: Double? = null,
    /**
     * True, wenn 0 °C unter der kaeltesten gemessenen Fahrt liegt - dann ist
     * die Kennzahl eine Hochrechnung, keine Messung.
     */
    @Json(name = "at_0c_is_extrapolated") val at0cIsExtrapolated: Boolean = false
)

// ---------- Reifen (Server ab 0.26.1 fuer die zweite Groesse) ----------
//
// Bewusst NUR im Server-Modus: die Zuordnung der Fahrten zu den Saetzen und
// der temperaturbereinigte Vergleich liegen komplett in `tires.py`. Sie lokal
// nachzubauen hiesse, die Verbrauchskurve aus `temperature.py` ein zweites Mal
// zu implementieren - mit der Aussicht, dass App und Web frueher oder spaeter
// andere Zahlen zeigen. Dieselbe Entscheidung wie bei der Temperaturauswertung
// auf dem Dashboard. Reifen sind deshalb auch nicht Teil des Room-Spiegels.

enum class TireKind(val wire: String) {
    SUMMER("summer"),
    WINTER("winter"),
    ALL_SEASON("all_season");

    companion object {
        fun from(wire: String?): TireKind = entries.firstOrNull { it.wire == wire } ?: SUMMER
    }
}

/**
 * Eine MONTAGE, kein physischer Satz: derselbe Reifen im Herbst wieder
 * aufgezogen ist ein zweiter Eintrag. Ein Enddatum gibt es deshalb nicht -
 * der naechste Wechsel beendet den vorherigen Satz.
 */
@JsonClass(generateAdapter = true)
data class TireSet(
    val id: String,
    @Json(name = "vehicle_id") val vehicleId: String,
    val kind: String,
    @ServerDate @Json(name = "installed_on") val installedOn: Long,
    /**
     * Kilometerstand beim Wechsel. Optional, aber die genauere Quelle fuer die
     * Laufleistung: die Differenz zweier Wechsel enthaelt auch die Fahrt, die
     * ueber den Wechsel hinweg lief und keinem Satz zugeordnet werden kann.
     */
    @Json(name = "odometer_km") val odometerKm: Double? = null,
    /**
     * Groesse aller vier Raeder - oder, wenn [sizeRear] gesetzt ist, die der
     * Vorderachse (Mischbereifung).
     */
    val size: String? = null,
    @Json(name = "size_rear") val sizeRear: String? = null,
    val brand: String? = null,
    val model: String? = null,
    val notes: String? = null,
    /**
     * DOT-Datumscode (Woche + Jahr, "2323") je Achse, ab Server 0.30.0 -
     * gegen einen aelteren Server einfach null.
     */
    val dot: String? = null,
    @Json(name = "dot_rear") val dotRear: String? = null
) {
    val tireKind: TireKind get() = TireKind.from(kind)

    /**
     * Bei Mischbereifung beide Achsen ("vorne / hinten"), sonst die eine
     * Groesse - dieselbe Regel wie serverseitig in `tires.py::size_label()`.
     */
    val sizeLabel: String
        get() = if (!size.isNullOrBlank() && !sizeRear.isNullOrBlank()) "$size / $sizeRear"
        else listOfNotNull(size, sizeRear).firstOrNull { it.isNotBlank() } ?: ""

    /** Marke, Modell und Groesse in einer Zeile - leere Felder fallen weg. */
    val label: String
        get() = listOfNotNull(brand, model, sizeLabel).filter { it.isNotBlank() }.joinToString(" ")
}

/**
 * Leere Textfelder werden als `null` GESCHICKT, nicht weggelassen: der Server
 * wendet `exclude_unset` an, ein fehlendes Feld liesse den alten Wert stehen -
 * eine geleerte Marke waere also nicht zu loeschen. Das Fahrzeug faellt beim
 * Bearbeiten weg (dort kennt das Schema es nicht, es bleibt unveraenderlich).
 */
@JsonClass(generateAdapter = true)
data class TireSetPayload(
    @Json(name = "vehicle_id") val vehicleId: String? = null,
    val kind: String,
    @ServerDate @Json(name = "installed_on") val installedOn: Long,
    @Json(name = "odometer_km") val odometerKm: Double? = null,
    val size: String? = null,
    @Json(name = "size_rear") val sizeRear: String? = null,
    val brand: String? = null,
    val model: String? = null,
    val notes: String? = null,
    val dot: String? = null,
    @Json(name = "dot_rear") val dotRear: String? = null,
    /**
     * Nur beim Anlegen: Profiltiefe des aufgezogenen und des abgenommenen
     * Satzes (die Montage davor an diesem Fahrzeug). Der Server legt daraus je
     * eine Messung mit Datum und Kilometerstand des Wechsels an; beim
     * Bearbeiten ignoriert er die Felder.
     */
    val tread: TreadInput? = null,
    @Json(name = "removed_tread") val removedTread: TreadInput? = null
)

/** Profiltiefe in mm: ein Gesamtwert und/oder die vier Raeder. Gespeichert wird immer der geringste. */
@JsonClass(generateAdapter = true)
data class TreadInput(
    @Json(name = "depth_mm") val depthMm: Double? = null,
    @Json(name = "front_left_mm") val frontLeftMm: Double? = null,
    @Json(name = "front_right_mm") val frontRightMm: Double? = null,
    @Json(name = "rear_left_mm") val rearLeftMm: Double? = null,
    @Json(name = "rear_right_mm") val rearRightMm: Double? = null
) {
    val isEmpty: Boolean
        get() = listOf(depthMm, frontLeftMm, frontRightMm, rearLeftMm, rearRightMm).all { it == null }
}

@JsonClass(generateAdapter = true)
data class TreadMeasurementPayload(
    @ServerDate @Json(name = "measured_on") val measuredOn: Long,
    @Json(name = "odometer_km") val odometerKm: Double? = null,
    @Json(name = "depth_mm") val depthMm: Double? = null,
    @Json(name = "front_left_mm") val frontLeftMm: Double? = null,
    @Json(name = "front_right_mm") val frontRightMm: Double? = null,
    @Json(name = "rear_left_mm") val rearLeftMm: Double? = null,
    @Json(name = "rear_right_mm") val rearRightMm: Double? = null
)

/** Eine Profilmessung an einer Montage (Server ab 0.30.0). */
@JsonClass(generateAdapter = true)
data class TireTreadMeasurement(
    val id: String,
    @Json(name = "tire_set_id") val tireSetId: String,
    @ServerDate @Json(name = "measured_on") val measuredOn: Long,
    @Json(name = "odometer_km") val odometerKm: Double? = null,
    /** Immer die geringste Tiefe - der Wert fuer Mindestprofil und Austausch. */
    @Json(name = "depth_mm") val depthMm: Double,
    @Json(name = "front_left_mm") val frontLeftMm: Double? = null,
    @Json(name = "front_right_mm") val frontRightMm: Double? = null,
    @Json(name = "rear_left_mm") val rearLeftMm: Double? = null,
    @Json(name = "rear_right_mm") val rearRightMm: Double? = null
) {
    val wheels: List<Double?> get() = listOf(frontLeftMm, frontRightMm, rearLeftMm, rearRightMm)
}

/**
 * Kennzahlen einer einzelnen Montage. [removedOn] leitet der Server ab (der
 * naechste Wechsel an diesem Fahrzeug) - null heisst "liegt noch drauf".
 */
@JsonClass(generateAdapter = true)
data class TireMounting(
    @Json(name = "tire_set_id") val tireSetId: String,
    @Json(name = "vehicle_id") val vehicleId: String,
    val kind: String,
    val label: String,
    @ServerDate @Json(name = "installed_on") val installedOn: Long,
    @ServerDate @Json(name = "removed_on") val removedOn: Long? = null,
    @Json(name = "is_current") val isCurrent: Boolean = false,
    val days: Int = 0,
    val drives: Int = 0,
    val km: Double = 0.0,
    /**
     * "odometer" (Differenz der Kilometerstaende, exakt) oder "drives" (Summe
     * der zugeordneten Fahrten - die Fahrt ueber den Wechsel fehlt dort).
     */
    @Json(name = "km_source") val kmSource: String? = null,
    @Json(name = "energy_kwh") val energyKwh: Double = 0.0,
    @Json(name = "avg_consumption_kwh_per_100km") val avgConsumptionKwhPer100km: Double? = null,
    /** Juengste Profilmessung dieser Montage; Status "ok" | "low" | "legal_min" (Schwellen in tires.py). */
    @Json(name = "tread_depth_mm") val treadDepthMm: Double? = null,
    @Json(name = "tread_status") val treadStatus: String? = null
) {
    val tireKind: TireKind get() = TireKind.from(kind)
    val kmIsExact: Boolean get() = kmSource == "odometer"
}

/** Ein Satz ueber alle seine Montagen hinweg - erst so ergibt "wieviel km sind da drauf" eine Zahl. */
@JsonClass(generateAdapter = true)
data class TireSetSummary(
    val key: String,
    val label: String,
    val kind: String,
    val mountings: Int = 0,
    @ServerDate @Json(name = "first_installed_on") val firstInstalledOn: Long,
    /**
     * Alter seit der ersten Montage. Steht neben [daysMounted], weil Gummi
     * auch im Keller altert, die Laufleistung aber nicht.
     */
    @Json(name = "age_days") val ageDays: Int = 0,
    @Json(name = "days_mounted") val daysMounted: Int = 0,
    val drives: Int = 0,
    val km: Double = 0.0,
    /** "odometer" nur, wenn JEDE Montage dieses Satzes gemessene Kilometer hat. */
    @Json(name = "km_source") val kmSource: String? = null,
    @Json(name = "energy_kwh") val energyKwh: Double = 0.0,
    @Json(name = "is_current") val isCurrent: Boolean = false,
    @Json(name = "avg_consumption_kwh_per_100km") val avgConsumptionKwhPer100km: Double? = null,
    /**
     * Aus der DOT (aeltere Achse): Produktionsdatum als "YYYY-MM-DD" (reines
     * Datum, deshalb String), Alter ab da und "ok" | "check" (ab 6 Jahren) |
     * "replace" (ab 10). null ohne DOT oder bei aelterem Server.
     */
    @Json(name = "produced_on") val producedOn: String? = null,
    @Json(name = "production_age_days") val productionAgeDays: Int? = null,
    @Json(name = "age_status") val ageStatus: String? = null,
    @Json(name = "tread_depth_mm") val treadDepthMm: Double? = null,
    @Json(name = "tread_status") val treadStatus: String? = null
) {
    val tireKind: TireKind get() = TireKind.from(kind)
    val kmIsExact: Boolean get() = kmSource == "odometer"

    /** "03/2024" - so, wie man es mit der DOT auf dem Reifen vergleicht. */
    val producedLabel: String?
        get() = producedOn?.split("-")?.takeIf { it.size >= 2 }?.let { "${it[1]}/${it[0]}" }
}

@JsonClass(generateAdapter = true)
data class TireOverview(
    val mountings: List<TireMounting> = emptyList(),
    val sets: List<TireSetSummary> = emptyList(),
    @Json(name = "drives_without_set") val drivesWithoutSet: Int = 0,
    @Json(name = "drives_spanning_change") val drivesSpanningChange: Int = 0
)

@JsonClass(generateAdapter = true)
data class TireGroup(
    val key: String,
    val label: String,
    val kind: String,
    val drives: Int = 0,
    val km: Double = 0.0,
    @Json(name = "avg_consumption_kwh_per_100km") val avgConsumptionKwhPer100km: Double = 0.0,
    @Json(name = "avg_temp_c") val avgTempC: Double = 0.0,
    @Json(name = "min_temp_c") val minTempC: Double = 0.0,
    @Json(name = "max_temp_c") val maxTempC: Double = 0.0,
    /**
     * Erst dieser Wert ist zwischen den Gruppen vergleichbar: der rohe
     * Durchschnitt misst vor allem, bei welchen Temperaturen gefahren wurde.
     */
    @Json(name = "adjusted_consumption_kwh_per_100km") val adjustedConsumptionKwhPer100km: Double? = null,
    @Json(name = "delta_pct_vs_model") val deltaPctVsModel: Double? = null
) {
    val tireKind: TireKind get() = TireKind.from(kind)
}

@JsonClass(generateAdapter = true)
data class TireComparison(
    @Json(name = "by_kind") val byKind: List<TireGroup> = emptyList(),
    @Json(name = "by_set") val bySet: List<TireGroup> = emptyList(),
    @Json(name = "reference_temp_c") val referenceTempC: Double? = null,
    val r2: Double? = null,
    @Json(name = "drives_without_set") val drivesWithoutSet: Int = 0,
    @Json(name = "drives_spanning_change") val drivesSpanningChange: Int = 0,
    /**
     * Gemeinsamer Temperaturbereich der Arten. Ohne ihn rechnet das Modell
     * jeden Satz in Temperaturen hoch, in denen er nie gefahren ist - die
     * Ansicht sagt das dann auch, statt die Zahl unkommentiert zu zeigen.
     */
    @Json(name = "overlap_span_c") val overlapSpanC: Double? = null,
    @Json(name = "overlap_ok") val overlapOk: Boolean = false,
    @Json(name = "winter_vs_summer_pct") val winterVsSummerPct: Double? = null
)

// ---------- Grundgebuehren / Abos der Anbieter (Server ab 0.27.0) ----------

enum class FeeInterval(val wire: String, @param:StringRes val labelRes: Int, @param:StringRes val perRes: Int) {
    MONTHLY("monthly", R.string.fee_interval_monthly, R.string.fee_per_month),
    YEARLY("yearly", R.string.fee_interval_yearly, R.string.fee_per_year),
    ONCE("once", R.string.fee_interval_once, R.string.fee_per_once);

    companion object {
        fun from(wire: String?): FeeInterval = entries.firstOrNull { it.wire == wire } ?: MONTHLY
    }
}

/**
 * Grundgebuehr bzw. Abo eines Anbieters, z.B. Ionity Powerpass 15 EUR im Monat.
 *
 * Haengt am Anbieter, nicht am Ladevorgang: umgelegt wird erst beim Anzeigen, nach kWh auf alle
 * Ladevorgaenge des Anbieters in der Periode (siehe LocalFeeAllocator). [startDate]/[endDate]
 * sind reine Kalendertage (lokale Mitternacht) - der Server verwirft jede Uhrzeit.
 */
@JsonClass(generateAdapter = true)
data class ProviderFee(
    val id: String,
    @Json(name = "provider_id") val providerId: String,
    val amount: Double,
    val interval: String = FeeInterval.MONTHLY.wire,
    @ServerDate @Json(name = "start_date") val startDate: Long,
    /** Letzter Tag, an dem die Gebuehr gilt (inklusive). Bei "once" Pflicht, sonst Kuendigung. */
    @ServerDate @Json(name = "end_date") val endDate: Long? = null,
    val label: String? = null,
    val notes: String? = null
) {
    val intervalValue: FeeInterval get() = FeeInterval.from(interval)
}

/**
 * Payload fuer POST/PATCH /api/provider-fees - wird mit `encodeKeepingNulls` geschickt: sonst
 * liesse sich ein einmal gesetztes Enddatum (Kuendigung) per PATCH nie wieder entfernen, der
 * Server uebernimmt nur Felder, die im JSON stehen.
 */
@JsonClass(generateAdapter = true)
data class ProviderFeePayload(
    @Json(name = "provider_id") val providerId: String,
    val amount: Double,
    val interval: String,
    @ServerDate @Json(name = "start_date") val startDate: Long,
    @ServerDate @Json(name = "end_date") val endDate: Long? = null,
    val label: String? = null,
    val notes: String? = null
)

// ---------- Fahrzeugkosten (Server ab 0.32.0) ----------

/**
 * Feste Kategorienliste der sonstigen Fahrzeugkosten, wie `vehicle_costs.CATEGORIES` im Server.
 * Unbekannte Werte gelten als [OTHER].
 */
enum class CostCategory(val raw: String, @param:StringRes val labelRes: Int) {
    INSURANCE("insurance", R.string.cost_category_insurance),
    TAX("tax", R.string.cost_category_tax),
    MAINTENANCE("maintenance", R.string.cost_category_maintenance),
    REPAIR("repair", R.string.cost_category_repair),
    TIRES("tires", R.string.cost_category_tires),
    WALLBOX("wallbox", R.string.cost_category_wallbox),
    PARKING("parking", R.string.cost_category_parking),
    TOLL("toll", R.string.cost_category_toll),
    CLEANING("cleaning", R.string.cost_category_cleaning),
    OTHER("other", R.string.cost_category_other);

    companion object {
        fun from(raw: String?): CostCategory = entries.firstOrNull { it.raw == raw } ?: OTHER

        /** Anzeigename auch fuer die Auswertungs-Kategorien "charging", "depreciation", "leasing". */
        @StringRes
        fun labelResFor(raw: String): Int = when (raw) {
            "charging" -> R.string.cost_category_charging
            "depreciation" -> R.string.cost_category_depreciation
            "leasing" -> R.string.cost_category_leasing
            else -> from(raw).labelRes
        }
    }
}

/**
 * Sonstige Kosten eines Fahrzeugs (Versicherung, Steuer, Wartung ...). Einmalig, monatlich oder
 * jaehrlich; ein Jahresbetrag zaehlt in dem Monat, in dem er faellig ist (siehe
 * LocalVehicleCostCalculator). [startDate]/[endDate] sind reine Kalendertage, [endDate] ist der
 * letzte Tag, an dem noch gezahlt wird (nur bei wiederkehrenden).
 */
@JsonClass(generateAdapter = true)
data class VehicleCost(
    val id: String,
    @Json(name = "vehicle_id") val vehicleId: String,
    val amount: Double,
    val category: String = CostCategory.OTHER.raw,
    val interval: String = FeeInterval.ONCE.wire,
    @ServerDate @Json(name = "start_date") val startDate: Long,
    @ServerDate @Json(name = "end_date") val endDate: Long? = null,
    @Json(name = "odometer_km") val odometerKm: Int? = null,
    val label: String? = null,
    val notes: String? = null
) {
    val categoryValue: CostCategory get() = CostCategory.from(category)

    /** Wie der Server: alles ausser monatlich/jaehrlich ist eine einzelne Zahlung. */
    val intervalValue: FeeInterval
        get() = when (interval) {
            FeeInterval.MONTHLY.wire -> FeeInterval.MONTHLY
            FeeInterval.YEARLY.wire -> FeeInterval.YEARLY
            else -> FeeInterval.ONCE
        }
}

/**
 * Payload fuer POST/PATCH /api/vehicle-costs - mit `encodeKeepingNulls` gesendet, damit sich
 * Enddatum, km-Stand, Bezeichnung und Notiz per PATCH wieder leeren lassen.
 */
@JsonClass(generateAdapter = true)
data class VehicleCostPayload(
    @Json(name = "vehicle_id") val vehicleId: String,
    val amount: Double,
    val category: String,
    val interval: String,
    @ServerDate @Json(name = "start_date") val startDate: Long,
    @ServerDate @Json(name = "end_date") val endDate: Long? = null,
    @Json(name = "odometer_km") val odometerKm: Int? = null,
    val label: String? = null,
    val notes: String? = null
)

/** Ein Posten der Gesamtkosten: "charging" | "depreciation" | "leasing" | eine [CostCategory]. */
@JsonClass(generateAdapter = true)
data class CostCategoryStat(
    val category: String,
    val amount: Double
)

/**
 * Gesamtkosten im Zeitraum (Dashboard-Abschnitt "Gesamtkosten", Server ab 0.32.0) - lokal
 * gerechnet von LocalVehicleCostCalculator. Die uebrigen Kennzahlen der [StatsSummary] bleiben
 * reine Ladekosten.
 */
@JsonClass(generateAdapter = true)
data class VehicleCostSummary(
    @Json(name = "charging_cost") val chargingCost: Double,
    @Json(name = "other_cost") val otherCost: Double,
    /** Wertverlust bzw. Leasing. */
    @Json(name = "ownership_cost") val ownershipCost: Double,
    @Json(name = "total_cost") val totalCost: Double,
    @Json(name = "cost_per_km") val costPerKm: Double? = null,
    /** Groesster Posten zuerst. */
    @Json(name = "by_category") val byCategory: List<CostCategoryStat> = emptyList()
)

/** Kosten eines Kalenderjahres je Kategorie (Kostenliste eines Fahrzeugs). */
data class YearCostStat(
    val year: Int,
    val total: Double,
    val kmDriven: Int?,
    val costPerKm: Double?,
    val byCategory: List<CostCategoryStat>
)

// ---------- Erstattung Dienstwagen (Server ab 0.35.0) ----------

/**
 * Erstattungssatz je kWh eines Dienstwagens, gueltig ab [validFrom] (reiner Kalendertag, lokale
 * Mitternacht) bis zum naechsten Satz. Vor dem ersten Satz gilt keiner (siehe
 * [com.dominiqueherbrigpersonalteam.lademonitor.data.repo.LocalReimbursementCalculator]).
 */
@JsonClass(generateAdapter = true)
data class ReimbursementRate(
    val id: String,
    @Json(name = "vehicle_id") val vehicleId: String,
    @ServerDate @Json(name = "valid_from") val validFrom: Long,
    @Json(name = "rate_per_kwh") val ratePerKwh: Double
)

/** Payload fuer POST/PATCH /api/reimbursement/rates - alle Felder sind Pflicht. */
@JsonClass(generateAdapter = true)
data class ReimbursementRatePayload(
    @Json(name = "vehicle_id") val vehicleId: String,
    @ServerDate @Json(name = "valid_from") val validFrom: Long,
    @Json(name = "rate_per_kwh") val ratePerKwh: Double
)

// ---------- Erinnerungen (Server ab 0.33.0) ----------

/**
 * Art einer Erinnerung, wie `reminders.KINDS` im Server. Unbekannte Werte gelten als [CUSTOM].
 * [repeatMonths]/[repeatKm] sind die Vorlagenwerte beim Anlegen (alle aenderbar), [costCategory]
 * die Kostenkategorie, die "Erledigt" vorschlaegt (THG ist eine Einnahme - keine).
 */
enum class ReminderKind(
    val raw: String,
    @param:StringRes val labelRes: Int,
    val repeatMonths: Int?,
    val repeatKm: Int?,
    val costCategory: CostCategory?
) {
    HU("hu", R.string.reminder_kind_hu, 24, null, CostCategory.MAINTENANCE),
    INSPECTION("inspection", R.string.reminder_kind_inspection, 12, 30000, CostCategory.MAINTENANCE),
    BRAKE_FLUID("brake_fluid", R.string.reminder_kind_brake_fluid, 24, null, CostCategory.MAINTENANCE),
    TIRE_WINTER("tire_winter", R.string.reminder_kind_tire_winter, 12, null, CostCategory.TIRES),
    TIRE_SUMMER("tire_summer", R.string.reminder_kind_tire_summer, 12, null, CostCategory.TIRES),
    INSURANCE("insurance", R.string.reminder_kind_insurance, 12, null, CostCategory.INSURANCE),
    TAX("tax", R.string.reminder_kind_tax, 12, null, CostCategory.TAX),
    THG("thg", R.string.reminder_kind_thg, 12, null, null),
    CUSTOM("custom", R.string.reminder_kind_custom, null, null, CostCategory.OTHER);

    companion object {
        fun from(raw: String?): ReminderKind = entries.firstOrNull { it.raw == raw } ?: CUSTOM
    }
}

/** Status einer Erinnerung, wie `ReminderStatus` im Server; gerechnet von LocalReminderCalculator. */
enum class ReminderStatus(val raw: String, val rank: Int, @param:StringRes val labelRes: Int) {
    OK("ok", 0, R.string.reminder_status_ok),
    DONE("done", 0, R.string.reminder_status_done),
    DUE_SOON("due_soon", 1, R.string.reminder_status_due_soon),
    OVERDUE("overdue", 2, R.string.reminder_status_overdue)
}

/**
 * Erinnerung je Fahrzeug (HU, Inspektion, Reifenwechsel ...). Gespeichert wird nur die naechste
 * Faelligkeit (Datum und/oder km-Stand). Die vom Server zusaetzlich berechneten Felder (status,
 * current_km, km_remaining, ...) werden nicht uebernommen - die App rechnet sie selbst
 * ([com.dominiqueherbrigpersonalteam.lademonitor.data.repo.LocalReminderCalculator]), damit
 * beide Modi gleich aussehen. Datumsfelder sind reine Kalendertage (lokale Mitternacht).
 */
@JsonClass(generateAdapter = true)
data class Reminder(
    val id: String,
    @Json(name = "vehicle_id") val vehicleId: String,
    val kind: String = ReminderKind.CUSTOM.raw,
    val title: String? = null,
    @ServerDate @Json(name = "due_date") val dueDate: Long? = null,
    @Json(name = "due_km") val dueKm: Int? = null,
    @Json(name = "repeat_months") val repeatMonths: Int? = null,
    @Json(name = "repeat_km") val repeatKm: Int? = null,
    @Json(name = "lead_days") val leadDays: Int = 30,
    @Json(name = "lead_km") val leadKm: Int = 1000,
    val notes: String? = null,
    @ServerDate @Json(name = "last_done_date") val lastDoneDate: Long? = null,
    @Json(name = "last_done_km") val lastDoneKm: Int? = null
) {
    val kindValue: ReminderKind get() = ReminderKind.from(kind)

    /** Eigene Bezeichnung, sonst der Name der Art ("HU", "Inspektion" ...). */
    val displayTitle: String
        get() = title?.takeIf { it.isNotBlank() } ?: LademonitorApp.appContext.getString(kindValue.labelRes)

    /** Ein Payload mit denselben Werten, z.B. als Ausgangspunkt fuer "Erledigt". */
    fun toPayload() = ReminderPayload(
        vehicleId = vehicleId, kind = kind, title = title, dueDate = dueDate, dueKm = dueKm,
        repeatMonths = repeatMonths, repeatKm = repeatKm, leadDays = leadDays, leadKm = leadKm,
        notes = notes, lastDoneDate = lastDoneDate, lastDoneKm = lastDoneKm
    )
}

/**
 * Payload fuer POST/PATCH /api/reminders - mit `encodeKeepingNulls` gesendet, damit sich
 * Faelligkeit, Wiederholung, Bezeichnung und Notiz per PATCH wieder leeren lassen.
 */
@JsonClass(generateAdapter = true)
data class ReminderPayload(
    @Json(name = "vehicle_id") val vehicleId: String,
    val kind: String,
    val title: String? = null,
    @ServerDate @Json(name = "due_date") val dueDate: Long? = null,
    @Json(name = "due_km") val dueKm: Int? = null,
    @Json(name = "repeat_months") val repeatMonths: Int? = null,
    @Json(name = "repeat_km") val repeatKm: Int? = null,
    @Json(name = "lead_days") val leadDays: Int = 30,
    @Json(name = "lead_km") val leadKm: Int = 1000,
    val notes: String? = null,
    @ServerDate @Json(name = "last_done_date") val lastDoneDate: Long? = null,
    @Json(name = "last_done_km") val lastDoneKm: Int? = null
)

// MARK: - Laufender Ladevorgang

/**
 * Antwort von `GET /api/sessions/active` (Server ab 0.45.0): ein gerade laufender Ladevorgang
 * ("Lädt gerade"). Kein [ChargingSession] - der Vorgang entsteht auf dem Server erst beim
 * Ausstecken. Bis dahin ein Zwischenstand der Quelle (MyŠkoda, VW-Datenportal, Renault, Home
 * Assistant), nur zum Anzeigen, nicht im lokalen Spiegel und nicht im Sync - deshalb nur im
 * Server-Modus.
 */
@JsonClass(generateAdapter = true)
data class ActiveCharging(
    @Json(name = "vehicle_id") val vehicleId: String,
    @Json(name = "vehicle_name") val vehicleName: String? = null,
    /** myskoda | vw_data_act | renault | home_assistant */
    val source: String,
    @ServerDate @Json(name = "start_time") val startTime: Long? = null,
    @Json(name = "charging_type") val chargingType: String? = null,
    @Json(name = "soc_start") val socStart: Int? = null,
    /** Juengster bekannter SoC (letzter Bericht der Quelle). */
    @Json(name = "soc_current") val socCurrent: Int? = null,
    @Json(name = "energy_kwh") val energyKwh: Double? = null,
    @Json(name = "energy_is_estimated") val energyIsEstimated: Boolean = false,
    @Json(name = "max_power_kw") val maxPowerKw: Double? = null,
    @Json(name = "odometer_km") val odometerKm: Int? = null,
    @Json(name = "outside_temp_c") val outsideTempC: Double? = null,
    /** Zeitpunkt des Berichts, aus dem die Werte stammen - beim VW-Datenportal gern Stunden alt. */
    @ServerDate @Json(name = "updated_at") val updatedAt: Long? = null
) {
    val chargingTypeValue: ChargingType? get() = ChargingType.from(chargingType)
}

// MARK: - Akku-Gesundheit und Ladeverluste

/**
 * Antwort von `GET /api/stats/battery` (Server ab 0.28.0): Akku-Index und Ladeverluste je
 * Fahrzeug. Wie die Temperaturauswertung bewusst NUR im Server-Modus - die Regeln liegen allein
 * in `battery.py`, ein Nachbau hier wuerde frueher oder spaeter andere Zahlen zeigen.
 */
@JsonClass(generateAdapter = true)
data class BatteryStats(
    val vehicles: List<BatteryVehicleStats> = emptyList()
)

@JsonClass(generateAdapter = true)
data class BatteryVehicleStats(
    @Json(name = "vehicle_id") val vehicleId: String,
    @Json(name = "vehicle_name") val vehicleName: String,
    @Json(name = "nominal_capacity_kwh") val nominalCapacityKwh: Double? = null,
    val points: List<BatteryPoint> = emptyList(),
    @Json(name = "losses_by_type") val lossesByType: List<BatteryLossGroup> = emptyList(),
    @Json(name = "losses_by_provider") val lossesByProvider: List<BatteryLossGroup> = emptyList(),
    /** Index 100 = wie zu Beginn der Aufzeichnung, je Quartal. */
    @Json(name = "health_periods") val healthPeriods: List<BatteryHealthPeriod> = emptyList(),
    @Json(name = "health_latest_index_pct") val healthLatestIndexPct: Double? = null,
    @Json(name = "health_trend_pct_per_year") val healthTrendPctPerYear: Double? = null,
    val excluded: BatteryExclusions = BatteryExclusions(),
    @Json(name = "min_soc_delta") val minSocDelta: Int = 20
)

@JsonClass(generateAdapter = true)
data class BatteryPoint(
    @Json(name = "session_id") val sessionId: String,
    @ServerDate @Json(name = "start_time") val startTime: Long,
    /** "AC", "DC" oder "unknown" */
    @Json(name = "charging_type") val chargingType: String,
    @Json(name = "soc_delta") val socDelta: Int,
    @Json(name = "energy_kwh") val energyKwh: Double,
    @Json(name = "apparent_capacity_kwh") val apparentCapacityKwh: Double,
    @Json(name = "loss_pct") val lossPct: Double? = null,
    /** "charger" | "vehicle" (Server ab 0.29.0), wirksamer Messort der kWh. */
    @Json(name = "energy_meter") val energyMeter: String? = null
)

@JsonClass(generateAdapter = true)
data class BatteryLossGroup(
    /** Lade-Art (AC/DC/unknown) bzw. Anbietername ("" = ohne Anbieter). */
    val key: String,
    @Json(name = "session_count") val sessionCount: Int,
    @Json(name = "energy_kwh") val energyKwh: Double,
    @Json(name = "apparent_capacity_kwh") val apparentCapacityKwh: Double,
    /** Mehrbedarf gegenueber der Nennkapazitaet in Prozent. */
    @Json(name = "loss_pct") val lossPct: Double? = null
)

@JsonClass(generateAdapter = true)
data class BatteryHealthPeriod(
    /** "2026-Q3" */
    val period: String,
    @Json(name = "index_pct") val indexPct: Double,
    @Json(name = "session_count") val sessionCount: Int
) {
    val label: String get() = period.replace("-", " ")
}

@JsonClass(generateAdapter = true)
data class BatteryExclusions(
    @Json(name = "estimated_energy") val estimatedEnergy: Int = 0,
    @Json(name = "missing_values") val missingValues: Int = 0,
    @Json(name = "small_soc_delta") val smallSocDelta: Int = 0,
    val implausible: Int = 0,
    /**
     * Im Fahrzeug abgelesene Vorgaenge (Server ab 0.29.0). Sie zaehlen im Akku-Index mit, fehlen
     * aber bei den Ladeverlusten - deshalb NICHT Teil von [total].
     */
    @Json(name = "vehicle_measured") val vehicleMeasured: Int = 0
) {
    val total: Int get() = estimatedEnergy + missingValues + smallSocDelta + implausible
}
