package com.remotemedia.download

import android.content.Context
import com.remotemedia.core.LogTag
import com.remotemedia.core.Logger
import com.remotemedia.core.StorageManager
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.YoutubeDLResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

data class YtDlpProgress(
    val progressPct: Int,
    val speedStr: String,
    val etaStr: String,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val rawStatusLine: String
)

object YtDlpManager {

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val _isInitializing = MutableStateFlow(false)
    val isInitializing: StateFlow<Boolean> = _isInitializing.asStateFlow()

    private val _initError = MutableStateFlow<String?>(null)
    val initError: StateFlow<String?> = _initError.asStateFlow()

    private val activeProcesses = ConcurrentHashMap<String, Boolean>()

    private val PLATFORM_DOMAINS = listOf(
        "youtube.com", "youtu.be",
        "instagram.com",
        "tiktok.com",
        "twitter.com", "x.com",
        "facebook.com", "fb.watch",
        "reddit.com", "v.redd.it",
        "vimeo.com",
        "dailymotion.com",
        "twitch.tv",
        "streamable.com",
        "pinterest.com",
        "bilibili.com"
    )

    fun init(context: Context) {
        if (_isInitialized.value || _isInitializing.value) return
        _isInitializing.value = true

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val appCtx = context.applicationContext
                Logger.i(LogTag.SYSTEM, "Initializing native yt-dlp & FFmpeg engine...")
                
                // Initialize YoutubeDL and embedded Python runtime
                YoutubeDL.getInstance().init(appCtx)
                
                // Initialize FFmpeg for video/audio remuxing and format conversion
                try {
                    FFmpeg.getInstance().init(appCtx)
                    Logger.i(LogTag.SYSTEM, "Native FFmpeg initialized successfully.")
                } catch (fe: Exception) {
                    Logger.w(LogTag.SYSTEM, "FFmpeg init warning: ${fe.message}")
                }

                _isInitialized.value = true
                _initError.value = null
                Logger.i(LogTag.SYSTEM, "Native yt-dlp engine ready.")

                // Check for yt-dlp self-update in background
                runCatching {
                    val status = YoutubeDL.getInstance().updateYoutubeDL(appCtx)
                    Logger.i(LogTag.SYSTEM, "yt-dlp update status: $status")
                }
            } catch (e: Exception) {
                _initError.value = e.message
                Logger.e(LogTag.SYSTEM, "Failed to initialize yt-dlp engine: ${e.message}")
            } finally {
                _isInitializing.value = false
            }
        }
    }

    fun isSupportedPlatform(url: String): Boolean {
        val lower = url.lowercase()
        return PLATFORM_DOMAINS.any { lower.contains(it) }
    }

    fun cancel(processId: String): Boolean {
        return try {
            activeProcesses.remove(processId)
            YoutubeDL.getInstance().destroyProcessById(processId)
        } catch (e: Exception) {
            false
        }
    }

    suspend fun download(
        url: String,
        outputDir: File = StorageManager.getMoviesDir(),
        processId: String = "ytdlp_${System.currentTimeMillis()}",
        onProgress: (YtDlpProgress) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        if (!_isInitialized.value) {
            throw IllegalStateException("yt-dlp engine is not initialized yet. Please wait a few seconds and retry.")
        }

        activeProcesses[processId] = true

        // Capture files before download to detect newly created file
        val filesBefore = outputDir.listFiles()?.map { it.name }?.toSet() ?: emptySet()

        val outputTemplate = File(outputDir, "%(title)s.%(ext)s").absolutePath
        val request = YoutubeDLRequest(url)
        request.addOption("-o", outputTemplate)
        // Best video up to 1080p + best audio, muxed to mp4 (mobile friendly)
        request.addOption("-f", "bestvideo[height<=1080][ext=mp4]+bestaudio[ext=m4a]/bestvideo[height<=1080]+bestaudio/best[height<=1080]/best")
        request.addOption("--merge-output-format", "mp4")
        request.addOption("--no-mtime")
        request.addOption("--no-warnings")
        request.addOption("--ignore-errors")
        request.addOption("--no-playlist")
        request.addOption("--retries", "3")
        request.addOption("--fragment-retries", "5")

        var lastReportedPct = -1
        var totalBytesEstimated = 0L

        try {
            val response: YoutubeDLResponse = YoutubeDL.getInstance().execute(request, processId) { progress, etaInSeconds, line ->
                val pct = progress.toInt().coerceIn(0, 100)

                // Parse speed and size from stdout line if available, e.g. "[download]  45.0% of ~50.00MiB at 4.50MiB/s ETA 00:06"
                var speedStr = "Streaming..."
                var etaStr = if (etaInSeconds > 0) "${etaInSeconds}s" else "--"

                val speedMatch = Regex("""at\s+([\d.]+\s*[KMGT]?i?B/s)""", RegexOption.IGNORE_CASE).find(line)
                if (speedMatch != null) {
                    speedStr = speedMatch.groupValues[1]
                }

                val sizeMatch = Regex("""of\s+(?:~)?([\d.]+\s*[KMGT]?i?B)""", RegexOption.IGNORE_CASE).find(line)
                if (sizeMatch != null) {
                    val rawSizeStr = sizeMatch.groupValues[1]
                    val parsed = parseSizeStringToBytes(rawSizeStr)
                    if (parsed > 0) totalBytesEstimated = parsed
                }

                val downloadedBytes = if (totalBytesEstimated > 0) {
                    (totalBytesEstimated * (pct / 100.0)).toLong()
                } else 0L

                if (pct != lastReportedPct || pct == 100) {
                    lastReportedPct = pct
                    onProgress(
                        YtDlpProgress(
                            progressPct = pct,
                            speedStr = speedStr,
                            etaStr = etaStr,
                            downloadedBytes = downloadedBytes,
                            totalBytes = totalBytesEstimated,
                            rawStatusLine = line
                        )
                    )
                }
            }

            Logger.i(LogTag.TELEGRAM, "yt-dlp finished: exitCode=${response.exitCode}, time=${response.elapsedTime}ms")

            // Identify downloaded file
            val filesAfter = outputDir.listFiles() ?: emptyArray()
            val newFiles = filesAfter.filter { !filesBefore.contains(it.name) && it.length() > 0 }
            val downloadedFile = newFiles.maxByOrNull { it.lastModified() }
                ?: filesAfter.filter { it.lastModified() >= (System.currentTimeMillis() - response.elapsedTime - 10000) }
                    .maxByOrNull { it.lastModified() }

            downloadedFile
        } catch (e: YoutubeDLException) {
            Logger.e(LogTag.TELEGRAM, "yt-dlp execution error: ${e.message}")
            throw e
        } finally {
            activeProcesses.remove(processId)
        }
    }

    private fun parseSizeStringToBytes(str: String): Long {
        return try {
            val trimmed = str.trim().uppercase()
            val num = Regex("""[\d.]+""").find(trimmed)?.value?.toDoubleOrNull() ?: return 0L
            when {
                trimmed.endsWith("GIB") || trimmed.endsWith("GB") -> (num * 1024 * 1024 * 1024).toLong()
                trimmed.endsWith("MIB") || trimmed.endsWith("MB") -> (num * 1024 * 1024).toLong()
                trimmed.endsWith("KIB") || trimmed.endsWith("KB") -> (num * 1024).toLong()
                else -> num.toLong()
            }
        } catch (e: Exception) {
            0L
        }
    }
}
