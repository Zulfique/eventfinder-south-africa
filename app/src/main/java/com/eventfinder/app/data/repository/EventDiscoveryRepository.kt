package com.eventfinder.app.data.repository

import com.eventfinder.app.data.local.EventDao
import com.eventfinder.app.data.local.EventEntity
import com.eventfinder.app.data.remote.EventGeocoder
import com.eventfinder.app.data.remote.model.RemoteEvent
import com.eventfinder.app.data.sources.EventSource
import com.eventfinder.app.domain.model.EventCategory
import com.eventfinder.app.utils.AppLogger
import java.util.Locale

class EventDiscoveryRepository(
    private val eventDao: EventDao,
    private val sources: List<EventSource>,
    private val geocoder: EventGeocoder? = null
) {
    private val tag = "EventDiscoveryRepository"

    suspend fun refresh(): DiscoveryResult {
        val sourceResults = mutableListOf<SourceResult>()

        for (source in sources) {
            val result = fetchSource(source)
            sourceResults.add(result)
        }

        var totalInserted = 0

        for (result in sourceResults) {
            if (result.failed || result.inserted == 0) continue
            val organizerId = result.organizerId

            val entities = result.events.map { it.toEntity(organizerId) }
            if (entities.isNotEmpty()) {
                eventDao.replaceEventsForSource(organizerId, entities)
                totalInserted += entities.size
            }
        }

        val totalFetched = sourceResults.sumOf { it.fetched }
        val failedSources = sourceResults.count { it.failed }

        for (result in sourceResults) {
            AppLogger.i(
                tag,
                "${result.sourceName}: ${result.fetched} fetched / ${result.inserted} valid" +
                    if (result.failed) " [FAILED]" else ""
            )
        }
        AppLogger.i(
            tag,
            "Geocoding: ${sourceResults.sumOf { it.geocoded }} resolved / " +
                "${sourceResults.sumOf { it.geocodeFailed }} failed"
        )
        AppLogger.i(tag, "Discovery complete: $totalInserted inserted, $failedSources failed sources")

        return DiscoveryResult(
            fetched = totalFetched,
            inserted = totalInserted,
            failedSources = failedSources,
            sourceResults = sourceResults
        )
    }

    private suspend fun fetchSource(source: EventSource): SourceResult {
        return try {
            AppLogger.i(tag, "Fetching events from ${source.displayName}")
            val rawEvents = source.fetchEvents()

            var geocoded = 0
            var geocodeFailed = 0
            val events = rawEvents.map { event ->
                if (event.latitude == null || event.longitude == null) {
                    val result = geocodeIfNeeded(event)
                    if (result.latitude != null && event.latitude == null) {
                        geocoded++
                    } else if (event.latitude == null) {
                        geocodeFailed++
                    }
                    result
                } else {
                    event
                }
            }

            val validEvents = events.filter { isValid(it) }.distinctBy { it.stableId }

            if (validEvents.isEmpty()) {
                AppLogger.w(
                    tag,
                    "Source ${source.displayName} returned no valid events; keeping cached events"
                )
                // Policy: empty/failed sources preserve their cached events to avoid
                // data loss during temporary feed outages. Events that truly disappear
                // from a feed will be removed on the next successful non-empty sync
                // when the new event list is a superset of what's in the cache.
                return SourceResult(
                    sourceName = source.displayName,
                    fetched = rawEvents.size,
                    inserted = 0,
                    failed = false,
                    geocoded = geocoded,
                    geocodeFailed = geocodeFailed,
                    status = SourceStatus.EMPTY
                )
            }

            val organizerId = "external:${source.id}"

            val existingCount = eventDao.findIdsByOrganizerId(organizerId).size
            if (isSuspiciousReduction(existingCount, validEvents.size)) {
                AppLogger.w(
                    tag,
                    "Suspiciously small update from ${source.displayName}: " +
                        "${validEvents.size} new vs $existingCount cached — keeping cached events"
                )
                return SourceResult(
                    sourceName = source.displayName,
                    fetched = rawEvents.size,
                    inserted = 0,
                    failed = false,
                    geocoded = geocoded,
                    geocodeFailed = geocodeFailed,
                    status = SourceStatus.EMPTY
                )
            }

            AppLogger.i(tag, "Fetched ${validEvents.size} valid events from ${source.displayName}")

            SourceResult(
                sourceName = source.displayName,
                fetched = rawEvents.size,
                inserted = validEvents.size,
                failed = false,
                geocoded = geocoded,
                geocodeFailed = geocodeFailed,
                events = validEvents,
                organizerId = organizerId,
                status = SourceStatus.SUCCESS
            )
        } catch (e: Exception) {
            AppLogger.e(tag, "Failed to fetch ${source.displayName}", e)
            SourceResult(
                sourceName = source.displayName,
                fetched = 0,
                inserted = 0,
                failed = true,
                geocoded = 0,
                geocodeFailed = 0,
                status = SourceStatus.FAILED
            )
        }
    }

    private suspend fun geocodeIfNeeded(event: RemoteEvent): RemoteEvent {
        val locationName = event.venueName.ifBlank { event.address }
        if (locationName.isBlank()) return event

        val coords = geocoder?.geocode(locationName) ?: return event
        return event.copy(latitude = coords.first, longitude = coords.second)
    }

    private fun isValid(event: RemoteEvent): Boolean {
        if (event.title.isBlank()) return false
        if (event.sourceId.isBlank()) return false
        if (event.endDate < System.currentTimeMillis()) return false
        if (event.startDate <= 0L) return false

        val lat = event.latitude
        val lng = event.longitude
        if (lat != null && (lat !in SA_LAT_MIN..SA_LAT_MAX)) return false
        if (lng != null && (lng !in SA_LNG_MIN..SA_LNG_MAX)) return false

        return true
    }

    private fun RemoteEvent.toEntity(organizerId: String): EventEntity {
        val latitude = latitude?.takeIf { it in -90.0..90.0 }
        val longitude = longitude?.takeIf { it in -180.0..180.0 }

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
            organizerId = organizerId,
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

    /**
     * Protects the cache from a source that temporarily returns a much smaller
     * event list than what is already stored (e.g. feed truncation, partial
     * responses, or a degraded upstream API). A percentage floor alone is not
     * enough - a 100-event cache would accept a 21-event reply at 20%. Instead
     * we require the new count to be at least half the cached count (with a
     * small hard floor for tiny caches) before allowing a replacement.
     */
    private fun isSuspiciousReduction(existingCount: Int, newCount: Int): Boolean {
        if (existingCount < 10) return false

        val minimumExpected = maxOf(
            5,
            (existingCount * 0.50).toInt()
        )

        return newCount < minimumExpected
    }
}

data class DiscoveryResult(
    val fetched: Int,
    val inserted: Int,
    val failedSources: Int,
    val sourceResults: List<SourceResult> = emptyList()
)

data class SourceResult(
    val sourceName: String,
    val fetched: Int,
    val inserted: Int,
    val failed: Boolean,
    val geocoded: Int = 0,
    val geocodeFailed: Int = 0,
    val events: List<RemoteEvent> = emptyList(),
    val organizerId: String = "",
    val status: SourceStatus = SourceStatus.SUCCESS
)

enum class SourceStatus {
    /** Source returned events that were inserted into the DB. */
    SUCCESS,
    /** Source returned 0 valid events (feed empty or no upcoming events). */
    EMPTY,
    /** Source threw an exception (network error, parse error, HTTP error). */
    FAILED
}

private const val SA_LAT_MIN = -35.0
private const val SA_LAT_MAX = -22.0
private const val SA_LNG_MIN = 16.0
private const val SA_LNG_MAX = 33.0
