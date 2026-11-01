package com.eventfinder.app.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.eventfinder.app.data.local.EventDao
import com.eventfinder.app.data.repository.AuthRepository
import com.eventfinder.app.data.repository.EventRepository
import com.eventfinder.app.data.store.UserPreferences
import com.eventfinder.app.di.AppContainer
import com.eventfinder.app.R
import com.eventfinder.app.domain.model.SupportedLanguage
import com.eventfinder.app.notifications.ReminderHelper
import com.eventfinder.app.ui.components.UiMessage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** UI state for the settings screen. */
data class SettingsUiState(
    val language: SupportedLanguage = SupportedLanguage.ENGLISH,
    val biometricEnabled: Boolean = false,
    val remindersEnabled: Boolean = true,
    val newEventsAlerts: Boolean = true,
    val defaultCity: String = "",
    val defaultRadiusKm: Int = 50,
    val analyticsConsent: Boolean = false,
    val locationConsent: Boolean = false,
    val wheelchairNeeded: Boolean = false,
    val lowSensoryNeeded: Boolean = false,
    val reducedMotion: Boolean = false,
    val highContrast: Boolean = false,
    val loudNoiseNeeded: Boolean = false,
    val noMusicNeeded: Boolean = false,
    val notificationConsent: Boolean = true,
    val dynamicColor: Boolean = false
)

/**
 * Settings (Screen 9): preference switches persisted to DataStore — language,
 * biometric login, and local notification toggles.
 */
