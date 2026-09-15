package com.dominiqueherbrigpersonalteam.lademonitor.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.dominiqueherbrigpersonalteam.lademonitor.R
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.AuthUser
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.NotificationSettingsPayload
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ReviewDigestFrequency
import com.dominiqueherbrigpersonalteam.lademonitor.data.remote.ApiClient
import com.dominiqueherbrigpersonalteam.lademonitor.data.remote.ApiException
import com.dominiqueherbrigpersonalteam.lademonitor.data.session.SessionManager
import com.dominiqueherbrigpersonalteam.lademonitor.ui.common.SectionCard
import kotlinx.coroutines.launch

/**
 * Account settings in server mode: e-mail address, own password, notifications and account
 * deletion. Port of the iOS `AccountSettingsView`.
 *
 * Everything here needs a server from 0.14.0 on. An older server does not even send the
 * corresponding fields in `/api/auth/me` — then the screen shows a note instead of the forms,
 * rather than offering buttons that answer with a 404 (see [AuthUser.supportsAccountFeatures]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSettingsScreen(navController: NavController) {
    val user by SessionManager.currentUser.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { SessionManager.reloadCurrentUser() }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.account_title)) },
            navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back)
                    )
                }
            }
        )
    }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val current = user
            if (current == null) {
                CircularProgressIndicator()
                return@Column
            }

            SectionCard {
                AccountHeader(stringResource(R.string.settings_section_account))
                AccountLabeledRow(stringResource(R.string.account_label_username), current.username)
                if (current.isAdmin) {
                    AccountLabeledRow(
                        stringResource(R.string.account_label_role),
                        stringResource(R.string.account_role_admin)
                    )
                }
            }

            if (!current.supportsAccountFeatures) {
                SectionCard {
                    Text(
                        stringResource(R.string.account_unsupported_server),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                return@Column
            }

            EmailSection(current)
            PasswordSection()
            NotificationsSection(current)
            DeleteAccountSection()
        }
    }
}

/** A small feedback line below a section — success in the tertiary colour, errors in red. */
private data class AccountStatusMessage(val text: String, val isError: Boolean)

@Composable
private fun AccountStatusLine(status: AccountStatusMessage?) {
    status ?: return
    Text(
        status.text,
        style = MaterialTheme.typography.bodySmall,
        color = if (status.isError) MaterialTheme.colorScheme.error
        else MaterialTheme.colorScheme.tertiary,
        modifier = Modifier.padding(top = 8.dp)
    )
}

// MARK: - E-mail

