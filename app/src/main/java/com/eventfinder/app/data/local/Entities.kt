package com.eventfinder.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for a registered user.
 * The password is stored as a salted PBKDF2 hash.
 */
@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val fullName: String,
    val email: String,
    val passwordHash: String,
    val preferredLanguage: String,
    val defaultCity: String,
    val defaultRadiusKm: Int,
    val biometricEnabled: Boolean,
    val createdAt: Long
)

/**
 * Room entity for an event. All events live in this local catalogue —
 * seed/demo events, user-created events, and externally-discovered events.
 */
@Entity(
    tableName = "events",
    indices = [Index(value = ["category"]), Index(value = ["startDate"])]
)
data class EventEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val category: String,
    val startDate: Long,
    val endDate: Long,
    val venueName: String,
    val address: String,
    val latitude: Double?,
    val longitude: Double?,
    val imageUrl: String?,
    /** True = visible in this device's local catalogue. Has no cross-device semantics. */
    val isPublic: Boolean,
    val organizerId: String,
    val organizerName: String,
    val attendeeCount: Int,
    val isCreatedByUser: Boolean
)

/** Room entity for a favourite link. */
@Entity(
    tableName = "favorites",
    primaryKeys = ["userId", "eventId"]
)
data class FavoriteEntity(
    val userId: String,
    val eventId: String,
    val createdAt: Long
)

/** Room entity for an RSVP link. */
@Entity(
    tableName = "rsvps",
    primaryKeys = ["userId", "eventId"]
)
data class RsvpEntity(
    val userId: String,
    val eventId: String,
    val status: String,
    val createdAt: Long
)

/**
 * Per-source sync bookkeeping for the external event discovery pipeline.
 * Allows the cache to tell "feed temporarily under-reporting" apart from
 * "feed legitimately reduced its catalogue": a source whose events have not
 * refreshed successfully within the staleness window gets replaced (or its
 * stale events removed) on the next sync instead of being kept forever.
 */
@Entity(tableName = "source_sync")
data class SourceSyncEntity(
    @PrimaryKey val sourceId: String,
    val lastSuccessAt: Long,
    val lastEmptyAt: Long,
    val lastFailedAt: Long
)

/**
 * Persisted geocoding lookup cache, keyed by a normalized location string.
 * Survives process restarts so a multi-event feed restarting the app does not
 * re-issue Open-Meteo requests for locations it resolved before.
 */
@Entity(tableName = "geocode_cache")
data class GeocodeCacheEntity(
    @PrimaryKey val locationKey: String,
    val latitude: Double,
    val longitude: Double,
    val createdAt: Long
)