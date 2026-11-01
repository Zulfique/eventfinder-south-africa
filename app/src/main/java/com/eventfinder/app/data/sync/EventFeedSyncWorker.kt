package com.eventfinder.app.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.eventfinder.app.EventFinderApp
import com.eventfinder.app.utils.AppLogger
import java.util.concurrent.TimeUnit

/**
 * Periodic background refresh of the public event feeds (FR-10: background
 * synchronisation). Complements [com.eventfinder.app.data.cloud.SyncCoordinator],
 * which only reacts to offline→online transitions while the process is alive —
 * WorkManager keeps the refresh cadence even when the app stays closed.
 *
 * The work is network-aware (skips while offline, so it never blocks or drains
 * battery in airplane mode) and idempotent: the same Room-backed repositories
 * are used, so a refresh simply upserts.
 */
class EventFeedSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val container = (applicationContext as EventFinderApp).container
            if (!container.networkMonitor.isOnline.value) {
                AppLogger.d(TAG, "Offline; skipping scheduled feed sync")
                return Result.success()
            }
            container.eventDiscoveryRepository.refresh()
            AppLogger.i(TAG, "Periodic feed sync finished")
            Result.success()
        } catch (t: Throwable) {
            AppLogger.w(TAG, "Periodic feed sync failed: ${t.message}")
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "EventFeedSyncWorker"
        private const val UNIQUE_NAME = "event_feed_periodic_sync"
        private const val INTERVAL_HOURS = 6L

        /** (Re-)schedules the periodic refresh. Idempotent (KEEP policy). */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<EventFeedSyncWorker>(
                INTERVAL_HOURS, TimeUnit.HOURS
            ).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
