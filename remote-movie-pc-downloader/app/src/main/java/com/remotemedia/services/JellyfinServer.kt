package com.remotemedia.services

import com.google.gson.Gson
import com.remotemedia.core.FederatedMediaManager
import com.remotemedia.core.HostMediaManager
import com.remotemedia.core.NetworkShareManager
import com.remotemedia.core.LogTag
import com.remotemedia.core.Logger
import com.remotemedia.core.StorageManager
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.autohead.AutoHeadResponse
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.partialcontent.PartialContent
import io.ktor.server.request.header
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondFile
import io.ktor.server.response.respondOutputStream
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.options
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import com.remotemedia.core.MediaThumbnailManager
import com.remotemedia.download.TelegramMediaCatalog
import com.remotemedia.download.TorrentEngine
import com.remotemedia.core.PlaybackProgressManager
import io.ktor.server.request.receiveText
import java.io.File
import java.io.RandomAccessFile
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.UUID

object JellyfinServer {

    private var serverEngine: ApplicationEngine? = null
    private val gson = Gson()
    val dummyServerId = UUID.nameUUIDFromBytes("pocketnode-jellyfin-server".toByteArray()).toString().replace("-", "")

    fun start(port: Int = 8096) {
        if (serverEngine != null) return

        try {
            serverEngine = embeddedServer(CIO, host = "0.0.0.0", port = port) {
                install(PartialContent)
                install(AutoHeadResponse)

                intercept(io.ktor.server.application.ApplicationCallPipeline.Plugins) {
                    val reqOrigin = call.request.header("Origin")
                    val allowOrigin = if (!reqOrigin.isNullOrBlank()) reqOrigin else "*"
                    call.response.header("Access-Control-Allow-Origin", allowOrigin)
                    call.response.header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, HEAD, PATCH")
                    call.response.header("Access-Control-Allow-Headers", "*")
                    call.response.header("Access-Control-Expose-Headers", "Content-Range, Accept-Ranges, Content-Length")

                    if (call.request.httpMethod == io.ktor.http.HttpMethod.Options) {
                        call.respond(HttpStatusCode.NoContent)
                        finish()
                    }
                }

                routing {
                    // Preflight CORS for all endpoints
                    options("/{path...}") { call.respond(HttpStatusCode.NoContent) }

                    // Embedded HTML5 Jellyfin Web Client UI (Authentic PC Style)
                    get("/") { respondJellyfinWebUi(call) }
                    get("/web") { respondJellyfinWebUi(call) }
                    get("/web/") { respondJellyfinWebUi(call) }
                    get("/web/index.html") { respondJellyfinWebUi(call) }
                    get("/index.html") { respondJellyfinWebUi(call) }

                    // Video Still Card & Thumbnail Endpoints (Supports both iOS Jellyfin App & Web UI)
                    get("/Videos/{id}/thumbnail.jpg") { respondThumbnail(call) }
                    get("/Videos/{id}/thumbnail") { respondThumbnail(call) }
                    get("/videos/{id}/thumbnail.jpg") { respondThumbnail(call) }
                    get("/videos/{id}/thumbnail") { respondThumbnail(call) }
                    get("/Items/{id}/Images/Primary") { respondThumbnail(call) }
                    get("/items/{id}/images/primary") { respondThumbnail(call) }
                    get("/Items/{id}/Images/Primary/{tag}") { respondThumbnail(call) }
                    get("/items/{id}/images/primary/{tag}") { respondThumbnail(call) }
                    get("/Items/{id}/Images/Thumb") { respondThumbnail(call) }
                    get("/items/{id}/images/thumb") { respondThumbnail(call) }
                    get("/Items/{id}/Images/Thumb/{tag}") { respondThumbnail(call) }
                    get("/items/{id}/images/thumb/{tag}") { respondThumbnail(call) }
                    get("/Items/{id}/Images/Backdrop") { respondThumbnail(call) }
                    get("/items/{id}/images/backdrop") { respondThumbnail(call) }
                    get("/Items/{id}/Images/Backdrop/{tag}") { respondThumbnail(call) }
                    get("/items/{id}/images/backdrop/{tag}") { respondThumbnail(call) }
                    get("/api/thumbnail/{id}") { respondThumbnail(call) }

                    // Metadata & Media Scanner Trigger
                    get("/api/scan") { respondTriggerScan(call) }
                    post("/api/scan") { respondTriggerScan(call) }

                    // System Information Endpoints (Crucial for TV Client Handshake)
                    get("/System/Info/Public") { respondInfoPublic(call, port) }
                    get("/system/info/public") { respondInfoPublic(call, port) }
                    get("/health") { call.respondText("Healthy", ContentType.Text.Plain) }

                    // PWA & Web Client Manifest Endpoints (Required by LG webOS & Smart TV clients)
                    get("/web/manifest.json") { respondManifest(call) }
                    get("/manifest.json") { respondManifest(call) }
                    get("/web/manifest.webmanifest") { respondManifest(call) }
                    get("/manifest.webmanifest") { respondManifest(call) }
                    get("/web/config.json") { respondWebConfig(call) }
                    get("/config.json") { respondWebConfig(call) }

                    get("/System/Info") { respondInfo(call, port) }
                    get("/system/info") { respondInfo(call, port) }

                    get("/System/Endpoint") { respondEndpoint(call) }
                    get("/system/endpoint") { respondEndpoint(call) }

                    // Branding & Configuration Endpoints
                    get("/Branding/Configuration") { respondBranding(call) }
                    get("/branding/configuration") { respondBranding(call) }
                    get("/Branding/Css") { call.respondText("", ContentType.Text.CSS) }
                    get("/branding/css") { call.respondText("", ContentType.Text.CSS) }
                    get("/Branding/Css.css") { call.respondText("", ContentType.Text.CSS) }
                    get("/branding/css.css") { call.respondText("", ContentType.Text.CSS) }

                    get("/System/Configuration") { respondSystemConfig(call) }
                    get("/system/configuration") { respondSystemConfig(call) }

                    get("/QuickConnect/Enabled") { call.respondText("false", ContentType.Application.Json) }
                    get("/quickconnect/enabled") { call.respondText("false", ContentType.Application.Json) }
                    get("/QuickConnect/Initiate") { call.respondText("{}", ContentType.Application.Json) }
                    get("/quickconnect/initiate") { call.respondText("{}", ContentType.Application.Json) }

                    get("/Localization/Options") { call.respondText("[]", ContentType.Application.Json) }
                    get("/localization/options") { call.respondText("[]", ContentType.Application.Json) }
                    get("/Localization/ParentalRatings") { call.respondText("[]", ContentType.Application.Json) }
                    get("/localization/parentalratings") { call.respondText("[]", ContentType.Application.Json) }

                    get("/DisplayPreferences/usersettings") { call.respondText(gson.toJson(mapOf("CustomPrefs" to emptyMap<String, String>())), ContentType.Application.Json) }
                    get("/displaypreferences/usersettings") { call.respondText(gson.toJson(mapOf("CustomPrefs" to emptyMap<String, String>())), ContentType.Application.Json) }
                    get("/DisplayPreferences/{id}") { call.respondText(gson.toJson(mapOf("CustomPrefs" to emptyMap<String, String>())), ContentType.Application.Json) }
                    get("/displaypreferences/{id}") { call.respondText(gson.toJson(mapOf("CustomPrefs" to emptyMap<String, String>())), ContentType.Application.Json) }
                    post("/DisplayPreferences/{id}") { call.respondText(gson.toJson(mapOf("CustomPrefs" to emptyMap<String, String>())), ContentType.Application.Json) }
                    post("/displaypreferences/{id}") { call.respondText(gson.toJson(mapOf("CustomPrefs" to emptyMap<String, String>())), ContentType.Application.Json) }

                    // Users Endpoints
                    get("/Users/Public") { respondPublicUsers(call) }
                    get("/users/public") { respondPublicUsers(call) }

                    // Authentication Endpoints
                    post("/Users/AuthenticateByName") { respondAuth(call) }
                    post("/users/authenticatebyname") { respondAuth(call) }

                    get("/Users/{userId}") { respondUser(call) }
                    get("/users/{userId}") { respondUser(call) }
                    get("/Users/Me") { respondUser(call) }
                    get("/users/me") { respondUser(call) }

                    // User Views / Libraries Endpoint
                    get("/UserViews") { respondViews(call) }
                    get("/userviews") { respondViews(call) }
                    get("/Users/{userId}/Views") { respondViews(call) }
                    get("/users/{userId}/views") { respondViews(call) }

                    // Home Screen sections (Resume & Latest) - MUST be defined before /Items/{id}
                    get("/Items/Resume") { respondResume(call) }
                    get("/items/resume") { respondResume(call) }
                    get("/Users/{userId}/Items/Resume") { respondResume(call) }
                    get("/users/{userId}/items/resume") { respondResume(call) }
                    get("/api/playback/last") { respondPlaybackLast(call) }
                    get("/api/playback/resume") { respondResume(call) }
                    get("/api/playback/progress") { respondPlaybackLast(call) }
                    post("/api/playback/progress") { respondPlaybackProgress(call) }

                    get("/Items/Latest") { respondLatest(call) }
                    get("/items/latest") { respondLatest(call) }
                    get("/Users/{userId}/Items/Latest") { respondLatest(call) }
                    get("/users/{userId}/items/latest") { respondLatest(call) }

                    // Media Items Listing & Detail (for TV catalog navigation)
                    get("/Items") { respondItems(call) }
                    get("/items") { respondItems(call) }
                    get("/Users/{userId}/Items") { respondItems(call) }
                    get("/users/{userId}/items") { respondItems(call) }

                    get("/Items/{id}") { respondSingleItem(call) }
                    get("/items/{id}") { respondSingleItem(call) }
                    get("/Users/{userId}/Items/{id}") { respondSingleItem(call) }
                    get("/users/{userId}/items/{id}") { respondSingleItem(call) }

                    // Playback Stream Info
                    post("/Items/{id}/PlaybackInfo") { respondPlaybackInfo(call) }
                    get("/Items/{id}/PlaybackInfo") { respondPlaybackInfo(call) }
                    post("/items/{id}/playbackinfo") { respondPlaybackInfo(call) }
                    get("/items/{id}/playbackinfo") { respondPlaybackInfo(call) }

                    // Video Stream Direct Play Endpoint
                    get("/Videos/{id}/stream") { respondVideoStream(call) }
                    get("/Videos/{id}/stream.{container}") { respondVideoStream(call) }
                    get("/videos/{id}/stream") { respondVideoStream(call) }
                    get("/videos/{id}/stream.{container}") { respondVideoStream(call) }
                    get("/video/{name...}") { respondVideoStream(call) }

                    // App APK Downloads & Distribution Endpoints
                    get("/app") { respondJellyfinWebUi(call) }
                    get("/apk") { respondJellyfinWebUi(call) }
                    get("/apk/client") { respondApkDownload(call, isClient = true) }
                    get("/apk/server") { respondApkDownload(call, isClient = false) }
                    get("/download/pocketnode-client.apk") { respondApkDownload(call, isClient = true) }
                    get("/download/pocketnode-server.apk") { respondApkDownload(call, isClient = false) }
                    get("/download/app.apk") { respondApkDownload(call, isClient = true) }
                    get("/api/apk/info") { respondApkInfo(call) }

                    // Sessions & Capabilities (TV Client reporting)
                    post("/Sessions/Capabilities/Full") { call.respondText("{}", ContentType.Application.Json) }
                    post("/sessions/capabilities/full") { call.respondText("{}", ContentType.Application.Json) }
                    post("/Sessions/Playing") { call.respondText("{}", ContentType.Application.Json) }
                    post("/sessions/playing") { call.respondText("{}", ContentType.Application.Json) }
                    post("/Sessions/Playing/Progress") { respondSessionProgress(call) }
                    post("/sessions/playing/progress") { respondSessionProgress(call) }
                    post("/Sessions/Playing/Stopped") { respondSessionProgress(call) }
                    post("/sessions/playing/stopped") { respondSessionProgress(call) }

                    // Wildcard Handlers for unhandled GET/POST/HEAD queries from various TV clients (supports double slashes e.g. //System/Info/Public)
                    get("/{path...}") {
                        val requestedPath = call.request.path()
                        val normalized = requestedPath.replace("//", "/")
                        Logger.i(LogTag.JELLYFIN, "Handled Jellyfin GET fallback: $requestedPath (normalized: $normalized)")

                        when {
                            normalized.contains("manifest", ignoreCase = true) -> respondManifest(call)
                            normalized.contains("System/Info/Public", ignoreCase = true) -> respondInfoPublic(call, port)
                            normalized.contains("System/Info", ignoreCase = true) -> respondInfo(call, port)
                            normalized.contains("System/Endpoint", ignoreCase = true) -> respondEndpoint(call)
                            normalized.contains("Branding/Configuration", ignoreCase = true) -> respondBranding(call)
                            normalized.contains("config.json", ignoreCase = true) -> respondWebConfig(call)
                            normalized.contains("QuickConnect/Enabled", ignoreCase = true) -> call.respondText("false", ContentType.Application.Json)
                            normalized.contains("DisplayPreferences", ignoreCase = true) -> call.respondText(gson.toJson(mapOf("CustomPrefs" to emptyMap<String, String>())), ContentType.Application.Json)
                            normalized.contains("Users/Public", ignoreCase = true) -> respondPublicUsers(call)
                            normalized.contains("UserViews", ignoreCase = true) || normalized.contains("/Views", ignoreCase = true) -> respondViews(call)
                            normalized.contains("Latest", ignoreCase = true) -> respondLatest(call)
                            normalized.contains("Resume", ignoreCase = true) || normalized.contains("playback/resume", ignoreCase = true) -> respondResume(call)
                            normalized.contains("playback/last", ignoreCase = true) -> respondPlaybackLast(call)
                            normalized.contains("PlaybackInfo", ignoreCase = true) -> respondPlaybackInfo(call)
                            normalized.contains("web/index.html", ignoreCase = true) || normalized.endsWith("/web", ignoreCase = true) || normalized.endsWith("/web/", ignoreCase = true) -> respondJellyfinWebUi(call)
                            else -> {
                                val fallbackResponse = mapOf(
                                    "Items" to emptyList<Any>(),
                                    "TotalRecordCount" to 0
                                )
                                call.respondText(gson.toJson(fallbackResponse), ContentType.Application.Json)
                            }
                        }
                    }
                    post("/{path...}") {
                        val requestedPath = call.request.path()
                        val normalized = requestedPath.replace("//", "/")
                        Logger.i(LogTag.JELLYFIN, "Handled Jellyfin POST fallback: $requestedPath (normalized: $normalized)")

                        when {
                            normalized.contains("PlaybackInfo", ignoreCase = true) -> respondPlaybackInfo(call)
                            normalized.contains("AuthenticateByName", ignoreCase = true) -> respondAuth(call)
                            normalized.contains("DisplayPreferences", ignoreCase = true) -> call.respondText(gson.toJson(mapOf("CustomPrefs" to emptyMap<String, String>())), ContentType.Application.Json)
                            normalized.contains("playback/progress", ignoreCase = true) -> respondPlaybackProgress(call)
                            normalized.contains("Playing/Progress", ignoreCase = true) || normalized.contains("Playing/Stopped", ignoreCase = true) -> respondSessionProgress(call)
                            else -> call.respondText("{}", ContentType.Application.Json)
                        }
                    }
                }
            }.start(wait = false)

            Logger.i(LogTag.JELLYFIN, "Jellyfin API Server running on host 0.0.0.0:$port")
        } catch (e: Exception) {
            Logger.e(LogTag.JELLYFIN, "Failed to start Jellyfin Server: ${e.message}")
        }
    }

