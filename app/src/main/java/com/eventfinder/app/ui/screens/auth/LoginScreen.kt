package com.eventfinder.app.ui.screens.auth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eventfinder.app.R
import com.eventfinder.app.di.AppContainer
import com.eventfinder.app.security.BiometricAuth
import com.eventfinder.app.security.BiometricFailure
import com.eventfinder.app.security.BiometricMethods
import com.eventfinder.app.security.GoogleSignInHelper
import com.eventfinder.app.ui.components.resolve
import com.eventfinder.app.utils.AppLogger
import kotlinx.coroutines.launch

/**
 * Screen 2 (Login), rebuilt to the 2026 sheet: branded hero, email/phone
 * selector, icon-led fields, remember-me and a full-screen biometric flow with
 * success and failure panels.
 *
 * Password reset stays a two-step verified flow, and Google sign-in is kept as
 * an alternative because removing a working sign-in path would be a
 * regression, not a redesign.
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
    val methods = remember { BiometricMethods.detect(activity) }
    val biometricAvailable = methods.hasAny
    val viewModel: LoginViewModel = viewModel(
        factory = LoginViewModel.factory(container.authRepository, biometricAvailable)
    )
    val uiState by viewModel.uiState.collectAsState()
    var showForgotDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val googleSignInHelper = remember { GoogleSignInHelper(activity) }

    var biometricStage by remember { mutableStateOf<BiometricStage?>(null) }
    var biometricFailureText by remember { mutableStateOf<String?>(null) }
    var biometricRetryAllowed by remember { mutableStateOf(true) }
    // Kept in state so the prompt's callbacks outlive the trigger.
    var biometricPrompt by remember { mutableStateOf<BiometricAuth?>(null) }

    fun startBiometric() {
        val prompt = BiometricAuth(
            activity = activity,
            onAuthenticated = {
                viewModel.confirmBiometricLogin(
                    onSuccess = {
                        biometricFailureText = null
                        biometricStage = BiometricStage.SUCCESS
                    },
                    onFailure = {
                        biometricFailureText = activity.getString(R.string.biometric_failed_hint)
                        biometricRetryAllowed = true
                        biometricStage = BiometricStage.FAILURE
                    }
                )
            },
            onError = { failure ->
                AppLogger.w("LoginScreen", "Biometric prompt failed: $failure")
                when (failure) {
                    BiometricFailure.CANCELLED -> Unit
                    BiometricFailure.INVALIDATED -> {
                        viewModel.clearBiometricEnrollment()
                        biometricStage = null
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(
                                activity.getString(R.string.biometric_invalidated)
                            )
                        }
                    }
                    BiometricFailure.UNAVAILABLE -> {
                        biometricFailureText =
                            activity.getString(R.string.biometric_unavailable_now)
                        biometricRetryAllowed = false
                        biometricStage = BiometricStage.FAILURE
                    }
                    BiometricFailure.LOCKED_OUT -> {
                        biometricFailureText = activity.getString(R.string.biometric_locked_out)
                        biometricRetryAllowed = true
                        biometricStage = BiometricStage.FAILURE
                    }
                    BiometricFailure.FAILED -> {
                        biometricFailureText = null
                        biometricRetryAllowed = true
                        biometricStage = BiometricStage.FAILURE
                    }
                }
            }
        )
        biometricPrompt = prompt
        prompt.authenticate()
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

    val stage = biometricStage
    val primary = MaterialTheme.colorScheme.primary
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        // The hero glow runs behind the status bar so there is no seam.
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)
    ) { padding ->
        val backdrop = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to primary.copy(alpha = 0.20f),
                    0.30f to MaterialTheme.colorScheme.background,
                    1f to MaterialTheme.colorScheme.background
                )
            )
        if (stage != null) {
            Box(backdrop.padding(padding)) {
                BiometricLoginScreen(
                    methods = methods,
                    stage = stage,
                    failureMessage = biometricFailureText,
                    onAuthenticate = if (biometricRetryAllowed) ({ startBiometric() }) else null,
                    onUsePassword = {
                        biometricStage = null
                        biometricFailureText = null
                    },
                    onContinue = onLoggedIn,
                    onNeedHelp = {
                        biometricStage = null
                        viewModel.consumePasswordReset()
                        showForgotDialog = true
                    }
                )
            }
        } else {
            Column(
                modifier = backdrop
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding()
                    .imePadding()
            ) {
                LoginHero()

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                ) {
                    Text(
                        stringResource(R.string.welcome_back),
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.sign_in_to_continue),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(20.dp))

                    AuthMethodSelector(
                        onPhoneTap = {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(
                                    activity.getString(R.string.phone_sign_in_soon)
                                )
                            }
                        }
                    )
                    Spacer(Modifier.height(16.dp))

                    OutlinedTextField(
                        value = uiState.email,
                        onValueChange = viewModel::onEmailChange,
                        label = { Text(stringResource(R.string.email_address)) },
                        leadingIcon = { Icon(Icons.Outlined.MailOutline, contentDescription = null) },
                        singleLine = true,
                        isError = uiState.emailError != null,
                        supportingText = {
                            uiState.emailError?.let { res -> Text(stringResource(res)) }
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = loginFieldColors(),
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
                                    contentDescription = stringResource(
                                        if (uiState.showPassword) R.string.hide_password
                                        else R.string.show_password
                                    )
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
                        shape = RoundedCornerShape(14.dp),
                        colors = loginFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = uiState.rememberMe,
                            onCheckedChange = viewModel::setRememberMe,
                            colors = CheckboxDefaults.colors(
                                checkedColor = MaterialTheme.colorScheme.primary,
                                checkmarkColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                        Text(
                            stringResource(R.string.remember_me),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.weight(1f))
                        TextButton(
                            onClick = {
                                viewModel.consumePasswordReset()
                                showForgotDialog = true
                            },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                horizontal = 8.dp,
                                vertical = 4.dp
                            )
                        ) {
                            Text(
                                stringResource(R.string.forgot_password),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Button(
                        onClick = { viewModel.login(onLoggedIn) },
                        enabled = !uiState.isSubmitting && !uiState.isGoogleSigningIn,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                    ) {
                        if (uiState.isSubmitting) {
                            CircularProgressIndicator(
                                Modifier.size(22.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text(
                                stringResource(R.string.login_action),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                Icons.AutoMirrored.Outlined.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    if (GoogleSignInHelper.isConfigured) {
                        Spacer(Modifier.height(10.dp))
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
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            if (uiState.isGoogleSigningIn) {
                                CircularProgressIndicator(Modifier.size(22.dp))
                            } else {
                                Image(
                                    painter = painterResource(R.drawable.google_g_logo),
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(stringResource(R.string.login_google))
                            }
                        }
                    }

                    if (biometricAvailable && uiState.biometricEnabled) {
                        Spacer(Modifier.height(18.dp))
                        OrDivider()
                        Spacer(Modifier.height(14.dp))
                        OutlinedButton(
                            onClick = {
                                biometricFailureText = null
                                biometricRetryAllowed = true
                                biometricStage = BiometricStage.METHODS
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Fingerprint,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(stringResource(R.string.login_with_biometrics))
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.no_account_yet),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = onCreateAccount) {
                            Text(
                                stringResource(R.string.sign_up),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    TextButton(
                        onClick = onGuestLogin,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            stringResource(R.string.continue_as_guest),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

/** Branded banner: logo mark, wordmark and tagline over a soft green glow. */
@Composable
private fun LoginHero() {
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(214.dp)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(primary.copy(alpha = 0.22f), Color.Transparent),
                    center = Offset(size.width / 2f, size.height * 0.18f),
                    radius = size.minDimension * 0.55f
                ),
                radius = size.minDimension * 0.55f,
                center = Offset(size.width / 2f, size.height * 0.18f)
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(primary.copy(alpha = 0.14f))
                    .border(1.5.dp, primary.copy(alpha = 0.75f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.EventAvailable,
                    contentDescription = null,
                    tint = primary,
                    modifier = Modifier.size(30.dp)
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "EventFinder",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                stringResource(R.string.brand_country),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 3.sp,
                color = primary
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.brand_tagline),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Email / Phone selector from the design. Phone sign-in has no backend yet, so
 * the segment is visibly disabled and explains itself instead of pretending to
 * accept a number.
 */
@Composable
private fun AuthMethodSelector(onPhoneTap: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(9.dp))
                .background(MaterialTheme.colorScheme.primary)
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.MailOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.email),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(9.dp))
                .clickable(onClick = onPhoneTap)
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Phone,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.phone_with_soon),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun loginFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
    focusedContainerColor = MaterialTheme.colorScheme.surface,
    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
    focusedLeadingIconColor = MaterialTheme.colorScheme.primary,
    unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    focusedTrailingIconColor = MaterialTheme.colorScheme.primary,
    unfocusedTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
)
