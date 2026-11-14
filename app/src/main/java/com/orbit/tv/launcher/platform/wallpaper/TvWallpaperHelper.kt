package com.orbit.tv.launcher.platform.wallpaper

import android.view.View
import android.widget.ImageView
import com.orbit.tv.launcher.R
import com.orbit.tv.launcher.ui.views.SilkShaderView

/**
 * Universal wallpaper coordinator for Android TV screens.
 * Ensures instant, consistent rendering of animated silk or photo scenery across all launcher activities.
 */
object TvWallpaperHelper {

    fun apply(
        theme: String,
        silkView: SilkShaderView?,
        backdropView: ImageView?,
        dimmerView: View?
    ) {
        when (theme) {
            "SILK_VIOLET" -> setupSilk(0, silkView, backdropView, dimmerView)
            "SILK_CYAN" -> setupSilk(1, silkView, backdropView, dimmerView)
            "SILK_EMERALD" -> setupSilk(2, silkView, backdropView, dimmerView)
            "SILK_MIDNIGHT" -> setupSilk(3, silkView, backdropView, dimmerView)
            "SILK_CRIMSON" -> setupSilk(4, silkView, backdropView, dimmerView)
            else -> {
                silkView?.pauseRendering()
                silkView?.visibility = View.GONE
                backdropView?.visibility = View.VISIBLE
                backdropView?.setImageResource(R.drawable.backdrop_scenery)
                dimmerView?.visibility = View.VISIBLE
            }
        }
    }

    private fun setupSilk(
        palette: Int,
        silkView: SilkShaderView?,
        backdropView: ImageView?,
        dimmerView: View?
    ) {
        backdropView?.visibility = View.GONE
        dimmerView?.visibility = View.GONE
        silkView?.visibility = View.VISIBLE
        silkView?.setPalette(palette)
        silkView?.resumeRendering()
    }

    fun pause(silkView: SilkShaderView?) {
        silkView?.pauseRendering()
    }

    fun resume(silkView: SilkShaderView?) {
        if (silkView?.visibility == View.VISIBLE) {
            silkView.resumeRendering()
        }
    }
}
