package com.orbit.tv.launcher.ui.screens

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.orbit.tv.launcher.core.focus.TvFocusHelper
import com.orbit.tv.launcher.databinding.ActivityIptvBinding

class IptvActivity : AppCompatActivity() {

    private lateinit var binding: ActivityIptvBinding
    private var exoPlayer: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityIptvBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupFocus()
        setupListeners()
        initPreviewPlayer()
    }

    private fun setupFocus() {
        TvFocusHelper.bindFocusScale(binding.btnNavApps, scaleRatio = 1.12f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnNavMedia, scaleRatio = 1.12f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnNavIptv, scaleRatio = 1.12f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnNavGames, scaleRatio = 1.12f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnNavCast, scaleRatio = 1.12f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnNavPower, scaleRatio = 1.12f, elevationDp = 8f)

        TvFocusHelper.bindFocusScale(binding.playerPreviewContainer, scaleRatio = 1.04f, elevationDp = 10f)
        TvFocusHelper.bindFocusScale(binding.btnCatAll, scaleRatio = 1.06f, elevationDp = 6f)
        TvFocusHelper.bindFocusScale(binding.btnCatSports, scaleRatio = 1.06f, elevationDp = 6f)
        TvFocusHelper.bindFocusScale(binding.btnCatCinema, scaleRatio = 1.06f, elevationDp = 6f)
        TvFocusHelper.bindFocusScale(binding.btnCatNews, scaleRatio = 1.06f, elevationDp = 6f)

        binding.btnNavIptv.isSelected = true
    }

    private fun initPreviewPlayer() {
        try {
            exoPlayer = ExoPlayer.Builder(this).build()
            binding.playerView.player = exoPlayer

            // Public legal Big Buck Bunny sample stream for testing Media3 player hardware decoding
            val sampleMediaItem = MediaItem.fromUri("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4")
            exoPlayer?.setMediaItem(sampleMediaItem)
            exoPlayer?.prepare()
            exoPlayer?.playWhenReady = false // don't autoplay audio
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupListeners() {
        binding.btnNavMedia.setOnClickListener { finish() }
        binding.btnNavApps.setOnClickListener {
            startActivity(Intent(this, AppsHubActivity::class.java))
            finish()
        }
        binding.btnNavCast.setOnClickListener {
            startActivity(Intent(this, CastActivity::class.java))
            finish()
        }

        binding.btnCatAll.setOnClickListener {
            Toast.makeText(this, "Showing all 840 channels", Toast.LENGTH_SHORT).show()
        }
        binding.btnCatSports.setOnClickListener {
            Toast.makeText(this, "Filtered to Sports (48 channels)", Toast.LENGTH_SHORT).show()
        }
        binding.btnCatCinema.setOnClickListener {
            Toast.makeText(this, "Filtered to Cinema (124 channels)", Toast.LENGTH_SHORT).show()
        }
        binding.btnCatNews.setOnClickListener {
            Toast.makeText(this, "Filtered to News (62 channels)", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer?.release()
        exoPlayer = null
    }
}
