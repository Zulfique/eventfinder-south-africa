package com.eventfinder.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.eventfinder.app.domain.model.User

interface DatabaseTransactionHelper {
    /** Atomically inserts or removes a favourite. */
    suspend fun setFavoriteAtomically(userId: String, eventId: String, favorite: Boolean)
    /** Atomically upserts an RSVP. */
    suspend fun setRsvpAtomically(userId: String, eventId: String, status: String)
    /** Atomically creates an event. */
    suspend fun createEventAtomically(event: EventEntity)
    /** Atomically updates an event. */
    suspend fun updateEventAtomically(event: EventEntity)
    /** Atomically deletes an event, its favourite/RSVP links. */
    suspend fun deleteEventAtomically(eventId: String)
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
 * records (favorites, RSVPs) are retained under a 'legacy' owner.
 */
private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS favorites_new (userId TEXT NOT NULL, eventId TEXT NOT NULL, createdAt INTEGER NOT NULL, isSynced INTEGER NOT NULL, PRIMARY KEY(userId, eventId))")
        db.execSQL("INSERT INTO favorites_new (userId, eventId, createdAt, isSynced) SELECT 'legacy', eventId, createdAt, isSynced FROM favorites")
        db.execSQL("DROP TABLE favorites")
        db.execSQL("ALTER TABLE favorites_new RENAME TO favorites")

        db.execSQL("CREATE TABLE IF NOT EXISTS rsvps_new (userId TEXT NOT NULL, eventId TEXT NOT NULL, status TEXT NOT NULL, createdAt INTEGER NOT NULL, isSynced INTEGER NOT NULL, PRIMARY KEY(userId, eventId))")
        db.execSQL("INSERT INTO rsvps_new (userId, eventId, status, createdAt, isSynced) SELECT 'legacy', eventId, status, createdAt, isSynced FROM rsvps")
        db.execSQL("DROP TABLE rsvps")
        db.execSQL("ALTER TABLE rsvps_new RENAME TO rsvps")

        db.execSQL("ALTER TABLE pending_sync ADD COLUMN userId TEXT NOT NULL DEFAULT ''")
    }
}

/**
 * Room database migration from v2 to v3.
 *
 * Drops the dead `isFavorite` column from the events table.
 */
private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS events_new (id TEXT NOT NULL, title TEXT NOT NULL, description TEXT NOT NULL, category TEXT NOT NULL, startDate INTEGER NOT NULL, endDate INTEGER NOT NULL, venueName TEXT NOT NULL, address TEXT NOT NULL, latitude REAL NOT NULL, longitude REAL NOT NULL, imageUrl TEXT, isPublic INTEGER NOT NULL, organizerId TEXT NOT NULL, organizerName TEXT NOT NULL, attendeeCount INTEGER NOT NULL, isCreatedByUser INTEGER NOT NULL, isSynced INTEGER NOT NULL, PRIMARY KEY(id))")
        db.execSQL("INSERT INTO events_new (id, title, description, category, startDate, endDate, venueName, address, latitude, longitude, imageUrl, isPublic, organizerId, organizerName, attendeeCount, isCreatedByUser, isSynced) SELECT id, title, description, category, startDate, endDate, venueName, address, latitude, longitude, imageUrl, isPublic, organizerId, organizerName, attendeeCount, isCreatedByUser, isSynced FROM events")
        db.execSQL("DROP TABLE events")
        db.execSQL("ALTER TABLE events_new RENAME TO events")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_events_category ON events(category)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_events_startDate ON events(startDate)")
    }
}

/**
 * Room database migration from v3 to v4.
 *
 * Renames columns from the original cloud-sync design:
 *  - events.isSynced -> events.isExternal
 *  - favorites.isSynced -> favorites.isFlushed
 *  - rsvps.isSynced -> rsvps.isFlushed
 */
private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS events_new (id TEXT NOT NULL, title TEXT NOT NULL, description TEXT NOT NULL, category TEXT NOT NULL, startDate INTEGER NOT NULL, endDate INTEGER NOT NULL, venueName TEXT NOT NULL, address TEXT NOT NULL, latitude REAL NOT NULL, longitude REAL NOT NULL, imageUrl TEXT, isPublic INTEGER NOT NULL, organizerId TEXT NOT NULL, organizerName TEXT NOT NULL, attendeeCount INTEGER NOT NULL, isCreatedByUser INTEGER NOT NULL, isExternal INTEGER NOT NULL, PRIMARY KEY(id))")
        db.execSQL("INSERT INTO events_new (id, title, description, category, startDate, endDate, venueName, address, latitude, longitude, imageUrl, isPublic, organizerId, organizerName, attendeeCount, isCreatedByUser, isExternal) SELECT id, title, description, category, startDate, endDate, venueName, address, latitude, longitude, imageUrl, isPublic, organizerId, organizerName, attendeeCount, isCreatedByUser, isSynced FROM events")
        db.execSQL("DROP TABLE events")
        db.execSQL("ALTER TABLE events_new RENAME TO events")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_events_category ON events(category)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_events_startDate ON events(startDate)")

        db.execSQL("CREATE TABLE IF NOT EXISTS favorites_new (userId TEXT NOT NULL, eventId TEXT NOT NULL, createdAt INTEGER NOT NULL, isFlushed INTEGER NOT NULL, PRIMARY KEY(userId, eventId))")
        db.execSQL("INSERT INTO favorites_new (userId, eventId, createdAt, isFlushed) SELECT userId, eventId, createdAt, isSynced FROM favorites")
        db.execSQL("DROP TABLE favorites")
        db.execSQL("ALTER TABLE favorites_new RENAME TO favorites")

        db.execSQL("CREATE TABLE IF NOT EXISTS rsvps_new (userId TEXT NOT NULL, eventId TEXT NOT NULL, status TEXT NOT NULL, createdAt INTEGER NOT NULL, isFlushed INTEGER NOT NULL, PRIMARY KEY(userId, eventId))")
        db.execSQL("INSERT INTO rsvps_new (userId, eventId, status, createdAt, isFlushed) SELECT userId, eventId, status, createdAt, isSynced FROM rsvps")
        db.execSQL("DROP TABLE rsvps")
        db.execSQL("ALTER TABLE rsvps_new RENAME TO rsvps")
    }
}

