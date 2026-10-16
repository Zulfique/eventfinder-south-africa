package com.eventfinder.app.data.cloud

import android.content.Context
import com.eventfinder.app.data.local.EventDao
import com.eventfinder.app.data.local.EventEntity
import com.eventfinder.app.data.local.FavoriteDao
import com.eventfinder.app.data.local.FavoriteEntity
import com.eventfinder.app.data.local.RsvpDao
import com.eventfinder.app.data.local.RsvpEntity
import com.eventfinder.app.data.store.UserPreferences
import com.eventfinder.app.utils.AppLogger
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Cloud sync bridge between the local Room cache (offline-first source of
 * truth for the UI) and Cloud Firestore, Google's NoSQL document database.
 *
 * POE requirements covered here:
 *  - "Use a NoSQL database to store data"  → [pushLocalChanges]
 *  - "Use a NoSQL database to read data"   → [pullRemoteChanges]
 *  - Offline mode + automatic synchronisation when connectivity returns →
 *    [SyncCoordinator] invokes [syncNow] on every offline→online transition.
 *
 * Firestore layout (all reads/writes are scoped to the signed-in user):
 *   users/{userId}/events/{eventId}      — user-created events
 *   users/{userId}/favorites/{eventId}   — favourite links
 *   users/{userId}/rsvps/{eventId}       — RSVP links
 *
 * Every public method no-ops when Firebase is uninitialised (no real
 * google-services.json) so the app remains fully functional offline-only.
 */
class CloudSyncManager(
    private val context: Context,
    private val eventDao: EventDao,
    private val favoriteDao: FavoriteDao,
    private val rsvpDao: RsvpDao,
    private val preferences: UserPreferences
) {

    private val tag = "CloudSyncManager"

    private val firestore: FirebaseFirestore?
        get() = if (FirebaseStatus.isAvailable) FirebaseFirestore.getInstance() else null

    // ------------------------------------------------------------------
    // Public entry points
    // ------------------------------------------------------------------

    /**
     * Full bidirectional sync: push local changes first (so nothing created
     * offline is lost), then pull remote changes into Room. Safe to call
     * repeatedly — every operation is an idempotent upsert keyed by document id.
     */
    suspend fun syncNow() {
        if (!FirebaseStatus.isAvailable) {
            AppLogger.d(tag, "Firebase unavailable - skipping cloud sync (offline-only mode)")
            return
        }
        val userId = preferences.sessionUserId.first()
        if (userId.isNullOrBlank()) {
            AppLogger.d(tag, "No signed-in user - skipping cloud sync")
            return
        }
        AppLogger.i(tag, "Starting cloud sync for user $userId")
        pushLocalChanges(userId)
        pullRemoteChanges(userId)
        AppLogger.i(tag, "Cloud sync complete")
    }

    /** Pushes a single newly-created or updated event immediately (optimistic). */
    suspend fun pushEvent(event: EventEntity) {
        val userId = preferences.sessionUserId.first() ?: return
        if (!event.isCreatedByUser || event.organizerId != userId) return
        runCatching {
            userEventsCollection(userId).document(event.id).set(event.toFirestoreMap()).await()
            AppLogger.i(tag, "Pushed event ${event.id} to Firestore")
        }.onFailure {
            AppLogger.w(tag, "Failed to push event ${event.id}: ${it.message}")
        }
    }

    /** Removes an event document from Firestore after a local delete. */
    suspend fun deleteEventFromCloud(eventId: String) {
        val userId = preferences.sessionUserId.first() ?: return
        runCatching {
            userEventsCollection(userId).document(eventId).delete().await()
            AppLogger.i(tag, "Deleted event $eventId from Firestore")
        }.onFailure {
            AppLogger.w(tag, "Failed to delete event $eventId from Firestore: ${it.message}")
        }
    }

    /** Mirrors a favourite add/remove to Firestore. */
    suspend fun pushFavorite(userId: String, eventId: String, isFavorite: Boolean) {
        runCatching {
            val doc = userFavoritesCollection(userId).document(eventId)
            if (isFavorite) {
                doc.set(mapOf("eventId" to eventId, "createdAt" to System.currentTimeMillis())).await()
            } else {
                doc.delete().await()
            }
        }.onFailure {
            AppLogger.w(tag, "Failed to push favourite $eventId: ${it.message}")
        }
    }

    /** Mirrors an RSVP status change to Firestore. */
    suspend fun pushRsvp(userId: String, eventId: String, status: String) {
        runCatching {
            val doc = userRsvpsCollection(userId).document(eventId)
            if (status.isBlank() || status == "none") {
                doc.delete().await()
            } else {
                doc.set(mapOf("eventId" to eventId, "status" to status, "createdAt" to System.currentTimeMillis())).await()
            }
        }.onFailure {
            AppLogger.w(tag, "Failed to push RSVP $eventId: ${it.message}")
        }
    }

    // ------------------------------------------------------------------
    // Push (NoSQL store)
    // ------------------------------------------------------------------

    private suspend fun pushLocalChanges(userId: String) {
        val events = eventDao.getUserCreatedEventsForUser(userId)
        events.forEach { event ->
            runCatching {
                userEventsCollection(userId).document(event.id).set(event.toFirestoreMap()).await()
            }.onFailure {
                AppLogger.w(tag, "Event push failed for ${event.id}: ${it.message}")
            }
        }

        val favorites = favoriteDao.observeAllForUser(userId).first()
        favorites.forEach { favorite ->
            runCatching {
                userFavoritesCollection(userId).document(favorite.eventId)
                    .set(mapOf("eventId" to favorite.eventId, "createdAt" to favorite.createdAt))
                    .await()
            }
        }

        val rsvps = rsvpDao.observeAllForUser(userId).first()
        rsvps.forEach { rsvp ->
            runCatching {
                userRsvpsCollection(userId).document(rsvp.eventId)
                    .set(mapOf("eventId" to rsvp.eventId, "status" to rsvp.status, "createdAt" to rsvp.createdAt))
                    .await()
            }
        }
        AppLogger.i(
            tag,
            "Pushed ${events.size} event(s), ${favorites.size} favourite(s), ${rsvps.size} RSVP(s)"
        )
    }

    // ------------------------------------------------------------------
    // Pull (NoSQL read)
    // ------------------------------------------------------------------

    private suspend fun pullRemoteChanges(userId: String) {
        // Events: upsert every remote document into Room. Existing rows are
        // replaced only when the remote copy is not the same local-only seed.
        val remoteEvents = withTimeoutOrNull(PULL_TIMEOUT_MS) {
            userEventsCollection(userId)
                .orderBy("startDate", Query.Direction.ASCENDING)
                .limit(PULL_EVENT_LIMIT.toLong())
                .get()
                .await()
        }
        if (remoteEvents == null) {
            AppLogger.w(tag, "Event pull timed out")
        } else {
            val entities = remoteEvents.documents.mapNotNull { doc ->
                doc.data?.toEventEntity(doc.id)
            }
            if (entities.isNotEmpty()) {
                // Never overwrite events the user created on this device with a
                // stale remote copy of a different local row — doc ids match, so
                // a plain upsert is safe (same id = same event).
                eventDao.upsertAll(entities)
                AppLogger.i(tag, "Pulled ${entities.size} event(s) from Firestore")
            }
        }

        // Favourites
        val remoteFavorites = withTimeoutOrNull(PULL_TIMEOUT_MS) {
            userFavoritesCollection(userId).get().await()
        }
        remoteFavorites?.documents?.forEach { doc ->
            val eventId = doc.getString("eventId") ?: doc.id
            val createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
            if (!favoriteDao.exists(userId, eventId)) {
                favoriteDao.insert(FavoriteEntity(userId = userId, eventId = eventId, createdAt = createdAt))
            }
        }

        // RSVPs
        val remoteRsvps = withTimeoutOrNull(PULL_TIMEOUT_MS) {
            userRsvpsCollection(userId).get().await()
        }
        remoteRsvps?.documents?.forEach { doc ->
            val eventId = doc.getString("eventId") ?: doc.id
            val status = doc.getString("status") ?: return@forEach
            if (rsvpDao.statusFor(userId, eventId) == null) {
                rsvpDao.upsert(
                    RsvpEntity(
                        userId = userId,
                        eventId = eventId,
                        status = status,
                        createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
                    )
                )
            }
        }
    }

    // ------------------------------------------------------------------
    // Collections
    // ------------------------------------------------------------------

    private fun userCollection(userId: String) =
        firestore?.collection(USERS_COLLECTION)?.document(userId)
            ?: throw IllegalStateException("Firestore unavailable")

    private fun userEventsCollection(userId: String) = userCollection(userId).collection(EVENTS_COLLECTION)
    private fun userFavoritesCollection(userId: String) = userCollection(userId).collection(FAVORITES_COLLECTION)
    private fun userRsvpsCollection(userId: String) = userCollection(userId).collection(RSVPS_COLLECTION)

    companion object {
        private const val USERS_COLLECTION = "users"
        private const val EVENTS_COLLECTION = "events"
        private const val FAVORITES_COLLECTION = "favorites"
        private const val RSVPS_COLLECTION = "rsvps"
        private const val PULL_TIMEOUT_MS = 15_000L
        private const val PULL_EVENT_LIMIT = 200
    }
}

