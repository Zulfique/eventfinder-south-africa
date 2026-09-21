package com.eventfinder.app.data.sources

import com.eventfinder.app.data.remote.model.RemoteEvent
import com.eventfinder.app.utils.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Fetches events from an RSS/Atom feed.
 *
 * Parses common RSS event structures including:
 * - Standard RSS 2.0 with <item> elements
 * - Atom feeds with <entry> elements
 * - iTunes podcast namespace for date/time
 *
 * Events without coordinates will be geocoded by the ingestion pipeline.
 */
class RssEventSource(
    override val id: String,
    override val displayName: String,
    private val url: String,
    private val httpClient: OkHttpClient
) : EventSource {

    private val tag = "RssEventSource"

    private val dateFormats = listOf(
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") },
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") },
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") },
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US),
        SimpleDateFormat("dd MMM yyyy HH:mm:ss Z", Locale.US)
    )

    override suspend fun fetchEvents(): List<RemoteEvent> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .get()
                .header("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml")
                .header("User-Agent", "EventFinder/1.0 (https://github.com/Zulfique/eventfinder-south-africa)")
                .build()

            val body = httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("RSS source returned HTTP ${response.code}")
                }
                response.body?.string() ?: throw IllegalStateException("RSS source returned empty body")
            }

            val events = parseRss(body)
            AppLogger.i(tag, "Parsed ${events.size} events from RSS feed: $displayName")
            events
        } catch (e: Exception) {
            AppLogger.e(tag, "Failed to fetch RSS feed: $displayName", e)
            throw e
        }
    }

    private fun parseRss(xml: String): List<RemoteEvent> {
        val events = mutableListOf<RemoteEvent>()
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xml))

        var insideItem = false
        var title = ""
        var description = ""
        var link = ""
        var pubDate = ""
        var guid = ""
        var category = ""
        var location = ""
        var imageUrl = ""
        var inTitle = false
        var inDescription = false
        var inLink = false
        var inPubDate = false
        var inGuid = false
        var inCategory = false

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    val name = parser.name
                    when {
                        name == "item" || name == "entry" -> {
                            insideItem = true
                            title = ""
                            description = ""
                            link = ""
                            pubDate = ""
                            guid = ""
                            category = ""
                            location = ""
                            imageUrl = ""
                        }
                        insideItem -> when (name) {
                            "title" -> inTitle = true
                            "description", "summary", "content" -> {
                                if (!inDescription) inDescription = true
                            }
                            "link" -> inLink = true
                            "pubDate", "published", "updated" -> inPubDate = true
                            "guid", "id" -> inGuid = true
                            "category" -> inCategory = true
                            "enclosure" -> {
                                val type = parser.getAttributeValue(null, "type") ?: ""
                                val href = parser.getAttributeValue(null, "url")
                                    ?: parser.getAttributeValue(null, "href")
                                if (href != null && (type.startsWith("image/") || href.matches(Regex(".*\\.(jpg|jpeg|png|webp).*", RegexOption.IGNORE_CASE)))) {
                                    imageUrl = href
                                }
                            }
                            "media:content", "media:thumbnail" -> {
                                val href = parser.getAttributeValue(null, "url")
                                    ?: parser.getAttributeValue(null, "href")
                                if (href != null) imageUrl = href
                            }
                            "geo:lat" -> { location = parser.nextText().trim() }
                            "geo:long" -> { /* handled separately */ }
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inTitle) title += parser.text?.trim() ?: ""
                    if (inDescription) description += parser.text?.trim() ?: ""
                    if (inLink) link += parser.text?.trim() ?: ""
                    if (inPubDate) pubDate += parser.text?.trim() ?: ""
                    if (inGuid) guid += parser.text?.trim() ?: ""
                    if (inCategory) category += parser.text?.trim() ?: ""
                }
                XmlPullParser.END_TAG -> {
                    val name = parser.name
                    when (name) {
                        "item", "entry" -> {
                            insideItem = false
                            val eventId = guid.ifBlank { link }.ifBlank { title }
                            val startDate = parseRssDate(pubDate)

                            if (title.isNotBlank() && startDate != null && startDate > System.currentTimeMillis()) {
                                events.add(
                                    RemoteEvent(
                                        source = id,
                                        sourceId = eventId.hashCode().toString(),
                                        title = title.trim(),
                                        description = description.trim().take(2000),
                                        category = category.ifBlank { "OTHER" },
                                        startDate = startDate,
                                        endDate = startDate + 3 * 60 * 60 * 1000L,
                                        venueName = location.ifBlank { title.trim() },
                                        address = location,
                                        latitude = null,
                                        longitude = null,
                                        imageUrl = imageUrl.ifBlank { null },
                                        sourceUrl = link.ifBlank { null },
                                        organizerName = null
                                    )
                                )
                            }
                        }
                        "title" -> inTitle = false
                        "description", "summary", "content" -> {
                            inDescription = false
                        }
                        "link" -> inLink = false
                        "pubDate", "published", "updated" -> inPubDate = false
                        "guid", "id" -> inGuid = false
                        "category" -> inCategory = false
                    }
                }
            }
            eventType = parser.next()
        }

        return events
    }

    private fun parseRssDate(raw: String): Long? {
        val cleaned = raw.trim()
        if (cleaned.isBlank()) return null

        for (fmt in dateFormats) {
            runCatching { return fmt.parse(cleaned)?.time }.getOrNull()
        }

        runCatching {
            return java.time.Instant.parse(cleaned).toEpochMilli()
        }.getOrNull()

        runCatching {
            return java.time.OffsetDateTime.parse(cleaned).toInstant().toEpochMilli()
        }.getOrNull()

        return null
    }
}
