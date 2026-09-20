package com.eventfinder.app.data.remote

import com.eventfinder.app.data.remote.dto.NominatimPlace
import com.eventfinder.app.data.remote.dto.OverpassResponse
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * OpenStreetMap Nominatim.
 *
 * No API key/token.
 *
 * Nominatim's public server is rate-limited.
 * This app therefore uses it only for explicit user searches/reverse lookups.
 */
interface NominatimApi {

    @GET("search")
    suspend fun search(
        @Query("q")
        query: String,

        @Query("format")
        format: String = "jsonv2",

        @Query("limit")
        limit: Int = 5,

        @Query("addressdetails")
        addressDetails: Int = 1
    ): List<NominatimPlace>

    @GET("reverse")
    suspend fun reverse(
        @Query("lat")
        latitude: Double,

        @Query("lon")
        longitude: Double,

        @Query("format")
        format: String = "jsonv2",

        @Query("addressdetails")
        addressDetails: Int = 1,

        @Query("zoom")
        zoom: Int = 18
    ): NominatimPlace
}


/**
 * OpenStreetMap Overpass.
 *
 * No API key/token.
 */
interface OverpassApi {

    @FormUrlEncoded
    @POST("api/interpreter")
    suspend fun query(
        @Field("data")
        query: String
    ): OverpassResponse
}
