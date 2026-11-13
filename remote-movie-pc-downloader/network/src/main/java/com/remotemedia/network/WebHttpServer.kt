package com.remotemedia.network

import com.google.gson.Gson
import com.remotemedia.core.LogTag
import com.remotemedia.core.Logger
import com.remotemedia.storage.StorageManager
import com.remotemedia.storage.PlaybackProgressManager
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
import io.ktor.server.request.receive
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
import io.ktor.server.response.respondBytes
import java.io.File
import java.io.RandomAccessFile
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID

object WebHttpServer {

    const val API_VERSION = "1.0.0"
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

                    // Health check
                    get("/ping") {
                        call.respondText("pong", ContentType.Text.Plain)
                    }

                    // API versioning + feature flags (M0.4)
                    get("/capabilities") {
                        val caps = mapOf(
                            "api_version" to API_VERSION,
                            "features" to mapOf(
                                "jellyfin" to true,
                                "qbittorrent_v2" to true,
                                "dlna" to true,
                                "jackett" to true,
                                "torrent_streaming" to true,
                                "telegram_download" to true,
                                "ytdlp" to true,
                                "mesh_share" to false,
                                "watch_party" to false,
                                "auth" to true,
                                "qr_pairing" to true
                            )
                        )
                        call.respondText(gson.toJson(caps), ContentType.Application.Json)
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

                    post("/api/v2/auth/login") {
                        val params = call.receiveParameters()
                        val username = params["username"] ?: ""
                        val password = params["password"] ?: ""
                        val device = params["device"] ?: call.request.headers["User-Agent"] ?: "unknown"
                        val user = com.remotemedia.core.UserManager.authenticate(username, password)
                        if (user != null) {
                            val token = com.remotemedia.core.TokenManager.issue(username, device)
                            call.response.header("Set-Cookie", "SID=$token; HttpOnly; Path=/")
                            call.response.header("X-Auth-Token", token)
                            call.respondText("Ok.", ContentType.Text.Plain)
                        } else {
                            call.respondText("Fails.", ContentType.Text.Plain, HttpStatusCode.Forbidden)
                        }
                    }

                    get("/api/v2/auth/login") {
                        call.respondText("Ok.", ContentType.Text.Plain)
                    }

                    post("/api/v2/auth/logout") {
                        val token = call.request.headers["X-Auth-Token"]
                            ?: call.request.cookies["SID"] ?: ""
                        com.remotemedia.core.TokenManager.revoke(token)
                        call.respondText("Ok.", ContentType.Text.Plain)
                    }

                    get("/api/users") {
                        call.respondText(gson.toJson(com.remotemedia.core.UserManager.listUsers()),
                            ContentType.Application.Json)
                    }

                    get("/api/tokens") {
                        call.respondText(gson.toJson(com.remotemedia.core.TokenManager.listAll()),
                            ContentType.Application.Json)
                    }

                    post("/api/tokens/revoke") {
                        val params = call.receiveParameters()
                        val token = params["token"] ?: ""
                        val ok = com.remotemedia.core.TokenManager.revoke(token)
                        call.respondText(if (ok) "revoked" else "not_found", ContentType.Text.Plain)
                    }

                    get("/pair/offer") {
                        val offer = com.remotemedia.core.PairingManager.createOffer()
                        call.respondText(gson.toJson(mapOf(
                            "otp" to offer.otp,
                            "server_id" to offer.serverId,
                            "expires_at" to offer.expiresAt
                        )), ContentType.Application.Json)
                    }

                    post("/pair/redeem") {
                        val params = call.receiveParameters()
                        val otp = params["otp"] ?: call.request.queryParameters["otp"] ?: ""
                        val baseUrl = "http://${call.request.local.serverHost}:${call.request.local.serverPort}"
                        val payload = com.remotemedia.core.PairingManager.redeem(otp, baseUrl)
                        if (payload != null) {
                            call.respondText(gson.toJson(payload), ContentType.Application.Json)
                        } else {
                            call.respondText("invalid_or_expired", ContentType.Text.Plain,
                                HttpStatusCode.Gone)
                        }
                    }

                    get("/api/v2/app/version") {
                        call.respondText("v4.6.5", ContentType.Text.Plain)
                    }

                    post("/api/watch/state") {
                        try {
                            val body = call.receiveText()
                            val j = com.google.gson.JsonParser.parseString(body).asJsonObject
                            com.remotemedia.storage.WatchStateManager.upsert(
                                com.remotemedia.storage.WatchStateManager.WatchState(
                                    user = j["user"]?.asString ?: "default",
                                    itemId = j["item_id"]?.asString ?: "",
                                    positionSec = j["position_sec"]?.asLong ?: 0L,
                                    durationSec = j["duration_sec"]?.asLong ?: 0L,
                                    played = j["played"]?.asBoolean ?: false,
                                    updatedAt = System.currentTimeMillis()
                                )
                            )
                            call.respondText("{\"ok\":true}", ContentType.Application.Json)
                        } catch (e: Exception) {
                            call.respondText("{\"error\":\"${e.message}\"}", ContentType.Application.Json,
                                HttpStatusCode.BadRequest)
                        }
                    }

                    get("/api/watch/state") {
                        val user = call.request.queryParameters["user"] ?: "default"
                        val itemId = call.request.queryParameters["item_id"]
                        if (itemId != null) {
                            val s = com.remotemedia.storage.WatchStateManager.get(user, itemId)
                            call.respondText(gson.toJson(s), ContentType.Application.Json)
                        } else {
                            val list = com.remotemedia.storage.WatchStateManager.listForUser(user)
                            call.respondText(gson.toJson(list), ContentType.Application.Json)
                        }
                    }

                    // ── Drive Upload API (M2.1) ───────────────────────────────
                    // 1. Init session
                    post("/api/drive/upload/init") {
                        val name = call.request.queryParameters["name"] ?: "upload"
                        val size = call.request.queryParameters["size"]?.toLongOrNull() ?: 0L
                        val session = com.remotemedia.storage.DriveUploadManager.initSession(name, size)
                        call.respondText(gson.toJson(mapOf(
                            "upload_id" to session.uploadId,
                            "chunk_size" to com.remotemedia.storage.DriveUploadManager.CHUNK_SIZE
                        )), ContentType.Application.Json)
                    }

                    // 2. Write chunk — raw body bytes at given offset
                    post("/api/drive/upload/chunk") {
                        val uploadId = call.request.queryParameters["upload_id"] ?: ""
                        val offset = call.request.queryParameters["offset"]?.toLongOrNull() ?: 0L
                        val bytes = call.receive<ByteArray>()
                        try {
                            val received = com.remotemedia.storage.DriveUploadManager.writeChunk(uploadId, offset, bytes)
                            call.respondText(gson.toJson(mapOf("received" to received)),
                                ContentType.Application.Json)
                        } catch (e: Exception) {
                            call.respondText("{\"error\":\"${e.message}\"}", ContentType.Application.Json,
                                HttpStatusCode.BadRequest)
                        }
                    }

                    // 3. Complete — finalise and move to library
                    post("/api/drive/upload/complete") {
                        val uploadId = call.request.queryParameters["upload_id"] ?: ""
                        try {
                            val path = com.remotemedia.storage.DriveUploadManager.complete(uploadId)
                            call.respondText(gson.toJson(mapOf("path" to path)), ContentType.Application.Json)
                        } catch (e: Exception) {
                            call.respondText("{\"error\":\"${e.message}\"}", ContentType.Application.Json,
                                HttpStatusCode.BadRequest)
                        }
                    }

                    // 4. Cancel / abort
                    post("/api/drive/upload/cancel") {
                        val uploadId = call.request.queryParameters["upload_id"] ?: ""
                        com.remotemedia.storage.DriveUploadManager.cancel(uploadId)
                        call.respondText("{\"ok\":true}", ContentType.Application.Json)
                    }

                    // 5. Status check
                    get("/api/drive/upload/status") {
                        val uploadId = call.request.queryParameters["upload_id"] ?: ""
                        val s = com.remotemedia.storage.DriveUploadManager.getSession(uploadId)
                        if (s != null) {
                            call.respondText(gson.toJson(mapOf(
                                "upload_id" to s.uploadId, "file_name" to s.fileName,
                                "total_size" to s.totalSize, "bytes_received" to s.bytesReceived
                            )), ContentType.Application.Json)
                        } else {
                            call.respondText("{\"error\":\"not_found\"}", ContentType.Application.Json,
                                HttpStatusCode.NotFound)
                        }
                    }

                    // ── Drive Share Links (M2.2) ─────────────────────────────
                    post("/api/drive/share") {
                        val path = call.request.queryParameters["path"] ?: ""
                        val ttl = call.request.queryParameters["ttl_ms"]?.toLongOrNull()
                        val abs = com.remotemedia.storage.StorageManager.resolveRelativePath(path)?.absolutePath
                            ?: return@post call.respondText("{\"error\":\"not_found\"}", ContentType.Application.Json, HttpStatusCode.NotFound)
                        val link = com.remotemedia.storage.ShareLinkManager.create(abs, ttl)
                        call.respondText(gson.toJson(mapOf("token" to link.token, "url" to "/s/${link.token}")),
                            ContentType.Application.Json)
                    }

                    get("/s/{token}") {
                        val token = call.parameters["token"] ?: ""
                        val link = com.remotemedia.storage.ShareLinkManager.resolve(token)
                            ?: return@get call.respondText("not_found_or_expired", ContentType.Text.Plain, HttpStatusCode.Gone)
                        call.response.header(HttpHeaders.ContentDisposition,
                            ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, link.fileName).toString())
                        call.respondFile(java.io.File(link.absolutePath))
                    }

