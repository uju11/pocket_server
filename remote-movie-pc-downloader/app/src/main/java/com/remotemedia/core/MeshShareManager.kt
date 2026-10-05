package com.remotemedia.core

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * Client peer device OS/Platform type.
 */
enum class MeshPeerDeviceType(val label: String, val icon: String) {
    IOS("iOS (iPhone/iPad)", "📱"),
    ANDROID("Android Device", "🤖"),
    WINDOWS("Windows PC", "💻"),
    MACOS("macOS", "🍎"),
    LINUX("Linux Workstation", "🐧"),
    BROWSER("Web Browser Peer", "🌐"),
    OTHER("Network Peer", "📡")
}

/**
 * A client device registered as a peer in the PocketNode mesh.
 */
data class MeshPeerNode(
    val id: String = UUID.randomUUID().toString(),
    val deviceName: String,
    val deviceType: MeshPeerDeviceType = MeshPeerDeviceType.OTHER,
    val ipAddress: String,
    val port: Int = 8085,
    val authKey: String = "",
    val sharedFolders: List<String> = emptyList(),
    var lastHeartbeat: Long = System.currentTimeMillis(),
    var isOnline: Boolean = true,
    var fileCount: Int = 0,
    var totalSizeBytes: Long = 0L
)

/**
 * A media file hosted on a client mesh peer device and advertised to PocketNode.
 */
data class MeshVirtualMediaFile(
    val id: String = UUID.randomUUID().toString(),
    val peerNodeId: String,
    val peerNodeName: String,
    val name: String,
    val category: String, // Movies, Music, Videos, Photos, Documents, Others
    val mimeType: String,
    val sizeBytes: Long,
    val streamUrl: String, // Direct HTTP streaming URL hosted by peer or peer endpoint
    val relativePath: String = "",
    val lastModified: Long = System.currentTimeMillis()
)

/**
 * Milestone 6: Cross-Device Client Mesh & Peer Sharing Manager.
 *
 * Coordinates peer client nodes (iOS, Windows PC, Mac, Android, Web Browsers) that share
 * their local media folders directly into the PocketNode server network.
 */
object MeshShareManager {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val gson = Gson()
    private const val PREFS_NAME = "pocketnode_mesh_prefs"
    private const val KEY_PEERS = "mesh_peers"
    private const val KEY_MESH_ENABLED = "mesh_enabled"
    private const val HEARTBEAT_TIMEOUT_MS = 60_000L // 60s timeout before marking peer offline

    private val _isMeshEnabled = MutableStateFlow(true)
    val isMeshEnabled: StateFlow<Boolean> = _isMeshEnabled.asStateFlow()

    private val _peers = MutableStateFlow<List<MeshPeerNode>>(emptyList())
    val peers: StateFlow<List<MeshPeerNode>> = _peers.asStateFlow()

    private val _peerFiles = MutableStateFlow<List<MeshVirtualMediaFile>>(emptyList())
    val peerFiles: StateFlow<List<MeshVirtualMediaFile>> = _peerFiles.asStateFlow()

    private val _meshPeerCount = MutableStateFlow(0)
    val meshPeerCount: StateFlow<Int> = _meshPeerCount.asStateFlow()

    private val _meshFileCount = MutableStateFlow(0)
    val meshFileCount: StateFlow<Int> = _meshFileCount.asStateFlow()

