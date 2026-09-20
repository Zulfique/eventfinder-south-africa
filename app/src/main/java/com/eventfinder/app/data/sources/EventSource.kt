package com.eventfinder.app.data.sources

import com.eventfinder.app.data.remote.model.RemoteEvent

interface EventSource {
    val id: String
    val displayName: String

    suspend fun fetchEvents(): List<RemoteEvent>
}
