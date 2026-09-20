package com.eventfinder.app.data.repository

import com.eventfinder.app.data.local.DatabaseTransactionHelper
import com.eventfinder.app.data.local.EventDao
import com.eventfinder.app.data.local.EventEntity
import com.eventfinder.app.data.local.FavoriteDao
import com.eventfinder.app.data.local.PendingSyncDao
import com.eventfinder.app.data.local.RsvpDao
import com.eventfinder.app.data.local.toDomain
import com.eventfinder.app.data.remote.TicketmasterApi
import com.eventfinder.app.data.remote.TicketmasterMapper
import com.eventfinder.app.data.store.SessionProvider
import com.eventfinder.app.domain.model.Event
import com.eventfinder.app.domain.model.EventAlertDetector
import com.eventfinder.app.domain.model.EventCategory
import com.eventfinder.app.domain.model.RsvpStatus
import com.eventfinder.app.utils.AppLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.io.IOException

/** Outcome of a background sync or queue flush. */
sealed interface SyncResult {
    data object Synced : SyncResult
    data object NoSession : SyncResult
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
 * FR-09) with the free Ticketmaster REST API for live sync. The pending-action
 * queue is a local operation journal — Room is the only authoritative store.
 */
interface EventRepository {
    fun observeAllEvents(): Flow<List<Event>>
    fun observeFavoriteEvents(): Flow<List<Event>>
    fun observeFavoriteIds(): Flow<Set<String>>
    fun observeRsvpStatuses(): Flow<Map<String, RsvpStatus>>
    suspend fun ensureSeeded()
    suspend fun syncFromApi(): SyncOutcome
    suspend fun toggleFavorite(eventId: String): Boolean
    suspend fun setRsvp(eventId: String, status: RsvpStatus)
    suspend fun createEvent(draft: NewEventDraft): Result<String>
    suspend fun updateEvent(eventId: String, draft: NewEventDraft): Result<Unit>
    suspend fun deleteEvent(eventId: String): Result<Unit>
    suspend fun getEvent(eventId: String): Event?
    suspend fun clearLocalCache()
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
                flowOf(emptySet())
            } else {
                favoriteDao.observeAllForUser(userId).map { rows -> rows.map { it.eventId }.toSet() }
            }
        }

    override fun observeFavoriteEvents(): Flow<List<Event>> =
        preferences.sessionUserId.flatMapLatest { userId ->
            if (userId.isNullOrBlank()) {
                flowOf(emptyList())
            } else {
                eventDao.observeFavoriteEventsForUser(userId).map { rows -> rows.map { it.toDomain() } }
            }
        }

    override fun observeRsvpStatuses(): Flow<Map<String, RsvpStatus>> =
        preferences.sessionUserId.flatMapLatest { userId ->
            if (userId.isNullOrBlank()) {
                flowOf(emptyMap())
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
            return SyncOutcome(SyncResult.NoSession)
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

        database.setFavoriteAtomically(
            userId = userId,
            eventId = eventId,
            favorite = newValue,
            pendingAction = if (newValue) "create" else "delete",
            pendingPayload = eventId
        )
        AppLogger.i(tag, "${if (newValue) "Added" else "Removed"} favourite: $eventId")
        return newValue
    }

    override suspend fun setRsvp(eventId: String, status: RsvpStatus) {
        val userId = preferences.sessionUserId.first() ?: return
        database.setRsvpAtomically(
            userId = userId,
            eventId = eventId,
            status = status.storage,
            pendingPayload = status.storage
        )
        AppLogger.i(tag, "RSVP updated for $eventId -> ${status.storage}")
    }

    override suspend fun createEvent(draft: NewEventDraft): Result<String> {
        val userId = preferences.sessionUserId.first()
            ?: return Result.failure(IllegalStateException("not_logged_in"))

        val id = java.util.UUID.randomUUID().toString()
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
            isCreatedByUser = true,
            isSynced = false
        )
        database.createEventAtomically(entity, userId, "")
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
        database.updateEventAtomically(updated, userId, "")
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
        context?.let { com.eventfinder.app.notifications.NotificationHelper.cancelEventReminders(it, eventId) }
        database.deleteEventAtomically(eventId, userId, "")
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

    /**
     * Drains the local pending-operation queue.
     *
     * EventFinder has no cloud backend. Room is the authoritative data store.
     * The pending queue is therefore a durable local operation journal. Draining
     * it reconciles local sync flags and removes completed journal entries.
     */
    override suspend fun flushPendingActions(): SyncResult {
        val userId = preferences.sessionUserId.first() ?: return SyncResult.NoSession
        val pending = pendingSyncDao.allForUser(userId)
        if (pending.isEmpty()) return SyncResult.Synced

        for (action in pending) {
            try {
                when (action.entityType) {
                    "event" -> {
                        if (action.action != "delete") {
                            eventDao.markSynced(action.entityId)
                        }
                    }
                    "favorite" -> {
                        favoriteDao.markSynced(userId, action.entityId)
                    }
                    "rsvp" -> {
                        rsvpDao.markSynced(userId, action.entityId)
                    }
                    else -> {
                        AppLogger.w(tag, "Removing unknown local queue entry ${action.id}")
                    }
                }
                pendingSyncDao.delete(action.id)
            } catch (e: Exception) {
                AppLogger.e(tag, "Failed to drain local queue entry ${action.id}", e)
                pendingSyncDao.incrementRetry(action.id)
                return SyncResult.Failed
            }
        }
        return SyncResult.Synced
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
            isCreatedByUser = isCreatedByUser,
            isSynced = isSynced
        )
}
