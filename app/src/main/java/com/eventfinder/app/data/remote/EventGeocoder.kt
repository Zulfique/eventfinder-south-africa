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
    private val cache = ConcurrentHashMap<String, Pair<Double, Double>>()
    private val failedQueries = ConcurrentHashMap.newKeySet<String>()

    private val countryContexts = listOf(
        "south africa", "cape town", "johannesburg", "durban",
        "pretoria", "cape town", "stellenbosch", "port elizabeth",
        "bloemfontein", "polokwane", "mbombela", "east london",
        "kimberley", "richards bay", "george", "hermanus"
    )

    /**
     * Attempts to resolve [locationName] to coordinates.
     *
     * The query is sanitised and appended with ", South Africa" if not already
     * present, to avoid ambiguous matches to other countries.
     *
     * @return lat/lng pair if found, or null if the location cannot be resolved.
     */
    suspend fun geocode(locationName: String): Pair<Double, Double>? {
        val query = buildGeocodingQuery(locationName)
        if (query.isBlank()) return null

        val cacheKey = locationName.trim().lowercase()
        cache[cacheKey]?.let { return it }
        if (failedQueries.contains(cacheKey)) return null

        return try {
            val response = geocodingApi.geocode(name = query, count = 5)

            val result = response.results?.firstOrNull { res ->
                val country = res.countryCode?.lowercase()
                country == "za" || country == "south africa"
            } ?: response.results?.firstOrNull()

            if (result?.latitude != null && result.longitude != null) {
                val coords = result.latitude to result.longitude
                cache[cacheKey] = coords
                failedQueries.remove(cacheKey)
                AppLogger.d(tag, "Geocoded '$locationName' → ${coords.first}, ${coords.second}")
                coords
            } else {
                failedQueries.add(cacheKey)
                AppLogger.d(tag, "No geocoding result for '$locationName'")
                null
            }
        } catch (e: Exception) {
            failedQueries.add(cacheKey)
            AppLogger.w(tag, "Geocoding failed for '$locationName': ${e.message}")
            null
        }
    }

    private fun buildGeocodingQuery(raw: String): String {
        val cleaned = raw.trim()
            .replace(Regex("\\s+"), " ")
            .removePrefix("at ")
            .removePrefix("near ")

        if (cleaned.isBlank()) return ""

        if (cleaned.lowercase().contains("south africa") || cleaned.lowercase().contains(", za")) {
            return cleaned
        }

        val parts = cleaned.split(",").map { it.trim() }.filter { it.isNotBlank() }
        val mainPart = if (parts.size == 1) {
            parts[0]
        } else {
            val lastPart = parts.last()
            val numericPrefix = lastPart.contains(Regex("\\d"))
            if (numericPrefix && parts.size > 1) {
                parts[parts.size - 2]
            } else {
                lastPart
            }
        }

        if (mainPart.isBlank()) return ""

        val alreadyHasCountry = countryContexts.any { mainPart.lowercase().contains(it) }
        return if (alreadyHasCountry) {
            mainPart
        } else {
            "$mainPart, South Africa"
        }
    }

    fun clearCache() {
        cache.clear()
        failedQueries.clear()
    }
}
