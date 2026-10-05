package com.remotemedia.services

import com.google.gson.Gson
import com.remotemedia.core.HostMediaManager
import com.remotemedia.core.NetworkShareManager
import com.remotemedia.core.LogTag
import com.remotemedia.core.Logger
import com.remotemedia.core.StorageManager
import com.remotemedia.core.MeshPeerDeviceType
import com.remotemedia.core.MeshPeerNode
import com.remotemedia.core.MeshShareManager
import com.remotemedia.core.MeshVirtualMediaFile
import com.remotemedia.core.PlaybackProgressManager
import com.remotemedia.download.TelegramBotEngine
import com.remotemedia.download.TorrentEngine
import com.remotemedia.download.TorrentStreamManager
import com.remotemedia.download.TorrentTrackersManager
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.streamProvider
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.autohead.AutoHeadResponse
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.partialcontent.PartialContent
import io.ktor.server.request.receiveMultipart
import io.ktor.server.request.receiveParameters
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respondFile
import io.ktor.server.response.respondOutputStream
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import com.remotemedia.core.FederatedMediaManager
import com.remotemedia.core.MediaThumbnailManager
import io.ktor.server.response.respondBytes
import java.io.File
import java.io.RandomAccessFile
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID

object WebHttpServer {

    private var serverEngine: ApplicationEngine? = null
    private val gson = Gson()

