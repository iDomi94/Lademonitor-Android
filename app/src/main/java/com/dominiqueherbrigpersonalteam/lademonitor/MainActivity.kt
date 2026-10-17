package com.dominiqueherbrigpersonalteam.lademonitor

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.dominiqueherbrigpersonalteam.lademonitor.data.repo.WidgetSnapshotWriter
import com.dominiqueherbrigpersonalteam.lademonitor.data.session.SessionManager
import com.dominiqueherbrigpersonalteam.lademonitor.ui.AppShortcuts
import com.dominiqueherbrigpersonalteam.lademonitor.ui.LademonitorRoot
import com.dominiqueherbrigpersonalteam.lademonitor.ui.theme.LademonitorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Nur beim ersten Start: nach einer Neuerstellung (Drehen) haengt dasselbe Intent noch dran.
        if (savedInstanceState == null) AppShortcuts.handleIntent(intent)
        AppShortcuts.refreshAsync(this)
        setContent {
            LademonitorTheme {
                LademonitorRoot()
            }
        }
    }

    // launchMode singleTop: ein Kurzbefehl bei laufender App kommt hier an statt als zweite Instanz.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        AppShortcuts.handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        // The process may have started in the background (reboot, app update) while the Keystore
        // was not ready yet - then the token was unreadable at that moment, not gone.
        SessionManager.restoreIfTokenAvailable()
    }

    override fun onStop() {
        super.onStop()
        // Ein Fahrzeug kann inzwischen zum Dienstwagen geworden sein (oder keiner mehr).
        AppShortcuts.refreshAsync(this)
        // Wie beim Wechsel in den Hintergrund auf iOS: ein Sync kann inzwischen neue
        // Ladevorgaenge gebracht haben, die das Widget noch nicht kennt.
        WidgetSnapshotWriter.refreshAsync()
    }
}
