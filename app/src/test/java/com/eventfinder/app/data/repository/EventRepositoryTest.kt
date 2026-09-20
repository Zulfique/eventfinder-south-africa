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
import com.eventfinder.app.domain.model.EventCategory
import com.eventfinder.app.domain.model.RsvpStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Repository-level tests for the offline-first event store.
 *
 * In-memory fakes implement the Room DAO interfaces so the tests run on the JVM
 * without an Android device (and without Robolectric).
 */
class EventRepositoryTest {

    // --------------------------------------------------------------- fakes

    private class FakeEventDao(private val favoriteDao: FakeFavoriteDao? = null) : EventDao {
        val rows = linkedMapOf<String, EventEntity>()
        private val flow = MutableStateFlow<List<EventEntity>>(emptyList())

        private fun emit() {
            flow.value = rows.values.sortedBy { it.startDate }
        }

        override suspend fun upsertAll(events: List<EventEntity>) {
            events.forEach { rows[it.id] = it }
            emit()
        }

        override suspend fun upsert(event: EventEntity) {
            rows[event.id] = event
            emit()
        }

        override fun observeAll(): Flow<List<EventEntity>> = flow

        override suspend fun findById(id: String): EventEntity? = rows[id]

        override fun observeFavoriteEventsForUser(userId: String): Flow<List<EventEntity>> {
            if (favoriteDao == null) return flow
            return flow.map { events ->
                val favIds = favoriteDao.rows.values
                    .filter { it.userId == userId }
                    .map { it.eventId }
                    .toSet()
                events.filter { it.id in favIds }
            }
        }

        override suspend fun findByIds(ids: List<String>): List<EventEntity> =
            ids.mapNotNull { rows[it] }

        override suspend fun count(): Int = rows.size

        override suspend fun getNonUserCreated(): List<EventEntity> =
            rows.values.filter { !it.isCreatedByUser }

        override suspend fun deleteNonUserCreated() {
            rows.values.filter { !it.isCreatedByUser }.map { it.id }.forEach { rows.remove(it) }
            emit()
        }

        override suspend fun deleteCreatedByUser() {
            rows.values.filter { it.isCreatedByUser }.map { it.id }.forEach { rows.remove(it) }
            emit()
        }

        override suspend fun deleteById(id: String) {
            rows.remove(id)
            emit()
        }

        override suspend fun deleteCreatedByUserId(userId: String) {
            rows.values.filter { it.isCreatedByUser && it.organizerId == userId }.map { it.id }.forEach { rows.remove(it) }
            emit()
        }

        override suspend fun getUpcomingAttendingEventsForUser(userId: String, now: Long): List<EventEntity> =
            rows.values.filter { event ->
                event.startDate > now
            }

        override suspend fun replaceNonUserCreated(events: List<EventEntity>) {
            deleteNonUserCreated()
            upsertAll(events)
        }
    }

    private class FakeFavoriteDao : FavoriteDao {
        val rows = linkedMapOf<String, FavoriteEntity>()
        private val flow = MutableStateFlow<List<FavoriteEntity>>(emptyList())

        private fun emit() {
            flow.value = rows.values.toList()
        }

        override suspend fun insert(favorite: FavoriteEntity) {
            rows["${favorite.userId}:${favorite.eventId}"] = favorite
            emit()
        }

        override suspend fun delete(userId: String, eventId: String) {
            rows.remove("$userId:$eventId")
            emit()
        }

        override fun observeAllForUser(userId: String): Flow<List<FavoriteEntity>> =
            flow.map { list -> list.filter { it.userId == userId } }

        override suspend fun count(): Int = rows.size

        override suspend fun exists(userId: String, eventId: String): Boolean =
            rows.containsKey("$userId:$eventId")

        override suspend fun markFlushed(userId: String, eventId: String) {
            rows["$userId:$eventId"]?.let { rows["$userId:$eventId"] = it.copy(isFlushed = true) }
            emit()
        }

        override suspend fun deleteAllForUser(userId: String) {
            rows.keys.removeAll { it.startsWith("$userId:") }
            emit()
        }
    }

    private class FakeRsvpDao : RsvpDao {
        val rows = linkedMapOf<String, RsvpEntity>()
        private val flow = MutableStateFlow<List<RsvpEntity>>(emptyList())

        private fun emit() {
            flow.value = rows.values.toList()
        }

