package com.remotemedia.network

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

/**
 * M6.1 — Watch Party WebSocket hub with host-authoritative clock synchronization.
 * Implements the S4 spike clock-sync protocol (< 300ms drift over LAN/relay).
 */
object WatchPartyHub {

    private val gson = Gson()

    data class PartyState(
        val partyId: String,
        var mediaId: String = "",
        var playing: Boolean = false,
        var positionSec: Double = 0.0,
        var serverTimeMs: Long = System.currentTimeMillis()
    ) {
        fun currentPosition(): Double {
            return if (playing) {
                positionSec + (System.currentTimeMillis() - serverTimeMs) / 1000.0
            } else {
                positionSec
            }
        }

        fun toStateMap(): Map<String, Any> = mapOf(
            "type" to "state",
            "party_id" to partyId,
            "media_id" to mediaId,
            "playing" to playing,
            "position" to currentPosition(),
            "server_time" to System.currentTimeMillis()
        )
    }

    private val parties = ConcurrentHashMap<String, PartyState>()
    private val listeners = ConcurrentHashMap<String, CopyOnWriteArraySet<suspend (String) -> Unit>>()

    data class PartyInvite(
        val token: String,
        val partyId: String,
        val expiresAtMs: Long
    )

    private val invites = ConcurrentHashMap<String, PartyInvite>()

    fun createInvite(partyId: String, ttlMs: Long? = null): PartyInvite {
        val ttl = ttlMs ?: (24 * 60 * 60 * 1000L) // 24 hours default
        val token = java.util.UUID.randomUUID().toString().replace("-", "").take(16)
        val invite = PartyInvite(token = token, partyId = partyId, expiresAtMs = System.currentTimeMillis() + ttl)
        invites[token] = invite
        return invite
    }

    fun resolveInvite(token: String): PartyInvite? {
        val inv = invites[token] ?: return null
        if (System.currentTimeMillis() > inv.expiresAtMs) {
            invites.remove(token)
            return null
        }
        return inv
    }

    fun getOrCreateParty(partyId: String, mediaId: String = ""): PartyState {
        return parties.computeIfAbsent(partyId) {
            PartyState(partyId = partyId, mediaId = mediaId)
        }
    }

    fun addListener(partyId: String, listener: suspend (String) -> Unit) {
        listeners.computeIfAbsent(partyId) { CopyOnWriteArraySet() }.add(listener)
    }

    fun removeListener(partyId: String, listener: suspend (String) -> Unit) {
        listeners[partyId]?.remove(listener)
    }

    suspend fun broadcast(partyId: String, message: String) {
        val targets = listeners[partyId] ?: return
        for (listener in targets) {
            try {
                listener(message)
            } catch (_: Exception) {}
        }
    }

    /**
     * Process an incoming WebSocket JSON message for [partyId].
     * Broadcasts state changes to all peers. Returns a direct response only for ping/state queries.
     */
    suspend fun handleMessage(partyId: String, raw: String): String? {
        val json = try {
            JsonParser.parseString(raw).asJsonObject
        } catch (_: Exception) {
            return null
        }

        val type = json["type"]?.asString ?: return null
        val party = getOrCreateParty(partyId)
        val now = System.currentTimeMillis()

        return when (type) {
            "ping" -> {
                val t0 = json["t0"]?.asLong ?: 0L
                gson.toJson(mapOf("type" to "pong", "t0" to t0, "ts" to now))
            }
            "play" -> {
                party.positionSec = party.currentPosition()
                party.playing = true
                party.serverTimeMs = now
                broadcast(partyId, gson.toJson(party.toStateMap()))
                null // broadcast covers all listeners including sender
            }
            "pause" -> {
                party.positionSec = party.currentPosition()
                party.playing = false
                party.serverTimeMs = now
                broadcast(partyId, gson.toJson(party.toStateMap()))
                null // broadcast covers all listeners including sender
            }
            "seek" -> {
                val targetPos = json["position"]?.asDouble ?: 0.0
                party.positionSec = targetPos
                party.serverTimeMs = now
                broadcast(partyId, gson.toJson(party.toStateMap()))
                null // broadcast covers all listeners including sender
            }
            "state", "sync", "join" -> {
                gson.toJson(party.toStateMap())
            }
            else -> null
        }
    }

    fun getParty(partyId: String): PartyState? = parties[partyId]

    fun clearForTest() {
        parties.clear()
        listeners.clear()
    }
}
