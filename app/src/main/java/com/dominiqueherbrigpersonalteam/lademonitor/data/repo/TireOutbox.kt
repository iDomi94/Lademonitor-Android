package com.dominiqueherbrigpersonalteam.lademonitor.data.repo

import com.dominiqueherbrigpersonalteam.lademonitor.LademonitorApp
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.TireComparison
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.TireOverview
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.TireSet
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.TireSetPayload
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.TireTreadMeasurement
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.TreadMeasurementPayload
import com.dominiqueherbrigpersonalteam.lademonitor.data.net.NetworkMonitor
import com.dominiqueherbrigpersonalteam.lademonitor.data.remote.ApiClient
import com.dominiqueherbrigpersonalteam.lademonitor.data.remote.ApiException
import com.dominiqueherbrigpersonalteam.lademonitor.data.remote.Json
import com.dominiqueherbrigpersonalteam.lademonitor.data.session.SessionManager
import com.dominiqueherbrigpersonalteam.lademonitor.data.settings.AppMode
import com.dominiqueherbrigpersonalteam.lademonitor.data.settings.AppSettings
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Warteschlange fuer Reifen-Eingaben ohne Netz (Gegenstueck zu `TireOutbox.swift`).
 *
 * Reifensaetze und Profilmessungen sind - anders als Ladevorgaenge - nicht Teil des lokalen
 * Spiegels (die Auswertung liegt allein auf dem Server). Ohne Netz schlug Speichern deshalb
 * fehl. Jetzt landet eine solche Eingabe hier, wird in der Reifen-Ansicht als "wartet auf
 * Upload" gezeigt und beim naechsten Sync (oder beim naechsten Oeffnen der Ansicht mit Netz)
 * in der eingegebenen Reihenfolge hochgeladen.
 *
 * Gespeichert wird der fertig kodierte Request (Methode, Pfad, JSON-Body). Anlagen tragen
 * einen eigenen `Idempotency-Key`: kam die erste Anfrage doch an, legt ein Server ab 0.51.0
 * bei der Wiederholung nichts doppelt an.
 *
 * Ein Eintrag gehoert zu dem Konto, unter dem er erfasst wurde; nach einem Kontowechsel wird
 * er verworfen. Fehler beim Abarbeiten: kein Netz / 5xx / 408 / 429 / 401 -> Abbruch, spaeter
 * erneut; 404 beim Loeschen -> erledigt; sonstige 4xx -> bleibt mit Fehlertext stehen, wird
 * nicht mehr automatisch wiederholt und kann verworfen werden.
 */
object TireOutbox {

    @JsonClass(generateAdapter = true)
    data class Entry(
        val id: String,
        val kind: String,
        val method: String,
        val path: String,
        val body: String?,
        /** Fuer die Anzeige, schon beim Erfassen lokalisiert. */
        val label: String,
        val createdAt: Long,
        val userId: String?,
        /** Nur bei Anlagen: geht als `Idempotency-Key` mit. */
        val idempotencyKey: String?,
        /** Gesetzt, wenn der Server den Eintrag abgelehnt hat. */
        val failure: String? = null
    )

    const val CREATE_SET = "create_set"
    const val UPDATE_SET = "update_set"
    const val DELETE_SET = "delete_set"
    const val CREATE_TREAD = "create_tread"
    const val DELETE_TREAD = "delete_tread"

    private val adapter = Json.moshi.adapter<List<Entry>>(
        Types.newParameterizedType(List::class.java, Entry::class.java)
    )
    private val file: File get() = File(LademonitorApp.appContext.filesDir, "tire-outbox.json")
    private val flushLock = Mutex()

    private val _entries = MutableStateFlow(read())
    val entries: StateFlow<List<Entry>> = _entries

    /** Ob ein Fehler bedeutet "gerade kein Netz/Server" - dann wird gepuffert. */
    fun isTransient(error: Throwable): Boolean = when (error) {
        is ApiException.Network -> true
        is ApiException.Server -> error.statusCode >= 500 || error.statusCode in setOf(401, 408, 429)
        else -> false
    }

    // MARK: - Erfassen

    fun enqueueCreateSet(payload: TireSetPayload, label: String, idempotencyKey: String = UUID.randomUUID().toString()) =
        add(CREATE_SET, "POST", "/api/tires", ApiClient.tireSetBody(payload), label, idempotencyKey)

    fun enqueueUpdateSet(id: String, payload: TireSetPayload, label: String) =
        add(UPDATE_SET, "PATCH", "/api/tires/$id", ApiClient.tireSetBody(payload), label, null)