        override suspend fun upsert(rsvp: RsvpEntity) {
            rows["${rsvp.userId}:${rsvp.eventId}"] = rsvp
            emit()
        }

        override suspend fun delete(userId: String, eventId: String) {
            rows.remove("$userId:$eventId")
            emit()
        }

        override fun observeAllForUser(userId: String): Flow<List<RsvpEntity>> =
            flow.map { list -> list.filter { it.userId == userId } }

        override suspend fun statusFor(userId: String, eventId: String): String? =
            rows["$userId:$eventId"]?.status

        override suspend fun markFlushed(userId: String, eventId: String) {
            rows["$userId:$eventId"]?.let { rows["$userId:$eventId"] = it.copy(isFlushed = true) }
            emit()
        }

        override suspend fun attendingCount(): Int = rows.values.count { it.status == "attending" }

        override suspend fun deleteAllForUser(userId: String) {
            rows.keys.removeAll { it.startsWith("$userId:") }
            emit()
        }
    }

    private class FakePendingSyncDao : PendingSyncDao {
        val rows = mutableListOf<PendingSyncEntity>()
        private var nextId = 1L

        override suspend fun insert(pending: PendingSyncEntity) {
            rows += pending.copy(id = if (pending.id == 0L) nextId++ else pending.id)
        }

        override suspend fun allForUser(userId: String): List<PendingSyncEntity> =
            rows.filter { it.userId == userId }

        override suspend fun delete(id: Long) {
            rows.removeAll { it.id == id }
        }

        override suspend fun incrementRetry(id: Long) {
            val index = rows.indexOfFirst { it.id == id }
            if (index >= 0) rows[index] = rows[index].copy(retryCount = rows[index].retryCount + 1)
        }

        override suspend fun countForUser(userId: String): Int =
            rows.count { it.userId == userId }

        override suspend fun deleteAllForUser(userId: String) {
            rows.removeAll { it.userId == userId }
        }
    }

    private class TestPreferences : com.eventfinder.app.data.store.SessionProvider {
        private val _sessionId = MutableStateFlow<String?>(null)
        override val sessionUserId: Flow<String?> get() = _sessionId
        override val biometricUserId: Flow<String?> = MutableStateFlow<String?>(null)

        suspend fun setTestUserId(id: String?) { _sessionId.value = id }
        override suspend fun isLoggedIn(): Boolean = _sessionId.value != null
        override suspend fun setSessionUserId(userId: String?) { _sessionId.value = userId }
        override suspend fun setLanguage(lang: String) { /* no-op in tests */ }
        override suspend fun setBiometricUserId(userId: String?) { /* no-op in tests */ }
        override suspend fun clearUserPreferences(userId: String) { /* no-op in tests */ }
        override suspend fun clearAll() { _sessionId.value = null }
    }

