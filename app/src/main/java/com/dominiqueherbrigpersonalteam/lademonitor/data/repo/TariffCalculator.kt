package com.dominiqueherbrigpersonalteam.lademonitor.data.repo

import android.content.Context
import com.dominiqueherbrigpersonalteam.lademonitor.LademonitorApp
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ChargingSession
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ChargingType
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.FeeInterval
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ProviderFee
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Rechnung hinter "Lohnt sich der Tarif?" (Reiter Tools) - Port von `TariffCalculator.swift`.
 *
 * Bewusst nur in den Apps, ohne Server: es ist eine Vorausrechnung ueber ein paar Schieberegler,
 * die Vorschlagswerte kommen aus der lokalen Kopie und funktionieren damit auch im
 * Local-Only-Modus. Den RUECKBLICK (was hat ein Abo tatsaechlich gekostet) liefert schon die
 * Gebuehrenumlage ([LocalFeeAllocator], `feeShare`).
 *
 * Aenderungen an den Regeln hier in der iOS-App mitziehen.
 */
object TariffCalculator {
    /** Durchschnittliche Monatslaenge fuer "km pro Tag -> km pro Monat". */
    const val DAYS_PER_MONTH = 30.44

    /**
     * Zeitfenster der Vorschlaege: km und Anteil aus den letzten 90 Tagen, Verbrauch und
     * Vergleichspreis aus dem letzten Jahr (die schwanken mit der Jahreszeit, ein Quartal waere
     * einseitig).
     */
    const val RECENT_DAYS = 90L
    const val YEAR_DAYS = 365L

    /** Unter zwei Wochen Abstand zwischen zwei Kilometerstaenden ist eine Hochrechnung Zufall. */
    const val MIN_SPAN_DAYS = 14.0

    private const val DAY_MILLIS = 86_400_000.0

    data class Input(
        val tariffPricePerKwh: Double,
        val monthlyFee: Double,
        /** Anteil der gefahrenen Energie, der beim Tarif geladen wird (0..100). */
        val sharePct: Double,
        val kmPerMonth: Double,
        val consumptionKwhPer100km: Double,
        /** Preis je kWh ohne diesen Tarif; null = kein Vergleich moeglich. */
        val comparisonPricePerKwh: Double?
    )

    enum class Verdict { CHEAPER, MORE_EXPENSIVE, EQUAL, UNKNOWN }

    data class Result(
        /** kWh im Monat, die beim Tarif geladen werden. */
        val kwhPerMonth: Double,
        /** null, wenn keine einzige kWh beim Tarif geladen wird. */
        val effectivePricePerKwh: Double?,
        /** Grundgebuehr je geladene kWh, der Aufschlag auf den Tarifpreis. */
        val feePerKwh: Double?,
        val costWithTariff: Double,
        val costWithoutTariff: Double?,
        /** Positiv = mit Tarif guenstiger. */
        val savingsPerMonth: Double?,
        /** kWh bzw. km im Monat, ab denen der Tarif guenstiger ist. */
        val breakEvenKwh: Double?,
        val breakEvenKm: Double?,
        /** Der Tarifpreis liegt nicht unter dem Vergleichspreis - dann lohnt er sich nie. */
        val neverPaysOff: Boolean,
        val verdict: Verdict
    )

    fun compute(input: Input): Result {
        val share = input.sharePct.coerceIn(0.0, 100.0) / 100
        val kwhPerKm = input.consumptionKwhPer100km.coerceAtLeast(0.0) / 100
        val kwh = input.kmPerMonth.coerceAtLeast(0.0) * kwhPerKm * share
        val withTariff = input.monthlyFee + kwh * input.tariffPricePerKwh
        val effective = if (kwh > 0) withTariff / kwh else null
        val feePerKwh = if (kwh > 0) input.monthlyFee / kwh else null

        val comparison = input.comparisonPricePerKwh
            ?: return Result(
                kwh, effective, feePerKwh, withTariff, null, null,
                null, null, neverPaysOff = false, verdict = Verdict.UNKNOWN
            )

        val withoutTariff = kwh * comparison
        val savings = withoutTariff - withTariff
        val advantage = comparison - input.tariffPricePerKwh
        var breakEvenKwh: Double? = null
        var breakEvenKm: Double? = null
        var neverPaysOff = false
        if (advantage > 0) {
            val e = input.monthlyFee / advantage
            breakEvenKwh = e
            if (kwhPerKm * share > 0) breakEvenKm = e / (kwhPerKm * share)
        } else {
            // Ohne Preisvorteil je kWh gleicht nichts die Grundgebuehr aus. Ohne Grundgebuehr und
            // bei gleichem Preis sind beide Wege gleich teuer.
            neverPaysOff = input.monthlyFee > 0 || advantage < 0
        }

        // Unter einem halben Cent ist "guenstiger"/"teurer" Rundungsrauschen.
        val verdict = when {
            savings > 0.005 -> Verdict.CHEAPER
            savings < -0.005 -> Verdict.MORE_EXPENSIVE
            else -> Verdict.EQUAL
        }
        return Result(
            kwh, effective, feePerKwh, withTariff, withoutTariff, savings,
            breakEvenKwh, breakEvenKm, neverPaysOff, verdict
        )
    }

