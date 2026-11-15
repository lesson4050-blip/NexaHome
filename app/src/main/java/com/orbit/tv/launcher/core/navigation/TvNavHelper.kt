package com.orbit.tv.launcher.core.navigation

import android.app.Activity
import android.content.Intent
import com.orbit.tv.launcher.R
import com.orbit.tv.launcher.ui.screens.HomeActivity

object TvNavHelper {

    fun openScreen(activity: Activity, targetClass: Class<out Activity>) {
        if (activity.javaClass == targetClass) return

        val intent = Intent(activity, targetClass).apply {
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        }
        activity.startActivity(intent)
        activity.overridePendingTransition(R.anim.tv_nav_enter, R.anim.tv_nav_exit)

        if (activity !is HomeActivity) {
            activity.finish()
        }
    }

    fun finishWithPop(activity: Activity) {
        activity.finish()
        activity.overridePendingTransition(R.anim.tv_nav_pop_enter, R.anim.tv_nav_pop_exit)
    }
}
