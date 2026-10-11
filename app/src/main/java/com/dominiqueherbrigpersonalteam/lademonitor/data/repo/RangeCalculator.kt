package com.dominiqueherbrigpersonalteam.lademonitor.data.repo

import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ChargingSession
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.RangeCurvePoint
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.Vehicle

/** Fahrzeuge und Ladevorgaenge fuer den Reichweitenrechner, `vehicleId` in kanonischer Form. */
data class RangeBasis(
    val vehicles: List<Vehicle>,
    val sessions: List<ChargingSession>
)

/**
 * Rechnung hinter dem "Reichweitenrechner" (Reiter Tools) - Gegenstueck zu `RangeCalculator.swift`.
 *
 * Bewusst nur ein Dreisatz: Reichweite = nutzbare Energie / Verbrauch. Die Temperaturabhaengigkeit
 * des Verbrauchs rechnet allein der Server (`range_estimate.py`, `range.curve`); die App
 * interpoliert nur zwischen dessen Stuetzpunkten und kennt das Kurvenmodell nicht. Im
 * Local-Only-Modus (oder ohne Kurve) dient der Jahresdurchschnitt als Vorschlag, ohne
 * Temperatur.
 *
 * Aenderungen an den Regeln hier in der iOS-App mitziehen.
 */
object RangeCalculator {
    const val DEFAULT_SOC_START = 100.0
    const val DEFAULT_SOC_END = 10.0
    const val DEFAULT_TEMP_C = 15.0
    const val DEFAULT_CAPACITY_KWH = 77.0
    const val FALLBACK_CONSUMPTION = 18.0

    data class Result(
        /** Aus dem Akku entnommene Energie: Kapazitaet x (Start - Ende) / 100. */
        val usedKwh: Double,
        /** `null`, wenn kein sinnvoller Verbrauch vorliegt. */
        val rangeKm: Double?
    )

    fun compute(capacityKwh: Double, socStart: Double, socEnd: Double, consumptionKwhPer100km: Double): Result {
        val delta = (socStart - socEnd).coerceAtLeast(0.0)
        val used = capacityKwh.coerceAtLeast(0.0) * delta / 100
        val range = if (consumptionKwhPer100km > 0) used / consumptionKwhPer100km * 100 else null
        return Result(used, range)
    }

    /**
     * Linear zwischen den Stuetzpunkten der Server-Kurve, ausserhalb auf den ersten bzw. letzten
     * Punkt geklemmt (an den Raendern laesst der Server unplausible Werte weg - dort weiter zu
     * extrapolieren hiesse, genau das wieder einzufuehren).
     */
    fun interpolate(curve: List<RangeCurvePoint>, tempC: Double): Double? {
        val sorted = curve.sortedBy { it.tempC }
        if (sorted.isEmpty()) return null
        if (tempC <= sorted.first().tempC) return sorted.first().consumption
        if (tempC >= sorted.last().tempC) return sorted.last().consumption
        for ((a, b) in sorted.zipWithNext()) {
            if (tempC >= a.tempC && tempC <= b.tempC) {
                val span = b.tempC - a.tempC
                if (span <= 0) return a.consumption
                return a.consumption + (b.consumption - a.consumption) * (tempC - a.tempC) / span
            }
        }
        return sorted.last().consumption
    }

    /**
     * Durchschnittsverbrauch des letzten Jahres fuer EIN Fahrzeug, gerechnet wie das Dashboard
     * lokal (geladene kWh durch Strecke, also inkl. Ladeverlusten) - siehe
     * [TariffCalculator.suggestedConsumption].
     */
    fun yearlyAverageConsumption(sessions: List<ChargingSession>, vehicleId: String): Double? =
        TariffCalculator.suggestedConsumption(sessions.filter { it.vehicleId == vehicleId })
}
