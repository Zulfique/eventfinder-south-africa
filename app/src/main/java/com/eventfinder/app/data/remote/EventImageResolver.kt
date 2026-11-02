package com.eventfinder.app.data.remote

import android.net.Uri
import com.eventfinder.app.data.remote.model.RemoteEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Resolves an event's cover image through the app's priority pipeline:
 *
 *  1. Event organizer's own cover — [RemoteEvent.imageUrl] already set, so the
 *     resolver returns immediately (accurate event branding).
 *  2. Original event website `og:image` (and twitter:image) scraped from the
 *     event page ([EventImageResolver.extractImage]).
 *  3. Wikimedia Commons — authentic South African location imagery via
 *     [CommonsImageSearch] (keyless, openly licensed).
 *  4. Unsplash — high-quality event photography via [StockImageSearch].
 *  5. Pixabay — category-specific fallback photos via [StockImageSearch].
 *
 * Tiers 3–5 only run when every better option failed, and any failure leaves
 * the event untouched so the UI's branded placeholder art still shows.
 */
class EventImageResolver(
    private val client: OkHttpClient,
    private val commons: CommonsImageSearch? = null,
    private val stock: StockImageSearch? = null
) {

    /**
     * Runs the whole chain on [Dispatchers.IO]: callers include
     * HomeViewModel's main-dispatcher scope and tier 2 is a synchronous OkHttp
     * fetch, so without this the scrape blocks the UI thread.
     */
    suspend fun resolve(event: RemoteEvent): RemoteEvent =
        withContext(Dispatchers.IO) { resolveInternal(event) }

    private suspend fun resolveInternal(event: RemoteEvent): RemoteEvent {
        // 1. The organiser's own cover wins outright.
        if (!event.imageUrl.isNullOrBlank()) return event

        // 2. The event page's og:image.
        val sourceUrl = event.sourceUrl?.trim().orEmpty()
        var resolved: String? = null
        if (sourceUrl.isNotBlank()) {
            resolved = try {
                val html = client.newCall(
                    Request.Builder().url(sourceUrl).get().build()
                ).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    response.body?.string()
                }
                html?.let { extractImage(it, sourceUrl) }
            } catch (_: Exception) {
                null
            }
        }

        // 3. Wikimedia Commons — authentic South African imagery.
        if (resolved == null) {
            resolved = runCatching {
                commons?.findImage(event.title, commonsPlace(event))?.url
            }.getOrNull()
        }

        // 4-5. Unsplash, then Pixabay stock photography — always constrained to
        // the event's subject so a "cars" event never gets a hotel bedroom.
        if (resolved == null) {
            resolved = runCatching {
                stock?.findImage(stockQuery(event), stockTopics(event.category))?.url
            }.getOrNull()
        }

        return if (resolved == null) event else event.copy(imageUrl = resolved)
    }

    /** Venue/address mash-up Commons can search with ("Green Point, Cape Town"). */
    private fun commonsPlace(event: RemoteEvent): String {
        val venue = event.venueName.trim()
        val address = event.address.trim()
        return listOf(address, venue).filter { it.isNotBlank() }
            .joinToString(", ")
            .ifBlank { "South Africa" }
    }

    /** "<category> event <city>" — readable by both stock APIs. */
    internal fun stockQuery(event: RemoteEvent): String {
        // Feeds sometimes pack several interests into one category field
        // ("cars,karting,motorcycles"); stock engines read the leading subject
        // best, so keep only the first token.
        val category = event.category.split(',')
            .map { it.trim().replace('_', ' ').replace('-', ' ') }
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
        val city = stockCity(event)
        return listOf(category, "event", city)
            .filter { it.isNotBlank() }
            .joinToString(" ")
    }

    /**
     * The town the query should be scoped to. Feed addresses often carry only
     * a venue ("Thaba Echo Hotel, South Africa") or venue club names ("Rover
     * Motorcycle Club"); stock engines read those as subjects and return
     * hotel bedrooms or Mars rovers, so such segments are dropped along with
     * province codes, "TBA" placeholders and the country itself.
     */
    private fun stockCity(event: RemoteEvent): String {
        return event.address.split(',')
            .map { it.trim().trimEnd('\\', '\'').trim('.', '-', '…', ' ').trim() }
            .filter {
                val lower = it.lowercase()
                val upper = it.uppercase()
                it.isNotBlank() &&
                    !it.equals("South Africa", ignoreCase = true) &&
                    upper !in PROVINCE_CODES &&
                    !lower.startsWith("tba") &&
                    !lower.startsWith("tbc") &&
                    lower.split(' ').none { word -> word in VENUE_WORDS }
            }
            .lastOrNull()
            ?: "South Africa"
    }

    /**
     * Subject keywords the stock tiers must see in a hit's description/tags
     * before it may become the event's cover. Categories with no recognised
     * subject yield an empty set — relevance then cannot be judged, so the
     * first usable hit stands.
     */
    internal fun stockTopics(category: String): Set<String> {
        val c = category.lowercase()
        return when {
            listOf("motorcycl", "biker").any { c.contains(it) } -> setOf(
                "motorcycle", "motorbike", "motocross", "moto", "rider", "helmet", "enduro", "bike"
            )
            listOf("kart", "rally", "race", "racing", "enduro", "motorsport", "drift", "car", "offroad", "off-road").any { c.contains(it) } -> setOf(
                "car", "race", "racing", "rally", "circuit", "track", "kart", "motorsport", "vehicle", "auto", "drift"
            )
            listOf("music", "concert", "jazz", "band", " dj", "gig", "choir", "orchestra").any { c.contains(it) } -> setOf(
                "music", "concert", "band", "stage", "musician", "singer", "crowd", "performance", "guitar", "microphone", "dj", "choir"
            )
            listOf("food", "wine", "braai", "culinary", "beer", "dining", "taste", "restaurant").any { c.contains(it) } -> setOf(
                "food", "wine", "dining", "restaurant", "culinary", "cuisine", "dish", "meal", "cooking", "beer", "kitchen", "paella"
            )
            listOf("marathon", "rugby", "football", "cricket", "sport", "athletic", "cycling", "cycle").any { c.contains(it) } -> setOf(
                "sport", "runner", "runners", "marathon", "football", "rugby", "cricket", "athlete", "cycling", "cyclist", "match", "stadium"
            )
            listOf("conference", "business", "network", "expo", "tech", "hackathon", "seminar").any { c.contains(it) } -> setOf(
                "conference", "business", "speaker", "audience", "presentation", "office", "meeting", "expo", "technology"
            )
            listOf("theatre", "theater", "comedy", "stand-up", "opera", "ballet", "play ").any { c.contains(it) } -> setOf(
                "theatre", "theater", "stage", "performance", "actor", "curtain", "comedy", "opera", "ballet"
            )
            listOf("market", "craft", "flea", "stall").any { c.contains(it) } -> setOf(
                "market", "stall", "craft", "vendor", "bazaar", "marketplace"
            )
            listOf("art", "gallery", "exhibition", "museum", "painting").any { c.contains(it) } -> setOf(
                "art", "gallery", "exhibition", "painting", "artist", "museum", "canvas", "sculpture"
            )
            listOf("festival").any { c.contains(it) } -> setOf(
                "festival", "crowd", "stage", "celebration", "outdoor", "concert", "food"
            )
            else -> emptySet()
        }
    }

    private companion object {
        /** Province names/codes feeds append to addresses; never useful as a city. */
        private val PROVINCE_CODES = setOf(
            "GP", "GAUTENG", "WC", "WESTERN CAPE", "KZN", "KWAZULU-NATAL",
            "EC", "EASTERN CAPE", "FS", "FREE STATE", "MP", "MPUMALANGA",
            "NW", "NORTH WEST", "LP", "LIMPOPO", "NC", "NORTHERN CAPE"
        )

        /** Venue words: such segments are buildings/clubs, not towns. */
        private val VENUE_WORDS = setOf(
            "hotel", "lodge", "resort", "guest", "club", "centre", "center",
            "venue", "casino", "hall", "church", "school", "university", "stadium"
        )
    }

    internal fun extractImage(html: String, baseUrl: String? = null): String? {
        val patterns = listOf(
            Regex("<meta[^>]+property=\"og:image(?::secure_url)?\"[^>]*content=\"([^\"]+)\"", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)),
            Regex("<meta[^>]+content=\"([^\"]+)\"[^>]*property=\"og:image(?::secure_url)?\"", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)),
            Regex("<meta[^>]+name=\"twitter:image(?::src)?\"[^>]*content=\"([^\"]+)\"", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)),
            Regex("data-src=\"(https?://[^\"]+RETINA_PORTRAIT_16_9\\.(?:jpg|jpeg|png|webp))\"", RegexOption.IGNORE_CASE),
            Regex("data-src=\"(https?://[^\"]+\\.(?:jpg|jpeg|png|webp))\"", RegexOption.IGNORE_CASE)
        )

        for (pattern in patterns) {
            val match = pattern.find(html) ?: continue
            val raw = match.groupValues.getOrNull(1)?.trim().orEmpty()
            if (raw.isBlank()) continue
            val absolute = makeAbsolute(raw, baseUrl ?: return null)
            if (absolute != null) return absolute
        }
        return null
    }

    private fun makeAbsolute(raw: String, baseUrl: String): String? {
        if (raw.startsWith("http://", ignoreCase = true) || raw.startsWith("https://", ignoreCase = true)) {
            return raw
        }
        if (raw.startsWith("//")) {
            val scheme = Uri.parse(baseUrl).scheme ?: "https"
            return "$scheme:$raw"
        }
        return try {
            val resolved = java.net.URI(baseUrl).resolve(raw).toString()
            resolved
        } catch (e: Exception) {
            null
        }
    }
}
