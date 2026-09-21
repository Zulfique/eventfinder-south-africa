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
 * Returns the last known device position or performs a one-shot / continuous
 * location request.
 */
object LocationUtils {

    fun hasPermission(context: Context): Boolean {
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
     * Requests a single fresh location update. The 15-second timeout is
     * cancelled after a successful callback so [onUnavailable] is never
     * called after [onLocation].
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

        val mainHandler = Handler(Looper.getMainLooper())
        var delivered = false

        lateinit var timeoutRunnable: Runnable

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (delivered) return
                delivered = true
                mainHandler.removeCallbacks(timeoutRunnable)
                runCatching { manager.removeUpdates(this) }
                onLocation(location.latitude, location.longitude)
            }

            override fun onProviderDisabled(provider: String) {
                if (delivered) return
                delivered = true
                mainHandler.removeCallbacks(timeoutRunnable)
                runCatching { manager.removeUpdates(this) }
                onUnavailable()
            }
        }

        timeoutRunnable = Runnable {
            if (delivered) return
            delivered = true
            runCatching { manager.removeUpdates(listener) }
            onUnavailable()
        }

        try {
            manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
            mainHandler.postDelayed(timeoutRunnable, 15_000L)
        } catch (_: SecurityException) {
            delivered = true
            mainHandler.removeCallbacks(timeoutRunnable)
            onUnavailable()
        } catch (_: Exception) {
            delivered = true
            mainHandler.removeCallbacks(timeoutRunnable)
            onUnavailable()
        }
    }

    /**
     * Starts continuous location updates (every 5 s / 10 m) on all enabled
     * providers. Returns the [LocationManager] so the caller can call
     * [stopLocationUpdates] later.
     */
    @SuppressLint("MissingPermission")
    fun requestLocationUpdates(
        context: Context,
        onLocation: (Double, Double) -> Unit
    ): LocationManager? {
        if (!hasPermission(context)) return null

        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                onLocation(location.latitude, location.longitude)
            }
        }

        val providers = buildList {
            if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                add(LocationManager.GPS_PROVIDER)
            }
            if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                add(LocationManager.NETWORK_PROVIDER)
            }
        }

        if (providers.isEmpty()) return null

        return try {
            providers.forEach { provider ->
                manager.requestLocationUpdates(
                    provider,
                    5_000L,
                    10f,
                    listener,
                    Looper.getMainLooper()
                )
            }
            manager
        } catch (_: SecurityException) {
            null
        }
    }

    /**
     * Removes all location updates from the given [manager].
     * Call this when the composable is disposed or updates are no longer needed.
     */
    fun stopLocationUpdates(manager: LocationManager) {
        runCatching {
            manager.removeUpdates { }
        }
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
