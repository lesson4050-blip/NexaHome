package com.orbit.tv.launcher

import android.app.Application
import android.graphics.Bitmap
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache

/**
 * Custom Application class with memory-constrained Coil image loader
 * optimized for 1GB RAM Android TV hardware.
 */
class SmartTvApplication : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        com.orbit.tv.launcher.features.cast.CastServerManager.start(this)
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .bitmapConfig(Bitmap.Config.RGB_565) // 50% memory saving over ARGB_8888
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.18) // Cap at 18% of available heap
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
