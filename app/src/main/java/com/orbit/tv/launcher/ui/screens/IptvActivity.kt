package com.orbit.tv.launcher.ui.screens

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.recyclerview.widget.LinearLayoutManager
import com.orbit.tv.launcher.R
import com.orbit.tv.launcher.core.focus.TvFocusHelper
import com.orbit.tv.launcher.databinding.ActivityIptvBinding
import com.orbit.tv.launcher.features.iptv.IptvViewModel
import com.orbit.tv.launcher.features.iptv.adapters.EpgChannelAdapter
import com.orbit.tv.launcher.features.iptv.model.IptvChannel
import com.orbit.tv.launcher.ui.overlays.IptvPlaylistDialog
import com.orbit.tv.launcher.ui.overlays.TvPowerDialog
import kotlinx.coroutines.launch

import com.orbit.tv.launcher.platform.wallpaper.TvWallpaperHelper

class IptvActivity : AppCompatActivity() {

    private lateinit var binding: ActivityIptvBinding
    private val viewModel: IptvViewModel by viewModels()
    private val appPrefs by lazy { com.orbit.tv.launcher.features.apps.repository.AppPreferencesRepository(this) }

    private var exoPlayer: ExoPlayer? = null
    private lateinit var epgAdapter: EpgChannelAdapter
    private lateinit var categoryAdapter: com.orbit.tv.launcher.features.iptv.adapters.IptvCategoryAdapter
    private lateinit var osdAdapter: EpgChannelAdapter

    private var retryAttempt = 0
    private val maxAutoRetries = 5
    private val reconnectHandler = Handler(Looper.getMainLooper())
    private val reconnectRunnable = Runnable { performAutoReconnect() }

    private val bufferingWatchdogHandler = Handler(Looper.getMainLooper())
    private val bufferingWatchdogRunnable = Runnable {
        handlePlayerError(
            PlaybackException(
                "Таймаут буферизации (потеря пакетов)",
                null,
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
            )
        )
    }

    private val bannerHandler = Handler(Looper.getMainLooper())
    private val hideBannerRunnable = Runnable {
        binding.bannerFullscreenInfo.animate()
            .alpha(0f)
            .setDuration(400)
            .withEndAction { binding.bannerFullscreenInfo.visibility = View.GONE }
            .start()
    }

    private val osdHandler = Handler(Looper.getMainLooper())
    private val hideOsdRunnable = Runnable { hideOsdSidebar() }

    private val playlistReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            viewModel.loadChannels(forceRefresh = true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityIptvBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupFocus()
        setupRecyclerView()
        setupListeners()
        initExoPlayer()
        setupBackNavigation()
        observeViewModel()
        registerPlaylistReceiver()
    }

    private fun registerPlaylistReceiver() {
        val filter = android.content.IntentFilter(ACTION_IPTV_UPDATED)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(playlistReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(playlistReceiver, filter)
        }
    }

    private fun setupFocus() {
        TvFocusHelper.bindFocusScale(binding.btnNavMedia, scaleRatio = 1.12f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnNavHistory, scaleRatio = 1.12f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnNavApps, scaleRatio = 1.12f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnNavIptv, scaleRatio = 1.12f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnNavGames, scaleRatio = 1.12f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnNavCast, scaleRatio = 1.12f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnNavSettings, scaleRatio = 1.12f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnNavPower, scaleRatio = 1.12f, elevationDp = 8f)

        TvFocusHelper.bindFocusScale(binding.btnManagePlaylist, scaleRatio = 1.08f, elevationDp = 6f)
        TvFocusHelper.bindFocusScale(binding.playerPreviewContainer, scaleRatio = 1.04f, elevationDp = 10f)

        TvFocusHelper.bindFocusScale(binding.btnRetryPlayback, scaleRatio = 1.08f, elevationDp = 6f)
        TvFocusHelper.bindFocusScale(binding.btnFullscreenRetry, scaleRatio = 1.08f, elevationDp = 8f)

        binding.btnNavIptv.isSelected = true
    }

