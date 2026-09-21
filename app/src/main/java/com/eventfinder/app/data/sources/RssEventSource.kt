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
 * Only creates events when actual event-specific date fields are present:
 * - event:start / event:end (RSS events module)
 * - startDate / endDate (schema.org)
 * - itunes:start / itunes:end
 * - Published dates are NOT used as event start times.
 *
 * Supports:
 * - Standard RSS 2.0 with <item> elements
 * - Atom feeds with <entry> elements
 * - geo:lat / geo:long for coordinate extraction
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
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("Africa/Johannesburg") },
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("Africa/Johannesburg") },
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
        var imageUrl = ""
        var inTitle = false
        var inDescription = false
        var inLink = false
        var inPubDate = false
        var inGuid = false
        var inCategory = false
        var inEventStart = false
        var inEventEnd = false
        var inEventLocation = false

        var eventStartDate = ""
        var eventEndDate = ""
        var eventLocation = ""
        var eventLat: Double? = null
        var eventLng: Double? = null

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    val name = parser.name
                    val localName = parser.name
                    val ns = parser.namespace
                    when {
                        name == "item" || name == "entry" -> {
                            insideItem = true
                            title = ""; description = ""; link = ""; pubDate = ""
                            guid = ""; category = ""; imageUrl = ""
                            eventStartDate = ""; eventEndDate = ""; eventLocation = ""
                            eventLat = null; eventLng = null
                        }
                        insideItem -> when {
                            localName == "title" -> inTitle = true
                            localName == "description" || localName == "summary" || localName == "content" -> {
                                if (!inDescription) inDescription = true
                            }
                            localName == "link" -> inLink = true
                            localName == "pubDate" || localName == "published" || localName == "updated" -> inPubDate = true
                            localName == "guid" || localName == "id" -> inGuid = true
                            localName == "category" -> inCategory = true
                            localName == "enclosure" -> {
                                val type = parser.getAttributeValue(null, "type") ?: ""
                                val href = parser.getAttributeValue(null, "url")
                                    ?: parser.getAttributeValue(null, "href")
                                if (href != null && (type.startsWith("image/") || href.matches(Regex(".*\\.(jpg|jpeg|png|webp).*", RegexOption.IGNORE_CASE)))) {
                                    imageUrl = href
                                }
                            }
                            localName == "content" && ns?.contains("media") == true -> {
                                val href = parser.getAttributeValue(null, "url")
                                    ?: parser.getAttributeValue(null, "href")
                                if (href != null) imageUrl = href
                            }
                            localName == "thumbnail" && ns?.contains("media") == true -> {
                                val href = parser.getAttributeValue(null, "url")
                                    ?: parser.getAttributeValue(null, "href")
                                if (href != null) imageUrl = href
                            }
                            localName == "lat" && ns?.contains("geo") == true -> {
                                eventLat = parser.nextText().trim().toDoubleOrNull()
                            }
                            localName == "long" && ns?.contains("geo") == true -> {
                                eventLng = parser.nextText().trim().toDoubleOrNull()
                            }
                            localName == "start" && (ns?.contains("event") == true || ns?.contains("ev") == true) -> {
                                inEventStart = true
                            }
                            localName == "end" && (ns?.contains("event") == true || ns?.contains("ev") == true) -> {
                                inEventEnd = true
                            }
                            localName == "location" && (ns?.contains("event") == true || ns?.contains("ev") == true) -> {
                                inEventLocation = true
                            }
                            localName == "startDate" || localName == "eventStartDate" -> {
                                inEventStart = true
                            }
                            localName == "endDate" || localName == "eventEndDate" -> {
                                inEventEnd = true
                            }
                            localName == "eventVenue" -> {
                                inEventLocation = true
                            }
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
                    if (inEventStart) eventStartDate += parser.text?.trim() ?: ""
                    if (inEventEnd) eventEndDate += parser.text?.trim() ?: ""
                    if (inEventLocation) eventLocation += parser.text?.trim() ?: ""
                }
                XmlPullParser.END_TAG -> {
                    val name = parser.name
                    when (name) {
                        "item", "entry" -> {
                            insideItem = false
                            val eventId = guid.ifBlank { link }.ifBlank { title }

                            val hasEventDate = eventStartDate.isNotBlank()
                            val startDate = if (hasEventDate) {
                                parseRssDate(eventStartDate)
                            } else {
                                null
                            }

                            if (title.isNotBlank() && startDate != null && startDate > System.currentTimeMillis()) {
                                val endDate = if (eventEndDate.isNotBlank()) {
                                    parseRssDate(eventEndDate) ?: (startDate + 3 * 60 * 60 * 1000L)
                                } else {
                                    startDate + 3 * 60 * 60 * 1000L
                                }

                                val venueName = eventLocation.ifBlank { title.trim() }

                                events.add(
                                    RemoteEvent(
                                        source = id,
                                        sourceId = eventId,
                                        title = title.trim(),
                                        description = description.trim().take(2000),
                                        category = category.ifBlank { "OTHER" },
                                        startDate = startDate,
                                        endDate = endDate,
                                        venueName = venueName.ifBlank { title.trim() },
                                        address = eventLocation,
                                        latitude = eventLat,
                                        longitude = eventLng,
                                        imageUrl = imageUrl.ifBlank { null },
                                        sourceUrl = link.ifBlank { null },
                                        organizerName = null
                                    )
                                )
                            }
                        }
                        "title" -> inTitle = false
                        "description", "summary", "content" -> inDescription = false
                        "link" -> inLink = false
                        "pubDate", "published", "updated" -> inPubDate = false
                        "guid", "id" -> inGuid = false
                        "category" -> inCategory = false
                        "start", "startDate", "eventStartDate" -> inEventStart = false
                        "end", "endDate", "eventEndDate" -> inEventEnd = false
                        "location", "eventVenue" -> inEventLocation = false
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
