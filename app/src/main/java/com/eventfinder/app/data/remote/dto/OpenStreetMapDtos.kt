package com.eventfinder.app.data.remote.dto

import com.google.gson.annotations.SerializedName


data class NominatimPlace(
    @SerializedName("place_id")
    val placeId: Long? = null,

    @SerializedName("osm_type")
    val osmType: String? = null,

    @SerializedName("osm_id")
    val osmId: Long? = null,

    @SerializedName("display_name")
    val displayName: String? = null,

    @SerializedName("lat")
    val latitude: String? = null,

    @SerializedName("lon")
    val longitude: String? = null,

    @SerializedName("type")
    val type: String? = null,

    @SerializedName("category")
    val category: String? = null,

    @SerializedName("name")
    val name: String? = null,

    @SerializedName("address")
    val address: NominatimAddress? = null
)


data class NominatimAddress(
    @SerializedName("road")
    val road: String? = null,

    @SerializedName("house_number")
    val houseNumber: String? = null,

    @SerializedName("suburb")
    val suburb: String? = null,

    @SerializedName("city")
    val city: String? = null,

    @SerializedName("town")
    val town: String? = null,

    @SerializedName("village")
    val village: String? = null,

    @SerializedName("municipality")
    val municipality: String? = null,

    @SerializedName("state")
    val state: String? = null,

    @SerializedName("postcode")
    val postcode: String? = null,

    @SerializedName("country")
    val country: String? = null,

    @SerializedName("country_code")
    val countryCode: String? = null
)


data class OverpassResponse(
    @SerializedName("version")
    val version: Double? = null,

    @SerializedName("generator")
    val generator: String? = null,

    @SerializedName("elements")
    val elements: List<OverpassElement> = emptyList()
)


data class OverpassElement(
    @SerializedName("type")
    val type: String? = null,

    @SerializedName("id")
    val id: Long? = null,

    @SerializedName("lat")
    val latitude: Double? = null,

    @SerializedName("lon")
    val longitude: Double? = null,

    @SerializedName("center")
    val center: OverpassCenter? = null,

    @SerializedName("tags")
    val tags: Map<String, String>? = null
)


data class OverpassCenter(
    @SerializedName("lat")
    val latitude: Double? = null,

    @SerializedName("lon")
    val longitude: Double? = null
)
