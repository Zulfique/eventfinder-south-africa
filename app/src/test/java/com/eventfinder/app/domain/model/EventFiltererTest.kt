package com.eventfinder.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the pure Kotlin search / filter / sort engine (FR-02).
 * The ViewModels delegate list manipulation here, so this is the behavioural
 * contract behind the Home and Search screens.
 */
class EventFiltererTest {

    private fun event(
        id: String,
        title: String,
        category: EventCategory = EventCategory.MUSIC,
        venue: String = "Venue",
        organizer: String = "Organizer",
        lat: Double = -26.2041,
        lng: Double = 28.0473,
        start: Long = 1_000_000L
    ) = Event(
        id = id,
        title = title,
        description = "desc",
        category = category,
        startDate = start,
        endDate = start + 7_200_000,
        venueName = venue,
        address = "address",
        latitude = lat,
        longitude = lng,
        imageUrl = null,
        isPublic = true,
        organizerId = organizer,
        organizerName = organizer,
        attendeeCount = 0,
        isFavorite = false,
        isCreatedByUser = false
    )

    private val jhb = event(
        id = "1", title = "Amapiano Night", category = EventCategory.MUSIC,
        venue = "Zoo Lake", organizer = "Joburg Live", start = 20L
    )
    private val cpt = event(
        id = "2", title = "Cape Town Sevens", category = EventCategory.SPORTS,
        venue = "Cape Town Stadium", organizer = "SA Rugby",
        lat = -33.9249, lng = 18.4241, start = 10L
    )
    private val durban = event(
        id = "3", title = "Bunny Chow Festival", category = EventCategory.FOOD,
        venue = "Durban Beachfront", organizer = "KZN Eats",
        lat = -29.8587, lng = 31.0218, start = 30L
    )

    private val all = listOf(jhb, cpt, durban)

    @Test
    fun `no filters returns the original list`() {
        assertEquals(all, EventFilterer.filter(all))
    }

    @Test
    fun `keyword filter matches title case insensitively`() {
        assertEquals(listOf(jhb), EventFilterer.filter(all, query = "amapiano"))
    }

    @Test
    fun `keyword filter matches venue and organizer`() {
        assertEquals(listOf(durban), EventFilterer.filter(all, query = "kzn"))
        assertEquals(listOf(cpt), EventFilterer.filter(all, query = "stadium"))
    }

    @Test
    fun `keyword filter matches address and description`() {
        val sandton = jhb.copy(address = "Sandton, Johannesburg")
        val cricket = cpt.copy(description = "A family friendly cricket day")
        val list = listOf(sandton, cricket, durban)

        assertEquals(listOf(sandton), EventFilterer.filter(list, query = "sandton"))
        assertEquals(listOf(cricket), EventFilterer.filter(list, query = "cricket"))
    }

    @Test
    fun `radius of zero disables distance filtering`() {
        assertEquals(
            all,
            EventFilterer.filter(all, userLat = -26.2, userLng = 28.0, radiusKm = 0)
        )
    }

    @Test
    fun `blank query is ignored`() {
        assertEquals(all, EventFilterer.filter(all, query = "   "))
    }

    @Test
    fun `category filter keeps only the requested category`() {
        assertEquals(listOf(cpt), EventFilterer.filter(all, category = EventCategory.SPORTS))
    }

    @Test
    fun `radius filter keeps events near the user only`() {
        val near = EventFilterer.filter(all, userLat = -26.2041, userLng = 28.0473, radiusKm = 100)
        assertEquals(listOf(jhb), near)
    }

    @Test
    fun `filters combine with AND semantics`() {
        val result = EventFilterer.filter(
            all,
            query = "festival",
            category = EventCategory.FOOD,
            userLat = -29.8587,
            userLng = 31.0218,
            radiusKm = 50
        )
        assertEquals(listOf(durban), result)
    }

    @Test
    fun `sort by date orders ascending by start time`() {
        assertEquals(listOf(cpt, jhb, durban), EventFilterer.sort(all, EventSort.DATE))
    }

    @Test
    fun `sort by name orders alphabetically ignoring case`() {
        assertEquals(listOf(jhb, durban, cpt), EventFilterer.sort(all, EventSort.NAME))
    }

    @Test
    fun `sort by distance orders nearest first`() {
        val sorted = EventFilterer.sort(all, EventSort.DISTANCE, userLat = -26.2041, userLng = 28.0473)
        assertEquals(listOf(jhb, durban, cpt), sorted)
    }

    @Test
    fun `sort by distance falls back to date without a location`() {
        assertEquals(listOf(cpt, jhb, durban), EventFilterer.sort(all, EventSort.DISTANCE))
    }

    @Test
    fun `attachDistances fills distances when location is present`() {
        val views = EventFilterer.attachDistances(all, -26.2041, 28.0473)
        assertEquals(3, views.size)
        assertEquals(0.0, views.first { it.event.id == "1" }.distanceKm!!, 0.001)
        assertTrue(views.first { it.event.id == "2" }.distanceKm!! > 1000)
    }

    @Test
    fun `attachDistances leaves distance null without a location`() {
        val views = EventFilterer.attachDistances(all, null, null)
        assertTrue(views.all { it.distanceKm == null })
        assertNull(views.first().rsvpStatus)
    }
}
