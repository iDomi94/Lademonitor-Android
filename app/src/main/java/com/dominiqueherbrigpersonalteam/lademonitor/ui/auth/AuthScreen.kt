package com.dominiqueherbrigpersonalteam.lademonitor.ui.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dominiqueherbrigpersonalteam.lademonitor.R
import com.dominiqueherbrigpersonalteam.lademonitor.data.remote.ApiException
import com.dominiqueherbrigpersonalteam.lademonitor.data.repo.SyncService
import com.dominiqueherbrigpersonalteam.lademonitor.data.session.SessionManager
import com.dominiqueherbrigpersonalteam.lademonitor.data.remote.ApiClient
import com.dominiqueherbrigpersonalteam.lademonitor.data.settings.AppMode
import com.dominiqueherbrigpersonalteam.lademonitor.data.settings.AppSettings
import com.dominiqueherbrigpersonalteam.lademonitor.ui.common.ServerAddressSection
import kotlinx.coroutines.launch

/** Login/registration incl. server address. Port of the iOS `AuthView`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen() {
    val scope = rememberCoroutineScope()
    // Read as state so the submit button enables as soon as an address is entered.
    val serverUrl by AppSettings.serverUrlString.collectAsStateWithLifecycle()
    val isConfigured = remember(serverUrl) { AppSettings.isConfigured }

    var isRegistering by remember { mutableStateOf(false) }
    // On sign-in this is the username OR the e-mail address; on registration it is the username.
    var identifier by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordConfirm by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showingForgotPassword by remember { mutableStateOf(false) }

    val passwordsMismatchMessage = stringResource(R.string.auth_error_passwords_mismatch)
    val registerLabel = stringResource(R.string.auth_action_register)
    val loginLabel = stringResource(R.string.auth_action_login)

    val trimmedIdentifier = identifier.trim()
    val trimmedEmail = email.trim()
    val canSubmit = isConfigured && password.length >= 8 && when {
        // On registration the input is the username — minimum length 3, as server-side.
        isRegistering -> trimmedIdentifier.length >= 3 && password == passwordConfirm
        // On sign-in it may also be an e-mail address; the username rule does not apply here,
        // the server decides.
        else -> trimmedIdentifier.isNotEmpty()
    }

    fun submit() {
        errorMessage = null
        if (isRegistering && password != passwordConfirm) {
            errorMessage = passwordsMismatchMessage
            return
        }
        scope.launch {
            isSubmitting = true
            try {
                val response = if (isRegistering) {
                    ApiClient.register(
                        trimmedIdentifier, password, trimmedEmail.takeIf { it.isNotEmpty() }
                    )
                } else {
                    ApiClient.login(trimmedIdentifier, password)
                }
                SessionManager.completeAuthentication(response)
                // If data is already on the device and this account is new here, startAfterLogin()
                // asks first instead of pushing it into the account just signed in to (the dialog
                // hangs on LademonitorRoot, see SyncService.pendingLocalDataDecision). Otherwise
                // taking over local data is not a special case, just the first normal sync pass.
                SyncService.startAfterLogin()
            } catch (e: ApiException.Server) {
                errorMessage = e.serverMessage
            } catch (e: Exception) {
                errorMessage = e.localizedMessage
            } finally {
                isSubmitting = false
            }
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            ServerAddressSection()

            Spacer(Modifier.height(16.dp))
            Text(
                if (isRegistering) registerLabel else loginLabel,
                style = MaterialTheme.typography.labelLarge
            )
            OutlinedTextField(
                value = identifier,
                onValueChange = { identifier = it },
                label = {
                    Text(
                        if (isRegistering) stringResource(R.string.auth_username_label)
                        else stringResource(R.string.auth_identifier_label)
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (isRegistering) KeyboardType.Text else KeyboardType.Email
                ),
                modifier = Modifier.fillMaxWidth()
            )
            if (isRegistering) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(stringResource(R.string.auth_email_optional_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.auth_password_label)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            if (isRegistering) {
                OutlinedTextField(
                    value = passwordConfirm,
                    onValueChange = { passwordConfirm = it },
                    label = { Text(stringResource(R.string.auth_password_confirm_label)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
            Text(
                if (isRegistering) stringResource(R.string.auth_register_hint)
                else stringResource(R.string.auth_login_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
            }

            Button(
                onClick = { submit() },
                enabled = !isSubmitting && canSubmit,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(if (isRegistering) registerLabel else loginLabel)
                }
            }

            TextButton(
                onClick = {
                    isRegistering = !isRegistering
                    errorMessage = null
                    passwordConfirm = ""
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (isRegistering) stringResource(R.string.auth_toggle_to_login)
                    else stringResource(R.string.auth_toggle_to_register)
                )
            }

            if (!isRegistering) {
                TextButton(
                    onClick = { showingForgotPassword = true },
                    enabled = isConfigured,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.auth_forgot_password_action)) }
            }

            Spacer(Modifier.height(16.dp))
            TextButton(
                onClick = { AppSettings.setAppMode(AppMode.LOCAL_ONLY) },
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.auth_use_local_only)) }
        }
    }

    if (showingForgotPassword) {
        ForgotPasswordDialog(
            initialIdentifier = trimmedIdentifier,
            onDismiss = { showingForgotPassword = false }
        )
    }
}

/**
 * Requests a link to reset the password.
 *
 * Actually setting the password deliberately does NOT happen in the app but through the link in
 * the mail in the browser: the token belongs in exactly one hand, and the web page for it already
 * exists. The app only triggers the sending.
 */
@Composable
private fun ForgotPasswordDialog(initialIdentifier: String, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    // Takes over what the sign-in form already holds — whoever just tried to sign in without
    // success should not have to type it again.
    var identifier by remember { mutableStateOf(initialIdentifier) }
    var isSubmitting by remember { mutableStateOf(false) }
    var didSubmit by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.forgot_password_title)) },
        text = {
            Column {
                if (didSubmit) {
                    Text(stringResource(R.string.forgot_password_sent))
                    Text(
                        stringResource(R.string.forgot_password_sent_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                } else {
                    OutlinedTextField(
                        value = identifier,
                        onValueChange = { identifier = it },
                        label = { Text(stringResource(R.string.auth_identifier_label)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        stringResource(R.string.forgot_password_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Text(
                        stringResource(R.string.forgot_password_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    errorMessage?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (!didSubmit) {
                TextButton(
                    onClick = {
                        scope.launch {
                            errorMessage = null
                            isSubmitting = true
                            try {
                                ApiClient.requestPasswordReset(identifier.trim())
                                // The server always answers the same, whether the account exists
                                // or not — so the app must not distinguish anything here either.
                                didSubmit = true
                            } catch (e: Exception) {
                                // Only real connection/server errors land here; an unknown account
                                // looks like a success to the client.
                                errorMessage = e.localizedMessage
                            } finally {
                                isSubmitting = false
                            }
                        }
                    },
                    enabled = !isSubmitting && identifier.trim().isNotEmpty()
                ) { Text(stringResource(R.string.forgot_password_submit_action)) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    if (didSubmit) stringResource(R.string.action_done)
                    else stringResource(R.string.action_cancel)
                )
            }
        }
    )
}