    // MARK: - Vorschlagswerte

    private val zone: ZoneId get() = ZoneId.systemDefault()

    private fun daysAgo(days: Long, now: Long): Long =
        Instant.ofEpochMilli(now).atZone(zone).minusDays(days).toInstant().toEpochMilli()

    /**
     * Ab dem letzten Vorgang VOR [from], damit das Fenster wirklich die ganze Spanne abdeckt und
     * nicht erst beim ersten Einstecken darin beginnt.
     */
    private fun window(sorted: List<ChargingSession>, from: Long): List<ChargingSession> {
        val firstInside = sorted.indexOfFirst { it.startTime >= from }
        if (firstInside < 0) return emptyList()
        return sorted.subList((firstInside - 1).coerceAtLeast(0), sorted.size)
    }

    /**
     * Gefahrene km im Monat, je Fahrzeug aus dem Kilometerstand hochgerechnet und ueber die
     * Fahrzeuge summiert (ein Tarif gilt fuers Konto). Nimmt die letzten 90 Tage, reicht das nicht
     * fuer zwei Wochen Abstand, die ganze Historie - aber nur fuer Fahrzeuge, die im letzten Jahr
     * ueberhaupt geladen wurden (ein verkauftes Auto soll nicht mitzaehlen).
     */
    fun suggestedKmPerMonth(sessions: List<ChargingSession>, now: Long = System.currentTimeMillis()): Double? {
        val recentFrom = daysAgo(RECENT_DAYS, now)
        val activeFrom = daysAgo(YEAR_DAYS, now)
        var total = 0.0
        var found = false
        val withOdometer = sessions.filter { it.odometerKm != null && it.startTime <= now }
        for ((_, list) in withOdometer.groupBy { it.vehicleId }) {
            val sorted = list.sortedBy { it.startTime }
            val latest = sorted.lastOrNull() ?: continue
            if (latest.startTime < activeFrom) continue
            for (candidate in listOf(window(sorted, recentFrom), sorted)) {
                val first = candidate.firstOrNull() ?: continue
                val last = candidate.last()
                val a = first.odometerKm ?: continue
                val b = last.odometerKm ?: continue
                val days = (last.startTime - first.startTime) / DAY_MILLIS
                if (days < MIN_SPAN_DAYS || b <= a) continue
                total += (b - a) / days * DAYS_PER_MONTH
                found = true
                break
            }
        }
        return if (found) total else null
    }

    /**
     * Verbrauch kWh/100 km im letzten Jahr, je Fahrzeug wie das Dashboard gerechnet (geladene kWh
     * ab dem zweiten Vorgang durch die Strecke, enthaelt also Ladeverluste - genau die Energie,
     * die man bezahlt) und ueber alle Fahrzeuge km-gewichtet zusammengefasst.
     */
    fun suggestedConsumption(sessions: List<ChargingSession>, now: Long = System.currentTimeMillis()): Double? {
        val from = daysAgo(YEAR_DAYS, now)
        var kwh = 0.0
        var km = 0.0
        val withOdometer = sessions.filter { it.odometerKm != null && it.startTime <= now }
        for ((_, list) in withOdometer.groupBy { it.vehicleId }) {
            val slice = window(list.sortedBy { it.startTime }, from)
            val a = slice.firstOrNull()?.odometerKm ?: continue
            val b = slice.last().odometerKm ?: continue
            if (b <= a) continue
            kwh += slice.drop(1).sumOf { it.energyKwh ?: 0.0 }
            km += (b - a).toDouble()
        }
        if (km <= 0 || kwh <= 0) return null
        return kwh / km * 100
    }

    /**
     * kWh-Anteil der Ladevorgaenge, auf die [matches] zutrifft, an allen geladenen kWh. Letzte 90
     * Tage, ohne Ladungen darin das letzte Jahr.
     */
    fun suggestedSharePct(
        sessions: List<ChargingSession>,
        now: Long = System.currentTimeMillis(),
        matches: (ChargingSession) -> Boolean
    ): Double? {
        for (days in listOf(RECENT_DAYS, YEAR_DAYS)) {
            val from = daysAgo(days, now)
            val inRange = sessions.filter { it.startTime in from..now && (it.energyKwh ?: 0.0) > 0 }
            val total = inRange.sumOf { it.energyKwh ?: 0.0 }
            if (total <= 0) continue
            val matched = inRange.filter(matches).sumOf { it.energyKwh ?: 0.0 }
            return matched / total * 100
        }
        return null
    }

