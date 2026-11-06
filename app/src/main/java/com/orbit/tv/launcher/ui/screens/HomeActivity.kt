package com.orbit.tv.launcher.ui.screens

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.orbit.tv.launcher.R
import com.orbit.tv.launcher.core.focus.TvFocusHelper
import com.orbit.tv.launcher.databinding.ActivityHomeBinding
import com.orbit.tv.launcher.features.home.HomeViewModel
import com.orbit.tv.launcher.features.home.adapters.AppsAdapter
import com.orbit.tv.launcher.features.home.adapters.MediaAdapter
import kotlinx.coroutines.launch

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private val viewModel: HomeViewModel by viewModels()

    private lateinit var appsAdapter: AppsAdapter
    private lateinit var moviesAdapter: MediaAdapter
    private lateinit var seriesAdapter: MediaAdapter

    // Shared ViewPool between media shelves to minimize RAM allocations on 1GB RAM devices
    private val sharedMediaPool = RecyclerView.RecycledViewPool()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupFocusableControls()
        setupRecyclerViews()
        setupClickListeners()
        observeViewModelState()
    }

    override fun onResume() {
        super.onResume()
        // Refresh apps list in case packages were uninstalled/installed while user was in another app
        viewModel.loadInstalledApps()
    }

    private fun setupFocusableControls() {
        // Sidebar buttons focus animation
        TvFocusHelper.bindFocusScale(binding.btnNavApps, scaleRatio = 1.12f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnNavMedia, scaleRatio = 1.12f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnNavIptv, scaleRatio = 1.12f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnNavGames, scaleRatio = 1.12f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnNavCast, scaleRatio = 1.12f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnNavPower, scaleRatio = 1.12f, elevationDp = 8f)

        // Topbar controls focus animation
        TvFocusHelper.bindFocusScale(binding.searchBar, scaleRatio = 1.04f, elevationDp = 6f)
        TvFocusHelper.bindFocusScale(binding.btnVoiceSearch, scaleRatio = 1.12f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnSystemVpn, scaleRatio = 1.08f, elevationDp = 6f)
        TvFocusHelper.bindFocusScale(binding.btnSettings, scaleRatio = 1.12f, elevationDp = 8f)

        // Set initial selected nav state
        binding.btnNavApps.isSelected = true
    }

    private fun setupRecyclerViews() {
        // 1. Installed Apps Shelf
        appsAdapter = AppsAdapter { app ->
            val launched = viewModel.launchApp(app)
            if (!launched) {
                Toast.makeText(this, "Cannot launch ${app.label}", Toast.LENGTH_SHORT).show()
            }
        }
        binding.rvApps.apply {
            layoutManager = LinearLayoutManager(this@HomeActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = appsAdapter
            setHasFixedSize(true)
        }

        // 2. Movies Shelf
        moviesAdapter = MediaAdapter { media ->
            Toast.makeText(this, "${media.title} (${media.badge})", Toast.LENGTH_SHORT).show()
        }
        binding.rvMovies.apply {
            layoutManager = LinearLayoutManager(this@HomeActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = moviesAdapter
            setRecycledViewPool(sharedMediaPool)
            setHasFixedSize(true)
        }

        // 3. Series Shelf
        seriesAdapter = MediaAdapter { media ->
            Toast.makeText(this, "${media.title} (${media.badge})", Toast.LENGTH_SHORT).show()
        }
        binding.rvSeries.apply {
            layoutManager = LinearLayoutManager(this@HomeActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = seriesAdapter
            setRecycledViewPool(sharedMediaPool)
            setHasFixedSize(true)
        }
    }

    private fun setupClickListeners() {
        // Sidebar Navigation
        binding.btnNavApps.setOnClickListener {
            startActivity(Intent(this, AppsHubActivity::class.java))
        }
        binding.btnNavIptv.setOnClickListener {
            startActivity(Intent(this, IptvActivity::class.java))
        }
        binding.btnNavGames.setOnClickListener {
            startActivity(Intent(this, GamesActivity::class.java))
        }
        binding.btnNavCast.setOnClickListener {
            startActivity(Intent(this, CastActivity::class.java))
        }

        // VPN Widget
        binding.btnSystemVpn.setOnClickListener {
            viewModel.vpnCapabilityManager.openVpnSettings()
        }

        // Settings Button
        binding.btnSettings.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (e: Exception) {
                Toast.makeText(this, "Unable to open Settings", Toast.LENGTH_SHORT).show()
            }
        }

        // Search Bar
        binding.searchBar.setOnClickListener {
            try {
                val searchIntent = Intent(Intent.ACTION_ASSIST)
                startActivity(searchIntent)
            } catch (e: Exception) {
                // Fallback to voice search
                triggerVoiceSearch()
            }
        }

        // Voice Search
        binding.btnVoiceSearch.setOnClickListener {
            triggerVoiceSearch()
        }

        // Power Button
        binding.btnNavPower.setOnClickListener {
            // Open system power menu dialog
            try {
                val intent = Intent(Intent.ACTION_POWER_USAGE_SUMMARY)
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Sleep timer: press remote power key", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun triggerVoiceSearch() {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Voice search is not available on this device", Toast.LENGTH_SHORT).show()
        }
    }

    private fun observeViewModelState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Apps
                launch {
                    viewModel.installedApps.collect { apps ->
                        appsAdapter.submitList(apps)
                    }
                }

                // Movies
                launch {
                    viewModel.movies.collect { movies ->
                        moviesAdapter.submitList(movies)
                    }
                }

                // Series
                launch {
                    viewModel.series.collect { series ->
                        seriesAdapter.submitList(series)
                    }
                }

                // Clock & Date
                launch {
                    viewModel.currentTime.collect { time ->
                        binding.tvClock.text = time
                    }
                }
                launch {
                    viewModel.currentDate.collect { date ->
                        binding.tvDate.text = date
                    }
                }
                launch {
                    viewModel.currentDay.collect { day ->
                        binding.tvDay.text = day
                    }
                }

                // VPN Status
                launch {
                    viewModel.vpnCapabilityManager.isVpnActive.collect { isConnected ->
                        if (isConnected) {
                            binding.tvVpnStatus.text = getString(R.string.vpn_status_connected)
                            binding.tvVpnStatus.setTextColor(ContextCompat.getColor(this@HomeActivity, R.color.vpn_emerald))
                            binding.ivVpnIcon.setColorFilter(ContextCompat.getColor(this@HomeActivity, R.color.vpn_emerald))
                            binding.dotVpnStatus.backgroundTintList = ContextCompat.getColorStateList(this@HomeActivity, R.color.vpn_emerald)
                        } else {
                            binding.tvVpnStatus.text = getString(R.string.vpn_status_disconnected)
                            binding.tvVpnStatus.setTextColor(ContextCompat.getColor(this@HomeActivity, R.color.vpn_disconnected))
                            binding.ivVpnIcon.setColorFilter(ContextCompat.getColor(this@HomeActivity, R.color.vpn_disconnected))
                            binding.dotVpnStatus.backgroundTintList = ContextCompat.getColorStateList(this@HomeActivity, R.color.vpn_disconnected)
                        }
                    }
                }
            }
        }
    }
}
