package com.eventfinder.app.data.repository

import com.eventfinder.app.data.local.EventDao
import com.eventfinder.app.data.local.EventEntity
import com.eventfinder.app.data.local.FavoriteDao
import com.eventfinder.app.data.local.FavoriteEntity
import com.eventfinder.app.data.local.PendingSyncDao
import com.eventfinder.app.data.local.PendingSyncEntity
import com.eventfinder.app.data.local.RsvpDao
import com.eventfinder.app.data.local.RsvpEntity
import com.eventfinder.app.data.local.toDomain
import com.eventfinder.app.data.remote.TicketmasterApi
import com.eventfinder.app.data.remote.TicketmasterMapper
import com.eventfinder.app.domain.model.Event
import com.eventfinder.app.domain.model.EventCategory
import com.eventfinder.app.domain.model.RsvpStatus
import com.eventfinder.app.utils.AppLogger
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.util.UUID

/** Outcome of a background API sync. */
sealed interface SyncResult {
    data object Synced : SyncResult
    data object NoApiKey : SyncResult
    data object Failed : SyncResult
}

/** Draft for creating a new community event (FR-06). */
data class NewEventDraft(
    val title: String,
    val description: String,
    val category: EventCategory,
    val startDate: Long,
    val endDate: Long,
    val venueName: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val isPublic: Boolean,
    val imageUrl: String? = null
)

/**
 * Central event catalogue repository. Combines the Room cache (offline-first,
 * FR-09) with the free Ticketmaster REST API for live sync.
 */
interface EventRepository {
    /** All events from the local cache, streamed reactively. */
    fun observeAllEvents(): Flow<List<Event>>

    /** Favourite events (offline-accessible, FR-03). */
    fun observeFavoriteEvents(): Flow<List<Event>>

    /** Favourite event ids kept in sync with the cache. */
    fun observeFavoriteIds(): Flow<Set<String>>

    /** RSVP state per event id. */
    fun observeRsvpStatuses(): Flow<Map<String, RsvpStatus>>

    /** Seeds the cache with a curated SA sample directory on first run. */
    suspend fun ensureSeeded()

    /** Pulls fresh events from the free Ticketmaster API. Returns the outcome. */
    suspend fun syncFromApi(): SyncResult

    suspend fun toggleFavorite(eventId: String): Boolean

    suspend fun setRsvp(eventId: String, status: RsvpStatus)

    suspend fun createEvent(draft: NewEventDraft): Result<String>

    /** Updates a community event the user created (FR-06). */
    suspend fun updateEvent(eventId: String, draft: NewEventDraft): Result<Unit>

    /** Deletes a community event the user created (FR-06). */
    suspend fun deleteEvent(eventId: String): Result<Unit>

    suspend fun getEvent(eventId: String): Event?

    /** Clears the synced catalogue + user events, then reseeds the sample set. */
    suspend fun clearLocalCache()

    /** Tries to flush the offline action queue to the API (best effort). */
    suspend fun flushPendingActions(): SyncResult
}

