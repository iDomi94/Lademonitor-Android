package com.dominiqueherbrigpersonalteam.lademonitor.data.remote

import com.dominiqueherbrigpersonalteam.lademonitor.LademonitorApp
import com.dominiqueherbrigpersonalteam.lademonitor.R
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.AccountDeletePayload
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.AuthCredentials
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.AuthResponse
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.AuthUser
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.EmailUpdatePayload
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.NotificationSettingsPayload
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.PasswordChangePayload
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.PasswordResetRequestPayload
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.RegisterCredentials
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ChargingLocation
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ChargingSession
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ChargingSessionPayload
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.DeletionsResponse
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.GeocodeResult
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.LocationPayload
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.Provider
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ProviderPayload
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.StatsSummary
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.TemperatureStats
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.Vehicle
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.VehiclePayload
import com.dominiqueherbrigpersonalteam.lademonitor.data.session.SessionManager
import com.dominiqueherbrigpersonalteam.lademonitor.data.settings.AppSettings
import com.dominiqueherbrigpersonalteam.lademonitor.data.settings.TokenStore
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.lang.reflect.Type
import java.net.URLEncoder
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Hand-rolled REST client, a close port of the iOS `APIClient`: it attaches the bearer token to
 * authenticated calls, extracts `{"detail": ...}` error messages, and on a 401 for an
 * authenticated request invalidates the session (kicking the user back to login).
 *
 * All work happens on [Dispatchers.IO]; callers just `await` the suspend functions.
 */
object ApiClient {

    private val jsonMedia = "application/json".toMediaType()

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    private fun listType(elem: Type): Type = Types.newParameterizedType(List::class.java, elem)

    // MARK: - Request plumbing

    private fun buildRequest(
        path: String,
        method: String,
        body: String?,
        authenticated: Boolean
    ): Request {
        val base = AppSettings.serverUrl() ?: throw ApiException.NotConfigured
        val url = (base + path).toHttpUrlOrNull() ?: throw ApiException.InvalidResponse
        val builder = Request.Builder().url(url).header("Content-Type", "application/json")
        if (authenticated) {
            TokenStore.readToken()?.let { builder.header("Authorization", "Bearer $it") }
        }
        val requestBody = when (method) {
            "GET", "DELETE" -> if (body != null) body.toRequestBody(jsonMedia) else null
            else -> (body ?: "").toRequestBody(jsonMedia)
        }
        builder.method(method, requestBody)
        return builder.build()
    }