    fun start(port: Int = 8080) {
        if (serverEngine != null) return

        try {
            serverEngine = embeddedServer(CIO, host = "0.0.0.0", port = port) {
                install(CORS) {
                    anyHost()
                    allowNonSimpleContentTypes = true
                }
                install(PartialContent)
                install(AutoHeadResponse)

                routing {
                    // HTML5 Web UI File Browser & Player
                    get("/") {
                        val pathParam = call.request.queryParameters["path"] ?: ""
                        val decodedPath = URLDecoder.decode(pathParam, StandardCharsets.UTF_8.name())
                        val targetDir = StorageManager.resolveRelativePath(decodedPath) ?: StorageManager.getBaseDir()

                        Logger.i(LogTag.HTTP, "Web UI request for folder: ${targetDir.name}")
                        val html = renderWebDashboard(targetDir, pathParam)
                        call.respondText(html, ContentType.Text.Html)
                    }

                    // Direct File Download Endpoint
                    get("/download") {
                        val pathParam = call.request.queryParameters["path"] ?: ""
                        val decodedPath = URLDecoder.decode(pathParam, StandardCharsets.UTF_8.name())
                        val targetFile = StorageManager.resolveRelativePath(decodedPath)

                        if (targetFile != null && targetFile.exists() && targetFile.isFile) {
                            Logger.i(LogTag.HTTP, "Downloading file: ${targetFile.name}")
                            call.response.header(
                                HttpHeaders.ContentDisposition,
                                ContentDisposition.Attachment.withParameter(
                                    ContentDisposition.Parameters.FileName,
                                    targetFile.name
                                ).toString()
                            )
                            call.respondFile(targetFile)
                        } else {
                            call.respondText("File not found or access denied.", status = HttpStatusCode.NotFound)
                        }
                    }

                    // Direct Media Stream Endpoint (Partial Content)
                    get("/stream") {
                        val pathParam = call.request.queryParameters["path"] ?: ""
                        val decodedPath = URLDecoder.decode(pathParam, StandardCharsets.UTF_8.name())
                        val targetFile = StorageManager.resolveRelativePath(decodedPath)

                        if (targetFile != null && targetFile.exists() && targetFile.isFile) {
                            Logger.i(LogTag.HTTP, "Streaming file: ${targetFile.name}")
                            call.response.header(
                                HttpHeaders.ContentDisposition,
                                ContentDisposition.Inline.withParameter(
                                    ContentDisposition.Parameters.FileName,
                                    targetFile.name
                                ).toString()
                            )
                            call.respondFile(targetFile)
                        } else {
                            call.respondText("File not found or access denied.", status = HttpStatusCode.NotFound)
                        }
                    }

                    // Direct Video Playback Endpoints (Sandbox, Local, Federated)
                    get("/video/{name...}") { respondSandboxVideo(call) }
                    get("/Videos/{id}/stream") { respondSandboxVideo(call) }
                    get("/Videos/{id}/stream.{container}") { respondSandboxVideo(call) }
                    get("/videos/{id}/stream") { respondSandboxVideo(call) }
                    get("/videos/{id}/stream.{container}") { respondSandboxVideo(call) }
                    get("/Videos/{id}/thumbnail") { respondThumbnail(call) }
                    get("/Videos/{id}/thumbnail.jpg") { respondThumbnail(call) }
                    get("/videos/{id}/thumbnail") { respondThumbnail(call) }
                    get("/videos/{id}/thumbnail.jpg") { respondThumbnail(call) }

                    // Direct Stream for Host Mobile Shared Files
                    get("/hostmedia/stream") {
                        val fileId = call.request.queryParameters["id"] ?: ""
                        val vFile = HostMediaManager.getVirtualFileById(fileId)
                        if (vFile != null) {
                            val direct = HostMediaManager.getDirectFile(vFile)
                            if (direct != null && direct.exists()) {
                                call.response.header(
                                    HttpHeaders.ContentDisposition,
                                    ContentDisposition.Inline.withParameter(ContentDisposition.Parameters.FileName, vFile.name).toString()
                                )
                                call.respondFile(direct)
                            } else {
                                val ctx = StorageManager.getContext()
                                if (ctx != null) {
                                    val stream = HostMediaManager.openInputStream(ctx, vFile)
                                    if (stream != null) {
                                        call.response.header(
                                            HttpHeaders.ContentDisposition,
                                            ContentDisposition.Inline.withParameter(ContentDisposition.Parameters.FileName, vFile.name).toString()
                                        )
                                        val cType = runCatching { ContentType.parse(vFile.mimeType) }.getOrDefault(ContentType.Application.OctetStream)
                                        call.respondOutputStream(cType, HttpStatusCode.OK) {
                                            stream.use { it.copyTo(this) }
                                        }
                                        return@get
                                    }
                                }
                                call.respondText("File not readable", status = HttpStatusCode.NotFound)
                            }
                        } else {
                            call.respondText("Host media not found", status = HttpStatusCode.NotFound)
                        }
                    }

                    // Direct Stream for LAN Network Shared Files (PC / NAS SMB)
                    get("/netmedia/stream") {
                        val fileId = call.request.queryParameters["id"] ?: ""
                        val nFile = NetworkShareManager.getVirtualFileById(fileId)
                        if (nFile != null) {
                            val stream = NetworkShareManager.openInputStream(nFile)
                            if (stream != null) {
                                call.response.header(
                                    HttpHeaders.ContentDisposition,
                                    ContentDisposition.Inline.withParameter(ContentDisposition.Parameters.FileName, nFile.name).toString()
                                )
                                val cType = runCatching { ContentType.parse(nFile.mimeType) }.getOrDefault(ContentType.Application.OctetStream)
                                call.respondOutputStream(cType, HttpStatusCode.OK) {
                                    stream.use { it.copyTo(this) }
                                }
                                return@get
                            } else {
                                call.respondText("File not readable", status = HttpStatusCode.NotFound)
                            }
                        } else {
                            call.respondText("Network file not found", status = HttpStatusCode.NotFound)
                        }
                    }

                    // JSON API for directory listing
                    get("/api/files") {
                        val pathParam = call.request.queryParameters["path"] ?: ""
                        val decodedPath = URLDecoder.decode(pathParam, StandardCharsets.UTF_8.name())
                        val targetDir = StorageManager.resolveRelativePath(decodedPath) ?: StorageManager.getBaseDir()

                        val filesList = StorageManager.listFiles(targetDir).map { file ->
                            mapOf(
                                "name" to file.name,
                                "path" to getRelativePath(file),
                                "isDirectory" to file.isDirectory,
                                "size" to file.length(),
                                "formattedSize" to formatSize(file.length())
                            )
                        }
                        call.respondText(gson.toJson(filesList), ContentType.Application.Json)
                    }

                    // Direct Stream for Client Mesh Peer Files (iOS / PC / Browser)
                    get("/meshmedia/stream") {
                        val fileId = call.request.queryParameters["id"] ?: ""
                        val mFile = MeshShareManager.getVirtualFileById(fileId)
                        if (mFile != null) {
                            val stream = MeshShareManager.openInputStream(mFile)
                            if (stream != null) {
                                call.response.header(
                                    HttpHeaders.ContentDisposition,
                                    ContentDisposition.Inline.withParameter(ContentDisposition.Parameters.FileName, mFile.name).toString()
                                )
                                val cType = runCatching { ContentType.parse(mFile.mimeType) }.getOrDefault(ContentType.Application.OctetStream)
                                call.respondOutputStream(cType, HttpStatusCode.OK) {
                                    stream.use { it.copyTo(this) }
                                }
                                return@get
                            } else {
                                call.respondText("Mesh file stream unavailable", status = HttpStatusCode.NotFound)
                            }
                        } else {
                            call.respondText("Mesh file not found", status = HttpStatusCode.NotFound)
                        }
                    }
                    // ── Telegram Media Catalog API ──────────────────────────────
                    get("/api/telegram/media") {
                        val items = com.remotemedia.download.TelegramMediaCatalog.items.value
                        val responseJson = gson.toJson(mapOf("ok" to true, "items" to items))
                        call.respondText(responseJson, ContentType.Application.Json)
                    }

                    get("/api/telegram_catalog") {
                        val items = com.remotemedia.download.TelegramMediaCatalog.items.value
                        val responseJson = gson.toJson(items)
                        call.respondText(responseJson, ContentType.Application.Json)
                    }

                    // ── Trending / Recommended Torrents API ─────────────────────
                    get("/api/torrents/trending") {
                        val trending = getTrendingTorrents()
                        val responseJson = gson.toJson(mapOf("ok" to true, "items" to trending))
                        call.respondText(responseJson, ContentType.Application.Json)
                    }

                    get("/api/trending") {
                        val trending = getTrendingTorrents()
                        val responseJson = gson.toJson(trending)
                        call.respondText(responseJson, ContentType.Application.Json)
                    }

                    // ── Unified Search across Torrent, Telegram, and Sandbox ────
                    get("/api/search") {
                        val query = call.request.queryParameters["q"]?.trim() ?: ""
                        if (query.isBlank()) {
                            call.respondText(gson.toJson(mapOf("ok" to true, "query" to "", "results" to emptyList<UnifiedMediaSearchResult>())), ContentType.Application.Json)
                            return@get
                        }

                        val results = mutableListOf<UnifiedMediaSearchResult>()

                        // 1. Sandbox / Local Movies Storage
                        try {
                            val moviesDir = StorageManager.getMoviesDir()
                            val localFiles = moviesDir.listFiles()?.filter { f ->
                                f.isFile && f.length() > 0 &&
                                (f.name.endsWith(".mp4", ignoreCase = true) ||
                                 f.name.endsWith(".mkv", ignoreCase = true) ||
                                 f.name.endsWith(".avi", ignoreCase = true) ||
                                 f.name.endsWith(".webm", ignoreCase = true)) &&
                                f.name.contains(query, ignoreCase = true)
                            } ?: emptyList()

                            for (file in localFiles) {
                                val clean = file.nameWithoutExtension.replace(".", " ")
                                val year = Regex("""\b(19\d\d|20\d\d)\b""").find(file.name)?.value ?: ""
                                val quality = when {
                                    file.name.contains("2160p", true) || file.name.contains("4k", true) -> "4K UHD"
                                    file.name.contains("1080p", true) -> "1080p"
                                    file.name.contains("720p", true) -> "720p"
                                    else -> "HD"
                                }
                                results.add(
                                    UnifiedMediaSearchResult(
                                        id = "local_${file.name.hashCode()}",
                                        title = clean,
                                        source = "SANDBOX",
                                        size = formatSize(file.length()),
                                        quality = quality,
                                        year = year,
                                        streamUrl = "/video/${file.name}",
                                        isLocal = true
                                    )
                                )
                            }
                        } catch (e: Exception) {
                            Logger.w(LogTag.HTTP, "Local search error: ${e.message}")
                        }

                        // 2. Telegram Media Catalog (Saved Messages & Bot Forwarded)
                        try {
                            val tgCatalog = com.remotemedia.download.TelegramMediaCatalog.items.value
                            val tgMatches = tgCatalog.filter {
                                it.cleanTitle.contains(query, ignoreCase = true) ||
                                it.fileName.contains(query, ignoreCase = true)
                            }
                            for (item in tgMatches) {
                                val localCandidate = File(StorageManager.getMoviesDir(), item.fileName)
                                val existsLocally = localCandidate.exists() && localCandidate.length() > 0
                                results.add(
                                    UnifiedMediaSearchResult(
                                        id = item.id,
                                        title = item.cleanTitle,
                                        source = "TELEGRAM",
                                        size = item.sizeFormatted,
                                        quality = item.quality,
                                        year = item.year,
                                        posterUrl = item.posterUrl,
                                        streamUrl = item.streamUrl,
                                        fileId = item.fileId,
                                        isLocal = existsLocally
                                    )
                                )
                            }
                        } catch (e: Exception) {
                            Logger.w(LogTag.HTTP, "Telegram search error: ${e.message}")
                        }

                        // 3. Torrents (Jackett indexers + Trending catalog)
                        try {
                            val jackettResults = try {
                                JackettServer.searchAllIndexers(query)
                            } catch (_: Exception) {
                                emptyList()
                            }

                            if (jackettResults.isNotEmpty()) {
                                for (jr in jackettResults.take(15)) {
                                    val encodedMagnet = try { URLEncoder.encode(jr.magnetUrl, "UTF-8") } catch (_: Exception) { jr.magnetUrl }
                                    val encodedTitle = try { URLEncoder.encode(jr.title, "UTF-8") } catch (_: Exception) { jr.title }
                                    results.add(
                                        UnifiedMediaSearchResult(
                                            id = "jackett_${jr.guid}",
                                            title = jr.title,
                                            source = "TORRENT",
                                            size = formatSize(jr.sizeBytes),
                                            quality = if (jr.title.contains("2160p", true) || jr.title.contains("4k", true)) "4K" else "1080p",
                                            streamUrl = "/stream/torrent?magnet=$encodedMagnet&title=$encodedTitle",
                                            magnetUri = jr.magnetUrl,
                                            seeds = jr.seeders,
                                            isLocal = false
                                        )
                                    )
                                }
                            } else {
                                val trending = getTrendingTorrents().filter {
                                    it.title.contains(query, ignoreCase = true) ||
                                    it.genre.contains(query, ignoreCase = true)
                                }
                                for (t in trending) {
                                    val encodedMagnet = try { URLEncoder.encode(t.magnetUri, "UTF-8") } catch (_: Exception) { t.magnetUri }
                                    val encodedTitle = try { URLEncoder.encode(t.title, "UTF-8") } catch (_: Exception) { t.title }
                                    results.add(
                                        UnifiedMediaSearchResult(
                                            id = t.id,
                                            title = t.title,
                                            source = "TORRENT",
                                            size = t.size,
                                            quality = t.quality,
                                            year = t.year,
                                            streamUrl = "/stream/torrent?magnet=$encodedMagnet&title=$encodedTitle",
                                            magnetUri = t.magnetUri,
                                            seeds = t.seeds,
                                            isLocal = false
                                        )
                                    )
                                }
                            }
                        } catch (e: Exception) {
                            Logger.w(LogTag.HTTP, "Torrent search error: ${e.message}")
                        }

                        val responseJson = gson.toJson(mapOf("ok" to true, "query" to query, "results" to results))
                        call.respondText(responseJson, ContentType.Application.Json)
                    }

                    // ── Save/Download Torrent to Server independently ───────────
                    get("/api/torrent/download") {
                        val magnet = call.request.queryParameters["magnet"] ?: ""
                        val title = call.request.queryParameters["title"] ?: ""
                        if (magnet.isBlank()) {
                            call.respondText(gson.toJson(mapOf("ok" to false, "message" to "Missing magnet")), ContentType.Application.Json, HttpStatusCode.BadRequest)
                            return@get
                        }
                        TorrentEngine.downloadMagnet(magnet, title)
                        call.respondText(gson.toJson(mapOf("ok" to true, "message" to "Torrent download started on server")), ContentType.Application.Json)
                    }

                    post("/api/torrent/download") {
                        val params = runCatching { call.receiveParameters() }.getOrNull()
                        val magnet = params?.get("magnet") ?: call.request.queryParameters["magnet"] ?: ""
                        val title = params?.get("title") ?: call.request.queryParameters["title"] ?: ""
                        if (magnet.isBlank()) {
                            call.respondText(gson.toJson(mapOf("ok" to false, "message" to "Missing magnet")), ContentType.Application.Json, HttpStatusCode.BadRequest)
                            return@post
                        }
                        TorrentEngine.downloadMagnet(magnet, title)
                        call.respondText(gson.toJson(mapOf("ok" to true, "message" to "Torrent download started on server")), ContentType.Application.Json)
                    }

                    // ── Save/Download Telegram File to Server independently ─────
                    get("/api/telegram/download") {
                        val fileId = call.request.queryParameters["file_id"] ?: ""
                        val name = call.request.queryParameters["name"] ?: ""
                        val size = call.request.queryParameters["size"]?.toLongOrNull() ?: 0L
                        if (fileId.isBlank()) {
                            call.respondText(gson.toJson(mapOf("ok" to false, "message" to "Missing file_id")), ContentType.Application.Json, HttpStatusCode.BadRequest)
                            return@get
                        }
                        val catalogItem = com.remotemedia.download.TelegramMediaCatalog.items.value.find { it.fileId == fileId }
                        val resolvedName = name.ifBlank { catalogItem?.fileName ?: "telegram_media.mp4" }
                        val resolvedSize = if (size > 0) size else (catalogItem?.fileSize ?: 0L)

                        com.remotemedia.download.TelegramBotEngine.downloadTelegramFile(fileId, resolvedName, resolvedSize)
                        call.respondText(gson.toJson(mapOf("ok" to true, "message" to "Telegram file download queued on server")), ContentType.Application.Json)
                    }

                    post("/api/telegram/download") {
                        val params = runCatching { call.receiveParameters() }.getOrNull()
                        val fileId = params?.get("file_id") ?: call.request.queryParameters["file_id"] ?: ""
                        val name = params?.get("name") ?: call.request.queryParameters["name"] ?: ""
                        val size = (params?.get("size") ?: call.request.queryParameters["size"])?.toLongOrNull() ?: 0L
                        if (fileId.isBlank()) {
                            call.respondText(gson.toJson(mapOf("ok" to false, "message" to "Missing file_id")), ContentType.Application.Json, HttpStatusCode.BadRequest)
                            return@post
                        }
                        val catalogItem = com.remotemedia.download.TelegramMediaCatalog.items.value.find { it.fileId == fileId }
                        val resolvedName = name.ifBlank { catalogItem?.fileName ?: "telegram_media.mp4" }
                        val resolvedSize = if (size > 0) size else (catalogItem?.fileSize ?: 0L)

                        com.remotemedia.download.TelegramBotEngine.downloadTelegramFile(fileId, resolvedName, resolvedSize)
                        call.respondText(gson.toJson(mapOf("ok" to true, "message" to "Telegram file download queued on server")), ContentType.Application.Json)
                    }

                    // ── Playback Progress & Resume Tracking ─────────────────────
                    get("/api/playback/last") {
                        val last = PlaybackProgressManager.getLastPlayed()
                        if (last != null) {
                            val map = mapOf(
                                "itemId" to last.itemId,
                                "title" to last.title,
                                "streamUrl" to last.streamUrl,
                                "positionSeconds" to last.positionSeconds,
                                "durationSeconds" to last.durationSeconds,
                                "percent" to last.percent,
                                "lastPlayedTimestamp" to last.lastPlayedTimestamp
                            )
                            call.respondText(gson.toJson(map), ContentType.Application.Json)
                        } else {
                            call.respondText("{}", ContentType.Application.Json)
                        }
                    }

                    get("/api/playback/resume") {
                        val resumeItems = PlaybackProgressManager.getAllResumeItems()
                        call.respondText(gson.toJson(mapOf("items" to resumeItems, "count" to resumeItems.size)), ContentType.Application.Json)
                    }

                    post("/api/playback/progress") {
                        try {
                            val body = call.receiveText()
                            if (body.isNotBlank() && body.startsWith("{")) {
                                val json = gson.fromJson(body, com.google.gson.JsonObject::class.java)
                                val id = json.get("id")?.asString ?: json.get("itemId")?.asString ?: ""
                                val title = json.get("title")?.asString ?: ""
                                val streamUrl = json.get("streamUrl")?.asString ?: ""
                                val posSec = json.get("positionSeconds")?.asLong
                                    ?: (json.get("PositionTicks")?.asLong?.let { it / 10_000_000L })
                                    ?: 0L
                                val durSec = json.get("durationSeconds")?.asLong
                                    ?: (json.get("RunTimeTicks")?.asLong?.let { it / 10_000_000L })
                                    ?: 0L
                                if (title.isNotBlank() || id.isNotBlank()) {
                                    PlaybackProgressManager.updateProgress(
                                        itemId = id,
                                        title = title.ifBlank { id },
                                        streamUrl = streamUrl,
                                        positionSeconds = posSec,
                                        durationSeconds = durSec
                                    )
                                }
                            }
                            call.respondText("""{"ok":true}""", ContentType.Application.Json)
                        } catch (e: Exception) {
                            call.respondText("""{"ok":false,"error":"${e.message}"}""", ContentType.Application.Json)
                        }
                    }

                    // ── Direct Telegram Video Stream Proxy (HTTP 206 Partial Content) ─
                    get("/stream/telegram") {
                        val fileId = call.request.queryParameters["file_id"] ?: ""
                        if (fileId.isBlank()) {
                            call.respondText("Missing file_id parameter", status = HttpStatusCode.BadRequest)
                            return@get
                        }

                        // Check if file is already downloaded in local storage
                        val catalogItem = com.remotemedia.download.TelegramMediaCatalog.items.value.find { it.fileId == fileId }
                        if (catalogItem != null) {
                            val localCandidate = File(StorageManager.getMoviesDir(), catalogItem.fileName)
                            if (localCandidate.exists() && localCandidate.length() > 0) {
                                call.response.header(HttpHeaders.AcceptRanges, "bytes")
                                call.respondFile(localCandidate)
                                return@get
                            }
                        }

                        // Stream on-the-fly without saving to disk
                        val streamUrl = try {
                            TelegramBotEngine.getDownloadUrlForFile(fileId)
                        } catch (e: Exception) {
                            null
                        }

                        if (streamUrl == null) {
                            call.respondText("Failed to resolve Telegram stream URL", status = HttpStatusCode.NotFound)
                            return@get
                        }

                        val rangeHeader = call.request.headers[HttpHeaders.Range]
                        try {
                            val url = java.net.URL(streamUrl)
                            val conn = url.openConnection() as java.net.HttpURLConnection
                            conn.connectTimeout = 10000
                            conn.readTimeout = 30000
                            if (rangeHeader != null) {
                                conn.setRequestProperty("Range", rangeHeader)
                            }

                            val responseCode = conn.responseCode
                            val status = if (responseCode == 206) HttpStatusCode.PartialContent else HttpStatusCode.OK
                            val rawContentType = conn.contentType
                            val contentType = if (!rawContentType.isNullOrBlank() && rawContentType.contains("/")) {
                                rawContentType
                            } else if (catalogItem != null && catalogItem.fileName.endsWith(".mkv", ignoreCase = true)) {
                                "video/x-matroska"
                            } else {
                                "video/mp4"
                            }
                            val contentLength = conn.contentLengthLong

                            call.response.header(HttpHeaders.AcceptRanges, "bytes")
                            conn.getHeaderField(HttpHeaders.ContentRange)?.let {
                                call.response.header(HttpHeaders.ContentRange, it)
                            }
                            if (contentLength > 0) {
                                call.response.header(HttpHeaders.ContentLength, contentLength.toString())
                            }

                            val inputStream = conn.inputStream
                            call.respondOutputStream(ContentType.parse(contentType), status) {
                                inputStream.use { it.copyTo(this) }
                            }
                        } catch (e: Exception) {
                            Logger.w(LogTag.HTTP, "Telegram streaming proxy error: ${e.message}")
                            call.respondText("Stream transfer error: ${e.message}", status = HttpStatusCode.InternalServerError)
                        }
                    }

                    // ── Stremio-Style Sequential Torrent Video Stream ──────────
                    get("/stream/torrent") {
                        val magnet = call.request.queryParameters["magnet"] ?: ""
                        val hash = call.request.queryParameters["hash"] ?: ""
                        val title = call.request.queryParameters["title"] ?: ""

                        val magnetUri = when {
                            magnet.isNotBlank() -> try { URLDecoder.decode(magnet, StandardCharsets.UTF_8.name()) } catch (_: Exception) { magnet }
                            hash.isNotBlank() -> "magnet:?xt=urn:btih:$hash&dn=${title.ifBlank { "TorrentStream" }}"
                            else -> ""
                        }

                        if (magnetUri.isBlank()) {
                            call.respondText("Missing magnet or hash parameter", status = HttpStatusCode.BadRequest)
                            return@get
                        }

                        val streamSession = TorrentStreamManager.prepareStream(magnetUri, title)
                        if (streamSession == null) {
                            call.respondText("Swarm resolving metadata or connecting to peers... Please retry.", status = HttpStatusCode.GatewayTimeout)
                            return@get
                        }

                        val totalSize = streamSession.fileSize
                        val rangeHeader = call.request.headers[HttpHeaders.Range]

                        var rangeStart = 0L
                        var rangeEnd = totalSize - 1

                        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                            val rangeValues = rangeHeader.removePrefix("bytes=").split("-")
                            rangeStart = rangeValues[0].toLongOrNull() ?: 0L
                            if (rangeValues.size > 1 && rangeValues[1].isNotBlank()) {
                                rangeEnd = rangeValues[1].toLongOrNull() ?: (totalSize - 1)
                            }
                        }
                        rangeEnd = minOf(rangeEnd, totalSize - 1)
                        val contentLength = rangeEnd - rangeStart + 1
                        val isPartial = rangeHeader != null

                        call.response.header(HttpHeaders.AcceptRanges, "bytes")
                        if (isPartial) {
                            call.response.header(HttpHeaders.ContentRange, "bytes $rangeStart-$rangeEnd/$totalSize")
                        }
                        call.response.header(HttpHeaders.ContentLength, contentLength.toString())

                        val mimeType = when {
                            streamSession.fileName.endsWith(".mkv", ignoreCase = true) -> "video/x-matroska"
                            streamSession.fileName.endsWith(".mp4", ignoreCase = true) -> "video/mp4"
                            streamSession.fileName.endsWith(".webm", ignoreCase = true) -> "video/webm"
                            streamSession.fileName.endsWith(".avi", ignoreCase = true) -> "video/x-msvideo"
                            else -> "video/mp4"
                        }
                        val status = if (isPartial) HttpStatusCode.PartialContent else HttpStatusCode.OK

                        call.respondOutputStream(ContentType.parse(mimeType), status) {
                            TorrentStreamManager.streamBytes(streamSession, rangeStart, rangeEnd, this)
                        }
                    }

                    get("/api/torrent/stream_status") {
                        val hash = call.request.queryParameters["hash"] ?: ""
                        val magnet = call.request.queryParameters["magnet"] ?: ""
                        val status = TorrentStreamManager.getStreamStatus(hash.ifBlank { magnet })
                        call.respondText(gson.toJson(status), ContentType.Application.Json)
                    }

                    // HTML5 Client Mesh Portal (iOS / Mac / PC / Android Web Browser)
                    get("/mesh") {
                        Logger.i(LogTag.HTTP, "Mesh UI Portal request from: ${call.request.origin.remoteHost}")
                        val html = renderMeshPortal()
                        call.respondText(html, ContentType.Text.Html)
                    }

                    get("/mesh/") {
                        val html = renderMeshPortal()
                        call.respondText(html, ContentType.Text.Html)
                    }

                    // ── M6: Client Mesh Peer Ingestion APIs ──────────────────────
                    // Register or update a mesh peer node
                    post("/api/mesh/register") {
                        val body = call.receiveText()
                        try {
                            val req = gson.fromJson(body, MeshRegisterRequest::class.java)
                            val clientIp = if (req.ipAddress.isNotBlank()) req.ipAddress else call.request.origin.remoteHost
                            val peer = MeshPeerNode(
                                id = if (req.id.isNotBlank()) req.id else UUID.randomUUID().toString(),
                                deviceName = if (req.deviceName.isNotBlank()) req.deviceName else "Mesh Client ($clientIp)",
                                deviceType = runCatching { MeshPeerDeviceType.valueOf(req.deviceType.uppercase()) }.getOrDefault(MeshPeerDeviceType.OTHER),
                                ipAddress = clientIp,
                                port = if (req.port > 0) req.port else 8085,
                                authKey = req.authKey,
                                sharedFolders = req.sharedFolders ?: emptyList()
                            )
                            val files = (req.files ?: emptyList()).map { f ->
                                MeshVirtualMediaFile(
                                    id = if (f.id.isNotBlank()) f.id else UUID.randomUUID().toString(),
                                    peerNodeId = peer.id,
                                    peerNodeName = peer.deviceName,
                                    name = f.name,
                                    category = if (f.category.isNotBlank()) f.category else MeshShareManager.categorizeMedia(f.name, f.mimeType),
                                    mimeType = if (f.mimeType.isNotBlank()) f.mimeType else "application/octet-stream",
                                    sizeBytes = f.sizeBytes,
                                    streamUrl = f.streamUrl,
                                    relativePath = f.relativePath ?: ""
                                )
                            }
                            MeshShareManager.registerOrUpdatePeer(peer, files)
                            call.respondText(
                                gson.toJson(mapOf("status" to "ok", "peerId" to peer.id, "fileCount" to files.size)),
                                ContentType.Application.Json
                            )
                        } catch (e: Exception) {
                            Logger.e(LogTag.HTTP, "Error registering mesh peer: ${e.message}")
                            call.respondText(
                                gson.toJson(mapOf("status" to "error", "message" to (e.message ?: "Invalid request"))),
                                ContentType.Application.Json,
                                HttpStatusCode.BadRequest
                            )
                        }
                    }

                    // Peer heartbeat keepalive
                    post("/api/mesh/heartbeat") {
                        val peerId = call.request.queryParameters["peerId"] ?: runCatching {
                            gson.fromJson(call.receiveText(), Map::class.java)["peerId"]?.toString()
                        }.getOrNull() ?: ""
                        val success = MeshShareManager.updateHeartbeat(peerId)
                        call.respondText(
                            gson.toJson(mapOf("status" to if (success) "ok" else "not_found", "isOnline" to success)),
                            ContentType.Application.Json
                        )
                    }

                    // Get list of active mesh peers
                    get("/api/mesh/peers") {
                        val peers = MeshShareManager.peers.value.map { p ->
                            mapOf(
                                "id" to p.id,
                                "name" to p.deviceName,
                                "type" to p.deviceType.name,
                                "icon" to p.deviceType.icon,
                                "ip" to p.ipAddress,
                                "isOnline" to p.isOnline,
                                "fileCount" to p.fileCount,
                                "totalSizeBytes" to p.totalSizeBytes
                            )
                        }
                        call.respondText(gson.toJson(peers), ContentType.Application.Json)
                    }

                    // Unregister peer
                    post("/api/mesh/unregister") {
                        val peerId = call.request.queryParameters["peerId"] ?: runCatching {
                            gson.fromJson(call.receiveText(), Map::class.java)["peerId"]?.toString()
                        }.getOrNull() ?: ""
                        MeshShareManager.removePeer(peerId)
                        call.respondText(gson.toJson(mapOf("status" to "ok")), ContentType.Application.Json)
                    }

                    // ═══════════════════════════════════════════════════════════════════════════
                    // ── qBittorrent Web API v2 Compatibility Layer (qRemote, qbRemote, etc.) ──
                    // ═══════════════════════════════════════════════════════════════════════════

                    // 1. Auth: /api/v2/auth/login and logout
                    post("/api/v2/auth/login") {
                        call.response.header("Set-Cookie", "SID=pocketnode_session; HttpOnly; Path=/")
                        call.respondText("Ok.", ContentType.Text.Plain)
                    }

                    get("/api/v2/auth/login") {
                        call.respondText("Ok.", ContentType.Text.Plain)
                    }

                    post("/api/v2/auth/logout") {
                        call.respondText("Ok.", ContentType.Text.Plain)
                    }

                    // 2. App & Version Info
                    get("/api/v2/app/version") {
                        call.respondText("v4.6.5", ContentType.Text.Plain)
                    }

                    get("/api/v2/app/webapiVersion") {
                        call.respondText("2.9.3", ContentType.Text.Plain)
                    }

                    get("/api/v2/app/buildInfo") {
                        val info = mapOf(
                            "qt" to "6.5.0",
                            "libtorrent" to "2.1.0",
                            "boost" to "1.82",
                            "openssl" to "3.0.0",
                            "bitness" to 64
                        )
                        call.respondText(gson.toJson(info), ContentType.Application.Json)
                    }

                    get("/api/v2/app/preferences") {
                        val prefs = mapOf(
                            "save_path" to StorageManager.getMoviesDir().absolutePath,
                            "listen_port" to 6881,
                            "dht" to true,
                            "up_limit" to 0,
                            "dl_limit" to 0
                        )
                        call.respondText(gson.toJson(prefs), ContentType.Application.Json)
                    }

                    post("/api/v2/app/setPreferences") {
                        call.respondText("Ok.", ContentType.Text.Plain)
                    }

                    // 3. Torrents list: /api/v2/torrents/info
                    get("/api/v2/torrents/info") {
                        val downloads = TorrentEngine.downloads.value
                        val qbList = downloads.map { item ->
                            val isFinished = item.progressPct >= 100 || item.speedLabel == "COMPLETED" || item.speedLabel == "done"
                            val state = when {
                                item.isPaused -> "pausedDL"
                                isFinished -> "pausedUP"
                                item.seeds == 0 && item.peers == 0 -> "stalledDL"
                                else -> "downloading"
                            }
                            val dlSpeed = parseSpeedStringToBps(item.downSpeed)
                            val upSpeed = parseSpeedStringToBps(item.upSpeed)
                            val etaSec = parseEtaToSeconds(item.eta)

                            mapOf(
                                "hash" to item.hash.ifBlank { item.id }.lowercase(),
                                "name" to item.name,
                                "size" to item.totalBytes,
                                "progress" to (item.progressPct / 100.0),
                                "dlspeed" to dlSpeed,
                                "upspeed" to upSpeed,
                                "priority" to 0,
                                "num_seeds" to item.seeds,
                                "num_leechs" to item.peers,
                                "num_complete" to item.totalSeeds,
                                "num_incomplete" to item.totalPeers,
                                "ratio" to 1.0,
                                "eta" to etaSec,
                                "state" to state,
                                "seq_dl" to false,
                                "f_l_piece_prio" to false,
                                "category" to "",
                                "tags" to item.source,
                                "super_seeding" to false,
                                "force_start" to false,
                                "save_path" to item.savePath.ifBlank { StorageManager.getMoviesDir().absolutePath },
                                "added_on" to System.currentTimeMillis() / 1000,
                                "completion_on" to if (isFinished) System.currentTimeMillis() / 1000 else -1,
                                "tracker" to "",
                                "dl_limit" to -1,
                                "up_limit" to -1,
                                "downloaded" to item.downloadedBytes,
                                "uploaded" to 0,
                                "downloaded_session" to item.downloadedBytes,
                                "uploaded_session" to 0,
                                "amount_left" to (item.totalBytes - item.downloadedBytes).coerceAtLeast(0L),
                                "total_size" to item.totalBytes
                            )
                        }
                        call.respondText(gson.toJson(qbList), ContentType.Application.Json)
                    }

                    // 4. Torrent Details: properties, trackers, files, pieceStates
                    get("/api/v2/torrents/properties") {
                        val hash = call.request.queryParameters["hash"] ?: ""
                        val item = TorrentEngine.downloads.value.find {
                            it.hash.equals(hash, ignoreCase = true) || it.id.equals(hash, ignoreCase = true)
                        }
                        val dlSpeed = parseSpeedStringToBps(item?.downSpeed ?: "0")
                        val upSpeed = parseSpeedStringToBps(item?.upSpeed ?: "0")
                        val totalBytes = item?.totalBytes ?: 0L
                        val downloadedBytes = item?.downloadedBytes ?: 0L
                        val isComplete = (item?.progressPct ?: 0) >= 100

                        val props = mapOf(
                            "save_path" to (item?.savePath?.ifBlank { StorageManager.getMoviesDir().absolutePath } ?: StorageManager.getMoviesDir().absolutePath),
                            "creation_date" to (System.currentTimeMillis() / 1000),
                            "piece_size" to 1048576,
                            "num_pieces" to 0,
                            "total_wasted" to 0,
                            "total_uploaded" to 0,
                            "total_downloaded" to downloadedBytes,
                            "up_limit" to -1,
                            "dl_limit" to -1,
                            "time_elapsed" to 0,
                            "seeding_time" to 0,
                            "nb_connections" to (item?.peers ?: 0),
                            "nb_connections_limit" to 100,
                            "share_ratio" to (item?.shareRatio ?: 1.0f),
                            "addition_date" to (System.currentTimeMillis() / 1000),
                            "completion_date" to (if (isComplete) System.currentTimeMillis() / 1000 else -1),
                            "created_by" to "PocketNode",
                            "dl_speed_avg" to dlSpeed,
                            "dl_speed" to dlSpeed,
                            "eta" to parseEtaToSeconds(item?.eta ?: "--"),
                            "last_seen" to (System.currentTimeMillis() / 1000),
                            "peers" to (item?.peers ?: 0),
                            "peers_total" to (item?.totalPeers ?: 0),
                            "pieces_have" to 0,
                            "pieces_num" to 0,
                            "reannounce" to 0,
                            "seeds" to (item?.seeds ?: 0),
                            "seeds_total" to (item?.totalSeeds ?: 0),
                            "total_size" to totalBytes,
                            "up_speed_avg" to upSpeed,
                            "up_speed" to upSpeed
                        )
                        call.respondText(gson.toJson(props), ContentType.Application.Json)
                    }

                    get("/api/v2/torrents/trackers") {
                        val trackers = TorrentTrackersManager.DEFAULT_TRACKERS.map { tr ->
                            mapOf(
                                "url" to tr,
                                "status" to 2,
                                "tier" to 0,
                                "num_peers" to 0,
                                "num_seeds" to 0,
                                "num_leeches" to 0,
                                "num_downloaded" to 0,
                                "msg" to ""
                            )
                        }
                        call.respondText(gson.toJson(trackers), ContentType.Application.Json)
                    }

                    get("/api/v2/torrents/files") {
                        val hash = call.request.queryParameters["hash"] ?: ""
                        val files = TorrentEngine.getTorrentFiles(hash)
                        call.respondText(gson.toJson(files), ContentType.Application.Json)
                    }

                    post("/api/v2/torrents/filePrio") {
                        val body = call.receiveText()
                        val hash = call.request.queryParameters["hash"] ?: runCatching {
                            Regex("""hash=([^&]+)""").find(body)?.groupValues?.getOrNull(1)?.let { URLDecoder.decode(it, "UTF-8") }
                        }.getOrNull() ?: ""
                        val idParam = call.request.queryParameters["id"] ?: runCatching {
                            Regex("""id=([^&]+)""").find(body)?.groupValues?.getOrNull(1)?.let { URLDecoder.decode(it, "UTF-8") }
                        }.getOrNull() ?: ""
                        val priorityParam = call.request.queryParameters["priority"] ?: runCatching {
                            Regex("""priority=([^&]+)""").find(body)?.groupValues?.getOrNull(1)?.let { URLDecoder.decode(it, "UTF-8") }
                        }.getOrNull() ?: "1"

                        val fileIndices = idParam.split("|").mapNotNull { it.trim().toIntOrNull() }
                        val priority = priorityParam.toIntOrNull() ?: 1
                        if (hash.isNotBlank() && fileIndices.isNotEmpty()) {
                            TorrentEngine.setFilePriority(hash, fileIndices, priority)
                        }
                        call.respondText("Ok.", ContentType.Text.Plain)
                    }

                    get("/api/v2/torrents/pieceStates") {
                        call.respondText("[]", ContentType.Application.Json)
                    }

                    get("/api/v2/torrents/pieces") {
                        call.respondText("[]", ContentType.Application.Json)
                    }

                    get("/api/v2/torrents/pieceHashes") {
                        call.respondText("[]", ContentType.Application.Json)
                    }

                    // 5. Categories & Tags API
                    get("/api/v2/torrents/categories") {
                        call.respondText("{}", ContentType.Application.Json)
                    }
                    post("/api/v2/torrents/createCategory") {
                        call.respondText("Ok.", ContentType.Text.Plain)
                    }
                    post("/api/v2/torrents/setCategory") {
                        call.respondText("Ok.", ContentType.Text.Plain)
                    }
                    post("/api/v2/torrents/removeCategories") {
                        call.respondText("Ok.", ContentType.Text.Plain)
                    }

                    get("/api/v2/torrents/tags") {
                        call.respondText("[]", ContentType.Application.Json)
                    }
                    post("/api/v2/torrents/createTags") {
                        call.respondText("Ok.", ContentType.Text.Plain)
                    }
                    post("/api/v2/torrents/addTags") {
                        call.respondText("Ok.", ContentType.Text.Plain)
                    }
                    post("/api/v2/torrents/removeTags") {
                        call.respondText("Ok.", ContentType.Text.Plain)
                    }
                    post("/api/v2/torrents/deleteTags") {
                        call.respondText("Ok.", ContentType.Text.Plain)
                    }

                    // 6. Add Torrent: /api/v2/torrents/add & /api/v2/torrents/addTrackers
                    post("/api/v2/torrents/add") {
                        try {
                            val contentType = call.request.headers[HttpHeaders.ContentType] ?: ""
                            var targetSaveDir = StorageManager.getMoviesDir()

                            if (contentType.contains("multipart/form-data", ignoreCase = true)) {
                                val multipart = call.receiveMultipart()
                                var part = multipart.readPart()
                                val pendingUrls = mutableListOf<String>()

                                while (part != null) {
                                    when (part) {
                                        is PartData.FormItem -> {
                                            when (part.name) {
                                                "urls" -> {
                                                    pendingUrls.addAll(part.value.lines().map { it.trim() }.filter { it.isNotBlank() })
                                                }
                                                "savepath" -> {
                                                    val sp = part.value.trim()
                                                    if (sp.isNotBlank()) {
                                                        val f = File(sp)
                                                        if (f.isAbsolute) targetSaveDir = f
                                                    }
                                                }
                                            }
                                        }
                                        is PartData.FileItem -> {
                                            val bytes = part.streamProvider().readBytes()
                                            if (bytes.isNotEmpty()) {
                                                TorrentEngine.downloadTorrentBytes(bytes, part.originalFileName, saveDir = targetSaveDir)
                                            }
                                        }
                                        else -> {}
                                    }
                                    part.dispose()
                                    part = multipart.readPart()
                                }
                                for (u in pendingUrls) {
                                    handleIncomingTorrentUrl(u, saveDir = targetSaveDir)
                                }
                            } else {
                                val formText = call.receiveText()
                                val pairs = formText.split("&")
                                val pendingUrls = mutableListOf<String>()
                                for (p in pairs) {
                                    val key = p.substringBefore("=")
                                    val rawVal = p.substringAfter("=", "")
                                    val decoded = try { URLDecoder.decode(rawVal, StandardCharsets.UTF_8.name()) } catch (_: Exception) { rawVal }
                                    if (key == "urls") {
                                        pendingUrls.addAll(decoded.lines().map { it.trim() }.filter { it.isNotBlank() })
                                    } else if (key == "savepath" && decoded.isNotBlank()) {
                                        val f = File(decoded.trim())
                                        if (f.isAbsolute) targetSaveDir = f
                                    }
                                }
                                for (u in pendingUrls) {
                                    handleIncomingTorrentUrl(u, saveDir = targetSaveDir)
                                }
                            }
                            call.respondText("Ok.", ContentType.Text.Plain)
                        } catch (e: Exception) {
                            Logger.e(LogTag.HTTP, "qBittorrent add torrent failed: ${e.message}")
                            call.respondText("Fails.", status = HttpStatusCode.InternalServerError)
                        }
                    }

                    post("/api/v2/torrents/addTrackers") {
                        try {
                            val contentType = call.request.headers[HttpHeaders.ContentType] ?: ""
                            var hash = ""
                            val trackerUrls = mutableListOf<String>()

                            if (contentType.contains("multipart/form-data", ignoreCase = true)) {
                                val multipart = call.receiveMultipart()
                                var part = multipart.readPart()
                                while (part != null) {
                                    if (part is PartData.FormItem) {
                                        when (part.name) {
                                            "hash" -> hash = part.value.trim()
                                            "urls" -> trackerUrls.addAll(part.value.lines().map { it.trim() }.filter { it.isNotBlank() })
                                        }
                                    }
                                    part.dispose()
                                    part = multipart.readPart()
                                }
                            } else {
                                val formText = call.receiveText()
                                for (p in formText.split("&")) {
                                    val key = p.substringBefore("=")
                                    val rawVal = p.substringAfter("=", "")
                                    val decoded = try { URLDecoder.decode(rawVal, StandardCharsets.UTF_8.name()) } catch (_: Exception) { rawVal }
                                    if (key == "hash") hash = decoded.trim()
                                    if (key == "urls") {
                                        trackerUrls.addAll(decoded.lines().map { it.trim() }.filter { it.isNotBlank() })
                                    }
                                }
                            }

                            if (hash.isBlank()) {
                                hash = call.request.queryParameters["hash"] ?: ""
                            }
                            if (trackerUrls.isEmpty()) {
                                call.request.queryParameters["urls"]?.let {
                                    trackerUrls.addAll(it.lines().map { line -> line.trim() }.filter { line -> line.isNotBlank() })
                                }
                            }

                            if (hash.isNotBlank() && trackerUrls.isNotEmpty()) {
                                TorrentEngine.addTrackersToTorrent(hash, trackerUrls)
                            }
                            call.respondText("Ok.", ContentType.Text.Plain)
                        } catch (e: Exception) {
                            Logger.e(LogTag.HTTP, "qBittorrent addTrackers failed: ${e.message}")
                            call.respondText("Fails.", status = HttpStatusCode.InternalServerError)
                        }
                    }

                    // 7. Search API (qRemote search tab and download integration)
                    get("/api/v2/search/plugins") {
                        call.respondText("[]", ContentType.Application.Json)
                    }

                    post("/api/v2/search/start") {
                        call.respondText(gson.toJson(mapOf("id" to 1)), ContentType.Application.Json)
                    }

                    post("/api/v2/search/status") {
                        val status = listOf(mapOf("id" to 1, "status" to "Stopped", "total" to 0))
                        call.respondText(gson.toJson(status), ContentType.Application.Json)
                    }

                    post("/api/v2/search/results") {
                        val results = mapOf("results" to emptyList<Any>(), "status" to "Stopped", "total" to 0)
                        call.respondText(gson.toJson(results), ContentType.Application.Json)
                    }

                    post("/api/v2/search/stop") {
                        call.respondText("Ok.", ContentType.Text.Plain)
                    }

                    post("/api/v2/search/delete") {
                        call.respondText("Ok.", ContentType.Text.Plain)
                    }

                    post("/api/v2/search/downloadTorrent") {
                        try {
                            val contentType = call.request.headers[HttpHeaders.ContentType] ?: ""
                            var targetUrl = ""
                            var engineName: String? = null

                            if (contentType.contains("multipart/form-data", ignoreCase = true)) {
                                val multipart = call.receiveMultipart()
                                var part = multipart.readPart()
                                while (part != null) {
                                    if (part is PartData.FormItem) {
                                        when (part.name) {
                                            "url" -> targetUrl = part.value.trim()
                                            "engineName" -> engineName = part.value.trim()
                                        }
                                    }
                                    part.dispose()
                                    part = multipart.readPart()
                                }
                            } else {
                                val formText = call.receiveText()
                                for (p in formText.split("&")) {
                                    val key = p.substringBefore("=")
                                    val rawVal = p.substringAfter("=", "")
                                    val decoded = try { URLDecoder.decode(rawVal, StandardCharsets.UTF_8.name()) } catch (_: Exception) { rawVal }
                                    if (key == "url") targetUrl = decoded.trim()
                                    if (key == "engineName") engineName = decoded.trim()
                                }
                            }

                            if (targetUrl.isBlank()) {
                                targetUrl = call.request.queryParameters["url"] ?: ""
                            }

                            if (targetUrl.isNotBlank()) {
                                handleIncomingTorrentUrl(targetUrl, displayName = engineName)
                                call.respondText("Ok.", ContentType.Text.Plain)
                            } else {
                                call.respondText("Missing url parameter", status = HttpStatusCode.BadRequest)
                            }
                        } catch (e: Exception) {
                            Logger.e(LogTag.HTTP, "qBittorrent search downloadTorrent failed: ${e.message}")
                            call.respondText("Fails.", status = HttpStatusCode.InternalServerError)
                        }
                    }

                    // 8. Torrent Actions: pause, resume, delete, recheck, reannounce, setLocation, rename
                    post("/api/v2/torrents/pause") {
                        val body = call.receiveText()
                        val hashes = extractHashesParam(body, call.request.queryParameters["hashes"])
                        for (h in hashes) {
                            TorrentEngine.pauseDownload(h)
                        }
                        call.respondText("Ok.", ContentType.Text.Plain)
                    }

                    post("/api/v2/torrents/resume") {
                        val body = call.receiveText()
                        val hashes = extractHashesParam(body, call.request.queryParameters["hashes"])
                        for (h in hashes) {
                            TorrentEngine.resumeDownload(h)
                        }
                        call.respondText("Ok.", ContentType.Text.Plain)
                    }

                    post("/api/v2/torrents/delete") {
                        val body = call.receiveText()
                        val hashes = extractHashesParam(body, call.request.queryParameters["hashes"])
                        for (h in hashes) {
                            TorrentEngine.deleteDownload(h)
                        }
                        call.respondText("Ok.", ContentType.Text.Plain)
                    }

                    post("/api/v2/torrents/recheck") {
                        call.respondText("Ok.", ContentType.Text.Plain)
                    }

                    post("/api/v2/torrents/reannounce") {
                        call.respondText("Ok.", ContentType.Text.Plain)
                    }

                    post("/api/v2/torrents/setLocation") {
                        call.respondText("Ok.", ContentType.Text.Plain)
                    }

                    post("/api/v2/torrents/rename") {
                        call.respondText("Ok.", ContentType.Text.Plain)
                    }

                    post("/api/v2/torrents/toggleSequentialDownload") {
                        val body = call.receiveText()
                        val hashes = extractHashesParam(body, call.request.queryParameters["hashes"])
                        for (h in hashes) {
                            TorrentEngine.toggleSequentialDownload(h)
                        }
                        call.respondText("Ok.", ContentType.Text.Plain)
                    }

                    post("/api/v2/torrents/toggleFirstLastPiecePrio") {
                        call.respondText("Ok.", ContentType.Text.Plain)
                    }

                    post("/api/v2/torrents/setForceStart") {
                        call.respondText("Ok.", ContentType.Text.Plain)
                    }

                    post("/api/v2/torrents/setSuperSeeding") {
                        call.respondText("Ok.", ContentType.Text.Plain)
                    }

                    post("/api/v2/torrents/setDownloadLimit") {
                        call.respondText("Ok.", ContentType.Text.Plain)
                    }

                    post("/api/v2/torrents/setUploadLimit") {
                        call.respondText("Ok.", ContentType.Text.Plain)
                    }

                    post("/api/v2/torrents/setShareLimits") {
                        call.respondText("Ok.", ContentType.Text.Plain)
                    }

                    post("/api/v2/torrents/renameFile") {
                        call.respondText("Ok.", ContentType.Text.Plain)
                    }

                    post("/api/v2/torrents/renameFolder") {
                        call.respondText("Ok.", ContentType.Text.Plain)
                    }

                    post("/api/v2/torrents/editTracker") {
                        call.respondText("Ok.", ContentType.Text.Plain)
                    }

                    post("/api/v2/torrents/removeTrackers") {
                        call.respondText("Ok.", ContentType.Text.Plain)
                    }

                    get("/api/v2/sync/torrentPeers") {
                        val response = mapOf(
                            "full_update" to true,
                            "peers" to emptyMap<String, Any>(),
                            "rid" to (call.request.queryParameters["rid"]?.toIntOrNull() ?: 1),
                            "show_flags" to true
                        )
                        call.respondText(gson.toJson(response), ContentType.Application.Json)
                    }

                    get("/api/v2/transfer/speedLimitsMode") {
                        call.respondText("0", ContentType.Text.Plain)
                    }

                    post("/api/v2/transfer/toggleSpeedLimitsMode") {
                        call.respondText("Ok.", ContentType.Text.Plain)
                    }

                    post("/api/v2/transfer/setDownloadLimit") {
                        call.respondText("Ok.", ContentType.Text.Plain)
                    }

                    post("/api/v2/transfer/setUploadLimit") {
                        call.respondText("Ok.", ContentType.Text.Plain)
                    }

                    get("/api/v2/app/networkInterfaceList") {
                        call.respondText("[]", ContentType.Application.Json)
                    }

                    // 9. Logs API
                    get("/api/v2/log/main") {
                        call.respondText("[]", ContentType.Application.Json)
                    }

                    get("/api/v2/log/peers") {
                        call.respondText("[]", ContentType.Application.Json)
                    }

                    // 10. Transfer info & Sync
                    get("/api/v2/transfer/info") {
                        val downloads = TorrentEngine.downloads.value
                        var totalDlSpeed = 0L
                        var totalDownloaded = 0L
                        for (d in downloads) {
                            totalDlSpeed += parseSpeedStringToBps(d.downSpeed)
                            totalDownloaded += d.downloadedBytes
                        }
                        val info = mapOf(
                            "dl_info_speed" to totalDlSpeed,
                            "up_info_speed" to 0,
                            "dl_info_data" to totalDownloaded,
                            "up_info_data" to 0,
                            "connection_status" to "connected"
                        )
                        call.respondText(gson.toJson(info), ContentType.Application.Json)
                    }

                    get("/api/v2/sync/maindata") {
                        val downloads = TorrentEngine.downloads.value
                        val torrentsMap = mutableMapOf<String, Any>()
                        for (item in downloads) {
                            val h = item.hash.ifBlank { item.id }.lowercase()
                            torrentsMap[h] = mapOf(
                                "name" to item.name,
                                "size" to item.totalBytes,
                                "progress" to (item.progressPct / 100.0),
                                "dlspeed" to parseSpeedStringToBps(item.downSpeed),
                                "upspeed" to parseSpeedStringToBps(item.upSpeed),
                                "eta" to parseEtaToSeconds(item.eta),
                                "state" to if (item.isPaused) "pausedDL" else if (item.progressPct >= 100) "pausedUP" else "downloading",
                                "num_seeds" to item.seeds,
                                "num_leechs" to item.peers
                            )
                        }
                        val maindata = mapOf(
                            "rid" to 1,
                            "full_update" to true,
                            "torrents" to torrentsMap,
                            "server_state" to mapOf(
                                "connection_status" to "connected",
                                "dht_nodes" to 120,
                                "dl_info_speed" to downloads.sumOf { parseSpeedStringToBps(it.downSpeed) },
                                "up_info_speed" to 0
                            )
                        )
                        call.respondText(gson.toJson(maindata), ContentType.Application.Json)
                    }
                }
            }.start(wait = false)

            Logger.i(LogTag.HTTP, "Web Dashboard & File Server running on port $port")
        } catch (e: Exception) {
            Logger.e(LogTag.HTTP, "Failed to start Web HTTP Server: ${e.message}")
        }
    }

    fun stop() {
        serverEngine?.stop(1000, 2000)
        serverEngine = null
        Logger.i(LogTag.HTTP, "Web HTTP Server stopped.")
    }

    private fun renderWebDashboard(currentDir: File, currentRelPath: String): String {
        val files = StorageManager.listFiles(currentDir)
        val rows = StringBuilder()

        val isRoot = currentDir.canonicalPath == StorageManager.getBaseDir().canonicalPath
        if (!isRoot && currentDir.parentFile != null) {
            val parentRelPath = getRelativePath(currentDir.parentFile!!)
            val encodedParent = URLEncoder.encode(parentRelPath, StandardCharsets.UTF_8.name())
            rows.append(
                """
                <tr>
                    <td><a href="/?path=$encodedParent" class="folder">📁 .. (Parent Directory)</a></td>
                    <td>-</td>
                    <td>-</td>
                </tr>
                """.trimIndent()
            )
        }

        for (file in files) {
            val relPath = getRelativePath(file)
            val encodedRelPath = URLEncoder.encode(relPath, StandardCharsets.UTF_8.name())

            if (file.isDirectory) {
                rows.append(
                    """
                    <tr>
                        <td><a href="/?path=$encodedRelPath" class="folder">📁 ${file.name}/</a></td>
                        <td>Folder</td>
                        <td>-</td>
                    </tr>
                    """.trimIndent()
                )
            } else {
                val downloadUrl = "/download?path=$encodedRelPath"
                val streamUrl = "/stream?path=$encodedRelPath"
                val isVideo = isVideoFile(file.name)

                val playBtn = if (isVideo) {
                    """<button onclick="playMedia('$streamUrl', '${file.name}')" class="btn btn-play">▶ Stream</button>"""
                } else ""

                rows.append(
                    """
                    <tr>
                        <td>📄 ${file.name}</td>
                        <td>${formatSize(file.length())}</td>
                        <td>
                            $playBtn
                            <a href="$downloadUrl" class="btn btn-download" download>⬇ Download</a>
                        </td>
                    </tr>
                    """.trimIndent()
                )
            }
        }

        // Render Zero-Copy Host Mobile Shared Media
        if (isRoot && HostMediaManager.isSharingEnabled.value) {
            val hostFiles = HostMediaManager.virtualFiles.value
            if (hostFiles.isNotEmpty()) {
                rows.append(
                    """
                    <tr style="background: rgba(14, 165, 233, 0.15);">
                        <td colspan="3" style="font-weight: bold; color: #38bdf8; font-size: 13px; letter-spacing: 0.5px; padding: 10px 16px;">
                            📱 HOST DEVICE SHARED STORAGE (ZERO-COPY VIRTUAL MOUNT)
                        </td>
                    </tr>
                    """.trimIndent()
                )

                for (hf in hostFiles) {
                    val streamUrl = "/hostmedia/stream?id=${hf.id}"
                    val isVideo = isVideoFile(hf.name)
                    val playBtn = if (isVideo) {
                        """<button onclick="playMedia('$streamUrl', '${hf.name}')" class="btn btn-play">▶ Stream</button>"""
                    } else ""

                    rows.append(
                        """
                        <tr>
                            <td>
                                📱 ${hf.name} 
                                <span style="font-size: 10px; color: #38bdf8; background: rgba(56, 189, 248, 0.15); padding: 2px 6px; border-radius: 4px; font-weight: bold; margin-left: 6px;">${hf.category.uppercase()}</span>
                            </td>
                            <td>${formatSize(hf.sizeBytes)}</td>
                            <td>
                                $playBtn
                                <a href="$streamUrl" class="btn btn-download" download="${hf.name}">⬇ Download</a>
                            </td>
                        </tr>
                        """.trimIndent()
                    )
                }
            }
        }

        // Render LAN Network Shared Storage (PC / Mac / NAS SMB)
        if (isRoot) {
            val netFiles = NetworkShareManager.networkFiles.value
            if (netFiles.isNotEmpty()) {
                rows.append(
                    """
                    <tr style="background: rgba(16, 185, 129, 0.15);">
                        <td colspan="3" style="font-weight: bold; color: #10b981; font-size: 13px; letter-spacing: 0.5px; padding: 10px 16px;">
                            🖥️ LAN NETWORK SHARED STORAGE (PC / MAC / NAS SMB)
                        </td>
                    </tr>
                    """.trimIndent()
                )

                for (nf in netFiles) {
                    val streamUrl = "/netmedia/stream?id=${nf.id}"
                    val isVideo = isVideoFile(nf.name)
                    val playBtn = if (isVideo) {
                        """<button onclick="playMedia('$streamUrl', '${nf.name}')" class="btn btn-play">▶ Stream</button>"""
                    } else ""

                    rows.append(
                        """
                        <tr>
                            <td>
                                🖥️ ${nf.name} 
                                <span style="font-size: 10px; color: #10b981; background: rgba(16, 185, 129, 0.15); padding: 2px 6px; border-radius: 4px; font-weight: bold; margin-left: 6px;">${nf.category.uppercase()}</span>
                                <span style="font-size: 9px; color: #94a3b8; margin-left: 6px;">(${nf.hostName})</span>
                            </td>
                            <td>${formatSize(nf.sizeBytes)}</td>
                            <td>
                                $playBtn
                                <a href="$streamUrl" class="btn btn-download" download="${nf.name}">⬇ Download</a>
                            </td>
                        </tr>
                        """.trimIndent()
                    )
                }
            }
        }

        return """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Remote Media Server - Web Dashboard</title>
            <style>
                body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; background: #0f172a; color: #f8fafc; margin: 0; padding: 20px; }
                .container { max-width: 1000px; margin: 0 auto; background: #1e293b; padding: 24px; border-radius: 12px; box-shadow: 0 10px 25px rgba(0,0,0,0.5); }
                h1 { color: #38bdf8; margin-top: 0; font-size: 24px; }
                .subtitle { color: #94a3b8; font-size: 14px; margin-bottom: 20px; }
                table { width: 100%; border-collapse: collapse; margin-top: 15px; }
                th, td { text-align: left; padding: 12px 16px; border-bottom: 1px solid #334155; }
                th { background-color: #0f172a; color: #38bdf8; font-weight: 600; }
                tr:hover { background-color: #334155; }
                a { color: #38bdf8; text-decoration: none; font-weight: 500; }
                a.folder { color: #facc15; }
                .btn { display: inline-block; padding: 6px 12px; border-radius: 6px; font-size: 13px; text-decoration: none; cursor: pointer; border: none; margin-right: 6px; }
                .btn-play { background: #10b981; color: white; }
                .btn-download { background: #3b82f6; color: white; }
                .btn:hover { opacity: 0.9; }
                #player-modal { display: none; position: fixed; top: 0; left: 0; width: 100%; height: 100%; background: rgba(0,0,0,0.85); justify-content: center; align-items: center; z-index: 100; }
                .player-box { background: #0f172a; padding: 20px; border-radius: 12px; max-width: 800px; width: 90%; text-align: center; }
                video { width: 100%; max-height: 500px; border-radius: 8px; margin-top: 10px; }
                .close-btn { background: #ef4444; color: white; padding: 8px 16px; border-radius: 6px; cursor: pointer; float: right; border: none; }
            </style>
        </head>
        <body>
            <div class="container">
                <div style="display: flex; gap: 10px; margin-bottom: 16px;">
                    <a href="/" style="font-size: 11px; font-weight: bold; color: #38bdf8; text-decoration: none; padding: 6px 12px; border: 1px solid #38bdf8; border-radius: 4px; background: rgba(56, 189, 248, 0.1);">📁 FILE BROWSER</a>
                    <a href="/mesh" style="font-size: 11px; font-weight: bold; color: #94a3b8; text-decoration: none; padding: 6px 12px; border: 1px solid #334155; border-radius: 4px; background: #0f172a;">🌐 MESH PORTAL</a>
                    <a href="#" onclick="window.open('http://' + window.location.hostname + ':8096', '_blank'); return false;" style="font-size: 11px; font-weight: bold; color: #94a3b8; text-decoration: none; padding: 6px 12px; border: 1px solid #334155; border-radius: 4px; background: #0f172a;">🍿 JELLYFIN</a>
                </div>
                <h1>🎬 Remote Media Server Dashboard</h1>
                <div class="subtitle">Isolated Storage: <code>/media/${currentRelPath.ifEmpty { "root" }}</code></div>
                <table>
                    <thead>
                        <tr>
                            <th>File / Folder Name</th>
                            <th>Size</th>
                            <th>Actions</th>
                        </tr>
                    </thead>
                    <tbody>
                        $rows
                    </tbody>
                </table>
            </div>

            <div id="player-modal">
                <div class="player-box">
                    <button class="close-btn" onclick="closePlayer()">✕ Close</button>
                    <h3 id="player-title" style="color: #38bdf8; margin: 0 0 10px 0;">Playing Video</h3>
                    <video id="video-player" controls autoplay></video>
                </div>
            </div>

            <script>
                function playMedia(url, title) {
                    document.getElementById('player-title').innerText = title;
                    const video = document.getElementById('video-player');
                    video.src = url;
                    document.getElementById('player-modal').style.display = 'flex';
                }
                function closePlayer() {
                    const video = document.getElementById('video-player');
                    video.pause();
                    video.src = '';
                    document.getElementById('player-modal').style.display = 'none';
                }
            </script>
        </body>
        </html>
        """.trimIndent()
    }

    private fun isVideoFile(fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return listOf("mp4", "mkv", "avi", "mov", "webm", "flv", "m4v", "3gp", "ts").contains(ext)
    }

    private fun getRelativePath(file: File): String {
        val base = StorageManager.getBaseDir().canonicalPath
        val target = file.canonicalPath
        return target.removePrefix(base).trimStart('/', '\\')
    }

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format("%.2f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }

    private fun renderMeshPortal(): String {
        val peers = MeshShareManager.peers.value
        val peerCount = peers.size
        val totalFiles = MeshShareManager.meshFileCount.value

        return """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>PocketNode // Mesh Peer Portal</title>
            <style>
                :root {
                    --bg: #090d16;
                    --panel: #111827;
                    --surface: #1e293b;
                    --border: #334155;
                    --primary: #00F0FF;
                    --accent: #F59E0B;
                    --success: #10B981;
                    --danger: #EF4444;
                    --text: #F8FAFC;
                    --muted: #94A3B8;
                }
                * { box-sizing: border-box; margin: 0; padding: 0; font-family: 'JetBrains Mono', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, monospace; }
                body { background: var(--bg); color: var(--text); padding: 20px; min-height: 100vh; }
                .container { max-width: 860px; margin: 0 auto; }
                .header { display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid var(--border); padding-bottom: 16px; margin-bottom: 20px; }
                .logo { font-size: 18px; font-weight: 800; color: var(--primary); letter-spacing: 1px; display: flex; align-items: center; gap: 8px; }
                .nav-links { display: flex; gap: 10px; margin-bottom: 18px; }
                .nav-link { font-size: 11px; font-weight: bold; color: var(--muted); text-decoration: none; padding: 6px 12px; border: 1px solid var(--border); border-radius: 4px; background: var(--panel); transition: 0.15s; }
                .nav-link:hover { color: var(--primary); border-color: var(--primary); }
                .nav-link.active { color: var(--primary); border-color: var(--primary); background: rgba(0, 240, 255, 0.1); }
                .badge { font-size: 10px; padding: 4px 8px; border-radius: 3px; border: 1px solid; text-transform: uppercase; font-weight: bold; }
                .badge-active { background: rgba(16, 185, 129, 0.15); border-color: var(--success); color: var(--success); }
                .badge-warn { background: rgba(245, 158, 11, 0.15); border-color: var(--accent); color: var(--accent); }
                .card { background: var(--panel); border: 1px solid var(--border); border-radius: 8px; padding: 20px; margin-bottom: 20px; }
                .card-title { font-size: 13px; font-weight: bold; color: var(--muted); margin-bottom: 14px; display: flex; justify-content: space-between; align-items: center; }
                .notice-box { background: rgba(245, 158, 11, 0.08); border: 1px solid rgba(245, 158, 11, 0.35); border-radius: 6px; padding: 14px; margin-bottom: 20px; }
                .notice-title { color: var(--accent); font-weight: bold; font-size: 12px; margin-bottom: 4px; display: flex; align-items: center; gap: 6px; }
                .notice-desc { color: var(--muted); font-size: 11px; line-height: 1.5; }
                .input-group { margin-bottom: 14px; }
                .label { font-size: 11px; font-weight: bold; color: var(--text); margin-bottom: 6px; display: block; }
                .input { width: 100%; background: var(--surface); border: 1px solid var(--border); color: var(--text); padding: 10px 12px; border-radius: 6px; font-size: 12px; outline: none; }
                .input:focus { border-color: var(--primary); }
                .btn { display: inline-flex; align-items: center; justify-content: center; gap: 8px; font-size: 12px; font-weight: bold; padding: 10px 18px; border-radius: 6px; border: 1px solid transparent; cursor: pointer; text-decoration: none; transition: 0.15s; }
                .btn-primary { background: var(--primary); color: #000; }
                .btn-primary:hover { opacity: 0.9; }
                .btn-accent { background: var(--accent); color: #000; }
                .btn-outline { background: transparent; border-color: var(--border); color: var(--text); }
                .btn-outline:hover { background: var(--surface); }
                .dropzone { border: 2px dashed var(--border); border-radius: 8px; padding: 30px; text-align: center; cursor: pointer; background: rgba(30, 41, 59, 0.2); transition: 0.2s; }
                .dropzone:hover { border-color: var(--primary); background: rgba(0, 240, 255, 0.05); }
                .file-list { margin-top: 15px; max-height: 240px; overflow-y: auto; background: var(--surface); border-radius: 6px; border: 1px solid var(--border); display: none; }
                .file-row { display: flex; justify-content: space-between; align-items: center; padding: 8px 12px; border-bottom: 1px solid rgba(255,255,255,0.05); font-size: 11px; }
                .file-row:last-child { border-bottom: none; }
                .tag { font-size: 9px; padding: 2px 6px; border-radius: 3px; font-weight: bold; margin-left: 6px; text-transform: uppercase; }
                .tag-movies { background: rgba(239, 68, 68, 0.2); color: #f87171; border: 1px solid #ef4444; }
                .tag-music { background: rgba(16, 185, 129, 0.2); color: #34d399; border: 1px solid #10b981; }
                .tag-photos { background: rgba(56, 189, 248, 0.2); color: #38bdf8; border: 1px solid #38bdf8; }
                .tag-documents { background: rgba(245, 158, 11, 0.2); color: #fbbf24; border: 1px solid #f59e0b; }
                .peer-grid { display: flex; flex-direction: column; gap: 8px; margin-top: 10px; }
                .peer-card { background: var(--surface); border: 1px solid var(--border); border-radius: 6px; padding: 10px 14px; display: flex; justify-content: space-between; align-items: center; }
            </style>
        </head>
        <body>
            <div class="container">
                <div class="header">
                    <div class="logo">
                        <span>⚡</span> POCKETNODE // MESH PORTAL
                    </div>
                    <div id="statusBadge" class="badge badge-active">CONNECTED ($peerCount PEERS • $totalFiles FILES)</div>
                </div>

                <div class="nav-links">
                    <a href="/" class="nav-link">📁 FILE BROWSER</a>
                    <a href="/mesh" class="nav-link active">🌐 MESH PORTAL</a>
                    <a href="#" onclick="window.open('http://' + window.location.hostname + ':8096', '_blank'); return false;" class="nav-link">🍿 JELLYFIN STREAM</a>
                </div>

                <!-- Protocol Info Notice -->
                <div class="notice-box">
                    <div class="notice-title">⚡ HTTP / HTTPS Protocol Notice</div>
                    <div class="notice-desc">
                        PocketNode runs on local Wi-Fi port <strong>8080</strong> over high-speed cleartext HTTP (<code>http://pocketnode.local:8080/mesh</code>). 
                        If your browser auto-redirected to <strong>https://</strong>, TLS certificate errors will prevent loading. Always use <strong>http://</strong> on local network addresses.
                    </div>
                </div>

                <!-- Client Registration Card -->
                <div class="card">
                    <div class="card-title">
                        <span>// SHARE THIS DEVICE'S MEDIA WITH POCKETNODE</span>
                        <span id="platformTag" style="color: var(--primary); font-size: 11px;">AUTO-DETECTING...</span>
                    </div>

                    <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 12px;">
                        <div class="input-group">
                            <label class="label">DEVICE NAME</label>
                            <input type="text" id="deviceNameInput" class="input" placeholder="e.g. MacBook Pro, iPhone, Gaming PC">
                        </div>
                        <div class="input-group">
                            <label class="label">DEVICE TYPE</label>
                            <select id="deviceTypeSelect" class="input">
                                <option value="PHONE_IOS">📱 Apple iPhone / iPad</option>
                                <option value="MAC_OS">🍎 Apple Mac / macOS</option>
                                <option value="PC_WINDOWS">💻 Windows PC</option>
                                <option value="PHONE_ANDROID">🤖 Android Device</option>
                                <option value="LINUX_PC">🐧 Linux Desktop</option>
                                <option value="OTHER">🌐 Other Browser</option>
                            </select>
                        </div>
                    </div>

                    <div class="dropzone" onclick="document.getElementById('folderPicker').click()">
                        <div style="font-size: 32px; margin-bottom: 8px;">📂</div>
                        <div style="font-weight: bold; font-size: 13px; color: var(--text);">Click to Select Local Media Folder</div>
                        <div style="font-size: 11px; color: var(--muted); margin-top: 4px;">Zero-copy virtual mount: files stream on-demand across your Wi-Fi without uploading to phone storage.</div>
                        <input type="file" id="folderPicker" webkitdirectory directory multiple style="display: none;">
                    </div>

                    <div id="selectionSummary" style="margin-top: 10px; font-size: 11px; color: var(--accent); font-weight: bold;"></div>

                    <div id="fileListContainer" class="file-list"></div>

                    <div style="margin-top: 16px; display: flex; gap: 10px;">
                        <button id="broadcastBtn" class="btn btn-primary" style="display: none;" onclick="broadcastFiles()">⚡ BROADCAST FOLDER TO SERVER</button>
                    </div>
                </div>

                <!-- Active Connected Peers -->
                <div class="card">
                    <div class="card-title">
                        <span>// ACTIVE MESH PEERS ON NETWORK</span>
                        <button class="btn btn-outline" style="padding: 4px 10px; font-size: 10px;" onclick="loadPeers()">⟳ REFRESH</button>
                    </div>
                    <div id="peersContainer" class="peer-grid">
                        <div style="color: var(--muted); font-size: 11px; text-align: center; padding: 12px;">Loading connected peer nodes...</div>
                    </div>
                </div>
            </div>

            <script>
                // Protocol check: redirect if browser accidentally forced https on port 8080
                if (location.protocol === 'https:' && location.port === '8080') {
                    location.href = 'http://' + location.hostname + ':8080/mesh';
                }

                let peerId = 'peer_' + Math.random().toString(36).substr(2, 9);
                let selectedFiles = [];

                // Platform detection
                const ua = navigator.userAgent;
                let detectedName = 'Browser Client';
                let detectedType = 'OTHER';

                if (/iPhone|iPad|iPod/i.test(ua)) {
                    detectedName = 'Apple iOS Device';
                    detectedType = 'PHONE_IOS';
                } else if (/Macintosh|Mac OS X/i.test(ua)) {
                    detectedName = 'Apple Mac';
                    detectedType = 'MAC_OS';
                } else if (/Windows/i.test(ua)) {
                    detectedName = 'Windows PC';
                    detectedType = 'PC_WINDOWS';
                } else if (/Android/i.test(ua)) {
                    detectedName = 'Android Mobile';
                    detectedType = 'PHONE_ANDROID';
                } else if (/Linux/i.test(ua)) {
                    detectedName = 'Linux Client';
                    detectedType = 'LINUX_PC';
                }

                document.getElementById('deviceNameInput').value = detectedName;
                document.getElementById('deviceTypeSelect').value = detectedType;
                document.getElementById('platformTag').innerText = 'DETECTED: ' + detectedName;

                function categorize(name) {
                    const ext = name.split('.').pop().toLowerCase();
                    if (['mp4','mkv','avi','mov','webm'].includes(ext)) return 'Movies';
                    if (['mp3','flac','m4a','aac','wav','ogg'].includes(ext)) return 'Music';
                    if (['jpg','jpeg','png','webp','gif'].includes(ext)) return 'Photos';
                    if (['pdf','epub','txt','doc','docx'].includes(ext)) return 'Documents';
                    return 'Others';
                }

                document.getElementById('folderPicker').addEventListener('change', function(e) {
                    const files = Array.from(e.target.files);
                    if (files.length === 0) return;
                    selectedFiles = files;
                    document.getElementById('selectionSummary').innerText = '✓ ' + files.length + ' file(s) indexed from folder';

                    const listContainer = document.getElementById('fileListContainer');
                    listContainer.style.display = 'block';
                    listContainer.innerHTML = '';

                    files.slice(0, 100).forEach(f => {
                        const cat = categorize(f.name);
                        const row = document.createElement('div');
                        row.className = 'file-row';
                        row.innerHTML = '<span>📄 ' + f.name + '</span>' +
                            '<span><span class="tag tag-' + cat.toLowerCase() + '">' + cat + '</span> ' +
                            (f.size / (1024*1024)).toFixed(1) + ' MB</span>';
                        listContainer.appendChild(row);
                    });

                    if (files.length > 100) {
                        const more = document.createElement('div');
                        more.className = 'file-row';
                        more.style.color = '#94a3b8';
                        more.innerText = '+ ' + (files.length - 100) + ' more files...';
                        listContainer.appendChild(more);
                    }

                    document.getElementById('broadcastBtn').style.display = 'inline-flex';
                });

                async function broadcastFiles() {
                    const btn = document.getElementById('broadcastBtn');
                    btn.disabled = true;
                    btn.innerText = 'BROADCASTING...';

                    const manifestFiles = selectedFiles.map(f => ({
                        name: f.name,
                        category: categorize(f.name),
                        mimeType: f.type || 'application/octet-stream',
                        sizeBytes: f.size,
                        streamUrl: window.location.origin + '/meshmedia/stream',
                        relativePath: f.webkitRelativePath || f.name
                    }));

                    const devName = document.getElementById('deviceNameInput').value || detectedName;
                    const devType = document.getElementById('deviceTypeSelect').value || detectedType;

                    try {
                        const resp = await fetch('/api/mesh/register', {
                            method: 'POST',
                            headers: { 'Content-Type': 'application/json' },
                            body: JSON.stringify({
                                id: peerId,
                                deviceName: devName,
                                deviceType: devType,
                                files: manifestFiles
                            })
                        });
                        const res = await resp.json();
                        if (res.status === 'ok') {
                            btn.innerText = '✓ BROADCAST ACTIVE (' + manifestFiles.length + ' FILES)';
                            btn.style.background = '#10B981';
                            loadPeers();
                        } else {
                            btn.innerText = 'FAILED: ' + res.message;
                            btn.disabled = false;
                        }
                    } catch (err) {
                        btn.innerText = 'ERROR CONNECTING TO POCKETNODE';
                        btn.disabled = false;
                    }
                }

                async function loadPeers() {
                    try {
                        const resp = await fetch('/api/mesh/peers');
                        const peers = await resp.json();
                        const container = document.getElementById('peersContainer');
                        container.innerHTML = '';

                        if (peers.length === 0) {
                            container.innerHTML = '<div style="color: var(--muted); font-size: 11px; text-align: center; padding: 12px;">No active client peer nodes registered yet.</div>';
                            return;
                        }

                        peers.forEach(p => {
                            const card = document.createElement('div');
                            card.className = 'peer-card';
                            const icon = p.deviceType === 'PHONE_IOS' ? '📱' :
                                         p.deviceType === 'MAC_OS' ? '🍎' :
                                         p.deviceType === 'PC_WINDOWS' ? '💻' :
                                         p.deviceType === 'PHONE_ANDROID' ? '🤖' : '🌐';
                            card.innerHTML = '<div>' +
                                '<div style="font-weight: bold; font-size: 12px; color: var(--text);">' + icon + ' ' + p.deviceName + '</div>' +
                                '<div style="font-size: 10px; color: var(--muted);">' + p.ipAddress + ' • ' + p.fileCount + ' federated files</div>' +
                                '</div>' +
                                '<div class="badge badge-active">ONLINE</div>';
                            container.appendChild(card);
                        });
                    } catch (e) {
                        console.error('Failed to load peers', e);
                    }
                }

                // Initial load & heartbeat
                loadPeers();
                setInterval(() => {
                    if (selectedFiles.length > 0) {
                        fetch('/api/mesh/heartbeat?peerId=' + peerId, { method: 'POST' }).catch(() => {});
                    }
                    loadPeers();
                }, 10000);
            </script>
        </body>
        </html>
        """.trimIndent()
    }
}

