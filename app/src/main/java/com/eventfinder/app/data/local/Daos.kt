package com.eventfinder.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** Data access for registered users. */
@Dao
interface UserDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(user: UserEntity)

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    fun observeUser(id: String): Flow<UserEntity?>

    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun findByEmail(email: String): UserEntity?

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): UserEntity?

    @Query("SELECT * FROM users")
    fun observeAll(): Flow<List<UserEntity>>

    @Query("SELECT COUNT(*) FROM users")
    suspend fun count(): Long

    @Query("DELETE FROM users")
    suspend fun deleteAll()

    @Query("DELETE FROM users WHERE id = :id")
    suspend fun deleteById(id: String)
}

/** Data access for the event directory. */
@Dao
interface EventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(events: List<EventEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(event: EventEntity)

    @Query("SELECT * FROM events ORDER BY startDate ASC")
    fun observeAll(): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): EventEntity?

    @Query("""
        SELECT e.* FROM events e
        INNER JOIN favorites f ON f.eventId = e.id
        WHERE f.userId = :userId
        ORDER BY e.startDate ASC
    """)
    fun observeFavoriteEventsForUser(userId: String): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE id IN (:ids)")
    suspend fun findByIds(ids: List<String>): List<EventEntity>

    @Query("SELECT COUNT(*) FROM events")
    suspend fun count(): Int

    @Query("SELECT * FROM events WHERE isCreatedByUser = 0")
    suspend fun getSynced(): List<EventEntity>

    @Query("DELETE FROM events WHERE isCreatedByUser = 0")
    suspend fun deleteSynced()

    @Query("DELETE FROM events WHERE isCreatedByUser = 1")
    suspend fun deleteCreatedByUser()

    @Query("DELETE FROM events WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM events WHERE organizerId = :userId AND isCreatedByUser = 1")
    suspend fun deleteCreatedByUserId(userId: String)

    @Query("""
        SELECT e.* FROM events e
        INNER JOIN rsvps r ON r.eventId = e.id
        WHERE r.status = 'attending' AND r.userId = :userId AND e.startDate > :now
        ORDER BY e.startDate ASC
    """)
    suspend fun getUpcomingAttendingEventsForUser(userId: String, now: Long): List<EventEntity>

    @Transaction
    suspend fun replaceSyncedEvents(events: List<EventEntity>) {
        deleteSynced()
        upsertAll(events)
    }
}

/** Data access for favourites (offline-first, see FR-03). */
@Dao
interface FavoriteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE userId = :userId AND eventId = :eventId")
    suspend fun delete(userId: String, eventId: String)

    @Query("SELECT * FROM favorites WHERE userId = :userId")
    fun observeAllForUser(userId: String): Flow<List<FavoriteEntity>>

    @Query("SELECT COUNT(*) FROM favorites")
    suspend fun count(): Int

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE userId = :userId AND eventId = :eventId)")
    suspend fun exists(userId: String, eventId: String): Boolean

    @Query("DELETE FROM favorites WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)
}

/** Data access for RSVPs (FR-02/FR-06). */
@Dao
interface RsvpDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rsvp: RsvpEntity)

    @Query("DELETE FROM rsvps WHERE userId = :userId AND eventId = :eventId")
    suspend fun delete(userId: String, eventId: String)

    @Query("SELECT * FROM rsvps WHERE userId = :userId")
    fun observeAllForUser(userId: String): Flow<List<RsvpEntity>>

    @Query("SELECT status FROM rsvps WHERE userId = :userId AND eventId = :eventId LIMIT 1")
    suspend fun statusFor(userId: String, eventId: String): String?

    @Query("SELECT COUNT(*) FROM rsvps WHERE status = 'attending'")
    suspend fun attendingCount(): Int

    @Query("DELETE FROM rsvps WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)
}

/** Data access for the offline action queue (FR-09). */
@Dao
interface PendingSyncDao {

    @Insert
    suspend fun insert(pending: PendingSyncEntity)

    @Query("SELECT * FROM pending_sync WHERE userId = :userId ORDER BY createdAt ASC")
    suspend fun allForUser(userId: String): List<PendingSyncEntity>

    @Query("SELECT * FROM pending_sync ORDER BY createdAt ASC")
    suspend fun all(): List<PendingSyncEntity>

    @Query("DELETE FROM pending_sync WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE pending_sync SET retryCount = retryCount + 1 WHERE id = :id")
    suspend fun incrementRetry(id: Long)

    @Query("SELECT COUNT(*) FROM pending_sync WHERE userId = :userId")
    suspend fun countForUser(userId: String): Int

    @Query("SELECT COUNT(*) FROM pending_sync")
    suspend fun count(): Int

    @Query("DELETE FROM pending_sync WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)
}