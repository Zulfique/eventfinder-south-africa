package com.eventfinder.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Home feed collections are pure filter presets, so they're tested directly. */
class EventCollectionsTest {

    private val zone: ZoneId = ZoneId.systemDefault()

    private fun millisAt(date: LocalDate, hour: Int, minute: Int = 0): Long =
        date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun event(
        id: String = "e1",
        category: EventCategory = EventCategory.MUSIC,
        start: Long,
        end: Long = start + 3_600_000L
    ) = Event(
        id = id,
        title = "Test event",
        description = "desc",
        category = category,
        startDate = start,
        endDate = end,
        venueName = "Venue",
        address = "Address",
        latitude = null,
        longitude = null,
        imageUrl = null,
        isPublic = true,
        organizerId = "org",
        organizerName = "Organiser",
        attendeeCount = 0,
        isFavorite = false,
        isCreatedByUser = false
    )

    @Test
    fun `weekend window starts on friday and spans three days`() {
        // A Wednesday lunchtime: the next weekend begins the coming Friday.
        val wednesday = LocalDate.of(2026, 3, 4)
        val now = millisAt(wednesday, 12)

        val (start, end) = EventCollection.weekendWindow(now)

        val startDate = Instant.ofEpochMilli(start).atZone(zone).toLocalDate()
        assertEquals(DayOfWeek.FRIDAY, startDate.dayOfWeek)
        // Friday 00:00 → Monday 00:00
        assertEquals(3 * 86_400_000L, end - start)
    }

    @Test
    fun `saturday still counts as the current weekend`() {
        val saturday = LocalDate.of(2026, 3, 7)
        val now = millisAt(saturday, 10)

        val (start, _) = EventCollection.weekendWindow(now)
        val startDate = Instant.ofEpochMilli(start).atZone(zone).toLocalDate()

        assertEquals(LocalDate.of(2026, 3, 6), startDate)
        assertTrue(start < now)
    }

    @Test
    fun `this weekend collection accepts friday and rejects tuesday`() {
        val collection = EventCollection.byId("this_weekend")!!
        val friday = LocalDate.of(2026, 3, 6)
        val now = millisAt(friday, 9)

        val fridayEvent = event(start = millisAt(friday, 19))
        val tuesdayEvent = event(id = "e2", start = millisAt(friday.plusDays(4), 19))

        assertTrue(collection.matches(fridayEvent, now))
        assertFalse(collection.matches(tuesdayEvent, now))
    }

    @Test
    fun `category collection only matches its category`() {
        val comedy = EventCollection.byId("comedy")!!
        val now = millisAt(LocalDate.of(2026, 5, 1), 12)

        assertTrue(comedy.matches(event(category = EventCategory.COMEDY, start = now), now))
        assertFalse(comedy.matches(event(category = EventCategory.MUSIC, start = now), now))
    }

    @Test
    fun `curated collections have unique ids and resolve`() {
        val ids = EventCollection.curated.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        ids.forEach { id ->
            assertEquals(id, EventCollection.byId(id)?.id)
        }
        assertEquals(null, EventCollection.byId("missing"))
        assertEquals(null, EventCollection.byId(null))
    }
}
