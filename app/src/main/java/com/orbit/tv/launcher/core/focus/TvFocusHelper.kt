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
        duration: Long = 120L
    ) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            view.outlineAmbientShadowColor = android.graphics.Color.TRANSPARENT
            view.outlineSpotShadowColor = android.graphics.Color.TRANSPARENT
        }
        view.elevation = 0f
        view.translationZ = 0f

        view.setOnFocusChangeListener { v, hasFocus ->
            val targetScale = if (hasFocus) scaleRatio else 1.0f
            if (kotlin.math.abs(v.scaleX - targetScale) > 0.005f) {
                v.animate()
                    .scaleX(targetScale)
                    .scaleY(targetScale)
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
