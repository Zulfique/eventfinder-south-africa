package com.eventfinder.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.eventfinder.app.data.repository.AuthRepository
import com.eventfinder.app.data.store.UserPreferences
import com.eventfinder.app.di.AppContainer
import com.eventfinder.app.domain.model.SupportedLanguage
import kotlinx.coroutines.flow.SharingStarted
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
    private val authRepository: AuthRepository
) : ViewModel() {

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

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SettingsViewModel(container.preferences, container.authRepository)
            }
        }
    }
}