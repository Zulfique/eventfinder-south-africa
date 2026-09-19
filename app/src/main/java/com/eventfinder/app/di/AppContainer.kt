package com.eventfinder.app.di

import android.content.Context
import com.eventfinder.app.BuildConfig
import com.eventfinder.app.data.local.AppDatabase
import com.eventfinder.app.data.remote.ApiClient
import com.eventfinder.app.data.repository.AuthRepository
import com.eventfinder.app.data.repository.AuthRepositoryImpl
import com.eventfinder.app.data.repository.EventRepository
import com.eventfinder.app.data.repository.EventRepositoryImpl
import com.eventfinder.app.data.repository.WeatherRepository
import com.eventfinder.app.data.repository.WeatherRepositoryImpl
import com.eventfinder.app.data.store.UserPreferences
import com.eventfinder.app.utils.AppLogger
import com.eventfinder.app.utils.NetworkMonitor

/**
 * Simple manual dependency-injection container (service locator).
 *
 * Works well for a prototype and keeps constructors explicit so repositories
 * can be replaced with fakes in unit tests without reflection or heavyweight
 * frameworks. Annotation-based DI (Hilt) is documented as a final-POE upgrade.
 *
 * References:
 *  - Android Developers, "Manual dependency injection":
 *    https://developer.android.com/training/dependency-injection/manual
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
            pendingSyncDao = database.pendingSyncDao(),
            ticketmasterApi = ApiClient.ticketmasterApi(appContext.cacheDir),
            apiKey = BuildConfig.TICKETMASTER_API_KEY,
            preferences = preferences
        )
    }

    val weatherRepository: WeatherRepository by lazy {
        WeatherRepositoryImpl(ApiClient.openMeteoApi(appContext.cacheDir))
    }

    val networkMonitor: NetworkMonitor by lazy { NetworkMonitor(appContext) }

    init {
        AppLogger.d("AppContainer", "Dependency graph initialised")
    }
}