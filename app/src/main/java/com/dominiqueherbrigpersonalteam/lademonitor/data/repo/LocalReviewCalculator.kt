package com.dominiqueherbrigpersonalteam.lademonitor.data.repo

import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ChargingSession
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ChargingType
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.PlaceStat
import java.time.LocalDate
import java.time.YearMonth

/**
 * Lokaler Port von `GET /api/stats/review` (Server ab 0.36.0): Jahres- und Monatsrueckblick.
 * Rechnet in beiden Modi lokal, wie `LocalReviewCalculator.swift` - die Regeln MUESSEN mit dem
 * Server uebereinstimmen (Vertrag `schritt-11-vertrag.md`), sonst zeigen App und Web andere
 * Zahlen:
 *
 * - Zeitraum: Kalenderjahr bzw. -monat, zugeordnet ueber den lokalen Kalendertag von
 *   `startTime`, beide Enden inklusive. Vorperiode = Vorjahr bzw. Vormonat.
 * - Kosten = Saeulenpreis + Grundgebuehr-Anteil (umgelegt ueber ALLE Vorgaenge, steckt schon in
 *   [ChargingSession.feeShare]) + nicht umgelegte Perioden, die im Zeitraum beginnen (nur ohne
 *   Fahrzeugfilter).
 * - km je Fahrzeug: letzter Stand im Zeitraum minus Anker (letzter Stand VOR dem Zeitraum, sonst
 *   erster im Zeitraum), nie negativ.
 * - Verbrauch: km-gewichtetes Mittel der Einzelwerte ([LocalConsumptionCalculator]).
 * - Rekorde: bei Gleichstand der fruehere Vorgang (startTime, dann id).
 * - Gerundet wird erst in der Anzeige.
 */
object LocalReviewCalculator {

    enum class Period { YEAR, MONTH }

    data class Totals(
        val sessions: Int,
        val kwh: Double,
        val cost: Double,
        val km: Double,
        val avgPricePerKwh: Double?,
        val pricePer100km: Double?,
        val avgConsumptionKwhPer100km: Double?
    )

    data class Savings(
        val fuel: CombustionComparison.Fuel,
        val consumptionLPer100km: Double,
        val fuelPricePerL: Double,
        val gridCo2KgPerKwh: Double,
        /** false = Standardwerte, nie gespeichert. */
        val configured: Boolean,
        val fuelLiters: Double,
        val fuelCost: Double,
        val chargingCost: Double,
        /** Darf negativ sein. */
        val savedEur: Double,
        val co2CombustionKg: Double,
        val co2EvKg: Double,
        val savedCo2Kg: Double
    )

    data class SessionRecord(
        val sessionId: String,
        val startTime: Long,
        val value: Double,
        val place: String?,
        val vehicleName: String?,
        /** Nur bei der laengsten Strecke: Beginn des Vorgangs davor. */
        val previousStartTime: Long? = null
    )

    data class NamedRecord(val name: String, val sessions: Int, val kwh: Double)

    data class MonthRecord(val month: Int, val value: Double)

    data class Records(
        val cheapestPerKwh: SessionRecord? = null,
        val mostExpensivePerKwh: SessionRecord? = null,
        val biggestCharge: SessionRecord? = null,
        val coldest: SessionRecord? = null,
        val warmest: SessionRecord? = null,
        val longestDistance: SessionRecord? = null,
        val topLocation: NamedRecord? = null,
        val topProvider: NamedRecord? = null,
        val lowestConsumptionMonth: MonthRecord? = null,
        val highestConsumptionMonth: MonthRecord? = null
    )

    data class Split(
        val acKwh: Double,
        val dcKwh: Double,
        val acSharePct: Double?,
        val dcSharePct: Double?,
        val byPlace: List<PlaceStat>
    )

    data class MonthRow(
        val month: Int,
        val sessions: Int,
        val kwh: Double,
        val cost: Double,
        val avgConsumptionKwhPer100km: Double?
    )

