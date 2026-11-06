package com.eventfinder.app.data.local

import androidx.room.Entity

/**
 * A community content rating: how one attendee classifies an event's tone and
 * contents. Kept separate from [EventReviewEntity] because a rating is metadata
 * about suitability, not a written review, and the two have different lifecycles.
 *
 * One row per (event, user) so re-rating replaces the previous answer.
 */
@Entity(tableName = "content_ratings", primaryKeys = ["eventId", "userId"])
data class ContentRatingEntity(
    val eventId: String,
    val userId: String,
    /** 1..5 stars. */
    val stars: Int,
    /** family | adult | mixed | eighteen_plus */
    val contentType: String,
    /** Comma-separated tags: music, food, social, other. */
    val tags: String,
    val concerns: String,
    val createdAt: Long
)
