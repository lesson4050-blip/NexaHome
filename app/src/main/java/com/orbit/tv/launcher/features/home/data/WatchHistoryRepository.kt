package com.orbit.tv.launcher.features.home.data

import android.content.Context
import com.orbit.tv.launcher.features.home.model.MediaModel
import com.orbit.tv.launcher.features.iptv.data.OrbitDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class WatchHistoryRepository(private val context: Context) {

    private val dao = OrbitDatabase.getInstance(context).watchHistoryDao()
    private val mediaRepository = com.orbit.tv.launcher.features.home.repository.MediaRepository(context)

    val recentMedia: Flow<List<MediaModel>> = dao.getRecentHistory().map { list ->
        list.map { entity ->
            val targetAppLabel = when (entity.targetApp) {
                "Trailer" -> context.getString(com.orbit.tv.launcher.R.string.media_btn_trailer)
                null, "" -> null
                else -> entity.targetApp
            }
            val appBadge = if (!targetAppLabel.isNullOrBlank()) {
                "${entity.badge} • $targetAppLabel"
            } else {
                entity.badge
            }
            val baseModel = MediaModel(
                id = entity.id,
                title = entity.title,
                posterUrl = entity.posterUrl,
                badge = appBadge,
                genre = entity.genre,
                rating = entity.rating,
                description = entity.description,
                releaseYear = entity.releaseYear,
                isSeries = entity.isSeries,
                deeplinkUri = entity.deeplinkUri
            )
            mediaRepository.resolveLocalizedMedia(baseModel)
        }
    }

    suspend fun recordWatched(media: MediaModel, targetAppName: String? = null) {
        val entity = WatchHistoryEntity(
            id = media.id,
            title = media.title,
            posterUrl = media.posterUrl,
            badge = media.badge,
            genre = media.genre,
            rating = media.rating,
            description = media.description,
            releaseYear = media.releaseYear,
            isSeries = media.isSeries,
            deeplinkUri = media.deeplinkUri,
            targetApp = targetAppName,
            timestamp = System.currentTimeMillis()
        )
        dao.record(entity)
    }

    suspend fun deleteById(id: String) {
        dao.deleteById(id)
    }

    suspend fun clearHistory() {
        dao.clearAll()
    }
}
