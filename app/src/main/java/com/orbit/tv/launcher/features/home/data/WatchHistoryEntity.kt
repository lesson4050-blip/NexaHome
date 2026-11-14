package com.orbit.tv.launcher.features.home.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "watch_history")
data class WatchHistoryEntity(
    @PrimaryKey val id: String,
    val title: String,
    val posterUrl: String,
    val badge: String,
    val genre: String = "",
    val rating: String = "",
    val description: String = "",
    val releaseYear: String = "",
    val isSeries: Boolean = false,
    val deeplinkUri: String? = null,
    val targetApp: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
