package com.eventfinder.app.data.store

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.eventfinder.app.utils.AppLogger
import com.google.gson.Gson
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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
 * Account-specific settings (reminders, alerts, radius, recent searches) are
 * stored with user-scoped keys to prevent leakage between accounts on the
 * same device. Global keys are used for session, language, and biometric
 * state which must be accessible before login.
 *
 * References:
 *  - Android Developers, "DataStore":
 *    https://developer.android.com/topic/libraries/architecture/datastore
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UserPreferences(private val context: Context) : SessionProvider {

    private val gson = Gson()

    private object Keys {
        // Global keys
        val SESSION_USER_ID = stringPreferencesKey("session_user_id")
        val BIOMETRIC_USER_ID = stringPreferencesKey("biometric_user_id")
        val LANGUAGE = stringPreferencesKey("language")
        val PENDING_NAVIGATION_ROUTE = stringPreferencesKey("pending_navigation_route")
    }

    // ---- Helpers for user-scoped keys ----

    /** Builds a string preference key scoped to a specific user. */
    private fun userStringKey(name: String, userId: String) =
        stringPreferencesKey("user_${userId}_$name")

    /** Builds a boolean preference key scoped to a specific user. */
    private fun userBoolKey(name: String, userId: String) =
        booleanPreferencesKey("user_${userId}_$name")

    // ---- Session (global) ----

    override suspend fun isLoggedIn(): Boolean =
        context.eventFinderDataStore.data.first()[Keys.SESSION_USER_ID] != null

    override suspend fun setSessionUserId(userId: String?) {
        context.eventFinderDataStore.edit { prefs ->
            if (userId == null) prefs.remove(Keys.SESSION_USER_ID)
            else prefs[Keys.SESSION_USER_ID] = userId
        }
        AppLogger.i("UserPreferences", "Session ${if (userId == null) "cleared" else "set to $userId"}")
    }

    /** Emits the id of the signed-in user (null when logged out). */
    override val sessionUserId: Flow<String?> = context.eventFinderDataStore.data
        .map { it[Keys.SESSION_USER_ID] }

    // ---- Language (global — needed before login for the login screen) ----

    val language: Flow<String> = context.eventFinderDataStore.data
        .map { it[Keys.LANGUAGE] ?: "en" }

    override suspend fun setLanguage(lang: String) {
        context.eventFinderDataStore.edit { it[Keys.LANGUAGE] = lang }
        AppLogger.i("UserPreferences", "Language preference saved: $lang")
    }

    // ---- Biometric auth (global device-level, needed before login for biometric prompt) ----

    override val biometricUserId: Flow<String?> = context.eventFinderDataStore.data
        .map { it[Keys.BIOMETRIC_USER_ID] }

    override suspend fun setBiometricUserId(userId: String?) {
        context.eventFinderDataStore.edit {
            if (userId == null) it.remove(Keys.BIOMETRIC_USER_ID)
            else it[Keys.BIOMETRIC_USER_ID] = userId
        }
    }

    // ---- User-scoped notification preferences ----

    val remindersEnabled: Flow<Boolean> = sessionUserId.flatMapLatest { userId ->
        if (userId.isNullOrBlank()) {
            flowOf(true)
        } else {
            context.eventFinderDataStore.data
                .map { prefs -> prefs[userBoolKey("reminders_enabled", userId)] ?: true }
        }
    }

    suspend fun setRemindersEnabled(enabled: Boolean) {
        val userId = sessionUserId.first() ?: return
        context.eventFinderDataStore.edit {
            it[userBoolKey("reminders_enabled", userId)] = enabled
        }
    }

    val newEventAlertsEnabled: Flow<Boolean> = sessionUserId.flatMapLatest { userId ->
        if (userId.isNullOrBlank()) {
            flowOf(true)
        } else {
            context.eventFinderDataStore.data
                .map { prefs -> prefs[userBoolKey("new_event_alerts_enabled", userId)] ?: true }
        }
    }

    suspend fun setNewEventAlertsEnabled(enabled: Boolean) {
        val userId = sessionUserId.first() ?: return
        context.eventFinderDataStore.edit {
            it[userBoolKey("new_event_alerts_enabled", userId)] = enabled
        }
    }

    // ---- User-scoped location radius default ----

    val defaultRadiusKm: Flow<Int> = sessionUserId.flatMapLatest { userId ->
        if (userId.isNullOrBlank()) {
            flowOf(50)
        } else {
            context.eventFinderDataStore.data
                .map { prefs -> prefs[userStringKey("default_radius_km", userId)]?.toIntOrNull() ?: 50 }
        }
    }

    suspend fun setDefaultRadiusKm(km: Int) {
        val userId = sessionUserId.first() ?: return
        context.eventFinderDataStore.edit {
            it[userStringKey("default_radius_km", userId)] = km.toString()
        }
    }

    // ---- User-scoped recent searches (Screen 7) ----

    val recentSearches: Flow<List<String>> = sessionUserId.flatMapLatest { userId ->
        if (userId.isNullOrBlank()) {
            flowOf(emptyList())
        } else {
            context.eventFinderDataStore.data.map { prefs ->
                prefs[userStringKey("recent_searches_json", userId)]
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
        }
    }

    suspend fun addRecentSearch(term: String) {
        val trimmed = term.trim()
        if (trimmed.isEmpty()) return
        val userId = sessionUserId.first() ?: return
        context.eventFinderDataStore.edit { prefs ->
            val key = userStringKey("recent_searches_json", userId)
            val current = prefs[key]
                ?.let { json ->
                    runCatching {
                        gson.fromJson(json, Array<RecentSearch>::class.java).toMutableList()
                    }.getOrNull()
                }
                ?: mutableListOf()

            current.removeAll { it.term.equals(trimmed, ignoreCase = true) }
            current.add(RecentSearch(term = trimmed, timestamp = System.currentTimeMillis()))

            val latest = current.sortedByDescending { it.timestamp }.take(8)
            prefs[key] = gson.toJson(latest)
        }
        AppLogger.i("UserPreferences", "Recent search recorded: $trimmed")
    }

    suspend fun clearRecentSearches() {
        val userId = sessionUserId.first() ?: return
        context.eventFinderDataStore.edit {
            it.remove(userStringKey("recent_searches_json", userId))
        }
    }

    /**
     * Synchronous language read used when the Activity recreates for a locale change.
     *
     * Uses runBlocking intentionally: attachBaseContext() requires a synchronous
     * value and runs before any coroutine scope is available. This is the only
     * call-site where the blocking version is used; all other code should prefer
     * the suspend [currentLanguage] function.
     */
    fun currentLanguageBlocking(): String =
        runCatching {
            kotlinx.coroutines.runBlocking {
                context.eventFinderDataStore.data.first()[Keys.LANGUAGE] ?: "en"
            }
        }.getOrDefault("en")

    /** Suspending language read for use inside coroutines. */
    suspend fun currentLanguage(): String =
        context.eventFinderDataStore.data.first()[Keys.LANGUAGE] ?: "en"

    /** Saves a route to navigate to after Activity recreation (e.g. language change). */
    suspend fun setPendingNavigationRoute(route: String?) {
        context.eventFinderDataStore.edit { prefs ->
            if (route == null) prefs.remove(Keys.PENDING_NAVIGATION_ROUTE)
            else prefs[Keys.PENDING_NAVIGATION_ROUTE] = route
        }
    }

    /** Returns and clears the pending navigation route (one-shot). */
    suspend fun consumePendingNavigationRoute(): String? {
        val route = context.eventFinderDataStore.data.first()[Keys.PENDING_NAVIGATION_ROUTE]
        if (route != null) {
            context.eventFinderDataStore.edit { it.remove(Keys.PENDING_NAVIGATION_ROUTE) }
        }
        return route
    }

    /**
     * Removes all DataStore preferences scoped to [userId] without affecting
     * other accounts' settings. Also clears the session and biometric
     * association if they belong to this user — all in a single atomic
     * DataStore edit to prevent partial state.
     */
    override suspend fun clearUserPreferences(userId: String) {
        val currentSession = sessionUserId.first()
        val biometricUser = biometricUserId.first()
        context.eventFinderDataStore.edit { prefs ->
            prefs.asMap().keys
                .filter { it.name.startsWith("user_${userId}_") }
                .forEach { prefs.remove(it) }
            if (currentSession == userId) {
                prefs.remove(Keys.SESSION_USER_ID)
            }
            if (biometricUser == userId) {
                prefs.remove(Keys.BIOMETRIC_USER_ID)
            }
        }
        AppLogger.i("UserPreferences", "Preferences cleared for user $userId")
    }

    /**
     * Wipes every stored preference, including the session, biometric state,
     * and all user-scoped settings. Used only for full app reset.
     */
    override suspend fun clearAll() {
        context.eventFinderDataStore.edit { it.clear() }
        AppLogger.i("UserPreferences", "All preferences cleared")
    }
}
