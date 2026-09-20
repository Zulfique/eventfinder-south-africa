package com.eventfinder.app.data.remote

import com.eventfinder.app.data.remote.dto.PublicJsonEventDto
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class PublicJsonEventClient(
    private val httpClient: OkHttpClient,
    private val gson: Gson
) {
    suspend fun fetch(
        url: String,
        timeoutMillis: Long = 30_000L
    ): List<PublicJsonEventDto> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "application/json")
            .header(
                "User-Agent",
                "EventFinder/1.0 (https://github.com/Zulfique/eventfinder-south-africa)"
            )
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException(
                    "Public JSON source returned HTTP ${response.code}"
                )
            }
            val body = response.body?.string()
                ?: throw IllegalStateException("Public JSON source returned an empty response")
            parseEvents(body)
        }
    }

    private fun parseEvents(json: String): List<PublicJsonEventDto> {
        @Suppress("DEPRECATION")
        val root = JsonParser().parse(json)
        return when {
            root.isJsonArray -> parseArray(root.asJsonArray)
            root.isJsonObject -> parseObject(root.asJsonObject)
            else -> emptyList()
        }
    }

    private fun parseArray(array: JsonArray): List<PublicJsonEventDto> =
        array.mapNotNull { element ->
            if (!element.isJsonObject) null
            else runCatching { gson.fromJson(element, PublicJsonEventDto::class.java) }.getOrNull()
        }

    private fun parseObject(objectJson: JsonObject): List<PublicJsonEventDto> {
        val possibleKeys = listOf("events", "data", "results", "items")
        for (key in possibleKeys) {
            val value = objectJson.get(key)
            if (value != null && value.isJsonArray) {
                return parseArray(value.asJsonArray)
            }
        }
        val looksLikeEvent = objectJson.has("id") ||
            objectJson.has("eventId") ||
            objectJson.has("uuid") ||
            objectJson.has("title") ||
            objectJson.has("name")
        if (looksLikeEvent) {
            return listOf(gson.fromJson(objectJson, PublicJsonEventDto::class.java))
        }
        return emptyList()
    }
}
