package com.remotemedia.services

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.remotemedia.core.LogTag
import com.remotemedia.core.Logger
import com.remotemedia.download.TorrentEngine
import com.remotemedia.download.TorrentTrackersManager
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID

data class JackettReleaseItem(
    val title: String,
    val guid: String,
    val magnetUrl: String,
    val sizeBytes: Long,
    val seeders: Int,
    val leechers: Int,
    val indexer: String,
    val publishDate: String = "Fri, 02 Oct 2026 00:00:00 +0000"
)

object JackettServer {

    private var serverEngine: ApplicationEngine? = null
    private var apiKey: String = ""
    private var appContext: Context? = null
    private val httpClient = HttpClient(ClientCIO)
    private val gson = Gson()

    fun init(context: Context) {
        appContext = context.applicationContext
        val prefs = context.getSharedPreferences("remote_media_prefs", Context.MODE_PRIVATE)
        var savedKey = prefs.getString("jackett_api_key", "") ?: ""
        if (savedKey.isBlank()) {
            savedKey = UUID.randomUUID().toString().replace("-", "")
            prefs.edit().putString("jackett_api_key", savedKey).apply()
        }
        apiKey = savedKey
    }

    fun getApiKey(): String {
        if (apiKey.isBlank() && appContext != null) {
            val prefs = appContext!!.getSharedPreferences("remote_media_prefs", Context.MODE_PRIVATE)
            apiKey = prefs.getString("jackett_api_key", "") ?: ""
            if (apiKey.isBlank()) {
                apiKey = UUID.randomUUID().toString().replace("-", "")
                prefs.edit().putString("jackett_api_key", apiKey).apply()
            }
        }
        return apiKey
    }

    fun isRunning(): Boolean = serverEngine != null

    fun start(port: Int = 9117) {
        if (serverEngine != null) return

        getApiKey() // Ensure API key exists

        try {
            serverEngine = embeddedServer(CIO, host = "0.0.0.0", port = port) {
                install(CORS) {
                    anyHost()
                    allowNonSimpleContentTypes = true
                }

                routing {
                    // 1. Jackett Web Dashboard
                    get("/") {
                        call.respondText(generateDashboardHtml(), ContentType.Text.Html)
                    }
                    get("/UI/Dashboard") {
                        call.respondText(generateDashboardHtml(), ContentType.Text.Html)
                    }

                    // 2. Status Endpoint
                    get("/api/v2.0/server/status") {
                        val status = JsonObject().apply {
                            addProperty("status", "online")
                            addProperty("version", "0.24.2748-pocketnode")
                            addProperty("app", "PocketNode Embedded Jackett")
                            addProperty("apiKey", apiKey)
                        }
                        call.respondText(status.toString(), ContentType.Application.Json)
                    }

                    // 3. Torznab XML / Capabilities & Search Endpoint
                    get("/api/v2.0/indexers/all/results/torznab/api") {
                        handleTorznabRequest(call)
                    }
                    get("/api/v2.0/indexers/{indexer}/results/torznab/api") {
                        handleTorznabRequest(call)
                    }

                    // 4. Jackett JSON API Search Endpoint
                    get("/api/v2.0/indexers/all/results") {
                        val reqApiKey = call.request.queryParameters["apikey"] ?: ""
                        if (!isApiKeyValid(reqApiKey)) {
                            call.respondText(
                                "{\"error\": \"Invalid API Key\"}",
                                ContentType.Application.Json,
                                HttpStatusCode.Unauthorized
                            )
                            return@get
                        }

                        val query = call.request.queryParameters["Query"] ?: call.request.queryParameters["q"] ?: ""
                        val results = searchAllIndexers(query)

                        val jsonResults = JsonArray()
                        for (item in results) {
                            val obj = JsonObject().apply {
                                addProperty("Title", item.title)
                                addProperty("Guid", item.guid)
                                addProperty("MagnetUri", item.magnetUrl)
                                addProperty("Size", item.sizeBytes)
                                addProperty("Seeders", item.seeders)
                                addProperty("Peers", item.leechers)
                                addProperty("Tracker", item.indexer)
                                addProperty("PublishDate", item.publishDate)
                            }
                            jsonResults.add(obj)
                        }

                        val responseObj = JsonObject().apply {
                            add("Results", jsonResults)
                            addProperty("Total", results.size)
                        }
                        call.respondText(responseObj.toString(), ContentType.Application.Json)
                    }

                    // 5. Test search API for web UI
                    get("/api/search") {
                        val query = call.request.queryParameters["q"] ?: ""
                        val results = searchAllIndexers(query)
                        call.respondText(gson.toJson(results), ContentType.Application.Json)
                    }

                    // 6. Direct download trigger from web UI
                    post("/api/download") {
                        val params = call.receiveParameters()
                        val magnet = params["magnet"] ?: ""
                        if (magnet.isNotBlank()) {
                            TorrentEngine.downloadMagnet(magnet)
                            call.respondText("{\"status\":\"ok\"}", ContentType.Application.Json)
                        } else {
                            call.respondText("{\"error\":\"missing magnet\"}", ContentType.Application.Json, HttpStatusCode.BadRequest)
                        }
                    }
                }
            }.start(wait = false)

            Logger.i(LogTag.SYSTEM, "Embedded Jackett Server started on port $port (Torznab API Key: ${apiKey.take(8)}...)")
        } catch (e: Exception) {
            Logger.e(LogTag.SYSTEM, "Failed to start Jackett server on port $port: ${e.message}")
            serverEngine = null
        }
    }

