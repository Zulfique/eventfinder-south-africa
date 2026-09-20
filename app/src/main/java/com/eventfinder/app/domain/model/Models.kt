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
    val latitude: Double,
    val longitude: Double,
    val imageUrl: String?,
    /** True = visible in this device's local catalogue only. No cross-device semantics. */
    val isPublic: Boolean,
    val organizerId: String,
    val organizerName: String,
    val attendeeCount: Int,
    val isFavorite: Boolean,
    val isCreatedByUser: Boolean
)

/** Event combined with the viewing user's context (distance + RSVP state). */
data class EventView(
    val event: Event,
    val distanceKm: Double? = null,
    val rsvpStatus: RsvpStatus? = null
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
    val createdAt: Long
)