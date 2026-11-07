package com.orbit.tv.launcher.platform.home

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.orbit.tv.launcher.ui.screens.HomeActivity

/**
 * Priority Home key watcher service.
 * Intercepts KEYCODE_HOME and remote controller navigation keys before OEM firmware
 * can divert the user into stock bloatware launchers (common on Tanix, X96, Mecool).
 */
class TvHomeWatcherService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Window state changes observer (passive)
    }

    override fun onInterrupt() {
        // Service interrupted
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (event == null) return false
        val keyCode = event.keyCode
        val action = event.action

        if (keyCode == KeyEvent.KEYCODE_HOME || keyCode == KeyEvent.KEYCODE_TV_RADIO_SERVICE || keyCode == KeyEvent.KEYCODE_GUIDE) {
            if (action == KeyEvent.ACTION_UP) {
                val homeIntent = Intent(this, HomeActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                startActivity(homeIntent)
                return true // Intercept key event so OEM bloatware launcher never triggers
            }
            return true
        }
        return super.onKeyEvent(event)
    }
}