    fun stop() {
        try {
            serverEngine?.stop(1000, 2000)
            serverEngine = null
            Logger.i(LogTag.SYSTEM, "Jackett server stopped.")
        } catch (e: Exception) {
            Logger.e(LogTag.SYSTEM, "Error stopping Jackett server: ${e.message}")
        }
    }

    private fun isApiKeyValid(reqKey: String): Boolean =
        reqKey.isNotBlank() && reqKey.equals(apiKey, ignoreCase = true)

    private suspend fun handleTorznabRequest(call: io.ktor.server.application.ApplicationCall) {
        val reqApiKey = call.request.queryParameters["apikey"] ?: ""
        if (!isApiKeyValid(reqApiKey)) {
            val errorXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <error code="100" description="Incorrect user credentials"/>
            """.trimIndent()
            call.respondText(errorXml, ContentType.Text.Xml, HttpStatusCode.Unauthorized)
            return
        }

        val mode = call.request.queryParameters["t"] ?: "search"

        // Caps request (Used by Sonarr, Radarr, Prowlarr to test connectivity)
        if (mode.equals("caps", ignoreCase = true)) {
            val capsXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <caps>
                    <server version="0.24.2748" title="PocketNode Embedded Jackett" strapline="Android Native Torznab"/>
                    <searching>
                        <search available="yes" supportedParams="q"/>
                        <tv-search available="yes" supportedParams="q,season,ep"/>
                        <movie-search available="yes" supportedParams="q,imdbid"/>
                    </searching>
                    <categories>
                        <category id="2000" name="Movies"/>
                        <category id="5000" name="TV"/>
                    </categories>
                </caps>
            """.trimIndent()
            call.respondText(capsXml, ContentType.Text.Xml)
            return
        }

        // Search request
        val query = call.request.queryParameters["q"] ?: ""
        val results = searchAllIndexers(query)

        val xmlSb = java.lang.StringBuilder()
        xmlSb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        xmlSb.append("<rss version=\"2.0\" xmlns:torznab=\"http://torznab.com/schemas/2015/feed\">\n")
        xmlSb.append("  <channel>\n")
        xmlSb.append("    <title>PocketNode Jackett Indexer</title>\n")
        xmlSb.append("    <description>Universal Aggregated Torznab Stream</description>\n")

        for (item in results) {
            val escTitle = escapeXml(item.title)
            val escMagnet = escapeXml(item.magnetUrl)
            xmlSb.append("    <item>\n")
            xmlSb.append("      <title>$escTitle</title>\n")
            xmlSb.append("      <guid>${item.guid}</guid>\n")
            xmlSb.append("      <pubDate>${item.publishDate}</pubDate>\n")
            xmlSb.append("      <size>${item.sizeBytes}</size>\n")
            xmlSb.append("      <enclosure url=\"$escMagnet\" length=\"${item.sizeBytes}\" type=\"application/x-bittorrent\"/>\n")
            xmlSb.append("      <torznab:attr name=\"magneturl\" value=\"$escMagnet\"/>\n")
            xmlSb.append("      <torznab:attr name=\"seeders\" value=\"${item.seeders}\"/>\n")
            xmlSb.append("      <torznab:attr name=\"peers\" value=\"${item.leechers}\"/>\n")
            xmlSb.append("      <torznab:attr name=\"size\" value=\"${item.sizeBytes}\"/>\n")
            xmlSb.append("      <torznab:attr name=\"indexer\" value=\"${escapeXml(item.indexer)}\"/>\n")
            xmlSb.append("    </item>\n")
        }

        xmlSb.append("  </channel>\n")
        xmlSb.append("</rss>\n")

        call.respondText(xmlSb.toString(), ContentType.Text.Xml)
    }

