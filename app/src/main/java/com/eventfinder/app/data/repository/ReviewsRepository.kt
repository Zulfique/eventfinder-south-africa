package com.eventfinder.app.data.repository

import com.eventfinder.app.data.local.CachedReviewEntity
import com.eventfinder.app.data.local.CachedReviewSummaryEntity
import com.eventfinder.app.data.local.ReviewDao
import com.eventfinder.app.data.remote.ApiReviewDto
import com.eventfinder.app.data.remote.ApiReviewListDto
import com.eventfinder.app.data.remote.EventFinderApiClient
import com.eventfinder.app.data.store.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Ratings and reviews: reads flow from the Room cache (instant UI, works
 * offline) and writes go through the REST API, which owns the canonical copy
 * and the verified-attendee flag. After every successful write the returned
 * server state replaces the cache so averages stay authoritative.
 */
class ReviewsRepository(
    private val api: EventFinderApiClient,
    private val dao: ReviewDao,
    private val preferences: UserPreferences
) {

    fun observeReviews(eventId: String): Flow<List<CachedReviewEntity>> =
        dao.observeForEvent(eventId)

    fun observeSummary(eventId: String): Flow<CachedReviewSummaryEntity?> =
        dao.observeSummary(eventId)

    /** Re-fetches reviews + summary from the server. Failure keeps the cache. */
    suspend fun refresh(eventId: String): Result<Unit> =
        api.getReviews(eventId).map { list -> cache(eventId, list) }

    /** Creates or replaces the signed-in user's review. */
    suspend fun submit(eventId: String, rating: Int, comment: String?): Result<Unit> {
        if (rating !in 1..5) return Result.failure(IllegalArgumentException("rating_out_of_range"))
        val token = preferences.authToken.first()
            ?: return Result.failure(IllegalStateException("not_signed_in"))
        return api.putReview("Bearer $token", eventId, rating, comment?.trim())
            .map { cache(eventId, it) }
    }

    /** Deletes the signed-in user's review. */
    suspend fun delete(eventId: String): Result<Unit> {
        val token = preferences.authToken.first()
            ?: return Result.failure(IllegalStateException("not_signed_in"))
        return api.deleteReview("Bearer $token", eventId)
            .map { cache(eventId, it) }
    }

    /** Batch-loads rating summaries for event cards (server allows 100/call). */
    suspend fun loadSummaries(ids: List<String>) {
        ids.filter { it.isNotBlank() }.chunked(100).forEach { chunk ->
            api.reviewSummary(chunk).onSuccess { items ->
                items.forEach { item ->
                    dao.upsertSummary(
                        CachedReviewSummaryEntity(
                            eventId = item.eventId,
                            average = item.average,
                            count = item.count
                        )
                    )
                }
            }
        }
    }

    private suspend fun cache(eventId: String, list: ApiReviewListDto) {
        dao.clearForEvent(eventId)
        dao.upsertAll(list.items.map { it.toEntity(eventId) })
        dao.upsertSummary(
            CachedReviewSummaryEntity(
                eventId = eventId,
                average = list.average,
                count = list.count
            )
        )
    }
}

private fun ApiReviewDto.toEntity(eventId: String) = CachedReviewEntity(
    reviewId = id,
    eventId = eventId,
    userId = userId,
    userName = fullName,
    rating = rating,
    comment = comment,
    isVerifiedAttendee = isVerifiedAttendee,
    createdAt = parseIsoToEpochMillis(createdAt),
    updatedAt = parseIsoToEpochMillis(updatedAt)
)

/** ISO-8601 from ASP.NET (with or without nanos) -> epoch millis, 0 on failure. */
internal fun parseIsoToEpochMillis(raw: String?): Long {
    if (raw.isNullOrBlank()) return 0L
    return runCatching { java.time.Instant.parse(raw).toEpochMilli() }.getOrElse {
        runCatching {
            java.time.OffsetDateTime.parse(raw).toInstant().toEpochMilli()
        }.getOrDefault(0L)
    }
}
