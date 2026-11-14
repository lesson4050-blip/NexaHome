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
import com.orbit.tv.launcher.databinding.ActivityHomeBinding
import com.orbit.tv.launcher.features.home.HomeViewModel
import com.orbit.tv.launcher.features.home.adapters.AppsAdapter
import com.orbit.tv.launcher.features.home.adapters.MediaAdapter
import android.view.KeyEvent
import android.view.View
import android.view.ViewParent
import com.orbit.tv.launcher.ui.overlays.MediaDetailDialog
import com.orbit.tv.launcher.ui.overlays.SearchDialogFragment
import com.orbit.tv.launcher.ui.overlays.TvPowerDialog
import kotlinx.coroutines.launch

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private val viewModel: HomeViewModel by viewModels()

    private lateinit var appsAdapter: AppsAdapter
    private lateinit var moviesAdapter: MediaAdapter
    private lateinit var seriesAdapter: MediaAdapter
    private lateinit var movieGenreAdapter: com.orbit.tv.launcher.features.home.adapters.GenreAdapter
    private lateinit var seriesGenreAdapter: com.orbit.tv.launcher.features.home.adapters.GenreAdapter

    // Shared ViewPool between media shelves to minimize RAM allocations on 1GB RAM devices
    private val sharedMediaPool = RecyclerView.RecycledViewPool()

    private var speechRecognizer: SpeechRecognizer? = null
    private var isRecordingVoice: Boolean = false

    private val audioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startInPlaceVoiceRecognition()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupFocusableControls()
        setupRecyclerViews()
        setupClickListeners()
        setupBackNavigation()
        setupShelfScrollCoordination()
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
        java.util.Locale.setDefault(viewModel.getActiveLocale())
        // Refresh apps list in case packages were uninstalled/installed while user was in another app
        viewModel.loadInstalledApps()
        viewModel.checkAndReloadMediaIfNeeded()
        updateGoogleAssistantVisibility()
    }

    override fun onPause() {
        super.onPause()
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

        // Set initial selected nav state: Home button
        binding.btnNavMedia.isSelected = true
        binding.btnNavApps.isSelected = false
    }

    private fun setupRecyclerViews() {
        // 1. Installed Apps Shelf (showPinBadge = false: clean cards without pin needle)
        appsAdapter = AppsAdapter(
            showPinBadge = false,
            onAppClick = { app ->
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
        }

        // 2. Movies Shelf & Genre Filters
        movieGenreAdapter = com.orbit.tv.launcher.features.home.adapters.GenreAdapter { genre ->
            viewModel.selectMovieGenre(genre)
        }
        binding.rvMovieGenres.apply {
            layoutManager = LinearLayoutManager(this@HomeActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = movieGenreAdapter
        }
        movieGenreAdapter.submitList(viewModel.movieGenres)

        moviesAdapter = MediaAdapter { media ->
            MediaDetailDialog.newInstance(media).show(supportFragmentManager, MediaDetailDialog.TAG)
        }
        binding.rvMovies.apply {
            layoutManager = LinearLayoutManager(this@HomeActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = moviesAdapter
            setRecycledViewPool(sharedMediaPool)
        }

        // 3. Series Shelf & Genre Filters
        seriesGenreAdapter = com.orbit.tv.launcher.features.home.adapters.GenreAdapter { genre ->
            viewModel.selectSeriesGenre(genre)
        }
        binding.rvSeriesGenres.apply {
            layoutManager = LinearLayoutManager(this@HomeActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = seriesGenreAdapter
        }
        seriesGenreAdapter.submitList(viewModel.seriesGenres)

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
        // Sidebar Navigation: 1. Home, 2. History, 3. Apps, 4. IPTV, 5. Games, 6. Cast
        binding.btnNavMedia.setOnClickListener {
            binding.scrollContainer.smoothScrollTo(0, 0)
        }
        binding.btnNavHistory.setOnClickListener {
            startActivity(Intent(this, WatchHistoryActivity::class.java))
        }
        binding.btnNavApps.setOnClickListener {
            startActivity(Intent(this, AppsHubActivity::class.java))
        }
        binding.btnNavIptv.setOnClickListener {
            startActivity(Intent(this, IptvActivity::class.java))
        }
        binding.btnNavGames.setOnClickListener {
            startActivity(Intent(this, OptimizerActivity::class.java))
        }
        binding.btnNavCast.setOnClickListener {
            startActivity(Intent(this, CastActivity::class.java))
        }
        binding.btnNavSettings.setOnClickListener {
            startActivity(Intent(this, LauncherSettingsActivity::class.java))
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
                        movieGenreAdapter.selectedGenreId = genre.id
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
                        seriesGenreAdapter.selectedGenreId = genre.id
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
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    val focused = currentFocus
                    if (focused != null && isDescendantOf(focused, binding.rvSeries)) {
                        // Already on the bottom shelf; consume DOWN so NestedScrollView never scrolls into empty void
                        return true
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun setupShelfScrollCoordination() {
        // Hard-clamp maximum vertical scroll so content NEVER scrolls past Movies header
        binding.scrollContainer.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            val maxY = (binding.headerMovies.top - binding.scrollContainer.paddingTop).coerceAtLeast(0)
            if (scrollY > maxY) {
                binding.scrollContainer.scrollTo(0, maxY)
            }
        }

        binding.scrollContainer.viewTreeObserver.addOnGlobalFocusChangeListener { _, newFocus ->
            if (newFocus == null) return@addOnGlobalFocusChangeListener

            if (isDescendantOf(newFocus, binding.rvSeries) || 
                isDescendantOf(newFocus, binding.headerSeries) || 
                isDescendantOf(newFocus, binding.rvSeriesGenres)
            ) {
                // Focus is on Series shelf: scroll down so Movies header sits cleanly at the top
                val targetY = (binding.headerMovies.top - binding.scrollContainer.paddingTop).coerceAtLeast(0)
                binding.scrollContainer.smoothScrollTo(0, targetY)
            } else if (isDescendantOf(newFocus, binding.rvMovies) ||
                isDescendantOf(newFocus, binding.rvMovieGenres) ||
                isDescendantOf(newFocus, binding.headerMovies) ||
                isDescendantOf(newFocus, binding.rvApps) ||
                isDescendantOf(newFocus, binding.containerTopBar) ||
                isDescendantOf(newFocus, binding.containerSidebar)
            ) {
                // Focus is on Movies, Apps, TopBar or Sidebar: return cleanly to top home view
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
