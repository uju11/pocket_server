package com.remotemedia.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.remotemedia.core.LogTag
import com.remotemedia.core.Logger
import com.remotemedia.core.SystemMetricsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.UUID

/**
 * JellyfinDiscoveryResponder
 *
 * Listens on UDP port 7359 for Jellyfin client discovery broadcasts.
 * When a Jellyfin client (TV, Android, iOS, browser extension) sends
 * "who is JellyfinServer?" as a UDP broadcast, this responder replies
 * with a JSON payload containing the server's address and name.
 *
 * Protocol:
 *   Client → 255.255.255.255:7359  "who is JellyfinServer?"
 *   Server → client_ip:7359        {"Address":"http://ip:port","Id":"...","Name":"PocketNode"}
 *
 * This makes every Jellyfin client auto-detect PocketNode on the same Wi-Fi
 * without typing any IP address.
 */
object JellyfinDiscoveryResponder {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var listenerJob: Job? = null
    private var socket: DatagramSocket? = null

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val serverId get() = JellyfinServer.dummyServerId

    fun start(serverIp: String, jellyfinPort: Int) {
        if (listenerJob?.isActive == true) return

        val realIp = com.remotemedia.ServerForegroundService.getLocalIpAddress()
        val activeIp = if (serverIp.matches(Regex("""\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}""")) && serverIp != "127.0.0.1") {
            serverIp
        } else {
            realIp ?: "127.0.0.1"
        }

        listenerJob = scope.launch {
            try {
                socket = DatagramSocket(null).apply {
                    reuseAddress = true
                    bind(java.net.InetSocketAddress(7359))
                    broadcast = true
                }
                _isListening.value = true
                Logger.i(LogTag.JELLYFIN, "Discovery responder listening on UDP :7359 (advertising http://$activeIp:$jellyfinPort)")

                // Active periodic announcement beacon to 255.255.255.255:7359 (allows Smart TVs to auto-detect instantly)
                launch {
                    val broadcastAddr = InetAddress.getByName("255.255.255.255")
                    while (_isListening.value) {
                        try {
                            val beaconBytes = buildDiscoveryResponse(activeIp, jellyfinPort).toByteArray()
                            val beaconPacket = DatagramPacket(beaconBytes, beaconBytes.size, broadcastAddr, 7359)
                            socket?.send(beaconPacket)
                        } catch (e: Exception) {
                            // Non-fatal beacon broadcast error
                        }
                        delay(12000)
                    }
                }

                val buf = ByteArray(1024)

                while (_isListening.value) {
                    try {
                        val packet = DatagramPacket(buf, buf.size)
                        socket?.receive(packet) ?: break   // blocks until datagram arrives

                        val msg = String(packet.data, 0, packet.length).trim()

                        // Respond to standard Jellyfin client queries or pings
                        if (msg.contains("jellyfin", ignoreCase = true) ||
                            msg.contains("who is", ignoreCase = true) ||
                            msg.contains("server", ignoreCase = true) ||
                            msg.isEmpty()) {

                            val response = buildDiscoveryResponse(activeIp, jellyfinPort)
                            val responseBytes = response.toByteArray()
                            val reply = DatagramPacket(
                                responseBytes,
                                responseBytes.size,
                                packet.address,
                                packet.port
                            )
                            socket?.send(reply)
                            Logger.i(LogTag.JELLYFIN, "Discovery query from ${packet.address.hostAddress}:${packet.port} → responded with http://$activeIp:$jellyfinPort")
                        }
                    } catch (e: Exception) {
                        if (_isListening.value) {
                            Logger.w(LogTag.JELLYFIN, "Discovery read error: ${e.message}")
                            delay(500)
                        }
                    }
                }
            } catch (e: Exception) {
                Logger.e(LogTag.JELLYFIN, "Discovery responder failed to bind :7359 — ${e.message}")
            } finally {
                socket?.close()
                socket = null
                _isListening.value = false
            }
        }
    }

    fun stop() {
        _isListening.value = false
        runCatching { socket?.close() }
        listenerJob?.cancel()
        listenerJob = null
        Logger.i(LogTag.JELLYFIN, "Discovery responder stopped.")
    }

    fun updateAddress(serverIp: String, jellyfinPort: Int) {
        // Restart with new address if IP changed (e.g. DHCP renewal)
        if (listenerJob?.isActive == true) {
            stop()
            start(serverIp, jellyfinPort)
        }
    }

    private fun buildDiscoveryResponse(ip: String, port: Int): String {
        // Authentic Jellyfin discovery JSON payload expected by Smart TV & Android TV clients
        return """{"Address":"http://$ip:$port","Id":"$serverId","Name":"PocketNode","EndpointAddress":"http://$ip:$port"}"""
    }
}
