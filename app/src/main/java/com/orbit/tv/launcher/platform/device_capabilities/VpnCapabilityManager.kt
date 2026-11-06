package com.orbit.tv.launcher.platform.device_capabilities

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Capability-aware manager for detecting system VPN status
 * and safely launching VPN/Network settings across different OEM devices.
 */
class VpnCapabilityManager(private val context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val _isVpnActive = MutableStateFlow(false)
    val isVpnActive: StateFlow<Boolean> = _isVpnActive.asStateFlow()

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    init {
        checkCurrentVpnState()
        registerNetworkCallback()
    }

    private fun checkCurrentVpnState() {
        val cm = connectivityManager ?: return
        val activeNetwork = cm.activeNetwork ?: return
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return
        _isVpnActive.value = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    }

    private fun registerNetworkCallback() {
        val cm = connectivityManager ?: return
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                _isVpnActive.value = true
            }

            override fun onLost(network: Network) {
                checkCurrentVpnState()
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                _isVpnActive.value = networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            }
        }

        try {
            cm.registerNetworkCallback(request, networkCallback!!)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun unregister() {
        try {
            networkCallback?.let { connectivityManager?.unregisterNetworkCallback(it) }
        } catch (e: Exception) {
            // Ignored
        }
    }

    /**
     * Attempts to open the official system VPN settings screen.
     * Falls back to wireless/network settings if the OEM has stripped android.net.vpn.SETTINGS.
     */
    fun openVpnSettings(): Boolean {
        return try {
            val vpnIntent = Intent("android.net.vpn.SETTINGS").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(vpnIntent)
            true
        } catch (e: Exception) {
            try {
                val fallbackIntent = Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
                true
            } catch (e2: Exception) {
                false
            }
        }
    }
}