    fun stop() {
        serverEngine?.stop(1000, 2000)
        serverEngine = null
        Logger.i(LogTag.JELLYFIN, "Jellyfin API Server stopped.")
    }

    private suspend fun respondJellyfinWebUi(call: io.ktor.server.application.ApplicationCall) {
        FederatedMediaManager.refresh()

        val allVideoItems = FederatedMediaManager.getByCategory("Movies") +
                            FederatedMediaManager.getByCategory("Videos")
        val sandboxItems = allVideoItems.filter { it.origin == com.remotemedia.core.MediaOrigin.SANDBOX }

        // Check for last played media to populate hero spotlight & resume state
        val lastPlayed = PlaybackProgressManager.getLastPlayed()
        var spotlightResumeSeconds = 0L
        val spotlightTitle: String
        val spotlightSub: String
        val spotlightDesc: String
        val spotlightThumb: String
        val spotlightBadge: String
        val firstStreamUrl: String
        val firstTitle: String

        if (lastPlayed != null && lastPlayed.positionSeconds > 0L) {
            spotlightResumeSeconds = lastPlayed.positionSeconds
            firstStreamUrl = if (lastPlayed.streamUrl.isNotBlank()) lastPlayed.streamUrl else "/Videos/${lastPlayed.itemId}/stream.mp4"
            firstTitle = lastPlayed.title.replace("'", "\\'")
            spotlightTitle = MediaThumbnailManager.cleanTitle(lastPlayed.title)
            val pctInt = (lastPlayed.percent * 100).toInt()
            val posStr = formatSecondsToTime(lastPlayed.positionSeconds)
            spotlightSub = "CONTINUE WATCHING • Resuming at $posStr ($pctInt%)"
            spotlightDesc = "Pick up right where you left off. Zero transcoding delay with native high-efficiency direct playback."
            spotlightThumb = if (lastPlayed.itemId.isNotBlank()) "/Videos/${lastPlayed.itemId}/thumbnail.jpg" else "https://images.unsplash.com/photo-1514933651103-005eec06c04b?auto=format&fit=crop&w=1200&q=80"
            spotlightBadge = "[RESUME $pctInt%]"
        } else {
            val firstItem = allVideoItems.firstOrNull()
            firstStreamUrl = if (firstItem != null) "/Videos/${firstItem.id}/stream.mp4" else "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
            firstTitle = (firstItem?.displayName ?: "Ratatouille (2007)").replace("'", "\\'")
            spotlightTitle = if (firstItem != null) MediaThumbnailManager.cleanTitle(firstItem.displayName) else "Ratatouille"
            spotlightSub = if (firstItem != null) "${firstItem.originLabel} • Lossless Direct Play" else "BANK HERO • PIXAR UNIFIED4K"
            spotlightDesc = if (firstItem != null) "Ready to stream in ultra high definition directly from your personal pocket vault. Zero re-encoding required." else "A young rat named Remy dreams of becoming a renowned French chef, teaming up with an awkward garbage boy to create culinary magic in Paris against all odds."
            spotlightThumb = if (firstItem != null) "/Videos/${firstItem.id}/thumbnail.jpg" else "https://images.unsplash.com/photo-1514933651103-005eec06c04b?auto=format&fit=crop&w=1200&q=80"
            spotlightBadge = if (firstItem != null) firstItem.origin.badge else "[PIXAR 4K]"
        }

        // 0. CONTINUE WATCHING SHELF (In-progress media from server & client)
        val continueWatchingCards = StringBuilder()
        val resumeList = PlaybackProgressManager.getAllResumeItems()
        for (item in resumeList) {
            val streamUrl = if (item.streamUrl.isNotBlank()) item.streamUrl else "/Videos/${item.itemId}/stream.mp4"
            val cleanDisplay = MediaThumbnailManager.cleanTitle(item.title).replace("'", "\\'").replace("\"", "&quot;")
            val pctInt = (item.percent * 100).toInt()
            val posStr = formatSecondsToTime(item.positionSeconds)
            val durStr = formatSecondsToTime(item.durationSeconds)
            val thumbUrl = if (item.itemId.isNotBlank()) "/Videos/${item.itemId}/thumbnail.jpg" else "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?auto=format&fit=crop&w=400&q=80"

            continueWatchingCards.append("""
                <div class="movie-card" tabindex="0" onclick="playMedia('$streamUrl', '$cleanDisplay', ${item.positionSeconds})" onkeydown="if(event.key==='Enter'||event.keyCode===13)this.click()">
                    <div class="poster-container">
                        <img src="$thumbUrl" class="poster-img" alt="$cleanDisplay" loading="lazy" onerror="this.src='https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?auto=format&fit=crop&w=400&q=80';" />
                        <div class="card-badge-top-left">RESUME</div>
                        <div class="card-badge-top-right">★ $pctInt%</div>
                        <div class="poster-hover-overlay">
                            <div class="play-circle-btn">▶</div>
                        </div>
                        <div style="position: absolute; bottom: 0; left: 0; right: 0; height: 4px; background: rgba(0,0,0,0.6); z-index: 5;">
                            <div style="width: $pctInt%; height: 100%; background: var(--pn-red);"></div>
                        </div>
                    </div>
                    <div class="movie-info">
                        <div class="movie-title">$cleanDisplay</div>
                        <div class="movie-meta">$posStr / $durStr • $pctInt%</div>
                        <button class="watch-btn" onclick="event.stopPropagation(); playMedia('$streamUrl', '$cleanDisplay', ${item.positionSeconds})">RESUME</button>
                    </div>
                </div>
            """.trimIndent())
        }

        // 1. Recently Added Cards (Real Media from Library)
        val recentlyAddedCards = StringBuilder()
        if (allVideoItems.isNotEmpty()) {
            for (item in allVideoItems) {
                val streamUrl = "/Videos/${item.id}/stream.mp4"
                val cleanDisplay = MediaThumbnailManager.cleanTitle(item.displayName).replace("'", "\\'").replace("\"", "&quot;")
                val sizeStr = formatSize(item.sizeBytes)
                val badge = item.origin.badge
                recentlyAddedCards.append("""
                    <div class="movie-card" tabindex="0" onclick="playMedia('$streamUrl', '$cleanDisplay')" onkeydown="if(event.key==='Enter'||event.keyCode===13)this.click()">
                        <div class="poster-container">
                            <img src="/Videos/${item.id}/thumbnail.jpg" class="poster-img" alt="$cleanDisplay" loading="lazy" onerror="this.src='https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?auto=format&fit=crop&w=400&q=80';" />
                            <div class="card-badge-top-left">$badge</div>
                            <div class="card-badge-top-right">★ 1080P</div>
                            <div class="poster-hover-overlay">
                                <div class="play-circle-btn">▶</div>
                            </div>
                        </div>
                        <div class="movie-info">
                            <div class="movie-title">$cleanDisplay</div>
                            <div class="movie-meta">$sizeStr • Direct Play</div>
                            <button class="watch-btn" onclick="event.stopPropagation(); playMedia('$streamUrl', '$cleanDisplay')">WATCH</button>
                        </div>
                    </div>
                """.trimIndent())
            }
        }

        // 2. OFFLINE SANDBOX VAULT (Files saved to local disk for 100% offline play)
        val sandboxCards = StringBuilder()
        val allSandboxMedia = (sandboxItems.mapNotNull { FederatedMediaManager.getDirectFile(it) } +
            StorageManager.listFiles(StorageManager.getSandboxFolder()).filter { it.isFile && StorageManager.isMediaFile(it.name) } +
            StorageManager.listFiles(StorageManager.getMoviesFolder()).filter { it.isFile && StorageManager.isMediaFile(it.name) }
        ).distinctBy { it.absolutePath }

        if (allSandboxMedia.isNotEmpty()) {
            for (file in allSandboxMedia) {
                val cleanDisplay = MediaThumbnailManager.cleanTitle(file.nameWithoutExtension).replace("'", "\\'").replace("\"", "&quot;")
                val id = UUID.nameUUIDFromBytes(file.canonicalPath.toByteArray()).toString()
                val streamUrl = "/Videos/$id/stream.mp4"
                val sizeStr = formatSize(file.length())
                sandboxCards.append("""
                    <div class="movie-card" tabindex="0" onclick="playMedia('$streamUrl', '$cleanDisplay')" onkeydown="if(event.key==='Enter'||event.keyCode===13)this.click()">
                        <div class="poster-container">
                            <img src="/Videos/$id/thumbnail.jpg" class="poster-img" alt="$cleanDisplay" loading="lazy" onerror="this.src='https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?auto=format&fit=crop&w=400&q=80';" />
                            <div class="card-badge-top-left">SANDBOX</div>
                            <div class="card-badge-top-right">★ OFFLINE</div>
                            <div class="poster-hover-overlay">
                                <div class="play-circle-btn">▶</div>
                            </div>
                        </div>
                        <div class="movie-info">
                            <div class="movie-title">$cleanDisplay</div>
                            <div class="movie-meta">$sizeStr • Local Storage</div>
                            <button class="watch-btn" onclick="event.stopPropagation(); playMedia('$streamUrl', '$cleanDisplay')">WATCH</button>
                        </div>
                    </div>
                """.trimIndent())
            }
        } else {
            sandboxCards.append("""
                <div style="grid-column: 1 / -1; padding: 2.5rem 1.5rem; background: var(--bg-surface); border: 1.5px dashed var(--border-light); border-radius: 16px; text-align: center;">
                    <div style="font-size: 2rem; margin-bottom: 0.5rem;">📥</div>
                    <div style="font-weight: 700; color: var(--text-heading); font-size: 1.1rem; margin-bottom: 0.25rem;">Sandbox Vault Empty</div>
                    <div style="font-size: 0.85rem; color: var(--text-muted); max-width: 420px; margin: 0 auto;">Files downloaded from Telegram or Torrents will appear here for 100% offline direct streaming.</div>
                </div>
            """.trimIndent())
        }

        // 3. Telegram Cloud Library
        val telegramCards = StringBuilder()
        val tgItems = runCatching { TelegramMediaCatalog.items.value }.getOrDefault(emptyList())
        if (tgItems.isNotEmpty()) {
            for (tg in tgItems) {
                val clean = MediaThumbnailManager.cleanTitle(tg.cleanTitle.ifBlank { tg.fileName }).replace("'", "\\'").replace("\"", "&quot;")
                val streamUrl = tg.streamUrl
                val sizeStr = tg.sizeFormatted
                telegramCards.append("""
                    <div class="movie-card" tabindex="0" onclick="playMedia('$streamUrl', '$clean')" onkeydown="if(event.key==='Enter'||event.keyCode===13)this.click()">
                        <div class="poster-container">
                            <img src="https://images.unsplash.com/photo-1518709268805-4e9042af9f23?auto=format&fit=crop&w=400&q=80" class="poster-img" alt="$clean" loading="lazy" />
                            <div class="card-badge-top-left">TELEGRAM</div>
                            <div class="card-badge-top-right">★ CLOUD</div>
                            <div class="poster-hover-overlay">
                                <div class="play-circle-btn">▶</div>
                            </div>
                        </div>
                        <div class="movie-info">
                            <div class="movie-title">$clean</div>
                            <div class="movie-meta">$sizeStr • Instant Stream</div>
                            <button class="watch-btn" onclick="event.stopPropagation(); playMedia('$streamUrl', '$clean')">WATCH</button>
                        </div>
                    </div>
                """.trimIndent())
            }
        } else {
            telegramCards.append("""
                <div style="grid-column: 1 / -1; padding: 2.5rem 1.5rem; background: var(--bg-surface); border: 1.5px dashed var(--border-light); border-radius: 16px; text-align: center;">
                    <div style="font-size: 2rem; margin-bottom: 0.5rem;">✈️</div>
                    <div style="font-weight: 700; color: var(--text-heading); font-size: 1.1rem; margin-bottom: 0.25rem;">Telegram Cloud Connected</div>
                    <div style="font-size: 0.85rem; color: var(--text-muted); max-width: 420px; margin: 0 auto;">Forward any movie or video to your Telegram bot or save in Saved Messages to stream directly.</div>
                </div>
            """.trimIndent())
        }

        // 4. TRENDING TORRENTS (One-Click Stream)
        val trendingCards = StringBuilder()
        val trending = getTrendingTorrents()
        for (tr in trending) {
            val clean = tr.title.replace("'", "\\'").replace("\"", "&quot;")
            val encodedMag = java.net.URLEncoder.encode(tr.magnetUri, StandardCharsets.UTF_8.name())
            val encodedTitle = java.net.URLEncoder.encode(tr.title, StandardCharsets.UTF_8.name())
            val streamUrl = "/stream/torrent?magnet=$encodedMag&title=$encodedTitle"
            trendingCards.append("""
                <div class="movie-card" tabindex="0" onclick="playMedia('$streamUrl', '$clean')" onkeydown="if(event.key==='Enter'||event.keyCode===13)this.click()">
                    <div class="poster-container">
                        <img src="https://images.unsplash.com/photo-1536440136628-849c177e76a1?auto=format&fit=crop&w=400&q=80" class="poster-img" alt="$clean" loading="lazy" />
                        <div class="card-badge-top-left">TORRENT</div>
                        <div class="card-badge-top-right">★ ${tr.rating}</div>
                        <div class="poster-hover-overlay">
                            <div class="play-circle-btn">▶</div>
                        </div>
                    </div>
                    <div class="movie-info">
                        <div class="movie-title">$clean</div>
                        <div class="movie-meta">${tr.quality} • ${tr.seeds} Seeds</div>
                        <button class="watch-btn" onclick="event.stopPropagation(); playMedia('$streamUrl', '$clean')">STREAM</button>
                    </div>
                </div>
            """.trimIndent())
        }

        // 5. Dynamic Real Queue
        val queueItemsHtml = StringBuilder()
        val queueList = allVideoItems.take(8)
        if (queueList.isNotEmpty()) {
            var counter = 1
            for (item in queueList) {
                val num = String.format("%02d", counter++)
                val isFirst = counter == 2
                val clean = MediaThumbnailManager.cleanTitle(item.displayName).replace("'", "\\'").replace("\"", "&quot;")
                val streamUrl = "/Videos/${item.id}/stream.mp4"
                val badge = if (isFirst) """<span class="q-playing-badge">PLAYING</span>""" else """<span class="q-playing-badge" style="background:#0284c7;">${item.origin.badge}</span>"""
                val icon = if (isFirst) """<span class="q-sound-icon">🔊</span>""" else """<span class="q-drag-icon">▶</span>"""
                queueItemsHtml.append("""
                    <div class="queue-row ${if (isFirst) "queue-row-active" else ""}" onclick="playMedia('$streamUrl', '$clean')">
                        <div class="q-num">$num</div>
                        <div class="q-info">
                            <div class="q-title-row">
                                <span class="q-title">$clean</span>
                                $badge
                            </div>
                            <div class="q-meta">${formatSize(item.sizeBytes)} • ${item.originLabel}</div>
                        </div>
                        <div class="q-action">$icon</div>
                    </div>
                """.trimIndent())
            }
        } else {
            queueItemsHtml.append("""
                <div style="padding: 1.5rem; text-align: center; color: var(--text-muted); font-size: 0.85rem;">
                    No media currently queued. Add movies to library or start a stream.
                </div>
            """.trimIndent())
        }

        // 6. Real Disk Space Telemetry
        val baseDir = runCatching { StorageManager.getBaseDir() }.getOrNull()
        val totalSpace = baseDir?.totalSpace ?: 0L
        val freeSpace = baseDir?.freeSpace ?: 0L
        val usedSpace = (totalSpace - freeSpace).coerceAtLeast(0L)
        val storagePercent = if (totalSpace > 0L) ((usedSpace.toDouble() / totalSpace.toDouble()) * 100).toInt() else 0
        val storageUsedStr = formatSize(usedSpace)
        val storageTotalStr = formatSize(totalSpace)

        // 7. Real Offline Files List for Storage Modal
        val offlineFilesHtml = StringBuilder()
        if (allSandboxMedia.isNotEmpty()) {
            for (f in allSandboxMedia.take(6)) {
                val clean = MediaThumbnailManager.cleanTitle(f.nameWithoutExtension).replace("'", "\\'").replace("\"", "&quot;")
                val id = UUID.nameUUIDFromBytes(f.canonicalPath.toByteArray()).toString()
                val streamUrl = "/Videos/$id/stream.mp4"
                val sizeStr = formatSize(f.length())
                offlineFilesHtml.append("""
                    <div style="display: flex; align-items: center; justify-content: space-between; padding: 0.75rem 0.85rem; background: var(--bg-surface); border: 1px solid var(--border-light); border-radius: 10px; margin-bottom: 0.5rem; gap: 0.75rem; width: 100%; box-sizing: border-box;">
                        <div style="display: flex; align-items: center; gap: 0.75rem; min-width: 0; flex: 1;">
                            <div style="font-size: 1.25rem; flex-shrink: 0; line-height: 1;">🎬</div>
                            <div style="min-width: 0; flex: 1; overflow: hidden;">
                                <div style="font-weight: 600; font-size: 0.88rem; color: var(--text-heading); white-space: nowrap; overflow: hidden; text-overflow: ellipsis;">$clean</div>
                                <div style="font-size: 0.75rem; color: var(--text-muted); white-space: nowrap; overflow: hidden; text-overflow: ellipsis;">$sizeStr • Offline Ready</div>
                            </div>
                        </div>
                        <button onclick="playMedia('$streamUrl', '$clean')" style="flex-shrink: 0; background: var(--pn-red); color: white; border: none; border-radius: 6px; padding: 0.4rem 0.85rem; font-size: 0.75rem; font-weight: 700; cursor: pointer; white-space: nowrap; letter-spacing: 0.5px;">PLAY</button>
                    </div>
                """.trimIndent())
            }
        } else {
            offlineFilesHtml.append("""
                <div style="padding: 1rem; text-align: center; color: var(--text-muted); font-size: 0.85rem;">
                    No local files saved in sandbox vault.
                </div>
            """.trimIndent())
        }

        val html = JellyfinWebUiBuilder.build(
            allVideoCount = allVideoItems.size,
            firstStreamUrl = firstStreamUrl,
            firstTitle = firstTitle,
            spotlightTitle = spotlightTitle,
            spotlightSub = spotlightSub,
            spotlightDesc = spotlightDesc,
            spotlightThumb = spotlightThumb,
            spotlightBadge = spotlightBadge,
            spotlightResumeSeconds = spotlightResumeSeconds,
            continueWatchingCardsHtml = continueWatchingCards.toString(),
            recentlyAddedCards = recentlyAddedCards.toString(),
            sandboxCardsHtml = sandboxCards.toString(),
            telegramCardsHtml = telegramCards.toString(),
            trendingCardsHtml = trendingCards.toString(),
            queueItemsHtml = queueItemsHtml.toString(),
            queueCount = queueList.size,
            storageUsedStr = storageUsedStr,
            storageTotalStr = storageTotalStr,
            storagePercent = storagePercent,
            offlineFilesHtml = offlineFilesHtml.toString()
        )

        call.respondText(html, ContentType.Text.Html)
    }

