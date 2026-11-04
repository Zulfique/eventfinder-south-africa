package com.eventfinder.app.data.remote

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Query building and response parsing for the free Wikimedia Commons fallback
 * that supplies artwork when an event's own site publishes none.
 */
class CommonsImageSearchTest {

    private fun search() = CommonsImageSearch(OkHttpClient())

    // ------------------------------------------------------------ queries

    @Test
    fun `queries the sport first and only falls back to the town after it`() {
        val queries = search().imageQueries("TRACN4 NATIONAL RALLY", "DULLSTROOM")

        // Subject-scoped queries (town, then country) must be exhausted before
        // a bare-town scenery photo is allowed to become the cover.
        assertEquals(
            listOf("rally DULLSTROOM", "rally South Africa", "DULLSTROOM", "DULLSTROOM South Africa"),
            queries
        )
    }

    @Test
    fun `falls back to the sport across south africa when the venue is a placeholder`() {
        val queries = search().imageQueries("SA RALLY-RAID CHAMPIONSHIP", "TBA\\, KZN")

        assertEquals(listOf("rally South Africa"), queries)
    }

    @Test
    fun `astronomy imagery loses to a usable second hit for place collisions`() {
        val json = """
            {"query":{"pages":{
              "1":{"index":1,"title":"Ceres - dwarf planet - Hubble.png","imageinfo":[{"mime":"image/png","width":1200,"url":"https://upload.wikimedia.org/a.jpg","thumburl":"https://upload.wikimedia.org/a.jpg","extmetadata":{"LicenseShortName":{"value":"CC BY 4.0"},"Artist":{"value":"NASA"}}}]},
              "2":{"index":2,"title":"Enduro riders near Ceres.jpg","imageinfo":[{"mime":"image/jpeg","width":1200,"url":"https://upload.wikimedia.org/b.jpg","thumburl":"https://upload.wikimedia.org/b.jpg","extmetadata":{"LicenseShortName":{"value":"CC0"},"Artist":{"value":"Sipho"}}}]}
            }}}
        """.trimIndent()

        val image = search().parseResults(json)

        assertNotNull(image)
        assertEquals("https://upload.wikimedia.org/b.jpg", image!!.url)
    }

    @Test
    fun `uses a category keyword plus the town for non-sport titles`() {
        val queries = search().imageQueries("JAZZ IN THE PARK", "DURBAN")

        assertEquals(
            listOf("music concert DURBAN", "music concert South Africa", "DURBAN", "DURBAN South Africa"),
            queries
        )
    }

    @Test
    fun `drops the venue prefix and province code from a location`() {
        val queries = search().imageQueries("CLUB CHAMPIONSHIP", "KILLARNEY OVAL TRACK\\, WC")

        assertEquals(
            listOf("motorsport KILLARNEY OVAL TRACK", "motorsport South Africa", "KILLARNEY OVAL TRACK", "KILLARNEY OVAL TRACK South Africa"),
            queries
        )
    }

    @Test
    fun `ignores placeholder locations`() {
        assertEquals(emptyList<String>(), search().imageQueries("ROUND 5", "TBA\\, KZN"))
        assertEquals(emptyList<String>(), search().imageQueries("ROUND 5", ""))
    }

    @Test
    fun `strips placeholder punctuation before matching`() {
        // Feeds emit "TBA-" with a trailing dash; untrimmed it slipped past
        // the placeholder list and Commons was searched for the town "TBA-".
        val queries = search().imageQueries("SA RALLY CHAMPIONSHIP", "TBA-, South Africa")
        assertTrue(queries.none { it.contains("TBA", ignoreCase = true) })
        assertEquals(listOf("rally South Africa"), queries)
    }

    @Test
    fun `trims trailing ellipses from town names`() {
        val queries = search().imageQueries("SA RALLY CHAMPIONSHIP", "BLOEMFONTEIN\u2026., South Africa")
        assertTrue(queries.any { it.contains("BLOEMFONTEIN") })
        assertTrue(queries.none { it.contains("\u2026") })
    }

    @Test
    fun `maps titles onto the sport Commons is likely to photograph`() {
        val s = search()
        assertEquals("rally", s.sportKeyword("SA RALLY-RAID CHAMPIONSHIP"))
        assertEquals("motocross", s.sportKeyword("ZONE 7 MX CHAMPIONSHIP"))
        assertEquals("motocross", s.sportKeyword("MOTOCROSS CLUB DAY"))
        assertEquals("karting", s.sportKeyword("KARTING CLUB CHAMPIONSHIP"))
        assertEquals("enduro", s.sportKeyword("KZN REGIONAL ENDURO"))
        assertEquals("motorsport", s.sportKeyword("AMSC 3HR & PE 200"))
        assertEquals("motorsport", s.sportKeyword("SAES NINE HOUR ENDURANCE"))
        assertEquals("motorsport", s.sportKeyword("HISTORIC RACE MEETING"))
        assertNull(s.sportKeyword("JOBURG JAZZ NIGHTS"))
    }

    // -------------------------------------------------------------- URLs

    @Test
    fun `keeps the query-string separators literal so MediaWiki returns JSON`() {
        val url = search().buildSearchUrl("rally DULLSTROOM")

        // Encoding the whole query string (the old bug) turns every `=` and `&`
        // into %3D/%26, and MediaWiki answers with an HTML help page.
        assertTrue("action must be a literal param: $url", url.contains("action=query"))
        assertTrue("format must be a literal param: $url", url.contains("format=json"))
        assertTrue("separator must stay literal: $url", url.contains("&generator=search"))
        assertTrue("missing key/value separator: $url", url.contains("iiprop="))
    }

