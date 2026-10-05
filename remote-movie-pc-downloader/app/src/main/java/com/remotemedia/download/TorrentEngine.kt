package com.remotemedia.download

import android.content.Context
import com.remotemedia.core.FederatedMediaManager
import com.remotemedia.core.LogTag
import com.remotemedia.core.Logger
import com.remotemedia.core.StorageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.libtorrent4j.AlertListener
import org.libtorrent4j.Priority
import org.libtorrent4j.SessionManager
import org.libtorrent4j.TorrentInfo
import org.libtorrent4j.alerts.Alert
import java.io.File

data class DownloadItem(
    val id: String,
    val name: String,
    val source: String,   // "TORRENT", "TELEGRAM", "HTTP"
    val sizeLabel: String,
    val progressPct: Int, // 0-100, 100 = complete
    val speedLabel: String = "",
    val isPaused: Boolean = false,
    val seeds: Int = 0,
    val totalSeeds: Int = 0,
    val peers: Int = 0,
    val totalPeers: Int = 0,
    val downSpeed: String = "0 KB/s",
    val upSpeed: String = "0 KB/s",
    val eta: String = "--",
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val shareRatio: Float = 0.0f,
    val savePath: String = "",
    val hash: String = "",
    val magnetUri: String = "",
    val remoteUrl: String = "",
    val fileId: String = ""
)

object TorrentEngine {

    private var sessionManager: SessionManager? = null
    private val engineJob = SupervisorJob()
    private val engineScope = CoroutineScope(Dispatchers.IO + engineJob)
    private var isInitialized = false
    private val activeMagnets = mutableListOf<String>()

    // Download stats observable by UI
    private val _downloads = MutableStateFlow<List<DownloadItem>>(emptyList())
    val downloads: StateFlow<List<DownloadItem>> = _downloads.asStateFlow()

    val activeCount  get() = _downloads.value.count { it.progressPct < 100 }
    val completedCount get() = _downloads.value.count { it.progressPct == 100 }

    fun init(context: Context) {
        if (isInitialized && sessionManager != null) return

        try {
            val manager = SessionManager()
            val settings = org.libtorrent4j.SettingsPack()
            settings.setEnableDht(true)
            settings.setEnableLsd(true)
            settings.setDhtBootstrapNodes(
                "router.bittorrent.com:6881,dht.transmissionbt.com:6881,router.utorrent.com:6881,dht.libtorrent.org:25401"
            )
            settings.listenInterfaces("0.0.0.0:6881,[::]:6881")
            settings.activeDownloads(25)
            settings.connectionsLimit(350)
            settings.alertQueueSize(5000)

            manager.addListener(object : AlertListener {
                override fun types(): IntArray? = null

                override fun alert(alert: Alert<*>) {
                    val msg = alert.message()
                    if (msg.contains("added", true) || msg.contains("finished", true) || msg.contains("error", true)) {
                        Logger.i(LogTag.TORRENT, "Torrent Alert: $msg")
                    }
                }
            })

            val params = org.libtorrent4j.SessionParams(settings)
            manager.start(params)

            if (!manager.isDhtRunning) {
                try {
                    manager.startDht()
                    Logger.i(LogTag.TORRENT, "DHT network started for peer discovery.")
                } catch (dhtErr: Throwable) {
                    Logger.w(LogTag.TORRENT, "DHT start warning: ${dhtErr.message}")
                }
            }

            sessionManager = manager
            isInitialized = true
            Logger.i(LogTag.TORRENT, "Torrent Engine initialized with libtorrent4j & DHT swarm bootstrap.")

            // Start periodic progress monitoring
            startProgressMonitor()
        } catch (e: Throwable) {
            isInitialized = false
            sessionManager = null
            Logger.w(LogTag.TORRENT, "Torrent Engine native C++ library not available on this device: ${e.message}")
        }
    }

