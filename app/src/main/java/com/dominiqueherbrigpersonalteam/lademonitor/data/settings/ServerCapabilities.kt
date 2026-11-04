package com.dominiqueherbrigpersonalteam.lademonitor.data.settings

import android.content.Context
import android.content.SharedPreferences
import com.dominiqueherbrigpersonalteam.lademonitor.data.remote.ApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Funktionen der App, die einen bestimmten Server-Stand brauchen.
 *
 * Regel (seit 1.10): jede neue Funktion, die ein neues Server-Feld oder einen neuen Endpunkt
 * braucht, bekommt hier eine Mindestversion. Auf einem aelteren Server bleibt sie sichtbar, ist
 * aber gesperrt und sagt warum - sonst verwirft der alte Server den Wert still und der naechste
 * Abgleich setzt ihn in der App zurueck (so geschehen bei "Verteilen auf" mit einem Server vor
 * 0.49.0). Im Local-Only-Modus ist nie etwas gesperrt. Gleiche Regel wie
 * `ServerCapabilities.swift` in der iOS-App.
 */
enum class ServerFeature(val minimumServerVersion: String) {
    /** THG-Quote als Gutschrift (Kostenkategorie "thg"). */
    THG_CREDIT("0.50.0")
}

/**
 * Merkt sich die Version des Servers (aus `GET /health`, bei jedem Sync aufgefrischt) und
 * beantwortet, ob eine [ServerFeature] nutzbar ist. Die Version gilt nur fuer die Adresse, unter
 * der sie gemeldet wurde.
 */
object ServerCapabilities {

    private const val PREFS = "lademonitor_server_capabilities"
    private const val KEY_VERSION = "serverVersion"
    private const val KEY_URL = "serverVersionURL"

    private lateinit var prefs: SharedPreferences

    private val _serverVersion = MutableStateFlow<String?>(null)

    /** Zuletzt gemeldete Version (fuer Compose beobachtbar). */
    val serverVersion: StateFlow<String?> = _serverVersion

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_URL, null) == AppSettings.serverUrlString.value) {
            _serverVersion.value = prefs.getString(KEY_VERSION, null)
        }
    }

    /** Fragt `/health` ab und merkt sich die Version. Wirft nie. */
    suspend fun refresh() {
        if (AppSettings.appMode.value != AppMode.SERVER) return
        val version = runCatching { ApiClient.fetchServerVersion() }.getOrNull() ?: return
        _serverVersion.value = version
        prefs.edit()
            .putString(KEY_VERSION, version)
            .putString(KEY_URL, AppSettings.serverUrlString.value)
            .apply()
    }

    private fun currentVersion(): String? =
        if (prefs.getString(KEY_URL, null) == AppSettings.serverUrlString.value) _serverVersion.value else null

    fun supports(feature: ServerFeature): Boolean {
        if (AppSettings.appMode.value != AppMode.SERVER) return true
        // Unbekannt (noch nie synchronisiert) zaehlt als zu alt: lieber kurz gesperrt als einen
        // Wert anlegen, den der Server verwirft.
        val version = currentVersion() ?: return false
        return compare(version, feature.minimumServerVersion) >= 0
    }

    /** Vergleicht "0.49.1" mit "0.50.0" Stelle fuer Stelle; Anhaenge wie "-beta" zaehlen nicht. */
    fun compare(a: String, b: String): Int {
        fun parts(s: String) = s.split('.').map { p -> p.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
        val pa = parts(a)
        val pb = parts(b)
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return if (x < y) -1 else 1
        }
        return 0
    }
}
