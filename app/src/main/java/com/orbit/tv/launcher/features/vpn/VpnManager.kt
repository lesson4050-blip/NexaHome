package com.orbit.tv.launcher.features.vpn

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.orbit.tv.launcher.features.vpn.model.VpnAppInfo
import com.orbit.tv.launcher.platform.device_capabilities.VpnCapabilityManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URL

class VpnManager(private val context: Context) {

    val capabilityManager = VpnCapabilityManager(context)
    private val _isVpnActive = MutableStateFlow(false)
    val isVpnActive: StateFlow<Boolean> = _isVpnActive.asStateFlow()

    private val _vpnCountryCode = MutableStateFlow("OFF")
    val vpnCountryCode: StateFlow<String> = _vpnCountryCode.asStateFlow()

    private val _vpnDetails = MutableStateFlow("Direct ISP connection")
    val vpnDetails: StateFlow<String> = _vpnDetails.asStateFlow()

    private val managerScope = CoroutineScope(Dispatchers.IO + Job())

    // Target OEM & Popular Android TV VPN clients
    val supportedVpnPackages = listOf(
        "com.urbanvpn.android" to "Urban VPN",
        "com.cloudflare.onedotonedotonedotone" to "Cloudflare 1.1.1.1 (WARP)",
        "com.wireguard.android" to "WireGuard",
        "org.amnezia.vpn" to "Amnezia VPN",
        "de.blinkt.openvpn" to "OpenVPN for Android",
        "ch.protonvpn.android" to "Proton VPN",
        "com.adguard.vpn" to "AdGuard VPN",
        "com.v2ray.ang" to "v2rayNG",
        "com.github.shadowsocks" to "Shadowsocks",
        "com.surfshark.vpnclient.android" to "Surfshark TV",
        "com.expressvpn.vpn" to "ExpressVPN"
    )

    init {
        managerScope.launch {
            kotlinx.coroutines.flow.combine(
                capabilityManager.isVpnActive,
                com.orbit.tv.launcher.features.vpn.service.OrbitTvVpnService.isServiceRunning,
                com.orbit.tv.launcher.features.vpn.service.OrbitTvVpnService.currentLocation
            ) { active, running, location ->
                Triple(active || running, running, location)
            }.collect { (active, isInternal, location) ->
                _isVpnActive.value = active
                if (active) {
                    if (isInternal) {
                        _vpnCountryCode.value = location
                        val locName = when (location) {
                            "US" -> "США"
                            "DE" -> "Германия"
                            "PL" -> "Польша"
                            "UA" -> "Украина"
                            "JP" -> "Япония"
                            "GB" -> "Великобритания"
                            "NL" -> "Нидерланды"
                            "WARP" -> "Cloudflare WARP"
                            else -> location
                        }
                        _vpnDetails.value = "Туннель: $locName • DNS 1.1.1.1 Защищен"
                    } else {
                        resolveVpnLocation()
                    }
                } else {
                    _vpnCountryCode.value = "OFF"
                    _vpnDetails.value = "Traffic unencrypted • Direct ISP"
                }
            }
        }
    }

    private suspend fun resolveVpnLocation() = withContext(Dispatchers.IO) {
        val tunnelInfo = getActiveTunnelDetails()
        val defaultLabel = tunnelInfo?.first?.uppercase() ?: "VPN"

        try {
            val url = URL("https://ipwho.is/")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 2500
                readTimeout = 2500
                requestMethod = "GET"
                setRequestProperty("User-Agent", "SmartTvLauncher/1.0")
            }

            if (conn.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val response = reader.readText()
                reader.close()
                conn.disconnect()

                val json = JSONObject(response)
                val code = json.optString("country_code", defaultLabel)
                val country = json.optString("country", "Unknown")
                val city = json.optString("city", "")
                val ip = json.optString("ip", "")

                _vpnCountryCode.value = if (code.length in 2..4) code.uppercase() else defaultLabel
                _vpnDetails.value = if (city.isNotEmpty()) "$country ($city) • IP: $ip" else "$country • IP: $ip"
                return@withContext
            }
        } catch (e: Exception) {
            // Fallback to tunnel name or VPN
        }

        _vpnCountryCode.value = defaultLabel
        _vpnDetails.value = if (tunnelInfo != null) "Tunnel: ${tunnelInfo.first} • IP: ${tunnelInfo.second}" else "VPN Tunnel Active"
    }

    suspend fun getInstalledVpnApps(): List<VpnAppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val installedList = mutableListOf<VpnAppInfo>()

        for ((pkg, name) in supportedVpnPackages) {
            try {
                val appInfo = pm.getApplicationInfo(pkg, 0)
                val icon = pm.getApplicationIcon(appInfo)
                val packageInfo = pm.getPackageInfo(pkg, 0)
                installedList.add(
                    VpnAppInfo(
                        name = name,
                        packageName = pkg,
                        icon = icon,
                        isInstalled = true,
                        version = packageInfo.versionName
                    )
                )
            } catch (e: PackageManager.NameNotFoundException) {
                // Not installed
            }
        }
        installedList
    }

    fun isAppInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun launchOrInstallVpnApp(packageName: String) {
        val pm = context.packageManager
        val launchIntent = pm.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
        } else {
            try {
                val marketIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=$packageName")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(marketIntent)
            } catch (_: Exception) {
                val webIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://play.google.com/store/apps/details?id=$packageName")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webIntent)
            }
        }
    }

    fun launchVpnApp(packageName: String): Boolean {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun openSystemVpnSettings(): Boolean {
        return capabilityManager.openVpnSettings()
    }

    /**
     * Resolves the active VPN tunnel network interface (e.g., tun0, ppp0, wg0)
     * and returns local tunnel IP and interface name.
     */
    fun getActiveTunnelDetails(): Pair<String, String>? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            while (interfaces.hasMoreElements()) {
                val netIf = interfaces.nextElement()
                val name = netIf.name.lowercase()
                if (netIf.isUp && (name.startsWith("tun") || name.startsWith("ppp") || name.startsWith("wg"))) {
                    val addresses = netIf.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val addr = addresses.nextElement()
                        if (!addr.isLoopbackAddress && addr is Inet4Address) {
                            return Pair(netIf.name, addr.hostAddress ?: "Unknown IP")
                        }
                    }
                    return Pair(netIf.name, "Active Tunnel")
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    fun toggleWireguardTunnel(tunnelName: String, enable: Boolean) {
        try {
            val action = if (enable) {
                "com.wireguard.android.action.SET_TUNNEL_UP"
            } else {
                "com.wireguard.android.action.SET_TUNNEL_DOWN"
            }
            val intent = Intent(action).apply {
                setPackage("com.wireguard.android")
                putExtra("tunnel", tunnelName)
            }
            context.sendBroadcast(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun onDestroy() {
        capabilityManager.unregister()
    }
}
