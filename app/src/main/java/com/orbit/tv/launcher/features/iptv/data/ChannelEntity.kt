package com.orbit.tv.launcher.features.iptv.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "channels",
    indices = [
        Index(value = ["isFavorite", "number"]),
        Index(value = ["groupTitle"])
    ]
)
data class ChannelEntity(
    @PrimaryKey
    val id: String,
    val number: Int,
    val name: String,
    val logoUrl: String?,
    val streamUrl: String,
    val groupTitle: String,
    val isFavorite: Boolean = false,
    val lastWatchedTimestamp: Long = 0L
)

