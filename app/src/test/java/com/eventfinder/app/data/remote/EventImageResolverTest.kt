package com.eventfinder.app.data.remote

import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
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
}
