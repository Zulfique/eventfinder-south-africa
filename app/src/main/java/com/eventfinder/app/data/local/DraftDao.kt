package com.eventfinder.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DraftDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(draft: DraftEntity)

    @Query("SELECT * FROM event_drafts WHERE userId = :userId ORDER BY savedAt DESC")
    suspend fun forUser(userId: String): List<DraftEntity>

    @Query("DELETE FROM event_drafts WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM event_drafts WHERE userId = :userId")
    suspend fun clearForUser(userId: String)
}