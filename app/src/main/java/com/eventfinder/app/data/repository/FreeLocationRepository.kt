package com.eventfinder.app.data.repository

import com.eventfinder.app.data.remote.NominatimApi
import com.eventfinder.app.data.remote.OpenMeteoApi
import com.eventfinder.app.data.remote.OpenMeteoAirQualityApi
import com.eventfinder.app.data.remote.OverpassApi
import com.eventfinder.app.data.remote.dto.NominatimPlace
import com.eventfinder.app.data.remote.dto.OmAirQualityResponse
import com.eventfinder.app.data.remote.dto.OmElevationResponse
import com.eventfinder.app.data.remote.dto.OmGeocodingResult
import com.eventfinder.app.data.remote.dto.OverpassElement
import retrofit2.HttpException
import java.io.IOException


data class FreeLocation(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val country: String? = null,
    val region: String? = null,
    val timezone: String? = null,
    val elevationMeters: Double? = null
)


data class AirQualitySummary(
    val europeanAqi: Double?,
    val pm10: Double?,
    val pm25: Double?,
    val carbonMonoxide: Double?,
    val nitrogenDioxide: Double?,
    val sulphurDioxide: Double?,
    val ozone: Double?
) {
    val aqiLabel: String
        get() {
            val aqi = europeanAqi ?: return "Unavailable"
            return when {
                aqi <= 20 -> "Good"
                aqi <= 40 -> "Fair"
                aqi <= 60 -> "Moderate"
                aqi <= 80 -> "Poor"
                aqi <= 100 -> "Very poor"
                else -> "Extremely poor"
            }
        }
}


data class OsmPlace(
    val id: Long,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val type: String,
    val category: String,
    val address: String?
)


