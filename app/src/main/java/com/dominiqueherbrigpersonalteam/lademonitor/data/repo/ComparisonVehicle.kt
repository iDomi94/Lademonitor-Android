package com.dominiqueherbrigpersonalteam.lademonitor.data.repo

import android.content.Context
import com.dominiqueherbrigpersonalteam.lademonitor.LademonitorApp
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.CombustionVehicleOut
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.CombustionVehiclePayload
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Das Vergleichsfahrzeug (Verbrenner) fuer die Ersparnis im Rueckblick - Gegenstueck zu
 * `ComparisonVehicleStore.swift`. Global je Nutzer: im Server-Modus synchronisiert ueber
 * `GET/PUT /api/combustion-vehicle` (Server ab 0.36.0, siehe [SyncService]), im Local-Only-Modus
 * nur hier.
 *
 * Liegt in SharedPreferences samt Dirty-Flag statt in Room - es ist genau EIN Datensatz ohne
 * Fremdschluessel, eine Tabelle samt Migration waere dafuer zu viel. Die Zahlen liegen als String,
 * damit 0.36 auch als 0.36 zurueckkommt (Float wuerde runden).
 */
object ComparisonVehicleStore {

    /** Grenzen wie `schemas.CombustionVehicleIn` im Server - ausserhalb antwortet er mit 422. */
    val CONSUMPTION_RANGE = 1.0..30.0
    val PRICE_RANGE = 0.5..5.0
    val GRID_RANGE = 0.0..1.5

    data class Values(
        val fuel: CombustionComparison.Fuel = CombustionComparison.Fuel.PETROL,
        val consumption: Double = CombustionComparison.Fuel.PETROL.defaultConsumption,
        val price: Double = CombustionComparison.Fuel.PETROL.defaultPrice,
        val gridCo2: Double = CombustionComparison.DEFAULT_GRID_CO2_KG_PER_KWH,
        /** false = Standardwerte, nie gespeichert. */
        val configured: Boolean = false
    ) {
        fun sameNumbers(other: Values): Boolean =
            fuel == other.fuel && consumption == other.consumption && price == other.price && gridCo2 == other.gridCo2
    }

    private const val PREFS = "comparison_vehicle"
    private const val KEY_FUEL = "fuel"
    private const val KEY_CONSUMPTION = "consumption"
    private const val KEY_PRICE = "price"
    private const val KEY_GRID = "gridCo2"
    private const val KEY_CONFIGURED = "configured"
    private const val KEY_DIRTY = "dirty"

    private val prefs
        get() = LademonitorApp.appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _values: MutableStateFlow<Values> by lazy { MutableStateFlow(load()) }
    val values: StateFlow<Values> get() = _values

    /** Lokal geaendert und noch nicht beim Server angekommen. */
    val isDirty: Boolean get() = prefs.getBoolean(KEY_DIRTY, false)

    private fun load(): Values {
        val fuel = CombustionComparison.Fuel.from(prefs.getString(KEY_FUEL, null))
        fun num(key: String, default: Double) = prefs.getString(key, null)?.toDoubleOrNull() ?: default
        return Values(
            fuel = fuel,
            consumption = num(KEY_CONSUMPTION, fuel.defaultConsumption),
            price = num(KEY_PRICE, fuel.defaultPrice),
            gridCo2 = num(KEY_GRID, CombustionComparison.DEFAULT_GRID_CO2_KG_PER_KWH),
            configured = prefs.getBoolean(KEY_CONFIGURED, false)
        )
    }

    private fun write(values: Values, dirty: Boolean) {
        prefs.edit()
            .putString(KEY_FUEL, values.fuel.raw)
            .putString(KEY_CONSUMPTION, values.consumption.toString())
            .putString(KEY_PRICE, values.price.toString())
            .putString(KEY_GRID, values.gridCo2.toString())
            .putBoolean(KEY_CONFIGURED, values.configured)
            .putBoolean(KEY_DIRTY, dirty)
            .apply()
        _values.value = values
    }

    /**
     * Speichert eine Eingabe des Nutzers (Einstellungen oder Verbrenner-Tool). Auf die Grenzen
     * des Servers gezogen - sonst bliebe ein 422 bei jedem Sync dirty liegen. Den Sync stoesst
     * [AppRepository.saveComparisonVehicle] an.
     */
    fun save(fuel: CombustionComparison.Fuel, consumption: Double, price: Double, gridCo2: Double) {
        write(
            Values(
                fuel = fuel,
                consumption = consumption.coerceIn(CONSUMPTION_RANGE),
                price = price.coerceIn(PRICE_RANGE),
                gridCo2 = gridCo2.coerceIn(GRID_RANGE),
                configured = true
            ),
            dirty = true
        )
    }

    fun payload(values: Values = _values.value) = CombustionVehiclePayload(
        fuelType = values.fuel.raw,
        consumptionLPer100km = values.consumption,
        fuelPricePerL = values.price,
        gridCo2KgPerKwh = values.gridCo2
    )

    /**
     * Nach einem erfolgreichen PUT. Nur wenn sich seitdem nichts geaendert hat - eine Eingabe
     * waehrend des Uploads bliebe sonst ungepusht liegen.
     */
    fun markPushed(pushed: Values) {
        val current = _values.value
        if (current.sameNumbers(pushed)) write(current.copy(configured = true), dirty = false)
    }

    /** Stand des Servers uebernehmen - nie ueber eine lokal noch ungepushte Eingabe. */
    fun applyFromServer(server: CombustionVehicleOut) {
        if (isDirty) return
        write(
            Values(
                fuel = CombustionComparison.Fuel.from(server.fuelType),
                consumption = server.consumptionLPer100km,
                price = server.fuelPricePerL,
                gridCo2 = server.gridCo2KgPerKwh,
                configured = server.configured
            ),
            dirty = false
        )
    }
}
