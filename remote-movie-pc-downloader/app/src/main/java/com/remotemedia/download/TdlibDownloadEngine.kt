package com.remotemedia.download

import android.content.Context
import com.remotemedia.core.LogTag
import com.remotemedia.core.Logger
import com.remotemedia.core.StorageManager
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Native TDLib (Telegram Database Library) Engine.
 * Runs 100% locally on Android, connecting directly to Telegram MTProto data centers
 * to download media up to 2GB with zero PC and zero cloud limits.
 */
object TdlibDownloadEngine {

    private var client: Client? = null
    private val isInitialized = AtomicBoolean(false)
    private val isAuthorized = AtomicBoolean(false)

    private var currentApiId: Int = 0
    private var currentApiHash: String = ""
    private var currentBotToken: String = ""
    private var tdlibDir: File? = null

    // Active file downloads tracking: TdLib file ID -> metadata
    data class ActiveDownload(
        val fileId: Int,
        val fileName: String,
        val totalSize: Long,
        val chatId: Long,
        val statusMsgId: Long,
        val tgTrackingId: String,
        val startTime: Long = System.currentTimeMillis()
    )

    private val activeDownloads = ConcurrentHashMap<Int, ActiveDownload>()

    fun isReady(): Boolean = isAuthorized.get()

    fun init(context: Context, apiId: Int, apiHash: String, botToken: String) {
        if (apiId == 0 || apiHash.isBlank() || botToken.isBlank()) {
            Logger.w(LogTag.TELEGRAM, "TDLib cannot start: Missing apiId, apiHash, or botToken")
            return
        }

        if (isInitialized.get() && currentApiId == apiId && currentApiHash == apiHash && currentBotToken == botToken) {
            return
        }

        stop()

        currentApiId = apiId
        currentApiHash = apiHash
        currentBotToken = botToken
        tdlibDir = File(context.filesDir, "tdlib").apply { mkdirs() }

        try {
            val downloadDir = StorageManager.getMoviesDir()

            val updateHandler = Client.ResultHandler { obj ->
                handleTdUpdate(obj, downloadDir)
            }

            client = Client.create(updateHandler, null, null)
            isInitialized.set(true)
            Logger.i(LogTag.TELEGRAM, "Native TDLib client created successfully.")
        } catch (e: Throwable) {
            Logger.e(LogTag.TELEGRAM, "Failed to create TDLib client: ${e.message}")
        }
    }

    private fun handleTdUpdate(obj: TdApi.Object, downloadDir: File) {
        when (obj) {
            is TdApi.UpdateAuthorizationState -> {
                handleAuthState(obj.authorizationState)
            }
            is TdApi.UpdateFile -> {
                handleFileUpdate(obj.file, downloadDir)
            }
        }
    }

