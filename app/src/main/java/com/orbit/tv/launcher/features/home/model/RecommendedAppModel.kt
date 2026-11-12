package com.orbit.tv.launcher.features.home.model

import android.graphics.drawable.Drawable

data class RecommendedAppModel(
    val packageName: String,
    val name: String,
    val category: String,
    val description: String,
    val iconRes: Int,
    val isInstalled: Boolean = false,
    val installedIcon: Drawable? = null
)
