package com.orbit.tv.launcher.ui.screens

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.orbit.tv.launcher.core.focus.TvFocusHelper
import com.orbit.tv.launcher.databinding.ActivityGamesBinding
import com.orbit.tv.launcher.features.home.HomeViewModel
import com.orbit.tv.launcher.features.home.adapters.AppsAdapter
import kotlinx.coroutines.launch

class GamesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGamesBinding
    private val viewModel: HomeViewModel by viewModels()
    private lateinit var gamesAdapter: AppsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGamesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupFocus()
        setupGamesList()
        setupListeners()
        observeData()
    }

    private fun setupFocus() {
        TvFocusHelper.bindFocusScale(binding.btnNavApps, scaleRatio = 1.12f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnNavMedia, scaleRatio = 1.12f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnNavIptv, scaleRatio = 1.12f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnNavGames, scaleRatio = 1.12f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnNavCast, scaleRatio = 1.12f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnNavPower, scaleRatio = 1.12f, elevationDp = 8f)

        TvFocusHelper.bindFocusScale(binding.cardGeforce, scaleRatio = 1.05f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.cardXbox, scaleRatio = 1.05f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.cardPsremote, scaleRatio = 1.05f, elevationDp = 8f)

        binding.btnNavGames.isSelected = true
    }

    private fun setupGamesList() {
        gamesAdapter = AppsAdapter { game ->
            val launched = viewModel.launchApp(game)
            if (!launched) {
                Toast.makeText(this, "Cannot launch ${game.label}", Toast.LENGTH_SHORT).show()
            }
        }

        binding.rvInstalledGames.apply {
            layoutManager = LinearLayoutManager(this@GamesActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = gamesAdapter
        }
    }

    private fun setupListeners() {
        binding.btnNavMedia.setOnClickListener { finish() }
        binding.btnNavApps.setOnClickListener {
            startActivity(Intent(this, AppsHubActivity::class.java))
            finish()
        }
        binding.btnNavIptv.setOnClickListener {
            startActivity(Intent(this, IptvActivity::class.java))
            finish()
        }
        binding.btnNavCast.setOnClickListener {
            startActivity(Intent(this, CastActivity::class.java))
            finish()
        }

        binding.cardGeforce.setOnClickListener {
            Toast.makeText(this, "Opening GeForce NOW", Toast.LENGTH_SHORT).show()
        }
        binding.cardXbox.setOnClickListener {
            Toast.makeText(this, "Opening Xbox Cloud Gaming", Toast.LENGTH_SHORT).show()
        }
        binding.cardPsremote.setOnClickListener {
            Toast.makeText(this, "Opening PlayStation Remote Play", Toast.LENGTH_SHORT).show()
        }
    }

    private fun observeData() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.installedApps.collect { apps ->
                        val games = apps.filter { it.isGame }
                        gamesAdapter.submitList(if (games.isNotEmpty()) games else apps)
                    }
                }
                launch {
                    viewModel.currentTime.collect { time ->
                        binding.tvClock.text = time
                    }
                }
            }
        }
    }
}
