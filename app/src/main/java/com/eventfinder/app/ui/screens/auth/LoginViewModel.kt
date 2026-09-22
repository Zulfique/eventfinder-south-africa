package com.eventfinder.app.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.eventfinder.app.R
import com.eventfinder.app.data.repository.AuthRepository
import com.eventfinder.app.ui.components.UiMessage
import com.eventfinder.app.utils.AppLogger
import com.eventfinder.app.utils.RegistrationValidator
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** UI state for the login screen. */
data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val showPassword: Boolean = false,
    val isSubmitting: Boolean = false,
    val emailError: Int? = null,
    val passwordError: Int? = null,
    val biometricAvailable: Boolean = false,
    val biometricEnabled: Boolean = false,
    val passwordResetComplete: Boolean = false
)

/**
 * Login flow (FR-01). Validates fields client-side before invoking the
 * repository; a successful login updates the persisted session (FR-01).
 */
class LoginViewModel(
    private val authRepository: AuthRepository,
    biometricAvailable: Boolean
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        LoginUiState(biometricAvailable = biometricAvailable)
    )
    val uiState = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<UiMessage>()
    val messages = _messages.asSharedFlow()

    init {
        viewModelScope.launch {
            authRepository.biometricEnrolled.collect { enrolled ->
                _uiState.update {
                    it.copy(
                        biometricEnabled =
                            enrolled && it.biometricAvailable
                    )
                }
            }
        }
    }

    fun onEmailChange(value: String) {
        _uiState.update { it.copy(email = value, emailError = null) }
    }

    fun onPasswordChange(value: String) {
        _uiState.update { it.copy(password = value, passwordError = null) }
    }

    fun togglePasswordVisibility() {
        _uiState.update { it.copy(showPassword = !it.showPassword) }
    }

    /** Validates and attempts a password login. */
    fun login(onSuccess: () -> Unit) {
        val state = _uiState.value
        val validation = RegistrationValidator.validateLogin(state.email, state.password)
        if (validation is com.eventfinder.app.utils.ValidationResult.Invalid) {
            // Map the shared validation keys to per-field errors.
            val errors = validation.messages
            _uiState.update {
                it.copy(
                    emailError = if (errors.contains("invalid_email")) R.string.invalid_email else null,
                    passwordError = if (errors.contains("invalid_password")) R.string.invalid_password else null
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            authRepository.login(state.email, state.password)
                .onSuccess { user ->
                    AppLogger.i("LoginViewModel", "Login succeeded for ${user.email}")
                    _messages.emit(UiMessage.Resource(R.string.login_success))
                    onSuccess()
                }
                .onFailure { throwable ->
                    AppLogger.w("LoginViewModel", "Login failed: ${throwable.message}")
                    _messages.emit(UiMessage.Resource(R.string.login_failed))
                    _uiState.update { it.copy(passwordError = R.string.login_failed) }
                }
            _uiState.update { it.copy(isSubmitting = false) }
        }
    }

    /** Completes a biometric unlock straight into the app. */
    fun confirmBiometricLogin(onSuccess: () -> Unit) {
        viewModelScope.launch {
            authRepository.biometricLogin()
                .onSuccess {
                    AppLogger.i("LoginViewModel", "Biometric login succeeded")
                    onSuccess()
                }
                .onFailure {
                    _messages.emit(UiMessage.Resource(R.string.login_failed))
                }
        }
    }

    fun requestPasswordReset(newPassword: String, confirmPassword: String) {
        val email = _uiState.value.email
        if (newPassword != confirmPassword) {
            viewModelScope.launch { _messages.emit(UiMessage.Resource(R.string.passwords_mismatch)) }
            return
        }
        if (!com.eventfinder.app.utils.EmailValidator.isValid(email)) {
            viewModelScope.launch {
                _uiState.update { it.copy(emailError = R.string.invalid_email) }
                _messages.emit(UiMessage.Resource(R.string.invalid_email))
            }
            return
        }
        // Surface password-policy failures before hitting the repository so the
        // dialog gives actionable feedback (the repository only returns a generic
        // "weak_password" string that was previously swallowed as reset_failed).
        when (val validation = com.eventfinder.app.utils.PasswordValidator.validate(newPassword)) {
            is com.eventfinder.app.utils.ValidationResult.Invalid -> {
                val key = validation.messages.firstOrNull() ?: "weak_password"
                val resId = when (key) {
                    "password_too_short" -> R.string.password_too_short
                    "password_no_upper" -> R.string.password_no_upper
                    "password_no_digit" -> R.string.password_no_digit
                    "password_no_special" -> R.string.password_no_special
                    else -> R.string.weak_password
                }
                viewModelScope.launch { _messages.emit(UiMessage.Resource(resId)) }
                return
            }
            else -> Unit
        }
        viewModelScope.launch {
            authRepository.resetPassword(email, newPassword)
                .onSuccess {
                    AppLogger.i("LoginViewModel", "Local password reset succeeded")
                    _uiState.update { it.copy(passwordResetComplete = true) }
                    _messages.emit(UiMessage.Resource(R.string.reset_success))
                }
                .onFailure { throwable ->
                    AppLogger.w("LoginViewModel", "Password reset failed: ${throwable.message}")
                    when (throwable.message) {
                        "password_reset_requires_authenticated_backend" ->
                            _messages.emit(UiMessage.Resource(R.string.reset_not_available))
                        "invalid_email" -> {
                            _uiState.update { it.copy(emailError = R.string.invalid_email) }
                            _messages.emit(UiMessage.Resource(R.string.invalid_email))
                        }
                        "unknown_email" -> _messages.emit(UiMessage.Resource(R.string.unknown_email))
                        "weak_password" -> _messages.emit(UiMessage.Resource(R.string.weak_password))
                        "same_password" -> _messages.emit(UiMessage.Resource(R.string.password_unchanged))
                        else -> _messages.emit(UiMessage.Resource(R.string.reset_failed))
                    }
                }
        }
    }

    /** Clears the one-shot flag once the screen has closed the reset dialog. */
    fun consumePasswordReset() {
        _uiState.update { it.copy(passwordResetComplete = false) }
    }

    companion object {
        fun factory(
            authRepository: AuthRepository,
            biometricAvailable: Boolean
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                LoginViewModel(authRepository, biometricAvailable)
            }
        }
    }
}