    private suspend fun respondThumbnail(call: io.ktor.server.application.ApplicationCall) {
        val id = call.parameters["id"] ?: ""
        val ctx = StorageManager.getContext()
        val fedItem = FederatedMediaManager.getById(id)
        val directFile = if (fedItem != null) FederatedMediaManager.getDirectFile(fedItem) else findFileById(id)
        val bytes = MediaThumbnailManager.getThumbnail(ctx, id, directFile)
        call.response.header(HttpHeaders.CacheControl, "public, max-age=86400")
        call.respondBytes(bytes, ContentType.Image.JPEG)
    }

    private suspend fun respondTriggerScan(call: io.ktor.server.application.ApplicationCall) {
        FederatedMediaManager.refresh()
        val allVideoItems = FederatedMediaManager.getByCategory("Movies") + FederatedMediaManager.getByCategory("Videos")
        call.respondText(
            gson.toJson(mapOf(
                "status" to "success",
                "scannedCount" to allVideoItems.size,
                "timestamp" to System.currentTimeMillis()
            )),
            ContentType.Application.Json
        )
    }

    private suspend fun respondManifest(call: io.ktor.server.application.ApplicationCall) {
        val manifest = mapOf(
            "name" to "PocketNode",
            "short_name" to "PocketNode",
            "shortname" to "PocketNode",
            "start_url" to "index.html",
            "description" to "PocketNode Media Vault",
            "lang" to "en-US",
            "display" to "standalone",
            "background_color" to "#090a14",
            "theme_color" to "#090a14",
            "icons" to listOf(
                mapOf(
                    "sizes" to "512x512",
                    "src" to "favicon.ico",
                    "type" to "image/png"
                )
            )
        )
        call.respondText(gson.toJson(manifest), ContentType.Application.Json)
    }

