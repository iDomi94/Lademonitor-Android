package com.dominiqueherbrigpersonalteam.lademonitor.data.repo

import android.content.Context
import com.dominiqueherbrigpersonalteam.lademonitor.LademonitorApp
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ChargingSession

/**
 * Rechnung hinter "Vergleich mit Verbrenner" (Reiter Tools) - Gegenstueck zu
 * `CombustionComparison.swift`, Aenderungen an den Regeln in beiden Apps mitziehen.
 *
 * Wie der Tarifrechner bewusst nur in den Apps: die Grundlage (gefahrene km, bezahlter Strom)
 * liegt lokal vor, der Rest sind Annahmen ueber ein Vergleichsfahrzeug, die der Nutzer selbst
 * einstellt.
 */
object CombustionComparison {
    /**
     * [co2KgPerLiter]: kg CO2 je Liter bei der Verbrennung (Tank-to-Wheel), UBA-Werte. Die Vorkette
     * ist nicht enthalten - beim Strom steckt sie im Emissionsfaktor, der Vergleich faellt also
     * eher zugunsten des Verbrenners aus.
     * [upstreamCo2KgPerLiter]: Foerderung, Transport und Raffinerie (Well-to-Tank, grob 20 %
     * obendrauf). Nur im Lebenszyklus - dort steckt auch beim Strom die ganze Vorkette im
     * Emissionsfaktor.
     */
    enum class Fuel(
        val raw: String,
        val defaultConsumption: Double,
        val defaultPrice: Double,
        val co2KgPerLiter: Double,
        val upstreamCo2KgPerLiter: Double
    ) {
        PETROL("petrol", 7.0, 1.75, 2.37, 0.52),
        DIESEL("diesel", 5.8, 1.65, 2.65, 0.60);

        companion object {
            fun from(raw: String?): Fuel = entries.firstOrNull { it.raw == raw } ?: PETROL
        }
    }

    /** CO2 je kWh im deutschen Strommix (UBA, 2024 rund 0,36 kg). */
    const val DEFAULT_GRID_CO2_KG_PER_KWH = 0.36

    /**
     * Lebenszyklus: CO2 aus der Herstellung. Das Fahrzeug ohne Akku zaehlt fuer beide gleich
     * (rund 7 t fuer einen Kompakt-SUV), beim E-Auto kommt der Akku dazu. Fuer ihn nennen Studien
     * 60 bis 100 kg je kWh, je nach Zellchemie und Strommix des Werks.
     */
    const val VEHICLE_PRODUCTION_CO2_KG = 7_000.0
    const val DEFAULT_BATTERY_CO2_KG_PER_KWH = 75.0
    const val DEFAULT_BATTERY_KWH = 77.0
    const val DEFAULT_LIFETIME_KM = 200_000.0
    /** Richtwert fuer den Verbrauch, falls die eigenen Daten keinen hergeben. */
    const val FALLBACK_KWH_PER_100KM = 18.0

    /**
     * Was tatsaechlich gefahren und bezahlt wurde. Wie `price_per_100km` im Dashboard: km aus dem
     * Kilometerstand je Fahrzeug, Kosten und kWh ab dem zweiten Vorgang mit Kilometerstand.
     */
    data class Basis(val km: Double, val kwh: Double, val cost: Double, val sessionCount: Int)

    fun basis(sessions: List<ChargingSession>, since: Long?): Basis {
        var km = 0.0
        var kwh = 0.0
        var cost = 0.0
        var count = 0
        sessions.filter { it.odometerKm != null }.groupBy { it.vehicleId }.values.forEach { list ->
            val sorted = list.sortedBy { it.startTime }
            val slice = if (since == null) sorted else {
                // Ab dem letzten Vorgang VOR dem Zeitraum als Anker - sonst fehlte die Strecke
                // bis zum ersten Einstecken darin.
                val firstInside = sorted.indexOfFirst { it.startTime >= since }
                if (firstInside < 0) return@forEach
                sorted.subList(maxOf(firstInside - 1, 0), sorted.size)
            }
            val a = slice.firstOrNull()?.odometerKm ?: return@forEach
            val b = slice.lastOrNull()?.odometerKm ?: return@forEach
            if (b <= a) return@forEach
            km += (b - a).toDouble()
            slice.drop(1).forEach { s ->
                kwh += s.energyKwh ?: 0.0
                cost += (s.priceTotal ?: 0.0) + (s.feeShare ?: 0.0)
                count += 1
            }
        }
        return Basis(km, kwh, cost, count)
    }

    data class Input(
        val litersPer100km: Double,
        val pricePerLiter: Double,
        val co2KgPerLiter: Double,
        /** Nur fuer den Lebenszyklus, siehe [Fuel.upstreamCo2KgPerLiter]. */
        val upstreamCo2KgPerLiter: Double,
        val gridCo2KgPerKwh: Double
    )

    data class Result(
        val liters: Double,
        val combustionCost: Double,
        val electricCost: Double,
        /** Positiv = das E-Auto war guenstiger. */
        val savings: Double,
        val combustionCostPer100km: Double?,
        val electricCostPer100km: Double?,
        val combustionCo2Kg: Double,
        val electricCo2Kg: Double,
        /** Treibstoffpreis, bei dem beide gleich teuer gewesen waeren. */
        val breakEvenPricePerLiter: Double?
    )

    data class LifecycleInput(val lifetimeKm: Double, val batteryKwh: Double, val batteryCo2KgPerKwh: Double)