private data class MeshRegisterRequest(
    val id: String = "",
    val deviceName: String = "",
    val deviceType: String = "OTHER",
    val ipAddress: String = "",
    val port: Int = 8085,
    val authKey: String = "",
    val sharedFolders: List<String>? = null,
    val files: List<MeshFileDto>? = null
)

private data class MeshFileDto(
    val id: String = "",
    val name: String = "",
    val category: String = "",
    val mimeType: String = "",
    val sizeBytes: Long = 0L,
    val streamUrl: String = "",
    val relativePath: String = ""
)

data class TrendingTorrentDto(
    val id: String,
    val title: String,
    val year: String,
    val genre: String,
    val rating: String,
    val quality: String,
    val size: String,
    val seeds: Int,
    val magnetUri: String
)

data class UnifiedMediaSearchResult(
    val id: String,
    val title: String,
    val source: String, // "TORRENT", "TELEGRAM", "SANDBOX"
    val size: String,
    val quality: String = "1080p",
    val year: String = "",
    val posterUrl: String = "",
    val streamUrl: String = "",
    val magnetUri: String = "",
    val fileId: String = "",
    val isLocal: Boolean = false,
    val seeds: Int = 0
)

fun getTrendingTorrents(): List<TrendingTorrentDto> {
    return listOf(
        TrendingTorrentDto(
            id = "trend_1",
            title = "Big Buck Bunny",
            year = "2008",
            genre = "Animation • Comedy",
            rating = "8.8",
            quality = "1080p UHD",
            size = "276 MB",
            seeds = 2840,
            magnetUri = "magnet:?xt=urn:btih:dd8255ecdc7ca55fb0bbf81323d87062db1f6d1c&dn=Big+Buck+Bunny&tr=udp%3A%2F%2Fexplodie.org%3A6969&tr=udp%3A%2F%2Ftracker.coppersurfer.tk%3A6969&tr=udp%3A%2F%2Ftracker.empire-js.us%3A1337&tr=udp%3A%2F%2Ftracker.leechers-paradise.org%3A6969&tr=udp%3A%2F%2Ftracker.opentrackr.org%3A1337&tr=wss%3A%2F%2Ftracker.btorrent.xyz&tr=wss%3A%2F%2Ftracker.openwebtorrent.com"
        ),
        TrendingTorrentDto(
            id = "trend_2",
            title = "Sintel",
            year = "2010",
            genre = "Animation • Fantasy",
            rating = "8.5",
            quality = "1080p HD",
            size = "129 MB",
            seeds = 1920,
            magnetUri = "magnet:?xt=urn:btih:08ada5a7a6183aae1e09d831df6748d566095a10&dn=Sintel&tr=udp%3A%2F%2Fexplodie.org%3A6969&tr=udp%3A%2F%2Ftracker.coppersurfer.tk%3A6969&tr=udp%3A%2F%2Ftracker.empire-js.us%3A1337&tr=udp%3A%2F%2Ftracker.leechers-paradise.org%3A6969&tr=udp%3A%2F%2Ftracker.opentrackr.org%3A1337&tr=wss%3A%2F%2Ftracker.btorrent.xyz&tr=wss%3A%2F%2Ftracker.openwebtorrent.com"
        ),
        TrendingTorrentDto(
            id = "trend_3",
            title = "Tears of Steel",
            year = "2012",
            genre = "Sci-Fi • Action",
            rating = "8.2",
            quality = "1080p Remux",
            size = "571 MB",
            seeds = 1450,
            magnetUri = "magnet:?xt=urn:btih:209c614cdb49381cc46160e2528f32b3803c684b&dn=Tears+of+Steel&tr=udp%3A%2F%2Fexplodie.org%3A6969&tr=udp%3A%2F%2Ftracker.coppersurfer.tk%3A6969&tr=udp%3A%2F%2Ftracker.empire-js.us%3A1337&tr=udp%3A%2F%2Ftracker.leechers-paradise.org%3A6969&tr=udp%3A%2F%2Ftracker.opentrackr.org%3A1337&tr=wss%3A%2F%2Ftracker.btorrent.xyz&tr=wss%3A%2F%2Ftracker.openwebtorrent.com"
        ),
        TrendingTorrentDto(
            id = "trend_4",
            title = "Cosmos Laundromat",
            year = "2015",
            genre = "Animation • Sci-Fi",
            rating = "8.4",
            quality = "1080p Web-DL",
            size = "210 MB",
            seeds = 980,
            magnetUri = "magnet:?xt=urn:btih:c9e15763f722f23e98a29decd97e36b85760480f&dn=Cosmos+Laundromat&tr=udp%3A%2F%2Fexplodie.org%3A6969&tr=udp%3A%2F%2Ftracker.coppersurfer.tk%3A6969&tr=udp%3A%2F%2Ftracker.empire-js.us%3A1337&tr=udp%3A%2F%2Ftracker.leechers-paradise.org%3A6969&tr=udp%3A%2F%2Ftracker.opentrackr.org%3A1337&tr=wss%3A%2F%2Ftracker.btorrent.xyz&tr=wss%3A%2F%2Ftracker.openwebtorrent.com"
        )
    )
}

