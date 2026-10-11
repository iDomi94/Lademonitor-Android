package com.dominiqueherbrigpersonalteam.lademonitor.data.repo

import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ChargingPlace
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ChargingSession
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ChargingType
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.EnergyMeter
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.Provider
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ReimbursementMode
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ReimbursementRate
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.Vehicle
import java.time.LocalDate
import java.util.Locale

/**
 * Lokaler Port von `reimbursement.py` (Server ab 0.35.0): Erstattungsbericht fuer die
 * HEIMLADUNGEN eines Dienstwagens. Rechnet in beiden Modi lokal - die Regeln MUESSEN mit dem
 * Server uebereinstimmen, die Faelle in `tests/test_reimbursement.py` (Server-Repo) gelten hier
 * genauso:
 *
 * - Nur Vorgaenge DIESES Fahrzeugs, deren Anbieter auf "Zuhause" steht. "Arbeit", "Unterwegs" und
 *   Vorgaenge ohne Anbieter fallen heraus.
 * - Zeitraum ueber den lokalen Kalendertag von `startTime`, beide Enden inklusive; sortiert nach
 *   (`startTime`, `id`).
 * - Herkunft der kWh: geschaetzt, sonst der wirksame Messort (Vorgang, sonst Anbieter, sonst
 *   Wallbox).
 * - Pauschal: der Satz mit dem juengsten `validFrom` <= Ladetag; davor kein Betrag (gezaehlt).
 * - Tatsaechlich: Saeulenpreis, sonst Preis je kWh x kWh, sonst letzter Anbieterpreis der
 *   Lade-Art x kWh; auf Wunsch plus Grundgebuehr-Anteil ([LocalFeeAllocator], ueber ALLE
 *   Vorgaenge umgelegt - steckt schon in [ChargingSession.feeShare]).
 * - Betraege je Vorgang auf den Cent; Summen sind die Summe der gerundeten Betraege.
 */
object LocalReimbursementCalculator {

    /** Vorschlag fuer den ersten Satz (Pauschale 2026), wie `DEFAULT_RATE_PER_KWH` im Server. */
    const val DEFAULT_RATE_PER_KWH = 0.34

    enum class KwhSource(val raw: String) {
        /** An der Wallbox/Ladesaeule gemessen. */
        CHARGER("charger"),

        /** Im Fahrzeug abgelesen. */
        VEHICLE("vehicle"),

        /** Aus Ladestand x Akkukapazitaet geschaetzt. */
        ESTIMATED("estimated")
    }

    data class Row(
        val sessionId: String,
        val startTime: Long,
        val energyKwh: Double?,
        val kwhSource: KwhSource,
        val odometerKm: Int?,
        /** Satz bzw. tatsaechlicher Preis je kWh, mit dem gerechnet wurde. */
        val ratePerKwh: Double?,
        /** Grundgebuehr-Anteil (nur "tatsaechlich" mit Grundgebuehr), im Betrag enthalten. */
        val feeShare: Double,
        /** null = nicht berechenbar (kein Satz zum Datum bzw. kein Preis). */
        val amount: Double?
    )

    data class Month(
        /** "YYYY-MM" */
        val month: String,
        val sessions: Int,
        val energyKwh: Double,
        val estimatedKwh: Double,
        val amount: Double
    )

    data class Report(
        val vehicleId: String,
        val vehicleName: String,
        val licensePlate: String?,
        val employeeName: String?,
        val employeeNumber: String?,
        val mode: ReimbursementMode,
        val includeFees: Boolean,
        val dateFrom: LocalDate,
        val dateTo: LocalDate,
        val rows: List<Row>,
        val months: List<Month>,
        val totalKwh: Double,
        val estimatedKwh: Double,
        val estimatedSessions: Int,
        /** Vorgaenge ohne Betrag - gezaehlt, nicht still mit 0 verrechnet. */
        val missingAmountSessions: Int,
        val totalAmount: Double
    )

    fun kwhSource(session: ChargingSession, providers: List<Provider>): KwhSource {
        if (session.energyIsEstimated) return KwhSource.ESTIMATED
        return if (session.effectiveEnergyMeter(providers) == EnergyMeter.VEHICLE) KwhSource.VEHICLE
        else KwhSource.CHARGER
    }

    /** Satz mit dem juengsten `validFrom` <= [day]; bei gleichem Tag gewinnt der spaetere Eintrag. */
    fun rateAt(rates: List<ReimbursementRate>, day: LocalDate): Double? {
        var best: ReimbursementRate? = null
        var bestStart: LocalDate? = null
        for (rate in rates) {
            val start = LocalFeeAllocator.day(rate.validFrom)
            if (!start.isAfter(day) && (bestStart == null || !start.isBefore(bestStart))) {
                best = rate
                bestStart = start
            }
        }
        return best?.ratePerKwh
    }

