package com.eventfinder.app.data.store

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.eventfinder.app.utils.AppLogger
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// Top-level delegate so a single DataStore instance backs all preferences.
private val Context.eventFinderDataStore by preferencesDataStore(name = "eventfinder_prefs")

/**
 * Timestamped recent-search record for correct ordering.
 */
private data class RecentSearch(
    val term: String,
    val timestamp: Long
)

/**
 * Typed access to the DataStore preferences file. Wraps every key so the app's
 * preference names are defined in exactly one place.
 *
 * References:
 *  - Android Developers, "DataStore":
 *    https://developer.android.com/topic/libraries/architecture/datastore
 */
class UserPreferences(private val context: Context) : SessionProvider {

    private val gson = Gson()

    private object Keys {
        val SESSION_USER_ID = stringPreferencesKey("session_user_id")
        val BIOMETRIC_USER_ID = stringPreferencesKey("biometric_user_id")
        val LANGUAGE = stringPreferencesKey("language")
        val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        val REMINDERS_ENABLED = booleanPreferencesKey("reminders_enabled")
        val NEW_EVENT_ALERTS_ENABLED = booleanPreferencesKey("new_event_alerts_enabled")
        val DEFAULT_RADIUS_KM = stringPreferencesKey("default_radius_km")
        val RECENT_SEARCHES_JSON = stringPreferencesKey("recent_searches_json")
    }

    suspend fun isLoggedIn(): Boolean =
        context.eventFinderDataStore.data.first()[Keys.SESSION_USER_ID] != null

    suspend fun setSessionUserId(userId: String?) {
        context.eventFinderDataStore.edit { prefs ->
            if (userId == null) prefs.remove(Keys.SESSION_USER_ID)
            else prefs[Keys.SESSION_USER_ID] = userId
        }
        AppLogger.i("UserPreferences", "Session ${if (userId == null) "cleared" else "set to $userId"}")
    }

    /** Emits the id of the signed-in user (null when logged out). */
    val sessionUserId: Flow<String?> = context.eventFinderDataStore.data
        .map { it[Keys.SESSION_USER_ID] }

    // ---- Language (English / Afrikaans, FR-08) ----
    val language: Flow<String> = context.eventFinderDataStore.data
        .map { it[Keys.LANGUAGE] ?: "en" }

    suspend fun setLanguage(lang: String) {
        context.eventFinderDataStore.edit { it[Keys.LANGUAGE] = lang }
        AppLogger.i("UserPreferences", "Language preference saved: $lang")
    }

    // ---- Biometric auth (FR-01) ----
    val biometricEnabled: Flow<Boolean> = context.eventFinderDataStore.data
        .map { it[Keys.BIOMETRIC_ENABLED] ?: false }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        context.eventFinderDataStore.edit { it[Keys.BIOMETRIC_ENABLED] = enabled }
        AppLogger.i("UserPreferences", "Biometric enabled=$enabled")
    }

    /** Tied to the last user who enabled biometrics so the prompt opens the correct account. */
    val biometricUserId: Flow<String?> = context.eventFinderDataStore.data
        .map { it[Keys.BIOMETRIC_USER_ID] }

    suspend fun setBiometricUserId(userId: String?) {
        context.eventFinderDataStore.edit {
            if (userId == null) it.remove(Keys.BIOMETRIC_USER_ID)
            else it[Keys.BIOMETRIC_USER_ID] = userId
        }
    }

    // ---- Notification preferences (FR-04) ----
    val remindersEnabled: Flow<Boolean> = context.eventFinderDataStore.data
        .map { it[Keys.REMINDERS_ENABLED] ?: true }

    suspend fun setRemindersEnabled(enabled: Boolean) {
        context.eventFinderDataStore.edit { it[Keys.REMINDERS_ENABLED] = enabled }
    }

    val newEventAlertsEnabled: Flow<Boolean> = context.eventFinderDataStore.data
        .map { it[Keys.NEW_EVENT_ALERTS_ENABLED] ?: true }

    suspend fun setNewEventAlertsEnabled(enabled: Boolean) {
        context.eventFinderDataStore.edit { it[Keys.NEW_EVENT_ALERTS_ENABLED] = enabled }
    }

    // ---- Location radius default ----
    val defaultRadiusKm: Flow<Int> = context.eventFinderDataStore.data
        .map { it[Keys.DEFAULT_RADIUS_KM]?.toIntOrNull() ?: 50 }

    suspend fun setDefaultRadiusKm(km: Int) {
        context.eventFinderDataStore.edit { it[Keys.DEFAULT_RADIUS_KM] = km.toString() }
    }

    // ---- Recent searches (Screen 7) ----
    val recentSearches: Flow<List<String>> = context.eventFinderDataStore.data
        .map { prefs ->
            prefs[Keys.RECENT_SEARCHES_JSON]
                ?.let { json ->
                    runCatching {
                        gson.fromJson(json, Array<RecentSearch>::class.java)
                            .sortedByDescending { it.timestamp }
                            .take(8)
                            .map { it.term }
                    }.getOrNull()
                }
                .orEmpty()
        }

    suspend fun addRecentSearch(term: String) {
        val trimmed = term.trim()
        if (trimmed.isEmpty()) return
        context.eventFinderDataStore.edit { prefs ->
            val current = prefs[Keys.RECENT_SEARCHES_JSON]
                ?.let { json ->
                    runCatching {
                        gson.fromJson(json, Array<RecentSearch>::class.java).toMutableList()
                    }.getOrNull()
                }
                ?: mutableListOf()

            current.removeAll { it.term.equals(trimmed, ignoreCase = true) }
            current.add(RecentSearch(term = trimmed, timestamp = System.currentTimeMillis()))

            val latest = current.sortedByDescending { it.timestamp }.take(8)
            prefs[Keys.RECENT_SEARCHES_JSON] = gson.toJson(latest)
        }
        AppLogger.i("UserPreferences", "Recent search recorded: $trimmed")
    }

    suspend fun clearRecentSearches() {
        context.eventFinderDataStore.edit { it.remove(Keys.RECENT_SEARCHES_JSON) }
    }

    /** Synchronous language read used when the Activity recreates for a locale change. */
    fun currentLanguageBlocking(): String =
        runCatching {
            kotlinx.coroutines.runBlocking {
                context.eventFinderDataStore.data.first()[Keys.LANGUAGE] ?: "en"
            }
        }.getOrDefault("en")

    /** Suspending language read for use inside coroutines. */
    suspend fun currentLanguage(): String =
        context.eventFinderDataStore.data.first()[Keys.LANGUAGE] ?: "en"

    /** Wipes every stored preference, including the session (account deletion). */
    suspend fun clearAll() {
        context.eventFinderDataStore.edit { it.clear() }
        AppLogger.i("UserPreferences", "All preferences cleared")
    }
}
