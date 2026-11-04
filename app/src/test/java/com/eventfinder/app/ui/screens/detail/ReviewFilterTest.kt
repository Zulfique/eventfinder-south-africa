package com.eventfinder.app.ui.screens.detail

import com.eventfinder.app.domain.model.EventReview
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for the review filter chips, including the new date/photo filters. */
class ReviewFilterTest {

    private val now = System.currentTimeMillis()

    private fun review(
        rating: Int = 5,
        createdAt: Long = now,
        verified: Boolean = false,
        photos: List<String> = emptyList()
    ) = EventReview(
        eventId = "event-1",
        userId = "user-1",
        authorName = "Thandi",
        rating = rating,
        comment = "Great",
        createdAt = createdAt,
        verifiedAttendance = verified,
        photoUrls = photos
    )

    @Test
    fun `recent matches reviews from the last 30 days only`() {
        assertTrue(ReviewFilter.RECENT.matches(review(createdAt = now - 86_400_000L)))
        assertFalse(ReviewFilter.RECENT.matches(review(createdAt = now - 40L * 86_400_000L)))
    }

    @Test
    fun `photos filter requires at least one photo`() {
        assertTrue(ReviewFilter.PHOTOS.matches(review(photos = listOf("https://img/1.jpg"))))
        assertFalse(ReviewFilter.PHOTOS.matches(review()))
    }

    @Test
    fun `rating filters group low scores with the two star chip`() {
        assertTrue(ReviewFilter.RATING_2.matches(review(rating = 1)))
        assertTrue(ReviewFilter.RATING_2.matches(review(rating = 2)))
        assertFalse(ReviewFilter.RATING_2.matches(review(rating = 3)))
    }

    @Test
    fun `verified filter requires verified attendance`() {
        assertTrue(ReviewFilter.VERIFIED.matches(review(verified = true)))
        assertFalse(ReviewFilter.VERIFIED.matches(review(verified = false)))
    }
}
