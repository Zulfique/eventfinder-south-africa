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
 * Verified sources (as of Oct 2026):
 * - Aticket RSS: SA concerts/theatre/events with explicit eventStartDate/eventEndDate/eventVenue tags
 * - Motorsport SA ICS: SA motorsport events with VEVENT data (dates, locations, organizers)
 *
 * The Ardent Africa JSON feed was removed from this list: the platform retired
 * its public API and `https://api.ardent.africa/public/v1/events` now answers
 * `404 {"code":404,"message":"Application not found"}` for every path, so the
 * source only produced a permanent failure on each discovery run. The generic
 * JSON adapter ([PublicJsonEventSource] / [PublicJsonEventClient]) is still
 * wired up and unit tested — re-enable the feed by adding a
 * `PublicJsonEventSource` with a live URL to the list below.
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
            )
        )
    }
}