    private suspend fun respondWebConfig(call: io.ktor.server.application.ApplicationCall) {
        val config = mapOf(
            "includeCorsCredentials" to false,
            "multiserver" to false,
            "menuLinks" to emptyList<Any>(),
            "servers" to emptyList<Any>()
        )
        call.respondText(gson.toJson(config), ContentType.Application.Json)
    }

    private suspend fun respondInfoPublic(call: io.ktor.server.application.ApplicationCall, port: Int) {
        val requestHost = call.request.header("Host") ?: "127.0.0.1:$port"
        Logger.i(LogTag.JELLYFIN, "Jellyfin client requested /System/Info/Public from ${call.request.local.remoteHost} (Host: $requestHost)")
        val info = mapOf(
            "LocalAddress" to "http://$requestHost",
            "ServerName" to "PocketNode",
            "Version" to "10.8.13",
            "ProductName" to "Jellyfin Server",
            "OperatingSystem" to "Linux",
            "Id" to dummyServerId,
            "StartupWizardCompleted" to true
        )
        call.respondText(gson.toJson(info), ContentType.Application.Json)
    }

    private suspend fun respondInfo(call: io.ktor.server.application.ApplicationCall, port: Int = 8096) {
        val requestHost = call.request.header("Host") ?: "127.0.0.1:$port"
        val info = mapOf(
            "LocalAddress" to "http://$requestHost",
            "SystemUpdateLevel" to "0",
            "OperatingSystemState" to "Running",
            "ServerName" to "PocketNode",
            "Version" to "10.8.13",
            "ProductName" to "Jellyfin Server",
            "OperatingSystem" to "Linux",
            "Id" to dummyServerId,
            "StartupWizardCompleted" to true,
            "CanSelfRestart" to false,
            "CanSelfUpdate" to false,
            "HasPendingRestart" to false,
            "IsShuttingDown" to false
        )
        call.respondText(gson.toJson(info), ContentType.Application.Json)
    }

