package com.eventfinder.app.data.sources

import com.eventfinder.app.data.remote.dto.PublicJsonEventDto
import com.eventfinder.app.data.remote.dto.PublicJsonLocationDto
import com.eventfinder.app.data.remote.dto.PublicJsonVenueDto
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PublicJsonEventMapperTest {

    private lateinit var gson: Gson

    @Before
    fun setup() {
        gson = GsonBuilder().setLenient().create()
    }

    @Test
    fun `ardent africa location string parses as venue name`() {
        val json = """
            {
                "id": "test-123",
                "title": "Test Event",
                "description": "A test",
                "category": "Education & Academic",
                "start_date": "2026-09-25T10:00:00+00:00",
                "end_date": "2026-09-25T17:00:00+00:00",
                "location": "Adentan",
                "image_url": "https://example.com/img.jpg",
                "event_type": "conference"
            }
        """.trimIndent()

        val dto = gson.fromJson(json, PublicJsonEventDto::class.java)

        assertEquals("test-123", dto.resolvedId())
        assertEquals("Test Event", dto.resolvedTitle())
        assertEquals("A test", dto.resolvedDescription())
        assertEquals("Education & Academic", dto.resolvedCategory())
        assertEquals("2026-09-25T10:00:00+00:00", dto.resolvedStart())
        assertEquals("2026-09-25T17:00:00+00:00", dto.resolvedEnd())
        assertEquals("https://example.com/img.jpg", dto.resolvedImageUrl())

        val location = dto.location
        assertNotNull(location)
        assertEquals("Adentan", location?.resolvedName())
    }

    @Test
    fun `ardent africa full response parses all fields`() {
        val json = """
            {
                "data": [
                    {
                        "id": "a8541806-4537-435f-bf92-18c51d5ef326",
                        "slug": "infrayouth-conference",
                        "title": "InfraYouth Conference",
                        "description": "The Youth for Infrastructure Development in Africa Conference",
                        "event_type": "conference",
                        "category": "Education & Academic",
                        "category_id": "4d0d5c94-8150-475e-9b19-d163e99809d7",
                        "start_date": "2026-07-22T15:53:00+00:00",
                        "end_date": "2026-07-25T15:53:00+00:00",
                        "timezone": "Africa/Accra",
                        "location": "Adentan",
                        "is_online": true,
                        "attendance_mode": "hybrid",
                        "image_url": "https://example.com/event.jpg",
                        "is_paid": false,
                        "currency": "GHS",
                        "tags": [],
                        "is_featured": false,
                        "created_at": "2026-07-22T15:53:48.457046+00:00"
                    }
                ],
                "page": 1,
                "limit": 2,
                "total": 1
            }
        """.trimIndent()

        val root = gson.fromJson(json, com.google.gson.JsonObject::class.java)
        val dataArray = root.getAsJsonArray("data")
        val dto = gson.fromJson(dataArray[0], PublicJsonEventDto::class.java)

        assertEquals("a8541806-4537-435f-bf92-18c51d5ef326", dto.resolvedId())
        assertEquals("InfraYouth Conference", dto.resolvedTitle())
        assertTrue(dto.resolvedDescription().contains("Youth for Infrastructure"))
        assertEquals("Education & Academic", dto.resolvedCategory())
        assertNotNull(dto.resolvedStart())
        assertNotNull(dto.resolvedEnd())

        val location = dto.location
        assertNotNull(location)
        assertEquals("Adentan", location?.resolvedName())
    }

    @Test
    fun `object-style location still works`() {
        val json = """
            {
                "id": "obj-loc-1",
                "title": "Venue Event",
                "description": "Has venue-style location",
                "category": "Music",
                "start_date": "2026-10-01T18:00:00+02:00",
                "end_date": "2026-10-01T22:00:00+02:00",
                "location": {
                    "name": "Grand Arena",
                    "address": "123 Main St",
                    "city": "Cape Town",
                    "latitude": -33.9249,
                    "longitude": 18.4241
                }
            }
        """.trimIndent()

        val dto = gson.fromJson(json, PublicJsonEventDto::class.java)
        val location = dto.location!!

        assertEquals("Grand Arena", location.resolvedName())
        assertEquals("123 Main St, Cape Town", location.resolvedAddress())
        assertEquals(-33.9249, location.resolvedLatitude()!!, 0.001)
        assertEquals(18.4241, location.resolvedLongitude()!!, 0.001)
    }

    @Test
    fun `null location is handled gracefully`() {
        val json = """
            {
                "id": "no-loc-1",
                "title": "No Location Event",
                "description": "No location at all",
                "category": "Food",
                "start_date": "2026-10-05T12:00:00Z",
                "end_date": "2026-10-05T15:00:00Z"
            }
        """.trimIndent()

        val dto = gson.fromJson(json, PublicJsonEventDto::class.java)
        assertNull(dto.location)
    }

    @Test
    fun `mapper produces valid RemoteEvent with null coordinates`() {
        val dto = PublicJsonEventDto(
            id = "ardent-1",
            title = "Test",
            description = "Test event",
            category = "Technology",
            startDate = "2026-10-01T10:00:00Z",
            endDate = "2026-10-01T18:00:00Z",
            imageUrl = "https://example.com/img.jpg"
        )

        val event = PublicJsonEventMapper.map("ardent-africa", dto)

        assertNotNull(event)
        assertEquals("ardent-1", event?.sourceId)
        assertEquals("ardent-africa", event?.source)
        assertEquals("Test", event?.title)
        assertNull(event?.latitude)
        assertNull(event?.longitude)
    }
}
