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

 /**
  * Room database migration from v5 to v6.
  *
  * Makes latitude/longitude nullable to properly represent events without
  * location data (instead of using 0.0 sentinel in Gulf of Guinea).
  */
private val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS events_new (id TEXT NOT NULL, title TEXT NOT NULL, description TEXT NOT NULL, category TEXT NOT NULL, startDate INTEGER NOT NULL, endDate INTEGER NOT NULL, venueName TEXT NOT NULL, address TEXT NOT NULL, latitude REAL, longitude REAL, imageUrl TEXT, isPublic INTEGER NOT NULL, organizerId TEXT NOT NULL, organizerName TEXT NOT NULL, attendeeCount INTEGER NOT NULL, isCreatedByUser INTEGER NOT NULL, PRIMARY KEY(id))")
        db.execSQL("INSERT INTO events_new (id, title, description, category, startDate, endDate, venueName, address, latitude, longitude, imageUrl, isPublic, organizerId, organizerName, attendeeCount, isCreatedByUser) SELECT id, title, description, category, startDate, endDate, venueName, address, latitude, longitude, imageUrl, isPublic, organizerId, organizerName, attendeeCount, isCreatedByUser FROM events")
        db.execSQL("DROP TABLE events")
        db.execSQL("ALTER TABLE events_new RENAME TO events")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_events_category ON events(category)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_events_startDate ON events(startDate)")
    }
}

 /**
 * Room database migration from v6 to v7.
 *
 * Adds two bookkeeping tables that are entirely additive and do not touch the
 * existing events/favorites/rsvps/users schema:
 *  - source_sync: per-source discovery timestamps (success/empty/failure) so
 *    the event-feed cache can expire stale source data instead of keeping it
 *    forever.
 *  - geocode_cache: persisted geocoding lookups so feeds restarting the app do
 *    not re-issue Open-Meteo requests for previously resolved locations.
 */
private val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS source_sync " +
                "(sourceId TEXT NOT NULL, lastSuccessAt INTEGER NOT NULL, " +
                "lastEmptyAt INTEGER NOT NULL, lastFailedAt INTEGER NOT NULL, " +
                "PRIMARY KEY(sourceId))"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS geocode_cache " +
                "(locationKey TEXT NOT NULL, latitude REAL NOT NULL, " +
                "longitude REAL NOT NULL, createdAt INTEGER NOT NULL, " +
                "PRIMARY KEY(locationKey))"
        )
    }
}

private val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE users ADD COLUMN serverBacked INTEGER NOT NULL DEFAULT 0")
    }
}

private val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE users ADD COLUMN avatarType TEXT NOT NULL DEFAULT 'initials'")
    }
}

private val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS event_reviews (" +
                "eventId TEXT NOT NULL, userId TEXT NOT NULL, rating INTEGER NOT NULL, " +
                "comment TEXT NOT NULL, authorName TEXT NOT NULL, createdAt INTEGER NOT NULL, " +
                "PRIMARY KEY(eventId, userId))"
        )
    }
}

private val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE events ADD COLUMN sourceUrl TEXT")
        db.execSQL("ALTER TABLE events ADD COLUMN maxAttendees INTEGER")
    }
}

private val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE users ADD COLUMN avatarUrl TEXT")
        db.execSQL("ALTER TABLE users ADD COLUMN avatarImageUrl TEXT")
    }
}

private val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE users ADD COLUMN isVerified INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE users ADD COLUMN trustScore REAL NOT NULL DEFAULT 0.0")
    }
}

private val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS group_plans " +
            "(id TEXT NOT NULL, eventId TEXT NOT NULL, creatorId TEXT NOT NULL, " +
            "members TEXT NOT NULL, meetingPointLabel TEXT, meetingLat REAL, " +
            "meetingLng REAL, proposedTime INTEGER, createdAt INTEGER NOT NULL, " +
            "PRIMARY KEY(id))"
        )
    }
}

private val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS tickets " +
            "(id TEXT NOT NULL, eventId TEXT NOT NULL, userId TEXT NOT NULL, " +
            "ticketCode TEXT NOT NULL, issuedAt INTEGER NOT NULL, checkedInAt INTEGER, " +
            "PRIMARY KEY(id))"
        )
    }
}

