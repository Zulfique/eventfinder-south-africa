package com.eventfinder.app.data.store

import kotlinx.coroutines.flow.Flow

/**
 * Minimal interface for session management used by repositories.
 * Extracted so that tests can provide a simple fake without requiring
 * an Android DataStore context.
 */
interface SessionProvider {
    val sessionUserId: Flow<String?>
    suspend fun isLoggedIn(): Boolean
    suspend fun clearAll()
}
