package com.remotemedia.download

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.remotemedia.core.LogTag
import com.remotemedia.core.Logger
import com.remotemedia.core.StorageManager
import com.remotemedia.services.TelegramLocalServer
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentLength
import io.ktor.http.contentType
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

data class TelegramSearchResult(
    val title: String,
    val size: String,
    val seeds: Int,
    val source: String,
    val magnet: String
)

object TelegramBotEngine {

    private var botToken: String = ""
    private var adminChatId: String = ""
    private var apiEndpoint: String = "https://api.telegram.org"
    private var appPrefs: SharedPreferences? = null

    private val client = HttpClient(CIO)
    private val gson = Gson()
    private val botJob = SupervisorJob()
    private val botScope = CoroutineScope(Dispatchers.IO + botJob)
    private var isPolling = false
    private var lastUpdateId = 0L

    // In-memory cache for search results per chat ID
    private val searchResultsCache = ConcurrentHashMap<Long, List<TelegramSearchResult>>()
    // In-memory cache for search query text per chat ID
    private val searchQueriesCache = ConcurrentHashMap<Long, String>()
    // Track message IDs per chat for /clear command and auto-deletion
    private val chatMessageHistory = ConcurrentHashMap<Long, CopyOnWriteArrayList<Long>>()
    // Pending torrent fallback queries awaiting user confirmation (chatId -> cleanTitle)
    private val pendingTorrentLookups = ConcurrentHashMap<Long, String>()
    // Active Telegram download coroutine jobs (trackingId -> Job)
    private val activeTelegramJobs = ConcurrentHashMap<String, kotlinx.coroutines.Job>()

    fun getStreamBridgeUrl(): String = appPrefs?.getString("telegram_stream_bridge_url", "")?.trim()?.removeSuffix("/") ?: ""

    fun pauseTelegramDownload(trackingId: String) {
        val job = activeTelegramJobs.remove(trackingId)
        job?.cancel()
        Logger.i(LogTag.TELEGRAM, "Paused active Telegram download task: $trackingId")
    }

    fun resumeTelegramDownload(trackingId: String) {
        val item = TorrentEngine.downloads.value.find { it.id == trackingId }
        if (item != null) {
            val cid = adminChatId.toLongOrNull() ?: 0L
            val url = item.remoteUrl
            val fileId = item.fileId
            if (url.isNotBlank()) {
                downloadWebVideoUrl(
                    chatId = cid,
                    rawUrl = url,
                    customFileName = item.name,
                    reuseStatusMsgId = 0L,
                    existingTrackingId = trackingId,
                    fileId = fileId
                )
            } else if (fileId.isNotBlank()) {
                downloadTelegramFile(fileId, item.name, item.totalBytes)
            }
        }
    }

    fun start(context: Context): Boolean {
        val prefs = context.getSharedPreferences("remote_media_prefs", Context.MODE_PRIVATE)
        appPrefs = prefs
        botToken = prefs.getString("telegram_bot_token", "") ?: ""
        val savedChatId = prefs.getString("telegram_chat_id", "")?.trim() ?: ""
        adminChatId = if (savedChatId.isBlank() || savedChatId == "948281948") "835200448" else savedChatId
        val customEndpoint = prefs.getString("telegram_api_endpoint", "")?.trim() ?: ""
        val isLocalServerEnabled = prefs.getBoolean("tg_local_server_enabled", false)

        apiEndpoint = when {
            isLocalServerEnabled && TelegramLocalServer.isRunning.value -> TelegramLocalServer.getLocalEndpoint()
            customEndpoint.isNotBlank() -> customEndpoint.removeSuffix("/")
            else -> "https://api.telegram.org"
        }

        if (botToken.isBlank()) {
            Logger.w(LogTag.TELEGRAM, "Telegram Bot Token is not configured. Set it in Settings.")
            return false
        }

        TelegramMediaCatalog.init(context)

        // Initialize native TDLib MTProto engine if API ID & Hash are configured
        val numApiId = prefs.getString("telegram_api_id", "")?.trim()?.toIntOrNull() ?: 0
        val savedApiHash = prefs.getString("telegram_api_hash", "")?.trim() ?: ""
        if (numApiId > 0 && savedApiHash.isNotBlank() && botToken.isNotBlank()) {
            TdlibDownloadEngine.init(context, numApiId, savedApiHash, botToken)
        }

        if (isPolling) return true
        isPolling = true

        Logger.i(LogTag.TELEGRAM, "Telegram Bot Engine started. Long-polling active (Admin: $adminChatId, Endpoint: $apiEndpoint)...")
        startLongPolling()
        return true
    }

    fun stop() {
        isPolling = false
        TdlibDownloadEngine.stop()
        Logger.i(LogTag.TELEGRAM, "Telegram Bot Engine stopped.")
    }

    suspend fun getDownloadUrlForFile(fileId: String): String? {
        if (botToken.isBlank()) return null
        return try {
            val getFileUrl = "$apiEndpoint/bot$botToken/getFile?file_id=$fileId"
            val resp = client.get(getFileUrl).bodyAsText()
            val json = gson.fromJson(resp, JsonObject::class.java)
            if (json.has("ok") && json.get("ok").asBoolean) {
                val remotePath = json.getAsJsonObject("result").get("file_path").asString
                "$apiEndpoint/file/bot$botToken/$remotePath"
            } else null
        } catch (e: Exception) {
            Logger.w(LogTag.TELEGRAM, "Error getting download URL for file $fileId: ${e.message}")
            null
        }
    }

    /**
     * Allows independent direct download of Telegram files from the client app to the home server.
     */
    fun downloadTelegramFile(fileId: String, fileName: String, fileSize: Long = 0L): Boolean {
        botScope.launch {
            val cid = adminChatId.toLongOrNull() ?: 0L
            val cleanName = fileName.ifBlank { "telegram_file_${System.currentTimeMillis()}.mp4" }
            Logger.i(LogTag.TELEGRAM, "App requested server download for Telegram file: '$cleanName' ($fileId)")

            // 1. Try resolving direct download URL
            val directUrl = getDownloadUrlForFile(fileId)
            if (directUrl != null) {
                downloadWebVideoUrl(cid, directUrl, cleanName, fileId = fileId)
                return@launch
            }

            // 1b. Try Stream Bridge if configured
            val streamBridge = getStreamBridgeUrl()
            if (streamBridge.isNotBlank()) {
                val bridgeUrl = tryResolveFromStreamBridge(streamBridge, fileId, botToken, cleanName)
                if (bridgeUrl != null) {
                    downloadWebVideoUrl(cid, bridgeUrl, cleanName, fileId = fileId)
                    return@launch
                }
            }

            // 2. Try TDLib native MTProto engine
            if (TdlibDownloadEngine.isReady()) {
                val started = TdlibDownloadEngine.startDownload(
                    remoteFileId = fileId,
                    fileName = cleanName,
                    fileSize = fileSize,
                    chatId = cid,
                    statusMsgId = 0L,
                    tgTrackingId = "tg_${System.currentTimeMillis()}"
                )
                if (started) {
                    Logger.i(LogTag.TELEGRAM, "Started TDLib download for: $cleanName")
                    return@launch
                }
            }

            Logger.w(LogTag.TELEGRAM, "Unable to resolve download for Telegram file: $fileId")
        }
        return true
    }

    private fun startLongPolling() {
        botScope.launch {
            // Delete webhook to ensure getUpdates long-polling conflict (409) is resolved
            try {
                client.get("$apiEndpoint/bot$botToken/deleteWebhook")
            } catch (e: Exception) {
                Logger.w(LogTag.TELEGRAM, "deleteWebhook warning: ${e.message}")
            }

            Logger.i(LogTag.TELEGRAM, "Long-polling loop active on $apiEndpoint...")

            while (isPolling) {
                try {
                    val url = "$apiEndpoint/bot$botToken/getUpdates"
                    val resp = client.get(url) {
                        if (lastUpdateId > 0) {
                            parameter("offset", lastUpdateId + 1)
                        }
                        parameter("timeout", 20)
                    }

                    val responseText = resp.bodyAsText()
                    val json = gson.fromJson(responseText, JsonObject::class.java)
                    if (json.has("ok") && json.get("ok").asBoolean) {
                        val updates = json.getAsJsonArray("result")
                        for (i in 0 until updates.size()) {
                            val update = updates.get(i).asJsonObject
                            val updateId = update.get("update_id").asLong
                            lastUpdateId = updateId

                            if (update.has("message")) {
                                handleMessage(update.getAsJsonObject("message"))
                            } else if (update.has("callback_query")) {
                                handleCallbackQuery(update.getAsJsonObject("callback_query"))
                            }
                        }
                    } else {
                        Logger.w(LogTag.TELEGRAM, "getUpdates not ok: ${responseText.take(120)}")
                        delay(2000)
                    }
                } catch (e: Exception) {
                    Logger.e(LogTag.TELEGRAM, "Polling error: ${e.javaClass.simpleName} - ${e.message}")
                    delay(3000)
                }
            }
            Logger.i(LogTag.TELEGRAM, "Long-polling loop terminated.")
        }
    }

