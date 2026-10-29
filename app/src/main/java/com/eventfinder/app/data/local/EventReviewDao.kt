package com.eventfinder.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Local reviews and star ratings attached to an event. */
@Dao
interface EventReviewDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(review: EventReviewEntity)

    @Query("SELECT * FROM event_reviews WHERE eventId = :eventId ORDER BY createdAt DESC")
    fun observeForEvent(eventId: String): Flow<List<EventReviewEntity>>

    @Query("SELECT COUNT(*) FROM event_reviews WHERE eventId = :eventId")
    fun observeCount(eventId: String): Flow<Int>

    @Query("SELECT AVG(rating) FROM event_reviews WHERE eventId = :eventId")
    fun observeAverage(eventId: String): Flow<Double?>

    @Query("SELECT * FROM event_reviews WHERE eventId = :eventId AND userId = :userId LIMIT 1")
    suspend fun find(eventId: String, userId: String): EventReviewEntity?

    @Query("DELETE FROM event_reviews WHERE eventId = :eventId AND userId = :userId")
    suspend fun delete(eventId: String, userId: String)

    @Query("DELETE FROM event_reviews WHERE eventId = :eventId")
    suspend fun deleteAllForEvent(eventId: String)

    @Query("SELECT * FROM event_reviews ORDER BY createdAt DESC")
    fun observeAllReviews(): Flow<List<EventReviewEntity>>
}
