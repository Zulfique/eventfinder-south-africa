package com.eventfinder.app.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Contract tests for the tier 4-5 stock-photo parsers in the event-image
 * pipeline. A malformed or quota-shaped response must degrade to null rather
 * than throw out of a sync.
 */
class StockImageSearchTest {

    private val search = StockImageSearch(httpClient = null)

    private val unsplashJson = """
        {
          "id": "abc123",
          "urls": { "raw": "https://images.unsplash.com/photo-1", "regular": "https://images.unsplash.com/photo-1?ixid=x" },
          "user": { "name": "Thando Dlamini" },
          "links": { "html": "https://unsplash.com/photos/abc123" }
        }
    """.trimIndent()

    private val unsplashRandomJson = """{ "results": [$unsplashJson] }"""

    private val pixabayJson = """
        {
          "totalHits": 2,
          "hits": [
            {
              "largeImageURL": "https://cdn.pixabay.com/photo/large1.jpg",
              "webformatURL": "https://cdn.pixabay.com/photo/small1.jpg",
              "imageWidth": 1920, "imageHeight": 1080,
              "user": "Mike", "pageURL": "https://pixabay.com/images/photos/1/"
            },
            {
              "largeImageURL": "https://cdn.pixabay.com/photo/large0.jpg",
              "webformatURL": "https://cdn.pixabay.com/photo/small0.jpg",
              "imageWidth": 640, "imageHeight": 360,
              "user": "Small", "pageURL": "https://pixabay.com/images/photos/0/"
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `unsplash single photo parses with credit`() {
        val image = search.parseUnsplash(unsplashJson)
        assertNotNull(image)
        assertTrue(image!!.url.startsWith("https://images.unsplash.com/photo-1"))
        assertEquals("Photo by Thando Dlamini on Unsplash (https://unsplash.com/photos/abc123)", image.credit)
    }

    @Test
    fun `unsplash random endpoint array parses first result`() {
        val image = search.parseUnsplash(unsplashRandomJson)
        assertNotNull(image)
        assertTrue(image!!.credit.contains("Thando Dlamini"))
    }

    @Test
    fun `unsplash error body parses to null`() {
        assertNull(search.parseUnsplash("""{"errors":["Rate Limit Exceeded"]}"""))
    }

    @Test
    fun `pixabay prefers large hits but falls back to first`() {
        val image = search.parsePixabay(pixabayJson)
        assertNotNull(image)
        assertEquals("https://cdn.pixabay.com/photo/large1.jpg", image!!.url)
        assertEquals("Photo by Mike on Pixabay (https://pixabay.com/images/photos/1/)", image.credit)
    }

    @Test
    fun `pixabay empty hits parse to null`() {
        assertNull(search.parsePixabay("""{"totalHits":0,"hits":[]}"""))
    }

    @Test
    fun `pixabay category maps free-text queries`() {
        assertEquals("music", search.pixabayCategory("jazz concert cape town"))
        assertEquals("food", search.pixabayCategory("food festival durban"))
        assertEquals("sports", search.pixabayCategory("marathon race pretoria"))
        assertEquals("business", search.pixabayCategory("tech conference johannesburg"))
        assertNull(search.pixabayCategory("art exhibition gqeberha"))
    }

    @Test
    fun `null client disables stock lookup entirely`() = runBlockingCompat {
        assertNull(search.findImage("music festival cape town"))
    }
}

private fun runBlockingCompat(block: suspend () -> Unit) =
    kotlinx.coroutines.runBlocking { block() }

private fun assertTrue(condition: Boolean) {
    org.junit.Assert.assertTrue(condition)
}
