package com.orbit.tv.launcher.core.focus

import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator

/**
 * Universal Leanback Focus Helper for Android TV & Air-Mouse remotes.
 * Provides smooth 1.05x scale elevation without layout shifts.
 */
object TvFocusHelper {

    private val interpolator = DecelerateInterpolator()

    fun bindFocusScale(
        view: View,
        scaleRatio: Float = 1.05f,
        elevationDp: Float = 8f,
        duration: Long = 180L
    ) {
        val density = view.resources.displayMetrics.density
        val targetElevation = elevationDp * density

        view.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                v.animate()
                    .scaleX(scaleRatio)
                    .scaleY(scaleRatio)
                    .translationZ(targetElevation)
                    .setDuration(duration)
                    .setInterpolator(interpolator)
                    .start()
            } else {
                v.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .translationZ(0f)
                    .setDuration(duration)
                    .setInterpolator(interpolator)
                    .start()
            }
        }

        // Support G10S air-mouse hover to focus smoothly
        view.setOnGenericMotionListener { v, event ->
            if (event.action == MotionEvent.ACTION_HOVER_ENTER) {
                if (!v.isFocused) {
                    v.requestFocus()
                }
                true
            } else {
                false
            }
        }
    }
}
