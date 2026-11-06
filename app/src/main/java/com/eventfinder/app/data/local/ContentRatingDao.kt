package com.eventfinder.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ContentRatingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rating: ContentRatingEntity)

    @Query("SELECT * FROM content_ratings WHERE eventId = :eventId AND userId = :userId LIMIT 1")
    suspend fun find(eventId: String, userId: String): ContentRatingEntity?

    @Query("SELECT * FROM content_ratings WHERE eventId = :eventId")
    fun observeForEvent(eventId: String): Flow<List<ContentRatingEntity>>

    @Query("SELECT COUNT(*) FROM content_ratings WHERE eventId = :eventId")
    fun observeCount(eventId: String): Flow<Int>
}
