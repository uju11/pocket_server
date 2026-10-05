package com.remotemedia.download

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.remotemedia.core.LogTag
import com.remotemedia.core.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.URLEncoder

data class TelegramMediaItem(
    val id: String,
    val fileId: String,
    val fileName: String,
    val cleanTitle: String,
    val year: String = "",
    val quality: String = "1080p",
    val fileSize: Long,
    val sizeFormatted: String,
    val mimeType: String,
    val dateReceived: Long = System.currentTimeMillis(),
    val streamUrl: String = "",
    val posterUrl: String = "",
    val source: String = "Telegram", // "Saved Messages", "Channel Forward", "Direct Bot"
    val channelName: String = ""
)

object TelegramMediaCatalog {
    private var prefs: SharedPreferences? = null
    private val gson = Gson()
    private val _items = MutableStateFlow<List<TelegramMediaItem>>(emptyList())
    val items: StateFlow<List<TelegramMediaItem>> = _items.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.getSharedPreferences("telegram_media_catalog_prefs", Context.MODE_PRIVATE)
        prefs = p
        loadPersistedItems()

        // Seed with sample/detected Apocalypto if empty for instant preview
        if (_items.value.isEmpty()) {
            recordMedia(
                fileId = "sample_apocalypto_2006",
                fileName = "Apocalypto 2006 BluRay 1080p Maya DD 5.1 x264 MSubs - mk.mkv",
                cleanTitle = "Apocalypto",
                year = "2006",
                quality = "1080p BluRay",
                fileSize = 2834677760L,
                sizeFormatted = "2.64 GB",
                mimeType = "video/x-matroska",
                posterUrl = "https://image.tmdb.org/t/p/w500/1X6v8xN2zQv8n9b4d8Gf7k6r4H8.jpg",
                source = "Saved Messages",
                channelName = "Saved Files"
            )
        }
    }

    private fun loadPersistedItems() {
        val json = prefs?.getString("catalog_json", null) ?: return
        try {
            val type = object : TypeToken<List<TelegramMediaItem>>() {}.type
            val list: List<TelegramMediaItem> = gson.fromJson(json, type) ?: emptyList()
            _items.value = list
            Logger.i(LogTag.TELEGRAM, "Loaded ${_items.value.size} Telegram media items from catalog.")
        } catch (e: Exception) {
            Logger.w(LogTag.TELEGRAM, "Failed to load telegram catalog: ${e.message}")
        }
    }

    fun recordMedia(
        fileId: String,
        fileName: String,
        cleanTitle: String,
        year: String = "",
        quality: String = "1080p",
        fileSize: Long,
        sizeFormatted: String,
        mimeType: String,
        posterUrl: String = "",
        source: String = "Telegram",
        channelName: String = ""
    ): TelegramMediaItem {
        val existing = _items.value.find { it.fileId == fileId || (it.fileName.equals(fileName, ignoreCase = true) && fileSize == it.fileSize) }
        if (existing != null) {
            return existing
        }

        val resolvedYear = if (year.isNotBlank()) year else {
            Regex("""\b(19\d\d|20\d\d)\b""").find(fileName)?.value ?: ""
        }

        val resolvedQuality = when {
            quality.isNotBlank() && quality != "1080p" -> quality
            fileName.contains("2160p", ignoreCase = true) || fileName.contains("4k", ignoreCase = true) -> "4K UHD"
            fileName.contains("1080p", ignoreCase = true) -> "1080p"
            fileName.contains("720p", ignoreCase = true) -> "720p"
            fileName.contains("remux", ignoreCase = true) -> "Remux"
            fileName.contains("bluray", ignoreCase = true) -> "BluRay"
            else -> "1080p"
        }

        val encodedTitle = try { URLEncoder.encode(cleanTitle, "UTF-8") } catch (_: Exception) { cleanTitle.replace(" ", "%20") }
        val streamUrl = "/stream/telegram?file_id=$fileId&title=$encodedTitle"

        val item = TelegramMediaItem(
            id = "tg_${System.currentTimeMillis()}",
            fileId = fileId,
            fileName = fileName,
            cleanTitle = cleanTitle.ifBlank { fileName.substringBeforeLast('.') },
            year = resolvedYear,
            quality = resolvedQuality,
            fileSize = fileSize,
            sizeFormatted = sizeFormatted,
            mimeType = mimeType,
            dateReceived = System.currentTimeMillis(),
            streamUrl = streamUrl,
            posterUrl = posterUrl,
            source = source,
            channelName = channelName
        )

        val updated = listOf(item) + _items.value
        _items.value = updated
        persist(updated)
        Logger.i(LogTag.TELEGRAM, "Recorded Telegram media in library: '$cleanTitle' ($sizeFormatted) [Source: $source]")
        return item
    }

    fun removeItem(id: String) {
        val updated = _items.value.filterNot { it.id == id || it.fileId == id }
        _items.value = updated
        persist(updated)
    }

    private fun persist(list: List<TelegramMediaItem>) {
        try {
            val json = gson.toJson(list)
            prefs?.edit()?.putString("catalog_json", json)?.apply()
        } catch (e: Exception) {
            Logger.w(LogTag.TELEGRAM, "Failed to persist telegram catalog: ${e.message}")
        }
    }
}
