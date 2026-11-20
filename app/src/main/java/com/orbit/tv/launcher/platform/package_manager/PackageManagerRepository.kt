package com.orbit.tv.launcher.platform.package_manager

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.orbit.tv.launcher.features.home.model.AppModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Direct repository querying Android TV's system PackageManager.
 * Discovers Leanback apps first, with fallback to standard launchers for sideloaded apps.
 */
class PackageManagerRepository(private val context: Context) {

    companion object {
        @Volatile
        private var memoryCachedApps: List<AppModel>? = null

        private val iconCache = android.util.LruCache<String, android.graphics.drawable.Drawable>(150)

        fun getCachedIcon(packageName: String): android.graphics.drawable.Drawable? {
            return iconCache.get(packageName)
        }

        fun putCachedIcon(packageName: String, icon: android.graphics.drawable.Drawable) {
            iconCache.put(packageName, icon)
        }

        fun invalidateCache() {
            memoryCachedApps = null
        }

        fun loadIconAsync(context: Context, packageName: String, onLoaded: (android.graphics.drawable.Drawable) -> Unit) {
            val cached = iconCache.get(packageName)
            if (cached != null) {
                onLoaded(cached)
                return
            }
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                val icon = try {
                    context.packageManager.getApplicationIcon(packageName)
                } catch (_: Exception) {
                    null
                }
                if (icon != null) {
                    iconCache.put(packageName, icon)
                    withContext(Dispatchers.Main) {
                        onLoaded(icon)
                    }
                }
            }
        }
    }

    private val packageManager: PackageManager = context.packageManager
    private val ownPackageName: String = context.packageName

    suspend fun getInstalledTvApps(forceRefresh: Boolean = false): List<AppModel> = withContext(Dispatchers.IO) {
        if (!forceRefresh) {
            memoryCachedApps?.let { return@withContext it }
        }
        val appsMap = mutableMapOf<String, AppModel>()

        // 1. Primary: Native Leanback TV Apps
        val leanbackIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        }
        val leanbackApps = packageManager.queryIntentActivities(leanbackIntent, 0)

        for (resolveInfo in leanbackApps) {
            val pkg = resolveInfo.activityInfo.packageName
            if (pkg.startsWith("com.orbit.tv.launcher")) continue

            val label = resolveInfo.loadLabel(packageManager).toString()
            val isGame = (resolveInfo.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_IS_GAME) != 0
            val launchIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                component = android.content.ComponentName(pkg, resolveInfo.activityInfo.name)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val icon = iconCache.get(pkg) ?: try {
                resolveInfo.loadIcon(packageManager)?.also { iconCache.put(pkg, it) }
            } catch (_: Exception) { null }

            appsMap[pkg] = AppModel(
                packageName = pkg,
                label = label,
                iconDrawable = icon,
                isGame = isGame,
                launchIntent = launchIntent
            )
        }

        // 2. Secondary: Sideloaded / Mobile apps without Leanback banner
        val standardIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val standardApps = packageManager.queryIntentActivities(standardIntent, 0)

        for (resolveInfo in standardApps) {
            val pkg = resolveInfo.activityInfo.packageName
            if (pkg.startsWith("com.orbit.tv.launcher") || appsMap.containsKey(pkg)) continue

            val label = resolveInfo.loadLabel(packageManager).toString()
            val isGame = (resolveInfo.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_IS_GAME) != 0
            val launchIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                component = android.content.ComponentName(pkg, resolveInfo.activityInfo.name)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val icon = iconCache.get(pkg) ?: try {
                resolveInfo.loadIcon(packageManager)?.also { iconCache.put(pkg, it) }
            } catch (_: Exception) { null }

            appsMap[pkg] = AppModel(
                packageName = pkg,
                label = label,
                iconDrawable = icon,
                isGame = isGame,
                launchIntent = launchIntent
            )
        }

        val list = appsMap.values.toList()
        memoryCachedApps = list
        list
    }

    fun launchApp(app: AppModel): Boolean {
        return try {
            val intent = app.launchIntent ?: packageManager.getLeanbackLaunchIntentForPackage(app.packageName)
            ?: packageManager.getLaunchIntentForPackage(app.packageName)

            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
