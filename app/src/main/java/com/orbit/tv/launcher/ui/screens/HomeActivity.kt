package com.orbit.tv.launcher.ui.screens

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.orbit.tv.launcher.R
import com.orbit.tv.launcher.core.assistant.GoogleAssistantHelper
import com.orbit.tv.launcher.core.focus.TvFocusHelper
import com.orbit.tv.launcher.platform.wallpaper.TvWallpaperHelper
import com.orbit.tv.launcher.databinding.ActivityHomeBinding
import com.orbit.tv.launcher.features.home.HomeViewModel
import com.orbit.tv.launcher.features.home.adapters.AppsAdapter
import com.orbit.tv.launcher.features.home.adapters.MediaAdapter
import android.view.KeyEvent
import android.view.View
import android.view.ViewParent
import com.orbit.tv.launcher.ui.overlays.MediaDetailDialog
import com.orbit.tv.launcher.ui.overlays.SearchDialogFragment
import com.orbit.tv.launcher.ui.overlays.TvOptionPickerDialog
import com.orbit.tv.launcher.ui.overlays.TvPowerDialog
import kotlinx.coroutines.launch

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private val viewModel: HomeViewModel by viewModels()

    private lateinit var appsAdapter: AppsAdapter
    private lateinit var moviesAdapter: MediaAdapter
    private lateinit var seriesAdapter: MediaAdapter

    private val appPrefs by lazy { com.orbit.tv.launcher.features.apps.repository.AppPreferencesRepository(this) }

    // Shared ViewPool between media shelves to minimize RAM allocations on 1GB RAM devices
    private val sharedMediaPool = RecyclerView.RecycledViewPool()

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val t0 = android.os.SystemClock.uptimeMillis()
        binding = ActivityHomeBinding.inflate(layoutInflater)
        val t1 = android.os.SystemClock.uptimeMillis()
        setContentView(binding.root)
        val t2 = android.os.SystemClock.uptimeMillis()

        setupFocusableControls()
        val t3 = android.os.SystemClock.uptimeMillis()
        setupRecyclerViews()
        val t4 = android.os.SystemClock.uptimeMillis()
        setupClickListeners()
        val t5 = android.os.SystemClock.uptimeMillis()
        setupBackNavigation()
        val t6 = android.os.SystemClock.uptimeMillis()
        setupShelfScrollCoordination()
        val t7 = android.os.SystemClock.uptimeMillis()
        applyHomeLayoutMode()
        val t8 = android.os.SystemClock.uptimeMillis()
        observeViewModelState()
        val t9 = android.os.SystemClock.uptimeMillis()

        android.util.Log.i("OrbitPerf", "INFLATE=${t1-t0}ms, SET_VIEW=${t2-t1}ms, FOCUS=${t3-t2}ms, RVS=${t4-t3}ms, CLICKS=${t5-t4}ms, BACK=${t6-t5}ms, COORD=${t7-t6}ms, MODE=${t8-t7}ms, VM_OBS=${t9-t8}ms, TOTAL_ON_CREATE=${t9-t0}ms")

        binding.root.post {
            val tPost = android.os.SystemClock.uptimeMillis()
            android.util.Log.i("OrbitPerf", "FIRST_FRAME_POST=${tPost-t0}ms")
            lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                kotlinx.coroutines.delay(1200)
                com.orbit.tv.launcher.features.cast.CastServerManager.start(applicationContext)
            }
        }
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

    private var silkShaderView: com.orbit.tv.launcher.ui.views.SilkShaderView? = null

    private fun getOrInflateSilkShader(): com.orbit.tv.launcher.ui.views.SilkShaderView? {
        if (silkShaderView != null) return silkShaderView
        val theme = appPrefs.getWallpaperTheme()
        if (theme.startsWith("SILK")) {
            val stub = binding.stubSilkShader
            silkShaderView = stub.inflate() as? com.orbit.tv.launcher.ui.views.SilkShaderView
        }
        return silkShaderView
    }

    private val wallpaperReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            TvWallpaperHelper.apply(appPrefs.getWallpaperTheme(), getOrInflateSilkShader(), binding.ivBackdrop, binding.viewDimmer)
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = android.content.IntentFilter("com.orbit.tv.launcher.ACTION_WALLPAPER_CHANGED")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(wallpaperReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(wallpaperReceiver, filter)
        }
    }

    override fun onStop() {
        super.onStop()
        try {
            unregisterReceiver(wallpaperReceiver)
        } catch (_: Exception) {}
    }

    private var isFirstResume = true

    override fun onResume() {
        super.onResume()
        java.util.Locale.setDefault(viewModel.getActiveLocale())
        if (isFirstResume) {
            isFirstResume = false
            updateGoogleAssistantVisibility()
            TvWallpaperHelper.apply(appPrefs.getWallpaperTheme(), getOrInflateSilkShader(), binding.ivBackdrop, binding.viewDimmer)
        } else {
            applyHomeLayoutMode()
            // Refresh apps list in case packages were uninstalled/installed while user was in another app
            viewModel.loadInstalledApps()
            viewModel.checkAndReloadMediaIfNeeded()
            updateGoogleAssistantVisibility()
            TvWallpaperHelper.apply(appPrefs.getWallpaperTheme(), getOrInflateSilkShader(), binding.ivBackdrop, binding.viewDimmer)
        }
    }

    override fun onPause() {
        super.onPause()
        TvWallpaperHelper.pause(silkShaderView)
        stopInPlaceVoiceRecognition()
        animateVoiceMicActive(false)
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        super.onDestroy()
    }

    private fun setupFocusableControls() {
        // Sidebar buttons focus animation
        TvFocusHelper.bindFocusScale(binding.btnNavMedia, scaleRatio = 1.12f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnNavHistory, scaleRatio = 1.12f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnNavApps, scaleRatio = 1.12f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnNavIptv, scaleRatio = 1.12f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnNavGames, scaleRatio = 1.12f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnNavCast, scaleRatio = 1.12f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnNavSettings, scaleRatio = 1.12f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnNavPower, scaleRatio = 1.12f, elevationDp = 8f)

        // Topbar controls focus animation
        TvFocusHelper.bindFocusScale(binding.searchBar, scaleRatio = 1.04f, elevationDp = 6f)
        TvFocusHelper.bindFocusScale(binding.btnVoiceSearch, scaleRatio = 1.12f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnGoogleAssistant, scaleRatio = 1.08f, elevationDp = 6f)
        TvFocusHelper.bindFocusScale(binding.btnSystemVpn, scaleRatio = 1.08f, elevationDp = 6f)
        TvFocusHelper.bindFocusScale(binding.ivWifiStatus, scaleRatio = 1.12f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnSettings, scaleRatio = 1.12f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnFilterMovieGenre, scaleRatio = 1.08f, elevationDp = 6f)
        TvFocusHelper.bindFocusScale(binding.btnFilterSeriesGenre, scaleRatio = 1.08f, elevationDp = 6f)

        // Set initial selected nav state: Home button
        binding.btnNavMedia.isSelected = true
        binding.btnNavApps.isSelected = false
    }

    private fun setupRecyclerViews() {
        // 1. Installed Apps Shelf (showPinBadge = false: clean cards without pin needle)
        appsAdapter = AppsAdapter(
            showPinBadge = false,
            onAppClick = { app ->
                lastLaunchedAppPackage = app.packageName
                if (com.orbit.tv.launcher.features.home.TvDeepLinkHelper.isMediaApp(app.packageName)) {
                    val label = com.orbit.tv.launcher.features.home.TvDeepLinkHelper.getCategoryLabel(app.packageName) ?: "Медиа"
                    lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        try {
                            com.orbit.tv.launcher.features.home.data.WatchHistoryRepository(this@HomeActivity).recordWatched(
                                com.orbit.tv.launcher.features.home.model.MediaModel(
                                    id = "app_${app.packageName}",
                                    title = app.label,
                                    posterUrl = "",
                                    badge = label,
                                    genre = "Приложение",
                                    rating = "APP",
                                    description = app.label,
                                    releaseYear = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date()),
                                    isSeries = false,
                                    deeplinkUri = "package:${app.packageName}"
                                ),
                                targetAppName = app.label
                            )
                        } catch (_: Exception) {}
                    }
                }
                viewModel.launchApp(app)
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
            setHasFixedSize(true)
            setItemViewCacheSize(6)
        }

        // 2. Movies Shelf
        moviesAdapter = MediaAdapter { media ->
            MediaDetailDialog.newInstance(media).show(supportFragmentManager, MediaDetailDialog.TAG)
        }
        binding.rvMovies.apply {
            layoutManager = LinearLayoutManager(this@HomeActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = moviesAdapter
            setRecycledViewPool(sharedMediaPool)
            setHasFixedSize(true)
            setItemViewCacheSize(6)
        }

        // 3. Series Shelf
        seriesAdapter = MediaAdapter { media ->
            MediaDetailDialog.newInstance(media).show(supportFragmentManager, MediaDetailDialog.TAG)
        }
        binding.rvSeries.apply {
            layoutManager = LinearLayoutManager(this@HomeActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = seriesAdapter
            setRecycledViewPool(sharedMediaPool)
            setHasFixedSize(true)
            setItemViewCacheSize(6)
        }
        sharedMediaPool.setMaxRecycledViews(0, 10)

        val shaderScrollThrottleListener = object : RecyclerView.OnScrollListener() {
            private val resumeRunnable = Runnable { silkShaderView?.resumeRendering() }
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING || newState == RecyclerView.SCROLL_STATE_SETTLING) {
                    binding.root.removeCallbacks(resumeRunnable)
                    silkShaderView?.pauseRendering()
                } else if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    binding.root.removeCallbacks(resumeRunnable)
                    binding.root.postDelayed(resumeRunnable, 350)
                }
            }
        }

        binding.rvApps.addOnScrollListener(shaderScrollThrottleListener)
        binding.rvMovies.addOnScrollListener(shaderScrollThrottleListener)
        binding.rvSeries.addOnScrollListener(shaderScrollThrottleListener)
    }

    private fun setupClickListeners() {
        // Sidebar Navigation: 1. Home, 2. History, 3. Apps, 4. IPTV, 5. Games, 6. Cast
        binding.btnNavMedia.setOnClickListener {
            binding.scrollContainer.smoothScrollTo(0, 0)
        }
        binding.btnNavHistory.setOnClickListener {
            com.orbit.tv.launcher.core.navigation.TvNavHelper.openScreen(this, WatchHistoryActivity::class.java)
        }
        binding.btnNavApps.setOnClickListener {
            com.orbit.tv.launcher.core.navigation.TvNavHelper.openScreen(this, AppsHubActivity::class.java)
        }
        binding.btnNavIptv.setOnClickListener {
            com.orbit.tv.launcher.core.navigation.TvNavHelper.openScreen(this, IptvActivity::class.java)
        }
        binding.btnNavGames.setOnClickListener {
            com.orbit.tv.launcher.core.navigation.TvNavHelper.openScreen(this, OptimizerActivity::class.java)
        }
        binding.btnNavCast.setOnClickListener {
            com.orbit.tv.launcher.core.navigation.TvNavHelper.openScreen(this, CastActivity::class.java)
        }
        binding.btnNavSettings.setOnClickListener {
            com.orbit.tv.launcher.core.navigation.TvNavHelper.openScreen(this, LauncherSettingsActivity::class.java)
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
                } catch (e2: Exception) {}
            }
        }

        // Settings Button -> Native Android TV System Settings
        binding.btnSettings.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (e: Exception) {}
        }

        // Search Bar -> Open in-app search overlay
        binding.searchBar.setOnClickListener {
            openSearchDialog()
        }

        // Voice Search -> In-place Speech Recognizer (no popups)
        binding.btnVoiceSearch.setOnClickListener {
            if (isRecordingVoice) {
                stopInPlaceVoiceRecognition()
            } else {
                startInPlaceVoiceRecognition()
            }
        }

        // Google Assistant -> Dedicated System Assistant launch
        binding.btnGoogleAssistant.setOnClickListener {
            GoogleAssistantHelper.launch(this)
        }

        // Power Button
        binding.btnNavPower.setOnClickListener {
            TvPowerDialog.show(supportFragmentManager)
        }

        // Movie Genre Dropdown Picker
        binding.btnFilterMovieGenre.setOnClickListener {
            val currentGenre = viewModel.selectedMovieGenre.value
            val options = viewModel.movieGenres.map { g ->
                TvOptionPickerDialog.OptionItem(
                    id = g.id,
                    title = getString(g.nameRes),
                    isSelected = (g.id == currentGenre.id)
                )
            }
            TvOptionPickerDialog.show(
                fragmentManager = supportFragmentManager,
                title = getString(R.string.title_movies),
                options = options
            ) { selected ->
                val matched = viewModel.movieGenres.find { it.id == selected.id }
                if (matched != null) {
                    viewModel.selectMovieGenre(matched)
                }
            }
        }

        // Series Genre Dropdown Picker
        binding.btnFilterSeriesGenre.setOnClickListener {
            val currentGenre = viewModel.selectedSeriesGenre.value
            val options = viewModel.seriesGenres.map { g ->
                TvOptionPickerDialog.OptionItem(
                    id = g.id,
                    title = getString(g.nameRes),
                    isSelected = (g.id == currentGenre.id)
                )
            }
            TvOptionPickerDialog.show(
                fragmentManager = supportFragmentManager,
                title = getString(R.string.title_series),
                options = options
            ) { selected ->
                val matched = viewModel.seriesGenres.find { it.id == selected.id }
                if (matched != null) {
                    viewModel.selectSeriesGenre(matched)
                }
            }
        }
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
                        binding.tvSearchInput.text = getString(R.string.search_hint)
                        openSearchDialog()
                    }

                    override fun onResults(results: Bundle?) {
                        isRecordingVoice = false
                        animateVoiceMicActive(false)
                        binding.tvSearchInput.text = getString(R.string.search_hint)

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
                            val targetPkg = lastLaunchedAppPackage
                            if (targetPkg != null) {
                                val targetIdx = apps.indexOfFirst { it.packageName == targetPkg }
                                if (targetIdx != -1) {
                                    binding.rvApps.post {
                                        binding.rvApps.scrollToPosition(targetIdx)
                                        binding.rvApps.findViewHolderForAdapterPosition(targetIdx)?.itemView?.requestFocus()
                                    }
                                    lastLaunchedAppPackage = null
                                    return@submitList
                                }
                            }
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
                        moviesAdapter.submitList(movies) {
                            binding.rvMovies.scrollToPosition(0)
                        }
                    }
                }

                // Selected Movie Genre
                launch {
                    viewModel.selectedMovieGenre.collect { genre ->
                        binding.tvCurrentMovieGenre.text = getString(genre.nameRes)
                        if (genre.id == "all") {
                            binding.tvMoviesSubtitle.setText(R.string.subtitle_movies_trending)
                        } else {
                            binding.tvMoviesSubtitle.text = getString(genre.nameRes)
                        }
                    }
                }

                // Series
                launch {
                    viewModel.series.collect { series ->
                        seriesAdapter.submitList(series) {
                            binding.rvSeries.scrollToPosition(0)
                        }
                    }
                }

                // Selected Series Genre
                launch {
                    viewModel.selectedSeriesGenre.collect { genre ->
                        binding.tvCurrentSeriesGenre.text = getString(genre.nameRes)
                        if (genre.id == "all") {
                            binding.tvSeriesSubtitle.setText(R.string.subtitle_series_top)
                        } else {
                            binding.tvSeriesSubtitle.text = getString(genre.nameRes)
                        }
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
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        when (event.keyCode) {
            KeyEvent.KEYCODE_VOICE_ASSIST,
            KeyEvent.KEYCODE_ASSIST -> {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                    if (GoogleAssistantHelper.isAvailable(this)) {
                        GoogleAssistantHelper.launch(this)
                    } else if (!isRecordingVoice) {
                        startInPlaceVoiceRecognition()
                    }
                    return true
                } else if (event.action == KeyEvent.ACTION_UP) {
                    return true
                }
            }
            KeyEvent.KEYCODE_SEARCH -> {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                    if (GoogleAssistantHelper.isAvailable(this)) {
                        GoogleAssistantHelper.launch(this)
                    } else if (isRecordingVoice) {
                        stopInPlaceVoiceRecognition()
                    } else {
                        startInPlaceVoiceRecognition()
                    }
                    return true
                } else if (event.action == KeyEvent.ACTION_UP) {
                    return true
                }
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    val focused = currentFocus
                    val container = binding.containerAllShelves
                    if (focused != null && container.childCount > 0) {
                        val lastChild = container.getChildAt(container.childCount - 1)
                        if (isDescendantOf(focused, lastChild)) {
                            // Already on the bottom shelf; consume DOWN so NestedScrollView never scrolls into empty void
                            return true
                        }
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private var lastAppliedLayoutMode: String? = null

    private fun applyHomeLayoutMode() {
        val mode = appPrefs.getHomeLayoutMode()
        if (mode == lastAppliedLayoutMode) return
        val container = binding.containerAllShelves
        val apps = binding.containerAppsShelf
        val movies = binding.containerMoviesShelf
        val series = binding.containerSeriesShelf

        if (mode == com.orbit.tv.launcher.features.apps.repository.AppPreferencesRepository.HOME_LAYOUT_DEFAULT && lastAppliedLayoutMode == null) {
            lastAppliedLayoutMode = mode
            return
        }
        lastAppliedLayoutMode = mode

        container.removeAllViews()

        when (mode) {
            com.orbit.tv.launcher.features.apps.repository.AppPreferencesRepository.HOME_LAYOUT_CINEMA -> {
                movies.visibility = View.VISIBLE
                series.visibility = View.VISIBLE
                apps.visibility = View.VISIBLE
                container.addView(movies)
                container.addView(series)
                container.addView(apps)
            }
            com.orbit.tv.launcher.features.apps.repository.AppPreferencesRepository.HOME_LAYOUT_MOVIES_FIRST -> {
                movies.visibility = View.VISIBLE
                apps.visibility = View.VISIBLE
                series.visibility = View.VISIBLE
                container.addView(movies)
                container.addView(apps)
                container.addView(series)
            }
            com.orbit.tv.launcher.features.apps.repository.AppPreferencesRepository.HOME_LAYOUT_NO_SERIES -> {
                apps.visibility = View.VISIBLE
                movies.visibility = View.VISIBLE
                series.visibility = View.GONE
                container.addView(apps)
                container.addView(movies)
            }
            com.orbit.tv.launcher.features.apps.repository.AppPreferencesRepository.HOME_LAYOUT_MINIMAL -> {
                apps.visibility = View.VISIBLE
                movies.visibility = View.GONE
                series.visibility = View.GONE
                container.addView(apps)
            }
            else -> { // DEFAULT: Apps -> Movies -> Series
                apps.visibility = View.VISIBLE
                movies.visibility = View.VISIBLE
                series.visibility = View.VISIBLE
                container.addView(apps)
                container.addView(movies)
                container.addView(series)
            }
        }
    }

    private fun setupShelfScrollCoordination() {
        binding.scrollContainer.viewTreeObserver.addOnGlobalFocusChangeListener { _, newFocus ->
            if (newFocus == null) return@addOnGlobalFocusChangeListener

            val container = binding.containerAllShelves
            val childCount = container.childCount
            if (childCount > 1) {
                val lastChild = container.getChildAt(childCount - 1)
                if (isDescendantOf(newFocus, lastChild)) {
                    val targetY = (lastChild.top - binding.scrollContainer.paddingTop).coerceAtLeast(0)
                    binding.scrollContainer.smoothScrollTo(0, targetY)
                    return@addOnGlobalFocusChangeListener
                }
            }

            if (childCount > 0 && (isDescendantOf(newFocus, container.getChildAt(0)) ||
                isDescendantOf(newFocus, binding.containerTopBar) ||
                isDescendantOf(newFocus, binding.containerSidebar))
            ) {
                binding.scrollContainer.smoothScrollTo(0, 0)
            }
        }
    }

    private fun isDescendantOf(child: View, target: View): Boolean {
        if (child == target) return true
        var current: ViewParent? = child.parent
        while (current != null) {
            if (current == target) return true
            current = current.parent
        }
        return false
    }



}