    fun enqueueDeleteSet(id: String, label: String) =
        add(DELETE_SET, "DELETE", "/api/tires/$id", null, label, null)

    fun enqueueCreateTread(
        tireSetId: String, payload: TreadMeasurementPayload, label: String,
        idempotencyKey: String = UUID.randomUUID().toString()
    ) = add(CREATE_TREAD, "POST", "/api/tires/$tireSetId/tread", ApiClient.treadBody(payload), label, idempotencyKey)

    fun enqueueDeleteTread(id: String, label: String) =
        add(DELETE_TREAD, "DELETE", "/api/tires/tread/$id", null, label, null)

    fun discard(entry: Entry) = update { list -> list.filterNot { it.id == entry.id } }

    /** IDs, deren Loeschung noch aussteht - die Ansicht blendet sie schon aus. */
    fun pendingDeletedSetIds(list: List<Entry>): Set<String> = pendingIds(list, DELETE_SET, "/api/tires/")
    fun pendingDeletedTreadIds(list: List<Entry>): Set<String> = pendingIds(list, DELETE_TREAD, "/api/tires/tread/")

    private fun pendingIds(list: List<Entry>, kind: String, prefix: String): Set<String> =
        list.filter { it.kind == kind && it.failure == null }.map { it.path.removePrefix(prefix) }.toSet()

    private fun add(kind: String, method: String, path: String, body: String?, label: String, key: String?) =
        update { list ->
            list + Entry(
                id = UUID.randomUUID().toString(), kind = kind, method = method, path = path, body = body,
                label = label, createdAt = System.currentTimeMillis(),
                userId = SessionManager.currentUser.value?.id, idempotencyKey = key
            )
        }

    // MARK: - Abarbeiten

    /** Laedt alle wartenden Eintraege hoch, in der Reihenfolge der Eingabe. Wirft nie. */
    suspend fun flush() {
        if (AppSettings.appMode.value != AppMode.SERVER || !SessionManager.isAuthenticated.value) return
        if (!NetworkMonitor.isOnline.value) return
        if (!flushLock.tryLock()) return
        try {
            val currentUser = SessionManager.currentUser.value?.id
            // Erfasst unter einem anderen Konto: nicht in dieses hochladen.
            if (currentUser != null) {
                update { list -> list.filter { it.userId == null || it.userId == currentUser } }
            }
            for (entry in _entries.value.filter { it.failure == null }) {
                try {
                    ApiClient.sendQueued(entry.method, entry.path, entry.body, entry.idempotencyKey)
                    discard(entry)
                } catch (e: Exception) {
                    if (e is ApiException.Server && e.statusCode == 404 && entry.method == "DELETE") {
                        discard(entry)
                        continue
                    }
                    if (isTransient(e)) break
                    update { list ->
                        list.map { if (it.id == entry.id) it.copy(failure = e.localizedMessage ?: e.toString()) else it }
                    }
                }
            }
        } finally {
            flushLock.unlock()
        }
    }

    @Synchronized
    private fun update(change: (List<Entry>) -> List<Entry>) {
        val next = change(_entries.value)
        _entries.value = next
        runCatching { file.writeText(adapter.toJson(next)) }
    }

    private fun read(): List<Entry> =
        runCatching { if (file.exists()) adapter.fromJson(file.readText()) else null }.getOrNull() ?: emptyList()
}

/**
 * Letzter Stand der Reifen-Ansicht vom Server, damit sie auch ohne Netz etwas zeigt - und man
 * dort offline eine Profilmessung oder einen Wechsel erfassen kann. Reine Anzeige.
 */
@JsonClass(generateAdapter = true)
data class TireCache(
    val sets: List<TireSet>,
    val measurements: List<TireTreadMeasurement>,
    val overview: TireOverview?,
    val comparison: TireComparison?,
    val savedAt: Long,
    val userId: String?
) {
    companion object {
        private val adapter = Json.moshi.adapter(TireCache::class.java)
        private val file: File get() = File(LademonitorApp.appContext.filesDir, "tire-cache.json")

        suspend fun load(): TireCache? = withContext(Dispatchers.IO) {
            runCatching { if (file.exists()) adapter.fromJson(file.readText()) else null }.getOrNull()
                ?.takeIf { it.userId == SessionManager.currentUser.value?.id }
        }

        suspend fun save(cache: TireCache) = withContext(Dispatchers.IO) {
            runCatching { file.writeText(adapter.toJson(cache)) }
        }
    }
}
