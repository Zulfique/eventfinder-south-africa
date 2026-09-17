package com.eventfinder.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Verifies the category taxonomy mapping used in both directions:
 * Ticketmaster segment names -> local category, and the persisted string
 * resource key -> local category (FR-02).
 */
class EventCategoryTest {

    @Test
    fun `maps known Ticketmaster segments`() {
        assertEquals(EventCategory.MUSIC, EventCategory.fromTicketmaster("Music"))
        assertEquals(EventCategory.SPORTS, EventCategory.fromTicketmaster("Sports"))
        assertEquals(EventCategory.ARTS, EventCategory.fromTicketmaster("Arts & Theatre"))
        assertEquals(EventCategory.FOOD, EventCategory.fromTicketmaster("Food & Drink"))
    }

    @Test
    fun `segment matching is case insensitive`() {
        assertEquals(EventCategory.MUSIC, EventCategory.fromTicketmaster("mUsIc"))
        assertEquals(EventCategory.SPORTS, EventCategory.fromTicketmaster("SPORTS"))
    }

    @Test
    fun `unknown or null segments fall back to OTHER`() {
        assertEquals(EventCategory.OTHER, EventCategory.fromTicketmaster("Undefined"))
        assertEquals(EventCategory.OTHER, EventCategory.fromTicketmaster(null))
        assertEquals(EventCategory.OTHER, EventCategory.fromTicketmaster(""))
    }

    @Test
    fun `label key round trips back to the same category`() {
        EventCategory.entries.forEach { category ->
            assertEquals(category, EventCategory.fromLabelKey(category.labelKey))
        }
    }

    @Test
    fun `unknown label key falls back to OTHER`() {
        assertEquals(EventCategory.OTHER, EventCategory.fromLabelKey("not_a_category"))
    }

    @Test
    fun `every category exposes a unique label key`() {
        val keys = EventCategory.entries.map { it.labelKey }
        assertEquals(keys.size, keys.toSet().size)
    }
}
