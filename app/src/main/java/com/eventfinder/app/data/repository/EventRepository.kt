package com.eventfinder.app.data.repository

import com.eventfinder.app.data.local.DatabaseTransactionHelper
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
import com.eventfinder.app.data.store.SessionProvider
import com.eventfinder.app.domain.model.Event
import com.eventfinder.app.domain.model.EventAlertDetector
import com.eventfinder.app.domain.model.EventCategory
import com.eventfinder.app.domain.model.RsvpStatus
import com.eventfinder.app.notifications.NotificationHelper
import com.eventfinder.app.utils.AppLogger
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.util.UUID

/** Outcome of a background API sync. */
sealed interface SyncResult {
    data object Synced : SyncResult
    data object NoApiKey : SyncResult
    data object Failed : SyncResult
}

/**
 * Richer sync outcome carrying the alerts a sync produced so the caller can
 * post local notifications (new events / updated favourites).
 */
data class SyncOutcome(
    val result: SyncResult,
    val newEvents: List<Event> = emptyList(),
    val updatedFavorites: List<Event> = emptyList()
)

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
    suspend fun syncFromApi(): SyncOutcome

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
    private val database: DatabaseTransactionHelper,
    private val eventDao: EventDao,
    private val favoriteDao: FavoriteDao,
    private val rsvpDao: RsvpDao,
    private val pendingSyncDao: PendingSyncDao,
    private val ticketmasterApi: TicketmasterApi,
    private val apiKey: String,
    private val preferences: SessionProvider,
    private val context: android.content.Context? = null
) : EventRepository {

    private val mapper = TicketmasterMapper()
    private val gson = Gson()

    private val tag = "EventRepository"

    private suspend fun requireCurrentUserId(): Result<String> {
        val userId = preferences.sessionUserId.first()
        return if (userId.isNullOrBlank()) {
            Result.failure(IllegalStateException("not_logged_in"))
        } else {
            Result.success(userId)
        }
    }

    override fun observeAllEvents(): Flow<List<Event>> =
        eventDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun observeFavoriteIds(): Flow<Set<String>> =
        preferences.sessionUserId.flatMapLatest { userId ->
            if (userId.isNullOrBlank()) {
                kotlinx.coroutines.flow.flowOf(emptySet())
            } else {
                favoriteDao.observeAllForUser(userId).map { rows -> rows.map { it.eventId }.toSet() }
            }
        }

    override fun observeFavoriteEvents(): Flow<List<Event>> =
        preferences.sessionUserId.flatMapLatest { userId ->
            if (userId.isNullOrBlank()) {
                kotlinx.coroutines.flow.flowOf(emptyList())
            } else {
                eventDao.observeFavoriteEventsForUser(userId).map { rows -> rows.map { it.toDomain() } }
            }
        }

    override fun observeRsvpStatuses(): Flow<Map<String, RsvpStatus>> =
        preferences.sessionUserId.flatMapLatest { userId ->
            if (userId.isNullOrBlank()) {
                kotlinx.coroutines.flow.flowOf(emptyMap())
            } else {
                rsvpDao.observeAllForUser(userId).map { rows ->
                    rows.mapNotNull { row -> RsvpStatus.fromStorage(row.status)?.let { row.eventId to it } }.toMap()
                }
            }
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

    override suspend fun syncFromApi(): SyncOutcome {
        if (apiKey.isBlank()) {
            AppLogger.w(tag, "Sync skipped - no Ticketmaster API key configured (demo mode)")
            return SyncOutcome(SyncResult.NoApiKey)
        }
        return try {
            val previous = eventDao.getSynced().map { it.toDomain() }
            val userId = preferences.sessionUserId.first()
            val favoriteIds = if (userId != null) {
                favoriteDao.observeAllForUser(userId).first().map { it.eventId }.toSet()
            } else {
                emptySet()
            }

            val response = ticketmasterApi.getEvents(apiKey = apiKey, size = 100)
            val mapped = mapper.mapPage(response)
            if (mapped.isEmpty()) {
                AppLogger.w(tag, "Sync returned zero events - keeping existing cache")
                return SyncOutcome(SyncResult.Synced)
            }

            val alerts = EventAlertDetector.detect(previous, mapped, favoriteIds)

            // Replace the synced catalogue atomically to prevent half-updated state.
            eventDao.replaceSyncedEvents(mapped.map { it.toEntity(isSynced = true, isCreatedByUser = false) })
            AppLogger.i(tag, "Sync complete - ${mapped.size} live events stored")
            if (!alerts.isEmpty) {
                AppLogger.i(
                    tag,
                    "Alerts ready - ${alerts.newEvents.size} new, ${alerts.updatedFavorites.size} updated favourites"
                )
            }
            SyncOutcome(SyncResult.Synced, alerts.newEvents, alerts.updatedFavorites)
        } catch (t: IOException) {
            AppLogger.e(tag, "Sync failed - network unavailable, offline mode", t)
            SyncOutcome(SyncResult.Failed)
        } catch (t: Exception) {
            AppLogger.e(tag, "Sync failed - unexpected error", t)
            SyncOutcome(SyncResult.Failed)
        }
    }

    override suspend fun toggleFavorite(eventId: String): Boolean {
        val userId = preferences.sessionUserId.first()
            ?: return run { AppLogger.w(tag, "Cannot toggle favourite - not logged in") }.let { false }
        val already = favoriteDao.exists(userId, eventId)
        val newValue = !already

        database.setFavorite(userId = userId, eventId = eventId, favorite = newValue)

        enqueuePending(userId, "favorite", eventId, if (newValue) "create" else "delete", gson.toJson(eventId))
        AppLogger.i(tag, "${if (newValue) "Added" else "Removed"} favourite: $eventId")
        return newValue
    }

    override suspend fun setRsvp(eventId: String, status: RsvpStatus) {
        val userId = preferences.sessionUserId.first() ?: return
        rsvpDao.upsert(RsvpEntity(userId = userId, eventId = eventId, status = status.storage, createdAt = System.currentTimeMillis(), isSynced = false))
        enqueuePending(userId, "rsvp", eventId, "update", gson.toJson(status.storage))
        AppLogger.i(tag, "RSVP updated for $eventId -> ${status.storage}")
    }

    override suspend fun createEvent(draft: NewEventDraft): Result<String> {
        val userId = preferences.sessionUserId.first()
            ?: return Result.failure(IllegalStateException("not_logged_in"))

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
            imageUrl = draft.imageUrl,
            isPublic = draft.isPublic,
            organizerId = userId,
            organizerName = "You",
            attendeeCount = 0,
            isFavorite = false,
            isCreatedByUser = true,
            isSynced = false
        )
        eventDao.upsert(entity)
        enqueuePending(userId, "event", id, "create", gson.toJson(entity))
        AppLogger.i(tag, "Community event created locally: $id")
        return Result.success(id)
    }

    override suspend fun updateEvent(eventId: String, draft: NewEventDraft): Result<Unit> {
        val userId = requireCurrentUserId().getOrElse { return Result.failure(it) }

        val existing = eventDao.findById(eventId)
            ?: return Result.failure(IllegalArgumentException("event_missing"))
        if (!existing.isCreatedByUser) {
            AppLogger.w(tag, "Update rejected - not a user-created event: $eventId")
            return Result.failure(IllegalArgumentException("not_user_event"))
        }
        if (existing.organizerId != userId) {
            AppLogger.w(tag, "Update rejected - user $userId does not own event $eventId")
            return Result.failure(SecurityException("not_owner"))
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
        enqueuePending(userId, "event", eventId, "update", gson.toJson(updated))
        AppLogger.i(tag, "Community event updated locally: $eventId")
        return Result.success(Unit)
    }

    override suspend fun deleteEvent(eventId: String): Result<Unit> {
        val userId = requireCurrentUserId().getOrElse { return Result.failure(it) }

        val existing = eventDao.findById(eventId)
            ?: return Result.failure(IllegalArgumentException("event_missing"))
        if (!existing.isCreatedByUser) {
            AppLogger.w(tag, "Delete rejected - not a user-created event: $eventId")
            return Result.failure(IllegalArgumentException("not_user_event"))
        }
        if (existing.organizerId != userId) {
            AppLogger.w(tag, "Delete rejected - user $userId does not own event $eventId")
            return Result.failure(SecurityException("not_owner"))
        }
        context?.let { NotificationHelper.cancelEventReminders(it, eventId) }
        database.deleteEventAtomically(eventId, userId, gson.toJson(eventId))
        AppLogger.i(tag, "Community event deleted locally: $eventId")
        return Result.success(Unit)
    }

    override suspend fun getEvent(eventId: String): Event? =
        eventDao.findById(eventId)?.toDomain()

    override suspend fun clearLocalCache() {
        val userId = preferences.sessionUserId.first()
        if (userId != null && pendingSyncDao.countForUser(userId) > 0) {
            AppLogger.w(tag, "Skipping cache clear because pending changes exist for user")
            return
        }
        eventDao.deleteSynced()
        ensureSeeded()
        AppLogger.i(tag, "Remote cache cleared and sample events restored")
    }

    override suspend fun flushPendingActions(): SyncResult {
        if (apiKey.isBlank()) return SyncResult.NoApiKey
        val userId = preferences.sessionUserId.first() ?: return SyncResult.NoApiKey
        val pending = pendingSyncDao.allForUser(userId)
        if (pending.isEmpty()) return SyncResult.Synced
        AppLogger.w(
            tag,
            "Pending actions exist, but server replay is not implemented. " +
                "Keeping ${pending.size} action(s) queued."
        )
        return SyncResult.Failed
    }

    private suspend fun enqueuePending(userId: String, type: String, entityId: String, action: String, payload: String) {
        pendingSyncDao.insert(
            PendingSyncEntity(
                entityType = type,
                entityId = entityId,
                action = action,
                payload = payload,
                userId = userId,
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
            isFavorite = false,
            isCreatedByUser = isCreatedByUser,
            isSynced = isSynced
        )
}