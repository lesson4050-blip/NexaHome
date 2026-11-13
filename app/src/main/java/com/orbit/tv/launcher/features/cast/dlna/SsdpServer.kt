package com.orbit.tv.launcher.features.cast.dlna

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.util.UUID

/**
 * Lightweight SSDP (Simple Service Discovery Protocol) multicast responder.
 * Advertises the TV as a DLNA MediaRenderer to all phones and PCs on the local network.
 */
class SsdpServer(
    private val context: Context,
    private val httpPort: Int = 8080,
    private val uuid: String = generateDeviceUuid(context)
) {
    private var socket: MulticastSocket? = null
    private var serverJob: Job? = null
    private var notifyJob: Job? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    fun start(scope: CoroutineScope) {
        stop()
        serverJob = scope.launch(Dispatchers.IO) {
            try {
                val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                multicastLock = wm?.createMulticastLock("smart_tv_ssdp_lock")?.apply {
                    setReferenceCounted(true)
                    acquire()
                }

                val group = InetAddress.getByName(SSDP_MULTICAST_ADDR)
                val mSocket = MulticastSocket(null).apply {
                    reuseAddress = true
                    bind(InetSocketAddress(SSDP_PORT))
                    timeToLive = 4
                    try {
                        joinGroup(group)
                    } catch (_: Exception) {}
                }
                socket = mSocket

                // Launch periodic alive announcement
                notifyJob = scope.launch(Dispatchers.IO) {
                    while (isActive) {
                        sendAliveBroadcast()
                        delay(60000)
                    }
                }

                val buffer = ByteArray(4096)
                while (isActive) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        mSocket.receive(packet)
                    } catch (e: Exception) {
                        break
                    }

                    val message = String(packet.data, 0, packet.length, Charsets.UTF_8)
                    if (message.startsWith("M-SEARCH", ignoreCase = true)) {
                        handleMSearch(message, packet, mSocket)
                    }
                }
            } catch (e: Exception) {
                // Silently handle socket binding issues on emulators/isolated networks
            }
        }
    }

    fun stop() {
        try {
            sendByeByeBroadcast()
        } catch (_: Exception) {}

        try {
            socket?.close()
        } catch (_: Exception) {}
        socket = null

        serverJob?.cancel()
        serverJob = null

        notifyJob?.cancel()
        notifyJob = null

        try {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
            }
        } catch (_: Exception) {}
        multicastLock = null
    }

    private fun handleMSearch(message: String, packet: DatagramPacket, socket: MulticastSocket) {
        val lines = message.split("\r\n", "\n")
        var searchTarget = ""
        for (line in lines) {
            if (line.startsWith("ST:", ignoreCase = true)) {
                searchTarget = line.substringAfter(":").trim()
                break
            }
        }

        val localIp = getLocalIpAddress() ?: return
        val matchingTargets = getMatchingTargets(searchTarget)

        for (st in matchingTargets) {
            val response = buildSsdpResponse(localIp, st)
            val bytes = response.toByteArray(Charsets.UTF_8)
            try {
                val replyPacket = DatagramPacket(bytes, bytes.size, packet.socketAddress)
                socket.send(replyPacket)
            } catch (_: Exception) {}
        }
    }

    private fun sendAliveBroadcast() {
        val mSocket = socket ?: return
        val localIp = getLocalIpAddress() ?: return
        val group = InetAddress.getByName(SSDP_MULTICAST_ADDR)

        val targets = listOf(
            "upnp:rootdevice",
            TARGET_MEDIA_RENDERER,
            TARGET_AV_TRANSPORT,
            TARGET_RENDERING_CONTROL
        )

        for (st in targets) {
            val notifyMsg = buildSsdpNotify(localIp, st, "ssdp:alive")
            val bytes = notifyMsg.toByteArray(Charsets.UTF_8)
            try {
                val packet = DatagramPacket(bytes, bytes.size, group, SSDP_PORT)
                mSocket.send(packet)
            } catch (_: Exception) {}
        }
    }

    private fun sendByeByeBroadcast() {
        val mSocket = socket ?: return
        val localIp = getLocalIpAddress() ?: return
        val group = InetAddress.getByName(SSDP_MULTICAST_ADDR)

        val targets = listOf(
            "upnp:rootdevice",
            TARGET_MEDIA_RENDERER,
            TARGET_AV_TRANSPORT,
            TARGET_RENDERING_CONTROL
        )

        for (st in targets) {
            val byeMsg = buildSsdpNotify(localIp, st, "ssdp:byebye")
            val bytes = byeMsg.toByteArray(Charsets.UTF_8)
            try {
                val packet = DatagramPacket(bytes, bytes.size, group, SSDP_PORT)
                mSocket.send(packet)
            } catch (_: Exception) {}
        }
    }

    private fun getMatchingTargets(st: String): List<String> {
        val all = listOf(
            "upnp:rootdevice",
            "uuid:$uuid",
            TARGET_MEDIA_RENDERER,
            TARGET_AV_TRANSPORT,
            TARGET_RENDERING_CONTROL
        )
        return when {
            st.equals("ssdp:all", ignoreCase = true) -> all
            st.equals("upnp:rootdevice", ignoreCase = true) -> listOf("upnp:rootdevice")
            st.equals(TARGET_MEDIA_RENDERER, ignoreCase = true) -> listOf(TARGET_MEDIA_RENDERER)
            st.equals(TARGET_AV_TRANSPORT, ignoreCase = true) -> listOf(TARGET_AV_TRANSPORT)
            st.equals(TARGET_RENDERING_CONTROL, ignoreCase = true) -> listOf(TARGET_RENDERING_CONTROL)
            st.equals("uuid:$uuid", ignoreCase = true) -> listOf("uuid:$uuid")
            else -> emptyList()
        }
    }

    private fun buildSsdpResponse(ip: String, st: String): String {
        return "HTTP/1.1 200 OK\r\n" +
                "CACHE-CONTROL: max-age=1800\r\n" +
                "EXT:\r\n" +
                "LOCATION: http://$ip:$httpPort/upnp/description.xml\r\n" +
                "SERVER: Android/${Build.VERSION.RELEASE} UPnP/1.0 DLNADOC/1.50\r\n" +
                "ST: $st\r\n" +
                "USN: uuid:$uuid::$st\r\n\r\n"
    }

    private fun buildSsdpNotify(ip: String, st: String, nts: String): String {
        return "NOTIFY * HTTP/1.1\r\n" +
                "HOST: $SSDP_MULTICAST_ADDR:$SSDP_PORT\r\n" +
                "CACHE-CONTROL: max-age=1800\r\n" +
                "LOCATION: http://$ip:$httpPort/upnp/description.xml\r\n" +
                "SERVER: Android/${Build.VERSION.RELEASE} UPnP/1.0 DLNADOC/1.50\r\n" +
                "NT: $st\r\n" +
                "NTS: $nts\r\n" +
                "USN: uuid:$uuid::$st\r\n\r\n"
    }

    private fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            for (intf in interfaces) {
                val addrs = intf.inetAddresses ?: continue
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        val host = addr.hostAddress ?: continue
                        if (host.startsWith("192.168.") || host.startsWith("10.") || host.startsWith("172.")) {
                            return host
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    companion object {
        const val SSDP_MULTICAST_ADDR = "239.255.255.250"
        const val SSDP_PORT = 1900
        const val TARGET_MEDIA_RENDERER = "urn:schemas-upnp-org:device:MediaRenderer:1"
        const val TARGET_AV_TRANSPORT = "urn:schemas-upnp-org:service:AVTransport:1"
        const val TARGET_RENDERING_CONTROL = "urn:schemas-upnp-org:service:RenderingControl:1"

        fun generateDeviceUuid(context: Context): String {
            val androidId = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            ) ?: "smart_tv_default"
            return UUID.nameUUIDFromBytes(androidId.toByteArray(Charsets.UTF_8)).toString()
        }
    }
}
