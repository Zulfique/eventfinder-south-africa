package com.eventfinder.app.data.remote

import com.eventfinder.app.BuildConfig
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

object ApiClient {

    private const val TM_BASE_URL = "https://app.ticketmaster.com/"
    private const val OM_BASE_URL = "https://api.open-meteo.com/"
    private const val OM_GEOCODING_BASE_URL = "https://geocoding-api.open-meteo.com/"
    private const val OM_AIR_QUALITY_BASE_URL = "https://air-quality-api.open-meteo.com/"
    private const val OSM_NOMINATIM_BASE_URL = "https://nominatim.openstreetmap.org/"
    private const val OSM_OVERPASS_BASE_URL = "https://overpass-api.de/"

    private const val USER_AGENT =
        "EventFinder/1.0 (https://github.com/Zulfique/eventfinder-south-africa)"

    private val gson: Gson = GsonBuilder()
        .setLenient()
        .create()

    private fun userAgentInterceptor(): Interceptor =
        Interceptor { chain ->
            val request = chain.request()
                .newBuilder()
                .header("User-Agent", USER_AGENT)
                .build()
            chain.proceed(request)
        }

    private fun httpClient(
        cacheDir: File?,
        logging: Boolean = BuildConfig.DEBUG
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(userAgentInterceptor())

        if (logging) {
            builder.addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                }
            )
        }

        if (cacheDir != null) {
            builder.cache(
                Cache(
                    File(cacheDir, "http_cache"),
                    20L * 1024 * 1024
                )
            )
        }

        return builder.build()
    }

    private fun retrofit(
        baseUrl: String,
        cacheDir: File?
    ): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(httpClient(cacheDir))
            .addConverterFactory(
                GsonConverterFactory.create(gson)
            )
            .build()

    fun ticketmasterApi(
        cacheDir: File?
    ): TicketmasterApi =
        retrofit(TM_BASE_URL, cacheDir)
            .create(TicketmasterApi::class.java)

    fun openMeteoApi(
        cacheDir: File?
    ): OpenMeteoApi =
        retrofit(OM_BASE_URL, cacheDir)
            .create(OpenMeteoApi::class.java)

    fun openMeteoGeocodingApi(
        cacheDir: File?
    ): OpenMeteoApi =
        retrofit(OM_GEOCODING_BASE_URL, cacheDir)
            .create(OpenMeteoApi::class.java)

    fun openMeteoAirQualityApi(
        cacheDir: File?
    ): OpenMeteoAirQualityApi =
        retrofit(OM_AIR_QUALITY_BASE_URL, cacheDir)
            .create(OpenMeteoAirQualityApi::class.java)

    fun nominatimApi(
        cacheDir: File?
    ): NominatimApi =
        retrofit(OSM_NOMINATIM_BASE_URL, cacheDir)
            .create(NominatimApi::class.java)

    fun overpassApi(
        cacheDir: File?
    ): OverpassApi =
        retrofit(OSM_OVERPASS_BASE_URL, cacheDir)
            .create(OverpassApi::class.java)
}
