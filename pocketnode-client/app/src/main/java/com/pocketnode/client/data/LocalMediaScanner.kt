package com.pocketnode.client.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.util.Locale

/**
 * Discovers and resolves locally downloaded videos across device storage,
 * downloads directories, MediaStore, and PocketNode daemon cache.
 */
object LocalMediaScanner {

    private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "webm", "avi", "mov", "m4v", "ts")

    /**
     * Resolves a video target string (which could be a title, file path, file://, or content:// URI)
     * to a playable local Android [Uri].
     *
     * If the string already points to a valid local file or content URI, returns it directly.
     * Otherwise, searches standard device Movies/Downloads folders and MediaStore for a matching file.
     */
    fun resolveLocalVideoUri(context: Context, targetOrTitle: String): Uri? {
        val trimmed = targetOrTitle.trim()
        if (trimmed.isBlank()) return null

        // 1. Direct Content URI (e.g., from document picker or MediaStore)
        if (trimmed.startsWith("content://")) {
            return runCatching { Uri.parse(trimmed) }.getOrNull()
        }

        // 2. Direct File URI
        if (trimmed.startsWith("file://")) {
            val file = File(trimmed.removePrefix("file://"))
            if (file.exists() && file.length() > 0) {
                return Uri.fromFile(file)
            }
        }

        // 3. Absolute filesystem path
        if (trimmed.startsWith("/") || (trimmed.length > 2 && trimmed[1] == ':')) {
            val file = File(trimmed)
            if (file.exists() && file.length() > 0) {
                return Uri.fromFile(file)
            }
        }

        // 4. Search local directories for matching video by title keyword
        val candidate = findMatchingFileInStorage(context, trimmed)
        if (candidate != null) {
            return Uri.fromFile(candidate)
        }

        // 5. Search Android MediaStore for matching video
        val mediaStoreUri = findInMediaStore(context, trimmed)
        if (mediaStoreUri != null) {
            return mediaStoreUri
        }

        return null
    }

    /**
     * Scans storage for all downloaded video files to populate the Library and offline cache.
     */
    fun scanDownloadedVideos(context: Context): List<MediaItemDto> {
        val result = mutableListOf<MediaItemDto>()
        val seenPaths = mutableSetOf<String>()

        // 1. Query Android MediaStore
        try {
            val projection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.DATA
            )
            val selection = "${MediaStore.Video.Media.SIZE} > 5000000" // > 5MB
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                "${MediaStore.Video.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val dataCol = cursor.getColumnIndex(MediaStore.Video.Media.DATA)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "Downloaded Video"
                    val size = cursor.getLong(sizeCol)
                    val durMs = cursor.getLong(durCol)
                    val path = if (dataCol >= 0) cursor.getString(dataCol) ?: "" else ""
                    val contentUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)

                    val cleanTitle = cleanFileNameToTitle(name)
                    val ext = name.substringAfterLast(".", "mp4").uppercase(Locale.ROOT)
                    val durationMin = if (durMs > 0) "${durMs / 60000}m" else "Offline File"

                    if (path.isNotBlank()) seenPaths.add(path.lowercase(Locale.ROOT))

                    result.add(
                        MediaItemDto(
                            id = "local_media_$id",
                            title = cleanTitle,
                            year = "Local",
                            category = "Downloaded",
                            formatTag = "Direct Play • $ext • Local Device Storage",
                            formatBadge = "OFFLINE $ext",
                            sizeBytes = size,
                            sizeFormatted = formatBytes(size),
                            durationStr = durationMin,
                            progressPercent = 0f,
                            progressStr = "Stored locally on device",
                            posterUrl = PosterResolver.getPoster(cleanTitle),
                            streamUrl = contentUri.toString()
                        )
                    )
                }
            }
        } catch (_: Exception) {}

        // 2. Scan physical folders (Movies, Downloads, PocketNode cache)
        for (folder in getCandidateDirectories(context)) {
            if (!folder.exists() || !folder.isDirectory) continue
            folder.walkTopDown().maxDepth(2).filter { it.isFile && isVideoFile(it) }.forEach { file ->
                val pathKey = file.absolutePath.lowercase(Locale.ROOT)
                if (pathKey !in seenPaths) {
                    seenPaths.add(pathKey)
                    val cleanTitle = cleanFileNameToTitle(file.name)
                    val ext = file.extension.uppercase(Locale.ROOT)
                    result.add(
                        MediaItemDto(
                            id = "file_${file.name.hashCode()}",
                            title = cleanTitle,
                            year = "Local",
                            category = "Downloaded",
                            formatTag = "Local Storage • $ext • ${file.parentFile?.name ?: "Downloads"}",
                            formatBadge = "LOCAL $ext",
                            sizeBytes = file.length(),
                            sizeFormatted = formatBytes(file.length()),
                            durationStr = "Downloaded Video",
                            progressPercent = 0f,
                            progressStr = "Offline Ready • ${file.absolutePath}",
                            posterUrl = PosterResolver.getPoster(cleanTitle),
                            streamUrl = file.absolutePath
                        )
                    )
                }
            }
        }

        return result
    }

    private fun findMatchingFileInStorage(context: Context, keyword: String): File? {
        val normKeyword = keyword.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]"), "")

        if (normKeyword.isBlank()) return null

        for (dir in getCandidateDirectories(context)) {
            if (!dir.exists() || !dir.isDirectory) continue
            val matched = dir.walkTopDown().maxDepth(3).find { file ->
                if (file.isFile && isVideoFile(file)) {
                    val normName = file.nameWithoutExtension.lowercase(Locale.ROOT)
                        .replace(Regex("[^a-z0-9]"), "")
                    normName.contains(normKeyword) || normKeyword.contains(normName)
                } else {
                    false
                }
            }
            if (matched != null) return matched
        }
        return null
    }

    private fun findInMediaStore(context: Context, keyword: String): Uri? {
        val norm = keyword.lowercase(Locale.ROOT).trim()
        try {
            val projection = arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME)
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                null
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameCol)?.lowercase(Locale.ROOT) ?: ""
                    if (name.contains(norm) || norm.contains(name.substringBefore("."))) {
                        val id = cursor.getLong(idCol)
                        return ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    private fun getCandidateDirectories(context: Context): List<File> {
        val list = mutableListOf<File>()

        // 1. Android public Movies directory
        runCatching { Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES) }
            .getOrNull()?.let { list.add(it) }

        // 2. Android public Downloads directory
        runCatching { Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) }
            .getOrNull()?.let { list.add(it) }

        // 3. App-specific external files
        runCatching { context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) }
            .getOrNull()?.let { list.add(it) }
        runCatching { context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) }
            .getOrNull()?.let { list.add(it) }
        runCatching { context.getExternalFilesDir(null) }
            .getOrNull()?.let { list.add(it) }

        // 4. Well-known Android storage locations for PocketNode / Torrents
        val extRoot = runCatching { Environment.getExternalStorageDirectory() }.getOrNull()
        if (extRoot != null) {
            list.add(File(extRoot, "Movies/PocketNode"))
            list.add(File(extRoot, "Download/PocketNode"))
            list.add(File(extRoot, "Movies"))
            list.add(File(extRoot, "Download"))
            // Sibling Daemon App data folder if accessible
            list.add(File(extRoot, "Android/data/com.remotemedia/files/movies"))
        }

        return list
    }

    private fun isVideoFile(file: File): Boolean {
        return file.extension.lowercase(Locale.ROOT) in VIDEO_EXTENSIONS
    }

    fun cleanFileNameToTitle(fileName: String): String {
        return fileName
            .substringBeforeLast(".")
            .replace(Regex("[._-]"), " ")
            .replace(Regex("(?i)(1080p|2160p|4k|720p|bluray|remux|x264|x265|hevc|web-dl|dts-hd|aac|dual audio|mkv|mp4).*"), "")
            .trim()
            .ifBlank { fileName.substringBeforeLast(".") }
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(Locale.US, "%.1f GB", gb)
            mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
            kb >= 1.0 -> String.format(Locale.US, "%.1f KB", kb)
            else -> "$bytes B"
        }
    }
}
