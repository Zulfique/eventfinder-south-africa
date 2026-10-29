package com.eventfinder.app.data.remote

import android.net.Uri
import com.eventfinder.app.data.remote.model.RemoteEvent
import okhttp3.OkHttpClient
import okhttp3.Request

class EventImageResolver(private val client: OkHttpClient) {
    suspend fun resolve(event: RemoteEvent): RemoteEvent {
        if (!event.imageUrl.isNullOrBlank()) return event
        val sourceUrl = event.sourceUrl?.trim().orEmpty()
        if (sourceUrl.isBlank()) return event

        return try {
            val html = client.newCall(
                Request.Builder().url(sourceUrl).get().build()
            ).execute().use { response ->
                if (!response.isSuccessful) return@resolve event
                response.body?.string() ?: return@resolve event
            }

            val resolved = extractImage(html, sourceUrl)
            if (resolved == null) event else event.copy(imageUrl = resolved)
        } catch (_: Exception) {
            event
        }
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