    private var appContext: Context? = null
    private var heartbeatJob: Job? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        loadPersistedState(context)
        startHeartbeatMonitor()
        Logger.i(LogTag.STORAGE, "MeshShareManager initialized. Active peers: ${_peers.value.size}")
    }

    fun setMeshEnabled(context: Context, enabled: Boolean) {
        _isMeshEnabled.value = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_MESH_ENABLED, enabled)
            .apply()

        if (!enabled) {
            // Temporarily clear active peer files from catalog when mesh is disabled
            _peerFiles.value = emptyList()
            _meshFileCount.value = 0
        } else {
            recalculateAggregates()
        }
        Logger.i(LogTag.STORAGE, "Mesh sharing enabled toggled: $enabled")
    }

    /**
     * Registers or updates a client peer node and its advertised files.
     */
    fun registerOrUpdatePeer(
        peer: MeshPeerNode,
        files: List<MeshVirtualMediaFile>
    ): Boolean {
        if (!_isMeshEnabled.value) return false

        val updatedPeers = _peers.value.toMutableList()
        val index = updatedPeers.indexOfFirst { it.id == peer.id || (it.ipAddress == peer.ipAddress && it.deviceName == peer.deviceName) }

        val activePeer = peer.copy(
            lastHeartbeat = System.currentTimeMillis(),
            isOnline = true,
            fileCount = files.size,
            totalSizeBytes = files.sumOf { it.sizeBytes }
        )

        if (index >= 0) {
            updatedPeers[index] = activePeer
        } else {
            updatedPeers.add(activePeer)
        }

        _peers.value = updatedPeers
        _meshPeerCount.value = updatedPeers.count { it.isOnline }

        // Update file catalog for this peer
        val currentFiles = _peerFiles.value.filter { it.peerNodeId != activePeer.id }.toMutableList()
        currentFiles.addAll(files.map { it.copy(peerNodeId = activePeer.id, peerNodeName = activePeer.deviceName) })
        _peerFiles.value = currentFiles
        _meshFileCount.value = currentFiles.size

        persistPeers()
        Logger.i(
            LogTag.STORAGE,
            "Mesh peer registered/updated: ${activePeer.deviceName} (${activePeer.deviceType.label}) with ${files.size} files."
        )
        return true
    }

    /**
     * Updates peer heartbeat timestamp to keep it online.
     */
    fun updateHeartbeat(peerId: String): Boolean {
        val updatedPeers = _peers.value.map {
            if (it.id == peerId) {
                it.copy(lastHeartbeat = System.currentTimeMillis(), isOnline = true)
            } else {
                it
            }
        }
        _peers.value = updatedPeers
        _meshPeerCount.value = updatedPeers.count { it.isOnline }
        return updatedPeers.any { it.id == peerId }
    }

    /**
     * Replaces the advertised media file manifest for a specific peer.
     */
    fun updatePeerFiles(peerId: String, files: List<MeshVirtualMediaFile>) {
        val peer = _peers.value.find { it.id == peerId } ?: return
        val currentFiles = _peerFiles.value.filter { it.peerNodeId != peerId }.toMutableList()
        currentFiles.addAll(files.map { it.copy(peerNodeId = peerId, peerNodeName = peer.deviceName) })
        _peerFiles.value = currentFiles
        _meshFileCount.value = currentFiles.size

        val updatedPeers = _peers.value.map {
            if (it.id == peerId) {
                it.copy(
                    fileCount = files.size,
                    totalSizeBytes = files.sumOf { f -> f.sizeBytes },
                    lastHeartbeat = System.currentTimeMillis(),
                    isOnline = true
                )
            } else it
        }
        _peers.value = updatedPeers
        persistPeers()
    }

    /**
     * Removes a peer node and unpublishes all of its shared files.
     */
    fun removePeer(peerId: String) {
        val removed = _peers.value.find { it.id == peerId }
        _peers.value = _peers.value.filter { it.id != peerId }
        _peerFiles.value = _peerFiles.value.filter { it.peerNodeId != peerId }
        _meshPeerCount.value = _peers.value.count { it.isOnline }
        _meshFileCount.value = _peerFiles.value.size
        persistPeers()

        if (removed != null) {
            Logger.i(LogTag.STORAGE, "Removed mesh peer: ${removed.deviceName} (${removed.ipAddress})")
        }
    }

    /**
     * Clears all registered peers and their files.
     */
    fun clearAllPeers() {
        _peers.value = emptyList()
        _peerFiles.value = emptyList()
        _meshPeerCount.value = 0
        _meshFileCount.value = 0
        persistPeers()
        Logger.i(LogTag.STORAGE, "Cleared all mesh peers.")
    }

    fun getVirtualFileById(id: String): MeshVirtualMediaFile? {
        return _peerFiles.value.find { it.id == id }
    }

    fun getFilesByCategory(category: String): List<MeshVirtualMediaFile> {
        return _peerFiles.value.filter { it.category.equals(category, ignoreCase = true) }
    }

    /**
     * Opens an HTTP streaming input stream from the client peer node.
     * Supports streaming chunks directly to video clients (VLC, Jellyfin, Web).
     */
    fun openInputStream(file: MeshVirtualMediaFile): InputStream? {
        return try {
            val url = URL(file.streamUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 8000
            connection.readTimeout = 30000
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "PocketNode-MeshClient/1.0")

            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                connection.inputStream
            } else {
                Logger.e(LogTag.STORAGE, "Failed to stream mesh file ${file.name} from ${file.streamUrl}, code: $responseCode")
                null
            }
        } catch (e: Exception) {
            Logger.e(LogTag.STORAGE, "Exception opening stream for mesh file ${file.name}: ${e.message}")
            null
        }
    }

    /**
     * Determines category based on file extension and MIME type.
     */
    fun categorizeMedia(fileName: String, mimeType: String = ""): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when {
            ext in listOf("mp4", "mkv", "avi", "mov", "wmv", "webm", "m4v", "flv", "ts") || mimeType.startsWith("video/") -> {
                if (ext in listOf("mp4", "mkv", "avi", "mov") || fileName.contains("1080p", true) || fileName.contains("720p", true) || fileName.contains("4k", true) || fileName.contains("2160p", true)) {
                    "Movies"
                } else {
                    "Videos"
                }
            }
            ext in listOf("mp3", "flac", "m4a", "aac", "wav", "ogg", "opus") || mimeType.startsWith("audio/") -> "Music"
            ext in listOf("jpg", "jpeg", "png", "webp", "gif", "heic", "bmp") || mimeType.startsWith("image/") -> "Photos"
            ext in listOf("pdf", "epub", "txt", "doc", "docx", "mobi") || mimeType.startsWith("text/") || mimeType.contains("pdf") -> "Documents"
            else -> "Others"
        }
    }

    private fun startHeartbeatMonitor() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(15_000L) // Check every 15s
                val now = System.currentTimeMillis()
                var stateChanged = false

                val updatedPeers = _peers.value.map { peer ->
                    val isStillOnline = (now - peer.lastHeartbeat) < HEARTBEAT_TIMEOUT_MS
                    if (peer.isOnline != isStillOnline) {
                        stateChanged = true
                        peer.copy(isOnline = isStillOnline)
                    } else {
                        peer
                    }
                }

                if (stateChanged) {
                    _peers.value = updatedPeers
                    _meshPeerCount.value = updatedPeers.count { it.isOnline }

                    // Only expose files from currently online peers
                    val onlinePeerIds = updatedPeers.filter { it.isOnline }.map { it.id }.toSet()
                    val activeFiles = _peerFiles.value.filter { it.peerNodeId in onlinePeerIds }
                    _meshFileCount.value = activeFiles.size
                }
            }
        }
    }

    private fun persistPeers() {
        val ctx = appContext ?: return
        try {
            val json = gson.toJson(_peers.value)
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_PEERS, json)
                .apply()
        } catch (e: Exception) {
            Logger.e(LogTag.STORAGE, "Failed to persist mesh peers: ${e.message}")
        }
    }

    private fun loadPersistedState(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            _isMeshEnabled.value = prefs.getBoolean(KEY_MESH_ENABLED, true)
            val json = prefs.getString(KEY_PEERS, null)
            if (!json.isNullOrEmpty()) {
                val type = object : TypeToken<List<MeshPeerNode>>() {}.type
                val loadedPeers: List<MeshPeerNode> = gson.fromJson(json, type) ?: emptyList()
                // Mark loaded peers initially offline until they check in with heartbeat
                _peers.value = loadedPeers.map { it.copy(isOnline = false) }
                _meshPeerCount.value = 0
            }
        } catch (e: Exception) {
            Logger.e(LogTag.STORAGE, "Failed to load mesh peers: ${e.message}")
        }
    }

    private fun recalculateAggregates() {
        _meshPeerCount.value = _peers.value.count { it.isOnline }
        _meshFileCount.value = _peerFiles.value.size
    }
}
