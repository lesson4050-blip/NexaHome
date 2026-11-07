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
import com.orbit.tv.launcher.ui.overlays.MediaDetailDialog
import com.orbit.tv.launcher.ui.overlays.SearchDialogFragment
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
        setupBackNavigation()
        observeViewModelState()
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // TV Launcher rule: Never exit the launcher on Back press.
                // Return focus to top-level sidebar navigation instead.
                binding.btnNavMedia.requestFocus()
            }
        })
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
        TvFocusHelper.bindFocusScale(binding.ivWifiStatus, scaleRatio = 1.12f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnSettings, scaleRatio = 1.12f, elevationDp = 8f)

        // Set initial selected nav state: Home button
        binding.btnNavMedia.isSelected = true
        binding.btnNavApps.isSelected = false
    }

    private fun setupRecyclerViews() {
        // 1. Installed Apps Shelf (showPinBadge = false: clean cards without pin needle)
        appsAdapter = AppsAdapter(
            showPinBadge = false,
            onAppClick = { app ->
                val launched = viewModel.launchApp(app)
                if (!launched) {
                    Toast.makeText(this, "Cannot launch ${app.label}", Toast.LENGTH_SHORT).show()
                }
            },
            onAppLongClick = { app ->
                val dialog = com.orbit.tv.launcher.ui.overlays.AppOptionsDialog.newInstance(app) {
                    viewModel.loadInstalledApps()
                }
                dialog.show(supportFragmentManager, com.orbit.tv.launcher.ui.overlays.AppOptionsDialog.TAG)
            }
        )
        binding.rvApps.apply {
            layoutManager = LinearLayoutManager(this@HomeActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = appsAdapter
        }

        // 2. Movies Shelf
        moviesAdapter = MediaAdapter { media ->
            MediaDetailDialog.newInstance(media).show(supportFragmentManager, MediaDetailDialog.TAG)
        }
        binding.rvMovies.apply {
            layoutManager = LinearLayoutManager(this@HomeActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = moviesAdapter
            setRecycledViewPool(sharedMediaPool)
        }

        // 3. Series Shelf
        seriesAdapter = MediaAdapter { media ->
            MediaDetailDialog.newInstance(media).show(supportFragmentManager, MediaDetailDialog.TAG)
        }
        binding.rvSeries.apply {
            layoutManager = LinearLayoutManager(this@HomeActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = seriesAdapter
            setRecycledViewPool(sharedMediaPool)
        }
    }

    private fun setupClickListeners() {
        // Sidebar Navigation: 1. Home, 2. Apps, 3. IPTV, 4. Games, 5. Cast
        binding.btnNavMedia.setOnClickListener {
            binding.scrollContainer.smoothScrollTo(0, 0)
        }
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
            com.orbit.tv.launcher.ui.overlays.VpnQuickDialog.newInstance()
                .show(supportFragmentManager, com.orbit.tv.launcher.ui.overlays.VpnQuickDialog.TAG)
        }

        // Wi-Fi Button -> Native Android TV Wi-Fi Settings
        binding.ivWifiStatus.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
            } catch (e: Exception) {
                try {
                    startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
                } catch (e2: Exception) {
                    Toast.makeText(this, "Unable to open Wi-Fi Settings", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Settings Button -> Native Android TV System Settings
        binding.btnSettings.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (e: Exception) {
                Toast.makeText(this, "Unable to open Settings", Toast.LENGTH_SHORT).show()
            }
        }

        // Search Bar -> Open in-app search overlay
        binding.searchBar.setOnClickListener {
            openSearchDialog()
        }

        // Voice Search -> Try Google Assistant / Voice Search, or fallback to SearchDialog
        binding.btnVoiceSearch.setOnClickListener {
            handleVoiceSearch()
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

    private fun handleVoiceSearch() {
        // 1. Try launching Google Assistant if installed on Android TV
        val assistantIntent = Intent("android.intent.action.VOICE_ASSIST")
        if (intentResolves(assistantIntent)) {
            try {
                startActivity(assistantIntent)
                return
            } catch (e: Exception) {}
        }

        // 2. Try Android Speech Recognizer intent
        val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        }
        if (intentResolves(speechIntent)) {
            try {
                startActivity(speechIntent)
                return
            } catch (e: Exception) {}
        }

        // 3. Fallback: Open in-app Search Dialog with keyboard focused
        openSearchDialog()
    }

    private fun openSearchDialog() {
        SearchDialogFragment.newInstance()
            .show(supportFragmentManager, SearchDialogFragment.TAG)
    }

    private fun intentResolves(intent: Intent): Boolean {
        return packageManager.queryIntentActivities(intent, 0).isNotEmpty()
    }

    private fun observeViewModelState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Apps
                launch {
                    viewModel.installedApps.collect { apps ->
                        appsAdapter.submitList(apps) {
                            if (apps.isNotEmpty() && !binding.rvApps.hasFocus() && !binding.rvMovies.hasFocus() && !binding.rvSeries.hasFocus()) {
                                binding.rvApps.post {
                                    binding.rvApps.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
                                }
                            }
                        }
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

                // VPN Status & Dynamic Country Code
                launch {
                    viewModel.vpnManager.isVpnActive.collect { isConnected ->
                        if (isConnected) {
                            binding.tvVpnStatus.text = viewModel.vpnManager.vpnCountryCode.value
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
                launch {
                    viewModel.vpnManager.vpnCountryCode.collect { code ->
                        if (viewModel.vpnManager.isVpnActive.value) {
                            binding.tvVpnStatus.text = code
                        }
                    }
                }

                // Wi-Fi / Network Status Indicator (Connected / Disconnected)
                launch {
                    viewModel.networkStatus.collect { status ->
                        when (status) {
                            com.orbit.tv.launcher.platform.device_capabilities.NetworkStatus.WIFI_CONNECTED,
                            com.orbit.tv.launcher.platform.device_capabilities.NetworkStatus.ETHERNET_CONNECTED -> {
                                binding.ivWifiStatus.setImageResource(R.drawable.ic_wifi)
                                binding.ivWifiStatus.setColorFilter(null)
                                binding.ivWifiStatus.alpha = 1.0f
                            }
                            com.orbit.tv.launcher.platform.device_capabilities.NetworkStatus.DISCONNECTED -> {
                                binding.ivWifiStatus.setImageResource(R.drawable.ic_wifi_off)
                                binding.ivWifiStatus.setColorFilter(null)
                                binding.ivWifiStatus.alpha = 0.9f
                            }
                        }
                    }
                }
            }
        }
    }
}
