package com.orbit.tv.launcher

import android.app.Activity
import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache

import android.content.res.Configuration
import android.os.Build
import java.util.Locale

/**
 * Custom Application class with memory-constrained Coil image loader
 * and unified 240 DPI density lock across all TV hardware.
 */
class SmartTvApplication : Application(), ImageLoaderFactory {

    companion object {
        const val TARGET_DPI = 240
    }

    override fun attachBaseContext(base: Context) {
        val config = base.resources.configuration
        config.densityDpi = TARGET_DPI
        syncLocale(config)
        val newContext = base.createConfigurationContext(config)
        super.attachBaseContext(newContext)
    }

    override fun onCreate() {
        super.onCreate()
        syncLocale(resources.configuration)
        applyAppDensity()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                applyActivityDensity(activity)
            }
            override fun onActivityStarted(activity: Activity) {
                applyActivityDensity(activity)
            }
            override fun onActivityResumed(activity: Activity) {
                applyActivityDensity(activity)
                checkAndRefreshActivityLocale(activity)
            }
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        syncLocale(newConfig)
        applyAppDensity()
    }

    private fun getSystemLocale(): Locale {
        val sysConfig = android.content.res.Resources.getSystem().configuration
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (!sysConfig.locales.isEmpty) sysConfig.locales[0] else Locale.getDefault()
        } else {
            @Suppress("DEPRECATION")
            sysConfig.locale ?: Locale.getDefault()
        }
    }

    private fun checkAndRefreshActivityLocale(activity: Activity) {
        try {
            val sysLocale = getSystemLocale()
            val actConfig = activity.resources.configuration
            val actLocale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                if (!actConfig.locales.isEmpty) actConfig.locales[0] else null
            } else {
                @Suppress("DEPRECATION")
                actConfig.locale
            }
            if (actLocale != null && actLocale.language != sysLocale.language) {
                Locale.setDefault(sysLocale)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    actConfig.setLocales(android.content.res.Resources.getSystem().configuration.locales)
                } else {
                    @Suppress("DEPRECATION")
                    actConfig.locale = sysLocale
                }
                @Suppress("DEPRECATION")
                activity.resources.updateConfiguration(actConfig, activity.resources.displayMetrics)
                activity.recreate()
            }
        } catch (_: Exception) {}
    }

    private fun syncLocale(config: Configuration) {
        try {
            val locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                if (!config.locales.isEmpty) config.locales[0] else null
            } else {
                @Suppress("DEPRECATION")
                config.locale
            }
            if (locale != null) {
                Locale.setDefault(locale)
            }
        } catch (_: Exception) {}
    }

    private fun applyAppDensity() {
        val dm = resources.displayMetrics
        dm.densityDpi = TARGET_DPI
        dm.density = TARGET_DPI / 160f
        dm.scaledDensity = dm.density
        val config = resources.configuration
        config.densityDpi = TARGET_DPI
        val sysLocale = getSystemLocale()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(android.content.res.Resources.getSystem().configuration.locales)
        } else {
            @Suppress("DEPRECATION")
            config.locale = sysLocale
        }
        @Suppress("DEPRECATION")
        resources.updateConfiguration(config, dm)
    }

    private fun applyActivityDensity(activity: Activity) {
        val dm = activity.resources.displayMetrics
        dm.densityDpi = TARGET_DPI
        dm.density = TARGET_DPI / 160f
        dm.scaledDensity = dm.density
        val config = activity.resources.configuration
        config.densityDpi = TARGET_DPI
        val sysLocale = getSystemLocale()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(android.content.res.Resources.getSystem().configuration.locales)
        } else {
            @Suppress("DEPRECATION")
            config.locale = sysLocale
        }
        @Suppress("DEPRECATION")
        activity.resources.updateConfiguration(config, dm)
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .bitmapConfig(Bitmap.Config.RGB_565) // 50% memory saving over ARGB_8888
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.10) // 10% of available heap is optimal for 1-2GB TV boxes
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(40L * 1024 * 1024) // 40 MB max disk cache
                    .build()
            }
            .respectCacheHeaders(false)
            .crossfade(true)
            .build()
    }
}
