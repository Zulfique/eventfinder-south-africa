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
    private const val OSM_OVERPASS_BASE_URL = "https://overpass-api.de/"

    private val gson: Gson = GsonBuilder()
        .setLenient()
        .create()

    private fun httpClient(cacheDir: File?): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
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

    fun openStreetMapApi(cacheDir: File?): OpenStreetMapApi =
        retrofit(
            OSM_OVERPASS_BASE_URL,
            cacheDir
        ).create(OpenStreetMapApi::class.java)

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
