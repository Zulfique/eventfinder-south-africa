package com.eventfinder.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "group_plans")
data class GroupPlanEntity(
    @PrimaryKey val id: String,
    val eventId: String,
    val creatorId: String,
    val members: String,
    val meetingPointLabel: String?,
    val meetingLat: Double?,
    val meetingLng: Double?,
    val proposedTime: Long?,
    val createdAt: Long
)

/**
 * Room entity for a registered user.

 * The password is stored as a salted PBKDF2 hash.
 */
/** Cached server review for an event (one row per reviewer). */
@Entity(
    tableName = "event_reviews",
    indices = [Index("eventId")]
)
data class CachedReviewEntity(
    @PrimaryKey val reviewId: String,
    val eventId: String,
    val userId: String,
    val userName: String,
    val rating: Int,
    val comment: String?,
    val isVerifiedAttendee: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)

/** Cached aggregate rating for an event (shown on cards and detail header). */
@Entity(tableName = "event_review_summaries")
data class CachedReviewSummaryEntity(
    @PrimaryKey val eventId: String,
    val average: Double,
    val count: Int
)

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
    val createdAt: Long,
    val serverBacked: Boolean = false,
    val avatarType: String = "initials",
    val avatarUrl: String? = null,
    val avatarImageUrl: String? = null,
    val isVerified: Boolean = false,
    val trustScore: Double = 0.0,
    val commuteTimeMinutes: Int = 30,
    val preferredCategories: String = "",
    val skipReminders: Boolean = false,
    val accessibilityNeeds: String = ""
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
    /** Externally discovered URL for the event or ticketing page, when available. */
    val sourceUrl: String? = null,
    /** Optional RSVP / venue capacity, only when reported by the source. */
    val maxAttendees: Int? = null,
    /** True = visible in this device's local catalogue. Has no cross-device semantics. */
    val isPublic: Boolean,
    val organizerId: String,
    val organizerName: String,
    val attendeeCount: Int,
    val isCreatedByUser: Boolean,
    val wheelchairAccessible: Boolean = false,
    val loudMusic: Boolean = false,
    val sensoryFriendly: Boolean = false,
    val strobeLights: Boolean = false,
    val provenance: String = "live",
    val ageRestricted: Boolean = false,
    val isRecurring: Boolean = false,
    val recurrenceRule: String? = null,
    val parentEventId: String? = null,
    val collectionId: String? = null,
    val version: Int = 1,
    val updatedAt: Long = 0L
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

@Entity(tableName = "tickets")
data class TicketEntity(
    @PrimaryKey val id: String,
    val eventId: String,
    val userId: String,
    val ticketCode: String,
    val issuedAt: Long,
    val checkedInAt: Long?
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