package com.eventfinder.app

import android.app.Application
import android.os.Build
import android.util.Log
import com.eventfinder.app.di.AppContainer
import com.eventfinder.app.utils.AppLogger
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory

/**
 * Application entry point. Owns the dependency container and performs the
 * one-time global setup (logging, osmdroid configuration) described below.
 */
class EventFinderApp : Application() {

    /** Late-initialised dependency graph. */
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        enableStartupLogging()
        container = AppContainer(this)
        startNetworkMonitoring()
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
     * osmdroid (OpenStreetMap SDK) needs its user-agent before any MapView is
     * created. It is configured here exactly once, following the official docs:
     *  - osmdroid wiki: https://github.com/osmdroid/osmdroid/wiki/How-to-use-the-osmdroid-library
     */
    private fun initialiseMapSdk() {
        Configuration.getInstance().load(this, android.preference.PreferenceManager.getDefaultSharedPreferences(this))
        Configuration.getInstance().userAgentValue = "com.eventfinder.app"
        AppLogger.d("EventFinderApp", "osmdroid configured with ${TileSourceFactory.MAPNIK.name()}")
    }
}