    fun downloadMagnet(
        magnetUri: String,
        title: String? = null,
        expectedSize: String? = null,
        saveDir: File = StorageManager.getMoviesDir()
    ) {
        if (!isInitialized || sessionManager == null) {
            Logger.w(LogTag.TORRENT, "Torrent Engine is disabled or not initialized.")
            return
        }

        engineScope.launch {
            try {
                val session = sessionManager ?: return@launch

                val hashMatch = Regex("urn:btih:([a-zA-Z0-9]+)", RegexOption.IGNORE_CASE).find(magnetUri)
                val hashFromUri = hashMatch?.groupValues?.getOrNull(1)?.lowercase() ?: ""

                val dnFromUri = try {
                    val rawDn = magnetUri.substringAfter("dn=", "").substringBefore("&")
                    if (rawDn.isNotBlank()) {
                        java.net.URLDecoder.decode(rawDn.replace("+", " "), "UTF-8").trim()
                    } else ""
                } catch (_: Exception) { "" }

                val cleanTitle = title?.take(80)?.trim()?.ifEmpty { null }
                val hasRealTitle = cleanTitle != null && !cleanTitle.equals("Torrent Download", ignoreCase = true)

                val displayName = if (hasRealTitle) cleanTitle!!
                    else if (dnFromUri.isNotBlank()) dnFromUri
                    else "Torrent_${hashFromUri.take(8).ifEmpty { System.currentTimeMillis().toString() }}"

                val itemId = hashFromUri.ifEmpty { "torrent_${System.currentTimeMillis()}" }

                if (!saveDir.exists()) saveDir.mkdirs()

                // Try parsing augmented magnet first, then fallback to original raw magnet
                val augmentedUri = TorrentTrackersManager.augmentMagnet(magnetUri)
                val ec = org.libtorrent4j.swig.error_code()
                var params = org.libtorrent4j.swig.libtorrent.parse_magnet_uri(augmentedUri, ec)

                if (ec.value() != 0 || params == null) {
                    Logger.w(LogTag.TORRENT, "Augmented magnet parse note: ${ec.message()}, falling back to raw magnet URI")
                    ec.clear()
                    params = org.libtorrent4j.swig.libtorrent.parse_magnet_uri(magnetUri, ec)
                }

                if (ec.value() != 0 || params == null) {
                    Logger.e(LogTag.TORRENT, "Failed to parse magnet URI: ${ec.message()}")
                    return@launch
                }

                params.setSave_path(saveDir.absolutePath)
                if (params.getName().isNullOrEmpty()) {
                    params.setName(displayName)
                }
                params.setFlags(params.getFlags())

                // Synchronously add torrent into session so we know immediately if it succeeded
                val swigSession = session.swig()
                val addEc = org.libtorrent4j.swig.error_code()
                val swigHandle = swigSession.add_torrent(params, addEc)

                if (addEc.value() != 0) {
                    Logger.w(LogTag.TORRENT, "add_torrent warning: ${addEc.message()}")
                } else {
                    Logger.i(LogTag.TORRENT, "add_torrent successful for: $displayName (hash: $hashFromUri)")
                }

                val th = if (swigHandle != null && swigHandle.is_valid()) {
                    org.libtorrent4j.TorrentHandle(swigHandle)
                } else if (hashFromUri.length == 40) {
                    try { session.find(org.libtorrent4j.Sha1Hash.parseHex(hashFromUri)) } catch (_: Exception) { null }
                } else null

                if (th != null && th.isValid) {
                    th.resume()
                    th.forceReannounce()
                    th.forceDHTAnnounce()
                    for (trUrl in TorrentTrackersManager.DEFAULT_TRACKERS) {
                        try {
                            th.addTracker(org.libtorrent4j.AnnounceEntry(trUrl))
                        } catch (_: Exception) {}
                    }
                    Logger.i(LogTag.TORRENT, "TorrentHandle active & announced for: $displayName")
                }

                activeMagnets.add(augmentedUri)
                val resolvedHash = if (th != null && th.isValid) {
                    try { th.infoHash().toHex().lowercase() } catch (_: Exception) { hashFromUri }
                } else hashFromUri
                val realItemId = resolvedHash.ifEmpty { itemId }

                val existing = _downloads.value.find {
                    it.id == realItemId || it.id == itemId || (resolvedHash.isNotBlank() && it.hash.equals(resolvedHash, ignoreCase = true))
                }

                if (existing != null) {
                    val resolvedName = if (existing.name.isNotBlank() && !existing.name.startsWith("Torrent_", ignoreCase = true) && !existing.name.equals("Torrent Download", ignoreCase = true)) existing.name else displayName
                    _downloads.value = _downloads.value.map {
                        if (it.id == existing.id) it.copy(
                            id = realItemId,
                            name = resolvedName,
                            hash = resolvedHash,
                            savePath = saveDir.absolutePath,
                            magnetUri = magnetUri,
                            speedLabel = if (it.progressPct >= 100) "COMPLETED" else "SEARCHING SWARM"
                        ) else it
                    }
                } else {
                    val item = DownloadItem(
                        id = realItemId,
                        name = displayName,
                        source = "TORRENT",
                        sizeLabel = expectedSize ?: "...",
                        progressPct = 0,
                        speedLabel = "SEARCHING SWARM",
                        hash = resolvedHash,
                        savePath = saveDir.absolutePath,
                        magnetUri = magnetUri
                    )
                    _downloads.value = _downloads.value + item
                }
                Logger.i(LogTag.TORRENT, "Added magnet to queue: $displayName (hash: $resolvedHash, size: $expectedSize)")
            } catch (e: Throwable) {
                Logger.e(LogTag.TORRENT, "Magnet download fatal error: ${e.message}")
            }
        }
    }

