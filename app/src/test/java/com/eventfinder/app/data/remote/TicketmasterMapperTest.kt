package com.eventfinder.app.data.remote

import com.eventfinder.app.data.remote.dto.TmAddress
import com.eventfinder.app.data.remote.dto.TmCity
import com.eventfinder.app.data.remote.dto.TmClassification
import com.eventfinder.app.data.remote.dto.TmCountry
import com.eventfinder.app.data.remote.dto.TmDates
import com.eventfinder.app.data.remote.dto.TmEmbedded
import com.eventfinder.app.data.remote.dto.TmEvent
import com.eventfinder.app.data.remote.dto.TmEventEmbedded
import com.eventfinder.app.data.remote.dto.TmEventsResponse
import com.eventfinder.app.data.remote.dto.TmImage
import com.eventfinder.app.data.remote.dto.TmLocation
import com.eventfinder.app.data.remote.dto.TmSegment
import com.eventfinder.app.data.remote.dto.TmStart
import com.eventfinder.app.data.remote.dto.TmVenue
import com.eventfinder.app.domain.model.EventCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Unit tests for the Ticketmaster Discovery v2 -> domain mapper.
 *
 * Robustness is critical here: a single malformed event must be skipped rather
 * than crash the whole sync. Fallback behaviour (coordinates, venue name) is
 * asserted explicitly.
 */
class TicketmasterMapperTest {

    private val zone: ZoneId = ZoneId.of("Africa/Johannesburg")
    private val mapper = TicketmasterMapper(zone)

    private fun validEvent(id: String = "Z123") = TmEvent(
        id = id,
        name = "Kirstenbosch Summer Concert",
        url = "https://ticketmaster.example/event",
        images = listOf(
            TmImage(url = "https://img/small.jpg", width = 200, height = 113),
            TmImage(url = "https://img/hero.jpg", width = 1920, height = 1080),
            TmImage(url = null, width = 5000, height = 2000)
        ),
        dates = TmDates(start = TmStart(localDate = "2026-12-05", localTime = "19:30:00")),
        classifications = listOf(TmClassification(segment = TmSegment(name = "Music"))),
        description = null,
        embedded = TmEventEmbedded(
            venues = listOf(
                TmVenue(
                    name = "Kirstenbosch Gardens",
                    city = TmCity(name = "Cape Town"),
                    address = TmAddress(line1 = "Rhodes Drive"),
                    country = TmCountry(name = "South Africa"),
                    location = TmLocation(latitude = "-33.9884", longitude = "18.4327")
                )
            )
        )
    )

    @Test
    fun `maps a full event payload onto the domain model`() {
        val events = mapper.mapPage(TmEventsResponse(embedded = TmEmbedded(events = listOf(validEvent()))))

        assertEquals(1, events.size)
        val event = events.first()
        assertEquals("tm-Z123", event.id)
        assertEquals("Kirstenbosch Summer Concert", event.title)
        assertEquals(EventCategory.MUSIC, event.category)
        assertEquals(-33.9884, event.latitude, 0.0001)
        assertEquals(18.4327, event.longitude, 0.0001)
        assertEquals("Rhodes Drive, Cape Town, South Africa", event.address)
        assertEquals("Kirstenbosch Gardens", event.venueName)
        assertEquals("https://img/hero.jpg", event.imageUrl)
        assertEquals("ticketmaster", event.organizerId)
        assertTrue(event.isSynced)
        assertTrue(!event.isCreatedByUser)
    }

    @Test
    fun `parses the start date and time in the configured zone`() {
        val events = mapper.mapPage(TmEventsResponse(embedded = TmEmbedded(events = listOf(validEvent()))))
        val expected = LocalDateTime.of(LocalDate.of(2026, 12, 5), LocalTime.of(19, 30))
            .atZone(zone).toInstant().toEpochMilli()
        assertEquals(expected, events.first().startDate)
        assertEquals(expected + 3 * 60 * 60 * 1000L, events.first().endDate)
    }