@Composable
private fun EmailSection(user: AuthUser) {
    val scope = rememberCoroutineScope()
    var email by remember(user.email) { mutableStateOf(user.email.orEmpty()) }
    var currentPassword by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<AccountStatusMessage?>(null) }

    val savedMessage = stringResource(R.string.account_saved)
    val resentMessage = stringResource(R.string.account_email_resent)

    SectionCard {
        AccountHeader(stringResource(R.string.account_section_email))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text(stringResource(R.string.account_email_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth()
        )

        if (!user.email.isNullOrEmpty()) {
            if (user.isEmailVerified) {
                Text(
                    stringResource(R.string.account_email_verified),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            } else {
                Text(
                    stringResource(R.string.account_email_unverified),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp)
                )
                TextButton(onClick = {
                    scope.launch {
                        status = try {
                            ApiClient.resendEmailVerification()
                            AccountStatusMessage(resentMessage, isError = false)
                        } catch (e: Exception) {
                            AccountStatusMessage(e.localizedMessage.orEmpty(), isError = true)
                        }
                    }
                }) { Text(stringResource(R.string.account_email_resend_action)) }
            }
        }

        OutlinedTextField(
            value = currentPassword,
            onValueChange = { currentPassword = it },
            label = { Text(stringResource(R.string.account_current_password_label)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )

        Button(
            onClick = {
                scope.launch {
                    status = null
                    isSaving = true
                    try {
                        val trimmed = email.trim()
                        val updated = ApiClient.updateEmail(
                            trimmed.takeIf { it.isNotEmpty() }, currentPassword
                        )
                        SessionManager.applyUpdatedUser(updated)
                        currentPassword = ""
                        status = AccountStatusMessage(savedMessage, isError = false)
                    } catch (e: ApiException.Server) {
                        status = AccountStatusMessage(e.serverMessage, isError = true)
                    } catch (e: Exception) {
                        status = AccountStatusMessage(e.localizedMessage.orEmpty(), isError = true)
                    } finally {
                        isSaving = false
                    }
                }
            },
            enabled = currentPassword.isNotEmpty() && !isSaving,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
        ) {
            if (isSaving) CircularProgressIndicator(Modifier.height(20.dp), strokeWidth = 2.dp)
            else Text(stringResource(R.string.account_email_save_action))
        }

        AccountStatusLine(status)
        // Both reasons belong visibly where one wonders why a password is needed at all.
        AccountHint(stringResource(R.string.account_email_hint))
    }
}

// MARK: - Password

@Composable
private fun PasswordSection() {
    val scope = rememberCoroutineScope()
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var newPasswordConfirm by remember { mutableStateOf("") }
    var isChanging by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<AccountStatusMessage?>(null) }

    val changedMessage = stringResource(R.string.account_password_changed)

    SectionCard {
        AccountHeader(stringResource(R.string.account_section_password))
        AccountPasswordField(
            currentPassword, { currentPassword = it },
            stringResource(R.string.account_current_password_label)
        )
        AccountPasswordField(
            newPassword, { newPassword = it },
            stringResource(R.string.account_new_password_label), topPadding = 8.dp
        )
        AccountPasswordField(
            newPasswordConfirm, { newPasswordConfirm = it },
            stringResource(R.string.account_new_password_confirm_label), topPadding = 8.dp
        )

        Button(
            onClick = {
                scope.launch {
                    status = null
                    isChanging = true
                    try {
                        SessionManager.changePassword(currentPassword, newPassword)
                        currentPassword = ""
                        newPassword = ""
                        newPasswordConfirm = ""
                        status = AccountStatusMessage(changedMessage, isError = false)
                    } catch (e: ApiException.Server) {
                        status = AccountStatusMessage(e.serverMessage, isError = true)
                    } catch (e: Exception) {
                        status = AccountStatusMessage(e.localizedMessage.orEmpty(), isError = true)
                    } finally {
                        isChanging = false
                    }
                }
            },
            enabled = currentPassword.isNotEmpty() && newPassword.length >= 8 &&
                newPassword == newPasswordConfirm && !isChanging,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
        ) {
            if (isChanging) CircularProgressIndicator(Modifier.height(20.dp), strokeWidth = 2.dp)
            else Text(stringResource(R.string.account_password_change_action))
        }

        AccountStatusLine(status)
        AccountHint(stringResource(R.string.account_password_hint))
    }
}

// MARK: - Notifications

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationsSection(user: AuthUser) {
    val scope = rememberCoroutineScope()
    // The switches have server-side defaults; if they are missing (older server) the defaults set
    // here stay in place.
    var backupFailed by remember(user) { mutableStateOf(user.notifyBackupFailed ?: true) }
    var myskodaError by remember(user) { mutableStateOf(user.notifyMyskodaError ?: true) }
    var monthlyReport by remember(user) { mutableStateOf(user.notifyMonthlyReport ?: false) }
    var newRegistration by remember(user) { mutableStateOf(user.notifyNewRegistration ?: true) }
    var reviewDigest by remember(user) {
        mutableStateOf(user.reviewDigest ?: ReviewDigestFrequency.OFF)
    }
    var isSaving by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<AccountStatusMessage?>(null) }
    var digestExpanded by remember { mutableStateOf(false) }

    val savedMessage = stringResource(R.string.account_saved)

    SectionCard {
        AccountHeader(stringResource(R.string.account_section_notifications))
        AccountToggleRow(stringResource(R.string.account_notify_backup_failed), backupFailed) { backupFailed = it }
        AccountToggleRow(stringResource(R.string.account_notify_myskoda_error), myskodaError) { myskodaError = it }
        AccountToggleRow(stringResource(R.string.account_notify_monthly_report), monthlyReport) { monthlyReport = it }
        if (user.isAdmin) {
            AccountToggleRow(stringResource(R.string.account_notify_new_registration), newRegistration) {
                newRegistration = it
            }
        }

        ExposedDropdownMenuBox(
            expanded = digestExpanded,
            onExpandedChange = { digestExpanded = it },
            modifier = Modifier.padding(top = 8.dp)
        ) {
            OutlinedTextField(
                value = stringResource(reviewDigest.labelRes),
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.account_review_digest_label)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = digestExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = digestExpanded,
                onDismissRequest = { digestExpanded = false }
            ) {
                ReviewDigestFrequency.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(stringResource(option.labelRes)) },
                        onClick = { reviewDigest = option; digestExpanded = false }
                    )
                }
            }
        }

        Button(
            onClick = {
                scope.launch {
                    status = null
                    isSaving = true
                    try {
                        val updated = ApiClient.updateNotifications(
                            NotificationSettingsPayload(
                                notifyBackupFailed = backupFailed,
                                notifyMyskodaError = myskodaError,
                                notifyMonthlyReport = monthlyReport,
                                notifyNewRegistration = newRegistration,
                                reviewDigest = reviewDigest
                            )
                        )
                        SessionManager.applyUpdatedUser(updated)
                        status = AccountStatusMessage(savedMessage, isError = false)
                    } catch (e: ApiException.Server) {
                        status = AccountStatusMessage(e.serverMessage, isError = true)
                    } catch (e: Exception) {
                        status = AccountStatusMessage(e.localizedMessage.orEmpty(), isError = true)
                    } finally {
                        isSaving = false
                    }
                }
            },
            enabled = !isSaving,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
        ) {
            if (isSaving) CircularProgressIndicator(Modifier.height(20.dp), strokeWidth = 2.dp)
            else Text(stringResource(R.string.action_save))
        }

        AccountStatusLine(status)
        AccountHint(
            if (user.email.isNullOrEmpty()) stringResource(R.string.account_notifications_hint_no_email)
            else stringResource(R.string.account_notifications_hint)
        )
    }
}

