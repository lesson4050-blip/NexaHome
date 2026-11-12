package com.orbit.tv.launcher.features.home.model

import android.content.Context
import com.orbit.tv.launcher.R

/**
 * Model representing a Movie or TV Series card in recommendation rows.
 */
data class MediaModel(
    val id: String,
    val title: String,
    val posterUrl: String,
    val badge: String,
    val genre: String = "",
    val rawGenres: List<String> = emptyList(),
    val rating: String = "",
    val description: String = "",
    val releaseYear: String = "",
    val isSeries: Boolean = false,
    val deeplinkUri: String? = null
) {
    /**
     * Resolves genres dynamically to the current system locale, preventing stale language caching.
     */
    fun getLocalizedGenre(context: Context): String {
        val list = if (rawGenres.isNotEmpty()) {
            rawGenres
        } else {
            genre.split(",").map { it.trim() }
        }

        val genreMap = mapOf(
            "Action" to context.getString(R.string.genre_action),
            "Бойовик" to context.getString(R.string.genre_action),
            "Боевик" to context.getString(R.string.genre_action),
            "Adventure" to context.getString(R.string.genre_adventure),
            "Пригоди" to context.getString(R.string.genre_adventure),
            "Приключения" to context.getString(R.string.genre_adventure),
            "Animation" to context.getString(R.string.genre_animation),
            "Анімація" to context.getString(R.string.genre_animation),
            "Мультфильм" to context.getString(R.string.genre_animation),
            "Biography" to context.getString(R.string.genre_biography),
            "Біографія" to context.getString(R.string.genre_biography),
            "Биография" to context.getString(R.string.genre_biography),
            "Comedy" to context.getString(R.string.genre_comedy),
            "Комедія" to context.getString(R.string.genre_comedy),
            "Комедия" to context.getString(R.string.genre_comedy),
            "Crime" to context.getString(R.string.genre_crime),
            "Кримінал" to context.getString(R.string.genre_crime),
            "Криминал" to context.getString(R.string.genre_crime),
            "Documentary" to context.getString(R.string.genre_documentary),
            "Документальний" to context.getString(R.string.genre_documentary),
            "Документальный" to context.getString(R.string.genre_documentary),
            "Drama" to context.getString(R.string.genre_drama),
            "Драма" to context.getString(R.string.genre_drama),
            "Family" to context.getString(R.string.genre_family),
            "Сімейний" to context.getString(R.string.genre_family),
            "Семейный" to context.getString(R.string.genre_family),
            "Fantasy" to context.getString(R.string.genre_fantasy),
            "Фентезі" to context.getString(R.string.genre_fantasy),
            "Фэнтези" to context.getString(R.string.genre_fantasy),
            "History" to context.getString(R.string.genre_history),
            "Історичний" to context.getString(R.string.genre_history),
            "Исторический" to context.getString(R.string.genre_history),
            "Horror" to context.getString(R.string.genre_horror),
            "Жахи" to context.getString(R.string.genre_horror),
            "Ужасы" to context.getString(R.string.genre_horror),
            "Music" to context.getString(R.string.genre_music),
            "Музичний" to context.getString(R.string.genre_music),
            "Музыка" to context.getString(R.string.genre_music),
            "Musical" to context.getString(R.string.genre_musical),
            "Мюзикл" to context.getString(R.string.genre_musical),
            "Mystery" to context.getString(R.string.genre_mystery),
            "Детектив" to context.getString(R.string.genre_mystery),
            "Romance" to context.getString(R.string.genre_romance),
            "Мелодрама" to context.getString(R.string.genre_romance),
            "Sci-Fi" to context.getString(R.string.genre_scifi),
            "Фантастика" to context.getString(R.string.genre_scifi),
            "Sport" to context.getString(R.string.genre_sport),
            "Спорт" to context.getString(R.string.genre_sport),
            "Thriller" to context.getString(R.string.genre_thriller),
            "Трилер" to context.getString(R.string.genre_thriller),
            "Триллер" to context.getString(R.string.genre_thriller),
            "War" to context.getString(R.string.genre_war),
            "Військовий" to context.getString(R.string.genre_war),
            "Военный" to context.getString(R.string.genre_war),
            "Western" to context.getString(R.string.genre_western),
            "Вестерн" to context.getString(R.string.genre_western)
        )

        val translated = list.map { genreMap[it] ?: it }.filter { it.isNotBlank() }.joinToString(", ")
        return translated.ifBlank { genre.ifBlank { context.getString(R.string.genre_feature) } }
    }
}
