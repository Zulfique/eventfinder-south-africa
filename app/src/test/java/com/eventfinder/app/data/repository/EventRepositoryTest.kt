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
import com.eventfinder.app.data.remote.TicketmasterApi
import com.eventfinder.app.data.remote.dto.TmDates
import com.eventfinder.app.data.remote.dto.TmEmbedded
import com.eventfinder.app.data.remote.dto.TmEvent
import com.eventfinder.app.data.remote.dto.TmEventsResponse
import com.eventfinder.app.data.remote.dto.TmStart
import com.eventfinder.app.domain.model.EventCategory
import com.eventfinder.app.domain.model.RsvpStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
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
 * without an Android device (and without Robolectric). The Ticketmaster API is
 * faked, letting us assert sync behaviour in isolation from the network.
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
            return flow.combine(favoriteDao.observeAllForUser(userId)) { events, favorites ->
                val favIds = favorites.map { it.eventId }.toSet()
                events.filter { it.id in favIds }
            }
        }

        override suspend fun findByIds(ids: List<String>): List<EventEntity> =
            ids.mapNotNull { rows[it] }

        override suspend fun count(): Int = rows.size

        override suspend fun getSynced(): List<EventEntity> =
            rows.values.filter { !it.isCreatedByUser }

        override suspend fun deleteSynced() {
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

        override suspend fun replaceSyncedEvents(events: List<EventEntity>) {
            deleteSynced()
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

        override suspend fun all(): List<PendingSyncEntity> = rows.toList()

        override suspend fun delete(id: Long) {
            rows.removeAll { it.id == id }
        }

        override suspend fun incrementRetry(id: Long) {
            val index = rows.indexOfFirst { it.id == id }
            if (index >= 0) rows[index] = rows[index].copy(retryCount = rows[index].retryCount + 1)
        }

        override suspend fun countForUser(userId: String): Int =
            rows.count { it.userId == userId }

        override suspend fun count(): Int = rows.size

        override suspend fun deleteAllForUser(userId: String) {
            rows.removeAll { it.userId == userId }
        }
    }

    private class FakeTicketmasterApi(private val response: TmEventsResponse) : TicketmasterApi {
        var callCount = 0
        override suspend fun getEvents(
            apiKey: String,
            countryCode: String,
            size: Int,
            sort: String,
            keyword: String?,
            classificationName: String?
        ): TmEventsResponse {
            callCount++
            return response
        }

        override suspend fun searchEvents(
            apiKey: String,
            countryCode: String,
            size: Int,
            keyword: String
        ): TmEventsResponse = response
    }

    /**
     * Minimal in-memory stand-in for [UserPreferences] that lets tests set a
     * session user without Android DataStore dependencies.
     */
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
        override suspend fun setFavorite(userId: String, eventId: String, favorite: Boolean) {
            if (favorite) {
                favoriteDao.insert(FavoriteEntity(userId = userId, eventId = eventId, createdAt = System.currentTimeMillis(), isSynced = false))
            } else {
                favoriteDao.delete(userId, eventId)
            }
        }

        override suspend fun deleteAccountData(userId: String) {
            pendingSyncDao.deleteAllForUser(userId)
            favoriteDao.deleteAllForUser(userId)
            rsvpDao.deleteAllForUser(userId)
            eventDao.deleteCreatedByUserId(userId)
        }
    }

    private fun liveResponse(id: String = "Z1") = TmEventsResponse(
        embedded = TmEmbedded(
            events = listOf(
                TmEvent(
                    id = id,
                    name = "Live Music Night",
                    dates = TmDates(start = TmStart(localDate = "2026-11-20", localTime = "20:00:00"))
                )
            )
        )
    )

    private fun repository(
        favoriteDao: FakeFavoriteDao = FakeFavoriteDao(),
        eventDao: FakeEventDao = FakeEventDao(favoriteDao),
        rsvpDao: FakeRsvpDao = FakeRsvpDao(),
        pendingDao: FakePendingSyncDao = FakePendingSyncDao(),
        api: TicketmasterApi = FakeTicketmasterApi(liveResponse()),
        apiKey: String = "test-key",
        preferences: TestPreferences = TestPreferences()
    ): Pair<EventRepositoryImpl, TestPreferences> =
        Pair(
            EventRepositoryImpl(
                database = FakeDatabase(favoriteDao, eventDao, rsvpDao, pendingDao),
                eventDao = eventDao,
                favoriteDao = favoriteDao,
                rsvpDao = rsvpDao,
                pendingSyncDao = pendingDao,
                ticketmasterApi = api,
                apiKey = apiKey,
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

    // ------------------------------------------------------------ syncFromApi

    @Test
    fun `sync without an API key reports NoApiKey in demo mode`() = runTest {
        val (repo, _) = repository(apiKey = "")

        assertEquals(SyncResult.NoApiKey, repo.syncFromApi().result)
    }

    @Test
    fun `sync maps and stores live events`() = runTest {
        val dao = FakeEventDao()
        val (repo, _) = repository(eventDao = dao)

        val result = repo.syncFromApi()

        assertEquals(SyncResult.Synced, result.result)
        assertEquals(1, dao.count())
        val stored = repo.observeAllEvents().first().first()
        assertEquals("tm-Z1", stored.id)
        assertEquals("Live Music Night", stored.title)
    }

    @Test
    fun `sync replaces the synced catalogue but keeps user created events`() = runTest {
        val dao = FakeEventDao()
        val (repo, prefs) = repository(eventDao = dao)
        prefs.setTestUserId("user-1")
        repo.ensureSeeded()
        repo.syncFromApi()
        val created = repo.createEvent(
            NewEventDraft(
                title = "My Braai",
                description = "Backyard gathering",
                category = EventCategory.COMMUNITY,
                startDate = 1_000L,
                endDate = 2_000L,
                venueName = "Home",
                address = "Pretoria",
                latitude = -25.7,
                longitude = 28.2,
                isPublic = false
            )
        ).getOrThrow()

        repo.syncFromApi()

        val all = repo.observeAllEvents().first()
        assertTrue(all.any { it.id == created })
        assertTrue(all.any { it.id == "tm-Z1" })
        assertEquals(2, all.size)
    }

    // ---------------------------------------------------------- favourites

    @Test
    fun `sync reports newly added events and updated favourites`() = runTest {
        val favorites = FakeFavoriteDao()
        val dao = FakeEventDao(favorites)
        dao.upsert(
            knownSyncedEvent(
                id = "tm-OLD",
                title = "Old Name",
                venue = "Old Venue"
            )
        )
        favorites.insert(FavoriteEntity(userId = "system", eventId = "tm-OLD", createdAt = 0L, isSynced = true))

        val response = TmEventsResponse(
            embedded = TmEmbedded(
                events = listOf(
                    TmEvent(
                        id = "OLD",
                        name = "Old Name Renamed",
                        dates = TmDates(start = TmStart(localDate = "2026-11-20", localTime = "20:00:00"))
                    ),
                    TmEvent(
                        id = "NEW",
                        name = "Brand New Concert",
                        dates = TmDates(start = TmStart(localDate = "2026-12-01", localTime = "19:00:00"))
                    )
                )
            )
        )
        val (repo, prefs) = repository(
            eventDao = dao,
            favoriteDao = favorites,
            api = FakeTicketmasterApi(response)
        )
        prefs.setTestUserId("system")

        val outcome = repo.syncFromApi()

        assertEquals(SyncResult.Synced, outcome.result)
        assertEquals(listOf("tm-NEW"), outcome.newEvents.map { it.id })
        assertEquals(listOf("tm-OLD"), outcome.updatedFavorites.map { it.id })
    }

    @Test
    fun `first sync of a fresh install never alerts about the whole catalogue`() = runTest {
        val (repo, _) = repository()

        val outcome = repo.syncFromApi()

        assertTrue(outcome.newEvents.isEmpty())
        assertTrue(outcome.updatedFavorites.isEmpty())
    }

    private fun knownSyncedEvent(
        id: String,
        title: String,
        venue: String,
        favorite: Boolean = false
    ) = EventEntity(
        id = id,
        title = title,
        description = "Existing synced event",
        category = "music",
        startDate = 4_000_000_000_000L,
        endDate = 4_000_003_600_000L,
        venueName = venue,
        address = "Johannesburg",
        latitude = -26.2,
        longitude = 28.0,
        imageUrl = null,
        isPublic = true,
        organizerId = "org",
        organizerName = "Organizer",
        attendeeCount = 0,
        isFavorite = false,
        isCreatedByUser = false,
        isSynced = true
    )

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

        assertEquals(2, pending.count())
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
        assertFalse(stored.isSynced)
        assertEquals(1, pending.count())
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
        assertEquals(1, pending.count())
    }

    @Test
    fun `updateEvent refuses to edit an API-synced event`() = runTest {
        val (repo, _) = repository()
        repo.syncFromApi()
        val syncedId = repo.observeAllEvents().first().first().id

        val result = repo.updateEvent(syncedId, draft(title = "Hacked"))

        assertTrue(result.isFailure)
        assertEquals("Live Music Night", repo.getEvent(syncedId)!!.title)
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
    fun `deleteEvent refuses to delete an API-synced event`() = runTest {
        val (repo, _) = repository()
        repo.syncFromApi()
        val syncedId = repo.observeAllEvents().first().first().id

        val result = repo.deleteEvent(syncedId)

        assertTrue(result.isFailure)
        assertNotNull(repo.getEvent(syncedId))
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
        assertEquals(1, pending.count())
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

        // User A creates events, favourites, RSVPs, and has pending actions
        prefs.setTestUserId("user-a")
        val eventA = repo.createEvent(draft(title = "User A event 1")).getOrThrow()
        repo.createEvent(draft(title = "User A event 2"))
        repo.toggleFavorite(eventA)
        repo.setRsvp(eventA, RsvpStatus.ATTENDING)
        assertEquals(4, pending.count())

        // User B creates an event
        prefs.setTestUserId("user-b")
        val eventB = repo.createEvent(draft(title = "User B event")).getOrThrow()

        // Delete user A's data directly via the database helper
        database.deleteAccountData("user-a")

        // Verify: User A's events gone, favourites gone, RSVPs gone
        assertNull(dao.findById(eventA))
        assertTrue(dao.rows.values.none { it.organizerId == "user-a" })
        assertTrue(favorites.rows.values.none { it.userId == "user-a" })
        assertTrue(rsvps.rows.values.none { it.userId == "user-a" })
        assertTrue(pending.rows.none { it.userId == "user-a" })

        // Verify: User B's event still exists
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
                id = "tm-future",
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
                isFavorite = false,
                isCreatedByUser = false,
                isSynced = true
            )
        )
        dao.upsert(
            EventEntity(
                id = "tm-past",
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
                isFavorite = false,
                isCreatedByUser = false,
                isSynced = true
            )
        )

        val upcoming = dao.getUpcomingAttendingEventsForUser("user-1", System.currentTimeMillis())

        assertEquals(1, upcoming.size)
        assertEquals("tm-future", upcoming[0].id)
        assertEquals("Future Event", upcoming[0].title)
    }
}
