package com.eventfinder.app.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks connectivity so the UI can show the "offline — showing saved events"
 * banner (FR-09 / NFR-04 graceful degradation) and trigger a catch-up sync once
 * the device is back online.
 *
 * Uses [ConnectivityManager.registerDefaultNetworkCallback] so that both
 * Wi-Fi and mobile data count as online, and losing one interface while
 * another is still active does not incorrectly report offline.
 */
class NetworkMonitor(context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _isOnline = MutableStateFlow(false)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _isOnline.value = isCurrentlyOnline()
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            _isOnline.value = isCurrentlyOnline()
        }

        override fun onLost(network: Network) {
            _isOnline.value = isCurrentlyOnline()
        }
    }

    fun start() {
        runCatching {
            connectivityManager.registerDefaultNetworkCallback(networkCallback)
        }
        _isOnline.value = isCurrentlyOnline()
    }

    fun stop() {
        runCatching {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        }
    }

    fun isCurrentlyOnline(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
