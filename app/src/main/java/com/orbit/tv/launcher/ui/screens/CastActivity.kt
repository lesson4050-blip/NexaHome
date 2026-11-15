package com.orbit.tv.launcher.ui.screens

import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.orbit.tv.launcher.R
import com.orbit.tv.launcher.core.focus.TvFocusHelper
import com.orbit.tv.launcher.databinding.ActivityCastBinding
import com.orbit.tv.launcher.features.cast.CastServerManager
import com.orbit.tv.launcher.ui.overlays.CastProtocolDialog
import com.orbit.tv.launcher.ui.overlays.TvPowerDialog
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.NetworkInterface

import com.orbit.tv.launcher.platform.wallpaper.TvWallpaperHelper

class CastActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCastBinding
    private val appPrefs by lazy { com.orbit.tv.launcher.features.apps.repository.AppPreferencesRepository(this) }
    private var countdownJob: Job? = null
    private var secondsRemaining = 15 * 60 // 15 minutes
    private var currentPin = "849-210"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCastBinding.inflate(layoutInflater)
        setContentView(binding.root)

        CastServerManager.start(this)
        setupFocus()
        setupListeners()
        setupBackNavigation()
    }

    override fun onResume() {
        super.onResume()
        updateProtocolBadges()
        refreshPinAndQr(animatePin = false)
        TvWallpaperHelper.apply(appPrefs.getWallpaperTheme(), binding.silkShaderView, binding.ivBackdrop, binding.viewDimmer)
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

        TvFocusHelper.bindFocusScale(binding.btnRefreshPin, scaleRatio = 1.08f, elevationDp = 6f)
        TvFocusHelper.bindFocusScale(binding.btnProtoDlna, scaleRatio = 1.04f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnProtoMiracast, scaleRatio = 1.04f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnProtoChromecast, scaleRatio = 1.04f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnProtoAirplay, scaleRatio = 1.04f, elevationDp = 8f)

        binding.btnNavCast.isSelected = true
        binding.btnProtoDlna.requestFocus()
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
        binding.btnNavIptv.setOnClickListener {
            com.orbit.tv.launcher.core.navigation.TvNavHelper.openScreen(this, IptvActivity::class.java)
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

        binding.btnRefreshPin.setOnClickListener {
            refreshPinAndQr(animatePin = true)
        }

        // 0. DLNA / UPnP Media Renderer (Built-in)
        binding.btnProtoDlna.setOnClickListener {
            val ip = getLocalIpAddress() ?: "127.0.0.1"
            val model = android.os.Build.MODEL ?: "TV"
            val friendlyName = if (model.contains("TV", ignoreCase = true)) model else "Smart TV ($model)"
            val displayHost = if (ip.startsWith("10.0.2.")) "localhost:8080 (Emulator)" else "$ip:8080"
            CastProtocolDialog.show(
                fragmentManager = supportFragmentManager,
                title = getString(R.string.cast_proto_dlna_title),
                subtitle = getString(R.string.cast_proto_dlna_desc),
                instructions = getString(R.string.cast_dlna_dialog_msg, friendlyName, displayHost),
                iconRes = R.drawable.ic_cast_dlna,
                primaryText = getString(R.string.cast_btn_understood),
                localIp = ip
            )
        }

        // 1. Miracast / Wi-Fi Display (Android Smart View & Windows)
        binding.btnProtoMiracast.setOnClickListener {
            if (hasHardwareMiracastPackage()) {
                if (tryLaunchMiracast()) return@setOnClickListener
            }
            val isAirScreenInstalled = isAppInstalled("com.ionitech.airscreen")
            CastProtocolDialog.show(
                fragmentManager = supportFragmentManager,
                title = getString(R.string.cast_proto_miracast_title),
                subtitle = getString(R.string.cast_proto_miracast_desc),
                instructions = getString(R.string.cast_miracast_instructions),
                iconRes = R.drawable.ic_cast_miracast,
                primaryText = if (isAirScreenInstalled) getString(R.string.cast_btn_launch_airscreen) else getString(R.string.cast_btn_install_airscreen),
                localIp = getLocalIpAddress()
            )
        }

        // 2. Google Cast / Chromecast
        binding.btnProtoChromecast.setOnClickListener {
            val isAirScreenInstalled = isAppInstalled("com.ionitech.airscreen")
            CastProtocolDialog.show(
                fragmentManager = supportFragmentManager,
                title = getString(R.string.cast_proto_chromecast_title),
                subtitle = getString(R.string.cast_proto_chromecast_desc),
                instructions = getString(R.string.cast_chromecast_instructions),
                iconRes = R.drawable.ic_cast_chromecast,
                primaryText = if (isAirScreenInstalled) getString(R.string.cast_btn_launch_airscreen) else getString(R.string.cast_btn_install_airscreen),
                localIp = getLocalIpAddress()
            )
        }

        // 3. Apple AirPlay (iOS / macOS Mirroring)
        binding.btnProtoAirplay.setOnClickListener {
            val isAirScreenInstalled = isAppInstalled("com.ionitech.airscreen")
            CastProtocolDialog.show(
                fragmentManager = supportFragmentManager,
                title = getString(R.string.cast_proto_airplay_title),
                subtitle = getString(R.string.cast_proto_airplay_desc),
                instructions = getString(R.string.cast_airplay_instructions),
                iconRes = R.drawable.ic_cast_airplay,
                primaryText = if (isAirScreenInstalled) getString(R.string.cast_btn_launch_airscreen) else getString(R.string.cast_btn_install_airscreen),
                localIp = getLocalIpAddress()
            )
        }
    }

    private fun updateProtocolBadges() {
        val isAirScreenInstalled = isAppInstalled("com.ionitech.airscreen")
        val hasHwMiracast = hasHardwareMiracastPackage()

        val emerald = ContextCompat.getColor(this, R.color.vpn_emerald)
        val cyan = ContextCompat.getColor(this, R.color.leanback_cyan)

        if (isAirScreenInstalled) {
            binding.badgeAirplay.text = getString(R.string.cast_badge_open)
            binding.badgeAirplay.setTextColor(emerald)
            binding.badgeChromecast.text = getString(R.string.cast_badge_open)
            binding.badgeChromecast.setTextColor(emerald)
        } else {
            binding.badgeAirplay.text = getString(R.string.cast_badge_setup)
            binding.badgeAirplay.setTextColor(cyan)
            binding.badgeChromecast.text = getString(R.string.cast_badge_setup)
            binding.badgeChromecast.setTextColor(cyan)
        }

        if (hasHwMiracast) {
            binding.badgeMiracast.text = getString(R.string.cast_badge_launch)
            binding.badgeMiracast.setTextColor(Color.parseColor("#C084FC"))
        } else if (isAirScreenInstalled) {
            binding.badgeMiracast.text = getString(R.string.cast_badge_open)
            binding.badgeMiracast.setTextColor(emerald)
        } else {
            binding.badgeMiracast.text = getString(R.string.cast_badge_setup)
            binding.badgeMiracast.setTextColor(cyan)
        }
    }

    private fun refreshPinAndQr(animatePin: Boolean) {
        currentPin = if (animatePin) {
            CastServerManager.refreshPin()
        } else {
            CastServerManager.getPin()
        }

        val ip = getLocalIpAddress()
        if (ip != null) {
            val isEmulator = ip.startsWith("10.0.2.")
            val displayHost = if (isEmulator) "localhost:8080 (Emulator)" else "$ip:8080"
            val model = android.os.Build.MODEL ?: "TV"
            val deviceName = if (model.contains("TV", ignoreCase = true)) model else "Smart TV ($model)"
            binding.tvCastNetworkInfo.text = "$deviceName • $displayHost"
            binding.castPairingPin.text = "PIN: $currentPin"
            binding.tvCastExpiry.visibility = View.VISIBLE
            binding.imgCastQr.alpha = 1.0f

            val qrUrl = if (isEmulator) {
                "http://localhost:8080/cast?pin=$currentPin"
            } else {
                "http://$ip:8080/cast?pin=$currentPin"
            }
            val qrBitmap = generateQrCodeBitmap(qrUrl, 400)
            if (qrBitmap != null) {
                binding.imgCastQr.setImageBitmap(qrBitmap)
            }
            startCountdownTimer()
        } else {
            binding.tvCastNetworkInfo.text = getString(R.string.cast_network_offline)
            binding.castPairingPin.text = getString(R.string.cast_pin_offline)
            binding.tvCastExpiry.visibility = View.INVISIBLE
            binding.imgCastQr.setImageResource(R.drawable.ic_wifi)
            binding.imgCastQr.alpha = 0.35f
            countdownJob?.cancel()
        }

        if (animatePin) {
            binding.castPairingPin.animate()
                .scaleX(1.15f)
                .scaleY(1.15f)
                .setDuration(120)
                .withEndAction {
                    binding.castPairingPin.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(140)
                        .start()
                }
                .start()
        }
    }

    private fun startCountdownTimer() {
        countdownJob?.cancel()
        secondsRemaining = 15 * 60 // 15 minutes
        countdownJob = lifecycleScope.launch {
            while (isActive && secondsRemaining > 0) {
                val m = secondsRemaining / 60
                val s = secondsRemaining % 60
                binding.tvCastExpiry.text = getString(R.string.cast_expiry_format, m, s)
                delay(1000)
                secondsRemaining--
            }
            if (isActive && secondsRemaining <= 0) {
                refreshPinAndQr(animatePin = false)
            }
        }
    }

    private fun generateQrCodeBitmap(content: String, sizePx: Int): Bitmap? {
        return try {
            val hints = mapOf(
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.MARGIN to 1
            )
            val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bmp.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            bmp
        } catch (e: Exception) {
            null
        }
    }

    private fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            for (intf in interfaces) {
                val addrs = intf.inetAddresses ?: continue
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val host = addr.hostAddress ?: continue
                        if (host.startsWith("192.168.") || host.startsWith("10.") || host.startsWith("172.")) {
                            return host
                        }
                    }
                }
            }
        } catch (e: Exception) {}
        return null
    }

    private fun isAppInstalled(pkg: String): Boolean {
        return try {
            packageManager.getPackageInfo(pkg, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun hasHardwareMiracastPackage(): Boolean {
        val oemPackages = listOf(
            "com.droidlogic.miracast",
            "com.amlogic.miracast",
            "com.actions.wifidisplay",
            "com.rockchip.mediacenter",
            "com.softwinner.miracast"
        )
        return oemPackages.any { isAppInstalled(it) }
    }

    private fun launchAirScreen() {
        val launchIntent = packageManager.getLaunchIntentForPackage("com.ionitech.airscreen")
        if (launchIntent != null) {
            startActivity(launchIntent)
        }
    }

    private fun tryLaunchMiracast(): Boolean {
        // 1. Hardware Amlogic/Droidlogic Miracast activity
        try {
            val intent = Intent().apply {
                component = ComponentName("com.droidlogic.miracast", "com.droidlogic.miracast.WiFiDirectMainActivity")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            return true
        } catch (e: Exception) {}

        val oemPackages = listOf(
            "com.droidlogic.miracast",
            "com.amlogic.miracast",
            "com.actions.wifidisplay",
            "com.rockchip.mediacenter",
            "com.softwinner.miracast"
        )
        for (pkg in oemPackages) {
            val launchIntent = packageManager.getLaunchIntentForPackage(pkg)
            if (launchIntent != null) {
                startActivity(launchIntent)
                return true
            }
        }

        try {
            startActivity(Intent("android.settings.WIFI_DISPLAY_SETTINGS"))
            return true
        } catch (e: Exception) {}

        try {
            startActivity(Intent(Settings.ACTION_CAST_SETTINGS))
            return true
        } catch (e: Exception) {}

        return false
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!binding.containerSidebar.hasFocus()) {
                    binding.btnNavCast.requestFocus()
                } else {
                    com.orbit.tv.launcher.core.navigation.TvNavHelper.finishWithPop(this@CastActivity)
                }
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        countdownJob?.cancel()
    }
}
