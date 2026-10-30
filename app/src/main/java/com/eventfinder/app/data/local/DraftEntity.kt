package com.eventfinder.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "event_drafts")
data class DraftEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val title: String,
    val description: String,
    val category: String,
    val venueName: String,
    val address: String,
    val latitude: Double?,
    val longitude: Double?,
    val imageUrl: String?,
    val startDate: Long,
    val endDate: Long,
    val savedAt: Long
)