    /**
     * Aktive Grundgebuehren eines Anbieters, auf einen Monat umgerechnet (jaehrlich / 12, einmalig
     * ueber die Laenge ihres Zeitraums). null, wenn heute keine gilt.
     */
    fun suggestedMonthlyFee(providerId: String, fees: List<ProviderFee>, today: LocalDate = LocalDate.now()): Double? {
        val active = fees.filter { fee ->
            if (fee.providerId != providerId) return@filter false
            if (LocalFeeAllocator.day(fee.startDate).isAfter(today)) return@filter false
            val end = fee.endDate?.let { LocalFeeAllocator.day(it) }
            end == null || !end.isBefore(today)
        }
        if (active.isEmpty()) return null
        return active.sumOf { fee ->
            when (fee.intervalValue) {
                FeeInterval.MONTHLY -> fee.amount
                FeeInterval.YEARLY -> fee.amount / 12
                FeeInterval.ONCE -> {
                    val start = LocalFeeAllocator.day(fee.startDate)
                    val end = LocalFeeAllocator.day(fee.endDate ?: fee.startDate)
                    val days = ChronoUnit.DAYS.between(start, end) + 1
                    fee.amount / (days / DAYS_PER_MONTH).coerceAtLeast(1.0)
                }
            }
        }
    }

    data class PaidPrice(val pricePerKwh: Double, val sessionCount: Int)

    /**
     * Nach kWh gewichteter Preis, der wirklich bezahlt wurde: Saeulenpreis plus
     * Grundgebuehrenanteil, im letzten Jahr, nur Vorgaenge mit Preis und Energie. Vorgaenge ohne
     * Anbieter zaehlen nie mit - von denen weiss man nicht, ob sie oeffentlich waren.
     */
    fun paidPrice(
        sessions: List<ChargingSession>,
        now: Long = System.currentTimeMillis(),
        matches: (ChargingSession) -> Boolean
    ): PaidPrice? {
        val from = daysAgo(YEAR_DAYS, now)
        val relevant = sessions.filter {
            it.startTime in from..now && it.providerId != null && it.priceTotal != null &&
                (it.energyKwh ?: 0.0) > 0 && matches(it)
        }
        val kwh = relevant.sumOf { it.energyKwh ?: 0.0 }
        if (kwh <= 0) return null
        val paid = relevant.sumOf { (it.priceTotal ?: 0.0) + (it.feeShare ?: 0.0) }
        return PaidPrice(paid / kwh, relevant.size)
    }

    /**
     * Automatischer Vergleichspreis "ohne Tarif": was an oeffentlichen Anbietern bezahlt wurde,
     * ohne den gerade betrachteten (dort wurde ja schon zum Tarifpreis geladen) und nur fuer
     * dieselbe Lade-Art.
     */
    fun automaticComparisonPrice(
        sessions: List<ChargingSession>,
        excludedProviderIds: Set<String>,
        selectedProviderId: String?,
        chargingType: ChargingType,
        now: Long = System.currentTimeMillis()
    ): PaidPrice? = paidPrice(sessions, now) { session ->
        val providerId = session.providerId ?: return@paidPrice false
        providerId !in excludedProviderIds &&
            providerId != selectedProviderId &&
            session.chargingType == chargingType.raw
    }
}

/**
 * Lokale Einstellungen des Tarifrechners. Bewusst NICHT synchronisiert (Entscheidung 23.09.2026):
 * auf einem zweiten Geraet waehlt man die privaten Anbieter neu ab. Gespeichert werden die
 * ABGEWAEHLTEN Anbieter, damit neue Anbieter automatisch als oeffentlich zaehlen.
 */
object TariffCalculatorSettings {
    private const val PREFS = "tariff_calculator"
    private const val KEY_EXCLUDED = "excludedProviderIds"
    private const val KEY_MODE = "comparisonMode"
    private const val KEY_MANUAL_PRICE = "manualComparisonPrice"

    private val prefs
        get() = LademonitorApp.appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Roh gespeicherte IDs. Ein Anbieter kann hier noch unter seiner lokalen ID stehen, obwohl er
     * inzwischen eine Server-ID hat - [AppRepository.tariffBasis] bildet sie deshalb ab.
     */
    val storedExcludedProviderIds: Set<String>
        get() = prefs.getStringSet(KEY_EXCLUDED, emptySet())?.toSet() ?: emptySet()

    fun setExcluded(ids: Set<String>) {
        prefs.edit().putStringSet(KEY_EXCLUDED, ids.toSet()).apply()
    }

    /** true = Automatik, false = selbst eintragen. */
    var automaticComparison: Boolean
        get() = prefs.getString(KEY_MODE, "automatic") != "manual"
        set(value) { prefs.edit().putString(KEY_MODE, if (value) "automatic" else "manual").apply() }

    var manualComparisonText: String
        get() = prefs.getString(KEY_MANUAL_PRICE, "") ?: ""
        set(value) { prefs.edit().putString(KEY_MANUAL_PRICE, value).apply() }
}