    private suspend fun respondEndpoint(call: io.ktor.server.application.ApplicationCall) {
        val endpoint = mapOf(
            "IsLocal" to true,
            "IsInNetwork" to true
        )
        call.respondText(gson.toJson(endpoint), ContentType.Application.Json)
    }

    private suspend fun respondBranding(call: io.ktor.server.application.ApplicationCall) {
        val branding = mapOf(
            "LoginDisclaimer" to "",
            "CustomCss" to "",
            "SplashscreenEnabled" to true
        )
        call.respondText(gson.toJson(branding), ContentType.Application.Json)
    }

    private suspend fun respondSystemConfig(call: io.ktor.server.application.ApplicationCall) {
        val config = mapOf(
            "IsCodecsInstalled" to true
        )
        call.respondText(gson.toJson(config), ContentType.Application.Json)
    }

    private suspend fun respondPublicUsers(call: io.ktor.server.application.ApplicationCall) {
        val rawUsers = JellyfinManager.getUsers()
        val userList = if (rawUsers.isNotEmpty()) rawUsers else listOf(
            JellyfinUser(id = dummyServerId, name = "Admin", role = "ADMIN")
        )
        val users = userList.map { user ->
            mapOf(
                "Name" to user.name,
                "ServerId" to dummyServerId,
                "Id" to user.id,
                "HasPassword" to user.hasPassword,
                "HasConfiguredPassword" to user.hasPassword,
                "HasConfiguredEasyPassword" to false,
                "EnableAutoLogin" to true,
                "Policy" to mapOf(
                    "IsAdministrator" to user.isAdmin,
                    "IsHidden" to false,
                    "EnableContentDownloading" to true,
                    "EnableMediaPlayback" to true,
                    "EnableAudioPlaybackTranscoding" to true,
                    "EnableVideoPlaybackTranscoding" to true,
                    "EnablePlaybackRemuxing" to true
                ),
                "Configuration" to mapOf(
                    "PlayDefaultAudioTrack" to true,
                    "SubtitleMode" to "Default",
                    "DisplayMissingEpisodes" to false
                )
            )
        }
        call.respondText(gson.toJson(users), ContentType.Application.Json)
    }

    private suspend fun respondUser(call: io.ktor.server.application.ApplicationCall) {
        val requestedUserId = call.parameters["userId"]
        val rawUsers = JellyfinManager.getUsers()
        val user = rawUsers.firstOrNull { it.id == requestedUserId } ?: rawUsers.firstOrNull() ?: JellyfinUser(id = dummyServerId, name = "Admin", role = "ADMIN")
        val userObj = mapOf(
            "Name" to user.name,
            "ServerId" to dummyServerId,
            "Id" to user.id,
            "HasPassword" to false,
            "HasConfiguredPassword" to false,
            "HasConfiguredEasyPassword" to false,
            "EnableAutoLogin" to true,
            "Policy" to mapOf(
                "IsAdministrator" to user.isAdmin,
                "IsHidden" to false,
                "EnableContentDownloading" to true,
                "EnableMediaPlayback" to true,
                "EnableAudioPlaybackTranscoding" to true,
                "EnableVideoPlaybackTranscoding" to true,
                "EnablePlaybackRemuxing" to true
            ),
            "Configuration" to mapOf(
                "PlayDefaultAudioTrack" to true,
                "SubtitleMode" to "Default",
                "DisplayMissingEpisodes" to false
            )
        )
        call.respondText(gson.toJson(userObj), ContentType.Application.Json)
    }

    private suspend fun respondAuth(call: io.ktor.server.application.ApplicationCall) {
        Logger.i(LogTag.JELLYFIN, "Client authenticating with Jellyfin server")
        val rawUsers = JellyfinManager.getUsers()
        val firstUser = rawUsers.firstOrNull() ?: JellyfinUser(id = dummyServerId, name = "Admin", role = "ADMIN")
        val authResponse = mapOf(
            "User" to mapOf(
                "Name" to firstUser.name,
                "ServerId" to dummyServerId,
                "Id" to firstUser.id,
                "HasPassword" to false,
                "HasConfiguredPassword" to false,
                "HasConfiguredEasyPassword" to false,
                "EnableAutoLogin" to true,
                "Policy" to mapOf(
                    "IsAdministrator" to firstUser.isAdmin,
                    "IsHidden" to false,
                    "EnableContentDownloading" to true,
                    "EnableMediaPlayback" to true,
                    "EnableAudioPlaybackTranscoding" to true,
                    "EnableVideoPlaybackTranscoding" to true,
                    "EnablePlaybackRemuxing" to true
                ),
                "Configuration" to mapOf(
                    "PlayDefaultAudioTrack" to true,
                    "SubtitleMode" to "Default",
                    "DisplayMissingEpisodes" to false
                )
            ),
            "SessionInfo" to mapOf(
                "Id" to "session-${System.currentTimeMillis()}",
                "UserId" to firstUser.id,
                "ServerId" to dummyServerId
            ),
            "AccessToken" to "pocketnode-token-" + System.currentTimeMillis(),
            "ServerId" to dummyServerId
        )
        call.respondText(gson.toJson(authResponse), ContentType.Application.Json)
    }

