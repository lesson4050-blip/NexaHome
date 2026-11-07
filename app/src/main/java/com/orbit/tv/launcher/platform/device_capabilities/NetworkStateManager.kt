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

enum class NetworkStatus {
    WIFI_CONNECTED,
    ETHERNET_CONNECTED,
    DISCONNECTED
}

/**
 * Monitors real-time network connectivity: Wi-Fi, Ethernet, or Disconnected/Disabled.
 */
class NetworkStateManager(private val context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    private val _networkStatus = MutableStateFlow(determineCurrentStatus())
    val networkStatus: StateFlow<NetworkStatus> = _networkStatus.asStateFlow()

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
        
        updateStatus()
    }

    fun updateStatus() {
        _networkStatus.value = determineCurrentStatus()
    }

    private fun determineCurrentStatus(): NetworkStatus {
        try {
            val activeNetwork = connectivityManager.activeNetwork
            if (activeNetwork != null) {
                val caps = connectivityManager.getNetworkCapabilities(activeNetwork)
                if (caps != null) {
                    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                        return NetworkStatus.WIFI_CONNECTED
                    }
                    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                        return NetworkStatus.ETHERNET_CONNECTED
                    }
                }
            }

            for (net in connectivityManager.allNetworks) {
                val caps = connectivityManager.getNetworkCapabilities(net) ?: continue
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    return NetworkStatus.WIFI_CONNECTED
                }
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                    return NetworkStatus.ETHERNET_CONNECTED
                }
            }

            try {
                val wifiInfo = wifiManager?.connectionInfo
                if (wifiInfo != null && wifiInfo.networkId != -1) {
                    return NetworkStatus.WIFI_CONNECTED
                }
            } catch (e: Exception) {}

            return NetworkStatus.DISCONNECTED
        } catch (e: Exception) {
            return NetworkStatus.DISCONNECTED
        }
    }
}
