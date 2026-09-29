package com.dominiqueherbrigpersonalteam.lademonitor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.dominiqueherbrigpersonalteam.lademonitor.data.repo.WidgetSnapshotWriter
import com.dominiqueherbrigpersonalteam.lademonitor.ui.LademonitorRoot
import com.dominiqueherbrigpersonalteam.lademonitor.ui.theme.LademonitorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LademonitorTheme {
                LademonitorRoot()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Wie beim Wechsel in den Hintergrund auf iOS: ein Sync kann inzwischen neue
        // Ladevorgaenge gebracht haben, die das Widget noch nicht kennt.
        WidgetSnapshotWriter.refreshAsync()
    }
}
