package com.eventfinder.app.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the Haversine great-circle calculations used for proximity sorting
 * and the "near me" radius filter (FR-02, NFR-01).
 */
class DistanceCalculatorTest {

    // Johannesburg CBD and Cape Town CBD (well known points ~1265 km apart).
    private val johannesburg = -26.2041 to 28.0473
    private val capeTown = -33.9249 to 18.4241

    @Test
    fun `distance between Johannesburg and Cape Town is about 1265 km`() {
        val km = DistanceCalculator.between(
            johannesburg.first, johannesburg.second,
            capeTown.first, capeTown.second
        )
        assertTrue("was $km km", km in 1240.0..1290.0)
    }

    @Test
    fun `distance from a point to itself is zero`() {
        val km = DistanceCalculator.between(
            johannesburg.first, johannesburg.second,
            johannesburg.first, johannesburg.second
        )
        assertEquals(0.0, km, 0.0001)
    }

    @Test
    fun `distance is symmetric`() {
        val there = DistanceCalculator.between(-26.2041, 28.0473, -33.9249, 18.4241)
        val back = DistanceCalculator.between(-33.9249, 18.4241, -26.2041, 28.0473)
        assertEquals(there, back, 0.0001)
    }

    @Test
    fun `display distance rounds to one decimal place`() {
        assertEquals(1264.6, DistanceCalculator.displayDistanceKm(1264.6499), 0.0001)
        assertEquals(0.0, DistanceCalculator.displayDistanceKm(0.04), 0.0001)
        assertEquals(3.3, DistanceCalculator.displayDistanceKm(3.25), 0.0001)
        assertEquals(3.2, DistanceCalculator.displayDistanceKm(3.24), 0.0001)
    }

    @Test
    fun `radius test includes near points and excludes far ones`() {
        assertTrue(
            DistanceCalculator.isWithinRadius(
                johannesburg.first, johannesburg.second, capeTown, radiusKm = 1500.0
            )
        )
        assertFalse(
            DistanceCalculator.isWithinRadius(
                johannesburg.first, johannesburg.second, capeTown, radiusKm = 100.0
            )
        )
    }
}