private fun parseSpeedStringToBps(str: String): Long {
    return try {
        val num = Regex("""[\d.]+""").find(str)?.value?.toDoubleOrNull() ?: return 0L
        val upper = str.uppercase()
        when {
            upper.contains("GB/S") || upper.contains("GIB/S") -> (num * 1024 * 1024 * 1024).toLong()
            upper.contains("MB/S") || upper.contains("MIB/S") -> (num * 1024 * 1024).toLong()
            upper.contains("KB/S") || upper.contains("KIB/S") -> (num * 1024).toLong()
            else -> num.toLong()
        }
    } catch (_: Exception) { 0L }
}

private fun parseEtaToSeconds(str: String): Long {
    return try {
        val clean = str.trim()
        if (clean == "--" || clean == "Done" || clean.isBlank()) return 8640000L
        val secOnly = clean.removeSuffix("s").trim().toLongOrNull()
        if (secOnly != null) return secOnly
        val parts = clean.split(":")
        if (parts.size == 2) {
            val m = parts[0].toLongOrNull() ?: 0L
            val s = parts[1].toLongOrNull() ?: 0L
            m * 60 + s
        } else if (parts.size == 3) {
            val h = parts[0].toLongOrNull() ?: 0L
            val m = parts[1].toLongOrNull() ?: 0L
            val s = parts[2].toLongOrNull() ?: 0L
            h * 3600 + m * 60 + s
        } else 8640000L
    } catch (_: Exception) { 8640000L }
}

