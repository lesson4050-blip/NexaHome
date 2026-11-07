package com.orbit.tv.launcher.features.iptv.model

data class IptvChannel(
    val id: String,
    val number: Int,
    val name: String,
    val logoUrl: String?,
    val streamUrl: String,
    val group: String,
    val currentProgram: String,
    val nextProgram: String = "Upcoming Broadcast",
    val timeRange: String = "LIVE NOW",
    val progressPercent: Int = 45
)
