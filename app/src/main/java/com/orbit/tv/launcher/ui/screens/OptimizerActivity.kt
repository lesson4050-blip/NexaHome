package com.orbit.tv.launcher.ui.screens

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.orbit.tv.launcher.R
import com.orbit.tv.launcher.core.focus.TvFocusHelper
import com.orbit.tv.launcher.databinding.ActivityOptimizerBinding
import com.orbit.tv.launcher.ui.overlays.TvPowerDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class OptimizerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOptimizerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOptimizerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupFocus()
        setupListeners()
        setupBackNavigation()

        loadAllMetrics()
        startClockLoop()
    }

    private fun setupFocus() {
        // Sidebar navigation
        TvFocusHelper.bindFocusScale(binding.btnNavMedia, scaleRatio = 1.12f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnNavHistory, scaleRatio = 1.12f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnNavApps, scaleRatio = 1.12f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnNavIptv, scaleRatio = 1.12f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnNavGames, scaleRatio = 1.12f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnNavCast, scaleRatio = 1.12f, elevationDp = 8f)
        TvFocusHelper.bindFocusScale(binding.btnNavPower, scaleRatio = 1.12f, elevationDp = 8f)

        // Action Buttons
        TvFocusHelper.bindFocusScale(binding.btnBoostRam, scaleRatio = 1.04f, elevationDp = 6f)
        TvFocusHelper.bindFocusScale(binding.btnStorageClean, scaleRatio = 1.04f, elevationDp = 6f)
        TvFocusHelper.bindFocusScale(binding.btnTestSpeed, scaleRatio = 1.04f, elevationDp = 6f)

        // System Tweaks Buttons
        TvFocusHelper.bindFocusScale(binding.btnTweakDev, scaleRatio = 1.06f, elevationDp = 6f)
        TvFocusHelper.bindFocusScale(binding.btnTweakDisplay, scaleRatio = 1.06f, elevationDp = 6f)
        TvFocusHelper.bindFocusScale(binding.btnTweakSound, scaleRatio = 1.06f, elevationDp = 6f)
        TvFocusHelper.bindFocusScale(binding.btnTweakApps, scaleRatio = 1.06f, elevationDp = 6f)

        binding.btnNavGames.isSelected = true
        binding.root.post {
            binding.btnBoostRam.requestFocus()
        }
    }

    private fun setupListeners() {
        // Sidebar
        binding.btnNavMedia.setOnClickListener { finish() }
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
        binding.btnNavCast.setOnClickListener {
            startActivity(Intent(this, CastActivity::class.java))
            finish()
        }
        binding.btnNavPower.setOnClickListener {
            TvPowerDialog.show(supportFragmentManager)
        }

        // Actions
        binding.btnBoostRam.setOnClickListener {
            boostRam()
        }

        binding.btnStorageClean.setOnClickListener {
            openSystemSettings(
                Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS),
                getString(R.string.toast_storage_unavailable)
            )
        }

        binding.btnTestSpeed.setOnClickListener {
            runNetworkSpeedTest()
        }

        // Tweaks
        binding.btnTweakDev.setOnClickListener {
            openSystemSettings(
                Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
                getString(R.string.toast_dev_options_hint)
            )
        }

        binding.btnTweakDisplay.setOnClickListener {
            openSystemSettings(
                Intent(Settings.ACTION_DISPLAY_SETTINGS),
                getString(R.string.toast_display_unavailable)
            )
        }

        binding.btnTweakSound.setOnClickListener {
            openSystemSettings(
                Intent(Settings.ACTION_SOUND_SETTINGS),
                getString(R.string.toast_audio_unavailable)
            )
        }

        binding.btnTweakApps.setOnClickListener {
            openSystemSettings(
                Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS),
                getString(R.string.toast_apps_unavailable)
            )
        }
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })
    }

    private fun loadAllMetrics() {
        loadRamStats()
        loadStorageStats()
        loadNetworkInfo()
        loadHardwareInfo()
    }

    // 1. RAM Monitor
    private fun loadRamStats() {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)

        val totalBytes = mi.totalMem
        val availBytes = mi.availMem
        val usedBytes = totalBytes - availBytes

        val usedGb = usedBytes / (1024.0 * 1024.0 * 1024.0)
        val totalGb = totalBytes / (1024.0 * 1024.0 * 1024.0)
        val percent = (usedBytes * 100 / totalBytes).toInt().coerceIn(0, 100)

        val freeMb = (availBytes / (1024 * 1024)).toInt()
        val usedMb = (usedBytes / (1024 * 1024)).toInt()

        binding.tvRamMain.text = getString(R.string.optimizer_ram_format, usedGb, totalGb, percent)
        binding.pbRam.progress = percent
        binding.tvRamDetails.text = getString(R.string.optimizer_ram_details, freeMb, usedMb)

        if (percent > 85) {
            binding.tvRamBadge.text = getString(R.string.optimizer_badge_high)
            binding.tvRamBadge.setTextColor(getColor(android.R.color.holo_red_light))
        } else {
            binding.tvRamBadge.text = getString(R.string.optimizer_badge_normal)
            binding.tvRamBadge.setTextColor(getColor(R.color.leanback_cyan))
        }
    }

    // Real RAM Cleanup (Zero Emojis)
    private fun boostRam() {
        binding.btnBoostRam.isEnabled = false
        binding.btnBoostRam.text = getString(R.string.optimizer_btn_boosting)

        lifecycleScope.launch(Dispatchers.IO) {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val miBefore = ActivityManager.MemoryInfo()
            am.getMemoryInfo(miBefore)

            val packages = packageManager.getInstalledPackages(0)
            for (pkg in packages) {
                if (pkg.packageName != packageName) {
                    try {
                        am.killBackgroundProcesses(pkg.packageName)
                    } catch (_: Exception) {}
                }
            }

            System.gc()
            delay(400)

            val miAfter = ActivityManager.MemoryInfo()
            am.getMemoryInfo(miAfter)

            val freedBytes = miAfter.availMem - miBefore.availMem
            val freedMb = (freedBytes / (1024 * 1024)).coerceAtLeast(0)

            withContext(Dispatchers.Main) {
                loadRamStats()
                binding.btnBoostRam.isEnabled = true
                val resultText = if (freedMb > 0) "+${freedMb} MB" else getString(R.string.optimizer_boost_done)
                binding.btnBoostRam.text = resultText
                binding.pbRam.animate().scaleX(1.04f).scaleY(1.04f).setDuration(150).withEndAction {
                    binding.pbRam.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
                }.start()

                // Revert button text after 2 seconds
                binding.btnBoostRam.postDelayed({
                    if (!isDestroyed && !isFinishing) {
                        binding.btnBoostRam.text = getString(R.string.optimizer_btn_boost)
                    }
                }, 2200)
            }
        }
    }

    // 2. Storage Stats
    private fun loadStorageStats() {
        try {
            val dataDir = Environment.getDataDirectory()
            val stat = StatFs(dataDir.path)

            val totalBytes = stat.totalBytes
            val freeBytes = stat.availableBytes
            val usedBytes = totalBytes - freeBytes

            val usedGb = usedBytes / (1024.0 * 1024.0 * 1024.0)
            val totalGb = totalBytes / (1024.0 * 1024.0 * 1024.0)
            val freeGb = freeBytes / (1024.0 * 1024.0 * 1024.0)
            val percent = (usedBytes * 100 / totalBytes).toInt().coerceIn(0, 100)

            binding.tvStorageMain.text = getString(R.string.optimizer_storage_format, usedGb, totalGb, percent)
            binding.pbStorage.progress = percent
            binding.tvStorageDetails.text = getString(R.string.optimizer_storage_free, freeGb)
        } catch (_: Exception) {
            binding.tvStorageMain.text = getString(R.string.optimizer_storage_ready)
        }
    }

    // 3. Network Info & Speedtest (Zero Emojis, Safe across all devices)
    private fun loadNetworkInfo() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNet = cm.activeNetwork
        val caps = cm.getNetworkCapabilities(activeNet)

        var connectionType = getString(R.string.optimizer_net_badge_no)
        var connDetails = getString(R.string.optimizer_net_no_conn)

        if (caps != null) {
            when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                    connectionType = "WI-FI"
                    val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                    val info = wm?.connectionInfo
                    val freq = info?.frequency ?: 0
                    val freqStr = if (freq >= 4900) "5 GHz" else if (freq > 0) "2.4 GHz" else ""
                    val speed = info?.linkSpeed ?: -1
                    val speedStr = if (speed > 0) "$speed Mbps" else ""

                    val parts = mutableListOf(getString(R.string.optimizer_net_wifi))
                    if (freqStr.isNotEmpty()) parts.add(freqStr)
                    if (speedStr.isNotEmpty()) parts.add(speedStr)
                    connDetails = parts.joinToString(" • ")
                }
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> {
                    connectionType = "ETHERNET"
                    connDetails = getString(R.string.optimizer_net_lan)
                }
                caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> {
                    connectionType = "VPN"
                    connDetails = getString(R.string.optimizer_net_vpn)
                }
                else -> {
                    connectionType = getString(R.string.optimizer_net_badge_connected)
                    connDetails = getString(R.string.optimizer_net_online)
                }
            }
        }

        binding.tvNetType.text = connectionType
        binding.tvNetInfo.text = connDetails

        // IP address
        val localIp = getLocalIpAddress() ?: "127.0.0.1"
        binding.tvNetIp.text = "IP: $localIp"
    }

    private fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    // Real Socket Ping & HTTP Speedtest (Zero Emojis)
    private fun runNetworkSpeedTest() {
        binding.btnTestSpeed.isEnabled = false
        binding.btnTestSpeed.text = getString(R.string.optimizer_speedtest_running)
        binding.tvPingResult.text = getString(R.string.optimizer_ping_testing)
        binding.tvSpeedResult.text = getString(R.string.optimizer_speed_testing)

        lifecycleScope.launch(Dispatchers.IO) {
            // Real Socket Ping to Google DNS (8.8.8.8:53)
            var pingMs = -1L
            try {
                val start = System.currentTimeMillis()
                val socket = Socket()
                socket.connect(InetSocketAddress("8.8.8.8", 53), 2000)
                pingMs = (System.currentTimeMillis() - start).coerceAtLeast(1)
                socket.close()
            } catch (_: Exception) {
                try {
                    val start = System.currentTimeMillis()
                    val addr = InetAddress.getByName("8.8.8.8")
                    if (addr.isReachable(1500)) {
                        pingMs = (System.currentTimeMillis() - start).coerceAtLeast(1)
                    }
                } catch (_: Exception) {}
            }

            // Real HTTP chunk download test (1.5 MB)
            var speedMbps = 0.0
            try {
                val url = URL("https://speed.cloudflare.com/__down?bytes=1500000")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 3000
                conn.readTimeout = 4000
                conn.instanceFollowRedirects = true

                val start = System.currentTimeMillis()
                val stream = conn.inputStream
                val buffer = ByteArray(8192)
                var bytesReadTotal = 0L
                var read: Int

                while (stream.read(buffer).also { read = it } != -1) {
                    bytesReadTotal += read
                }
                stream.close()

                val durationSec = (System.currentTimeMillis() - start) / 1000.0
                if (durationSec > 0.05) {
                    speedMbps = (bytesReadTotal * 8.0) / (durationSec * 1_000_000.0)
                }
            } catch (_: Exception) {}

            withContext(Dispatchers.Main) {
                binding.btnTestSpeed.isEnabled = true
                binding.btnTestSpeed.text = getString(R.string.optimizer_speedtest_retry)

                if (pingMs > 0) {
                    binding.tvPingResult.text = getString(R.string.optimizer_ping_format, pingMs.toString())
                } else {
                    binding.tvPingResult.text = getString(R.string.optimizer_ping_unavailable)
                }

                if (speedMbps > 0.1) {
                    binding.tvSpeedResult.text = getString(R.string.optimizer_speed_format, speedMbps)
                } else {
                    binding.tvSpeedResult.text = getString(R.string.optimizer_speed_available)
                }
            }
        }
    }

    // 4. Hardware Specs & Uptime (Safe, complete across all Amlogic/TV devices)
    private fun loadHardwareInfo() {
        val manufacturer = Build.MANUFACTURER?.replaceFirstChar { it.uppercase() } ?: "Android"
        val rawModel = Build.MODEL ?: "TV Box"
        val modelText = if (rawModel.contains(manufacturer, ignoreCase = true)) {
            rawModel
        } else {
            "$manufacturer $rawModel"
        }
        binding.tvHwModel.text = getString(R.string.optimizer_model_format, modelText)

        val cpuName = getCpuHardware()
        val cores = Runtime.getRuntime().availableProcessors()
        val coresWord = if (cores == 1) getString(R.string.optimizer_core_single) else getString(R.string.optimizer_core_plural)
        binding.tvHwChip.text = getString(R.string.optimizer_chip_format, cpuName, cores, coresWord)

        val release = Build.VERSION.RELEASE
        val sdk = Build.VERSION.SDK_INT
        binding.tvHwAndroid.text = getString(R.string.optimizer_os_format, release, sdk)

        try {
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                display ?: wm.defaultDisplay
            } else {
                wm.defaultDisplay
            }
            val mode = display?.mode
            if (mode != null) {
                val hz = mode.refreshRate.roundToInt()
                binding.tvHwDisplay.text = getString(R.string.optimizer_display_format, mode.physicalWidth, mode.physicalHeight, hz)
            } else {
                val metrics = resources.displayMetrics
                binding.tvHwDisplay.text = getString(R.string.optimizer_display_format, metrics.widthPixels, metrics.heightPixels, 60)
            }
        } catch (_: Exception) {
            val metrics = resources.displayMetrics
            binding.tvHwDisplay.text = getString(R.string.optimizer_display_format, metrics.widthPixels, metrics.heightPixels, 60)
        }

        val uptimeMs = SystemClock.elapsedRealtime()
        val hours = (uptimeMs / (1000 * 60 * 60)).toInt()
        val minutes = ((uptimeMs / (1000 * 60)) % 60).toInt()
        binding.tvUptime.text = getString(R.string.optimizer_uptime_format, hours, minutes)
    }

    private fun getCpuHardware(): String {
        // 1. Build.SOC_MODEL (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val soc = Build.SOC_MODEL
            if (!soc.isNullOrBlank() && soc != "unknown") return soc
        }

        // 2. Read /proc/cpuinfo Hardware line
        try {
            val cpuInfo = File("/proc/cpuinfo")
            if (cpuInfo.exists() && cpuInfo.canRead()) {
                val hw = cpuInfo.useLines { lines ->
                    lines.firstOrNull { it.startsWith("Hardware", ignoreCase = true) }
                        ?.substringAfter(":")
                        ?.trim()
                }
                if (!hw.isNullOrBlank() && hw != "unknown") {
                    return hw
                }
            }
        } catch (_: Exception) {}

        // 3. Build.HARDWARE / Build.BOARD
        val hw = Build.HARDWARE
        val board = Build.BOARD
        if (!hw.isNullOrBlank() && hw != "unknown") {
            return if (!board.isNullOrBlank() && board != hw) "$hw ($board)" else hw
        }
        if (!board.isNullOrBlank()) return board
        return "ARM Processor"
    }

    private fun openSystemSettings(intent: Intent, fallbackMsg: String) {
        try {
            startActivity(intent)
        } catch (_: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (_: Exception) {}
        }
    }

    private fun startClockLoop() {
        lifecycleScope.launch {
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            while (isActive) {
                binding.tvClock.text = sdf.format(Date())
                delay(10000)
            }
        }
    }
}
