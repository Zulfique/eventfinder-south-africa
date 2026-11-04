package com.eventfinder.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** Imported feeds often omit a category, so titles drive the fallback. */
class EventCategoryTest {

    @Test
    fun `motorsport titles are classified as sport`() {
        assertEquals(
            EventCategory.SPORTS,
            EventCategory.inferFromText("WC REGIONAL & CSMX CLUB ENDURO CHAMPIONSHIP")
        )
        assertEquals(EventCategory.SPORTS, EventCategory.inferFromText("KZN REGIONAL ENDURO - ESHOWE"))
        assertEquals(EventCategory.SPORTS, EventCategory.inferFromText("MOTOCROSS CLUB DAY"))
        assertEquals(EventCategory.SPORTS, EventCategory.inferFromText("TRACN4 NATIONAL RALLY"))
    }

    @Test
    fun `music food and markets are recognised`() {
        assertEquals(EventCategory.MUSIC, EventCategory.inferFromText("Joburg Jazz Nights"))
        assertEquals(EventCategory.FOOD, EventCategory.inferFromText("Cape Town Street Food Festival"))
        assertEquals(EventCategory.MARKETS, EventCategory.inferFromText("Neighbourgoods Market"))
    }

    @Test
    fun `ambiguous substrings do not misfire`() {
        // "brunch" contains "run", "display" contains "play", "concertina" is not a concert.
        assertEquals(EventCategory.OTHER, EventCategory.inferFromText("Sunday brunch gathering"))
        assertEquals(EventCategory.OTHER, EventCategory.inferFromText("Shop window display evening"))
        assertEquals(EventCategory.OTHER, EventCategory.inferFromText("General meeting"))
    }

    @Test
    fun `blank text stays other`() {
        assertEquals(EventCategory.OTHER, EventCategory.inferFromText(""))
        assertEquals(EventCategory.OTHER, EventCategory.inferFromText("   "))
    }
}
