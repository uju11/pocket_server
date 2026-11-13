package com.remotemedia.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.remotemedia.core.LogTag
import com.remotemedia.core.Logger
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
    private val gson = Gson()

    // Additional user-shared folders (besides the main PocketNode base dir)
    private val _additionalSharedPaths = mutableListOf<File>()

    // Thread-safe path & UUID fast index for O(1) instant resolution
    private val fileIdCache = java.util.concurrent.ConcurrentHashMap<String, File>()

    fun cacheFile(f: File) {
        val abs = runCatching { f.canonicalPath }.getOrElse { f.absolutePath }
        fileIdCache[abs] = f
        fileIdCache[f.absolutePath] = f
        runCatching {
            val canonUuid = UUID.nameUUIDFromBytes(f.canonicalPath.toByteArray()).toString()
            fileIdCache[canonUuid] = f
            fileIdCache[canonUuid.replace("-", "")] = f
            fileIdCache["sandbox_$canonUuid"] = f
        }
        runCatching {
            val absUuid = UUID.nameUUIDFromBytes(f.absolutePath.toByteArray()).toString()
            fileIdCache[absUuid] = f
            fileIdCache[absUuid.replace("-", "")] = f
            fileIdCache["sandbox_$absUuid"] = f
        }
        fileIdCache[f.name.lowercase(Locale.ROOT)] = f
        fileIdCache[f.nameWithoutExtension.lowercase(Locale.ROOT)] = f
        val nameUuid = UUID.nameUUIDFromBytes(f.name.toByteArray()).toString()
        fileIdCache[nameUuid] = f
        fileIdCache[nameUuid.replace("-", "")] = f
    }

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

        // Restore previously saved additional shared paths
        loadAdditionalSharedPaths(context)
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

    private fun normalizeUserPath(path: String): String {
        var normalized = path.replace('\\', '/').trim()
        if (!normalized.startsWith("/")) {
            normalized = "/$normalized"
        }
        if (normalized.startsWith("/downloads/", ignoreCase = true) || normalized.startsWith("/download/", ignoreCase = true) || normalized.startsWith("/donload/", ignoreCase = true)) {
            val suffix = normalized.substring(normalized.indexOf("/", 1) + 1)
            return File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), suffix).absolutePath
        } else if (normalized.startsWith("/movies/", ignoreCase = true) || normalized.startsWith("/movie/", ignoreCase = true)) {
            val suffix = normalized.substring(normalized.indexOf("/", 1) + 1)
            return File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), suffix).absolutePath
        }
        return normalized
    }

    fun setCustomStorageDir(context: Context, newPath: String): Boolean {
        val normalizedPath = normalizeUserPath(newPath)
        val file = File(normalizedPath)
        if (!file.exists()) {
            file.mkdirs()
        }
        return if (file.exists() && file.isDirectory && file.canWrite()) {
            init(context, normalizedPath)
            true
        } else {
            false
        }
    }


    // ── Additional Shared Paths ──────────────────────────────────────────────────
    // User-defined extra folders (e.g. /movies/new/) served with the same
    // lossless direct-play as the main PocketNode folder.

    /** Adds a folder path as an additional shared source. Returns true if valid and added. */
    fun addAdditionalSharedPath(context: Context, path: String): Boolean {
        val normalizedPath = normalizeUserPath(path)
        val file = File(normalizedPath)
        if (!file.exists() || !file.isDirectory || !file.canRead()) return false
        val canon = runCatching { file.canonicalFile }.getOrElse { file.absoluteFile }
        synchronized(_additionalSharedPaths) {
            if (_additionalSharedPaths.any { f -> runCatching { f.canonicalPath }.getOrElse { _ -> f.absolutePath } == canon.absolutePath }) {
                return true // already present
            }
            _additionalSharedPaths.add(canon)
        }
        saveAdditionalSharedPaths(context)
        Logger.i(LogTag.STORAGE, "Added additional shared path: ${canon.absolutePath}")
        return true
    }

    /** Removes a previously-added shared path by its exact absolute path. */
    fun removeAdditionalSharedPath(context: Context, path: String) {
        val normalizedPath = normalizeUserPath(path)
        val canon = runCatching { File(normalizedPath).canonicalPath }.getOrElse { normalizedPath }
        synchronized(_additionalSharedPaths) {
            _additionalSharedPaths.removeAll { f -> runCatching { f.canonicalPath }.getOrElse { _ -> f.absolutePath } == canon }
        }
        saveAdditionalSharedPaths(context)
        Logger.i(LogTag.STORAGE, "Removed additional shared path: $path")
    }

    /** Returns all currently active additional shared paths. */
    fun getAdditionalSharedPaths(): List<File> = synchronized(_additionalSharedPaths) {
        _additionalSharedPaths.filter { it.exists() && it.isDirectory }
    }

    private fun saveAdditionalSharedPaths(context: Context) {
        val paths = synchronized(_additionalSharedPaths) { _additionalSharedPaths.map { it.absolutePath } }
        val json = gson.toJson(paths)
        context.getSharedPreferences("remote_media_prefs", Context.MODE_PRIVATE)
            .edit().putString("additional_shared_paths_json", json).apply()
    }

    private fun loadAdditionalSharedPaths(context: Context) {
        val json = context.getSharedPreferences("remote_media_prefs", Context.MODE_PRIVATE)
            .getString("additional_shared_paths_json", null) ?: return
        runCatching {
            val type = object : com.google.gson.reflect.TypeToken<List<String>>() {}.type
            val paths: List<String> = gson.fromJson(json, type)
            synchronized(_additionalSharedPaths) {
                _additionalSharedPaths.clear()
                paths.mapNotNull { p ->
                    val f = File(p)
                    if (f.exists() && f.isDirectory) runCatching { f.canonicalFile }.getOrElse { f } else null
                }.forEach { _additionalSharedPaths.add(it) }
            }
        }.onFailure { Logger.w(LogTag.STORAGE, "Failed to restore additional shared paths: ${it.message}") }
    }

    /** All roots that the app is allowed to read from: base dir + additional shared paths. */
    private fun allAllowedRoots(): List<File> {
        val roots = mutableListOf<File>()
        runCatching { getBaseDir() }.getOrNull()?.let { roots.add(it) }
        roots.addAll(getAdditionalSharedPaths())
        return roots
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
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return ext in VIDEO_EXTS || ext in AUDIO_EXTS || ext in IMAGE_EXTS || ext in DOC_EXTS
    }
    fun isMediaFile(file: File): Boolean = isMediaFile(file.name)

    fun getDownloadsDir(): File {
        val dir = File(getBaseDir(), "downloads")
        if (!dir.exists()) dir.mkdirs()
        createNoMediaFile(dir)
        return dir.canonicalFile
    }

    /**
     * Returns the configured base directory, its immediate subdirectories, and any
     * additional shared paths the user has registered — the full set of allowed roots.
     */
    fun getAllSandboxSearchDirs(): List<File> {
        val dirs = mutableListOf<File>()
        for (root in allAllowedRoots()) {
            if (!root.exists() || !root.isDirectory) continue
            dirs.add(root)
            root.listFiles()?.forEach { child ->
                if (child.isDirectory && !child.name.startsWith(".")) dirs.add(child)
            }
        }
        return dirs.distinctBy { f -> runCatching { f.canonicalPath.lowercase() }.getOrElse { f.absolutePath.lowercase() } }
    }

    /**
     * Verifies that a file is within the configured base directory OR any additional
     * shared path the user registered. Protects against path traversal (e.g. "../../").
     */
    fun isPathSafe(file: File): Boolean {
        return try {
            val canon = file.canonicalFile
            allAllowedRoots().any { root ->
                pathIsInside(canon.path, root.canonicalPath)
            }
        } catch (e: Exception) {
            try {
                allAllowedRoots().any { root ->
                    pathIsInside(file.absolutePath, root.absolutePath)
                }
            } catch (_: Exception) {
                false
            }
        }
    }

    /** True when [target] is [base] or a path under it (requires separator boundary). */
    private fun pathIsInside(target: String, base: String): Boolean {
        val normTarget = target.replace('\\', '/').trimEnd('/')
        val normBase = base.replace('\\', '/').trimEnd('/')
        if (normTarget.equals(normBase, ignoreCase = true)) return true
        return normTarget.startsWith("$normBase/", ignoreCase = true)
    }

    /**
     * Recursive walk that never aborts the whole scan when one subdirectory is unreadable.
     * (Default [File.walkTopDown] throws on the first IOException and can wipe the library.)
     */
    private fun safeWalkFiles(dir: File): Sequence<File> {
        if (!dir.exists() || !dir.isDirectory) return emptySequence()
        return try {
            dir.walkTopDown()
                .onFail { file, exc ->
                    Logger.w(LogTag.STORAGE, "Skipping unreadable path during media scan: ${file.path} (${exc.message})")
                }
                .filter { it.isFile }
        } catch (e: Exception) {
            Logger.w(LogTag.STORAGE, "Media walk failed for ${dir.path}: ${e.message}")
            emptySequence()
        }
    }

    /**
     * Resolves a relative or absolute path strictly within the allowed roots (base dir + shared paths).
     * Returns null if the path escapes outside the configured PocketNode folder or shared folders.
     */
    fun resolveRelativePath(relativePath: String): File? {
        val cleanPath = relativePath.trimStart('/', '\\')
        // Absolute path — only allowed if it's inside our allowed roots
        val abs = File(cleanPath)
        if (abs.isAbsolute && abs.exists() && abs.isFile && isPathSafe(abs)) {
            cacheFile(abs)
            return abs
        }
        // Relative resolution across base dir and all additional shared paths
        for (root in allAllowedRoots()) {
            val target = File(root, cleanPath)
            if (target.exists() && target.isFile && isPathSafe(target)) {
                cacheFile(target)
                return target
            }
        }
        return null
    }

    /**
     * Recursively lists all non-hidden files within [dir], resolving subdirectories created by torrents.
     */
    fun listFiles(dir: File = getBaseDir()): List<File> {
        if (!isPathSafe(dir) || !dir.exists()) return emptyList()
        return safeWalkFiles(dir)
            .filter { isPathSafe(it) && !it.name.startsWith(".") && it.name != ".nomedia" && !isTemporaryDownload(it.name) }
            .onEach { cacheFile(it) }
            .sortedBy { it.name }
            .toList()
    }

    /**
     * Immediate child directories of [dir] (for web/file browser navigation into movies/, etc.).
     */
    fun listSubdirectories(dir: File = getBaseDir()): List<File> {
        if (!isPathSafe(dir) || !dir.exists() || !dir.isDirectory) return emptyList()
        return dir.listFiles()
            ?.filter { it.isDirectory && isPathSafe(it) && !it.name.startsWith(".") }
            ?.sortedBy { it.name }
            ?: emptyList()
    }

    /**
     * Recursively finds all media files inside a category folder (e.g., movies/ or music/).
     */
    fun getCategoryFiles(category: String): List<File> {
        val catDir = getCategoryDir(category)
        if (!catDir.exists()) return emptyList()
        return safeWalkFiles(catDir)
            .filter { !it.name.startsWith(".") && it.name != ".nomedia" && !isTemporaryDownload(it.name) }
            .onEach { cacheFile(it) }
            .sortedBy { it.name }
            .toList()
    }

    /** All video files within the base dir and any additional shared paths (recursive). */
    fun getLocalVideoFiles(): List<File> {
        return allAllowedRoots()
            .filter { it.exists() && it.isDirectory }
            .flatMap { root ->
                safeWalkFiles(root).filter {
                    !it.name.startsWith(".") &&
                        it.name != ".nomedia" &&
                        !isTemporaryDownload(it.name) &&
                        it.length() > 0 &&
                        isVideoFile(it.name)
                }.onEach { cacheFile(it) }
                .toList()
            }
            .distinctBy { f -> runCatching { f.canonicalPath.lowercase() }.getOrElse { f.absolutePath.lowercase() } }
            .sortedBy { it.name }
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
     * Federation helper — returns all media files across the base dir and all additional
     * shared paths the user has registered. Categorizes movies/videos/audio/etc.
     */
    fun getFederatedSandboxFiles(): List<SandboxMediaFile> {
        val result = mutableListOf<SandboxMediaFile>()
        val roots = allAllowedRoots()
        if (roots.isEmpty()) return emptyList()

        val baseCanon = runCatching { getBaseDir().canonicalPath }.getOrElse { getBaseDir().absolutePath }
        // Walk every root recursively
        val searchDirs = roots.distinctBy { f -> runCatching { f.canonicalPath }.getOrElse { f.absolutePath } }

        for (dir in searchDirs) {
            if (!dir.exists()) continue
            for (f in safeWalkFiles(dir)) {
                if (f.name.startsWith(".") || f.name == ".nomedia" || isTemporaryDownload(f.name) || f.length() <= 0) continue
                runCatching {
                    cacheFile(f)
                    val abs = runCatching { f.canonicalPath }.getOrElse { f.absolutePath }
                    val mime = getMimeFromExtension(f.name)
                    val cat = categorizeBySizeAndMime(f.name, mime, f.length(), f.parentFile?.name, abs)
                    result.add(
                        SandboxMediaFile(
                            id           = UUID.nameUUIDFromBytes(abs.toByteArray()).toString(),
                            name         = f.name,
                            category     = cat,
                            mimeType     = mime,
                            sizeBytes    = f.length(),
                            absolutePath = abs
                        )
                    )
                }.onFailure {
                    Logger.w(LogTag.STORAGE, "Skipping sandbox file ${f.name}: ${it.message}")
                }
            }
        }

        val deduped = result.distinctBy { it.absolutePath }
        Logger.i(
            LogTag.STORAGE,
            "Sandbox scan: ${deduped.size} media file(s) (base=$baseCanon, extra=${roots.size - 1} shared path(s)) " +
                "(videos=${deduped.count { it.category == "Movies" || it.category == "Videos" }})"
        )
        return deduped
    }

    /** M5 Federation helper — find a sandbox file by its federation ID, filename, or path recursively. */
    fun getSandboxFileById(id: String): File? {
        if (id.isBlank()) return null
        val decoded = try { java.net.URLDecoder.decode(id, "UTF-8") } catch (_: Exception) { id }
        val clean = decoded.removePrefix("sandbox_").removePrefix("file_").removePrefix("local_").removePrefix("tg_")
        val cleanLower = clean.lowercase(Locale.ROOT)
        val noExt = clean.substringBeforeLast('.').lowercase(Locale.ROOT)
        val cleanNoHyphen = clean.replace("-", "")

        // 1. Check in-memory fast index first
        fileIdCache[id]?.takeIf { it.exists() && isPathSafe(it) }?.let { return it }
        fileIdCache[clean]?.takeIf { it.exists() && isPathSafe(it) }?.let { return it }
        fileIdCache[decoded]?.takeIf { it.exists() && isPathSafe(it) }?.let { return it }
        fileIdCache[cleanLower]?.takeIf { it.exists() && isPathSafe(it) }?.let { return it }
        fileIdCache[cleanNoHyphen]?.takeIf { it.exists() && isPathSafe(it) }?.let { return it }
        fileIdCache[noExt]?.takeIf { it.exists() && isPathSafe(it) }?.let { return it }

        // 2. Absolute path — allowed if it resolves inside any allowed root
        val absCandidate = File(clean)
        if (absCandidate.isAbsolute && absCandidate.exists() && absCandidate.isFile && isPathSafe(absCandidate)) {
            cacheFile(absCandidate)
            return absCandidate
        }

        // 3. Fallback: Search all allowed roots
        val searchDirs = allAllowedRoots()
            .distinctBy { f -> runCatching { f.canonicalPath }.getOrElse { f.absolutePath } }
        for (dir in searchDirs) {
            if (!dir.exists()) continue
            val found = safeWalkFiles(dir).firstOrNull { f ->
                if (f.name.startsWith(".") || f.name == ".nomedia") return@firstOrNull false
                cacheFile(f)
                val fCanonUuid = runCatching { UUID.nameUUIDFromBytes(f.canonicalPath.toByteArray()).toString() }.getOrNull()
                val fAbsUuid = runCatching { UUID.nameUUIDFromBytes(f.absolutePath.toByteArray()).toString() }.getOrNull()
                val fNameUuid = runCatching { UUID.nameUUIDFromBytes(f.name.toByteArray()).toString() }.getOrNull()

                fCanonUuid == clean || fCanonUuid == id ||
                (fCanonUuid != null && (fCanonUuid.replace("-", "") == cleanNoHyphen || fCanonUuid.replace("-", "") == id.replace("-", ""))) ||
                fAbsUuid == clean || fAbsUuid == id ||
                (fAbsUuid != null && (fAbsUuid.replace("-", "") == cleanNoHyphen || fAbsUuid.replace("-", "") == id.replace("-", ""))) ||
                fNameUuid == clean || fNameUuid == id ||
                (fNameUuid != null && (fNameUuid.replace("-", "") == cleanNoHyphen || fNameUuid.replace("-", "") == id.replace("-", ""))) ||
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

    fun categorizeBySizeAndMime(
        fileName: String,
        mime: String,
        sizeBytes: Long,
        parentFolderName: String? = null,
        absolutePath: String? = null
    ): String {
        val ext   = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val parent = parentFolderName?.lowercase(Locale.ROOT) ?: ""
        val pathLower = absolutePath?.lowercase(Locale.ROOT)?.replace('\\', '/') ?: ""

        return when {
            // All video files in library are categorized as Movies so they are never missed
            mime.startsWith("video/") || ext in VIDEO_EXTS -> "Movies"
            mime.startsWith("audio/") || ext in AUDIO_EXTS || parent == "music" || pathLower.contains("/music/") -> "Music"
            mime.startsWith("image/") || ext in IMAGE_EXTS || parent == "photos" || pathLower.contains("/photos/") -> "Photos"
            mime.startsWith("text/") || mime.contains("pdf") || mime.contains("document") || ext in DOC_EXTS || parent == "documents" || pathLower.contains("/documents/") -> "Documents"
            else -> "Others"
        }
    }
}
