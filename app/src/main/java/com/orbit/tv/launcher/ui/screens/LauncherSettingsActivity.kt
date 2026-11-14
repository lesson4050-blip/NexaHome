package com.orbit.tv.launcher.ui.screens

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.orbit.tv.launcher.R
import com.orbit.tv.launcher.core.focus.TvFocusHelper
import com.orbit.tv.launcher.databinding.ActivitySettingsBinding
import com.orbit.tv.launcher.features.apps.repository.AppPreferencesRepository
import com.orbit.tv.launcher.features.home.TvDeepLinkHelper
import com.orbit.tv.launcher.features.home.model.AppModel
import com.orbit.tv.launcher.platform.package_manager.PackageManagerRepository
import com.orbit.tv.launcher.platform.storage.CacheManager
import com.orbit.tv.launcher.ui.overlays.TvPowerDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LauncherSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var appPrefs: AppPreferencesRepository
    private lateinit var pkgRepo: PackageManagerRepository
    private lateinit var cacheManager: CacheManager

    private lateinit var hiddenAppsAdapter: HiddenAppsAdapter
    private lateinit var wallpaperAdapter: SettingsChipAdapter
    private lateinit var languageAdapter: SettingsChipAdapter
    private lateinit var playerAdapter: SettingsChipAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        appPrefs = AppPreferencesRepository(this)
        pkgRepo = PackageManagerRepository(this)
        cacheManager = CacheManager(this)

        applyWallpaper(appPrefs.getWallpaperTheme())

        setupFocus()
        setupSidebar()
        setupCacheSection()
        setupWallpaperSection()
        setupLanguageSection()
        setupPlayerSection()
        setupHiddenApps()
    }

    override fun onResume() {
        super.onResume()
        loadHiddenApps()
        refreshCacheMetrics()
    }

    private fun applyWallpaper(theme: String) {
        when (theme) {
            "FOCUS" -> binding.ivBackdrop.setImageResource(R.drawable.bg_wallpaper_focus)
            "NEBULA" -> binding.ivBackdrop.setImageResource(R.drawable.bg_wallpaper_nebula)
            else -> binding.ivBackdrop.setImageResource(R.drawable.backdrop_scenery)
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

        binding.btnNavSettings.isSelected = true

        TvFocusHelper.bindFocusScale(binding.btnClearImageCache, scaleRatio = 1.08f, elevationDp = 6f)
        TvFocusHelper.bindFocusScale(binding.btnClearCatalogCache, scaleRatio = 1.08f, elevationDp = 6f)
        TvFocusHelper.bindFocusScale(binding.btnClearAllCache, scaleRatio = 1.08f, elevationDp = 6f)
        TvFocusHelper.bindFocusScale(binding.btnUnhideAll, scaleRatio = 1.08f, elevationDp = 6f)
    }

    private fun setupSidebar() {
        binding.btnNavMedia.setOnClickListener {
            startActivity(Intent(this, HomeActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            })
            finish()
        }
        binding.btnNavHistory.setOnClickListener {
            startActivity(Intent(this, WatchHistoryActivity::class.java))
            finish()
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
            // Already in Launcher Settings
        }
        binding.btnNavPower.setOnClickListener {
            TvPowerDialog.show(supportFragmentManager)
        }
    }

    private fun setupCacheSection() {
        binding.btnClearImageCache.setOnClickListener {
            lifecycleScope.launch {
                cacheManager.clearImageCache()
                Toast.makeText(this@LauncherSettingsActivity, R.string.settings_cache_cleared, Toast.LENGTH_SHORT).show()
                refreshCacheMetrics()
            }
        }

        binding.btnClearCatalogCache.setOnClickListener {
            lifecycleScope.launch {
                cacheManager.clearCatalogCache()
                Toast.makeText(this@LauncherSettingsActivity, R.string.settings_cache_cleared, Toast.LENGTH_SHORT).show()
                refreshCacheMetrics()
            }
        }

        binding.btnClearAllCache.setOnClickListener {
            lifecycleScope.launch {
                cacheManager.clearAllCache()
                Toast.makeText(this@LauncherSettingsActivity, R.string.settings_cache_cleared, Toast.LENGTH_SHORT).show()
                refreshCacheMetrics()
            }
        }
    }

    private fun refreshCacheMetrics() {
        lifecycleScope.launch(Dispatchers.IO) {
            val imgBytes = cacheManager.getImageCacheSize()
            val catBytes = cacheManager.getCatalogCacheSize()
            val totalBytes = imgBytes + catBytes

            withContext(Dispatchers.Main) {
                binding.tvCacheImagesVal.text = cacheManager.formatSize(imgBytes)
                binding.tvCacheCatalogVal.text = cacheManager.formatSize(catBytes)
                binding.tvCacheTotalVal.text = cacheManager.formatSize(totalBytes)
            }
        }
    }

    private fun setupWallpaperSection() {
        val themes = listOf(
            SettingsChip("FOCUS", getString(R.string.settings_wallpaper_focus)),
            SettingsChip("SCENERY", getString(R.string.settings_wallpaper_scenery)),
            SettingsChip("NEBULA", getString(R.string.settings_wallpaper_nebula))
        )

        wallpaperAdapter = SettingsChipAdapter { chip ->
            appPrefs.setWallpaperTheme(chip.id)
            wallpaperAdapter.selectedId = chip.id
            applyWallpaper(chip.id)
            Toast.makeText(this, chip.title, Toast.LENGTH_SHORT).show()
        }

        wallpaperAdapter.submitList(themes)
        wallpaperAdapter.selectedId = appPrefs.getWallpaperTheme()
        binding.rvWallpaperThemes.adapter = wallpaperAdapter

    }

    private fun setupLanguageSection() {
        val languages = listOf(
            SettingsChip("SYSTEM", getString(R.string.settings_lang_system)),
            SettingsChip("uk", "Українська"),
            SettingsChip("ru", "Русский"),
            SettingsChip("en", "English"),
            SettingsChip("be", "Беларуская"),
            SettingsChip("pl", "Polski"),
            SettingsChip("de", "Deutsch"),
            SettingsChip("fr", "Français"),
            SettingsChip("es", "Español")
        )

        languageAdapter = SettingsChipAdapter { chip ->
            appPrefs.setContentLanguage(chip.id)
            languageAdapter.selectedId = chip.id
            Toast.makeText(this, chip.title, Toast.LENGTH_SHORT).show()
        }

        languageAdapter.submitList(languages)
        languageAdapter.selectedId = appPrefs.getContentLanguage()
        binding.rvContentLanguages.adapter = languageAdapter
    }

    private fun setupPlayerSection() {
        val alwaysAsk = SettingsChip("ASK", getString(R.string.settings_player_always_ask))
        val installedPlayers = TvDeepLinkHelper.getInstalledMediaPlayers(this)

        val playerChips = mutableListOf(alwaysAsk)
        installedPlayers.forEach { player ->
            playerChips.add(SettingsChip(player.packageName, player.name))
        }

        playerAdapter = SettingsChipAdapter { chip ->
            appPrefs.setPreferredPlayer(chip.id)
            playerAdapter.selectedId = chip.id
            Toast.makeText(this, chip.title, Toast.LENGTH_SHORT).show()
        }

        playerAdapter.submitList(playerChips)
        playerAdapter.selectedId = appPrefs.getPreferredPlayer()
        binding.rvPreferredPlayers.adapter = playerAdapter
    }

    private fun setupHiddenApps() {
        hiddenAppsAdapter = HiddenAppsAdapter { app ->
            appPrefs.unhideApp(app.packageName)
            Toast.makeText(
                this,
                getString(R.string.settings_toast_unhidden, app.label),
                Toast.LENGTH_SHORT
            ).show()
            loadHiddenApps()
        }
        binding.rvHiddenApps.adapter = hiddenAppsAdapter

        binding.btnUnhideAll.setOnClickListener {
            appPrefs.unhideAll()
            Toast.makeText(this, R.string.settings_btn_unhide_all, Toast.LENGTH_SHORT).show()
            loadHiddenApps()
        }
    }

    private fun loadHiddenApps() {
        lifecycleScope.launch(Dispatchers.IO) {
            val hiddenSet = appPrefs.getHiddenPackages()
            val allApps = pkgRepo.getInstalledTvApps()
            val hiddenApps = allApps.filter { hiddenSet.contains(it.packageName) }

            withContext(Dispatchers.Main) {
                if (hiddenApps.isEmpty()) {
                    binding.containerHiddenEmpty.visibility = View.VISIBLE
                    binding.rvHiddenApps.visibility = View.GONE
                    binding.btnUnhideAll.visibility = View.GONE
                    binding.tvHiddenCount.text = getString(R.string.settings_hidden_count, 0)
                    hiddenAppsAdapter.submitList(emptyList())
                } else {
                    binding.containerHiddenEmpty.visibility = View.GONE
                    binding.rvHiddenApps.visibility = View.VISIBLE
                    binding.btnUnhideAll.visibility = View.VISIBLE
                    binding.tvHiddenCount.text = getString(R.string.settings_hidden_count, hiddenApps.size)
                    hiddenAppsAdapter.submitList(hiddenApps)
                }
            }
        }
    }
}
