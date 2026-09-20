package com.eventfinder.app.data.remote

import com.eventfinder.app.data.remote.dto.TmEvent
import com.eventfinder.app.data.remote.dto.TmEventsResponse
import com.eventfinder.app.data.remote.dto.TmImage
import com.eventfinder.app.data.remote.dto.TmVenue
import com.eventfinder.app.domain.model.Event
import com.eventfinder.app.domain.model.EventCategory
import com.eventfinder.app.utils.AppLogger
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Maps Ticketmaster Discovery v2 responses onto the app's [Event] domain model.
 *
 * Pure conversion logic (no Android dependencies) so the mapping rules can be
 * verified with targeted unit tests — including malformed payloads, which are
 * skipped gracefully rather than crashing the sync (robustness requirement).
 */
class TicketmasterMapper(
    private val zoneId: ZoneId = ZoneId.systemDefault()
) {

    // Johannesburg coordinates used as a safe fallback for venues that do not
    // expose a location (the map still renders a useful marker).
    private val fallbackLat = -26.2041
    private val fallbackLng = 28.0473

    /** Converts a Ticketmaster payload page into domain events (skips unusable rows). */
    fun mapPage(response: TmEventsResponse): List<Event> {
        val rawEvents = response.embedded?.events.orEmpty()
        val converted = rawEvents.mapNotNull { mapEvent(it) }
        AppLogger.i("TicketmasterMapper", "Mapped ${converted.size}/${rawEvents.size} tickets into events")
        return converted
    }

    private fun mapEvent(dto: TmEvent): Event? {
        val id = dto.id ?: return null
        val title = dto.name ?: return null
        val start = parseStart(dto.dates?.start?.localDate, dto.dates?.start?.localTime) ?: return null
        val end = start + 3 * 60 * 60 * 1000L // default 3-hour duration when not provided

        val venue = dto.embedded?.venues?.firstOrNull()
        val (lat, lng) = resolveCoordinates(venue?.location?.latitude, venue?.location?.longitude)
        val category = dto.classifications
            ?.firstOrNull { it.segment?.name != null }
            ?.segment?.name
            ?.let { EventCategory.fromTicketmaster(it) }
            ?: EventCategory.OTHER

        val address = buildAddress(venue)
        val venueName = venue?.name ?: dto.url ?: "Venue TBC"

        return Event(
            id = "tm-$id",
            title = title,
            description = dto.description
                ?: "Join us for $title. Save this event to get reminders before it starts.",
            category = category,
            startDate = start,
            endDate = end,
            venueName = venueName,
            address = address,
            latitude = lat,
            longitude = lng,
            imageUrl = pickImageUrl(dto.images),
            isPublic = true,
            organizerId = "ticketmaster",
            organizerName = "Ticketmaster",
            attendeeCount = 0,
            isFavorite = false,
            isCreatedByUser = false,
            isExternal = true
        )
    }

    /** Combines and parses "2026-10-03" + "19:00:00" into epoch millis. Returns null on bad input. */
    internal fun parseStart(date: String?, time: String?): Long? {
        if (date.isNullOrBlank()) return null
        return try {
            val localDate = LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE)
            val localTime = if (time.isNullOrBlank()) LocalTime.NOON
            else LocalTime.parse(time, DateTimeFormatter.ISO_LOCAL_TIME)
            LocalDateTime.of(localDate, localTime).atZone(zoneId).toInstant().toEpochMilli()
        } catch (t: Exception) {
            AppLogger.w("TicketmasterMapper", "Cannot parse start date='$date' time='$time'")
            null
        }
    }

    private fun resolveCoordinates(latStr: String?, lngStr: String?): Pair<Double, Double> {
        val lat = latStr?.toDoubleOrNull()
        val lng = lngStr?.toDoubleOrNull()
        if (lat == null || lng == null || lat !in -90.0..90.0 || lng !in -180.0..180.0) {
            return fallbackLat to fallbackLng
        }
        return lat to lng
    }

    private fun buildAddress(venue: TmVenue?): String {
        if (venue == null) return "South Africa"
        val line = venue.address?.line1
        val city = venue.city?.name
        val country = venue.country?.name
        return listOfNotNull(line, city, country)
            .filter { it.isNotBlank() }
            .joinToString(", ")
            .ifBlank { "South Africa" }
    }

    /** Picks the widest 16:9 image (hero banners render best at 16:9). */
    internal fun pickImageUrl(images: List<TmImage>?): String? {
        return images
            ?.filter { it.url != null }
            ?.maxWithOrNull(compareBy<TmImage> { it.width }.thenBy { it.url!!.length })
            ?.url
    }
}