    private suspend fun handleMessage(message: JsonObject) {
        if (!message.has("chat")) return
        val chatId = message.getAsJsonObject("chat").get("id").asLong
        val messageId = if (message.has("message_id")) message.get("message_id").asLong else 0L

        if (messageId > 0) {
            recordMessageId(chatId, messageId)
        }

        // 1. Guard against messages from any bot
        if (message.has("from")) {
            val from = message.getAsJsonObject("from")
            if (from.has("is_bot") && from.get("is_bot").asBoolean) {
                Logger.i(LogTag.TELEGRAM, "Ignored message from bot (${from.get("id")?.asString})")
                return
            }
        }

        // 2. Guard against unauthorized users, or auto-pair if unset or placeholder
        val senderId = if (message.has("from")) message.getAsJsonObject("from").get("id")?.asString ?: "" else ""
        val chatIdStr = chatId.toString()

        if (adminChatId.isBlank() || adminChatId == "948281948") {
            val bindId = if (senderId.isNotBlank()) senderId else chatIdStr
            adminChatId = bindId
            appPrefs?.edit()?.putString("telegram_chat_id", bindId)?.apply()
            Logger.i(LogTag.TELEGRAM, "Auto-paired Telegram Admin with Chat ID: $bindId")
        } else if (chatIdStr != adminChatId && senderId != adminChatId) {
            Logger.w(LogTag.TELEGRAM, "Ignored message from unauthorized chat: $chatId / sender: $senderId (admin: $adminChatId)")
            return
        }

        val caption = if (message.has("caption")) message.get("caption").asString.trim() else ""
        val text = if (message.has("text")) message.get("text").asString.trim() else caption

        // 3. Handle forwarded/shared Telegram media files (documents, videos, audio)
        val hasMedia = message.has("document") || message.has("video") || message.has("audio")
        if (hasMedia) {
            val fileObj = when {
                message.has("document") -> message.getAsJsonObject("document")
                message.has("video") -> message.getAsJsonObject("video")
                message.has("audio") -> message.getAsJsonObject("audio")
                else -> null
            }
            if (fileObj != null) {
                handleIncomingMedia(chatId, messageId, fileObj, caption, message)
                return
            }
        }

        if (text.isBlank()) return

        // 3. Guard against bot status/response/echo message loops
        val isBotOutput = text.startsWith("🔍") ||
                text.startsWith("❌") ||
                text.startsWith("✅") ||
                text.startsWith("📥") ||
                text.startsWith("🎉") ||
                text.startsWith("🟢") ||
                text.startsWith("📂") ||
                text.startsWith("🎬") ||
                text.startsWith("⚠️") ||
                text.startsWith("⏱️") ||
                text.startsWith("📊") ||
                text.startsWith("•") ||
                text.startsWith("ℹ️") ||
                text.startsWith("🔴") ||
                text.startsWith("🟡") ||
                text.startsWith("🧹") ||
                text.contains("auto-deletes in", ignoreCase = true) ||
                text.contains("PocketNode Daemon", ignoreCase = true)

        if (isBotOutput) {
            Logger.i(LogTag.TELEGRAM, "Ignored automated output/status message: '$text'")
            return
        }

        Logger.i(LogTag.TELEGRAM, "Received Telegram command: '$text' from chat $chatId")

        // 4. Direct Web Video / Direct URL Download: "link ::: <url>"
        if (text.lowercase().startsWith("link") && text.contains(":::")) {
            val urlPart = text.split(":::", limit = 2)[1].trim()
            if (urlPart.contains("magnet:?")) {
                val magnet = extractMagnetOrUrl(urlPart) ?: urlPart
                startTorrentWithTelegramProgress(chatId, magnet)
            } else if (urlPart.isNotBlank()) {
                downloadWebVideoUrl(chatId, urlPart)
            } else {
                sendMessage(chatId, "❌ No URL provided.\nUsage: `link ::: https://site.com/video.mp4`", parseMode = "Markdown", autoDeleteSeconds = 120)
            }
            return
        }

        // 5. Direct Magnet Link Detection
        val magnetUrl = extractMagnetOrUrl(text)
        if (magnetUrl != null) {
            Logger.i(LogTag.TELEGRAM, "Direct Magnet/Torrent link detected in message: '$text'")
            startTorrentWithTelegramProgress(chatId, magnetUrl)
            return
        }

        // 5a. Direct Web Link / Video Stream URL Detection (e.g. shared from browser, YouTube, or direct MP4/MKV)
        val trimmedText = text.trim()
        val urlRegex = Regex("""https?://[^\s]+""")
        val urlMatch = urlRegex.find(trimmedText)?.value
        if (urlMatch != null && !trimmedText.startsWith("/")) {
            if (urlMatch.contains("magnet:?")) {
                val extracted = extractMagnetOrUrl(urlMatch) ?: urlMatch
                startTorrentWithTelegramProgress(chatId, extracted)
                return
            } else if (urlMatch.contains(".torrent", ignoreCase = true)) {
                startTorrentWithTelegramProgress(chatId, urlMatch)
                return
            } else {
                downloadWebVideoUrl(chatId, urlMatch)
                return
            }
        }

        // 5b. Handle User Yes/No confirmation for Torrent Fallback Lookup
        val pendingLookup = pendingTorrentLookups[chatId]
        if (pendingLookup != null) {
            val lower = text.lowercase().trim()
            if (lower == "yes" || lower == "y" || lower == "ok" || lower.startsWith("search")) {
                pendingTorrentLookups.remove(chatId)
                sendMessage(chatId, "🔍 Initiating torrent swarm search for **$pendingLookup**...", parseMode = "Markdown")
                performSearchAndPresentChoices(chatId, pendingLookup)
                return
            } else if (lower == "no" || lower == "n" || lower == "cancel" || lower == "c") {
                pendingTorrentLookups.remove(chatId)
                sendMessage(chatId, "❌ Torrent search cancelled.")
                return
            }
        }

        // 6. Active Search Choice Replies (Number or Cancel)
        val activeResults = searchResultsCache[chatId]
        if (activeResults != null && activeResults.isNotEmpty()) {
            val num = text.toIntOrNull()
            if (num != null && num in 1..activeResults.size) {
                val selectedResult = activeResults[num - 1]
                searchResultsCache.remove(chatId)
                searchQueriesCache.remove(chatId)
                startTorrentWithTelegramProgress(
                    chatId = chatId,
                    magnetUri = selectedResult.magnet,
                    title = selectedResult.title,
                    expectedSize = selectedResult.size
                )
                return
            } else if (text.equals("cancel", ignoreCase = true) || text.equals("c", ignoreCase = true)) {
                searchResultsCache.remove(chatId)
                searchQueriesCache.remove(chatId)
                sendMessage(chatId, "❌ Search cancelled.", autoDeleteSeconds = 30)
                return
            }
        }

        when {
            text.startsWith("/start") || text.startsWith("/help") -> {
                val helpMsg = "🎬 *Remote Media Downloader Bot*\n\n" +
                        "Available Commands:\n" +
                        "• `Movie Name ::: search` — Search all resolutions/releases\n" +
                        "• `Movie Name ::: 1080p` or `Movie Name ::: 4k` — Search specific resolution\n" +
                        "• `link ::: https://site.com/video.mp4` — Download web video / direct link\n" +
                        "• `/search Movie Name` — Fast release search\n" +
                        "• Send any Magnet Link directly to start download\n" +
                        "• `/status` — Check active server status & IP\n" +
                        "• `/list` — List movies in server library\n" +
                        "• `/clear` or `/clearchat` — Clear entire chat history"
                sendMessage(chatId, helpMsg, parseMode = "Markdown", autoDeleteSeconds = 600)
            }

            text.equals("/status", ignoreCase = true) || text.equals("status", ignoreCase = true) -> {
                val filesCount = StorageManager.listFiles(StorageManager.getMoviesDir()).size
                val statusMsg = "🟢 *Remote Media Server Active*\n" +
                        "📁 Movies in library: $filesCount\n" +
                        "🌐 Web Dashboard: `http://localhost:8080`\n" +
                        "🎬 Jellyfin API: `http://localhost:8096`"
                sendMessage(chatId, statusMsg, parseMode = "Markdown", autoDeleteSeconds = 600)
            }

            text.equals("/list", ignoreCase = true) || text.equals("list", ignoreCase = true) -> {
                val files = StorageManager.listFiles(StorageManager.getMoviesDir())
                val listText = if (files.isEmpty()) {
                    "📂 Media library is currently empty."
                } else {
                    "📂 *Media Files in Server:*\n" + files.joinToString("\n") { "• `${it.name}`" }
                }
                sendMessage(chatId, listText, parseMode = "Markdown", autoDeleteSeconds = 600)
            }

            text.equals("/clear", ignoreCase = true) || text.equals("/clearchat", ignoreCase = true) -> {
                clearEntireChat(chatId, messageId)
            }

            text.startsWith("/pc") -> {
                val ip = text.removePrefix("/pc").trim()
                if (ip.isNotBlank()) {
                    appPrefs?.edit()?.putString("pc_host", ip)?.apply()
                    sendMessage(chatId, "🖥️ *PC Downloader Linked!*\n\nIP: `$ip` (port 8765)\nWeb links from YouTube/sites will now auto-route to PC (yt-dlp)!", parseMode = "Markdown", autoDeleteSeconds = 60)
                } else {
                    val current = appPrefs?.getString("pc_host", "Not set") ?: "Not set"
                    sendMessage(chatId, "🖥️ *PC Downloader Status*\n\nCurrent PC IP: `$current`\n\nTo link your PC, send: `/pc <IP>`\nExample: `/pc 192.168.1.100`", parseMode = "Markdown", autoDeleteSeconds = 60)
                }
            }

            text.startsWith("/download") || text.startsWith("/search") || text.contains(":::") -> {
                val (rawQuery, filterPart) = if (text.contains(":::")) {
                    val parts = text.split(":::")
                    val q = parts[0].trim()
                    val f = parts.getOrNull(1)?.trim() ?: ""
                    Pair(q, f)
                } else {
                    val q = text.removePrefix("/download").removePrefix("/search").trim()
                    Pair(q, "")
                }

                if (rawQuery.isNotBlank()) {
                    val searchQuery = if (filterPart.isNotBlank() && !filterPart.equals("search", ignoreCase = true)) {
                        "$rawQuery $filterPart"
                    } else {
                        rawQuery
                    }
                    performSearchAndPresentChoices(chatId, searchQuery)
                }
            }

            // Fallback: user types movie name directly
            !text.startsWith("/") && text.length > 2 -> {
                performSearchAndPresentChoices(chatId, text)
            }
        }
    }

