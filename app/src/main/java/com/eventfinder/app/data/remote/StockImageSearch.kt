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

    /**
     * Returns a stock image matching [query], or null when both tiers fail.
     *
     * [topics] are the event's subject keywords ("motorcycle", "race", ...):
     * a hit whose description/tags mention none of them is off-topic — a Mars
     * rover photo for a motorcycle event, a hotel bedroom for a car
     * championship — so it is skipped in favour of the next hit, and finally
     * of the app's branded category artwork. An empty [topics] (unrecognised
     * category) accepts the first usable hit.
     */
    suspend fun findImage(query: String, topics: Collection<String> = emptyList()): StockImage? {
        if (httpClient == null) return null
        val trimmed = query.trim().lowercase()
        if (trimmed.isBlank()) return null
        val key = if (topics.isEmpty()) trimmed
        else "$trimmed|${topics.map { it.lowercase() }.sorted().joinToString(",")}" 

        cache[key]?.let { return it }
        if (misses.contains(key)) return null

        val found = try {
            withContext(Dispatchers.IO) {
                searchUnsplash(trimmed, topics) ?: searchPixabay(trimmed, topics)
            }
        } catch (t: Exception) {
            // Never let artwork lookup break the event sync around it.
            AppLogger.w(TAG, "Stock image search failed for '$trimmed': ${t.message}")
            null
        }

        if (found != null) {
            cache[key] = found
            AppLogger.i(TAG, "Stock image for '$trimmed' -> ${found.credit}")
            return found
        }
        misses.add(key)
        return null
    }

    // ---- Tier 4: Unsplash -------------------------------------------------

    private fun searchUnsplash(query: String, topics: Collection<String>): StockImage? {
        val key = unsplashAccessKey?.trim().orEmpty()
        if (key.isBlank()) return null

        val url = UNSPLASH_ENDPOINT.toHttpUrl().newBuilder()
            .addQueryParameter("query", query)
            .addQueryParameter("orientation", "landscape")
            .addQueryParameter("content_filter", "high")
            // Several candidates per request so one off-topic photo does not
            // waste the whole lookup (requests, not photos, hit the quota).
            .addQueryParameter("count", "4")
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
            return parseUnsplash(body, topics)
        }
    }

    /** Parses an Unsplash `/photos/random` response, skipping off-topic hits. */
    internal fun parseUnsplash(json: String, topics: Collection<String> = emptyList()): StockImage? {
        return try {
            // /photos/random answers a JSON *array* (that shape broke the old
            // object-only parser and silently dropped every valid photo), while
            // /search/photos answers {results:[...]}.
            val element = JsonParser.parseString(json)
            val photos = when {
                element.isJsonArray ->
                    element.asJsonArray.mapNotNull { runCatching { it.asJsonObject }.getOrNull() }
                element.isJsonObject -> {
                    val obj = element.asJsonObject
                    if (obj.has("results") && obj.get("results").isJsonArray) {
                        obj.getAsJsonArray("results")
                            .mapNotNull { runCatching { it.asJsonObject }.getOrNull() }
                    } else listOf(obj)
                }
                else -> emptyList()
            }

            photos.firstNotNullOfOrNull { photo -> unsplashImage(photo, topics) }
        } catch (t: Exception) {
            AppLogger.d(TAG, "Could not parse Unsplash response: ${t.message}")
            null
        }
    }

    /** Builds an image from one Unsplash photo, or null when off-topic. */
    private fun unsplashImage(
        photo: com.google.gson.JsonObject,
        topics: Collection<String>
    ): StockImage? {
        val url = photo.getAsJsonObject("urls")?.get("regular")?.asString
            ?: photo.getAsJsonObject("urls")?.get("raw")?.asString
            ?: return null
        if (!isRelevant(unsplashText(photo), topics)) return null
        val author = photo.getAsJsonObject("user")?.get("name")?.asString.orEmpty()
        val page = photo.getAsJsonObject("links")?.get("html")?.asString.orEmpty()

        return StockImage(
            url = url.substringBefore('?') + "?w=1200&q=80&fm=jpg",
            credit = buildCredit(author, "Unsplash", page)
        )
    }

    /** The searchable prose Unsplash publishes about a photo. */
    private fun unsplashText(photo: com.google.gson.JsonObject): String = buildString {
        for (field in listOf("description", "alt_description")) {
            photo.get(field)
                ?.takeIf { it.isJsonPrimitive }
                ?.asString
                ?.takeIf { it.isNotBlank() }
                ?.let { append(it).append(' ') }
        }
        photo.getAsJsonArray("tags")?.forEach { tag ->
            val title = (tag as? com.google.gson.JsonObject)?.get("title")
                ?.takeIf { it.isJsonPrimitive }?.asString
            if (!title.isNullOrBlank()) append(title).append(' ')
        }
    }

    // ---- Tier 5: Pixabay --------------------------------------------------

    private fun searchPixabay(query: String, topics: Collection<String>): StockImage? {
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
            return parsePixabay(body, topics)
        }
    }

    /**
     * Parses a Pixabay `/api/` response: only hits whose comma-separated
     * `tags` match the event's [topics] qualify, and among those landscape
     * hits >=1280px are preferred.
     */
    internal fun parsePixabay(json: String, topics: Collection<String> = emptyList()): StockImage? {
        return try {
            val root = JsonParser.parseString(json).asJsonObject
            val hits = root.getAsJsonArray("hits") ?: return null
            val candidates = hits.mapNotNull { runCatching { it.asJsonObject }.getOrNull() }
                .filter { isRelevant(it.get("tags")?.asString, topics) }
            val hit = candidates.firstOrNull { (it.get("imageWidth")?.asInt ?: 0) >= 1280 }
                ?: candidates.firstOrNull()
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

    /**
     * Topical relevance gate: does any of [topics] appear in [text] as a
     * word-start ("motorcycle" matches "Motorcycles racing", "car" does not
     * match "scarecrow")? Empty topics — an unrecognised category where no
     * judgement is possible — accept everything; blank text has nothing to
     * disprove either, so only a *mismatching* description rejects a hit.
     */
    internal fun isRelevant(text: String?, topics: Collection<String>): Boolean {
        if (topics.isEmpty()) return true
        val hay = text?.lowercase().orEmpty()
        if (hay.isBlank()) return true
        return topics.any { topic ->
            val t = topic.trim().lowercase()
            t.isNotEmpty() && Regex("""\b${Regex.escape(t)}""").containsMatchIn(hay)
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
