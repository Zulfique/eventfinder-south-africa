package com.eventfinder.app.data.sources

import com.eventfinder.app.data.remote.PublicJsonEventClient
import okhttp3.OkHttpClient

/**
 * South African public event feed sources.
 *
 * All URLs are free, keyless, and publicly accessible. If a feed is
 * unavailable, the per-source error handling in EventDiscoveryRepository
 * ensures other sources continue to work and cached events are preserved.
 *
 * Verified sources (as of Sep 2026):
 * - Aticket RSS: SA concerts/theatre/events with explicit eventStartDate/eventEndDate/eventVenue tags
 * - Motorsport SA ICS: SA motorsport events with VEVENT data (dates, locations, organizers)
 * - Ardent Africa JSON: SA events API (may return empty results intermittently)
 */
object SouthAfricaEventSources {

    fun create(
        client: PublicJsonEventClient,
        httpClient: OkHttpClient
    ): List<EventSource> {
        return listOf(
            RssEventSource(
                id = "aticket-rss",
                displayName = "Aticket South Africa Events",
                url = "https://za.aticket.net/feed/featured-events",
                httpClient = httpClient
            ),
            IcsEventSource(
                id = "motorsport-sa-ics",
                displayName = "Motorsport South Africa Events",
                url = "https://www.motorsport.co.za/events/list/?ical=1",
                httpClient = httpClient
            ),
            PublicJsonEventSource(
                id = "ardent-africa",
                displayName = "Ardent Africa Events (South Africa)",
                url = "https://api.ardent.africa/public/v1/events?location=South+Africa&limit=50",
                client = client
            )
        )
    }
}
