package com.eventfinder.app.data.remote.model

/**
 * Provider-independent event returned by an external public event source.
 *
 * This is deliberately separate from the Room EventEntity. Remote providers
 * have different JSON schemas, while EventFinder needs one stable model.
 */
data class RemoteEvent(
    val source: String,
    val sourceId: String,
    val title: String,
    val description: String,
    val category: String,
    val startDate: Long,
    val endDate: Long,
    val venueName: String,
    val address: String,
    val latitude: Double?,
    val longitude: Double?,
    val imageUrl: String?,
    val sourceUrl: String?,
    val organizerName: String?
) {
    val stableId: String
        get() = "$source:$sourceId"
}
