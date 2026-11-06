package com.orbit.tv.launcher.ui.screens

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.orbit.tv.launcher.core.focus.TvFocusHelper
import com.orbit.tv.launcher.databinding.ActivityCastBinding

class CastActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCastBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCastBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupFocus()
        setupListeners()
    }

    private fun setupFocus() {
        TvFocusHelper.bindFocusScale(binding.btnNavApps, scaleRatio = 1.12f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnNavMedia, scaleRatio = 1.12f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnNavIptv, scaleRatio = 1.12f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnNavGames, scaleRatio = 1.12f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnNavCast, scaleRatio = 1.12f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnNavPower, scaleRatio = 1.12f, elevationDp = 8f)

        TvFocusHelper.bindFocusScale(binding.btnProtoMiracast, scaleRatio = 1.05f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnProtoChromecast, scaleRatio = 1.05f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnProtoAirplay, scaleRatio = 1.05f, elevationDp = 8f)

        binding.btnNavCast.isSelected = true
    }

    private fun setupListeners() {
        binding.btnNavMedia.setOnClickListener { finish() }
        binding.btnNavApps.setOnClickListener {
            startActivity(Intent(this, AppsHubActivity::class.java))
            finish()
        }

        // 1. Miracast / Screen Mirroring
        binding.btnProtoMiracast.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_CAST_SETTINGS))
            } catch (e: Exception) {
                try {
                    startActivity(Intent("android.settings.WIFI_DISPLAY_SETTINGS"))
                } catch (e2: Exception) {
                    Toast.makeText(this, "Miracast is managed by system firmware", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // 2. Google Cast
        binding.btnProtoChromecast.setOnClickListener {
            Toast.makeText(this, "Google Cast Receiver is active in background", Toast.LENGTH_SHORT).show()
        }

        // 3. Apple AirPlay
        binding.btnProtoAirplay.setOnClickListener {
            Toast.makeText(this, "AirPlay: Use compatible receiver app or Cast screen", Toast.LENGTH_SHORT).show()
        }
    }
}
