package com.eventfinder.app.data.repository

import com.eventfinder.app.data.local.EventDao
import com.eventfinder.app.data.local.EventEntity
import com.eventfinder.app.data.remote.model.RemoteEvent
import com.eventfinder.app.data.sources.EventSource
import com.eventfinder.app.domain.model.EventCategory
import com.eventfinder.app.utils.AppLogger

class EventDiscoveryRepository(
    private val eventDao: EventDao,
    private val sources: List<EventSource>
) {
    private val tag = "EventDiscoveryRepository"

    suspend fun refresh(): DiscoveryResult {
        var totalFetched = 0
        var failedSources = 0
        val allValid = mutableListOf<RemoteEvent>()

        for (source in sources) {
            try {
                AppLogger.i(tag, "Fetching events from ${source.displayName}")
                val events = source.fetchEvents()
                totalFetched += events.size
                val validEvents = events.filter { isValid(it) }.distinctBy { it.stableId }
                allValid.addAll(validEvents)
                AppLogger.i(tag, "Fetched ${validEvents.size} events from ${source.displayName}")
            } catch (e: Exception) {
                failedSources++
                AppLogger.e(tag, "Failed to fetch ${source.displayName}", e)
            }
        }

        val entities = allValid.mapNotNull { it.toEntity() }
        if (entities.isNotEmpty()) {
            eventDao.replaceNonUserCreated(entities)
        } else {
            eventDao.deleteNonUserCreated()
        }
        AppLogger.i(tag, "Discovery complete: ${allValid.size} valid, ${entities.size} inserted, $failedSources failed sources")

        return DiscoveryResult(
            fetched = totalFetched,
            inserted = entities.size,
            failedSources = failedSources
        )
    }

    private fun isValid(event: RemoteEvent): Boolean {
        if (event.title.isBlank()) return false
        if (event.sourceId.isBlank()) return false
        if (event.endDate < System.currentTimeMillis()) return false
        if (event.startDate <= 0L) return false

        val lat = event.latitude
        val lng = event.longitude
        if (lat == null || lng == null) return false
        if (lat !in SA_LAT_MIN..SA_LAT_MAX) return false
        if (lng !in SA_LNG_MIN..SA_LNG_MAX) return false

        return true
    }

    private fun RemoteEvent.toEntity(): EventEntity? {
        val latitude = latitude?.takeIf { it in -90.0..90.0 } ?: return null
        val longitude = longitude?.takeIf { it in -180.0..180.0 } ?: return null

        return EventEntity(
            id = "remote:$stableId",
            title = title,
            description = description,
            category = mapCategory(category),
            startDate = startDate,
            endDate = endDate,
            venueName = venueName,
            address = address,
            latitude = latitude,
            longitude = longitude,
            imageUrl = imageUrl,
            isPublic = true,
            organizerId = "external:$source",
            organizerName = organizerName ?: source,
            attendeeCount = 0,
            isCreatedByUser = false
        )
    }

    private fun mapCategory(raw: String): String {
        val value = raw.trim().lowercase()
        return when {
            value.contains("music") || value.contains("concert") || value.contains("festival") ->
                EventCategory.MUSIC.labelKey
            value.contains("sport") || value.contains("football") || value.contains("rugby") || value.contains("running") ->
                EventCategory.SPORTS.labelKey
            value.contains("food") || value.contains("market") || value.contains("restaurant") ->
                EventCategory.FOOD.labelKey
            value.contains("art") || value.contains("theatre") || value.contains("theater") || value.contains("culture") || value.contains("museum") ->
                EventCategory.ARTS.labelKey
            value.contains("business") || value.contains("conference") || value.contains("network") ->
                EventCategory.BUSINESS.labelKey
            value.contains("community") || value.contains("charity") ->
                EventCategory.COMMUNITY.labelKey
            else -> EventCategory.OTHER.labelKey
        }
    }
}

data class DiscoveryResult(
    val fetched: Int,
    val inserted: Int,
    val failedSources: Int
)

private const val SA_LAT_MIN = -35.0
private const val SA_LAT_MAX = -22.0
private const val SA_LNG_MIN = 16.0
private const val SA_LNG_MAX = 33.0
