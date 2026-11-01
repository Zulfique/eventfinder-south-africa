package com.eventfinder.app.data.remote

import android.net.Uri
import com.eventfinder.app.data.remote.model.RemoteEvent
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

    suspend fun resolve(event: RemoteEvent): RemoteEvent {
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

        // 4-5. Unsplash, then Pixabay stock photography.
        if (resolved == null) {
            resolved = runCatching {
                stock?.findImage(stockQuery(event))?.url
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

    /** "<category> event <city> south africa" — readable by both stock APIs. */
    private fun stockQuery(event: RemoteEvent): String {
        val city = event.address.split(',')
            .map { it.trim() }
            .lastOrNull { it.isNotBlank() && !it.equals("South Africa", ignoreCase = true) }
            ?: "South Africa"
        val category = event.category.trim()
            .replace('_', ' ')
            .replace('-', ' ')
        return listOf(category, "event", city)
            .filter { it.isNotBlank() }
            .joinToString(" ")
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