    @Test
    fun `encodes the search terms and the filetype filter`() {
        val url = search().buildSearchUrl("rally DULLSTROOM")

        assertTrue("space must be percent-encoded: $url", url.contains("gsrsearch=rally%20DULLSTROOM%20filetype"))
        assertTrue("wrong host: $url", url.startsWith("https://commons.wikimedia.org/w/api.php?"))
    }

    // ----------------------------------------------------------- parsing

    /** Builds one MediaWiki search-result page object with Gson (always valid JSON). */
    private fun page(
        index: Int,
        title: String,
        mime: String = "image/jpeg",
        width: Int = 1600,
        licence: String = "CC BY-SA 4.0",
        thumb: String = "https://upload.wikimedia.org/x.jpg",
        categories: List<String> = emptyList()
    ): JsonObject = JsonObject().apply {
        addProperty("pageid", index)
        addProperty("index", index)
        addProperty("title", title)
        add("imageinfo", JsonArray().apply {
            add(JsonObject().apply {
                addProperty("url", "https://upload.wikimedia.org/original.jpg")
                addProperty("thumburl", thumb)
                addProperty("mime", mime)
                addProperty("width", width)
                add("extmetadata", JsonObject().apply {
                    add("LicenseShortName", JsonObject().apply { addProperty("value", licence) })
                    add("Artist", JsonObject().apply { addProperty("value", "<span>Jane Doe</span>") })
                })
            })
        })
        if (categories.isNotEmpty()) {
            add("categories", JsonArray().apply {
                categories.forEach { c ->
                    add(JsonObject().apply { addProperty("title", c) })
                }
            })
        }
    }

    @Test
    fun `photographs beat higher-ranked png diagrams but png still works alone`() {
        // Motorsport searches surface circuit-layout PNGs above real photos.
        val json = doc(
            page(1, "File:Prince George Circuit.png", mime = "image/png", thumb = "https://upload.wikimedia.org/diagram.png"),
            page(2, "File:Grand Prix JAR.jpg", thumb = "https://upload.wikimedia.org/photo.jpg")
        )
        assertEquals("https://upload.wikimedia.org/photo.jpg", search().parseResults(json)?.url)

        // When only a PNG qualifies it is still used — no image beats none.
        val pngOnly = doc(page(1, "File:Ceres town.png", mime = "image/png", thumb = "https://upload.wikimedia.org/town.png"))
        assertEquals("https://upload.wikimedia.org/town.png", search().parseResults(pngOnly)?.url)
    }

    @Test
    fun `rejects space-science subjects even with innocent titles`() {
        // "Ceres 01.jpg" is the dwarf planet, not the Western Cape town.
        assertNull(
            search().parseResults(
                doc(page(1, "File:Ceres 01.jpg", categories = listOf("Category:CC-BY-4.0", "Category:Ceres (dwarf planet)")))
            )
        )
        assertNull(
            search().parseResults(
                doc(page(1, "File:Sky view.jpg", categories = listOf("Category:Astronomy", "Category:PD NASA")))
            )
        )
        // A genuine town photograph keeps its place.
        assertNotNull(
            search().parseResults(
                doc(page(1, "File:Ceres, Western Cape.jpg", categories = listOf("Category:Ceres, Western Cape", "Category:Western Cape")))
            )
        )
    }

    /** Wraps page objects the way MediaWiki returns them. */
    private fun doc(vararg pages: JsonObject): String = JsonObject().apply {
        add("query", JsonObject().apply {
            add("pages", JsonObject().apply {
                pages.forEachIndexed { i, p -> add("page${(i + 1) * 10}", p) }
            })
        })
    }.toString()

    @Test
    fun `returns the best-ranked reusable image with a credit`() {
        val image = search().parseResults(doc(page(1, "File:Dullstroom rally.jpg")))

        assertNotNull(image)
        assertEquals("https://upload.wikimedia.org/x.jpg", image!!.url)
        assertEquals("Jane Doe (CC BY-SA 4.0)", image.credit)
    }

    @Test
    fun `honours relevance ranking over json order`() {
        // pageid 10 (index 1) is a logo; pageid 20 (index 2) is the real photo.
        // Key order and rank order disagree, so only rank-aware parsing wins.
        val json = doc(
            page(1, "File:Sponsor logo.jpg"),
            page(2, "File:Rally car.jpg", thumb = "https://upload.wikimedia.org/real.jpg")
        )

        assertEquals("https://upload.wikimedia.org/real.jpg", search().parseResults(json)?.url)
    }

    @Test
    fun `rejects maps flags and other town-search noise`() {
        for (title in listOf(
            "File:Dullstroom locator map.png",
            "File:Thembisa flag.svg",
            "File:Coat of arms of Polokwane.png",
            "File:Seal of Johannesburg.png"
        )) {
            assertNull("should reject $title", search().parseResults(doc(page(1, title))))
        }
    }

    @Test
    fun `rejects unusable files`() {
        assertNull(
            "non-jpeg/png",
            search().parseResults(doc(page(1, "File:Scan.tiff", mime = "image/tiff")))
        )
        assertNull(
            "small images",
            search().parseResults(doc(page(1, "File:Small.png", mime = "image/png", width = 120)))
        )
        assertNull(
            "licences that forbid reuse",
            search().parseResults(doc(page(1, "File:Private.jpg", licence = "All rights reserved")))
        )
    }

    @Test
    fun `accepts public domain and cc0 material`() {
        assertNotNull(search().parseResults(doc(page(1, "File:PD photo.jpg", licence = "Public domain"))))
        assertNotNull(search().parseResults(doc(page(1, "File:CC0 photo.jpg", licence = "CC0"))))
    }

    @Test
    fun `survives a malformed response`() {
        assertNull(search().parseResults("not json at all"))
        assertNull(search().parseResults("{}"))
    }
}