    fun getSessionManager(): SessionManager? = sessionManager
    fun isEngineActive(): Boolean = isInitialized && sessionManager != null

    fun getOrAddTorrentForStreaming(
        magnetUri: String,
        title: String? = null,
        saveDir: File = StorageManager.getMoviesDir()
    ): org.libtorrent4j.TorrentHandle? {
        if (!isInitialized || sessionManager == null) {
            Logger.w(LogTag.TORRENT, "Torrent Engine is disabled or not initialized.")
            return null
        }
        val session = sessionManager ?: return null

        val hashMatch = Regex("urn:btih:([a-zA-Z0-9]+)", RegexOption.IGNORE_CASE).find(magnetUri)
        val hashFromUri = hashMatch?.groupValues?.getOrNull(1)?.lowercase() ?: ""

        // Check if handle already exists
        if (hashFromUri.length == 40) {
            val existing = try { session.find(org.libtorrent4j.Sha1Hash.parseHex(hashFromUri)) } catch (_: Exception) { null }
            if (existing != null && existing.isValid) {
                existing.resume()
                return existing
            }
        }

        val dnFromUri = magnetUri.substringAfter("dn=", "").substringBefore("&")
            .replace("+", " ")
            .replace("%20", " ")
            .take(60)

        val displayName = title?.take(60)?.ifEmpty { null }
            ?: dnFromUri.ifEmpty { "Torrent_${System.currentTimeMillis()}" }

        val itemId = hashFromUri.ifEmpty { "torrent_${System.currentTimeMillis()}" }
        if (!saveDir.exists()) saveDir.mkdirs()

        val augmentedUri = TorrentTrackersManager.augmentMagnet(magnetUri)
        val ec = org.libtorrent4j.swig.error_code()
        var params = org.libtorrent4j.swig.libtorrent.parse_magnet_uri(augmentedUri, ec)

        if (ec.value() != 0 || params == null) {
            ec.clear()
            params = org.libtorrent4j.swig.libtorrent.parse_magnet_uri(magnetUri, ec)
        }

        if (ec.value() != 0 || params == null) {
            Logger.e(LogTag.TORRENT, "Failed to parse magnet URI for streaming: ${ec.message()}")
            return null
        }

        params.setSave_path(saveDir.absolutePath)
        if (params.getName().isNullOrEmpty()) {
            params.setName(displayName)
        }
        params.setFlags(params.getFlags().or_(org.libtorrent4j.TorrentFlags.SEQUENTIAL_DOWNLOAD))

        val swigSession = session.swig()
        val addEc = org.libtorrent4j.swig.error_code()
        val swigHandle = swigSession.add_torrent(params, addEc)

        val th = if (swigHandle != null && swigHandle.is_valid()) {
            org.libtorrent4j.TorrentHandle(swigHandle)
        } else if (hashFromUri.length == 40) {
            try { session.find(org.libtorrent4j.Sha1Hash.parseHex(hashFromUri)) } catch (_: Exception) { null }
        } else null

        if (th != null && th.isValid) {
            th.resume()
            th.forceReannounce()
            th.forceDHTAnnounce()
            for (trUrl in TorrentTrackersManager.DEFAULT_TRACKERS) {
                try { th.addTracker(org.libtorrent4j.AnnounceEntry(trUrl)) } catch (_: Exception) {}
            }
            Logger.i(LogTag.TORRENT, "Streaming TorrentHandle active & sequential mode enabled for: $displayName")
        }

        val existing = _downloads.value.find {
            it.id == itemId || (hashFromUri.isNotBlank() && it.hash.equals(hashFromUri, ignoreCase = true))
        }
        if (existing == null) {
            val item = DownloadItem(
                id = itemId,
                name = displayName,
                source = "TORRENT",
                sizeLabel = "STREAMING",
                progressPct = 0,
                speedLabel = "INITIALIZING STREAM",
                hash = hashFromUri,
                savePath = saveDir.absolutePath,
                magnetUri = magnetUri
            )
            _downloads.value = _downloads.value + item
        }

        return th
    }

