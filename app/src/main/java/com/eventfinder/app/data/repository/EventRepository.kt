package com.eventfinder.app.data.repository

import com.eventfinder.app.data.cloud.CloudSyncManager
import com.eventfinder.app.data.local.DatabaseTransactionHelper
import com.eventfinder.app.data.local.EventDao
import com.eventfinder.app.data.local.EventEntity
import com.eventfinder.app.data.local.FavoriteDao
import com.eventfinder.app.data.local.RsvpDao
import com.eventfinder.app.data.local.toDomain
import com.eventfinder.app.data.store.SessionProvider
import com.eventfinder.app.domain.model.Event
import com.eventfinder.app.domain.model.EventCategory
import com.eventfinder.app.domain.model.RsvpStatus
import com.eventfinder.app.utils.AppLogger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Sentinel stored in [NewEventDraft.imageUrl] to indicate the image was removed. */
const val IMAGE_REMOVED = "\u0000IMAGE_REMOVED"

/** Draft for creating or updating a community event. */
data class NewEventDraft(
    val title: String,
    val description: String,
    val category: EventCategory,
    val startDate: Long,
    val endDate: Long,
    val venueName: String,
    val address: String,
    val latitude: Double?,
    val longitude: Double?,
    val isPublic: Boolean,
    /** null = unchanged (edit mode), IMAGE_REMOVED = user removed image, otherwise = new image URL. */
    val imageUrl: String? = null
)

/**
 * Central event catalogue repository backed by Room. Room is the offline-first
 * source of truth; user-created events, favourites and RSVPs are additionally
 * mirrored to Cloud Firestore via [CloudSyncManager] when connectivity allows.
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

    /**
     * Clears non-user-created events and reseeds the sample catalogue.
     * Returns the number of active RSVPs that blocked the clear (0 = cleared).
     */
    suspend fun clearLocalCache(): Int
}

