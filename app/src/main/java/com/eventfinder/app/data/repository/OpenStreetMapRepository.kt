package com.eventfinder.app.data.repository

import com.eventfinder.app.data.remote.OpenStreetMapApi
import com.eventfinder.app.data.remote.dto.OsmElement
import com.eventfinder.app.domain.model.EventCategory
import com.eventfinder.app.domain.model.OsmVenue
import com.eventfinder.app.utils.AppLogger
import java.io.IOException
import java.util.Locale

/**
 * Keyless OpenStreetMap / Overpass discovery.
 *
 * Returns real-world places that are relevant to events:
 * theatres, cinemas, stadiums, arts centres, museums, galleries,
 * community centres, attractions and similar venues.
 *
 * It deliberately does NOT invent event dates. These are venue/place
 * discovery records and should not be treated as scheduled events.
 *
 * If the primary Overpass endpoint fails, a secondary endpoint is tried.
 */
class OpenStreetMapRepository(
    private val api: OpenStreetMapApi,
    private val fallbackApi: OpenStreetMapApi? = null
) {

    private val tag = "OpenStreetMapRepository"

    suspend fun findNearbyVenues(
        latitude: Double,
        longitude: Double,
        radiusMeters: Int = 10_000
    ): Result<List<OsmVenue>> {

        if (latitude !in -90.0..90.0) {
            return Result.failure(
                IllegalArgumentException("invalid_latitude")
            )
        }

        if (longitude !in -180.0..180.0) {
            return Result.failure(
                IllegalArgumentException("invalid_longitude")
            )
        }

        val radius = radiusMeters.coerceIn(1_000, 50_000)

        val query = buildVenueQuery(
            latitude = latitude,
            longitude = longitude,
            radiusMeters = radius
        )

        return try {
            val response = try {
                api.queryOverpass(query)
            } catch (e: Exception) {
                if (fallbackApi != null) {
                    AppLogger.w(tag, "Primary Overpass failed, trying fallback endpoint: ${e.message}")
                    fallbackApi.queryOverpass(query)
                } else {
                    throw e
                }
            }

            val venues = response.elements
                .mapNotNull { it.toVenue() }
                .distinctBy { it.id }

            AppLogger.i(
                tag,
                "Overpass returned ${venues.size} nearby venues"
            )

            Result.success(venues)
        } catch (e: IOException) {
            AppLogger.e(
                tag,
                "Overpass network request failed",
                e
            )

            Result.failure(e)
        } catch (e: Exception) {
            AppLogger.e(
                tag,
                "Overpass request failed",
                e
            )

            Result.failure(e)
        }
    }

    private fun buildVenueQuery(
        latitude: Double,
        longitude: Double,
        radiusMeters: Int
    ): String {
        val lat = String.format(
            Locale.US,
            "%.6f",
            latitude
        )

        val lon = String.format(
            Locale.US,
            "%.6f",
            longitude
        )

        return """
            [out:json][timeout:30];

            (
              nwr(around:$radiusMeters,$lat,$lon)
                ["amenity"~"^(theatre|cinema|arts_centre|community_centre|conference_centre)$"];

              nwr(around:$radiusMeters,$lat,$lon)
                ["tourism"~"^(museum|gallery|attraction|theme_park|zoo)$"];

              nwr(around:$radiusMeters,$lat,$lon)
                ["leisure"~"^(stadium|sports_centre|sports_hall|park)$"];

              nwr(around:$radiusMeters,$lat,$lon)
                ["amenity"="events_venue"];

              nwr(around:$radiusMeters,$lat,$lon)
                ["building"="stadium"];
            );

            out center tags;
        """.trimIndent()
    }

    private fun OsmElement.toVenue(): OsmVenue? {
        val elementId = id ?: return null
        val tags = tags ?: return null

        val name = tags["name"]
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return null

        val coordinates = when {
            lat != null && lon != null ->
                lat to lon

            center?.lat != null && center.lon != null ->
                center.lat to center.lon

            else -> null
        } ?: return null

        val category = determineCategory(tags)

        val address = listOfNotNull(
            tags["addr:housenumber"],
            tags["addr:street"],
            tags["addr:suburb"],
            tags["addr:city"]
        )
            .filter { it.isNotBlank() }
            .joinToString(", ")

        val description = listOfNotNull(
            tags["description"],
            tags["tourism"]?.let { "Tourism: $it" },
            tags["amenity"]?.let { "Amenity: $it" },
            tags["leisure"]?.let { "Leisure: $it" }
        )
            .firstOrNull()
            ?: "Event-related venue discovered from OpenStreetMap."

        val website = tags["website"]
            ?: tags["contact:website"]

        return OsmVenue(
            id = "osm-${type ?: "element"}-$elementId",
            name = name,
            description = description,
            category = category,
            latitude = coordinates.first,
            longitude = coordinates.second,
            address = address.ifBlank { "OpenStreetMap location" },
            website = website,
            osmType = type ?: "unknown",
            osmId = elementId
        )
    }

    private fun determineCategory(
        tags: Map<String, String>
    ): EventCategory {

        val amenity = tags["amenity"]
            ?.lowercase()

        val tourism = tags["tourism"]
            ?.lowercase()

        val leisure = tags["leisure"]
            ?.lowercase()

        return when {
            amenity in setOf(
                "theatre",
                "cinema",
                "arts_centre"
            ) -> EventCategory.ARTS

            amenity in setOf(
                "community_centre",
                "conference_centre",
                "events_venue"
            ) -> EventCategory.COMMUNITY

            tourism in setOf(
                "museum",
                "gallery"
            ) -> EventCategory.ARTS

            tourism in setOf(
                "attraction",
                "theme_park",
                "zoo"
            ) -> EventCategory.OTHER

            leisure in setOf(
                "stadium",
                "sports_centre",
                "sports_hall"
            ) -> EventCategory.SPORTS

            leisure == "park" -> EventCategory.COMMUNITY

            else -> EventCategory.OTHER
        }
    }
}
