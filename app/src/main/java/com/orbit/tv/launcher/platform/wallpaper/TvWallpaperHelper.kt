package com.orbit.tv.launcher.platform.wallpaper

import android.view.View
import android.widget.ImageView
import coil.load
import com.orbit.tv.launcher.R
import com.orbit.tv.launcher.ui.views.SilkShaderView
import java.io.File

/**
 * Universal wallpaper coordinator for Android TV screens.
 * Ensures instant, consistent rendering of animated silk, 4K nature backdrops,
 * or custom user-uploaded backgrounds across all launcher activities.
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
            "SCENERY_AURORA" -> setupStatic(R.drawable.backdrop_aurora, silkView, backdropView, dimmerView)
            "SCENERY_ALPINE" -> setupStatic(R.drawable.backdrop_alpine, silkView, backdropView, dimmerView)
            "SCENERY_FOREST" -> setupStatic(R.drawable.backdrop_forest, silkView, backdropView, dimmerView)
            "SCENERY_LAKE" -> setupStatic(R.drawable.backdrop_lake, silkView, backdropView, dimmerView)
            "CUSTOM" -> {
                val customFile = backdropView?.context?.let { File(it.filesDir, "custom_wallpaper.jpg") }
                if (customFile != null && customFile.exists()) {
                    silkView?.pauseRendering()
                    silkView?.visibility = View.GONE
                    backdropView.visibility = View.VISIBLE
                    backdropView.load(customFile) {
                        crossfade(true)
                    }
                    dimmerView?.setBackgroundResource(R.drawable.bg_top_subtle_gradient)
                    dimmerView?.visibility = View.VISIBLE
                } else {
                    setupStatic(R.drawable.backdrop_scenery, silkView, backdropView, dimmerView)
                }
            }
            else -> setupStatic(R.drawable.backdrop_scenery, silkView, backdropView, dimmerView)
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

    private fun setupStatic(
        drawableRes: Int,
        silkView: SilkShaderView?,
        backdropView: ImageView?,
        dimmerView: View?
    ) {
        silkView?.pauseRendering()
        silkView?.visibility = View.GONE
        backdropView?.visibility = View.VISIBLE
        backdropView?.setImageResource(drawableRes)
        dimmerView?.setBackgroundResource(R.drawable.bg_top_subtle_gradient)
        dimmerView?.visibility = View.VISIBLE
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
