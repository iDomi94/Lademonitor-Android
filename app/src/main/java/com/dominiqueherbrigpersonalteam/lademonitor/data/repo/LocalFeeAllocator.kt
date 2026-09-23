package com.dominiqueherbrigpersonalteam.lademonitor.data.repo

import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ChargingSession
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.FeeInterval
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ProviderFee
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Lokaler Port von `fees.py` (Server ab 0.27.0), wie `LocalFeeAllocator.swift` in der iOS-App:
 * legt die Grundgebuehren/Abos der Anbieter nach kWh auf die Ladevorgaenge um.
 *
 * Noetig, weil das Dashboard auch im Server-Modus lokal entsteht
 * ([AppRepository.fetchStatsSummary]). Die Regeln MUESSEN mit fees.py uebereinstimmen, sonst
 * zeigen App und Web andere Kosten - die Faelle in `tests/test_fees.py` (Server-Repo) gelten hier
 * genauso:
 *
 * - Perioden sind halboffen `[Beginn, naechster Beginn)` und werden immer vom URSPRUENGLICHEN
 *   Ankertag aus gerechnet (31.01. -> 28.02. -> 31.03.).
 * - Es zaehlen nur Perioden, die bis heute begonnen haben - dann aber voll.
 * - `endDate` ist inklusive: die Periode, in die es faellt, zaehlt voll, ihre Ladevorgaenge aber
 *   nur bis zu diesem Tag.
 * - Zugeordnet wird ueber den Kalendertag von `startTime`.
 * - Umgelegt nach kWh; haben alle Vorgaenge keine Energie, gleichmaessig. Auf den Cent gerundet,
 *   der Rest landet beim ersten groessten Vorgang.
 */
object LocalFeeAllocator {

    data class Period(
        val feeId: String,
        val providerId: String,
        /** Inklusive. */
        val start: LocalDate,
        /** Exklusive. */
        val end: LocalDate,
        val amount: Double
    )

    data class Allocation(
        /** Ladevorgang-ID -> umgelegter Anteil in EUR. */
        val shares: Map<String, Double> = emptyMap(),
        /** Perioden ohne einen Ladevorgang des Anbieters - bezahlt, aber an keinem Vorgang sichtbar. */
        val unallocated: List<Period> = emptyList()
    )

    private val zone: ZoneId get() = ZoneId.systemDefault()

    fun day(epochMillis: Long): LocalDate = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()

    /** `plusMonths` kuerzt selbst aufs Monatsende, wie `add_months()` im Server. */
    fun addMonths(anchor: LocalDate, months: Long): LocalDate = anchor.plusMonths(months)

    /** Alle Perioden einer Gebuehr, die bis [until] (einschliesslich) begonnen haben. */
    fun periods(fee: ProviderFee, until: LocalDate = LocalDate.now()): List<Period> {
        val start = day(fee.startDate)
        val endIncl = fee.endDate?.let { day(it) }
        if (start.isAfter(until)) return emptyList()

        if (fee.intervalValue == FeeInterval.ONCE) {
            val last = endIncl?.takeIf { !it.isBefore(start) } ?: start
            return listOf(Period(fee.id, fee.providerId, start, last.plusDays(1), fee.amount))
        }

        val step = if (fee.intervalValue == FeeInterval.YEARLY) 12L else 1L
        val result = mutableListOf<Period>()
        var k = 0L
        while (true) {
            val pStart = addMonths(start, k * step)
            if (pStart.isAfter(until)) break
            if (endIncl != null && pStart.isAfter(endIncl)) break
            var pEnd = addMonths(start, (k + 1) * step)
            if (endIncl != null) pEnd = minOf(pEnd, endIncl.plusDays(1))
            result += Period(fee.id, fee.providerId, pStart, pEnd, fee.amount)
            k++
        }
        return result
    }

    /** Was die Gebuehr bis heute insgesamt gekostet hat. */
    fun chargedToDate(fee: ProviderFee, until: LocalDate = LocalDate.now()): Double =
        round2(periods(fee, until).sumOf { it.amount })

    private fun split(amount: Double, sessions: List<ChargingSession>): List<Pair<String, Double>> {
        var weights = sessions.map { maxOf(it.energyKwh ?: 0.0, 0.0) }
        var total = weights.sum()
        if (total <= 0) {
            weights = List(sessions.size) { 1.0 }
            total = sessions.size.toDouble()
        }
        val shares = weights.map { round2(amount * it / total) }.toMutableList()
        val remainder = round2(amount - shares.sum())
        if (remainder != 0.0) {
            // Erster Index mit dem groessten Gewicht, wie Pythons max().
            var biggest = 0
            for (i in weights.indices) if (weights[i] > weights[biggest]) biggest = i
            shares[biggest] = round2(shares[biggest] + remainder)
        }
        return sessions.map { it.id }.zip(shares)
    }

    /**
     * Legt alle Perioden aller Gebuehren auf die Ladevorgaenge um.
     *
     * [sessions] muss die VOLLSTAENDIGE Menge sein (alle Fahrzeuge, kein Datumsfilter) - ein
     * Filter darf erst nach der Umlage greifen, sonst bekaeme der letzte Vorgang vor der
     * Filtergrenze die ganze Gebuehr. `providerId` muss in Gebuehren und Vorgaengen dieselbe Form
     * haben (siehe [LocalDataStore.feeAllocation], das beide vereinheitlicht).
     */
    fun allocate(
        fees: List<ProviderFee>,
        sessions: List<ChargingSession>,
        until: LocalDate = LocalDate.now()
    ): Allocation {
        // Feste Reihenfolge wie im Server, damit der Rundungsrest bei gleich grossen Vorgaengen
        // immer beim fruehesten landet.
        val byProvider = sessions
            .filter { it.providerId != null }
            .groupBy { it.providerId!! }
            .mapValues { (_, list) -> list.sortedWith(compareBy({ it.startTime }, { it.id })) }

        val shares = HashMap<String, Double>()
        val unallocated = mutableListOf<Period>()
        for (fee in fees) {
            val candidates = byProvider[fee.providerId].orEmpty()
            for (period in periods(fee, until)) {
                val inPeriod = candidates.filter {
                    val d = day(it.startTime)
                    !d.isBefore(period.start) && d.isBefore(period.end)
                }
                if (inPeriod.isEmpty()) {
                    unallocated += period
                    continue
                }
                for ((sessionId, share) in split(period.amount, inPeriod)) {
                    shares[sessionId] = round2((shares[sessionId] ?: 0.0) + share)
                }
            }
        }
        return Allocation(shares, unallocated)
    }

    /** Auf den Cent, bei exakt halben Cents zur geraden Zahl - wie Pythons `round()`. */
    private fun round2(v: Double): Double = Math.rint(v * 100) / 100.0
}
