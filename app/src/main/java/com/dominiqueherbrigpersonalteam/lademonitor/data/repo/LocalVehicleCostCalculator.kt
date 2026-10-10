package com.dominiqueherbrigpersonalteam.lademonitor.data.repo

import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ChargingSession
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.CostCategory
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.CostCategoryStat
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.FeeInterval
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.Ownership
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.Vehicle
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.VehicleCost
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.VehicleCostSummary
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.YearCostStat
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.pow

/**
 * Lokaler Port von `vehicle_costs.py` (Server ab 0.32.0) samt `build_vehicle_costs()` und
 * `cost_summary()` aus den Routern, wie `LocalVehicleCostCalculator.swift` in der iOS-App.
 *
 * Noetig, weil das Dashboard auch im Server-Modus lokal entsteht und die Kosten im
 * Local-Only-Modus ohne Server funktionieren muessen. Die Regeln MUESSEN mit vehicle_costs.py
 * uebereinstimmen - die Faelle in `tests/test_vehicle_costs.py` (Server-Repo) gelten hier genauso:
 *
 * - Sonstige Kosten: `once` ist eine Zahlung am Startdatum, wiederkehrende laufen vom
 *   URSPRUENGLICHEN Ankertag aus (31.01. -> 28.02. -> 31.03.) bis zum inklusiven Enddatum bzw.
 *   bis heute. Ein Jahresbetrag zaehlt in dem Monat, in dem er faellig ist.
 * - Leasing: Rate plus Sonderzahlung / Laufzeit in Monaten; ohne Enddatum zaehlt die
 *   Sonderzahlung im ersten Monat.
 * - Kauf: Restwertkurve (1. Jahr linear auf 75 %, danach x0,87 je Jahr) ueber das "wirksame
 *   Alter" = max(Kalenderalter, km / 15.000). Der Kaufpreis ist der Wert BEIM KAUF, gerechnet
 *   wird mit dem Verhaeltnis der Kurve. Ein fester Monatsbetrag ersetzt die Kurve.
 * - Monatsperioden ab Kauf-/Leasingbeginn zaehlen voll, sobald sie begonnen haben; jeder Betrag
 *   auf den Cent gerundet (half-even wie Pythons `round()`).
 */
object LocalVehicleCostCalculator {

    /** Jahresfahrleistung, die das "wirksame Alter" einem Kalenderjahr gleichsetzt. */
    const val KM_PER_YEAR_REFERENCE = 15000.0
    const val FIRST_YEAR_RESIDUAL = 0.75
    const val YEARLY_RESIDUAL_FACTOR = 0.87
    const val DAYS_PER_YEAR = 365.25

    /** Zeitraum, aus dem die aktuelle Fahrleistung geschaetzt wird. */
    const val MILEAGE_WINDOW_DAYS = 365L
    const val MIN_MILEAGE_SPAN_DAYS = 30L

    const val CHARGING = "charging"
    const val DEPRECIATION = "depreciation"
    const val LEASING = "leasing"

    /** Eine einzelne Zahlung bzw. ein Monatsbetrag, fertig zum Aufsummieren. */
    data class CostEntry(
        val vehicleId: String,
        val day: LocalDate,
        val amount: Double,
        /** Raw-Wert einer [CostCategory], bzw. [DEPRECIATION] / [LEASING]. */
        val category: String
    )

    /** Kennzahlen fuer die Anzeige am Fahrzeug, wie `ownership_info()`. */
    data class OwnershipInfo(
        /** Geschaetzter heutiger Wert (nur Kauf). */
        val estimatedValue: Double? = null,
        /** Wertverlust je Monat laut Restwertkurve (nur Kauf). */
        val suggestedMonthly: Double? = null,
        /** Was tatsaechlich zaehlt: Vorschlag bzw. fester Wert, oder Leasingrate inkl. Anteil Sonderzahlung. */
        val ownershipMonthly: Double? = null
    )

    private fun day(epochMillis: Long): LocalDate = LocalFeeAllocator.day(epochMillis)

    private fun addMonths(anchor: LocalDate, months: Int): LocalDate =
        LocalFeeAllocator.addMonths(anchor, months.toLong())