private fun handleIncomingTorrentUrl(url: String, displayName: String? = null, saveDir: File = StorageManager.getMoviesDir()) {
    val trimmed = url.trim()
    if (trimmed.isBlank()) return
    val cleanName = displayName?.takeIf { !it.equals("Torrent Download", ignoreCase = true) }
    when {
        trimmed.startsWith("magnet:", ignoreCase = true) -> {
            val extractedName = cleanName ?: try {
                val rawDn = trimmed.substringAfter("dn=", "").substringBefore("&")
                if (rawDn.isNotBlank()) URLDecoder.decode(rawDn.replace("+", " "), StandardCharsets.UTF_8.name()).trim() else null
            } catch (_: Exception) { null }
            TorrentEngine.downloadMagnet(trimmed, extractedName, saveDir = saveDir)
        }
        trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true) -> {
            TorrentEngine.downloadTorrentUrl(trimmed, cleanName, saveDir = saveDir)
        }
        trimmed.length == 40 && trimmed.matches(Regex("^[0-9a-fA-F]+$")) -> {
            TorrentEngine.downloadMagnet("magnet:?xt=urn:btih:$trimmed", cleanName, saveDir = saveDir)
        }
        trimmed.length == 32 && trimmed.matches(Regex("^[2-7a-zA-Z]+$")) -> {
            TorrentEngine.downloadMagnet("magnet:?xt=urn:btih:$trimmed", cleanName, saveDir = saveDir)
        }
        else -> {
            Logger.w(LogTag.HTTP, "Unrecognized torrent URL format: $trimmed")
        }
    }
}

