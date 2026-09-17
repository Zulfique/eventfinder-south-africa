package com.eventfinder.app.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat

/**
 * Thin wrapper around the platform LocationManager (free, no Play Services).
 * Returns the last known device position so events can be sorted by distance.
 */
object LocationUtils {

    /** Best available last-known position, or null when unavailable. */
    fun lastKnown(context: Context): Pair<Double, Double>? {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) return null

        return try {
            val gps = manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val network = manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            (gps ?: network)?.let { it.latitude to it.longitude }
        } catch (_: SecurityException) {
            null
        } catch (_: Exception) {
            null
        }
    }
}