package com.orbit.tv.launcher.platform.package_manager

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.orbit.tv.launcher.features.home.model.AppModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Direct repository querying Android TV's system PackageManager.
 * Discovers Leanback apps first, with fallback to standard launchers for sideloaded apps.
 */
class PackageManagerRepository(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager
    private val ownPackageName: String = context.packageName

    suspend fun getInstalledTvApps(): List<AppModel> = withContext(Dispatchers.IO) {
        val appsMap = mutableMapOf<String, AppModel>()

        // 1. Primary: Native Leanback TV Apps
        val leanbackIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        }
        val leanbackApps = packageManager.queryIntentActivities(leanbackIntent, 0)

        for (resolveInfo in leanbackApps) {
            val pkg = resolveInfo.activityInfo.packageName
            if (pkg == ownPackageName) continue

            val label = resolveInfo.loadLabel(packageManager).toString()
            val icon = resolveInfo.loadIcon(packageManager)
            val isGame = (resolveInfo.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_IS_GAME) != 0
            val launchIntent = packageManager.getLeanbackLaunchIntentForPackage(pkg)
                ?: packageManager.getLaunchIntentForPackage(pkg)

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
            if (pkg == ownPackageName || appsMap.containsKey(pkg)) continue

            val label = resolveInfo.loadLabel(packageManager).toString()
            val icon = resolveInfo.loadIcon(packageManager)
            val isGame = (resolveInfo.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_IS_GAME) != 0
            val launchIntent = packageManager.getLaunchIntentForPackage(pkg)

            appsMap[pkg] = AppModel(
                packageName = pkg,
                label = label,
                iconDrawable = icon,
                isGame = isGame,
                launchIntent = launchIntent
            )
        }

        appsMap.values.toList()
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
