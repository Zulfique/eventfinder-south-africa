package com.eventfinder.app.data.repository

import com.eventfinder.app.data.remote.OpenStreetMapApi
import com.eventfinder.app.data.remote.dto.OsmCenter
import com.eventfinder.app.data.remote.dto.OsmElement
import com.eventfinder.app.data.remote.dto.OsmOverpassResponse
import com.eventfinder.app.domain.model.EventCategory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenStreetMapRepositoryTest {

    private class FakeApi : OpenStreetMapApi {
        var lastQuery: String? = null

        override suspend fun queryOverpass(
            query: String
        ): OsmOverpassResponse {
            lastQuery = query

            return OsmOverpassResponse(
                elements = listOf(
                    OsmElement(
                        type = "node",
                        id = 123L,
                        lat = -33.9249,
                        lon = 18.4241,
                        tags = mapOf(
                            "name" to "Cape Town Theatre",
                            "amenity" to "theatre",
                            "addr:city" to "Cape Town"
                        )
                    ),
                    OsmElement(
                        type = "way",
                        id = 456L,
                        center = OsmCenter(
                            lat = -33.9250,
                            lon = 18.4250
                        ),
                        tags = mapOf(
                            "name" to "Cape Town Stadium",
                            "leisure" to "stadium"
                        )
                    )
                )
            )
        }
    }

    @Test
    fun `nearby venue query returns mapped OSM venues`() = runTest {

        val api = FakeApi()
        val repository = OpenStreetMapRepository(api)

        val result = repository.findNearbyVenues(
            latitude = -33.9249,
            longitude = 18.4241
        )

        assertTrue(result.isSuccess)

        val venues = result.getOrThrow()

        assertEquals(2, venues.size)

        assertEquals(
            "Cape Town Theatre",
            venues[0].name
        )

        assertEquals(
            EventCategory.ARTS,
            venues[0].category
        )

        assertEquals(
            "Cape Town Stadium",
            venues[1].name
        )

        assertEquals(
            EventCategory.SPORTS,
            venues[1].category
        )
    }

    @Test
    fun `query is geographically bounded`() = runTest {

        val api = FakeApi()
        val repository = OpenStreetMapRepository(api)

        repository.findNearbyVenues(
            latitude = -33.9249,
            longitude = 18.4241,
            radiusMeters = 10_000
        )

        val query = api.lastQuery!!

        assertTrue(
            query.contains("around:10000")
        )

        assertTrue(
            query.contains("-33.924900")
        )

        assertTrue(
            query.contains("18.424100")
        )
    }
}
