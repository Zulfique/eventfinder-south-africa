package com.eventfinder.app.domain.model

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Ranks events a user is likely to want after looking at [event].
 *
 * Everything is derived from data already stored locally, so the rail works
 * offline and never invents recommendations: same category, same organiser and
 * proximity to the viewed event's venue all add relevance.
 */
object SimilarEvents {

    /** Candidates closer than this (km) get the full proximity bonus. */
    private const val NEAR_KM = 25.0

    /** Candidates further than this (km) are not considered "similar". */
    private const val FAR_KM = 120.0

    fun rank(
        event: Event,
        candidates: List<Event>,
        now: Long = System.currentTimeMillis(),
        limit: Int = 6
    ): List<Event> {
        if (limit <= 0) return emptyList()
        return candidates.asSequence()
            .filter { it.id != event.id && it.startDate >= now }
            .map { candidate -> candidate to score(event, candidate) }
            .filter { it.second > 0 }
            .sortedWith(
                compareByDescending<Pair<Event, Int>> { it.second }
                    .thenBy { it.first.startDate }
            )
            .map { it.first }
            .take(limit)
            .toList()
    }

    private fun score(event: Event, candidate: Event): Int {
        var score = 0
        if (candidate.category == event.category) score += 5
        if (candidate.organizerId.isNotBlank() && candidate.organizerId == event.organizerId) {
            score += 2
        }
        val km = distanceKm(event, candidate)
        when {
            km == null -> Unit
            km <= NEAR_KM -> score += 3
            km > FAR_KM -> return 0
            else -> score += 1
        }
        return score
    }

    /** Great-circle distance between two events' venues, or null when unknown. */
    private fun distanceKm(a: Event, b: Event): Double? {
        val lat1 = a.latitude ?: return null
        val lon1 = a.longitude ?: return null
        val lat2 = b.latitude ?: return null
        val lon2 = b.longitude ?: return null
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        return 2 * 6371.0 * asin(sqrt(h))
    }
}
