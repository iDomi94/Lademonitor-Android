package com.dominiqueherbrigpersonalteam.lademonitor

import android.app.Application
import android.content.Context
import com.dominiqueherbrigpersonalteam.lademonitor.data.local.LocalStore
import com.dominiqueherbrigpersonalteam.lademonitor.data.net.NetworkMonitor
import com.dominiqueherbrigpersonalteam.lademonitor.data.repo.AppRepository
import com.dominiqueherbrigpersonalteam.lademonitor.data.repo.SyncService
import com.dominiqueherbrigpersonalteam.lademonitor.data.session.SessionManager
import com.dominiqueherbrigpersonalteam.lademonitor.data.settings.AppSettings
import com.dominiqueherbrigpersonalteam.lademonitor.data.settings.TokenStore
import org.osmdroid.config.Configuration

class LademonitorApp : Application() {

    companion object {
        /**
         * Application [Context], usable for resolving localized string resources (via
         * [Context.getString]) from plain Kotlin singletons/exceptions that live outside any
         * `@Composable` or `Activity` scope (e.g. [com.dominiqueherbrigpersonalteam.lademonitor.data.repo.SyncService],
         * [com.dominiqueherbrigpersonalteam.lademonitor.data.remote.ApiException]). Safe to hold
         * statically since it is the process-wide Application context, not an Activity.
         */
        lateinit var appContext: Context
            private set
    }

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext

        // Order matters: SessionManager reads the token, so TokenStore must be ready first.
        TokenStore.init(this)
        SessionManager.init()
        AppSettings.init(this)
        LocalStore.init(this)
        NetworkMonitor.init(this)
        SyncService.init(this)
        // Fruehere lokale Abwahl des Tarifrechners in die Ladeort-Art der Anbieter uebernehmen.
        AppRepository.migrateLegacyTariffExclusionsAsync()

        // osmdroid needs a user-agent set (OSM tile policy) and a cache location.
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = packageName
    }
}
