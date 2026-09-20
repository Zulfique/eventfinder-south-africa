package com.eventfinder.app.data.sources

import com.eventfinder.app.data.remote.dto.PublicJsonEventDto
import com.eventfinder.app.data.remote.model.RemoteEvent
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

object PublicJsonEventMapper {

    fun map(
        sourceId: String,
        dto: PublicJsonEventDto,
        sourceName: String
    ): RemoteEvent? {
        val id = dto.resolvedId()?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val title = dto.resolvedTitle()?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val start = parseDate(dto.resolvedStart()) ?: return null
        val parsedEnd = parseDate(dto.resolvedEnd())
        val end = parsedEnd?.takeIf { it > start } ?: (start + 60 * 60 * 1000L)

        val venueName = dto.venue?.resolvedName()?.takeIf { it.isNotBlank() }
            ?: dto.location?.resolvedName()?.takeIf { it.isNotBlank() }
            ?: "Unknown venue"

        val address = dto.venue?.resolvedAddress()?.takeIf { it.isNotBlank() }
            ?: dto.location?.resolvedAddress()?.takeIf { it.isNotBlank() }
            ?: ""

        val latitude = dto.venue?.resolvedLatitude() ?: dto.location?.resolvedLatitude()
        val longitude = dto.venue?.resolvedLongitude() ?: dto.location?.resolvedLongitude()

        return RemoteEvent(
            source = sourceId,
            sourceId = id,
            title = title,
            description = dto.resolvedDescription().trim(),
            category = dto.resolvedCategory().trim(),
            startDate = start,
            endDate = end,
            venueName = venueName,
            address = address,
            latitude = latitude,
            longitude = longitude,
            imageUrl = dto.resolvedImageUrl(),
            sourceUrl = dto.resolvedSourceUrl(),
            organizerName = dto.resolvedOrganizerName()
        )
    }

    private fun parseDate(raw: String?): Long? {
        val value = raw?.trim()?.takeIf { it.isNotBlank() } ?: return null

        runCatching { return Instant.parse(value).toEpochMilli() }
        runCatching { return OffsetDateTime.parse(value).toInstant().toEpochMilli() }
        runCatching { return LocalDateTime.parse(value).toInstant(ZoneOffset.UTC).toEpochMilli() }
        runCatching { return LocalDate.parse(value).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() }

        return null
    }
}
