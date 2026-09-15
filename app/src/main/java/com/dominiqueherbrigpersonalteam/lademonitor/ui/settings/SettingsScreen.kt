package com.dominiqueherbrigpersonalteam.lademonitor.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.dominiqueherbrigpersonalteam.lademonitor.R
import com.dominiqueherbrigpersonalteam.lademonitor.data.repo.LocalDataStore
import com.dominiqueherbrigpersonalteam.lademonitor.data.repo.SyncService
import com.dominiqueherbrigpersonalteam.lademonitor.data.session.SessionManager
import com.dominiqueherbrigpersonalteam.lademonitor.data.settings.AppMode
import com.dominiqueherbrigpersonalteam.lademonitor.data.settings.AppSettings
import com.dominiqueherbrigpersonalteam.lademonitor.ui.common.Fmt
import com.dominiqueherbrigpersonalteam.lademonitor.ui.common.SectionCard
import androidx.compose.material3.AlertDialog
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    val appMode by AppSettings.appMode.collectAsStateWithLifecycle()
    val user by SessionManager.currentUser.collectAsStateWithLifecycle()
    val isSyncing by SyncService.isSyncing.collectAsStateWithLifecycle()
    val lastSyncDate by SyncService.lastSyncDate.collectAsStateWithLifecycle()
    val lastSyncError by SyncService.lastSyncError.collectAsStateWithLifecycle()

    var isLoggingOut by remember { mutableStateOf(false) }
    var showServerSwitch by remember { mutableStateOf(false) }
    var showReset by remember { mutableStateOf(false) }
    var resetError by remember { mutableStateOf<String?>(null) }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.tab_settings)) }) }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Mode
            SectionCard {
                Header(stringResource(R.string.settings_section_mode))
                LabeledRow(
                    stringResource(R.string.settings_label_current),
                    if (appMode == AppMode.LOCAL_ONLY) stringResource(R.string.settings_mode_local) else stringResource(R.string.settings_mode_server)
                )
                if (appMode == AppMode.LOCAL_ONLY) {
                    ActionText(stringResource(R.string.settings_switch_to_server_action)) { showServerSwitch = true }
                    Text(
                        stringResource(R.string.settings_mode_local_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    ActionText(stringResource(R.string.settings_switch_to_local_action)) { AppSettings.setAppMode(AppMode.LOCAL_ONLY) }
                }
            }

            if (appMode == AppMode.SERVER) {
                SectionCard {
                    Header(stringResource(R.string.settings_section_account))
                    user?.let { LabeledRow(stringResource(R.string.settings_label_logged_in_as), it.username) }
                    HorizontalDivider()
                    NavRow(stringResource(R.string.settings_nav_account)) { navController.navigate("settings/account") }
                    HorizontalDivider()
                    if (isLoggingOut) {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text(stringResource(R.string.settings_logging_out), color = MaterialTheme.colorScheme.error)
                            CircularProgressIndicator(Modifier.padding(start = 8.dp).height(18.dp), strokeWidth = 2.dp)
                        }
                    } else {
                        ActionText(stringResource(R.string.settings_logout_action), color = MaterialTheme.colorScheme.error) {
                            scope.launch { isLoggingOut = true; SessionManager.logout(); isLoggingOut = false }
                        }
                    }
                }

                SectionCard {
                    Header(stringResource(R.string.settings_section_connection))
                    NavRow(stringResource(R.string.settings_nav_connection)) { navController.navigate("settings/connection") }
                }

                SectionCard {
                    Header(stringResource(R.string.settings_section_sync))
                    if (lastSyncDate != null) LabeledRow(stringResource(R.string.settings_label_last_synced), Fmt.relative(lastSyncDate!!))
                    else Text(stringResource(R.string.settings_not_synced_yet), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    lastSyncError?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                    }
                    if (isSyncing) {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text(stringResource(R.string.settings_syncing))
                            CircularProgressIndicator(Modifier.padding(start = 8.dp).height(18.dp), strokeWidth = 2.dp)
                        }
                    } else {
                        ActionText(stringResource(R.string.settings_sync_now_action)) { scope.launch { SyncService.syncNow() } }
                    }
                }
            }

            SectionCard {
                Header(stringResource(R.string.settings_section_management))
                NavRow(stringResource(R.string.map_legend_locations)) { navController.navigate("settings/locations") }
                HorizontalDivider()
                NavRow(stringResource(R.string.settings_nav_vehicles)) { navController.navigate("settings/vehicles") }
                HorizontalDivider()
                NavRow(stringResource(R.string.settings_nav_providers)) { navController.navigate("settings/providers") }
            }

            SectionCard {
                ActionText(stringResource(R.string.settings_reset_action), color = MaterialTheme.colorScheme.error) {
                    showReset = true
                }
                resetError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                Text(
                    if (appMode == AppMode.SERVER)
                        stringResource(R.string.settings_reset_hint_server)
                    else
                        stringResource(R.string.settings_reset_hint_local),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
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

    if (showReset) {
        AlertDialog(
            onDismissRequest = { showReset = false },
            title = { Text(stringResource(R.string.settings_reset_confirm_title)) },
            text = { Text(stringResource(R.string.action_cannot_be_undone)) },
            confirmButton = {
                TextButton(onClick = {
                    showReset = false
                    scope.launch {
                        resetError = null
                        try {
                            LocalDataStore.resetAllData()
                            if (appMode == AppMode.SERVER) SyncService.syncNow()
                        } catch (e: Exception) { resetError = e.localizedMessage }
                    }
                }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showReset = false }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }
}

@Composable
private fun Header(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun LabeledRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ActionText(text: String, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary, onClick: () -> Unit) {
    Text(
        text, color = color,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp)
    )
}

@Composable
private fun NavRow(text: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
