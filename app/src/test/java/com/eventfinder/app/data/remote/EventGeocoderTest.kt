package com.eventfinder.app.data.remote

import com.eventfinder.app.data.remote.dto.OmGeocodingResponse
import com.eventfinder.app.data.remote.dto.OmGeocodingResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EventGeocoderTest {

    private class FakeGeocodingApi(
        private val results: List<OmGeocodingResult>
    ) : OpenMeteoGeocodingApi {
        override suspend fun geocode(
            name: String,
            count: Int,
            language: String,
            format: String
        ): OmGeocodingResponse = OmGeocodingResponse(results)

        override suspend fun elevation(
            latitude: String,
            longitude: String
        ): com.eventfinder.app.data.remote.dto.OmElevationResponse =
            com.eventfinder.app.data.remote.dto.OmElevationResponse(elevation = emptyList())
    }

    private fun geocoder(vararg results: OmGeocodingResult): EventGeocoder =
        EventGeocoder(FakeGeocodingApi(results.toList()))

    @Test
    fun `returns null when the api only returns a non South African result`() = runTest {
        val coder = geocoder(
            OmGeocodingResult(
                name = "Kigali", latitude = -1.94, longitude = 30.06,
                country = "Rwanda", countryCode = "RW"
            )
        )
        assertNull(coder.geocode("Kigali"))
    }

    @Test
    fun `picks the South African result from mixed results`() = runTest {
        val coder = geocoder(
            OmGeocodingResult(
                name = "Cape Girardeau", latitude = 37.30, longitude = -89.51,
                country = "United States", countryCode = "US"
            ),
            OmGeocodingResult(
                name = "Cape Town", latitude = -33.92, longitude = 18.42,
                country = "South Africa", countryCode = "ZA"
            )
        )
        val coords = coder.geocode("Cape Town")
        assertNotNull(coords)
        assertEquals(-33.92, coords!!.first, 0.001)
        assertEquals(18.42, coords.second, 0.001)
    }

    @Test
    fun `accepts a result recognised via the full country name`() = runTest {
        val coder = geocoder(
            OmGeocodingResult(
                name = "Durban", latitude = -29.86, longitude = 31.03,
                country = "South Africa", countryCode = null
            )
        )
        val coords = coder.geocode("Durban")
        assertNotNull(coords)
        assertEquals(-29.86, coords!!.first, 0.001)
    }

    @Test
    fun `accepts a result recognised via the country code`() = runTest {
        val coder = geocoder(
            OmGeocodingResult(
                name = "Pretoria", latitude = -25.75, longitude = 28.19,
                country = null, countryCode = "ZA"
            )
        )
        val coords = coder.geocode("Pretoria")
        assertNotNull(coords)
        assertEquals(-25.75, coords!!.first, 0.001)
    }

    // ------------------------------------------------- candidate query strategy

    /** Fake that only answers for the known keys and records every query. */
    private class SelectiveGeocodingApi(
        private val answers: Map<String, OmGeocodingResult>
    ) : OpenMeteoGeocodingApi {
        val queries = mutableListOf<String>()

        override suspend fun geocode(
            name: String,
            count: Int,
            language: String,
            format: String
        ): OmGeocodingResponse {
            queries += name
            val answer = answers.entries.firstOrNull { (key, _) ->
                name.startsWith(key, ignoreCase = true)
            }
            return OmGeocodingResponse(answer?.let { listOf(it.value) } ?: emptyList())
        }

        override suspend fun elevation(
            latitude: String,
            longitude: String
        ): com.eventfinder.app.data.remote.dto.OmElevationResponse =
            com.eventfinder.app.data.remote.dto.OmElevationResponse(elevation = emptyList())
    }

    private fun za(name: String, lat: Double, lng: Double) =
        OmGeocodingResult(
            name = name, latitude = lat, longitude = lng,
            country = "South Africa", countryCode = "ZA"
        )

    @Test
    fun `falls back to the town when the venue name is unknown`() = runTest {
        val api = SelectiveGeocodingApi(mapOf("Pretoria" to za("Pretoria", -25.75, 28.19)))

        val coords = EventGeocoder(api).geocode("SunBet Arena Time Square, Pretoria")

        assertNotNull("venue string should fall back to its town", coords)
        assertEquals(-25.75, coords!!.first, 0.001)
        assertTrue(api.queries.size > 1)
        assertTrue(
            "a town-only query should be attempted: ${api.queries}",
            api.queries.any { it.startsWith("Pretoria") }
        )
    }

    @Test
    fun `unescapes ICS commas and finds the town`() = runTest {
        val api = SelectiveGeocodingApi(mapOf("GQEBERHA" to za("GQEBERha", -33.96, 25.60)))

        val coords = EventGeocoder(api)
            .geocode("MISSION ROAD\\, GREENBUSHES\\, GQEBERHA\\, EC")

        assertNotNull(coords)
        assertEquals(25.60, coords!!.second, 0.001)
        assertTrue(api.queries.any { it.startsWith("GQEBERHA") })
    }

    @Test
    fun `never queries a province code or placeholder on its own`() {
        val coder = EventGeocoder(FakeGeocodingApi(emptyList()))

        val candidates = coder.candidateQueries("KILLARNEY OVAL TRACK\\, WC")

        assertTrue(candidates.isNotEmpty())
        assertTrue(
            "province codes are not places: $candidates",
            candidates.none { it.startsWith("WC") || it.startsWith("EC") }
        )
        assertTrue(
            "province codes are not places: $candidates",
            coder.candidateQueries("TBA\\, KZN").none { it.startsWith("KZN") }
        )
        assertTrue("bounded API usage", candidates.size <= 5)
    }

    @Test
    fun `cleans feed noise before querying`() {
        val coder = EventGeocoder(FakeGeocodingApi(emptyList()))

        val candidates = coder.candidateQueries("FORMULA K KART CIRCUIT\u2026\u2026")

        assertTrue(candidates.isNotEmpty())
        assertEquals("trailing ellipsis trimmed", "FORMULA K KART CIRCUIT, South Africa", candidates.first())
    }

    @Test
    fun `does not retry a location that already failed`() = runTest {
        val api = SelectiveGeocodingApi(emptyMap())
        val coder = EventGeocoder(api)

        assertNull(coder.geocode("Nowhere Special, EC"))
        val queriesAfterFirst = api.queries.size
        assertTrue(queriesAfterFirst in 1..5)

        assertNull(coder.geocode("Nowhere Special, EC"))
        assertEquals("failed lookup is memoised", queriesAfterFirst, api.queries.size)
    }
}