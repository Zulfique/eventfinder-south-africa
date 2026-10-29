package com.eventfinder.app.ui.screens.editprofile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.eventfinder.app.R
import com.eventfinder.app.data.cloud.AzureImageUploader
import com.eventfinder.app.data.repository.AuthRepository
import com.eventfinder.app.data.store.UserPreferences
import com.eventfinder.app.di.AppContainer
import com.eventfinder.app.ui.components.UiMessage
import com.eventfinder.app.utils.EmailValidator
import com.eventfinder.app.utils.ImageStorage
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
    val saving: Boolean = false,
    val nameError: Int? = null,
    val emailError: Int? = null,
    val verificationToken: String = "",
    val verifying: Boolean = false,
    val avatarUrl: String? = null,
    val avatarStyle: Int = 0,
    val uploadingPhoto: Boolean = false
)

/**
 * Edit profile: pre-fills the current user, validates the form and persists
 * via the auth repository.
 */
class EditProfileViewModel(
    private val authRepository: AuthRepository,
    private val preferences: UserPreferences,
    private val imageUploader: AzureImageUploader
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditProfileUiState())
    val uiState = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<UiMessage>()
    val messages = _messages.asSharedFlow()

    init {
        viewModelScope.launch {
            authRepository.currentUser.first()?.let { user ->
                _uiState.update {
                    it.copy(fullName = user.fullName, email = user.email)
                }
            }
            val userId = preferences.sessionUserId.first()
            if (userId != null) {
                _uiState.update {
                    it.copy(
                        avatarUrl = preferences.avatarUrl(userId),
                        avatarStyle = preferences.avatarStyle(userId)
                    )
                }
            }
        }
    }

    /**
     * Imports the picked photo into internal storage for an instant preview,
     * then uploads it to Azure Blob Storage and swaps to the permanent URL.
     * Everything fails softly: a failed upload keeps the local file URI.
     */
    fun onPhotoPicked(context: android.content.Context, uri: android.net.Uri) {
        viewModelScope.launch {
            val local = ImageStorage.importImage(context, uri)
            if (local == null) {
                _messages.emit(UiMessage.Resource(R.string.photo_import_failed))
                return@launch
            }
            val userId = preferences.sessionUserId.first()
            if (userId != null) preferences.setAvatarUrl(userId, local)
            _uiState.update { it.copy(avatarUrl = local) }

            _uiState.update { it.copy(uploadingPhoto = true) }
            val remote = imageUploader.uploadProfilePhoto(local)
            _uiState.update { it.copy(uploadingPhoto = false) }
            if (remote != null && userId != null) {
                preferences.setAvatarUrl(userId, remote)
                _uiState.update { it.copy(avatarUrl = remote) }
            }
        }
    }

    /** 0 = initials, 1 = male silhouette, 2 = female silhouette. */
    fun onAvatarStyleChange(style: Int) {
        viewModelScope.launch {
            val userId = preferences.sessionUserId.first() ?: return@launch
            preferences.setAvatarStyle(userId, style)
            _uiState.update { it.copy(avatarStyle = style) }
        }
    }

    /** Drops the uploaded photo so the generated avatar is shown again. */
    fun onRemovePhoto() {
        viewModelScope.launch {
            val userId = preferences.sessionUserId.first() ?: return@launch
            preferences.setAvatarUrl(userId, null)
            _uiState.update { it.copy(avatarUrl = null) }
        }
    }

    fun onNameChange(v: String) = _uiState.update { it.copy(fullName = v, nameError = null) }
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
            initializer {
                EditProfileViewModel(
                    container.authRepository,
                    container.preferences,
                    container.imageUploader
                )
            }
        }
    }
}