private fun extractHashesParam(body: String, queryParam: String?): List<String> {
    val raw = if (!queryParam.isNullOrBlank()) queryParam else {
        val match = Regex("""hashes=([^&]+)""").find(body)
        match?.groupValues?.getOrNull(1)?.let { URLDecoder.decode(it, "UTF-8") } ?: ""
    }
    if (raw.isBlank() || raw == "all") return TorrentEngine.downloads.value.map { it.hash.ifBlank { it.id } }
    return raw.split("|").map { it.trim() }.filter { it.isNotBlank() }
}

private suspend fun respondSandboxVideo(call: io.ktor.server.application.ApplicationCall) {
    val nameParam = call.parameters.getAll("name")?.joinToString("/")
    val idParam = call.parameters["id"]
    val targetIdOrName = when {
        !nameParam.isNullOrBlank() -> try { URLDecoder.decode(nameParam, java.nio.charset.StandardCharsets.UTF_8.name()) } catch (_: Exception) { nameParam }
        !idParam.isNullOrBlank() -> try { URLDecoder.decode(idParam, java.nio.charset.StandardCharsets.UTF_8.name()) } catch (_: Exception) { idParam }
        else -> call.request.queryParameters["id"] ?: call.request.queryParameters["path"] ?: ""
    }

    if (targetIdOrName.isBlank()) {
        call.respondText("Missing video identifier", status = HttpStatusCode.BadRequest)
        return
    }

    // 1. Try StorageManager lookup (supports IDs, filenames, stripped prefixes)
    val file = StorageManager.getSandboxFileById(targetIdOrName)
        ?: StorageManager.resolveRelativePath(targetIdOrName)
        ?: FederatedMediaManager.findLocalFileByTitle(targetIdOrName)

    if (file != null && file.exists() && file.isFile) {
        respondDirectFileWithRange(call, file)
        return
    }

    // 2. Try Federated lookup
    val fedItem = FederatedMediaManager.getById(targetIdOrName)
    if (fedItem != null) {
        val direct = FederatedMediaManager.getDirectFile(fedItem)
        if (direct != null && direct.exists() && direct.isFile) {
            respondDirectFileWithRange(call, direct)
            return
        }
    }

    Logger.w(LogTag.HTTP, "Sandbox video file not found on disk: $targetIdOrName")
    call.respondText("Video file not found on disk: $targetIdOrName", status = HttpStatusCode.NotFound)
}

