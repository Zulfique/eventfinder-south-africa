package com.eventfinder.app.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoNormalizerTest {

    @Test
    fun `normalizeVenueName formats raw strings correctly`() {
        assertEquals("", GeoNormalizer.normalizeVenueName(null))
        assertEquals("", GeoNormalizer.normalizeVenueName("   "))
        assertEquals("Green Point Stadium", GeoNormalizer.normalizeVenueName("  green   point   STADIUM  "))
    }

    @Test
    fun `normalizeAddress cleans whitespace`() {
        assertEquals("", GeoNormalizer.normalizeAddress(null))
        assertEquals("123 Long Street, Cape Town", GeoNormalizer.normalizeAddress("123   Long Street,    Cape Town"))
    }

    @Test
    fun `isValidSaCoordinate checks bounding box`() {
        assertTrue(GeoNormalizer.isValidSaCoordinate(-33.92, 18.42)) // Cape Town
        assertTrue(GeoNormalizer.isValidSaCoordinate(-26.20, 28.04)) // Johannesburg
        assertFalse(GeoNormalizer.isValidSaCoordinate(null, 18.42))
        assertFalse(GeoNormalizer.isValidSaCoordinate(-33.92, null))
        assertFalse(GeoNormalizer.isValidSaCoordinate(0.0, 0.0)) // Equator/Null Island
        assertFalse(GeoNormalizer.isValidSaCoordinate(-40.0, 18.42)) // Outside SA
    }
}
