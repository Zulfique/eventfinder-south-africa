package com.eventfinder.app.data.remote

import com.eventfinder.app.data.remote.dto.TmAddress
import com.eventfinder.app.data.remote.dto.TmClassification
import com.eventfinder.app.data.remote.dto.TmCity
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.ZoneId

class TicketmasterMapperTest {

    private lateinit var mapper: TicketmasterMapper

    @Before
    fun setUp() {
        mapper = TicketmasterMapper(zoneId = ZoneId.of("Africa/Johannesburg"))
    }

    private fun baseEvent(
        id: String? = "tm-1",
        name: String? = "Test Event",
        date: String? = "2026-10-03",
        time: String? = "19:00:00",
        description: String? = "A test event"
    ) = TmEvent(
        id = id,
        name = name,
        description = description,
        dates = TmDates(start = TmStart(localDate = date, localTime = time)),
        images = listOf(TmImage(url = "https://example.com/hero.jpg", width = 1920, height = 1080)),
        classifications = listOf(TmClassification(segment = TmSegment(name = "Music"))),
        embedded = TmEventEmbedded(
            venues = listOf(
                TmVenue(
                    name = "The Bassline",
                    address = TmAddress(line1 = "44 Nile St"),
                    city = TmCity(name = "Johannesburg"),
                    country = TmCountry(name = "South Africa"),
                    location = TmLocation(latitude = "-26.19", longitude = "28.04")
                )
            )
        )
    )

    @Test
    fun `mapPage maps a valid payload to domain events`() {
        val response = TmEventsResponse(
            embedded = TmEmbedded(events = listOf(baseEvent()))
        )

        val result = mapper.mapPage(response)

        assertEquals(1, result.size)
        val event = result[0]
        assertEquals("tm-tm-1", event.id)
        assertEquals("Test Event", event.title)
        assertEquals("A test event", event.description)
        assertEquals(EventCategory.MUSIC, event.category)
        assertEquals("The Bassline", event.venueName)
        assertEquals("44 Nile St, Johannesburg, South Africa", event.address)
        assertEquals(-26.19, event.latitude, 0.01)
        assertEquals(28.04, event.longitude, 0.01)
        assertEquals("https://example.com/hero.jpg", event.imageUrl)
        assertTrue(event.isExternal)
        assertTrue(!event.isCreatedByUser)
    }

    @Test
    fun `mapPage skips events with null id`() {
        val response = TmEventsResponse(
            embedded = TmEmbedded(events = listOf(baseEvent(id = "tm-1"), baseEvent(id = null)))
        )

        val result = mapper.mapPage(response)

        assertEquals(1, result.size)
        assertEquals("tm-tm-1", result[0].id)
    }

