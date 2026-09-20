package com.eventfinder.app.domain.model

data class OsmVenue(
    val id: String,
    val name: String,
    val description: String,
    val category: EventCategory,
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val website: String?,
    val osmType: String,
    val osmId: Long
)
