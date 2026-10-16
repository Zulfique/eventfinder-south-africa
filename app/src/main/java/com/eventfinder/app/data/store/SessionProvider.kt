package com.eventfinder.app.data.store

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Minimal interface for session and preference management used by repositories.
 * Extracted so that tests can provide a simple fake without requiring
 * an Android DataStore context.
 *
 * Includes methods needed by [com.eventfinder.app.data.repository.AuthRepositoryImpl]
 * for session lifecycle (login/logout), biometric enrollment state, and the
 * JWT bearer token issued by the EventFinder REST API.
 */
interface SessionProvider {
    val sessionUserId: Flow<String?>
    val biometricUserId: Flow<String?>
    suspend fun isLoggedIn(): Boolean
    suspend fun setSessionUserId(userId: String?)
    suspend fun setLanguage(lang: String)
    suspend fun setBiometricUserId(userId: String?)
    suspend fun clearUserPreferences(userId: String)
    suspend fun clearAll()

    /** JWT bearer token for the REST API (null for local/guest accounts). */
    val authToken: Flow<String?> get() = flowOf(null)

    /** Persists or clears the REST API bearer token. */
    suspend fun setAuthToken(token: String?) = Unit
}