    private suspend fun <T> send(
        path: String,
        method: String = "GET",
        body: String? = null,
        authenticated: Boolean = true,
        type: Type
    ): T = withContext(Dispatchers.IO) {
        val request = buildRequest(path, method, body, authenticated)
        val response = try {
            http.newCall(request).execute()
        } catch (e: Exception) {
            throw ApiException.Network(e)
        }
        response.use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                if (resp.code == 401 && request.header("Authorization") != null) {
                    SessionManager.invalidateSession()
                }
                throw ApiException.Server(resp.code, messageFrom(raw))
            }
            try {
                @Suppress("UNCHECKED_CAST")
                Json.moshi.adapter<Any>(type).fromJson(raw) as T
            } catch (e: Exception) {
                throw ApiException.Decoding(e)
            }
        }
    }

    private suspend fun sendNoContent(
        path: String,
        method: String,
        body: String? = null,
        authenticated: Boolean = true
    ) = withContext(Dispatchers.IO) {
        val request = buildRequest(path, method, body, authenticated)
        val response = try {
            http.newCall(request).execute()
        } catch (e: Exception) {
            throw ApiException.Network(e)
        }
        response.use { resp ->
            if (!resp.isSuccessful) {
                val raw = resp.body?.string().orEmpty()
                if (resp.code == 401 && request.header("Authorization") != null) {
                    SessionManager.invalidateSession()
                }
                throw ApiException.Server(resp.code, messageFrom(raw))
            }
        }
    }

    private fun messageFrom(raw: String): String {
        if (raw.isBlank()) return LademonitorApp.appContext.getString(R.string.api_error_unknown)
        return try {
            val map = Json.moshi.adapter<Map<String, Any>>(
                Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
            ).fromJson(raw)
            (map?.get("detail") as? String)?.takeIf { it.isNotEmpty() } ?: raw
        } catch (_: Exception) {
            raw
        }
    }

    private inline fun <reified T> encode(value: T): String =
        Json.moshi.adapter(T::class.java).toJson(value)

    /**
     * Like [encode], but keeps `null` fields in the JSON. Needed where the server distinguishes
     * "field absent" from "field explicitly null" - removing the e-mail address is exactly that.
     */
    private inline fun <reified T> encodeKeepingNulls(value: T): String =
        Json.moshi.adapter(T::class.java).serializeNulls().toJson(value)

    // MARK: - Auth

    suspend fun register(username: String, password: String, email: String? = null): AuthResponse =
        send(
            "/api/auth/register", "POST",
            encode(RegisterCredentials(username, password, email)),
            authenticated = false, type = AuthResponse::class.java
        )

    /**
     * [identifier] is the username OR the e-mail address. The JSON field is still called
     * `username` server-side (see `schemas.LoginRequest`) - the name was deliberately kept so
     * existing clients keep working.
     */
    suspend fun login(identifier: String, password: String): AuthResponse =
        send(
            "/api/auth/login", "POST",
            encode(AuthCredentials(identifier, password)),
            authenticated = false, type = AuthResponse::class.java
        )

    suspend fun logout() = sendNoContent("/api/auth/logout", "POST")

    suspend fun fetchMe(): AuthUser = send("/api/auth/me", type = AuthUser::class.java)

    // MARK: - Account (server 0.14.0 and newer)

    /**
     * Changes the own password and returns the new session.
     *
     * The server drops ALL sessions of the user - the own one included - and immediately issues a
     * new one. Since server 0.14.1 it ships that token in the response (like login and
     * registration); before that a bearer client had to sign in a second time.
     */
    suspend fun changePassword(currentPassword: String, newPassword: String): AuthResponse =
        send(
            "/api/auth/password", "PUT",
            encode(PasswordChangePayload(currentPassword, newPassword)),
            type = AuthResponse::class.java
        )

    /**
     * Sets or removes (`email == null`) the own address. A changed address counts as unconfirmed
     * afterwards; the server automatically sends a confirmation link if mail delivery is set up.
     */
    suspend fun updateEmail(email: String?, currentPassword: String): AuthUser =
        send(
            "/api/auth/email", "PUT",
            encodeKeepingNulls(EmailUpdatePayload(email, currentPassword)),
            type = AuthUser::class.java
        )

    suspend fun resendEmailVerification() =
        sendNoContent("/api/auth/email/verify/resend", "POST")

    /**
     * Deletes the own account irrevocably, including all own data on the server (vehicles,
     * charging sessions, providers, charging locations, ...).
     */
    suspend fun deleteAccount(currentPassword: String) =
        sendNoContent("/api/auth/me", "DELETE", encode(AccountDeletePayload(currentPassword)))

    suspend fun updateNotifications(payload: NotificationSettingsPayload): AuthUser =
        send("/api/auth/notifications", "PUT", encode(payload), type = AuthUser::class.java)

    /**
     * Requests a reset link. The server answers with 204 ON PURPOSE - also for an unknown account,
     * a missing or an unconfirmed address. Any distinction would be a directory of all usernames
     * and addresses of this server. So the app must not derive anything from it and shows the same
     * message in every case.
     */
    suspend fun requestPasswordReset(identifier: String) =
        sendNoContent(
            "/api/auth/password-reset/request", "POST",
            encode(PasswordResetRequestPayload(identifier)),
            authenticated = false
        )

    suspend fun checkHealth(): Boolean {
        val map: Map<String, Any> = send(
            "/health", authenticated = false,
            type = Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
        )
        return map["status"] == "ok"
    }

    // MARK: - Vehicles

    suspend fun fetchVehicles(): List<Vehicle> =
        send("/api/vehicles", type = listType(Vehicle::class.java))

    suspend fun createVehicle(payload: VehiclePayload): Vehicle =
        send("/api/vehicles", "POST", encode(payload), type = Vehicle::class.java)

    suspend fun updateVehicle(id: String, payload: VehiclePayload): Vehicle =
        send("/api/vehicles/$id", "PATCH", encode(payload), type = Vehicle::class.java)

    suspend fun deleteVehicle(id: String) = sendNoContent("/api/vehicles/$id", "DELETE")

    // MARK: - Providers

    suspend fun fetchProviders(): List<Provider> =
        send("/api/providers", type = listType(Provider::class.java))

    suspend fun createProvider(payload: ProviderPayload): Provider =
        send("/api/providers", "POST", encode(payload), type = Provider::class.java)

    suspend fun updateProvider(id: String, payload: ProviderPayload): Provider =
        send("/api/providers/$id", "PATCH", encode(payload), type = Provider::class.java)

    suspend fun deleteProvider(id: String) = sendNoContent("/api/providers/$id", "DELETE")

    // MARK: - Locations

    suspend fun fetchLocations(): List<ChargingLocation> =
        send("/api/locations", type = listType(ChargingLocation::class.java))

    suspend fun createLocation(payload: LocationPayload): ChargingLocation =
        send("/api/locations", "POST", encode(payload), type = ChargingLocation::class.java)

    suspend fun updateLocation(id: String, payload: LocationPayload): ChargingLocation =
        send("/api/locations/$id", "PATCH", encode(payload), type = ChargingLocation::class.java)

    suspend fun deleteLocation(id: String) = sendNoContent("/api/locations/$id", "DELETE")

    // MARK: - Geocoding (server proxy to Nominatim)

    suspend fun forwardGeocode(query: String): List<GeocodeResult> {
        val base = AppSettings.serverUrl() ?: throw ApiException.NotConfigured
        val url = "$base/api/geocode/forward".toHttpUrlOrNull()
            ?.newBuilder()?.addQueryParameter("query", query)?.build()
            ?: throw ApiException.InvalidResponse
        return withContext(Dispatchers.IO) {
            // The token is attached explicitly, like everywhere else: the endpoint sits behind
            // the sign-in requirement. Without the header the call would usually still work via
            // the session cookie - and that implicit side path fails as soon as the cookie store
            // is empty, without the 401 handling in [send] kicking in.
            val builder = Request.Builder().url(url).header("Content-Type", "application/json")
            TokenStore.readToken()?.let { builder.header("Authorization", "Bearer $it") }
            val request = builder.get().build()
            val response = try {
                http.newCall(request).execute()
            } catch (e: Exception) {
                throw ApiException.Network(e)
            }
            response.use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw ApiException.Server(resp.code, messageFrom(raw))
                try {
                    Json.moshi.adapter<List<GeocodeResult>>(listType(GeocodeResult::class.java))
                        .fromJson(raw) ?: emptyList()
                } catch (e: Exception) {
                    throw ApiException.Decoding(e)
                }
            }
        }
    }

    // MARK: - Sessions

    suspend fun fetchSessions(
        vehicleId: String? = null,
        needsReview: Boolean? = null
    ): List<ChargingSession> {
        val query = buildList {
            vehicleId?.let { add("vehicle_id=$it") }
            needsReview?.let { add("needs_review=$it") }
        }
        val path = "/api/sessions" + if (query.isNotEmpty()) "?" + query.joinToString("&") else ""
        return send(path, type = listType(ChargingSession::class.java))
    }

    suspend fun createSession(payload: ChargingSessionPayload): ChargingSession =
        send("/api/sessions", "POST", encode(payload), type = ChargingSession::class.java)

    suspend fun updateSession(id: String, payload: ChargingSessionPayload): ChargingSession =
        send("/api/sessions/$id", "PATCH", encode(payload), type = ChargingSession::class.java)

    suspend fun deleteSession(id: String) = sendNoContent("/api/sessions/$id", "DELETE")

    // MARK: - Sync

    /**
     * Serverseitige Loeschungen seit [since] (der `server_time`-Wert des vorherigen Aufrufs,
     * roh durchgereicht — siehe [DeletionsResponse]). Ohne [since] kommen alle; das ist der
     * erste Abgleich eines Geraets.
     */
    suspend fun fetchDeletions(since: String? = null): DeletionsResponse {
        val path = "/api/sync/deletions" +
            if (since.isNullOrEmpty()) "" else "?since=" + URLEncoder.encode(since, "UTF-8")
        return send(path, type = DeletionsResponse::class.java)
    }

    // MARK: - Stats (server-side; the app normally computes stats locally)

    suspend fun fetchStatsSummary(vehicleId: String? = null): StatsSummary {
        val path = "/api/stats/summary" + if (vehicleId != null) "?vehicle_id=$vehicleId" else ""
        return send(path, type = StatsSummary::class.java)
    }

    /**
     * Verbrauch gegen Aussentemperatur (Streudiagramm, Klassenmittel, Ausgleichsgerade,
     * Jahreszeiten). Nur im Server-Modus verfuegbar — die Rechnung liegt bewusst allein auf
     * dem Server, siehe [TemperatureStats].
     *
     * Der Zeitraumfilter geht hier als `start_date`/`end_date` mit (reines Datum, keine
     * Uhrzeit) — anders als bei der Zusammenfassung, die aus dem lokalen Spiegel kommt und
     * dort gefiltert wird.
     */
    suspend fun fetchTemperatureStats(
        vehicleId: String? = null,
        startDate: LocalDate? = null,
        endDate: LocalDate? = null
    ): TemperatureStats {
        val params = buildList {
            if (vehicleId != null) add("vehicle_id=$vehicleId")
            if (startDate != null) add("start_date=$startDate")
            if (endDate != null) add("end_date=$endDate")
        }
        val path = "/api/stats/temperature" + if (params.isEmpty()) "" else "?" + params.joinToString("&")
        return send(path, type = TemperatureStats::class.java)
    }
}