private val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE events ADD COLUMN wheelchairAccessible INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE events ADD COLUMN loudMusic INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE events ADD COLUMN sensoryFriendly INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE events ADD COLUMN strobeLights INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE users ADD COLUMN commuteTimeMinutes INTEGER NOT NULL DEFAULT 30")
        db.execSQL("ALTER TABLE users ADD COLUMN preferredCategories TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE users ADD COLUMN skipReminders INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE users ADD COLUMN accessibilityNeeds TEXT NOT NULL DEFAULT ''")
    }
}

private val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE event_reviews ADD COLUMN verifiedAttendance INTEGER NOT NULL DEFAULT 0")
    }
}

private val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE events ADD COLUMN provenance TEXT NOT NULL DEFAULT 'live'")
        db.execSQL("ALTER TABLE events ADD COLUMN ageRestricted INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE events ADD COLUMN isRecurring INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE events ADD COLUMN recurrenceRule TEXT")
        db.execSQL("ALTER TABLE events ADD COLUMN parentEventId TEXT")
        db.execSQL("ALTER TABLE events ADD COLUMN collectionId TEXT")
    }
}

private val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS reports " +
            "(id TEXT NOT NULL, reporterId TEXT NOT NULL, targetType TEXT NOT NULL, " +
            "targetId TEXT NOT NULL, reason TEXT NOT NULL, createdAt INTEGER NOT NULL, " +
            "resolved INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(id))"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS blocked_users " +
            "(userId TEXT NOT NULL, blockedUserId TEXT NOT NULL, createdAt INTEGER NOT NULL, " +
            "PRIMARY KEY(userId, blockedUserId))"
        )
    }
}

private val MIGRATION_19_20 = object : Migration(19, 20) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE events ADD COLUMN version INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE events ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS event_drafts " +
            "(id TEXT NOT NULL, userId TEXT NOT NULL, title TEXT NOT NULL, " +
            "description TEXT NOT NULL, category TEXT NOT NULL, venueName TEXT NOT NULL, " +
            "address TEXT NOT NULL, latitude REAL, longitude REAL, imageUrl TEXT, " +
            "startDate INTEGER NOT NULL, endDate INTEGER NOT NULL, savedAt INTEGER NOT NULL, " +
            "PRIMARY KEY(id))"
        )
    }
}

  @Database(
    entities = [
        UserEntity::class,
        EventEntity::class,
        FavoriteEntity::class,
        RsvpEntity::class,
        SourceSyncEntity::class,
        GeocodeCacheEntity::class,
        EventReviewEntity::class,
        GroupPlanEntity::class,
        TicketEntity::class,
        ReportEntity::class,
        BlockedUserEntity::class,
        DraftEntity::class
    ],
    version = 20,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase(), DatabaseTransactionHelper {

    abstract fun userDao(): UserDao
    abstract fun eventDao(): EventDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun rsvpDao(): RsvpDao
    abstract fun geocodeCacheDao(): GeocodeCacheDao
    abstract fun eventReviewDao(): EventReviewDao
    abstract fun groupPlanDao(): GroupPlanDao
    abstract fun ticketDao(): TicketDao
    abstract fun reportDao(): ReportDao
    abstract fun draftDao(): DraftDao



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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20)
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
    createdAt = createdAt,
    avatarType = avatarType,
    avatarUrl = avatarUrl,
    avatarImageUrl = avatarImageUrl,
    commuteTimeMinutes = commuteTimeMinutes,
    preferredCategories = preferredCategories,
    skipReminders = skipReminders,
    accessibilityNeeds = accessibilityNeeds
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
        sourceUrl = sourceUrl,
        maxAttendees = maxAttendees,
        isPublic = isPublic,
        organizerId = organizerId,
        organizerName = organizerName,
        attendeeCount = attendeeCount,
        isFavorite = false,
        isCreatedByUser = isCreatedByUser,
        wheelchairAccessible = wheelchairAccessible,
        loudMusic = loudMusic,
        sensoryFriendly = sensoryFriendly,
        strobeLights = strobeLights,
        provenance = provenance,
        ageRestricted = ageRestricted,
        isRecurring = isRecurring,
        recurrenceRule = recurrenceRule,
        parentEventId = parentEventId,
        collectionId = collectionId,
        version = version,
        updatedAt = updatedAt
    )
