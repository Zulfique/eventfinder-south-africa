package com.eventfinder.app.data.remote

import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Test

class RelativeImageResolverTest {
    private val resolver = EventImageResolver(OkHttpClient())

    @Test
    fun makesRelativeUrlsAbsolute() {
        assertEquals(
            "https://za.ticketm.net/images/cover.jpg",
            resolver.extractImage("""<meta property="og:image" content="/images/cover.jpg" />""", "https://za.ticketm.net/event/abc")
        )
    }
}
