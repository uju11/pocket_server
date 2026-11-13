package com.remotemedia.core

import android.content.Context
import android.content.SharedPreferences
import java.util.concurrent.ConcurrentHashMap

/**
 * M4.1 — Per-item public/private visibility store.
 * AGENTS.md safety rule: "Default visibility private."
 * An item is private unless explicitly flagged as public.
 */
object MediaVisibilityManager {

    private const val PREFS_NAME = "media_visibility"
    private var prefs: SharedPreferences? = null
    private val memoryStore = ConcurrentHashMap<String, Boolean>()

    fun init(context: Context) {
        val p = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = p
        p.all.forEach { (key, value) ->
            if (value is Boolean) {
                memoryStore[key] = value
            }
        }
    }

    /**
     * Returns true if [mediaId] is explicitly marked public;
     * false by default (private).
     */
    fun isPublic(mediaId: String): Boolean {
        return memoryStore[mediaId] ?: false
    }

    /**
     * Set the visibility of [mediaId].
     */
    fun setVisibility(mediaId: String, isPublic: Boolean) {
        memoryStore[mediaId] = isPublic
        prefs?.edit()?.putBoolean(mediaId, isPublic)?.apply()
    }

    /**
     * Returns all media IDs marked public.
     */
    fun getAllPublicIds(): Set<String> {
        return memoryStore.filterValues { it }.keys
    }

    /**
     * Filters a collection of [mediaIds].
     * If [isGuest] is true, only public items are returned.
     * Otherwise (authenticated user/admin), all items are returned.
     */
    fun filterVisible(mediaIds: Collection<String>, isGuest: Boolean): List<String> {
        return if (isGuest) {
            mediaIds.filter { isPublic(it) }
        } else {
            mediaIds.toList()
        }
    }

    /** Reset memory cache (for testing). */
    fun clearForTest() {
        memoryStore.clear()
        prefs = null
    }
}