    @Test
    fun `skips events without an id or a start date`() {
        val noId = validEvent().copy(id = null)
        val noDate = validEvent(id = "Z999").copy(dates = TmDates(start = TmStart(localDate = null)))
        val response = TmEventsResponse(embedded = TmEmbedded(events = listOf(validEvent(), noId, noDate)))

        val events = mapper.mapPage(response)

        assertEquals(1, events.size)
        assertEquals("tm-Z123", events.first().id)
    }

    @Test
    fun `falls back to Johannesburg coordinates when the venue has none`() {
        val venueWithoutLocation = TmVenue(name = "Venue TBC", location = null)
        val event = validEvent().copy(
            embedded = TmEventEmbedded(venues = listOf(venueWithoutLocation))
        )

        val mapped = mapper.mapPage(TmEventsResponse(embedded = TmEmbedded(events = listOf(event)))).first()

        assertEquals(-26.2041, mapped.latitude, 0.0001)
        assertEquals(28.0473, mapped.longitude, 0.0001)
    }

    @Test
    fun `rejects out of range coordinates and uses the fallback`() {
        val badLocation = TmVenue(
            name = "Bad Venue",
            location = TmLocation(latitude = "999", longitude = "-500")
        )
        val event = validEvent().copy(embedded = TmEventEmbedded(venues = listOf(badLocation)))

        val mapped = mapper.mapPage(TmEventsResponse(embedded = TmEmbedded(events = listOf(event)))).first()

        assertEquals(-26.2041, mapped.latitude, 0.0001)
    }

    @Test
    fun `unknown segment maps to OTHER`() {
        val event = validEvent().copy(
            classifications = listOf(TmClassification(segment = TmSegment(name = "Hobbies")))
        )
        val mapped = mapper.mapPage(TmEventsResponse(embedded = TmEmbedded(events = listOf(event)))).first()
        assertEquals(EventCategory.OTHER, mapped.category)
    }

    @Test
    fun `missing classification maps to OTHER`() {
        val event = validEvent().copy(classifications = null)
        val mapped = mapper.mapPage(TmEventsResponse(embedded = TmEmbedded(events = listOf(event)))).first()
        assertEquals(EventCategory.OTHER, mapped.category)
    }

    @Test
    fun `generates a default description when the API provides none`() {
        val mapped = mapper.mapPage(TmEventsResponse(embedded = TmEmbedded(events = listOf(validEvent())))).first()
        assertTrue(mapped.description.contains("Kirstenbosch Summer Concert"))
    }

    @Test
    fun `empty or null embedded payloads map to an empty list`() {
        assertTrue(mapper.mapPage(TmEventsResponse()).isEmpty())
        assertTrue(mapper.mapPage(TmEventsResponse(embedded = TmEmbedded(events = null))).isEmpty())
    }

    @Test
    fun `parseStart returns null for unparseable input`() {
        assertNull(mapper.parseStart(null, "19:00:00"))
        assertNull(mapper.parseStart("", "19:00:00"))
        assertNull(mapper.parseStart("not-a-date", "19:00:00"))
        assertNull(mapper.parseStart("2026-13-40", "19:00:00"))
    }

    @Test
    fun `parseStart defaults to noon when the time is absent`() {
        val expected = LocalDateTime.of(LocalDate.of(2026, 12, 5), LocalTime.NOON)
            .atZone(zone).toInstant().toEpochMilli()
        assertEquals(expected, mapper.parseStart("2026-12-05", null))
        assertEquals(expected, mapper.parseStart("2026-12-05", ""))
    }

    @Test
    fun `pickImageUrl selects the widest available image`() {
        val url = mapper.pickImageUrl(
            listOf(
                TmImage(url = "small", width = 100),
                TmImage(url = "hero", width = 1920),
                TmImage(url = "medium", width = 640)
            )
        )
        assertEquals("hero", url)
    }

    @Test
    fun `pickImageUrl returns null when there are no usable images`() {
        assertNull(mapper.pickImageUrl(null))
        assertNull(mapper.pickImageUrl(emptyList()))
        assertNull(mapper.pickImageUrl(listOf(TmImage(url = null, width = 100))))
    }
}