    private fun actualPrice(session: ChargingSession, provider: Provider?): Double? {
        session.priceTotal?.let { return it }
        val kwh = session.energyKwh
        if (kwh == null || kwh == 0.0) return null
        session.pricePerKwh?.let { return it * kwh }
        if (provider == null) return null
        val last = if (session.chargingTypeValue == ChargingType.DC) provider.lastPriceDcPerKwh
        else provider.lastPriceAcPerKwh
        return last?.let { it * kwh }
    }

    /**
     * Baut den Bericht. [sessions] und [providers] muessen dieselbe Fahrzeug-/Anbieter-ID-Form
     * haben wie [vehicle] (siehe [LocalDataStore.reimbursementBasis]); [rates] werden auf das
     * Fahrzeug gefiltert.
     */
    fun build(
        vehicle: Vehicle,
        sessions: List<ChargingSession>,
        providers: List<Provider>,
        rates: List<ReimbursementRate>,
        dateFrom: LocalDate,
        dateTo: LocalDate
    ): Report {
        val providerById = providers.associateBy { it.id }
        val mode = vehicle.reimbursementModeValue
        val includeFees = (vehicle.reimbursementIncludeFees ?: false) && mode == ReimbursementMode.ACTUAL
        val vehicleRates = rates.filter { it.vehicleId == vehicle.id }

        val home = sessions
            .filter { s ->
                if (s.vehicleId != vehicle.id) return@filter false
                val provider = s.providerId?.let { providerById[it] } ?: return@filter false
                if (ChargingPlace.from(provider.chargingPlace) != ChargingPlace.HOME) return@filter false
                val day = LocalFeeAllocator.day(s.startTime)
                !day.isBefore(dateFrom) && !day.isAfter(dateTo)
            }
            .sortedWith(compareBy({ it.startTime }, { it.id }))

        val rows = mutableListOf<Row>()
        val months = LinkedHashMap<String, MonthAcc>()
        for (s in home) {
            val source = kwhSource(s, providers)
            val kwh = s.energyKwh
            val fee = if (includeFees) round2(s.feeShare ?: 0.0) else 0.0
            val day = LocalFeeAllocator.day(s.startTime)
            val (rate, amount) = when (mode) {
                ReimbursementMode.FLAT -> {
                    val flat = rateAt(vehicleRates, day)
                    flat to (if (flat != null && kwh != null) round2(kwh * flat) else null)
                }
                ReimbursementMode.ACTUAL -> {
                    val price = actualPrice(s, s.providerId?.let { providerById[it] })
                    val total = price?.let { round2(it + fee) }
                    (if (total != null && kwh != null && kwh != 0.0) round4(total / kwh) else null) to total
                }
            }
            rows += Row(
                sessionId = s.id,
                startTime = s.startTime,
                energyKwh = kwh,
                kwhSource = source,
                odometerKm = s.odometerKm,
                ratePerKwh = rate,
                feeShare = if (amount != null) fee else 0.0,
                amount = amount
            )
            val key = String.format(Locale.ROOT, "%04d-%02d", day.year, day.monthValue)
            val m = months.getOrPut(key) { MonthAcc() }
            m.sessions += 1
            m.kwh += kwh ?: 0.0
            if (source == KwhSource.ESTIMATED) m.est += kwh ?: 0.0
            m.amount += amount ?: 0.0
        }

        return Report(
            vehicleId = vehicle.id,
            vehicleName = vehicle.name,
            licensePlate = vehicle.licensePlate,
            employeeName = vehicle.employeeName,
            employeeNumber = vehicle.employeeNumber,
            mode = mode,
            includeFees = includeFees,
            dateFrom = dateFrom,
            dateTo = dateTo,
            rows = rows,
            months = months.map { (k, v) ->
                Month(k, v.sessions, round2(v.kwh), round2(v.est), round2(v.amount))
            },
            totalKwh = round2(rows.sumOf { it.energyKwh ?: 0.0 }),
            estimatedKwh = round2(rows.filter { it.kwhSource == KwhSource.ESTIMATED }.sumOf { it.energyKwh ?: 0.0 }),
            estimatedSessions = rows.count { it.kwhSource == KwhSource.ESTIMATED },
            missingAmountSessions = rows.count { it.amount == null },
            totalAmount = round2(rows.sumOf { it.amount ?: 0.0 })
        )
    }

    private class MonthAcc {
        var sessions = 0
        var kwh = 0.0
        var est = 0.0
        var amount = 0.0
    }

    /** Auf den Cent, bei exakt halben Cents zur geraden Zahl - wie Pythons `round()`. */
    fun round2(v: Double): Double = Math.rint(v * 100) / 100.0

    private fun round4(v: Double): Double = Math.rint(v * 10_000) / 10_000.0
}
