package com.orbit.tv.launcher.features.home

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.orbit.tv.launcher.R
import com.orbit.tv.launcher.features.home.model.MediaPlayerApp
import java.net.URLEncoder

object TvDeepLinkHelper {

    private data class KnownPlayerDef(
        val packageNames: List<String>,
        val categoryLabel: String
    )

    private val KNOWN_PLAYERS = listOf(
        KnownPlayerDef(
            listOf("com.teamsmart.videomanager.tv", "com.liskovsoft.smarttubetv.beta"),
            "YouTube без рекламы"
        ),
        KnownPlayerDef(
            listOf("com.google.android.youtube.tv", "com.google.android.youtube"),
            "YouTube"
        ),
        KnownPlayerDef(
            listOf("ru.kinopoisk.tv", "com.yandex.tv.kinopoisk"),
            "Онлайн-кинотеатр"
        ),
        KnownPlayerDef(
            listOf("com.lampa.app", "com.lampa.tv", "ru.lampa.tv"),
            "Медиа-агрегатор"
        ),
        KnownPlayerDef(
            listOf("com.num"),
            "Торрент-каталог фильмов"
        ),
        KnownPlayerDef(
            listOf("com.vokino"),
            "Онлайн-кинотеатр"
        ),
        KnownPlayerDef(
            listOf("ru.eugene.deluxe", "ru.eugene.deluxereborn"),
            "Медиа-агрегатор"
        ),
        KnownPlayerDef(
            listOf("ru.ivi.client"),
            "Онлайн-кинотеатр"
        ),
        KnownPlayerDef(
            listOf("tv.okko.androidtv"),
            "Онлайн-кинотеатр"
        ),
        KnownPlayerDef(
            listOf("premier.one.tv"),
            "Онлайн-кинотеатр"
        ),
        KnownPlayerDef(
            listOf("ru.start.android"),
            "Онлайн-кинотеатр"
        ),
        KnownPlayerDef(
            listOf("com.vk.vkvideo.tv", "com.vk.vkvideo"),
            "VK Видео"
        ),
        KnownPlayerDef(
            listOf("ru.rutube.app"),
            "Rutube"
        ),
        KnownPlayerDef(
            listOf("ru.mts.mtstv"),
            "KION"
        ),
        KnownPlayerDef(
            listOf("ru.rt.video.app.tv"),
            "Wink"
        ),
        KnownPlayerDef(
            listOf("org.videolan.vlc"),
            "Медиаплеер"
        ),
        KnownPlayerDef(
            listOf("ru.yourok.torrserve"),
            "TorrServe"
        )
    )

    fun getInstalledMediaPlayers(context: Context): List<MediaPlayerApp> {
        val pm = context.packageManager
        val result = mutableListOf<MediaPlayerApp>()
        val foundPackages = mutableSetOf<String>()

        for (def in KNOWN_PLAYERS) {
            for (pkg in def.packageNames) {
                if (foundPackages.contains(pkg)) continue
                try {
                    val appInfo = pm.getApplicationInfo(pkg, 0)
                    val label = pm.getApplicationLabel(appInfo).toString()
                    val icon = pm.getApplicationIcon(appInfo)
                    result.add(
                        MediaPlayerApp(
                            packageName = pkg,
                            name = label,
                            icon = icon,
                            categoryLabel = def.categoryLabel
                        )
                    )
                    foundPackages.add(pkg)
                    break // Take first matched package variant
                } catch (_: PackageManager.NameNotFoundException) {}
            }
        }

        // Always add Global Android TV Media Search at the bottom
        result.add(
            MediaPlayerApp(
                packageName = "system.search",
                name = "Глобальный поиск Android TV",
                icon = ContextCompat.getDrawable(context, R.drawable.ic_search),
                categoryLabel = "Поиск по всем приложениям",
                isSystemSearch = true
            )
        )

        return result
    }

    fun launchSearchInApp(context: Context, app: MediaPlayerApp, query: String) {
        if (app.isSystemSearch) {
            launchSystemGlobalSearch(context, query)
            return
        }

        val pm = context.packageManager
        val pkg = app.packageName

        // Strategy 1: Standard Android Search Intent targeted to specific package
        try {
            val searchIntent = Intent(Intent.ACTION_SEARCH).apply {
                setPackage(pkg)
                putExtra(SearchManager.QUERY, query)
                putExtra("query", query)
                putExtra("search_query", query)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (searchIntent.resolveActivity(pm) != null) {
                context.startActivity(searchIntent)
                return
            }
        } catch (_: Exception) {}

        // Strategy 2: Specific deep links for YouTube / SmartTube
        if (pkg.contains("youtube") || pkg.contains("smarttube") || pkg.contains("teamsmart")) {
            try {
                val encoded = URLEncoder.encode(query, "UTF-8")
                val ytUri = Uri.parse("vnd.youtube://results?search_query=$encoded")
                val ytIntent = Intent(Intent.ACTION_VIEW, ytUri).apply {
                    setPackage(pkg)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(ytIntent)
                return
            } catch (_: Exception) {}
        }

        // Strategy 3: Standard launch intent with extras
        try {
            val launchIntent = pm.getLaunchIntentForPackage(pkg)?.apply {
                putExtra(SearchManager.QUERY, query)
                putExtra("query", query)
                putExtra("search_query", query)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (launchIntent != null) {
                context.startActivity(launchIntent)
                return
            }
        } catch (_: Exception) {}

        Toast.makeText(context, "Не удалось открыть $query в ${app.name}", Toast.LENGTH_SHORT).show()
    }

    private fun launchSystemGlobalSearch(context: Context, query: String) {
        try {
            val intent = Intent(Intent.ACTION_SEARCH).apply {
                putExtra(SearchManager.QUERY, query)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            // Fallback: Web search URL if device has no global search handler
            try {
                val encoded = URLEncoder.encode(query, "UTF-8")
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$encoded")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webIntent)
            } catch (_: Exception) {
                Toast.makeText(context, "Поиск недоступен", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
