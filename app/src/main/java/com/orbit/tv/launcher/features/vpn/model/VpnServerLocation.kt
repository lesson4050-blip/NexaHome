package com.orbit.tv.launcher.features.vpn.model

data class VpnServerLocation(
    val code: String,
    val flag: String,
    val country: String,
    val city: String,
    val pingMs: Int
)
