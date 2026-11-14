package com.orbit.tv.launcher.features.home.data

import android.content.Context
import com.orbit.tv.launcher.features.home.model.MediaModel
import com.orbit.tv.launcher.features.iptv.data.OrbitDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class WatchHistoryRepository(context: Context) {

    private val dao = OrbitDatabase.getInstance(context).watchHistoryDao()

    val recentMedia: Flow<List<MediaModel>> = dao.getRecentHistory().map { list ->
        list.map { entity ->
            val appBadge = if (!entity.targetApp.isNullOrBlank()) {
                "${entity.badge} • ${entity.targetApp}"
            } else {
                entity.badge
            }
            MediaModel(
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
