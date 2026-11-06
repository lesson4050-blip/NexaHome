package com.orbit.tv.launcher.platform.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.orbit.tv.launcher.ui.screens.HomeActivity

/**
 * Boot receiver to ensure Home launcher auto-start on OEM devices
 * that restart into stock firmware without prompting.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED || action == "android.intent.action.QUICKBOOT_POWERON") {
            val homeIntent = Intent(context, HomeActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            try {
                context.startActivity(homeIntent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
