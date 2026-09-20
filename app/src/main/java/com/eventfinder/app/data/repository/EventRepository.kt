package com.eventfinder.app.data.repository

import com.eventfinder.app.data.local.DatabaseTransactionHelper
import com.eventfinder.app.data.local.EventDao
import com.eventfinder.app.data.local.EventEntity
import com.eventfinder.app.data.local.FavoriteDao
import com.eventfinder.app.data.local.PendingSyncDao
import com.eventfinder.app.data.local.RsvpDao
import com.eventfinder.app.data.local.toDomain
import com.eventfinder.app.data.store.SessionProvider
import com.eventfinder.app.domain.model.Event
import com.eventfinder.app.domain.model.EventCategory
import com.eventfinder.app.domain.model.RsvpStatus
import com.eventfinder.app.utils.AppLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** Outcome of a journal flush operation. */
sealed interface SyncResult {
    data object Synced : SyncResult
    /** No user session is active; queue flush skipped. */
    data object NoSession : SyncResult
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
 * FR-09) with sample seed data. The pending-action queue is a local operation
 * journal — Room is the only authoritative store.
 */
interface EventRepository {
    fun observeAllEvents(): Flow<List<Event>>
    fun observeFavoriteEvents(): Flow<List<Event>>
    fun observeFavoriteIds(): Flow<Set<String>>
    fun observeRsvpStatuses(): Flow<Map<String, RsvpStatus>>
    suspend fun ensureSeeded()
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
    private val preferences: SessionProvider,
    private val context: android.content.Context? = null
) : EventRepository {

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
            eventDao.upsertAll(seed.map { it.toEntity(isExternal = false, isCreatedByUser = false) })
            AppLogger.i(tag, "Seeded ${seed.size} sample events into cache")
        } else {
            AppLogger.d(tag, "Cache already contains $count events - no seeding required")
        }
    }

    override suspend fun toggleFavorite(eventId: String): Boolean {
        val userId = preferences.sessionUserId.first()
            ?: return run { AppLogger.w(tag, "Cannot toggle favourite - not logged in") }.let { false }
        val event = eventDao.findById(eventId)
        if (event == null) {
            AppLogger.w(tag, "Cannot toggle favourite - event not found: $eventId")
            return false
        }
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
        val event = eventDao.findById(eventId)
        if (event == null) {
            AppLogger.w(tag, "Cannot set RSVP - event not found: $eventId")
            return
        }
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
            isExternal = false
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
            isExternal = false
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

        if (
            userId != null &&
            pendingSyncDao.countForUser(userId) > 0
        ) {
            AppLogger.w(
                tag,
                "Skipping cache clear because pending local changes exist"
            )
            return
        }

        eventDao.deleteNonUserCreated()
        ensureSeeded()

        AppLogger.i(
            tag,
            "External/local catalogue cache cleared and seed data restored"
        )
    }

    /**
     * Drains the local pending-operation queue.
     *
     * EventFinder has no cloud backend. Room is the authoritative data store.
     * The pending queue is therefore a durable local operation journal. Draining
     * it reconciles local entity flags (marks favorites/RSVPs as flushed and
     * events as external where appropriate) and removes completed journal entries.
     */
    override suspend fun flushPendingActions(): SyncResult {
        val userId =
            preferences.sessionUserId.first()
                ?: return SyncResult.NoSession

        val pending =
            pendingSyncDao.allForUser(userId)

        if (pending.isEmpty()) {
            return SyncResult.Synced
        }

        for (action in pending) {
            try {
                when (action.entityType) {
                    "event" -> {
                        // Room is the authoritative store.
                        // Nothing needs to be uploaded or marked externally.
                    }
                    "favorite" -> {
                        favoriteDao.markFlushed(
                            userId,
                            action.entityId
                        )
                    }
                    "rsvp" -> {
                        rsvpDao.markFlushed(
                            userId,
                            action.entityId
                        )
                    }
                    else -> {
                        AppLogger.w(
                            tag,
                            "Removing unknown local journal entry ${action.id}"
                        )
                    }
                }

                pendingSyncDao.delete(action.id)

            } catch (e: Exception) {

                AppLogger.e(
                    tag,
                    "Failed to reconcile local journal entry ${action.id}",
                    e
                )

                pendingSyncDao.incrementRetry(action.id)

                return SyncResult.Failed
            }
        }

        return SyncResult.Synced
    }

    private fun Event.toEntity(isExternal: Boolean, isCreatedByUser: Boolean): EventEntity =
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
            isExternal = isExternal
        )
}
