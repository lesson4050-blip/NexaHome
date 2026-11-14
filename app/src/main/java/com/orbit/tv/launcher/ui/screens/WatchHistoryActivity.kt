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
import com.orbit.tv.launcher.ui.overlays.TvPowerDialog
import kotlinx.coroutines.launch

class WatchHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWatchHistoryBinding
    private lateinit var repository: WatchHistoryRepository
    private lateinit var mediaAdapter: MediaAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWatchHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = WatchHistoryRepository(this)

        setupFocus()
        setupSidebarListeners()
        setupHistoryList()
        setupBackNavigation()
        observeHistory()
    }

    private fun setupFocus() {
        TvFocusHelper.bindFocusScale(binding.btnNavMedia, scaleRatio = 1.12f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnNavHistory, scaleRatio = 1.12f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnNavApps, scaleRatio = 1.12f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnNavIptv, scaleRatio = 1.12f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnNavGames, scaleRatio = 1.12f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnNavCast, scaleRatio = 1.12f, elevationDp = 8f)
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

    private fun setupHistoryList() {
        mediaAdapter = MediaAdapter { media ->
            MediaDetailDialog.newInstance(media)
                .show(supportFragmentManager, MediaDetailDialog.TAG)
        }

        binding.rvHistory.apply {
            layoutManager = GridLayoutManager(this@WatchHistoryActivity, 5)
            adapter = mediaAdapter
        }
    }

    private fun observeHistory() {
        val mediaRepo = com.orbit.tv.launcher.features.home.repository.MediaRepository(this)
        lifecycleScope.launch {
            repository.recentMedia.collect { list ->
                if (list.isNotEmpty()) {
                    binding.rvHistory.visibility = View.VISIBLE
                    binding.containerEmpty.visibility = View.GONE
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
                    binding.rvHistory.visibility = View.GONE
                    binding.containerEmpty.visibility = View.VISIBLE
                    binding.btnClearHistory.visibility = View.GONE
                }
            }
        }
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.rvHistory.hasFocus() || binding.btnClearHistory.hasFocus()) {
                    binding.btnNavHistory.requestFocus()
                } else {
                    finish()
                }
            }
        })
    }
}
