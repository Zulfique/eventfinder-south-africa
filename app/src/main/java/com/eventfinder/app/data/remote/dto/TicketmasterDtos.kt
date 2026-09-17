package com.eventfinder.app.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Gson DTOs for the Ticketmaster Discovery API v2 (free developer tier).
 *
 * The prototype uses the South Africa event catalogue (`countryCode=ZA`), which
 * gives the app genuine local events for Johannesburg, Cape Town, Durban, etc.
 * Only the fields needed by the app are modelled; unknown JSON fields are
 * ignored by Gson.
 *
 * API docs:
 *  - https://developer.ticketmaster.com/products-and-docs/apis/discovery-api/v2/
 */
data class TmEventsResponse(
    @SerializedName("_embedded") val embedded: TmEmbedded? = null,
    @SerializedName("page") val page: TmPage? = null
)

data class TmEmbedded(
    @SerializedName("events") val events: List<TmEvent>? = null
)

data class TmPage(
    @SerializedName("totalElements") val totalElements: Int = 0
)

data class TmEvent(
    @SerializedName("id") val id: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("url") val url: String? = null,
    @SerializedName("images") val images: List<TmImage>? = null,
    @SerializedName("dates") val dates: TmDates? = null,
    @SerializedName("classifications") val classifications: List<TmClassification>? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("_embedded") val embedded: TmEventEmbedded? = null
)

data class TmEventEmbedded(
    @SerializedName("venues") val venues: List<TmVenue>? = null
)

data class TmImage(
    @SerializedName("url") val url: String? = null,
    @SerializedName("ratio") val ratio: String? = null,
    @SerializedName("width") val width: Int = 0,
    @SerializedName("height") val height: Int = 0
)

data class TmDates(
    @SerializedName("start") val start: TmStart? = null
)

data class TmStart(
    @SerializedName("localDate") val localDate: String? = null,
    @SerializedName("localTime") val localTime: String? = null
)

data class TmClassification(
    @SerializedName("segment") val segment: TmSegment? = null
)

data class TmSegment(
    @SerializedName("name") val name: String? = null
)

data class TmVenue(
    @SerializedName("name") val name: String? = null,
    @SerializedName("city") val city: TmCity? = null,
    @SerializedName("address") val address: TmAddress? = null,
    @SerializedName("location") val location: TmLocation? = null,
    @SerializedName("country") val country: TmCountry? = null
)

data class TmCity(
    @SerializedName("name") val name: String? = null
)

data class TmAddress(
    @SerializedName("line1") val line1: String? = null
)

data class TmLocation(
    @SerializedName("latitude") val latitude: String? = null,
    @SerializedName("longitude") val longitude: String? = null
)

data class TmCountry(
    @SerializedName("name") val name: String? = null
)