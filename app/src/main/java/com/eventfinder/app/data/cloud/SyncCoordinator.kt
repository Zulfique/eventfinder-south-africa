package com.eventfinder.app.data.cloud

import com.eventfinder.app.data.repository.EventDiscoveryRepository
import com.eventfinder.app.utils.AppLogger
import com.eventfinder.app.utils.NetworkMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Listens to [NetworkMonitor] and, on every offline→online transition,
 * automatically triggers:
 *  1. a Cloud Firestore bidirectional sync ([CloudSyncManager.syncNow])
 *  2. a refresh of the public event discovery feeds
 *
 * POE requirement: "Offline Mode with Synchronisation … automatically
 * synchronises data once a network connection is re-established."
 *
 * A short debounce guards against flapping connectivity so a brief blip does
 * not queue a storm of syncs.
 */
class SyncCoordinator(
    private val networkMonitor: NetworkMonitor,
    private val cloudSyncManager: CloudSyncManager,
    private val eventDiscoveryRepository: EventDiscoveryRepository,
    private val scope: CoroutineScope
) {

    private val tag = "SyncCoordinator"
    private var wasOnline: Boolean? = null

    /** Begins collecting connectivity changes. Call once from Application.onCreate. */
    fun start() {
        scope.launch {
            // StateFlow only emits when the value actually changes, so no
            // explicit distinctUntilChanged() is required.
            networkMonitor.isOnline
                .collect { online ->
                    val previous = wasOnline
                    wasOnline = online
                    if (online && previous == false) {
                        AppLogger.i(tag, "Connection restored - starting automatic sync")
                        // Debounce flapping radios.
                        delay(DEBOUNCE_MS)
                        runCatching { cloudSyncManager.syncNow() }
                            .onFailure { AppLogger.w(tag, "Cloud sync failed: ${it.message}") }
                        runCatching { eventDiscoveryRepository.refresh() }
                            .onFailure { AppLogger.w(tag, "Feed refresh failed: ${it.message}") }
                        AppLogger.i(tag, "Automatic sync finished")
                    } else if (online && previous == null) {
                        // First observation: already online at launch — the
                        // HomeViewModel already refreshes on init, so only do
                        // the cloud catch-up here.
                        runCatching { cloudSyncManager.syncNow() }
                            .onFailure { AppLogger.w(tag, "Initial cloud sync failed: ${it.message}") }
                    }
                }
        }
        AppLogger.d(tag, "Sync coordinator started")
    }

    companion object {
        private const val DEBOUNCE_MS = 1_500L
    }
}
