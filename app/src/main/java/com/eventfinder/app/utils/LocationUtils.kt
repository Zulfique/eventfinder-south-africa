package com.eventfinder.app.utils

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat

/**
 * Thin wrapper around the platform LocationManager (free, no Play Services).
 * Returns the last known device position or performs a one-shot location request.
 */
object LocationUtils {

    private fun hasPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
    }

    /** Best available last-known position, or null when unavailable. */
    @SuppressLint("MissingPermission")
    fun lastKnown(context: Context): Pair<Double, Double>? {
        if (!hasPermission(context)) return null

        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        return try {
            val gps = runCatching {
                manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            }.getOrNull()

            val network = runCatching {
                manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            }.getOrNull()

            selectBest(gps, network)?.let { it.latitude to it.longitude }
        } catch (_: SecurityException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Requests a single fresh location update. Falls back to [onUnavailable]
     * if no provider is enabled or the request fails.
     */
    @SuppressLint("MissingPermission")
    fun requestCurrentLocation(
        context: Context,
        onLocation: (Double, Double) -> Unit,
        onUnavailable: () -> Unit = {}
    ) {
        if (!hasPermission(context)) {
            onUnavailable()
            return
        }

        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        val provider = when {
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> {
                onUnavailable()
                return
            }
        }

        var delivered = false
        val mainHandler = Handler(Looper.getMainLooper())

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (delivered) return
                delivered = true
                manager.removeUpdates(this)
                onLocation(location.latitude, location.longitude)
            }

            override fun onProviderDisabled(provider: String) {
                if (!delivered) {
                    delivered = true
                    manager.removeUpdates(this)
                    onUnavailable()
                }
            }
        }

        try {
            manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
        } catch (_: SecurityException) {
            onUnavailable()
        } catch (_: Exception) {
            onUnavailable()
        }

        // Safety timeout: if Android never delivers a callback, give up after 15s.
        mainHandler.postDelayed({
            if (!delivered) {
                delivered = true
                manager.removeUpdates(listener)
                onUnavailable()
            }
        }, 15_000L)
    }

    private fun selectBest(first: Location?, second: Location?): Location? {
        if (first == null) return second
        if (second == null) return first
        return when {
            first.accuracy <= second.accuracy -> first
            else -> second
        }
    }
}
