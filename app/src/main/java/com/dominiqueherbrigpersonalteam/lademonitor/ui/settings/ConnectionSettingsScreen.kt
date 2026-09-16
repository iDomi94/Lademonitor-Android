package com.dominiqueherbrigpersonalteam.lademonitor.ui.settings

import androidx.compose.foundation.layout.Column
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

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.settings_section_connection)) },
            navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
            }
        )
    }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxWidth()) {
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