class SettingsViewModel(
    private val preferences: UserPreferences,
    private val authRepository: AuthRepository,
    private val eventRepository: EventRepository,
    private val context: Context,
    private val eventDao: EventDao
) : ViewModel() {

    private val _messages = MutableSharedFlow<UiMessage>()
    val messages = _messages.asSharedFlow()

    val consentFlow = kotlinx.coroutines.flow.combine(
        preferences.analyticsConsent,
        preferences.locationConsent,
        preferences.notificationConsent
    ) { analytics, location, notifications -> listOf(analytics, location, notifications) }

    val a11yFlow = kotlinx.coroutines.flow.combine(
        preferences.wheelchairNeeded,
        preferences.lowSensoryNeeded,
        preferences.reducedMotion,
        preferences.highContrast,
        preferences.loudNoiseNeeded
    ) { w, l, r, h, loud -> listOf(w, l, r, h, loud) }

    val uiState = kotlinx.coroutines.flow.combine(
        kotlinx.coroutines.flow.combine(
            preferences.language.map { SupportedLanguage.fromCode(it) },
            authRepository.currentUser.map { it?.biometricEnabled ?: false },
            preferences.remindersEnabled,
            preferences.newEventAlertsEnabled
        ) { lang, bio, rem, alerts ->
            listOf(lang, bio, rem, alerts)
        },
        preferences.defaultCity,
        preferences.defaultRadiusKm,
        consentFlow,
        kotlinx.coroutines.flow.combine(
            a11yFlow,
            preferences.noMusicNeeded,
            preferences.dynamicColorEnabled
        ) { a11y, noMusic, dynamic -> listOf(a11y, noMusic, dynamic) }
    ) { base, city, radius, consent, extra ->
        @Suppress("UNCHECKED_CAST")
        SettingsUiState(
            language = base[0] as SupportedLanguage,
            biometricEnabled = base[1] as Boolean,
            remindersEnabled = base[2] as Boolean,
            newEventsAlerts = base[3] as Boolean,
            defaultCity = city,
            defaultRadiusKm = radius,
            analyticsConsent = consent[0] as Boolean,
            locationConsent = consent[1] as Boolean,
            notificationConsent = consent[2] as Boolean,
            wheelchairNeeded = (extra[0] as List<Boolean>)[0],
            lowSensoryNeeded = (extra[0] as List<Boolean>)[1],
            reducedMotion = (extra[0] as List<Boolean>)[2],
            highContrast = (extra[0] as List<Boolean>)[3],
            loudNoiseNeeded = (extra[0] as List<Boolean>)[4],
            noMusicNeeded = extra[1] as Boolean,
            dynamicColor = extra[2] as Boolean
        )
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
        viewModelScope.launch {
            preferences.setRemindersEnabled(enabled)
            val userId = preferences.sessionUserId.first()
            if (userId != null) {
                if (enabled) {
                    ReminderHelper.restoreReminders(context, eventDao, userId)
                } else {
                    ReminderHelper.cancelReminders(context, eventDao, userId)
                }
            }
        }
    }

    fun setNewEventsAlerts(enabled: Boolean) {
        viewModelScope.launch { preferences.setNewEventAlertsEnabled(enabled) }
    }

    fun setAnalyticsConsent(enabled: Boolean) {
        viewModelScope.launch { preferences.setAnalyticsConsent(enabled) }
    }

    fun setLocationConsent(enabled: Boolean) {
        viewModelScope.launch { preferences.setLocationConsent(enabled) }
    }

    fun setWheelchairNeeded(enabled: Boolean) {
        viewModelScope.launch { preferences.setWheelchairNeeded(enabled) }
    }

    fun setLowSensoryNeeded(enabled: Boolean) {
        viewModelScope.launch { preferences.setLowSensoryNeeded(enabled) }
    }

    fun setReducedMotion(enabled: Boolean) {
        viewModelScope.launch { preferences.setReducedMotion(enabled) }
    }

    fun setHighContrast(enabled: Boolean) {
        viewModelScope.launch { preferences.setHighContrast(enabled) }
    }

    fun setLoudNoiseNeeded(enabled: Boolean) {
        viewModelScope.launch { preferences.setLoudNoiseNeeded(enabled) }
    }

    fun setNoMusicNeeded(enabled: Boolean) {
        viewModelScope.launch { preferences.setNoMusicNeeded(enabled) }
    }

    fun setNotificationConsent(enabled: Boolean) {
        viewModelScope.launch { preferences.setNotificationConsent(enabled) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { preferences.setDynamicColorEnabled(enabled) }
    }

    private val _exportPath = kotlinx.coroutines.flow.MutableSharedFlow<String>()
    val exportPath = _exportPath.asSharedFlow()

    fun exportData() {
        viewModelScope.launch {
            try {
                val userId = preferences.sessionUserId.first()
                val events = eventRepository.observeAllEvents().first()
                val json = buildString {
                    append("{")
                    append("\"userId\":\"").append(userId ?: "").append("\",")
                    append("\"exportedAt\":").append(System.currentTimeMillis()).append(",")
                    append("\"events\":[")
                    events.forEachIndexed { i, e ->
                        if (i > 0) append(",")
                        append("{\"id\":\"").append(e.id).append("\",\"title\":\"").append(e.title).append("\"}")
                    }
                    append("]}")
                }
                val file = java.io.File(context.cacheDir, "eventfinder_export.json")
                file.writeText(json)
                _exportPath.emit(file.absolutePath)
                _messages.emit(com.eventfinder.app.ui.components.UiMessage.Literal("Data exported"))
            } catch (e: Exception) {
                _messages.emit(com.eventfinder.app.ui.components.UiMessage.Literal("Export failed"))
            }
        }
    }

    fun setDefaultCity(city: String) {
        viewModelScope.launch { preferences.setDefaultCity(city) }
    }

    fun setDefaultRadiusKm(km: Int) {
        viewModelScope.launch { preferences.setDefaultRadiusKm(km.coerceIn(5, 200)) }
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
            val blockedBy = eventRepository.clearLocalCache()
            _messages.emit(
                if (blockedBy > 0) {
                    UiMessage.Resource(R.string.cache_clear_blocked, listOf(blockedBy))
                } else {
                    UiMessage.Resource(R.string.cache_cleared)
                }
            )
        }
    }

    /**
     * Admin "Repair local data" action: rebuilds the local event cache by
     * reseeding the curated demo catalogue (idempotent — only missing rows are
     * restored) so a corrupted or partial Room cache recovers without a wipe.
     */
    fun repairLocalData() {
        viewModelScope.launch {
            runCatching { eventRepository.ensureSeeded() }
                .onSuccess {
                    _messages.emit(UiMessage.Resource(R.string.local_data_repaired))
                }
                .onFailure {
                    _messages.emit(UiMessage.Resource(R.string.local_data_repair_failed))
                }
        }
    }

    /** Deletes the account and signs out (FR-01). Only removes the deleted user's preferences. */
    fun deleteAccount(onDeleted: () -> Unit) {
        viewModelScope.launch {
            val result = authRepository.deleteAccount()
            if (result.isSuccess) {
                _messages.emit(UiMessage.Resource(R.string.account_deleted))
                onDeleted()
            } else {
                _messages.emit(UiMessage.Resource(R.string.account_delete_failed))
            }
        }
    }

    fun savePendingRoute(route: String) {
        viewModelScope.launch { preferences.setPendingNavigationRoute(route) }
    }

    companion object {
        fun factory(container: AppContainer, appContext: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SettingsViewModel(
                    container.preferences,
                    container.authRepository,
                    container.eventRepository,
                    appContext,
                    container.database.eventDao()
                )
            }
        }
    }
}