package com.remotemedia.network

import com.remotemedia.core.ConnectionResolver
import com.remotemedia.core.LogTag
import com.remotemedia.core.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

/**
 * M7.1 — Hub federation discovery.
 * Discovers and tracks peer PocketNode hosts on the local network via UDP announcements and LAN pinging.
 */
data class PeerNode(
    val serverId: String,
    val host: String,
    val port: Int = 8080,
    val nodeName: String = "PocketNode Peer",
    val lastSeenMs: Long = System.currentTimeMillis()
) {
    val baseUrl: String get() = "http://$host:$port"
}

object HubFederationManager {

    private const val DISCOVERY_PORT = 8998
    private const val PEER_TIMEOUT_MS = 60_000L // 60s offline threshold

    private val activePeers = ConcurrentHashMap<String, PeerNode>()

    fun registerPeer(peer: PeerNode) {
        activePeers[peer.serverId] = peer
        ConnectionResolver.registerPeer(peer.serverId, peer.baseUrl)
        Logger.i(LogTag.NETWORK, "Federated peer registered: ${peer.nodeName} (${peer.baseUrl})")
    }

    fun listActivePeers(): List<PeerNode> {
        val cutoff = System.currentTimeMillis() - PEER_TIMEOUT_MS
        // Remove stale peers
        activePeers.entries.removeIf { it.value.lastSeenMs < cutoff }
        return activePeers.values.toList()
    }

    /**
     * Broadcasts UDP announcement packet to LAN broadcast address (255.255.255.255).
     */
    suspend fun broadcastAnnouncement(
        serverId: String,
        nodeName: String,
        serverPort: Int = 8080
    ) = withContext(Dispatchers.IO) {
        try {
            val payload = "POCKETNODE_ANNOUNCE:$serverId:$nodeName:$serverPort"
            val data = payload.toByteArray(Charsets.UTF_8)
            DatagramSocket().use { socket ->
                socket.broadcast = true
                val packet = DatagramPacket(
                    data,
                    data.size,
                    InetAddress.getByName("255.255.255.255"),
                    DISCOVERY_PORT
                )
                socket.send(packet)
            }
        } catch (e: Exception) {
            Logger.w(LogTag.NETWORK, "Discovery broadcast error: ${e.message}")
        }
    }

    /**
     * Parses an incoming UDP announcement string and registers the peer.
     */
    fun parseAndRegisterAnnouncement(raw: String, senderIp: String): PeerNode? {
        if (!raw.startsWith("POCKETNODE_ANNOUNCE:")) return null
        val parts = raw.split(":")
        if (parts.size < 4) return null

        val serverId = parts[1]
        val nodeName = parts[2]
        val port = parts[3].toIntOrNull() ?: 8080

        val peer = PeerNode(
            serverId = serverId,
            host = senderIp,
            port = port,
            nodeName = nodeName,
            lastSeenMs = System.currentTimeMillis()
        )
        registerPeer(peer)
        return peer
    }

    fun clearForTest() {
        activePeers.clear()
    }
}
