package com.orbit.tv.launcher.platform.storage

import android.content.Context
import coil.Coil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class CacheManager(private val context: Context) {

    fun getImageCacheSize(): Long {
        val dir = context.cacheDir.resolve("image_cache")
        return getFolderSize(dir)
    }

    fun getCatalogCacheSize(): Long {
        var size = 0L
        context.cacheDir.listFiles()?.forEach { file ->
            if (file.isFile && file.name.endsWith(".json")) {
                size += file.length()
            }
        }
        return size
    }

    fun getTotalCacheSize(): Long {
        return getImageCacheSize() + getCatalogCacheSize()
    }

    @OptIn(coil.annotation.ExperimentalCoilApi::class)
    suspend fun clearImageCache() = withContext(Dispatchers.IO) {
        try {
            Coil.imageLoader(context).diskCache?.clear()
        } catch (_: Exception) {}
        val dir = context.cacheDir.resolve("image_cache")
        deleteFolder(dir)
    }

    suspend fun clearCatalogCache() = withContext(Dispatchers.IO) {
        context.cacheDir.listFiles()?.forEach { file ->
            if (file.isFile && file.name.endsWith(".json")) {
                try { file.delete() } catch (_: Exception) {}
            }
        }
    }

    suspend fun clearAllCache() = withContext(Dispatchers.IO) {
        clearImageCache()
        clearCatalogCache()
    }

    fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 KB"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        return if (mb >= 1.0) {
            String.format(Locale.US, "%.1f MB", mb)
        } else {
            String.format(Locale.US, "%.0f KB", kb)
        }
    }

    private fun getFolderSize(dir: File): Long {
        if (!dir.exists()) return 0L
        var size = 0L
        dir.listFiles()?.forEach { file ->
            size += if (file.isDirectory) getFolderSize(file) else file.length()
        }
        return size
    }

    private fun deleteFolder(dir: File) {
        if (!dir.exists()) return
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) deleteFolder(file) else file.delete()
        }
    }
}
