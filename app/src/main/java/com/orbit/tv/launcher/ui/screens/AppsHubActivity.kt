package com.orbit.tv.launcher.ui.screens

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
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
import com.orbit.tv.launcher.features.home.model.AppModel
import android.speech.RecognizerIntent
import com.orbit.tv.launcher.ui.overlays.AppOptionsDialog
import com.orbit.tv.launcher.ui.overlays.SearchDialogFragment
import com.orbit.tv.launcher.ui.overlays.VpnQuickDialog
import kotlinx.coroutines.launch

class AppsHubActivity : AppCompatActivity() {

    enum class HubTab { ALL, FAVORITES, HIDDEN }

    private lateinit var binding: ActivityAppsHubBinding
    private val viewModel: HomeViewModel by viewModels()
    private lateinit var appsGridAdapter: AppsAdapter
    private var currentTab: HubTab = HubTab.ALL
    private var cachedApps: List<AppModel> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppsHubBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupFocusControls()
        setupGrid()
        setupListeners()
        observeData()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadInstalledApps()
    }

    private fun setupFocusControls() {
        // Sidebar navigation
        TvFocusHelper.bindFocusScale(binding.btnNavApps, scaleRatio = 1.12f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnNavMedia, scaleRatio = 1.12f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnNavIptv, scaleRatio = 1.12f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnNavGames, scaleRatio = 1.12f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnNavCast, scaleRatio = 1.12f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnNavPower, scaleRatio = 1.12f, elevationDp = 8f)

        // Top bar
        TvFocusHelper.bindFocusScale(binding.searchBar, scaleRatio = 1.04f, elevationDp = 6f)
        TvFocusHelper.bindFocusScale(binding.btnVoiceSearch, scaleRatio = 1.12f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnSystemVpn, scaleRatio = 1.08f, elevationDp = 6f)
        TvFocusHelper.bindFocusScale(binding.ivWifiStatus, scaleRatio = 1.12f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnSettings, scaleRatio = 1.12f, elevationDp = 8f)

        // Filter tabs
        TvFocusHelper.bindFocusScale(binding.btnTabAll, scaleRatio = 1.06f, elevationDp = 4f)
        TvFocusHelper.bindFocusScale(binding.btnTabFavorites, scaleRatio = 1.06f, elevationDp = 4f)
        TvFocusHelper.bindFocusScale(binding.btnTabHidden, scaleRatio = 1.06f, elevationDp = 4f)

        binding.btnNavApps.isSelected = true
        updateTabSelectionUI()
    }

    private fun setupGrid() {
        appsGridAdapter = AppsAdapter(
            onAppClick = { app ->
                val launched = viewModel.launchApp(app)
                if (!launched) {
                    Toast.makeText(this, "Cannot launch ${app.label}", Toast.LENGTH_SHORT).show()
                }
            },
            onAppLongClick = { app ->
                showAppOptions(app)
            }
        )

        binding.rvAllApps.apply {
            layoutManager = GridLayoutManager(this@AppsHubActivity, 5)
            adapter = appsGridAdapter
        }
    }

    private fun showAppOptions(app: AppModel) {
        val dialog = AppOptionsDialog.newInstance(app) {
            viewModel.loadInstalledApps()
        }
        dialog.show(supportFragmentManager, AppOptionsDialog.TAG)
    }

    private fun setupListeners() {
        binding.btnNavMedia.setOnClickListener {
            finish() // Return to Home screen
        }

        binding.btnNavIptv.setOnClickListener {
            val intent = Intent(this, IptvActivity::class.java)
            startActivity(intent)
        }

        binding.btnTabAll.setOnClickListener {
            currentTab = HubTab.ALL
            updateTabSelectionUI()
            renderCurrentList()
        }

        binding.btnTabFavorites.setOnClickListener {
            currentTab = HubTab.FAVORITES
            updateTabSelectionUI()
            renderCurrentList()
        }

        binding.btnTabHidden.setOnClickListener {
            currentTab = HubTab.HIDDEN
            updateTabSelectionUI()
            renderCurrentList()
        }

        binding.btnSystemVpn.setOnClickListener {
            val dialog = VpnQuickDialog()
            dialog.show(supportFragmentManager, VpnQuickDialog.TAG)
        }

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

        binding.btnSettings.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (e: Exception) {
                Toast.makeText(this, "Unable to open Settings", Toast.LENGTH_SHORT).show()
            }
        }

        binding.searchBar.setOnClickListener {
            SearchDialogFragment.newInstance()
                .show(supportFragmentManager, SearchDialogFragment.TAG)
        }

        binding.btnVoiceSearch.setOnClickListener {
            val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            }
            if (packageManager.queryIntentActivities(speechIntent, 0).isNotEmpty()) {
                try {
                    startActivity(speechIntent)
                    return@setOnClickListener
                } catch (e: Exception) {}
            }
            SearchDialogFragment.newInstance()
                .show(supportFragmentManager, SearchDialogFragment.TAG)
        }
    }

    private fun updateTabSelectionUI() {
        binding.btnTabAll.isSelected = (currentTab == HubTab.ALL)
        binding.btnTabFavorites.isSelected = (currentTab == HubTab.FAVORITES)
        binding.btnTabHidden.isSelected = (currentTab == HubTab.HIDDEN)
    }

    private fun renderCurrentList() {
        val filtered = when (currentTab) {
            HubTab.ALL -> cachedApps.filter { !it.isHidden }
            HubTab.FAVORITES -> cachedApps.filter { it.isPinned && !it.isHidden }
            HubTab.HIDDEN -> cachedApps.filter { it.isHidden }
        }

        val freshList = ArrayList(filtered)
        appsGridAdapter.submitList(freshList) {
            binding.rvAllApps.post {
                binding.rvAllApps.requestLayout()
            }
        }
        binding.tvAppCount.text = "${filtered.size} applications"

        if (filtered.isEmpty()) {
            binding.tvEmptyState.visibility = View.VISIBLE
            binding.tvEmptyState.text = when (currentTab) {
                HubTab.FAVORITES -> "No favorite apps pinned yet.\nHold [OK] or [MENU] on any app to pin it here."
                HubTab.HIDDEN -> "No hidden apps.\nHold [OK] or [MENU] on bloatware apps to hide them."
                HubTab.ALL -> "No applications found."
            }
        } else {
            binding.tvEmptyState.visibility = View.GONE
        }
    }

    private fun observeData() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.rawInstalledApps.collect { apps ->
                        cachedApps = apps
                        renderCurrentList()
                    }
                }

                launch {
                    viewModel.currentTime.collect { time ->
                        binding.tvClock.text = time
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

                launch {
                    viewModel.vpnManager.isVpnActive.collect { isConnected ->
                        if (isConnected) {
                            binding.tvVpnStatus.text = viewModel.vpnManager.vpnCountryCode.value
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
                launch {
                    viewModel.vpnManager.vpnCountryCode.collect { code ->
                        if (viewModel.vpnManager.isVpnActive.value) {
                            binding.tvVpnStatus.text = code
                        }
                    }
                }
            }
        }
    }
}
