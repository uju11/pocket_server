package com.remotemedia.core

import android.content.Context
import com.remotemedia.download.TelegramBotEngine
import com.remotemedia.download.TelegramMediaCatalog
import com.remotemedia.services.JellyfinClusterManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * Origin badge for a federated media item — identifies which source a file came from.
 */
enum class MediaOrigin(val badge: String, val label: String) {
    SANDBOX("[SANDBOX]", "Sandbox"),        // Files stored in the PocketNode isolated storage directory
    HOST("[HOST]", "Host Phone"),            // Zero-copy virtual mount from the host phone's local storage
    SMB("[PC-SMB]", "PC/NAS SMB"),          // Mounted LAN Samba/SMB share (Windows PC, Mac, NAS)
    NFS("[NFS]", "NFS Share"),              // Mounted NFS share (Linux/NAS)
    MESH("[MESH]", "Client Mesh"),          // Cross-device peer client shared folders (iOS/PC/Client)
    REMOTE_JELLYFIN("[JELLYFIN]", "Remote Jellyfin"), // Upstream PC / NAS Jellyfin server nodes
    TELEGRAM("[TELEGRAM]", "Telegram Cloud"), // Saved and bot-forwarded Telegram cloud files
    TORRENT("[TORRENT]", "Torrent Swarm")     // Trending and on-demand torrent streams
}

/**
 * A unified media item that can originate from any of the available media sources.
 */
data class FederatedMediaItem(
    val id: String,                 // Globally unique ID across all sources
    val name: String,
    val displayName: String,        // Name without extension for UI display
    val category: String,           // Movies, Music, Videos, Photos, Documents, Others
    val mimeType: String,
    val sizeBytes: Long,
    val origin: MediaOrigin,
    val sourceId: String,           // The original file ID in its source manager
    val originLabel: String = ""    // e.g., hostname for SMB, folder name for Host, peer name for Mesh
)

object FederatedMediaManager {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Combined view mode toggle
    private val _combinedViewEnabled = MutableStateFlow(true)
    val combinedViewEnabled: StateFlow<Boolean> = _combinedViewEnabled.asStateFlow()

    // The unified federated library — all files from all sources, merged and deduplicated
    private val _federatedLibrary = MutableStateFlow<List<FederatedMediaItem>>(emptyList())
    val federatedLibrary: StateFlow<List<FederatedMediaItem>> = _federatedLibrary.asStateFlow()

    // Per-category convenience accessors
    private val _movies  = MutableStateFlow<List<FederatedMediaItem>>(emptyList())
    private val _music   = MutableStateFlow<List<FederatedMediaItem>>(emptyList())
    private val _videos  = MutableStateFlow<List<FederatedMediaItem>>(emptyList())
    private val _photos  = MutableStateFlow<List<FederatedMediaItem>>(emptyList())
    private val _docs    = MutableStateFlow<List<FederatedMediaItem>>(emptyList())
    private val _others  = MutableStateFlow<List<FederatedMediaItem>>(emptyList())

    val movies: StateFlow<List<FederatedMediaItem>> = _movies.asStateFlow()
    val music:  StateFlow<List<FederatedMediaItem>> = _music.asStateFlow()
    val videos: StateFlow<List<FederatedMediaItem>> = _videos.asStateFlow()
    val photos: StateFlow<List<FederatedMediaItem>> = _photos.asStateFlow()
    val docs:   StateFlow<List<FederatedMediaItem>> = _docs.asStateFlow()
    val others: StateFlow<List<FederatedMediaItem>> = _others.asStateFlow()

    // Source counts for the UI summary card
    private val _sandboxCount = MutableStateFlow(0)
    private val _hostCount    = MutableStateFlow(0)
    private val _networkCount = MutableStateFlow(0)
    private val _meshCount    = MutableStateFlow(0)
    private val _clusterCount = MutableStateFlow(0)
    val sandboxCount: StateFlow<Int> = _sandboxCount.asStateFlow()
    val hostCount:    StateFlow<Int> = _hostCount.asStateFlow()
    val networkCount: StateFlow<Int> = _networkCount.asStateFlow()
    val meshCount:    StateFlow<Int> = _meshCount.asStateFlow()
    val clusterCount: StateFlow<Int> = _clusterCount.asStateFlow()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences("remote_media_prefs", Context.MODE_PRIVATE)
        _combinedViewEnabled.value = prefs.getBoolean("federated_combined_view", true)

