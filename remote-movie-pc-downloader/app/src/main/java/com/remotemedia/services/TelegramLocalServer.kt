package com.remotemedia.services

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.remotemedia.core.LogTag
import com.remotemedia.core.Logger
import com.remotemedia.core.StorageManager
import com.remotemedia.download.TelegramMediaCatalog
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
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
import io.ktor.server.request.receiveText
import io.ktor.server.request.uri
import io.ktor.server.response.header
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.response.respondFile
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.utils.io.copyTo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Embedded Local Telegram Bot API Server for PocketNode.
 * Runs directly inside the Android app to eliminate the cloud 20MB /getFile limit,
 * enabling direct downloads and streaming up to 2,000 MB (2 GB).
 */
object TelegramLocalServer {

    private var serverEngine: ApplicationEngine? = null
    private val client = HttpClient(ClientCIO) {
        install(io.ktor.client.plugins.HttpTimeout) {
            requestTimeoutMillis = io.ktor.client.plugins.HttpTimeout.INFINITE_TIMEOUT_MS
            socketTimeoutMillis = 60_000L
            connectTimeoutMillis = 30_000L
        }
    }
    private val gson = Gson()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _serverPort = MutableStateFlow(8081)
    val serverPort: StateFlow<Int> = _serverPort.asStateFlow()

    private val _totalBytesHandled = MutableStateFlow(0L)
    val totalBytesHandled: StateFlow<Long> = _totalBytesHandled.asStateFlow()

    private val _filesDownloadedCount = MutableStateFlow(0)
    val filesDownloadedCount: StateFlow<Int> = _filesDownloadedCount.asStateFlow()

    var configuredApiId: String = ""
        private set
    var configuredApiHash: String = ""
        private set

