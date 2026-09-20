package com.eventfinder.app.data.sources

import com.eventfinder.app.data.remote.PublicJsonEventClient

object SouthAfricaEventSources {

    private const val ARDENT_AFRICA_BASE = "https://api.ardent.africa/public/v1/events"

    fun create(client: PublicJsonEventClient): List<EventSource> {
        return listOf(
            PublicJsonEventSource(
                id = "ardent-africa",
                displayName = "Ardent Africa Events",
                url = ARDENT_AFRICA_BASE,
                client = client
            )
        )
    }
}
