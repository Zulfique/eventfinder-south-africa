package com.eventfinder.app.data.sources

import com.eventfinder.app.data.remote.PublicJsonEventClient
import com.eventfinder.app.data.remote.model.RemoteEvent

class PublicJsonEventSource(
    override val id: String,
    override val displayName: String,
    private val url: String,
    private val client: PublicJsonEventClient
) : EventSource {

    override suspend fun fetchEvents(): List<RemoteEvent> {
        val rawEvents = client.fetch(url)
        return rawEvents.mapNotNull { dto ->
            PublicJsonEventMapper.map(sourceId = id, dto = dto, sourceName = displayName)
        }
    }
}
