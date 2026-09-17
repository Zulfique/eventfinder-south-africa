package com.eventfinder.app.data.repository

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

    private class FakeEventDao : EventDao {
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

        override fun observeFavorites(): Flow<List<EventEntity>> =
            flow.map { list -> list.filter { it.isFavorite } }

        override suspend fun setFavorite(eventId: String, isFavorite: Boolean) {
            rows[eventId]?.let { rows[eventId] = it.copy(isFavorite = isFavorite) }
            emit()
        }

        override suspend fun findByIds(ids: List<String>): List<EventEntity> =
            ids.mapNotNull { rows[it] }

        override suspend fun count(): Int = rows.size

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
    }

    private class FakeFavoriteDao : FavoriteDao {
        val rows = linkedMapOf<String, FavoriteEntity>()
        private val flow = MutableStateFlow<List<FavoriteEntity>>(emptyList())

        private fun emit() {
            flow.value = rows.values.toList()
        }

        override suspend fun insert(favorite: FavoriteEntity) {
            rows[favorite.eventId] = favorite
            emit()
        }

        override suspend fun delete(eventId: String) {
            rows.remove(eventId)
            emit()
        }

        override fun observeAll(): Flow<List<FavoriteEntity>> = flow

        override suspend fun count(): Int = rows.size

        override suspend fun exists(eventId: String): Boolean? = rows.containsKey(eventId).takeIf { it }
    }

    private class FakeRsvpDao : RsvpDao {
        val rows = linkedMapOf<String, RsvpEntity>()
        private val flow = MutableStateFlow<List<RsvpEntity>>(emptyList())

        private fun emit() {
            flow.value = rows.values.toList()
        }

        override suspend fun upsert(rsvp: RsvpEntity) {
            rows[rsvp.eventId] = rsvp
            emit()
        }

        override suspend fun delete(eventId: String) {
            rows.remove(eventId)
            emit()
        }

        override fun observeAll(): Flow<List<RsvpEntity>> = flow

        override suspend fun statusFor(eventId: String): String? = rows[eventId]?.status

        override suspend fun attendingCount(): Int = rows.values.count { it.status == "attending" }
    }

    private class FakePendingSyncDao : PendingSyncDao {
        val rows = mutableListOf<PendingSyncEntity>()
        private var nextId = 1L

        override suspend fun insert(pending: PendingSyncEntity) {
            rows += pending.copy(id = if (pending.id == 0L) nextId++ else pending.id)
        }

        override suspend fun all(): List<PendingSyncEntity> = rows.toList()

        override suspend fun delete(id: Long) {
            rows.removeAll { it.id == id }
        }

        override suspend fun incrementRetry(id: Long) {
            val index = rows.indexOfFirst { it.id == id }
            if (index >= 0) rows[index] = rows[index].copy(retryCount = rows[index].retryCount + 1)
        }

        override suspend fun count(): Int = rows.size
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
        eventDao: FakeEventDao = FakeEventDao(),
        favoriteDao: FakeFavoriteDao = FakeFavoriteDao(),
        rsvpDao: FakeRsvpDao = FakeRsvpDao(),
        pendingDao: FakePendingSyncDao = FakePendingSyncDao(),
        api: TicketmasterApi = FakeTicketmasterApi(liveResponse()),
        apiKey: String = "test-key"
    ) = EventRepositoryImpl(eventDao, favoriteDao, rsvpDao, pendingDao, api, apiKey)

    // ---------------------------------------------------------- ensureSeeded

    @Test
    fun `ensureSeeded populates the cache when it is empty`() = runTest {
        val dao = FakeEventDao()
        val repo = repository(eventDao = dao)

        repo.ensureSeeded()

        assertTrue(dao.count() >= 10)
        assertEquals(dao.count(), repo.observeAllEvents().first().size)
    }

    @Test
    fun `ensureSeeded is idempotent`() = runTest {
        val dao = FakeEventDao()
        val repo = repository(eventDao = dao)

        repo.ensureSeeded()
        val afterFirst = dao.count()
        repo.ensureSeeded()

        assertEquals(afterFirst, dao.count())
    }

    // ------------------------------------------------------------ syncFromApi

    @Test
    fun `sync without an API key reports NoApiKey in demo mode`() = runTest {
        val repo = repository(apiKey = "")

        assertEquals(SyncResult.NoApiKey, repo.syncFromApi())
    }

    @Test
    fun `sync maps and stores live events`() = runTest {
        val dao = FakeEventDao()
        val repo = repository(eventDao = dao)

        val result = repo.syncFromApi()

        assertEquals(SyncResult.Synced, result)
        assertEquals(1, dao.count())
        val stored = repo.observeAllEvents().first().first()
        assertEquals("tm-Z1", stored.id)
        assertEquals("Live Music Night", stored.title)
    }

    @Test
    fun `sync replaces the synced catalogue but keeps user created events`() = runTest {
        val dao = FakeEventDao()
        val repo = repository(eventDao = dao)
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
    fun `toggleFavorite adds then removes a favourite and queues offline actions`() = runTest {
        val favorites = FakeFavoriteDao()
        val pending = FakePendingSyncDao()
        val dao = FakeEventDao()
        val repo = repository(eventDao = dao, favoriteDao = favorites, pendingDao = pending)
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
        val repo = repository(eventDao = dao)
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
        val repo = repository(eventDao = dao, pendingDao = pending)

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
        assertTrue(stored.isCreatedByUser)
        assertFalse(stored.isSynced)
        assertEquals(1, pending.count())
    }

    // -------------------------------------------------- update / delete event

    @Test
    fun `updateEvent edits a user owned event and queues an offline action`() = runTest {
        val dao = FakeEventDao()
        val pending = FakePendingSyncDao()
        val repo = repository(eventDao = dao, pendingDao = pending)
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
        val repo = repository()
        repo.syncFromApi()
        val syncedId = repo.observeAllEvents().first().first().id

        val result = repo.updateEvent(syncedId, draft(title = "Hacked"))

        assertTrue(result.isFailure)
        assertEquals("Live Music Night", repo.getEvent(syncedId)!!.title)
    }

    @Test
    fun `deleteEvent removes the event and its favourite and rsvp links`() = runTest {
        val favorites = FakeFavoriteDao()
        val rsvps = FakeRsvpDao()
        val repo = repository(favoriteDao = favorites, rsvpDao = rsvps)
        val id = repo.createEvent(draft(title = "To remove")).getOrThrow()
        repo.toggleFavorite(id)
        repo.setRsvp(id, RsvpStatus.ATTENDING)

        val result = repo.deleteEvent(id)

        assertTrue(result.isSuccess)
        assertNull(repo.getEvent(id))
        assertFalse(favorites.rows.containsKey(id))
        assertFalse(rsvps.rows.containsKey(id))
    }

    @Test
    fun `deleteEvent refuses to delete an API-synced event`() = runTest {
        val repo = repository()
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
    fun `clearLocalCache removes cached and user events then reseeds samples`() = runTest {
        val dao = FakeEventDao()
        val repo = repository(eventDao = dao)
        repo.ensureSeeded()
        repo.createEvent(
            NewEventDraft(
                title = "Temp",
                description = "Temp",
                category = EventCategory.OTHER,
                startDate = 1L,
                endDate = 2L,
                venueName = "Temp",
                address = "Temp",
                latitude = -26.0,
                longitude = 28.0,
                isPublic = true
            )
        )

        repo.clearLocalCache()

        val all = repo.observeAllEvents().first()
        assertTrue(all.isNotEmpty())
        assertTrue(all.none { it.isCreatedByUser })
        assertTrue(all.all { it.title != "Temp" })
    }

    @Test
    fun `getEvent returns null for an unknown id`() = runTest {
        assertNull(repository().getEvent("missing"))
    }
}
