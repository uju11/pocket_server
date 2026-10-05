package com.remotemedia.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.Locale
import java.util.UUID

data class HostSharedFolder(
    val id: String = UUID.randomUUID().toString(),
    val uriString: String,
    val displayName: String,
    val addedTimestamp: Long = System.currentTimeMillis(),
    var fileCount: Int = 0,
    var totalSizeBytes: Long = 0L
)

data class HostVirtualMediaFile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val uriString: String,
    val realFilePath: String? = null,
    val mimeType: String = "",
    val sizeBytes: Long = 0L,
    val category: String = "Others", // Movies, Music, Videos, Photos, Documents, Others
    val lastModified: Long = 0L,
    val sourceFolderId: String = ""
)

object HostMediaManager {

    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _isSharingEnabled = MutableStateFlow(false)
    val isSharingEnabled: StateFlow<Boolean> = _isSharingEnabled.asStateFlow()

    private val _sharedFolders = MutableStateFlow<List<HostSharedFolder>>(emptyList())
    val sharedFolders: StateFlow<List<HostSharedFolder>> = _sharedFolders.asStateFlow()

    private val _virtualFiles = MutableStateFlow<List<HostVirtualMediaFile>>(emptyList())
    val virtualFiles: StateFlow<List<HostVirtualMediaFile>> = _virtualFiles.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences("remote_media_prefs", Context.MODE_PRIVATE)
        _isSharingEnabled.value = prefs.getBoolean("host_media_sharing_enabled", false)

        val foldersJson = prefs.getString("host_shared_folders_json", null)
        if (!foldersJson.isNullOrBlank()) {
            runCatching {
                val type = object : TypeToken<List<HostSharedFolder>>() {}.type
                val saved: List<HostSharedFolder> = gson.fromJson(foldersJson, type)
                _sharedFolders.value = saved
            }
        }

        val filesJson = prefs.getString("host_virtual_files_json", null)
        if (!filesJson.isNullOrBlank()) {
            runCatching {
                val type = object : TypeToken<List<HostVirtualMediaFile>>() {}.type
                val saved: List<HostVirtualMediaFile> = gson.fromJson(filesJson, type)
                _virtualFiles.value = saved
            }
        }

