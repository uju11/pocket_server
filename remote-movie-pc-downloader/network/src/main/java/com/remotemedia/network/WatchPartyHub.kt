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
     * Returns a direct response string if needed, and broadcasts state changes to other peers.
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
                val stateMsg = gson.toJson(party.toStateMap())
                broadcast(partyId, stateMsg)
                stateMsg
            }
            "pause" -> {
                party.positionSec = party.currentPosition()
                party.playing = false
                party.serverTimeMs = now
                val stateMsg = gson.toJson(party.toStateMap())
                broadcast(partyId, stateMsg)
                stateMsg
            }
            "seek" -> {
                val targetPos = json["position"]?.asDouble ?: 0.0
                party.positionSec = targetPos
                party.serverTimeMs = now
                val stateMsg = gson.toJson(party.toStateMap())
                broadcast(partyId, stateMsg)
                stateMsg
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
