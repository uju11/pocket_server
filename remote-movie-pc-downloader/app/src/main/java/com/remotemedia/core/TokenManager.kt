package com.remotemedia.core

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/**
 * M1.2 — Per-device tokens: issue, list, revoke.
 * Tokens are opaque 32-byte random strings stored in filesDir/tokens.json.
 * Each token is bound to a username + device label.
 */
object TokenManager {

    data class Token(
        val token: String,
        val username: String,
        val device: String,   // human-readable label, e.g. "phone-pixel7"
        val issuedAt: Long    // epoch millis
    )

    private val gson = Gson()
    private val tokenType = object : com.google.gson.reflect.TypeToken<MutableList<Token>>() {}.type
    private lateinit var tokensFile: File
    // token string → Token, in-memory fast-path
    private val index = ConcurrentHashMap<String, Token>()
    private val rand = SecureRandom()

    @Synchronized
    fun init(context: Context) {
        tokensFile = File(context.filesDir, "tokens.json")
        if (tokensFile.exists()) {
            val loaded: List<Token>? = try {
                gson.fromJson(tokensFile.readText(), tokenType)
            } catch (_: Exception) { null }
            loaded?.forEach { index[it.token] = it }
        }
    }

    // ── Public API ────────────────────────────────────────────────

    /** Issue a new token for [username] on [device]. Returns the token string. */
    @Synchronized
    fun issue(username: String, device: String): String {
        val token = newToken()
        val t = Token(token, username, device, System.currentTimeMillis())
        index[token] = t
        persist()
        return token
    }

    /** Returns the [Token] if valid, null otherwise. */
    fun validate(token: String): Token? = index[token]

    /** Revoke a specific token. Returns true if it existed. */
    @Synchronized
    fun revoke(token: String): Boolean {
        val removed = index.remove(token) != null
        if (removed) persist()
        return removed
    }

    /** List all tokens for [username] (safe: no token string exposed to callers, only metadata). */
    fun listForUser(username: String): List<Map<String, Any>> =
        index.values
            .filter { it.username == username }
            .map { mapOf("device" to it.device, "issued_at" to it.issuedAt,
                         "token_prefix" to it.token.take(8) + "…") }

    /** List ALL tokens (admin view). */
    fun listAll(): List<Map<String, Any>> =
        index.values.map { mapOf("username" to it.username, "device" to it.device,
                                 "issued_at" to it.issuedAt,
                                 "token_prefix" to it.token.take(8) + "…") }

    // ── Private helpers ───────────────────────────────────────────

    private fun newToken(): String {
        val bytes = ByteArray(32).also { rand.nextBytes(it) }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    @Synchronized
    private fun persist() {
        try { tokensFile.writeText(gson.toJson(index.values.toList())) } catch (_: Exception) {}
    }
}
