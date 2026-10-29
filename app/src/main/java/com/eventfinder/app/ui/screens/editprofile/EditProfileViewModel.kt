package com.eventfinder.app.ui.screens.editprofile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.eventfinder.app.R
import com.eventfinder.app.data.repository.AuthRepository
import com.eventfinder.app.di.AppContainer
import com.eventfinder.app.ui.components.UiMessage
import com.eventfinder.app.utils.EmailValidator
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** UI state for the edit-profile screen. */
data class EditProfileUiState(
    val fullName: String = "",
    val email: String = "",
    val avatarType: String = "initials",
    val avatarUrl: String? = null,
    val avatarImageUrl: String? = null,
    val avatarUploading: Boolean = false,
    val saving: Boolean = false,
    val nameError: Int? = null,
    val emailError: Int? = null,
    val verificationToken: String = "",
    val verifying: Boolean = false
)

/**
 * Edit profile: pre-fills the current user, validates the form and persists
 * via the auth repository.
 */
class EditProfileViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditProfileUiState())
    val uiState = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<UiMessage>()
    val messages = _messages.asSharedFlow()

    init {
        viewModelScope.launch {
            authRepository.currentUser.first()?.let { user ->
                _uiState.update {
                    it.copy(
                        fullName = user.fullName,
                        email = user.email,
                        avatarType = user.avatarType,
                        avatarUrl = user.avatarUrl,
                        avatarImageUrl = user.avatarImageUrl
                    )
                }
            }
        }
    }

    fun onAvatarTypeChange(v: String) =
        _uiState.update { state ->
            when (v) {
                "uploaded" -> state.copy(avatarType = v)
                else -> state.copy(avatarType = v, avatarUrl = null, avatarImageUrl = null)
            }
        }

    fun onAvatarImageChange(url: String?) {
        val normalized = url?.takeIf { it.isNotBlank() }
        _uiState.update {
            it.copy(
                avatarType = "uploaded",
                avatarUrl = normalized,
                avatarImageUrl = normalized
            )
        }
    }

    fun setAvatarUploading(value: Boolean) =
        _uiState.update { it.copy(avatarUploading = value) }    fun onNameChange(v: String) = _uiState.update { it.copy(fullName = v, nameError = null) }
    fun onEmailChange(v: String) = _uiState.update { it.copy(email = v, emailError = null) }
    fun onVerificationTokenChange(v: String) = _uiState.update { it.copy(verificationToken = v) }

    fun verifyEmail(onVerified: () -> Unit) {
        val token = _uiState.value.verificationToken.trim()
        if (token.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(verifying = true) }
            val result = authRepository.verifyEmailChange(token)
            _uiState.update { it.copy(verifying = false) }
            result.fold(
                onSuccess = {
                    _messages.emit(UiMessage.Resource(R.string.profile_updated))
                    // Refresh local profile from server.
                    onVerified()
                },
                onFailure = {
                    _messages.emit(UiMessage.Resource(R.string.save_failed))
                }
            )
        }
    }

    /** Returns true when validation passes; surfaces errors inline in the form. */
    fun validate(): Boolean {
        val state = _uiState.value
        val nameError = if (state.fullName.isBlank()) R.string.name_required else null
        // Guest sessions have no email by design (see AuthRepository.continueAsGuest),
        // so the email field is not enforced for them.
        val isGuest = state.email.isBlank()
        val emailError = if (!isGuest && !EmailValidator.isValid(state.email)) R.string.invalid_email else null
        _uiState.update { it.copy(nameError = nameError, emailError = emailError) }
        return nameError == null && emailError == null
    }

    fun save(onSaved: () -> Unit) {
        val state = _uiState.value
        if (state.saving || !validate()) return
        viewModelScope.launch {
            _uiState.update { it.copy(saving = true) }
            authRepository.updateAvatarType(state.avatarType, state.avatarImageUrl ?: state.avatarUrl)
            val result = authRepository.updateProfile(state.fullName.trim(), state.email.trim())
            _uiState.update { it.copy(saving = false) }
            result.fold(
                onSuccess = { _messages.emit(UiMessage.Resource(R.string.profile_updated)); onSaved() },
                onFailure = { throwable ->
                    if (throwable.message == "email_verification_sent") {
                        _messages.emit(UiMessage.Resource(R.string.profile_updated))
                        onSaved()
                    } else {
                        _messages.emit(
                            when (throwable.message) {
                                "email_in_use" -> UiMessage.Resource(R.string.email_in_use)
                                else -> UiMessage.Resource(R.string.save_failed)
                            }
                        )
                    }
                }
            )
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { EditProfileViewModel(container.authRepository) }
        }
    }
}