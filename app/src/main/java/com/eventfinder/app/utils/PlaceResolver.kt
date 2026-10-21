package com.eventfinder.app.utils

import android.content.Context
import com.eventfinder.app.data.remote.EventGeocoder
import com.eventfinder.app.data.store.UserPreferences
import kotlinx.coroutines.flow.firstOrNull

/**
 * Where a resolved place came from. Used for logging and for deciding whether
 * a device fix should override the value.
 */
enum class PlaceSource {
    /** The device reported a real position. */
    DEVICE,

    /** The "Default city" preference from Settings. */
    DEFAULT_CITY,

    /** The nation-wide fallback (Cape Town) — no location and no default city. */
    FALLBACK
}

/**
 * A resolved user place.
 *
 * @property latitude latitude in decimal degrees.
 * @property longitude longitude in decimal degrees.
 * @property source how the place was determined.
 * @property label human readable name, e.g. "Cape Town" or the configured city.
 */
data class ResolvedPlace(
    val latitude: Double,
    val longitude: Double,
    val source: PlaceSource,
    val label: String
)

/**
 * Resolves "where is the user" so the app behaves the same in every South
 * African city.
 *
 * Resolution order:
 *  1. **Device position** — the last known GPS/network fix, when the location
 *     permission has been granted. Works in Cape Town, Durban, Gqeberha or
 *     anywhere else the user happens to be.
 *  2. **Default city** — the city configured in Settings → Discovery defaults.
 *     Resolved offline from [SouthAfricanPlaces] and, when that fails, through
 *     the South-African-only [EventGeocoder].
 *  3. **Nation-wide fallback** — [SouthAfricanPlaces.nationalDefault], so the
 *     map and newly created events are never placed in a random city.
 *
 * The previous behaviour picked the map centre from the *device language*
 * (English → Cape Town, Afrikaans → Johannesburg) and pinned user-created
 * events in Johannesburg regardless of where they actually were.
 */
class PlaceResolver(
    private val context: Context,
    private val preferences: UserPreferences,
    private val geocoder: EventGeocoder? = null
) {

    /**
     * Resolves the current place. Never throws: on any failure the
     * nation-wide fallback is returned.
     */
    suspend fun resolve(): ResolvedPlace {
        LocationUtils.lastKnown(context)?.let { (lat, lng) ->
            return ResolvedPlace(
                latitude = lat,
                longitude = lng,
                source = PlaceSource.DEVICE,
                label = "current location"
            )
        }

        val configuredCity = preferences.defaultCity.firstOrNull()
        if (!configuredCity.isNullOrBlank()) {
            resolveCity(configuredCity)?.let { place ->
                return ResolvedPlace(
                    latitude = place.latitude,
                    longitude = place.longitude,
                    source = PlaceSource.DEFAULT_CITY,
                    label = place.name
                )
            }
        }

        val fallback = SouthAfricanPlaces.nationalDefault
        return ResolvedPlace(
            latitude = fallback.latitude,
            longitude = fallback.longitude,
            source = PlaceSource.FALLBACK,
            label = fallback.name
        )
    }

    /**
     * Resolves a city name to coordinates: the offline table first, then the
     * geocoding API (which only accepts South African results).
     */
    suspend fun resolveCity(cityName: String): SouthAfricanPlace? {
        SouthAfricanPlaces.findByName(cityName)?.let { return it }

        val geocoder = geocoder ?: return null
        val coords = runCatching { geocoder.geocode(cityName) }.getOrNull() ?: return null
        return SouthAfricanPlace(cityName.trim(), coords.first, coords.second)
    }
}
