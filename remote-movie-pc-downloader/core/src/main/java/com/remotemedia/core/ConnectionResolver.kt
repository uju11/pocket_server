package com.remotemedia.core

import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

/**
 * M3.3 — URL resolver for peers.
 * Given a peer server_id, selects the best reachable URL:
 * checks LAN endpoint first; falls back to relay if LAN is unreachable.
 * Caches resolution results for [CACHE_TTL_MS] (30 seconds).
 */
object ConnectionResolver {

    const val CACHE_TTL_MS = 30_000L

    data class PeerRecord(
        val serverId: String,
        val lanUrl: String?,
        val relayUrl: String? = null
    )

    data class CachedResolution(
        val url: String,
        val timestampMs: Long
    )

    private val peerRegistry = ConcurrentHashMap<String, PeerRecord>()
    private val resolutionCache = ConcurrentHashMap<String, CachedResolution>()

    /** Register or update candidate endpoints for a peer. */
    fun registerPeer(serverId: String, lanUrl: String?, relayUrl: String? = null) {
        peerRegistry[serverId] = PeerRecord(serverId, lanUrl, relayUrl)
        resolutionCache.remove(serverId) // invalidate cache on update
    }

    /** Unregister a peer and remove its cached resolution. */
    fun unregisterPeer(serverId: String) {
        peerRegistry.remove(serverId)
        resolutionCache.remove(serverId)
    }

    /** Clear cached resolutions (for testing or network change). */
    fun clearCache() {
        resolutionCache.clear()
    }

    /**
     * Resolves the best URL for [serverId].
     * 1. Returns cached URL if fresh (< 30s).
     * 2. Checks LAN endpoint reachability (quick TCP connect).
     * 3. Falls back to relay URL if LAN is unreachable.
     * 4. Updates cache on successful resolution.
     */
    fun resolve(serverId: String, localIp: String? = null, timeoutMs: Int = 1000): String? {
        val now = System.currentTimeMillis()
        val cached = resolutionCache[serverId]
        if (cached != null && (now - cached.timestampMs) < CACHE_TTL_MS) {
            return cached.url
        }

        val record = peerRegistry[serverId] ?: return null

        // 1. Try LAN candidate first
        if (!record.lanUrl.isNullOrBlank()) {
            val hostPort = parseHostPort(record.lanUrl)
            if (hostPort != null) {
                val (host, port) = hostPort
                // If localIp is provided and matches subnet, or if reachable via TCP probe
                val sameSubnet = localIp != null && isSameSubnet(localIp, host)
                if (sameSubnet || isReachable(host, port, timeoutMs)) {
                    resolutionCache[serverId] = CachedResolution(record.lanUrl, now)
                    return record.lanUrl
                }
            }
        }

        // 2. Fall back to relay
        if (!record.relayUrl.isNullOrBlank()) {
            resolutionCache[serverId] = CachedResolution(record.relayUrl, now)
            return record.relayUrl
        }

        return null
    }

    /** Parse host and port from an HTTP/HTTPS/WS URL string. */
    fun parseHostPort(url: String): Pair<String, Int>? {
        return try {
            val uri = URI(url)
            val host = uri.host ?: return null
            val port = if (uri.port != -1) uri.port else if (uri.scheme?.startsWith("https") == true || uri.scheme == "wss") 443 else 80
            Pair(host, port)
        } catch (_: Exception) {
            null
        }
    }

    /** Checks whether host:port accepts a TCP connection within [timeoutMs]. */
    fun isReachable(host: String, port: Int, timeoutMs: Int = 1000): Boolean {
        return try {
            Socket().use { sock ->
                sock.connect(InetSocketAddress(host, port), timeoutMs)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    /** Simple /24 subnet comparison between two IPv4 strings. */
    fun isSameSubnet(ip1: String, ip2: String): Boolean {
        val p1 = ip1.split(".")
        val p2 = ip2.split(".")
        return p1.size == 4 && p2.size == 4 && p1[0] == p2[0] && p1[1] == p2[1] && p1[2] == p2[2]
    }
}
