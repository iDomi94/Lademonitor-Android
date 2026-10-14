package com.dominiqueherbrigpersonalteam.lademonitor.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import com.dominiqueherbrigpersonalteam.lademonitor.data.session.SessionManager
import com.dominiqueherbrigpersonalteam.lademonitor.data.settings.AppMode
import com.dominiqueherbrigpersonalteam.lademonitor.ui.common.SectionCard
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.dominiqueherbrigpersonalteam.lademonitor.R
import com.dominiqueherbrigpersonalteam.lademonitor.data.remote.ApiClient
import com.dominiqueherbrigpersonalteam.lademonitor.data.settings.AppSettings
import com.dominiqueherbrigpersonalteam.lademonitor.ui.common.ServerAddressSection
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionSettingsScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    // Read as state so the test button enables as soon as an address is entered.
    val serverUrl by AppSettings.serverUrlString.collectAsStateWithLifecycle()
    val isConfigured = remember(serverUrl) { AppSettings.isConfigured }
    var isTesting by remember { mutableStateOf(false) }
    var success by remember { mutableStateOf<Boolean?>(null) }
    var failMessage by remember { mutableStateOf<String?>(null) }
    val statusNotOkMessage = stringResource(R.string.connection_status_not_ok)
    // Modus und Konto liegen hier gebuendelt wie in der iOS-App (ServerSettingsView), damit das
    // Einstellungs-Hauptmenue mit der Verwaltung beginnt.
    val appMode by AppSettings.appMode.collectAsStateWithLifecycle()
    val user by SessionManager.currentUser.collectAsStateWithLifecycle()
    var isLoggingOut by remember { mutableStateOf(false) }
    var showServerSwitch by remember { mutableStateOf(false) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.settings_nav_connection)) },
            navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
            }
        )
    }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SectionCard {
                SettingsHeader(stringResource(R.string.settings_section_mode))
                SettingsLabeledRow(
                    stringResource(R.string.settings_label_current),
                    if (appMode == AppMode.LOCAL_ONLY) stringResource(R.string.settings_mode_local) else stringResource(R.string.settings_mode_server)
                )
                if (appMode == AppMode.LOCAL_ONLY) {
                    SettingsActionText(stringResource(R.string.settings_switch_to_server_action)) { showServerSwitch = true }
                    Text(
                        stringResource(R.string.settings_mode_local_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    SettingsActionText(stringResource(R.string.settings_switch_to_local_action)) { AppSettings.setAppMode(AppMode.LOCAL_ONLY) }
                }
            }

            if (appMode == AppMode.SERVER) {
                SectionCard {
                    SettingsHeader(stringResource(R.string.settings_section_account))
                    user?.let { SettingsLabeledRow(stringResource(R.string.settings_label_logged_in_as), it.username) }
                    HorizontalDivider()
                    SettingsNavRow(stringResource(R.string.settings_nav_account)) { navController.navigate("settings/account") }
                    HorizontalDivider()
                    if (isLoggingOut) {
                        androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.settings_logging_out), color = MaterialTheme.colorScheme.error)
                            CircularProgressIndicator(Modifier.padding(start = 8.dp).height(18.dp), strokeWidth = 2.dp)
                        }
                    } else {
                        SettingsActionText(stringResource(R.string.settings_logout_action), color = MaterialTheme.colorScheme.error) {
                            scope.launch { isLoggingOut = true; SessionManager.logout(); isLoggingOut = false }
                        }
                    }
                }
            }

            Column {
            ServerAddressSection()

            Button(
                onClick = {
                    scope.launch {
                        isTesting = true; success = null; failMessage = null
                        try {
                            val ok = ApiClient.checkHealth()
                            success = ok
                            if (!ok) failMessage = statusNotOkMessage
                        } catch (e: Exception) {
                            success = false; failMessage = e.localizedMessage
                        }
                        isTesting = false
                    }
                },
                enabled = isConfigured && !isTesting,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
            ) {
                if (isTesting) CircularProgressIndicator(Modifier.height(20.dp), strokeWidth = 2.dp)
                else Text(stringResource(R.string.connection_test_action))
            }

            when (success) {
                true -> Row(iconTint = MaterialTheme.colorScheme.tertiary, icon = Icons.Filled.CheckCircle, text = stringResource(R.string.connection_success))
                false -> Row(iconTint = MaterialTheme.colorScheme.error, icon = Icons.Filled.Cancel, text = failMessage ?: stringResource(R.string.connection_failed))
                null -> {}
            }
            }
        }
    }

    if (showServerSwitch) {
        AlertDialog(
            onDismissRequest = { showServerSwitch = false },
            title = { Text(stringResource(R.string.settings_switch_to_server_title)) },
            text = { Text(stringResource(R.string.settings_switch_to_server_message)) },
            confirmButton = { TextButton(onClick = { showServerSwitch = false; AppSettings.setAppMode(AppMode.SERVER) }) { Text(stringResource(R.string.settings_switch_action)) } },
            dismissButton = { TextButton(onClick = { showServerSwitch = false }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }
}

@Composable
private fun Row(iconTint: androidx.compose.ui.graphics.Color, icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = iconTint)
        Spacer(Modifier.height(0.dp))
        Text("  $text", color = iconTint)
    }
}
