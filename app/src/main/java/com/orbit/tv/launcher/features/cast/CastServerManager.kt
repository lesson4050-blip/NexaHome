package com.orbit.tv.launcher.features.cast

import android.content.Context
import com.orbit.tv.launcher.features.cast.dlna.SsdpServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.security.SecureRandom

/**
 * Singleton manager for CastWebServer and DLNA SSDP server.
 * Decouples the HTTP/Cast server lifecycle from CastActivity,
 * ensuring seamless casting and remote control across the entire launcher lifecycle.
 */
object CastServerManager {

    private var server: CastWebServer? = null
    private var ssdpServer: SsdpServer? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var currentPin: String = generatePin()

    @Synchronized
    fun start(context: Context) {
        val appContext = context.applicationContext
        if (server == null) {
            server = CastWebServer(appContext, 8080) { currentPin }
            server?.start(scope)
        }
        if (ssdpServer == null) {
            ssdpServer = SsdpServer(appContext, 8080)
            ssdpServer?.start(scope)
        }
    }

    @Synchronized
    fun stop() {
        ssdpServer?.stop()
        ssdpServer = null
        server?.stop()
        server = null
    }

    fun isRunning(): Boolean = server != null

    fun getPin(): String = currentPin

    @Synchronized
    fun refreshPin(): String {
        currentPin = generatePin()
        return currentPin
    }

    private fun generatePin(): String {
        val random = SecureRandom()
        val part1 = random.nextInt(900) + 100
        val part2 = random.nextInt(900) + 100
        return "$part1-$part2"
    }
}
