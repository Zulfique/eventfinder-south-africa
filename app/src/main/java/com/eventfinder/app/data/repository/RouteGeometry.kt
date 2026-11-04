package com.eventfinder.app.data.repository

import kotlin.math.roundToInt

/** Decodes a precision-6 encoded polyline (Valhalla / Google format). */
fun decodePolyline6(encoded: String): List<RoutePoint> {
    val factor = 1_000_000.0
    val points = ArrayList<RoutePoint>()
    var index = 0
    var lat = 0
    var lng = 0
    while (index < encoded.length) {
        var result = 0
        var shift = 0
        var byte: Int
        do {
            if (index >= encoded.length) return points
            byte = encoded[index++].code - 63
            result = result or ((byte and 0x1f) shl shift)
            shift += 5
        } while (byte >= 0x20)
        lat += if (result and 1 != 0) (result shr 1).inv() else result shr 1
        result = 0
        shift = 0
        do {
            if (index >= encoded.length) return points
            byte = encoded[index++].code - 63
            result = result or ((byte and 0x1f) shl shift)
            shift += 5
        } while (byte >= 0x20)
        lng += if (result and 1 != 0) (result shr 1).inv() else result shr 1
        points.add(RoutePoint(lat / factor, lng / factor))
    }
    return points
}

/** "12.4 km" / "840 m" from provider metres. */
fun formatRouteDistance(meters: Double): String =
    if (meters >= 1000) "%.1f km".format(meters / 1000.0)
    else "${meters.roundToInt()} m"

/** "18 min" / "1 h 05 min" from provider seconds. */
fun formatRouteDuration(seconds: Double): String {
    val totalMinutes = (seconds / 60.0).roundToInt().coerceAtLeast(1)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "$hours h %02d min".format(minutes) else "$minutes min"
}