    private fun daysBetween(from: LocalDate, to: LocalDate): Long = ChronoUnit.DAYS.between(from, to)

    // ---------- Sonstige Kosten ----------

    /** Zahlungstermine eines Kosteneintrags bis [until] (einschliesslich). */
    fun occurrences(cost: VehicleCost, until: LocalDate = LocalDate.now()): List<LocalDate> {
        val start = day(cost.startDate)
        if (start.isAfter(until)) return emptyList()
        val interval = cost.intervalValue
        if (interval == FeeInterval.ONCE) return listOf(start)
        val end = cost.endDate?.let { day(it) }
        val step = if (interval == FeeInterval.YEARLY) 12 else 1
        val result = mutableListOf<LocalDate>()
        var k = 0
        while (true) {
            val d = addMonths(start, k * step)
            if (d.isAfter(until) || (end != null && d.isAfter(end))) break
            result += d
            k++
        }
        return result
    }

    /** Summe aller Zahlungen bis heute ("bisher bezahlt"), wie `paid_to_date` im Server. */
    fun paidToDate(cost: VehicleCost, until: LocalDate = LocalDate.now()): Double =
        round2(cost.amount * occurrences(cost, until).size)

    fun costEntries(costs: List<VehicleCost>, until: LocalDate): List<CostEntry> =
        costs.flatMap { c ->
            occurrences(c, until).map { CostEntry(c.vehicleId, it, c.amount, c.categoryValue.raw) }
        }

    // ---------- Wertverlust ----------

    /** Restwert in Anteilen des Neupreises nach [ageYears] wirksamen Jahren. */
    fun residualFraction(ageYears: Double): Double = when {
        ageYears <= 0 -> 1.0
        ageYears <= 1 -> 1.0 - (1.0 - FIRST_YEAR_RESIDUAL) * ageYears
        else -> FIRST_YEAR_RESIDUAL * YEARLY_RESIDUAL_FACTOR.pow(ageYears - 1)
    }

    /**
     * Wertverlauf eines gekauften Fahrzeugs. [odometer] sind (Datum, km-Stand)-Paare aus den
     * Ladevorgaengen. Zwischen zwei Staenden wird linear interpoliert, nach dem letzten mit der
     * aktuellen Fahrleistung fortgeschrieben - sonst stuende der Wertverlust still, sobald ein
     * paar Tage nicht geladen wurde.
     */
    class Depreciation(vehicle: Vehicle, odometer: List<Pair<LocalDate, Int>>, val today: LocalDate) {
        val price: Double = vehicle.purchasePrice ?: 0.0
        val purchaseDay: LocalDate? = vehicle.purchaseDate?.let { day(it) }
        val registrationDay: LocalDate? = vehicle.firstRegistration?.let { day(it) } ?: purchaseDay
        val points: List<Pair<LocalDate, Double>>
        val kmPerYear: Double

        init {
            val list = mutableListOf<Pair<LocalDate, Double>>()
            val purchaseKm = vehicle.purchaseOdometerKm
            if (purchaseDay != null && purchaseKm != null) list += purchaseDay to purchaseKm.toDouble()
            for ((d, km) in odometer.sortedWith(compareBy({ it.first }, { it.second }))) {
                if (purchaseDay != null && d.isBefore(purchaseDay)) continue
                // Ein rueckwaerts laufender Stand (Zahlendreher) wird ignoriert.
                if (list.isNotEmpty() && km < list.last().second) continue
                list += d to km.toDouble()
            }
            points = list
            kmPerYear = computeKmPerYear()
        }

        val valid: Boolean get() = price > 0 && purchaseDay != null

        private fun computeKmPerYear(): Double {
            if (points.size < 2) return KM_PER_YEAR_REFERENCE
            val (lastDay, lastKm) = points.last()
            val windowStart = lastDay.minusDays(MILEAGE_WINDOW_DAYS)
            val recent = points.filter { !it.first.isBefore(windowStart) }
            var (firstDay, firstKm) = if (recent.size >= 2) recent.first() else points.first()
            var span = daysBetween(firstDay, lastDay)
            if (span < MIN_MILEAGE_SPAN_DAYS) {
                firstDay = points.first().first
                firstKm = points.first().second
                span = daysBetween(firstDay, lastDay)
            }
            if (span < MIN_MILEAGE_SPAN_DAYS) return KM_PER_YEAR_REFERENCE
            return max(lastKm - firstKm, 0.0) / span * DAYS_PER_YEAR
        }

