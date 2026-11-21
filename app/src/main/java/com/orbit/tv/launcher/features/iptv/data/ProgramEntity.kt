package com.orbit.tv.launcher.features.iptv.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "programs",
    indices = [
        Index(value = ["channelId", "startTime"]),
        Index(value = ["endTime"])
    ]
)
data class ProgramEntity(
    @PrimaryKey
    val programId: String,
    val channelId: String,
    val title: String,
    val description: String?,
    val startTime: Long,
    val endTime: Long,
    val timeRange: String
)