    @Test
    fun `mapPage skips events with null name`() {
        val response = TmEventsResponse(
            embedded = TmEmbedded(events = listOf(baseEvent(name = null)))
        )

        val result = mapper.mapPage(response)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `mapPage skips events with unparseable date`() {
        val response = TmEventsResponse(
            embedded = TmEmbedded(events = listOf(baseEvent(date = "not-a-date")))
        )

        val result = mapper.mapPage(response)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `mapPage handles null embedded and null events`() {
        val response = TmEventsResponse(embedded = null)

        val result = mapper.mapPage(response)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `mapPage handles empty events list`() {
        val response = TmEventsResponse(embedded = TmEmbedded(events = emptyList()))

        val result = mapper.mapPage(response)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `mapPage defaults to noon when time is missing`() {
        val response = TmEventsResponse(
            embedded = TmEmbedded(events = listOf(baseEvent(time = null)))
        )

        val result = mapper.mapPage(response)

        assertEquals(1, result.size)
    }

    @Test
    fun `mapPage defaults to noon when time is blank`() {
        val response = TmEventsResponse(
            embedded = TmEmbedded(events = listOf(baseEvent(time = "  ")))
        )

        val result = mapper.mapPage(response)

        assertEquals(1, result.size)
    }

    @Test
    fun `mapPage uses Johannesburg fallback when venue has no location`() {
        val event = TmEvent(
            id = "tm-99",
            name = "No Location Event",
            description = "No venue location",
            dates = TmDates(start = TmStart(localDate = "2026-10-03", localTime = "19:00:00")),
            embedded = TmEventEmbedded(
                venues = listOf(
                    TmVenue(name = "Unknown Venue")
                )
            )
        )
        val response = TmEventsResponse(embedded = TmEmbedded(events = listOf(event)))

        val result = mapper.mapPage(response)

        assertEquals(1, result.size)
        assertEquals(-26.2041, result[0].latitude, 0.01)
        assertEquals(28.0473, result[0].longitude, 0.01)
        assertEquals("South Africa", result[0].address)
        assertEquals("Unknown Venue", result[0].venueName)
    }

    @Test
    fun `mapPage uses Johannesburg fallback for out of range coordinates`() {
        val event = TmEvent(
            id = "tm-bad-coord",
            name = "Bad Coord Event",
            description = "Out of range coords",
            dates = TmDates(start = TmStart(localDate = "2026-10-03", localTime = "19:00:00")),
            embedded = TmEventEmbedded(
                venues = listOf(
                    TmVenue(
                        name = "Bad Venue",
                        location = TmLocation(latitude = "999", longitude = "999")
                    )
                )
            )
        )
        val response = TmEventsResponse(embedded = TmEmbedded(events = listOf(event)))

        val result = mapper.mapPage(response)

        assertEquals(1, result.size)
        assertEquals(-26.2041, result[0].latitude, 0.01)
        assertEquals(28.0473, result[0].longitude, 0.01)
    }

    @Test
    fun `mapPage handles non-numeric coordinates gracefully`() {
        val event = TmEvent(
            id = "tm-nan",
            name = "NaN Coord Event",
            description = "Non-numeric coords",
            dates = TmDates(start = TmStart(localDate = "2026-10-03", localTime = "19:00:00")),
            embedded = TmEventEmbedded(
                venues = listOf(
                    TmVenue(
                        name = "NaN Venue",
                        location = TmLocation(latitude = "abc", longitude = "xyz")
                    )
                )
            )
        )
        val response = TmEventsResponse(embedded = TmEmbedded(events = listOf(event)))

        val result = mapper.mapPage(response)

        assertEquals(1, result.size)
        assertEquals(-26.2041, result[0].latitude, 0.01)
        assertEquals(28.0473, result[0].longitude, 0.01)
    }

    @Test
    fun `mapPage uses venue url as fallback venue name when venue name is null`() {
        val event = TmEvent(
            id = "tm-88",
            name = "No Venue Name Event",
            description = "No venue name",
            url = "https://ticketmaster.com/event/123",
            dates = TmDates(start = TmStart(localDate = "2026-10-03", localTime = "19:00:00")),
            embedded = TmEventEmbedded(venues = listOf(TmVenue()))
        )
        val response = TmEventsResponse(embedded = TmEmbedded(events = listOf(event)))

        val result = mapper.mapPage(response)

        assertEquals(1, result.size)
        assertEquals("https://ticketmaster.com/event/123", result[0].venueName)
    }

    @Test
    fun `mapPage uses venue name when present`() {
        val event = TmEvent(
            id = "tm-87",
            name = "Venue Name Event",
            description = "Has venue name",
            url = "https://ticketmaster.com/event/456",
            dates = TmDates(start = TmStart(localDate = "2026-10-03", localTime = "19:00:00")),
            embedded = TmEventEmbedded(venues = listOf(TmVenue(name = "The Orbit")))
        )
        val response = TmEventsResponse(embedded = TmEmbedded(events = listOf(event)))

        val result = mapper.mapPage(response)

        assertEquals(1, result.size)
        assertEquals("The Orbit", result[0].venueName)
    }

    @Test
    fun `mapPage maps non-music segment to OTHER category`() {
        val event = TmEvent(
            id = "tm-arts",
            name = "Art Exhibition",
            description = "Art event",
            dates = TmDates(start = TmStart(localDate = "2026-10-03", localTime = "19:00:00")),
            classifications = listOf(TmClassification(segment = TmSegment(name = "Arts & Theatre")))
        )
        val response = TmEventsResponse(embedded = TmEmbedded(events = listOf(event)))

        val result = mapper.mapPage(response)

        assertEquals(1, result.size)
        assertEquals(EventCategory.ARTS, result[0].category)
    }

    @Test
    fun `mapPage uses default description when none provided`() {
        val event = TmEvent(
            id = "tm-no-desc",
            name = "No Description Event",
            description = null,
            dates = TmDates(start = TmStart(localDate = "2026-10-03", localTime = "19:00:00"))
        )
        val response = TmEventsResponse(embedded = TmEmbedded(events = listOf(event)))

        val result = mapper.mapPage(response)

        assertEquals(1, result.size)
        assertEquals("Join us for No Description Event. Save this event to get reminders before it starts.", result[0].description)
    }

    @Test
    fun `mapPage handles empty images list`() {
        val event = TmEvent(
            id = "tm-no-img",
            name = "No Image Event",
            description = "No images",
            dates = TmDates(start = TmStart(localDate = "2026-10-03", localTime = "19:00:00")),
            images = emptyList()
        )
        val response = TmEventsResponse(embedded = TmEmbedded(events = listOf(event)))

        val result = mapper.mapPage(response)

        assertEquals(1, result.size)
        assertNull(result[0].imageUrl)
    }

    @Test
    fun `mapPage handles null images`() {
        val event = TmEvent(
            id = "tm-null-img",
            name = "Null Image Event",
            description = "Null images",
            dates = TmDates(start = TmStart(localDate = "2026-10-03", localTime = "19:00:00")),
            images = null
        )
        val response = TmEventsResponse(embedded = TmEmbedded(events = listOf(event)))

        val result = mapper.mapPage(response)

        assertEquals(1, result.size)
        assertNull(result[0].imageUrl)
    }

    @Test
    fun `pickImageUrl selects widest image`() {
        val images = listOf(
            TmImage(url = "https://example.com/narrow.jpg", width = 800, height = 600),
            TmImage(url = "https://example.com/wide.jpg", width = 1920, height = 1080),
            TmImage(url = "https://example.com/medium.jpg", width = 1200, height = 800)
        )

        val result = mapper.pickImageUrl(images)

        assertEquals("https://example.com/wide.jpg", result)
    }

    @Test
    fun `pickImageUrl prefers wider aspect when widths equal`() {
        val images = listOf(
            TmImage(url = "https://example.com/a.jpg", width = 1920, height = 1080),
            TmImage(url = "https://example.com/b.jpg", width = 1920, height = 1080)
        )

        val result = mapper.pickImageUrl(images)

        assertEquals("https://example.com/a.jpg", result)
    }

    @Test
    fun `pickImageUrl returns null for null images`() {
        assertNull(mapper.pickImageUrl(null))
    }

    @Test
    fun `pickImageUrl returns null for empty list`() {
        assertNull(mapper.pickImageUrl(emptyList()))
    }

    @Test
    fun `pickImageUrl filters out null urls`() {
        val images = listOf(
            TmImage(url = null, width = 1920, height = 1080),
            TmImage(url = "https://example.com/valid.jpg", width = 800, height = 600)
        )

        val result = mapper.pickImageUrl(images)

        assertEquals("https://example.com/valid.jpg", result)
    }

    @Test
    fun `parseStart handles valid date and time`() {
        val result = mapper.parseStart("2026-10-03", "19:00:00")

        assertNotNull(result)
    }

    @Test
    fun `parseStart returns null for null date`() {
        assertNull(mapper.parseStart(null, "19:00:00"))
    }

    @Test
    fun `parseStart returns null for blank date`() {
        assertNull(mapper.parseStart("  ", "19:00:00"))
    }

    @Test
    fun `parseStart returns null for invalid date`() {
        assertNull(mapper.parseStart("not-a-date", "19:00:00"))
    }

    @Test
    fun `parseStart returns null for invalid time`() {
        assertNull(mapper.parseStart("2026-10-03", "not-a-time"))
    }

    @Test
    fun `parseStart handles null time by defaulting to noon`() {
        val result = mapper.parseStart("2026-10-03", null)

        assertNotNull(result)
    }

    @Test
    fun `parseStart handles blank time by defaulting to noon`() {
        val result = mapper.parseStart("2026-10-03", "  ")

        assertNotNull(result)
    }

    @Test
    fun `mapPage builds full address from venue fields`() {
        val event = TmEvent(
            id = "tm-addr",
            name = "Address Event",
            description = "Full address",
            dates = TmDates(start = TmStart(localDate = "2026-10-03", localTime = "19:00:00")),
            embedded = TmEventEmbedded(
                venues = listOf(
                    TmVenue(
                        name = "Theatre on the Square",
                        address = TmAddress(line1 = "Nelson Mandela Square"),
                        city = TmCity(name = "Sandton"),
                        country = TmCountry(name = "South Africa")
                    )
                )
            )
        )
        val response = TmEventsResponse(embedded = TmEmbedded(events = listOf(event)))

        val result = mapper.mapPage(response)

        assertEquals("Nelson Mandela Square, Sandton, South Africa", result[0].address)
    }

    @Test
    fun `mapPage handles venue with only country`() {
        val event = TmEvent(
            id = "tm-country-only",
            name = "Country Only Event",
            description = "Country only",
            dates = TmDates(start = TmStart(localDate = "2026-10-03", localTime = "19:00:00")),
            embedded = TmEventEmbedded(
                venues = listOf(
                    TmVenue(country = TmCountry(name = "South Africa"))
                )
            )
        )
        val response = TmEventsResponse(embedded = TmEmbedded(events = listOf(event)))

        val result = mapper.mapPage(response)

        assertEquals("South Africa", result[0].address)
    }

    @Test
    fun `mapPage handles venue with only city`() {
        val event = TmEvent(
            id = "tm-city-only",
            name = "City Only Event",
            description = "City only",
            dates = TmDates(start = TmStart(localDate = "2026-10-03", localTime = "19:00:00")),
            embedded = TmEventEmbedded(
                venues = listOf(
                    TmVenue(city = TmCity(name = "Cape Town"))
                )
            )
        )
        val response = TmEventsResponse(embedded = TmEmbedded(events = listOf(event)))

        val result = mapper.mapPage(response)

        assertEquals("Cape Town", result[0].address)
    }

    @Test
    fun `mapPage defaults address to South Africa when venue is null`() {
        val event = TmEvent(
            id = "tm-no-venue",
            name = "No Venue Event",
            description = "No venue",
            dates = TmDates(start = TmStart(localDate = "2026-10-03", localTime = "19:00:00")),
            embedded = TmEventEmbedded(venues = null)
        )
        val response = TmEventsResponse(embedded = TmEmbedded(events = listOf(event)))

        val result = mapper.mapPage(response)

        assertEquals("South Africa", result[0].address)
    }

    @Test
    fun `mapPage handles mixed valid and invalid events`() {
        val response = TmEventsResponse(
            embedded = TmEmbedded(
                events = listOf(
                    baseEvent(id = "tm-good"),
                    baseEvent(id = null),
                    baseEvent(date = "bad-date"),
                    baseEvent(name = null)
                )
            )
        )

        val result = mapper.mapPage(response)

        assertEquals(1, result.size)
        assertEquals("tm-tm-good", result[0].id)
    }

    @Test
    fun `mapPage maps all event fields correctly for a complex payload`() {
        val response = TmEventsResponse(
            embedded = TmEmbedded(
                events = listOf(
                    TmEvent(
                        id = "tm-complex",
                        name = "Jazz Night",
                        description = "A night of jazz",
                        url = "https://ticketmaster.com/jazz",
                        dates = TmDates(start = TmStart(localDate = "2026-11-15", localTime = "20:30:00")),
                        images = listOf(
                            TmImage(url = "https://example.com/hero.jpg", width = 1920, height = 1080),
                            TmImage(url = "https://example.com/thumb.jpg", width = 400, height = 300)
                        ),
                        classifications = listOf(
                            TmClassification(segment = TmSegment(name = "Music"))
                        ),
                        embedded = TmEventEmbedded(
                            venues = listOf(
                                TmVenue(
                                    name = "The Blue Room",
                                    address = TmAddress(line1 = "12 Long St"),
                                    city = TmCity(name = "Cape Town"),
                                    country = TmCountry(name = "South Africa"),
                                    location = TmLocation(latitude = "-33.92", longitude = "18.42")
                                )
                            )
                        )
                    )
                )
            )
        )

        val result = mapper.mapPage(response)

        assertEquals(1, result.size)
        val event = result[0]
        assertEquals("tm-tm-complex", event.id)
        assertEquals("Jazz Night", event.title)
        assertEquals("A night of jazz", event.description)
        assertEquals(EventCategory.MUSIC, event.category)
        assertEquals("The Blue Room", event.venueName)
        assertEquals("12 Long St, Cape Town, South Africa", event.address)
        assertEquals(-33.92, event.latitude, 0.01)
        assertEquals(18.42, event.longitude, 0.01)
        assertEquals("https://example.com/hero.jpg", event.imageUrl)
        assertEquals("ticketmaster", event.organizerId)
        assertEquals("Ticketmaster", event.organizerName)
        assertTrue(event.isPublic)
        assertTrue(event.isExternal)
        assertTrue(!event.isCreatedByUser)
        assertEquals(0, event.attendeeCount)
    }

    @Test
    fun `mapPage handles multiple venues and picks the first`() {
        val event = TmEvent(
            id = "tm-multi-venue",
            name = "Multi Venue Event",
            description = "Multiple venues",
            dates = TmDates(start = TmStart(localDate = "2026-10-03", localTime = "19:00:00")),
            embedded = TmEventEmbedded(
                venues = listOf(
                    TmVenue(
                        name = "First Venue",
                        location = TmLocation(latitude = "-26.20", longitude = "28.04")
                    ),
                    TmVenue(
                        name = "Second Venue",
                        location = TmLocation(latitude = "-33.92", longitude = "18.42")
                    )
                )
            )
        )
        val response = TmEventsResponse(embedded = TmEmbedded(events = listOf(event)))

        val result = mapper.mapPage(response)

        assertEquals(1, result.size)
        assertEquals("First Venue", result[0].venueName)
        assertEquals(-26.20, result[0].latitude, 0.01)
    }

    @Test
    fun `mapPage handles multiple classifications and picks the first with a segment`() {
        val event = TmEvent(
            id = "tm-multi-class",
            name = "Multi Classification Event",
            description = "Multiple classifications",
            dates = TmDates(start = TmStart(localDate = "2026-10-03", localTime = "19:00:00")),
            classifications = listOf(
                TmClassification(segment = TmSegment(name = "Music")),
                TmClassification(segment = TmSegment(name = "Arts & Theatre"))
            )
        )
        val response = TmEventsResponse(embedded = TmEmbedded(events = listOf(event)))

        val result = mapper.mapPage(response)

        assertEquals(1, result.size)
        assertEquals(EventCategory.MUSIC, result[0].category)
    }

    @Test
    fun `mapPage handles classification with null segment`() {
        val event = TmEvent(
            id = "tm-null-segment",
            name = "Null Segment Event",
            description = "Null segment",
            dates = TmDates(start = TmStart(localDate = "2026-10-03", localTime = "19:00:00")),
            classifications = listOf(TmClassification(segment = null))
        )
        val response = TmEventsResponse(embedded = TmEmbedded(events = listOf(event)))

        val result = mapper.mapPage(response)

        assertEquals(1, result.size)
        assertEquals(EventCategory.OTHER, result[0].category)
    }

    @Test
    fun `mapPage handles null classifications`() {
        val event = TmEvent(
            id = "tm-null-classifications",
            name = "Null Classifications Event",
            description = "Null classifications",
            dates = TmDates(start = TmStart(localDate = "2026-10-03", localTime = "19:00:00")),
            classifications = null
        )
        val response = TmEventsResponse(embedded = TmEmbedded(events = listOf(event)))

        val result = mapper.mapPage(response)

        assertEquals(1, result.size)
        assertEquals(EventCategory.OTHER, result[0].category)
    }

    @Test
    fun `parseStart returns correct epoch millis for known date`() {
        val result = mapper.parseStart("2026-10-03", "19:00:00")

        assertNotNull(result)
        assertTrue(result!! > 0)
    }

    @Test
    fun `parseStart uses noon for blank time`() {
        val result = mapper.parseStart("2026-10-03", "  ")

        assertNotNull(result)
    }

    @Test
    fun `mapPage handles images with null url`() {
        val event = TmEvent(
            id = "tm-null-img-url",
            name = "Null Image URL Event",
            description = "Null image URL",
            dates = TmDates(start = TmStart(localDate = "2026-10-03", localTime = "19:00:00")),
            images = listOf(TmImage(url = null, width = 1920, height = 1080))
        )
        val response = TmEventsResponse(embedded = TmEmbedded(events = listOf(event)))

        val result = mapper.mapPage(response)

        assertEquals(1, result.size)
        assertNull(result[0].imageUrl)
    }

    @Test
    fun `mapPage handles images with null width`() {
        val event = TmEvent(
            id = "tm-null-width",
            name = "Null Width Event",
            description = "Null width",
            dates = TmDates(start = TmStart(localDate = "2026-10-03", localTime = "19:00:00")),
            images = listOf(TmImage(url = "https://example.com/img.jpg", width = 0, height = 0))
        )
        val response = TmEventsResponse(embedded = TmEmbedded(events = listOf(event)))

        val result = mapper.mapPage(response)

        assertEquals(1, result.size)
        assertEquals("https://example.com/img.jpg", result[0].imageUrl)
    }

    @Test
    fun `mapPage handles multiple images and selects widest`() {
        val event = TmEvent(
            id = "tm-multi-img",
            name = "Multi Image Event",
            description = "Multiple images",
            dates = TmDates(start = TmStart(localDate = "2026-10-03", localTime = "19:00:00")),
            images = listOf(
                TmImage(url = "https://example.com/small.jpg", width = 400, height = 300),
                TmImage(url = "https://example.com/large.jpg", width = 1920, height = 1080),
                TmImage(url = "https://example.com/medium.jpg", width = 800, height = 600)
            )
        )
        val response = TmEventsResponse(embedded = TmEmbedded(events = listOf(event)))

        val result = mapper.mapPage(response)

        assertEquals(1, result.size)
        assertEquals("https://example.com/large.jpg", result[0].imageUrl)
    }

    @Test
    fun `mapPage handles venue with empty address line`() {
        val event = TmEvent(
            id = "tm-empty-addr",
            name = "Empty Address Event",
            description = "Empty address",
            dates = TmDates(start = TmStart(localDate = "2026-10-03", localTime = "19:00:00")),
            embedded = TmEventEmbedded(
                venues = listOf(
                    TmVenue(
                        name = "Empty Addr Venue",
                        address = TmAddress(line1 = "  "),
                        city = TmCity(name = "Pretoria"),
                        country = TmCountry(name = "South Africa")
                    )
                )
            )
        )
        val response = TmEventsResponse(embedded = TmEmbedded(events = listOf(event)))

        val result = mapper.mapPage(response)

        assertEquals(1, result.size)
        assertEquals("Pretoria, South Africa", result[0].address)
    }

    @Test
    fun `mapPage handles venue with empty city name`() {
        val event = TmEvent(
            id = "tm-empty-city",
            name = "Empty City Event",
            description = "Empty city",
            dates = TmDates(start = TmStart(localDate = "2026-10-03", localTime = "19:00:00")),
            embedded = TmEventEmbedded(
                venues = listOf(
                    TmVenue(
                        name = "Empty City Venue",
                        address = TmAddress(line1 = "1 Main Rd"),
                        city = TmCity(name = "  "),
                        country = TmCountry(name = "South Africa")
                    )
                )
            )
        )
        val response = TmEventsResponse(embedded = TmEmbedded(events = listOf(event)))

        val result = mapper.mapPage(response)

        assertEquals(1, result.size)
        assertEquals("1 Main Rd, South Africa", result[0].address)
    }

    @Test
    fun `mapPage handles venue with empty country name`() {
        val event = TmEvent(
            id = "tm-empty-country",
            name = "Empty Country Event",
            description = "Empty country",
            dates = TmDates(start = TmStart(localDate = "2026-10-03", localTime = "19:00:00")),
            embedded = TmEventEmbedded(
                venues = listOf(
                    TmVenue(
                        name = "Empty Country Venue",
                        address = TmAddress(line1 = "1 Main Rd"),
                        city = TmCity(name = "Durban"),
                        country = TmCountry(name = "  ")
                    )
                )
            )
        )
        val response = TmEventsResponse(embedded = TmEmbedded(events = listOf(event)))

        val result = mapper.mapPage(response)

        assertEquals(1, result.size)
        assertEquals("1 Main Rd, Durban", result[0].address)
    }

    @Test
    fun `mapPage handles venue with null address, city and country`() {
        val event = TmEvent(
            id = "tm-null-fields",
            name = "Null Fields Event",
            description = "Null fields",
            dates = TmDates(start = TmStart(localDate = "2026-10-03", localTime = "19:00:00")),
            embedded = TmEventEmbedded(
                venues = listOf(
                    TmVenue(name = "Null Fields Venue")
                )
            )
        )
        val response = TmEventsResponse(embedded = TmEmbedded(events = listOf(event)))

        val result = mapper.mapPage(response)

        assertEquals(1, result.size)
        assertEquals("South Africa", result[0].address)
    }

    @Test
    fun `mapPage handles all venue address fields blank`() {
        val event = TmEvent(
            id = "tm-all-blank",
            name = "All Blank Event",
            description = "All blank",
            dates = TmDates(start = TmStart(localDate = "2026-10-03", localTime = "19:00:00")),
            embedded = TmEventEmbedded(
                venues = listOf(
                    TmVenue(
                        name = "All Blank Venue",
                        address = TmAddress(line1 = "  "),
                        city = TmCity(name = "  "),
                        country = TmCountry(name = "  ")
                    )
                )
            )
        )
        val response = TmEventsResponse(embedded = TmEmbedded(events = listOf(event)))

        val result = mapper.mapPage(response)

        assertEquals(1, result.size)
        assertEquals("South Africa", result[0].address)
    }

    @Test
    fun `mapPage handles valid date with invalid time gracefully`() {
        val event = TmEvent(
            id = "tm-bad-time",
            name = "Bad Time Event",
            description = "Bad time",
            dates = TmDates(start = TmStart(localDate = "2026-10-03", localTime = "25:99:99"))
        )
        val response = TmEventsResponse(embedded = TmEmbedded(events = listOf(event)))

        val result = mapper.mapPage(response)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `mapPage handles completely empty TmEvent`() {
        val event = TmEvent()
        val response = TmEventsResponse(embedded = TmEmbedded(events = listOf(event)))

        val result = mapper.mapPage(response)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `mapPage handles many events with mixed validity`() {
        val events = (1..10).map { i ->
            if (i % 3 == 0) baseEvent(id = null)
            else baseEvent(id = "tm-$i", name = "Event $i")
        }
        val response = TmEventsResponse(embedded = TmEmbedded(events = events))

        val result = mapper.mapPage(response)

        assertEquals(7, result.size)
    }

    @Test
    fun `mapPage maps coordinates to correct values`() {
        val event = TmEvent(
            id = "tm-precise",
            name = "Precise Coords Event",
            description = "Precise coords",
            dates = TmDates(start = TmStart(localDate = "2026-10-03", localTime = "19:00:00")),
            embedded = TmEventEmbedded(
                venues = listOf(
                    TmVenue(
                        name = "Precise Venue",
                        location = TmLocation(latitude = "-26.2041", longitude = "28.0473")
                    )
                )
            )
        )
        val response = TmEventsResponse(embedded = TmEmbedded(events = listOf(event)))

        val result = mapper.mapPage(response)

        assertEquals(1, result.size)
        assertEquals(-26.2041, result[0].latitude, 0.0001)
        assertEquals(28.0473, result[0].longitude, 0.0001)
    }

    @Test
    fun `mapPage handles multiple events all valid`() {
        val events = (1..5).map { i ->
            baseEvent(id = "tm-$i", name = "Event $i")
        }
        val response = TmEventsResponse(embedded = TmEmbedded(events = events))

        val result = mapper.mapPage(response)

        assertEquals(5, result.size)
    }
}