        fun kmAt(d: LocalDate): Double {
            // Ohne jeden km-Stand zaehlt allein das Kalenderalter.
            if (points.isEmpty()) return 0.0
            if (!d.isAfter(points.first().first)) return points.first().second
            for ((p0, p1) in points.zipWithNext()) {
                val (d0, k0) = p0
                val (d1, k1) = p1
                if (!d.isBefore(d0) && !d.isAfter(d1)) {
                    val span = daysBetween(d0, d1)
                    return if (span == 0L) k0 else k0 + (k1 - k0) * daysBetween(d0, d) / span
                }
            }
            val (lastDay, lastKm) = points.last()
            return lastKm + kmPerYear * daysBetween(lastDay, d) / DAYS_PER_YEAR
        }

        fun effectiveAge(d: LocalDate): Double {
            val reg = registrationDay
            val calendar = if (reg == null) 0.0 else max(daysBetween(reg, d), 0L) / DAYS_PER_YEAR
            return max(calendar, kmAt(d) / KM_PER_YEAR_REFERENCE)
        }

        /** Nur bei [valid] aufrufen. */
        fun valueAt(d: LocalDate): Double {
            val base = residualFraction(effectiveAge(purchaseDay ?: d))
            return price * residualFraction(effectiveAge(d)) / base
        }

        fun lossBetween(start: LocalDate, end: LocalDate): Double = max(valueAt(start) - valueAt(end), 0.0)

        fun suggestedMonthly(): Double = lossBetween(today, addMonths(today, 1))
    }

    fun odometerPoints(sessions: List<ChargingSession>): List<Pair<LocalDate, Int>> =
        sessions.mapNotNull { s -> s.odometerKm?.let { day(s.startTime) to it } }

    /**
     * Monatsbetraege fuer Wertverlust bzw. Leasing bis [until]. Eine Periode laeuft vom Kauf- bzw.
     * Leasingbeginn aus je einen Monat und zaehlt voll, sobald sie begonnen hat.
     */
    fun ownershipEntries(vehicle: Vehicle, odometer: List<Pair<LocalDate, Int>>, until: LocalDate): List<CostEntry> {
        return when (vehicle.ownershipValue) {
            Ownership.LEASING -> leasingEntries(vehicle, until)
            Ownership.PURCHASE -> purchaseEntries(vehicle, odometer, until)
            Ownership.NONE -> emptyList()
        }
    }

    private fun purchaseEntries(vehicle: Vehicle, odometer: List<Pair<LocalDate, Int>>, until: LocalDate): List<CostEntry> {
        val start = vehicle.purchaseDate?.let { day(it) } ?: return emptyList()
        val override = vehicle.depreciationMonthlyOverride
        // Wie Pythons `not (purchase_price or override)`: 0 zaehlt wie "nicht angegeben".
        if (start.isAfter(until) || ((vehicle.purchasePrice ?: 0.0) == 0.0 && (override ?: 0.0) == 0.0)) {
            return emptyList()
        }
        val curve = if (override == null) Depreciation(vehicle, odometer, until) else null
        if (curve != null && !curve.valid) return emptyList()
        val result = mutableListOf<CostEntry>()
        var k = 0
        while (true) {
            val pStart = addMonths(start, k)
            if (pStart.isAfter(until)) break
            val amount = override ?: curve!!.lossBetween(pStart, addMonths(start, k + 1))
            result += CostEntry(vehicle.id, pStart, round2(amount), DEPRECIATION)
            k++
        }
        return result
    }

    /** Laufzeit in Monaten (Perioden, die bis zum inklusiven Ende beginnen), sonst null. */
    fun leasingTermMonths(vehicle: Vehicle): Int? {
        val start = vehicle.leasingStart?.let { day(it) } ?: return null
        val end = vehicle.leasingEnd?.let { day(it) } ?: return null
        if (end.isBefore(start)) return null
        var k = 0
        while (!addMonths(start, k).isAfter(end)) k++
        return k
    }

