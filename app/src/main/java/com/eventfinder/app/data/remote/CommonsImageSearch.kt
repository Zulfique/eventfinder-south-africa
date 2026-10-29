package com.eventfinder.app.data.remote

import com.eventfinder.app.utils.AppLogger
import com.google.gson.JsonParser
import java.util.concurrent.ConcurrentHashMap
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** A Wikimedia Commons image together with its licence credit. */
data class CommonsImage(val url: String, val credit: String)

/**
 * Finds real, freely licensed artwork for events whose own site publishes none.
 *
 * The Motorsport SA feed is the motivating case: its ICS carries no image, its
 * event pages expose no `og:image`, its WordPress records have
 * `featured_media: 0` and its body copy is empty - there is literally nothing
 * to scrape. Rather than show an empty card, this searches **Wikimedia
 * Commons**, which is keyless, free, and only serves images whose licence
 * permits reuse (public domain, CC0, CC BY and CC BY-SA).
 *
 * Queries are built from the sport in the title plus the town the event is in
 * ("rally Dullstroom", then just "Dullstroom"), because Commons is a photo
 * archive of places and subjects, not of event listings. Only bitmap files of
 * a usable size are accepted, and map/flag/coat-of-arms style files are
 * skipped since those are what town searches usually surface first.
 *
 * Results - including misses - are memoised so a sync issues at most a couple
 * of requests per unique query. A null [httpClient] disables the feature.
 */
class CommonsImageSearch(private val httpClient: OkHttpClient?) {

    private val cache = ConcurrentHashMap<String, CommonsImage>()
    private val misses = ConcurrentHashMap.newKeySet<String>()

    /** Returns a Commons image for [title] at [place], or null. */
    suspend fun findImage(title: String, place: String): CommonsImage? {
        if (httpClient == null) return null

        for (query in imageQueries(title, place)) {
            cache[query]?.let { return it }
            if (misses.contains(query)) continue

            val found = try {
                withContext(Dispatchers.IO) { search(query) }
            } catch (t: Exception) {
                // Never let artwork lookup break the event sync around it.
                AppLogger.w(TAG, "Commons search failed for '$query': ${t.message}")
                misses.add(query)
                continue
            }

            if (found != null) {
                cache[query] = found
                AppLogger.i(
                    TAG,
                    "Commons image for '$title' via '$query' -> ${found.credit}"
                )
                return found
            }
            misses.add(query)
        }
        return null
    }

    /**
     * Ordered queries for an event: sport + town first (most specific), then
     * the town on its own, and finally the sport scoped to South Africa. The
     * country tier covers placeholder venues ("TBA, KZN") and tracks Commons
     * does not know by the name the feed uses. Blank places yield only the
     * country tier; titles with no recognisable sport yield nothing.
     */
    internal fun imageQueries(title: String, place: String): List<String> {
        val town = cleanPlace(place)
        val sport = sportKeyword(title)
        val queries = mutableListOf<String>()
        if (town.isNotEmpty()) {
            if (sport != null) queries += "$sport $town"
            queries += town
        }
        if (sport != null) queries += "$sport South Africa"
        return queries.distinct().take(MAX_QUERIES)
    }

    /** Maps a motorsport event title to the sport Commons is likely to have. */
    internal fun sportKeyword(title: String): String? {
        val upper = title.uppercase()
        return when {
            upper.contains("RALLY") -> "rally"
            upper.contains("MOTOCROSS") || Regex("\\bMX\\b").containsMatchIn(upper) -> "motocross"
            upper.contains("KARTING") -> "karting"
            upper.contains("ENDURO") -> "enduro"
            upper.contains("ENDURANCE") || upper.contains(" HOUR") ||
                Regex("""\b\d+HR\b""").containsMatchIn(upper) -> "motorsport"
            upper.contains("OFF-ROAD") || upper.contains("OFFROAD") -> "off-road racing"
            upper.contains("CIRCUIT") || upper.contains("CHAMPIONSHIP") ||
                upper.contains("RACEWAY") || upper.contains("HISTORIC") -> "motorsport"
            else -> null
        }
    }

    /** Drops the venue noise and province codes that feeds append to locations. */
    private fun cleanPlace(place: String): String {
        val cleaned = place.replace("\\,", ",")
            .replace("\\", "")
            .split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .lastOrNull { seg ->
                val key = seg.lowercase()
                key !in PROVINCE_CODES && key !in PLACEHOLDERS && key.length >= 3
            } ?: return ""

        return if (cleaned.all { it.isDigit() || it == ' ' }) "" else cleaned
    }