// ------------------------------------------------------------------
// Mapping helpers
// ------------------------------------------------------------------

/** Serialises an event into a Firestore document map. */
internal fun EventEntity.toFirestoreMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "title" to title,
    "description" to description,
    "category" to category,
    "startDate" to startDate,
    "endDate" to endDate,
    "venueName" to venueName,
    "address" to address,
    "latitude" to latitude,
    "longitude" to longitude,
    "imageUrl" to imageUrl,
    "isPublic" to isPublic,
    "organizerId" to organizerId,
    "organizerName" to organizerName,
    "attendeeCount" to attendeeCount,
    "isCreatedByUser" to true,
    "syncedAt" to System.currentTimeMillis()
)

/** Rebuilds an [EventEntity] from a Firestore document map; null when malformed. */
internal fun Map<String, Any?>.toEventEntity(docId: String): EventEntity? {
    val title = get("title") as? String ?: return null
    val description = get("description") as? String ?: return null
    val category = get("category") as? String ?: return null
    val startDate = (get("startDate") as? Number)?.toLong() ?: return null
    val endDate = (get("endDate") as? Number)?.toLong() ?: return null
    return EventEntity(
        id = docId,
        title = title,
        description = description,
        category = category,
        startDate = startDate,
        endDate = endDate,
        venueName = get("venueName") as? String ?: "",
        address = get("address") as? String ?: "",
        latitude = (get("latitude") as? Number)?.toDouble(),
        longitude = (get("longitude") as? Number)?.toDouble(),
        imageUrl = get("imageUrl") as? String,
        isPublic = get("isPublic") as? Boolean ?: true,
        organizerId = get("organizerId") as? String ?: "",
        organizerName = get("organizerName") as? String ?: "",
        attendeeCount = (get("attendeeCount") as? Number)?.toInt() ?: 0,
        isCreatedByUser = true
    )
}
