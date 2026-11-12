package com.remotemedia.core

import android.content.Context
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * M1.3 — QR pairing.
 * Generates one-time tokens clients scan to get server_id + endpoints.
 * Token expires after first use or TTL_MS.
 */
object PairingManager {

    private const val TTL_MS = 5 * 60 * 1000L   // 5-minute expiry

    data class PairingOffer(
        val otp: String,          // one-time token (URL-safe base64, 16 bytes)
        val serverId: String,     // stable per-install UUID
        val expiresAt: Long
    )

    data class PairingPayload(
        val server_id: String,
        val base_url: String,     // e.g. http://192.168.1.42:8080
        val otp: String
    )

    private val rand = SecureRandom()
    private val otps = ConcurrentHashMap<String, PairingOffer>()
    lateinit var serverId: String
        private set

    fun init(context: Context) {
        val prefs = context.getSharedPreferences("pairing", Context.MODE_PRIVATE)
        serverId = prefs.getString("server_id", null) ?: run {
            val id = UUID.randomUUID().toString()
            prefs.edit().putString("server_id", id).apply()
            id
        }
    }

    // ── Public API ────────────────────────────────────────────────

    /** Create a new one-time pairing offer. Returns the OTP. */
    fun createOffer(): PairingOffer {
        purgeExpired()
        val otp = newOtp()
        val offer = PairingOffer(otp, serverId, System.currentTimeMillis() + TTL_MS)
        otps[otp] = offer
        return offer
    }

    /**
     * Redeem an OTP: returns the payload (server_id + base_url) if valid, null otherwise.
     * Consumes the OTP (single-use).
     */
    fun redeem(otp: String, baseUrl: String): PairingPayload? {
        val offer = otps.remove(otp) ?: return null
        if (System.currentTimeMillis() > offer.expiresAt) return null
        return PairingPayload(serverId, baseUrl, otp)
    }

    // ── Private helpers ───────────────────────────────────────────

    private fun newOtp(): String {
        val bytes = ByteArray(16).also { rand.nextBytes(it) }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun purgeExpired() {
        val now = System.currentTimeMillis()
        otps.entries.removeIf { it.value.expiresAt < now }
    }
}
