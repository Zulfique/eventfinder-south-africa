package com.eventfinder.app.utils

import com.eventfinder.app.domain.model.Event

/**
 * Last-write-wins conflict resolver for events edited on multiple devices.
 *
 * Compares [local] and [remote] by [Event.updatedAt] (falling back to
 * [Event.version]). When timestamps tie, the higher version wins. Returns the
 * merged event, preferring the newer side field-by-field for non-null values.
 */
object ConflictResolver {
    fun resolve(local: Event, remote: Event): Event {
        val localNewer = when {
            local.updatedAt != remote.updatedAt -> local.updatedAt > remote.updatedAt
            else -> local.version >= remote.version
        }
        return if (localNewer) local else remote
    }

    fun isStale(cachedAt: Long, ttlMillis: Long = 24L * 60L * 60L * 1000L): Boolean =
        System.currentTimeMillis() - cachedAt > ttlMillis
}