                    get("/api/drive/share") {
                        call.respondText(gson.toJson(com.remotemedia.storage.ShareLinkManager.listAll()),
                            ContentType.Application.Json)
                    }

                    post("/api/drive/share/revoke") {
                        val token = call.request.queryParameters["token"] ?: ""
                        val ok = com.remotemedia.storage.ShareLinkManager.revoke(token)
                        call.respondText(if (ok) "{\"ok\":true}" else "{\"error\":\"not_found\"}",
                            ContentType.Application.Json)
                    }

                    // ── Thumbnails (M2.3) ─────────────────────────────────────
                    get("/api/drive/thumb") {
                        val path = call.request.queryParameters["path"] ?: ""
                        val file = com.remotemedia.storage.StorageManager.resolveRelativePath(path)
                            ?: return@get call.respondText("{\"error\":\"not_found\"}", ContentType.Application.Json, HttpStatusCode.NotFound)
                        val bytes = com.remotemedia.media.MediaThumbnailManager.getThumbnail(
                            context = null, mediaId = file.canonicalPath, targetFile = file
                        )
                        call.respondBytes(bytes, ContentType.Image.JPEG)
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

                    get("/api/v2/torrents/categories") {
                        call.respondText("{}", ContentType.Application.Json)
                    }
                    get("/api/v2/torrents/tags") {
                        call.respondText("[]", ContentType.Application.Json)
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
        val subdirs = StorageManager.listSubdirectories(currentDir)
        val files = StorageManager.listFiles(currentDir).filter {
            val parent = it.parentFile ?: return@filter false
            runCatching { parent.canonicalPath == currentDir.canonicalPath }.getOrElse {
                parent.absolutePath == currentDir.absolutePath
            }
        }
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

        for (dir in subdirs) {
            val relPath = getRelativePath(dir)
            val encodedRelPath = URLEncoder.encode(relPath, StandardCharsets.UTF_8.name())
            rows.append(
                """
                <tr>
                    <td><a href="/?path=$encodedRelPath" class="folder">📁 ${dir.name}/</a></td>
                    <td>Folder</td>
                    <td>-</td>
                </tr>
                """.trimIndent()
            )
        }

        for (file in files) {
            val relPath = getRelativePath(file)
            val encodedRelPath = URLEncoder.encode(relPath, StandardCharsets.UTF_8.name())

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
                    if (['mp3','flac','m4a','aac','wav'].includes(ext)) return 'Music';
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
