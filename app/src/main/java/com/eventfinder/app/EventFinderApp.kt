package com.eventfinder.app

import android.app.Application
import android.content.Context
import android.os.Build
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.eventfinder.app.di.AppContainer
import com.eventfinder.app.notifications.EventFinderMessagingService
import com.eventfinder.app.utils.AppLogger
import okhttp3.OkHttpClient
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory

/**
 * Application entry point. Owns the dependency container and performs the
 * one-time global setup (logging, osmdroid configuration, Firebase topics,
 * automatic reconnect sync) described below.
 */
class EventFinderApp : Application(), ImageLoaderFactory {

    /** Late-initialised dependency graph. */
    lateinit var container: AppContainer
        private set

    /**
     * Shared client for Coil. Wikimedia returns **403** for OkHttp's default
     * "okhttp/x.y.z" user-agent, which made every Commons cover-art URL fail to
     * render in the app while the very same URL opened fine in the browser.
     * Every image request therefore carries an identifying user-agent.
     */
    private val imageHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", IMAGE_USER_AGENT)
                        .build()
                )
            }
            .build()
    }

    /**
     * Image memory is deliberately conservative: entry-level South African
     * phones ship 2-4 GB of RAM, where Coil's default 25% bitmap cache and
     * full-colour bitmaps push the app towards the low-memory killer. A 15%
     * cache, RGB_565 bitmaps and a bounded 64 MB disk cache keep it smooth.
     */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .callFactory(imageHttpClient)
            .memoryCache {
                coil.memory.MemoryCache.Builder(this)
                    .maxSizePercent(0.15)
                    .build()
            }
            .diskCache {
                coil.disk.DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(64L * 1024 * 1024)
                    .build()
            }
            .allowRgb565(true)
            .build()

    override fun onCreate() {
        super.onCreate()
        enableStartupLogging()
        com.eventfinder.app.utils.CrashReporter.install(this)
        container = AppContainer(this)
        startNetworkMonitoring()
        startReconnectSync()
        schedulePeriodicSync()
        subscribeToPushTopics()
        initialiseMapSdk()
    }

    /** Platform logging util used before the app logger is available. */
    private fun enableStartupLogging() {
        Log.i("EventFinder", "Application starting (SDK ${Build.VERSION.SDK_INT})")
    }

    /** Track connectivity so screens can switch to offline mode instantly. */
    private fun startNetworkMonitoring() {
        container.networkMonitor.start()
        AppLogger.d("EventFinderApp", "Network monitoring started")
    }

    /**
     * Wires the offline→online automatic synchronisation (POE: Offline Mode
     * with Synchronisation). [SyncCoordinator] pushes Room changes to Firestore
     * and refreshes discovery feeds as soon as connectivity returns.
     */
    private fun startReconnectSync() {
        container.syncCoordinator.start()
        AppLogger.d("EventFinderApp", "Reconnect sync coordinator started")
    }

    /**
     * Periodic WorkManager refresh so discovery feeds update on a cadence even
     * when the app stays closed (FR-10: background synchronisation).
     */
    private fun schedulePeriodicSync() {
        com.eventfinder.app.data.sync.EventFeedSyncWorker.schedule(this)
        AppLogger.d("EventFinderApp", "Periodic background sync scheduled")
    }

    /** Subscribes this install to the shared FCM topic for real-time pushes. */
    private fun subscribeToPushTopics() {
        EventFinderMessagingService.subscribeToTopics()
    }

    /**
     * osmdroid (OpenStreetMap SDK) needs its user-agent before any MapView is
     * created. It is configured here exactly once, following the official docs:
     *  - osmdroid wiki: https://github.com/osmdroid/osmdroid/wiki/How-to-use-the-osmdroid-library
     */
    private fun initialiseMapSdk() {
        // Use device-protected storage so osmdroid can be initialised before the
        // user unlocks credential-encrypted storage (e.g. during direct boot or
        // from instrumented test runners).
        Configuration.getInstance().load(
            createDeviceProtectedStorageContext(),
            createDeviceProtectedStorageContext().getSharedPreferences("${packageName}_preferences", Context.MODE_PRIVATE)
        )
        Configuration.getInstance().userAgentValue = IMAGE_USER_AGENT
        // Keep the offline tile cache bounded so map browsing cannot slowly
        // consume a low-storage device.
        Configuration.getInstance().tileFileSystemCacheMaxBytes = 40L * 1024 * 1024
        Configuration.getInstance().tileFileSystemCacheTrimBytes = 20L * 1024 * 1024
        AppLogger.d("EventFinderApp", "osmdroid configured with ${TileSourceFactory.MAPNIK.name()}")
    }

    companion object {
        private const val IMAGE_USER_AGENT =
            "EventFinder/1.1 (https://github.com/Zulfique/eventfinder-south-africa)"
    }
}