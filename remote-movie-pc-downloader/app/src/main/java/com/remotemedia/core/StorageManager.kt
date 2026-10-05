package com.remotemedia.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import java.io.File
import java.util.Locale
import java.util.UUID

/** Lightweight model for a sandbox-resident media file, used by [FederatedMediaManager]. */
data class SandboxMediaFile(
    val id: String,
    val name: String,
    val category: String,
    val mimeType: String,
    val sizeBytes: Long,
    val absolutePath: String
)

object StorageManager {
    private var baseDir: File? = null
    private var appContext: Context? = null

    fun getContext(): Context? = appContext

    fun isAllFilesAccessGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    fun isScopedSandbox(dir: File = getBaseDir()): Boolean {
        return dir.absolutePath.contains("Android/data", ignoreCase = true)
    }

    fun openStorageAccessSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (_: Exception) {}
            }
        }
    }

    fun resolveDefaultStorageDir(context: Context): File {
        // 1. Try public Movies/PocketNode
        try {
            val publicMovies = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "PocketNode")
            if (!publicMovies.exists()) publicMovies.mkdirs()
            if (publicMovies.exists() && publicMovies.canWrite()) {
                return publicMovies
            }
        } catch (_: Exception) {}

        // 2. Try public Download/PocketNode
        try {
            val publicDownloads = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "PocketNode")
            if (!publicDownloads.exists()) publicDownloads.mkdirs()
            if (publicDownloads.exists() && publicDownloads.canWrite()) {
                return publicDownloads
            }
        } catch (_: Exception) {}

        // 3. Fallback to app external files sandbox
        return context.getExternalFilesDir("media") ?: File(context.filesDir, "media")
    }

    fun switchToPublicStorage(context: Context): Boolean {
        try {
            val publicMovies = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "PocketNode")
            if (!publicMovies.exists()) publicMovies.mkdirs()
            if (publicMovies.exists() && publicMovies.canWrite()) {
                init(context, publicMovies.absolutePath)
                return true
            }
        } catch (_: Exception) {}

        try {
            val publicDownloads = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "PocketNode")
            if (!publicDownloads.exists()) publicDownloads.mkdirs()
            if (publicDownloads.exists() && publicDownloads.canWrite()) {
                init(context, publicDownloads.absolutePath)
                return true
            }
        } catch (_: Exception) {}

        return false
    }

    fun init(context: Context, customPath: String? = null) {
        appContext = context.applicationContext
        val prefs = context.getSharedPreferences("remote_media_prefs", Context.MODE_PRIVATE)
        val selectedPath = customPath ?: prefs.getString("custom_storage_path", null)

        val targetDir = if (!selectedPath.isNullOrBlank()) {
            val file = File(selectedPath)
            if (file.exists() && file.isDirectory && file.canWrite()) {
                file
            } else {
                resolveDefaultStorageDir(context)
            }
        } else {
            resolveDefaultStorageDir(context)
        }

        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }

        baseDir = targetDir.canonicalFile

        // Save selected path
        prefs.edit().putString("custom_storage_path", baseDir?.absolutePath).apply()

        // Initialize sandbox sentinel file to prevent Android Gallery indexing
        createNoMediaFile(baseDir)

        // Initialize curated category subfolders
        CURATED_CATEGORIES.forEach { category ->
            val catDir = File(baseDir, category.lowercase())
            if (!catDir.exists()) catDir.mkdirs()
            createNoMediaFile(catDir)
        }
    }

    val CURATED_CATEGORIES = listOf("Movies", "Music", "Videos", "Photos", "Documents", "Others")

    private fun createNoMediaFile(dir: File?) {
        if (dir == null) return
        try {
            val noMedia = File(dir, ".nomedia")
            if (!noMedia.exists()) {
                noMedia.createNewFile()
            }
        } catch (_: Exception) {}
    }

    fun setCustomStorageDir(context: Context, newPath: String): Boolean {
        val file = File(newPath)
        if (!file.exists()) {
            file.mkdirs()
        }
        return if (file.exists() && file.isDirectory && file.canWrite()) {
            init(context, newPath)
            true
        } else {
            false
        }
    }

    fun getBaseDir(): File {
        return baseDir ?: throw IllegalStateException("StorageManager is not initialized")
    }

    fun getSandboxFolder(): File = getBaseDir()

    fun getCategoryDir(category: String): File {
        val dir = File(getBaseDir(), category.lowercase())
        if (!dir.exists()) dir.mkdirs()
        createNoMediaFile(dir)
        return dir.canonicalFile
    }

    fun getMoviesDir(): File = getCategoryDir("movies")
    fun getMoviesFolder(): File = getMoviesDir()

    fun isMediaFile(fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return listOf("mp4", "mkv", "avi", "mov", "webm", "flv", "m4v", "3gp", "ts", "mp3", "flac", "wav", "jpg", "png").contains(ext)
    }
    fun isMediaFile(file: File): Boolean = isMediaFile(file.name)

    fun getDownloadsDir(): File {
        val dir = File(getBaseDir(), "downloads")
        if (!dir.exists()) dir.mkdirs()
        createNoMediaFile(dir)
        return dir.canonicalFile
    }

    /**
     * Strictly verifies that a target file is contained within the chosen base directory sandbox.
     * Protects against path traversal security vulnerabilities (e.g., "../../").
     */
    /**
     * Strictly verifies that a target file is contained within the chosen base directory sandbox.
     * Protects against path traversal security vulnerabilities (e.g., "../../").
     */
    fun isPathSafe(file: File): Boolean {
        return try {
            val base = getBaseDir()
            val canonicalTarget = file.canonicalFile.path
            val canonicalBase = base.canonicalFile.path
            if (canonicalTarget.startsWith(canonicalBase)) return true
            val absTarget = file.absoluteFile.path
            val absBase = base.absoluteFile.path
            absTarget.startsWith(absBase)
        } catch (e: Exception) {
            try {
                file.absolutePath.startsWith(getBaseDir().absolutePath)
            } catch (_: Exception) {
                true
            }
        }
    }

    /**
     * Resolves a relative path within the isolated base directory safely.
     * Returns null if path attempts to escape the root directory.
     */
    fun resolveRelativePath(relativePath: String): File? {
        val cleanPath = relativePath.trimStart('/', '\\')
        val target = File(getBaseDir(), cleanPath)
        return if (isPathSafe(target)) target else null
    }

    /**
     * Recursively lists all non-hidden files within [dir], resolving subdirectories created by torrents.
     */
    fun listFiles(dir: File = getBaseDir()): List<File> {
        if (!isPathSafe(dir) || !dir.exists()) return emptyList()
        return dir.walkTopDown()
            .filter { isPathSafe(it) && it.isFile && !it.name.startsWith(".") && it.name != ".nomedia" && !isTemporaryDownload(it.name) }
            .sortedBy { it.name }
            .toList()
    }

    /**
     * Recursively finds all media files inside a category folder (e.g., movies/ or music/).
     */
    fun getCategoryFiles(category: String): List<File> {
        val catDir = getCategoryDir(category)
        if (!catDir.exists()) return emptyList()
        return catDir.walkTopDown()
            .filter { it.isFile && !it.name.startsWith(".") && it.name != ".nomedia" && !isTemporaryDownload(it.name) }
            .sortedBy { it.name }
            .toList()
    }

    private val VIDEO_EXTS = setOf("mp4", "mkv", "avi", "mov", "webm", "flv", "m4v", "ts", "wmv", "3gp", "vob", "mpg", "mpeg", "m2ts", "asf")
    private val AUDIO_EXTS = setOf("mp3", "flac", "wav", "aac", "m4a", "ogg", "wma", "opus", "alac", "aiff")
    private val IMAGE_EXTS = setOf("jpg", "jpeg", "png", "webp", "gif", "heic", "bmp", "tiff")
    private val DOC_EXTS   = setOf("pdf", "epub", "doc", "docx", "txt", "mobi", "xlsx", "pptx", "md", "srt", "vtt")

    fun isVideoFile(fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return ext in VIDEO_EXTS
    }

    private fun isTemporaryDownload(fileName: String): Boolean {
        val lower = fileName.lowercase(Locale.ROOT)
        return lower.endsWith(".part") || lower.endsWith(".crdownload") || lower.endsWith(".!bt") || lower.endsWith(".tmp")
    }

    /**
     * Comprehensive Federation helper — returns all media files across all sandbox directories,
     * including movies/, downloads/, music/, photos/, documents/, and root baseDir subdirectories.
     * Accurately categorizes movies and video torrents so Jellyfin, DLNA, and Web always detect them.
     */
    fun getFederatedSandboxFiles(): List<SandboxMediaFile> {
        val result = mutableListOf<SandboxMediaFile>()
        val base = runCatching { getBaseDir() }.getOrNull() ?: return emptyList()
        if (!base.exists()) return emptyList()

        // Scan all folders: curated folders, downloads, and base root recursively
        val searchDirs = (CURATED_CATEGORIES.map { getCategoryDir(it) } + getDownloadsDir() + base).distinctBy { it.canonicalPath }

        for (dir in searchDirs) {
            if (!dir.exists()) continue
            val files = dir.walkTopDown()
                .filter { it.isFile && !it.name.startsWith(".") && it.name != ".nomedia" && !isTemporaryDownload(it.name) && it.length() > 0 }
                .toList()

            for (f in files) {
                val mime = getMimeFromExtension(f.name)
                val cat = categorizeBySizeAndMime(f.name, mime, f.length(), f.parentFile?.name)
                result.add(
                    SandboxMediaFile(
                        id           = UUID.nameUUIDFromBytes(f.canonicalPath.toByteArray()).toString(),
                        name         = f.name,
                        category     = cat,
                        mimeType     = mime,
                        sizeBytes    = f.length(),
                        absolutePath = f.canonicalPath
                    )
                )
            }
        }
        return result.distinctBy { it.absolutePath }
    }

    /** M5 Federation helper — find a sandbox file by its federation ID, filename, or path recursively. */
    fun getSandboxFileById(id: String): File? {
        val base = runCatching { getBaseDir() }.getOrNull() ?: return null
        if (!base.exists()) return null

        val decoded = try { java.net.URLDecoder.decode(id, "UTF-8") } catch (_: Exception) { id }
        val clean = decoded.removePrefix("sandbox_").removePrefix("file_").removePrefix("local_").removePrefix("tg_")
        val noExt = clean.substringBeforeLast('.')
        val cleanNoHyphen = clean.replace("-", "")
        val idNoHyphen = id.replace("-", "")

        // Try direct file path resolution
        resolveRelativePath(clean)?.let { if (it.exists() && it.isFile) return it }
        val absCandidate = File(clean)
        if (absCandidate.isAbsolute && absCandidate.exists() && absCandidate.isFile) return absCandidate

        val searchDirs = (CURATED_CATEGORIES.map { getCategoryDir(it) } + getDownloadsDir() + base).distinctBy { it.canonicalPath }
        for (dir in searchDirs) {
            if (!dir.exists()) continue
            val found = dir.walkTopDown().firstOrNull { f ->
                if (!f.isFile || f.name.startsWith(".") || f.name == ".nomedia") return@firstOrNull false
                val fCanonUuid = runCatching { UUID.nameUUIDFromBytes(f.canonicalPath.toByteArray()).toString() }.getOrNull()
                val fAbsUuid = runCatching { UUID.nameUUIDFromBytes(f.absolutePath.toByteArray()).toString() }.getOrNull()
                val fNameUuid = runCatching { UUID.nameUUIDFromBytes(f.name.toByteArray()).toString() }.getOrNull()

                fCanonUuid == clean || fCanonUuid == id ||
                (fCanonUuid != null && (fCanonUuid.replace("-", "") == cleanNoHyphen || fCanonUuid.replace("-", "") == idNoHyphen)) ||
                fAbsUuid == clean || fAbsUuid == id ||
                (fAbsUuid != null && (fAbsUuid.replace("-", "") == cleanNoHyphen || fAbsUuid.replace("-", "") == idNoHyphen)) ||
                fNameUuid == clean || fNameUuid == id ||
                (fNameUuid != null && (fNameUuid.replace("-", "") == cleanNoHyphen || fNameUuid.replace("-", "") == idNoHyphen)) ||
                f.name.equals(clean, ignoreCase = true) ||
                f.name.equals(decoded, ignoreCase = true) ||
                f.name.replace('+', ' ').equals(clean.replace('+', ' '), ignoreCase = true) ||
                f.nameWithoutExtension.equals(clean, ignoreCase = true) ||
                f.nameWithoutExtension.equals(noExt, ignoreCase = true) ||
                f.nameWithoutExtension.replace('+', ' ').equals(noExt.replace('+', ' '), ignoreCase = true) ||
                f.name.contains(noExt, ignoreCase = true)
            }
            if (found != null) return found
        }
        return null
    }

    fun getMimeFromExtension(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return when (ext) {
            "mp4"        -> "video/mp4"
            "mkv"        -> "video/x-matroska"
            "avi"        -> "video/x-msvideo"
            "mov"        -> "video/quicktime"
            "webm"       -> "video/webm"
            "m4v"        -> "video/x-m4v"
            "flv"        -> "video/x-flv"
            "ts"         -> "video/mp2t"
            "wmv"        -> "video/x-ms-wmv"
            "3gp"        -> "video/3gpp"
            "vob"        -> "video/dvd"
            "mp3"        -> "audio/mpeg"
            "flac"       -> "audio/flac"
            "wav"        -> "audio/wav"
            "aac"        -> "audio/aac"
            "m4a"        -> "audio/mp4"
            "ogg"        -> "audio/ogg"
            "opus"       -> "audio/opus"
            "jpg","jpeg" -> "image/jpeg"
            "png"        -> "image/png"
            "webp"       -> "image/webp"
            "gif"        -> "image/gif"
            "pdf"        -> "application/pdf"
            "txt"        -> "text/plain"
            "epub"       -> "application/epub+zip"
            "srt"        -> "text/plain"
            "vtt"        -> "text/vtt"
            else         -> "application/octet-stream"
        }
    }

    fun categorizeBySizeAndMime(fileName: String, mime: String, sizeBytes: Long, parentFolderName: String? = null): String {
        val lower = fileName.lowercase(Locale.ROOT)
        val ext   = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val parent = parentFolderName?.lowercase(Locale.ROOT) ?: ""

        return when {
            mime.startsWith("video/") || ext in VIDEO_EXTS -> {
                // If it's inside the movies directory or over 150MB or has standard movie naming tags, categorize as Movies
                if (parent == "movies" || sizeBytes > 150 * 1024 * 1024 ||
                    lower.contains("1080p") || lower.contains("720p") || lower.contains("2160p") || lower.contains("4k") ||
                    lower.contains("bluray") || lower.contains("webrip") || lower.contains("web-dl") || lower.contains("movie") ||
                    lower.contains("yify") || lower.contains("yts") || lower.contains("x264") || lower.contains("x265"))
                    "Movies" else "Videos"
            }
            mime.startsWith("audio/") || ext in AUDIO_EXTS || parent == "music" -> "Music"
            mime.startsWith("image/") || ext in IMAGE_EXTS || parent == "photos" -> "Photos"
            mime.startsWith("text/") || mime.contains("pdf") || mime.contains("document") || ext in DOC_EXTS || parent == "documents" -> "Documents"
            else -> "Others"
        }
    }
}
