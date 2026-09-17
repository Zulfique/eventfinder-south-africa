package com.eventfinder.app.utils

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Great-circle distance calculations (Haversine formula) used for the
 * proximity-driven event sorting feature (FR-02, NFR-01).
 *
 * Adapted from the standard Haversine implementation documented on
 * "Moveable Type Scripts" by Chris Veness:
 *   https://www.movable-type.co.uk/scripts/latlong.html
 */
object DistanceCalculator {

    private const val EARTH_RADIUS_KM = 6371.0

    /** Great-circle distance in kilometres between two coordinates. */
    fun between(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLng / 2) * sin(dLng / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_KM * c
    }

    /** Round-trip-consistent display distance in kilometres. */
    fun displayDistanceKm(distanceKm: Double): Double =
        (distanceKm * 10.0).roundToInt() / 10.0

    /** True when [candidate] lies within [radiusKm] of [lat]/[lng]. */
    fun isWithinRadius(lat: Double, lng: Double, candidate: Pair<Double, Double>, radiusKm: Double): Boolean =
        between(lat, lng, candidate.first, candidate.second) <= radiusKm
}