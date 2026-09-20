package com.eventfinder.app.data.remote.dto

import com.google.gson.annotations.SerializedName

data class OsmOverpassResponse(
    @SerializedName("elements")
    val elements: List<OsmElement> = emptyList()
)

data class OsmElement(
    @SerializedName("type")
    val type: String? = null,

    @SerializedName("id")
    val id: Long? = null,

    @SerializedName("lat")
    val lat: Double? = null,

    @SerializedName("lon")
    val lon: Double? = null,

    @SerializedName("center")
    val center: OsmCenter? = null,

    @SerializedName("tags")
    val tags: Map<String, String>? = null
)

data class OsmCenter(
    @SerializedName("lat")
    val lat: Double? = null,

    @SerializedName("lon")
    val lon: Double? = null
)
