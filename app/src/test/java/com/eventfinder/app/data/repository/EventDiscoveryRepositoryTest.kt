package com.eventfinder.app.data.repository

import com.eventfinder.app.data.local.EventDao
import com.eventfinder.app.data.local.EventEntity
import com.eventfinder.app.data.remote.model.RemoteEvent
import com.eventfinder.app.data.sources.EventSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EventDiscoveryRepositoryTest {

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

        override fun observeFavoriteEventsForUser(userId: String): Flow<List<EventEntity>> =
            flow.map { list -> list.filter { it.organizerId == userId } }

        override suspend fun findByIds(ids: List<String>): List<EventEntity> =
            ids.mapNotNull { rows[it] }

        override suspend fun count(): Int = rows.size

        override suspend fun getNonUserCreated(): List<EventEntity> =
            rows.values.filter { !it.isCreatedByUser }

        override suspend fun deleteNonUserCreated() {
            rows.values.filter { !it.isCreatedByUser }.forEach { rows.remove(it.id) }
            emit()
        }

        override suspend fun deleteCreatedByUser() {
            rows.values.filter { it.isCreatedByUser }.forEach { rows.remove(it.id) }
            emit()
        }

        override suspend fun deleteById(id: String) {
            rows.remove(id)
            emit()
        }

        override suspend fun deleteCreatedByUserId(userId: String) {
            rows.values.filter { it.isCreatedByUser && it.organizerId == userId }
                .forEach { rows.remove(it.id) }
            emit()
        }

        override suspend fun getUpcomingAttendingEventsForUser(userId: String, now: Long): List<EventEntity> =
            rows.values.filter { it.startDate > now }

        override suspend fun replaceNonUserCreated(events: List<EventEntity>) {
            deleteNonUserCreated()
            upsertAll(events)
        }
    }

    private class FakeEventSource(
        override val id: String = "fake-source",
        override val displayName: String = "Fake Source",
        private val eventsToReturn: List<RemoteEvent> = emptyList(),
        private val exceptionToThrow: Exception? = null
    ) : EventSource {
        override suspend fun fetchEvents(): List<RemoteEvent> {
            exceptionToThrow?.let { throw it }
            return eventsToReturn
        }
    }

    private fun futureEvent(
        source: String = "fake-source",
        sourceId: String = "evt-1",
        title: String = "Test Event",
        latitude: Double? = -33.9249,
        longitude: Double? = 18.4241
    ): RemoteEvent {
        val now = System.currentTimeMillis()
        return RemoteEvent(
            source = source,
            sourceId = sourceId,
            title = title,
            description = "A test event",
            category = "MUSIC",
            startDate = now + 24 * 60 * 60 * 1000L,
            endDate = now + 48 * 60 * 60 * 1000L,
            venueName = "Test Venue",
            address = "123 Test St",
            latitude = latitude,
            longitude = longitude,
            imageUrl = null,
            sourceUrl = null,
            organizerName = null
        )
    }

    @Test
    fun `inserts valid future events with coordinates`() = runTest {
        val dao = FakeEventDao()
        val event = futureEvent()
        val source = FakeEventSource(eventsToReturn = listOf(event))
        val repo = EventDiscoveryRepository(dao, listOf(source))

        val result = repo.refresh()

        assertEquals(1, result.fetched)
        assertEquals(1, result.inserted)
        assertEquals(0, result.failedSources)
        assertEquals(1, dao.rows.size)
        assertTrue(dao.rows.containsKey("remote:fake-source:evt-1"))
    }

    @Test
    fun `rejects events without valid coordinates`() = runTest {
        val dao = FakeEventDao()
        val event = futureEvent(latitude = null, longitude = null)
        val source = FakeEventSource(eventsToReturn = listOf(event))
        val repo = EventDiscoveryRepository(dao, listOf(source))

        val result = repo.refresh()

        assertEquals(1, result.fetched)
        assertEquals(0, result.inserted)
        assertTrue(dao.rows.isEmpty())
    }

    @Test
    fun `rejects past events`() = runTest {
        val dao = FakeEventDao()
        val now = System.currentTimeMillis()
        val pastEvent = RemoteEvent(
            source = "fake-source",
            sourceId = "past-1",
            title = "Past Event",
            description = "Already happened",
            category = "MUSIC",
            startDate = now - 48 * 60 * 60 * 1000L,
            endDate = now - 24 * 60 * 60 * 1000L,
            venueName = "Past Venue",
            address = "123 Past St",
            latitude = -33.9249,
            longitude = 18.4241,
            imageUrl = null,
            sourceUrl = null,
            organizerName = null
        )
        val source = FakeEventSource(eventsToReturn = listOf(pastEvent))
        val repo = EventDiscoveryRepository(dao, listOf(source))

        val result = repo.refresh()

        assertEquals(1, result.fetched)
        assertEquals(0, result.inserted)
        assertTrue(dao.rows.isEmpty())
    }

    @Test
    fun `handles source failure gracefully`() = runTest {
        val dao = FakeEventDao()
        val source = FakeEventSource(exceptionToThrow = RuntimeException("network error"))
        val repo = EventDiscoveryRepository(dao, listOf(source))

        val result = repo.refresh()

        assertEquals(0, result.fetched)
        assertEquals(0, result.inserted)
        assertEquals(1, result.failedSources)
        assertTrue(dao.rows.isEmpty())
    }

    @Test
    fun `deduplicates events by stableId`() = runTest {
        val dao = FakeEventDao()
        val event1 = futureEvent(sourceId = "dup-1")
        val event2 = futureEvent(sourceId = "dup-1")
        val source = FakeEventSource(eventsToReturn = listOf(event1, event2))
        val repo = EventDiscoveryRepository(dao, listOf(source))

        val result = repo.refresh()

        assertEquals(2, result.fetched)
        assertEquals(1, result.inserted)
        assertEquals(1, dao.rows.size)
    }

    @Test
    fun `fetches from multiple sources`() = runTest {
        val dao = FakeEventDao()
        val event1 = futureEvent(sourceId = "evt-a", title = "Event A")
        val event2 = futureEvent(sourceId = "evt-b", title = "Event B")
        val source1 = FakeEventSource(id = "src-1", displayName = "Source 1", eventsToReturn = listOf(event1))
        val source2 = FakeEventSource(id = "src-2", displayName = "Source 2", eventsToReturn = listOf(event2))
        val repo = EventDiscoveryRepository(dao, listOf(source1, source2))

        val result = repo.refresh()

        assertEquals(2, result.fetched)
        assertEquals(2, result.inserted)
        assertEquals(0, result.failedSources)
        assertEquals(2, dao.rows.size)
    }

    @Test
    fun `events from different sources do not collide`() = runTest {
        val dao = FakeEventDao()
        val event1 = futureEvent(source = "src-a", sourceId = "123", title = "Source A event")
        val event2 = futureEvent(source = "src-b", sourceId = "123", title = "Source B event")
        val source1 = FakeEventSource(id = "src-a", displayName = "Source A", eventsToReturn = listOf(event1))
        val source2 = FakeEventSource(id = "src-b", displayName = "Source B", eventsToReturn = listOf(event2))
        val repo = EventDiscoveryRepository(dao, listOf(source1, source2))

        val result = repo.refresh()

        assertEquals(2, result.inserted)
        assertEquals(2, dao.rows.size)
        assertTrue(dao.rows.containsKey("remote:src-a:123"))
        assertTrue(dao.rows.containsKey("remote:src-b:123"))
    }

    @Test
    fun `rejects events with blank title`() = runTest {
        val dao = FakeEventDao()
        val event = futureEvent(title = "")
        val source = FakeEventSource(eventsToReturn = listOf(event))
        val repo = EventDiscoveryRepository(dao, listOf(source))

        val result = repo.refresh()

        assertEquals(0, result.inserted)
        assertTrue(dao.rows.isEmpty())
    }

    @Test
    fun `rejects events with blank sourceId`() = runTest {
        val dao = FakeEventDao()
        val event = futureEvent(sourceId = "")
        val source = FakeEventSource(eventsToReturn = listOf(event))
        val repo = EventDiscoveryRepository(dao, listOf(source))

        val result = repo.refresh()

        assertEquals(0, result.inserted)
        assertTrue(dao.rows.isEmpty())
    }

    @Test
    fun `rejects events with zero startDate`() = runTest {
        val dao = FakeEventDao()
        val event = RemoteEvent(
            source = "fake-source",
            sourceId = "zero-start",
            title = "Zero Start",
            description = "desc",
            category = "MUSIC",
            startDate = 0L,
            endDate = System.currentTimeMillis() + 86_400_000L,
            venueName = "Venue",
            address = "addr",
            latitude = -33.92,
            longitude = 18.42,
            imageUrl = null,
            sourceUrl = null,
            organizerName = null
        )
        val source = FakeEventSource(eventsToReturn = listOf(event))
        val repo = EventDiscoveryRepository(dao, listOf(source))

        val result = repo.refresh()

        assertEquals(0, result.inserted)
    }

    @Test
    fun `returns empty result when no sources configured`() = runTest {
        val dao = FakeEventDao()
        val repo = EventDiscoveryRepository(dao, emptyList())

        val result = repo.refresh()

        assertEquals(0, result.fetched)
        assertEquals(0, result.inserted)
        assertEquals(0, result.failedSources)
        assertTrue(dao.rows.isEmpty())
    }
}