    fun downloadTorrentFile(
        file: File,
        saveDir: File = StorageManager.getMoviesDir()
    ) {
        if (!isInitialized || sessionManager == null) {
            Logger.w(LogTag.TORRENT, "Torrent Engine is disabled or not initialized.")
            return
        }

        engineScope.launch {
            try {
                val session = sessionManager ?: return@launch
                val ti = TorrentInfo(file)
                if (!saveDir.exists()) saveDir.mkdirs()
                session.download(ti, saveDir)

                val hash = ti.infoHash().toHex().lowercase()
                val name = ti.name().ifBlank { file.nameWithoutExtension }
                val size = ti.totalSize()

                val th = session.find(ti.infoHash())
                if (th != null && th.isValid) {
                    th.resume()
                    th.forceReannounce()
                    th.forceDHTAnnounce()
                    for (trUrl in TorrentTrackersManager.DEFAULT_TRACKERS) {
                        try { th.addTracker(org.libtorrent4j.AnnounceEntry(trUrl)) } catch (_: Exception) {}
                    }
                }

                val item = DownloadItem(
                    id = hash,
                    name = name,
                    source = "TORRENT",
                    sizeLabel = formatSize(size),
                    progressPct = 0,
                    speedLabel = "SEARCHING SWARM",
                    hash = hash,
                    savePath = saveDir.absolutePath,
                    totalBytes = size
                )
                _downloads.value = _downloads.value.filterNot { it.id == hash || it.hash.equals(hash, ignoreCase = true) } + item
                Logger.i(LogTag.TORRENT, "Added .torrent file to queue: $name (hash: $hash)")
            } catch (e: Throwable) {
                Logger.e(LogTag.TORRENT, "Failed to download torrent file: ${e.message}")
            }
        }
    }

    fun downloadTorrentBytes(
        bytes: ByteArray,
        displayName: String? = null,
        saveDir: File = StorageManager.getMoviesDir()
    ) {
        if (!isInitialized || sessionManager == null) {
            Logger.w(LogTag.TORRENT, "Torrent Engine is disabled or not initialized.")
            return
        }

        engineScope.launch {
            try {
                val session = sessionManager ?: return@launch
                val ti = TorrentInfo(bytes)
                if (!saveDir.exists()) saveDir.mkdirs()
                session.download(ti, saveDir)

                val hash = ti.infoHash().toHex().lowercase()
                val name = displayName?.ifBlank { null } ?: ti.name().ifBlank { "Torrent_${hash.take(8)}" }
                val size = ti.totalSize()

                val th = session.find(ti.infoHash())
                if (th != null && th.isValid) {
                    th.resume()
                    th.forceReannounce()
                    th.forceDHTAnnounce()
                    for (trUrl in TorrentTrackersManager.DEFAULT_TRACKERS) {
                        try { th.addTracker(org.libtorrent4j.AnnounceEntry(trUrl)) } catch (_: Exception) {}
                    }
                }

                val item = DownloadItem(
                    id = hash,
                    name = name,
                    source = "TORRENT",
                    sizeLabel = formatSize(size),
                    progressPct = 0,
                    speedLabel = "SEARCHING SWARM",
                    hash = hash,
                    savePath = saveDir.absolutePath,
                    totalBytes = size
                )
                _downloads.value = _downloads.value.filterNot { it.id == hash || it.hash.equals(hash, ignoreCase = true) } + item
                Logger.i(LogTag.TORRENT, "Added torrent bytes to queue: $name (hash: $hash)")
            } catch (e: Throwable) {
                Logger.e(LogTag.TORRENT, "Failed to download torrent bytes: ${e.message}")
            }
        }
    }

    fun downloadTorrentUrl(
        url: String,
        displayName: String? = null,
        saveDir: File = StorageManager.getMoviesDir()
    ) {
        engineScope.launch(Dispatchers.IO) {
            try {
                val cleanUrl = url.trim()
                if (cleanUrl.startsWith("magnet:", ignoreCase = true)) {
                    downloadMagnet(cleanUrl, displayName ?: "Torrent Download", saveDir = saveDir)
                    return@launch
                }

                val connection = java.net.URL(cleanUrl).openConnection() as java.net.HttpURLConnection
                connection.instanceFollowRedirects = true
                connection.connectTimeout = 12000
                connection.readTimeout = 20000
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                
                val redirectUrl = connection.getHeaderField("Location")
                if (!redirectUrl.isNullOrBlank() && redirectUrl.startsWith("magnet:", ignoreCase = true)) {
                    downloadMagnet(redirectUrl, displayName ?: "Torrent Download", saveDir = saveDir)
                    return@launch
                }

                val responseCode = connection.responseCode
                if (responseCode in 200..299) {
                    val bytes = connection.inputStream.use { it.readBytes() }
                    if (bytes.isNotEmpty()) {
                        val textSample = String(bytes.take(200).toByteArray(), Charsets.UTF_8).trim()
                        if (textSample.startsWith("magnet:", ignoreCase = true)) {
                            downloadMagnet(textSample, displayName ?: "Torrent Download", saveDir = saveDir)
                        } else {
                            val cd = connection.getHeaderField("Content-Disposition")
                            var filename = displayName
                            if (filename.isNullOrBlank() && !cd.isNullOrBlank()) {
                                val match = Regex("""filename=["']?([^"';]+)["']?""").find(cd)
                                filename = match?.groupValues?.getOrNull(1)
                            }
                            if (filename.isNullOrBlank()) {
                                filename = cleanUrl.substringAfterLast("/").substringBefore("?")
                            }
                            downloadTorrentBytes(bytes, filename, saveDir = saveDir)
                        }
                    }
                } else {
                    Logger.e(LogTag.TORRENT, "Failed to fetch torrent URL (HTTP $responseCode): $cleanUrl")
                }
            } catch (e: Throwable) {
                Logger.e(LogTag.TORRENT, "Error downloading torrent URL ($url): ${e.message}")
            }
        }
    }

