package com.eventfinder.app.data.remote

import com.eventfinder.app.utils.AppLogger
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves free-text location strings (e.g. "Cape Town", "Sandton, Johannesburg")
 * into latitude/longitude coordinates using the Open-Meteo geocoding API.
 *
 * Results are cached in memory so repeated lookups for the same location string
 * (common when multiple events share a venue city) are instant.
 *
 * If geocoding fails or the location string is blank, null is returned and
 * the caller should handle the missing coordinates.
 */
class EventGeocoder(
    private val geocodingApi: OpenMeteoGeocodingApi
) {
    private val tag = "EventGeocoder"
    private val cache = ConcurrentHashMap<String, Pair<Double, Double>?>()

    /**
     * Attempts to resolve [locationName] to coordinates.
     *
     * The query is sanitised to extract the most meaningful part of the
     * location string (e.g. "Events at Cape Town Convention Centre" → "Cape Town").
     *
     * @return lat/lng pair if found, or null if the location cannot be resolved.
     */
    suspend fun geocode(locationName: String): Pair<Double, Double>? {
        val query = extractQuery(locationName)
        if (query.isBlank()) return null

        cache[query.lowercase()]?.let { return it }

        return try {
            val response = geocodingApi.geocode(name = query, count = 3)
            val result = response.results?.firstOrNull()

            if (result?.latitude != null && result.longitude != null) {
                val coords = result.latitude to result.longitude
                cache[query.lowercase()] = coords
                AppLogger.d(tag, "Geocoded '$query' → ${coords.first}, ${coords.second}")
                coords
            } else {
                cache[query.lowercase()] = null
                AppLogger.d(tag, "No geocoding result for '$query'")
                null
            }
        } catch (e: Exception) {
            AppLogger.w(tag, "Geocoding failed for '$query': ${e.message}")
            null
        }
    }

    /**
     * Extracts the most meaningful search term from a location string.
     *
     * Examples:
     * - "Cape Town" → "Cape Town"
     * - "Sandton City, Johannesburg" → "Sandton City"
     * - "Events at Cape Town Convention Centre" → "Cape Town"
     * - "123 Main St, Pretoria" → "Pretoria"
     */
    private fun extractQuery(raw: String): String {
        val cleaned = raw.trim()
            .replace(Regex("\\s+"), " ")
            .removePrefix("at ")
            .removePrefix("near ")

        if (cleaned.isBlank()) return ""

        val parts = cleaned.split(",").map { it.trim() }.filter { it.isNotBlank() }

        if (parts.size == 1) return parts[0]

        val lastPart = parts.last()
        val numericPrefix = lastPart.contains(Regex("\\d"))
        return if (numericPrefix && parts.size > 1) {
            parts[parts.size - 2]
        } else {
            lastPart
        }
    }

    fun clearCache() {
        cache.clear()
    }
}