// MARK: - Delete account

@Composable
private fun DeleteAccountSection() {
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var isDeleting by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<AccountStatusMessage?>(null) }
    var showingConfirmation by remember { mutableStateOf(false) }

    val unsupportedMessage = stringResource(R.string.account_delete_unsupported_server)

    SectionCard {
        AccountHeader(stringResource(R.string.account_section_delete))
        AccountPasswordField(password, { password = it }, stringResource(R.string.account_current_password_label))

        TextButton(
            onClick = { showingConfirmation = true },
            enabled = password.isNotEmpty() && !isDeleting,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) {
            if (isDeleting) CircularProgressIndicator(Modifier.height(20.dp), strokeWidth = 2.dp)
            else Text(
                stringResource(R.string.account_delete_action),
                color = MaterialTheme.colorScheme.error
            )
        }

        AccountStatusLine(status)
        AccountHint(stringResource(R.string.account_delete_hint))
    }

    if (showingConfirmation) {
        AlertDialog(
            onDismissRequest = { showingConfirmation = false },
            title = { Text(stringResource(R.string.account_delete_confirm_title)) },
            text = { Text(stringResource(R.string.account_delete_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showingConfirmation = false
                    scope.launch {
                        status = null
                        isDeleting = true
                        try {
                            SessionManager.deleteAccount(password)
                            // From here on isAuthenticated is false and LademonitorRoot switches
                            // back by itself — no manual navigation needed.
                        } catch (e: ApiException.Server) {
                            status = if (e.statusCode == 404) {
                                AccountStatusMessage(unsupportedMessage, isError = true)
                            } else {
                                AccountStatusMessage(e.serverMessage, isError = true)
                            }
                        } catch (e: Exception) {
                            status = AccountStatusMessage(e.localizedMessage.orEmpty(), isError = true)
                        } finally {
                            isDeleting = false
                        }
                    }
                }) {
                    Text(
                        stringResource(R.string.action_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showingConfirmation = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

// MARK: - Building blocks

@Composable
private fun AccountHeader(text: String) {
    Text(
        text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun AccountHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun AccountLabeledRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AccountToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun AccountPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    topPadding: androidx.compose.ui.unit.Dp = 0.dp
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth().padding(top = topPadding)
    )
}
