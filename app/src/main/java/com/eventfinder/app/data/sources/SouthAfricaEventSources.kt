package com.eventfinder.app.data.sources

import com.eventfinder.app.data.remote.PublicJsonEventClient

object SouthAfricaEventSources {

    private const val ARDENT_AFRICA_BASE =
        "https://api.ardent.africa/public/v1/events?location=South+Africa&limit=50"

    fun create(client: PublicJsonEventClient): List<EventSource> {
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
