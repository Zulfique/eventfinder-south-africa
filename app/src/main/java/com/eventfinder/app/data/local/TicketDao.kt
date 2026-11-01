package com.eventfinder.app.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TicketDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(ticket: TicketEntity)

    @Query("SELECT * FROM tickets WHERE id = :id")
    suspend fun getById(id: String): TicketEntity?

    @Query("SELECT * FROM tickets WHERE ticketCode = :code")
    suspend fun getByCode(code: String): TicketEntity?

    @Query("SELECT * FROM tickets WHERE eventId = :eventId")
    fun getTicketsForEvent(eventId: String): Flow<List<TicketEntity>>

    @Query("UPDATE tickets SET checkedInAt = :timestamp WHERE id = :id")
    suspend fun markCheckedIn(id: String, timestamp: Long)

    @Query("SELECT * FROM tickets WHERE eventId = :eventId AND checkedInAt IS NULL")
    suspend fun getUncheckedTicketsForEvent(eventId: String): List<TicketEntity>

    @Query("SELECT * FROM tickets WHERE eventId = :eventId")
    suspend fun getAllTicketsForEvent(eventId: String): List<TicketEntity>

    @Query("SELECT * FROM tickets WHERE eventId = :eventId AND userId = :userId LIMIT 1")
    suspend fun findForUser(eventId: String, userId: String): TicketEntity?

    @Query("SELECT * FROM tickets WHERE userId = :userId ORDER BY issuedAt DESC")
    suspend fun getAllForUser(userId: String): List<TicketEntity>

    @Query("SELECT * FROM tickets WHERE userId = :userId ORDER BY issuedAt DESC")
    fun observeForUser(userId: String): Flow<List<TicketEntity>>
}
