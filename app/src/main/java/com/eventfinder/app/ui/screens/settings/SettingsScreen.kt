package com.eventfinder.app.ui.screens.settings

import android.app.Activity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eventfinder.app.R
import com.eventfinder.app.di.AppContainer
import com.eventfinder.app.domain.model.SupportedLanguage
import com.eventfinder.app.security.BiometricAuth
import com.eventfinder.app.ui.components.resolve

/**
 * Screen 9 (Settings): language, biometric login and notification preferences.
 * Changing the language persists it and recreates the activity so the new
 * locale is applied everywhere (FR-08).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onLoggedOut: () -> Unit = {}
) {
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(container))
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val biometricSupported = BiometricAuth.isAvailable(context)
    val snackbarHostState = remember { SnackbarHostState() }

    var showChangePassword by remember { mutableStateOf(false) }
    var showDeleteAccount by remember { mutableStateOf(false) }

    // Recreate once when the persisted locale changes so the UI redraws.
    val languageAtStart = remember { state.language }
    LaunchedEffect(state.language) {
        if (state.language != languageAtStart) {
            (context as? Activity)?.recreate()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { msg ->
            msg.resolve(context)?.let { snackbarHostState.showSnackbar(it) }
        }
    }

    if (showChangePassword) {
        ChangePasswordDialog(
            onDismiss = { showChangePassword = false },
            onConfirm = { current, newPassword, confirm ->
                showChangePassword = false
                viewModel.changePassword(current, newPassword, confirm)
            }
        )
    }

    if (showDeleteAccount) {
        AlertDialog(
            onDismissRequest = { showDeleteAccount = false },
            title = { Text(stringResource(R.string.delete_account)) },
            text = { Text(stringResource(R.string.delete_account_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteAccount = false
                    viewModel.deleteAccount(onLoggedOut)
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAccount = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.change_password)) },
                        supportingContent = { Text(stringResource(R.string.change_password_hint)) },
                        leadingContent = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                        modifier = Modifier.clickable { showChangePassword = true }
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.clear_cache)) },
                        supportingContent = { Text(stringResource(R.string.clear_cache_hint)) },
                        leadingContent = { Icon(Icons.Outlined.DeleteSweep, contentDescription = null) },
                        modifier = Modifier.clickable { viewModel.clearCache() }
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = {
                            Text(stringResource(R.string.delete_account), color = MaterialTheme.colorScheme.error)
                        },
                        supportingContent = { Text(stringResource(R.string.delete_account_hint)) },
                        leadingContent = {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        modifier = Modifier.clickable { showDeleteAccount = true }
                    )
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column {
                    LanguageSelector(state.language, viewModel::setLanguage)
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.biometric_login)) },
                        supportingContent = {
                            Text(
                                if (biometricSupported) stringResource(R.string.biometric_available)
                                else stringResource(R.string.biometric_unavailable)
                            )
                        },
                        leadingContent = { Icon(Icons.Outlined.Fingerprint, contentDescription = null) },
                        trailingContent = {
                            Switch(
                                checked = state.biometricEnabled,
                                enabled = biometricSupported,
                                onCheckedChange = viewModel::setBiometric
                            )
                        }
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.event_reminders)) },
                        supportingContent = { Text(stringResource(R.string.event_reminders_hint)) },
                        leadingContent = { Icon(Icons.Outlined.NotificationsActive, contentDescription = null) },
                        trailingContent = {
                            Switch(
                                checked = state.remindersEnabled,
                                onCheckedChange = viewModel::setReminders
                            )
                        }
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.new_events_alerts)) },
                        leadingContent = { Icon(Icons.Outlined.Notifications, contentDescription = null) },
                        trailingContent = {
                            Switch(
                                checked = state.newEventsAlerts,
                                onCheckedChange = viewModel::setNewEventsAlerts
                            )
                        }
                    )
                }
            }

            Text(
                stringResource(R.string.settings_about, "EventFinder"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChangePasswordDialog(
    onDismiss: () -> Unit,
    onConfirm: (current: String, newPassword: String, confirm: String) -> Unit
) {
    var current by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.change_password)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.password_rules_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = current,
                    onValueChange = { current = it },
                    label = { Text(stringResource(R.string.current_password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text(stringResource(R.string.new_password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    label = { Text(stringResource(R.string.confirm_password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(current, newPassword, confirm) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageSelector(
    selected: SupportedLanguage,
    onSelect: (SupportedLanguage) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    // A plain Box + DropdownMenu is used (instead of ExposedDropdownMenuBox) so the
    // custom row reliably opens the menu on tap.
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Language, contentDescription = null)
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.language), style = MaterialTheme.typography.bodyLarge)
                Text(
                    selected.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(SupportedLanguage.ENGLISH.displayName) },
                onClick = {
                    onSelect(SupportedLanguage.ENGLISH)
                    expanded = false
                }
            )
            DropdownMenuItem(
                text = { Text(SupportedLanguage.AFRIKAANS.displayName) },
                onClick = {
                    onSelect(SupportedLanguage.AFRIKAANS)
                    expanded = false
                }
            )
        }
    }
}