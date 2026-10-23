package com.eventfinder.app.di

import android.content.Context
import com.eventfinder.app.data.cloud.CloudSyncManager
import com.eventfinder.app.data.cloud.R2ImageUploader
import com.eventfinder.app.data.cloud.SyncCoordinator
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
import com.eventfinder.app.utils.PlaceResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Simple manual dependency-injection container (service locator).
 *
 * Works well for a prototype and keeps constructors explicit so repositories
 * can be replaced with fakes in unit tests without reflection or heavyweight
 * frameworks.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    /** Application-wide coroutine scope for background work (sync, push). */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    internal val database: AppDatabase by lazy { AppDatabase.build(appContext) }

    val preferences: UserPreferences by lazy { UserPreferences(appContext) }

    /** REST backend client (ASP.NET Core on Render; optional at runtime). */
    val eventFinderApiClient: com.eventfinder.app.data.remote.EventFinderApiClient by lazy {
        com.eventfinder.app.data.remote.EventFinderApiClient()
    }

    val cloudSyncManager: CloudSyncManager by lazy {
        CloudSyncManager(
            context = appContext,
            eventDao = database.eventDao(),
            favoriteDao = database.favoriteDao(),
            rsvpDao = database.rsvpDao(),
            preferences = preferences
        )
    }

    /**
     * Blob storage uploader (Cloudflare R2 via the REST API). Replaces the
     * Firebase Storage uploader, which required the paid Blaze plan.
     */
    val imageUploader: R2ImageUploader by lazy {
        R2ImageUploader(
            context = appContext,
            api = eventFinderApiClient,
            session = preferences
        )
    }

    val authRepository: AuthRepository by lazy {
        AuthRepositoryImpl(
            database = database,
            userDao = database.userDao(),
            eventDao = database.eventDao(),
            context = appContext,
            preferences = preferences,
            api = eventFinderApiClient
        )
    }

    val eventRepository: EventRepository by lazy {
        EventRepositoryImpl(
            database = database,
            eventDao = database.eventDao(),
            favoriteDao = database.favoriteDao(),
            rsvpDao = database.rsvpDao(),
            preferences = preferences,
            context = appContext,
            cloudSyncManager = cloudSyncManager,
            apiClient = eventFinderApiClient
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

    /**
     * Resolves the user's current place (device fix → configured default city →
     * nation-wide fallback) so the app works from any South African city.
     */
    val placeResolver: PlaceResolver by lazy {
        PlaceResolver(
            context = appContext,
            preferences = preferences,
            geocoder = eventGeocoder
        )
    }

    private val httpClient by lazy {
        ApiClient.httpClient(appContext.cacheDir)
    }

    val eventDiscoveryRepository: EventDiscoveryRepository by lazy {
        EventDiscoveryRepository(
            eventDao = database.eventDao(),
            sources = SouthAfricaEventSources.create(publicJsonEventClient, httpClient),
            geocoder = eventGeocoder,
            apiClient = eventFinderApiClient
        )
    }

    val networkMonitor: NetworkMonitor by lazy { NetworkMonitor(appContext) }

    val syncCoordinator: SyncCoordinator by lazy {
        SyncCoordinator(
            networkMonitor = networkMonitor,
            cloudSyncManager = cloudSyncManager,
            eventDiscoveryRepository = eventDiscoveryRepository,
            scope = applicationScope
        )
    }

    init {
        AppLogger.d("AppContainer", "Dependency graph initialised")
    }
}