    private suspend fun respondViews(call: io.ktor.server.application.ApplicationCall) {
        FederatedMediaManager.refresh()
        val libraries = JellyfinManager.getLibraries()
        val defaultLibs = if (libraries.isNotEmpty()) libraries else listOf(
            JellyfinLibrary(
                id = UUID.nameUUIDFromBytes("movies-lib".toByteArray()).toString(),
                name = "Movies & Media",
                collectionType = "movies",
                folderPath = StorageManager.getMoviesDir().absolutePath
            )
        )
        val items = defaultLibs.map { lib ->
            mapOf(
                "Name" to lib.name,
                "ServerId" to dummyServerId,
                "Id" to lib.id,
                "Type" to "CollectionFolder",
                "CollectionType" to lib.collectionType
            )
        }
        val view = mapOf(
            "Items" to items,
            "TotalRecordCount" to items.size
        )
        call.respondText(gson.toJson(view), ContentType.Application.Json)
    }

    private suspend fun respondResume(call: io.ktor.server.application.ApplicationCall) {
        FederatedMediaManager.refresh()
        val resumeItems = PlaybackProgressManager.getAllResumeItems()
        val allItems = buildAllItemsList()
        val itemsToReturn = mutableListOf<Map<String, Any?>>()
        for (prog in resumeItems) {
            val match = allItems.firstOrNull {
                it["Id"] == prog.itemId ||
                it["Name"]?.toString().equals(prog.title, ignoreCase = true) ||
                (it["OriginalTitle"]?.toString().equals(prog.title, ignoreCase = true))
            }
            if (match != null) {
                itemsToReturn.add(match)
            } else {
                val clean = MediaThumbnailManager.cleanTitle(prog.title)
                val uData = mapOf(
                    "PlaybackPositionTicks" to prog.positionTicks,
                    "PlayCount" to 0,
                    "IsFavorite" to false,
                    "Played" to false,
                    "PlayedPercentage" to (prog.percent * 100.0)
                )
                itemsToReturn.add(mapOf(
                    "Name"          to clean,
                    "OriginalTitle" to prog.title,
                    "ServerId"      to dummyServerId,
                    "Id"            to prog.itemId.ifBlank { UUID.nameUUIDFromBytes(prog.title.toByteArray()).toString() },
                    "Type"          to "Movie",
                    "MediaType"     to "Video",
                    "IsFolder"      to false,
                    "Path"          to prog.streamUrl,
                    "RunTimeTicks"  to (prog.durationSeconds * 10_000_000L),
                    "UserData"      to uData
                ))
            }
        }
        val response = mapOf(
            "Items" to itemsToReturn,
            "TotalRecordCount" to itemsToReturn.size
        )
        call.respondText(gson.toJson(response), ContentType.Application.Json)
    }

    private suspend fun respondPlaybackLast(call: io.ktor.server.application.ApplicationCall) {
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

    private suspend fun respondPlaybackProgress(call: io.ktor.server.application.ApplicationCall) {
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
            Logger.e(LogTag.JELLYFIN, "Error in respondPlaybackProgress: ${e.message}")
            call.respondText("""{"ok":false,"error":"${e.message}"}""", ContentType.Application.Json)
        }
    }

    private suspend fun respondSessionProgress(call: io.ktor.server.application.ApplicationCall) {
        try {
            val body = call.receiveText()
            if (body.isNotBlank() && body.startsWith("{")) {
                val json = gson.fromJson(body, com.google.gson.JsonObject::class.java)
                val itemId = json.get("ItemId")?.asString ?: ""
                val ticks = json.get("PositionTicks")?.asLong ?: 0L
                val posSec = ticks / 10_000_000L
                if (itemId.isNotBlank() && posSec > 0L) {
                    val fed = FederatedMediaManager.getById(itemId)
                    val title = fed?.displayName ?: itemId
                    val durSec = 7200L
                    PlaybackProgressManager.updateProgress(
                        itemId = itemId,
                        title = title,
                        streamUrl = "/Videos/$itemId/stream.mp4",
                        positionSeconds = posSec,
                        durationSeconds = durSec
                    )
                }
            }
        } catch (_: Exception) {}
        call.respondText("{}", ContentType.Application.Json)
    }

    private fun createUserData(itemId: String, title: String): Map<String, Any> {
        val progress = PlaybackProgressManager.getProgress(itemId) ?: PlaybackProgressManager.getProgress(title)
        val posTicks = progress?.positionTicks ?: 0L
        val playedPct = if (progress != null) (progress.percent * 100.0) else 0.0
        val isPlayed = (progress?.percent ?: 0f) >= 0.92f
        return mapOf(
            "PlaybackPositionTicks" to posTicks,
            "PlayCount" to if (isPlayed) 1 else 0,
            "IsFavorite" to false,
            "Played" to isPlayed,
            "PlayedPercentage" to playedPct
        )
    }

    private suspend fun respondLatest(call: io.ktor.server.application.ApplicationCall) {
        FederatedMediaManager.refresh()
        val items = buildAllItemsList().take(20)
        call.respondText(gson.toJson(items), ContentType.Application.Json)
    }

    private fun buildAllItemsList(): List<Map<String, Any?>> {
        val items = mutableListOf<Map<String, Any?>>()

        if (FederatedMediaManager.combinedViewEnabled.value) {
            // Combined view — serve all federated items as a flat Jellyfin Items list
            val fedItems = FederatedMediaManager.getByCategory("Movies") +
                           FederatedMediaManager.getByCategory("Videos")
            for (fItem in fedItems) {
                val uData = createUserData(fItem.id, fItem.displayName)
                val prog = PlaybackProgressManager.getProgress(fItem.id) ?: PlaybackProgressManager.getProgress(fItem.displayName)
                val runTimeTicks = if (prog != null && prog.durationSeconds > 0) prog.durationSeconds * 10_000_000L else 36000000000L
                items.add(mapOf(
                    "Name"          to fItem.displayName,
                    "OriginalTitle" to fItem.name,
                    "ServerId"      to dummyServerId,
                    "Id"            to fItem.id,
                    "Type"          to "Movie",
                    "MediaType"     to "Video",
                    "IsFolder"      to false,
                    "Path"          to "[${fItem.origin.badge}] ${fItem.originLabel}",
                    "RunTimeTicks"  to runTimeTicks,
                    "Size"          to fItem.sizeBytes,
                    "Container"     to fItem.name.substringAfterLast('.', "mp4"),
                    "UserData"      to uData
                ))
            }
        } else {
            // Split-view — each source enumerated independently
            val libraries = JellyfinManager.getLibraries()
            for (lib in libraries) {
                val allFiles = StorageManager.listFiles(File(lib.folderPath))
                items.addAll(allFiles.distinctBy { it.absolutePath }.map { file ->
                    val id = UUID.nameUUIDFromBytes(file.name.toByteArray()).toString().replace("-", "")
                    val uData = createUserData(id, file.nameWithoutExtension)
                    val prog = PlaybackProgressManager.getProgress(id) ?: PlaybackProgressManager.getProgress(file.nameWithoutExtension)
                    val runTimeTicks = if (prog != null && prog.durationSeconds > 0) prog.durationSeconds * 10_000_000L else 36000000000L
                    mapOf(
                        "Name"          to file.nameWithoutExtension,
                        "OriginalTitle" to file.name,
                        "ServerId"      to dummyServerId,
                        "Id"            to id,
                        "Type"          to if (file.isDirectory) "Folder" else "Movie",
                        "MediaType"     to if (file.isDirectory) "Unknown" else "Video",
                        "IsFolder"      to file.isDirectory,
                        "Path"          to file.absolutePath,
                        "RunTimeTicks"  to runTimeTicks,
                        "Size"          to file.length(),
                        "Container"     to file.extension.ifEmpty { "mp4" },
                        "UserData"      to uData
                    )
                })
            }
            if (HostMediaManager.isSharingEnabled.value) {
                val hostMovies = HostMediaManager.getFilesByCategory("Movies") + HostMediaManager.getFilesByCategory("Videos")
                for (hfile in hostMovies) {
                    val uData = createUserData(hfile.id, hfile.name.substringBeforeLast('.'))
                    val prog = PlaybackProgressManager.getProgress(hfile.id) ?: PlaybackProgressManager.getProgress(hfile.name.substringBeforeLast('.'))
                    val runTimeTicks = if (prog != null && prog.durationSeconds > 0) prog.durationSeconds * 10_000_000L else 36000000000L
                    items.add(mapOf(
                        "Name"          to hfile.name.substringBeforeLast('.'),
                        "OriginalTitle" to hfile.name,
                        "ServerId"      to dummyServerId,
                        "Id"            to hfile.id,
                        "Type"          to "Movie",
                        "MediaType"     to "Video",
                        "IsFolder"      to false,
                        "Path"          to (hfile.realFilePath ?: hfile.uriString),
                        "RunTimeTicks"  to runTimeTicks,
                        "Size"          to hfile.sizeBytes,
                        "Container"     to hfile.name.substringAfterLast('.', "mp4"),
                        "UserData"      to uData
                    ))
                }
            }
            val netMovies = NetworkShareManager.getFilesByCategory("Movies") + NetworkShareManager.getFilesByCategory("Videos")
            for (nfile in netMovies) {
                val uData = createUserData(nfile.id, nfile.name.substringBeforeLast('.'))
                val prog = PlaybackProgressManager.getProgress(nfile.id) ?: PlaybackProgressManager.getProgress(nfile.name.substringBeforeLast('.'))
                val runTimeTicks = if (prog != null && prog.durationSeconds > 0) prog.durationSeconds * 10_000_000L else 36000000000L
                items.add(mapOf(
                    "Name"          to nfile.name.substringBeforeLast('.'),
                    "OriginalTitle" to nfile.name,
                    "ServerId"      to dummyServerId,
                    "Id"            to nfile.id,
                    "Type"          to "Movie",
                    "MediaType"     to "Video",
                    "IsFolder"      to false,
                    "Path"          to "\\\\${nfile.hostName}\\${nfile.relativePath}",
                    "RunTimeTicks"  to runTimeTicks,
                    "Size"          to nfile.sizeBytes,
                    "Container"     to nfile.name.substringAfterLast('.', "mp4"),
                    "UserData"      to uData
                ))
            }
        }
        return items
    }

