package com.dominiqueherbrigpersonalteam.lademonitor.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dominiqueherbrigpersonalteam.lademonitor.R
import com.dominiqueherbrigpersonalteam.lademonitor.data.settings.AppSettings

/** Where the Lademonitor server is hosted (the link in the hint and in the mode selection). */
const val SERVER_SOURCE_URL = "https://github.com/iDomi94/Lademonitor-Server"

/**
 * Server-address section shared by the login screen and the connection settings: the hosting
 * choice (own domain vs. lademonitor.cloud) plus — only when self-hosted — the address field.
 *
 * Port of the section that exists in both `AuthView` and `ServerSettingsView` on iOS.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerAddressSection(modifier: Modifier = Modifier) {
    val serverUrl by AppSettings.serverUrlString.collectAsStateWithLifecycle()
    val hosting by AppSettings.serverHosting.collectAsStateWithLifecycle()

    Column(modifier.fillMaxWidth()) {
        Text(stringResource(R.string.auth_server_address_label), style = MaterialTheme.typography.labelLarge)

        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            AppSettings.ServerHosting.entries.forEachIndexed { index, choice ->
                SegmentedButton(
                    selected = hosting == choice,
                    onClick = { AppSettings.setServerHosting(choice) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = AppSettings.ServerHosting.entries.size
                    )
                ) { Text(stringResource(choice.labelRes), maxLines = 1) }
            }
        }

        if (hosting == AppSettings.ServerHosting.SELF_HOSTED) {
            OutlinedTextField(
                value = serverUrl,
                onValueChange = { AppSettings.setServerUrlString(it) },
                placeholder = { Text(stringResource(R.string.auth_server_address_placeholder)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
        }

        Text(
            when (hosting) {
                AppSettings.ServerHosting.SELF_HOSTED ->
                    stringResource(R.string.server_hosting_self_hosted_hint)

                AppSettings.ServerHosting.CLOUD ->
                    stringResource(R.string.server_hosting_cloud_hint)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
        SourceCodeLink()
    }
}

/** Link to the server's source code — the same one the iOS footers carry. */
@Composable
fun SourceCodeLink(modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    Text(
        stringResource(R.string.server_source_link),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
        textDecoration = TextDecoration.Underline,
        modifier = modifier
            .padding(top = 4.dp)
            .clickable { uriHandler.openUri(SERVER_SOURCE_URL) }
    )
}