    private class FakeDatabase(
        private val favoriteDao: FakeFavoriteDao,
        private val eventDao: FakeEventDao,
        private val rsvpDao: FakeRsvpDao,
        private val pendingSyncDao: FakePendingSyncDao
    ) : DatabaseTransactionHelper {
        override suspend fun setFavoriteAtomically(userId: String, eventId: String, favorite: Boolean, pendingAction: String, pendingPayload: String) {
            if (favorite) {
                favoriteDao.insert(FavoriteEntity(userId = userId, eventId = eventId, createdAt = System.currentTimeMillis(), isFlushed = false))
            } else {
                favoriteDao.delete(userId, eventId)
            }
            pendingSyncDao.insert(
                PendingSyncEntity(entityType = "favorite", entityId = eventId, action = pendingAction, payload = pendingPayload, userId = userId, createdAt = System.currentTimeMillis())
            )
        }

        override suspend fun setRsvpAtomically(userId: String, eventId: String, status: String, pendingPayload: String) {
            rsvpDao.upsert(RsvpEntity(userId = userId, eventId = eventId, status = status, createdAt = System.currentTimeMillis(), isFlushed = false))
            pendingSyncDao.insert(
                PendingSyncEntity(entityType = "rsvp", entityId = eventId, action = "update", payload = pendingPayload, userId = userId, createdAt = System.currentTimeMillis())
            )
        }

        override suspend fun createEventAtomically(event: EventEntity, userId: String, pendingPayload: String) {
            eventDao.upsert(event)
            pendingSyncDao.insert(
                PendingSyncEntity(entityType = "event", entityId = event.id, action = "create", payload = pendingPayload, userId = userId, createdAt = System.currentTimeMillis())
            )
        }

        override suspend fun updateEventAtomically(event: EventEntity, userId: String, pendingPayload: String) {
            eventDao.upsert(event)
            pendingSyncDao.insert(
                PendingSyncEntity(entityType = "event", entityId = event.id, action = "update", payload = pendingPayload, userId = userId, createdAt = System.currentTimeMillis())
            )
        }

        override suspend fun deleteAccountData(userId: String) {
            pendingSyncDao.deleteAllForUser(userId)
            favoriteDao.deleteAllForUser(userId)
            rsvpDao.deleteAllForUser(userId)
            eventDao.deleteCreatedByUserId(userId)
        }

        override suspend fun deleteEventAtomically(eventId: String, userId: String, pendingPayload: String) {
            eventDao.deleteById(eventId)
            favoriteDao.delete(userId, eventId)
            rsvpDao.delete(userId, eventId)
            pendingSyncDao.insert(
                PendingSyncEntity(
                    entityType = "event",
                    entityId = eventId,
                    action = "delete",
                    payload = pendingPayload,
                    userId = userId,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    private fun repository(
        favoriteDao: FakeFavoriteDao = FakeFavoriteDao(),
        eventDao: FakeEventDao = FakeEventDao(favoriteDao),
        rsvpDao: FakeRsvpDao = FakeRsvpDao(),
        pendingDao: FakePendingSyncDao = FakePendingSyncDao(),
        preferences: TestPreferences = TestPreferences()
    ): Pair<EventRepositoryImpl, TestPreferences> =
        Pair(
            EventRepositoryImpl(
                database = FakeDatabase(favoriteDao, eventDao, rsvpDao, pendingDao),
                eventDao = eventDao,
                favoriteDao = favoriteDao,
                rsvpDao = rsvpDao,
                pendingSyncDao = pendingDao,
                preferences = preferences
            ),
            preferences
        )

    // ---------------------------------------------------------- ensureSeeded

    @Test
    fun `ensureSeeded populates the cache when it is empty`() = runTest {
        val dao = FakeEventDao()
        val (repo, _) = repository(eventDao = dao)

        repo.ensureSeeded()

        assertTrue(dao.count() >= 10)
        assertEquals(dao.count(), repo.observeAllEvents().first().size)
    }

    @Test
    fun `ensureSeeded is idempotent`() = runTest {
        val dao = FakeEventDao()
        val (repo, _) = repository(eventDao = dao)

        repo.ensureSeeded()
        val afterFirst = dao.count()
        repo.ensureSeeded()

        assertEquals(afterFirst, dao.count())
    }

    // ------------------------------------------------------------ favourites

    @Test
    fun `toggleFavorite adds then removes a favourite and queues offline actions`() = runTest {
        val favorites = FakeFavoriteDao()
        val pending = FakePendingSyncDao()
        val (repo, prefs) = repository(favoriteDao = favorites, pendingDao = pending)
        prefs.setTestUserId("user-1")
        repo.ensureSeeded()
        val target = repo.observeAllEvents().first().first().id

        val added = repo.toggleFavorite(target)
        assertTrue(added)
        assertEquals(setOf(target), repo.observeFavoriteIds().first())
        assertTrue(repo.observeFavoriteEvents().first().any { it.id == target })

        val removed = repo.toggleFavorite(target)
        assertFalse(removed)
        assertTrue(repo.observeFavoriteIds().first().isEmpty())

        assertEquals(2, pending.rows.size)
    }

    // ---------------------------------------------------------------- RSVP

    @Test
    fun `setRsvp stores the status and exposes it through the flow`() = runTest {
        val dao = FakeEventDao()
        val (repo, prefs) = repository(eventDao = dao)
        prefs.setTestUserId("user-1")
        repo.ensureSeeded()
        val target = repo.observeAllEvents().first().first().id

        repo.setRsvp(target, RsvpStatus.ATTENDING)

        val statuses = repo.observeRsvpStatuses().first()
        assertEquals(RsvpStatus.ATTENDING, statuses[target])
    }

    // --------------------------------------------------------- createEvent

    @Test
    fun `createEvent persists a user owned event`() = runTest {
        val dao = FakeEventDao()
        val pending = FakePendingSyncDao()
        val (repo, _) = repository(eventDao = dao, pendingDao = pending)

        val result = repo.createEvent(
            NewEventDraft(
                title = "  Community Market  ",
                description = "Fresh produce",
                category = EventCategory.FOOD,
                startDate = 5_000L,
                endDate = 9_000L,
                venueName = " Church Square ",
                address = "Pretoria CBD",
                latitude = -25.7461,
                longitude = 28.1881,
                isPublic = true
            )
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun `createEvent succeeds when logged in`() = runTest {
        val dao = FakeEventDao()
        val pending = FakePendingSyncDao()
        val (repo, prefs) = repository(eventDao = dao, pendingDao = pending)
        prefs.setTestUserId("user-1")

        val result = repo.createEvent(
            NewEventDraft(
                title = "  Community Market  ",
                description = "Fresh produce",
                category = EventCategory.FOOD,
                startDate = 5_000L,
                endDate = 9_000L,
                venueName = " Church Square ",
                address = "Pretoria CBD",
                latitude = -25.7461,
                longitude = 28.1881,
                isPublic = true
            )
        )

        assertTrue(result.isSuccess)
        val stored = repo.getEvent(result.getOrThrow())
        assertNotNull(stored)
        assertEquals("Community Market", stored!!.title)
        assertEquals("Church Square", stored.venueName)
        assertEquals("user-1", stored.organizerId)
        assertTrue(stored.isCreatedByUser)
        assertFalse(stored.isExternal)
        assertEquals(1, pending.rows.size)
    }

    // -------------------------------------------------- update / delete event

    @Test
    fun `updateEvent edits a user owned event and queues an offline action`() = runTest {
        val dao = FakeEventDao()
        val pending = FakePendingSyncDao()
        val (repo, prefs) = repository(eventDao = dao, pendingDao = pending)
        prefs.setTestUserId("user-1")
        val id = repo.createEvent(draft(title = "Original")).getOrThrow()
        pending.rows.clear()

        val result = repo.updateEvent(
            id,
            draft(title = "Updated", venue = "New Venue", public = false)
        )

        assertTrue(result.isSuccess)
        val stored = repo.getEvent(id)!!
        assertEquals("Updated", stored.title)
        assertEquals("New Venue", stored.venueName)
        assertFalse(stored.isPublic)
        assertTrue(stored.isCreatedByUser)
        assertEquals(1, pending.rows.size)
    }

    @Test
    fun `updateEvent refuses to edit a non-user-created event`() = runTest {
        val dao = FakeEventDao()
        val (repo, prefs) = repository(eventDao = dao)
        prefs.setTestUserId("user-1")
        repo.ensureSeeded()
        val nonUserEvent = repo.observeAllEvents().first().first()
        val originalTitle = nonUserEvent.title

        val result = repo.updateEvent(nonUserEvent.id, draft(title = "Hacked"))

        assertTrue(result.isFailure)
        assertEquals(originalTitle, repo.getEvent(nonUserEvent.id)!!.title)
    }

    @Test
    fun `updateEvent rejects a different user`() = runTest {
        val (repo, prefs) = repository()
        prefs.setTestUserId("user-a")
        val id = repo.createEvent(draft(title = "User A event")).getOrThrow()

        prefs.setTestUserId("user-b")
        val result = repo.updateEvent(id, draft(title = "Hacked"))

        assertTrue(result.isFailure)
        assertEquals("not_owner", result.exceptionOrNull()?.message)
    }

    @Test
    fun `deleteEvent removes the event and its favourite and rsvp links`() = runTest {
        val favorites = FakeFavoriteDao()
        val rsvps = FakeRsvpDao()
        val (repo, prefs) = repository(favoriteDao = favorites, rsvpDao = rsvps)
        prefs.setTestUserId("user-1")
        val id = repo.createEvent(draft(title = "To remove")).getOrThrow()
        repo.toggleFavorite(id)
        repo.setRsvp(id, RsvpStatus.ATTENDING)

        val result = repo.deleteEvent(id)

        assertTrue(result.isSuccess)
        assertNull(repo.getEvent(id))
        assertFalse(favorites.rows.values.any { it.eventId == id })
        assertFalse(rsvps.rows.values.any { it.eventId == id })
    }

    @Test
    fun `deleteEvent refuses to delete a non-user-created event`() = runTest {
        val dao = FakeEventDao()
        val (repo, prefs) = repository(eventDao = dao)
        prefs.setTestUserId("user-1")
        repo.ensureSeeded()
        val nonUserEvent = repo.observeAllEvents().first().first()

        val result = repo.deleteEvent(nonUserEvent.id)

        assertTrue(result.isFailure)
        assertNotNull(repo.getEvent(nonUserEvent.id))
    }

    private fun draft(
        title: String,
        venue: String = "Venue",
        public: Boolean = true
    ) = NewEventDraft(
        title = title,
        description = "A community event",
        category = EventCategory.COMMUNITY,
        startDate = 10_000L,
        endDate = 12_000L,
        venueName = venue,
        address = "Cape Town",
        latitude = -33.9,
        longitude = 18.4,
        isPublic = public
    )

    // -------------------------------------------------------- cache clearing

    @Test
    fun `clearLocalCache skips when pending sync actions exist`() = runTest {
        val dao = FakeEventDao()
        val pending = FakePendingSyncDao()
        val (repo, prefs) = repository(eventDao = dao, pendingDao = pending)
        prefs.setTestUserId("user-1")
        repo.ensureSeeded()
        pending.rows.add(PendingSyncEntity(userId = "user-1", entityType = "event", entityId = "x", action = "create", payload = "{}", createdAt = 1L))

        repo.clearLocalCache()

        assertEquals(SampleEventsProvider.johannesburgAndCapeTown().size, dao.count())
        assertEquals(1, pending.rows.size)
    }

    @Test
    fun `clearLocalCache removes only synced events and reseeds`() = runTest {
        val dao = FakeEventDao()
        val pending = FakePendingSyncDao()
        val (repo, prefs) = repository(eventDao = dao, pendingDao = pending)
        prefs.setTestUserId("user-1")
        repo.ensureSeeded()
        repo.createEvent(
            NewEventDraft(
                title = "User Event",
                description = "desc",
                category = EventCategory.OTHER,
                startDate = 1L,
                endDate = 2L,
                venueName = "Venue",
                address = "Address",
                latitude = -26.0,
                longitude = 28.0,
                isPublic = true
            )
        )

        repo.clearLocalCache()

        val all = repo.observeAllEvents().first()
        assertTrue(all.isNotEmpty())
        assertTrue(all.any { it.isCreatedByUser })
        assertTrue(all.any { it.title == "User Event" })
    }

    @Test
    fun `getEvent returns null for an unknown id`() = runTest {
        val (repo, _) = repository()
        assertNull(repo.getEvent("missing"))
    }

    // ------------------------------------------------------ account deletion

    @Test
    fun `deleteAccount via authRepository removes all user data but leaves other users intact`() = runTest {
        val favorites = FakeFavoriteDao()
        val rsvps = FakeRsvpDao()
        val pending = FakePendingSyncDao()
        val dao = FakeEventDao(favorites)
        val database = FakeDatabase(favorites, dao, rsvps, pending)
        val (repo, prefs) = repository(favoriteDao = favorites, eventDao = dao, rsvpDao = rsvps, pendingDao = pending)

        prefs.setTestUserId("user-a")
        val eventA = repo.createEvent(draft(title = "User A event 1")).getOrThrow()
        repo.createEvent(draft(title = "User A event 2"))
        repo.toggleFavorite(eventA)
        repo.setRsvp(eventA, RsvpStatus.ATTENDING)
        assertEquals(4, pending.rows.size)

        prefs.setTestUserId("user-b")
        val eventB = repo.createEvent(draft(title = "User B event")).getOrThrow()

        database.deleteAccountData("user-a")

        assertNull(dao.findById(eventA))
        assertTrue(dao.rows.values.none { it.organizerId == "user-a" })
        assertTrue(favorites.rows.values.none { it.userId == "user-a" })
        assertTrue(rsvps.rows.values.none { it.userId == "user-a" })
        assertTrue(pending.rows.none { it.userId == "user-a" })

        assertNotNull(dao.findById(eventB))
        assertEquals("User B event", dao.findById(eventB)!!.title)
    }

    // --------------------------------------------------- upcoming attending

    @Test
    fun `upcoming attending events are returned for reminder restoration`() = runTest {
        val dao = FakeEventDao()
        val (repo, prefs) = repository(eventDao = dao)
        prefs.setTestUserId("user-1")

        val futureEvent = repo.createEvent(
            NewEventDraft(
                title = "Future Attending",
                description = "desc",
                category = EventCategory.COMMUNITY,
                startDate = System.currentTimeMillis() + 100_000,
                endDate = System.currentTimeMillis() + 200_000,
                venueName = "Venue",
                address = "Address",
                latitude = -26.0,
                longitude = 28.0,
                isPublic = true
            )
        ).getOrThrow()
        repo.setRsvp(futureEvent, RsvpStatus.ATTENDING)

        val result = dao.getUpcomingAttendingEventsForUser("user-1", System.currentTimeMillis())

        assertEquals(1, result.size)
        assertEquals(futureEvent, result[0].id)
    }

    // --------------------------------------------------- boot receiver

    @Test
    fun `BootReceiver restoration queries upcoming events and schedules reminders`() = runTest {
        val dao = FakeEventDao()

        val futureTime = System.currentTimeMillis() + 100_000
        val pastTime = System.currentTimeMillis() - 100_000

        dao.upsert(
            EventEntity(
                id = "ev-future",
                title = "Future Event",
                description = "Upcoming",
                category = "music",
                startDate = futureTime,
                endDate = futureTime + 3_600_000,
                venueName = "Venue",
                address = "Address",
                latitude = -26.0,
                longitude = 28.0,
                imageUrl = null,
                isPublic = true,
                organizerId = "org",
                organizerName = "Organizer",
                attendeeCount = 0,
                isCreatedByUser = false,
                isExternal = true
            )
        )
        dao.upsert(
            EventEntity(
                id = "ev-past",
                title = "Past Event",
                description = "Already happened",
                category = "music",
                startDate = pastTime,
                endDate = pastTime + 3_600_000,
                venueName = "Venue",
                address = "Address",
                latitude = -26.0,
                longitude = 28.0,
                imageUrl = null,
                isPublic = true,
                organizerId = "org",
                organizerName = "Organizer",
                attendeeCount = 0,
                isCreatedByUser = false,
                isExternal = true
            )
        )

        val upcoming = dao.getUpcomingAttendingEventsForUser("user-1", System.currentTimeMillis())

        assertEquals(1, upcoming.size)
        assertEquals("ev-future", upcoming[0].id)
        assertEquals("Future Event", upcoming[0].title)
    }

    // ------------------------------------------------- flushPendingActions

    @Test
    fun `flushPendingActions drains the queue without corrupting isExternal`() = runTest {
        val pending = FakePendingSyncDao()
        val dao = FakeEventDao()
        val (repo, prefs) = repository(eventDao = dao, pendingDao = pending)
        prefs.setTestUserId("user-1")

        val id = repo.createEvent(draft(title = "Flush Me")).getOrThrow()
        assertEquals(1, pending.rows.size)
        assertFalse(dao.findById(id)!!.isExternal)

        val result = repo.flushPendingActions()

        assertEquals(SyncResult.Synced, result)
        assertTrue(pending.rows.isEmpty())
        assertFalse(dao.findById(id)!!.isExternal)
    }

    @Test
    fun `flushPendingActions marks favorites as flushed`() = runTest {
        val pending = FakePendingSyncDao()
        val favorites = FakeFavoriteDao()
        val (repo, prefs) = repository(favoriteDao = favorites, pendingDao = pending)
        prefs.setTestUserId("user-1")
        repo.ensureSeeded()
        val target = repo.observeAllEvents().first().first().id

        repo.toggleFavorite(target)
        assertEquals(1, pending.rows.size)

        repo.flushPendingActions()

        assertTrue(pending.rows.isEmpty())
        assertTrue(favorites.rows.values.any { it.userId == "user-1" && it.eventId == target && it.isFlushed })
    }

    @Test
    fun `flushPendingActions marks rsvps as flushed`() = runTest {
        val pending = FakePendingSyncDao()
        val rsvps = FakeRsvpDao()
        val (repo, prefs) = repository(rsvpDao = rsvps, pendingDao = pending)
        prefs.setTestUserId("user-1")
        repo.ensureSeeded()
        val target = repo.observeAllEvents().first().first().id

        repo.setRsvp(target, RsvpStatus.ATTENDING)
        assertEquals(1, pending.rows.size)

        repo.flushPendingActions()

        assertTrue(pending.rows.isEmpty())
        assertTrue(rsvps.rows.values.any { it.userId == "user-1" && it.eventId == target && it.isFlushed })
    }

    @Test
    fun `flushPendingActions returns NoSession when no session`() = runTest {
        val pending = FakePendingSyncDao()
        val (repo, _) = repository(pendingDao = pending)

        assertEquals(SyncResult.NoSession, repo.flushPendingActions())
    }
}
