package com.remotemedia.storage

import android.content.Context
import java.io.File
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/**
 * M2.2 — Share links: generate a public token → file mapping.
 * POST /api/drive/share?path=<relative>  → { "token": "…", "url": "/s/<token>" }
 * GET  /s/<token>                         → file download (no auth)
 * DELETE /api/drive/share/<token>         → revoke
 */
object ShareLinkManager {

    data class ShareLink(
        val token: String,
        val absolutePath: String,
        val fileName: String,
        val createdAt: Long,
        val expiresAt: Long?   // null = never
    )

    private val links = ConcurrentHashMap<String, ShareLink>()
    private val rand = SecureRandom()

    fun init(@Suppress("UNUSED_PARAMETER") context: Context) { /* stateless for now */ }

    // ── Public API ────────────────────────────────────────────────

    /** Create a share link for [absolutePath]. [ttlMs] null = no expiry. */
    fun create(absolutePath: String, ttlMs: Long? = null): ShareLink {
        val token = newToken()
        val file = File(absolutePath)
        val link = ShareLink(
            token = token,
            absolutePath = absolutePath,
            fileName = file.name,
            createdAt = System.currentTimeMillis(),
            expiresAt = ttlMs?.let { System.currentTimeMillis() + it }
        )
        links[token] = link
        return link
    }

    /** Resolve token → ShareLink if valid (not expired, file exists). */
    fun resolve(token: String): ShareLink? {
        val link = links[token] ?: return null
        if (link.expiresAt != null && System.currentTimeMillis() > link.expiresAt) {
            links.remove(token)
            return null
        }
        if (!File(link.absolutePath).exists()) {
            links.remove(token)
            return null
        }
        return link
    }

    fun revoke(token: String): Boolean = links.remove(token) != null

    fun listAll(): List<Map<String, Any?>> = links.values.map {
        mapOf("token" to it.token, "file_name" to it.fileName,
              "created_at" to it.createdAt, "expires_at" to it.expiresAt)
    }

    private fun newToken(): String {
        val bytes = ByteArray(16).also { rand.nextBytes(it) }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