    suspend fun searchAllIndexers(query: String): List<JackettReleaseItem> = coroutineScope {
        if (query.isBlank()) return@coroutineScope emptyList<JackettReleaseItem>()

        val tasks = mutableListOf<kotlinx.coroutines.Deferred<List<JackettReleaseItem>>>()

        // 1. The Pirate Bay via apibay.org
        if (TorrentTrackersManager.isIndexerEnabled("tpb")) {
            tasks.add(async(Dispatchers.IO) { searchPirateBay(query) })
        }

        // 2. YTS Movies via yts.mx
        if (TorrentTrackersManager.isIndexerEnabled("yts")) {
            tasks.add(async(Dispatchers.IO) { searchYts(query) })
        }

        // 3. Torrents.csv via torrents-csv.com
        if (TorrentTrackersManager.isIndexerEnabled("torrents-csv")) {
            tasks.add(async(Dispatchers.IO) { searchTorrentsCsv(query) })
        }

        // 4. EZTV TV Shows via eztv.re
        if (TorrentTrackersManager.isIndexerEnabled("eztv")) {
            tasks.add(async(Dispatchers.IO) { searchEztv(query) })
        }

        val allResults = tasks.awaitAll().flatten()
        allResults.sortedByDescending { it.seeders }
    }

    private suspend fun searchPirateBay(query: String): List<JackettReleaseItem> {
        val list = mutableListOf<JackettReleaseItem>()
        try {
            val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
            val url = "https://apibay.org/q.php?q=$encoded"
            val text = withTimeoutOrNull(4000L) {
                httpClient.get(url) {
                    headers.append(io.ktor.http.HttpHeaders.UserAgent, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                }.bodyAsText()
            } ?: return list

            if (!text.trimStart().startsWith("[")) return list
            val array = gson.fromJson(text, JsonArray::class.java)
            if (array != null) {
                for (i in 0 until array.size()) {
                    val item = array.get(i).asJsonObject
                    val infoHash = item.get("info_hash")?.asString ?: ""
                    val name = item.get("name")?.asString ?: ""
                    val size = item.get("size")?.asLong ?: 0L
                    val seeders = item.get("seeders")?.asInt ?: 0
                    val leechers = item.get("leechers")?.asInt ?: 0

                    if (infoHash.isNotBlank() && infoHash != "0000000000000000000000000000000000000000") {
                        val encName = URLEncoder.encode(name, StandardCharsets.UTF_8.name())
                        val rawMagnet = "magnet:?xt=urn:btih:$infoHash&dn=$encName"
                        val fullMagnet = TorrentTrackersManager.augmentMagnet(rawMagnet)

                        list.add(
                            JackettReleaseItem(
                                title = name,
                                guid = "tpb_$infoHash",
                                magnetUrl = fullMagnet,
                                sizeBytes = size,
                                seeders = seeders,
                                leechers = leechers,
                                indexer = "The Pirate Bay"
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Logger.w(LogTag.TORRENT, "Jackett apibay search: ${e.message}")
        }
        return list
    }

    private suspend fun searchTorrentsCsv(query: String): List<JackettReleaseItem> {
        val list = mutableListOf<JackettReleaseItem>()
        try {
            val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
            val url = "https://torrents-csv.com/service/search?q=$encoded"
            val text = withTimeoutOrNull(4000L) {
                httpClient.get(url) {
                    headers.append(io.ktor.http.HttpHeaders.UserAgent, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                }.bodyAsText()
            } ?: return list

            if (!text.trimStart().startsWith("{")) return list
            val json = gson.fromJson(text, JsonObject::class.java)

            if (json.has("torrents")) {
                val torrents = json.getAsJsonArray("torrents")
                for (i in 0 until minOf(torrents.size(), 20)) {
                    val item = torrents.get(i).asJsonObject
                    val infohash = item.get("infohash")?.asString ?: ""
                    val name = item.get("name")?.asString ?: query
                    val sizeBytes = item.get("size_bytes")?.asLong ?: 0L
                    val seeders = item.get("seeders")?.asInt ?: 0
                    val leechers = item.get("leechers")?.asInt ?: 0

                    if (infohash.isNotBlank()) {
                        val encName = URLEncoder.encode(name, StandardCharsets.UTF_8.name())
                        val rawMagnet = "magnet:?xt=urn:btih:$infohash&dn=$encName"
                        val fullMagnet = TorrentTrackersManager.augmentMagnet(rawMagnet)

                        list.add(
                            JackettReleaseItem(
                                title = name,
                                guid = "csv_$infohash",
                                magnetUrl = fullMagnet,
                                sizeBytes = sizeBytes,
                                seeders = seeders,
                                leechers = leechers,
                                indexer = "Torrents.csv"
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Logger.w(LogTag.TORRENT, "Jackett torrents-csv search: ${e.message}")
        }
        return list
    }

    private suspend fun searchYts(query: String): List<JackettReleaseItem> {
        val list = mutableListOf<JackettReleaseItem>()
        val ytsMirrors = listOf(
            "https://yts.bz",
            "https://web.yts.gg",
            "https://yts.lt",
            "https://yts.am",
            "https://yts.ag"
        )
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
        for (mirror in ytsMirrors) {
            try {
                val url = "$mirror/api/v2/list_movies.json?query_term=$encoded"
                val text = withTimeoutOrNull(3000L) {
                    httpClient.get(url) {
                        headers.append(io.ktor.http.HttpHeaders.UserAgent, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    }.bodyAsText()
                } ?: continue

                if (!text.trimStart().startsWith("{")) continue
                val json = try { gson.fromJson(text, JsonObject::class.java) } catch (e: Exception) { null }

                if (json != null && json.has("data") && json.getAsJsonObject("data").has("movies")) {
                    val movies = json.getAsJsonObject("data").getAsJsonArray("movies")
                    if (movies != null && movies.size() > 0) {
                        for (m in 0 until movies.size()) {
                            val movie = movies.get(m).asJsonObject
                            val title = movie.get("title").asString
                            val year = movie.get("year")?.asInt ?: 0
                            val torrents = movie.getAsJsonArray("torrents") ?: continue

                            for (t in 0 until torrents.size()) {
                                val torrent = torrents.get(t).asJsonObject
                                val hash = torrent.get("hash").asString
                                val quality = torrent.get("quality").asString
                                val sizeBytes = torrent.get("size_bytes")?.asLong ?: 0L
                                val seeders = torrent.get("seeds")?.asInt ?: 0
                                val peers = torrent.get("peers")?.asInt ?: 0

                                val releaseTitle = "$title ($year) [$quality] [YTS]"
                                val encTitle = URLEncoder.encode(releaseTitle, StandardCharsets.UTF_8.name())
                                val rawMagnet = "magnet:?xt=urn:btih:$hash&dn=$encTitle"
                                val fullMagnet = TorrentTrackersManager.augmentMagnet(rawMagnet)

                                list.add(
                                    JackettReleaseItem(
                                        title = releaseTitle,
                                        guid = "yts_$hash",
                                        magnetUrl = fullMagnet,
                                        sizeBytes = sizeBytes,
                                        seeders = seeders,
                                        leechers = peers,
                                        indexer = "YTS"
                                    )
                                )
                            }
                        }
                        if (list.isNotEmpty()) return list
                    }
                }
            } catch (e: Exception) {
                // Try next mirror
            }
        }

        // Fallback: If direct YTS mirrors are blocked by regional ISP, extract YIFY/YTS from apibay index
        if (list.isEmpty()) {
            try {
                val apibayUrl = "https://apibay.org/q.php?q=$encoded"
                val text = withTimeoutOrNull(3500L) {
                    httpClient.get(apibayUrl) {
                        headers.append(io.ktor.http.HttpHeaders.UserAgent, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    }.bodyAsText()
                }
                if (text != null && text.trimStart().startsWith("[")) {
                    val array = gson.fromJson(text, JsonArray::class.java)
                    if (array != null) {
                        for (i in 0 until minOf(array.size(), 20)) {
                            val item = array.get(i).asJsonObject
                            val name = item.get("name")?.asString ?: ""
                            if (name.contains("YIFY", ignoreCase = true) || name.contains("YTS", ignoreCase = true)) {
                                val infoHash = item.get("info_hash")?.asString ?: ""
                                val size = item.get("size")?.asLong ?: 0L
                                val seeders = item.get("seeders")?.asInt ?: 0
                                val leechers = item.get("leechers")?.asInt ?: 0
                                if (infoHash.isNotBlank() && infoHash != "0000000000000000000000000000000000000000") {
                                    val encName = URLEncoder.encode(name, StandardCharsets.UTF_8.name())
                                    val rawMagnet = "magnet:?xt=urn:btih:$infoHash&dn=$encName"
                                    val fullMagnet = TorrentTrackersManager.augmentMagnet(rawMagnet)
                                    list.add(
                                        JackettReleaseItem(
                                            title = name,
                                            guid = "yts_$infoHash",
                                            magnetUrl = fullMagnet,
                                            sizeBytes = size,
                                            seeders = seeders,
                                            leechers = leechers,
                                            indexer = "YTS (Mirror)"
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Logger.w(LogTag.TORRENT, "Jackett YTS fallback search: ${e.message}")
            }
        }

        return list
    }

    private suspend fun searchEztv(query: String): List<JackettReleaseItem> {
        val list = mutableListOf<JackettReleaseItem>()
        try {
            val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
            val url = "https://eztv.re/api/get-torrents?limit=15&query=$encoded"
            val text = withTimeoutOrNull(4000L) {
                httpClient.get(url) {
                    headers.append(io.ktor.http.HttpHeaders.UserAgent, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                }.bodyAsText()
            } ?: return list

            if (!text.trimStart().startsWith("{")) return list
            val json = gson.fromJson(text, JsonObject::class.java)

            if (json.has("torrents")) {
                val torrents = json.getAsJsonArray("torrents")
                for (i in 0 until torrents.size()) {
                    val t = torrents.get(i).asJsonObject
                    val magnetUrl = t.get("magnet_url")?.asString ?: ""
                    val title = t.get("title")?.asString ?: ""
                    val sizeBytes = t.get("size_bytes")?.asLong ?: 0L
                    val seeders = t.get("seeds")?.asInt ?: 0
                    val hash = t.get("hash")?.asString ?: UUID.randomUUID().toString()

                    if (magnetUrl.isNotBlank()) {
                        val fullMagnet = TorrentTrackersManager.augmentMagnet(magnetUrl)
                        list.add(
                            JackettReleaseItem(
                                title = title,
                                guid = "eztv_$hash",
                                magnetUrl = fullMagnet,
                                sizeBytes = sizeBytes,
                                seeders = seeders,
                                leechers = 0,
                                indexer = "EZTV"
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Logger.w(LogTag.TORRENT, "Jackett EZTV search: ${e.message}")
        }
        return list
    }

    private fun escapeXml(str: String): String =
        str.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")

    private fun generateDashboardHtml(): String {
        val currentKey = getApiKey()
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Jackett - PocketNode Embedded</title>
                <style>
                    :root {
                        --bg: #0b0c0e;
                        --card: #181a20;
                        --border: #2a2e3a;
                        --text: #e8ebf0;
                        --muted: #8a92a3;
                        --orange: #ff5722;
                        --green: #00c853;
                        --blue: #00b0ff;
                    }
                    * { box-sizing: border-box; margin: 0; padding: 0; }
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, monospace;
                        background-color: var(--bg);
                        color: var(--text);
                        padding: 24px;
                    }
                    .container { max-width: 1000px; margin: 0 auto; }
                    .header {
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                        border-bottom: 1px solid var(--border);
                        padding-bottom: 16px;
                        margin-bottom: 24px;
                    }
                    .header h1 { font-size: 20px; letter-spacing: 1px; }
                    .badge {
                        background: rgba(0, 200, 83, 0.15);
                        color: var(--green);
                        border: 1px solid var(--green);
                        padding: 4px 10px;
                        font-size: 11px;
                        border-radius: 4px;
                        font-weight: bold;
                    }
                    .card {
                        background: var(--card);
                        border: 1px solid var(--border);
                        border-radius: 8px;
                        padding: 20px;
                        margin-bottom: 20px;
                    }
                    .card-title {
                        font-size: 13px;
                        color: var(--muted);
                        text-transform: uppercase;
                        margin-bottom: 12px;
                        font-weight: bold;
                    }
                    .key-box {
                        display: flex;
                        align-items: center;
                        gap: 10px;
                        background: #111317;
                        border: 1px solid var(--border);
                        padding: 10px 14px;
                        border-radius: 6px;
                        font-family: monospace;
                    }
                    .key-box input {
                        background: transparent;
                        border: none;
                        color: #fff;
                        font-family: monospace;
                        font-size: 14px;
                        width: 100%;
                        outline: none;
                    }
                    button {
                        background: var(--orange);
                        color: #000;
                        border: none;
                        padding: 8px 16px;
                        font-weight: bold;
                        border-radius: 4px;
                        cursor: pointer;
                        font-family: monospace;
                    }
                    button:hover { opacity: 0.9; }
                    .search-bar { display: flex; gap: 8px; margin-bottom: 16px; }
                    .search-bar input {
                        flex: 1;
                        background: #111317;
                        border: 1px solid var(--border);
                        padding: 10px 14px;
                        color: #fff;
                        border-radius: 4px;
                        font-size: 14px;
                    }
                    table { width: 100%; border-collapse: collapse; margin-top: 10px; font-size: 13px; }
                    th, td { text-align: left; padding: 10px; border-bottom: 1px solid var(--border); }
                    th { color: var(--muted); font-size: 11px; text-transform: uppercase; }
                    .seeds { color: var(--green); font-weight: bold; }
                    .leech { color: #ff9100; }
                    .copy-btn { background: #2a2e3a; color: #fff; padding: 4px 8px; font-size: 11px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <div>
                            <h1>JACKETT // POCKETNODE ENGINE</h1>
                            <div style="font-size: 12px; color: var(--muted); margin-top: 4px;">Standalone Android Torznab Indexer (Port 9117)</div>
                        </div>
                        <div class="badge">● DAEMON ONLINE</div>
                    </div>

                    <div class="card">
                        <div class="card-title">// TORZNAB API KEY</div>
                        <div class="key-box">
                            <input id="apiKeyInput" type="text" readonly value="$currentKey">
                            <button onclick="copyKey()">COPY KEY</button>
                        </div>
                        <div style="font-size: 11px; color: var(--muted); margin-top: 10px;">
                            Feed URL: <code style="color: var(--blue);">http://&lt;phone-ip&gt;:9117/api/v2.0/indexers/all/results/torznab/api?apikey=$currentKey&t=search</code>
                        </div>
                    </div>

                    <div class="card">
                        <div class="card-title">// SEARCH RELEASES</div>
                        <div class="search-bar">
                            <input id="searchInput" type="text" placeholder="Search movies, shows, software (e.g. Inception, Avatar)..." onkeypress="if(event.key==='Enter') doSearch()">
                            <button onclick="doSearch()">SEARCH TRACKERS</button>
                        </div>
                        <div id="statusMsg" style="font-size: 12px; color: var(--muted); margin-bottom: 10px;"></div>
                        <table id="resultsTable" style="display: none;">
                            <thead>
                                <tr>
                                    <th>Release Title</th>
                                    <th>Indexer</th>
                                    <th>Size</th>
                                    <th>Seeds</th>
                                    <th>Action</th>
                                </tr>
                            </thead>
                            <tbody id="resultsBody"></tbody>
                        </table>
                    </div>
                </div>

                <script>
                    function copyKey() {
                        const copyText = document.getElementById("apiKeyInput");
                        copyText.select();
                        navigator.clipboard.writeText(copyText.value);
                        alert("API Key copied: " + copyText.value);
                    }

                    async function doSearch() {
                        const q = document.getElementById("searchInput").value.trim();
                        if (!q) return;
                        const msg = document.getElementById("statusMsg");
                        msg.innerText = "Querying indexers (Pirate Bay, YTS, EZTV)...";
                        const table = document.getElementById("resultsTable");
                        const tbody = document.getElementById("resultsBody");
                        tbody.innerHTML = "";

                        try {
                            const res = await fetch("/api/search?q=" + encodeURIComponent(q));
                            const data = await res.json();
                            msg.innerText = "Found " + data.length + " releases.";
                            if (data.length > 0) {
                                table.style.display = "table";
                                data.forEach(item => {
                                    const tr = document.createElement("tr");
                                    const sizeMb = (item.sizeBytes / (1024 * 1024)).toFixed(1) + " MB";
                                    tr.innerHTML = "<td><b>" + item.title + "</b></td>" +
                                        "<td><span style='color: var(--orange);'>" + item.indexer + "</span></td>" +
                                        "<td>" + sizeMb + "</td>" +
                                        "<td class='seeds'>▲ " + item.seeders + "</td>" +
                                        "<td><button class='copy-btn' onclick='downloadMagnet(\"" + item.magnetUrl.replace(/'/g, "\\'") + "\")'>⬇ DOWNLOAD</button></td>";
                                    tbody.appendChild(tr);
                                });
                            } else {
                                table.style.display = "none";
                            }
                        } catch (e) {
                            msg.innerText = "Search error: " + e;
                        }
                    }

                    async function downloadMagnet(magnet) {
                        try {
                            const form = new URLSearchParams();
                            form.append("magnet", magnet);
                            await fetch("/api/download", { method: "POST", body: form });
                            alert("Torrent added directly to PocketNode download engine!");
                        } catch(e) {
                            alert("Error: " + e);
                        }
                    }
                </script>
            </body>
            </html>
        """.trimIndent()
    }
}
