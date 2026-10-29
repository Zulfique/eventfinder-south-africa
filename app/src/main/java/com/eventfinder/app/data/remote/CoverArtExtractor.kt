package com.eventfinder.app.data.remote

import com.eventfinder.app.utils.AppLogger
import java.io.ByteArrayOutputStream
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Finds a cover image for an event by scraping the event's own page.
 *
 * The two public feeds publish a link to each event but almost no artwork:
 * Motorsport SA's ICS has no image field at all and Aticket only carries one
 * for some entries, so without this the whole catalogue renders with the same
 * stock placeholder photos. Most event sites advertise a share image, which is
 * free to read - no API key, paid service or image search involved.
 *
 * The lookup is tried in decreasing order of reliability:
 *
 *  1. `og:image` (the share/preview image nearly every event site sets),
 *  2. `twitter:image`,
 *  3. `<link rel="image_src">`,
 *  4. JSON-LD `image`,
 *  5. the first `<img>` that looks like a real photo rather than a logo.
 *
 * Relative URLs are resolved against the page, only http(s) results are kept,
 * and results - including misses - are memoised so one sync never fetches the
 * same page twice. [httpClient] null disables the feature entirely, which is
 * what unit tests rely on.
 */
class CoverArtExtractor(private val httpClient: OkHttpClient?) {

    /** page URL -> image URL for the pages that had one. */
    private val cache = ConcurrentHashMap<String, String>()

    /**
     * Pages already checked that had no usable image. Misses are tracked
     * separately because [ConcurrentHashMap] rejects null values - caching a
     * miss as null used to throw, and because the call sits inside the feed
     * sync the exception failed the whole source.
     */
    private val misses = ConcurrentHashMap.newKeySet<String>()

    /**
     * Returns a public image URL for [pageUrl], or null when the page has no
     * usable cover image (or the feature is disabled).
     */
    suspend fun extract(pageUrl: String?): String? {
        val url = pageUrl?.trim().orEmpty()
        if (httpClient == null || !url.startsWith("http")) return null

        cache[url]?.let { return it }
        if (misses.contains(url)) return null

        return try {
            val found = withContext(Dispatchers.IO) { fetchAndParse(url) }

            if (found != null) {
                if (cache.size >= MAX_CACHED_PAGES) {
                    cache.clear()
                    misses.clear()
                }
                cache[url] = found
            } else {
                misses.add(url)
            }

            AppLogger.d(
                TAG,
                if (found != null) "Cover art for $url -> $found" else "No cover art on $url"
            )
            found
        } catch (t: Exception) {
            // Cover art is decoration: a lookup failure must never abort the
            // event sync that is running around it.
            AppLogger.w(TAG, "Cover-art lookup failed for $url: ${t.message}")
            null
        }
    }

