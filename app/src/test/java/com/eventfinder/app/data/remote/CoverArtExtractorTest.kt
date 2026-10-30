package com.eventfinder.app.data.remote

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The parsing half of the cover-art feature: given the markup real event sites
 * publish, pick the right share image and reject logos / tracking pixels.
 */
class CoverArtExtractorTest {

    private val page = "https://example.co.za/events/rally-2026"

    private fun find(html: String, baseUrl: String = page) =
        CoverArtExtractor.findCoverImageUrl(html, baseUrl)

    @Test
    fun `prefers the og share image over everything else`() {
        val html = """
            <html><head>
            <meta property="og:image" content="https://cdn.example.co.za/cover.jpg"/>
            <meta name="twitter:image" content="https://cdn.example.co.za/twitter.jpg"/>
            </head></html>
        """.trimIndent()

        assertEquals("https://cdn.example.co.za/cover.jpg", find(html))
    }

    @Test
    fun `reads the share image when the content attribute comes first`() {
        val html = """<meta content="https://cdn.example.co.za/cover2.jpg" property="og:image">"""

        assertEquals("https://cdn.example.co.za/cover2.jpg", find(html))
    }

    @Test
    fun `falls back to twitter image`() {
        val html = """<meta name="twitter:image" content="https://cdn.example.co.za/tw.png">"""

        assertEquals("https://cdn.example.co.za/tw.png", find(html))
    }

    @Test
    fun `falls back to link image_src`() {
        val html = """<link rel="image_src" href="https://cdn.example.co.za/src.jpg">"""

        assertEquals("https://cdn.example.co.za/src.jpg", find(html))
    }

    @Test
    fun `reads a JSON-LD image object`() {
        val html = """
            <script type="application/ld+json">
            {"@type":"Event","name":"Rally","image":{"@type":"ImageObject","url":"https://cdn.example.co.za/ld.jpg"}}
            </script>
        """.trimIndent()

        assertEquals("https://cdn.example.co.za/ld.jpg", find(html))
    }

    @Test
    fun `resolves a relative share image against the page`() {
        val html = """<meta property="og:image" content="/images/cover.jpg">"""

        assertEquals("https://example.co.za/images/cover.jpg", find(html))
    }

    @Test
    fun `resolves a root-relative image against the host`() {
        val html = """<meta property="og:image" content="//cdn.example.co.za/c.jpg">"""

        assertEquals("https://cdn.example.co.za/c.jpg", find(html))
    }

    @Test
    fun `skips logos and icons when falling back to img tags`() {
        val html = """
            <body>
            <img src="/static/logo.png" alt="logo">
            <img src="/static/sprite.svg">
            <img data-src="/media/rally-front.jpg">
            </body>
        """.trimIndent()

        assertEquals("https://example.co.za/media/rally-front.jpg", find(html))
    }

    @Test
    fun `ignores tracking pixels and data uris`() {
        val html = """
            <meta property="og:image" content="data:image/png;base64,AAAA"/>
            <img src="https://tracker.example.com/pixel.gif?x=1">
        """.trimIndent()

        assertNull(find(html))
    }

    @Test
    fun `never picks a site loading spinner as cover art`() {
        // The Events Calendar puts this GIF first on every event page; picking
        // it rendered an animated buffer wheel on every card.
        val html = """
            <img src="/static/logo.png" alt="site">
            <img class="tribe-events-spinner-medium" src="https://www.motorsport.co.za/wp-content/plugins/the-events-calendar/src/resources/images/tribe-loading.gif" alt="Loading Events">
            <img src="https://www.motorsport.co.za/wp-content/plugins/events-calendar-pro/src/resources/images/tribe-related-events-placeholder.png" alt="Event">
        """.trimIndent()

        assertNull(find(html))
    }

    @Test
    fun `picks a real upload after skipping spinners and logos`() {
        val html = """
            <img src="/wp-content/uploads/2023/02/logo_MSA.png">
            <img src="/wp-content/plugins/x/images/spinner.gif">
            <img src="/wp-content/uploads/2026/01/rally-car.jpg">
        """.trimIndent()

        assertEquals("https://example.co.za/wp-content/uploads/2026/01/rally-car.jpg", find(html))
    }

    @Test
    fun `returns null when the page has no artwork at all`() {
        assertNull(find("<html><body><p>No images here</p></body></html>"))
        assertNull(find(""))
    }

    @Test
    fun `a page with no artwork is remembered without throwing`() = runTest {
        // Regression: caching the miss put null into a ConcurrentHashMap, whose
        // NPE aborted the entire feed sync that calls this.
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("<html><body>nothing here</body></html>"))
            server.enqueue(MockResponse().setBody("<html><body>nothing here</body></html>"))

            val extractor = CoverArtExtractor(OkHttpClient())
            val page = server.url("/event/1").toString()

            assertNull(extractor.extract(page))
            assertNull("memoised miss returns null again", extractor.extract(page))
            assertEquals("second call must not hit the network", 1, server.requestCount)
        }
    }

    @Test
    fun `a page with an og image returns an absolute url`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody(
                    """<html><head><meta property="og:image" content="/img/cover.jpg"></head></html>"""
                )
            )

            val extractor = CoverArtExtractor(OkHttpClient())
            val found = extractor.extract(server.url("/event/2").toString())

            assertEquals("${server.url("/")}img/cover.jpg", found)
        }
    }
}
