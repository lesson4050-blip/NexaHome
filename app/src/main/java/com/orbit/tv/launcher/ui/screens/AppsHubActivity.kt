package com.orbit.tv.launcher.ui.screens

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import com.orbit.tv.launcher.R
import com.orbit.tv.launcher.core.focus.TvFocusHelper
import com.orbit.tv.launcher.databinding.ActivityAppsHubBinding
import com.orbit.tv.launcher.features.home.HomeViewModel
import com.orbit.tv.launcher.features.home.adapters.AppsAdapter
import kotlinx.coroutines.launch

class AppsHubActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppsHubBinding
    private val viewModel: HomeViewModel by viewModels()
    private lateinit var appsGridAdapter: AppsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppsHubBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupFocusControls()
        setupGrid()
        setupListeners()
        observeData()
    }

    private fun setupFocusControls() {
        TvFocusHelper.bindFocusScale(binding.btnNavApps, scaleRatio = 1.12f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnNavMedia, scaleRatio = 1.12f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnNavIptv, scaleRatio = 1.12f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnNavGames, scaleRatio = 1.12f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnNavCast, scaleRatio = 1.12f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnNavPower, scaleRatio = 1.12f, elevationDp = 8f)

        TvFocusHelper.bindFocusScale(binding.searchBar, scaleRatio = 1.04f, elevationDp = 6f)
        TvFocusHelper.bindFocusScale(binding.btnVoiceSearch, scaleRatio = 1.12f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnSystemVpn, scaleRatio = 1.08f, elevationDp = 6f)
        TvFocusHelper.bindFocusScale(binding.btnSettings, scaleRatio = 1.12f, elevationDp = 8f)

        binding.btnNavApps.isSelected = true
    }

    private fun setupGrid() {
        appsGridAdapter = AppsAdapter { app ->
            val launched = viewModel.launchApp(app)
            if (!launched) {
                Toast.makeText(this, "Cannot launch ${app.label}", Toast.LENGTH_SHORT).show()
            }
        }

        binding.rvAllApps.apply {
            layoutManager = GridLayoutManager(this@AppsHubActivity, 5)
            adapter = appsGridAdapter
            setHasFixedSize(true)
        }
    }

    private fun setupListeners() {
        binding.btnNavMedia.setOnClickListener {
            finish() // Return to Home screen
        }

        binding.btnSystemVpn.setOnClickListener {
            viewModel.vpnCapabilityManager.openVpnSettings()
        }

        binding.btnSettings.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (e: Exception) {
                Toast.makeText(this, "Unable to open Settings", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun observeData() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.installedApps.collect { apps ->
                        appsGridAdapter.submitList(apps)
                        binding.tvAppCount.text = "${apps.size} applications"
                    }
                }

                launch {
                    viewModel.currentTime.collect { time ->
                        binding.tvClock.text = time
                    }
                }

                launch {
                    viewModel.vpnCapabilityManager.isVpnActive.collect { isConnected ->
                        if (isConnected) {
                            binding.tvVpnStatus.text = getString(R.string.vpn_status_connected)
                            binding.tvVpnStatus.setTextColor(ContextCompat.getColor(this@AppsHubActivity, R.color.vpn_emerald))
                            binding.ivVpnIcon.setColorFilter(ContextCompat.getColor(this@AppsHubActivity, R.color.vpn_emerald))
                            binding.dotVpnStatus.backgroundTintList = ContextCompat.getColorStateList(this@AppsHubActivity, R.color.vpn_emerald)
                        } else {
                            binding.tvVpnStatus.text = getString(R.string.vpn_status_disconnected)
                            binding.tvVpnStatus.setTextColor(ContextCompat.getColor(this@AppsHubActivity, R.color.vpn_disconnected))
                            binding.ivVpnIcon.setColorFilter(ContextCompat.getColor(this@AppsHubActivity, R.color.vpn_disconnected))
                            binding.dotVpnStatus.backgroundTintList = ContextCompat.getColorStateList(this@AppsHubActivity, R.color.vpn_disconnected)
                        }
                    }
                }
            }
        }
    }
}
