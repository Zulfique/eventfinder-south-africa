package com.eventfinder.app.data.repository

import java.io.IOException

/**
 * Travel modes surfaced by the route panel. Each maps to the routing
 * provider's costing name and to the Google Maps deep-link mode used by the
 * Start navigation button.
 */
enum class TravelMode(val valhallaCosting: String, val googleMapsMode: String) {
    DRIVING("auto", "driving"),
    WALKING("pedestrian", "walking"),
    CYCLING("bicycle", "bicycling")
}

/** One coordinate of a decoded route line. */
data class RoutePoint(val lat: Double, val lng: Double)

/**
 * A single candidate route: real road geometry from the routing provider plus
 * its measured metrics. Alternatives arrive as additional [RouteOption]s.
 */
data class RouteOption(
    val distanceMeters: Double,
    val durationSeconds: Double,
    val points: List<RoutePoint>
)

/** Result of a routing request: fresh routes, or a cached match when offline. */
data class RouteRequestResult(
    val routes: List<RouteOption>,
    val fromCache: Boolean
)

/** Thrown when neither the provider nor the local cache can produce a route. */
class RouteUnavailableException : IOException("Route unavailable")