class EventRepositoryImpl(
    private val database: DatabaseTransactionHelper,
    private val eventDao: EventDao,
    private val favoriteDao: FavoriteDao,
    private val rsvpDao: RsvpDao,
    private val preferences: SessionProvider,
    private val context: android.content.Context? = null,
    private val cloudSyncManager: CloudSyncManager? = null,
    private val apiClient: com.eventfinder.app.data.remote.EventFinderApiClient? = null
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

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeFavoriteIds(): Flow<Set<String>> =
        preferences.sessionUserId.flatMapLatest { userId ->
            if (userId.isNullOrBlank()) {
                flowOf(emptySet())
            } else {
                favoriteDao.observeAllForUser(userId).map { rows -> rows.map { it.eventId }.toSet() }
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeFavoriteEvents(): Flow<List<Event>> =
        preferences.sessionUserId.flatMapLatest { userId ->
            if (userId.isNullOrBlank()) {
                flowOf(emptyList())
            } else {
                eventDao.observeFavoriteEventsForUser(userId).map { rows -> rows.map { it.toDomain() } }
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
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
    val now = System.currentTimeMillis()
    val upcomingSamples = eventDao.countUpcomingSampleEvents(now)

    if (upcomingSamples == 0) {
        eventDao.deleteSampleEvents()

        val seed = SampleEventsProvider.johannesburgAndCapeTown()

        eventDao.upsertAll(
            seed.map { it.toEntity(isCreatedByUser = false) }
        )

        AppLogger.i(
            tag,
            "Seeded ${seed.size} fresh sample events into cache"
        )
    } else {
        AppLogger.d(
            tag,
            "Upcoming sample catalogue already contains $upcomingSamples events"
        )
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

        database.setFavoriteAtomically(userId, eventId, newValue)
        AppLogger.i(tag, "${if (newValue) "Added" else "Removed"} favourite: $eventId")
        // Mirror the change to Firestore (fire-and-forget; retried on reconnect).
        cloudSyncManager?.let { sync ->
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                runCatching { sync.pushFavorite(userId, eventId, newValue) }
            }
        }
        return newValue
    }

    override suspend fun setRsvp(eventId: String, status: RsvpStatus) {
        val userId = preferences.sessionUserId.first() ?: return
        val event = eventDao.findById(eventId)
        if (event == null) {
            AppLogger.w(tag, "Cannot set RSVP - event not found: $eventId")
            return
        }
        database.setRsvpAtomically(userId, eventId, status.storage)
        AppLogger.i(tag, "RSVP updated for $eventId -> ${status.storage}")
        cloudSyncManager?.let { sync ->
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                runCatching { sync.pushRsvp(userId, eventId, status.storage) }
            }
        }
        // Mirror to the backend so multi-user attendee lists stay current.
        pushRsvpToApi(eventId, status.storage)
    }

    private fun pushRsvpToApi(eventId: String, status: String) {
        val client = apiClient ?: return
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            val token = preferences.authToken.firstOrNull() ?: return@launch
            val bearer = "Bearer $token"
            if (status.isBlank() || status == "none") {
                runCatching { client.cancelRsvp(bearer, eventId) }
            } else {
                runCatching { client.rsvp(bearer, eventId, status) }
            }.onFailure { AppLogger.w(tag, "API RSVP push failed: ${it.message}") }
        }
    }

    override suspend fun createEvent(draft: NewEventDraft): Result<String> {
        val userId = preferences.sessionUserId.first()
            ?: return Result.failure(IllegalStateException("not_logged_in"))

        // Normalize IMAGE_REMOVED to null for new events (there's no previous image to remove)
        val imageUrl = when (draft.imageUrl) {
            null -> null
            IMAGE_REMOVED -> null
            else -> draft.imageUrl
        }

        // Prefer a server-generated identifier when the backend is reachable.
        var id = java.util.UUID.randomUUID().toString()
        val token = preferences.authToken.firstOrNull()
        if (apiClient != null && !token.isNullOrBlank()) {
            val body = com.eventfinder.app.data.remote.EventFinderApiClient.eventBody(
                title = draft.title.trim(),
                description = draft.description.trim(),
                venueName = draft.venueName.trim(),
                address = draft.address.trim(),
                city = "",
                latitude = draft.latitude ?: -26.2041,
                longitude = draft.longitude ?: 28.0473,
                startUtcMillis = draft.startDate,
                endUtcMillis = draft.endDate,
                category = draft.category.labelKey,
                imageUrl = imageUrl,
                isPublic = draft.isPublic
            )
            apiClient.createEvent("Bearer $token", body)
                .onSuccess { remote -> id = remote.id }
                .onFailure { AppLogger.w(tag, "API create failed - using local id: ${it.message}") }
        }

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
            imageUrl = imageUrl,
            isPublic = draft.isPublic,
            organizerId = userId,
            organizerName = "You",
            attendeeCount = 0,
            isCreatedByUser = true
        )
        database.createEventAtomically(entity)
        AppLogger.i(tag, "Community event created locally: $id")
        cloudSyncManager?.let { sync ->
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                runCatching { sync.pushEvent(entity) }
            }
        }
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

        val newImageUrl = when (draft.imageUrl) {
            null -> existing.imageUrl
            IMAGE_REMOVED -> null
            else -> draft.imageUrl
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
            imageUrl = newImageUrl,
            isPublic = draft.isPublic
        )
        database.updateEventAtomically(updated)
        AppLogger.i(tag, "Community event updated locally: $eventId")
        cloudSyncManager?.let { sync ->
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                runCatching { sync.pushEvent(updated) }
            }
        }
        // Best-effort mirror to the backend (owner-guarded server-side).
        apiClient?.let { client ->
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                val token = preferences.authToken.firstOrNull() ?: return@launch
                val body = com.eventfinder.app.data.remote.EventFinderApiClient.eventBody(
                    title = updated.title,
                    description = updated.description,
                    venueName = updated.venueName,
                    address = updated.address,
                    city = "",
                    latitude = updated.latitude ?: 0.0,
                    longitude = updated.longitude ?: 0.0,
                    startUtcMillis = updated.startDate,
                    endUtcMillis = updated.endDate,
                    category = updated.category,
                    imageUrl = updated.imageUrl,
                    isPublic = updated.isPublic
                )
                runCatching { client.updateEvent("Bearer $token", eventId, body) }
                    .onFailure { AppLogger.w(tag, "API update push failed: ${it.message}") }
            }
        }
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
        database.deleteEventAtomically(eventId)
        AppLogger.i(tag, "Community event deleted locally: $eventId")
        cloudSyncManager?.let { sync ->
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                runCatching { sync.deleteEventFromCloud(eventId) }
            }
        }
        apiClient?.let { client ->
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                val token = preferences.authToken.firstOrNull() ?: return@launch
                runCatching { client.deleteEvent("Bearer $token", eventId) }
                    .onFailure { AppLogger.w(tag, "API delete push failed: ${it.message}") }
            }
        }
        return Result.success(Unit)
    }

    override suspend fun getEvent(eventId: String): Event? =
        eventDao.findById(eventId)?.toDomain()

    override suspend fun clearLocalCache(): Int {
        val userId = preferences.sessionUserId.first()

        val activeRsvps = userId?.let { rsvpDao.attendingCountForUser(it) } ?: 0
        if (activeRsvps > 0) {
            AppLogger.w(tag, "Skipping cache clear because user has active RSVPs")
            return activeRsvps
        }

        eventDao.clearNonUserCreatedAtomically()
        ensureSeeded()
        AppLogger.i(tag, "External/local catalogue cache cleared and seed data restored")
        return 0
    }

    private fun Event.toEntity(isCreatedByUser: Boolean): EventEntity =
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
            isCreatedByUser = isCreatedByUser
        )
}
