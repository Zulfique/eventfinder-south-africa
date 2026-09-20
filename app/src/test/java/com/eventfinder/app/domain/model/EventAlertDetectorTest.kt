package com.eventfinder.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the pure alert-diffing logic behind "new event" and
 * "favourite updated" local notifications (FR-04).
 */
class EventAlertDetectorTest {

    private val now = 1_800_000_000_000L

    private fun event(
        id: String,
        title: String = "Title",
        venue: String = "Venue",
        start: Long = now + 86_400_000L
    ) = Event(
        id = id,
        title = title,
        description = "desc",
        category = EventCategory.MUSIC,
        startDate = start,
        endDate = start + 7_200_000,
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
        isExternal = true
    )

    @Test
    fun `first sync with an empty previous catalogue produces no alerts`() {
        val alerts = EventAlertDetector.detect(
            previous = emptyMap(),
            current = listOf(event("a"), event("b")),
            favoriteIds = emptySet(),
            now = now
        )

        assertTrue(alerts.isEmpty)
    }

    @Test
    fun `new future events are reported`() {
        val alerts = EventAlertDetector.detect(
            previous = mapOf("a" to event("a")),
            current = listOf(event("a"), event("b"), event("c")),
            favoriteIds = emptySet(),
            now = now
        )

        assertEquals(listOf("b", "c"), alerts.newEvents.map { it.id })
    }

    @Test
    fun `past events are never alerted`() {
        val alerts = EventAlertDetector.detect(
            previous = mapOf("a" to event("a")),
            current = listOf(event("past", start = now - 1_000L)),
            favoriteIds = emptySet(),
            now = now
        )

        assertTrue(alerts.newEvents.isEmpty())
    }

    @Test
    fun `favourite with a changed title is reported`() {
        val previous = mapOf("fav" to event("fav", title = "Old name"))
        val alerts = EventAlertDetector.detect(
            previous = previous,
            current = listOf(event("fav", title = "New name")),
            favoriteIds = setOf("fav"),
            now = now
        )

        assertEquals(listOf("fav"), alerts.updatedFavorites.map { it.id })
    }

    @Test
    fun `favourite with a changed venue or date is reported`() {
        val previous = mapOf(
            "v" to event("v", venue = "Old venue"),
            "d" to event("d", start = now + 10_000L)
        )
        val alerts = EventAlertDetector.detect(
            previous = previous,
            current = listOf(
                event("v", venue = "New venue"),
                event("d", start = now + 500_000L)
            ),
            favoriteIds = setOf("v", "d"),
            now = now
        )

        assertEquals(setOf("v", "d"), alerts.updatedFavorites.map { it.id }.toSet())
    }

    @Test
    fun `unchanged favourites are not reported`() {
        val previous = mapOf("fav" to event("fav"))
        val alerts = EventAlertDetector.detect(
            previous = previous,
            current = listOf(event("fav")),
            favoriteIds = setOf("fav"),
            now = now
        )

        assertTrue(alerts.updatedFavorites.isEmpty())
    }

    @Test
    fun `changes to non-favourite events are ignored`() {
        val previous = mapOf("plain" to event("plain", title = "Old"))
        val alerts = EventAlertDetector.detect(
            previous = previous,
            current = listOf(event("plain", title = "New")),
            favoriteIds = emptySet(),
            now = now
        )

        assertTrue(alerts.updatedFavorites.isEmpty())
        // The id was already known, so it is neither new nor an updated favourite.
        assertTrue(alerts.newEvents.isEmpty())
        assertTrue(alerts.isEmpty)
    }
}
