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
    val saving: Boolean = false,
    val nameError: Int? = null,
    val emailError: Int? = null
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
                    it.copy(fullName = user.fullName, email = user.email)
                }
            }
        }
    }

    fun onNameChange(v: String) = _uiState.update { it.copy(fullName = v, nameError = null) }
    fun onEmailChange(v: String) = _uiState.update { it.copy(email = v, emailError = null) }

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
                    _messages.emit(
                        when (throwable.message) {
                            "email_in_use" -> UiMessage.Resource(R.string.email_in_use)
                            else -> UiMessage.Resource(R.string.save_failed)
                        }
                    )
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