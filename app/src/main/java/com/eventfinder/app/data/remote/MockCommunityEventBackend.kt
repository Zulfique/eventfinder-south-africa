package com.eventfinder.app.data.remote

import com.eventfinder.app.utils.AppLogger
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory mock implementation of [CommunityEventApi].
 *
 * Stores events, favourites and RSVPs in thread-safe maps so the
 * prototype can demonstrate the full offline-sync lifecycle on a single
 * device without requiring a real server.
 *
 * Data survives for the lifetime of the process — a config change or
 * process death resets the store, which is acceptable for a prototype.
 * In production, this would be replaced by a Retrofit client backed by
 * a real database.
 */
class MockCommunityEventBackend : CommunityEventApi {

    private val tag = "MockCommunityBackend"
    private val events = ConcurrentHashMap<String, CommunityEventDto>()
    private val favourites = ConcurrentHashMap<String, Boolean>()   // key = "userId:eventId"
    private val rsvps = ConcurrentHashMap<String, String>()         // key = "userId:eventId" → status

    override suspend fun upsertEvent(event: CommunityEventDto) {
        events[event.id] = event
        AppLogger.d(tag, "Event upserted: ${event.id} (${event.title})")
    }

    override suspend fun deleteEvent(eventId: String) {
        events.remove(eventId)
        AppLogger.d(tag, "Event deleted: $eventId")
    }

    override suspend fun upsertFavourite(userId: String, eventId: String) {
        favourites["$userId:$eventId"] = true
        AppLogger.d(tag, "Favourite added: $userId → $eventId")
    }

    override suspend fun deleteFavourite(userId: String, eventId: String) {
        favourites.remove("$userId:$eventId")
        AppLogger.d(tag, "Favourite removed: $userId → $eventId")
    }

    override suspend fun upsertRsvp(userId: String, eventId: String, status: String) {
        rsvps["$userId:$eventId"] = status
        AppLogger.d(tag, "RSVP upserted: $userId → $eventId ($status)")
    }

    override suspend fun getEventsByUser(userId: String): List<CommunityEventDto> =
        events.values.filter { it.organizerId == userId }

    override suspend fun getAllEvents(): List<CommunityEventDto> =
        events.values.toList()
}
