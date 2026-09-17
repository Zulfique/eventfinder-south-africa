package com.eventfinder.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.eventfinder.app.data.repository.AuthRepository
import com.eventfinder.app.data.repository.EventRepository
import com.eventfinder.app.data.store.UserPreferences
import com.eventfinder.app.di.AppContainer
import com.eventfinder.app.R
import com.eventfinder.app.domain.model.SupportedLanguage
import com.eventfinder.app.ui.components.UiMessage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** UI state for the settings screen. */
data class SettingsUiState(
    val language: SupportedLanguage = SupportedLanguage.ENGLISH,
    val biometricEnabled: Boolean = false,
    val remindersEnabled: Boolean = true,
    val newEventsAlerts: Boolean = true
)

/**
 * Settings (Screen 9): preference switches persisted to DataStore — language,
 * biometric login, and local notification toggles.
 */
class SettingsViewModel(
    private val preferences: UserPreferences,
    private val authRepository: AuthRepository,
    private val eventRepository: EventRepository
) : ViewModel() {

    private val _messages = MutableSharedFlow<UiMessage>()
    val messages = _messages.asSharedFlow()

    val uiState = kotlinx.coroutines.flow.combine(
        preferences.language.map { SupportedLanguage.fromCode(it) },
        preferences.biometricEnabled,
        preferences.remindersEnabled,
        preferences.newEventAlertsEnabled
    ) { lang, bio, rem, alerts ->
        SettingsUiState(lang, bio, rem, alerts)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState()
    )

    fun setLanguage(language: SupportedLanguage) {
        viewModelScope.launch {
            preferences.setLanguage(language.code)
        }
    }

    fun setBiometric(enabled: Boolean) {
        viewModelScope.launch {
            authRepository.setBiometricEnabled(enabled)
        }
    }

    fun setReminders(enabled: Boolean) {
        viewModelScope.launch { preferences.setRemindersEnabled(enabled) }
    }

    fun setNewEventsAlerts(enabled: Boolean) {
        viewModelScope.launch { preferences.setNewEventAlertsEnabled(enabled) }
    }

    /** Verifies the current password, then stores the new PBKDF2 hash (FR-01). */
    fun changePassword(current: String, newPassword: String, confirm: String) {
        viewModelScope.launch {
            val error = when {
                current.isBlank() -> R.string.invalid_password
                newPassword != confirm -> R.string.passwords_mismatch
                else -> null
            }
            if (error != null) {
                _messages.emit(UiMessage.Resource(error))
                return@launch
            }
            val result = authRepository.changePassword(current, newPassword)
            _messages.emit(
                when (result.exceptionOrNull()?.message) {
                    null -> UiMessage.Resource(R.string.password_changed)
                    "wrong_password" -> UiMessage.Resource(R.string.current_password_wrong)
                    "same_password" -> UiMessage.Resource(R.string.password_unchanged)
                    "weak_password", "no_session", "user_missing" ->
                        UiMessage.Resource(R.string.weak_password)
                    else -> UiMessage.Resource(R.string.password_change_failed)
                }
            )
        }
    }

    /** Wipes the local event cache and reseeds the curated sample set. */
    fun clearCache() {
        viewModelScope.launch {
            eventRepository.clearLocalCache()
            _messages.emit(UiMessage.Resource(R.string.cache_cleared))
        }
    }

    /** Deletes the account, clears cached data and signs out (FR-01). */
    fun deleteAccount(onDeleted: () -> Unit) {
        viewModelScope.launch {
            val result = authRepository.deleteAccount()
            if (result.isSuccess) {
                eventRepository.clearLocalCache()
                preferences.clearAll()
                _messages.emit(UiMessage.Resource(R.string.account_deleted))
                onDeleted()
            } else {
                _messages.emit(UiMessage.Resource(R.string.account_delete_failed))
            }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SettingsViewModel(
                    container.preferences,
                    container.authRepository,
                    container.eventRepository
                )
            }
        }
    }
}