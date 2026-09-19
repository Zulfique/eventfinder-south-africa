package com.eventfinder.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.eventfinder.app.domain.model.Event
import com.eventfinder.app.domain.model.User

/**
 * Transactional operations that the repository needs. Extracted into an
 * interface so tests can provide fakes without Room dependencies.
 */
interface DatabaseTransactionHelper {
    /** Atomically inserts/removes a favourite. */
    suspend fun setFavorite(userId: String, eventId: String, favorite: Boolean)
    /** Atomically deletes all user data during account deletion. */
    suspend fun deleteAccountData(userId: String)
}

/**
 * Room database migration from v1 (single-user) to v2 (multi-user).
 *
 * Adds userId columns to favorites, rsvps, and pending_sync tables.
 * Changes favorites and rsvps primary keys from (eventId) to (userId, eventId).
 *
 * Legacy rows: v1 did not store account ownership, so existing relationship
 * records (favorites, RSVPs) are retained under a 'legacy' owner. These rows
 * are effectively orphaned — no logged-in user can query them — but they are
 * preserved to avoid data loss during the schema upgrade.
 */
private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // favorites: recreate with composite PK
        db.execSQL("CREATE TABLE IF NOT EXISTS favorites_new (userId TEXT NOT NULL, eventId TEXT NOT NULL, createdAt INTEGER NOT NULL, isSynced INTEGER NOT NULL, PRIMARY KEY(userId, eventId))")
        db.execSQL("INSERT INTO favorites_new (userId, eventId, createdAt, isSynced) SELECT 'legacy', eventId, createdAt, isSynced FROM favorites")
        db.execSQL("DROP TABLE favorites")
        db.execSQL("ALTER TABLE favorites_new RENAME TO favorites")

        // rsvps: recreate with composite PK
        db.execSQL("CREATE TABLE IF NOT EXISTS rsvps_new (userId TEXT NOT NULL, eventId TEXT NOT NULL, status TEXT NOT NULL, createdAt INTEGER NOT NULL, isSynced INTEGER NOT NULL, PRIMARY KEY(userId, eventId))")
        db.execSQL("INSERT INTO rsvps_new (userId, eventId, status, createdAt, isSynced) SELECT 'legacy', eventId, status, createdAt, isSynced FROM rsvps")
        db.execSQL("DROP TABLE rsvps")
        db.execSQL("ALTER TABLE rsvps_new RENAME TO rsvps")

        // pending_sync: add userId column
        db.execSQL("ALTER TABLE pending_sync ADD COLUMN userId TEXT NOT NULL DEFAULT ''")
    }
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
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase(), DatabaseTransactionHelper {

    abstract fun userDao(): UserDao
    abstract fun eventDao(): EventDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun rsvpDao(): RsvpDao
    abstract fun pendingSyncDao(): PendingSyncDao

    override suspend fun setFavorite(userId: String, eventId: String, favorite: Boolean) {
        if (favorite) {
            favoriteDao().insert(
                FavoriteEntity(userId = userId, eventId = eventId, createdAt = System.currentTimeMillis(), isSynced = false)
            )
        } else {
            favoriteDao().delete(userId, eventId)
        }
    }

    override suspend fun deleteAccountData(userId: String) {
        pendingSyncDao().deleteAllForUser(userId)
        favoriteDao().deleteAllForUser(userId)
        rsvpDao().deleteAllForUser(userId)
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
                .addMigrations(MIGRATION_1_2)
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
