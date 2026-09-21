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
import org.junit.Assert.assertFalse
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

        override suspend fun deleteByOrganizerId(organizerId: String) {
            rows.values.filter { it.organizerId == organizerId && !it.isCreatedByUser }
                .forEach { rows.remove(it.id) }
            emit()
        }

        override suspend fun getUpcomingAttendingEventsForUser(userId: String, now: Long): List<EventEntity> =
            rows.values.filter { it.startDate > now }

        override suspend fun replaceNonUserCreated(events: List<EventEntity>) {
            deleteNonUserCreated()
            upsertAll(events)
        }

        override suspend fun replaceEventsForSource(organizerId: String, events: List<EventEntity>) {
            deleteByOrganizerId(organizerId)
            if (events.isNotEmpty()) {
                upsertAll(events)
            }
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

    @Test
    fun `ardent africa location string does not crash parser`() = runTest {
        val dao = FakeEventDao()
        val event = RemoteEvent(
            source = "ardent-africa",
            sourceId = "test-id",
            title = "Ardent Test Event",
            description = "Test",
            category = "Education & Academic",
            startDate = System.currentTimeMillis() + 86_400_000L,
            endDate = System.currentTimeMillis() + 172_800_000L,
            venueName = "Unknown venue",
            address = "",
            latitude = null,
            longitude = null,
            imageUrl = "https://example.com/image.jpg",
            sourceUrl = null,
            organizerName = null
        )
        val source = FakeEventSource(eventsToReturn = listOf(event))
        val repo = EventDiscoveryRepository(dao, listOf(source))

        val result = repo.refresh()

        assertEquals(1, result.fetched)
        assertEquals(0, result.inserted)
        assertEquals(0, result.failedSources)
    }

    @Test
    fun `rejects events outside South Africa bounding box`() = runTest {
        val dao = FakeEventDao()
        val event = futureEvent(
            sourceId = "ghana-1",
            title = "Accra Event",
            latitude = 5.6037,
            longitude = -0.1870
        )
        val source = FakeEventSource(eventsToReturn = listOf(event))
        val repo = EventDiscoveryRepository(dao, listOf(source))

        val result = repo.refresh()

        assertEquals(1, result.fetched)
        assertEquals(0, result.inserted)
        assertTrue(dao.rows.isEmpty())
    }

    @Test
    fun `accepts events within South Africa bounding box`() = runTest {
        val dao = FakeEventDao()
        val event = futureEvent(
            sourceId = "ct-1",
            title = "Cape Town Event",
            latitude = -33.9249,
            longitude = 18.4241
        )
        val source = FakeEventSource(eventsToReturn = listOf(event))
        val repo = EventDiscoveryRepository(dao, listOf(source))

        val result = repo.refresh()

        assertEquals(1, result.fetched)
        assertEquals(1, result.inserted)
        assertEquals(1, dao.rows.size)
    }

    @Test
    fun `accepts events at SA bounding box edges`() = runTest {
        val dao = FakeEventDao()
        val event = futureEvent(
            sourceId = "edge-1",
            title = "Edge Event",
            latitude = -22.0,
            longitude = 33.0
        )
        val source = FakeEventSource(eventsToReturn = listOf(event))
        val repo = EventDiscoveryRepository(dao, listOf(source))

        val result = repo.refresh()

        assertEquals(1, result.fetched)
        assertEquals(1, result.inserted)
    }

    @Test
    fun `rejects events just outside SA bounding box`() = runTest {
        val dao = FakeEventDao()
        val event = futureEvent(
            sourceId = "outside-1",
            title = "Mozambique Event",
            latitude = -21.9,
            longitude = 35.0
        )
        val source = FakeEventSource(eventsToReturn = listOf(event))
        val repo = EventDiscoveryRepository(dao, listOf(source))

        val result = repo.refresh()

        assertEquals(1, result.fetched)
        assertEquals(0, result.inserted)
        assertTrue(dao.rows.isEmpty())
    }

    @Test
    fun `stale remote events are removed when no longer returned by source`() = runTest {
        val dao = FakeEventDao()
        val now = System.currentTimeMillis()
        val oldRemote = EventEntity(
            id = "remote:old-1",
            title = "Old Event",
            description = "Previously fetched",
            category = "other",
            startDate = now + 86_400_000L,
            endDate = now + 172_800_000L,
            venueName = "Old Venue",
            address = "",
            latitude = -33.0,
            longitude = 18.0,
            imageUrl = null,
            isPublic = true,
            organizerId = "external:fake-source",
            organizerName = "Fake Source",
            attendeeCount = 0,
            isCreatedByUser = false
        )
        val userEvent = EventEntity(
            id = "user:my-event",
            title = "My Event",
            description = "Created by me",
            category = "other",
            startDate = now + 86_400_000L,
            endDate = now + 172_800_000L,
            venueName = "My Venue",
            address = "",
            latitude = -33.0,
            longitude = 18.0,
            imageUrl = null,
            isPublic = true,
            organizerId = "user:me",
            organizerName = "Me",
            attendeeCount = 1,
            isCreatedByUser = true
        )

        dao.rows["remote:old-1"] = oldRemote
        dao.rows["user:my-event"] = userEvent

        val newEvent = futureEvent(sourceId = "new-1", title = "New Event")
        val source = FakeEventSource(eventsToReturn = listOf(newEvent))
        val repo = EventDiscoveryRepository(dao, listOf(source))

        val result = repo.refresh()

        assertEquals(1, result.fetched)
        assertEquals(1, result.inserted)
        assertTrue(dao.rows.containsKey("remote:fake-source:new-1"))
        assertTrue(dao.rows.containsKey("user:my-event"))
        assertFalse(dao.rows.containsKey("remote:old-1"))
    }

    @Test
    fun `user-created events are preserved across refresh`() = runTest {
        val dao = FakeEventDao()
        val now = System.currentTimeMillis()
        val userEvent = EventEntity(
            id = "user:concert",
            title = "My Concert",
            description = "I created this event for testing",
            category = "music",
            startDate = now + 86_400_000L,
            endDate = now + 172_800_000L,
            venueName = "My Venue",
            address = "123 Main St",
            latitude = -33.9,
            longitude = 18.4,
            imageUrl = null,
            isPublic = true,
            organizerId = "user:me",
            organizerName = "Me",
            attendeeCount = 5,
            isCreatedByUser = true
        )
        dao.rows["user:concert"] = userEvent

        val remoteEvent = futureEvent(sourceId = "remote-1", title = "Remote Festival")
        val source = FakeEventSource(eventsToReturn = listOf(remoteEvent))
        val repo = EventDiscoveryRepository(dao, listOf(source))

        repo.refresh()

        assertTrue(dao.rows.containsKey("user:concert"))
        assertTrue(dao.rows.containsKey("remote:fake-source:remote-1"))
        assertEquals(2, dao.rows.size)
    }

    @Test
    fun `empty source preserves cached events`() = runTest {
        val dao = FakeEventDao()
        val now = System.currentTimeMillis()
        val cachedEvent = EventEntity(
            id = "remote:stale-1",
            title = "Stale",
            description = "Should be preserved",
            category = "other",
            startDate = now + 86_400_000L,
            endDate = now + 172_800_000L,
            venueName = "Stale Venue",
            address = "",
            latitude = -33.0,
            longitude = 18.0,
            imageUrl = null,
            isPublic = true,
            organizerId = "external:fake-source",
            organizerName = "Fake Source",
            attendeeCount = 0,
            isCreatedByUser = false
        )
        dao.rows["remote:stale-1"] = cachedEvent

        val source = FakeEventSource(eventsToReturn = emptyList())
        val repo = EventDiscoveryRepository(dao, listOf(source))

        val result = repo.refresh()

        assertEquals(0, result.fetched)
        assertEquals(0, result.inserted)
        assertTrue(dao.rows.containsKey("remote:stale-1"))
        assertEquals(1, dao.rows.size)
    }

    @Test
    fun `failed source does not erase other sources cached events`() = runTest {
        val dao = FakeEventDao()
        val now = System.currentTimeMillis()

        val sourceAEvent = EventEntity(
            id = "remote:sourceA:evt-1",
            title = "Event A",
            description = "From source A",
            category = "other",
            startDate = now + 86_400_000L,
            endDate = now + 172_800_000L,
            venueName = "Venue A",
            address = "",
            latitude = -33.0,
            longitude = 18.0,
            imageUrl = null,
            isPublic = true,
            organizerId = "external:sourceA",
            organizerName = "Source A",
            attendeeCount = 0,
            isCreatedByUser = false
        )

        val sourceBEvent = EventEntity(
            id = "remote:sourceB:evt-1",
            title = "Event B",
            description = "From source B",
            category = "other",
            startDate = now + 86_400_000L,
            endDate = now + 172_800_000L,
            venueName = "Venue B",
            address = "",
            latitude = -34.0,
            longitude = 19.0,
            imageUrl = null,
            isPublic = true,
            organizerId = "external:sourceB",
            organizerName = "Source B",
            attendeeCount = 0,
            isCreatedByUser = false
        )

        dao.rows["remote:sourceA:evt-1"] = sourceAEvent
        dao.rows["remote:sourceB:evt-1"] = sourceBEvent

        val sourceA = FakeEventSource(
            id = "sourceA",
            displayName = "Source A",
            eventsToReturn = listOf(futureEvent(source = "sourceA", sourceId = "evt-new"))
        )
        val sourceB = FakeEventSource(
            id = "sourceB",
            displayName = "Source B",
            exceptionToThrow = RuntimeException("Source B is down")
        )

        val repo = EventDiscoveryRepository(dao, listOf(sourceA, sourceB))
        val result = repo.refresh()

        assertEquals(1, result.fetched)
        assertEquals(1, result.inserted)
        assertEquals(1, result.failedSources)

        assertTrue(dao.rows.containsKey("remote:sourceA:evt-new"))
        assertTrue(dao.rows.containsKey("remote:sourceB:evt-1"))
        assertFalse(dao.rows.containsKey("remote:sourceA:evt-1"))
    }

    @Test
    fun `empty source preserves own cache while other source updates`() = runTest {
        val dao = FakeEventDao()
        val now = System.currentTimeMillis()

        val sourceAEvent = EventEntity(
            id = "remote:sourceA:evt-1",
            title = "Event A",
            description = "From source A",
            category = "other",
            startDate = now + 86_400_000L,
            endDate = now + 172_800_000L,
            venueName = "Venue A",
            address = "",
            latitude = -33.0,
            longitude = 18.0,
            imageUrl = null,
            isPublic = true,
            organizerId = "external:sourceA",
            organizerName = "Source A",
            attendeeCount = 0,
            isCreatedByUser = false
        )

        val sourceBEvent = EventEntity(
            id = "remote:sourceB:evt-1",
            title = "Event B",
            description = "From source B",
            category = "other",
            startDate = now + 86_400_000L,
            endDate = now + 172_800_000L,
            venueName = "Venue B",
            address = "",
            latitude = -34.0,
            longitude = 19.0,
            imageUrl = null,
            isPublic = true,
            organizerId = "external:sourceB",
            organizerName = "Source B",
            attendeeCount = 0,
            isCreatedByUser = false
        )

        dao.rows["remote:sourceA:evt-1"] = sourceAEvent
        dao.rows["remote:sourceB:evt-1"] = sourceBEvent

        val sourceA = FakeEventSource(id = "sourceA", displayName = "Source A", eventsToReturn = emptyList())
        val sourceB = FakeEventSource(
            id = "sourceB",
            displayName = "Source B",
            eventsToReturn = listOf(futureEvent(source = "sourceB", sourceId = "evt-new"))
        )

        val repo = EventDiscoveryRepository(dao, listOf(sourceA, sourceB))
        val result = repo.refresh()

        assertEquals(1, result.fetched)
        assertEquals(1, result.inserted)
        assertEquals(0, result.failedSources)

        assertTrue(dao.rows.containsKey("remote:sourceA:evt-1"))
        assertFalse(dao.rows.containsKey("remote:sourceB:evt-1"))
        assertTrue(dao.rows.containsKey("remote:sourceB:evt-new"))
    }

    @Test
    fun `source returning events that all fail validation preserves cached events`() = runTest {
        val dao = FakeEventDao()
        val now = System.currentTimeMillis()

        val existingEvent = EventEntity(
            id = "remote:sourceA:old",
            title = "Old Event",
            description = "Previously cached",
            category = "other",
            startDate = now + 86_400_000L,
            endDate = now + 172_800_000L,
            venueName = "Venue",
            address = "",
            latitude = -33.0,
            longitude = 18.0,
            imageUrl = null,
            isPublic = true,
            organizerId = "external:sourceA",
            organizerName = "Source A",
            attendeeCount = 0,
            isCreatedByUser = false
        )
        dao.rows["remote:sourceA:old"] = existingEvent

        val pastEvent = RemoteEvent(
            source = "sourceA",
            sourceId = "past-1",
            title = "Past Event",
            description = "Already ended",
            category = "music",
            startDate = now - 172_800_000L,
            endDate = now - 86_400_000L,
            venueName = "Venue",
            address = "",
            latitude = -33.0,
            longitude = 18.0,
            imageUrl = null,
            sourceUrl = null,
            organizerName = null
        )
        val noCoordsEvent = RemoteEvent(
            source = "sourceA",
            sourceId = "nocoords-1",
            title = "No Coords Event",
            description = "Has no coordinates",
            category = "food",
            startDate = now + 86_400_000L,
            endDate = now + 172_800_000L,
            venueName = "Venue",
            address = "",
            latitude = null,
            longitude = null,
            imageUrl = null,
            sourceUrl = null,
            organizerName = null
        )
        val source = FakeEventSource(id = "sourceA", displayName = "Source A", eventsToReturn = listOf(pastEvent, noCoordsEvent))
        val repo = EventDiscoveryRepository(dao, listOf(source))

        val result = repo.refresh()

        assertEquals(2, result.fetched)
        assertEquals(0, result.inserted)
        assertEquals(0, result.failedSources)
        assertTrue(dao.rows.containsKey("remote:sourceA:old"))
    }

    @Test
    fun `cross-source dedup merges same event from multiple sources`() = runTest {
        val dao = FakeEventDao()
        val eventFromA = futureEvent(source = "ardent", sourceId = "jazz-1", title = "Cape Town Jazz Festival")
        val eventFromB = futureEvent(source = "rss", sourceId = "jazz-rss", title = "Cape Town Jazz Festival")
        val sourceA = FakeEventSource(id = "ardent", displayName = "Ardent", eventsToReturn = listOf(eventFromA))
        val sourceB = FakeEventSource(id = "rss", displayName = "RSS", eventsToReturn = listOf(eventFromB))
        val repo = EventDiscoveryRepository(dao, listOf(sourceA, sourceB))

        val result = repo.refresh()

        assertEquals(2, result.fetched)
        assertEquals(1, result.inserted)
        assertEquals(1, dao.rows.size)
    }

    @Test
    fun `cross-source dedup keeps different events`() = runTest {
        val dao = FakeEventDao()
        val event1 = futureEvent(source = "ardent", sourceId = "evt-1", title = "Jazz Festival")
        val event2 = futureEvent(source = "rss", sourceId = "evt-2", title = "Food Market")
        val sourceA = FakeEventSource(id = "ardent", displayName = "Ardent", eventsToReturn = listOf(event1))
        val sourceB = FakeEventSource(id = "rss", displayName = "RSS", eventsToReturn = listOf(event2))
        val repo = EventDiscoveryRepository(dao, listOf(sourceA, sourceB))

        val result = repo.refresh()

        assertEquals(2, result.fetched)
        assertEquals(2, result.inserted)
        assertEquals(2, dao.rows.size)
    }

    @Test
    fun `geocoder fallback for events without coordinates`() = runTest {
        val dao = FakeEventDao()
        val eventNoCoords = futureEvent(latitude = null, longitude = null, title = "Locationless Event")
        val source = FakeEventSource(eventsToReturn = listOf(eventNoCoords))
        val repo = EventDiscoveryRepository(dao, listOf(source), geocoder = null)

        val result = repo.refresh()

        assertEquals(1, result.fetched)
        assertEquals(0, result.inserted)
        assertEquals(0, dao.rows.size)
    }

    @Test
    fun `result includes per-source observability data`() = runTest {
        val dao = FakeEventDao()
        val event = futureEvent()
        val source = FakeEventSource(eventsToReturn = listOf(event))
        val repo = EventDiscoveryRepository(dao, listOf(source))

        val result = repo.refresh()

        assertEquals(1, result.sourceResults.size)
        assertEquals("Fake Source", result.sourceResults[0].sourceName)
        assertEquals(1, result.sourceResults[0].fetched)
        assertEquals(1, result.sourceResults[0].inserted)
        assertFalse(result.sourceResults[0].failed)
    }

    @Test
    fun `failed source shows failed in source results`() = runTest {
        val dao = FakeEventDao()
        val source = FakeEventSource(exceptionToThrow = RuntimeException("down"))
        val repo = EventDiscoveryRepository(dao, listOf(source))

        val result = repo.refresh()

        assertEquals(1, result.failedSources)
        assertTrue(result.sourceResults[0].failed)
    }

    @Test
    fun `three sources with one failure preserves two successful caches`() = runTest {
        val dao = FakeEventDao()
        val eventA = futureEvent(source = "srcA", sourceId = "a-1", title = "Event A")
        val eventB = futureEvent(source = "srcB", sourceId = "b-1", title = "Event B")
        val sourceA = FakeEventSource(id = "srcA", displayName = "Source A", eventsToReturn = listOf(eventA))
        val sourceB = FakeEventSource(id = "srcB", displayName = "Source B", eventsToReturn = listOf(eventB))
        val sourceC = FakeEventSource(id = "srcC", displayName = "Source C", exceptionToThrow = RuntimeException("down"))
        val repo = EventDiscoveryRepository(dao, listOf(sourceA, sourceB, sourceC))

        val result = repo.refresh()

        assertEquals(2, result.fetched)
        assertEquals(2, result.inserted)
        assertEquals(1, result.failedSources)
        assertEquals(2, dao.rows.size)
        assertTrue(dao.rows.containsKey("remote:srcA:a-1"))
        assertTrue(dao.rows.containsKey("remote:srcB:b-1"))
    }
}
