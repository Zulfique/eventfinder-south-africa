package com.eventfinder.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Validates the bundled South African demo dataset that seeds the offline cache
 * when no Ticketmaster API key is configured.
 */
class SampleEventsProviderTest {

    private val events = SampleEventsProvider.johannesburgAndCapeTown()

    @Test
    fun `provides a non-trivial national catalogue`() {
        assertTrue("expected a useful demo catalogue", events.size >= 10)
    }

    @Test
    fun `event ids are unique`() {
        val ids = events.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `every event has a title, venue and description`() {
        events.forEach { event ->
            assertTrue("blank title for ${event.id}", event.title.isNotBlank())
            assertTrue("blank venue for ${event.id}", event.venueName.isNotBlank())
            assertTrue("blank description for ${event.id}", event.description.isNotBlank())
        }
    }

    @Test
    fun `every event ends after it starts`() {
        events.forEach { event ->
            assertTrue("end before start for ${event.id}", event.endDate > event.startDate)
        }
    }

    @Test
    fun `all coordinates fall within South Africa`() {
        // Generous bounding box covering the whole country.
        events.forEach { event ->
            assertTrue("latitude out of range for ${event.id}: ${event.latitude}", event.latitude in -35.0..-22.0)
            assertTrue("longitude out of range for ${event.id}: ${event.longitude}", event.longitude in 16.0..33.0)
        }
    }

    @Test
    fun `all seeded events are public and not user created`() {
        events.forEach { event ->
            assertTrue(event.isPublic)
            assertTrue(!event.isCreatedByUser)
        }
    }
}
