package com.orbit.tv.launcher.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.orbit.tv.launcher.R
import com.orbit.tv.launcher.core.focus.TvFocusHelper
import com.orbit.tv.launcher.databinding.ActivitySettingsBinding
import com.orbit.tv.launcher.features.apps.repository.AppPreferencesRepository
import com.orbit.tv.launcher.features.home.TvDeepLinkHelper
import com.orbit.tv.launcher.features.home.model.AppModel
import com.orbit.tv.launcher.platform.package_manager.PackageManagerRepository
import com.orbit.tv.launcher.platform.storage.CacheManager
import com.orbit.tv.launcher.platform.wallpaper.TvWallpaperHelper
import com.orbit.tv.launcher.ui.overlays.TvOptionPickerDialog
import com.orbit.tv.launcher.ui.overlays.TvPowerDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class LauncherSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var appPrefs: AppPreferencesRepository
    private lateinit var pkgRepo: PackageManagerRepository
    private lateinit var cacheManager: CacheManager

    private lateinit var hiddenAppsAdapter: HiddenAppsAdapter

    private val pickCustomWallpaperLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            lifecycleScope.launch {
                val customFile = File(filesDir, "custom_wallpaper.jpg")
                val success = saveOptimizedCustomWallpaper(uri, customFile)
                if (success) {
                    appPrefs.setWallpaperTheme("CUSTOM")
                    applyWallpaper("CUSTOM")
                    setupWallpaperSection()
                    Toast.makeText(
                        this@LauncherSettingsActivity,
                        R.string.settings_custom_wallpaper_success,
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        this@LauncherSettingsActivity,
                        R.string.settings_custom_wallpaper_fail,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private suspend fun saveOptimizedCustomWallpaper(uri: Uri, destFile: File): Boolean = withContext(Dispatchers.IO) {
        try {
            // Step 1: Read image dimensions only without loading bitmap pixels into heap (0 bytes heap)
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            }

            val origWidth = boundsOptions.outWidth
            val origHeight = boundsOptions.outHeight
            if (origWidth <= 0 || origHeight <= 0) return@withContext false

            // Step 2: For standard wallpapers (up to 4K 3840x2160), stream-copy directly!
            // Stream copy uses only 64KB buffer: ZERO CPU load, ZERO RAM spike, 100% original color quality!
            if (origWidth <= 3840 && origHeight <= 2160) {
                contentResolver.openInputStream(uri)?.use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output, bufferSize = 65536)
                    }
                }
                return@withContext true
            }

            // Step 3: For extreme oversized images (> 4K / 50-100 MP), safely downsample to 1920x1080 with ARGB_8888
            val targetWidth = 1920
            val targetHeight = 1080
            var sampleSize = 1
            while ((origWidth / (sampleSize * 2)) >= targetWidth &&
                (origHeight / (sampleSize * 2)) >= targetHeight) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inDither = true
            }

            val decoded = contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return@withContext false

            destFile.outputStream().use { outStream ->
                decoded.compress(Bitmap.CompressFormat.PNG, 100, outStream)
            }
            decoded.recycle()
            true
        } catch (e: Throwable) {
            android.util.Log.e("LauncherSettings", "Error saving custom wallpaper: ${e.message}", e)
            false
        }
    }

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
        setupBackNavigation()
        setupCacheSection()
        setupWallpaperSection()
        setupHomeLayoutSection()
        setupLanguageSection()
        setupPlayerSection()
        setupHiddenApps()
    }

    override fun onResume() {
        super.onResume()
        loadHiddenApps()
        refreshCacheMetrics()
        TvWallpaperHelper.apply(appPrefs.getWallpaperTheme(), binding.silkShaderView, binding.ivBackdrop, binding.viewDimmer)
    }

    override fun onPause() {
        super.onPause()
        TvWallpaperHelper.pause(binding.silkShaderView)
    }

    private fun applyWallpaper(theme: String) {
        TvWallpaperHelper.apply(theme, binding.silkShaderView, binding.ivBackdrop, binding.viewDimmer)
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
        TvFocusHelper.bindFocusScale(binding.btnUploadWallpaper, scaleRatio = 1.08f, elevationDp = 6f)
        TvFocusHelper.bindFocusScale(binding.cardSelectWallpaper, scaleRatio = 1.04f, elevationDp = 6f)
        TvFocusHelper.bindFocusScale(binding.cardSelectHomeLayout, scaleRatio = 1.04f, elevationDp = 6f)
        TvFocusHelper.bindFocusScale(binding.cardSelectLanguage, scaleRatio = 1.04f, elevationDp = 6f)
        TvFocusHelper.bindFocusScale(binding.cardSelectPlayer, scaleRatio = 1.04f, elevationDp = 6f)
    }

    private fun setupSidebar() {
        binding.btnNavMedia.setOnClickListener {
            com.orbit.tv.launcher.core.navigation.TvNavHelper.finishWithPop(this)
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
            // Already in Launcher Settings
        }
        binding.btnNavPower.setOnClickListener {
            TvPowerDialog.show(supportFragmentManager)
        }
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!binding.containerSidebar.hasFocus()) {
                    binding.btnNavSettings.requestFocus()
                } else {
                    com.orbit.tv.launcher.core.navigation.TvNavHelper.finishWithPop(this@LauncherSettingsActivity)
                }
            }
        })
    }

    private fun setupCacheSection() {
        binding.btnClearImageCache.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                cacheManager.clearImageCache()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@LauncherSettingsActivity, R.string.settings_cache_cleared, Toast.LENGTH_SHORT).show()
                    refreshCacheMetrics()
                }
            }
        }

        binding.btnClearCatalogCache.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                cacheManager.clearCatalogCache()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@LauncherSettingsActivity, R.string.settings_cache_cleared, Toast.LENGTH_SHORT).show()
                    refreshCacheMetrics()
                }
            }
        }

        binding.btnClearAllCache.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                cacheManager.clearAllCache()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@LauncherSettingsActivity, R.string.settings_cache_cleared, Toast.LENGTH_SHORT).show()
                    refreshCacheMetrics()
                }
            }
        }

        refreshCacheMetrics()
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
        binding.btnUploadWallpaper.setOnClickListener {
            pickCustomWallpaperLauncher.launch("image/*")
        }

        fun buildWallpaperOptions(): List<TvOptionPickerDialog.OptionItem> {
            val current = appPrefs.getWallpaperTheme()
            val customFile = File(filesDir, "custom_wallpaper.jpg")
            val list = mutableListOf<TvOptionPickerDialog.OptionItem>()

            if (customFile.exists()) {
                list.add(
                    TvOptionPickerDialog.OptionItem(
                        id = "CUSTOM",
                        title = getString(R.string.settings_wallpaper_custom),
                        subtitle = "Пользовательское фото из памяти",
                        isSelected = (current == "CUSTOM")
                    )
                )
            }

            list.add(TvOptionPickerDialog.OptionItem("SCENERY", getString(R.string.settings_wallpaper_scenery_fjord), "4K фотообои", current == "SCENERY" || current == "SCENERY_FJORD"))
            list.add(TvOptionPickerDialog.OptionItem("SCENERY_AURORA", getString(R.string.settings_wallpaper_scenery_aurora), "4K фотообои", current == "SCENERY_AURORA"))
            list.add(TvOptionPickerDialog.OptionItem("SCENERY_ALPINE", getString(R.string.settings_wallpaper_scenery_alpine), "4K фотообои", current == "SCENERY_ALPINE"))
            list.add(TvOptionPickerDialog.OptionItem("SCENERY_FOREST", getString(R.string.settings_wallpaper_scenery_forest), "4K фотообои", current == "SCENERY_FOREST"))
            list.add(TvOptionPickerDialog.OptionItem("SCENERY_LAKE", getString(R.string.settings_wallpaper_scenery_lake), "4K фотообои", current == "SCENERY_LAKE"))
            list.add(TvOptionPickerDialog.OptionItem("SILK_VIOLET", getString(R.string.settings_wallpaper_silk_violet), "Живой шейдер 60 FPS", current == "SILK_VIOLET"))
            list.add(TvOptionPickerDialog.OptionItem("SILK_CYAN", getString(R.string.settings_wallpaper_silk_cyan), "Живой шейдер 60 FPS", current == "SILK_CYAN"))
            list.add(TvOptionPickerDialog.OptionItem("SILK_EMERALD", getString(R.string.settings_wallpaper_silk_emerald), "Живой шейдер 60 FPS", current == "SILK_EMERALD"))
            list.add(TvOptionPickerDialog.OptionItem("SILK_MIDNIGHT", getString(R.string.settings_wallpaper_silk_midnight), "Живой шейдер 60 FPS", current == "SILK_MIDNIGHT"))
            list.add(TvOptionPickerDialog.OptionItem("SILK_CRIMSON", getString(R.string.settings_wallpaper_silk_crimson), "Живой шейдер 60 FPS", current == "SILK_CRIMSON"))

            return list
        }

        fun updateSelectedWallpaperLabel() {
            val options = buildWallpaperOptions()
            val current = appPrefs.getWallpaperTheme()
            val matched = options.find { it.id == current }
            binding.tvSelectedWallpaperVal.text = matched?.title ?: getString(R.string.settings_wallpaper_scenery_fjord)
        }

        updateSelectedWallpaperLabel()

        binding.cardSelectWallpaper.setOnClickListener {
            val options = buildWallpaperOptions()
            TvOptionPickerDialog.show(
                fragmentManager = supportFragmentManager,
                title = getString(R.string.settings_section_wallpaper),
                options = options
            ) { selected ->
                appPrefs.setWallpaperTheme(selected.id)
                applyWallpaper(selected.id)
                updateSelectedWallpaperLabel()
                Toast.makeText(this, selected.title, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupHomeLayoutSection() {
        val currentMode = appPrefs.getHomeLayoutMode()
        binding.tvSelectedHomeLayoutVal.text = getHomeLayoutLabel(currentMode)

        binding.cardSelectHomeLayout.setOnClickListener {
            val options = listOf(
                TvOptionPickerDialog.OptionItem(
                    id = AppPreferencesRepository.HOME_LAYOUT_DEFAULT,
                    title = getString(R.string.settings_home_layout_default),
                    isSelected = (appPrefs.getHomeLayoutMode() == AppPreferencesRepository.HOME_LAYOUT_DEFAULT)
                ),
                TvOptionPickerDialog.OptionItem(
                    id = AppPreferencesRepository.HOME_LAYOUT_CINEMA,
                    title = getString(R.string.settings_home_layout_cinema),
                    isSelected = (appPrefs.getHomeLayoutMode() == AppPreferencesRepository.HOME_LAYOUT_CINEMA)
                ),
                TvOptionPickerDialog.OptionItem(
                    id = AppPreferencesRepository.HOME_LAYOUT_MOVIES_FIRST,
                    title = getString(R.string.settings_home_layout_movies_first),
                    isSelected = (appPrefs.getHomeLayoutMode() == AppPreferencesRepository.HOME_LAYOUT_MOVIES_FIRST)
                ),
                TvOptionPickerDialog.OptionItem(
                    id = AppPreferencesRepository.HOME_LAYOUT_NO_SERIES,
                    title = getString(R.string.settings_home_layout_no_series),
                    isSelected = (appPrefs.getHomeLayoutMode() == AppPreferencesRepository.HOME_LAYOUT_NO_SERIES)
                ),
                TvOptionPickerDialog.OptionItem(
                    id = AppPreferencesRepository.HOME_LAYOUT_MINIMAL,
                    title = getString(R.string.settings_home_layout_minimal),
                    isSelected = (appPrefs.getHomeLayoutMode() == AppPreferencesRepository.HOME_LAYOUT_MINIMAL)
                )
            )

            TvOptionPickerDialog.show(
                fragmentManager = supportFragmentManager,
                title = getString(R.string.settings_home_layout),
                options = options
            ) { selected ->
                appPrefs.setHomeLayoutMode(selected.id)
                binding.tvSelectedHomeLayoutVal.text = selected.title
            }
        }
    }

    private fun getHomeLayoutLabel(mode: String): String {
        return when (mode) {
            AppPreferencesRepository.HOME_LAYOUT_CINEMA -> getString(R.string.settings_home_layout_cinema)
            AppPreferencesRepository.HOME_LAYOUT_MOVIES_FIRST -> getString(R.string.settings_home_layout_movies_first)
            AppPreferencesRepository.HOME_LAYOUT_NO_SERIES -> getString(R.string.settings_home_layout_no_series)
            AppPreferencesRepository.HOME_LAYOUT_MINIMAL -> getString(R.string.settings_home_layout_minimal)
            else -> getString(R.string.settings_home_layout_default)
        }
    }

    private fun setupLanguageSection() {
        val languages = listOf(
            TvOptionPickerDialog.OptionItem("SYSTEM", getString(R.string.settings_lang_system), "Автоматически по языку системы"),
            TvOptionPickerDialog.OptionItem("uk", "Українська", "uk • Каталог фильмов и сериалов"),
            TvOptionPickerDialog.OptionItem("ru", "Русский", "ru • Каталог фильмов и сериалов"),
            TvOptionPickerDialog.OptionItem("en", "English", "en • Content catalog & metadata"),
            TvOptionPickerDialog.OptionItem("be", "Беларуская", "be • Каталог кантэнту"),
            TvOptionPickerDialog.OptionItem("pl", "Polski", "pl • Katalog treści i opisy"),
            TvOptionPickerDialog.OptionItem("de", "Deutsch", "de • Inhaltskatalog & Details"),
            TvOptionPickerDialog.OptionItem("fr", "Français", "fr • Catalogue de contenu"),
            TvOptionPickerDialog.OptionItem("es", "Español", "es • Catálogo de contenido")
        )

        fun updateSelectedLabel() {
            val currentCode = appPrefs.getContentLanguage()
            val matched = languages.find { it.id == currentCode }
            binding.tvSelectedLanguageVal.text = matched?.title ?: getString(R.string.settings_lang_system)
        }

        updateSelectedLabel()

        binding.cardSelectLanguage.setOnClickListener {
            val currentCode = appPrefs.getContentLanguage()
            val mappedOptions = languages.map { opt ->
                opt.copy(isSelected = (opt.id == currentCode))
            }

            TvOptionPickerDialog.show(
                fragmentManager = supportFragmentManager,
                title = getString(R.string.settings_dialog_language_title),
                options = mappedOptions
            ) { selected ->
                appPrefs.setContentLanguage(selected.id)
                updateSelectedLabel()
                Toast.makeText(this, selected.title, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupPlayerSection() {
        fun buildPlayerOptions(): List<TvOptionPickerDialog.OptionItem> {
            val currentPlayer = appPrefs.getPreferredPlayer()
            val options = mutableListOf<TvOptionPickerDialog.OptionItem>()

            options.add(
                TvOptionPickerDialog.OptionItem(
                    id = "ASK",
                    title = getString(R.string.settings_player_always_ask),
                    subtitle = "Показывать диалог выбора плеера при каждом запуске",
                    isSelected = ("ASK" == currentPlayer)
                )
            )

            val installed = TvDeepLinkHelper.getInstalledMediaPlayers(this)
            installed.forEach { player ->
                options.add(
                    TvOptionPickerDialog.OptionItem(
                        id = player.packageName,
                        title = player.name,
                        subtitle = player.packageName,
                        isSelected = (player.packageName == currentPlayer)
                    )
                )
            }
            return options
        }

        fun updateSelectedPlayerLabel() {
            val currentPlayer = appPrefs.getPreferredPlayer()
            if (currentPlayer == "ASK") {
                binding.tvSelectedPlayerVal.text = getString(R.string.settings_player_always_ask)
            } else {
                val installed = TvDeepLinkHelper.getInstalledMediaPlayers(this)
                val matched = installed.find { it.packageName == currentPlayer }
                binding.tvSelectedPlayerVal.text = matched?.name ?: currentPlayer
            }
        }

        updateSelectedPlayerLabel()

        binding.cardSelectPlayer.setOnClickListener {
            val options = buildPlayerOptions()
            TvOptionPickerDialog.show(
                fragmentManager = supportFragmentManager,
                title = getString(R.string.settings_dialog_player_title),
                options = options
            ) { selected ->
                appPrefs.setPreferredPlayer(selected.id)
                updateSelectedPlayerLabel()
                Toast.makeText(this, selected.title, Toast.LENGTH_SHORT).show()
            }
        }
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
