package com.orbit.tv.launcher.features.vpn.model

import android.graphics.drawable.Drawable

data class VpnAppInfo(
    val name: String,
    val packageName: String,
    val icon: Drawable? = null,
    val isInstalled: Boolean = false,
    val version: String? = null
)
