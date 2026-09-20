package com.eventfinder.app.domain.model

/** RSVP status values defined in the Part 1 design document (§5.3). */
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
 * Domain model for an event. Mirrors the `EventResponse` DTO from the Part 1
 * API specification (§5.3) and the room `EventEntity` defined in §7.1.
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
    val isPublic: Boolean,
    val organizerId: String,
    val organizerName: String,
    val attendeeCount: Int,
    val isFavorite: Boolean,
    val isCreatedByUser: Boolean,
    val isExternal: Boolean
)

/** Event combined with the viewing user's context (distance + RSVP state). */
data class EventView(
    val event: Event,
    val distanceKm: Double? = null,
    val rsvpStatus: RsvpStatus? = null
)

/** Registered EventFinder user (subset of the User schema in §5.3). */
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

/**
 * Aggregated environment data for an event's venue location.
 * Populated by [FreeLocationRepository] and displayed on the event detail screen.
 */
data class EventEnvironment(
    val weather: com.eventfinder.app.data.repository.WeatherSummary? = null,
    val airQuality: com.eventfinder.app.data.repository.AirQualitySummary? = null,
    val elevationMeters: Double? = null,
    val address: String? = null,
    val nearbyPlaces: List<com.eventfinder.app.data.repository.OsmPlace> = emptyList()
)