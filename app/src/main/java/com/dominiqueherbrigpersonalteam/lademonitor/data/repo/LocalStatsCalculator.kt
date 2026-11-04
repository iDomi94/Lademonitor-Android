package com.dominiqueherbrigpersonalteam.lademonitor.data.repo

import com.dominiqueherbrigpersonalteam.lademonitor.LademonitorApp
import com.dominiqueherbrigpersonalteam.lademonitor.R
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ChargingPlace
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ChargingSession
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ChargingType
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.MonthlyStat
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.PlaceStat
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.Provider
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ProviderStat
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.SolarShare
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.StatsSummary
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.Vehicle
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToLong

/**
 * Local port of the server's `routers/stats.py`, matching the iOS `LocalStatsCalculator`. Order,
 * rounding and sign conventions are kept identical so numbers don't visibly "jump" when switching
 * between local-only and server mode.
 */
object LocalStatsCalculator {

    private val monthFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM")

    private fun monthKey(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(monthFormatter)

    /**
     * [sessions] muessen `feeShare` bereits tragen ([LocalDataStore.fetchSessions]),
     * [unallocatedFees] sind die schon nach Fahrzeug und Zeitraum gefilterten Perioden ohne
     * Ladevorgang ([AppRepository.fetchStatsSummary]) - wie in stats.py zaehlen Grundgebuehren in
     * allen Kostenkennzahlen mit.
     *
     * [canonicalProviderIds] bildet lokale wie Server-IDs eines Anbieters auf `Provider.id` ab
     * ([LocalDataStore.canonicalProviderIds]) - ein Ladevorgang kann seinen Anbieter noch unter
     * der lokalen ID kennen, obwohl der inzwischen eine Server-ID hat.
     *
     * [sessions] muessen ausserdem `consumptionKwhPer100km`/`consumptionKm` ueber die ganze
     * Historie tragen ([LocalDataStore.fetchSessions]); [history] sind alle Vorgaenge der
     * betrachteten Fahrzeuge OHNE Zeitraumfilter, fuer den km-Anker vor dem Zeitraum.
     */
    fun compute(
        sessions: List<ChargingSession>,
        vehicles: List<Vehicle>,
        providers: List<Provider>,
        unallocatedFees: List<LocalFeeAllocator.Period> = emptyList(),
        canonicalProviderIds: Map<String, String> = emptyMap(),
        history: List<ChargingSession>? = null
    ): StatsSummary {
        val sorted = sessions.sortedBy { it.startTime }

        fun cost(s: ChargingSession): Double = (s.priceTotal ?: 0.0) + (s.feeShare ?: 0.0)

        val totalSessions = sorted.size
        val totalKwh = round2(sorted.sumOf { it.energyKwh ?: 0.0 })
        val sessionFees = sorted.sumOf { it.feeShare ?: 0.0 }
        val unallocatedSum = unallocatedFees.sumOf { it.amount }
        val totalFees = sessionFees + unallocatedSum
        val totalCost = round2(sorted.sumOf { it.priceTotal ?: 0.0 } + totalFees)
        val avgPrice = if (totalKwh > 0) round4(totalCost / totalKwh) else null

        val acKwh = sorted.filter { it.chargingTypeValue == ChargingType.AC }.sumOf { it.energyKwh ?: 0.0 }
        val dcKwh = sorted.filter { it.chargingTypeValue == ChargingType.DC }.sumOf { it.energyKwh ?: 0.0 }
        val typedKwh = acKwh + dcKwh
        val acShare = if (typedKwh > 0) round1(acKwh / typedKwh * 100) else null
        val dcShare = if (typedKwh > 0) round1(dcKwh / typedKwh * 100) else null

        // Sonnenanteil nur ueber die Vorgaenge mit bekanntem Netzbezug (Server ab 0.41.0 bzw.
        // Home Assistant) - Vorgaenge ohne Angabe wuerden den Anteil sonst kuenstlich druecken.
        val shares = sorted.mapNotNull { it.solarShare }
        val solarShare = if (shares.isEmpty()) null else SolarShare(
            solarKwh = round2(shares.sumOf { it.solarKwh }),
            gridKwh = round2(shares.sumOf { it.gridKwh })
        )

        // Verbrauch, Preis je 100 km und km - wie stats.py seit 0.49.1. Vorher: kWh ab dem zweiten
        // Vorgang im Zeitraum / km zwischen erstem und letztem km-Stand. Bei kurzen Zeitraeumen hing
        // das am Akkustand an den Raendern ("Monat bis jetzt", 04.11.2026: zwei Ladungen mit 19,4 und
        // 20,6 kWh/100 km ergaben 29,5). Jetzt das km-gewichtete Mittel der Einzelwerte, die
        // LocalDataStore ueber die GANZE Historie rechnet - also genau die Werte aus der Liste.
        var consumptionNum = 0.0
        var consumptionKmSum = 0.0
        for (session in sorted) {
            val value = session.consumptionKwhPer100km
            val km = session.consumptionKm
            if (value != null && km != null && km > 0) {
                consumptionNum += value * km
                consumptionKmSum += km
            }
        }
        val rawConsumption = if (consumptionKmSum > 0) consumptionNum / consumptionKmSum else null
        val consumption = rawConsumption?.let { round1(it) }
        // Verbrauch x Ø Preis je kWh (inkl. Grundgebuehren) - Kosten / km haette denselben
        // Randfehler.
        val pricePer100km = rawConsumption?.takeIf { totalKwh > 0 }?.let { round2(it * totalCost / totalKwh) }

        // km je Fahrzeug: letzter Stand im Zeitraum minus Anker (letzter Stand VOR dem Zeitraum aus
        // [history], sonst der erste im Zeitraum), wie stats.py/review.py. Summiert, damit bei
        // mehreren Fahrzeugen nicht Kilometerstaende verschiedener Autos voneinander abgezogen werden.
        var totalKmDriven: Int? = null
        val firstStart = sorted.firstOrNull()?.startTime
        val inRangeIds = sorted.map { it.id }.toSet()
        val readingsSource = (history ?: sorted).sortedBy { it.startTime }
        for (vehicleId in sorted.map { it.vehicleId }.toSet()) {
            val readings = readingsSource.filter { it.vehicleId == vehicleId && it.odometerKm != null }
            val inside = readings.filter { it.id in inRangeIds }
            val last = inside.lastOrNull()?.odometerKm ?: continue
            val firstInside = inside.first().odometerKm ?: continue
            val before = if (firstStart == null) emptyList()
                else readings.filter { it.startTime < firstStart && it.id !in inRangeIds }
            if (before.isEmpty() && inside.size < 2) continue
            val anchor = before.lastOrNull()?.odometerKm ?: firstInside
            totalKmDriven = (totalKmDriven ?: 0) + maxOf(last - anchor, 0)
        }

        // Provider split; sessions without provider -> localized "No provider" placeholder.
        val noProviderLabel = LademonitorApp.appContext.getString(R.string.stats_no_provider)
        val providerKwh = HashMap<String, Double>()
        val providerCost = HashMap<String, Double>()
        val providerFees = HashMap<String, Double>()
        val providerCount = HashMap<String, Int>()
        val providerUnpriced = HashMap<String, Int>()
        // Je Anbieter und Lade-Art: kWh und Kosten fuer den Ø Preis je Lade-Art.
        val providerTyped = HashMap<String, HashMap<ChargingType, Pair<Double, Double>>>()
        for (session in sorted) {
            val name = providers.firstOrNull { it.id == session.providerId }?.name ?: noProviderLabel
            providerKwh[name] = (providerKwh[name] ?: 0.0) + (session.energyKwh ?: 0.0)
            providerCost[name] = (providerCost[name] ?: 0.0) + cost(session)
            providerFees[name] = (providerFees[name] ?: 0.0) + (session.feeShare ?: 0.0)
            providerCount[name] = (providerCount[name] ?: 0) + 1
            if (session.priceTotal == null) providerUnpriced[name] = (providerUnpriced[name] ?: 0) + 1
            session.chargingTypeValue?.let { type ->
                val byType = providerTyped.getOrPut(name) { HashMap() }
                val (kwh, c) = byType[type] ?: (0.0 to 0.0)
                byType[type] = (kwh + (session.energyKwh ?: 0.0)) to (c + cost(session))
            }
        }
        for (period in unallocatedFees) {
            val name = providers.firstOrNull { it.id == period.providerId }?.name ?: noProviderLabel
            providerKwh[name] = providerKwh[name] ?: 0.0  // Anbieter auch ohne kWh fuehren
            providerCost[name] = (providerCost[name] ?: 0.0) + period.amount
            providerFees[name] = (providerFees[name] ?: 0.0) + period.amount
        }
        val byProvider = providerKwh.keys
            .sortedByDescending { providerKwh[it]!! }
            .map { name ->
                val kwh = providerKwh[name]!!
                val ac = providerTyped[name]?.get(ChargingType.AC)
                val dc = providerTyped[name]?.get(ChargingType.DC)
                ProviderStat(
                    name,
                    round2(kwh),
                    round2(providerCost[name]!!),
                    round2(providerFees[name] ?: 0.0),
                    sessionCount = providerCount[name] ?: 0,
                    sessionsWithoutPrice = providerUnpriced[name] ?: 0,
                    avgPricePerKwh = if (kwh > 0) round4(providerCost[name]!! / kwh) else null,
                    acKwh = ac?.let { round2(it.first) },
                    acAvgPricePerKwh = ac?.takeIf { it.first > 0 }?.let { round4(it.second / it.first) },
                    dcKwh = dc?.let { round2(it.first) },
                    dcAvgPricePerKwh = dc?.takeIf { it.first > 0 }?.let { round4(it.second / it.first) }
                )
            }

        val byPlace = buildByPlace(sorted, providers, unallocatedFees, canonicalProviderIds) { cost(it) }

        // Monatlicher Verbrauch: km-gewichtet ueber dieselben Einzelwerte wie oben (aus der ganzen
        // Historie - sonst fehlte dem ersten Vorgang im Zeitraum sein Vorgaenger).
        val monthlyCost = HashMap<String, Double>()
        val monthlyFees = HashMap<String, Double>()
        val monthlyKwh = HashMap<String, Double>()
        val monthlyCount = HashMap<String, Int>()
        val monthlyConsumptionNum = HashMap<String, Double>()
        val monthlyConsumptionKm = HashMap<String, Double>()
        // Nicht umgelegte Gebuehren im Monat, in dem ihre Periode beginnt.
        for (period in unallocatedFees) {
            val key = period.start.format(monthFormatter)
            monthlyCost[key] = (monthlyCost[key] ?: 0.0) + period.amount
            monthlyFees[key] = (monthlyFees[key] ?: 0.0) + period.amount
        }
        for (session in sorted) {
            val key = monthKey(session.startTime)
            monthlyCost[key] = (monthlyCost[key] ?: 0.0) + cost(session)
            monthlyFees[key] = (monthlyFees[key] ?: 0.0) + (session.feeShare ?: 0.0)
            monthlyKwh[key] = (monthlyKwh[key] ?: 0.0) + (session.energyKwh ?: 0.0)
            monthlyCount[key] = (monthlyCount[key] ?: 0) + 1
            val value = session.consumptionKwhPer100km
            val km = session.consumptionKm
            if (value != null && km != null && km > 0) {
                monthlyConsumptionNum[key] = (monthlyConsumptionNum[key] ?: 0.0) + value * km
                monthlyConsumptionKm[key] = (monthlyConsumptionKm[key] ?: 0.0) + km
            }
        }
        val monthly = monthlyCost.keys.sortedDescending().map { month ->
            val km = monthlyConsumptionKm[month] ?: 0.0
            MonthlyStat(
                month = month,
                totalCost = round2(monthlyCost[month] ?: 0.0),
                totalKwh = round2(monthlyKwh[month] ?: 0.0),
                sessionCount = monthlyCount[month] ?: 0,
                avgConsumptionKwhPer100km = if (km > 0) round1(monthlyConsumptionNum[month]!! / km) else null,
                totalFees = round2(monthlyFees[month] ?: 0.0)
            )
        }

        return StatsSummary(
            totalSessions = totalSessions,
            totalKwh = totalKwh,
            totalCost = totalCost,
            avgPricePerKwh = avgPrice,
            avgConsumptionKwhPer100km = consumption,
            pricePer100km = pricePer100km,
            acSharePct = acShare,
            dcSharePct = dcShare,
            acKwh = round2(acKwh),
            dcKwh = round2(dcKwh),
            totalKmDriven = totalKmDriven,
            byProvider = byProvider,
            monthly = monthly,
            totalFees = round2(totalFees),
            unallocatedFees = round2(unallocatedSum),
            byPlace = byPlace,
            solarShare = solarShare
        )
    }

    /**
     * Kennzahlen je Ladeort-Art, wie `build_by_place()` in stats.py. Leer, solange kein Vorgang
     * "Zuhause" oder "Arbeit" ist - eine Aufteilung mit nur "Unterwegs" sagt nichts. Vorgaenge
     * ohne (bekannten) Anbieter zaehlen als unterwegs; Perioden ohne Ladevorgang kommen erst
     * dazu, wenn es ueberhaupt eine Aufteilung gibt.
     */
    internal fun buildByPlace(
        sessions: List<ChargingSession>,
        providers: List<Provider>,
        unallocatedFees: List<LocalFeeAllocator.Period>,
        canonicalProviderIds: Map<String, String>,
        cost: (ChargingSession) -> Double
    ): List<PlaceStat> {
        val placeById = providers.associate { it.id to ChargingPlace.from(it.chargingPlace) }
        fun placeOf(providerId: String?): ChargingPlace {
            if (providerId == null) return ChargingPlace.PUBLIC
            val id = canonicalProviderIds[providerId] ?: providerId
            return placeById[id] ?: ChargingPlace.PUBLIC
        }

        val count = HashMap<ChargingPlace, Int>()
        val kwh = HashMap<ChargingPlace, Double>()
        val total = HashMap<ChargingPlace, Double>()
        for (s in sessions) {
            val place = placeOf(s.providerId)
            count[place] = (count[place] ?: 0) + 1
            kwh[place] = (kwh[place] ?: 0.0) + (s.energyKwh ?: 0.0)
            total[place] = (total[place] ?: 0.0) + cost(s)
        }
        if ((count[ChargingPlace.HOME] ?: 0) == 0 && (count[ChargingPlace.WORK] ?: 0) == 0) return emptyList()
        for (period in unallocatedFees) {
            val place = placeOf(period.providerId)
            total[place] = (total[place] ?: 0.0) + period.amount
        }
        return ChargingPlace.entries.mapNotNull { place ->
            val n = count[place] ?: 0
            val k = kwh[place] ?: 0.0
            val c = total[place] ?: 0.0
            if (n == 0 && c == 0.0) return@mapNotNull null
            PlaceStat(
                place = place.raw,
                sessionCount = n,
                totalKwh = round2(k),
                totalCost = round2(c),
                avgPricePerKwh = if (k != 0.0) round4(c / k) else null
            )
        }
    }

    private fun round1(v: Double): Double = (v * 10).roundToLong() / 10.0
    private fun round2(v: Double): Double = (v * 100).roundToLong() / 100.0
    private fun round4(v: Double): Double = (v * 10000).roundToLong() / 10000.0
}
