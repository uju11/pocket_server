package com.remotemedia.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.media.MediaMetadataRetriever
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Handles real-time video thumbnail extraction and still card generation for Jellyfin clients & web UI.
 * Uses Android's MediaMetadataRetriever to extract authentic video frames with high-performance disk caching.
 */
object MediaThumbnailManager {

    private val memoryCache = ConcurrentHashMap<String, ByteArray>()

    suspend fun getThumbnail(
        context: Context?,
        mediaId: String,
        targetFile: File? = null
    ): ByteArray = withContext(Dispatchers.IO) {
        val cacheKey = targetFile?.canonicalPath ?: mediaId
        memoryCache[cacheKey]?.let { return@withContext it }

        // Check persistent disk cache
        val diskCacheDir = context?.cacheDir?.let { File(it, "jellyfin_stills") }
        if (diskCacheDir != null && !diskCacheDir.exists()) diskCacheDir.mkdirs()
        val safeKey = Math.abs(cacheKey.hashCode()).toString()
        val diskFile = diskCacheDir?.let { File(it, "still_$safeKey.jpg") }

        if (diskFile != null && diskFile.exists() && diskFile.length() > 512) {
            val bytes = diskFile.readBytes()
            memoryCache[cacheKey] = bytes
            return@withContext bytes
        }

        // Try authentic video frame extraction via MediaMetadataRetriever
        val fileToScan = targetFile ?: resolveFile(mediaId)
        if (fileToScan != null && fileToScan.exists() && fileToScan.length() > 4096) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(fileToScan.absolutePath)
                val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                val durationMs = durationStr?.toLongOrNull() ?: 0L
                // Extract frame at 10% of video or 5 seconds in to avoid opening black frames
                val targetUs = if (durationMs > 10_000) {
                    minOf(5_000_000L, (durationMs * 100).coerceAtLeast(1_000_000L))
                } else {
                    1_000_000L
                }

                var bitmap = retriever.getFrameAtTime(targetUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (bitmap == null) {
                    bitmap = retriever.frameAtTime
                }

                if (bitmap != null) {
                    val stream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                    val bytes = stream.toByteArray()
                    diskFile?.let { runCatching { it.writeBytes(bytes) } }
                    memoryCache[cacheKey] = bytes
                    Logger.i(LogTag.JELLYFIN, "Extracted authentic video thumbnail for: ${fileToScan.name} (${bytes.size} bytes)")
                    return@withContext bytes
                }
            } catch (e: Exception) {
                Logger.w(LogTag.JELLYFIN, "MediaMetadataRetriever extraction failed for ${fileToScan.name}: ${e.message}")
            } finally {
                runCatching { retriever.release() }
            }
        }

        // High quality stylized still card fallback (branded with title, quality, gradient)
        val title = fileToScan?.nameWithoutExtension ?: cleanTitle(mediaId)
        val fallbackBytes = generateStylizedStill(title)
        diskFile?.let { runCatching { it.writeBytes(fallbackBytes) } }
        memoryCache[cacheKey] = fallbackBytes
        return@withContext fallbackBytes
    }

    private fun resolveFile(idOrTitle: String): File? {
        val clean = idOrTitle.removePrefix("sandbox_").removePrefix("tg_")
        val moviesDir = StorageManager.getMoviesDir()
        if (moviesDir.exists()) {
            val match = moviesDir.walkTopDown().firstOrNull { f ->
                f.isFile && !f.name.startsWith(".") &&
                        (f.name.contains(clean, ignoreCase = true) ||
                         f.nameWithoutExtension.contains(clean, ignoreCase = true) ||
                         Math.abs(f.name.hashCode()).toString() == clean)
            }
            if (match != null) return match
        }
        return StorageManager.getSandboxFileById(clean)
    }

    fun cleanTitle(raw: String): String {
        return raw.replace(Regex("""(?i)\.(1080p|720p|2160p|4k|bluray|brrip|web-dl|x264|x265|hevc|yify|aac|dts).*"""), "")
            .replace(".", " ")
            .replace("_", " ")
            .trim()
    }

    private fun generateStylizedStill(title: String): ByteArray {
        val width = 480
        val height = 270 // 16:9 Still Card ratio standard for Jellyfin/iOS
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val hash = title.hashCode()
        val hue = (Math.abs(hash) % 360).toFloat()
        val colorDark = Color.HSVToColor(floatArrayOf(hue, 0.70f, 0.12f))
        val colorLight = Color.HSVToColor(floatArrayOf((hue + 45) % 360, 0.55f, 0.22f))

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val gradient = android.graphics.LinearGradient(
            0f, 0f, width.toFloat(), height.toFloat(),
            colorDark, colorLight, android.graphics.Shader.TileMode.CLAMP
        )
        paint.shader = gradient
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null

        // Decorative subtle grid overlay
        paint.color = Color.argb(18, 255, 255, 255)
        paint.strokeWidth = 1f
        paint.style = Paint.Style.STROKE
        for (x in 0..width step 40) {
            canvas.drawLine(x.toFloat(), 0f, x.toFloat(), height.toFloat(), paint)
        }
        for (y in 0..height step 40) {
            canvas.drawLine(0f, y.toFloat(), width.toFloat(), y.toFloat(), paint)
        }

        // Inner glowing border
        paint.color = Color.argb(45, 192, 76, 253)
        paint.strokeWidth = 2f
        canvas.drawRoundRect(RectF(8f, 8f, width - 8f, height - 8f), 12f, 12f, paint)

        // Film Reel / Play emblem
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.textSize = 42f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("🎬", width / 2f, height / 2f - 12f, paint)

        // Clean movie title
        paint.textSize = 19f
        paint.color = Color.WHITE
        paint.isFakeBoldText = true
        val displayTitle = cleanTitle(title).take(30)
        canvas.drawText(displayTitle, width / 2f, height / 2f + 36f, paint)

        // Badges
        paint.textSize = 11f
        paint.isFakeBoldText = false
        paint.color = Color.argb(220, 56, 189, 248)
        canvas.drawText("1080P DIRECT PLAY • HOME VAULT", width / 2f, height / 2f + 64f, paint)

        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
        return stream.toByteArray()
    }
}
