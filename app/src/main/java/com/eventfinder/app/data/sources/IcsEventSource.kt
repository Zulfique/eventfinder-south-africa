package com.eventfinder.app.data.sources

import com.eventfinder.app.data.remote.model.RemoteEvent
import com.eventfinder.app.utils.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Fetches events from an ICS/iCalendar feed.
 *
 * Parses standard RFC 5545 VEVENT entries. Supports:
 * - SUMMARY, DESCRIPTION, DTSTART, DTEND
 * - LOCATION (parsed as venue/address)
 * - CATEGORIES
 * - URL
 * - GEO (lat;lng)
 * - UID
 *
 * Events without coordinates will be geocoded by the ingestion pipeline.
 */
class IcsEventSource(
    override val id: String,
    override val displayName: String,
    private val url: String,
    private val httpClient: OkHttpClient
) : EventSource {

    private val tag = "IcsEventSource"

    private val icsDateFormats = listOf(
        SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") },
        SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.US),
        SimpleDateFormat("yyyyMMdd", Locale.US)
    )

    override suspend fun fetchEvents(): List<RemoteEvent> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .get()
                .header("Accept", "text/calendar, application/ics")
                .header("User-Agent", "EventFinder/1.0 (https://github.com/Zulfique/eventfinder-south-africa)")
                .build()

            val body = httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("ICS source returned HTTP ${response.code}")
                }
                response.body?.string() ?: throw IllegalStateException("ICS source returned empty body")
            }

            val events = parseIcs(body)
            AppLogger.i(tag, "Parsed ${events.size} events from ICS feed: $displayName")
            events
        } catch (e: Exception) {
            AppLogger.e(tag, "Failed to fetch ICS feed: $displayName", e)
            throw e
        }
    }

    private fun parseIcs(icsText: String): List<RemoteEvent> {
        val events = mutableListOf<RemoteEvent>()

        val unfolded = unfoldIcs(icsText)
        val blocks = splitVevents(unfolded)

        for (block in blocks) {
            val props = mutableMapOf<String, String>()
            val geoLine = StringBuilder()
            var inGeo = false

            for (line in block.lines()) {
                val trimmed = line.trim()
                when {
                    trimmed.startsWith("GEO;") || trimmed.startsWith("GEO:") -> {
                        geoLine.clear()
                        geoLine.append(trimmed)
                        inGeo = true
                    }
                    inGeo && (trimmed.startsWith(" ") || trimmed.startsWith("\t")) -> {
                        geoLine.append(trimmed.trimStart())
                    }
                    else -> {
                        inGeo = false
                        val colonIndex = trimmed.indexOf(':')
                        if (colonIndex > 0) {
                            val key = trimmed.substring(0, colonIndex).substringBefore(';')
                            val value = trimmed.substring(colonIndex + 1)
                            props[key] = props.getOrDefault(key, "") + value
                        }
                    }
                }
            }

            val uid = props["UID"]?.trim() ?: continue
            val summary = props["SUMMARY"]?.trim() ?: continue
            val description = props["DESCRIPTION"]?.trim()?.take(2000) ?: ""
            val location = props["LOCATION"]?.trim() ?: ""
            val categories = props["CATEGORIES"]?.trim() ?: ""
            val url = props["URL"]?.trim()

            val dtStart = props["DTSTART"]?.trim() ?: continue
            val dtEnd = props["DTEND"]?.trim()

            val startDate = parseIcsDate(dtStart)
            val endDate = dtEnd?.let { parseIcsDate(it) }

            if (startDate == null || startDate < System.currentTimeMillis()) continue

            val finalEnd = if (endDate != null && endDate > startDate) endDate else startDate + 3 * 60 * 60 * 1000L

            var latitude: Double? = null
            var longitude: Double? = null
            val geoValue = geoLine.toString().trim()
            if (geoValue.contains("GEO")) {
                val geoParts = geoValue.substringAfter(":").trim().split(";")
                if (geoParts.size >= 2) {
                    latitude = geoParts[0].trim().toDoubleOrNull()
                    longitude = geoParts[1].trim().toDoubleOrNull()
                }
            }

            val venueName = location.ifBlank { summary }
            val address = location

            events.add(
                RemoteEvent(
                    source = id,
                    sourceId = uid.hashCode().toString(),
                    title = summary,
                    description = description,
                    category = categories.ifBlank { "OTHER" },
                    startDate = startDate,
                    endDate = finalEnd,
                    venueName = venueName,
                    address = address,
                    latitude = latitude,
                    longitude = longitude,
                    imageUrl = null,
                    sourceUrl = url,
                    organizerName = null
                )
            )
        }

        return events
    }

    private fun unfoldIcs(text: String): String {
        val sb = StringBuilder()
        var prevWasContinuation = false
        for (line in text.lines()) {
            if (prevWasContinuation) {
                sb.append(line.removePrefix(" ").removePrefix("\t"))
            } else {
                if (sb.isNotEmpty()) sb.append("\n")
                sb.append(line)
            }
            prevWasContinuation = line.endsWith("\r") || line.endsWith(" ") || line.endsWith("\t")
        }
        return sb.toString()
    }

    private fun splitVevents(text: String): List<String> {
        val blocks = mutableListOf<String>()
        var inVevent = false
        val current = StringBuilder()

        for (line in text.lines()) {
            val trimmed = line.trim()
            when {
                trimmed == "BEGIN:VEVENT" -> {
                    inVevent = true
                    current.clear()
                }
                trimmed == "END:VEVENT" -> {
                    if (inVevent) {
                        blocks.add(current.toString())
                    }
                    inVevent = false
                    current.clear()
                }
                inVevent -> {
                    current.appendLine(line)
                }
            }
        }
        return blocks
    }

    private fun parseIcsDate(raw: String): Long? {
        val cleaned = raw.trim()
        if (cleaned.isBlank()) return null

        for (fmt in icsDateFormats) {
            runCatching { return fmt.parse(cleaned)?.time }.getOrNull()
        }

        return null
    }
}