    private fun handleAuthState(state: TdApi.AuthorizationState) {
        when (state) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> {
                val params = TdApi.SetTdlibParameters()
                params.databaseDirectory = tdlibDir?.absolutePath ?: ""
                params.useMessageDatabase = false
                params.useSecretChats = false
                params.useFileDatabase = true
                params.useChatInfoDatabase = false
                params.apiId = currentApiId
                params.apiHash = currentApiHash
                params.systemLanguageCode = "en"
                params.deviceModel = "Android"
                params.applicationVersion = "1.0"
                params.databaseEncryptionKey = byteArrayOf()

                client?.send(params) { res ->
                    if (res is TdApi.Error) {
                        Logger.e(LogTag.TELEGRAM, "TDLib SetTdlibParameters error: ${res.message}")
                    }
                }
            }
            is TdApi.AuthorizationStateWaitPhoneNumber -> {
                // Authenticate as Bot using CheckAuthenticationBotToken
                client?.send(TdApi.CheckAuthenticationBotToken(currentBotToken)) { res ->
                    if (res is TdApi.Error) {
                        Logger.e(LogTag.TELEGRAM, "TDLib CheckAuthenticationBotToken error: ${res.message}")
                    }
                }
            }
            is TdApi.AuthorizationStateReady -> {
                isAuthorized.set(true)
                Logger.i(LogTag.TELEGRAM, "🚀 Native TDLib authenticated as bot! 2GB MTProto engine ready.")
            }
            is TdApi.AuthorizationStateLoggingOut, is TdApi.AuthorizationStateClosed -> {
                isAuthorized.set(false)
                isInitialized.set(false)
            }
        }
    }

    private fun handleFileUpdate(file: TdApi.File, downloadDir: File) {
        val tracking = activeDownloads[file.id] ?: return
        val rawDownloaded = file.local.downloadedSize.toLong().let { if (it < 0) it and 0xFFFFFFFFL else it }
        val rawTotal = if (tracking.totalSize > 0) tracking.totalSize else file.size.toLong().let { if (it < 0) it and 0xFFFFFFFFL else it }
        val downloaded = rawDownloaded
        val total = rawTotal

        val pct = if (total > 0) ((downloaded * 100) / total).toInt().coerceIn(0, 100) else 0

        TorrentEngine.updateTelegramDownload(
            id = tracking.tgTrackingId,
            downloadedBytes = downloaded,
            totalBytes = total,
            speedStr = if (file.local.isDownloadingActive) "Downloading" else "Pending",
            etaStr = "$pct%",
            pct = pct
        )

        if (file.local.isDownloadingCompleted) {
            activeDownloads.remove(file.id)
            Logger.i(LogTag.TELEGRAM, "✅ TDLib file download finished: ${file.local.path}")

            val downloadedFile = File(file.local.path)
            val destFile = File(downloadDir, tracking.fileName)
            try {
                if (downloadedFile.exists() && downloadedFile.absolutePath != destFile.absolutePath) {
                    downloadedFile.copyTo(destFile, overwrite = true)
                    downloadedFile.delete()
                }
            } catch (e: Exception) {
                Logger.e(LogTag.TELEGRAM, "Failed to copy downloaded file to Movies dir: ${e.message}")
            }

            TorrentEngine.updateTelegramDownload(
                id = tracking.tgTrackingId,
                downloadedBytes = total,
                totalBytes = total,
                speedStr = "Completed",
                etaStr = "0s",
                pct = 100
            )

            TelegramBotEngine.notifyDownloadFinished(tracking.chatId, tracking.statusMsgId, tracking.fileName, total)
        }
    }

    fun startDownload(
        remoteFileId: String,
        fileName: String,
        fileSize: Long,
        chatId: Long,
        statusMsgId: Long,
        tgTrackingId: String
    ): Boolean {
        if (!isAuthorized.get()) return false

        client?.send(TdApi.GetRemoteFile(remoteFileId, null)) { obj ->
            if (obj is TdApi.File) {
                activeDownloads[obj.id] = ActiveDownload(
                    fileId = obj.id,
                    fileName = fileName,
                    totalSize = if (obj.size > 0) obj.size.toLong() else fileSize,
                    chatId = chatId,
                    statusMsgId = statusMsgId,
                    tgTrackingId = tgTrackingId
                )

                client?.send(TdApi.DownloadFile(obj.id, 32, 0, 0, false)) { res ->
                    if (res is TdApi.Error) {
                        Logger.e(LogTag.TELEGRAM, "TDLib DownloadFile error: ${res.message}")
                        activeDownloads.remove(obj.id)
                    }
                }
            } else if (obj is TdApi.Error) {
                Logger.e(LogTag.TELEGRAM, "TDLib GetRemoteFile error: ${obj.message}")
            }
        }
        return true
    }

    fun stop() {
        isAuthorized.set(false)
        isInitialized.set(false)
        activeDownloads.clear()
        try {
            client?.send(TdApi.Close(), null)
            client = null
        } catch (_: Throwable) {}
    }
}
