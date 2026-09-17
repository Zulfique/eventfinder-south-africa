package com.eventfinder.app.data.remote

import com.eventfinder.app.BuildConfig
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Retrofit + OkHttp wiring for the REST API clients.
 *
 * A shared OkHttp client provides response caching (so already-fetched event
 * pages survive offline launches) and request logging. Gson is configured to be
 * lenient so third-party payloads with surprising fields never crash parsing.
 */
object ApiClient {

    private const val TM_BASE_URL = "https://app.ticketmaster.com/"
    private const val OM_BASE_URL = "https://api.open-meteo.com/"

    private val gson: Gson = GsonBuilder()
        .setLenient()
        .create()

    /** Builds a shared OkHttp client with an HTTP cache and logging. */
    private fun httpClient(cacheDir: File?): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
                else HttpLoggingInterceptor.Level.NONE
            })

        if (cacheDir != null) {
            builder.cache(Cache(File(cacheDir, "http_cache"), 10L * 1024 * 1024))
        }
        return builder.build()
    }

    fun ticketmasterApi(cacheDir: File?): TicketmasterApi =
        retrofit(TM_BASE_URL, cacheDir).create(TicketmasterApi::class.java)

    fun openMeteoApi(cacheDir: File?): OpenMeteoApi =
        retrofit(OM_BASE_URL, cacheDir).create(OpenMeteoApi::class.java)

    private fun retrofit(baseUrl: String, cacheDir: File?): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(httpClient(cacheDir))
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
}