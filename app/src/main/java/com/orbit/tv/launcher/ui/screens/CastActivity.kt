package com.orbit.tv.launcher.ui.screens

import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.orbit.tv.launcher.core.focus.TvFocusHelper
import com.orbit.tv.launcher.databinding.ActivityCastBinding
import com.orbit.tv.launcher.features.cast.CastWebServer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.NetworkInterface

class CastActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCastBinding
    private var countdownJob: Job? = null
    private var secondsRemaining = 15 * 60 // 15 minutes
    private var currentPin = "849-210"
    private var webServer: CastWebServer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCastBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupFocus()
        setupListeners()
        setupBackNavigation()
        refreshPinAndQr(showToast = false)
        startWebServer()
    }

    private fun startWebServer() {
        webServer = CastWebServer(applicationContext, 8080) { currentPin }
        webServer?.start(lifecycleScope)
    }

    private fun setupFocus() {
        TvFocusHelper.bindFocusScale(binding.btnNavApps, scaleRatio = 1.12f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnNavMedia, scaleRatio = 1.12f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnNavIptv, scaleRatio = 1.12f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnNavGames, scaleRatio = 1.12f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnNavCast, scaleRatio = 1.12f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnNavPower, scaleRatio = 1.12f, elevationDp = 8f)

        TvFocusHelper.bindFocusScale(binding.btnRefreshPin, scaleRatio = 1.08f, elevationDp = 6f)
        TvFocusHelper.bindFocusScale(binding.btnProtoMiracast, scaleRatio = 1.04f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnProtoChromecast, scaleRatio = 1.04f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnProtoAirplay, scaleRatio = 1.04f, elevationDp = 8f)

        binding.btnNavCast.isSelected = true
        binding.btnProtoMiracast.requestFocus()
    }

    private fun setupListeners() {
        binding.btnNavMedia.setOnClickListener { finish() }
        binding.btnNavApps.setOnClickListener {
            startActivity(Intent(this, AppsHubActivity::class.java))
            finish()
        }
        binding.btnNavIptv.setOnClickListener {
            startActivity(Intent(this, IptvActivity::class.java))
            finish()
        }
        binding.btnNavGames.setOnClickListener {
            startActivity(Intent(this, GamesActivity::class.java))
            finish()
        }
        binding.btnNavPower.setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_POWER_USAGE_SUMMARY))
            } catch (e: Exception) {
                Toast.makeText(this, "Таймер сна: используйте клавишу питания на пульте", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnRefreshPin.setOnClickListener {
            refreshPinAndQr(showToast = true)
        }

        // 1. Miracast / Wi-Fi Display (Hardware screen mirroring)
        binding.btnProtoMiracast.setOnClickListener {
            val opened = tryLaunchMiracast()
            if (!opened) {
                Toast.makeText(this, "Miracast активен. Откройте «Беспроводной экран» на смартфоне или ПК", Toast.LENGTH_LONG).show()
            }
        }

        // 2. Google Cast / Chromecast
        binding.btnProtoChromecast.setOnClickListener {
            val opened = tryLaunchAirScreen("Chromecast")
            if (!opened) {
                try {
                    startActivity(Intent(Settings.ACTION_CAST_SETTINGS))
                } catch (e: Exception) {
                    Toast.makeText(this, "Chromecast готов к трансляции из YouTube, Chrome или Google Home", Toast.LENGTH_LONG).show()
                }
            }
        }

        // 3. Apple AirPlay (iOS / macOS Mirroring)
        binding.btnProtoAirplay.setOnClickListener {
            val opened = tryLaunchAirScreen("Apple AirPlay")
            if (!opened) {
                handleAirplayActionFallback()
            }
        }
    }

    private fun refreshPinAndQr(showToast: Boolean) {
        val p1 = (100..999).random()
        val p2 = (100..999).random()
        currentPin = "$p1-$p2"
        binding.castPairingPin.text = "PIN: $currentPin"

        val ip = getLocalIpAddress()
        val qrUrl = "http://$ip:8080/cast?pin=$currentPin"

        val qrBitmap = generateQrCodeBitmap(qrUrl, 400)
        if (qrBitmap != null) {
            binding.imgCastQr.setImageBitmap(qrBitmap)
        }

        startCountdownTimer()

        if (showToast) {
            Toast.makeText(this, "Новый PIN сгенерирован: $currentPin", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startCountdownTimer() {
        countdownJob?.cancel()
        secondsRemaining = 15 * 60 // 15 minutes
        countdownJob = lifecycleScope.launch {
            while (isActive && secondsRemaining > 0) {
                val m = secondsRemaining / 60
                val s = secondsRemaining % 60
                binding.tvCastExpiry.text = String.format("Действителен: %02d:%02d", m, s)
                delay(1000)
                secondsRemaining--
            }
            if (isActive && secondsRemaining <= 0) {
                refreshPinAndQr(showToast = false)
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

    private fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (intf in interfaces) {
                val addrs = intf.inetAddresses
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
        return "192.168.31.204"
    }

    private fun tryLaunchMiracast(): Boolean {
        // 1. Try launching hardware Amlogic/Droidlogic Miracast directly
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

    private fun tryLaunchAirScreen(protocolName: String): Boolean {
        val airScreenPkg = "com.ionitech.airscreen"
        val launchIntent = packageManager.getLaunchIntentForPackage(airScreenPkg)
        if (launchIntent != null) {
            startActivity(launchIntent)
            Toast.makeText(this, "$protocolName активен через AirScreen. Выберите Orbit TV на телефоне", Toast.LENGTH_LONG).show()
            return true
        }
        return false
    }

    private fun handleAirplayActionFallback() {
        val ip = getLocalIpAddress()
        AlertDialog.Builder(this)
            .setTitle("Apple AirPlay")
            .setMessage("Для трансляции с iPhone, iPad или Mac убедитесь, что устройство подключено к одной сети Wi-Fi.\n\nИмя ТВ: Orbit TV\nIP адрес: $ip\n\nРекомендуется установить AirScreen из Google Play.")
            .setPositiveButton("Установить AirScreen") { _, _ ->
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.ionitech.airscreen")))
                } catch (e: Exception) {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.ionitech.airscreen")))
                    } catch (e2: Exception) {}
                }
            }
            .setNegativeButton("Понятно", null)
            .show()
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        countdownJob?.cancel()
        webServer?.stop()
    }
}