        // If sharing is enabled and we have folders, schedule a background refresh
        if (_isSharingEnabled.value && _sharedFolders.value.isNotEmpty()) {
            scope.launch {
                rescanAllFoldersInternal(context)
            }
        }
    }

    fun setSharingEnabled(context: Context, enabled: Boolean) {
        _isSharingEnabled.value = enabled
        val prefs = context.getSharedPreferences("remote_media_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("host_media_sharing_enabled", enabled).apply()
        Logger.i(LogTag.STORAGE, "Host mobile media sharing toggled: $enabled")

        if (enabled && _sharedFolders.value.isNotEmpty()) {
            scope.launch {
                rescanAllFoldersInternal(context)
            }
        }
    }

    fun addSharedFolder(context: Context, treeUri: Uri): HostSharedFolder? {
        try {
            // Persist read URI permission across device reboots
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(treeUri, takeFlags)

            val uriStr = treeUri.toString()
            // Check if already added
            val existing = _sharedFolders.value.find { it.uriString == uriStr }
            if (existing != null) {
                Logger.i(LogTag.STORAGE, "Folder already shared: ${existing.displayName}")
                return existing
            }

            val docFile = DocumentFile.fromTreeUri(context, treeUri)
            val displayName = deriveDisplayName(treeUri, docFile?.name)

            val newFolder = HostSharedFolder(
                uriString = uriStr,
                displayName = displayName
            )

            val updatedList = _sharedFolders.value + newFolder
            _sharedFolders.value = updatedList
            saveFolders(context, updatedList)
            Logger.i(LogTag.STORAGE, "Added host shared folder: $displayName")

            scope.launch {
                rescanAllFoldersInternal(context)
            }
            return newFolder
        } catch (e: Exception) {
            Logger.e(LogTag.STORAGE, "Failed to add shared folder: ${e.message}")
            return null
        }
    }

    fun removeSharedFolder(context: Context, folderId: String) {
        val target = _sharedFolders.value.find { it.id == folderId }
        val updatedFolders = _sharedFolders.value.filter { it.id != folderId }
        _sharedFolders.value = updatedFolders
        saveFolders(context, updatedFolders)

        // Release persistable URI permission if possible
        target?.let {
            runCatching {
                val uri = Uri.parse(it.uriString)
                context.contentResolver.releasePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        }

        // Clean indexed files for this folder
        val updatedFiles = _virtualFiles.value.filter { it.sourceFolderId != folderId }
        _virtualFiles.value = updatedFiles
        saveFiles(context, updatedFiles)
        Logger.i(LogTag.STORAGE, "Removed host shared folder: ${target?.displayName}")
    }

    fun triggerRescan(context: Context) {
        scope.launch {
            rescanAllFoldersInternal(context)
        }
    }

    private suspend fun rescanAllFoldersInternal(context: Context) {
        if (_isScanning.value) return
        _isScanning.value = true
        Logger.i(LogTag.STORAGE, "Starting zero-copy scan of host shared folders...")

        try {
            val allIndexedFiles = mutableListOf<HostVirtualMediaFile>()
            val updatedFolders = _sharedFolders.value.map { folder ->
                val treeUri = Uri.parse(folder.uriString)
                val docDir = DocumentFile.fromTreeUri(context, treeUri)
                var count = 0
                var size = 0L

                if (docDir != null && docDir.exists() && docDir.isDirectory) {
                    val filesInTree = scanDocumentTree(context, docDir, folder.id)
                    allIndexedFiles.addAll(filesInTree)
                    count = filesInTree.size
                    size = filesInTree.sumOf { it.sizeBytes }
                }

                folder.copy(fileCount = count, totalSizeBytes = size)
            }

            withContext(Dispatchers.Main) {
                _sharedFolders.value = updatedFolders
                _virtualFiles.value = allIndexedFiles
            }

            saveFolders(context, updatedFolders)
            saveFiles(context, allIndexedFiles)
            Logger.i(LogTag.STORAGE, "Zero-copy scan complete. Indexed ${allIndexedFiles.size} media items.")
        } catch (e: Exception) {
            Logger.e(LogTag.STORAGE, "Error during host media scan: ${e.message}")
        } finally {
            _isScanning.value = false
        }
    }

    private fun scanDocumentTree(
        context: Context,
        dir: DocumentFile,
        sourceFolderId: String
    ): List<HostVirtualMediaFile> {
        val result = mutableListOf<HostVirtualMediaFile>()
        val files = dir.listFiles()

        for (file in files) {
            if (file.isDirectory) {
                // Recursive folder traversal (depth-first)
                result.addAll(scanDocumentTree(context, file, sourceFolderId))
            } else if (file.isFile) {
                val fileName = file.name ?: continue
                if (fileName.startsWith(".") || fileName.equals(".nomedia", ignoreCase = true)) continue

                val size = file.length()
                val mime = file.type ?: getMimeTypeFromExtension(fileName)
                val category = categorizeMedia(fileName, mime, size)
                val realPath = tryResolveRealPath(file.uri)

                result.add(
                    HostVirtualMediaFile(
                        id = UUID.nameUUIDFromBytes(file.uri.toString().toByteArray()).toString(),
                        name = fileName,
                        uriString = file.uri.toString(),
                        realFilePath = realPath,
                        mimeType = mime,
                        sizeBytes = size,
                        category = category,
                        lastModified = file.lastModified(),
                        sourceFolderId = sourceFolderId
                    )
                )
            }
        }
        return result
    }

    fun getFilesByCategory(category: String): List<HostVirtualMediaFile> {
        if (!_isSharingEnabled.value) return emptyList()
        return _virtualFiles.value.filter { it.category.equals(category, ignoreCase = true) }
    }

    fun getVirtualFileById(id: String): HostVirtualMediaFile? {
        return _virtualFiles.value.find { it.id == id }
    }

    fun openInputStream(context: Context, file: HostVirtualMediaFile): InputStream? {
        return try {
            if (!file.realFilePath.isNullOrBlank()) {
                val directFile = File(file.realFilePath)
                if (directFile.exists() && directFile.canRead()) {
                    return directFile.inputStream()
                }
            }
            context.contentResolver.openInputStream(Uri.parse(file.uriString))
        } catch (e: Exception) {
            Logger.e(LogTag.STORAGE, "Failed to open stream for ${file.name}: ${e.message}")
            null
        }
    }

    fun getDirectFile(file: HostVirtualMediaFile): File? {
        if (!file.realFilePath.isNullOrBlank()) {
            val f = File(file.realFilePath)
            if (f.exists() && f.canRead()) return f
        }
        return null
    }

    private fun categorizeMedia(fileName: String, mime: String, sizeBytes: Long): String {
        val lower = fileName.lowercase(Locale.ROOT)
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)

        return when {
            // Video / Movies
            mime.startsWith("video/") || ext in listOf("mp4", "mkv", "avi", "mov", "webm", "flv", "m4v", "ts", "wmv") -> {
                // If larger than 300MB or has cinematic naming tags, categorize as Movies, else Videos
                if (sizeBytes > 300 * 1024 * 1024 ||
                    lower.contains("1080p") || lower.contains("720p") || lower.contains("2160p") ||
                    lower.contains("bluray") || lower.contains("webrip") || lower.contains("x264") ||
                    lower.contains("hevc") || lower.contains("movie")
                ) {
                    "Movies"
                } else {
                    "Videos"
                }
            }
            // Music
            mime.startsWith("audio/") || ext in listOf("mp3", "flac", "wav", "aac", "m4a", "ogg", "wma", "opus") -> "Music"
            // Photos
            mime.startsWith("image/") || ext in listOf("jpg", "jpeg", "png", "webp", "gif", "heic", "bmp", "svg") -> "Photos"
            // Documents
            mime.startsWith("text/") || mime.contains("pdf") || mime.contains("document") ||
            ext in listOf("pdf", "epub", "doc", "docx", "txt", "mobi", "xlsx", "pptx", "md", "csv") -> "Documents"
            else -> "Others"
        }
    }

    private fun getMimeTypeFromExtension(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return when (ext) {
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "avi" -> "video/x-msvideo"
            "webm" -> "video/webm"
            "mp3" -> "audio/mpeg"
            "flac" -> "audio/flac"
            "wav" -> "audio/wav"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "pdf" -> "application/pdf"
            "txt" -> "text/plain"
            else -> "application/octet-stream"
        }
    }

    private fun deriveDisplayName(treeUri: Uri, docName: String?): String {
        if (!docName.isNullOrBlank()) return docName
        val path = treeUri.path ?: return "Storage Folder"
        return when {
            path.contains("/primary:") -> path.substringAfter("/primary:").replace("/", " / ")
            path.contains(":") -> path.substringAfter(":").replace("/", " / ")
            else -> "Device Folder"
        }
    }

    private fun tryResolveRealPath(uri: Uri): String? {
        val path = uri.path ?: return null
        return when {
            path.contains("/primary:") -> "/storage/emulated/0/${path.substringAfter("/primary:")}"
            path.startsWith("/storage/") -> path
            else -> null
        }
    }

    private fun saveFolders(context: Context, list: List<HostSharedFolder>) {
        val prefs = context.getSharedPreferences("remote_media_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("host_shared_folders_json", gson.toJson(list)).apply()
    }

    private fun saveFiles(context: Context, list: List<HostVirtualMediaFile>) {
        val prefs = context.getSharedPreferences("remote_media_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("host_virtual_files_json", gson.toJson(list)).apply()
    }
}
