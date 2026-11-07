package com.orbit.tv.launcher.features.home.model

import android.graphics.drawable.Drawable

/**
 * Model representing an installed TV-capable application.
 */
data class AppModel(
    val packageName: String,
    val label: String,
    val iconDrawable: Drawable?,
    val isGame: Boolean = false,
    val launchIntent: android.content.Intent? = null,
    val isPinned: Boolean = false,
    val isHidden: Boolean = false
)
