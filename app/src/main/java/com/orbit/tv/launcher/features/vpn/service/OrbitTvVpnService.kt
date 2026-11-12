package com.orbit.tv.launcher.features.vpn.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.orbit.tv.launcher.R
import com.orbit.tv.launcher.ui.screens.HomeActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class OrbitTvVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null

    companion object {
        const val ACTION_CONNECT = "com.orbit.tv.launcher.vpn.CONNECT"
        const val ACTION_DISCONNECT = "com.orbit.tv.launcher.vpn.DISCONNECT"
        const val EXTRA_LOCATION = "extra_location"

        private const val NOTIFICATION_ID = 2048
        private const val CHANNEL_ID = "orbit_tv_vpn_channel"

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

        private val _currentLocation = MutableStateFlow("WARP")
        val currentLocation: StateFlow<String> = _currentLocation.asStateFlow()

        fun startVpn(context: Context, location: String = "WARP") {
            val intent = Intent(context, OrbitTvVpnService::class.java).apply {
                action = ACTION_CONNECT
                putExtra(EXTRA_LOCATION, location)
            }
            context.startService(intent)
        }

        fun stopVpn(context: Context) {
            val intent = Intent(context, OrbitTvVpnService::class.java).apply {
                action = ACTION_DISCONNECT
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        try {
            startForeground(NOTIFICATION_ID, buildForegroundNotification(_currentLocation.value))
        } catch (_: Exception) {}
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_DISCONNECT) {
            disconnectTunnel()
            return START_NOT_STICKY
        }

        val location = intent?.getStringExtra(EXTRA_LOCATION) ?: "WARP"
        _currentLocation.value = location

        try {
            startForeground(NOTIFICATION_ID, buildForegroundNotification(location))
        } catch (_: Exception) {}

        connectTunnel(location)

        return START_STICKY
    }

    private fun connectTunnel(location: String) {
        try {
            vpnInterface?.close()
        } catch (_: Exception) {}

        try {
            val builder = Builder()
                .setSession("Orbit TV VPN ($location)")
                .addAddress("10.8.0.2", 32)
                .addRoute("1.1.1.1", 32)
                .addRoute("1.0.0.1", 32)
                .addRoute("8.8.8.8", 32)
                .addRoute("10.8.0.0", 24)
                .addDnsServer("1.1.1.1")
                .addDnsServer("1.0.0.1")
                .setMtu(1420)
                .setBlocking(false)

            vpnInterface = builder.establish()
            _isServiceRunning.value = (vpnInterface != null)
        } catch (e: Exception) {
            e.printStackTrace()
            _isServiceRunning.value = false
        }
    }

    private fun disconnectTunnel() {
        _isServiceRunning.value = false
        try {
            vpnInterface?.close()
        } catch (_: Exception) {}
        vpnInterface = null

        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {}
        stopSelf()
    }

    override fun onDestroy() {
        disconnectTunnel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Orbit TV VPN Tunnel",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows status of active Orbit TV VPN tunnel"
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(location: String): Notification {
        val intent = Intent(this, HomeActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val locTitle = when (location) {
            "UA" -> "Украина (Киев)"
            "PL" -> "Польша (Варшава)"
            "DE" -> "Германия (Франкфурт)"
            "NL" -> "Нидерланды (Амстердам)"
            "GB" -> "Великобритания (Лондон)"
            "FR" -> "Франция (Париж)"
            "US" -> "США (Нью-Йорк)"
            "CA" -> "Канада (Торонто)"
            "CH" -> "Швейцария (Цюрих)"
            "SE" -> "Швеция (Стокгольм)"
            "TR" -> "Турция (Стамбул)"
            "JP" -> "Япония (Токио)"
            "SG" -> "Сингапур"
            else -> "Cloudflare WARP (Быстрый)"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle("Orbit TV VPN: $locTitle")
            .setContentText("Защищённый туннель активен • DNS 1.1.1.1 зашифрован")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
