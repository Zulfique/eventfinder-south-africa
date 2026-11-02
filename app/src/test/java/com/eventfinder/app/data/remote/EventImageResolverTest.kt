package com.eventfinder.app.data.remote

import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EventImageResolverTest {
    private val resolver = EventImageResolver(OkHttpClient())

    @Test
    fun extractsOpenGraphImage() {
        val html = """
            <html><head>
            <meta property="og:image" content="https://example.com/cover.jpg" />
            </head><body></body></html>
        """.trimIndent()

        assertEquals(
            "https://example.com/cover.jpg",
            resolver.extractImage(html, "https://example.com/event")
        )
    }

    @Test
    fun extractsAticketRetinaImage() {
        val html = "<img data-src=\"https://s1.ticketm.net/dam/a/a.jpg_RETINA_PORTRAIT_16_9.jpg\" />"

        assertEquals(
            "https://s1.ticketm.net/dam/a/a.jpg_RETINA_PORTRAIT_16_9.jpg",
            resolver.extractImage(html, "https://za.aticket.net/event/x")
        )
    }

    @Test
    fun returnsNullWhenNoImageIsAvailable() {
        assertEquals(null, resolver.extractImage("<html><body>no image</body></html>", "https://example.com/event"))
    }

    // ---- Stock-query construction ----------------------------------------

    @Test
    fun `stock query keeps the subject and a real town`() {
        assertEquals(
            "cars event Kempton Park",
            resolver.stockQuery(event(category = "cars,karting", address = "Kempton Park, GP"))
        )
        assertEquals(
            "music event Cape Town",
            resolver.stockQuery(event(category = "music", address = "Green Point Park, Cape Town, South Africa"))
        )
    }

    @Test
    fun `stock query drops venues placeholders and provinces`() {
        // Venue-only address must not become the subject ("… hotel" used to
        // fetch a hotel bedroom for a car championship).
        assertEquals(
            "cars event South Africa",
            resolver.stockQuery(event(category = "cars", address = "Thaba Echo Hotel, South Africa"))
        )
        assertEquals(
            "cars event South Africa",
            resolver.stockQuery(event(category = "cars", address = "Rover Motorcycle Club"))
        )
        assertEquals(
            "rally event South Africa",
            resolver.stockQuery(event(category = "rally", address = "TBA-, South Africa"))
        )
    }

    @Test
    fun `stock topics constrain recognised categories only`() {
        assertTrue(resolver.stockTopics("cars").contains("car"))
        assertTrue(resolver.stockTopics("motorcycles").contains("motorcycle"))
        assertTrue(resolver.stockTopics("jazz concert").contains("concert"))
        assertTrue(resolver.stockTopics("food festival").contains("food"))
        // Unrecognisable subjects must not gate the stock tiers.
        assertEquals(emptySet<String>(), resolver.stockTopics("school open day"))
    }

    private fun event(category: String, address: String) = com.eventfinder.app.data.remote.model.RemoteEvent(
        source = "test",
        sourceId = "1",
        title = "Championship round",
        description = "",
        category = category,
        startDate = 0L,
        endDate = 0L,
        venueName = "",
        address = address,
        latitude = null,
        longitude = null,
        imageUrl = null,
        sourceUrl = null,
        organizerName = null
    )
}
