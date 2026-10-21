package com.eventfinder.app.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the offline South African place table. The app must resolve a
 * user in any South African city, so the lookup has to accept the common
 * spellings, nicknames and Afrikaans names.
 */
class SouthAfricanPlacesTest {

    @Test
    fun `resolves major cities by their English name`() {
        assertEquals("Cape Town", SouthAfricanPlaces.findByName("Cape Town")?.name)
        assertEquals("Johannesburg", SouthAfricanPlaces.findByName("Johannesburg")?.name)
        assertEquals("Durban", SouthAfricanPlaces.findByName("Durban")?.name)
        assertEquals("Pretoria", SouthAfricanPlaces.findByName("Pretoria")?.name)
        assertEquals("Port Elizabeth", SouthAfricanPlaces.findByName("Port Elizabeth")?.name)
    }

    @Test
    fun `resolves cities regardless of case and spacing`() {
        assertEquals("Cape Town", SouthAfricanPlaces.findByName("  cape town ")?.name)
        assertEquals("Cape Town", SouthAfricanPlaces.findByName("CAPE TOWN")?.name)
        assertEquals("Cape Town", SouthAfricanPlaces.findByName("capetown")?.name)
    }

    @Test
    fun `resolves Afrikaans names and nicknames`() {
        assertEquals("Cape Town", SouthAfricanPlaces.findByName("Kaapstad")?.name)
        assertEquals("Johannesburg", SouthAfricanPlaces.findByName("Joburg")?.name)
        assertEquals("Port Elizabeth", SouthAfricanPlaces.findByName("Gqeberha")?.name)
        assertEquals("East London", SouthAfricanPlaces.findByName("Oos-London")?.name)
        assertEquals("Mossel Bay", SouthAfricanPlaces.findByName("Mosselbaai")?.name)
    }

    @Test
    fun `resolves a city mentioned with a province or suburb`() {
        assertEquals("Cape Town", SouthAfricanPlaces.findByName("Cape Town, Western Cape")?.name)
        assertEquals("Johannesburg", SouthAfricanPlaces.findByName("Sandton Johannesburg")?.name)
        assertEquals("Durban", SouthAfricanPlaces.findByName("Durban, KwaZulu-Natal")?.name)
    }

    @Test
    fun `returns null for unknown or blank names`() {
        assertNull(SouthAfricanPlaces.findByName("Atlantis"))
        assertNull(SouthAfricanPlaces.findByName(""))
        assertNull(SouthAfricanPlaces.findByName("   "))
        assertNull(SouthAfricanPlaces.findByName(null))
    }

    @Test
    fun `national default is a South African city`() {
        val fallback = SouthAfricanPlaces.nationalDefault
        assertNotNull(SouthAfricanPlaces.findByName(fallback.name))
        assertTrue(fallback.latitude in -35.0..-22.0)
        assertTrue(fallback.longitude in 16.0..33.0)
    }

    @Test
    fun `every table entry lies inside South Africa`() {
        SouthAfricanPlaces.all.forEach { place ->
            assertTrue(
                "${place.name} latitude out of range",
                place.latitude in -35.5..-21.5
            )
            assertTrue(
                "${place.name} longitude out of range",
                place.longitude in 16.0..33.5
            )
        }
    }

    @Test
    fun `table has no duplicate city names`() {
        val names = SouthAfricanPlaces.all.map { it.name }
        assertEquals(names.size, names.toSet().size)
    }
}