    fun addTrackersToTorrent(hash: String, trackerUrls: List<String>) {
        if (!isInitialized || sessionManager == null) return
        val session = sessionManager ?: return
        try {
            val cleanHash = hash.trim().lowercase()
            val th = if (cleanHash.length == 40) {
                try { session.find(org.libtorrent4j.Sha1Hash.parseHex(cleanHash)) } catch (_: Exception) { null }
            } else null

            if (th != null && th.isValid) {
                var addedCount = 0
                for (url in trackerUrls) {
                    val cleanUrl = url.trim()
                    if (cleanUrl.isNotBlank() && (cleanUrl.startsWith("http://", ignoreCase = true) || cleanUrl.startsWith("https://", ignoreCase = true) || cleanUrl.startsWith("udp://", ignoreCase = true))) {
                        try {
                            th.addTracker(org.libtorrent4j.AnnounceEntry(cleanUrl))
                            addedCount++
                        } catch (_: Exception) {}
                    }
                }
                if (addedCount > 0) {
                    th.forceReannounce()
                    th.forceDHTAnnounce()
                    Logger.i(LogTag.TORRENT, "Added $addedCount trackers to torrent $cleanHash")
                }
            }
        } catch (e: Throwable) {
            Logger.w(LogTag.TORRENT, "Error adding trackers to torrent: ${e.message}")
        }
    }

    fun getTorrentFiles(hash: String): List<Map<String, Any>> {
        val cleanHash = hash.trim().lowercase()
        val session = sessionManager
        if (session != null && cleanHash.length == 40) {
            try {
                val th = session.find(org.libtorrent4j.Sha1Hash.parseHex(cleanHash))
                if (th != null && th.isValid && th.status().hasMetadata()) {
                    val ti = th.torrentFile()
                    if (ti != null) {
                        val fs = ti.files()
                        val numFiles = fs.numFiles()
                        val progresses = try { th.fileProgress() } catch (_: Exception) { null }
                        val result = mutableListOf<Map<String, Any>>()
                        for (i in 0 until numFiles) {
                            val name = fs.filePath(i).ifBlank { fs.fileName(i) }
                            val size = fs.fileSize(i)
                            val done = if (progresses != null && i < progresses.size) progresses[i] else 0L
                            val prog = if (size > 0) (done.toDouble() / size.toDouble()).coerceIn(0.0, 1.0) else 0.0
                            result.add(
                                mapOf(
                                    "index" to i,
                                    "name" to name,
                                    "size" to size,
                                    "progress" to prog,
                                    "priority" to 1,
                                    "is_seed" to (prog >= 1.0),
                                    "piece_range" to listOf(0, 1),
                                    "availability" to 1.0
                                )
                            )
                        }
                        if (result.isNotEmpty()) return result
                    }
                }
            } catch (e: Exception) {
                Logger.w(LogTag.TORRENT, "Error reading torrent files for $cleanHash: ${e.message}")
            }
        }
        val item = _downloads.value.find { it.hash.equals(hash, ignoreCase = true) || it.id.equals(hash, ignoreCase = true) }
        if (item != null) {
            return listOf(
                mapOf(
                    "index" to 0,
                    "name" to item.name,
                    "size" to item.totalBytes,
                    "progress" to (item.progressPct / 100.0),
                    "priority" to 1,
                    "is_seed" to (item.progressPct >= 100),
                    "piece_range" to listOf(0, 1),
                    "availability" to 1.0
                )
            )
        }
        return emptyList()
    }