    data class Lifecycle(
        val electricProductionCo2Kg: Double,
        val combustionProductionCo2Kg: Double,
        val electricTotalCo2Kg: Double,
        val combustionTotalCo2Kg: Double,
        val electricCo2Per100km: Double,
        val combustionCo2Per100km: Double,
        val electricEnergyCost: Double,
        val combustionEnergyCost: Double,
        /** Ab dieser Laufleistung ist der Herstellungs-Rucksack eingeholt; null, wenn nie. */
        val co2BreakEvenKm: Double?
    )

    /** Verbrauch je km aus den eigenen Daten (geladene kWh, inkl. Ladeverlusten), sonst Richtwert. */
    fun kwhPerKm(basis: Basis): Double =
        if (basis.km > 0 && basis.kwh > 0) basis.kwh / basis.km else FALLBACK_KWH_PER_100KM / 100

    fun costPerKm(basis: Basis): Double? = if (basis.km > 0) basis.cost / basis.km else null

    fun lifecycle(basis: Basis, input: Input, lifecycle: LifecycleInput): Lifecycle {
        val km = lifecycle.lifetimeKm.coerceAtLeast(1.0)
        val litersPerKm = input.litersPer100km.coerceAtLeast(0.0) / 100
        val electricPerKm = kwhPerKm(basis) * input.gridCo2KgPerKwh
        val combustionPerKm = litersPerKm * (input.co2KgPerLiter + input.upstreamCo2KgPerLiter)
        val electricProduction = VEHICLE_PRODUCTION_CO2_KG +
            lifecycle.batteryKwh.coerceAtLeast(0.0) * lifecycle.batteryCo2KgPerKwh.coerceAtLeast(0.0)
        val combustionProduction = VEHICLE_PRODUCTION_CO2_KG
        val electricTotal = electricProduction + electricPerKm * km
        val combustionTotal = combustionProduction + combustionPerKm * km
        val advantage = combustionPerKm - electricPerKm
        return Lifecycle(
            electricProductionCo2Kg = electricProduction,
            combustionProductionCo2Kg = combustionProduction,
            electricTotalCo2Kg = electricTotal,
            combustionTotalCo2Kg = combustionTotal,
            electricCo2Per100km = electricTotal / km * 100,
            combustionCo2Per100km = combustionTotal / km * 100,
            electricEnergyCost = (costPerKm(basis) ?: 0.0) * km,
            combustionEnergyCost = litersPerKm * input.pricePerLiter.coerceAtLeast(0.0) * km,
            co2BreakEvenKm = if (advantage > 0) (electricProduction - combustionProduction) / advantage else null
        )
    }

    fun compute(basis: Basis, input: Input): Result {
        val liters = basis.km * input.litersPer100km.coerceAtLeast(0.0) / 100
        val combustion = liters * input.pricePerLiter.coerceAtLeast(0.0)
        fun per100(v: Double) = if (basis.km > 0) v / basis.km * 100 else null
        return Result(
            liters = liters,
            combustionCost = combustion,
            electricCost = basis.cost,
            savings = combustion - basis.cost,
            combustionCostPer100km = per100(combustion),
            electricCostPer100km = per100(basis.cost),
            combustionCo2Kg = liters * input.co2KgPerLiter,
            electricCo2Kg = basis.kwh * input.gridCo2KgPerKwh,
            breakEvenPricePerLiter = if (liters > 0) basis.cost / liters else null
        )
    }
}

/** Zuletzt eingestellte Werte des Verbrenner-Vergleichs, nur auf diesem Geraet. */
object CombustionComparisonSettings {
    private const val PREFS = "combustion_comparison"
    private val prefs
        get() = LademonitorApp.appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var lastYearOnly: Boolean
        get() = prefs.getBoolean("lastYearOnly", true)
        set(value) { prefs.edit().putBoolean("lastYearOnly", value).apply() }

    var fuel: CombustionComparison.Fuel
        get() = CombustionComparison.Fuel.from(prefs.getString("fuel", null))
        set(value) { prefs.edit().putString("fuel", value.raw).apply() }

    fun consumption(fuel: CombustionComparison.Fuel): Double =
        prefs.getFloat("consumption", fuel.defaultConsumption.toFloat()).toDouble()

    fun price(fuel: CombustionComparison.Fuel): Double =
        prefs.getFloat("price", fuel.defaultPrice.toFloat()).toDouble()

    var gridCo2: Double
        get() = prefs.getFloat("gridCo2", CombustionComparison.DEFAULT_GRID_CO2_KG_PER_KWH.toFloat()).toDouble()
        set(value) { prefs.edit().putFloat("gridCo2", value.toFloat()).apply() }

    /** "total" | "per100km" | "lifecycle" */
    var mode: String
        get() = prefs.getString("mode", "per100km") ?: "per100km"
        set(value) { prefs.edit().putString("mode", value).apply() }

    var lifetimeKm: Double
        get() = prefs.getFloat("lifetimeKm", CombustionComparison.DEFAULT_LIFETIME_KM.toFloat()).toDouble()
        set(value) { prefs.edit().putFloat("lifetimeKm", value.toFloat()).apply() }

    var batteryCo2: Double
        get() = prefs.getFloat("batteryCo2", CombustionComparison.DEFAULT_BATTERY_CO2_KG_PER_KWH.toFloat()).toDouble()
        set(value) { prefs.edit().putFloat("batteryCo2", value.toFloat()).apply() }

    /** null = Akkukapazitaet des Fahrzeugs verwenden. */
    var batteryKwh: Double?
        get() = if (prefs.contains("batteryKwh")) prefs.getFloat("batteryKwh", 0f).toDouble() else null
        set(value) {
            prefs.edit().apply { if (value == null) remove("batteryKwh") else putFloat("batteryKwh", value.toFloat()) }.apply()
        }

    fun save(consumption: Double, price: Double) {
        prefs.edit().putFloat("consumption", consumption.toFloat()).putFloat("price", price.toFloat()).apply()
    }
}
