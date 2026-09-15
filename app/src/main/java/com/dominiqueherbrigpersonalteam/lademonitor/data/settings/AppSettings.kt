package com.dominiqueherbrigpersonalteam.lademonitor.data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.StringRes
import com.dominiqueherbrigpersonalteam.lademonitor.R
import com.dominiqueherbrigpersonalteam.lademonitor.data.session.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Port of the iOS `AppSettings`: holds the (non-secret) server address and the app mode.
 * Backed by [SharedPreferences] for synchronous reads (the [com.dominiqueherbrigpersonalteam.lademonitor.data.remote.ApiClient]
 * needs the URL synchronously) and mirrored into [StateFlow]s so Compose recomposes on change.
 */
object AppSettings {

    private const val PREFS = "lademonitor_settings"
    private const val KEY_SERVER_URL = "serverURL"
    private const val KEY_APP_MODE = "appMode"

    /** The public server operated by us - the alternative to self-hosting, see [ServerHosting]. */
    const val CLOUD_SERVER_URL = "https://lademonitor.cloud"

    /**
     * The choice in the server-address section (AuthScreen/ConnectionSettingsScreen): either a
     * freely entered own domain, or fixed lademonitor.cloud. No state of its own - it is derived
     * purely from [serverUrlString] so there cannot be two truths (e.g. after an app update, or
     * when somebody types lademonitor.cloud manually instead of using the picker).
     */
    enum class ServerHosting(@param:StringRes val labelRes: Int) {
        SELF_HOSTED(R.string.server_hosting_self_hosted),
        CLOUD(R.string.server_hosting_cloud)
    }

    private lateinit var prefs: SharedPreferences

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _serverUrlString = MutableStateFlow("")
    val serverUrlString: StateFlow<String> = _serverUrlString

    private val _appMode = MutableStateFlow(AppMode.UNDECIDED)
    val appMode: StateFlow<AppMode> = _appMode

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _serverUrlString.value = prefs.getString(KEY_SERVER_URL, "") ?: ""
        _appMode.value = AppMode.from(prefs.getString(KEY_APP_MODE, null))
    }

    fun setServerUrlString(value: String) {
        _serverUrlString.value = value
        prefs.edit().putString(KEY_SERVER_URL, value).apply()
    }

    /** Derived from the stored URL, so the picker can never disagree with the address field. */
    val serverHosting: StateFlow<ServerHosting> = _serverUrlString
        .map { hostingFor(it) }
        .stateIn(scope, SharingStarted.Eagerly, ServerHosting.SELF_HOSTED)

    /** Also recognises the cloud when it was typed by hand, e.g. as a bare "lademonitor.cloud". */
    private fun hostingFor(url: String): ServerHosting =
        if (normalized(url) == CLOUD_SERVER_URL) ServerHosting.CLOUD else ServerHosting.SELF_HOSTED

    fun setServerHosting(hosting: ServerHosting) {
        when (hosting) {
            ServerHosting.CLOUD -> setServerUrlString(CLOUD_SERVER_URL)
            // Only reset when the cloud URL is actually still in there - otherwise a mistaken tap
            // on "self-hosted" would wipe an already entered own domain.
            ServerHosting.SELF_HOSTED ->
                if (hostingFor(_serverUrlString.value) == ServerHosting.CLOUD) setServerUrlString("")
        }
    }

    fun setAppMode(mode: AppMode) {
        _appMode.value = mode
        prefs.edit().putString(KEY_APP_MODE, mode.raw).apply()
        if (mode == AppMode.LOCAL_ONLY) {
            // Drop any credentials when switching to local-only, so a later switch back to server
            // always requires a fresh sign-in (matches the iOS behaviour).
            SessionManager.invalidateSession()
        }
    }

    /**
     * The base URL (no trailing slash). If the user typed a bare domain, `https://` is prepended;
     * an explicit `http://` is preserved. Returns null when nothing is configured.
     */
    fun serverUrl(): String? = normalized(_serverUrlString.value)

    private fun normalized(value: String): String? {
        var raw = value.trim()
        if (raw.isEmpty()) return null
        if (!raw.startsWith("http://") && !raw.startsWith("https://")) raw = "https://$raw"
        if (raw.endsWith("/")) raw = raw.dropLast(1)
        return raw
    }

    val isConfigured: Boolean get() = serverUrl() != null

    /** Whether views may load data: always in local-only mode; in server mode only when configured. */
    val isReadyForDataAccess: Boolean
        get() = _appMode.value == AppMode.LOCAL_ONLY || isConfigured
}