    private fun fetchAndParse(pageUrl: String): String? = try {
        val request = Request.Builder()
            .url(pageUrl)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8")
            .build()

        httpClient!!.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                AppLogger.d(TAG, "Cover-art fetch ${response.code} for $pageUrl")
                return null
            }
            val contentType = response.header("Content-Type").orEmpty()
            if (contentType.isNotEmpty() && !contentType.contains("html", ignoreCase = true)) {
                return null
            }
            val html = readCapped(response.body?.byteStream() ?: return null)
            findCoverImageUrl(html, pageUrl)
        }
    } catch (e: Exception) {
        AppLogger.d(TAG, "Cover-art lookup failed for $pageUrl: ${e.message}")
        null
    }

    /** Reads at most [MAX_HTML_BYTES] so a huge page cannot stall the sync. */
    private fun readCapped(stream: java.io.InputStream): String = stream.use { input ->
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8_192)
        var total = 0
        while (total < MAX_HTML_BYTES) {
            val read = input.read(buffer, 0, minOf(buffer.size, MAX_HTML_BYTES - total))
            if (read <= 0) break
            out.write(buffer, 0, read)
            total += read
        }
        String(out.toByteArray(), Charsets.UTF_8)
    }

    companion object {
        private const val TAG = "CoverArtExtractor"
        private const val MAX_HTML_BYTES = 512 * 1024
        private const val MAX_CACHED_PAGES = 500
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) EventFinder/1.0 (+https://eventfinder.app)"

        private val META_TAG = Regex("(?is)<meta\\s[^>]*>")
        private val LINK_TAG = Regex("(?is)<link\\s[^>]*>")
        private val IMG_TAG = Regex("(?is)<img\\s[^>]*>")
        private val JSON_LD_IMAGE = Regex(
            "(?is)\"image\"\\s*:\\s*(?:\"([^\"\\\\]+)\"|\\{[^{}]*?\"url\"\\s*:\\s*\"([^\"\\\\]+)\")"
        )

        /**
         * Asset types and names that are never a cover image. The
         * `tribe-loading.gif` / `...-placeholder.png` entries matter: The Events
         * Calendar emits them as the first `<img>` on every event page, and
         * picking one turned every card into an animated spinner.
         */
        private val REJECTED_SUFFIXES = listOf(".svg", ".ico", ".gif")
        private val REJECTED_WORDS = listOf(
            "logo", "icon", "sprite", "avatar", "favicon", "placeholder",
            "tracking", "pixel", "blank.", "transparent",
            "loading", "loader", "spinner", "ajax", "tribe-"
        )

        /**
         * Pure HTML scan - kept separate from the network so it can be unit
         * tested against the markup real event sites emit.
         *
         * @param html first chunk of the page.
         * @param baseUrl page URL used to resolve relative image paths.
         */
        internal fun findCoverImageUrl(html: String, baseUrl: String): String? {
            if (html.isBlank()) return null

            metaContent(html, "og:image", "og:image:url", "og:image:secure_url")
                ?.let { if (usable(it)) return resolve(baseUrl, it) }

            metaContent(html, "twitter:image", "twitter:image:src")
                ?.let { if (usable(it)) return resolve(baseUrl, it) }

            linkHref(html, "image_src")?.let { if (usable(it)) return resolve(baseUrl, it) }

            JSON_LD_IMAGE.find(html)?.let { match ->
                val raw = match.groupValues[1].ifEmpty { match.groupValues[2] }
                if (raw.isNotBlank() && usable(raw)) resolve(baseUrl, raw)?.let { return it }
            }

            for (match in IMG_TAG.findAll(html)) {
                val src = attribute(match.value, "data-src", "data-original", "src") ?: continue
                if (!looksLikePhoto(src)) continue
                resolve(baseUrl, src)?.let { return it }
            }

            return null
        }

        /** First `<meta>` whose property/name matches one of [keys]. */
        private fun metaContent(html: String, vararg keys: String): String? {
            for (match in META_TAG.findAll(html)) {
                val tag = match.value
                val key = attribute(tag, "property") ?: attribute(tag, "name") ?: continue
                if (keys.any { it.equals(key, ignoreCase = true) }) {
                    attribute(tag, "content")?.let { return it }
                }
            }
            return null
        }

        private fun linkHref(html: String, rel: String): String? {
            for (match in LINK_TAG.findAll(html)) {
                val tag = match.value
                val tagRel = attribute(tag, "rel") ?: continue
                if (tagRel.split(Regex("\\s+")).any { it.equals(rel, ignoreCase = true) }) {
                    attribute(tag, "href")?.let { return it }
                }
            }
            return null
        }

        private fun attribute(tag: String, name: String, vararg more: String): String? =
            (listOf(name) + more).firstNotNullOfOrNull { key ->
                Regex("(?i)\\b" + Regex.escape(key) + "\\s*=\\s*[\"']([^\"']+)[\"']")
                    .find(tag)?.groupValues?.get(1)?.trim()
            }

        /** Rejects tracking pixels, icons and non-image URLs. */
        private fun usable(raw: String): Boolean {
            val value = raw.trim()
            if (value.isEmpty() || value.startsWith("data:")) return false
            if (!(value.startsWith("http") || value.startsWith("/") ||
                    value.startsWith("./") || value.startsWith("../"))
            ) {
                return false
            }
            val lower = value.lowercase()
            if (REJECTED_SUFFIXES.any { lower.contains(it) }) return false
            if (lower.contains("tracking-pixel") || lower.contains("analytics")) return false
            return true
        }

        /** True when a candidate `<img>` looks like content, not chrome. */
        internal fun looksLikePhoto(url: String): Boolean {
            if (!usable(url)) return false
            val lower = url.lowercase()
            return REJECTED_WORDS.none { lower.contains(it) }
        }

        private fun resolve(baseUrl: String, raw: String): String? {
            val value = raw.trim()
            if (value.isEmpty() || value.startsWith("data:")) return null
            return try {
                val absolute = if (value.startsWith("http")) value else URI(baseUrl).resolve(value).toString()
                if (absolute.startsWith("http")) absolute else null
            } catch (e: Exception) {
                null
            }
        }
    }
}
