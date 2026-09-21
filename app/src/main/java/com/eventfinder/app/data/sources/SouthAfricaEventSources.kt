package com.eventfinder.app.data.sources

import com.eventfinder.app.data.remote.PublicJsonEventClient
import okhttp3.OkHttpClient

object SouthAfricaEventSources {

    private const val ARDENT_AFRICA_BASE =
        "https://api.ardent.africa/public/v1/events?location=South+Africa&limit=50"

    private const val SA_FESTIVALS_RSS =
        "https://www.gov.za/rss/sa-government-news-releases.xml"

    private const val SA_HERITAGE_ICS =
        "https://www.sahra.org.za/ics/events.ics"

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
            ),
            RssEventSource(
                id = "sa-government-news",
                displayName = "South Africa Government News",
                url = SA_FESTIVALS_RSS,
                httpClient = httpClient
            ),
            IcsEventSource(
                id = "sa-heritage",
                displayName = "South African Heritage Events",
                url = SA_HERITAGE_ICS,
                httpClient = httpClient
            )
        )
    }
}
