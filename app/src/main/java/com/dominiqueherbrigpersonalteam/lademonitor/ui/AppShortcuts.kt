package com.dominiqueherbrigpersonalteam.lademonitor.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.dominiqueherbrigpersonalteam.lademonitor.MainActivity
import com.dominiqueherbrigpersonalteam.lademonitor.R
import com.dominiqueherbrigpersonalteam.lademonitor.data.repo.LocalDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Kurzbefehle (langes Druecken aufs App-Symbol). "Ladevorgang erfassen" ist statisch
 * (res/xml/shortcuts.xml), "Dashboard" und "Erstattungsbericht" sind dynamisch - Letzterer nur,
 * wenn ein Fahrzeug als Dienstwagen markiert ist.
 *
 * Alle drei oeffnen MainActivity mit einer `lademonitor://shortcut/<ziel>`-Adresse; das Ziel
 * landet in [pending] und wird vom MainScaffold abgeholt, sobald es angezeigt wird (also auch
 * erst nach Moduswahl bzw. Anmeldung).
 */
object AppShortcuts {
    enum class Target(val path: String) {
        NEW_SESSION("new-session"),
        DASHBOARD("dashboard"),
        REIMBURSEMENT("reimbursement")
    }

    private const val SCHEME = "lademonitor"
    private const val HOST = "shortcut"

    private val _pending = MutableStateFlow<Target?>(null)
    val pending: StateFlow<Target?> = _pending.asStateFlow()

    /** Vom Ladevorgangs-Reiter abzuholen: das Formular "Neuer Ladevorgang" oeffnen. */
    private val _openNewSession = MutableStateFlow(false)
    val openNewSession: StateFlow<Boolean> = _openNewSession.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun handleIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme != SCHEME || data.host != HOST) return
        val target = Target.entries.firstOrNull { it.path == data.lastPathSegment } ?: return
        _pending.value = target
    }

    fun consumePending() {
        _pending.value = null
    }

    fun requestNewSession() {
        _openNewSession.value = true
    }

    fun consumeNewSession() {
        _openNewSession.value = false
    }

    private fun intentFor(context: Context, target: Target): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse("$SCHEME://$HOST/${target.path}"), context, MainActivity::class.java)

    fun refreshAsync(context: Context) {
        val appContext = context.applicationContext
        scope.launch { refresh(appContext) }
    }

    private suspend fun refresh(context: Context) {
        try {
            val hasCompanyCar = LocalDataStore.fetchVehicles().any { it.isCompanyCar == true }
            val shortcuts = mutableListOf(
                ShortcutInfoCompat.Builder(context, Target.DASHBOARD.path)
                    .setShortLabel(context.getString(R.string.shortcut_dashboard_short))
                    .setLongLabel(context.getString(R.string.shortcut_dashboard_long))
                    .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_dashboard))
                    .setIntent(intentFor(context, Target.DASHBOARD))
                    .setRank(0)
                    .build()
            )
            if (hasCompanyCar) {
                shortcuts += ShortcutInfoCompat.Builder(context, Target.REIMBURSEMENT.path)
                    .setShortLabel(context.getString(R.string.shortcut_reimbursement_short))
                    .setLongLabel(context.getString(R.string.shortcut_reimbursement_long))
                    .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_reimbursement))
                    .setIntent(intentFor(context, Target.REIMBURSEMENT))
                    .setRank(1)
                    .build()
            }
            ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
        } catch (e: Exception) {
            // Fehlende Kurzbefehle sind kein Grund, irgendetwas anderes scheitern zu lassen.
        }
    }
}
