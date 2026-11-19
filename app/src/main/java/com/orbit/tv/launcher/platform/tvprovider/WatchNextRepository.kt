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
        val seenTitles = mutableSetOf<String>()

        // 1. Query TvContract.WatchNextPrograms
        try {
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

            context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                "${TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS} DESC"
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
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
                        if (title.isBlank() || !seenTitles.add(title.lowercase())) continue

                        val episode = if (episodeIdx >= 0) cursor.getString(episodeIdx) else null
                        val poster = if (posterIdx >= 0) cursor.getString(posterIdx) else null
                        val pos = if (posIdx >= 0) cursor.getLong(posIdx) else 0L
                        val dur = if (durIdx >= 0) cursor.getLong(durIdx) else 0L
                        val intentUri = if (intentIdx >= 0) cursor.getString(intentIdx) else null
                        val pkg = if (pkgIdx >= 0) cursor.getString(pkgIdx) else null

                        val appName = resolveAppName(pkg)

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
                    } while (cursor.moveToNext() && list.size < 25)
                }
            }
        } catch (e: Exception) {
            Log.w("WatchNextRepository", "Unable to query WatchNextPrograms: ${e.message}")
        }

        // 2. Supplement with TvContract.PreviewPrograms (SmartTube, YouTube TV, streaming apps)
        if (list.size < 20) {
            try {
                val previewUri = Uri.parse("content://android.media.tv/preview_program")
                val projection = arrayOf(
                    "_id",
                    "title",
                    "short_description",
                    "poster_art_uri",
                    "duration_millis",
                    "intent_uri",
                    "package_name"
                )

                context.contentResolver.query(
                    previewUri,
                    projection,
                    null,
                    null,
                    "_id DESC"
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idIdx = cursor.getColumnIndex("_id")
                        val titleIdx = cursor.getColumnIndex("title")
                        val descIdx = cursor.getColumnIndex("short_description")
                        val posterIdx = cursor.getColumnIndex("poster_art_uri")
                        val durIdx = cursor.getColumnIndex("duration_millis")
                        val intentIdx = cursor.getColumnIndex("intent_uri")
                        val pkgIdx = cursor.getColumnIndex("package_name")

                        do {
                            val id = if (idIdx >= 0) cursor.getLong(idIdx) else 0L
                            val title = if (titleIdx >= 0) cursor.getString(titleIdx).orEmpty() else ""
                            if (title.isBlank() || !seenTitles.add(title.lowercase())) continue

                            val desc = if (descIdx >= 0) cursor.getString(descIdx) else null
                            val poster = if (posterIdx >= 0) cursor.getString(posterIdx) else null
                            val dur = if (durIdx >= 0) cursor.getLong(durIdx) else 0L
                            val intentUri = if (intentIdx >= 0) cursor.getString(intentIdx) else null
                            val pkg = if (pkgIdx >= 0) cursor.getString(pkgIdx) else null

                            val appName = resolveAppName(pkg)

                            list.add(
                                WatchNextItem(
                                    id = id,
                                    title = title,
                                    episodeTitle = desc,
                                    posterUri = poster,
                                    positionMillis = 0L,
                                    durationMillis = dur,
                                    intentUri = intentUri,
                                    packageName = pkg,
                                    appName = appName
                                )
                            )
                        } while (cursor.moveToNext() && list.size < 30)
                    }
                }
            } catch (e: Exception) {
                Log.w("WatchNextRepository", "Unable to query PreviewPrograms: ${e.message}")
            }
        }

        return list
    }

    private fun resolveAppName(pkg: String?): String {
        if (pkg.isNullOrBlank()) return "Media Player"
        return try {
            val appInfo = packageManager.getApplicationInfo(pkg, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            when (pkg) {
                "com.google.android.youtube.tv", "com.google.android.youtube" -> "YouTube"
                "com.teamsmart.videomanager.tv", "org.smarttube.stable", "com.liskovsoft.smarttubetv.beta" -> "SmartTube"
                else -> pkg
            }
        }
    }

    fun launch(item: WatchNextItem): Boolean {
        try {
            val intentUri = item.intentUri
            if (!intentUri.isNullOrBlank()) {
                if (intentUri.startsWith("vnd.youtube:", ignoreCase = true)) {
                    val videoId = intentUri.substringAfter("vnd.youtube://").substringAfter("vnd.youtube:")
                    val webUrl = "https://www.youtube.com/watch?v=$videoId"
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    val candidates = listOf(
                        "org.smarttube.stable",
                        "com.teamsmart.videomanager.tv",
                        "com.liskovsoft.smarttubetv.beta",
                        "com.google.android.youtube.tv"
                    )
                    val targetPkg = candidates.firstOrNull { isPackageInstalled(it) } ?: item.packageName
                    if (!targetPkg.isNullOrBlank() && isPackageInstalled(targetPkg)) {
                        intent.setPackage(targetPkg)
                    }
                    context.startActivity(intent)
                    return true
                }

                val intent = Intent.parseUri(intentUri, Intent.URI_INTENT_SCHEME).apply {
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

    private fun isPackageInstalled(pkg: String): Boolean {
        return try {
            packageManager.getPackageInfo(pkg, 0)
            true
        } catch (_: Exception) {
            false
        }
    }
}
