package com.dominiqueherbrigpersonalteam.lademonitor.data.settings

import android.content.Context
import com.dominiqueherbrigpersonalteam.lademonitor.LademonitorApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Einstellungen des Ladebalkens in der Ladevorgangsliste (rote Markierungen,
 * z.B. Mindest- und Zielladestand). Liegen bewusst nur auf diesem Geraet
 * (SharedPreferences) und werden NICHT synchronisiert - reine Darstellung.
 * Als [StateFlow] gespiegelt, damit die Liste sofort auf Aenderungen reagiert.
 */
object ChargeBarSettings {

    data class Config(
        val lowerMarkerEnabled: Boolean = true,
        val lowerMarker: Int = DEFAULT_LOWER_MARKER,
        val upperMarkerEnabled: Boolean = true,
        val upperMarker: Int = DEFAULT_UPPER_MARKER
    ) {
        /** Untere Markierung, falls eingeschaltet. */
        val lower: Int? get() = if (lowerMarkerEnabled) lowerMarker else null
        /** Obere Markierung, falls eingeschaltet. */
        val upper: Int? get() = if (upperMarkerEnabled) upperMarker else null
    }

    const val DEFAULT_LOWER_MARKER = 10
    const val DEFAULT_UPPER_MARKER = 100

    private const val PREFS = "charge_bar"
    private const val KEY_LOWER_ENABLED = "chargeBar.lowerMarkerEnabled"
    private const val KEY_LOWER = "chargeBar.lowerMarker"
    private const val KEY_UPPER_ENABLED = "chargeBar.upperMarkerEnabled"
    private const val KEY_UPPER = "chargeBar.upperMarker"

    private val prefs
        get() = LademonitorApp.appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _config: MutableStateFlow<Config> by lazy {
        MutableStateFlow(
            Config(
                lowerMarkerEnabled = prefs.getBoolean(KEY_LOWER_ENABLED, true),
                lowerMarker = prefs.getInt(KEY_LOWER, DEFAULT_LOWER_MARKER).coerceIn(0, 100),
                upperMarkerEnabled = prefs.getBoolean(KEY_UPPER_ENABLED, true),
                upperMarker = prefs.getInt(KEY_UPPER, DEFAULT_UPPER_MARKER).coerceIn(0, 100)
            )
        )
    }
    val config: StateFlow<Config> get() = _config

    fun setLowerMarkerEnabled(value: Boolean) = update(_config.value.copy(lowerMarkerEnabled = value))
    fun setLowerMarker(value: Int) = update(_config.value.copy(lowerMarker = value.coerceIn(0, 100)))
    fun setUpperMarkerEnabled(value: Boolean) = update(_config.value.copy(upperMarkerEnabled = value))
    fun setUpperMarker(value: Int) = update(_config.value.copy(upperMarker = value.coerceIn(0, 100)))

    private fun update(config: Config) {
        _config.value = config
        prefs.edit()
            .putBoolean(KEY_LOWER_ENABLED, config.lowerMarkerEnabled)
            .putInt(KEY_LOWER, config.lowerMarker)
            .putBoolean(KEY_UPPER_ENABLED, config.upperMarkerEnabled)
            .putInt(KEY_UPPER, config.upperMarker)
            .apply()
    }
}
