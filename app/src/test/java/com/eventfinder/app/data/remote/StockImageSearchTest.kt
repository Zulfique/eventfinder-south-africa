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
    fun `random endpoint answers an array and still parses`() {
        val json = """[{"urls":{"regular":"https://images.unsplash.com/photo-1?ixid=abc"},"user":{"name":"Sipho"},"links":{"html":"https://unsplash.com/photos/1"}}]"""
        val image = search.parseUnsplash(json)
        assertNotNull(image)
        assertEquals("https://images.unsplash.com/photo-1?w=1200&q=80&fm=jpg", image!!.url)
        assertTrue(image.credit.contains("Sipho"))
    }

    @Test
    fun `null client disables stock lookup entirely`() = runBlockingCompat {
        assertNull(search.findImage("music festival cape town"))
    }

    // ---- Topic relevance gate -------------------------------------------

    @Test
    fun `topic matching is word-based and tolerates plurals`() {
        val motorcycle = listOf("motorcycle")
        assertTrue(search.isRelevant("Motorcycles racing at the track", motorcycle))
        // "moto" is one of the motorcycle topics, so rider variants match too.
        assertTrue(search.isRelevant("a motorcyclist leans into a turn", listOf("motorcycle", "moto")))
        assertFalse(search.isRelevant("mars rover exploring the red planet", motorcycle))
        assertFalse(search.isRelevant("a luxurious hotel bedroom", listOf("car")))
        // A topic only counts at a word start: "car" must not hit "scarce".
        assertFalse(search.isRelevant("a scarce campsite", listOf("car")))
    }

    @Test
    fun `unknown categories and blank descriptions accept the hit`() {
        assertTrue(search.isRelevant("anything at all", emptyList()))
        assertTrue(search.isRelevant(null, listOf("car")))
        assertTrue(search.isRelevant("", listOf("car")))
    }

    @Test
    fun `pixabay skips off-topic hits and prefers a relevant large one`() {
        val json = """
            {
              "totalHits": 3,
              "hits": [
                {
                  "largeImageURL": "https://cdn.pixabay.com/photo/bedroom.jpg",
                  "imageWidth": 1920,
                  "tags": "hotel, bedroom, luxurious",
                  "user": "Host", "pageURL": "https://pixabay.com/hotel"
                },
                {
                  "largeImageURL": "https://cdn.pixabay.com/photo/car.jpg",
                  "imageWidth": 1920,
                  "tags": "car, racing, motorsport",
                  "user": "Racer", "pageURL": "https://pixabay.com/car"
                },
                {
                  "largeImageURL": "https://cdn.pixabay.com/photo/smallcar.jpg",
                  "imageWidth": 640,
                  "tags": "cars",
                  "user": "Small", "pageURL": "https://pixabay.com/smallcar"
                }
              ]
            }
        """.trimIndent()

        val image = search.parsePixabay(json, listOf("car", "race"))
        assertNotNull(image)
        assertEquals("https://cdn.pixabay.com/photo/car.jpg", image!!.url)
    }

    @Test
    fun `pixabay returns null when no hit fits the event`() {
        val json = """
            {"totalHits":1,"hits":[
              {"largeImageURL":"https://cdn.pixabay.com/photo/mars.jpg","imageWidth":1920,
               "tags":"mars, rover, space","user":"N","pageURL":"https://pixabay.com/m"}
            ]}
        """.trimIndent()

        assertNull(search.parsePixabay(json, listOf("motorcycle", "rider")))
        // No topics (unrecognised category) still accepts the hit.
        assertNotNull(search.parsePixabay(json))
    }

    @Test
    fun `unsplash array skips off-topic photos for the next candidate`() {
        val json = """
            [
              {
                "urls": {"regular": "https://images.unsplash.com/photo-mars?ixid=a"},
                "alt_description": "a mars rover on the red planet",
                "user": {"name": "NASA Fan"},
                "links": {"html": "https://unsplash.com/photos/mars"}
              },
              {
                "urls": {"regular": "https://images.unsplash.com/photo-bike?ixid=b"},
                "alt_description": "motorcycle parked on a coastal road",
                "user": {"name": "Zanele"},
                "links": {"html": "https://unsplash.com/photos/bike"}
              }
            ]
        """.trimIndent()

        val image = search.parseUnsplash(json, listOf("motorcycle", "rider"))
        assertNotNull(image)
        assertTrue(image!!.url.startsWith("https://images.unsplash.com/photo-bike"))
        assertTrue(image.credit.contains("Zanele"))

        // Same payload, wrong event: every photo is off-topic -> null.
        assertNull(search.parseUnsplash(json, listOf("food", "wine")))
    }
}

private fun runBlockingCompat(block: suspend () -> Unit) =
    kotlinx.coroutines.runBlocking { block() }

private fun assertTrue(condition: Boolean) {
    org.junit.Assert.assertTrue(condition)
}

private fun assertFalse(condition: Boolean) {
    org.junit.Assert.assertFalse(condition)
}
