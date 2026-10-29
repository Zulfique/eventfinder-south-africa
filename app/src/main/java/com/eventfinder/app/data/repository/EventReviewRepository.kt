package com.eventfinder.app.data.repository

import com.eventfinder.app.data.local.EventReviewDao
import com.eventfinder.app.data.local.EventReviewEntity
import com.eventfinder.app.data.local.EventDao
import com.eventfinder.app.data.local.RsvpDao
import com.eventfinder.app.data.local.UserDao
import com.eventfinder.app.data.store.SessionProvider
import com.eventfinder.app.domain.model.EventReview
import com.eventfinder.app.domain.model.RsvpStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class RatingSummary(
    val average: Double?,
    val count: Int
)

interface EventReviewRepository {
    fun observeReviews(eventId: String): Flow<List<EventReview>>
    fun observeRatingSummary(eventId: String): Flow<RatingSummary>
    fun observeAllRatingSummaries(): Flow<Map<String, RatingSummary>>
    suspend fun canReview(eventId: String): Boolean
    suspend fun submitReview(eventId: String, rating: Int, comment: String): Result<Unit>
}

class EventReviewRepositoryImpl(
    private val reviewDao: EventReviewDao,
    private val eventDao: EventDao,
    private val rsvpDao: RsvpDao,
    private val userDao: UserDao,
    private val preferences: SessionProvider
) : EventReviewRepository {

    override fun observeReviews(eventId: String): Flow<List<EventReview>> =
        reviewDao.observeForEvent(eventId).map { rows -> rows.map { it.toDomain() } }

    override fun observeRatingSummary(eventId: String): Flow<RatingSummary> =
        combine(reviewDao.observeAverage(eventId), reviewDao.observeCount(eventId)) { average, count ->
            RatingSummary(average = if (count == 0) null else average, count = count)
        }

    override fun observeAllRatingSummaries(): Flow<Map<String, RatingSummary>> =
        reviewDao.observeAllReviews().map { rows ->
            rows.groupBy { it.eventId }
                .mapValues { (_, eventRows) ->
                    val average = eventRows.map { it.rating }.average()
                    RatingSummary(average = average, count = eventRows.size)
                }
        }

    override suspend fun canReview(eventId: String): Boolean {
        val userId = preferences.sessionUserId.first() ?: return false
        val event = eventDao.findById(eventId) ?: return false
        if (event.isCreatedByUser && event.organizerId == userId) return true
        return rsvpDao.statusFor(userId, eventId) == RsvpStatus.ATTENDING.storage
    }

    override suspend fun submitReview(eventId: String, rating: Int, comment: String): Result<Unit> =
        runCatching {
            if (rating !in 1..5) throw IllegalArgumentException("rating_out_of_range")
            val userId = preferences.sessionUserId.first()
                ?: throw IllegalStateException("not_logged_in")
            if (!canReview(eventId)) throw SecurityException("not_verified_attendee")
            val existing = reviewDao.find(eventId, userId)
            val user = userDao.findById(userId)
            val event = eventDao.findById(eventId) ?: throw IllegalArgumentException("event_missing")
            reviewDao.upsert(
                com.eventfinder.app.data.local.EventReviewEntity(
                    eventId = eventId,
                    userId = userId,
                    rating = rating,
                    comment = comment.trim().take(500),
                    authorName = user?.fullName?.trim()?.ifBlank { null } ?: user?.email?.trim().orEmpty().ifBlank { "User" },
                    createdAt = existing?.createdAt
                        ?: event.startDate.coerceAtMost(System.currentTimeMillis())
                )
            )
        }

    private fun com.eventfinder.app.data.local.EventReviewEntity.toDomain() =
        EventReview(
            eventId = eventId,
            userId = userId,
            authorName = authorName,
            rating = rating,
            comment = comment,
            createdAt = createdAt
        )
}
