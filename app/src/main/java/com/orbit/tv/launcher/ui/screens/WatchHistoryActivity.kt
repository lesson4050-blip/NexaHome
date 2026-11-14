package com.orbit.tv.launcher.ui.screens

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.orbit.tv.launcher.core.focus.TvFocusHelper
import com.orbit.tv.launcher.databinding.ActivityWatchHistoryBinding
import com.orbit.tv.launcher.features.home.adapters.MediaAdapter
import com.orbit.tv.launcher.features.home.data.WatchHistoryRepository
import com.orbit.tv.launcher.ui.overlays.MediaDetailDialog
import androidx.recyclerview.widget.LinearLayoutManager
import com.orbit.tv.launcher.features.apps.repository.AppPreferencesRepository
import com.orbit.tv.launcher.platform.tvprovider.WatchNextRepository
import com.orbit.tv.launcher.platform.wallpaper.TvWallpaperHelper
import com.orbit.tv.launcher.ui.adapters.WatchNextAdapter
import com.orbit.tv.launcher.ui.overlays.TvPowerDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WatchHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWatchHistoryBinding
    private lateinit var repository: WatchHistoryRepository
    private lateinit var appPrefs: AppPreferencesRepository
    private lateinit var mediaAdapter: MediaAdapter
    private lateinit var watchNextRepo: WatchNextRepository
    private lateinit var watchNextAdapter: WatchNextAdapter

    private var hasLocalHistory = false
    private var hasWatchNext = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWatchHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = WatchHistoryRepository(this)
        appPrefs = AppPreferencesRepository(this)
        watchNextRepo = WatchNextRepository(this)

        setupFocus()
        setupSidebarListeners()
        setupWatchNextList()
        setupHistoryList()
        setupBackNavigation()
        observeHistory()
    }

    override fun onResume() {
        super.onResume()
        TvWallpaperHelper.apply(appPrefs.getWallpaperTheme(), binding.silkShaderView, binding.ivBackdrop, binding.viewDimmer)
        loadWatchNext()
    }

    override fun onPause() {
        super.onPause()
        TvWallpaperHelper.pause(binding.silkShaderView)
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
        TvFocusHelper.bindFocusScale(binding.btnClearHistory, scaleRatio = 1.08f, elevationDp = 6f)

        binding.btnNavHistory.isSelected = true
    }

    private fun setupSidebarListeners() {
        binding.btnNavMedia.setOnClickListener {
            finish()
        }
        binding.btnNavHistory.setOnClickListener {
            // Already here: focus history list
            if (binding.rvHistory.visibility == View.VISIBLE && mediaAdapter.itemCount > 0) {
                binding.rvHistory.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
            }
        }
        binding.btnNavApps.setOnClickListener {
            startActivity(Intent(this, AppsHubActivity::class.java))
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

        binding.btnClearHistory.setOnClickListener {
            lifecycleScope.launch {
                repository.clearHistory()
                binding.btnNavHistory.requestFocus()
            }
        }
    }

    private fun setupWatchNextList() {
        watchNextAdapter = WatchNextAdapter { item ->
            watchNextRepo.launch(item)
        }
        binding.rvWatchNext.apply {
            layoutManager = LinearLayoutManager(this@WatchHistoryActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = watchNextAdapter
        }
    }

    private fun loadWatchNext() {
        lifecycleScope.launch(Dispatchers.IO) {
            val items = watchNextRepo.getWatchNextPrograms()
            withContext(Dispatchers.Main) {
                hasWatchNext = items.isNotEmpty()
                if (hasWatchNext) {
                    binding.containerWatchNext.visibility = View.VISIBLE
                    watchNextAdapter.submitList(items)
                } else {
                    binding.containerWatchNext.visibility = View.GONE
                }
                updateEmptyState()
            }
        }
    }

    private fun setupHistoryList() {
        mediaAdapter = MediaAdapter { media ->
            MediaDetailDialog.newInstance(media)
                .show(supportFragmentManager, MediaDetailDialog.TAG)
        }

        binding.rvHistory.apply {
            layoutManager = LinearLayoutManager(this@WatchHistoryActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = mediaAdapter
        }
    }

    private fun observeHistory() {
        val mediaRepo = com.orbit.tv.launcher.features.home.repository.MediaRepository(this)
        lifecycleScope.launch {
            repository.recentMedia.collect { list ->
                hasLocalHistory = list.isNotEmpty()
                if (hasLocalHistory) {
                    binding.containerLocalHistorySection.visibility = View.VISIBLE
                    binding.rvHistory.visibility = View.VISIBLE
                    binding.btnClearHistory.visibility = View.VISIBLE
                    mediaAdapter.submitList(list)

                    // Background enrichment for items not found in current shelf cache
                    val tmdbLang = mediaRepo.getTmdbLanguage()
                    val unlocalized = list.filter { item ->
                        val itemCache = java.io.File(cacheDir, "item_${item.id}_${tmdbLang.replace('-', '_')}.json")
                        !itemCache.exists() && item.id.matches(Regex("\\d+"))
                    }
                    if (unlocalized.isNotEmpty()) {
                        launch {
                            var changed = false
                            val updated = list.toMutableList()
                            for (item in unlocalized) {
                                val fetched = mediaRepo.fetchAndCacheSingleItem(item.id, item.isSeries)
                                if (fetched != null) {
                                    val idx = updated.indexOfFirst { it.id == item.id }
                                    if (idx != -1) {
                                        updated[idx] = updated[idx].copy(
                                            title = fetched.title,
                                            description = fetched.description,
                                            posterUrl = fetched.posterUrl.ifBlank { updated[idx].posterUrl },
                                            genre = fetched.genre
                                        )
                                        changed = true
                                    }
                                }
                            }
                            if (changed) {
                                mediaAdapter.submitList(updated.toList())
                            }
                        }
                    }
                } else {
                    binding.containerLocalHistorySection.visibility = View.GONE
                    binding.rvHistory.visibility = View.GONE
                    binding.btnClearHistory.visibility = View.GONE
                }
                updateEmptyState()
            }
        }
    }

    private fun updateEmptyState() {
        val allEmpty = !hasLocalHistory && !hasWatchNext
        binding.containerEmpty.visibility = if (allEmpty) View.VISIBLE else View.GONE
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!binding.containerSidebar.hasFocus()) {
                    binding.btnNavHistory.requestFocus()
                } else {
                    finish()
                }
            }
        })
    }
}
