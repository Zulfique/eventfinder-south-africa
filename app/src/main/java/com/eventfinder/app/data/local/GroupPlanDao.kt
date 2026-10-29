package com.eventfinder.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupPlanDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(plan: GroupPlanEntity)

    @Query("SELECT * FROM group_plans WHERE eventId = :eventId")
    fun observePlansForEvent(eventId: String): Flow<List<GroupPlanEntity>>

    @Query("SELECT * FROM group_plans WHERE id = :planId")
    suspend fun findById(planId: String): GroupPlanEntity?

    @Query("DELETE FROM group_plans WHERE id = :planId")
    suspend fun deleteById(planId: String)
}
