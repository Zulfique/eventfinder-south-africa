package com.eventfinder.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.eventfinder.app.domain.model.Event
import com.eventfinder.app.domain.model.User

/**
 * Transactional operations that the repository needs. Extracted into an
 * interface so tests can provide fakes without Room dependencies.
 */
interface DatabaseTransactionHelper {
    /** Atomically inserts/removes a favourite and updates the event flag. */
    suspend fun setFavorite(eventId: String, favorite: Boolean)
    /** Atomically deletes all user data during account deletion. */
    suspend fun deleteAccountData(userId: String)
}

/**
 * Local persistence layer (Room) implementing the offline-first strategy from
 * FR-09 of the design document (§7.1 Local Database Models).
 *
 * References:
 *  - Android Developers, "Room Persistence Library":
 *    https://developer.android.com/training/data-storage/room
 */
@Database(
    entities = [
        UserEntity::class,
        EventEntity::class,
        FavoriteEntity::class,
        RsvpEntity::class,
        PendingSyncEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase(), DatabaseTransactionHelper {

    abstract fun userDao(): UserDao
    abstract fun eventDao(): EventDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun rsvpDao(): RsvpDao
    abstract fun pendingSyncDao(): PendingSyncDao

    override suspend fun setFavorite(eventId: String, favorite: Boolean) {
        if (favorite) {
            favoriteDao().insert(
                FavoriteEntity(eventId = eventId, createdAt = System.currentTimeMillis(), isSynced = false)
            )
        } else {
            favoriteDao().delete(eventId)
        }
        eventDao().setFavorite(eventId, favorite)
    }

    override suspend fun deleteAccountData(userId: String) {
        pendingSyncDao().deleteForUserEvents(userId)
        favoriteDao().deleteForUserEvents(userId)
        rsvpDao().deleteForUserEvents(userId)
        eventDao().deleteCreatedByUserId(userId)
        userDao().deleteById(userId)
    }

    companion object {
        private const val DB_NAME = "eventfinder.db"

        /** Build the database instance. Room is a singleton to avoid leaks. */
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DB_NAME
            )
                .build()
    }
}

/** Maps a Room user row onto the immutable domain model. */
fun UserEntity.toDomain(): User = User(
    id = id,
    fullName = fullName,
    email = email,
    preferredLanguage = preferredLanguage,
    defaultCity = defaultCity,
    defaultRadiusKm = defaultRadiusKm,
    biometricEnabled = biometricEnabled,
    createdAt = createdAt
)

/** Maps a Room event row onto the immutable domain model. */
fun EventEntity.toDomain(): Event = Event(
    id = id,
    title = title,
    description = description,
    category = com.eventfinder.app.domain.model.EventCategory.fromLabelKey(category),
    startDate = startDate,
    endDate = endDate,
    venueName = venueName,
    address = address,
    latitude = latitude,
    longitude = longitude,
    imageUrl = imageUrl,
    isPublic = isPublic,
    organizerId = organizerId,
    organizerName = organizerName,
    attendeeCount = attendeeCount,
    isFavorite = isFavorite,
    isCreatedByUser = isCreatedByUser,
    isSynced = isSynced
)