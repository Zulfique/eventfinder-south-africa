package com.eventfinder.app.data.remote

/**
 * Abstraction for the community-event backend that stores user-created events,
 * favourites and RSVPs. The current implementation is an in-memory mock that
 * keeps data in a local singleton — suitable for a single-device prototype.
 *
 * In production this would be a Retrofit interface against a real REST API
 * backed by a database. The interface is designed so swapping the mock for a
 * real backend requires only providing a different implementation; no
 * repository code changes are needed.
 */
interface CommunityEventApi {

    /** Stores or replaces a community event. */
    suspend fun upsertEvent(event: CommunityEventDto)

    /** Deletes a community event by id. */
    suspend fun deleteEvent(eventId: String)

    /** Stores or replaces a favourite link. */
    suspend fun upsertFavourite(userId: String, eventId: String)

    /** Removes a favourite link. */
    suspend fun deleteFavourite(userId: String, eventId: String)

    /** Stores or replaces an RSVP link. */
    suspend fun upsertRsvp(userId: String, eventId: String, status: String)

    /** Returns all events created by [userId]. */
    suspend fun getEventsByUser(userId: String): List<CommunityEventDto>

    /** Returns all events (community catalogue for sync). */
    suspend fun getAllEvents(): List<CommunityEventDto>
}

/** Lightweight DTO exchanged with the community backend. */
data class CommunityEventDto(
    val id: String,
    val title: String,
    val description: String,
    val category: String,
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
    val attendeeCount: Int
)