    fun setFilePriority(hash: String, fileIndices: List<Int>, priority: Int) {
        val cleanHash = hash.trim().lowercase()
        val session = sessionManager ?: return
        if (cleanHash.length != 40) return
        try {
            val th = session.find(org.libtorrent4j.Sha1Hash.parseHex(cleanHash))
            if (th != null && th.isValid) {
                val prioEnum = when (priority) {
                    0 -> org.libtorrent4j.Priority.IGNORE
                    1 -> Priority.DEFAULT
                    7 -> Priority.TOP_PRIORITY
                    else -> Priority.DEFAULT
                }
                for (idx in fileIndices) {
                    try { th.filePriority(idx, prioEnum) } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            Logger.w(LogTag.TORRENT, "Error setting file priority: ${e.message}")
        }
    }

    fun toggleSequentialDownload(hash: String) {
        val cleanHash = hash.trim().lowercase()
        val session = sessionManager ?: return
        if (cleanHash.length != 40) return
        try {
            val th = session.find(org.libtorrent4j.Sha1Hash.parseHex(cleanHash))
            if (th != null && th.isValid) {
                val status = th.status()
                val isSeq = status.flags().and_(org.libtorrent4j.TorrentFlags.SEQUENTIAL_DOWNLOAD).non_zero()
                if (isSeq) {
                    th.unsetFlags(org.libtorrent4j.TorrentFlags.SEQUENTIAL_DOWNLOAD)
                } else {
                    th.setFlags(org.libtorrent4j.TorrentFlags.SEQUENTIAL_DOWNLOAD)
                }
            }
        } catch (_: Exception) {}
    }


    // Called by TelegramBotEngine when a download is initiated via bot
    fun recordTelegramDownload(
        name: String,
        sizeLabel: String = "?",
        id: String = "tg_${System.currentTimeMillis()}",
        remoteUrl: String = "",
        fileId: String = ""
    ): String {
        val existing = _downloads.value.find { it.id == id }
        if (existing == null) {
            val item = DownloadItem(
                id = id,
                name = name,
                source = "TELEGRAM",
                sizeLabel = sizeLabel,
                progressPct = 0,
                speedLabel = "STARTING...",
                remoteUrl = remoteUrl,
                fileId = fileId
            )
            _downloads.value = _downloads.value + item
        } else {
            _downloads.value = _downloads.value.map {
                if (it.id == id) it.copy(
                    remoteUrl = if (remoteUrl.isNotBlank()) remoteUrl else it.remoteUrl,
                    fileId = if (fileId.isNotBlank()) fileId else it.fileId,
                    sizeLabel = if (sizeLabel != "?" && sizeLabel.isNotBlank()) sizeLabel else it.sizeLabel
                ) else it
            }
        }
        return id
    }

    fun updateTelegramDownload(
        id: String,
        downloadedBytes: Long,
        totalBytes: Long,
        speedStr: String,
        etaStr: String,
        pct: Int
    ) {
        val wasIncomplete = _downloads.value.find { it.id == id }?.progressPct ?: 0 < 100
        _downloads.value = _downloads.value.map {
            if (it.id == id) {
                it.copy(
                    progressPct = pct,
                    downloadedBytes = downloadedBytes,
                    totalBytes = totalBytes,
                    downSpeed = speedStr,
                    eta = etaStr,
                    speedLabel = if (pct >= 100) "COMPLETED" else if (it.isPaused) "PAUSED" else if (speedStr.contains("RECONNECTING", true)) speedStr else "DOWNLOADING",
                    sizeLabel = if (totalBytes > 0) formatSize(totalBytes) else it.sizeLabel
                )
            } else it
        }
        if (pct >= 100 && wasIncomplete) {
            runCatching { FederatedMediaManager.refresh() }
        }
    }

    fun markComplete(id: String) {
        _downloads.value = _downloads.value.map { if (it.id == id) it.copy(progressPct = 100, speedLabel = "COMPLETED", isPaused = false) else it }
        runCatching { FederatedMediaManager.refresh() }
    }

    fun pauseDownload(id: String) {
        val item = _downloads.value.find { it.id == id }
        if (item?.source?.equals("TELEGRAM", ignoreCase = true) == true) {
            runCatching { TelegramBotEngine.pauseTelegramDownload(id) }
        } else {
            try {
                val hash = item?.hash?.ifBlank { id } ?: id
                if (hash.length == 40) {
                    val th = sessionManager?.find(org.libtorrent4j.Sha1Hash.parseHex(hash))
                    if (th != null && th.isValid) {
                        th.pause()
                    }
                }
            } catch (_: Exception) {}
        }
        _downloads.value = _downloads.value.map {
            if (it.id == id) it.copy(isPaused = true, speedLabel = "PAUSED") else it
        }
        Logger.i(LogTag.TORRENT, "Paused download: $id")
    }

    fun resumeDownload(id: String) {
        val item = _downloads.value.find { it.id == id }
        if (item != null && item.source.equals("TELEGRAM", ignoreCase = true)) {
            _downloads.value = _downloads.value.map {
                if (it.id == id) it.copy(isPaused = false, speedLabel = "RESUMING TELEGRAM...") else it
            }
            runCatching { TelegramBotEngine.resumeTelegramDownload(id) }
            Logger.i(LogTag.TELEGRAM, "Resumed Telegram download: $id")
            return
        }

        var reconnected = false
        if (item != null) {
            val hash = item.hash.ifBlank { id }
            if (hash.length == 40) {
                try {
                    val th = sessionManager?.find(org.libtorrent4j.Sha1Hash.parseHex(hash))
                    if (th != null && th.isValid) {
                        th.resume()
                        th.forceReannounce()
                        th.forceDHTAnnounce()
                        reconnected = true
                    }
                } catch (_: Exception) {}
            }
            // If torrent handle was missing from libtorrent session, re-enqueue it immediately!
            if (!reconnected && item.magnetUri.isNotBlank()) {
                val folder = if (item.savePath.isNotBlank()) File(item.savePath) else StorageManager.getMoviesDir()
                downloadMagnet(
                    magnetUri = item.magnetUri,
                    title = item.name,
                    expectedSize = item.sizeLabel,
                    saveDir = folder
                )
            }
        }
        _downloads.value = _downloads.value.map {
            if (it.id == id) it.copy(isPaused = false, speedLabel = "SEARCHING SWARM") else it
        }
        Logger.i(LogTag.TORRENT, "Resumed download: $id")
    }

    fun deleteDownload(id: String) {
        val item = _downloads.value.find { it.id == id }
        if (item != null) {
            val hash = item.hash.ifBlank { id }
            if (hash.length == 40) {
                try {
                    val th = sessionManager?.find(org.libtorrent4j.Sha1Hash.parseHex(hash))
                    if (th != null && th.isValid) {
                        sessionManager?.remove(th)
                    }
                } catch (_: Exception) {}
            }
        }
        _downloads.value = _downloads.value.filterNot { it.id == id }
        activeMagnets.removeAll { it.take(24) == id }
        Logger.i(LogTag.TORRENT, "Removed download from queue: $id")
    }

    fun clearCompleted() {
        _downloads.value = _downloads.value.filter { it.progressPct < 100 }
    }

    fun stop() {
        if (isInitialized && sessionManager != null) {
            try {
                sessionManager?.stop()
                sessionManager = null
                isInitialized = false
                activeMagnets.clear()
                Logger.i(LogTag.TORRENT, "Torrent Engine stopped.")
            } catch (e: Throwable) {
                Logger.e(LogTag.TORRENT, "Error stopping Torrent Engine: ${e.message}")
            }
        }
    }

    fun formatSpeed(bytesPerSec: Long): String {
        if (bytesPerSec <= 0) return "0 KB/s"
        val kb = bytesPerSec / 1024.0
        val mb = kb / 1024.0
        return if (mb >= 1.0) String.format(java.util.Locale.US, "%.1f MB/s", mb)
        else String.format(java.util.Locale.US, "%.0f KB/s", kb)
    }

    fun formatEta(remainingBytes: Long, bytesPerSec: Long): String {
        if (remainingBytes <= 0) return "0s"
        if (bytesPerSec <= 0) return "∞"
        val sec = remainingBytes / bytesPerSec
        val hours = sec / 3600
        val mins = (sec % 3600) / 60
        val s = sec % 60
        return when {
            hours > 0 -> "${hours}h ${mins}m"
            mins > 0 -> "${mins}m ${s}s"
            else -> "${s}s"
        }
    }

    fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(java.util.Locale.US, "%.2f GB", gb)
            mb >= 1.0 -> String.format(java.util.Locale.US, "%.1f MB", mb)
            kb >= 1.0 -> String.format(java.util.Locale.US, "%.0f KB", kb)
            else -> "$bytes B"
        }
    }