    /**
     * Builds a Commons search URL. Only the parameter *values* are encoded -
     * percent-encoding the whole query string turns the `&`/`=` separators into
     * literals, MediaWiki then serves an HTML help page instead of JSON, and
     * every lookup fails.
     */
    internal fun buildSearchUrl(query: String): String = ENDPOINT.toHttpUrl()
        .newBuilder()
        .addQueryParameter("action", "query")
        .addQueryParameter("format", "json")
        .addQueryParameter("generator", "search")
        .addQueryParameter("gsrsearch", "$query filetype:bitmap")
        .addQueryParameter("gsrnamespace", "6")
        .addQueryParameter("gsrlimit", "8")
        .addQueryParameter("prop", "imageinfo")
        .addQueryParameter("iiprop", "url|mime|size|extmetadata")
        .addQueryParameter("iiurlwidth", "900")
        .build()
        .toString()

    /** Runs one Commons search and picks the first usable, reusable image. */
    private fun search(query: String): CommonsImage? {
        val url = buildSearchUrl(query)

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .build()

        httpClient!!.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                AppLogger.d(TAG, "Commons HTTP ${response.code} for '$query'")
                return null
            }
            val body = response.body?.string() ?: return null
            return parseResults(body)
        }
    }

    /** Picks the first acceptable image out of a MediaWiki search response. */
    internal fun parseResults(json: String): CommonsImage? {
        return try {
        val root = JsonParser.parseString(json).asJsonObject
        val pages = root.getAsJsonObject("query")?.getAsJsonObject("pages") ?: return null

        // `index` is the search engine's relevance rank, so honour it instead
        // of relying on JSON object iteration order.
        pages.entrySet()
            .map { (_, value) -> value.asJsonObject }
            .sortedBy { page -> page.get("index")?.asInt ?: Int.MAX_VALUE }
            .firstNotNullOfOrNull { page ->
                val title = page.get("title")?.asString.orEmpty()
                if (REJECTED_TITLE_WORDS.any { title.contains(it, ignoreCase = true) }) {
                    return@firstNotNullOfOrNull null
                }

                val info = page.getAsJsonArray("imageinfo")?.firstOrNull()?.asJsonObject
                    ?: return@firstNotNullOfOrNull null

                val mime = info.get("mime")?.asString.orEmpty()
                if (mime != "image/jpeg" && mime != "image/png") return@firstNotNullOfOrNull null

                val width = info.get("width")?.asInt ?: 0
                if (width < MIN_WIDTH) return@firstNotNullOfOrNull null

                val meta = info.getAsJsonObject("extmetadata")
                val licence = meta?.getAsJsonObject("LicenseShortName")
                    ?.get("value")?.asString.orEmpty()
                if (ALLOWED_LICENCES.none { licence.startsWith(it, ignoreCase = true) }) {
                    return@firstNotNullOfOrNull null
                }

                val thumb = info.get("thumburl")?.asString
                    ?: info.get("url")?.asString
                    ?: return@firstNotNullOfOrNull null

                CommonsImage(
                    url = thumb,
                    credit = "${stripHtml(meta?.getAsJsonObject("Artist")?.get("value")?.asString)} ($licence)"
                )
            }
    } catch (t: Exception) {
        AppLogger.d(TAG, "Could not parse Commons response: ${t.message}")
        null
    }
    }

    private fun stripHtml(raw: String?): String {
        val text = raw?.replace(Regex("<[^>]*>"), "")?.trim().orEmpty()
        return if (text.isBlank()) "Wikimedia Commons" else text.take(60)
    }

    companion object {
        private const val TAG = "CommonsImageSearch"
        private const val ENDPOINT = "https://commons.wikimedia.org/w/api.php"
        private const val MAX_QUERIES = 3
        private const val MIN_WIDTH = 600
        private const val USER_AGENT =
            "EventFinder/1.0 (https://eventfinder.app; open-source event discovery app)"

        /** Licences that permit reuse in this app. */
        private val ALLOWED_LICENCES = listOf(
            "Public domain", "PD-", "CC0", "CC BY", "CC BY-SA", "CC BY 4.0", "CC BY-SA 4.0"
        )

        /** Town searches mostly return these; they make poor cover art. */
        private val REJECTED_TITLE_WORDS = listOf(
            "map", "flag", "coat of arms", "logo", "locator", "seal of",
            ".svg", ".pdf", ".djvu", ".tif"
        )

        private val PROVINCE_CODES = setOf(
            "wc", "ec", "nc", "fs", "gp", "nw", "kzn", "ls", "mp",
            "western cape", "eastern cape", "northern cape", "free state",
            "north west", "gauteng", "limpopo", "mpumalanga",
            "kwazulu-natal", "kwazulu natal"
        )

        private val PLACEHOLDERS = setOf("tba", "tbc", "za", "south africa", "venue")
    }
}
