package com.eventfinder.app.ui.screens.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eventfinder.app.R
import com.eventfinder.app.di.AppContainer
import com.eventfinder.app.security.BiometricAuth
import com.eventfinder.app.security.GoogleSignInHelper
import com.eventfinder.app.ui.components.resolve
import com.eventfinder.app.utils.AppLogger
import kotlinx.coroutines.launch

/**
 * Screen 2 (Login): email/password with validation, Google sign-in via
 * Credential Manager, biometric unlock when enabled, a two-step verified
 * password reset, and a link to registration.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    container: AppContainer,
    onLoggedIn: () -> Unit,
    onCreateAccount: () -> Unit,
    onGuestLogin: () -> Unit = {}
) {
    val activity = LocalContext.current as FragmentActivity
    val snackbarHostState = remember { SnackbarHostState() }
    val biometricAvailable = remember {
        BiometricAuth.isAvailable(activity)
    }
    val viewModel: LoginViewModel = viewModel(
        factory = LoginViewModel.factory(container.authRepository, biometricAvailable)
    )
    val uiState by viewModel.uiState.collectAsState()
    var showForgotDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val googleSignInHelper = remember { GoogleSignInHelper(activity) }

    // Biometric prompt wiring: falls back silently to password login on error.
    var biometricPrompt by remember {
        mutableStateOf<BiometricAuth?>(null)
    }

    LaunchedEffect(Unit) {
        biometricPrompt = BiometricAuth(
            activity = activity,
            onAuthenticated = { viewModel.confirmBiometricLogin(onLoggedIn) },
            onError = {
                AppLogger.w("LoginScreen", "Biometric prompt error: $it")
            }
        )
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            message.resolve(activity)?.let { snackbarHostState.showSnackbar(it) }
        }
    }

    if (showForgotDialog) {
        var resetToken by remember { mutableStateOf("") }
        var newPassword by remember { mutableStateOf("") }
        var confirmPassword by remember { mutableStateOf("") }

        LaunchedEffect(uiState.passwordResetComplete) {
            if (uiState.passwordResetComplete) {
                showForgotDialog = false
                viewModel.consumePasswordReset()
            }
        }

        AlertDialog(
            onDismissRequest = { showForgotDialog = false },
            title = { Text(stringResource(R.string.reset_password_title)) },
            text = {
                Column {
                    if (!uiState.resetEmailSent) {
                        Text(stringResource(R.string.reset_send_token_hint))
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = uiState.email,
                            onValueChange = viewModel::onEmailChange,
                            label = { Text(stringResource(R.string.email)) },
                            singleLine = true,
                            isError = uiState.emailError != null,
                            supportingText = {
                                uiState.emailError?.let { res -> Text(stringResource(res)) }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(stringResource(R.string.reset_enter_token_hint))
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = resetToken,
                            onValueChange = { resetToken = it },
                            label = { Text(stringResource(R.string.reset_token_label)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = newPassword,
                            onValueChange = { newPassword = it },
                            label = { Text(stringResource(R.string.new_password)) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it },
                            label = { Text(stringResource(R.string.confirm_password)) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !uiState.isSubmitting,
                    onClick = {
                        if (!uiState.resetEmailSent) viewModel.sendResetEmail()
                        else viewModel.confirmPasswordReset(resetToken, newPassword, confirmPassword)
                    }
                ) {
                    if (uiState.isSubmitting) {
                        CircularProgressIndicator(Modifier.size(18.dp))
                    } else {
                        Text(
                            stringResource(
                                if (!uiState.resetEmailSent) R.string.reset_send_token_action
                                else R.string.reset_password_action
                            )
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showForgotDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.login_title)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.login_subtitle),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(28.dp))

            OutlinedTextField(
                value = uiState.email,
                onValueChange = viewModel::onEmailChange,
                label = { Text(stringResource(R.string.email)) },
                leadingIcon = { Icon(Icons.Outlined.MailOutline, contentDescription = null) },
                singleLine = true,
                isError = uiState.emailError != null,
                supportingText = {
                    uiState.emailError?.let { res ->
                        Text(stringResource(res))
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = uiState.password,
                onValueChange = viewModel::onPasswordChange,
                label = { Text(stringResource(R.string.password)) },
                leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = viewModel::togglePasswordVisibility) {
                        Icon(
                            if (uiState.showPassword) Icons.Outlined.VisibilityOff
                            else Icons.Outlined.Visibility,
                            contentDescription = null
                        )
                    }
                },
                singleLine = true,
                visualTransformation = if (uiState.showPassword) VisualTransformation.None
                else PasswordVisualTransformation(),
                isError = uiState.passwordError != null,
                supportingText = {
                    uiState.passwordError?.let { res -> Text(stringResource(res)) }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = {
                    viewModel.consumePasswordReset()
                    showForgotDialog = true
                }) {
                    Text(stringResource(R.string.forgot_password))
                }
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = { viewModel.login(onLoggedIn) },
                enabled = !uiState.isSubmitting && !uiState.isGoogleSigningIn,
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                if (uiState.isSubmitting) CircularProgressIndicator(Modifier.size(22.dp))
                else Text(stringResource(R.string.login), fontWeight = FontWeight.SemiBold)
            }

            if (GoogleSignInHelper.isConfigured) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        coroutineScope.launch {
                            val idToken = googleSignInHelper.getCredential()
                            if (idToken != null) {
                                viewModel.signInWithGoogle(idToken, onLoggedIn)
                            }
                        }
                    },
                    enabled = !uiState.isSubmitting && !uiState.isGoogleSigningIn,
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    if (uiState.isGoogleSigningIn) {
                        CircularProgressIndicator(Modifier.size(22.dp))
                    } else {
                        Image(
                            painter = painterResource(R.drawable.google_g_logo),
                            contentDescription = "Google",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.login_google))
                    }
                }
            }

            if (biometricAvailable && uiState.biometricEnabled) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { biometricPrompt?.authenticate() },
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Icon(Icons.Outlined.Fingerprint, contentDescription = null)
                    Spacer(Modifier.padding(4.dp))
                    Text(stringResource(R.string.login_with_biometrics))
                }
            }

            Spacer(Modifier.height(16.dp))

            OutlinedButton(
                onClick = onGuestLogin,
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text(stringResource(R.string.continue_as_guest))
            }

            Spacer(Modifier.height(8.dp))

            TextButton(onClick = onCreateAccount) {
                Text(stringResource(R.string.login_no_account))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
