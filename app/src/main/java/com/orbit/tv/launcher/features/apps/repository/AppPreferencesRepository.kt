package com.orbit.tv.launcher.features.apps.repository

import android.content.Context
import android.content.SharedPreferences

/**
 * Persists user customization for Android TV apps:
 * - Pinned apps on Home screen shelf (ordered list).
 * - Hidden apps (bloatware/system junk) excluded from main launchers.
 *
 * Uses SharedPreferences for zero memory overhead on 1GB RAM budget.
 */
class AppPreferencesRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getPinnedPackages(): List<String> {
        val raw = prefs.getString(KEY_PINNED_APPS, "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split(",").filter { it.isNotBlank() }
    }

    fun isPinned(packageName: String): Boolean {
        return getPinnedPackages().contains(packageName)
    }

    fun pinApp(packageName: String) {
        val current = getPinnedPackages().toMutableList()
        if (!current.contains(packageName)) {
            current.add(packageName)
            savePinnedPackages(current)
        }
    }

    fun unpinApp(packageName: String) {
        val current = getPinnedPackages().toMutableList()
        if (current.remove(packageName)) {
            savePinnedPackages(current)
        }
    }

    fun togglePin(packageName: String): Boolean {
        return if (isPinned(packageName)) {
            unpinApp(packageName)
            false
        } else {
            pinApp(packageName)
            true
        }
    }

    private fun savePinnedPackages(packages: List<String>) {
        prefs.edit().putString(KEY_PINNED_APPS, packages.joinToString(",")).apply()
    }

    fun getHiddenPackages(): Set<String> {
        return prefs.getStringSet(KEY_HIDDEN_APPS, emptySet()) ?: emptySet()
    }

    fun isHidden(packageName: String): Boolean {
        return getHiddenPackages().contains(packageName)
    }

    fun hideApp(packageName: String) {
        val current = getHiddenPackages().toMutableSet()
        current.add(packageName)
        // Also unpin if it was pinned
        unpinApp(packageName)
        prefs.edit().putStringSet(KEY_HIDDEN_APPS, current).apply()
    }

    fun unhideApp(packageName: String) {
        val current = getHiddenPackages().toMutableSet()
        current.remove(packageName)
        prefs.edit().putStringSet(KEY_HIDDEN_APPS, current).apply()
    }

    fun toggleHide(packageName: String): Boolean {
        return if (isHidden(packageName)) {
            unhideApp(packageName)
            false
        } else {
            hideApp(packageName)
            true
        }
    }

    fun unhideAll() {
        prefs.edit().remove(KEY_HIDDEN_APPS).apply()
    }

    fun getContentLanguage(): String {
        return prefs.getString(KEY_CONTENT_LANGUAGE, "SYSTEM") ?: "SYSTEM"
    }

    fun setContentLanguage(lang: String) {
        prefs.edit().putString(KEY_CONTENT_LANGUAGE, lang).apply()
    }

    fun getPreferredPlayer(): String {
        return prefs.getString(KEY_PREFERRED_PLAYER, "ASK") ?: "ASK"
    }

    fun setPreferredPlayer(pkg: String) {
        prefs.edit().putString(KEY_PREFERRED_PLAYER, pkg).apply()
    }

    fun getWallpaperTheme(): String {
        return prefs.getString(KEY_WALLPAPER_THEME, "SCENERY") ?: "SCENERY"
    }

    fun setWallpaperTheme(theme: String) {
        prefs.edit().putString(KEY_WALLPAPER_THEME, theme).apply()
    }

    fun getDimmerOpacity(): Float {
        return prefs.getFloat(KEY_DIMMER_OPACITY, 0.82f)
    }

    fun setDimmerOpacity(opacity: Float) {
        prefs.edit().putFloat(KEY_DIMMER_OPACITY, opacity).apply()
    }

    fun getHomeLayoutMode(): String {
        return prefs.getString(KEY_HOME_LAYOUT_MODE, HOME_LAYOUT_DEFAULT) ?: HOME_LAYOUT_DEFAULT
    }

    fun setHomeLayoutMode(mode: String) {
        prefs.edit().putString(KEY_HOME_LAYOUT_MODE, mode).apply()
    }

    companion object {
        private const val PREFS_NAME = "orbit_launcher_app_prefs"
        private const val KEY_PINNED_APPS = "key_pinned_packages"
        private const val KEY_HIDDEN_APPS = "key_hidden_packages"
        private const val KEY_CONTENT_LANGUAGE = "key_content_language"
        private const val KEY_PREFERRED_PLAYER = "key_preferred_player"
        private const val KEY_WALLPAPER_THEME = "key_wallpaper_theme"
        private const val KEY_DIMMER_OPACITY = "key_dimmer_opacity"
        private const val KEY_HOME_LAYOUT_MODE = "key_home_layout_mode"

        const val HOME_LAYOUT_DEFAULT = "DEFAULT"
        const val HOME_LAYOUT_CINEMA = "CINEMA"
        const val HOME_LAYOUT_MOVIES_FIRST = "MOVIES_FIRST"
        const val HOME_LAYOUT_NO_SERIES = "NO_SERIES"
        const val HOME_LAYOUT_MINIMAL = "MINIMAL"

        const val LAYOUT_APPS_MOVIES_SERIES = HOME_LAYOUT_DEFAULT
        const val LAYOUT_MOVIES_SERIES_APPS = HOME_LAYOUT_CINEMA
        const val LAYOUT_MOVIES_APPS_SERIES = HOME_LAYOUT_MOVIES_FIRST
        const val LAYOUT_APPS_MOVIES = HOME_LAYOUT_NO_SERIES
        const val LAYOUT_APPS_ONLY = HOME_LAYOUT_MINIMAL
    }
}
