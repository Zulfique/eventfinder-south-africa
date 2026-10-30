package com.eventfinder.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ReportDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(report: ReportEntity)

    @Query("SELECT * FROM reports WHERE resolved = 0 ORDER BY createdAt DESC")
    suspend fun pendingReports(): List<ReportEntity>

    @Query("UPDATE reports SET resolved = 1 WHERE id = :id")
    suspend fun resolve(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun block(blocked: BlockedUserEntity)

    @Query("SELECT blockedUserId FROM blocked_users WHERE userId = :userId")
    suspend fun blockedIds(userId: String): List<String>

    @Query("DELETE FROM blocked_users WHERE userId = :userId AND blockedUserId = :blockedId")
    suspend fun unblock(userId: String, blockedId: String)
}