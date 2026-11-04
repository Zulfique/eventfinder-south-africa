package com.eventfinder.app.data.repository

import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Real road routing for the map: user location -> event venue.
 *
 * Providers: Valhalla (driving/walking/cycling) first, then the OSRM demo for
 * driving. Successful routes are cached on disk so a previous route can be
 * shown offline. Geometry is never fabricated: with no network and no cached
 * match the request fails and the panel shows its retry state.
 */
interface RouteRepository {
    suspend fun route(
        fromLat: Double,
        fromLng: Double,
        toLat: Double,
        toLng: Double,
        mode: TravelMode
    ): Result<RouteRequestResult>
}

class RouteRepositoryImpl(
    httpClient: OkHttpClient,
    cacheDir: File?
) : RouteRepository {

    private val client: OkHttpClient = httpClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val cacheFile: File? = cacheDir?.let { File(it, "route_cache.json") }
    private val lock = Any()

    private data class CacheEntry(val key: String, val routes: List<RouteOption>)
    private data class CacheStore(val entries: List<CacheEntry>)

    override suspend fun route(
        fromLat: Double,
        fromLng: Double,
        toLat: Double,
        toLng: Double,
        mode: TravelMode
    ): Result<RouteRequestResult> = withContext(Dispatchers.IO) {
        val fresh = runCatching { fetchValhalla(fromLat, fromLng, toLat, toLng, mode) }
            .recoverCatching { error ->
                if (error is CancellationException) throw error
                if (mode == TravelMode.DRIVING) fetchOsrm(fromLat, fromLng, toLat, toLng)
                else throw error
            }
            .getOrNull()
            ?.filter { it.points.size > 1 }

        if (!fresh.isNullOrEmpty()) {
            persistCache(toLat, toLng, mode, fresh)
            Result.success(RouteRequestResult(fresh, fromCache = false))
        } else {
            val cached = readCache(cacheKey(toLat, toLng, mode))
                ?.filter { it.points.size > 1 }
            if (!cached.isNullOrEmpty()) {
                Result.success(RouteRequestResult(cached, fromCache = true))
            } else {
                Result.failure(RouteUnavailableException())
            }
        }
    }

    /** Valhalla: primary route + alternatives, geometry as polyline6 per leg. */
    private fun fetchValhalla(
        fromLat: Double,
        fromLng: Double,
        toLat: Double,
        toLng: Double,
        mode: TravelMode
    ): List<RouteOption> {
        val payload = JSONObject().apply {
            put(
                "locations",
                JSONArray()
                    .put(JSONObject().put("lat", fromLat).put("lon", fromLng))
                    .put(JSONObject().put("lat", toLat).put("lon", toLng))
            )
            put("costing", mode.valhallaCosting)
            put("alternates", 2)
            put("directions_options", JSONObject().put("units", "kilometers"))
        }
        val url: HttpUrl = "https://valhalla1.openstreetmap.de/route"
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("json", payload.toString())
            .build()

        val root = JSONObject(execute(url))
        val trips = when {
            root.has("trips") -> root.getJSONArray("trips")
            root.has("trip") -> JSONArray().put(root.getJSONObject("trip"))
            else -> throw IOException(root.optString("error", "routing error"))
        }

        return (0 until trips.length()).map { i ->
            val trip = trips.getJSONObject(i)
            val summary = trip.getJSONObject("summary")
            val legs = trip.getJSONArray("legs")
            val points = (0 until legs.length()).flatMap { leg ->
                decodePolyline6(legs.getJSONObject(leg).getString("shape"))
            }
            RouteOption(
                distanceMeters = summary.getDouble("length") * 1000.0,
                durationSeconds = summary.getDouble("time"),
                points = points
            )
        }
    }

    /** OSRM demo fallback (driving): GeoJSON coordinates + metrics. */
    private fun fetchOsrm(
        fromLat: Double,
        fromLng: Double,
        toLat: Double,
        toLng: Double
    ): List<RouteOption> {
        val url: HttpUrl = (
            "https://router.project-osrm.org/route/v1/driving/" +
                "$fromLng,$fromLat;$toLng,$toLat" +
                "?overview=full&geometries=geojson&alternatives=true"
            ).toHttpUrl()

        val root = JSONObject(execute(url))
        if (root.optString("code") != "Ok") throw IOException("routing error")
        val routes = root.getJSONArray("routes")
        return (0 until routes.length()).map { i ->
            val route = routes.getJSONObject(i)
            val coords = route.getJSONObject("geometry").getJSONArray("coordinates")
            val points = (0 until coords.length()).map { j ->
                val pair = coords.getJSONArray(j)
                RoutePoint(lat = pair.getDouble(1), lng = pair.getDouble(0))
            }
            RouteOption(
                distanceMeters = route.getDouble("distance"),
                durationSeconds = route.getDouble("duration"),
                points = points
            )
        }
    }

    private fun execute(url: HttpUrl): String {
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            return response.body?.string() ?: throw IOException("empty response")
        }
    }

    private fun cacheKey(toLat: Double, toLng: Double, mode: TravelMode): String =
        "%.4f,%.4f|%s".format(toLat, toLng, mode.name)

    private fun persistCache(
        toLat: Double,
        toLng: Double,
        mode: TravelMode,
        routes: List<RouteOption>
    ) {
        val file = cacheFile ?: return
        synchronized(lock) {
            runCatching {
                val key = cacheKey(toLat, toLng, mode)
                val entries = (
                    readStore().entries.filterNot { it.key == key } +
                        CacheEntry(key, routes)
                    ).takeLast(MAX_CACHE_ENTRIES)
                file.writeText(gson.toJson(CacheStore(entries)))
            }
        }
    }

    private fun readCache(key: String): List<RouteOption>? {
        val file = cacheFile ?: return null
        if (!file.exists()) return null
        return synchronized(lock) {
            runCatching { readStore().entries.lastOrNull { it.key == key }?.routes }.getOrNull()
        }
    }

    private fun readStore(): CacheStore =
        runCatching {
            gson.fromJson(cacheFile!!.readText(), CacheStore::class.java)
                ?: CacheStore(emptyList())
        }.getOrDefault(CacheStore(emptyList()))

    private companion object {
        const val MAX_CACHE_ENTRIES = 8
    }
}