class FreeLocationRepository(
    private val openMeteoApi: OpenMeteoApi,
    private val openMeteoGeocodingApi: OpenMeteoApi,
    private val airQualityApi: OpenMeteoAirQualityApi,
    private val nominatimApi: NominatimApi,
    private val overpassApi: OverpassApi
) {

    /**
     * Open-Meteo place search.
     */
    suspend fun searchLocations(
        query: String,
        countryCode: String = "ZA"
    ): Result<List<FreeLocation>> {
        if (query.isBlank()) {
            return Result.success(emptyList())
        }

        return try {
            val response = openMeteoGeocodingApi.geocode(
                name = query.trim(),
                count = 10
            )

            Result.success(
                response.results
                    .orEmpty()
                    .filter {
                        countryCode.isBlank() ||
                            it.countryCode.equals(countryCode, ignoreCase = true)
                    }
                    .mapNotNull(::mapGeocoding)
            )
        } catch (t: IOException) {
            Result.failure(t)
        } catch (t: HttpException) {
            Result.failure(t)
        } catch (t: Exception) {
            Result.failure(t)
        }
    }

    private fun mapGeocoding(
        item: OmGeocodingResult
    ): FreeLocation? {
        val latitude = item.latitude ?: return null
        val longitude = item.longitude ?: return null

        return FreeLocation(
            name = item.name.orEmpty(),
            latitude = latitude,
            longitude = longitude,
            country = item.country,
            region = item.admin1,
            timezone = item.timezone,
            elevationMeters = item.elevation
        )
    }

    /**
     * Reverse geocoding via Nominatim.
     */
    suspend fun reverseGeocode(
        latitude: Double,
        longitude: Double
    ): Result<String> {
        return try {
            val result = nominatimApi.reverse(
                latitude = latitude,
                longitude = longitude
            )
            Result.success(result.displayName.orEmpty())
        } catch (t: IOException) {
            Result.failure(t)
        } catch (t: HttpException) {
            Result.failure(t)
        } catch (t: Exception) {
            Result.failure(t)
        }
    }

    /**
     * Search an address/place with Nominatim.
     */
    suspend fun searchOsm(
        query: String
    ): Result<List<OsmPlace>> {
        if (query.isBlank()) {
            return Result.success(emptyList())
        }

        return try {
            val results = nominatimApi.search(
                query = query.trim(),
                limit = 5
            )
            Result.success(results.mapNotNull(::mapNominatim))
        } catch (t: IOException) {
            Result.failure(t)
        } catch (t: HttpException) {
            Result.failure(t)
        } catch (t: Exception) {
            Result.failure(t)
        }
    }

    private fun mapNominatim(
        item: NominatimPlace
    ): OsmPlace? {
        val lat = item.latitude?.toDoubleOrNull() ?: return null
        val lng = item.longitude?.toDoubleOrNull() ?: return null

        return OsmPlace(
            id = item.osmId ?: item.placeId ?: return null,
            name = item.name
                ?.takeIf { it.isNotBlank() }
                ?: item.displayName.orEmpty(),
            latitude = lat,
            longitude = lng,
            type = item.type.orEmpty(),
            category = item.category.orEmpty(),
            address = item.displayName
        )
    }

    /**
     * Elevation lookup.
     */
    suspend fun elevation(
        latitude: Double,
        longitude: Double
    ): Result<Double> {
        return try {
            val response: OmElevationResponse =
                openMeteoApi.elevation(
                    latitude = latitude.toString(),
                    longitude = longitude.toString()
                )
            val elevation =
                response.elevation?.firstOrNull()
                    ?: return Result.failure(
                        IllegalStateException("elevation_unavailable")
                    )
            Result.success(elevation)
        } catch (t: Exception) {
            Result.failure(t)
        }
    }

    /**
     * Air quality data.
     */
    suspend fun airQuality(
        latitude: Double,
        longitude: Double
    ): Result<AirQualitySummary> {
        return try {
            val response: OmAirQualityResponse =
                airQualityApi.getAirQuality(
                    latitude = latitude,
                    longitude = longitude
                )
            val current = response.current
                ?: return Result.failure(
                    IllegalStateException("air_quality_unavailable")
                )
            Result.success(
                AirQualitySummary(
                    europeanAqi = current.europeanAqi,
                    pm10 = current.pm10,
                    pm25 = current.pm25,
                    carbonMonoxide = current.carbonMonoxide,
                    nitrogenDioxide = current.nitrogenDioxide,
                    sulphurDioxide = current.sulphurDioxide,
                    ozone = current.ozone
                )
            )
        } catch (t: Exception) {
            Result.failure(t)
        }
    }

    /**
     * Nearby OSM places via Overpass.
     */
    suspend fun nearbyPlaces(
        latitude: Double,
        longitude: Double,
        radiusMeters: Int = 1500,
        limit: Int = 50
    ): Result<List<OsmPlace>> {
        val safeRadius = radiusMeters.coerceIn(100, 5000)
        val safeLimit = limit.coerceIn(1, 100)

        val query = """
            [out:json][timeout:15];
            (
              nwr["amenity"](around:$safeRadius,$latitude,$longitude);
              nwr["tourism"](around:$safeRadius,$latitude,$longitude);
              nwr["leisure"](around:$safeRadius,$latitude,$longitude);
              nwr["shop"](around:$safeRadius,$latitude,$longitude);
            );
            out center tags;
        """.trimIndent()

        return try {
            val response = overpassApi.query(query)
            Result.success(
                response.elements
                    .asSequence()
                    .mapNotNull(::mapOverpass)
                    .distinctBy { it.id }
                    .take(safeLimit)
                    .toList()
            )
        } catch (t: Exception) {
            Result.failure(t)
        }
    }

    private fun mapOverpass(
        element: OverpassElement
    ): OsmPlace? {
        val tags = element.tags.orEmpty()

        val latitude =
            element.latitude ?: element.center?.latitude ?: return null
        val longitude =
            element.longitude ?: element.center?.longitude ?: return null
        val id = element.id ?: return null

        val name =
            tags["name"]
                ?.takeIf { it.isNotBlank() }
                ?: tags["amenity"]
                ?: tags["tourism"]
                ?: tags["leisure"]
                ?: tags["shop"]
                ?: return null

        return OsmPlace(
            id = id,
            name = name,
            latitude = latitude,
            longitude = longitude,
            type = tags["amenity"]
                ?: tags["tourism"]
                ?: tags["leisure"]
                ?: tags["shop"]
                ?: "place",
            category = tags["amenity"]
                ?: tags["tourism"]
                ?: tags["leisure"]
                ?: tags["shop"]
                ?: "place",
            address = listOfNotNull(
                tags["addr:housenumber"],
                tags["addr:street"],
                tags["addr:suburb"],
                tags["addr:city"]
            ).joinToString(", ")
                .ifBlank { null }
        )
    }
}
