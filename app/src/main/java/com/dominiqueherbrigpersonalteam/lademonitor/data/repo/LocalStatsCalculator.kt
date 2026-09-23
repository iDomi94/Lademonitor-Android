package com.dominiqueherbrigpersonalteam.lademonitor.data.repo

import com.dominiqueherbrigpersonalteam.lademonitor.LademonitorApp
import com.dominiqueherbrigpersonalteam.lademonitor.R
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ChargingSession
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ChargingType
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.MonthlyStat
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.Provider
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ProviderStat
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
     */
    fun compute(
        sessions: List<ChargingSession>,
        vehicles: List<Vehicle>,
        providers: List<Provider>,
        unallocatedFees: List<LocalFeeAllocator.Period> = emptyList()
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

        val withOdo = sorted.filter { it.odometerKm != null }
        var consumption: Double? = null
        var pricePer100km: Double? = null
        var totalKmDriven: Int? = null
        if (withOdo.size >= 2) {
            val first = withOdo.first().odometerKm
            val last = withOdo.last().odometerKm
            if (first != null && last != null) {
                val kmDriven = last - first
                totalKmDriven = kmDriven
                if (kmDriven > 0) {
                    val kwhInRange = withOdo.drop(1).sumOf { it.energyKwh ?: 0.0 }
                    consumption = round1(kwhInRange / kmDriven.toDouble() * 100)
                    // Wie die kWh ab dem zweiten Vorgang, dazu nur die nicht umgelegten
                    // Gebuehren, deren Periode in genau diesem Zeitraum beginnt.
                    val firstDay = LocalFeeAllocator.day(withOdo.first().startTime)
                    val lastDay = LocalFeeAllocator.day(withOdo.last().startTime)
                    val costInRange = withOdo.drop(1).sumOf { cost(it) } +
                        unallocatedFees.filter { it.start.isAfter(firstDay) && !it.start.isAfter(lastDay) }
                            .sumOf { it.amount }
                    pricePer100km = round2(costInRange / kmDriven.toDouble() * 100)
                }
            }
        }

        // Provider split; sessions without provider -> localized "No provider" placeholder.
        val noProviderLabel = LademonitorApp.appContext.getString(R.string.stats_no_provider)
        val providerKwh = HashMap<String, Double>()
        val providerCost = HashMap<String, Double>()
        val providerFees = HashMap<String, Double>()
        for (session in sorted) {
            val name = providers.firstOrNull { it.id == session.providerId }?.name ?: noProviderLabel
            providerKwh[name] = (providerKwh[name] ?: 0.0) + (session.energyKwh ?: 0.0)
            providerCost[name] = (providerCost[name] ?: 0.0) + cost(session)
            providerFees[name] = (providerFees[name] ?: 0.0) + (session.feeShare ?: 0.0)
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
                ProviderStat(
                    name,
                    round2(providerKwh[name]!!),
                    round2(providerCost[name]!!),
                    round2(providerFees[name] ?: 0.0)
                )
            }

        // Monthly consumption: km-weighted mean over per-vehicle consumption results.
        val consumptionByVehicle = HashMap<String, LocalConsumptionCalculator.Result>()
        for (vehicle in vehicles) {
            val vehicleSessions = sorted.filter { it.vehicleId == vehicle.id }
            consumptionByVehicle.putAll(
                LocalConsumptionCalculator.compute(vehicleSessions, vehicle.batteryCapacityKwh)
            )
        }

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
            val result = consumptionByVehicle[session.id]
            val value = result?.value
            val km = result?.km
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
            unallocatedFees = round2(unallocatedSum)
        )
    }

    private fun round1(v: Double): Double = (v * 10).roundToLong() / 10.0
    private fun round2(v: Double): Double = (v * 100).roundToLong() / 100.0
    private fun round4(v: Double): Double = (v * 10000).roundToLong() / 10000.0
}
