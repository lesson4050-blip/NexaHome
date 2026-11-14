package com.orbit.tv.launcher.features.home.model

import android.graphics.drawable.Drawable

data class MediaPlayerApp(
    val packageName: String,
    val name: String,
    val icon: Drawable?,
    val categoryLabel: String,
    val isSystemSearch: Boolean = false
)