class EventRepositoryImpl(
    private val eventDao: EventDao,
    private val favoriteDao: FavoriteDao,
    private val rsvpDao: RsvpDao,
    private val pendingSyncDao: PendingSyncDao,
    private val ticketmasterApi: TicketmasterApi,
    private val apiKey: String
) : EventRepository {

    private val mapper = TicketmasterMapper()
    private val gson = Gson()

    private val tag = "EventRepository"

    override fun observeAllEvents(): Flow<List<Event>> =
        eventDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun observeFavoriteIds(): Flow<Set<String>> =
        favoriteDao.observeAll().map { rows -> rows.map { it.eventId }.toSet() }

    override fun observeFavoriteEvents(): Flow<List<Event>> =
        eventDao.observeFavorites().map { rows -> rows.map { it.toDomain() } }

    override fun observeRsvpStatuses(): Flow<Map<String, RsvpStatus>> =
        rsvpDao.observeAll().map { rows ->
            rows.mapNotNull { row -> RsvpStatus.fromStorage(row.status)?.let { row.eventId to it } }.toMap()
        }

    override suspend fun ensureSeeded() {
        val count = eventDao.count()
        if (count == 0) {
            val seed = SampleEventsProvider.johannesburgAndCapeTown()
            eventDao.upsertAll(seed.map { it.toEntity(isSynced = false, isCreatedByUser = false) })
            AppLogger.i(tag, "Seeded ${seed.size} sample events into cache")
        } else {
            AppLogger.d(tag, "Cache already contains $count events - no seeding required")
        }
    }

    override suspend fun syncFromApi(): SyncResult {
        if (apiKey.isBlank()) {
            AppLogger.w(tag, "Sync skipped - no Ticketmaster API key configured (demo mode)")
            return SyncResult.NoApiKey
        }
        return try {
            val response = ticketmasterApi.getEvents(apiKey = apiKey, size = 100)
            val mapped = mapper.mapPage(response)
            if (mapped.isEmpty()) {
                AppLogger.w(tag, "Sync returned zero events - keeping existing cache")
                return SyncResult.Synced
            }
            // Replace the synced catalogue but preserve user-created events.
            eventDao.deleteSynced()
            eventDao.upsertAll(mapped.map { it.toEntity(isSynced = true, isCreatedByUser = false) })
            AppLogger.i(tag, "Sync complete - ${mapped.size} live events stored")
            SyncResult.Synced
        } catch (t: IOException) {
            AppLogger.e(tag, "Sync failed - network unavailable, offline mode", t)
            SyncResult.Failed
        } catch (t: Exception) {
            AppLogger.e(tag, "Sync failed - unexpected error", t)
            SyncResult.Failed
        }
    }

    override suspend fun toggleFavorite(eventId: String): Boolean {
        val already = favoriteDao.exists(eventId) != null
        if (already) {
            favoriteDao.delete(eventId)
            eventDao.setFavorite(eventId, false)
            AppLogger.i(tag, "Removed favourite: $eventId")
        } else {
            favoriteDao.insert(FavoriteEntity(eventId = eventId, createdAt = System.currentTimeMillis(), isSynced = true))
            eventDao.setFavorite(eventId, true)
            AppLogger.i(tag, "Added favourite: $eventId")
        }
        enqueuePending("favorite", eventId, if (already) "delete" else "create", gson.toJson(eventId))
        return !already
    }

    override suspend fun setRsvp(eventId: String, status: RsvpStatus) {
        rsvpDao.upsert(RsvpEntity(eventId = eventId, status = status.storage, createdAt = System.currentTimeMillis(), isSynced = true))
        enqueuePending("rsvp", eventId, "update", gson.toJson(status.storage))
        AppLogger.i(tag, "RSVP updated for $eventId -> ${status.storage}")
    }

    override suspend fun createEvent(draft: NewEventDraft): Result<String> {
        // Server-issued ids are GUIDs per the Part 1 design (§5.3); used locally too.
        val id = UUID.randomUUID().toString()
        val entity = EventEntity(
            id = id,
            title = draft.title.trim(),
            description = draft.description.trim(),
            category = draft.category.labelKey,
            startDate = draft.startDate,
            endDate = draft.endDate,
            venueName = draft.venueName.trim(),
            address = draft.address.trim(),
            latitude = draft.latitude,
            longitude = draft.longitude,
            imageUrl = null,
            isPublic = draft.isPublic,
            organizerId = "local-user",
            organizerName = "You",
            attendeeCount = 0,
            isFavorite = false,
            isCreatedByUser = true,
            isSynced = false
        )
        eventDao.upsert(entity)
        enqueuePending("event", id, "create", gson.toJson(entity))
        AppLogger.i(tag, "Community event created locally: $id")
        return Result.success(id)
    }

    override suspend fun updateEvent(eventId: String, draft: NewEventDraft): Result<Unit> {
        val existing = eventDao.findById(eventId)
            ?: return Result.failure(IllegalArgumentException("event_missing"))
        if (!existing.isCreatedByUser) {
            AppLogger.w(tag, "Update rejected - not a user-created event: $eventId")
            return Result.failure(IllegalArgumentException("not_owner"))
        }
        val updated = existing.copy(
            title = draft.title.trim(),
            description = draft.description.trim(),
            category = draft.category.labelKey,
            startDate = draft.startDate,
            endDate = draft.endDate,
            venueName = draft.venueName.trim(),
            address = draft.address.trim(),
            latitude = draft.latitude,
            longitude = draft.longitude,
            imageUrl = draft.imageUrl ?: existing.imageUrl,
            isPublic = draft.isPublic,
            isSynced = false
        )
        eventDao.upsert(updated)
        enqueuePending("event", eventId, "update", gson.toJson(updated))
        AppLogger.i(tag, "Community event updated locally: $eventId")
        return Result.success(Unit)
    }

    override suspend fun deleteEvent(eventId: String): Result<Unit> {
        val existing = eventDao.findById(eventId)
            ?: return Result.failure(IllegalArgumentException("event_missing"))
        if (!existing.isCreatedByUser) {
            AppLogger.w(tag, "Delete rejected - not a user-created event: $eventId")
            return Result.failure(IllegalArgumentException("not_owner"))
        }
        eventDao.deleteById(eventId)
        favoriteDao.delete(eventId)
        rsvpDao.delete(eventId)
        enqueuePending("event", eventId, "delete", gson.toJson(eventId))
        AppLogger.i(tag, "Community event deleted locally: $eventId")
        return Result.success(Unit)
    }

    override suspend fun getEvent(eventId: String): Event? =
        eventDao.findById(eventId)?.toDomain()

    override suspend fun clearLocalCache() {
        eventDao.deleteSynced()
        eventDao.deleteCreatedByUser()
        ensureSeeded()
        AppLogger.i(tag, "Local event cache cleared and reseeded")
    }

    override suspend fun flushPendingActions(): SyncResult {
        if (apiKey.isBlank()) return SyncResult.NoApiKey
        val pending = pendingSyncDao.all()
        if (pending.isEmpty()) return SyncResult.Synced
        // Prototype: mock API acknowledgement. Real replay is added in the final POE
        // against the ASP.NET Core backend endpoints defined in Part 1 §5.2.
        pending.forEach { action ->
            AppLogger.i(tag, "Flushing pending ${action.entityType} '${action.action}' (${action.entityId})")
            pendingSyncDao.delete(action.id)
        }
        return SyncResult.Synced
    }

    private suspend fun enqueuePending(type: String, entityId: String, action: String, payload: String) {
        pendingSyncDao.insert(
            PendingSyncEntity(
                entityType = type,
                entityId = entityId,
                action = action,
                payload = payload,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    private fun Event.toEntity(isSynced: Boolean, isCreatedByUser: Boolean): EventEntity =
        EventEntity(
            id = id,
            title = title,
            description = description,
            category = category.labelKey,
            startDate = startDate,
            endDate = endDate,
            venueName = venueName,
            address = address,
            latitude = latitude,
            longitude = longitude,
            imageUrl = imageUrl,
            isPublic = isPublic,
            organizerId = organizerId,
            organizerName = organizerName,
            attendeeCount = attendeeCount,
            isFavorite = isFavorite,
            isCreatedByUser = isCreatedByUser,
            isSynced = isSynced
        )
}