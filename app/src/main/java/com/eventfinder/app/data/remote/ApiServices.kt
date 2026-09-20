package com.eventfinder.app.data.remote

import com.eventfinder.app.data.remote.dto.TmEventsResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit interface for the Ticketmaster Discovery API v2 (free tier).
 *
 * Discovery v2 returns events (`GET /discovery/v2/events.json`) and requires
 * only the free `apikey` query parameter — no server infrastructure needed.
 * We pass `countryCode=ZA` to scope results to South Africa.
 */
interface TicketmasterApi {

    @GET("discovery/v2/events.json")
    suspend fun getEvents(
        @Query("apikey") apiKey: String,
        @Query("countryCode") countryCode: String = "ZA",
        @Query("size") size: Int = 100,
        @Query("sort") sort: String = "date,asc",
        @Query("keyword") keyword: String? = null,
        @Query("classificationName") classificationName: String? = null
    ): TmEventsResponse

    @GET("discovery/v2/events.json")
    suspend fun searchEvents(
        @Query("apikey") apiKey: String,
        @Query("countryCode") countryCode: String = "ZA",
        @Query("size") size: Int = 50,
        @Query("keyword") keyword: String
    ): TmEventsResponse
}