    private suspend fun respondItems(call: io.ktor.server.application.ApplicationCall) {
        FederatedMediaManager.refresh()
        val items = buildAllItemsList()
        val result = mapOf(
            "Items" to items,
            "TotalRecordCount" to items.size
        )
        call.respondText(gson.toJson(result), ContentType.Application.Json)
    }

    private suspend fun respondSingleItem(call: io.ktor.server.application.ApplicationCall) {
        FederatedMediaManager.refresh()
        val id = call.parameters["id"]
        if (id.isNullOrBlank()) {
            call.respond(HttpStatusCode.NotFound)
            return
        }
        if (id.equals("Resume", ignoreCase = true)) {
            respondResume(call)
            return
        }
        if (id.equals("Latest", ignoreCase = true)) {
            val items = buildAllItemsList().take(20)
            call.respondText(gson.toJson(items), ContentType.Application.Json)
            return
        }
        val items = buildAllItemsList()
        val item = items.firstOrNull { it["Id"] == id || it["Id"]?.toString()?.replace("-", "") == id.replace("-", "") }
        if (item != null) {
            val detail = item.toMutableMap()
            detail["MediaSources"] = listOf(
                mapOf(
                    "Id" to id,
                    "Path" to "/Videos/$id/stream",
                    "Protocol" to "Http",
                    "Container" to (item["Container"] ?: "mp4"),
                    "IsRemote" to false,
                    "SupportsDirectPlay" to true,
                    "SupportsDirectStream" to true,
                    "SupportsTranscoding" to false,
                    "DirectStreamUrl" to "/Videos/$id/stream"
                )
            )
            call.respondText(gson.toJson(detail), ContentType.Application.Json)
        } else {
            call.respond(HttpStatusCode.NotFound)
        }
    }

    private suspend fun respondPlaybackInfo(call: io.ktor.server.application.ApplicationCall) {
        val itemId = call.parameters["id"]
        val playbackInfo = mapOf(
            "MediaSources" to listOf(
                mapOf(
                    "Id" to itemId,
                    "Path" to "/Videos/$itemId/stream",
                    "Protocol" to "Http",
                    "Container" to "mp4",
                    "IsRemote" to false,
                    "SupportsDirectPlay" to true,
                    "SupportsDirectStream" to true,
                    "SupportsTranscoding" to false,
                    "DirectStreamUrl" to "/Videos/$itemId/stream"
                )
            ),
            "PlaySessionId" to UUID.randomUUID().toString()
        )
        call.respondText(gson.toJson(playbackInfo), ContentType.Application.Json)
    }


    private suspend fun respondVideoStream(call: io.ktor.server.application.ApplicationCall) {
        val rawParam = call.parameters["id"] ?: call.parameters.getAll("name")?.joinToString("/")
        val id = if (!rawParam.isNullOrBlank()) {
            try { URLDecoder.decode(rawParam, StandardCharsets.UTF_8.name()) } catch (_: Exception) { rawParam }
        } else null
        val ctx = StorageManager.getContext()

        // 1. StorageManager direct lookup by ID, filename, or stripped prefix
        if (!id.isNullOrBlank()) {
            val cleanId = id.trim()
            val directLocal = StorageManager.getSandboxFileById(cleanId)
                ?: StorageManager.getSandboxFileById(cleanId.replace('+', ' '))
                ?: StorageManager.resolveRelativePath(cleanId)
                ?: StorageManager.resolveRelativePath(cleanId.replace('+', ' '))
                ?: FederatedMediaManager.findLocalFileByTitle(cleanId)
                ?: findFileById(cleanId)
                ?: findFileById(cleanId.replace('+', ' '))

            if (directLocal != null && directLocal.exists() && directLocal.isFile) {
                Logger.i(LogTag.JELLYFIN, "Streaming sandbox video (directLocal): ${directLocal.name}")
                respondDirectFileWithRange(call, directLocal)
                return
            }
        }

        // 2. Federated Media Manager Lookup
        if (id != null && ctx != null) {
            val fedItem = FederatedMediaManager.getById(id)
            if (fedItem != null) {
                if (fedItem.origin == com.remotemedia.core.MediaOrigin.REMOTE_JELLYFIN) {
                    val rItem = JellyfinClusterManager.remoteItems.value.find { it.id == fedItem.sourceId }
                    if (rItem != null) {
                        val rangeHeader = call.request.header(HttpHeaders.Range)
                        val remoteResp = JellyfinClusterManager.openRemoteStreamResponse(rItem, rangeHeader)
                        if (remoteResp != null) {
                            Logger.i(LogTag.JELLYFIN, "[FEDERATED] Streaming REMOTE_JELLYFIN: ${fedItem.name} (${remoteResp.statusCode}) Range: $rangeHeader")
                            val safeAscii = fedItem.name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                            call.response.header(HttpHeaders.ContentDisposition, "inline; filename=\"$safeAscii\"")
                            call.response.header(HttpHeaders.AcceptRanges, "bytes")
                            if (remoteResp.contentRange != null) {
                                call.response.header(HttpHeaders.ContentRange, remoteResp.contentRange)
                            }
                            if (remoteResp.contentLength > 0) {
                                call.response.header(HttpHeaders.ContentLength, remoteResp.contentLength.toString())
                            }
                            val status = runCatching { HttpStatusCode.fromValue(remoteResp.statusCode) }.getOrDefault(HttpStatusCode.OK)
                            val cType = remoteResp.contentType?.let { runCatching { ContentType.parse(it) }.getOrNull() }
                                ?: runCatching { ContentType.parse(fedItem.mimeType) }.getOrDefault(ContentType.Video.MP4)
                            call.respondOutputStream(cType, status) {
                                remoteResp.inputStream.use { it.copyTo(this) }
                            }
                            return
                        }
                    }
                }

                val directFile = FederatedMediaManager.getDirectFile(fedItem)
                if (directFile != null && directFile.exists() && directFile.isFile) {
                    Logger.i(LogTag.JELLYFIN, "[FEDERATED] Streaming ${fedItem.origin.badge} media (direct): ${fedItem.name}")
                    respondDirectFileWithRange(call, directFile)
                    return
                }
                val stream = FederatedMediaManager.openInputStream(ctx, fedItem, call.request.header(HttpHeaders.Range))
                if (stream != null) {
                    Logger.i(LogTag.JELLYFIN, "[FEDERATED] Streaming ${fedItem.origin.badge} media (stream): ${fedItem.name}")
                    val safeAscii = fedItem.name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                    call.response.header(HttpHeaders.ContentDisposition, "inline; filename=\"$safeAscii\"")
                    val cType = runCatching { io.ktor.http.ContentType.parse(fedItem.mimeType) }
                        .getOrDefault(ContentType.Video.MP4)
                    call.respondOutputStream(cType, HttpStatusCode.OK) {
                        stream.use { it.copyTo(this) }
                    }
                    return
                }
            }
        }

        // 3. Fallback search by ID or path in library folders
        val file = findFileById(id)
        if (file != null && file.exists() && file.isFile) {
            Logger.i(LogTag.JELLYFIN, "Streaming sandbox video to Jellyfin client: ${file.name}")
            respondDirectFileWithRange(call, file)
            return
        }

        // 4. Host Virtual Media
        if (ctx != null) {
            val virtualFile = HostMediaManager.getVirtualFileById(id ?: "")
            if (virtualFile != null) {
                val directFile = HostMediaManager.getDirectFile(virtualFile)
                if (directFile != null && directFile.exists() && directFile.isFile) {
                    Logger.i(LogTag.JELLYFIN, "Streaming host media (direct): ${virtualFile.name}")
                    respondDirectFileWithRange(call, directFile)
                    return
                }
                val stream = HostMediaManager.openInputStream(ctx, virtualFile)
                if (stream != null) {
                    Logger.i(LogTag.JELLYFIN, "Streaming host media (stream): ${virtualFile.name}")
                    val safeAscii = virtualFile.name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                    call.response.header(HttpHeaders.ContentDisposition, "inline; filename=\"$safeAscii\"")
                    call.respondOutputStream(ContentType.Video.MP4, HttpStatusCode.OK) {
                        stream.use { it.copyTo(this) }
                    }
                    return
                }
            }
        }

        // 5. Network SMB Virtual File
        val netFile = NetworkShareManager.getVirtualFileById(id ?: "")
        if (netFile != null) {
            val stream = NetworkShareManager.openInputStream(netFile)
            if (stream != null) {
                Logger.i(LogTag.JELLYFIN, "Streaming LAN network media (SMB): ${netFile.name}")
                val safeAscii = netFile.name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                call.response.header(HttpHeaders.ContentDisposition, "inline; filename=\"$safeAscii\"")
                call.respondOutputStream(ContentType.Video.MP4, HttpStatusCode.OK) {
                    stream.use { it.copyTo(this) }
                }
                return
            }
        }

        call.respondText("Media file not found: $id", status = HttpStatusCode.NotFound)
    }

