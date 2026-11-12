package com.orbit.tv.launcher.ui.screens

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import com.orbit.tv.launcher.ui.overlays.TvPowerDialog
import kotlinx.coroutines.launch

class IptvActivity : AppCompatActivity() {

    private lateinit var binding: ActivityIptvBinding
    private val viewModel: IptvViewModel by viewModels()

    private var exoPlayer: ExoPlayer? = null
    private lateinit var epgAdapter: EpgChannelAdapter

    private val bannerHandler = Handler(Looper.getMainLooper())
    private val hideBannerRunnable = Runnable {
        binding.bannerFullscreenInfo.animate()
            .alpha(0f)
            .setDuration(400)
            .withEndAction { binding.bannerFullscreenInfo.visibility = View.GONE }
            .start()
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
    }

    private fun setupFocus() {
        TvFocusHelper.bindFocusScale(binding.btnNavApps, scaleRatio = 1.12f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnNavMedia, scaleRatio = 1.12f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnNavIptv, scaleRatio = 1.12f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnNavGames, scaleRatio = 1.12f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnNavCast, scaleRatio = 1.12f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnNavPower, scaleRatio = 1.12f, elevationDp = 8f)

        TvFocusHelper.bindFocusScale(binding.playerPreviewContainer, scaleRatio = 1.04f, elevationDp = 10f)
        TvFocusHelper.bindFocusScale(binding.btnCatAll, scaleRatio = 1.06f, elevationDp = 6f)
        TvFocusHelper.bindFocusScale(binding.btnCatSports, scaleRatio = 1.06f, elevationDp = 6f)
        TvFocusHelper.bindFocusScale(binding.btnCatCinema, scaleRatio = 1.06f, elevationDp = 6f)
        TvFocusHelper.bindFocusScale(binding.btnCatNews, scaleRatio = 1.06f, elevationDp = 6f)

        binding.btnNavIptv.isSelected = true
    }

    private fun setupRecyclerView() {
        epgAdapter = EpgChannelAdapter { channel ->
            viewModel.selectChannel(channel)
        }
        binding.rvEpgChannels.apply {
            layoutManager = LinearLayoutManager(this@IptvActivity)
            adapter = epgAdapter
            setHasFixedSize(true)
        }
    }

    private fun setupListeners() {
        binding.btnNavMedia.setOnClickListener { finish() }
        binding.btnNavApps.setOnClickListener {
            startActivity(Intent(this, AppsHubActivity::class.java))
            finish()
        }
        binding.btnNavCast.setOnClickListener {
            startActivity(Intent(this, CastActivity::class.java))
            finish()
        }
        binding.btnNavGames.setOnClickListener {
            startActivity(Intent(this, OptimizerActivity::class.java))
            finish()
        }
        binding.btnNavPower.setOnClickListener {
            TvPowerDialog.show(supportFragmentManager)
        }

        binding.playerPreviewContainer.setOnClickListener {
            viewModel.toggleFullscreen()
        }

        binding.btnCatAll.setOnClickListener { viewModel.selectCategory("All") }
        binding.btnCatSports.setOnClickListener { viewModel.selectCategory("Sports") }
        binding.btnCatCinema.setOnClickListener { viewModel.selectCategory("Cinema") }
        binding.btnCatNews.setOnClickListener { viewModel.selectCategory("News") }
    }

    private fun initExoPlayer() {
        exoPlayer = ExoPlayer.Builder(this).build().apply {
            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    // Failover gracefully without intrusive toast dialogs on TV screen
                }
            })
        }
        binding.playerView.player = exoPlayer
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (viewModel.isFullscreen.value) {
                    viewModel.exitFullscreen()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Filtered Channels
                launch {
                    viewModel.filteredChannels.collect { channels ->
                        epgAdapter.submitList(channels)
                    }
                }

                // Active Channel Playback
                launch {
                    viewModel.activeChannel.collect { channel ->
                        channel?.let { playChannel(it) }
                    }
                }

                // Selected Category UI indicator
                launch {
                    viewModel.selectedCategory.collect { category ->
                        binding.btnCatAll.isSelected = category.equals("All", ignoreCase = true)
                        binding.btnCatSports.isSelected = category.equals("Sports", ignoreCase = true)
                        binding.btnCatCinema.isSelected = category.equals("Cinema", ignoreCase = true)
                        binding.btnCatNews.isSelected = category.equals("News", ignoreCase = true)
                    }
                }

                // Fullscreen mode transition
                launch {
                    viewModel.isFullscreen.collect { isFullscreen ->
                        applyFullscreenMode(isFullscreen)
                    }
                }
            }
        }
    }

    private fun playChannel(channel: IptvChannel) {
        epgAdapter.activeChannelId = channel.id
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
        bannerHandler.postDelayed(hideBannerRunnable, 4000)
    }

    override fun onResume() {
        super.onResume()
        exoPlayer?.playWhenReady = true
    }

    override fun onPause() {
        super.onPause()
        exoPlayer?.playWhenReady = false
    }

    override fun onDestroy() {
        super.onDestroy()
        bannerHandler.removeCallbacks(hideBannerRunnable)
        exoPlayer?.release()
        exoPlayer = null
    }
}