private suspend fun respondThumbnail(call: io.ktor.server.application.ApplicationCall) {
    val idParam = call.parameters["id"] ?: ""
    val targetId = try { URLDecoder.decode(idParam, java.nio.charset.StandardCharsets.UTF_8.name()) } catch (_: Exception) { idParam }
    val ctx = StorageManager.getContext()
    val fedItem = FederatedMediaManager.getById(targetId)
    val directFile = if (fedItem != null) FederatedMediaManager.getDirectFile(fedItem) else StorageManager.getSandboxFileById(targetId)
    val bytes = MediaThumbnailManager.getThumbnail(ctx, targetId, directFile)
    call.response.header(HttpHeaders.CacheControl, "public, max-age=86400")
    call.respondBytes(bytes, ContentType.Image.JPEG)
}

suspend fun respondDirectFileWithRange(call: io.ktor.server.application.ApplicationCall, file: File) {
    if (!file.exists() || !file.isFile) {
        call.respondText("File not found", status = HttpStatusCode.NotFound)
        return
    }

    val totalLength = file.length()
    val rangeHeader = call.request.headers[HttpHeaders.Range]

    val safeAsciiName = file.name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    call.response.header(
        HttpHeaders.ContentDisposition,
        "inline; filename=\"$safeAsciiName\""
    )
    call.response.header(HttpHeaders.AcceptRanges, "bytes")

    val ext = file.extension.lowercase()
    val mimeType = when (ext) {
        "mp4", "m4v" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "avi" -> "video/x-msvideo"
        "mov" -> "video/quicktime"
        "mp3" -> "audio/mpeg"
        "flac" -> "audio/flac"
        "wav" -> "audio/wav"
        else -> StorageManager.getMimeFromExtension(ext)
    }
    val contentType = runCatching { ContentType.parse(mimeType) }.getOrDefault(ContentType.Application.OctetStream)

    if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
        val ranges = rangeHeader.removePrefix("bytes=").split("-")
        val start = ranges[0].toLongOrNull() ?: 0L
        val end = if (ranges.size > 1 && ranges[1].isNotBlank()) {
            ranges[1].toLongOrNull()?.coerceAtMost(totalLength - 1) ?: (totalLength - 1)
        } else {
            totalLength - 1
        }

        val clampedStart = start.coerceIn(0L, (totalLength - 1).coerceAtLeast(0L))
        val clampedEnd = end.coerceIn(clampedStart, (totalLength - 1).coerceAtLeast(0L))
        val contentLength = clampedEnd - clampedStart + 1

        call.response.header(HttpHeaders.ContentRange, "bytes $clampedStart-$clampedEnd/$totalLength")
        call.response.header(HttpHeaders.ContentLength, contentLength.toString())

        call.respondOutputStream(contentType, HttpStatusCode.PartialContent) {
            RandomAccessFile(file, "r").use { raf ->
                raf.seek(clampedStart)
                var remaining = contentLength
                val buffer = ByteArray(64 * 1024)
                while (remaining > 0) {
                    val toRead = minOf(remaining, buffer.size.toLong()).toInt()
                    val bytesRead = raf.read(buffer, 0, toRead)
                    if (bytesRead <= 0) break
                    this.write(buffer, 0, bytesRead)
                    remaining -= bytesRead
                }
            }
        }
    } else {
        call.response.header(HttpHeaders.ContentLength, totalLength.toString())
        call.respondFile(file)
    }
}