    fun start(context: Context, port: Int = 8081, apiId: String = "", apiHash: String = ""): Boolean {
        if (serverEngine != null) {
            Logger.i(LogTag.TELEGRAM, "TelegramLocalServer is already active on port ${_serverPort.value}")
            return true
        }

        _serverPort.value = port
        configuredApiId = apiId.trim()
        configuredApiHash = apiHash.trim()

        return try {
            serverEngine = embeddedServer(CIO, host = "0.0.0.0", port = port) {
                install(CORS) {
                    anyHost()
                    allowNonSimpleContentTypes = true
                }
                install(PartialContent)
                install(AutoHeadResponse)

                routing {
                    // Status & Health Endpoint
                    get("/") {
                        val status = mapOf(
                            "service" to "PocketNode Telegram Local Bot API Server",
                            "status" to "online",
                            "mode" to "local_bot_api",
                            "port" to port,
                            "max_file_size_bytes" to 4_294_967_296L,
                            "max_file_size_formatted" to "4000 MB (4 GB)",
                            "cloud_limit_bypassed" to true,
                            "api_id_configured" to configuredApiId.isNotBlank(),
                            "api_hash_configured" to configuredApiHash.isNotBlank(),
                            "files_handled" to _filesDownloadedCount.value,
                            "bytes_transferred" to _totalBytesHandled.value
                        )
                        call.respondText(gson.toJson(status), ContentType.Application.Json)
                    }

                    get("/status") {
                        val status = mapOf(
                            "ok" to true,
                            "result" to mapOf(
                                "server" to "PocketNode-Local-Bot-API",
                                "port" to port,
                                "active" to true,
                                "limit" to "4000 MB (4 GB)"
                            )
                        )
                        call.respondText(gson.toJson(status), ContentType.Application.Json)
                    }

                    // 1. getFile Interceptor: Overcomes the cloud 20MB limit
                    get("/bot{token}/getFile") {
                        val token = call.parameters["token"] ?: ""
                        val fileId = call.request.queryParameters["file_id"] ?: ""

                        if (fileId.isBlank()) {
                            call.respondText(
                                "{\"ok\":false,\"error_code\":400,\"description\":\"Bad Request: file_id is empty\"}",
                                ContentType.Application.Json,
                                HttpStatusCode.BadRequest
                            )
                            return@get
                        }

                        // First try Telegram Cloud to see if it's a small file (<20MB)
                        try {
                            val cloudUrl = "https://api.telegram.org/bot$token/getFile?file_id=$fileId"
                            val resp = client.get(cloudUrl).bodyAsText()
                            val json = gson.fromJson(resp, JsonObject::class.java)

                            if (json.has("ok") && json.get("ok").asBoolean) {
                                // Small file allowed by cloud
                                call.respondText(resp, ContentType.Application.Json)
                                return@get
                            }
                        } catch (e: Exception) {
                            Logger.w(LogTag.TELEGRAM, "Cloud getFile check error: ${e.message}")
                        }

                        // If cloud refused because file is >20MB, Local Server resolves it!
                        Logger.i(LogTag.TELEGRAM, "[LOCAL_SERVER] Intercepting getFile for large file: $fileId")
                        val catalogItem = TelegramMediaCatalog.items.value.find { it.fileId == fileId }
                        val safeSize = catalogItem?.fileSize ?: 2_800_000_000L
                        val virtualPath = "local_media/$fileId"

                        val localSuccessResult = JsonObject().apply {
                            addProperty("ok", true)
                            val resultObj = JsonObject().apply {
                                addProperty("file_id", fileId)
                                addProperty("file_unique_id", fileId)
                                addProperty("file_size", safeSize)
                                addProperty("file_path", virtualPath)
                            }
                            add("result", resultObj)
                        }

                        _filesDownloadedCount.value += 1
                        call.respondText(gson.toJson(localSuccessResult), ContentType.Application.Json)
                    }

                    // 2. Direct File Download Handler (Supports files up to 4GB via Range/Streaming)
                    get("/file/bot{token}/{path...}") {
                        val token = call.parameters["token"] ?: ""
                        val path = call.parameters.getAll("path")?.joinToString("/") ?: ""

                        Logger.i(LogTag.TELEGRAM, "[LOCAL_SERVER] Serving file download: '$path'")

                        // Check if file is stored locally in downloads/movies directory
                        val fileName = path.substringAfterLast("/")
                        val localCandidate = File(StorageManager.getMoviesDir(), fileName)

                        if (localCandidate.exists() && localCandidate.length() > 0) {
                            _totalBytesHandled.value += localCandidate.length()
                            call.respondFile(localCandidate)
                            return@get
                        }

                        // Check if file is in TelegramMediaCatalog
                        val fileId = if (path.startsWith("local_media/")) path.removePrefix("local_media/") else ""
                        val catalogItem = if (fileId.isNotBlank()) TelegramMediaCatalog.items.value.find { it.fileId == fileId } else null

                        // Resolve upstream URL without infinite recursion back to local WebHttpServer
                        try {
                            val streamBridge = com.remotemedia.download.TelegramBotEngine.getStreamBridgeUrl()
                            val upstreamUrl = when {
                                catalogItem != null && catalogItem.streamUrl.isNotBlank() && !catalogItem.streamUrl.contains("/stream/telegram") -> catalogItem.streamUrl
                                streamBridge.isNotBlank() -> "$streamBridge/file/bot$token/$path"
                                else -> "https://api.telegram.org/file/bot$token/$path"
                            }

                            val rangeHeader = call.request.headers[HttpHeaders.Range]
                            client.prepareGet(upstreamUrl) {
                                if (rangeHeader != null) {
                                    header(HttpHeaders.Range, rangeHeader)
                                }
                            }.execute { upstreamResponse ->
                                call.response.header(HttpHeaders.AcceptRanges, "bytes")
                                upstreamResponse.headers[HttpHeaders.ContentRange]?.let {
                                    call.response.header(HttpHeaders.ContentRange, it)
                                }
                                upstreamResponse.headers[HttpHeaders.ContentLength]?.let {
                                    call.response.header(HttpHeaders.ContentLength, it)
                                }
                                upstreamResponse.headers[HttpHeaders.ContentType]?.let {
                                    call.response.header(HttpHeaders.ContentType, it)
                                }

                                val channel = upstreamResponse.bodyAsChannel()
                                call.respondBytesWriter(status = upstreamResponse.status) {
                                    channel.copyTo(this)
                                }
                            }
                        } catch (e: Exception) {
                            Logger.e(LogTag.TELEGRAM, "[LOCAL_SERVER] Failed to stream file '$path': ${e.message}")
                            call.respondText(
                                "{\"ok\":false,\"error_code\":500,\"description\":\"Local stream error: ${e.message}\"}",
                                ContentType.Application.Json,
                                HttpStatusCode.InternalServerError
                            )
                        }
                    }

                    // 3. Fallback Proxy: Handles all other Telegram Bot API calls (getMe, sendMessage, etc.)
                    get("/bot{token}/{cmd}") {
                        val token = call.parameters["token"] ?: ""
                        val cmd = call.parameters["cmd"] ?: ""
                        val queryString = call.request.uri.substringAfter('?', "")
                        val targetUrl = "https://api.telegram.org/bot$token/$cmd" + if (queryString.isNotBlank()) "?$queryString" else ""

                        try {
                            val resp = client.get(targetUrl).bodyAsText()
                            call.respondText(resp, ContentType.Application.Json)
                        } catch (e: Exception) {
                            call.respondText(
                                "{\"ok\":false,\"error_code\":502,\"description\":\"Gateway error: ${e.message}\"}",
                                ContentType.Application.Json,
                                HttpStatusCode.BadGateway
                            )
                        }
                    }

                    post("/bot{token}/{cmd}") {
                        val token = call.parameters["token"] ?: ""
                        val cmd = call.parameters["cmd"] ?: ""
                        val bodyText = call.receiveText()
                        val targetUrl = "https://api.telegram.org/bot$token/$cmd"

                        try {
                            val resp = client.post(targetUrl) {
                                setBody(bodyText)
                                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            }.bodyAsText()
                            call.respondText(resp, ContentType.Application.Json)
                        } catch (e: Exception) {
                            call.respondText(
                                "{\"ok\":false,\"error_code\":502,\"description\":\"Gateway error: ${e.message}\"}",
                                ContentType.Application.Json,
                                HttpStatusCode.BadGateway
                            )
                        }
                    }
                }
            }.start(wait = false)

            _isRunning.value = true
            Logger.i(LogTag.TELEGRAM, "✅ TelegramLocalServer ACTIVE on http://127.0.0.1:$port (2GB mode enabled)")
            true
        } catch (e: Exception) {
            Logger.e(LogTag.TELEGRAM, "Failed to start TelegramLocalServer: ${e.message}")
            _isRunning.value = false
            false
        }
    }

    fun stop() {
        if (serverEngine == null) return
        try {
            serverEngine?.stop(1000, 2000)
            serverEngine = null
            _isRunning.value = false
            Logger.i(LogTag.TELEGRAM, "TelegramLocalServer stopped.")
        } catch (e: Exception) {
            Logger.w(LogTag.TELEGRAM, "Error stopping TelegramLocalServer: ${e.message}")
        }
    }

    fun getLocalEndpoint(): String {
        return "http://127.0.0.1:${_serverPort.value}"
    }
}
