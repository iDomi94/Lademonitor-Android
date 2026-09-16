package com.dominiqueherbrigpersonalteam.lademonitor.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.dominiqueherbrigpersonalteam.lademonitor.R
import com.dominiqueherbrigpersonalteam.lademonitor.data.repo.SyncService
import com.dominiqueherbrigpersonalteam.lademonitor.data.session.SessionManager
import com.dominiqueherbrigpersonalteam.lademonitor.data.settings.AppMode
import com.dominiqueherbrigpersonalteam.lademonitor.data.settings.AppSettings
import com.dominiqueherbrigpersonalteam.lademonitor.ui.auth.AuthScreen
import com.dominiqueherbrigpersonalteam.lademonitor.ui.auth.ModeSelectionScreen
import com.dominiqueherbrigpersonalteam.lademonitor.ui.dashboard.DashboardScreen
import com.dominiqueherbrigpersonalteam.lademonitor.ui.map.MapScreen
import com.dominiqueherbrigpersonalteam.lademonitor.ui.sessions.SessionsListScreen
import com.dominiqueherbrigpersonalteam.lademonitor.ui.settings.AccountSettingsScreen
import com.dominiqueherbrigpersonalteam.lademonitor.ui.settings.ConnectionSettingsScreen
import com.dominiqueherbrigpersonalteam.lademonitor.ui.settings.LocationsSettingsScreen
import com.dominiqueherbrigpersonalteam.lademonitor.ui.settings.ProvidersSettingsScreen
import com.dominiqueherbrigpersonalteam.lademonitor.ui.settings.SettingsScreen
import com.dominiqueherbrigpersonalteam.lademonitor.ui.settings.VehiclesSettingsScreen
import kotlinx.coroutines.launch

@Composable
fun LademonitorRoot() {
    val appMode by AppSettings.appMode.collectAsStateWithLifecycle()
    val isAuthenticated by SessionManager.isAuthenticated.collectAsStateWithLifecycle()

    when (appMode) {
        AppMode.UNDECIDED -> ModeSelectionScreen()
        AppMode.LOCAL_ONLY -> MainScaffold()
        AppMode.SERVER -> {
            if (isAuthenticated) {
                LaunchedEffect(Unit) { SessionManager.refreshCurrentUserIfNeeded() }
                MainScaffold()
            } else {
                AuthScreen()
            }
        }
    }

    // The dialog deliberately hangs HERE and not on the AuthScreen: after signing in that one has
    // already left the hierarchy (see SyncService.pendingLocalDataDecision), so its dialog would
    // never appear. LademonitorRoot exists in every state.
    LocalDataDecisionDialog()
}

/**
 * Asks what should happen to the data already on the device when signing in to an account this
 * device has never synced with. Port of the alert on the iOS `ContentView`.
 */
@Composable
private fun LocalDataDecisionDialog() {
    val scope = rememberCoroutineScope()
    val isPending by SyncService.pendingLocalDataDecision.collectAsStateWithLifecycle()
    val summary by SyncService.pendingLocalDataSummary.collectAsStateWithLifecycle()
    val user by SessionManager.currentUser.collectAsStateWithLifecycle()
    if (!isPending) return

    // Explains both ways concretely - including the assurance that nothing is lost on the server.
    // That is exactly the worry that makes one hesitate here.
    val existing = if (summary.isEmpty()) stringResource(R.string.local_data_decision_generic)
    else stringResource(R.string.local_data_decision_summary, summary)
    val account = user?.username ?: stringResource(R.string.local_data_decision_this_account)

    AlertDialog(
        // Not dismissible by tapping outside: the question has to be answered, otherwise the next
        // arbitrary sync would answer it silently with "upload".
        onDismissRequest = {},
        title = { Text(stringResource(R.string.local_data_decision_title)) },
        text = {
            Text(existing + " " + stringResource(R.string.local_data_decision_explanation, account))
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch { SyncService.applyLocalDataDecision(SyncService.LocalDataDecision.UPLOAD) }
            }) { Text(stringResource(R.string.local_data_decision_upload)) }
        },
        dismissButton = {
            Column {
                TextButton(onClick = {
                    scope.launch {
                        SyncService.applyLocalDataDecision(SyncService.LocalDataDecision.DISCARD)
                    }
                }) {
                    Text(
                        stringResource(R.string.local_data_decision_discard),
                        color = MaterialTheme.colorScheme.error
                    )
                }
                TextButton(onClick = {
                    scope.launch { SyncService.cancelLocalDataDecision() }
                }) { Text(stringResource(R.string.action_cancel)) }
            }
        }
    )
}

private enum class Tab(val route: String, val labelRes: Int, val icon: ImageVector) {
    DASHBOARD("dashboard", R.string.tab_dashboard, Icons.Filled.BarChart),
    SESSIONS("sessions", R.string.tab_sessions, Icons.Filled.Bolt),
    MAP("map", R.string.tab_map, Icons.Filled.Map),
    SETTINGS("settings", R.string.tab_settings, Icons.Filled.Settings)
}

@Composable
private fun MainScaffold() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                    val label = stringResource(tab.labelRes)
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = label) },
                        label = { Text(label, maxLines = 1) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Tab.DASHBOARD.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Tab.DASHBOARD.route) { DashboardScreen() }
            composable(Tab.SESSIONS.route) { SessionsListScreen() }
            composable(Tab.MAP.route) { MapScreen() }
            composable(Tab.SETTINGS.route) { SettingsScreen(navController) }
            composable("settings/vehicles") { VehiclesSettingsScreen(navController) }
            composable("settings/providers") { ProvidersSettingsScreen(navController) }
            composable("settings/locations") { LocationsSettingsScreen(navController) }
            composable("settings/connection") { ConnectionSettingsScreen(navController) }
            composable("settings/account") { AccountSettingsScreen(navController) }
        }
    }
}
