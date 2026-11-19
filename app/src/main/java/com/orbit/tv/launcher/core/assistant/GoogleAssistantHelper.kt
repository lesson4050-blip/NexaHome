package com.orbit.tv.launcher.core.assistant

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

object GoogleAssistantHelper {

    private const val KATNISS_PKG = "com.google.android.katniss"
    private const val SEARCHBOX_PKG = "com.google.android.googlequicksearchbox"
    @Volatile
    private var cachedAvailable: Boolean? = null

    fun isAvailable(context: Context): Boolean {
        cachedAvailable?.let { return it }
        for (pkg in listOf(KATNISS_PKG, SEARCHBOX_PKG)) {
            try {
                context.packageManager.getPackageInfo(pkg, 0)
                cachedAvailable = true
                return true
            } catch (_: PackageManager.NameNotFoundException) {
            }
        }
        val assistIntent = Intent(Intent.ACTION_ASSIST)
        val available = assistIntent.resolveActivity(context.packageManager) != null
        cachedAvailable = available
        return available
    }
    fun launch(context: Context): Boolean {
        // 1. Prioritize Katniss Assistant TV search trampoline
        try {
            val assistIntent = Intent(Intent.ACTION_ASSIST).apply {
                component = android.content.ComponentName(
                    KATNISS_PKG,
                    "com.google.android.apps.tvsearch.app.launch.trampoline.SearchActivityTrampoline"
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(assistIntent)
            return true
        } catch (_: Exception) {}

        // 2. Package-directed Katniss ASSIST intent
        try {
            val assistIntent = Intent(Intent.ACTION_ASSIST).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (isPackageInstalled(context, KATNISS_PKG)) {
                    `package` = KATNISS_PKG
                }
            }
            context.startActivity(assistIntent)
            return true
        } catch (_: Exception) {}

        // 2. Generic system ASSIST intent
        try {
            val assistIntent = Intent(Intent.ACTION_ASSIST).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(assistIntent)
            return true
        } catch (_: Exception) {}

        // 3. VOICE_ASSIST intent fallback
        try {
            val voiceIntent = Intent("android.intent.action.VOICE_ASSIST").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(voiceIntent)
            return true
        } catch (_: Exception) {}

        // 4. Fallback directly to Katniss or Google app launcher intent
        try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(KATNISS_PKG)
                ?: context.packageManager.getLaunchIntentForPackage(SEARCHBOX_PKG)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                return true
            }
        } catch (_: Exception) {}

        return false
    }

    private fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }
}