    private suspend fun respondDirectFileWithRange(call: io.ktor.server.application.ApplicationCall, file: File) {
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
        call.response.header(HttpHeaders.AccessControlAllowOrigin, "*")
        call.response.header(HttpHeaders.AccessControlAllowHeaders, "*")
        call.response.header(HttpHeaders.AccessControlAllowMethods, "GET, HEAD, OPTIONS")

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

    private fun findFileById(id: String?): File? {
        if (id == null) return null
        // 1. StorageManager direct lookup
        StorageManager.getSandboxFileById(id)?.let { return it }

        // 2. Search across all configured libraries and base storage directories
        val libraries = JellyfinManager.getLibraries()
        val dirsToSearch = (libraries.map { File(it.folderPath) } + listOfNotNull(StorageManager.getContext()?.let { runCatching { StorageManager.getBaseDir() }.getOrNull() })).distinct()
        for (dir in dirsToSearch) {
            if (!dir.exists()) continue
            val files = StorageManager.listFiles(dir)
            val match = files.firstOrNull { file ->
                val uuidStr = UUID.nameUUIDFromBytes(file.name.toByteArray()).toString()
                uuidStr == id ||
                uuidStr.replace("-", "") == id.replace("-", "") ||
                UUID.nameUUIDFromBytes(file.canonicalPath.toByteArray()).toString() == id ||
                UUID.nameUUIDFromBytes(file.canonicalPath.toByteArray()).toString().replace("-", "") == id.replace("-", "") ||
                UUID.nameUUIDFromBytes(file.absolutePath.toByteArray()).toString() == id ||
                UUID.nameUUIDFromBytes(file.absolutePath.toByteArray()).toString().replace("-", "") == id.replace("-", "") ||
                file.name == id ||
                file.nameWithoutExtension == id
            }
            if (match != null) return match
        }
        return null
    }

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format("%.2f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }

    private fun formatSecondsToTime(sec: Long): String {
        val h = sec / 3600
        val m = (sec % 3600) / 60
        val s = sec % 60
        return if (h > 0) {
            String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, s)
        } else {
            String.format(java.util.Locale.US, "%02d:%02d", m, s)
        }
    }

    private fun findApkFile(isClient: Boolean): File? {
        val ctx = StorageManager.getContext()
        val baseDir = runCatching { StorageManager.getBaseDir() }.getOrNull()

        if (!isClient) {
            // 1. Storage directory
            if (baseDir != null && baseDir.exists()) {
                val candidates = listOf("apks/pocketnode-server.apk", "pocketnode-server.apk", "apks/server.apk", "server.apk")
                for (rel in candidates) {
                    val f = File(baseDir, rel)
                    if (f.exists() && f.isFile && f.length() > 0) return f
                }
            }
            // 2. Currently running server APK on device
            if (ctx != null) {
                runCatching {
                    val sourceApk = File(ctx.applicationInfo.sourceDir)
                    if (sourceApk.exists() && sourceApk.isFile && sourceApk.length() > 0) return sourceApk
                }
            }
            // 3. Dev build path
            val devApk = File("c:/Users/cotton candy/Documents/home server/remote-movie-pc-downloader/app/build/outputs/apk/debug/app-debug.apk")
            if (devApk.exists() && devApk.isFile) return devApk
        } else {
            // Client APK
            // 1. Storage directory
            if (baseDir != null && baseDir.exists()) {
                val candidates = listOf(
                    "apks/pocketnode-client.apk",
                    "pocketnode-client.apk",
                    "apks/client.apk",
                    "client.apk",
                    "pocketnode.apk",
                    "app.apk"
                )
                for (rel in candidates) {
                    val f = File(baseDir, rel)
                    if (f.exists() && f.isFile && f.length() > 0) return f
                }
            }
            // 2. Dev build path
            val devClientApk = File("c:/Users/cotton candy/Documents/home server/pocketnode-client/app/build/outputs/apk/debug/app-debug.apk")
            if (devClientApk.exists() && devClientApk.isFile) return devClientApk
        }
        return null
    }

    private suspend fun respondApkDownload(call: io.ktor.server.application.ApplicationCall, isClient: Boolean) {
        val apkFile = findApkFile(isClient)
        if (apkFile != null && apkFile.exists()) {
            val fileName = if (isClient) "pocketnode-client.apk" else "pocketnode-server.apk"
            call.response.header(
                HttpHeaders.ContentDisposition,
                "attachment; filename=\"$fileName\""
            )
            call.response.header(HttpHeaders.AccessControlAllowOrigin, "*")
            call.respondFile(apkFile)
        } else {
            val appType = if (isClient) "pocketnode-client.apk" else "pocketnode-server.apk"
            call.respondText("APK file not found: $appType. Place it in your PocketNode storage or apks/ folder.", status = HttpStatusCode.NotFound)
        }
    }

    private suspend fun respondApkInfo(call: io.ktor.server.application.ApplicationCall) {
        val clientApk = findApkFile(isClient = true)
        val serverApk = findApkFile(isClient = false)
        val info = mapOf(
            "client" to mapOf(
                "available" to (clientApk != null && clientApk.exists()),
                "name" to "PocketNode Client (Android TV / Phone)",
                "fileName" to "pocketnode-client.apk",
                "downloadUrl" to "/apk/client",
                "sizeBytes" to (clientApk?.length() ?: 0L),
                "sizeFormatted" to (clientApk?.let { formatSize(it.length()) } ?: "21.4 MB"),
                "lastModified" to (clientApk?.lastModified() ?: 0L)
            ),
            "server" to mapOf(
                "available" to (serverApk != null && serverApk.exists()),
                "name" to "PocketNode Server (Home Server)",
                "fileName" to "pocketnode-server.apk",
                "downloadUrl" to "/apk/server",
                "sizeBytes" to (serverApk?.length() ?: 0L),
                "sizeFormatted" to (serverApk?.let { formatSize(it.length()) } ?: "62.9 MB"),
                "lastModified" to (serverApk?.lastModified() ?: 0L)
            )
        )
        call.response.header(HttpHeaders.AccessControlAllowOrigin, "*")
        call.respondText(gson.toJson(info), ContentType.Application.Json)
    }
}
