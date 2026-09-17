package com.eventfinder.app.ui.screens.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eventfinder.app.R
import com.eventfinder.app.di.AppContainer
import com.eventfinder.app.ui.components.resolve
import com.eventfinder.app.utils.PasswordStrength

/**
 * Screen 3 (Registration) from the design: name, email, password + strength
 * indicator, password confirmation and a language preference (EN/AF).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    container: AppContainer,
    onRegistered: () -> Unit,
    onLogin: () -> Unit
) {
    val viewModel: RegisterViewModel = viewModel(
        factory = RegisterViewModel.factory(container.authRepository)
    )
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    BackHandler { onLogin() }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            message.resolve(context)?.let { snackbarHostState.showSnackbar(it) }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.register_title)) }) },
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
                stringResource(R.string.register_subtitle),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(24.dp))

            OutlinedTextField(
                value = state.fullName,
                onValueChange = viewModel::onNameChange,
                label = { Text(stringResource(R.string.full_name)) },
                leadingIcon = { Icon(Icons.Outlined.Face, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = state.email,
                onValueChange = viewModel::onEmailChange,
                label = { Text(stringResource(R.string.email)) },
                leadingIcon = { Icon(Icons.Outlined.MailOutline, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = state.password,
                onValueChange = viewModel::onPasswordChange,
                label = { Text(stringResource(R.string.password)) },
                leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = viewModel::togglePasswordVisibility) {
                        Icon(
                            if (state.showPassword) Icons.Outlined.VisibilityOff
                            else Icons.Outlined.Visibility,
                            contentDescription = null
                        )
                    }
                },
                singleLine = true,
                visualTransformation = if (state.showPassword) VisualTransformation.None
                else PasswordVisualTransformation(),
                supportingText = { PasswordStrengthRow(state.passwordStrength) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = state.confirmPassword,
                onValueChange = viewModel::onConfirmPasswordChange,
                label = { Text(stringResource(R.string.confirm_password)) },
                leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )

            // Inline validation errors (all shown at once, user corrects in one pass).
            state.errorMessages.forEach { resId ->
                Text(
                    text = stringResource(resId),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                )
            }

            Spacer(Modifier.height(20.dp))

            Text(stringResource(R.string.language_preference), style = MaterialTheme.typography.labelLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = state.language == "en",
                    onClick = { viewModel.onLanguageChange("en") }
                )
                Text(stringResource(R.string.english))
                Spacer(Modifier.padding(8.dp))
                RadioButton(
                    selected = state.language == "af",
                    onClick = { viewModel.onLanguageChange("af") }
                )
                Text(stringResource(R.string.afrikaans))
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = { viewModel.register(onRegistered) },
                enabled = !state.isSubmitting,
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                if (state.isSubmitting) CircularProgressIndicator(Modifier.padding(vertical = 14.dp))
                else Text(stringResource(R.string.register), fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onLogin) {
                Text(stringResource(R.string.already_have_account))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Coloured strength indicator + label under the password field. */
@Composable
private fun PasswordStrengthRow(strength: PasswordStrength) {
    val strengthText = when (strength) {
        PasswordStrength.WEAK -> stringResource(R.string.password_strength_weak)
        PasswordStrength.MEDIUM -> stringResource(R.string.password_strength_medium)
        PasswordStrength.STRONG -> stringResource(R.string.password_strength_strong)
    }
    val color = when (strength) {
        PasswordStrength.WEAK -> MaterialTheme.colorScheme.error
        PasswordStrength.MEDIUM -> MaterialTheme.colorScheme.secondary
        PasswordStrength.STRONG -> MaterialTheme.colorScheme.primary
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.password_strength_weak) + " · " +
                stringResource(R.string.password_strength_medium) + " · " +
                stringResource(R.string.password_strength_strong),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.weight(1f))
        Text(strengthText, color = color, style = MaterialTheme.typography.labelMedium)
    }
}