    data class Review(
        val period: Period,
        val year: Int,
        val month: Int?,
        val startDate: LocalDate,
        val endDate: LocalDate,
        val vehicleId: String?,
        val totals: Totals,
        /** null, wenn die Vorperiode keinen Ladevorgang hat. */
        val previous: Totals?,
        /** null, wenn keine km gefahren wurden. */
        val savings: Savings?,
        val records: Records,
        val split: Split,
        /** Nur beim Jahr (immer 12 Zeilen), sonst leer. */
        val monthly: List<MonthRow>
    )

    /** kg CO2 je Liter, Tank-to-Wheel - dieselben Werte wie [CombustionComparison.Fuel]. */
    private fun co2PerLiter(fuel: CombustionComparison.Fuel): Double = fuel.co2KgPerLiter

    fun range(period: Period, year: Int, month: Int?): Pair<LocalDate, LocalDate> = when (period) {
        Period.YEAR -> LocalDate.of(year, 1, 1) to LocalDate.of(year, 12, 31)
        Period.MONTH -> {
            val ym = YearMonth.of(year, month ?: 1)
            ym.atDay(1) to ym.atEndOfMonth()
        }
    }

    private val sessionOrder = compareBy<ChargingSession>({ it.startTime }, { it.id })

    private fun day(s: ChargingSession): LocalDate = LocalFeeAllocator.day(s.startTime)

    private fun inRange(d: LocalDate, from: LocalDate, to: LocalDate) = !d.isBefore(from) && !d.isAfter(to)

    /** Python-`or`: ein leerer Text zaehlt wie keiner. */
    private fun String?.orNullIfEmpty(): String? = this?.takeIf { it.isNotEmpty() }

