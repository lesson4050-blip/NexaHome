package com.orbit.tv.launcher.features.home.model

/**
 * Model representing a Movie or TV Series card in recommendation rows.
 */
data class MediaModel(
    val id: String,
    val title: String,
    val posterUrl: String,
    val badge: String,
    val genre: String = "",
    val rating: String = "",
    val isSeries: Boolean = false,
    val deeplinkUri: String? = null
)
