package com.remotemedia.services

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.remotemedia.core.StorageManager
import java.io.File
import java.util.UUID

data class JellyfinUser(
    val id: String = UUID.nameUUIDFromBytes(UUID.randomUUID().toString().toByteArray()).toString(),
    val name: String,
    val role: String = "ADMIN", // "ADMIN", "GUEST", "KIDS"
    val isAdmin: Boolean = role == "ADMIN",
    val hasPassword: Boolean = false,
    val canDownload: Boolean = role != "KIDS",
    val canDelete: Boolean = role == "ADMIN"
)

data class JellyfinLibrary(
    val id: String = UUID.nameUUIDFromBytes(UUID.randomUUID().toString().toByteArray()).toString(),
    val name: String,
    val collectionType: String = "movies", // "movies", "tvshows", "music"
    val folderPath: String
)

object JellyfinManager {

    private val gson = Gson()
    private val usersList = mutableListOf<JellyfinUser>()
    private val librariesList = mutableListOf<JellyfinLibrary>()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences("remote_media_prefs", Context.MODE_PRIVATE)

        // Load users
        val usersJson = prefs.getString("jellyfin_users_json", null)
        usersList.clear()
        if (!usersJson.isNullOrBlank()) {
            val type = object : TypeToken<List<JellyfinUser>>() {}.type
            val savedUsers: List<JellyfinUser> = gson.fromJson(usersJson, type)
            usersList.addAll(savedUsers)
        } else {
            // Default Admin, Guest, Kids user group
            setupDefaultUsers(context, "Admin", false)
        }

        // Load libraries
        val libsJson = prefs.getString("jellyfin_libraries_json", null)
        librariesList.clear()
        if (!libsJson.isNullOrBlank()) {
            val type = object : TypeToken<List<JellyfinLibrary>>() {}.type
            val savedLibs: List<JellyfinLibrary> = gson.fromJson(libsJson, type)
            librariesList.addAll(savedLibs)
        } else {
            // Default Movies library
            val defaultMoviesDir = StorageManager.getMoviesDir().absolutePath
            librariesList.add(
                JellyfinLibrary(
                    id = UUID.nameUUIDFromBytes("movies-lib".toByteArray()).toString(),
                    name = "Movies & Media",
                    collectionType = "movies",
                    folderPath = defaultMoviesDir
                )
            )
        }
    }

    fun getUsers(): List<JellyfinUser> {
        if (usersList.isEmpty()) {
            val ctx = StorageManager.getContext()
            if (ctx != null) runCatching { init(ctx) }
            if (usersList.isEmpty()) {
                usersList.add(
                    JellyfinUser(
                        id = UUID.nameUUIDFromBytes("admin-user".toByteArray()).toString(),
                        name = "Admin",
                        role = "ADMIN",
                        isAdmin = true,
                        hasPassword = false,
                        canDownload = true,
                        canDelete = true
                    )
                )
            }
        }
        return usersList.toList()
    }

    fun setupDefaultUsers(context: Context, adminName: String, adminHasPassword: Boolean): List<JellyfinUser> {
        usersList.clear()
        val admin = JellyfinUser(
            id = UUID.nameUUIDFromBytes("admin-user".toByteArray()).toString(),
            name = if (adminName.isNotBlank()) adminName else "Admin",
            role = "ADMIN",
            isAdmin = true,
            hasPassword = adminHasPassword,
            canDownload = true,
            canDelete = true
        )
        val guest = JellyfinUser(
            id = UUID.nameUUIDFromBytes("guest-user".toByteArray()).toString(),
            name = "Guest",
            role = "GUEST",
            isAdmin = false,
            hasPassword = false,
            canDownload = true,
            canDelete = false
        )
        val kids = JellyfinUser(
            id = UUID.nameUUIDFromBytes("kids-user".toByteArray()).toString(),
            name = "Kids",
            role = "KIDS",
            isAdmin = false,
            hasPassword = false,
            canDownload = false,
            canDelete = false
        )
        usersList.addAll(listOf(admin, guest, kids))
        saveState(context)
        return usersList.toList()
    }

    fun addUser(context: Context, name: String, isAdmin: Boolean, role: String = if (isAdmin) "ADMIN" else "GUEST"): JellyfinUser {
        val newUser = JellyfinUser(
            id = UUID.randomUUID().toString(),
            name = name,
            role = role,
            isAdmin = role == "ADMIN",
            canDownload = role != "KIDS",
            canDelete = role == "ADMIN"
        )
        usersList.add(newUser)
        saveState(context)
        return newUser
    }

    fun removeUser(context: Context, userId: String) {
        if (usersList.size > 1) { // Prevent deleting the last user
            usersList.removeAll { it.id == userId }
            saveState(context)
        }
    }

    fun getLibraries(): List<JellyfinLibrary> {
        val currentMoviesDir = runCatching { StorageManager.getMoviesDir().absolutePath }.getOrNull()
        if (currentMoviesDir != null) {
            val hasActiveMovies = librariesList.any {
                runCatching {
                    val f = File(it.folderPath)
                    f.exists() && (it.folderPath == currentMoviesDir || f.canonicalPath == File(currentMoviesDir).canonicalPath)
                }.getOrDefault(false)
            }
            if (!hasActiveMovies) {
                val updated = librariesList.toMutableList()
                val defIdx = updated.indexOfFirst { it.id == UUID.nameUUIDFromBytes("movies-lib".toByteArray()).toString() }
                if (defIdx >= 0) {
                    updated[defIdx] = updated[defIdx].copy(folderPath = currentMoviesDir)
                } else {
                    updated.add(0, JellyfinLibrary(
                        id = UUID.nameUUIDFromBytes("movies-lib".toByteArray()).toString(),
                        name = "Movies & Media",
                        collectionType = "movies",
                        folderPath = currentMoviesDir
                    ))
                }
                librariesList.clear()
                librariesList.addAll(updated)
            }
        }
        return librariesList.toList()
    }

    fun addLibrary(context: Context, name: String, collectionType: String, folderPath: String): JellyfinLibrary {
        val targetDir = File(folderPath)
        if (!targetDir.exists()) targetDir.mkdirs()

        val newLib = JellyfinLibrary(
            id = UUID.randomUUID().toString(),
            name = name,
            collectionType = collectionType,
            folderPath = targetDir.absolutePath
        )
        librariesList.add(newLib)
        saveState(context)
        return newLib
    }

    fun removeLibrary(context: Context, libraryId: String) {
        librariesList.removeAll { it.id == libraryId }
        saveState(context)
    }

    private fun saveState(context: Context) {
        val prefs = context.getSharedPreferences("remote_media_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("jellyfin_users_json", gson.toJson(usersList))
            .putString("jellyfin_libraries_json", gson.toJson(librariesList))
            .apply()
    }
}
