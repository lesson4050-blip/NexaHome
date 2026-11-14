package com.orbit.tv.launcher.features.home.model

import androidx.annotation.StringRes

data class GenreFilter(
    val id: String,
    @StringRes val nameRes: Int,
    val tmdbGenreId: Int? = null,
    val isSeries: Boolean = false
)