    private fun setupRecyclerView() {
        categoryAdapter = com.orbit.tv.launcher.features.iptv.adapters.IptvCategoryAdapter { category ->
            viewModel.selectCategory(category.id)
        }
        binding.rvIptvCategories.apply {
            layoutManager = androidx.recyclerview.widget.GridLayoutManager(this@IptvActivity, 2)
            adapter = categoryAdapter
            setHasFixedSize(true)
        }

        epgAdapter = EpgChannelAdapter(
            onChannelClicked = { channel -> viewModel.selectChannel(channel) },
            onChannelLongClicked = { channel -> viewModel.toggleFavorite(channel) }
        )
        binding.rvEpgChannels.apply {
            layoutManager = LinearLayoutManager(this@IptvActivity)
            adapter = epgAdapter
            setHasFixedSize(true)
        }

        osdAdapter = EpgChannelAdapter(
            onChannelClicked = { channel ->
                viewModel.selectChannel(channel)
                hideOsdSidebar()
            },
            onChannelLongClicked = { channel ->
                viewModel.toggleFavorite(channel)
            }
        )
        binding.rvOsdChannels.apply {
            layoutManager = LinearLayoutManager(this@IptvActivity)
            adapter = osdAdapter
            setHasFixedSize(true)
        }
    }

    private fun setupListeners() {
        binding.btnNavMedia.setOnClickListener {
            com.orbit.tv.launcher.core.navigation.TvNavHelper.finishWithPop(this)
        }
        binding.btnNavHistory.setOnClickListener {
            com.orbit.tv.launcher.core.navigation.TvNavHelper.openScreen(this, WatchHistoryActivity::class.java)
        }
        binding.btnNavApps.setOnClickListener {
            com.orbit.tv.launcher.core.navigation.TvNavHelper.openScreen(this, AppsHubActivity::class.java)
        }
        binding.btnNavCast.setOnClickListener {
            com.orbit.tv.launcher.core.navigation.TvNavHelper.openScreen(this, CastActivity::class.java)
        }
        binding.btnNavGames.setOnClickListener {
            com.orbit.tv.launcher.core.navigation.TvNavHelper.openScreen(this, OptimizerActivity::class.java)
        }
        binding.btnNavSettings.setOnClickListener {
            com.orbit.tv.launcher.core.navigation.TvNavHelper.openScreen(this, LauncherSettingsActivity::class.java)
        }
        binding.btnNavPower.setOnClickListener {
            TvPowerDialog.show(supportFragmentManager)
        }

        binding.btnManagePlaylist.setOnClickListener {
            IptvPlaylistDialog.show(supportFragmentManager)
        }

        binding.playerPreviewContainer.setOnClickListener {
            viewModel.toggleFullscreen()
        }

        binding.btnRetryPlayback.setOnClickListener {
            retryActiveChannel()
        }
        binding.btnFullscreenRetry.setOnClickListener {
            retryActiveChannel()
        }
    }

