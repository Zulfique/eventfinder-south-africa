package com.eventfinder.app.data.remote

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

object ApiClient {

    private const val OM_BASE_URL = "https://api.open-meteo.com/"
    private const val OM_GEOCODING_BASE_URL = "https://geocoding-api.open-meteo.com/"
    private const val OM_AIR_QUALITY_BASE_URL = "https://air-quality-api.open-meteo.com/"
    private const val OSM_OVERPASS_BASE_URL = "https://overpass-api.de/"
    private const val OSM_OVERPASS_FALLBACK_URL = "https://overpass.kumi.systems/"

    private val gson: Gson = GsonBuilder()
        .setLenient()
        .create()

    private fun httpClient(cacheDir: File?): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request()
                    .newBuilder()
                    .header(
                        "User-Agent",
                        "EventFinder/1.0 (https://github.com/Zulfique/eventfinder-south-africa)"
                    )
                    .build()
                chain.proceed(request)
            }
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.NONE
                }
            )

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

    fun openMeteoApi(cacheDir: File?): OpenMeteoApi =
        retrofit(
            OM_BASE_URL,
            cacheDir
        ).create(OpenMeteoApi::class.java)

    fun openMeteoGeocodingApi(cacheDir: File?): OpenMeteoGeocodingApi =
        retrofit(
            OM_GEOCODING_BASE_URL,
            cacheDir
        ).create(OpenMeteoGeocodingApi::class.java)

    fun openMeteoAirQualityApi(cacheDir: File?): OpenMeteoAirQualityApi =
        retrofit(
            OM_AIR_QUALITY_BASE_URL,
            cacheDir
        ).create(OpenMeteoAirQualityApi::class.java)

    fun openStreetMapApi(cacheDir: File?): OpenStreetMapApi =
        retrofit(
            OSM_OVERPASS_BASE_URL,
            cacheDir
        ).create(OpenStreetMapApi::class.java)

    fun openStreetMapFallbackApi(cacheDir: File?): OpenStreetMapApi =
        retrofit(
            OSM_OVERPASS_FALLBACK_URL,
            cacheDir
        ).create(OpenStreetMapApi::class.java)

    fun publicJsonEventClient(cacheDir: File?): PublicJsonEventClient =
        PublicJsonEventClient(
            httpClient = httpClient(cacheDir),
            gson = gson
        )

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
}