    private fun startProgressMonitor() {
        engineScope.launch {
            while (isInitialized) {
                try {
                    val session = sessionManager
                    if (session != null) {
                        val handlesVector = session.swig().get_torrents()
                        if (handlesVector != null && handlesVector.size > 0) {
                            for (i in 0 until handlesVector.size) {
                                val th = org.libtorrent4j.TorrentHandle(handlesVector.get(i))
                                if (!th.isValid) continue
                                val status = th.status()
                                val thHash = try { th.infoHash().toHex().lowercase() } catch (e: Exception) { "" }
                                val thName = try { (th.getName() ?: "").take(60) } catch (e: Exception) { "" }
                                val pct = (status.progress() * 100).toInt().coerceIn(0, 100)
                                val downRate = status.downloadPayloadRate().toLong()
                                val upRate = status.uploadPayloadRate().toLong()
                                val totalB = status.totalWanted()
                                val doneB = status.totalDone()
                                val seeds = status.numSeeds()
                                val totalSeeds = status.listSeeds()
                                val peers = status.numPeers()
                                val totalPeers = status.listPeers()
                                val isPaused = try { status.flags().and_(org.libtorrent4j.TorrentFlags.PAUSED).non_zero() } catch (e: Exception) { false }
                                val isFinished = status.isFinished() || pct == 100
                                val hasMeta = status.hasMetadata()

                                val ti = try { if (hasMeta) th.torrentFile() else null } catch (_: Exception) { null }
                                val tiName = ti?.name()?.take(80)?.trim().orEmpty()
                                val tiSize = ti?.totalSize() ?: 0L

                                val currentList = _downloads.value
                                val existing = currentList.find {
                                    (thHash.isNotBlank() && it.hash.equals(thHash, ignoreCase = true)) ||
                                    (thHash.isNotBlank() && it.id.equals(thHash, ignoreCase = true)) ||
                                    (tiName.isNotBlank() && it.name.equals(tiName, ignoreCase = true)) ||
                                    (thName.isNotBlank() && it.name.equals(thName, ignoreCase = true))
                                }

                                if (existing != null) {
                                    val finalTotal = if (tiSize > 0) tiSize else if (totalB > 0) totalB else existing.totalBytes
                                    val remB = if (finalTotal > doneB) finalTotal - doneB else 0L
                                    val resolvedSize = if (finalTotal > 0) formatSize(finalTotal) else existing.sizeLabel

                                    val isPlaceholder = existing.name.isBlank() ||
                                        existing.name.startsWith("Torrent_", ignoreCase = true) ||
                                        existing.name.equals("Torrent Download", ignoreCase = true) ||
                                        existing.name.startsWith("torrent_", ignoreCase = true) ||
                                        existing.name.matches(Regex("^[0-9a-fA-F]{32,40}$"))

                                    val realName = when {
                                        tiName.isNotBlank() && (isPlaceholder || existing.name != tiName) -> tiName
                                        thName.isNotBlank() && !thName.startsWith("Torrent_") && !thName.equals("Torrent Download", ignoreCase = true) -> thName
                                        else -> existing.name
                                    }

                                    val statusLabel = when {
                                        isPaused -> "PAUSED"
                                        isFinished -> "COMPLETED"
                                        !hasMeta -> if (peers > 0 || seeds > 0) "DOWNLOADING METADATA (${seeds}s / ${peers}p)" else "RESOLVING METADATA (DHT)"
                                        downRate > 0 -> "DOWNLOADING"
                                        seeds == 0 && peers == 0 -> if (totalSeeds > 0 || totalPeers > 0) "CONNECTING TO SWARM (${totalSeeds}s / ${totalPeers}p)" else "CONNECTING TO PEERS"
                                        else -> "FINDING SEEDS (${seeds} seeds / ${peers} peers)"
                                    }

                                    val updated = existing.copy(
                                        name = realName,
                                        hash = if (existing.hash.isBlank()) thHash else existing.hash,
                                        progressPct = pct,
                                        speedLabel = statusLabel,
                                        isPaused = isPaused,
                                        seeds = seeds,
                                        totalSeeds = totalSeeds,
                                        peers = peers,
                                        totalPeers = totalPeers,
                                        downSpeed = if (isFinished) "0 KB/s" else formatSpeed(downRate),
                                        upSpeed = formatSpeed(upRate),
                                        eta = if (isFinished) "Done" else formatEta(remB, downRate),
                                        downloadedBytes = doneB,
                                        totalBytes = finalTotal,
                                        sizeLabel = resolvedSize
                                    )
                                    val wasNotFinished = existing.progressPct < 100
                                    _downloads.value = currentList.map { if (it.id == existing.id) updated else it }
                                    if (isFinished && wasNotFinished) {
                                        runCatching { FederatedMediaManager.refresh() }
                                    }
                                } else if (thHash.isNotBlank()) {
                                    val finalTotal = if (tiSize > 0) tiSize else totalB
                                    val resolvedName = when {
                                        tiName.isNotBlank() -> tiName
                                        thName.isNotBlank() && !thName.startsWith("Torrent_") && !thName.equals("Torrent Download", ignoreCase = true) -> thName
                                        else -> "Torrent_${thHash.take(8)}"
                                    }
                                    val newItem = DownloadItem(
                                        id = thHash,
                                        hash = thHash,
                                        name = resolvedName,
                                        source = "TORRENT",
                                        sizeLabel = if (finalTotal > 0) formatSize(finalTotal) else "?",
                                        progressPct = pct,
                                        speedLabel = if (isFinished) "COMPLETED" else "DOWNLOADING",
                                        isPaused = isPaused,
                                        seeds = seeds,
                                        totalSeeds = totalSeeds,
                                        peers = peers,
                                        totalPeers = totalPeers,
                                        downSpeed = formatSpeed(downRate),
                                        upSpeed = formatSpeed(upRate),
                                        eta = if (isFinished) "Done" else formatEta(finalTotal - doneB, downRate),
                                        downloadedBytes = doneB,
                                        totalBytes = finalTotal
                                    )
                                    _downloads.value = currentList + newItem
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Ignore transient errors
                }
                delay(1500)
            }
        }
    }
}