    private fun initExoPlayer() {
        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                2500, // minBufferMs
                15000, // maxBufferMs
                1000, // bufferForPlaybackMs
                2000 // bufferForPlaybackAfterRebufferMs
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val httpDataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(8000)
            .setReadTimeoutMs(8000)
            .setKeepPostFor302Redirects(true)

        val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(this)
            .setDataSourceFactory(httpDataSourceFactory)

        exoPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .build()
            .apply {
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_READY -> {
                                bufferingWatchdogHandler.removeCallbacks(bufferingWatchdogRunnable)
                                retryAttempt = 0
                                reconnectHandler.removeCallbacks(reconnectRunnable)
                                binding.overlayPlayerError.visibility = View.GONE
                                binding.overlayFullscreenError.visibility = View.GONE
                                binding.btnRetryPlayback.visibility = View.VISIBLE
                                binding.btnFullscreenRetry.visibility = View.VISIBLE
                            }
                            Player.STATE_BUFFERING -> {
                                bufferingWatchdogHandler.removeCallbacks(bufferingWatchdogRunnable)
                                bufferingWatchdogHandler.postDelayed(bufferingWatchdogRunnable, 5000L)
                            }
                            Player.STATE_ENDED, Player.STATE_IDLE -> {
                                bufferingWatchdogHandler.removeCallbacks(bufferingWatchdogRunnable)
                            }
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        handlePlayerError(error)
                    }
                })
            }
        binding.playerView.player = exoPlayer
    }

    private fun handlePlayerError(error: PlaybackException) {
        bufferingWatchdogHandler.removeCallbacks(bufferingWatchdogRunnable)
        if (retryAttempt < maxAutoRetries) {
            retryAttempt++
            // Exponential backoff: 1.5s, 3s, 6s, 12s, 15s
            val delayMs = (1500L * (1 shl (retryAttempt - 1))).coerceAtMost(15000L)
            val reconnectingText = "Переподключение... ($retryAttempt/$maxAutoRetries)"

            if (viewModel.isFullscreen.value) {
                binding.overlayFullscreenError.visibility = View.VISIBLE
                binding.tvFullscreenErrorDesc.text = "$reconnectingText • ВВЕРХ/ВНИЗ для переключения"
                binding.btnFullscreenRetry.visibility = View.GONE
            } else {
                binding.overlayPlayerError.visibility = View.VISIBLE
                binding.tvErrorMessage.text = reconnectingText
                binding.btnRetryPlayback.visibility = View.GONE
            }

            reconnectHandler.removeCallbacks(reconnectRunnable)
            reconnectHandler.postDelayed(reconnectRunnable, delayMs)
        } else {
            val errorMessage = error.localizedMessage?.take(60) ?: "Ошибка подключения к потоку"
            if (viewModel.isFullscreen.value) {
                binding.overlayFullscreenError.visibility = View.VISIBLE
                binding.btnFullscreenRetry.visibility = View.VISIBLE
                binding.tvFullscreenErrorDesc.text = "Канал временно недоступен • ВВЕРХ/ВНИЗ для переключения"
                binding.btnFullscreenRetry.requestFocus()
            } else {
                binding.overlayPlayerError.visibility = View.VISIBLE
                binding.btnRetryPlayback.visibility = View.VISIBLE
                binding.tvErrorMessage.text = "Поток недоступен ($errorMessage)"
                binding.btnRetryPlayback.requestFocus()
            }
        }
    }

    private fun performAutoReconnect() {
        val channel = viewModel.activeChannel.value ?: return
        exoPlayer?.let { player ->
            player.stop()
            val mediaItem = MediaItem.fromUri(channel.streamUrl)
            player.setMediaItem(mediaItem)
            player.prepare()
            player.playWhenReady = true
        }
    }

    private fun retryActiveChannel() {
        retryAttempt = 0
        reconnectHandler.removeCallbacks(reconnectRunnable)
        binding.overlayPlayerError.visibility = View.GONE
        binding.overlayFullscreenError.visibility = View.GONE
        binding.btnRetryPlayback.visibility = View.VISIBLE
        binding.btnFullscreenRetry.visibility = View.VISIBLE
        viewModel.activeChannel.value?.let { playChannel(it) }
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.osdChannelSidebar.visibility == View.VISIBLE) {
                    hideOsdSidebar()
                } else if (viewModel.isFullscreen.value) {
                    viewModel.exitFullscreen()
                } else if (!binding.containerSidebar.hasFocus()) {
                    binding.btnNavIptv.requestFocus()
                } else {
                    finish()
                }
            }
        })
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Real-time Clock
                launch {
                    viewModel.currentTime.collect { time ->
                        binding.tvClock.text = time
                    }
                }

                // Dynamic Categories
                launch {
                    viewModel.categories.collect { categories ->
                        categoryAdapter.submitList(categories)
                    }
                }

                // Selected Category
                launch {
                    viewModel.selectedCategory.collect { category ->
                        categoryAdapter.selectedCategoryId = category
                    }
                }

                // Filtered Channels
                launch {
                    viewModel.filteredChannels.collect { channels ->
                        epgAdapter.submitList(channels)
                        osdAdapter.submitList(channels)
                    }
                }

                // Active Channel Playback
                launch {
                    viewModel.activeChannel.collect { channel ->
                        channel?.let { playChannel(it) }
                    }
                }

                // Fullscreen mode transition
                launch {
                    viewModel.isFullscreen.collect { isFullscreen ->
                        applyFullscreenMode(isFullscreen)
                    }
                }

                // Auto-reconnect on network restoration
                launch {
                    viewModel.networkStatus.collect { status ->
                        if (status != com.orbit.tv.launcher.platform.device_capabilities.NetworkStatus.DISCONNECTED) {
                            if (retryAttempt > 0 || binding.overlayPlayerError.visibility == View.VISIBLE || binding.overlayFullscreenError.visibility == View.VISIBLE) {
                                retryAttempt = 0
                                reconnectHandler.removeCallbacks(reconnectRunnable)
                                performAutoReconnect()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun playChannel(channel: IptvChannel) {
        retryAttempt = 0
        reconnectHandler.removeCallbacks(reconnectRunnable)
        binding.overlayPlayerError.visibility = View.GONE
        binding.overlayFullscreenError.visibility = View.GONE
        binding.btnRetryPlayback.visibility = View.VISIBLE
        binding.btnFullscreenRetry.visibility = View.VISIBLE

        epgAdapter.activeChannelId = channel.id
        osdAdapter.activeChannelId = channel.id
        binding.tvPreviewChannel.text = channel.name
        binding.tvPreviewProgram.text = channel.currentProgram

        binding.tvFullscreenChannel.text = channel.name
        binding.tvFullscreenProgram.text = channel.currentProgram

        exoPlayer?.let { player ->
            player.stop()
            val mediaItem = MediaItem.fromUri(channel.streamUrl)
            player.setMediaItem(mediaItem)
            player.prepare()
            player.playWhenReady = true
        }

        if (viewModel.isFullscreen.value) {
            showFullscreenBanner()
        }
    }

    private fun applyFullscreenMode(isFullscreen: Boolean) {
        if (isFullscreen) {
            binding.containerFullscreenMode.visibility = View.VISIBLE
            binding.playerView.player = null
            binding.fullscreenPlayerView.player = exoPlayer
            binding.fullscreenPlayerView.requestFocus()
            showFullscreenBanner()
        } else {
            hideOsdSidebar()
            bannerHandler.removeCallbacks(hideBannerRunnable)
            binding.containerFullscreenMode.visibility = View.GONE
            binding.fullscreenPlayerView.player = null
            binding.playerView.player = exoPlayer
            binding.playerPreviewContainer.requestFocus()
        }
    }

    private fun showFullscreenBanner() {
        bannerHandler.removeCallbacks(hideBannerRunnable)
        binding.bannerFullscreenInfo.alpha = 1f
        binding.bannerFullscreenInfo.visibility = View.VISIBLE
        bannerHandler.postDelayed(hideBannerRunnable, 3500)
    }

    private fun showOsdSidebar() {
        osdHandler.removeCallbacks(hideOsdRunnable)
        binding.osdChannelSidebar.visibility = View.VISIBLE
        binding.osdChannelSidebar.alpha = 0f
        binding.osdChannelSidebar.animate()
            .alpha(1f)
            .setDuration(200)
            .start()

        val activeId = viewModel.activeChannel.value?.id
        val list = viewModel.filteredChannels.value
        val idx = list.indexOfFirst { it.id == activeId }
        if (idx >= 0) {
            binding.rvOsdChannels.scrollToPosition(idx)
            binding.rvOsdChannels.postDelayed({
                binding.rvOsdChannels.findViewHolderForAdapterPosition(idx)?.itemView?.requestFocus()
                    ?: binding.rvOsdChannels.requestFocus()
            }, 80)
        } else {
            binding.rvOsdChannels.requestFocus()
        }

        osdHandler.postDelayed(hideOsdRunnable, 6000)
    }

    private fun hideOsdSidebar() {
        osdHandler.removeCallbacks(hideOsdRunnable)
        if (binding.osdChannelSidebar.visibility == View.VISIBLE) {
            binding.osdChannelSidebar.animate()
                .alpha(0f)
                .setDuration(180)
                .withEndAction {
                    binding.osdChannelSidebar.visibility = View.GONE
                    if (viewModel.isFullscreen.value) {
                        binding.fullscreenPlayerView.requestFocus()
                    }
                }
                .start()
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (viewModel.isFullscreen.value && event.action == KeyEvent.ACTION_DOWN) {
            if (binding.osdChannelSidebar.visibility == View.VISIBLE) {
                if (event.keyCode == KeyEvent.KEYCODE_BACK || event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    hideOsdSidebar()
                    return true
                }
                osdHandler.removeCallbacks(hideOsdRunnable)
                osdHandler.postDelayed(hideOsdRunnable, 6000)
                return super.dispatchKeyEvent(event)
            }

            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (binding.overlayFullscreenError.visibility == View.VISIBLE && event.keyCode != KeyEvent.KEYCODE_DPAD_LEFT) {
                        retryActiveChannel()
                    } else {
                        showOsdSidebar()
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> {
                    viewModel.previousChannel()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                    viewModel.nextChannel()
                    return true
                }
                KeyEvent.KEYCODE_MENU -> {
                    viewModel.activeChannel.value?.let { viewModel.toggleFavorite(it) }
                    return true
                }
                KeyEvent.KEYCODE_BACK -> {
                    viewModel.exitFullscreen()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onResume() {
        super.onResume()
        exoPlayer?.playWhenReady = true
        TvWallpaperHelper.apply(appPrefs.getWallpaperTheme(), binding.silkShaderView, binding.ivBackdrop, binding.viewDimmer)
    }

    override fun onPause() {
        super.onPause()
        exoPlayer?.playWhenReady = false
        TvWallpaperHelper.pause(binding.silkShaderView)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(playlistReceiver)
        } catch (e: Exception) {}
        bannerHandler.removeCallbacks(hideBannerRunnable)
        osdHandler.removeCallbacks(hideOsdRunnable)
        reconnectHandler.removeCallbacks(reconnectRunnable)
        bufferingWatchdogHandler.removeCallbacks(bufferingWatchdogRunnable)
        exoPlayer?.release()
        exoPlayer = null
    }

    companion object {
        const val ACTION_IPTV_UPDATED = "com.orbit.tv.launcher.ACTION_IPTV_PLAYLIST_UPDATED"
    }
}