    private fun leasingEntries(vehicle: Vehicle, until: LocalDate): List<CostEntry> {
        val start = vehicle.leasingStart?.let { day(it) } ?: return emptyList()
        if (start.isAfter(until)) return emptyList()
        val end = vehicle.leasingEnd?.let { day(it) }
        val rate = vehicle.leasingRateMonthly ?: 0.0
        val term = leasingTermMonths(vehicle)
        val down = vehicle.leasingDownPayment ?: 0.0
        val result = mutableListOf<CostEntry>()
        var k = 0
        while (true) {
            val pStart = addMonths(start, k)
            if (pStart.isAfter(until) || (end != null && pStart.isAfter(end))) break
            // Ohne Enddatum laesst sich die Sonderzahlung nicht verteilen - dann zaehlt sie im
            // ersten Monat.
            val share = if (term != null && term > 0) down / term else if (k == 0) down else 0.0
            result += CostEntry(vehicle.id, pStart, round2(rate + share), LEASING)
            k++
        }
        return result
    }

    fun ownershipInfo(vehicle: Vehicle, odometer: List<Pair<LocalDate, Int>>, today: LocalDate = LocalDate.now()): OwnershipInfo {
        return when (vehicle.ownershipValue) {
            Ownership.LEASING -> {
                val term = leasingTermMonths(vehicle)
                val down = vehicle.leasingDownPayment ?: 0.0
                if (vehicle.leasingRateMonthly != null || down != 0.0) {
                    val share = if (term != null && term > 0) down / term else 0.0
                    OwnershipInfo(ownershipMonthly = round2((vehicle.leasingRateMonthly ?: 0.0) + share))
                } else OwnershipInfo()
            }
            Ownership.PURCHASE -> {
                val curve = Depreciation(vehicle, odometer, today)
                var estimated: Double? = null
                var suggested: Double? = null
                if (curve.valid) {
                    estimated = round2(curve.valueAt(today))
                    suggested = round2(curve.suggestedMonthly())
                }
                OwnershipInfo(estimated, suggested, vehicle.depreciationMonthlyOverride ?: suggested)
            }
            Ownership.NONE -> OwnershipInfo()
        }
    }

    /**
     * Ob es fuer dieses Fahrzeug ueberhaupt etwas jenseits des Ladens gibt - sonst blendet das
     * Dashboard den Abschnitt aus.
     */
    fun hasCostData(vehicle: Vehicle, costs: List<VehicleCost>): Boolean =
        vehicle.ownershipValue != Ownership.NONE || costs.any { it.vehicleId == vehicle.id }

    /** Kosteneintraege und Wertverlust/Leasing aller [vehicles] bis [until]. */
    private fun allEntries(basis: VehicleCostBasis, vehicles: List<Vehicle>, until: LocalDate): List<CostEntry> {
        val ids = vehicles.map { it.id }.toSet()
        val entries = costEntries(basis.costs.filter { it.vehicleId in ids }, until).toMutableList()
        for (v in vehicles) {
            if (v.ownershipValue == Ownership.NONE) continue
            // Die km-Staende kommen aus der GANZEN Historie des Fahrzeugs, nicht nur aus dem
            // gefilterten Zeitraum.
            entries += ownershipEntries(v, odometerPoints(basis.sessions.filter { it.vehicleId == v.id }), until)
        }
        return entries
    }

