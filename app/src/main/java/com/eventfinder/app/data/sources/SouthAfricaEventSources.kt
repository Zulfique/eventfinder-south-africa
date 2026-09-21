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
 * The RSS and ICS sources only produce events when actual event-specific
 * date fields are present (not from article publication dates).
 */
object SouthAfricaEventSources {

    private const val ARDENT_AFRICA_BASE =
        "https://api.ardent.africa/public/v1/events?location=South+Africa&limit=50"

    fun create(
        client: PublicJsonEventClient,
        httpClient: OkHttpClient
    ): List<EventSource> {
        return listOf(
            PublicJsonEventSource(
                id = "ardent-africa",
                displayName = "Ardent Africa Events (South Africa)",
                url = ARDENT_AFRICA_BASE,
                client = client
            )
        )
    }
}
