package com.eventfinder.app.data.remote

import com.eventfinder.app.utils.AppLogger
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap

/** A stock photograph with its required attribution line. */
data class StockImage(val url: String, val credit: String)

/**
 * Priorities 4 and 5 of the event-image resolution pipeline:
 *
 *  1. event organizer's own cover          (handled upstream — [EventImageResolver] returns early)
 *  2. original event website `og:image`    (handled upstream in [EventImageResolver])
 *  3. Wikimedia Commons                    (handled upstream in [CommonsImageSearch])
 *  4. **Unsplash** — high-quality event photography (free demo tier, 50 req/hour)
 *  5. **Pixabay** — category-specific fallback photos (free tier, 100 req/minute)
 *
 * Both tiers only run when 1–3 fail, so the per-hour Unsplash demo quota is
 * never burned on events that already have real artwork. The keys are *public*
 * client keys (both APIs are designed for client-side usage with per-key rate
 * limiting), supplied via BuildConfig; a blank key disables that tier.
 *
 * Queries and misses are memoised so one sync issues at most one request per
 * unique query per provider. A null [httpClient] disables the feature.
 */
class StockImageSearch(
    private val httpClient: OkHttpClient?,
    private val unsplashAccessKey: String? = null,
    private val pixabayKey: String? = null
) {

    private val cache = ConcurrentHashMap<String, StockImage>()
    private val misses = ConcurrentHashMap.newKeySet<String>()

    /** Returns a stock image matching [query], or null when both tiers fail. */
    suspend fun findImage(query: String): StockImage? {
        if (httpClient == null) return null
        val key = query.trim().lowercase()
        if (key.isBlank()) return null

        cache[key]?.let { return it }
        if (misses.contains(key)) return null

        val found = try {
            withContext(Dispatchers.IO) {
                searchUnsplash(key) ?: searchPixabay(key)
            }
        } catch (t: Exception) {
            // Never let artwork lookup break the event sync around it.
            AppLogger.w(TAG, "Stock image search failed for '$key': ${t.message}")
            null
        }

        if (found != null) {
            cache[key] = found
            AppLogger.i(TAG, "Stock image for '$key' -> ${found.credit}")
            return found
        }
        misses.add(key)
        return null
    }

    // ---- Tier 4: Unsplash -------------------------------------------------

    private fun searchUnsplash(query: String): StockImage? {
        val key = unsplashAccessKey?.trim().orEmpty()
        if (key.isBlank()) return null

        val url = UNSPLASH_ENDPOINT.toHttpUrl().newBuilder()
            .addQueryParameter("query", query)
            .addQueryParameter("orientation", "landscape")
            .addQueryParameter("content_filter", "high")
            .addQueryParameter("count", "1")
            .build()
            .toString()

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Client-ID $key")
            .header("Accept-Version", "v1")
            .header("Accept", "application/json")
            .build()

        httpClient!!.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                AppLogger.d(TAG, "Unsplash HTTP ${response.code} for '$query'")
                return null
            }
            val body = response.body?.string() ?: return null
            return parseUnsplash(body)
        }
    }

    /** Parses an Unsplash `/photos/random` response. */
    internal fun parseUnsplash(json: String): StockImage? {
        return try {
            val root = JsonParser.parseString(json).asJsonObject
            val photo = when {
                root.has("results") && root.get("results").isJsonArray ->
                    root.getAsJsonArray("results").firstOrNull()?.asJsonObject
                else -> root
            } ?: return null

            val url = photo.getAsJsonObject("urls")?.get("regular")?.asString
                ?: photo.getAsJsonObject("urls")?.get("raw")?.asString
                ?: return null
            val author = photo.getAsJsonObject("user")?.get("name")?.asString.orEmpty()
            val page = photo.getAsJsonObject("links")?.get("html")?.asString.orEmpty()

            StockImage(
                url = url.substringBefore('?') + "?w=1200&q=80&fm=jpg",
                credit = buildCredit(author, "Unsplash", page)
            )
        } catch (t: Exception) {
            AppLogger.d(TAG, "Could not parse Unsplash response: ${t.message}")
            null
        }
    }

    // ---- Tier 5: Pixabay --------------------------------------------------

    private fun searchPixabay(query: String): StockImage? {
        val key = pixabayKey?.trim().orEmpty()
        if (key.isBlank()) return null

        val url = PIXABAY_ENDPOINT.toHttpUrl().newBuilder()
            .addQueryParameter("key", key)
            .addQueryParameter("q", query)
            .addQueryParameter("image_type", "photo")
            .addQueryParameter("orientation", "horizontal")
            .addQueryParameter("category", pixabayCategory(query))
            .addQueryParameter("safesearch", "true")
            .addQueryParameter("order", "popular")
            .addQueryParameter("per_page", "10")
            .build()
            .toString()

        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .build()

        httpClient!!.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                AppLogger.d(TAG, "Pixabay HTTP ${response.code} for '$query'")
                return null
            }
            val body = response.body?.string() ?: return null
            return parsePixabay(body)
        }
    }

    /** Parses a Pixabay `/api/` response, preferring landscape hits >=1280px. */
    internal fun parsePixabay(json: String): StockImage? {
        return try {
            val root = JsonParser.parseString(json).asJsonObject
            val hits = root.getAsJsonArray("hits") ?: return null
            val hit = hits.mapNotNull { runCatching { it.asJsonObject }.getOrNull() }
                .firstOrNull { (it.get("imageWidth")?.asInt ?: 0) >= 1280 }
                ?: hits.firstOrNull()?.asJsonObject
                ?: return null

            val url = hit.get("largeImageURL")?.asString
                ?: hit.get("webformatURL")?.asString
                ?: return null
            val author = hit.get("user")?.asString.orEmpty()
            val page = hit.get("pageURL")?.asString.orEmpty()

            StockImage(url = url, credit = buildCredit(author, "Pixabay", page))
        } catch (t: Exception) {
            AppLogger.d(TAG, "Could not parse Pixabay response: ${t.message}")
            null
        }
    }

    // ---- Shared helpers ---------------------------------------------------

    /**
     * Maps free-text queries onto Pixabay's fixed category filter; anything
     * unrecognised searches all categories (the parameter must be one of the
     * documented values or omitted, so we only set it when confident).
     */
    internal fun pixabayCategory(query: String): String? {
        val q = query.lowercase()
        return when {
            // NOTE: "festival" deliberately maps nowhere — a food/wine/arts
            // festival must not be pulled into the music bucket.
            listOf("music", "concert", "dj", "band", "jazz").any { q.contains(it) } ->
                "music"
            listOf("food", "wine", "dining", "culinary", "beer").any { q.contains(it) } ->
                "food"
            listOf("sport", "football", "marathon", "rugby", "race").any { q.contains(it) } ->
                "sports"
            listOf("business", "conference", "networking", "technology", "expo").any { q.contains(it) } ->
                "business"
            else -> null
        }
    }

    private fun buildCredit(author: String, site: String, page: String): String {
        val name = author.trim().ifBlank { "Anonymous" }
        return if (page.isBlank()) "Photo by $name on $site" else "Photo by $name on $site ($page)"
    }

    private companion object {
        private const val TAG = "StockImageSearch"
        private const val UNSPLASH_ENDPOINT = "https://api.unsplash.com/photos/random"
        private const val PIXABAY_ENDPOINT = "https://pixabay.com/api/"
    }
}
