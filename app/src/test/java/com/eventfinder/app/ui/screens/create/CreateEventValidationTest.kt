package com.eventfinder.app.ui.screens.create

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Covers the pure validation behind each wizard step. Keeping it framework-free
 * (no Android resources) lets the JVM tests assert the exact failure cause, which
 * is what drives the inline error messages on screen.
 */
class CreateEventValidationTest {

    private val future = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(2)
    private val past = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1)

    private fun details(
        title: String = "Community braai",
        description: String = "A relaxed afternoon braai for the whole neighbourhood."
    ) = CreateEventUiState(step = 1, title = title, description = description)

    private fun dateVenue(
        dateMillis: Long = future,
        venueName: String = "Maboneng Precinct Hall",
        latitude: String = "",
        longitude: String = ""
    ) = CreateEventUiState(
        step = 2,
        dateMillis = dateMillis,
        venueName = venueName,
        latitude = latitude,
        longitude = longitude
    )

    @Test
    fun `step one requires a title`() {
        assertEquals(ValidationError.TITLE_REQUIRED, validateCreateStep(details(title = "   ")))
    }

    @Test
    fun `step one requires a meaningful description`() {
        assertEquals(
            ValidationError.DESCRIPTION_REQUIRED,
            validateCreateStep(details(description = "too short"))
        )
    }

    @Test
    fun `step one passes with title and description`() {
        assertNull(validateCreateStep(details()))
    }

    @Test
    fun `step two requires a date`() {
        assertEquals(ValidationError.DATE_REQUIRED, validateCreateStep(dateVenue(dateMillis = 0L)))
    }

    @Test
    fun `step two rejects a past date`() {
        assertEquals(ValidationError.DATE_IN_PAST, validateCreateStep(dateVenue(dateMillis = past)))
    }

    @Test
    fun `step two requires a venue`() {
        assertEquals(
            ValidationError.VENUE_REQUIRED,
            validateCreateStep(dateVenue(venueName = " "))
        )
    }

    @Test
    fun `step two rejects out-of-range coordinates`() {
        assertEquals(
            ValidationError.INVALID_COORDINATES,
            validateCreateStep(dateVenue(latitude = "123.4"))
        )
        assertEquals(
            ValidationError.INVALID_COORDINATES,
            validateCreateStep(dateVenue(longitude = "-999"))
        )
    }

    @Test
    fun `step two passes with a future date and venue`() {
        assertNull(validateCreateStep(dateVenue()))
        assertNull(validateCreateStep(dateVenue(latitude = "-26.2041", longitude = "28.0473")))
    }

    @Test
    fun `review step never blocks`() {
        assertNull(validateCreateStep(CreateEventUiState(step = 3, dateMillis = 0L)))
    }

    @Test
    fun `coordinate bounds are inclusive and blanks allowed`() {
        assertTrue(isValidLatitude(""))
        assertTrue(isValidLatitude("-90"))
        assertTrue(isValidLatitude("90"))
        assertFalse(isValidLatitude("90.1"))
        assertFalse(isValidLatitude("abc"))

        assertTrue(isValidLongitude(""))
        assertTrue(isValidLongitude("-180"))
        assertTrue(isValidLongitude("180"))
        assertFalse(isValidLongitude("180.5"))
    }
}
