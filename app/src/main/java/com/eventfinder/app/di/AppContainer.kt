package com.eventfinder.app.di

import android.content.Context
import com.eventfinder.app.data.local.AppDatabase
import com.eventfinder.app.data.remote.ApiClient
import com.eventfinder.app.data.remote.EventGeocoder
import com.eventfinder.app.data.remote.PublicJsonEventClient
import com.eventfinder.app.data.repository.AuthRepository
import com.eventfinder.app.data.repository.AuthRepositoryImpl
import com.eventfinder.app.data.repository.EventDiscoveryRepository
import com.eventfinder.app.data.repository.EventRepository
import com.eventfinder.app.data.repository.EventRepositoryImpl
import com.eventfinder.app.data.repository.OpenStreetMapRepository
import com.eventfinder.app.data.repository.WeatherRepository
import com.eventfinder.app.data.repository.WeatherRepositoryImpl
import com.eventfinder.app.data.sources.SouthAfricaEventSources
import com.eventfinder.app.data.store.UserPreferences
import com.eventfinder.app.utils.AppLogger
import com.eventfinder.app.utils.NetworkMonitor

/**
 * Simple manual dependency-injection container (service locator).
 *
 * Works well for a prototype and keeps constructors explicit so repositories
 * can be replaced with fakes in unit tests without reflection or heavyweight
 * frameworks.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    internal val database: AppDatabase by lazy { AppDatabase.build(appContext) }

    val preferences: UserPreferences by lazy { UserPreferences(appContext) }

    val authRepository: AuthRepository by lazy {
        AuthRepositoryImpl(
            database = database,
            userDao = database.userDao(),
            eventDao = database.eventDao(),
            context = appContext,
            preferences = preferences
        )
    }

    val eventRepository: EventRepository by lazy {
        EventRepositoryImpl(
            database = database,
            eventDao = database.eventDao(),
            favoriteDao = database.favoriteDao(),
            rsvpDao = database.rsvpDao(),
            preferences = preferences,
            context = appContext
        )
    }

    val weatherRepository: WeatherRepository by lazy {
        WeatherRepositoryImpl(ApiClient.openMeteoApi(appContext.cacheDir))
    }

    val openStreetMapRepository: OpenStreetMapRepository by lazy {
        OpenStreetMapRepository(
            api = ApiClient.openStreetMapApi(appContext.cacheDir),
            fallbackApi = ApiClient.openStreetMapFallbackApi(appContext.cacheDir)
        )
    }

    private val publicJsonEventClient: PublicJsonEventClient by lazy {
        ApiClient.publicJsonEventClient(appContext.cacheDir)
    }

    private val eventGeocoder: EventGeocoder by lazy {
        EventGeocoder(
            geocodingApi = ApiClient.openMeteoGeocodingApi(appContext.cacheDir),
            geocodeCacheDao = database.geocodeCacheDao()
        )
    }

    private val httpClient by lazy {
        ApiClient.httpClient(appContext.cacheDir)
    }

    val eventDiscoveryRepository: EventDiscoveryRepository by lazy {
        EventDiscoveryRepository(
            eventDao = database.eventDao(),
            sources = SouthAfricaEventSources.create(publicJsonEventClient, httpClient),
            geocoder = eventGeocoder
        )
    }

    val networkMonitor: NetworkMonitor by lazy { NetworkMonitor(appContext) }

    init {
        AppLogger.d("AppContainer", "Dependency graph initialised")
    }
}
