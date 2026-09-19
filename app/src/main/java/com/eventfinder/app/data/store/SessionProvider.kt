package com.eventfinder.app.data.store

import kotlinx.coroutines.flow.Flow

/**
 * Minimal interface for session and preference management used by repositories.
 * Extracted so that tests can provide a simple fake without requiring
 * an Android DataStore context.
 *
 * Includes methods needed by [com.eventfinder.app.data.repository.AuthRepositoryImpl]
 * for session lifecycle (login/logout) and biometric enrollment state.
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
}
