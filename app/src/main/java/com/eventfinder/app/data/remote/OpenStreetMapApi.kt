package com.eventfinder.app.data.remote

import com.eventfinder.app.data.remote.dto.OsmOverpassResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * OpenStreetMap services used by EventFinder.
 *
 * No API key or token is required for these public read endpoints.
 *
 * Overpass is used for nearby OSM objects such as:
 * - theatres
 * - cinemas
 * - stadiums
 * - arts centres
 * - museums
 * - galleries
 * - community centres
 * - attractions
 * - event venues
 *
 * This is venue/place discovery, not a replacement for a dedicated
 * future-event catalogue.
 */
interface OpenStreetMapApi {

    @GET("api/interpreter")
    suspend fun queryOverpass(
        @Query("data") query: String
    ): OsmOverpassResponse
}
