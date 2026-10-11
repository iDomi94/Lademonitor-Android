package com.dominiqueherbrigpersonalteam.lademonitor.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Build
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
import androidx.navigation.NavType
import androidx.navigation.navArgument
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
import com.dominiqueherbrigpersonalteam.lademonitor.ui.settings.ChargeBarSettingsScreen
import com.dominiqueherbrigpersonalteam.lademonitor.ui.settings.ConnectionSettingsScreen
import com.dominiqueherbrigpersonalteam.lademonitor.ui.settings.FeesSettingsScreen
import com.dominiqueherbrigpersonalteam.lademonitor.ui.settings.LocationsSettingsScreen
import com.dominiqueherbrigpersonalteam.lademonitor.ui.settings.ProvidersSettingsScreen
import com.dominiqueherbrigpersonalteam.lademonitor.ui.settings.SettingsScreen
import com.dominiqueherbrigpersonalteam.lademonitor.ui.settings.TiresSettingsScreen
import com.dominiqueherbrigpersonalteam.lademonitor.ui.settings.VehicleCostsScreen
import com.dominiqueherbrigpersonalteam.lademonitor.ui.settings.VehicleRemindersScreen
import com.dominiqueherbrigpersonalteam.lademonitor.ui.settings.VehiclesSettingsScreen
import com.dominiqueherbrigpersonalteam.lademonitor.ui.tools.BatteryHealthScreen
import com.dominiqueherbrigpersonalteam.lademonitor.ui.tools.CombustionComparisonScreen
import com.dominiqueherbrigpersonalteam.lademonitor.ui.tools.RangeCalculatorScreen
import com.dominiqueherbrigpersonalteam.lademonitor.ui.tools.ReimbursementScreen
import com.dominiqueherbrigpersonalteam.lademonitor.ui.tools.TariffCalculatorScreen
import com.dominiqueherbrigpersonalteam.lademonitor.ui.tools.ToolsScreen
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
    // Eine Liste von Tools, nicht ein Reiter je Tool - weitere kommen dort als Zeilen dazu.
    TOOLS("tools", R.string.tab_tools, Icons.Filled.Build),
    SETTINGS("settings", R.string.tab_settings, Icons.Filled.Settings)
}

@Composable
private fun MainScaffold() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val shortcutTarget by AppShortcuts.pending.collectAsStateWithLifecycle()

    fun navigateToTab(tab: Tab) {
        navController.navigate(tab.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // Erst ein Eintrag im Back-Stack heisst: der NavHost hat seinen Graphen, navigate() geht.
    val graphReady = backStackEntry != null

    // Kurzbefehl vom App-Symbol: wie ein Tipp auf den Reiter, ggf. mit dem Ziel obendrauf.
    LaunchedEffect(shortcutTarget, graphReady) {
        val target = shortcutTarget ?: return@LaunchedEffect
        if (!graphReady) return@LaunchedEffect
        AppShortcuts.consumePending()
        when (target) {
            AppShortcuts.Target.NEW_SESSION -> {
                AppShortcuts.requestNewSession()
                navigateToTab(Tab.SESSIONS)
            }
            AppShortcuts.Target.DASHBOARD -> navigateToTab(Tab.DASHBOARD)
            AppShortcuts.Target.REIMBURSEMENT -> {
                navigateToTab(Tab.TOOLS)
                navController.navigate("tools/reimbursement") { launchSingleTop = true }
            }
        }
    }

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
            composable(Tab.DASHBOARD.route) {
                DashboardScreen(
                    onOpenReminders = { id -> navController.navigate("settings/vehicles/reminders/$id") },
                    // Erst in den Tools-Reiter wechseln (wie ein Tipp auf den Reiter), dann das
                    // Tool darauf legen - so fuehrt "Zurueck" in die Tools-Liste.
                    onOpenRangeCalculator = { vehicleId ->
                        navController.navigate(Tab.TOOLS.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                        navController.navigate("tools/range" + (vehicleId?.let { "?vehicleId=$it" } ?: ""))
                    }
                )
            }
            composable(Tab.SESSIONS.route) { SessionsListScreen() }
            composable(Tab.MAP.route) { MapScreen() }
            composable(Tab.TOOLS.route) { ToolsScreen(navController) }
            composable(
                "tools/tariff?providerId={providerId}",
                arguments = listOf(navArgument("providerId") { type = NavType.StringType; nullable = true; defaultValue = null })
            ) { entry -> TariffCalculatorScreen(navController, entry.arguments?.getString("providerId")) }
            composable("tools/combustion") { CombustionComparisonScreen(navController) }
            composable("tools/battery") { BatteryHealthScreen(navController) }
            composable(
                "tools/range?vehicleId={vehicleId}",
                arguments = listOf(navArgument("vehicleId") { type = NavType.StringType; nullable = true; defaultValue = null })
            ) { entry -> RangeCalculatorScreen(navController, entry.arguments?.getString("vehicleId")) }
            composable("tools/reimbursement") { ReimbursementScreen(navController) }
            composable(Tab.SETTINGS.route) { SettingsScreen(navController) }
            composable("settings/vehicles") { VehiclesSettingsScreen(navController) }
            composable(
                "settings/vehicles/costs/{vehicleId}",
                arguments = listOf(navArgument("vehicleId") { type = NavType.StringType })
            ) { entry -> VehicleCostsScreen(navController, entry.arguments?.getString("vehicleId") ?: "") }
            composable(
                "settings/vehicles/reminders/{vehicleId}",
                arguments = listOf(navArgument("vehicleId") { type = NavType.StringType })
            ) { entry -> VehicleRemindersScreen(navController, entry.arguments?.getString("vehicleId") ?: "") }
            composable("settings/providers") { ProvidersSettingsScreen(navController) }
            composable("settings/fees") { FeesSettingsScreen(navController) }
            composable("settings/tires") { TiresSettingsScreen(navController) }
            composable("settings/locations") { LocationsSettingsScreen(navController) }
            composable("settings/connection") { ConnectionSettingsScreen(navController) }
            composable("settings/account") { AccountSettingsScreen(navController) }
            composable("settings/chargebar") { ChargeBarSettingsScreen(navController) }
        }
    }
}
