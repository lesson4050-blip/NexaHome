package com.orbit.tv.launcher.ui.screens

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.KeyEvent
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import com.orbit.tv.launcher.R
import com.orbit.tv.launcher.core.assistant.GoogleAssistantHelper
import com.orbit.tv.launcher.core.focus.TvFocusHelper
import com.orbit.tv.launcher.platform.wallpaper.TvWallpaperHelper
import com.orbit.tv.launcher.databinding.ActivityAppsHubBinding
import com.orbit.tv.launcher.features.home.HomeViewModel
import com.orbit.tv.launcher.features.home.adapters.AppsAdapter
import com.orbit.tv.launcher.features.home.adapters.RecommendedAppsAdapter
import com.orbit.tv.launcher.features.home.model.AppModel
import com.orbit.tv.launcher.features.home.model.RecommendedAppModel
import com.orbit.tv.launcher.ui.overlays.AppOptionsDialog
import com.orbit.tv.launcher.ui.overlays.SearchDialogFragment
import com.orbit.tv.launcher.ui.overlays.TvPowerDialog
import com.orbit.tv.launcher.ui.overlays.VpnQuickDialog
import kotlinx.coroutines.launch

class AppsHubActivity : AppCompatActivity() {

    enum class HubTab { ALL, FAVORITES, RECOMMENDED, HIDDEN }

    private lateinit var binding: ActivityAppsHubBinding
    private val viewModel: HomeViewModel by viewModels()
    private lateinit var appsGridAdapter: AppsAdapter
    private lateinit var recommendedAppsAdapter: RecommendedAppsAdapter
    private var currentTab: HubTab = HubTab.ALL
    private var cachedApps: List<AppModel> = emptyList()
    private val appPrefs by lazy { com.orbit.tv.launcher.features.apps.repository.AppPreferencesRepository(this) }

    private var speechRecognizer: SpeechRecognizer? = null
    private var isRecordingVoice: Boolean = false
    private var lastLaunchedAppPackage: String? = null