    fun build(
        basis: ReviewBasis,
        period: Period,
        year: Int,
        month: Int?,
        vehicleId: String?,
        comparison: ComparisonVehicleStore.Values
    ): Review {
        val (start, end) = range(period, year, month)
        val (prevStart, prevEnd) = when (period) {
            Period.YEAR -> range(period, year - 1, null)
            Period.MONTH -> {
                val prev = YearMonth.of(year, month ?: 1).minusMonths(1)
                range(period, prev.year, prev.monthValue)
            }
        }

        val all = basis.sessions
            .filter { vehicleId == null || it.vehicleId == vehicleId }
            .sortedWith(sessionOrder)
        // Ohne Fahrzeugfilter zaehlen auch Abo-Perioden ganz ohne Ladevorgang (sie gehoeren zu
        // keinem Fahrzeug), nach ihrem Beginn.
        val unallocated = if (vehicleId == null) basis.unallocatedFees else emptyList()

        // Verbrauch je Vorgang ueber die VOLLE Historie des Fahrzeugs - der Vorgaenger kann vor
        // dem Zeitraum liegen.
        val capacity = basis.vehicles.associate { it.id to it.batteryCapacityKwh }
        val consumption = HashMap<String, LocalConsumptionCalculator.Result>()
        all.groupBy { it.vehicleId }.forEach { (vid, list) ->
            consumption.putAll(LocalConsumptionCalculator.compute(list, capacity[vid]))
        }

        fun totalsFor(from: LocalDate, to: LocalDate): Totals {
            val sel = all.filter { inRange(day(it), from, to) }
            val kwh = sel.sumOf { it.energyKwh ?: 0.0 }
            val cost = sel.sumOf { (it.priceTotal ?: 0.0) + (it.feeShare ?: 0.0) } +
                unallocated.filter { inRange(it.start, from, to) }.sumOf { it.amount }
            var km = 0.0
            all.filter { it.odometerKm != null }.groupBy { it.vehicleId }.values.forEach { list ->
                val inside = list.filter { inRange(day(it), from, to) }
                if (inside.isEmpty()) return@forEach
                val last = inside.last().odometerKm!!
                val anchor = list.lastOrNull { day(it).isBefore(from) }?.odometerKm ?: inside.first().odometerKm!!
                km += maxOf(0, last - anchor).toDouble()
            }
            return Totals(
                sessions = sel.size,
                kwh = kwh,
                cost = cost,
                km = km,
                avgPricePerKwh = if (kwh > 0) cost / kwh else null,
                pricePer100km = if (km > 0) cost / km * 100 else null,
                avgConsumptionKwhPer100km = weightedConsumption(sel, consumption)
            )
        }

        val totals = totalsFor(start, end)
        val previous = totalsFor(prevStart, prevEnd).takeIf { it.sessions > 0 }
        val selected = all.filter { inRange(day(it), start, end) }

        val savings = if (totals.km > 0) {
            val liters = totals.km * comparison.consumption / 100
            val fuelCost = liters * comparison.price
            val co2Combustion = liters * co2PerLiter(comparison.fuel)
            val co2Ev = totals.kwh * comparison.gridCo2
            Savings(
                fuel = comparison.fuel,
                consumptionLPer100km = comparison.consumption,
                fuelPricePerL = comparison.price,
                gridCo2KgPerKwh = comparison.gridCo2,
                configured = comparison.configured,
                fuelLiters = liters,
                fuelCost = fuelCost,
                chargingCost = totals.cost,
                savedEur = fuelCost - totals.cost,
                co2CombustionKg = co2Combustion,
                co2EvKg = co2Ev,
                savedCo2Kg = co2Combustion - co2Ev
            )
        } else null

        val monthly = if (period == Period.YEAR) {
            (1..12).map { m ->
                val ym = YearMonth.of(year, m)
                val t = totalsFor(ym.atDay(1), ym.atEndOfMonth())
                MonthRow(m, t.sessions, t.kwh, t.cost, t.avgConsumptionKwhPer100km)
            }
        } else emptyList()

        val records = buildRecords(basis, all, selected, monthly)

        val acKwh = selected.filter { it.chargingTypeValue == ChargingType.AC }.sumOf { it.energyKwh ?: 0.0 }
        val dcKwh = selected.filter { it.chargingTypeValue == ChargingType.DC }.sumOf { it.energyKwh ?: 0.0 }
        val typed = acKwh + dcKwh
        val byPlace = LocalStatsCalculator.buildByPlace(
            selected,
            basis.providers,
            unallocated.filter { inRange(it.start, start, end) },
            emptyMap()
        ) { (it.priceTotal ?: 0.0) + (it.feeShare ?: 0.0) }

        return Review(
            period = period,
            year = year,
            month = if (period == Period.MONTH) month else null,
            startDate = start,
            endDate = end,
            vehicleId = vehicleId,
            totals = totals,
            previous = previous,
            savings = savings,
            records = records,
            split = Split(
                acKwh = acKwh,
                dcKwh = dcKwh,
                acSharePct = if (typed > 0) acKwh / typed * 100 else null,
                dcSharePct = if (typed > 0) dcKwh / typed * 100 else null,
                byPlace = byPlace
            ),
            monthly = monthly
        )
    }

    /** Wie `monthly[].avg_consumption_kwh_per_100km` in stats.py: gewichtet mit den km der Fahrt. */
    private fun weightedConsumption(
        sessions: List<ChargingSession>,
        consumption: Map<String, LocalConsumptionCalculator.Result>
    ): Double? {
        var num = 0.0
        var km = 0.0
        for (s in sessions) {
            val r = consumption[s.id] ?: continue
            val value = r.value ?: continue
            val weight = r.km ?: continue
            if (weight <= 0) continue
            num += value * weight
            km += weight
        }
        return if (km > 0) num / km else null
    }