    /**
     * Dashboard-Abschnitt "Gesamtkosten", wie `build_vehicle_costs()`: folgt Fahrzeug- und
     * Zeitraumfilter. [chargingCost] ist `total_cost` der Zusammenfassung (inkl. Grundgebuehren),
     * [kmDriven] deren `total_km_driven`. null, solange es zu keinem der betrachteten Fahrzeuge
     * Kosten oder eine Kauf-/Leasingangabe gibt.
     */
    fun summary(
        basis: VehicleCostBasis,
        vehicleId: String?,
        startDate: LocalDate?,
        endDate: LocalDate?,
        chargingCost: Double,
        kmDriven: Int?,
        today: LocalDate = LocalDate.now()
    ): VehicleCostSummary? {
        val vehicles = if (vehicleId == null) basis.vehicles else basis.vehicles.filter { it.id == vehicleId }
        if (vehicles.isEmpty()) return null
        val ids = vehicles.map { it.id }.toSet()
        val costs = basis.costs.filter { it.vehicleId in ids }
        if (vehicles.none { hasCostData(it, costs) }) return null

        val until = if (endDate != null && endDate.isBefore(today)) endDate else today
        val entries = allEntries(basis, vehicles, until).filter { startDate == null || !it.day.isBefore(startDate) }

        val byCat = LinkedHashMap<String, Double>()
        if (chargingCost != 0.0) byCat[CHARGING] = chargingCost
        for (e in entries) byCat[e.category] = (byCat[e.category] ?: 0.0) + e.amount
        val ownershipCost = byCat.filterKeys { it == DEPRECIATION || it == LEASING }.values.sum()
        val otherCost = byCat.filterKeys { it != CHARGING && it != DEPRECIATION && it != LEASING }.values.sum()
        val total = chargingCost + otherCost + ownershipCost
        return VehicleCostSummary(
            chargingCost = round2(chargingCost),
            otherCost = round2(otherCost),
            ownershipCost = round2(ownershipCost),
            totalCost = round2(total),
            costPerKm = if (kmDriven != null && kmDriven > 0) round3(total / kmDriven) else null,
            byCategory = byCat.entries
                .sortedByDescending { it.value }
                .filter { it.value != 0.0 }
                .map { CostCategoryStat(it.key, round2(it.value)) }
        )
    }

    /**
     * Gesamtkosten je Kalenderjahr und Kategorie fuer ein Fahrzeug, neuestes Jahr zuerst - wie
     * `GET /api/vehicle-costs/summary?vehicle_id=`: Laden (Saeulenpreis plus Grundgebuehr-
     * Anteil), sonstige Kosten, Wertverlust bzw. Leasing. km je Jahr: letzter Stand des Jahres
     * minus letzter Stand davor (bzw. erster des Jahres).
     */
    fun yearSummary(basis: VehicleCostBasis, vehicleId: String, today: LocalDate = LocalDate.now()): List<YearCostStat> {
        val vehicle = basis.vehicles.firstOrNull { it.id == vehicleId } ?: return emptyList()
        val sessions = basis.sessions.filter { it.vehicleId == vehicleId }.sortedBy { it.startTime }
        val byYear = sortedMapOf<Int, LinkedHashMap<String, Double>>()
        fun add(year: Int, category: String, amount: Double) {
            val cats = byYear.getOrPut(year) { LinkedHashMap() }
            cats[category] = (cats[category] ?: 0.0) + amount
        }
        for (s in sessions) {
            val amount = (s.priceTotal ?: 0.0) + (s.feeShare ?: 0.0)
            if (amount != 0.0) add(day(s.startTime).year, CHARGING, amount)
        }
        for (e in allEntries(basis, listOf(vehicle), today)) add(e.day.year, e.category, e.amount)

        val points = sessions.mapNotNull { s -> s.odometerKm?.let { day(s.startTime).year to it } }
        val kmByYear = HashMap<Int, Int>()
        for (year in points.map { it.first }.toSet()) {
            val before = points.filter { it.first < year }.map { it.second }
            val within = points.filter { it.first == year }.map { it.second }
            val start = before.lastOrNull() ?: within.first()
            kmByYear[year] = (kmByYear[year] ?: 0) + max(within.last() - start, 0)
        }

        return byYear.keys.sortedDescending().map { year ->
            val cats = byYear.getValue(year)
            val total = cats.values.sum()
            val km = kmByYear[year]?.takeIf { it > 0 }
            YearCostStat(
                year = year,
                total = round2(total),
                kmDriven = km,
                costPerKm = km?.let { round3(total / it) },
                byCategory = cats.entries
                    .sortedByDescending { it.value }
                    .filter { it.value != 0.0 }
                    .map { CostCategoryStat(it.key, round2(it.value)) }
            )
        }
    }

    /** Auf den Cent, bei exakt halben Cents zur geraden Zahl - wie Pythons `round()`. */
    private fun round2(v: Double): Double = Math.rint(v * 100) / 100.0
    private fun round3(v: Double): Double = Math.rint(v * 1000) / 1000.0
}
