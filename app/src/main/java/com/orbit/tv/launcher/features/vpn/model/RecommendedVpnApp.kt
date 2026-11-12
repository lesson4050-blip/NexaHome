package com.orbit.tv.launcher.features.vpn.model

data class RecommendedVpnApp(
    val packageName: String,
    val name: String,
    val description: String,
    val isInstalled: Boolean = false
)
