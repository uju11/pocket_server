package com.remotemedia.sources

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * M5.3 — Internet Archive public domain video source plugin.
 * Queries archive.org search & metadata APIs for direct streaming/downloadable MP4 media.
 */
data class ArchiveMediaItem(
    val identifier: String,
    val title: String,
    val description: String = "",
    val streamUrl: String,
    val thumbUrl: String = "",
    val sizeBytes: Long = 0L
)

object ArchiveOrgSource {

    private val gson = Gson()

    /**
     * Searches Internet Archive movies collection for [query].
     * Returns direct MP4 streaming links for top results.
     */
    suspend fun search(
        query: String,
        limit: Int = 10,
        timeoutMs: Int = 8_000
    ): List<ArchiveMediaItem> = withContext(Dispatchers.IO) {
        try {
            val encodedQ = URLEncoder.encode("$query AND mediatype:movies", "UTF-8")
            val searchUrl = "https://archive.org/advancedsearch.php?q=$encodedQ&fl[]=identifier,title,description&rows=$limit&output=json"

            val jsonStr = httpGet(searchUrl, timeoutMs) ?: return@withContext emptyList()
            val root = gson.fromJson(jsonStr, JsonObject::class.java)
            val docs = root.getAsJsonObject("response")?.getAsJsonArray("docs") ?: return@withContext emptyList()

            val results = mutableListOf<ArchiveMediaItem>()
            for (el in docs) {
                val doc = el.asJsonObject
                val id = doc["identifier"]?.asString ?: continue
                val title = doc["title"]?.asString ?: id
                val desc = doc["description"]?.asString ?: ""

                // Construct direct stream and thumbnail URLs
                val streamUrl = "https://archive.org/download/$id/$id.mp4"
                val thumbUrl = "https://archive.org/services/img/$id"

                results.add(
                    ArchiveMediaItem(
                        identifier = id,
                        title = title,
                        description = desc,
                        streamUrl = streamUrl,
                        thumbUrl = thumbUrl
                    )
                )
            }
            results
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Fetches detailed file listing for an item to find the best video file.
     */
    suspend fun resolveBestVideoUrl(identifier: String, timeoutMs: Int = 8_000): String? = withContext(Dispatchers.IO) {
        try {
            val metaUrl = "https://archive.org/metadata/$identifier"
            val jsonStr = httpGet(metaUrl, timeoutMs) ?: return@withContext null
            val root = gson.fromJson(jsonStr, JsonObject::class.java)
            val files = root.getAsJsonArray("files") ?: return@withContext null

            var bestFile: String? = null
            var bestSize: Long = -1L

            for (f in files) {
                val obj = f.asJsonObject
                val name = obj["name"]?.asString ?: continue
                val format = obj["format"]?.asString ?: ""
                val size = obj["size"]?.asString?.toLongOrNull() ?: 0L

                if (name.endsWith(".mp4", ignoreCase = true) || format.contains("h.264", ignoreCase = true)) {
                    if (size > bestSize) {
                        bestSize = size
                        bestFile = name
                    }
                }
            }

            bestFile?.let { "https://archive.org/download/$identifier/${URLEncoder.encode(it, "UTF-8")}" }
        } catch (_: Exception) {
            null
        }
    }

    private fun httpGet(urlStr: String, timeoutMs: Int): String? {
        return try {
            val conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.setRequestProperty("User-Agent", "PocketNode/1.0")
            if (conn.responseCode in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else null
        } catch (_: Exception) {
            null
        }
    }
}
