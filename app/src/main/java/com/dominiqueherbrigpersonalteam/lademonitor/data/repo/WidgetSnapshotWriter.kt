package com.dominiqueherbrigpersonalteam.lademonitor.data.repo

import android.content.Context
import com.dominiqueherbrigpersonalteam.lademonitor.LademonitorApp
import com.dominiqueherbrigpersonalteam.lademonitor.ui.widget.LademonitorWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/**
 * Stand fuer das Homescreen-Widget - Gegenstueck zu `WidgetSnapshotWriter.swift`.
 *
 * Das Widget rechnet nicht selbst: es liest nur, was hier in die SharedPreferences geschrieben
 * wird. Aufgerufen nach jedem Speichern oder Loeschen eines Ladevorgangs, nach jedem Laden des
 * Dashboards und wenn die App in den Hintergrund geht. Rechnet aus der lokalen Kopie, also in
 * beiden Modi gleich und ohne Netz.
 */
object WidgetSnapshotWriter {
    private const val PREFS = "widget_snapshot"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    data class Snapshot(
        val monthSessions: Int,
        val monthKwh: Double,
        val monthCost: Double,
        val lastStartTime: Long?,
        val lastEnergyKwh: Double?,
        val lastCost: Double?,
        val lastProviderName: String?,
        val lastSocStart: Int?,
        val lastSocEnd: Int?
    )

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun refreshAsync() {
        scope.launch { refresh() }
    }

    suspend fun refresh() {
        try {
            val now = System.currentTimeMillis()
            val zone = ZoneId.systemDefault()
            val monthStart = LocalDate.now(zone).withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val all = LocalDataStore.fetchSessions(null, null, null)
            val month = all.filter { it.startTime in monthStart..now }
            val canonical = LocalDataStore.canonicalProviderIds()
            val names = LocalDataStore.fetchProviders().associate { it.id to it.name }

            // Nur Grundgebuehren, die an Ladevorgaengen haengen - wie das Dashboard mit
            // Monatsfilter, ohne die Perioden ohne Vorgang.
            val cost = month.sumOf { it.effectiveTotal ?: 0.0 }
            val kwh = month.sumOf { it.energyKwh ?: 0.0 }
            val last = all.filter { it.startTime <= now }.maxByOrNull { it.startTime }

            val context = LademonitorApp.appContext
            prefs(context).edit().apply {
                putBoolean("present", true)
                putInt("monthSessions", month.size)
                putFloat("monthKwh", kwh.toFloat())
                putFloat("monthCost", cost.toFloat())
                if (last == null) remove("lastStartTime") else putLong("lastStartTime", last.startTime)
                putOptional("lastEnergyKwh", last?.energyKwh)
                putOptional("lastCost", last?.effectiveTotal)
                putString("lastProviderName", last?.providerId?.let { names[canonical[it] ?: it] })
                if (last?.socStart == null) remove("lastSocStart") else putInt("lastSocStart", last.socStart)
                if (last?.socEnd == null) remove("lastSocEnd") else putInt("lastSocEnd", last.socEnd)
            }.apply()
            LademonitorWidgetProvider.updateAll(context)
        } catch (e: Exception) {
            // Ein veraltetes Widget ist kein Grund, irgendetwas anderes scheitern zu lassen.
        }
    }

    /** null, solange die App noch nie einen Stand geschrieben hat. */
    fun read(context: Context): Snapshot? {
        val p = prefs(context)
        if (!p.getBoolean("present", false)) return null
        return Snapshot(
            monthSessions = p.getInt("monthSessions", 0),
            monthKwh = p.getFloat("monthKwh", 0f).toDouble(),
            monthCost = p.getFloat("monthCost", 0f).toDouble(),
            lastStartTime = if (p.contains("lastStartTime")) p.getLong("lastStartTime", 0) else null,
            lastEnergyKwh = if (p.contains("lastEnergyKwh")) p.getFloat("lastEnergyKwh", 0f).toDouble() else null,
            lastCost = if (p.contains("lastCost")) p.getFloat("lastCost", 0f).toDouble() else null,
            lastProviderName = p.getString("lastProviderName", null),
            lastSocStart = if (p.contains("lastSocStart")) p.getInt("lastSocStart", 0) else null,
            lastSocEnd = if (p.contains("lastSocEnd")) p.getInt("lastSocEnd", 0) else null
        )
    }

    private fun android.content.SharedPreferences.Editor.putOptional(key: String, value: Double?) {
        if (value == null) remove(key) else putFloat(key, value.toFloat())
    }
}
