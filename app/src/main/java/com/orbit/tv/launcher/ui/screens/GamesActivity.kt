package com.orbit.tv.launcher.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.orbit.tv.launcher.core.focus.TvFocusHelper
import com.orbit.tv.launcher.databinding.ActivityGamesBinding
import com.orbit.tv.launcher.features.home.HomeViewModel
import com.orbit.tv.launcher.features.home.adapters.AppsAdapter
import com.orbit.tv.launcher.ui.overlays.AppOptionsDialog
import com.orbit.tv.launcher.ui.overlays.TvArcadeDialog
import kotlinx.coroutines.launch

class GamesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGamesBinding
    private val viewModel: HomeViewModel by viewModels()
    private lateinit var gamesAdapter: AppsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGamesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupFocus()
        setupGamesList()
        setupListeners()
        setupBackNavigation()
        observeData()
    }

    private fun setupFocus() {
        // Sidebar focus
        TvFocusHelper.bindFocusScale(binding.btnNavApps, scaleRatio = 1.12f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnNavMedia, scaleRatio = 1.12f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnNavIptv, scaleRatio = 1.12f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnNavGames, scaleRatio = 1.12f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnNavCast, scaleRatio = 1.12f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnNavPower, scaleRatio = 1.12f, elevationDp = 8f)

        // Arcade Cards
        TvFocusHelper.bindFocusScale(binding.cardArcadeTetris, scaleRatio = 1.05f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.cardArcadeSnake, scaleRatio = 1.05f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.cardArcade2048, scaleRatio = 1.05f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.cardArcadeBreaker, scaleRatio = 1.05f, elevationDp = 8f)

        // Emulators & Bluetooth
        TvFocusHelper.bindFocusScale(binding.btnBluetoothSettings, scaleRatio = 1.08f, elevationDp = 6f)
        TvFocusHelper.bindFocusScale(binding.cardEmuRetroarch, scaleRatio = 1.05f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.cardEmuDuckstation, scaleRatio = 1.05f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.cardEmuNostalgia, scaleRatio = 1.05f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.cardRomsStorage, scaleRatio = 1.05f, elevationDp = 8f)

        binding.btnNavGames.isSelected = true
        binding.cardArcadeTetris.requestFocus()
    }

    private fun setupGamesList() {
        gamesAdapter = AppsAdapter(
            onAppClick = { game ->
                val launched = viewModel.launchApp(game)
                if (!launched) {
                    Toast.makeText(this, "Не удалось запустить ${game.label}", Toast.LENGTH_SHORT).show()
                }
            },
            onAppLongClick = { game ->
                val dialog = AppOptionsDialog.newInstance(game) {
                    viewModel.loadInstalledApps()
                }
                dialog.show(supportFragmentManager, AppOptionsDialog.TAG)
            }
        )

        binding.rvInstalledGames.apply {
            layoutManager = LinearLayoutManager(this@GamesActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = gamesAdapter
            setHasFixedSize(true)
        }
    }

    private fun setupListeners() {
        // Sidebar navigation
        binding.btnNavMedia.setOnClickListener { finish() }
        binding.btnNavApps.setOnClickListener {
            startActivity(Intent(this, AppsHubActivity::class.java))
            finish()
        }
        binding.btnNavIptv.setOnClickListener {
            startActivity(Intent(this, IptvActivity::class.java))
            finish()
        }
        binding.btnNavCast.setOnClickListener {
            startActivity(Intent(this, CastActivity::class.java))
            finish()
        }
        binding.btnNavPower.setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_POWER_USAGE_SUMMARY))
            } catch (e: Exception) {
                Toast.makeText(this, "Таймер сна: используйте клавишу питания на пульте", Toast.LENGTH_SHORT).show()
            }
        }

        // Bluetooth settings
        binding.btnBluetoothSettings.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            } catch (e: Exception) {
                Toast.makeText(this, "Настройки Bluetooth недоступны на этой прошивке", Toast.LENGTH_SHORT).show()
            }
        }

        // Playable TV Arcades (Authentic NES ROMs)
        binding.cardArcadeSnake.setOnClickListener { launchArcade("mario") }
        binding.cardArcadeTetris.setOnClickListener { launchArcade("tetris") }
        binding.cardArcade2048.setOnClickListener { launchArcade("mario3") }
        binding.cardArcadeBreaker.setOnClickListener { launchArcade("mario2") }

        // Retro Emulators
        binding.cardEmuRetroarch.setOnClickListener {
            launchOrInstall("com.retroarch", "RetroArch TV", "https://play.google.com/store/apps/details?id=com.retroarch")
        }
        binding.cardEmuDuckstation.setOnClickListener {
            launchOrInstall("com.github.stenzek.duckstation", "DuckStation (PS1)", "https://play.google.com/store/apps/details?id=com.github.stenzek.duckstation")
        }
        binding.cardEmuNostalgia.setOnClickListener {
            launchOrInstall("com.nostalgiaemulators.nesfull", "Nostalgia.NES (Dendy)", "https://play.google.com/store/apps/details?id=com.nostalgiaemulators.neslite")
        }
        binding.cardRomsStorage.setOnClickListener {
            val storageIntents = listOf(
                Intent("android.os.storage.action.MANAGE_STORAGE"),
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(Uri.parse("content://com.android.externalstorage.documents/document/primary%3AROMs"), "vnd.android.document/directory")
                },
                Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                }
            )
            var opened = false
            for (intent in storageIntents) {
                try {
                    startActivity(intent)
                    opened = true
                    break
                } catch (_: Exception) {}
            }
            if (!opened) {
                Toast.makeText(this, "Папка ROMs: /storage/emulated/0/ROMs/ (Создайте ее на накопителе)", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun launchArcade(gameType: String) {
        val dialog = TvArcadeDialog.newInstance(gameType)
        dialog.show(supportFragmentManager, TvArcadeDialog.TAG)
    }

    private fun launchOrInstall(packageName: String, appName: String, fallbackUrl: String) {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            startActivity(launchIntent)
        } else {
            try {
                val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
                startActivity(marketIntent)
            } catch (e: Exception) {
                try {
                    val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUrl))
                    startActivity(webIntent)
                } catch (e2: Exception) {
                    Toast.makeText(this, "$appName не установлено на ТВ", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val isDown = event.action == KeyEvent.ACTION_DOWN
        val latencyMs = (System.currentTimeMillis() - event.eventTime).coerceIn(1, 99)

        binding.tvGpLatency.text = "Задержка: $latencyMs мс"
        binding.tvGpLastKey.text = "Кнопка: ${KeyEvent.keyCodeToString(event.keyCode).removePrefix("KEYCODE_")}"

        val targetView: TextView? = when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> binding.btnGpUp
            KeyEvent.KEYCODE_DPAD_DOWN -> binding.btnGpDown
            KeyEvent.KEYCODE_DPAD_LEFT -> binding.btnGpLeft
            KeyEvent.KEYCODE_DPAD_RIGHT -> binding.btnGpRight
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> binding.btnGpOk
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> binding.btnGpBack
            KeyEvent.KEYCODE_MENU -> binding.btnGpMenu
            KeyEvent.KEYCODE_BUTTON_A -> binding.btnGpA
            KeyEvent.KEYCODE_BUTTON_B -> binding.btnGpB
            KeyEvent.KEYCODE_BUTTON_X -> binding.btnGpX
            KeyEvent.KEYCODE_BUTTON_Y -> binding.btnGpY
            KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_L2 -> binding.btnGpL1
            KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_BUTTON_R2 -> binding.btnGpR1
            else -> null
        }

        targetView?.isSelected = isDown

        return super.dispatchKeyEvent(event)
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })
    }

    private fun observeData() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.installedApps.collect { apps ->
                        val games = apps.filter { it.isGame }
                        if (games.isNotEmpty()) {
                            binding.tvEmptyGames.visibility = View.GONE
                            binding.rvInstalledGames.visibility = View.VISIBLE
                            gamesAdapter.submitList(games)
                        } else {
                            binding.tvEmptyGames.visibility = View.VISIBLE
                            binding.rvInstalledGames.visibility = View.GONE
                            gamesAdapter.submitList(emptyList())
                        }
                    }
                }
                launch {
                    viewModel.currentTime.collect { time ->
                        binding.tvClock.text = time
                    }
                }
            }
        }
    }
}
