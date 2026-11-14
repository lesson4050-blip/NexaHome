package com.orbit.tv.launcher.platform.tvprovider

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.media.tv.TvContract
import android.net.Uri
import android.util.Log

data class WatchNextItem(
    val id: Long,
    val title: String,
    val episodeTitle: String?,
    val posterUri: String?,
    val positionMillis: Long,
    val durationMillis: Long,
    val intentUri: String?,
    val packageName: String?,
    val appName: String
) {
    val progressPercent: Int
        get() = if (durationMillis > 0) {
            ((positionMillis.toDouble() / durationMillis) * 100).toInt().coerceIn(0, 100)
        } else 0
}

/**
 * Accesses standard Android TV 'Watch Next' programs populated by SmartTube, VLC, Kodi, and streaming apps.
 */
class WatchNextRepository(private val context: Context) {

    private val packageManager = context.packageManager

    fun getWatchNextPrograms(): List<WatchNextItem> {
        val list = mutableListOf<WatchNextItem>()
        val uri = TvContract.WatchNextPrograms.CONTENT_URI

        val projection = arrayOf(
            TvContract.WatchNextPrograms._ID,
            TvContract.WatchNextPrograms.COLUMN_TITLE,
            TvContract.WatchNextPrograms.COLUMN_EPISODE_TITLE,
            TvContract.WatchNextPrograms.COLUMN_POSTER_ART_URI,
            TvContract.WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS,
            TvContract.WatchNextPrograms.COLUMN_DURATION_MILLIS,
            TvContract.WatchNextPrograms.COLUMN_INTENT_URI,
            TvContract.WatchNextPrograms.COLUMN_PACKAGE_NAME
        )

        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                "${TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS} DESC"
            )

            if (cursor != null && cursor.moveToFirst()) {
                val idIdx = cursor.getColumnIndex(TvContract.WatchNextPrograms._ID)
                val titleIdx = cursor.getColumnIndex(TvContract.WatchNextPrograms.COLUMN_TITLE)
                val episodeIdx = cursor.getColumnIndex(TvContract.WatchNextPrograms.COLUMN_EPISODE_TITLE)
                val posterIdx = cursor.getColumnIndex(TvContract.WatchNextPrograms.COLUMN_POSTER_ART_URI)
                val posIdx = cursor.getColumnIndex(TvContract.WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS)
                val durIdx = cursor.getColumnIndex(TvContract.WatchNextPrograms.COLUMN_DURATION_MILLIS)
                val intentIdx = cursor.getColumnIndex(TvContract.WatchNextPrograms.COLUMN_INTENT_URI)
                val pkgIdx = cursor.getColumnIndex(TvContract.WatchNextPrograms.COLUMN_PACKAGE_NAME)

                do {
                    val id = if (idIdx >= 0) cursor.getLong(idIdx) else 0L
                    val title = if (titleIdx >= 0) cursor.getString(titleIdx).orEmpty() else ""
                    if (title.isBlank()) continue

                    val episode = if (episodeIdx >= 0) cursor.getString(episodeIdx) else null
                    val poster = if (posterIdx >= 0) cursor.getString(posterIdx) else null
                    val pos = if (posIdx >= 0) cursor.getLong(posIdx) else 0L
                    val dur = if (durIdx >= 0) cursor.getLong(durIdx) else 0L
                    val intentUri = if (intentIdx >= 0) cursor.getString(intentIdx) else null
                    val pkg = if (pkgIdx >= 0) cursor.getString(pkgIdx) else null

                    val appName = if (!pkg.isNullOrBlank()) {
                        try {
                            val appInfo = packageManager.getApplicationInfo(pkg, 0)
                            packageManager.getApplicationLabel(appInfo).toString()
                        } catch (_: Exception) {
                            pkg
                        }
                    } else "Media Player"

                    list.add(
                        WatchNextItem(
                            id = id,
                            title = title,
                            episodeTitle = episode,
                            posterUri = poster,
                            positionMillis = pos,
                            durationMillis = dur,
                            intentUri = intentUri,
                            packageName = pkg,
                            appName = appName
                        )
                    )
                } while (cursor.moveToNext())
            }
        } catch (e: Exception) {
            Log.w("WatchNextRepository", "Unable to query WatchNextPrograms: ${e.message}")
        } finally {
            cursor?.close()
        }

        return list
    }

    fun launch(item: WatchNextItem): Boolean {
        try {
            if (!item.intentUri.isNullOrBlank()) {
                val intent = Intent.parseUri(item.intentUri, Intent.URI_INTENT_SCHEME).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return true
            }
            if (!item.packageName.isNullOrBlank()) {
                val launchIntent = packageManager.getLaunchIntentForPackage(item.packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    return true
                }
            }
        } catch (e: Exception) {
            Log.w("WatchNextRepository", "Failed to launch WatchNextItem: ${e.message}")
        }
        return false
    }
}
