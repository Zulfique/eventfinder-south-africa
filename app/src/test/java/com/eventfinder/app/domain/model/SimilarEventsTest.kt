package com.eventfinder.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for the similar-events ranking used by the detail screen. */
class SimilarEventsTest {

    private val now = 1_700_000_000_000L

    private fun event(
        id: String,
        category: EventCategory = EventCategory.MUSIC,
        organizerId: String = "org-1",
        latitude: Double? = -33.9249,
        longitude: Double? = 18.4241,
        startDate: Long = now + 86_400_000L
    ) = Event(
        id = id,
        title = "Event $id",
        description = "",
        category = category,
        startDate = startDate,
        endDate = startDate + 3_600_000L,
        venueName = "Venue $id",
        address = "Cape Town",
        latitude = latitude,
        longitude = longitude,
        imageUrl = null,
        isPublic = true,
        organizerId = organizerId,
        organizerName = "Organiser",
        attendeeCount = 0,
        isFavorite = false,
        isCreatedByUser = false
    )

    @Test
    fun `same category and nearby ranks first`() {
        val viewed = event("viewed")
        val sameCategoryNearby = event("nearby")
        // ~50 km away: still eligible, but scores lower than the near match.
        val sameCategoryFurther = event("further", latitude = -33.5, longitude = 18.6)
        val ranked = SimilarEvents.rank(viewed, listOf(sameCategoryFurther, sameCategoryNearby), now = now)
        assertEquals(listOf("nearby", "further"), ranked.map { it.id })
    }

    @Test
    fun `the viewed event itself is excluded`() {
        val viewed = event("viewed")
        val ranked = SimilarEvents.rank(viewed, listOf(viewed, event("other")), now = now)
        assertFalse(ranked.any { it.id == "viewed" })
    }

    @Test
    fun `past events are never recommended`() {
        val viewed = event("viewed")
        val past = event("past", startDate = now - 86_400_000L)
        val ranked = SimilarEvents.rank(viewed, listOf(past), now = now)
        assertTrue(ranked.isEmpty())
    }

    @Test
    fun `unrelated far events are dropped`() {
        val viewed = event("viewed")
        val unrelated = event(
            id = "unrelated",
            category = EventCategory.FOOD,
            organizerId = "org-2",
            latitude = 51.5074,
            longitude = -0.1278
        )
        val ranked = SimilarEvents.rank(viewed, listOf(unrelated), now = now)
        assertTrue(ranked.isEmpty())
    }

    @Test
    fun `result is capped at the requested limit`() {
        val viewed = event("viewed")
        val candidates = (1..10).map { event("e$it") }
        val ranked = SimilarEvents.rank(viewed, candidates, now = now, limit = 4)
        assertEquals(4, ranked.size)
    }

    @Test
    fun `same organiser adds relevance within the same category`() {
        val viewed = event("viewed", organizerId = "org-1")
        val sameOrganiser = event("same-org", organizerId = "org-1")
        val otherOrganiser = event("other-org", organizerId = "org-2")
        val ranked = SimilarEvents.rank(viewed, listOf(otherOrganiser, sameOrganiser), now = now)
        assertEquals("same-org", ranked.first().id)
    }
}