    private fun extractCleanMovieTitle(fileName: String, caption: String = ""): String {
        var base = fileName.substringBeforeLast('.')
        // If fileName is generic or empty, try the first line of caption
        if (base.startsWith("telegram_", ignoreCase = true) || base.isBlank()) {
            if (caption.isNotBlank()) {
                val candidate = caption.lines().firstOrNull { it.isNotBlank() } ?: ""
                if (candidate.isNotBlank()) {
                    base = candidate.substringBefore("(").substringBeforeLast('.')
                }
            }
        }

        // Clean common release tags, resolutions, audio codecs, group names
        val cleaned = base
            .replace(Regex("""(?i)\b(1080p|720p|2160p|4k|bluray|brrip|bdrip|web-dl|webrip|web|dvdrip|x264|x265|hevc|h264|h265|aac|dts|dd5\.1|ac3|msubs|subs|multi|hindi|tamil|telugu|malayalam|kannada|dual audio|english|yts|yify|maya|mk)\b"""), " ")
            .replace(Regex("""[.\-_()\[\]{}]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

        return if (cleaned.length >= 2) cleaned else fileName.substringBeforeLast('.').trim()
    }

    private fun handleIncomingMedia(
        chatId: Long,
        messageId: Long,
        fileObj: JsonObject,
        caption: String = "",
        message: JsonObject? = null
    ) {
        val fileId = fileObj.get("file_id").asString
        val rawName = fileObj.get("file_name")?.asString?.trim() ?: ""
        val mimeType = fileObj.get("mime_type")?.asString ?: ""
        val fileSize = fileObj.get("file_size")?.asLong ?: 0L

        val ext = when {
            rawName.contains(".") -> "." + rawName.substringAfterLast(".")
            mimeType.contains("video/mp4") -> ".mp4"
            mimeType.contains("matroska") -> ".mkv"
            mimeType.contains("avi") -> ".avi"
            mimeType.contains("audio/mpeg") -> ".mp3"
            else -> ".mp4"
        }

        val fileName = if (rawName.isNotBlank()) rawName else "telegram_${System.currentTimeMillis()}$ext"
        val tgTrackingId = "tg_${System.currentTimeMillis()}"

        val cleanTitle = extractCleanMovieTitle(fileName, caption)

        // Detect forward / saved origin
        val (source, channelName) = when {
            message != null && message.has("forward_from_chat") -> {
                val chat = message.getAsJsonObject("forward_from_chat")
                val title = chat.get("title")?.asString ?: chat.get("username")?.asString ?: "Channel"
                "Channel Forward" to title
            }
            message != null && message.has("forward_from") -> {
                val user = message.getAsJsonObject("forward_from")
                val name = user.get("first_name")?.asString ?: "User"
                "Saved Messages" to name
            }
            message != null && message.has("forward_sender_name") -> {
                "Forwarded" to message.get("forward_sender_name").asString
            }
            message != null && message.has("forward_origin") -> {
                val origin = message.getAsJsonObject("forward_origin")
                val originType = origin.get("type")?.asString ?: ""
                val originTitle = origin.getAsJsonObject("chat")?.get("title")?.asString
                    ?: origin.getAsJsonObject("sender_user")?.get("first_name")?.asString
                    ?: origin.get("sender_user_name")?.asString
                    ?: "Forwarded"
                if (originType == "channel") "Channel Forward" to originTitle else "Saved Messages" to originTitle
            }
            else -> "Saved Messages" to "Direct Chat"
        }

        val year = Regex("""\b(19\d\d|20\d\d)\b""").find(fileName)?.value ?: ""
        val quality = when {
            fileName.contains("2160p", ignoreCase = true) || fileName.contains("4k", ignoreCase = true) -> "4K UHD"
            fileName.contains("1080p", ignoreCase = true) -> "1080p"
            fileName.contains("720p", ignoreCase = true) -> "720p"
            fileName.contains("remux", ignoreCase = true) -> "Remux"
            fileName.contains("bluray", ignoreCase = true) -> "BluRay"
            else -> "1080p"
        }

        TelegramMediaCatalog.recordMedia(
            fileId = fileId,
            fileName = fileName,
            cleanTitle = cleanTitle,
            year = year,
            quality = quality,
            fileSize = fileSize,
            sizeFormatted = formatSize(fileSize),
            mimeType = mimeType,
            source = source,
            channelName = channelName
        )

        Logger.i(LogTag.TELEGRAM, "Attempting direct Telegram media download: '$fileName' (${formatSize(fileSize)})")
        TorrentEngine.recordTelegramDownload(fileName, formatSize(fileSize), tgTrackingId)

        botScope.launch {
            val safeName = fileName.replace("*", "").replace("`", "")
            val statusMsgId = sendMessage(
                chatId = chatId,
                text = "📥 *File detected in Telegram!*\n📄 `$safeName`\n💾 Size: ${formatSize(fileSize)}\n⏳ Resolving download stream...",
                parseMode = "Markdown",
                autoDeleteSeconds = 0
            )

            var directDownloadUrl: String? = null
            val isCloudEndpoint = apiEndpoint.contains("api.telegram.org")

            // 1. Try standard getFile if file is under 20MB or on a local 2GB bot API server
            if (!isCloudEndpoint || fileSize <= 20 * 1024 * 1024L) {
                try {
                    val getFileUrl = "$apiEndpoint/bot$botToken/getFile?file_id=$fileId"
                    val fileMetaResp = client.get(getFileUrl).bodyAsText()
                    val metaJson = gson.fromJson(fileMetaResp, JsonObject::class.java)
                    if (metaJson.has("ok") && metaJson.get("ok").asBoolean) {
                        val remotePath = metaJson.getAsJsonObject("result").get("file_path").asString
                        directDownloadUrl = "$apiEndpoint/file/bot$botToken/$remotePath"
                    }
                } catch (e: Exception) {
                    Logger.w(LogTag.TELEGRAM, "Standard getFile error: ${e.message}")
                }
            }

            // 1a. If native TDLib MTProto engine is active, download on-device up to 2GB with no PC or cloud limits!
            if (directDownloadUrl == null && TdlibDownloadEngine.isReady()) {
                Logger.i(LogTag.TELEGRAM, "Delegating '$fileName' to native on-device TDLib MTProto engine...")
                val started = TdlibDownloadEngine.startDownload(
                    remoteFileId = fileId,
                    fileName = fileName,
                    fileSize = fileSize,
                    chatId = chatId,
                    statusMsgId = statusMsgId,
                    tgTrackingId = tgTrackingId
                )
                if (started) {
                    editMessageText(
                        chatId = chatId,
                        messageId = statusMsgId,
                        text = "🚀 **Downloading via Native MTProto (2GB On-Device)!**\n\n" +
                               "📄 **File:** `$fileName`\n" +
                               "💾 **Size:** ${formatSize(fileSize)}\n\n" +
                               "⚡ *Streaming chunks directly into Movies folder with zero cloud limits...*",
                        parseMode = "Markdown"
                    )
                    return@launch
                }
            }

            // 2. If >20MB or cloud refused, check configured Stream Bridge URL for auto-link resolution
            if (directDownloadUrl == null) {
                val streamBridge = appPrefs?.getString("telegram_stream_bridge_url", "")?.trim()?.removeSuffix("/") ?: ""
                if (streamBridge.isNotBlank()) {
                    directDownloadUrl = tryResolveFromStreamBridge(streamBridge, fileId, botToken, fileName)
                }
            }

            // 3. If stream URL resolved, download automatically in background!
            if (directDownloadUrl != null) {
                Logger.i(LogTag.TELEGRAM, "Direct stream resolved for '$fileName'. Initiating high-speed stream download...")
                downloadWebVideoUrl(
                    chatId = chatId,
                    rawUrl = directDownloadUrl,
                    customFileName = fileName,
                    reuseStatusMsgId = statusMsgId,
                    existingTrackingId = tgTrackingId,
                    fileId = fileId
                )
                return@launch
            }

            // 4. If PC is available on LAN, delegate to PC MTProto downloader
            val delegated = tryDelegateTelegramToPc(chatId, messageId, fileName, fileSize)
            if (delegated) {
                editMessageText(
                    chatId = chatId,
                    messageId = statusMsgId,
                    text = "🚀 **Delegated to PC Downloader (2 GB MTProto Mode)!**\n\n" +
                           "📄 **File:** `$fileName`\n" +
                           "💾 **Size:** ${formatSize(fileSize)}\n\n" +
                           "⚡ *Downloading directly to your home server library!*",
                    parseMode = "Markdown"
                )
                return@launch
            }

            // 5. If unable to auto-download, inform user with 1-tap options
            if (statusMsgId > 0) deleteMessage(chatId, statusMsgId)

            TorrentEngine.updateTelegramDownload(
                id = tgTrackingId,
                downloadedBytes = 0L,
                totalBytes = fileSize,
                speedStr = "Telegram Refused",
                etaStr = "Failed",
                pct = 0
            )

            val cleanTitle = extractCleanMovieTitle(fileName, caption)
            pendingTorrentLookups[chatId] = cleanTitle

            val sizeInfo = if (fileSize > 0) " (${formatSize(fileSize)})" else ""
            val isLimitErr = fileSize > 20 * 1024 * 1024L

            val promptText = if (isLimitErr) {
                "⚠️ **Telegram Cloud 20 MB Limit Reached**\n\n" +
                "📄 **File:** `$fileName`$sizeInfo\n" +
                "🎬 **Detected Movie:** `$cleanTitle`\n\n" +
                "💡 *Telegram's official Cloud Bot API blocks direct downloads over 20 MB.*\n\n" +
                "👉 **How to download this on your Android device (PC-free):**\n" +
                "1️⃣ **Native 2GB Mode**: Enter your free Telegram API ID & Hash in Settings → Telegram Tab for 100% on-device auto-downloads.\n" +
                "2️⃣ **Instant Link Forward**: Forward this file to `@DirectLinkGeneratorBot` or `@uploadbot`, and send the resulting link here. PocketNode will auto-download it instantly!\n" +
                "3️⃣ **Auto Stream Bridge**: Add your free Stream Bridge URL in app Settings → Telegram Tab for 100% invisible auto-downloads.\n" +
                "4️⃣ **Search Torrents**: Or tap below to download **$cleanTitle** from high-speed Torrents!"
            } else {
                "❌ **Telegram Direct Download Failed**\n\n" +
                "📄 **File:** `$fileName`$sizeInfo\n" +
                "🎬 **Detected Movie:** `$cleanTitle`\n\n" +
                "Would you like to search & download **$cleanTitle** from high-speed Torrents instead?"
            }

            val inlineKeyboard = listOf(
                listOf(
                    mapOf("text" to "🔍 Search Torrent Swarms", "callback_data" to "fallback_yes"),
                    mapOf("text" to "❌ Cancel", "callback_data" to "fallback_no")
                )
            )

            sendMessage(
                chatId = chatId,
                text = promptText,
                parseMode = "Markdown",
                replyMarkup = mapOf("inline_keyboard" to inlineKeyboard),
                autoDeleteSeconds = 300
            )
            return@launch
        }
    }

    private suspend fun tryResolveFromStreamBridge(
        bridgeUrl: String,
        fileId: String,
        token: String,
        fileName: String
    ): String? {
        val candidates = listOf(
            "$bridgeUrl/bot$token/getFile?file_id=$fileId",
            "$bridgeUrl/api/resolve?file_id=$fileId&token=$token",
            "$bridgeUrl/watch/$fileId",
            "$bridgeUrl/stream/$fileId"
        )
        for (candidate in candidates) {
            try {
                if (candidate.contains("getFile")) {
                    val resp = client.get(candidate).bodyAsText()
                    val json = gson.fromJson(resp, JsonObject::class.java)
                    if (json.has("ok") && json.get("ok").asBoolean) {
                        val path = json.getAsJsonObject("result").get("file_path").asString
                        return "$bridgeUrl/file/bot$token/$path"
                    }
                } else {
                    val resp = client.get(candidate)
                    if (resp.status == HttpStatusCode.OK || resp.status.value in 300..308) {
                        return candidate
                    }
                }
            } catch (e: Exception) {
                // Ignore and try next pattern
            }
        }
        return null
    }

    private fun startTorrentWithTelegramProgress(
        chatId: Long,
        magnetUri: String,
        title: String? = null,
        expectedSize: String? = null
    ) {
        val hashMatch = Regex("urn:btih:([a-zA-Z0-9]+)", RegexOption.IGNORE_CASE).find(magnetUri)
        val hashFromUri = hashMatch?.groupValues?.getOrNull(1)?.lowercase() ?: ""
        val dnFromUri = magnetUri.substringAfter("dn=", "").substringBefore("&")
            .replace("+", " ")
            .replace("%20", " ")
            .trim()
            .take(60)

        val displayName = title?.take(60)?.ifBlank { null }
            ?: dnFromUri.ifBlank { "Torrent_${System.currentTimeMillis()}" }

        botScope.launch {
            val sizeInfo = if (!expectedSize.isNullOrBlank()) "📦 *Size:* `$expectedSize`\n" else ""
            val statusMsgId = sendMessage(
                chatId = chatId,
                text = "📥 *Torrent Download Initiated...*\n\n" +
                        "🎬 `$displayName`\n" +
                        sizeInfo +
                        "\n[░░░░░░░░░░] **0%**\n" +
                        "⏳ Connecting to DHT swarm & finding seeders...",
                parseMode = "Markdown",
                autoDeleteSeconds = 0
            )

            TorrentEngine.downloadMagnet(
                magnetUri = magnetUri,
                title = displayName,
                expectedSize = expectedSize
            )

            var lastPct = -1
            var lastUpdateTs = 0L
            var isFinished = false
            var finalTotalSize = 0L
            var finalName = displayName

            // Monitor active torrent progress in TorrentEngine (up to 48 hours)
            for (step in 0 until 48 * 3600) {
                delay(3000L)

                val downloads = TorrentEngine.downloads.value
                val item = downloads.find {
                    (hashFromUri.isNotBlank() && it.hash.equals(hashFromUri, ignoreCase = true)) ||
                    (it.name.isNotBlank() && it.name.equals(displayName, ignoreCase = true)) ||
                    (hashFromUri.isNotBlank() && it.id.equals(hashFromUri, ignoreCase = true))
                } ?: continue

                if (item.name.isNotBlank() && !item.name.startsWith("Torrent_")) {
                    finalName = item.name
                }
                finalTotalSize = item.totalBytes
                val pct = item.progressPct
                val now = System.currentTimeMillis()

                if (pct >= 100 || item.speedLabel == "COMPLETED" || item.speedLabel == "done") {
                    isFinished = true
                    break
                }

                if (now - lastUpdateTs >= 3500L && pct != lastPct) {
                    lastPct = pct
                    lastUpdateTs = now

                    val barFilled = (pct / 10).coerceIn(0, 10)
                    val bar = "█".repeat(barFilled) + "░".repeat(10 - barFilled)

                    val downloadedStr = formatSize(item.downloadedBytes)
                    val totalStr = if (item.totalBytes > 0) formatSize(item.totalBytes) else (expectedSize ?: item.sizeLabel)
                    val speed = item.downSpeed.ifBlank { "0 KB/s" }
                    val eta = item.eta.ifBlank { "--" }
                    val seeders = if (item.seeds > 0 || item.peers > 0) " | 👥 ${item.seeds} seeds (${item.peers} peers)" else ""

                    val progressText = "📥 *Downloading Torrent...*\n\n" +
                            "🎬 `$finalName`\n\n" +
                            "[$bar] **$pct%**\n" +
                            "⚡ $speed$seeders\n" +
                            "⏳ ETA: $eta\n" +
                            "💾 $downloadedStr / $totalStr"

                    editMessageText(chatId, statusMsgId, progressText, parseMode = "Markdown")
                }
            }

            if (isFinished) {
                if (statusMsgId > 0) deleteMessage(chatId, statusMsgId)

                val displaySize = if (finalTotalSize > 0) formatSize(finalTotalSize) else (expectedSize ?: "")
                val finalSizeLine = if (displaySize.isNotBlank()) "📦 *Size:* `$displaySize`\n" else ""

                sendMessage(
                    chatId = chatId,
                    text = "🎉 *Torrent Download Complete!*\n\n" +
                            "🎬 `$finalName`\n" +
                            finalSizeLine +
                            "📂 Saved to server library. Available in Jellyfin!",
                    parseMode = "Markdown",
                    autoDeleteSeconds = 600
                )
            }
        }
    }

    private fun downloadWebVideoUrl(
        chatId: Long,
        rawUrl: String,
        customFileName: String? = null,
        reuseStatusMsgId: Long = 0L,
        existingTrackingId: String? = null,
        fileId: String = ""
    ) {
        var url = rawUrl.trim()
        if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
            url = "https://$url"
        }

        val tgTrackingId = existingTrackingId ?: "web_${System.currentTimeMillis()}"

        val job = botScope.launch {
            val statusMsgId = if (reuseStatusMsgId > 0L) {
                editMessageText(
                    chatId = chatId,
                    messageId = reuseStatusMsgId,
                    text = "🔗 *Direct Stream Download*\n\n📄 `${customFileName ?: rawUrl.substringAfterLast("/").substringBefore("?")}`\n⏳ Connecting to stream link...",
                    parseMode = "Markdown"
                )
                reuseStatusMsgId
            } else {
                sendMessage(
                    chatId = chatId,
                    text = "🔗 *Direct Web Video Download*\n\n🌐 *URL:* `$url`\n⏳ Initializing stream resolver...",
                    parseMode = "Markdown",
                    autoDeleteSeconds = 0
                )
            }

            // If it is a known video platform (YouTube, Insta, TikTok, Reddit, etc.), use native yt-dlp on Android
            if (YtDlpManager.isSupportedPlatform(url)) {
                downloadWithNativeYtDlp(chatId, url, statusMsgId, tgTrackingId)
                return@launch
            }

            try {
                // 1. Standalone mobile video extraction & download (shows live progress in Telegram like PC)
                val resolvedStream = resolveVideoStreamUrl(url)
                val targetDownloadUrl = resolvedStream?.first ?: url
                val extractedTitle = resolvedStream?.second

                // Follow initial redirects robustly to resolve destination metadata
                var currentUrl = targetDownloadUrl
                var initialConn: HttpURLConnection? = null
                var redirects = 0
                while (redirects < 10) {
                    val u = URL(currentUrl)
                    val c = u.openConnection() as HttpURLConnection
                    c.instanceFollowRedirects = true
                    c.connectTimeout = 30000
                    c.readTimeout = 60000
                    c.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                    c.setRequestProperty("Accept", "*/*")
                    c.setRequestProperty("Referer", url)
                    c.setRequestProperty("Accept-Encoding", "identity")
                    c.setRequestProperty("Connection", "keep-alive")

                    val status = c.responseCode
                    if (status in 301..308) {
                        val location = c.getHeaderField("Location")
                        if (!location.isNullOrBlank()) {
                            currentUrl = URL(u, location).toString()
                            c.disconnect()
                            redirects++
                            continue
                        }
                    }
                    initialConn = c
                    break
                }

                if (initialConn == null || initialConn.responseCode !in 200..299) {
                    val code = initialConn?.responseCode ?: -1
                    val err = initialConn?.responseMessage ?: "Unable to connect"
                    throw java.io.IOException("HTTP $code: $err")
                }

                val contentLength = initialConn.contentLengthLong.let { if (it < 0) 0L else it }
                val disposition = initialConn.getHeaderField("Content-Disposition")
                val contentType = initialConn.contentType ?: ""

                // Reject HTML web pages or JSON API error responses from raw HTTP download; process via yt-dlp
                val isWebPage = contentType.contains("text/html", ignoreCase = true) ||
                        contentType.contains("text/plain", ignoreCase = true) ||
                        contentType.contains("application/json", ignoreCase = true) ||
                        contentType.contains("application/xhtml", ignoreCase = true)

                if (isWebPage) {
                    initialConn.disconnect()
                    downloadWithNativeYtDlp(chatId, url, statusMsgId, tgTrackingId)
                    return@launch
                }

                var fileNameFromHeader: String? = null
                if (!disposition.isNullOrBlank()) {
                    if (disposition.contains("filename*=", ignoreCase = true)) {
                        val rawEnc = disposition.substringAfter("filename*=", "").substringAfter("''").substringBefore(";").trim().replace("\"", "")
                        runCatching { fileNameFromHeader = URLDecoder.decode(rawEnc, "UTF-8") }
                    } else if (disposition.contains("filename=", ignoreCase = true)) {
                        fileNameFromHeader = disposition.substringAfter("filename=", "").substringBefore(";").trim().replace("\"", "")
                    }
                }

                val defaultExt = when {
                    contentType.contains("mp4", ignoreCase = true) -> ".mp4"
                    contentType.contains("matroska", ignoreCase = true) || contentType.contains("mkv", ignoreCase = true) -> ".mkv"
                    contentType.contains("webm", ignoreCase = true) -> ".webm"
                    contentType.contains("avi", ignoreCase = true) -> ".avi"
                    contentType.contains("quicktime", ignoreCase = true) -> ".mov"
                    else -> ".mp4"
                }

                val lastPathSegment = currentUrl.substringAfterLast("/").substringBefore("?").trim()
                var rawFileName: String = when {
                    !customFileName.isNullOrBlank() -> customFileName
                    !extractedTitle.isNullOrBlank() -> if (extractedTitle.endsWith(".mp4", ignoreCase = true) || extractedTitle.endsWith(".mkv", ignoreCase = true)) extractedTitle else "$extractedTitle$defaultExt"
                    !fileNameFromHeader.isNullOrBlank() -> fileNameFromHeader
                    lastPathSegment.isNotBlank() && lastPathSegment.contains(".") -> lastPathSegment
                    else -> "web_video_${System.currentTimeMillis()}$defaultExt"
                } ?: "web_video_${System.currentTimeMillis()}$defaultExt"
                
                var fileName = rawFileName.replace(Regex("""[\\/:*?"<>|]"""), "_").trim()
                if (!fileName.contains(".")) {
                    fileName = "$fileName$defaultExt"
                }

                initialConn.disconnect()

                val targetFile = File(StorageManager.getMoviesDir(), fileName)

                TorrentEngine.recordTelegramDownload(
                    name = fileName,
                    sizeLabel = formatSize(contentLength),
                    id = tgTrackingId,
                    remoteUrl = url,
                    fileId = fileId
                )

                var retryCount = 0
                val maxRetries = 15
                var isCompleted = false
                var lastUpdateTs = 0L
                var lastPct = -1
                val startTime = System.currentTimeMillis()

                while (!isCompleted && retryCount < maxRetries && coroutineContext.isActive) {
                    var conn: HttpURLConnection? = null
                    var inputStream: InputStream? = null
                    var fileStream: FileOutputStream? = null

                    try {
                        val currentDownloadedBytes = if (targetFile.exists()) targetFile.length() else 0L
                        if (contentLength > 0 && currentDownloadedBytes >= contentLength) {
                            isCompleted = true
                            break
                        }

                        val u = URL(currentUrl)
                        conn = u.openConnection() as HttpURLConnection
                        conn.instanceFollowRedirects = true
                        conn.connectTimeout = 30000
                        conn.readTimeout = 60000 // 60s read timeout for massive chunks
                        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                        conn.setRequestProperty("Accept", "*/*")
                        conn.setRequestProperty("Referer", url)
                        conn.setRequestProperty("Accept-Encoding", "identity")
                        conn.setRequestProperty("Connection", "keep-alive")

                        if (currentDownloadedBytes > 0L) {
                            Logger.i(LogTag.TELEGRAM, "Requesting Range: bytes=$currentDownloadedBytes- for '$fileName'")
                            conn.setRequestProperty("Range", "bytes=$currentDownloadedBytes-")
                        }

                        val status = conn.responseCode
                        if (status == 416) {
                            // 416 Range Not Satisfiable: file is already complete
                            isCompleted = true
                            break
                        }

                        if (status !in 200..299) {
                            throw java.io.IOException("HTTP $status: ${conn.responseMessage}")
                        }

                        // If 206 Partial Content, append to file; if 200 OK, rewrite from 0
                        val isAppending = status == 206 && currentDownloadedBytes > 0L
                        fileStream = FileOutputStream(targetFile, isAppending)
                        var downloadedBytes = if (isAppending) currentDownloadedBytes else 0L

                        inputStream = BufferedInputStream(conn.inputStream, 256 * 1024)
                        val buffer = ByteArray(256 * 1024)

                        while (coroutineContext.isActive) {
                            val read = inputStream.read(buffer, 0, buffer.size)
                            if (read <= 0) {
                                // Reached end of stream
                                val finalLength = targetFile.length()
                                if (contentLength <= 0 || finalLength >= contentLength || finalLength >= (contentLength * 0.98).toLong()) {
                                    isCompleted = true
                                }
                                break
                            }

                            fileStream.write(buffer, 0, read)
                            downloadedBytes += read

                            val now = System.currentTimeMillis()
                            if (now - lastUpdateTs >= 3500L && contentLength > 0) {
                                lastUpdateTs = now
                                val pct = ((downloadedBytes.toDouble() / contentLength.toDouble()) * 100).toInt().coerceIn(0, 100)
                                if (pct != lastPct) {
                                    lastPct = pct
                                    val elapsedSec = (now - startTime) / 1000.0
                                    val speedBytes = if (elapsedSec > 0) (downloadedBytes / elapsedSec).toLong() else 0L
                                    val speedStr = formatSpeed(speedBytes)
                                    val remainingBytes = (contentLength - downloadedBytes).coerceAtLeast(0L)
                                    val etaStr = if (speedBytes > 0) formatEta(remainingBytes, speedBytes) else "calculating..."
                                    val barFilled = (pct / 10).coerceIn(0, 10)
                                    val bar = "█".repeat(barFilled) + "░".repeat(10 - barFilled)

                                    val progressText = "📥 *Downloading Video...*\n\n📄 `$fileName`\n\n[$bar] **$pct%**\n⚡ $speedStr | ⏳ ETA: $etaStr\n💾 ${formatSize(downloadedBytes)} / ${formatSize(contentLength)}"
                                    if (statusMsgId > 0) editMessageText(chatId, statusMsgId, progressText, parseMode = "Markdown")

                                    TorrentEngine.updateTelegramDownload(
                                        id = tgTrackingId,
                                        downloadedBytes = downloadedBytes,
                                        totalBytes = contentLength,
                                        speedStr = speedStr,
                                        etaStr = etaStr,
                                        pct = pct
                                    )
                                }
                            } else if (now - lastUpdateTs >= 3500L && contentLength <= 0L) {
                                lastUpdateTs = now
                                val elapsedSec = (now - startTime) / 1000.0
                                val speedBytes = if (elapsedSec > 0) (downloadedBytes / elapsedSec).toLong() else 0L
                                val speedStr = formatSpeed(speedBytes)
                                val progressText = "📥 *Downloading Video Stream...*\n\n📄 `$fileName`\n\n⚡ $speedStr | 💾 Downloaded: ${formatSize(downloadedBytes)}"
                                if (statusMsgId > 0) editMessageText(chatId, statusMsgId, progressText, parseMode = "Markdown")

                                val simulatedPct = ((downloadedBytes / (10 * 1024 * 1024.0)) * 50).toInt().coerceIn(1, 95)
                                TorrentEngine.updateTelegramDownload(
                                    id = tgTrackingId,
                                    downloadedBytes = downloadedBytes,
                                    totalBytes = 0L,
                                    speedStr = speedStr,
                                    etaStr = "Streaming...",
                                    pct = simulatedPct
                                )
                            }
                        }
                    } catch (e: Exception) {
                        if (!coroutineContext.isActive) {
                            Logger.i(LogTag.TELEGRAM, "Telegram download $tgTrackingId stopped/paused gracefully.")
                            return@launch
                        }

                        retryCount++
                        val currentBytes = targetFile.length()
                        val currentPct = if (contentLength > 0) ((currentBytes.toDouble() / contentLength.toDouble()) * 100).toInt().coerceIn(0, 99) else 0
                        Logger.w(LogTag.TELEGRAM, "Stream dropped at ${formatSize(currentBytes)} ($currentPct%) with error: ${e.message}. Resuming attempt $retryCount/$maxRetries...")

                        TorrentEngine.updateTelegramDownload(
                            id = tgTrackingId,
                            downloadedBytes = currentBytes,
                            totalBytes = contentLength,
                            speedStr = "RECONNECTING ($retryCount/$maxRetries)",
                            etaStr = "Resuming...",
                            pct = currentPct
                        )

                        delay(2000L * minOf(retryCount, 4))
                    } finally {
                        runCatching { fileStream?.flush() }
                        runCatching { fileStream?.close() }
                        runCatching { inputStream?.close() }
                        runCatching { conn?.disconnect() }
                    }
                }

                if (isCompleted || (contentLength > 0 && targetFile.length() >= (contentLength * 0.95).toLong())) {
                    TorrentEngine.updateTelegramDownload(
                        id = tgTrackingId,
                        downloadedBytes = targetFile.length(),
                        totalBytes = targetFile.length(),
                        speedStr = "0 KB/s",
                        etaStr = "Done",
                        pct = 100
                    )
                    TorrentEngine.markComplete(tgTrackingId)
                    activeTelegramJobs.remove(tgTrackingId)
                    runCatching { com.remotemedia.core.FederatedMediaManager.refresh() }

                    if (statusMsgId > 0) deleteMessage(chatId, statusMsgId)

                    sendMessage(
                        chatId = chatId,
                        text = "🎉 *Web Video Download Complete!*\n\n📄 *File:* `$fileName`\n📦 *Size:* `${formatSize(targetFile.length())}`\n📂 Saved to server library.",
                        parseMode = "Markdown",
                        autoDeleteSeconds = 600
                    )
                } else if (!coroutineContext.isActive) {
                    Logger.i(LogTag.TELEGRAM, "Download paused/cancelled for: $fileName")
                } else {
                    activeTelegramJobs.remove(tgTrackingId)
                    Logger.e(LogTag.TELEGRAM, "Download stalled after $maxRetries retries for: $fileName (${formatSize(targetFile.length())})")
                    TorrentEngine.updateTelegramDownload(
                        id = tgTrackingId,
                        downloadedBytes = targetFile.length(),
                        totalBytes = contentLength,
                        speedStr = "PAUSED (TAP TO RESUME)",
                        etaStr = "Paused",
                        pct = if (contentLength > 0) ((targetFile.length().toDouble() / contentLength.toDouble()) * 100).toInt().coerceIn(0, 99) else 0
                    )
                }
            } catch (e: Exception) {
                activeTelegramJobs.remove(tgTrackingId)
                Logger.e(LogTag.TELEGRAM, "Fatal error downloading web video URL '$url': ${e.message}")
                TorrentEngine.updateTelegramDownload(
                    id = tgTrackingId,
                    downloadedBytes = 0L,
                    totalBytes = 0L,
                    speedStr = "ERROR",
                    etaStr = "Failed",
                    pct = 0
                )
                if (statusMsgId > 0) deleteMessage(chatId, statusMsgId)
                sendMessage(
                    chatId = chatId,
                    text = "❌ *Web Video Download Failed*\n\n🌐 URL: `$url`\n⚠️ Error: `${e.message}`\n\n💡 Tip: You can tap Resume on the Downloads screen or use Stream Bridge for large files.",
                    parseMode = "Markdown",
                    autoDeleteSeconds = 120
                )
            } finally {
                activeTelegramJobs.remove(tgTrackingId)
            }
        }

