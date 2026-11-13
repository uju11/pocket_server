package com.remotemedia.network

import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket

/**
 * M3.1 — LAN IP detection and reachability helpers.
 * Pure functions; no Android service or context dependencies.
 */
object NetworkManager {

    /**
     * Returns the first non-loopback, non-link-local IPv4 address found on
     * active network interfaces (prefers wlan0 / eth0 naming conventions).
     */
    fun getLocalIp(): String? {
        return try {
            val ifaces = NetworkInterface.getNetworkInterfaces()?.toList() ?: return null
            // Prefer wireless / ethernet names
            val preferred = ifaces.filter { iface ->
                iface.isUp && !iface.isLoopback &&
                        (iface.name.startsWith("wlan") || iface.name.startsWith("eth") ||
                                iface.name.startsWith("rmnet") || iface.name.startsWith("en"))
            }
            val candidates = if (preferred.isNotEmpty()) preferred else ifaces.filter { it.isUp && !it.isLoopback }
            candidates.flatMap { iface ->
                iface.inetAddresses.toList().filter { addr ->
                    !addr.isLoopbackAddress && !addr.isLinkLocalAddress &&
                            addr.hostAddress?.contains(':') == false // IPv4 only
                }
            }.firstOrNull()?.hostAddress
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Returns the local subnet prefix for [ip] (e.g. "192.168.1." for a /24).
     * Useful for simple same-subnet checks.
     */
    fun subnetPrefix(ip: String): String? {
        val parts = ip.split(".")
        return if (parts.size == 4) "${parts[0]}.${parts[1]}.${parts[2]}." else null
    }

    /**
     * Returns true if [host]:[port] accepts a TCP connection within [timeoutMs].
     */
    fun isReachable(host: String, port: Int, timeoutMs: Int = 1500): Boolean {
        return try {
            Socket().use { sock ->
                sock.connect(InetSocketAddress(host, port), timeoutMs)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Returns true if [peerIp] appears to be on the same /24 subnet as [localIp].
     */
    fun isSameSubnet(localIp: String, peerIp: String): Boolean {
        val local = subnetPrefix(localIp) ?: return false
        return peerIp.startsWith(local)
    }
}