/**
 * Room database migration from v4 to v5.
 *
 * Removes legacy cloud-sync columns now that there is no backend:
 *  - events: drops isExternal (was always false)
 *  - favorites: drops isFlushed (no journal to reconcile)
 *  - rsvps: drops isFlushed
 *  - drops pending_sync table entirely (no pending-operation journal)
 */
private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS events_new (id TEXT NOT NULL, title TEXT NOT NULL, description TEXT NOT NULL, category TEXT NOT NULL, startDate INTEGER NOT NULL, endDate INTEGER NOT NULL, venueName TEXT NOT NULL, address TEXT NOT NULL, latitude REAL NOT NULL, longitude REAL NOT NULL, imageUrl TEXT, isPublic INTEGER NOT NULL, organizerId TEXT NOT NULL, organizerName TEXT NOT NULL, attendeeCount INTEGER NOT NULL, isCreatedByUser INTEGER NOT NULL, PRIMARY KEY(id))")
        db.execSQL("INSERT INTO events_new (id, title, description, category, startDate, endDate, venueName, address, latitude, longitude, imageUrl, isPublic, organizerId, organizerName, attendeeCount, isCreatedByUser) SELECT id, title, description, category, startDate, endDate, venueName, address, latitude, longitude, imageUrl, isPublic, organizerId, organizerName, attendeeCount, isCreatedByUser FROM events")
        db.execSQL("DROP TABLE events")
        db.execSQL("ALTER TABLE events_new RENAME TO events")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_events_category ON events(category)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_events_startDate ON events(startDate)")

        db.execSQL("CREATE TABLE IF NOT EXISTS favorites_new (userId TEXT NOT NULL, eventId TEXT NOT NULL, createdAt INTEGER NOT NULL, PRIMARY KEY(userId, eventId))")
        db.execSQL("INSERT INTO favorites_new (userId, eventId, createdAt) SELECT userId, eventId, createdAt FROM favorites")
        db.execSQL("DROP TABLE favorites")
        db.execSQL("ALTER TABLE favorites_new RENAME TO favorites")

        db.execSQL("CREATE TABLE IF NOT EXISTS rsvps_new (userId TEXT NOT NULL, eventId TEXT NOT NULL, status TEXT NOT NULL, createdAt INTEGER NOT NULL, PRIMARY KEY(userId, eventId))")
        db.execSQL("INSERT INTO rsvps_new (userId, eventId, status, createdAt) SELECT userId, eventId, status, createdAt FROM rsvps")
        db.execSQL("DROP TABLE rsvps")
        db.execSQL("ALTER TABLE rsvps_new RENAME TO rsvps")

        db.execSQL("DROP TABLE IF EXISTS pending_sync")
    }
}

@Database(
    entities = [
        UserEntity::class,
        EventEntity::class,
        FavoriteEntity::class,
        RsvpEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase(), DatabaseTransactionHelper {

    abstract fun userDao(): UserDao
    abstract fun eventDao(): EventDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun rsvpDao(): RsvpDao

    @Transaction
    override suspend fun setFavoriteAtomically(userId: String, eventId: String, favorite: Boolean) {
        if (favorite) {
            favoriteDao().insert(
                FavoriteEntity(userId = userId, eventId = eventId, createdAt = System.currentTimeMillis())
            )
        } else {
            favoriteDao().delete(userId, eventId)
        }
    }

    @Transaction
    override suspend fun setRsvpAtomically(userId: String, eventId: String, status: String) {
        rsvpDao().upsert(
            RsvpEntity(userId = userId, eventId = eventId, status = status, createdAt = System.currentTimeMillis())
        )
    }

    @Transaction
    override suspend fun createEventAtomically(event: EventEntity) {
        eventDao().upsert(event)
    }

    @Transaction
    override suspend fun updateEventAtomically(event: EventEntity) {
        eventDao().upsert(event)
    }

    @Transaction
    override suspend fun deleteAccountData(userId: String) {
        favoriteDao().deleteAllForUser(userId)
        rsvpDao().deleteAllForUser(userId)
        eventDao().deleteCreatedByUserId(userId)
        userDao().deleteById(userId)
    }

    @Transaction
    override suspend fun deleteEventAtomically(eventId: String) {
        favoriteDao().deleteAllForEvent(eventId)
        rsvpDao().deleteAllForEvent(eventId)
        eventDao().deleteById(eventId)
    }

    companion object {
        private const val DB_NAME = "eventfinder.db"

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DB_NAME
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
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
fun EventEntity.toDomain(): com.eventfinder.app.domain.model.Event =
    com.eventfinder.app.domain.model.Event(
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
        isFavorite = false,
        isCreatedByUser = isCreatedByUser
    )
