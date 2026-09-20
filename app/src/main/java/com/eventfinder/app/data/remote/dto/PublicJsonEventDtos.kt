package com.eventfinder.app.data.remote.dto

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

data class PublicJsonEventResponse(
    @SerializedName("events")
    val events: List<PublicJsonEventDto>? = null,
    @SerializedName("data")
    val data: List<PublicJsonEventDto>? = null,
    @SerializedName("results")
    val results: List<PublicJsonEventDto>? = null
)

data class PublicJsonEventDto(
    @SerializedName("id")
    val id: String? = null,
    @SerializedName("eventId")
    val eventId: String? = null,
    @SerializedName("uuid")
    val uuid: String? = null,
    @SerializedName("slug")
    val slug: String? = null,
    @SerializedName("title")
    val title: String? = null,
    @SerializedName("name")
    val name: String? = null,
    @SerializedName("description")
    val description: String? = null,
    @SerializedName("summary")
    val summary: String? = null,
    @SerializedName("category")
    val category: String? = null,
    @SerializedName("type")
    val type: String? = null,
    @SerializedName("event_type")
    val eventType: String? = null,
    @SerializedName("startDate")
    val startDate: String? = null,
    @SerializedName("start_date")
    val startDateSnake: String? = null,
    @SerializedName("startsAt")
    val startsAt: String? = null,
    @SerializedName("starts_at")
    val startsAtSnake: String? = null,
    @SerializedName("endDate")
    val endDate: String? = null,
    @SerializedName("end_date")
    val endDateSnake: String? = null,
    @SerializedName("endsAt")
    val endsAt: String? = null,
    @SerializedName("ends_at")
    val endsAtSnake: String? = null,
    @SerializedName("venue")
    val venue: PublicJsonVenueDto? = null,
    @SerializedName("location")
    val locationElement: JsonElement? = null,
    @SerializedName("imageUrl")
    val imageUrl: String? = null,
    @SerializedName("image_url")
    val imageUrlSnake: String? = null,
    @SerializedName("image")
    val image: String? = null,
    @SerializedName("url")
    val url: String? = null,
    @SerializedName("sourceUrl")
    val sourceUrl: String? = null,
    @SerializedName("source_url")
    val sourceUrlSnake: String? = null,
    @SerializedName("organizer")
    val organizer: PublicJsonOrganizerDto? = null,
    @SerializedName("organizerName")
    val organizerName: String? = null,
    @SerializedName("organizer_name")
    val organizerNameSnake: String? = null
) {
    fun resolvedId(): String? = id ?: eventId ?: uuid ?: slug
    fun resolvedTitle(): String? = title ?: name
    fun resolvedDescription(): String = description ?: summary ?: ""
    fun resolvedCategory(): String = category ?: type ?: eventType ?: "OTHER"
    fun resolvedStart(): String? = startDate ?: startDateSnake ?: startsAt ?: startsAtSnake
    fun resolvedEnd(): String? = endDate ?: endDateSnake ?: endsAt ?: endsAtSnake
    fun resolvedImageUrl(): String? = imageUrl ?: imageUrlSnake ?: image
    fun resolvedSourceUrl(): String? = sourceUrl ?: sourceUrlSnake ?: url
    fun resolvedOrganizerName(): String? =
        organizerName ?: organizerNameSnake ?: organizer?.name

    val location: PublicJsonLocationDto?
        get() = runCatching {
            locationElement?.let { element ->
                when {
                    element.isJsonObject -> Gson().fromJson(
                        element, PublicJsonLocationDto::class.java
                    )
                    element.isJsonPrimitive && element.asString.isNotBlank() ->
                        PublicJsonLocationDto(name = element.asString)
                    else -> null
                }
            }
        }.getOrNull()
}

data class PublicJsonVenueDto(
    @SerializedName("name")
    val name: String? = null,
    @SerializedName("title")
    val title: String? = null,
    @SerializedName("address")
    val address: String? = null,
    @SerializedName("street")
    val street: String? = null,
    @SerializedName("city")
    val city: String? = null,
    @SerializedName("latitude")
    val latitude: Double? = null,
    @SerializedName("lat")
    val lat: Double? = null,
    @SerializedName("longitude")
    val longitude: Double? = null,
    @SerializedName("lng")
    val lng: Double? = null,
    @SerializedName("lon")
    val lon: Double? = null
) {
    fun resolvedName(): String = name ?: title ?: ""
    fun resolvedAddress(): String {
        address?.takeIf { it.isNotBlank() }?.let { return it }
        return listOfNotNull(
            street?.takeIf { it.isNotBlank() },
            city?.takeIf { it.isNotBlank() }
        ).joinToString(", ")
    }
    fun resolvedLatitude(): Double? = latitude ?: lat
    fun resolvedLongitude(): Double? = longitude ?: lng ?: lon
}

data class PublicJsonLocationDto(
    @SerializedName("name")
    val name: String? = null,
    @SerializedName("address")
    val address: String? = null,
    @SerializedName("city")
    val city: String? = null,
    @SerializedName("latitude")
    val latitude: Double? = null,
    @SerializedName("lat")
    val lat: Double? = null,
    @SerializedName("longitude")
    val longitude: Double? = null,
    @SerializedName("lng")
    val lng: Double? = null,
    @SerializedName("lon")
    val lon: Double? = null
) {
    fun resolvedName(): String = name ?: ""
    fun resolvedAddress(): String =
        listOfNotNull(
            address?.takeIf { it.isNotBlank() },
            city?.takeIf { it.isNotBlank() }
        ).joinToString(", ")
    fun resolvedLatitude(): Double? = latitude ?: lat
    fun resolvedLongitude(): Double? = longitude ?: lng ?: lon
}

data class PublicJsonOrganizerDto(
    @SerializedName("name")
    val name: String? = null
)
