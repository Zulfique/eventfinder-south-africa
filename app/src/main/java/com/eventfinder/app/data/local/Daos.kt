package com.eventfinder.app.data.local

import androidx.room.Dao
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

    @Query("SELECT COUNT(*) FROM users")
    suspend fun count(): Long

    @Query("DELETE FROM users")
    suspend fun deleteAll()

    @Query("DELETE FROM users WHERE id = :id")
    suspend fun deleteById(id: String)
}

/** Data access for the local event catalogue. */
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
    suspend fun getNonUserCreated(): List<EventEntity>

    @Query("DELETE FROM events WHERE isCreatedByUser = 0")
    suspend fun deleteNonUserCreated()

    @Query("DELETE FROM events WHERE isCreatedByUser = 1")
    suspend fun deleteCreatedByUser()

    @Query("DELETE FROM events WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM events WHERE organizerId = :userId AND isCreatedByUser = 1")
    suspend fun deleteCreatedByUserId(userId: String)

    @Query("DELETE FROM events WHERE organizerId = :organizerId AND isCreatedByUser = 0")
    suspend fun deleteByOrganizerId(organizerId: String)

    @Query("SELECT id FROM events WHERE organizerId = :organizerId AND isCreatedByUser = 0")
    suspend fun findIdsByOrganizerId(organizerId: String): List<String>

    @Query("DELETE FROM events WHERE id IN (:eventIds)")
    suspend fun deleteByIds(eventIds: List<String>)

    @Query("""
        SELECT e.* FROM events e
        INNER JOIN rsvps r ON r.eventId = e.id
        WHERE r.status = 'attending' AND r.userId = :userId AND e.startDate > :now
        ORDER BY e.startDate ASC
    """)
    suspend fun getUpcomingAttendingEventsForUser(userId: String, now: Long): List<EventEntity>

    @Transaction
    suspend fun replaceNonUserCreated(events: List<EventEntity>) {
        deleteNonUserCreated()
        upsertAll(events)
    }

    @Transaction
    suspend fun replaceEventsForSource(organizerId: String, events: List<EventEntity>) {
        val existingIds = findIdsByOrganizerId(organizerId).toSet()
        val incomingIds = events.map { it.id }.toSet()
        val staleIds = existingIds - incomingIds

        if (staleIds.isNotEmpty()) {
            deleteOrphanFavorites(staleIds.toList())
            deleteOrphanRsvps(staleIds.toList())
            deleteByIds(staleIds.toList())
        }

        if (events.isNotEmpty()) {
            upsertAll(events)
        }
    }

    @Query("DELETE FROM favorites WHERE eventId IN (:eventIds)")
    suspend fun deleteOrphanFavorites(eventIds: List<String>)

    @Query("DELETE FROM rsvps WHERE eventId IN (:eventIds)")
    suspend fun deleteOrphanRsvps(eventIds: List<String>)
}

/** Data access for favourites. */
@Dao
interface FavoriteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE userId = :userId AND eventId = :eventId")
    suspend fun delete(userId: String, eventId: String)

    @Query("DELETE FROM favorites WHERE eventId = :eventId")
    suspend fun deleteAllForEvent(eventId: String)

    @Query("SELECT * FROM favorites WHERE userId = :userId")
    fun observeAllForUser(userId: String): Flow<List<FavoriteEntity>>

    @Query("SELECT COUNT(*) FROM favorites")
    suspend fun count(): Int

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE userId = :userId AND eventId = :eventId)")
    suspend fun exists(userId: String, eventId: String): Boolean

    @Query("DELETE FROM favorites WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)
}

/** Data access for RSVPs. */
@Dao
interface RsvpDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rsvp: RsvpEntity)

    @Query("DELETE FROM rsvps WHERE userId = :userId AND eventId = :eventId")
    suspend fun delete(userId: String, eventId: String)

    @Query("DELETE FROM rsvps WHERE eventId = :eventId")
    suspend fun deleteAllForEvent(eventId: String)

    @Query("SELECT * FROM rsvps WHERE userId = :userId")
    fun observeAllForUser(userId: String): Flow<List<RsvpEntity>>

    @Query("SELECT status FROM rsvps WHERE userId = :userId AND eventId = :eventId LIMIT 1")
    suspend fun statusFor(userId: String, eventId: String): String?

    @Query("SELECT COUNT(*) FROM rsvps WHERE status = 'attending'")
    suspend fun attendingCount(): Int

    @Query("DELETE FROM rsvps WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)
}