    private val audioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startInPlaceVoiceRecognition()
        }
    }

    private val rawCuratedApps = listOf(
        RecommendedAppModel(
            packageName = "org.smarttube.stable",
            name = "SmartTube",
            category = "Медиа",
            description = "YouTube без рекламы, 4K 60fps HDR, пропуск спонсорских вставок",
            iconRes = R.drawable.ic_brand_smarttube,
            directApkUrl = "https://github.com/yuliskov/SmartTube/releases/download/latest/smarttube_stable.apk"
        ),
        RecommendedAppModel(
            packageName = "org.videolan.vlc",
            name = "VLC Player",
            category = "Видеоплеер",
            description = "Всеядный медиаплеер с поддержкой AC3/DTS и сетевых дисков (SMB/NFS)",
            iconRes = R.drawable.ic_brand_vlc
        ),
        RecommendedAppModel(
            packageName = "org.courville.nova",
            name = "Nova Video Player",
            category = "Кинозал",
            description = "Плеер для Android TV с автоматической загрузкой постеров и описаний",
            iconRes = R.drawable.ic_brand_nova
        ),
        RecommendedAppModel(
            packageName = "com.ionitech.airscreen",
            name = "AirScreen",
            category = "Трансляция",
            description = "Беспроводной приемник Apple AirPlay, Google Cast, Miracast и DLNA",
            iconRes = R.drawable.ic_brand_airscreen
        ),
        RecommendedAppModel(
            packageName = "com.google.android.katniss",
            name = "Google Ассистент",
            category = "Система",
            description = "Голосовое управление и системный поиск для Android TV",
            iconRes = R.drawable.ic_brand_google_assistant
        ),
        RecommendedAppModel(
            packageName = "top.rootu.lampa",
            name = "Lampa TV",
            category = "Каталог",
            description = "Каталог фильмов и сериалов с трейлерами и онлайн-источниками",
            iconRes = R.drawable.ic_brand_lampa,
            directApkUrl = "https://github.com/lampa-app/LAMPA/releases/latest/download/app-lite-release.apk"
        ),
        RecommendedAppModel(
            packageName = "ch.protonvpn.android",
            name = "Proton VPN",
            category = "VPN & Сеть",
            description = "Официальный защищенный клиент с интерфейсом для Android TV",
            iconRes = R.drawable.ic_brand_proton_vpn
        ),
        RecommendedAppModel(
            packageName = "org.xbmc.kodi",
            name = "Kodi Media Center",
            category = "Медиацентр",
            description = "Мощный открытый комбайн для видео, плагинов и домашней медиатеки",
            iconRes = R.drawable.ic_brand_kodi
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppsHubBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupFocusControls()
        setupGrids()
        setupListeners()
        setupBackNavigation()
        observeData()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadInstalledApps()
        if (currentTab == HubTab.RECOMMENDED) {
            renderRecommendedList()
        }
        updateGoogleAssistantVisibility()
        TvWallpaperHelper.apply(appPrefs.getWallpaperTheme(), binding.silkShaderView, binding.ivBackdrop, binding.viewDimmer)
    }

    override fun onPause() {
        super.onPause()
        TvWallpaperHelper.pause(binding.silkShaderView)
        stopInPlaceVoiceRecognition()
        animateVoiceMicActive(false)
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        super.onDestroy()
    }

    private fun setupFocusControls() {
        // Sidebar navigation
        TvFocusHelper.bindFocusScale(binding.btnNavMedia, scaleRatio = 1.12f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnNavHistory, scaleRatio = 1.12f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnNavApps, scaleRatio = 1.12f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnNavIptv, scaleRatio = 1.12f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnNavGames, scaleRatio = 1.12f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnNavCast, scaleRatio = 1.12f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnNavPower, scaleRatio = 1.12f, elevationDp = 8f)

        // Top bar
        TvFocusHelper.bindFocusScale(binding.searchBar, scaleRatio = 1.04f, elevationDp = 6f)
        TvFocusHelper.bindFocusScale(binding.btnVoiceSearch, scaleRatio = 1.12f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnGoogleAssistant, scaleRatio = 1.08f, elevationDp = 6f)
        TvFocusHelper.bindFocusScale(binding.btnSystemVpn, scaleRatio = 1.08f, elevationDp = 6f)
        TvFocusHelper.bindFocusScale(binding.ivWifiStatus, scaleRatio = 1.12f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnSettings, scaleRatio = 1.12f, elevationDp = 8f)

        // Filter tabs
        TvFocusHelper.bindFocusScale(binding.btnTabAll, scaleRatio = 1.06f, elevationDp = 4f)
        TvFocusHelper.bindFocusScale(binding.btnTabFavorites, scaleRatio = 1.06f, elevationDp = 4f)
        TvFocusHelper.bindFocusScale(binding.btnTabRecommended, scaleRatio = 1.06f, elevationDp = 4f)
        TvFocusHelper.bindFocusScale(binding.btnTabHidden, scaleRatio = 1.06f, elevationDp = 4f)

        binding.btnNavApps.isSelected = true
        updateTabSelectionUI()
    }

    private fun setupGrids() {
        appsGridAdapter = AppsAdapter(
            showPinBadge = true,
            isGridMode = true,
            onAppClick = { app ->
                lastLaunchedAppPackage = app.packageName
                viewModel.launchApp(app)
            },
            onAppLongClick = { app ->
                showAppOptions(app)
            }
        )

        binding.rvAllApps.apply {
            layoutManager = GridLayoutManager(this@AppsHubActivity, 6)
            adapter = appsGridAdapter
        }

        recommendedAppsAdapter = RecommendedAppsAdapter { app ->
            onRecommendedAppAction(app)
        }

        binding.rvRecommendedApps.apply {
            layoutManager = GridLayoutManager(this@AppsHubActivity, 3)
            adapter = recommendedAppsAdapter
        }
    }

    private fun onRecommendedAppAction(app: RecommendedAppModel) {
        if (app.isInstalled) {
            if (app.packageName == "com.google.android.katniss") {
                if (GoogleAssistantHelper.launch(this)) {
                    return
                }
                startInPlaceVoiceRecognition()
                return
            }

            val launchIntent = packageManager.getLeanbackLaunchIntentForPackage(app.packageName)
                ?: packageManager.getLaunchIntentForPackage(app.packageName)
            if (launchIntent != null) {
                lastLaunchedAppPackage = app.packageName
                startActivity(launchIntent)
            }
            return
        }

        // Sideloaded / open-source TV apps not present in Google Play: download & install in-app
        if (!app.directApkUrl.isNullOrBlank()) {
            com.orbit.tv.launcher.ui.overlays.SideloadInstallDialog.newInstance(
                appName = app.name,
                packageName = app.packageName,
                apkUrl = app.directApkUrl,
                iconRes = app.iconRes
            ).show(supportFragmentManager, com.orbit.tv.launcher.ui.overlays.SideloadInstallDialog.TAG)
            return
        }

        // 1-Click install via Google Play TV with web fallback
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${app.packageName}")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            openBrowserUrl("https://play.google.com/store/apps/details?id=${app.packageName}")
        }
    }

    private fun openBrowserUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {}
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

        binding.btnNavHistory.setOnClickListener {
            startActivity(Intent(this, WatchHistoryActivity::class.java))
            finish()
        }

        binding.btnNavIptv.setOnClickListener {
            startActivity(Intent(this, IptvActivity::class.java))
            finish()
        }

        binding.btnNavGames.setOnClickListener {
            startActivity(Intent(this, OptimizerActivity::class.java))
            finish()
        }

        binding.btnNavCast.setOnClickListener {
            startActivity(Intent(this, CastActivity::class.java))
            finish()
        }

        binding.btnNavSettings.setOnClickListener {
            startActivity(Intent(this, LauncherSettingsActivity::class.java))
            finish()
        }

        binding.btnNavPower.setOnClickListener {
            TvPowerDialog.show(supportFragmentManager)
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

        binding.btnTabRecommended.setOnClickListener {
            currentTab = HubTab.RECOMMENDED
            updateTabSelectionUI()
            renderRecommendedList()
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
                } catch (e2: Exception) {}
            }
        }

        binding.btnSettings.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (e: Exception) {}
        }

        binding.searchBar.setOnClickListener {
            openSearchDialog()
        }

        binding.btnVoiceSearch.setOnClickListener {
            if (isRecordingVoice) {
                stopInPlaceVoiceRecognition()
            } else {
                startInPlaceVoiceRecognition()
            }
        }

        binding.btnGoogleAssistant.setOnClickListener {
            GoogleAssistantHelper.launch(this)
        }
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!binding.containerSidebar.hasFocus()) {
                    binding.btnNavApps.requestFocus()
                } else {
                    finish()
                }
            }
        })
    }

    private fun updateGoogleAssistantVisibility() {
        binding.btnGoogleAssistant.visibility = if (GoogleAssistantHelper.isAvailable(this)) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun animateVoiceMicActive(active: Boolean) {
        if (active) {
            binding.btnVoiceSearch.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#EA4335"))
            binding.btnVoiceSearch.scaleX = 1.20f
            binding.btnVoiceSearch.scaleY = 1.20f
        } else {
            binding.btnVoiceSearch.backgroundTintList = null
            val targetScale = if (binding.btnVoiceSearch.isFocused) 1.12f else 1.0f
            binding.btnVoiceSearch.animate()
                .scaleX(targetScale)
                .scaleY(targetScale)
                .setDuration(200)
                .start()
        }
    }

    private fun startInPlaceVoiceRecognition() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            openSearchDialog()
            return
        }

        try {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        isRecordingVoice = true
                        animateVoiceMicActive(true)
                        binding.tvSearchInput.text = getString(R.string.search_voice_listening)
                    }

                    override fun onBeginningOfSpeech() {}

                    override fun onRmsChanged(rmsdB: Float) {
                        val norm = (rmsdB.coerceIn(0f, 10f) / 10f)
                        val s = 1.18f + (norm * 0.16f)
                        binding.btnVoiceSearch.scaleX = s
                        binding.btnVoiceSearch.scaleY = s
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        binding.tvSearchInput.text = getString(R.string.search_voice_processing)
                    }

                    override fun onError(error: Int) {
                        isRecordingVoice = false
                        animateVoiceMicActive(false)
                        binding.tvSearchInput.text = getString(R.string.search_apps_hint)
                    }

                    override fun onResults(results: Bundle?) {
                        isRecordingVoice = false
                        animateVoiceMicActive(false)
                        binding.tvSearchInput.text = getString(R.string.search_apps_hint)

                        val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                        if (!spoken.isNullOrBlank()) {
                            openSearchDialog(spoken)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                        if (!partial.isNullOrBlank()) {
                            binding.tvSearchInput.text = partial
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            speechRecognizer?.startListening(speechIntent)
        } catch (e: Exception) {
            isRecordingVoice = false
            animateVoiceMicActive(false)
            openSearchDialog()
        }
    }

    private fun stopInPlaceVoiceRecognition() {
        if (isRecordingVoice) {
            speechRecognizer?.stopListening()
        }
    }

    private fun openSearchDialog(prefill: String? = null) {
        SearchDialogFragment.newInstance(prefill)
            .show(supportFragmentManager, SearchDialogFragment.TAG)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        when (event.keyCode) {
            KeyEvent.KEYCODE_VOICE_ASSIST,
            KeyEvent.KEYCODE_SEARCH,
            KeyEvent.KEYCODE_ASSIST -> {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    if (event.repeatCount == 0 && !isRecordingVoice) {
                        startInPlaceVoiceRecognition()
                    }
                    return true
                } else if (event.action == KeyEvent.ACTION_UP) {
                    stopInPlaceVoiceRecognition()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun updateTabSelectionUI() {
        binding.btnTabAll.isSelected = (currentTab == HubTab.ALL)
        binding.btnTabFavorites.isSelected = (currentTab == HubTab.FAVORITES)
        binding.btnTabRecommended.isSelected = (currentTab == HubTab.RECOMMENDED)
        binding.btnTabHidden.isSelected = (currentTab == HubTab.HIDDEN)
    }

    private fun renderCurrentList() {
        if (currentTab == HubTab.RECOMMENDED) {
            renderRecommendedList()
            return
        }

        binding.rvAllApps.visibility = View.VISIBLE
        binding.rvRecommendedApps.visibility = View.GONE

        val filtered = when (currentTab) {
            HubTab.ALL -> cachedApps.filter { !it.isHidden }
            HubTab.FAVORITES -> cachedApps.filter { it.isPinned && !it.isHidden }
            HubTab.HIDDEN -> cachedApps.filter { it.isHidden }
            HubTab.RECOMMENDED -> emptyList()
        }

        val freshList = ArrayList(filtered)
        appsGridAdapter.submitList(freshList) {
            val targetPkg = lastLaunchedAppPackage
            if (targetPkg != null) {
                val targetIdx = freshList.indexOfFirst { it.packageName == targetPkg }
                if (targetIdx != -1) {
                    binding.rvAllApps.post {
                        binding.rvAllApps.scrollToPosition(targetIdx)
                        binding.rvAllApps.findViewHolderForAdapterPosition(targetIdx)?.itemView?.requestFocus()
                    }
                    lastLaunchedAppPackage = null
                }
            }
        }
        binding.tvAppCount.text = getString(R.string.apps_count_format, filtered.size)

        if (filtered.isEmpty()) {
            binding.tvEmptyState.visibility = View.VISIBLE
            binding.tvEmptyState.text = when (currentTab) {
                HubTab.FAVORITES -> getString(R.string.apps_empty_favorites)
                HubTab.HIDDEN -> getString(R.string.apps_empty_hidden)
                else -> getString(R.string.apps_empty_all)
            }
        } else {
            binding.tvEmptyState.visibility = View.GONE
        }
    }

    private fun renderRecommendedList() {
        binding.rvAllApps.visibility = View.GONE
        binding.rvRecommendedApps.visibility = View.VISIBLE
        binding.tvEmptyState.visibility = View.GONE

        val packageAliases = mapOf(
            "org.smarttube.stable" to listOf("org.smarttube.stable", "com.teamsmart.videomanager.tv", "com.liskovsoft.videomanager"),
            "top.rootu.lampa" to listOf("top.rootu.lampa", "tv.lampa", "com.lampa.app", "ru.lampa.tv")
        )

        val pm = packageManager
        val list = rawCuratedApps.map { item ->
            var installed = false
            var icon = item.installedIcon
            var effectivePackage = item.packageName

            val candidates = packageAliases[item.packageName] ?: listOf(item.packageName)
            for (pkg in candidates) {
                try {
                    pm.getPackageInfo(pkg, 0)
                    installed = true
                    icon = pm.getApplicationIcon(pkg)
                    effectivePackage = pkg
                    break
                } catch (_: Exception) {}
            }
            item.copy(packageName = effectivePackage, isInstalled = installed, installedIcon = icon)
        }

        binding.tvAppCount.text = getString(R.string.apps_count_recommended, list.size)
        recommendedAppsAdapter.submitList(list)
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

                // Wi-Fi Status Indicator (Connected / Disconnected / Disabled)
                launch {
                    viewModel.wifiUiState.collect { state ->
                        when (state) {
                            com.orbit.tv.launcher.platform.device_capabilities.WifiUiState.DISABLED -> {
                                binding.ivWifiStatus.setImageResource(R.drawable.ic_wifi_off)
                                binding.ivWifiStatus.setColorFilter(null)
                                binding.ivWifiStatus.alpha = 0.9f
                            }
                            com.orbit.tv.launcher.platform.device_capabilities.WifiUiState.CONNECTED -> {
                                binding.ivWifiStatus.setImageResource(R.drawable.ic_wifi)
                                binding.ivWifiStatus.setColorFilter(null)
                                binding.ivWifiStatus.alpha = 1.0f
                            }
                            com.orbit.tv.launcher.platform.device_capabilities.WifiUiState.DISCONNECTED -> {
                                binding.ivWifiStatus.setImageResource(R.drawable.ic_wifi)
                                binding.ivWifiStatus.setColorFilter(null)
                                binding.ivWifiStatus.alpha = 0.55f
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

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOICE_ASSIST || keyCode == KeyEvent.KEYCODE_SEARCH) {
            if (isRecordingVoice) {
                stopInPlaceVoiceRecognition()
            } else {
                startInPlaceVoiceRecognition()
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
