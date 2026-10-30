package com.eventfinder.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Local (device) reviews and star ratings attached to an event.
 *
 * Reviews are restricted to verified attendees by the repository. The first
 * release keeps review state in Room only, so ratings remain truthful when
 * the device is offline.
 */
@Entity(
    tableName = "event_reviews",
    primaryKeys = ["eventId", "userId"],
    foreignKeys = [
        ForeignKey(
            entity = EventEntity::class,
            parentColumns = ["id"],
            childColumns = ["eventId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("userId")]
)
data class EventReviewEntity(
    val eventId: String,
    val userId: String,
    val authorName: String,
    val rating: Int,
    val comment: String,
    val createdAt: Long,
    val verifiedAttendance: Boolean = false
)
