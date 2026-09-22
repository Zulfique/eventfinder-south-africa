package com.eventfinder.app.domain.model

import com.eventfinder.app.utils.DistanceCalculator

/**
 * Pure Kotlin search / filter / sort engine for the local event catalogue.
 *
 * Kept free of Android dependencies so sorting, radius filtering and category
 * filtering can be exhaustively tested on the JVM. The ViewModels delegate all
 * list manipulation here, which also keeps the UI layer thin.
 */
object EventFilterer {

    /**
     * Applies category + keyword + radius filtering (FR-02).
     *
     * @param events      source list
     * @param query       free-text keyword matched against title, description, venue & address/city
     * @param category    category chip filter (null = all)
     * @param userLat     current latitude (null disables radius filtering)
     * @param userLng     current longitude
     * @param radiusKm    maximum search radius in km (0 disables radius filtering)
     */
    fun filter(
        events: List<Event>,
        query: String? = null,
        category: EventCategory? = null,
        userLat: Double? = null,
        userLng: Double? = null,
        radiusKm: Int = 0
    ): List<Event> {
        val normalizedQuery = query?.trim()?.lowercase()
        return events.filter { event ->
            val matchesQuery = normalizedQuery.isNullOrEmpty() ||
                event.title.lowercase().contains(normalizedQuery) ||
                event.description.lowercase().contains(normalizedQuery) ||
                event.venueName.lowercase().contains(normalizedQuery) ||
                event.address.lowercase().contains(normalizedQuery) ||
                event.organizerName.lowercase().contains(normalizedQuery)

            val matchesCategory = category == null || event.category == category

            val matchesRadius = userLat == null || userLng == null || radiusKm <= 0 ||
                (event.latitude != null &&
                    event.longitude != null &&
                    DistanceCalculator.between(
                        userLat, userLng, event.latitude, event.longitude
                    ) <= radiusKm)

            matchesQuery && matchesCategory && matchesRadius
        }
    }

    /** Sorts a list according to the selected [EventSort] option. */
    fun sort(
        events: List<Event>,
        sort: EventSort,
        userLat: Double? = null,
        userLng: Double? = null
    ): List<Event> = when (sort) {
        EventSort.DATE -> events.sortedBy { it.startDate }
        EventSort.NAME -> events.sortedBy { it.title.lowercase() }
        EventSort.DISTANCE -> {
            if (userLat == null || userLng == null) events.sortedBy { it.startDate }
            else events.sortedBy { event ->
                if (event.latitude == null || event.longitude == null) Double.MAX_VALUE
                else DistanceCalculator.between(userLat, userLng, event.latitude, event.longitude)
            }
        }
    }

    /** Enriches a filtered list with per-event distance in km. */
    fun attachDistances(
        events: List<Event>,
        userLat: Double?,
        userLng: Double?
    ): List<EventView> = events.map { event ->
        val distance = if (userLat != null && userLng != null &&
            event.latitude != null && event.longitude != null
        ) {
            DistanceCalculator.between(userLat, userLng, event.latitude, event.longitude)
        } else null
        EventView(event, distanceKm = distance)
    }
}