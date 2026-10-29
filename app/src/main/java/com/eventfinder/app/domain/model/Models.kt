package com.eventfinder.app.domain.model

/** RSVP status values. */
enum class RsvpStatus(val storage: String) {
    ATTENDING("attending"),
    MAYBE("maybe"),
    DECLINED("declined");

    companion object {
        fun fromStorage(value: String?): RsvpStatus? =
            entries.firstOrNull { it.storage == value }
    }
}

/** Sorting options exposed on the Home filter sheet. */
enum class EventSort { DATE, DISTANCE, NAME }

/**
 * Domain model for an event. All events in the local Room catalogue share
 * this model — seed/demo events, user-created events, and externally-
 * discovered events.
 */
data class Event(
    val id: String,
    val title: String,
    val description: String,
    val category: EventCategory,
    val startDate: Long,
    val endDate: Long,
    val venueName: String,
    val address: String,
    val latitude: Double?,
    val longitude: Double?,
    val imageUrl: String?,
    /** Optional source event or ticketing page. */
    val sourceUrl: String? = null,
    /** Optional attendance capacity from the source feed. */
    val maxAttendees: Int? = null,
    /** True = visible in this device's local catalogue only. No cross-device semantics. */
    val isPublic: Boolean,
    val organizerId: String,
    val organizerName: String,
    val organizerIsVerified: Boolean = false,
    val attendeeCount: Int,
    val isFavorite: Boolean,
    val isCreatedByUser: Boolean
)

/** Event combined with the viewing user's context (distance + RSVP state). */
data class EventView(
    val event: Event,
    val distanceKm: Double? = null,
    val rsvpStatus: RsvpStatus? = null,
    /** Filled in by the presentation layer from the per-user favourites table. */
    val isFavorite: Boolean = false,
    /** Optional event rating already rendered in Card views. */
    val averageRating: Double? = null,
    val reviewCount: Int = 0
)

/** A verified attendee review / star rating attached to an event. */
data class EventReview(
    val eventId: String,
    val userId: String,
    val authorName: String,
    val rating: Int,
    val comment: String,
    val createdAt: Long
)

/** A coordinated group plan for attending a specific event. */
data class GroupPlan(
    val id: String,
    val eventId: String,
    val creatorId: String,
    val members: Set<String>,
    val meetingPointLabel: String? = null,
    val meetingLat: Double? = null,
    val meetingLng: Double? = null,
    val proposedTime: Long? = null,
    val createdAt: Long
)

/** A digital ticket for an event attendee. */
data class EventTicket(
    val id: String,
    val eventId: String,
    val userId: String,
    val ticketCode: String,
    val issuedAt: Long,
    val checkedInAt: Long? = null
)

/** Registered EventFinder user. */
data class User(
    val id: String,
    val fullName: String,
    val email: String,
    val preferredLanguage: String,
    val defaultCity: String,
    val defaultRadiusKm: Int,
    val biometricEnabled: Boolean,
    val createdAt: Long,
    val avatarType: String = "initials",
    val avatarUrl: String? = null,
    val avatarImageUrl: String? = null,
    val isVerified: Boolean = false,
    val trustScore: Double = 0.0
)