    private fun buildRecords(
        basis: ReviewBasis,
        all: List<ChargingSession>,
        selected: List<ChargingSession>,
        monthly: List<MonthRow>
    ): Records {
        val locationName = basis.locations.associate { it.id to it.name }
        val providerName = basis.providers.associate { it.id to it.name }
        val vehicleName = basis.vehicles.associate { it.id to it.name }

        fun locName(s: ChargingSession): String? =
            s.locationId?.let { locationName[it] }.orNullIfEmpty() ?: s.geocodedPlace.orNullIfEmpty()

        fun place(s: ChargingSession): String? =
            locName(s) ?: s.providerId?.let { providerName[it] }.orNullIfEmpty()

        fun record(s: ChargingSession, value: Double, previousStart: Long? = null) =
            SessionRecord(s.id, s.startTime, value, place(s), vehicleName[s.vehicleId], previousStart)

        /**
         * Erster Vorgang (in [selected]-Reihenfolge = startTime, id) mit dem kleinsten bzw.
         * groessten Wert - strikter Vergleich, damit bei Gleichstand der fruehere gewinnt.
         */
        fun pick(candidates: List<Pair<ChargingSession, Double>>, biggest: Boolean): Pair<ChargingSession, Double>? {
            var best: Pair<ChargingSession, Double>? = null
            for (c in candidates) {
                val b = best
                if (b == null || (if (biggest) c.second > b.second else c.second < b.second)) best = c
            }
            return best
        }

        val priced = selected
            .filter { (it.energyKwh ?: 0.0) >= 1 && (it.priceTotal ?: 0.0) > 0 }
            .map { it to it.priceTotal!! / it.energyKwh!! }
        val cheapest = if (priced.size >= 2) pick(priced, biggest = false) else null
        val dearest = if (priced.size >= 2) pick(priced, biggest = true) else null

        val biggestCharge = pick(
            selected.filter { (it.energyKwh ?: 0.0) > 0 }.map { it to it.energyKwh!! }, biggest = true
        )

        val withTemp = selected.filter { it.outsideTempC != null }.map { it to it.outsideTempC!! }
        val coldest = if (withTemp.size >= 2) pick(withTemp, biggest = false) else null
        val warmest = if (withTemp.size >= 2) pick(withTemp, biggest = true) else null

        // Laengste Strecke: Abstand zum vorherigen Vorgang DESSELBEN Fahrzeugs mit Kilometerstand,
        // der darf vor dem Zeitraum liegen.
        val previousOf = HashMap<String, ChargingSession>()
        all.filter { it.odometerKm != null }.groupBy { it.vehicleId }.values.forEach { list ->
            for (i in 1 until list.size) previousOf[list[i].id] = list[i - 1]
        }
        var longest: SessionRecord? = null
        for (s in selected) {
            val odo = s.odometerKm ?: continue
            val prev = previousOf[s.id] ?: continue
            val distance = (odo - prev.odometerKm!!).toDouble()
            if (distance <= 0) continue
            if (longest == null || distance > longest.value) longest = record(s, distance, prev.startTime)
        }

        fun top(names: List<Pair<String, ChargingSession>>): NamedRecord? {
            if (names.isEmpty()) return null
            return names.groupBy({ it.first }, { it.second })
                .map { (name, list) -> NamedRecord(name, list.size, list.sumOf { it.energyKwh ?: 0.0 }) }
                .sortedWith(compareByDescending<NamedRecord> { it.sessions }.thenByDescending { it.kwh }.thenBy { it.name })
                .first()
        }
        val topLocation = top(selected.mapNotNull { s -> locName(s)?.let { it to s } })
        val topProvider = top(selected.mapNotNull { s -> s.providerId?.let { providerName[it] }?.let { it to s } })

        val monthsWithValue = monthly.mapNotNull { row -> row.avgConsumptionKwhPer100km?.let { MonthRecord(row.month, it) } }
        var lowestMonth: MonthRecord? = null
        var highestMonth: MonthRecord? = null
        if (monthsWithValue.size >= 2) {
            for (m in monthsWithValue) {
                if (lowestMonth == null || m.value < lowestMonth.value) lowestMonth = m
                if (highestMonth == null || m.value > highestMonth.value) highestMonth = m
            }
        }

        return Records(
            cheapestPerKwh = cheapest?.let { record(it.first, it.second) },
            mostExpensivePerKwh = dearest?.let { record(it.first, it.second) },
            biggestCharge = biggestCharge?.let { record(it.first, it.second) },
            coldest = coldest?.let { record(it.first, it.second) },
            warmest = warmest?.let { record(it.first, it.second) },
            longestDistance = longest,
            topLocation = topLocation,
            topProvider = topProvider,
            lowestConsumptionMonth = lowestMonth,
            highestConsumptionMonth = highestMonth
        )
    }
}
