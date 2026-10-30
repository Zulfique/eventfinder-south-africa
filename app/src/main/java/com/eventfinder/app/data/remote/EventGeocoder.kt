package com.eventfinder.app.data.remote

import com.eventfinder.app.data.local.GeocodeCacheDao
import com.eventfinder.app.data.local.GeocodeCacheEntity
import com.eventfinder.app.data.remote.dto.OmGeocodingResult
import com.eventfinder.app.utils.AppLogger
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves free-text location strings (e.g. "Cape Town", "SunBet Arena Time
 * Square, Pretoria") into latitude/longitude coordinates using the Open-Meteo
 * geocoding API.
 *
 * Open-Meteo's gazetteer only knows **populated places**, not venues, so a
 * single query built from the whole string fails for most feed entries: the
 * two public feeds supply strings like
 * `MISSION ROAD\, GREENBUSHES\, GQEBERHA\, EC` or `Teatro at Montecasino,
 * Johannesburg`. Probing those feeds showed 26 of 28 unique locations resolving
 * to nothing, which left events without coordinates and produced meaningless
 * distances. [candidateQueries] therefore falls back through progressively
 * simpler, still-place-like queries:
 *
 *  1. the whole (cleaned) string,
 *  2. each comma-separated segment from the most specific backwards - normally
 *     the town - skipping province codes such as `WC`/`KZN` and placeholders
 *     such as `TBA`,
 *  3. the leading segment with trailing words dropped one at a time, so
 *     `KYALAMI GRAND PRIX CIRCUIT` can still fall back to `KYALAMI`.
 *
 * Every candidate must resolve to a South African result: a wrong-country match
 * is worse than no coordinates, because it shows a distance like
 * "16963.1 km away". When all candidates fail, null is returned and the caller
 * leaves the event without coordinates.
 *
 * Results are cached in memory so repeated lookups for the same location string
 * (common when multiple events share a venue city) are instant, and persisted
 * in Room (via [GeocodeCacheDao]) so the cache survives process restarts and a
 * multi-event feed does not re-issue Open-Meteo requests for the same places.
 */
class EventGeocoder(
    private val geocodingApi: OpenMeteoGeocodingApi,
    private val geocodeCacheDao: GeocodeCacheDao? = null
) {
    private val tag = "EventGeocoder"
    private val cache = ConcurrentHashMap<String, Pair<Double, Double>>()
    private val failedQueries = ConcurrentHashMap.newKeySet<String>()

    /**
     * Attempts to resolve [locationName] to coordinates.
     *
     * @return lat/lng pair if found, or null if the location cannot be resolved.
     */
    suspend fun geocode(locationName: String): Pair<Double, Double>? {
        val cacheKey = locationName.trim().lowercase()
        if (cacheKey.isBlank()) return null
        cache[cacheKey]?.let { return it }
        if (failedQueries.contains(cacheKey)) return null

        geocodeCacheDao?.findByKey(cacheKey)?.let { entry ->
            val coords = entry.latitude to entry.longitude
            cache[cacheKey] = coords
            return coords
        }

        val candidates = candidateQueries(locationName)
        if (candidates.isEmpty()) {
            failedQueries.add(cacheKey)
            return null
        }

        for (query in candidates) {
            val results = try {
                geocodingApi.geocode(name = query, count = GEOFETCH_COUNT).results
            } catch (e: Exception) {
                // Network-level failure: do not burn the remaining candidates.
                failedQueries.add(cacheKey)
                AppLogger.w(tag, "Geocoding failed for '$query': ${e.message}")
                return null
            } ?: continue

            // Only accept results that are explicitly South African. Without this
            // guard a query for "George" could silently resolve to a non-South
            // African location, placing an event in the wrong country.
            val result = results.firstOrNull { it.isSouthAfrican() }

            if (result?.latitude != null && result.longitude != null) {
                val coords = result.latitude to result.longitude
                cache[cacheKey] = coords
                failedQueries.remove(cacheKey)
                geocodeCacheDao?.upsert(
                    GeocodeCacheEntity(
                        locationKey = cacheKey,
                        latitude = coords.first,
                        longitude = coords.second,
                        createdAt = System.currentTimeMillis()
                    )
                )
                AppLogger.d(
                    tag,
                    "Geocoded '$locationName' via '$query' → ${coords.first}, ${coords.second}"
                )
                return coords
            }
            AppLogger.d(tag, "No South African result for '$query'")
        }

        failedQueries.add(cacheKey)
        AppLogger.d(tag, "No South African geocoding result for '$locationName'")
        return null
    }

    /**
     * Builds the ordered, de-duplicated list of queries to try for
     * [locationName]. See the class docs for the fallback order.
     */
    internal fun candidateQueries(locationName: String): List<String> {
        val cleaned = normalize(locationName)
        if (cleaned.isBlank()) return emptyList()

        val candidates = LinkedHashSet<String>()

        candidates += withCountry(cleaned)

        val segments = cleaned.split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }

        // Town names trail the venue in both feeds, so walk from the back.
        for (segment in segments.asReversed()) {
            if (!isPlaceLike(segment)) continue
            candidates += withCountry(segment)
            if (candidates.size >= MAX_QUERIES) return candidates.take(MAX_QUERIES)
        }

        // Last resort: drop trailing words from the leading (venue) segment.
        val first = segments.firstOrNull().orEmpty()
        val words = first.split(' ').filter { it.isNotBlank() }
        for (drop in 1 until words.size) {
            candidates += withCountry(words.dropLast(drop).joinToString(" "))
            if (candidates.size >= MAX_QUERIES) break
        }

        return candidates.take(MAX_QUERIES)
    }

    /**
     * Cleans feed noise: ICS escapes commas as `\,`, carries stray backslashes
     * and replacement characters from broken encodings, and often ends with a
     * truncated ellipsis (`FORMULA K KART CIRCUIT…..`).
     */
    private fun normalize(raw: String): String = raw
        .replace("\\,", ",")            // ICS-escaped comma
        .replace("\\", "")              // any other stray backslash
        .replace(FLUFFY, " ")           // control chars + mojibake
        .replace(Regex("[.\\s\\u2026]+$"), "") // trailing dots / ellipsis / whitespace
        .replace(Regex("\\s+"), " ")
        .trim()
        .trim(',', ' ')

    /** Province codes and placeholders that are not place names on their own. */
    private fun isPlaceLike(segment: String): Boolean {
        val key = segment.lowercase().trim()
        if (key.length < MIN_PLACE_LENGTH) return false
        if (key in PROVINCE_TOKENS || key in PLACEHOLDER_TOKENS) return false
        if (key.all { it.isDigit() || it == ' ' }) return false
        return true
    }

    /** Appends the country unless the string already names it. */
    private fun withCountry(segment: String): String {
        val lower = segment.lowercase()
        return if (lower.contains("south africa") || lower.endsWith(", za") || lower == "za") {
            segment
        } else {
            "$segment, South Africa"
        }
    }

    private fun OmGeocodingResult.isSouthAfrican(): Boolean =
        countryCode?.equals("ZA", ignoreCase = true) == true ||
            country?.equals("South Africa", ignoreCase = true) == true

    fun clearCache() {
        cache.clear()
        failedQueries.clear()
    }

    companion object {
        /** Bounds the number of Open-Meteo calls spent on one location. */
        private const val MAX_QUERIES = 5
        private const val GEOFETCH_COUNT = 5
        private const val MIN_PLACE_LENGTH = 3

        /** Control characters and the mojibake marker the feeds emit. */
        private val FLUFFY = Regex("[\\u0000-\\u001F\\uFFFD]")

        private val PROVINCE_TOKENS = setOf(
            "wc", "ec", "nc", "fs", "gp", "nw", "kzn", "ls", "mp",
            "western cape", "eastern cape", "northern cape", "free state",
            "north west", "gauteng", "limpopo", "mpumalanga",
            "kwazulu-natal", "kwazulu natal", "kznatal"
        )

        private val PLACEHOLDER_TOKENS = setOf("tba", "tbc", "za", "south africa")
    }
}
