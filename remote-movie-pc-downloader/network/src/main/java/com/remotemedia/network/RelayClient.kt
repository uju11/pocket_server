package com.remotemedia.network

import com.remotemedia.core.Logger
import com.remotemedia.core.LogTag

/**
 * M3.2 — Relay client stub.
 *
 * Dials the relay WS URL (set via [configure]), registers as a host, and
 * forwards heartbeat pings. Disabled by default (seeding off by default per AGENTS.md safety rules).
 *
 * Full WebSocket dialling requires a live device; this stub provides the
 * state machine, configuration, and hooks so M3.3+ can wire it up.
 */
object RelayClient {

    // ── Configuration ────────────────────────────────────────────────────
    @Volatile private var relayUrl: String? = null
    @Volatile private var serverId: String? = null
    @Volatile private var enabled: Boolean = false   // off by default

    @Volatile var isConnected: Boolean = false
        private set

    /** Call from ServerForegroundService.init; does NOT start connection. */
    fun configure(url: String?, id: String, enable: Boolean = false) {
        relayUrl = url
        serverId = id
        enabled = enable && !url.isNullOrBlank()
        Logger.d(LogTag.NETWORK, "RelayClient configured url=$url enabled=$enabled")
    }

    // ── Lifecycle ────────────────────────────────────────────────────────

    /**
     * Start the relay connection if [enabled]. No-op otherwise.
     * TODO(M3.2): replace with real OkHttp / Ktor WS dial when device-tested.
     */
    fun start() {
        if (!enabled) {
            Logger.d(LogTag.NETWORK, "RelayClient disabled — skipping start")
            return
        }
        Logger.d(LogTag.NETWORK, "RelayClient start() stub — TODO dial $relayUrl as $serverId")
        // Stub: mark connected=false until real WS impl lands
        isConnected = false
    }

    /** Stop and clean up the relay connection. */
    fun stop() {
        isConnected = false
        Logger.d(LogTag.NETWORK, "RelayClient stopped")
    }

    // ── Heartbeat ────────────────────────────────────────────────────────

    /**
     * Send a heartbeat ping to the relay.
     * No-op in stub; real impl will write a WS text frame.
     */
    fun sendHeartbeat() {
        if (!enabled || !isConnected) return
        Logger.d(LogTag.NETWORK, "RelayClient heartbeat (stub)")
    }

    // ── Status ───────────────────────────────────────────────────────────

    fun status(): Map<String, Any?> = mapOf(
        "enabled" to enabled,
        "relay_url" to relayUrl,
        "connected" to isConnected
    )
}