        activeTelegramJobs[tgTrackingId] = job
    }

    private suspend fun downloadWithNativeYtDlp(
        chatId: Long,
        url: String,
        statusMsgId: Long,
        tgTrackingId: String
    ) {
        val domain = runCatching { URL(url).host.removePrefix("www.") }.getOrNull() ?: "Web Video"
        editMessageText(
            chatId = chatId,
            messageId = statusMsgId,
            text = "🎬 *Downloading Media (Native yt-dlp)*\n\n🌐 *Source:* `$domain`\n🔗 `$url`\n\n⏳ Extracting video stream & metadata...",
            parseMode = "Markdown"
        )

        TorrentEngine.recordTelegramDownload("$domain Media", "Extracting...", tgTrackingId)

        var lastUpdateTs = 0L
        var lastReportedPct = -1

        try {
            val downloadedFile = YtDlpManager.download(
                url = url,
                outputDir = StorageManager.getMoviesDir(),
                processId = tgTrackingId
            ) { progress ->
                val now = System.currentTimeMillis()
                val pct = progress.progressPct
                if ((now - lastUpdateTs >= 3500L && pct != lastReportedPct) || pct == 100) {
                    lastUpdateTs = now
                    lastReportedPct = pct

                    val barFilled = (pct / 10).coerceIn(0, 10)
                    val bar = "█".repeat(barFilled) + "░".repeat(10 - barFilled)

                    val downloadedStr = if (progress.downloadedBytes > 0) formatSize(progress.downloadedBytes) else "--"
                    val totalStr = if (progress.totalBytes > 0) formatSize(progress.totalBytes) else "--"
                    val sizeLine = if (progress.totalBytes > 0) "💾 $downloadedStr / $totalStr\n" else ""

                    val progressText = "📥 *Downloading Video (yt-dlp)...*\n\n" +
                            "🌐 `$domain`\n\n" +
                            "[$bar] **$pct%**\n" +
                            "⚡ ${progress.speedStr} | ⏳ ETA: ${progress.etaStr}\n" +
                            sizeLine

                    botScope.launch {
                        editMessageText(chatId, statusMsgId, progressText, parseMode = "Markdown")
                    }

                    TorrentEngine.updateTelegramDownload(
                        id = tgTrackingId,
                        downloadedBytes = progress.downloadedBytes,
                        totalBytes = progress.totalBytes,
                        speedStr = progress.speedStr,
                        etaStr = progress.etaStr,
                        pct = pct
                    )
                }
            }

            if (downloadedFile != null && downloadedFile.exists() && downloadedFile.length() > 0) {
                TorrentEngine.updateTelegramDownload(
                    id = tgTrackingId,
                    downloadedBytes = downloadedFile.length(),
                    totalBytes = downloadedFile.length(),
                    speedStr = "0 KB/s",
                    etaStr = "Done",
                    pct = 100
                )
                runCatching { com.remotemedia.core.FederatedMediaManager.refresh() }

                if (statusMsgId > 0) deleteMessage(chatId, statusMsgId)

                sendMessage(
                    chatId = chatId,
                    text = "🎉 *Web Video Download Complete!*\n\n" +
                            "🎬 `${downloadedFile.name}`\n" +
                            "📦 *Size:* `${formatSize(downloadedFile.length())}`\n" +
                            "📂 Saved to server library. Available in Jellyfin & DLNA!",
                    parseMode = "Markdown",
                    autoDeleteSeconds = 600
                )
            } else {
                throw java.io.IOException("yt-dlp finished but output file could not be located.")
            }
        } catch (e: Exception) {
            TorrentEngine.deleteDownload(tgTrackingId)
            Logger.e(LogTag.TELEGRAM, "Native yt-dlp download failed: ${e.message}")

            // If phone failed, see if PC is available to handle it
            val delegated = tryDelegateToPc(url)
            if (delegated) {
                if (statusMsgId > 0) deleteMessage(chatId, statusMsgId)
                sendMessage(
                    chatId = chatId,
                    text = "🚀 *Delegated to PC Downloader (yt-dlp)!*\n\n🌐 *URL:* `$url`\n⚡ PC is downloading in highest quality to shared library.",
                    parseMode = "Markdown",
                    autoDeleteSeconds = 300
                )
                return
            }

            if (statusMsgId > 0) deleteMessage(chatId, statusMsgId)
            sendMessage(
                chatId = chatId,
                text = "❌ *Web Video Download Failed*\n\n🌐 URL: `$url`\n⚠️ Error: `${e.message}`\n\n💡 Tip: Check if the video is private or restricted.",
                parseMode = "Markdown",
                autoDeleteSeconds = 120
            )
        }
    }

    private suspend fun resolveVideoStreamUrl(url: String): Pair<String, String>? {
        try {
            val ytId = extractYouTubeVideoId(url)
            if (ytId != null) {
                val instances = listOf("https://inv.tux.pizza", "https://invidious.nerdvpn.de", "https://invidious.jing.rocks")
                for (inst in instances) {
                    try {
                        val apiUrl = "$inst/api/v1/videos/$ytId"
                        val resp = client.get(apiUrl) {
                            headers.append(HttpHeaders.UserAgent, "Mozilla/5.0")
                        }
                        if (resp.status == HttpStatusCode.OK) {
                            val json = gson.fromJson(resp.bodyAsText(), JsonObject::class.java)
                            val title = json.get("title")?.asString ?: "YouTube_Video"
                            val cleanTitle = title.replace(Regex("[^a-zA-Z0-9 ._-]"), "").take(50)

                            if (json.has("formatStreams")) {
                                val formats = json.getAsJsonArray("formatStreams")
                                var bestUrl = ""
                                for (i in 0 until formats.size()) {
                                    val f = formats.get(i).asJsonObject
                                    val fUrl = f.get("url")?.asString ?: ""
                                    if (fUrl.isNotBlank()) {
                                        bestUrl = fUrl
                                    }
                                }
                                if (bestUrl.isNotBlank()) {
                                    return Pair(bestUrl, cleanTitle)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // Try next instance
                    }
                }
            }
        } catch (e: Exception) {
            Logger.w(LogTag.TELEGRAM, "Video stream resolution error: ${e.message}")
        }
        return null
    }

    private fun extractYouTubeVideoId(url: String): String? {
        val pattern = Regex("(?:youtu\\.be/|youtube\\.com/(?:embed/|v/|watch\\?v=|watch\\?.+&v=|shorts/))([a-zA-Z0-9_-]{11})")
        return pattern.find(url)?.groupValues?.getOrNull(1)
    }

    private suspend fun tryDelegateToPc(url: String): Boolean {
        val pcHost = appPrefs?.getString("pc_host", "")?.trim() ?: ""
        val hostsToTry = mutableListOf<String>()
        if (pcHost.isNotBlank()) hostsToTry.add(pcHost)
        for (share in com.remotemedia.core.NetworkShareManager.mountedShares.value) {
            if (share.host.isNotBlank() && !hostsToTry.contains(share.host)) {
                hostsToTry.add(share.host)
            }
        }
        for (candidate in listOf("10.0.2.2", "127.0.0.1", "192.168.1.100", "192.168.1.5", "192.168.0.100")) {
            if (!hostsToTry.contains(candidate)) hostsToTry.add(candidate)
        }

        for (host in hostsToTry) {
            try {
                val pcEndpoint = "http://$host:8765/api/download_link"
                val encUrl = URLEncoder.encode(url, StandardCharsets.UTF_8.name())
                val resp = client.get("$pcEndpoint?url=$encUrl") {
                    headers.append(HttpHeaders.UserAgent, "PocketNode Mobile Bot")
                }
                if (resp.status == HttpStatusCode.OK) {
                    Logger.i(LogTag.TELEGRAM, "Successfully delegated URL download to PC at $host")
                    return true
                }
            } catch (e: Exception) {
                // PC not reachable on this host
            }
        }
        return false
    }

    private suspend fun tryDelegateTelegramToPc(
        chatId: Long,
        messageId: Long,
        fileName: String,
        fileSize: Long
    ): Boolean {
        val pcHost = appPrefs?.getString("pc_host", "")?.trim() ?: ""
        val hostsToTry = mutableListOf<String>()
        if (pcHost.isNotBlank()) hostsToTry.add(pcHost)
        for (share in com.remotemedia.core.NetworkShareManager.mountedShares.value) {
            if (share.host.isNotBlank() && !hostsToTry.contains(share.host)) {
                hostsToTry.add(share.host)
            }
        }
        for (candidate in listOf("10.0.2.2", "127.0.0.1", "192.168.1.100", "192.168.1.5", "192.168.0.100")) {
            if (!hostsToTry.contains(candidate)) hostsToTry.add(candidate)
        }

        val encName = URLEncoder.encode(fileName, StandardCharsets.UTF_8.name())
        for (host in hostsToTry) {
            try {
                val pcEndpoint = "http://$host:8765/api/download_telegram?chat_id=$chatId&message_id=$messageId&file_name=$encName&size=$fileSize"
                val resp = client.get(pcEndpoint) {
                    headers.append(HttpHeaders.UserAgent, "PocketNode Mobile Bot")
                }
                if (resp.status == HttpStatusCode.OK) {
                    Logger.i(LogTag.TELEGRAM, "Successfully delegated Telegram 2GB media download to PC at $host")
                    return true
                }
            } catch (e: Exception) {
                // PC not reachable on this host
            }
        }
        return false
    }

    private fun formatSpeed(bytesPerSec: Long): String {
        if (bytesPerSec <= 0) return "0 KB/s"
        val kb = bytesPerSec / 1024.0
        val mb = kb / 1024.0
        return if (mb >= 1.0) String.format(java.util.Locale.US, "%.1f MB/s", mb)
        else String.format(java.util.Locale.US, "%.0f KB/s", kb)
    }

    private fun formatEta(remainingBytes: Long, bytesPerSec: Long): String {
        if (remainingBytes <= 0) return "0s"
        if (bytesPerSec <= 0) return "∞"
        val sec = remainingBytes / bytesPerSec
        val hours = sec / 3600
        val mins = (sec % 3600) / 60
        val s = sec % 60
        return when {
            hours > 0 -> "${hours}h ${mins}m"
            mins > 0 -> "${mins}m ${s}s"
            else -> "${s}s"
        }
    }

    private fun extractMagnetOrUrl(text: String): String? {
        val magnetIdx = text.indexOf("magnet:?")
        if (magnetIdx >= 0) {
            val candidate = text.substring(magnetIdx).trim()
            val spaceIdx = candidate.indexOf(" ")
            return if (spaceIdx > 0) candidate.substring(0, spaceIdx) else candidate
        }
        if (text.startsWith("http://") || text.startsWith("https://")) {
            if (text.contains(".torrent", ignoreCase = true)) {
                return text
            }
        }
        return null
    }

    private suspend fun getSpellingSuggestion(query: String): String? {
        val searchQuery = query.split(":::")[0].trim()
        val encoded = URLEncoder.encode(searchQuery, StandardCharsets.UTF_8.name())
        val url = "https://ac.duckduckgo.com/ac/?q=$encoded&type=json"
        try {
            val responseText = client.get(url) {
                headers.append(HttpHeaders.UserAgent, "Mozilla/5.0")
            }.bodyAsText()
            val array = gson.fromJson(responseText, JsonArray::class.java)
            if (array != null && array.size() > 0) {
                val first = array.get(0).asJsonObject
                val phrase = first.get("phrase")?.asString ?: ""
                if (phrase.isNotBlank() && !phrase.equals(searchQuery, ignoreCase = true)) {
                    return phrase
                }
            }
        } catch (e: Exception) {
            Logger.w(LogTag.TELEGRAM, "DuckDuckGo suggestion error: ${e.message}")
        }
        return null
    }

    private suspend fun performSearchAndPresentChoices(chatId: Long, query: String) {
        val searchMsgId = sendMessage(chatId, "🔍 Searching for '$query' across global torrent indexers...", autoDeleteSeconds = 0)

        var results = searchTorrentResults(query)

        // If no results and query contains a 4-digit year (e.g. "Apocalypto 2006"), retry with title only ("Apocalypto")
        val yearRegex = Regex("""\b(19\d\d|20\d\d)\b""")
        if (results.isEmpty() && yearRegex.containsMatchIn(query)) {
            val titleWithoutYear = query.replace(yearRegex, "").trim().replace(Regex("""\s+"""), " ")
            if (titleWithoutYear.length >= 2) {
                Logger.i(LogTag.TELEGRAM, "No torrent results for '$query', retrying with title only: '$titleWithoutYear'")
                results = searchTorrentResults(titleWithoutYear)
            }
        }

        if (results.isNotEmpty()) {
            searchResultsCache[chatId] = results
            searchQueriesCache[chatId] = query

            if (searchMsgId > 0) deleteMessage(chatId, searchMsgId)

            renderSearchResultsPage(chatId = chatId, messageId = 0L, query = query, pageIndex = 0)
        } else {
            val suggestion = getSpellingSuggestion(query)
            if (searchMsgId > 0) deleteMessage(chatId, searchMsgId)
            if (suggestion != null) {
                sendMessage(
                    chatId = chatId,
                    text = "❌ No torrent results found for **$query**.\n\nDid you mean: **$suggestion**?\nPlease try searching again with the corrected name.",
                    parseMode = "Markdown",
                    autoDeleteSeconds = 120
                )
            } else {
                sendMessage(chatId, "❌ No torrent results found for '$query'.", autoDeleteSeconds = 120)
            }
        }
    }

    private suspend fun renderSearchResultsPage(
        chatId: Long,
        messageId: Long,
        query: String,
        pageIndex: Int
    ) {
        val results = searchResultsCache[chatId] ?: return
        val pageSize = 6
        val totalPages = (results.size + pageSize - 1) / pageSize
        val safePage = pageIndex.coerceIn(0, maxOf(0, totalPages - 1))

        val startIndex = safePage * pageSize
        val endIndex = minOf(results.size, startIndex + pageSize)
        val pageResults = results.subList(startIndex, endIndex)

        val textBuilder = StringBuilder("🎬 *Torrent Releases for '$query'* (Page ${safePage + 1}/$totalPages):\n\n")
        val inlineKeyboard = mutableListOf<List<Map<String, String>>>()

        pageResults.forEachIndexed { i, result ->
            val globalIndex = startIndex + i
            val seedersStr = if (result.seeds > 0) " | 👤 ${result.seeds}s" else ""
            
            textBuilder.append("**${globalIndex + 1}.** `${result.title}`\n   💾 ${result.size}$seedersStr • _${result.source}_\n\n")

            val buttonText = "${globalIndex + 1}. ${result.title} [${result.size}$seedersStr] (${result.source})"
            inlineKeyboard.add(
                listOf(
                    mapOf(
                        "text" to buttonText.take(64),
                        "callback_data" to "dl_$globalIndex"
                    )
                )
            )
        }

        textBuilder.append("👇 *Tap a button below or reply with a number (1-${results.size}) to start download:*")

        // Pagination Navigation Row (Prev / Cancel / Next)
        val navRow = mutableListOf<Map<String, String>>()
        if (safePage > 0) {
            navRow.add(mapOf("text" to "⏪ Prev", "callback_data" to "page_${safePage - 1}"))
        }
        navRow.add(mapOf("text" to "❌ Cancel", "callback_data" to "cancel"))
        if (endIndex < results.size) {
            navRow.add(mapOf("text" to "Next ⏩", "callback_data" to "page_${safePage + 1}"))
        }
        inlineKeyboard.add(navRow)

        val replyMarkup = mapOf("inline_keyboard" to inlineKeyboard)

        if (messageId > 0) {
            editMessageText(chatId, messageId, textBuilder.toString(), parseMode = "Markdown", replyMarkup = replyMarkup)
        } else {
            sendMessage(
                chatId = chatId,
                text = textBuilder.toString(),
                parseMode = "Markdown",
                replyMarkup = replyMarkup,
                autoDeleteSeconds = 600 // 10 minutes timeout
            )
        }
    }

    private suspend fun handleCallbackQuery(callbackQuery: JsonObject) {
        val queryId = if (callbackQuery.has("id")) callbackQuery.get("id").asString else ""
        val data = if (callbackQuery.has("data")) callbackQuery.get("data").asString else ""
        val message = if (callbackQuery.has("message")) callbackQuery.getAsJsonObject("message") else null

        if (message == null || !message.has("chat")) return
        val chatId = message.getAsJsonObject("chat").get("id").asLong
        val messageId = if (message.has("message_id")) message.get("message_id").asLong else 0L

        if (data == "cancel") {
            searchResultsCache.remove(chatId)
            searchQueriesCache.remove(chatId)
            answerCallbackQuery(queryId, "Search cancelled.")
            if (messageId > 0) deleteMessage(chatId, messageId)
            return
        }

        if (data == "fallback_yes") {
            val title = pendingTorrentLookups.remove(chatId)
            answerCallbackQuery(queryId, "Searching torrents for ${title ?: "movie"}...")
            if (messageId > 0) deleteMessage(chatId, messageId)
            if (!title.isNullOrBlank()) {
                performSearchAndPresentChoices(chatId, title)
            }
            return
        }

        if (data == "fallback_no") {
            pendingTorrentLookups.remove(chatId)
            answerCallbackQuery(queryId, "Torrent search cancelled.")
            if (messageId > 0) deleteMessage(chatId, messageId)
            return
        }

        if (data.startsWith("dl_")) {
            val index = data.removePrefix("dl_").toIntOrNull() ?: -1
            val results = searchResultsCache[chatId]

            if (index >= 0 && results != null && index < results.size) {
                val selectedResult = results[index]
                answerCallbackQuery(queryId, "Starting download for ${selectedResult.title}")

                if (messageId > 0) {
                    deleteMessage(chatId, messageId)
                }

                startTorrentWithTelegramProgress(
                    chatId = chatId,
                    magnetUri = selectedResult.magnet,
                    title = selectedResult.title,
                    expectedSize = selectedResult.size
                )
            } else {
                answerCallbackQuery(queryId, "Search selection expired. Please search again.")
            }
        } else if (data.startsWith("page_")) {
            if (data == "page_noop") {
                answerCallbackQuery(queryId, "Page indicator")
                return
            }
            val targetPage = data.removePrefix("page_").toIntOrNull() ?: 0
            answerCallbackQuery(queryId, "Loading page ${targetPage + 1}...")
            val lastQuery = searchQueriesCache[chatId] ?: "search"
            renderSearchResultsPage(chatId, messageId, lastQuery, targetPage)
        }
    }

    private suspend fun searchTorrentResults(query: String): List<TelegramSearchResult> {
        val resultsList = CopyOnWriteArrayList<TelegramSearchResult>()
        val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.name())

        // Run indexer queries in parallel
        val ytsJob = botScope.async {
            if (TorrentTrackersManager.isIndexerEnabled("yts")) {
                val ytsMirrors = listOf(
                    "https://yts.bz",
                    "https://web.yts.gg",
                    "https://yts.lt",
                    "https://yts.am",
                    "https://yts.ag"
                )
                var foundDirectYts = false
                for (mirror in ytsMirrors) {
                    try {
                        val ytsUrl = "$mirror/api/v2/list_movies.json?query_term=$encodedQuery"
                        val responseText = withTimeoutOrNull(3000L) {
                            client.get(ytsUrl) {
                                headers.append(HttpHeaders.UserAgent, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                                headers.append(HttpHeaders.Accept, "application/json")
                            }.bodyAsText()
                        } ?: continue

                        if (!responseText.trimStart().startsWith("{")) continue

                        val json = try { gson.fromJson(responseText, JsonObject::class.java) } catch (e: Exception) { null }

                        if (json != null && json.has("data") && json.getAsJsonObject("data")?.has("movies") == true) {
                            val movies = json.getAsJsonObject("data").getAsJsonArray("movies")
                            if (movies != null && movies.size() > 0) {
                                for (i in 0 until minOf(movies.size(), 10)) {
                                    val movie = movies.get(i).asJsonObject
                                    val movieTitle = movie.get("title")?.asString ?: query
                                    val torrents = movie.getAsJsonArray("torrents")
                                    if (torrents != null) {
                                        for (j in 0 until torrents.size()) {
                                            val t = torrents.get(j).asJsonObject
                                            val quality = t.get("quality")?.asString ?: "1080p"
                                            val size = t.get("size")?.asString ?: "1.5 GB"
                                            val seeds = t.get("seeds")?.asInt ?: 0
                                            val hash = t.get("hash")?.asString ?: ""
                                            if (hash.isNotBlank()) {
                                                val rawMagnet = "magnet:?xt=urn:btih:$hash&dn=${URLEncoder.encode(movieTitle, StandardCharsets.UTF_8.name())}"
                                                val magnet = TorrentTrackersManager.augmentMagnet(rawMagnet)
                                                resultsList.add(
                                                    TelegramSearchResult(
                                                        title = "$movieTitle [$quality]",
                                                        size = size,
                                                        seeds = seeds,
                                                        source = "YTS",
                                                        magnet = magnet
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                                foundDirectYts = true
                                break // Successfully obtained results from working mirror
                            }
                        }
                    } catch (e: Exception) {
                        Logger.w(LogTag.TELEGRAM, "YTS mirror ($mirror) error: ${e.javaClass.simpleName} - ${e.message ?: "Failed"}")
                    }
                }

                // If direct YTS mirrors are blocked by ISP (NXDOMAIN or DPI drop), fallback to extracting YTS/YIFY releases from TPB index
                if (!foundDirectYts) {
                    try {
                        val apibayUrl = "https://apibay.org/q.php?q=$encodedQuery"
                        val responseText = withTimeoutOrNull(3500L) {
                            client.get(apibayUrl) {
                                headers.append(HttpHeaders.UserAgent, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                            }.bodyAsText()
                        }
                        if (responseText != null && responseText.trimStart().startsWith("[")) {
                            val results = gson.fromJson(responseText, JsonArray::class.java)
                            if (results != null && results.size() > 0) {
                                for (i in 0 until minOf(results.size(), 20)) {
                                    val item = results.get(i).asJsonObject
                                    val name = item.get("name")?.asString ?: ""
                                    val isYify = name.contains("YIFY", ignoreCase = true) || name.contains("YTS", ignoreCase = true)
                                    if (isYify) {
                                        val infoHash = item.get("info_hash")?.asString ?: ""
                                        val sizeBytes = item.get("size")?.asLong ?: 0L
                                        val seeds = item.get("seeders")?.asInt ?: 0
                                        if (infoHash.isNotBlank() && infoHash != "0000000000000000000000000000000000000000") {
                                            val encodedName = URLEncoder.encode(name, StandardCharsets.UTF_8.name())
                                            val rawMagnet = "magnet:?xt=urn:btih:$infoHash&dn=$encodedName"
                                            val magnet = TorrentTrackersManager.augmentMagnet(rawMagnet)
                                            resultsList.add(
                                                TelegramSearchResult(
                                                    title = name,
                                                    size = formatSize(sizeBytes),
                                                    seeds = seeds,
                                                    source = "YTS/YIFY",
                                                    magnet = magnet
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Logger.w(LogTag.TELEGRAM, "YTS fallback error: ${e.message}")
                    }
                }
            }
        }

        val solidJob = botScope.async {
            if (TorrentTrackersManager.isIndexerEnabled("solid")) {
                try {
                    val solidUrl = "https://solidtorrents.net/api/v1/search?q=$encodedQuery&category=all"
                    val responseText = withTimeoutOrNull(3500L) {
                        client.get(solidUrl) {
                            headers.append(HttpHeaders.UserAgent, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        }.bodyAsText()
                    } ?: return@async

                    if (!responseText.trimStart().startsWith("{")) return@async
                    val json = gson.fromJson(responseText, JsonObject::class.java)

                    if (json.has("results")) {
                        val items = json.getAsJsonArray("results")
                        for (i in 0 until minOf(items.size(), 15)) {
                            val item = items.get(i).asJsonObject
                            val title = item.get("title")?.asString ?: query
                            val magnet = item.get("magnet")?.asString ?: ""
                            val sizeBytes = item.get("size")?.asLong ?: 0L
                            val swarm = item.getAsJsonObject("swarm")
                            val seeds = swarm?.get("seeders")?.asInt ?: 0
                            if (magnet.isNotBlank()) {
                                resultsList.add(
                                    TelegramSearchResult(
                                        title = title,
                                        size = formatSize(sizeBytes),
                                        seeds = seeds,
                                        source = "SolidTorrents",
                                        magnet = TorrentTrackersManager.augmentMagnet(magnet)
                                    )
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    Logger.w(LogTag.TELEGRAM, "SolidTorrents search error: ${e.message}")
                }
            }
        }

        val tpbJob = botScope.async {
            if (TorrentTrackersManager.isIndexerEnabled("tpb")) {
                try {
                    val apibayUrl = "https://apibay.org/q.php?q=$encodedQuery"
                    val responseText = withTimeoutOrNull(4000L) {
                        client.get(apibayUrl) {
                            headers.append(HttpHeaders.UserAgent, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        }.bodyAsText()
                    } ?: return@async

                    if (!responseText.trimStart().startsWith("[")) return@async
                    val results = gson.fromJson(responseText, JsonArray::class.java)

                    if (results != null && results.size() > 0) {
                        for (i in 0 until minOf(results.size(), 15)) {
                            val item = results.get(i).asJsonObject
                            val infoHash = item.get("info_hash")?.asString ?: ""
                            val name = item.get("name")?.asString ?: query
                            val sizeBytes = item.get("size")?.asLong ?: 0L
                            val seeds = item.get("seeders")?.asInt ?: 0
                            if (infoHash.isNotBlank() && infoHash != "0000000000000000000000000000000000000000") {
                                val encodedName = URLEncoder.encode(name, StandardCharsets.UTF_8.name())
                                val rawMagnet = "magnet:?xt=urn:btih:$infoHash&dn=$encodedName"
                                val magnet = TorrentTrackersManager.augmentMagnet(rawMagnet)
                                resultsList.add(
                                    TelegramSearchResult(
                                        title = name,
                                        size = formatSize(sizeBytes),
                                        seeds = seeds,
                                        source = "PirateBay",
                                        magnet = magnet
                                    )
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    Logger.w(LogTag.TELEGRAM, "apibay search error: ${e.message}")
                }
            }
        }

        val torrentsCsvJob = botScope.async {
            if (TorrentTrackersManager.isIndexerEnabled("torrents-csv")) {
                try {
                    val csvUrl = "https://torrents-csv.com/service/search?q=$encodedQuery"
                    val responseText = withTimeoutOrNull(4000L) {
                        client.get(csvUrl) {
                            headers.append(HttpHeaders.UserAgent, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        }.bodyAsText()
                    } ?: return@async

                    if (!responseText.trimStart().startsWith("{")) return@async
                    val json = gson.fromJson(responseText, JsonObject::class.java)

                    if (json.has("torrents")) {
                        val torrents = json.getAsJsonArray("torrents")
                        for (i in 0 until minOf(torrents.size(), 15)) {
                            val item = torrents.get(i).asJsonObject
                            val infohash = item.get("infohash")?.asString ?: ""
                            val name = item.get("name")?.asString ?: query
                            val sizeBytes = item.get("size_bytes")?.asLong ?: 0L
                            val seeds = item.get("seeders")?.asInt ?: 0
                            if (infohash.isNotBlank()) {
                                val encodedName = URLEncoder.encode(name, StandardCharsets.UTF_8.name())
                                val rawMagnet = "magnet:?xt=urn:btih:$infohash&dn=$encodedName"
                                val magnet = TorrentTrackersManager.augmentMagnet(rawMagnet)
                                resultsList.add(
                                    TelegramSearchResult(
                                        title = name,
                                        size = formatSize(sizeBytes),
                                        seeds = seeds,
                                        source = "TorrentsCSV",
                                        magnet = magnet
                                    )
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    Logger.w(LogTag.TELEGRAM, "TorrentsCSV search error: ${e.message}")
                }
            }
        }

        val eztvJob = botScope.async {
            if (TorrentTrackersManager.isIndexerEnabled("eztv")) {
                try {
                    val eztvUrl = "https://eztv.re/api/get-torrents?limit=10&query=$encodedQuery"
                    val responseText = withTimeoutOrNull(4000L) {
                        client.get(eztvUrl).bodyAsText()
                    } ?: return@async

                    if (!responseText.trimStart().startsWith("{")) return@async
                    val json = gson.fromJson(responseText, JsonObject::class.java)

                    if (json.has("torrents")) {
                        val torrents = json.getAsJsonArray("torrents")
                        for (i in 0 until minOf(torrents.size(), 8)) {
                            val item = torrents.get(i).asJsonObject
                            val magnetUrl = item.get("magnet_url")?.asString
                            if (!magnetUrl.isNullOrBlank()) {
                                val title = item.get("title")?.asString ?: query
                                val sizeBytes = item.get("size_bytes")?.asLong ?: 0L
                                val seeds = item.get("seeds")?.asInt ?: 0
                                resultsList.add(
                                    TelegramSearchResult(
                                        title = title,
                                        size = formatSize(sizeBytes),
                                        seeds = seeds,
                                        source = "EZTV",
                                        magnet = TorrentTrackersManager.augmentMagnet(magnetUrl)
                                    )
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    Logger.w(LogTag.TELEGRAM, "EZTV search error: ${e.message}")
                }
            }
        }

        val jackettJob = botScope.async {
            if (TorrentTrackersManager.isIndexerEnabled("jackett")) {
                try {
                    var jackettUrl = appPrefs?.getString("jackett_url", "")?.trim() ?: ""
                    var jackettApiKey = appPrefs?.getString("jackett_api_key", "")?.trim() ?: ""
                    if (jackettUrl.isBlank()) {
                        jackettUrl = "http://127.0.0.1:9117"
                    }
                    if (jackettApiKey.isBlank()) {
                        jackettApiKey = com.remotemedia.services.JackettServer.getApiKey()
                    }
                    if (jackettUrl.isNotBlank() && jackettApiKey.isNotBlank()) {
                        val url = "$jackettUrl/api/v2.0/indexers/all/results?apikey=$jackettApiKey&Query=$encodedQuery"
                        val responseText = withTimeoutOrNull(4000L) {
                            client.get(url) {
                                headers.append(HttpHeaders.UserAgent, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                            }.bodyAsText()
                        } ?: return@async

                        if (!responseText.trimStart().startsWith("{")) return@async
                        val json = gson.fromJson(responseText, JsonObject::class.java)
                        if (json.has("Results")) {
                            val items = json.getAsJsonArray("Results")
                            for (i in 0 until minOf(items.size(), 25)) {
                                val item = items.get(i).asJsonObject
                                val title = item.get("Title")?.asString ?: query
                                val sizeBytes = item.get("Size")?.asLong ?: 0L
                                val seeds = item.get("Seeders")?.asInt ?: 0
                                val magnet = item.get("MagnetUri")?.asString ?: ""
                                val link = item.get("Link")?.asString ?: ""
                                val targetUri = if (magnet.startsWith("magnet:?")) magnet else link
                                val tracker = item.get("Tracker")?.asString ?: "Jackett"
                                if (targetUri.isNotBlank()) {
                                    val finalMagnet = if (targetUri.startsWith("magnet:?")) TorrentTrackersManager.augmentMagnet(targetUri) else targetUri
                                    resultsList.add(
                                        TelegramSearchResult(
                                            title = title,
                                            size = formatSize(sizeBytes),
                                            seeds = seeds,
                                            source = tracker,
                                            magnet = finalMagnet
                                        )
                                    )
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Logger.w(LogTag.TELEGRAM, "Jackett search error: ${e.message}")
                }
            }
        }

        // Await all parallel search jobs
        ytsJob.await()
        solidJob.await()
        tpbJob.await()
        torrentsCsvJob.await()
        eztvJob.await()
        jackettJob.await()

        return resultsList.distinctBy { it.magnet }.sortedByDescending { it.seeds }
    }

    private suspend fun sendMessage(
        chatId: Long,
        text: String,
        parseMode: String? = null,
        replyMarkup: Map<String, Any>? = null,
        autoDeleteSeconds: Int = 0
    ): Long {
        try {
            val url = "$apiEndpoint/bot$botToken/sendMessage"
            val payload = mutableMapOf<String, Any>(
                "chat_id" to chatId,
                "text" to text
            )
            if (parseMode != null) payload["parse_mode"] = parseMode
            if (replyMarkup != null) payload["reply_markup"] = replyMarkup

            val resp = withTimeoutOrNull(10000L) {
                client.post(url) {
                    contentType(ContentType.Application.Json)
                    setBody(gson.toJson(payload))
                }
            } ?: run {
                Logger.w(LogTag.TELEGRAM, "sendMessage timed out after 10s")
                return 0L
            }

            val responseText = resp.bodyAsText()
            val json = gson.fromJson(responseText, JsonObject::class.java)
            if (json.has("ok") && json.get("ok").asBoolean && json.has("result")) {
                val sentMsgId = json.getAsJsonObject("result").get("message_id").asLong
                recordMessageId(chatId, sentMsgId)

                if (autoDeleteSeconds > 0) {
                    botScope.launch {
                        delay(autoDeleteSeconds * 1000L)
                        deleteMessage(chatId, sentMsgId)
                    }
                }
                return sentMsgId
            } else {
                Logger.w(LogTag.TELEGRAM, "sendMessage HTTP ${resp.status.value}: ${responseText.take(120)}")
                if (parseMode != null) {
                    // Retry without parse_mode as clean plain text so user ALWAYS gets message
                    val fallbackText = text.replace("*", "").replace("`", "").replace("_", " ")
                    return sendMessage(chatId, fallbackText, null, replyMarkup, autoDeleteSeconds)
                }
            }
        } catch (e: Exception) {
            Logger.e(LogTag.TELEGRAM, "Failed to send Telegram message: ${e.message}")
            if (parseMode != null) {
                try {
                    val fallbackText = text.replace("*", "").replace("`", "").replace("_", " ")
                    return sendMessage(chatId, fallbackText, null, replyMarkup, autoDeleteSeconds)
                } catch (_: Exception) {}
            }
        }
        return 0L
    }

    private suspend fun editMessageText(
        chatId: Long,
        messageId: Long,
        text: String,
        parseMode: String? = null,
        replyMarkup: Map<String, Any>? = null
    ) {
        try {
            val url = "$apiEndpoint/bot$botToken/editMessageText"
            val payload = mutableMapOf<String, Any>(
                "chat_id" to chatId,
                "message_id" to messageId,
                "text" to text
            )
            if (parseMode != null) payload["parse_mode"] = parseMode
            if (replyMarkup != null) payload["reply_markup"] = replyMarkup

            val resp = withTimeoutOrNull(8000L) {
                client.post(url) {
                    contentType(ContentType.Application.Json)
                    setBody(gson.toJson(payload))
                }
            }
            if (resp != null && resp.status.value != 200 && parseMode != null) {
                // Retry plain text
                payload.remove("parse_mode")
                payload["text"] = text.replace("*", "").replace("`", "").replace("_", " ")
                client.post(url) {
                    contentType(ContentType.Application.Json)
                    setBody(gson.toJson(payload))
                }
            }
        } catch (e: Exception) {
            Logger.w(LogTag.TELEGRAM, "Failed to editMessageText: ${e.message}")
        }
    }

    private suspend fun deleteMessage(chatId: Long, messageId: Long) {
        if (messageId <= 0) return
        try {
            val url = "$apiEndpoint/bot$botToken/deleteMessage"
            val payload = mapOf(
                "chat_id" to chatId,
                "message_id" to messageId
            )
            client.post(url) {
                contentType(ContentType.Application.Json)
                setBody(gson.toJson(payload))
            }
        } catch (e: Exception) {
            Logger.w(LogTag.TELEGRAM, "Failed to delete message $messageId: ${e.message}")
        }
    }

    private suspend fun answerCallbackQuery(callbackQueryId: String, text: String) {
        if (callbackQueryId.isBlank()) return
        try {
            val url = "$apiEndpoint/bot$botToken/answerCallbackQuery"
            val payload = mapOf(
                "callback_query_id" to callbackQueryId,
                "text" to text
            )
            client.post(url) {
                contentType(ContentType.Application.Json)
                setBody(gson.toJson(payload))
            }
        } catch (e: Exception) {
            Logger.w(LogTag.TELEGRAM, "Failed to answer callback query: ${e.message}")
        }
    }

    private suspend fun clearEntireChat(chatId: Long, triggerMessageId: Long) {
        Logger.i(LogTag.TELEGRAM, "Clearing entire Telegram chat history for chatId: $chatId...")

        val historyList = chatMessageHistory[chatId] ?: emptyList()
        val allMessageIds = historyList.toSet().toMutableSet()

        // Scan backwards for last 100 message IDs to purge chat
        if (triggerMessageId > 0) {
            for (id in triggerMessageId downTo maxOf(1L, triggerMessageId - 100)) {
                allMessageIds.add(id)
            }
        }

        for (msgId in allMessageIds) {
            deleteMessage(chatId, msgId)
            delay(40) // avoid rate limits
        }

        chatMessageHistory[chatId]?.clear()

        val confirmMsgId = sendMessage(chatId, "🧹 *Chat cleared successfully!*", parseMode = "Markdown", autoDeleteSeconds = 5)
    }

    private fun recordMessageId(chatId: Long, messageId: Long) {
        val list = chatMessageHistory.getOrPut(chatId) { CopyOnWriteArrayList() }
        if (!list.contains(messageId)) {
            list.add(messageId)
            if (list.size > 200) {
                list.removeAt(0)
            }
        }
    }

    fun notifyDownloadFinished(chatId: Long, statusMsgId: Long, fileName: String, totalBytes: Long) {
        botScope.launch {
            if (statusMsgId > 0) {
                editMessageText(
                    chatId = chatId,
                    messageId = statusMsgId,
                    text = "✅ **Download Complete!**\n\n" +
                           "📄 **File:** `$fileName`\n" +
                           "💾 **Size:** ${formatSize(totalBytes)}\n" +
                           "📁 **Saved to:** `Movies/`\n\n" +
                           "🎬 *Ready to stream on Jellyfin & UPnP!*",
                    parseMode = "Markdown"
                )
            } else {
                sendMessage(
                    chatId = chatId,
                    text = "✅ **Download Complete!**\n\n" +
                           "📄 **File:** `$fileName`\n" +
                           "💾 **Size:** ${formatSize(totalBytes)}\n" +
                           "📁 **Saved to:** `Movies/`",
                    parseMode = "Markdown"
                )
            }
        }
    }

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format("%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }
}
