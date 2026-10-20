package com.eventfinder.app.data.cloud

import android.content.Context
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import kotlinx.coroutines.CancellationException
import com.eventfinder.app.data.local.EventDao
import com.eventfinder.app.data.local.EventEntity
import com.eventfinder.app.data.local.FavoriteDao
import com.eventfinder.app.data.local.FavoriteEntity
import com.eventfinder.app.data.local.RsvpDao
import com.eventfinder.app.data.local.RsvpEntity
import com.eventfinder.app.data.remote.EventFinderApiClient
import com.eventfinder.app.data.store.UserPreferences
import com.eventfinder.app.utils.AppLogger
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Cloud sync bridge between the local Room cache (offline-first source of
 * truth for the UI) and Cloud Firestore, Google's NoSQL document database.
 *
 * POE requirements covered here:
 *  - "Use a NoSQL database to store data"  â†’ [pushLocalChanges]
 *  - "Use a NoSQL database to read data"   â†’ [pullRemoteChanges]
 *  - Offline mode + automatic synchronisation when connectivity returns â†’
 *    [SyncCoordinator] invokes [syncNow] on every offlineâ†’online transition.
 *
 * Firestore layout (all reads/writes are scoped to the signed-in user;
 * the path key is the Firebase anonymous-auth uid so secure rules
 * `request.auth.uid == userId` authorise every operation):
 *   users/{uid}/events/{eventId}      â€” user-created events
 *   users/{uid}/favorites/{eventId}   â€” favourite links
 *   users/{uid}/rsvps/{eventId}       â€” RSVP links
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

    private suspend fun ownerUid(userId: String?): String? {
        if (userId.isNullOrBlank() || !FirebaseStatus.isAvailable) return null
        return try {
            val bearer = preferences.authToken.first()
            if (!bearer.isNullOrBlank()) {
                val tokenResult = EventFinderApiClient.default.firebaseToken("Bearer $bearer")
                val firebaseToken = tokenResult.getOrNull()?.firebaseToken
                if (!firebaseToken.isNullOrBlank()) {
                    val user = withTimeoutOrNull(AUTH_TIMEOUT_MS) {
                        FirebaseAuth.getInstance().signInWithCustomToken(firebaseToken).await()?.user
                    }
                    val uid = user?.uid
                    if (uid != null) {
                        preferences.setFirebaseUid(uid)
                        return uid
                    }
                }
            }

            val storedUid = preferences.firebaseUid.first()
            if (storedUid != null) {
                return storedUid
            }

            val auth = FirebaseAuth.getInstance()
            val user = auth.currentUser
                ?: withTimeoutOrNull(AUTH_TIMEOUT_MS) { auth.signInAnonymously().await()?.user }
            val uid = user?.uid
            if (uid != null) {
                preferences.setFirebaseUid(uid)
            }
            uid
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.w(tag, "Firebase auth failed: ${e.message}")
            preferences.firebaseUid.first()
        }
    }

    // ------------------------------------------------------------------
    // Public entry points
    // ------------------------------------------------------------------

    /**
     * Full bidirectional sync: push local changes first (so nothing created
     * offline is lost), then pull remote changes into Room. Safe to call
     * repeatedly â€” every operation is an idempotent upsert keyed by document id.
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
        val owner = ownerUid(userId)
        if (owner == null) {
            AppLogger.d(tag, "No Firebase identity - skipping cloud sync")
            return
        }
        AppLogger.i(tag, "Starting cloud sync for user $userId (owner $owner)")
        pushLocalChanges(userId, owner)
        pullRemoteChanges(userId, owner)
        AppLogger.i(tag, "Cloud sync complete")
    }

    /** Pushes a single newly-created or updated event immediately (optimistic). */
    suspend fun pushEvent(event: EventEntity) {
        val userId = preferences.sessionUserId.first() ?: return
        if (!event.isCreatedByUser || event.organizerId != userId) return
        val owner = ownerUid(userId) ?: return
        runCatching {
            userEventsCollection(owner).document(event.id).set(event.toFirestoreMap()).await()
            AppLogger.i(tag, "Pushed event ${event.id} to Firestore")
        }.onFailure { exception ->
            if (exception is CancellationException) throw exception
            AppLogger.w(tag, "Failed to push event ${event.id}: ${exception.message}")
        }
    }

    /** Removes an event document from Firestore after a local delete. */
    suspend fun deleteEventFromCloud(eventId: String) {
        val userId = preferences.sessionUserId.first() ?: return
        val owner = ownerUid(userId) ?: return
        runCatching {
            userEventsCollection(owner).document(eventId).delete().await()
            AppLogger.i(tag, "Deleted event $eventId from Firestore")
        }.onFailure { exception ->
            if (exception is CancellationException) throw exception
            AppLogger.w(tag, "Failed to delete event $eventId from Firestore: ${exception.message}")
        }
    }

    /** Mirrors a favourite add/remove to Firestore. */
    @Suppress("UNUSED_PARAMETER")
    suspend fun pushFavorite(userId: String, eventId: String, isFavorite: Boolean) {
        val owner = ownerUid(userId) ?: return
        runCatching {
            val doc = userFavoritesCollection(owner).document(eventId)
            if (isFavorite) {
                doc.set(mapOf("eventId" to eventId, "createdAt" to System.currentTimeMillis())).await()
            } else {
                doc.delete().await()
            }
        }.onFailure { exception ->
            if (exception is CancellationException) throw exception
            AppLogger.w(tag, "Failed to push favourite $eventId: ${exception.message}")
        }
    }

    /** Mirrors an RSVP status change to Firestore. */
    @Suppress("UNUSED_PARAMETER")
    suspend fun pushRsvp(userId: String, eventId: String, status: String) {
        val owner = ownerUid(userId) ?: return
        runCatching {
            val doc = userRsvpsCollection(owner).document(eventId)
            if (status.isBlank() || status == "none") {
                doc.delete().await()
            } else {
                doc.set(mapOf("eventId" to eventId, "status" to status, "createdAt" to System.currentTimeMillis())).await()
            }
        }.onFailure { exception ->
            if (exception is CancellationException) throw exception
            AppLogger.w(tag, "Failed to push RSVP $eventId: ${exception.message}")
        }
    }

    // ------------------------------------------------------------------
    // Push (NoSQL store)
    // ------------------------------------------------------------------

    private suspend fun pushLocalChanges(userId: String, owner: String) {
        val events = eventDao.getUserCreatedEventsForUser(userId)
        events.forEach { event ->
            runCatching {
                userEventsCollection(owner).document(event.id).set(event.toFirestoreMap()).await()
            }.onFailure { exception ->
                if (exception is CancellationException) throw exception
                AppLogger.w(tag, "Event push failed for ${event.id}: ${exception.message}")
            }
        }

        val favorites = favoriteDao.observeAllForUser(userId).first()
        favorites.forEach { favorite ->
            runCatching {
                userFavoritesCollection(owner).document(favorite.eventId)
                    .set(mapOf("eventId" to favorite.eventId, "createdAt" to favorite.createdAt))
                    .await()
            }.onFailure { exception ->
                if (exception is CancellationException) throw exception
            }
        }

        val rsvps = rsvpDao.observeAllForUser(userId).first()
        rsvps.forEach { rsvp ->
            runCatching {
                userRsvpsCollection(owner).document(rsvp.eventId)
                    .set(mapOf("eventId" to rsvp.eventId, "status" to rsvp.status, "createdAt" to rsvp.createdAt))
                    .await()
            }.onFailure { exception ->
                if (exception is CancellationException) throw exception
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

    private suspend fun pullRemoteChanges(userId: String, owner: String) {
        val checkpoint = preferences.getSyncCheckpoint(userId)


        var lastStartDate: Long? = null
        var lastDocId: String? = null


        if (!checkpoint.isNullOrBlank()) {
            val separator = checkpoint.indexOf(':')
            if (separator > 0 && separator < checkpoint.lastIndex) {
                lastStartDate = checkpoint.substring(0, separator).toLongOrNull()
                lastDocId = checkpoint.substring(separator + 1)
            }


            // A corrupt checkpoint must not silently skip the event catalogue.
            if (lastStartDate == null || lastDocId.isNullOrBlank()) {
                AppLogger.w(tag, "Invalid event sync checkpoint for user $userId; restarting pagination")
                preferences.setSyncCheckpoint(userId, "")
                lastStartDate = null
                lastDocId = null
            }
        }


        var totalPulled = 0
        var hasMore = true


        while (hasMore) {
            val query = userEventsCollection(owner)
                .orderBy("startDate", com.google.firebase.firestore.Query.Direction.ASCENDING)
                .orderBy(
                    com.google.firebase.firestore.FieldPath.documentId(),
                    com.google.firebase.firestore.Query.Direction.ASCENDING
                )


            val page = withTimeoutOrNull(PULL_TIMEOUT_MS) {
                val pagedQuery = if (lastStartDate != null && lastDocId != null) {
                    query.startAfter(lastStartDate, lastDocId)
                } else {
                    query
                }


                pagedQuery
                    .limit(PULL_EVENT_LIMIT.toLong())
                    .get()
                    .await()
            }


            if (page == null) {
                AppLogger.w(tag, "Cloud event pull timed out; retrying on next sync")
                return
            }


            val documents = page.documents
            if (documents.isEmpty()) {
                hasMore = false
                continue
            }


            val mappedEvents = mutableListOf<EventEntity>()


            for (doc in documents) {
                val mapped = doc.data?.toEventEntity(doc.id)
                if (mapped != null) {
                    mappedEvents.add(mapped)
                } else {
                    AppLogger.w(tag, "Malformed event document skipped: ${doc.id}")
                }
            }


            // Save valid records before advancing the checkpoint.
            if (mappedEvents.isNotEmpty()) {
                eventDao.upsertAll(mappedEvents)
                totalPulled += mappedEvents.size
            }


            // Advance using the actual last document in the ordered page,
            // including when that document is malformed and intentionally skipped.
            val lastDocument = documents.last()
            val pageStartDate = lastDocument.getLong("startDate")


            if (pageStartDate == null) {
                // Without the ordering field we cannot create a safe cursor.
                // Stop rather than risk skipping later records.
                AppLogger.w(
                    tag,
                    "Last event document ${lastDocument.id} has no numeric startDate; stopping pagination"
                )
                return
            }


            lastStartDate = pageStartDate
            lastDocId = lastDocument.id
            preferences.setSyncCheckpoint(userId, "$lastStartDate:$lastDocId")


            hasMore = documents.size >= PULL_EVENT_LIMIT
        }


        AppLogger.i(tag, "Pulled $totalPulled event(s) from Firestore")


        // Pull favourites. This remains an additive merge; see the deletion note below.
        val remoteFavorites = withTimeoutOrNull(PULL_TIMEOUT_MS) {
            userFavoritesCollection(owner).get().await()
        }


        remoteFavorites?.documents?.forEach { doc ->
            val eventId = doc.getString("eventId") ?: doc.id
            val createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()


            if (!favoriteDao.exists(userId, eventId)) {
                favoriteDao.insert(
                    FavoriteEntity(
                        userId = userId,
                        eventId = eventId,
                        createdAt = createdAt
                    )
                )
            }
        }

        // Pull RSVPs from Firestore so this device receives remote RSVP records.
        val remoteRsvps = withTimeoutOrNull(PULL_TIMEOUT_MS) {
            userRsvpsCollection(owner).get().await()
        }

        if (remoteRsvps == null) {
            AppLogger.w(tag, "Remote RSVP pull timed out; will retry on next sync")
        } else {
            remoteRsvps.documents.forEach { doc ->
                val eventId = doc.getString("eventId") ?: doc.id
                val status = doc.getString("status") ?: return@forEach
                val createdAt = doc.getLong("createdAt")
                    ?: System.currentTimeMillis()

                if (rsvpDao.statusFor(userId, eventId) == null) {
                    rsvpDao.upsert(
                        RsvpEntity(
                            userId = userId,
                            eventId = eventId,
                            status = status,
                            createdAt = createdAt
                        )
                    )
                }
            }
        }
    }


    // ------------------------------------------------------------------
    // Collections
    // ------------------------------------------------------------------

    private fun userCollection(ownerId: String) =
        firestore?.collection(USERS_COLLECTION)?.document(ownerId)
            ?: throw IllegalStateException("Firestore unavailable")

    private fun userEventsCollection(ownerId: String) = userCollection(ownerId).collection(EVENTS_COLLECTION)
    private fun userFavoritesCollection(ownerId: String) = userCollection(ownerId).collection(FAVORITES_COLLECTION)
    private fun userRsvpsCollection(ownerId: String) = userCollection(ownerId).collection(RSVPS_COLLECTION)

    companion object {
        private const val USERS_COLLECTION = "users"
        private const val EVENTS_COLLECTION = "events"
        private const val FAVORITES_COLLECTION = "favorites"
        private const val RSVPS_COLLECTION = "rsvps"
        private const val PULL_TIMEOUT_MS = 15_000L
        private const val PULL_EVENT_LIMIT = 200
        private const val AUTH_TIMEOUT_MS = 10_000L
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
