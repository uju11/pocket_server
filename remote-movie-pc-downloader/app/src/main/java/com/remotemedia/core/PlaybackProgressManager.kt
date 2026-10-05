package com.remotemedia.core

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

data class PlaybackProgress(
    val itemId: String,
    val title: String,
    val streamUrl: String,
    val positionTicks: Long,       // 1 second = 10,000,000 ticks (Jellyfin standard)
    val positionSeconds: Long,
    val durationSeconds: Long,
    val percent: Float,
    val lastPlayedTimestamp: Long = System.currentTimeMillis()
)

object PlaybackProgressManager {
    private val gson = Gson()
    private val progressMap = ConcurrentHashMap<String, PlaybackProgress>()
    private val _lastPlayed = MutableStateFlow<PlaybackProgress?>(null)
    val lastPlayed: StateFlow<PlaybackProgress?> = _lastPlayed.asStateFlow()

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences("playback_progress_prefs", Context.MODE_PRIVATE)
        loadFromPrefs()
    }

    private fun ensureInit() {
        if (prefs == null) {
            StorageManager.getContext()?.let { init(it) }
        }
    }

    private fun loadFromPrefs() {
        val json = prefs?.getString("saved_progress", null) ?: return
        try {
            val type = object : TypeToken<Map<String, PlaybackProgress>>() {}.type
            val saved: Map<String, PlaybackProgress> = gson.fromJson(json, type) ?: emptyMap()
            progressMap.putAll(saved)
            _lastPlayed.value = progressMap.values.maxByOrNull { it.lastPlayedTimestamp }
        } catch (_: Exception) {}
    }

    private fun saveToPrefs() {
        try {
            val json = gson.toJson(progressMap)
            prefs?.edit()?.putString("saved_progress", json)?.apply()
        } catch (_: Exception) {}
    }

    fun updateProgress(
        itemId: String,
        title: String,
        streamUrl: String = "",
        positionSeconds: Long,
        durationSeconds: Long
    ) {
        ensureInit()
        if (durationSeconds <= 0L) return
        val percent = (positionSeconds.toFloat() / durationSeconds.toFloat()).coerceIn(0f, 1f)
        val ticks = positionSeconds * 10_000_000L

        val cleanKey = cleanKey(itemId.ifBlank { title })

        // If played >= 92%, consider completed and remove from resume queue
        if (percent >= 0.92f) {
            progressMap.remove(cleanKey)
            saveToPrefs()
            if (_lastPlayed.value?.itemId == itemId || _lastPlayed.value?.title == title) {
                _lastPlayed.value = progressMap.values.maxByOrNull { it.lastPlayedTimestamp }
            }
            Logger.i(LogTag.JELLYFIN, "Playback completed for $title ($percent). Removed from resume queue.")
            return
        }

        // Only save if watched at least 3 seconds
        if (positionSeconds < 3L) return

        val item = PlaybackProgress(
            itemId = itemId,
            title = title,
            streamUrl = streamUrl,
            positionTicks = ticks,
            positionSeconds = positionSeconds,
            durationSeconds = durationSeconds,
            percent = percent,
            lastPlayedTimestamp = System.currentTimeMillis()
        )

        progressMap[cleanKey] = item
        _lastPlayed.value = item
        saveToPrefs()
        Logger.i(LogTag.JELLYFIN, "Updated playback progress for '$title': ${positionSeconds}s / ${durationSeconds}s (${(percent * 100).toInt()}%)")
    }

    fun getProgress(idOrTitle: String): PlaybackProgress? {
        ensureInit()
        val key = cleanKey(idOrTitle)
        return progressMap[key] ?: progressMap.values.find {
            it.itemId.equals(idOrTitle, ignoreCase = true) ||
            it.title.equals(idOrTitle, ignoreCase = true) ||
            cleanKey(it.title) == key
        }
    }

    fun getAllResumeItems(): List<PlaybackProgress> {
        ensureInit()
        return progressMap.values
            .filter { it.percent in 0.01f..0.92f }
            .sortedByDescending { it.lastPlayedTimestamp }
    }

    fun getLastPlayed(): PlaybackProgress? {
        ensureInit()
        return _lastPlayed.value ?: progressMap.values.maxByOrNull { it.lastPlayedTimestamp }
    }

    private fun cleanKey(raw: String): String {
        return raw.lowercase()
            .replace(Regex("[^a-z0-9]"), "")
    }
}
