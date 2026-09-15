package com.dominiqueherbrigpersonalteam.lademonitor.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dominiqueherbrigpersonalteam.lademonitor.R
import com.dominiqueherbrigpersonalteam.lademonitor.data.settings.AppMode
import com.dominiqueherbrigpersonalteam.lademonitor.data.settings.AppSettings
import com.dominiqueherbrigpersonalteam.lademonitor.ui.common.SourceCodeLink

/**
 * Shown once on the very first launch (AppMode.UNDECIDED). Server mode leads to the AuthScreen;
 * local-only mode jumps straight into the main app. Port of the iOS `ModeSelectionView`.
 */
@Composable
fun ModeSelectionScreen() {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Bewusst die gerundete Kachel (= das App-Icon) statt eines Symbols aus
            // dem Farbschema: dieser Screen folgt dem Hell/Dunkel-Modus des Systems,
            // und die graue Silhouette mit grünem Kabel ist für dunklen Grund
            // gezeichnet. Die Kachel bringt ihren eigenen Grund mit und sitzt damit
            // in beiden Modi richtig. Dekorativ - der Schriftzug darunter trägt den
            // Namen schon, deshalb keine contentDescription.
            Image(
                painterResource(R.drawable.logo_mark),
                contentDescription = null,
                modifier = Modifier.size(96.dp)
            )
            Spacer(Modifier.size(16.dp))
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.mode_selection_question),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 32.dp)
            )

            ModeOptionCard(
                icon = Icons.Filled.PhoneAndroid,
                title = stringResource(R.string.mode_selection_local_title),
                description = stringResource(R.string.mode_selection_local_description)
            ) { AppSettings.setAppMode(AppMode.LOCAL_ONLY) }

            Spacer(Modifier.size(16.dp))

            ModeOptionCard(
                icon = Icons.Filled.Dns,
                title = stringResource(R.string.mode_selection_server_title),
                description = stringResource(R.string.mode_selection_server_description),
                showSourceLink = true
            ) {
                AppSettings.setServerHosting(AppSettings.ServerHosting.SELF_HOSTED)
                AppSettings.setAppMode(AppMode.SERVER)
            }

            Spacer(Modifier.size(16.dp))

            ModeOptionCard(
                icon = Icons.Filled.Cloud,
                title = stringResource(R.string.mode_selection_cloud_title),
                description = stringResource(R.string.mode_selection_cloud_description)
            ) {
                AppSettings.setServerHosting(AppSettings.ServerHosting.CLOUD)
                AppSettings.setAppMode(AppMode.SERVER)
            }
        }
    }
}

@Composable
private fun ModeOptionCard(
    icon: ImageVector,
    title: String,
    description: String,
    showSourceLink: Boolean = false,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
                if (showSourceLink) SourceCodeLink()
            }
        }
    }
}
