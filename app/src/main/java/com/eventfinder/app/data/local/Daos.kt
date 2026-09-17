package com.eventfinder.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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

    @Query("SELECT * FROM events WHERE isFavorite = 1 ORDER BY startDate ASC")
    fun observeFavorites(): Flow<List<EventEntity>>

    @Query("UPDATE events SET isFavorite = :isFavorite WHERE id = :eventId")
    suspend fun setFavorite(eventId: String, isFavorite: Boolean)

    @Query("SELECT * FROM events WHERE id IN (:ids)")
    suspend fun findByIds(ids: List<String>): List<EventEntity>

    @Query("SELECT COUNT(*) FROM events")
    suspend fun count(): Int

    @Query("DELETE FROM events WHERE isCreatedByUser = 0")
    suspend fun deleteSynced()

    @Query("DELETE FROM events WHERE isCreatedByUser = 1")
    suspend fun deleteCreatedByUser()

    @Query("DELETE FROM events WHERE id = :id")
    suspend fun deleteById(id: String)
}

/** Data access for favourites (offline-first, see FR-03). */
@Dao
interface FavoriteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE eventId = :eventId")
    suspend fun delete(eventId: String)

    @Query("SELECT * FROM favorites")
    fun observeAll(): Flow<List<FavoriteEntity>>

    @Query("SELECT COUNT(*) FROM favorites")
    suspend fun count(): Int

    @Query("SELECT 1 FROM favorites WHERE eventId = :eventId LIMIT 1")
    suspend fun exists(eventId: String): Boolean?
}

/** Data access for RSVPs (FR-02/FR-06). */
@Dao
interface RsvpDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rsvp: RsvpEntity)

    @Query("DELETE FROM rsvps WHERE eventId = :eventId")
    suspend fun delete(eventId: String)

    @Query("SELECT * FROM rsvps")
    fun observeAll(): Flow<List<RsvpEntity>>

    @Query("SELECT status FROM rsvps WHERE eventId = :eventId LIMIT 1")
    suspend fun statusFor(eventId: String): String?

    @Query("SELECT COUNT(*) FROM rsvps WHERE status = 'attending'")
    suspend fun attendingCount(): Int
}

/** Data access for the offline action queue (FR-09). */
@Dao
interface PendingSyncDao {

    @Insert
    suspend fun insert(pending: PendingSyncEntity)

    @Query("SELECT * FROM pending_sync ORDER BY createdAt ASC")
    suspend fun all(): List<PendingSyncEntity>

    @Query("DELETE FROM pending_sync WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE pending_sync SET retryCount = retryCount + 1 WHERE id = :id")
    suspend fun incrementRetry(id: Long)

    @Query("SELECT COUNT(*) FROM pending_sync")
    suspend fun count(): Int
}