package com.orbit.tv.launcher.features.home

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
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
            listOf("com.megogo.application", "net.megogo.tv"),
            "Онлайн-кинотеатр MEGOGO"
        ),
        KnownPlayerDef(
            listOf("tv.sweet.player", "tv.sweet.tv"),
            "Онлайн-кинотеатр Sweet.tv"
        ),
        KnownPlayerDef(
            listOf("ua.kyivstar.tv"),
            "Онлайн-кинотеатр Киевстар ТВ"
        ),
        KnownPlayerDef(
            listOf("ru.kinopoisk.tv", "com.yandex.tv.kinopoisk"),
            "Онлайн-кинотеатр Кинопоиск"
        ),
        KnownPlayerDef(
            listOf("com.lampa.app", "com.lampa.tv", "ru.lampa.tv"),
            "Медиа-агрегатор Lampa"
        ),
        KnownPlayerDef(
            listOf("com.num"),
            "Торрент-каталог NUM"
        ),
        KnownPlayerDef(
            listOf("com.vokino"),
            "Онлайн-кинотеатр VoKino"
        ),
        KnownPlayerDef(
            listOf("ru.eugene.deluxe", "ru.eugene.deluxereborn"),
            "Медиа-агрегатор LazyMedia"
        ),
        KnownPlayerDef(
            listOf("com.hdrezka", "org.rezka", "com.rezka.app"),
            "Онлайн-кинотеатр HDRezka"
        ),
        KnownPlayerDef(
            listOf("com.zona", "com.kinoplay"),
            "Онлайн-кинотеатр"
        ),
        KnownPlayerDef(
            listOf("ru.ivi.client"),
            "Онлайн-кинотеатр IVI"
        ),
        KnownPlayerDef(
            listOf("tv.okko.androidtv"),
            "Онлайн-кинотеатр Okko"
        ),
        KnownPlayerDef(
            listOf("premier.one.tv"),
            "Онлайн-кинотеатр Premier"
        ),
        KnownPlayerDef(
            listOf("ru.start.android"),
            "Онлайн-кинотеатр START"
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
            listOf("com.netflix.ninja", "com.netflix.mediaclient"),
            "Netflix"
        ),
        KnownPlayerDef(
            listOf("org.videolan.vlc"),
            "Медиаплеер VLC"
        ),
        KnownPlayerDef(
            listOf("com.mxtech.videoplayer.ad", "com.mxtech.videoplayer.pro"),
            "Медиаплеер MX Player"
        ),
        KnownPlayerDef(
            listOf("net.gtvbox.videoplayer"),
            "Медиаплеер Vimu"
        ),
        KnownPlayerDef(
            listOf("com.brouken.player"),
            "Медиаплеер Just Player"
        ),
        KnownPlayerDef(
            listOf("org.courville.nova"),
            "Медиаплеер Nova"
        ),
        KnownPlayerDef(
            listOf("org.xbmc.kodi"),
            "Медиацентр Kodi"
        ),
        KnownPlayerDef(
            listOf("ru.yourok.torrserve", "ru.yourok.torrserver"),
            "Торрент-сервер TorrServe"
        )
    )

    private val SYSTEM_IGNORE_PACKAGES = setOf(
        "android",
        "com.android.systemui",
        "com.android.settings",
        "com.android.vending",
        "com.google.android.gms",
        "com.google.android.googlequicksearchbox",
        "com.google.android.katniss",
        "com.google.android.inputmethod.latin"
    )

    private val EXCLUDED_KEYWORDS = listOf(
        "file", "explorer", "manager", "commander", "xplore", "проводник", "файлы",
        "music", "музыка", "audio", "sound", "podcast", "radio",
        "stub", "test", "benchmark", "gallery", "галерея", "camera", "камера", "photo", "фото",
        "launcher", "keyboard", "input", "settings", "overlay"
    )

    private fun isExcludedApp(pkg: String, label: String, myPackage: String): Boolean {
        if (pkg == myPackage || SYSTEM_IGNORE_PACKAGES.contains(pkg)) return true
        if (pkg.startsWith("com.android.") || pkg.startsWith("android.")) return true
        if (pkg.startsWith("com.google.android.") && !pkg.contains("youtube")) return true

        val lowerPkg = pkg.lowercase()
        val lowerLabel = label.lowercase()
        return EXCLUDED_KEYWORDS.any { lowerPkg.contains(it) || lowerLabel.contains(it) }
    }

    fun getInstalledMediaPlayers(context: Context): List<MediaPlayerApp> {
        val pm = context.packageManager
        val result = mutableListOf<MediaPlayerApp>()
        val foundPackages = mutableSetOf<String>()
        val myPackage = context.packageName

        // 1. Process known catalog services first (clean names and priority)
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
                    break
                } catch (_: PackageManager.NameNotFoundException) {}
            }
        }

        // 2. Dynamic discovery: query apps registered to view video content (strictly filtering out file managers / stubs)
        try {
            val videoIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse("http://localhost/video.mp4"), "video/*")
            }
            val videoHandlers = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(videoIntent, PackageManager.ResolveInfoFlags.of(0))
            } else {
                pm.queryIntentActivities(videoIntent, 0)
            }
            for (ri in videoHandlers) {
                val pkg = ri.activityInfo?.packageName ?: continue
                if (foundPackages.contains(pkg)) continue
                try {
                    val appInfo = pm.getApplicationInfo(pkg, 0)
                    val label = pm.getApplicationLabel(appInfo).toString()
                    if (isExcludedApp(pkg, label, myPackage)) continue
                    if (pm.getLaunchIntentForPackage(pkg) == null) continue

                    val icon = pm.getApplicationIcon(appInfo)
                    result.add(
                        MediaPlayerApp(
                            packageName = pkg,
                            name = label,
                            icon = icon,
                            categoryLabel = "Видеоплеер / Стриминг"
                        )
                    )
                    foundPackages.add(pkg)
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}

        // 3. Dynamic discovery: scan installed apps strictly marked with CATEGORY_VIDEO
        try {
            val installedApps = pm.getInstalledApplications(0)
            for (appInfo in installedApps) {
                val pkg = appInfo.packageName
                if (foundPackages.contains(pkg)) continue
                if (appInfo.category != ApplicationInfo.CATEGORY_VIDEO) continue
                if (pm.getLaunchIntentForPackage(pkg) == null) continue

                try {
                    val label = pm.getApplicationLabel(appInfo).toString()
                    if (isExcludedApp(pkg, label, myPackage)) continue

                    val icon = pm.getApplicationIcon(appInfo)
                    result.add(
                        MediaPlayerApp(
                            packageName = pkg,
                            name = label,
                            icon = icon,
                            categoryLabel = "Видеоплеер / Стриминг"
                        )
                    )
                    foundPackages.add(pkg)
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}

        // 4. Always add Google Play / System Search fallback at the end
        result.add(
            MediaPlayerApp(
                packageName = "system.search",
                name = "Поиск в Google Play / Браузере",
                icon = ContextCompat.getDrawable(context, R.drawable.ic_search),
                categoryLabel = "Поиск по всей системе",
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

        // Special handling for Lampa deep link
        if (pkg.contains("lampa")) {
            try {
                val encoded = URLEncoder.encode(query, "UTF-8")
                val lampaUri = Uri.parse("lampa://search?q=$encoded")
                val lampaIntent = Intent(Intent.ACTION_VIEW, lampaUri).apply {
                    setPackage(pkg)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(lampaIntent)
                return
            } catch (_: Exception) {}
        }

        // Special handling for YouTube / SmartTube
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

        // Strategy 2: Standard launch intent with query extras
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
        val pm = context.packageManager
        try {
            val intent = Intent(Intent.ACTION_SEARCH).apply {
                putExtra(SearchManager.QUERY, query)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(pm) != null) {
                context.startActivity(intent)
                return
            }
        } catch (_: Exception) {}

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

    fun isMediaApp(packageName: String): Boolean {
        return KNOWN_PLAYERS.any { def -> def.packageNames.any { pkg -> packageName.contains(pkg, ignoreCase = true) } } ||
                packageName.contains("youtube", ignoreCase = true) ||
                packageName.contains("smarttube", ignoreCase = true) ||
                packageName.contains("lampa", ignoreCase = true) ||
                packageName.contains("kodi", ignoreCase = true) ||
                packageName.contains("vlc", ignoreCase = true) ||
                packageName.contains("video", ignoreCase = true) ||
                packageName.contains("player", ignoreCase = true)
    }

    fun getCategoryLabel(packageName: String): String? {
        val found = KNOWN_PLAYERS.firstOrNull { def -> def.packageNames.any { pkg -> packageName.contains(pkg, ignoreCase = true) } }
        return found?.categoryLabel
    }
}
