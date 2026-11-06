package com.eventfinder.app.data.repository

import com.eventfinder.app.data.local.ContentRatingDao
import com.eventfinder.app.data.local.ContentRatingEntity
import com.eventfinder.app.data.store.SessionProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Content types an event can be classified as. */
enum class EventContentType(val storage: String) {
    FAMILY("family"),
    ADULT("adult"),
    MIXED("mixed"),
    EIGHTEEN_PLUS("eighteen_plus");

    companion object {
        fun fromStorage(value: String?): EventContentType =
            entries.firstOrNull { it.storage == value } ?: MIXED
    }
}

/** Content tags offered in the rating form. */
enum class EventContentTag(val storage: String) {
    MUSIC("music"),
    FOOD_AND_DRINK("food"),
    SOCIAL("social"),
    OTHER("other");

    companion object {
        fun fromStorage(value: String): EventContentTag? =
            entries.firstOrNull { it.storage == value }
    }
}

data class ContentRating(
    val eventId: String,
    val stars: Int,
    val contentType: EventContentType,
    val tags: Set<EventContentTag>,
    val concerns: String,
    val createdAt: Long
)

interface ContentRatingRepository {
    /** Stores the signed-in user's rating for [eventId], replacing any previous one. */
    suspend fun submit(
        eventId: String,
        stars: Int,
        contentType: EventContentType,
        tags: Set<EventContentTag>,
        concerns: String
    ): Result<Unit>

    /** The signed-in user's existing rating, so the form can be pre-filled. */
    suspend fun myRating(eventId: String): ContentRating?

    /** How many people have rated the event. */
    fun observeCount(eventId: String): Flow<Int>
}

class ContentRatingRepositoryImpl(
    private val dao: ContentRatingDao,
    private val preferences: SessionProvider
) : ContentRatingRepository {

    override suspend fun submit(
        eventId: String,
        stars: Int,
        contentType: EventContentType,
        tags: Set<EventContentTag>,
        concerns: String
    ): Result<Unit> = runCatching {
        if (stars !in 1..5) throw IllegalArgumentException("rating_out_of_range")
        val userId = preferences.sessionUserId.first()
            ?: throw IllegalStateException("not_logged_in")
        dao.upsert(
            ContentRatingEntity(
                eventId = eventId,
                userId = userId,
                stars = stars,
                contentType = contentType.storage,
                tags = tags.map { it.storage }.sorted().joinToString(","),
                concerns = concerns.trim().take(MAX_CONCERNS),
                createdAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun myRating(eventId: String): ContentRating? {
        val userId = preferences.sessionUserId.first() ?: return null
        val row = dao.find(eventId, userId) ?: return null
        return ContentRating(
            eventId = row.eventId,
            stars = row.stars,
            contentType = EventContentType.fromStorage(row.contentType),
            tags = row.tags.split(',').mapNotNull { EventContentTag.fromStorage(it.trim()) }.toSet(),
            concerns = row.concerns,
            createdAt = row.createdAt
        )
    }

    override fun observeCount(eventId: String): Flow<Int> = dao.observeCount(eventId)

    /** Kept in step with the 500-character field in the UI and Play's review limits. */
    companion object {
        const val MAX_CONCERNS = 500
    }
}