        // Observe all source flows and recompute the unified library whenever any changes
        scope.launch {
            combine(
                HostMediaManager.virtualFiles,
                NetworkShareManager.networkFiles,
                MeshShareManager.peerFiles,
                HostMediaManager.isSharingEnabled,
                MeshShareManager.isMeshEnabled
            ) { hostFiles, netFiles, meshFiles, hostEnabled, meshEnabled ->
                rebuildFederatedLibrary(hostFiles, netFiles, meshFiles, hostEnabled, meshEnabled)
            }.collect { }
        }

        // Observe remote Jellyfin cluster items & Telegram catalog items
        scope.launch {
            JellyfinClusterManager.remoteItems.collect { refresh() }
        }
        scope.launch {
            TelegramMediaCatalog.items.collect { refresh() }
        }
    }

    fun setCombinedViewEnabled(context: Context, enabled: Boolean) {
        _combinedViewEnabled.value = enabled
        context.getSharedPreferences("remote_media_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("federated_combined_view", enabled).apply()
        Logger.i(LogTag.STORAGE, "Federated Combined View toggled: $enabled")
    }

    /**
     * Rebuilds the complete federated library from all sources.
     */
    private fun rebuildFederatedLibrary(
        hostFiles: List<HostVirtualMediaFile>,
        netFiles: List<NetworkVirtualMediaFile>,
        meshFiles: List<MeshVirtualMediaFile>,
        hostEnabled: Boolean,
        meshEnabled: Boolean
    ) {
        val allItems = mutableListOf<FederatedMediaItem>()

        // ── Source 1: Sandbox files (from StorageManager) ───────────────────────
        val sandboxFiles = StorageManager.getFederatedSandboxFiles()
        for (f in sandboxFiles) {
            allItems.add(
                FederatedMediaItem(
                    id          = "sandbox_${f.id}",
                    name        = f.name,
                    displayName = f.name.substringBeforeLast('.'),
                    category    = f.category,
                    mimeType    = f.mimeType,
                    sizeBytes   = f.sizeBytes,
                    origin      = MediaOrigin.SANDBOX,
                    sourceId    = f.id,
                    originLabel = "Sandbox"
                )
            )
        }

        // ── Source 2: Host phone virtual mounts ──────────────────────────────────
        if (hostEnabled) {
            for (f in hostFiles) {
                allItems.add(
                    FederatedMediaItem(
                        id          = "host_${f.id}",
                        name        = f.name,
                        displayName = f.name.substringBeforeLast('.'),
                        category    = f.category,
                        mimeType    = f.mimeType,
                        sizeBytes   = f.sizeBytes,
                        origin      = MediaOrigin.HOST,
                        sourceId    = f.id,
                        originLabel = "Phone"
                    )
                )
            }
        }

        // ── Source 3: LAN network shares (SMB / NFS) ─────────────────────────────
        for (f in netFiles) {
            val origin = if (f.mimeType.contains("nfs", ignoreCase = true)) MediaOrigin.NFS else MediaOrigin.SMB
            allItems.add(
                FederatedMediaItem(
                    id          = "net_${f.id}",
                    name        = f.name,
                    displayName = f.name.substringBeforeLast('.'),
                    category    = f.category,
                    mimeType    = f.mimeType,
                    sizeBytes   = f.sizeBytes,
                    origin      = origin,
                    sourceId    = f.id,
                    originLabel = f.hostName
                )
            )
        }

        // ── Source 4: Cross-Device Client Mesh Shares ───────────────────────────
        if (meshEnabled) {
            for (f in meshFiles) {
                allItems.add(
                    FederatedMediaItem(
                        id          = "mesh_${f.id}",
                        name        = f.name,
                        displayName = f.name.substringBeforeLast('.'),
                        category    = f.category,
                        mimeType    = f.mimeType,
                        sizeBytes   = f.sizeBytes,
                        origin      = MediaOrigin.MESH,
                        sourceId    = f.id,
                        originLabel = f.peerNodeName
                    )
                )
            }
        }

        // ── Source 5: Upstream Jellyfin Server Nodes ──────────────────────────────
        val remoteJellyItems = JellyfinClusterManager.remoteItems.value
        for (f in remoteJellyItems) {
            allItems.add(
                FederatedMediaItem(
                    id          = "jelly_${f.id}",
                    name        = f.name,
                    displayName = f.name,
                    category    = f.category,
                    mimeType    = f.mimeType,
                    sizeBytes   = f.sizeBytes,
                    origin      = MediaOrigin.REMOTE_JELLYFIN,
                    sourceId    = f.id,
                    originLabel = f.serverName
                )
            )
        }

        // ── Source 6: Telegram Media Catalog ────────────────────────────────────
        val tgItems = TelegramMediaCatalog.items.value
        for (tg in tgItems) {
            allItems.add(
                FederatedMediaItem(
                    id          = "tg_${tg.fileId}",
                    name        = tg.fileName,
                    displayName = tg.cleanTitle,
                    category    = "Movies",
                    mimeType    = tg.mimeType,
                    sizeBytes   = tg.fileSize,
                    origin      = MediaOrigin.TELEGRAM,
                    sourceId    = tg.fileId,
                    originLabel = "Telegram (${tg.sizeFormatted})"
                )
            )
        }

        val deduplicated = deduplicateItems(allItems)

        _federatedLibrary.value = deduplicated
        _movies.value  = deduplicated.filter { it.category.equals("Movies",    ignoreCase = true) }
        _music.value   = deduplicated.filter { it.category.equals("Music",     ignoreCase = true) }
        _videos.value  = deduplicated.filter { it.category.equals("Videos",    ignoreCase = true) }
        _photos.value  = deduplicated.filter { it.category.equals("Photos",    ignoreCase = true) }
        _docs.value    = deduplicated.filter { it.category.equals("Documents", ignoreCase = true) }
        _others.value  = deduplicated.filter { it.category.equals("Others",    ignoreCase = true) }

        _sandboxCount.value = sandboxFiles.size
        _hostCount.value    = if (hostEnabled) hostFiles.size else 0
        _networkCount.value = netFiles.size
        _meshCount.value    = if (meshEnabled) meshFiles.size else 0
        _clusterCount.value = remoteJellyItems.size
    }

    private fun deduplicateItems(items: List<FederatedMediaItem>): List<FederatedMediaItem> {
        val priorityOrder = listOf(MediaOrigin.SANDBOX, MediaOrigin.TELEGRAM, MediaOrigin.HOST, MediaOrigin.SMB, MediaOrigin.NFS, MediaOrigin.MESH, MediaOrigin.REMOTE_JELLYFIN)
        val sorted = items.sortedBy { priorityOrder.indexOf(it.origin) }

        val seen = mutableListOf<FederatedMediaItem>()
        for (item in sorted) {
            val isDuplicate = seen.any { existing ->
                val sameBaseName = existing.name.substringBeforeLast('.').equals(
                    item.name.substringBeforeLast('.'), ignoreCase = true
                )
                val sizeClose = if (existing.sizeBytes > 0 && item.sizeBytes > 0) {
                    val ratio = item.sizeBytes.toDouble() / existing.sizeBytes.toDouble()
                    ratio in 0.95..1.05
                } else {
                    false
                }
                sameBaseName && sizeClose
            }
            if (!isDuplicate) {
                seen.add(item)
            }
        }
        return seen
    }

    fun refresh() {
        val hostFiles = HostMediaManager.virtualFiles.value
        val netFiles = NetworkShareManager.networkFiles.value
        val meshFiles = MeshShareManager.peerFiles.value
        val hostEnabled = HostMediaManager.isSharingEnabled.value
        val meshEnabled = MeshShareManager.isMeshEnabled.value
        rebuildFederatedLibrary(hostFiles, netFiles, meshFiles, hostEnabled, meshEnabled)
    }

    fun getByCategory(category: String): List<FederatedMediaItem> {
        var items = _federatedLibrary.value.filter { it.category.equals(category, ignoreCase = true) }
        if (items.isEmpty()) {
            refresh()
            items = _federatedLibrary.value.filter { it.category.equals(category, ignoreCase = true) }
        }
        return items
    }

    fun getById(federatedId: String): FederatedMediaItem? {
        val found = _federatedLibrary.value.find { it.id == federatedId }
        if (found != null) return found
        refresh()
        val afterRefresh = _federatedLibrary.value.find { it.id == federatedId }
        if (afterRefresh != null) return afterRefresh

        val cleanId = federatedId.removePrefix("sandbox_").removePrefix("tg_")
        val sandboxFile = StorageManager.getSandboxFileById(cleanId) ?: findLocalFileByTitle(cleanId)
        if (sandboxFile != null) {
            val mime = StorageManager.getMimeFromExtension(sandboxFile.name)
            return FederatedMediaItem(
                id          = "sandbox_${UUID.nameUUIDFromBytes(sandboxFile.canonicalPath.toByteArray())}",
                name        = sandboxFile.name,
                displayName = sandboxFile.name.substringBeforeLast('.'),
                category    = "Movies",
                mimeType    = mime,
                sizeBytes   = sandboxFile.length(),
                origin      = MediaOrigin.SANDBOX,
                sourceId    = cleanId,
                originLabel = "Sandbox"
            )
        }
        return null
    }

    fun findLocalFileByTitle(titleOrId: String): File? {
        val base = runCatching { StorageManager.getBaseDir() }.getOrNull() ?: return null
        val decoded = try { java.net.URLDecoder.decode(titleOrId, "UTF-8") } catch (_: Exception) { titleOrId }
        val clean = decoded.removePrefix("sandbox_").removePrefix("tg_").removePrefix("local_").removePrefix("file_")
        val cleanNoExt = clean.substringBeforeLast('.')

        val searchDirs = (StorageManager.CURATED_CATEGORIES.map { StorageManager.getCategoryDir(it) } + StorageManager.getDownloadsDir() + base).distinctBy { it.canonicalPath }
        for (dir in searchDirs) {
            if (!dir.exists()) continue
            val found = dir.walkTopDown().firstOrNull { f ->
                f.isFile && !f.name.startsWith(".") && f.name != ".nomedia" &&
                        (f.name.equals(clean, ignoreCase = true) ||
                         f.nameWithoutExtension.equals(clean, ignoreCase = true) ||
                         f.nameWithoutExtension.equals(cleanNoExt, ignoreCase = true) ||
                         f.name.contains(cleanNoExt, ignoreCase = true))
            }
            if (found != null) return found
        }
        return null
    }

    /**
     * Returns the direct [File] for sandbox or downloaded Telegram items,
     * enabling zero-copy `respondFile()` instead of stream piping.
     */
    fun getDirectFile(item: FederatedMediaItem): File? {
        return when (item.origin) {
            MediaOrigin.SANDBOX -> {
                StorageManager.getSandboxFileById(item.sourceId)
                    ?: StorageManager.getSandboxFileById(item.name)
                    ?: StorageManager.getSandboxFileById(item.id)
                    ?: findLocalFileByTitle(item.name)
                    ?: findLocalFileByTitle(item.displayName)
            }
            MediaOrigin.TELEGRAM -> {
                // Check if the Telegram file was already downloaded to local movies directory
                StorageManager.getSandboxFileById(item.name)
                    ?: findLocalFileByTitle(item.name)
                    ?: findLocalFileByTitle(item.displayName)
            }
            else -> null
        }
    }

    /**
     * Opens an [InputStream] for any federated item.
     */
    fun openInputStream(context: Context, item: FederatedMediaItem, rangeHeader: String? = null): InputStream? {
        // 1. Try local file on disk first
        val directFile = getDirectFile(item)
        if (directFile != null && directFile.exists()) {
            return directFile.inputStream()
        }

        return when (item.origin) {
            MediaOrigin.SANDBOX -> {
                val sf = StorageManager.getSandboxFileById(item.sourceId)
                sf?.inputStream()
            }
            MediaOrigin.HOST -> {
                val hf = HostMediaManager.getVirtualFileById(item.sourceId)
                hf?.let { HostMediaManager.openInputStream(context, it) }
            }
            MediaOrigin.SMB, MediaOrigin.NFS -> {
                val nf = NetworkShareManager.getVirtualFileById(item.sourceId)
                nf?.let { NetworkShareManager.openInputStream(it) }
            }
            MediaOrigin.MESH -> {
                val mf = MeshShareManager.getVirtualFileById(item.sourceId)
                mf?.let { MeshShareManager.openInputStream(it) }
            }
            MediaOrigin.REMOTE_JELLYFIN -> {
                val rItem = JellyfinClusterManager.remoteItems.value.find { it.id == item.sourceId }
                rItem?.let { JellyfinClusterManager.openRemoteStream(it, rangeHeader) }
            }
            MediaOrigin.TELEGRAM -> {
                try {
                    val streamUrl = runBlocking {
                        TelegramBotEngine.getDownloadUrlForFile(item.sourceId)
                    }
                    if (streamUrl != null) {
                        val url = URL(streamUrl)
                        val conn = url.openConnection() as HttpURLConnection
                        conn.connectTimeout = 8000
                        conn.readTimeout = 20000
                        if (rangeHeader != null) {
                            conn.setRequestProperty("Range", rangeHeader)
                        }
                        conn.inputStream
                    } else null
                } catch (e: Exception) {
                    Logger.w(LogTag.STORAGE, "Telegram stream error: ${e.message}")
                    null
                }
            }
            MediaOrigin.TORRENT -> null
        }
    }
}
