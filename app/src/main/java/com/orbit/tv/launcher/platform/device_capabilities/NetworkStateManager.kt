package com.orbit.tv.launcher.platform.device_capabilities

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class NetworkStatus {
    WIFI_CONNECTED,
    ETHERNET_CONNECTED,
    DISCONNECTED
}

enum class WifiUiState {
    DISABLED,     // Radio switch is OFF in system settings -> ic_wifi_off (crossed out)
    CONNECTED,    // Radio switch is ON and connected to Wi-Fi AP -> ic_wifi (1.0f)
    DISCONNECTED  // Radio switch is ON, but not connected / saved -> ic_wifi (0.55f dimmed, NOT crossed out)
}

/**
 * Monitors real-time network connectivity: Wi-Fi, Ethernet, or Disconnected/Disabled.
 */
class NetworkStateManager(private val context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob())

    private val _networkStatus = MutableStateFlow(NetworkStatus.DISCONNECTED)
    val networkStatus: StateFlow<NetworkStatus> = _networkStatus.asStateFlow()

    private val _isWifiActive = MutableStateFlow(false)
    val isWifiActive: StateFlow<Boolean> = _isWifiActive.asStateFlow()

    private val _wifiUiState = MutableStateFlow(WifiUiState.DISCONNECTED)
    val wifiUiState: StateFlow<WifiUiState> = _wifiUiState.asStateFlow()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            updateStatus()
        }

        override fun onLost(network: Network) {
            updateStatus()
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            updateStatus()
        }
    }

    private val wifiStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateStatus()
        }
    }

    init {
        scope.launch {
            try {
                connectivityManager.registerDefaultNetworkCallback(networkCallback)
            } catch (e: Exception) {
                try {
                    val request = NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .build()
                    connectivityManager.registerNetworkCallback(request, networkCallback)
                } catch (e2: Exception) {}
            }

            val filter = IntentFilter().apply {
                addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
                addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
                @Suppress("DEPRECATION")
                addAction(ConnectivityManager.CONNECTIVITY_ACTION)
            }
            try {
                context.registerReceiver(wifiStateReceiver, filter)
            } catch (e: Exception) {}

            _wifiUiState.value = determineWifiUiState()
            _isWifiActive.value = isWifiEnabled()
            _networkStatus.value = determineCurrentStatus()
        }
    }

    fun updateStatus() {
        scope.launch {
            _wifiUiState.value = determineWifiUiState()
            _isWifiActive.value = isWifiEnabled()
            _networkStatus.value = determineCurrentStatus()
        }
    }

    fun isWifiEnabled(): Boolean {
        return try {
            wifiManager?.isWifiEnabled ?: false
        } catch (_: Exception) {
            false
        }
    }

    fun determineWifiUiState(): WifiUiState {
        val enabled = isWifiEnabled()
        if (!enabled) return WifiUiState.DISABLED
        return if (isWifiConnected()) WifiUiState.CONNECTED else WifiUiState.DISCONNECTED
    }

    fun isWifiConnected(): Boolean {
        try {
            val isEnabled = wifiManager?.isWifiEnabled ?: false
            if (!isEnabled) return false

            val activeNetwork = connectivityManager.activeNetwork
            if (activeNetwork != null) {
                val caps = connectivityManager.getNetworkCapabilities(activeNetwork)
                if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                    return true
                }
            }

            for (net in connectivityManager.allNetworks) {
                val caps = connectivityManager.getNetworkCapabilities(net) ?: continue
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    return true
                }
            }

            val wifiInfo = wifiManager?.connectionInfo
            if (wifiInfo != null && wifiInfo.networkId != -1 && wifiInfo.bssid != null && wifiInfo.bssid != "00:00:00:00:00:00") {
                return true
            }
        } catch (_: Exception) {}
        return false
    }

    private fun determineCurrentStatus(): NetworkStatus {
        try {
            if (isWifiConnected()) {
                return NetworkStatus.WIFI_CONNECTED
            }

            val activeNetwork = connectivityManager.activeNetwork
            if (activeNetwork != null) {
                val caps = connectivityManager.getNetworkCapabilities(activeNetwork)
                if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                    return NetworkStatus.ETHERNET_CONNECTED
                }
            }

            for (net in connectivityManager.allNetworks) {
                val caps = connectivityManager.getNetworkCapabilities(net) ?: continue
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                    return NetworkStatus.ETHERNET_CONNECTED
                }
            }

            return NetworkStatus.DISCONNECTED
        } catch (e: Exception) {
            return NetworkStatus.DISCONNECTED
        }
    }
}
