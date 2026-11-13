package com.remotemedia.sources

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.regex.Pattern

/**
 * M5.2 — Torznab XML/RSS search client.
 * Parses Torznab / Newznab / Jackett feeds into unified media items.
 */
data class TorznabMediaItem(
    val title: String,
    val magnetUrl: String,
    val sizeBytes: Long,
    val seeders: Int,
    val leechers: Int,
    val publishDate: String = "",
    val category: String = "movies"
)

object TorznabClient {

    /**
     * Parses an RSS/Torznab XML string into a list of [TorznabMediaItem].
     * Uses regex-based tag extraction to remain lightweight and independent of Android XML parser variations.
     */
    fun parseXml(xml: String): List<TorznabMediaItem> {
        val items = mutableListOf<TorznabMediaItem>()
        val itemMatcher = Pattern.compile("<item>(.*?)</item>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE).matcher(xml)

        while (itemMatcher.find()) {
            val itemBlock = itemMatcher.group(1) ?: continue

            val title = extractTag(itemBlock, "title")?.trim() ?: "Untitled"

            // Magnet URL can appear in <link>, <enclosure url="...">, or <torznab:attr name="magneturl" ...>
            val magnetUrl = extractTorznabAttr(itemBlock, "magneturl")
                ?: extractTag(itemBlock, "link")?.takeIf { it.startsWith("magnet:", ignoreCase = true) }
                ?: extractEnclosureUrl(itemBlock)
                ?: ""

            // Size in bytes
            val sizeStr = extractTorznabAttr(itemBlock, "size")
                ?: extractTag(itemBlock, "size")
                ?: extractEnclosureLength(itemBlock)
            val sizeBytes = sizeStr?.toLongOrNull() ?: 0L

            // Seeders and peers
            val seeders = extractTorznabAttr(itemBlock, "seeders")?.toIntOrNull() ?: 0
            val peers = extractTorznabAttr(itemBlock, "peers")?.toIntOrNull() ?: 0
            val leechers = (peers - seeders).coerceAtLeast(0)

            val pubDate = extractTag(itemBlock, "pubDate") ?: ""

            if (title.isNotBlank() && (magnetUrl.isNotBlank() || sizeBytes > 0)) {
                items.add(
                    TorznabMediaItem(
                        title = title,
                        magnetUrl = magnetUrl,
                        sizeBytes = sizeBytes,
                        seeders = seeders,
                        leechers = leechers,
                        publishDate = pubDate
                    )
                )
            }
        }
        return items
    }

    /**
     * Searches a Torznab-compatible indexer with the given [query].
     */
    suspend fun search(
        baseUrl: String,
        apiKey: String,
        query: String,
        timeoutMs: Int = 10_000
    ): List<TorznabMediaItem> = withContext(Dispatchers.IO) {
        try {
            val cleanBase = baseUrl.trimEnd('/')
            val encodedQ = URLEncoder.encode(query, "UTF-8")
            val targetUrl = "$cleanBase/api?t=search&q=$encodedQ&apikey=$apiKey"
            val conn = URL(targetUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.setRequestProperty("User-Agent", "PocketNode/1.0")

            if (conn.responseCode in 200..299) {
                val xml = conn.inputStream.bufferedReader().use { it.readText() }
                parseXml(xml)
            } else {
                emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun extractTag(block: String, tag: String): String? {
        val m = Pattern.compile("<$tag>(.*?)</$tag>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE).matcher(block)
        return if (m.find()) m.group(1)?.replace("<![CDATA[", "")?.replace("]]>", "")?.trim() else null
    }

    private fun extractTorznabAttr(block: String, name: String): String? {
        val m = Pattern.compile("""<torznab:attr[^>]+name=["']$name["'][^>]+value=["']([^"']+)["']""", Pattern.CASE_INSENSITIVE).matcher(block)
        return if (m.find()) m.group(1) else null
    }

    private fun extractEnclosureUrl(block: String): String? {
        val m = Pattern.compile("""<enclosure[^>]+url=["']([^"']+)["']""", Pattern.CASE_INSENSITIVE).matcher(block)
        return if (m.find()) m.group(1) else null
    }

    private fun extractEnclosureLength(block: String): String? {
        val m = Pattern.compile("""<enclosure[^>]+length=["']([^"']+)["']""", Pattern.CASE_INSENSITIVE).matcher(block)
        return if (m.find()) m.group(1) else null
    }
}
