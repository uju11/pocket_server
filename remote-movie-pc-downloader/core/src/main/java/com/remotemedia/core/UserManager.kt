package com.remotemedia.core

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * M1.1 — User store: hashed passwords, roles (admin/user/guest).
 * Stored as JSON in filesDir/users.json.
 * Thread-safe via synchronized.
 */
object UserManager {

    enum class Role { ADMIN, USER, GUEST }

    data class User(
        val username: String,
        val passwordHash: String, // SHA-256(salt + password), hex
        val salt: String,         // base64, 16 bytes
        val role: Role
    )

    private val gson = Gson()
    private val userType = object : TypeToken<MutableList<User>>() {}.type
    private lateinit var usersFile: File
    private val users = mutableListOf<User>()
    private val rand = SecureRandom()

    @Synchronized
    fun init(context: Context) {
        usersFile = File(context.filesDir, "users.json")
        if (usersFile.exists()) {
            val loaded: List<User>? = try {
                gson.fromJson(usersFile.readText(), userType)
            } catch (_: Exception) { null }
            if (loaded != null) {
                users.clear()
                users.addAll(loaded)
            }
        }
        // Seed a default admin if no users exist
        if (users.isEmpty()) {
            addUser("admin", "adminadmin", Role.ADMIN)
        }
    }

    // ── Public API ────────────────────────────────────────────────

    @Synchronized
    fun authenticate(username: String, password: String): User? {
        val u = users.find { it.username == username } ?: return null
        return if (hash(password, u.salt) == u.passwordHash) u else null
    }

    @Synchronized
    fun addUser(username: String, password: String, role: Role = Role.USER): Boolean {
        if (users.any { it.username == username }) return false
        val salt = newSalt()
        users += User(username, hash(password, salt), salt, role)
        persist()
        return true
    }

    @Synchronized
    fun changePassword(username: String, newPassword: String): Boolean {
        val idx = users.indexOfFirst { it.username == username }
        if (idx < 0) return false
        val salt = newSalt()
        users[idx] = users[idx].copy(passwordHash = hash(newPassword, salt), salt = salt)
        persist()
        return true
    }

    @Synchronized
    fun listUsers(): List<Map<String, String>> =
        users.map { mapOf("username" to it.username, "role" to it.role.name.lowercase()) }

    // ── Private helpers ───────────────────────────────────────────

    private fun newSalt(): String {
        val bytes = ByteArray(16).also { rand.nextBytes(it) }
        return Base64.getEncoder().encodeToString(bytes)
    }

    private fun hash(password: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update((salt + password).toByteArray(Charsets.UTF_8))
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun persist() {
        try { usersFile.writeText(gson.toJson(users)) } catch (_: Exception) {}
    }
}
