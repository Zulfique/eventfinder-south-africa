package com.eventfinder.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for a registered user. Mirrors the `UserEntity` model from the
 * Part 1 design document §7.1. The password is stored as a salted PBKDF2 hash.
 */
@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val fullName: String,
    val email: String,
    val passwordHash: String,
    val preferredLanguage: String,
    val defaultCity: String,
    val defaultRadiusKm: Int,
    val biometricEnabled: Boolean,
    val createdAt: Long
)

/**
 * Room entity for an event. Mirrors `EventEntity` from §7.1 including the
 * favourite + sync flags needed for the offline-first prototype behaviour.
 */
@Entity(
    tableName = "events",
    indices = [Index(value = ["category"]), Index(value = ["startDate"])]
)
data class EventEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val category: String,
    val startDate: Long,
    val endDate: Long,
    val venueName: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val imageUrl: String?,
    val isPublic: Boolean,
    val organizerId: String,
    val organizerName: String,
    val attendeeCount: Int,
    val isCreatedByUser: Boolean,
    val isSynced: Boolean
)

/** Room entity for a favourite link (schema §5.3 / §7.1). */
@Entity(
    tableName = "favorites",
    primaryKeys = ["userId", "eventId"]
)
data class FavoriteEntity(
    val userId: String,
    val eventId: String,
    val createdAt: Long,
    val isSynced: Boolean
)

/** Room entity for an RSVP link (schema §5.3). */
@Entity(
    tableName = "rsvps",
    primaryKeys = ["userId", "eventId"]
)
data class RsvpEntity(
    val userId: String,
    val eventId: String,
    val status: String,
    val createdAt: Long,
    val isSynced: Boolean
)

/**
 * Local durable operation journal. Each row records a mutation (event create /
 * update / delete, favourite toggle, RSVP toggle) that has not yet been
 * reconciled with the local sync flags. The queue is drained on startup by
 * [EventRepositoryImpl.flushPendingActions], which marks the corresponding
 * entity as `isSynced` and removes the journal entry.
 */
@Entity(tableName = "pending_sync")
data class PendingSyncEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entityType: String,
    val entityId: String,
    val action: String,
    val payload: String,
    val userId: String,
    val createdAt: Long,
    val retryCount: Int = 0
)