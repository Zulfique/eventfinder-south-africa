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
import com.eventfinder.app.utils.PasswordStrength
import com.eventfinder.app.utils.PasswordValidator
import com.eventfinder.app.utils.RegistrationValidator
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** UI state for the registration screen. */
data class RegisterUiState(
    val fullName: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val showPassword: Boolean = false,
    val language: String = "en",
    val errorMessages: List<Int> = emptyList(),
    val isSubmitting: Boolean = false,
    val passwordStrength: PasswordStrength = PasswordStrength.WEAK
)

/**
 * Registration flow (FR-01) with full client-side validation, a live password
 * strength indicator and the language preference captured at sign-up (FR-08).
 */
class RegisterViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<UiMessage>()
    val messages = _messages.asSharedFlow()

    fun onNameChange(value: String) {
        _uiState.update { it.copy(fullName = value, errorMessages = emptyList()) }
    }

    fun onEmailChange(value: String) {
        _uiState.update { it.copy(email = value, errorMessages = emptyList()) }
    }

    fun onPasswordChange(value: String) {
        _uiState.update {
            it.copy(
                password = value,
                passwordStrength = PasswordValidator.strength(value),
                errorMessages = emptyList()
            )
        }
    }

    fun onConfirmPasswordChange(value: String) {
        _uiState.update { it.copy(confirmPassword = value, errorMessages = emptyList()) }
    }

    fun togglePasswordVisibility() {
        _uiState.update { it.copy(showPassword = !it.showPassword) }
    }

    fun onLanguageChange(language: String) {
        _uiState.update { it.copy(language = language) }
    }

    /** Translates a validation message key into a displayable string resource. */
    private fun messageKeyToResourceId(key: String): Int = when (key) {
        "name_required" -> R.string.name_required
        "name_too_short" -> R.string.name_too_short
        "invalid_email" -> R.string.invalid_email
        "password_too_short" -> R.string.password_too_short
        "password_no_upper" -> R.string.password_no_upper
        "password_no_digit" -> R.string.password_no_digit
        "password_no_special" -> R.string.password_no_special
        "passwords_mismatch" -> R.string.passwords_mismatch
        else -> R.string.error
    }

    fun register(onSuccess: () -> Unit) {
        val state = _uiState.value
        val validation = RegistrationValidator.validateForm(
            state.fullName, state.email, state.password, state.confirmPassword
        )
        if (validation is com.eventfinder.app.utils.ValidationResult.Invalid) {
            _uiState.update {
                it.copy(errorMessages = validation.messages.map(::messageKeyToResourceId))
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            authRepository.register(
                fullName = state.fullName,
                email = state.email,
                password = state.password,
                language = state.language
            )
                .onSuccess { user ->
                    AppLogger.i("RegisterViewModel", "Registration complete for ${user.email}")
                    _messages.emit(UiMessage.Resource(R.string.register_success))
                    onSuccess()
                }
                .onFailure { throwable ->
                    AppLogger.w("RegisterViewModel", "Registration failed: ${throwable.message}")
                    _messages.emit(
                        if (throwable.message == "email_in_use") {
                            UiMessage.Resource(R.string.invalid_email)
                        } else {
                            UiMessage.Resource(R.string.error)
                        }
                    )
                }
            _uiState.update { it.copy(isSubmitting = false) }
        }
    }

    companion object {
        fun factory(authRepository: AuthRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { RegisterViewModel(authRepository) }
        }
    }
}