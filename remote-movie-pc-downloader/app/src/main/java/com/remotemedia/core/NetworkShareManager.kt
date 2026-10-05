package com.remotemedia.core

import android.content.Context
import android.net.ConnectivityManager
import java.net.Inet4Address
import com.remotemedia.ServerForegroundService
import android.net.wifi.WifiManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.*
import java.util.concurrent.ConcurrentHashMap

data class DiscoveredShare(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val host: String,
    val port: Int = 445,
    val protocol: String = "SMB", // SMB, NFS
    val isMounted: Boolean = false,
    val lastSeenTimestamp: Long = System.currentTimeMillis()
)

data class MountedShare(
    val id: String = UUID.randomUUID().toString(),
    val displayName: String,
    val host: String,
    val port: Int = 445,
    val shareName: String,
    val username: String = "",
    val domain: String = "",
    val password: String = "",
    val protocol: String = "SMB",
    var fileCount: Int = 0,
    var totalSizeBytes: Long = 0L,
    val autoConnect: Boolean = true
)

data class NetworkVirtualMediaFile(
    val id: String = UUID.randomUUID().toString(),
    val shareId: String,
    val name: String,
    val relativePath: String,
    val category: String, // Movies, Music, Videos, Photos, Documents, Others
    val sizeBytes: Long,
    val mimeType: String,
    val hostName: String
)

object NetworkShareManager {

    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scanProgress = MutableStateFlow(0f)
    val scanProgress: StateFlow<Float> = _scanProgress.asStateFlow()

    private val _discoveredShares = MutableStateFlow<List<DiscoveredShare>>(emptyList())
    val discoveredShares: StateFlow<List<DiscoveredShare>> = _discoveredShares.asStateFlow()

    private val _mountedShares = MutableStateFlow<List<MountedShare>>(emptyList())
    val mountedShares: StateFlow<List<MountedShare>> = _mountedShares.asStateFlow()

    private val _networkFiles = MutableStateFlow<List<NetworkVirtualMediaFile>>(emptyList())
    val networkFiles: StateFlow<List<NetworkVirtualMediaFile>> = _networkFiles.asStateFlow()

    // Active disk shares cache for low-latency streaming
    private val activeDiskShares = ConcurrentHashMap<String, DiskShare>()
    private val activeClients = ConcurrentHashMap<String, SMBClient>()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences("remote_media_prefs", Context.MODE_PRIVATE)

        // Load saved mounted shares
        val mountedJson = prefs.getString("network_mounted_shares_json", null)
        if (!mountedJson.isNullOrBlank()) {
            runCatching {
                val type = object : TypeToken<List<MountedShare>>() {}.type
                val saved: List<MountedShare> = gson.fromJson(mountedJson, type)
                _mountedShares.value = saved
            }
        }

        val filesJson = prefs.getString("network_virtual_files_json", null)
        if (!filesJson.isNullOrBlank()) {
            runCatching {
                val type = object : TypeToken<List<NetworkVirtualMediaFile>>() {}.type
                val saved: List<NetworkVirtualMediaFile> = gson.fromJson(filesJson, type)
                _networkFiles.value = saved
            }
        }

        // Auto-reconnect previously mounted shares in the background
        if (_mountedShares.value.isNotEmpty()) {
            scope.launch {
                _mountedShares.value.forEach { share ->
                    if (share.autoConnect) {
                        mountShareInternal(context, share, isReconnect = true)
                    }
                }
            }
        }
    }

    /**
     * Scans local Wi-Fi /24 subnet for SMB (port 445/139), NFS (port 2049),
     * and Universal Plug and Play (UPnP / DLNA media servers e.g. Windows Media Player, NAS).
     */
    fun scanLocalSubnet(context: Context) {
        if (_isScanning.value) return
        _isScanning.value = true
        _scanProgress.value = 0f

        scope.launch {
            try {
                Logger.i(LogTag.STORAGE, "Starting LAN network shares & UPnP discovery scan...")
                val ownIps = getAllDeviceIpAddresses(context)
                val localIp = getDeviceIpAddress(context) ?: "192.168.1.1"
                val subnetBase = localIp.substringBeforeLast('.') + "."

                val discoveredList = mutableListOf<DiscoveredShare>()

                // 1. Parallel SSDP Multicast Probe (Universal Plug and Play / DLNA)
                val ssdpJob = launch {
                    val ssdpFound = scanSsdpUpnp(context, ownIps)
                    synchronized(discoveredList) {
                        val activeSrv = ServerForegroundService.ipAddressState.value
                        val boundHost = ServerForegroundService.boundHostState.value
                        ssdpFound.forEach { upnpShare ->
                            val isOwn = ownIps.contains(upnpShare.host) || 
                                        (activeSrv.isNotBlank() && upnpShare.host == activeSrv) ||
                                        (boundHost.isNotBlank() && upnpShare.host == boundHost) ||
                                        upnpShare.name.contains("PocketNode", ignoreCase = true) ||
                                        upnpShare.name.contains("RemoteMediaServer", ignoreCase = true)
                            if (!isOwn && discoveredList.none { it.host == upnpShare.host }) {
                                discoveredList.add(upnpShare)
                                Logger.i(LogTag.STORAGE, "Discovered UPnP/DLNA Server via SSDP: ${upnpShare.name} (${upnpShare.host})")
                            }
                        }
                    }
                }

                // 2. Subnet Port Sweep for SMB (445/139), NFS (2049), and UPnP HTTP (2869/10243/8200)
                val semaphore = Semaphore(25)
                val totalIps = 254
                var scannedCount = 0

                val jobs = (1..totalIps).map { hostNum ->
                    launch {
                        semaphore.withPermit {
                            val targetIp = "$subnetBase$hostNum"
                            // Skip discovering our own phone's IP and active servers
                            if (ownIps.contains(targetIp)) {
                                scannedCount++
                                _scanProgress.value = scannedCount.toFloat() / totalIps.toFloat()
                                return@withPermit
                            }

                            val smbOpen = isPortOpen(targetIp, 445, 300) || isPortOpen(targetIp, 139, 300)
                            val nfsOpen = !smbOpen && isPortOpen(targetIp, 2049, 300)
                            val upnpOpen = (isPortOpen(targetIp, 2869, 300) || isPortOpen(targetIp, 10243, 300) || isPortOpen(targetIp, 8200, 300) || isPortOpen(targetIp, 5001, 300))

                            if (smbOpen || nfsOpen || upnpOpen) {
                                val hostname = resolveHostName(targetIp)
                                val protocol = when {
                                    smbOpen -> "SMB"
                                    nfsOpen -> "NFS"
                                    else -> "UPnP / DLNA"
                                }
                                val isAlreadyMounted = _mountedShares.value.any { it.host == targetIp }

                                val share = DiscoveredShare(
                                    name = if (protocol == "UPnP / DLNA") "$hostname (UPnP Media Server)" else hostname,
                                    host = targetIp,
                                    port = when {
                                        smbOpen -> 445
                                        nfsOpen -> 2049
                                        else -> 2869
                                    },
                                    protocol = protocol,
                                    isMounted = isAlreadyMounted
                                )
                                synchronized(discoveredList) {
                                    if (discoveredList.none { it.host == targetIp && it.protocol == protocol }) {
                                        discoveredList.add(share)
                                    }
                                }
                                Logger.i(LogTag.STORAGE, "Discovered $protocol share: $hostname ($targetIp)")
                            }

                            scannedCount++
                            _scanProgress.value = scannedCount.toFloat() / totalIps.toFloat()
                        }
                    }
                }

                jobs.joinAll()
                ssdpJob.join()

                withContext(Dispatchers.Main) {
                    val currentOwnIps = getAllDeviceIpAddresses(context)
                    val activeSrv = ServerForegroundService.ipAddressState.value
                    val boundHost = ServerForegroundService.boundHostState.value
                    _discoveredShares.value = discoveredList
                        .filterNot { currentOwnIps.contains(it.host) }
                        .filterNot { activeSrv.isNotBlank() && it.host == activeSrv }
                        .filterNot { boundHost.isNotBlank() && it.host == boundHost }
                        .filterNot { it.name.contains("PocketNode", ignoreCase = true) || it.name.contains("RemoteMediaServer", ignoreCase = true) }
                        .sortedWith(
                            compareBy<DiscoveredShare> { it.isMounted }.reversed()
                                .thenBy { it.protocol }
                                .thenBy { it.name }
                        )
                }
                Logger.i(LogTag.STORAGE, "LAN scan complete. Found ${discoveredList.size} network storage targets.")
            } catch (e: Exception) {
                Logger.e(LogTag.STORAGE, "LAN share discovery failed: ${e.message}")
            } finally {
                _isScanning.value = false
                _scanProgress.value = 1f
            }
        }
    }

    /**
     * Broadcasts SSDP M-SEARCH over UDP port 1900 to discover UPnP/DLNA Media Servers.
     */
    private suspend fun scanSsdpUpnp(context: Context, ownIps: Set<String> = emptySet()): List<DiscoveredShare> = withContext(Dispatchers.IO) {
        val upnpList = mutableListOf<DiscoveredShare>()
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val multicastLock = wifiManager?.createMulticastLock("PocketNodeUpnpLock")?.apply {
            setReferenceCounted(true)
            acquire()
        }

        var socket: java.net.DatagramSocket? = null
        try {
            val group = InetAddress.getByName("239.255.255.250")
            val port = 1900
            val query = "M-SEARCH * HTTP/1.1\r\n" +
                    "HOST: 239.255.255.250:1900\r\n" +
                    "MAN: \"ssdp:discover\"\r\n" +
                    "MX: 2\r\n" +
                    "ST: ssdp:all\r\n\r\n"
            val sendData = query.toByteArray(Charsets.UTF_8)
            val sendPacket = java.net.DatagramPacket(sendData, sendData.size, group, port)

            socket = java.net.DatagramSocket().apply {
                broadcast = true
                soTimeout = 2500
                send(sendPacket)
            }

            val receiveBuffer = ByteArray(4096)
            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < 2500) {
                try {
                    val recvPacket = java.net.DatagramPacket(receiveBuffer, receiveBuffer.size)
                    socket.receive(recvPacket)
                    val response = String(recvPacket.data, 0, recvPacket.length, Charsets.UTF_8)
                    val senderIp = recvPacket.address.hostAddress?.substringBefore('%') ?: continue
                    if (ownIps.contains(senderIp)) continue // Ignore own device's UPnP server response
                    val activeServerIp = ServerForegroundService.ipAddressState.value
                    if (activeServerIp.isNotBlank() && (senderIp == activeServerIp || senderIp == ServerForegroundService.boundHostState.value)) continue
                    if (response.contains("RemoteMediaServer", ignoreCase = true) ||
                        response.contains("PocketNode", ignoreCase = true) ||
                        response.contains("pocketnode", ignoreCase = true)) {
                        continue // Ignore own PocketNode server response
                    }

                    // Check if it's a MediaServer or MediaRenderer or standard UPnP device
                    val isMediaDevice = response.contains("MediaServer", ignoreCase = true) ||
                            response.contains("MediaRenderer", ignoreCase = true) ||
                            response.contains("upnp:rootdevice", ignoreCase = true) ||
                            response.contains("Windows Media", ignoreCase = true)

                    val resolvedHost = resolveHostName(senderIp)
                    val displayName = if (resolvedHost != senderIp) {
                        "$resolvedHost (UPnP Media Server)"
                    } else {
                        "UPnP Media Server ($senderIp)"
                    }

                    if (upnpList.none { it.host == senderIp }) {
                        upnpList.add(
                            DiscoveredShare(
                                name = displayName,
                                host = senderIp,
                                port = 2869,
                                protocol = "UPnP / DLNA",
                                isMounted = false
                            )
                        )
                    }
                } catch (_: java.net.SocketTimeoutException) {
                    break
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Logger.w(LogTag.STORAGE, "SSDP Discovery error: ${e.message}")
        } finally {
            try { socket?.close() } catch (_: Exception) {}
            try { multicastLock?.release() } catch (_: Exception) {}
        }
        upnpList
    }

    /**
     * Connects to and mounts an SMB share (e.g., Windows PC folder, NAS share).
     */
    fun mountShare(
        context: Context,
        host: String,
        port: Int = 445,
        shareName: String,
        username: String = "",
        domain: String = "",
        password: String = "",
        displayName: String = "$shareName ($host)",
        onResult: (Boolean, String?) -> Unit
    ) {
        scope.launch {
            val share = MountedShare(
                displayName = displayName,
                host = host,
                port = port,
                shareName = shareName.trim('/', '\\'),
                username = username,
                domain = domain,
                password = password
            )

            val success = mountShareInternal(context, share, isReconnect = false)
            withContext(Dispatchers.Main) {
                if (success) {
                    onResult(true, null)
                } else {
                    onResult(false, "Failed to connect to SMB share. Check host, share name, or credentials.")
                }
            }
        }
    }

    private suspend fun mountShareInternal(context: Context, share: MountedShare, isReconnect: Boolean): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Logger.i(LogTag.STORAGE, "Connecting to SMB share: \\\\${share.host}\\${share.shareName}...")
                val client = SMBClient()
                val connection = client.connect(share.host, share.port)

                val authContext = if (share.username.isNotBlank()) {
                    AuthenticationContext(share.username, share.password.toCharArray(), share.domain)
                } else {
                    AuthenticationContext.guest()
                }

                val session = connection.authenticate(authContext)
                val diskShare = session.connectShare(share.shareName) as DiskShare

                activeClients[share.id] = client
                activeDiskShares[share.id] = diskShare

                // Index remote files recursively
                val indexedFiles = mutableListOf<NetworkVirtualMediaFile>()
                indexRemoteDirectory(diskShare, "", share.id, share.host, indexedFiles)

                share.fileCount = indexedFiles.size
                share.totalSizeBytes = indexedFiles.sumOf { it.sizeBytes }

                val currentMounted = _mountedShares.value.filter { it.id != share.id } + share
                val currentFiles = _networkFiles.value.filter { it.shareId != share.id } + indexedFiles

                withContext(Dispatchers.Main) {
                    _mountedShares.value = currentMounted
                    _networkFiles.value = currentFiles

                    // Mark as mounted in discovered list
                    _discoveredShares.value = _discoveredShares.value.map {
                        if (it.host == share.host) it.copy(isMounted = true) else it
                    }
                }

                saveMountedShares(context, currentMounted)
                saveFiles(context, currentFiles)
                Logger.i(LogTag.STORAGE, "Mounted share \\\\${share.host}\\${share.shareName} with ${indexedFiles.size} media items.")
                true
            } catch (e: Exception) {
                Logger.e(LogTag.STORAGE, "SMB mount error for ${share.host}: ${e.message}")
                false
            }
        }
    }

    private fun indexRemoteDirectory(
        diskShare: DiskShare,
        dirPath: String,
        shareId: String,
        hostName: String,
        outputList: MutableList<NetworkVirtualMediaFile>
    ) {
        runCatching {
            val list = diskShare.list(dirPath)
            for (item in list) {
                val name = item.fileName
                if (name == "." || name == ".." || name.startsWith(".")) continue

                val itemPath = if (dirPath.isEmpty()) name else "$dirPath\\$name"
                val isDir = (item.fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value) != 0L

                if (isDir) {
                    // Recurse subfolder
                    indexRemoteDirectory(diskShare, itemPath, shareId, hostName, outputList)
                } else {
                    val size = item.endOfFile
                    val mime = getMimeTypeFromExtension(name)
                    val category = categorizeMedia(name, mime, size)

                    outputList.add(
                        NetworkVirtualMediaFile(
                            id = UUID.nameUUIDFromBytes("${shareId}_$itemPath".toByteArray()).toString(),
                            shareId = shareId,
                            name = name,
                            relativePath = itemPath,
                            category = category,
                            sizeBytes = size,
                            mimeType = mime,
                            hostName = hostName
                        )
                    )
                }
            }
        }
    }

    fun unmountShare(context: Context, shareId: String) {
        scope.launch {
            try {
                activeDiskShares[shareId]?.close()
                activeDiskShares.remove(shareId)
                activeClients[shareId]?.close()
                activeClients.remove(shareId)

                val updatedShares = _mountedShares.value.filter { it.id != shareId }
                val updatedFiles = _networkFiles.value.filter { it.shareId != shareId }

                withContext(Dispatchers.Main) {
                    _mountedShares.value = updatedShares
                    _networkFiles.value = updatedFiles
                }

                saveMountedShares(context, updatedShares)
                saveFiles(context, updatedFiles)
                Logger.i(LogTag.STORAGE, "Unmounted network share: $shareId")
            } catch (e: Exception) {
                Logger.e(LogTag.STORAGE, "Error unmounting share: ${e.message}")
            }
        }
    }

    /**
     * Provides an open InputStream for direct streaming from the remote SMB share.
     */
    fun openInputStream(file: NetworkVirtualMediaFile): InputStream? {
        return try {
            val diskShare = activeDiskShares[file.shareId] ?: return null
            val smbFile = diskShare.openFile(
                file.relativePath,
                EnumSet.of(AccessMask.GENERIC_READ),
                EnumSet.of(FileAttributes.FILE_ATTRIBUTE_NORMAL),
                EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ),
                SMB2CreateDisposition.FILE_OPEN,
                null
            )
            smbFile.inputStream
        } catch (e: Exception) {
            Logger.e(LogTag.STORAGE, "Failed to stream SMB file ${file.name}: ${e.message}")
            null
        }
    }

    fun getFilesByCategory(category: String): List<NetworkVirtualMediaFile> {
        return _networkFiles.value.filter { it.category.equals(category, ignoreCase = true) }
    }

    fun getVirtualFileById(id: String): NetworkVirtualMediaFile? {
        return _networkFiles.value.find { it.id == id }
    }

    private fun isPortOpen(ip: String, port: Int, timeoutMs: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), timeoutMs)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun resolveHostName(ip: String): String {
        return try {
            val addr = InetAddress.getByName(ip)
            val name = addr.canonicalHostName
            if (name.isNotEmpty() && name != ip) name else "Network Host ($ip)"
        } catch (_: Exception) {
            "Storage Device ($ip)"
        }
    }

    private fun getDeviceIpAddress(context: Context): String? {
        // Priority 1: Check active ServerForegroundService IP
        val activeSrvIp = ServerForegroundService.ipAddressState.value
        if (activeSrvIp.isNotBlank() && activeSrvIp != "127.0.0.1" && !activeSrvIp.contains("pocketnode") && activeSrvIp.contains(".")) {
            return activeSrvIp
        }
        // Priority 2: ConnectivityManager link properties
        try {
            val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val linkProps = cm?.getLinkProperties(cm.activeNetwork)
            linkProps?.linkAddresses?.forEach { linkAddr ->
                val addr = linkAddr.address
                if (!addr.isLoopbackAddress && addr is Inet4Address) {
                    val host = addr.hostAddress?.substringBefore('%')
                    if (!host.isNullOrBlank()) return host
                }
            }
        } catch (_: Exception) {}
        // Priority 3: NetworkInterface wlan / eth
        try {
            val interfaces = Collections.list(java.net.NetworkInterface.getNetworkInterfaces())
            for (iface in interfaces) {
                if (iface.isUp && !iface.isLoopback) {
                    for (addr in Collections.list(iface.inetAddresses)) {
                        if (!addr.isLoopbackAddress && addr is Inet4Address) {
                            val host = addr.hostAddress?.substringBefore('%')
                            if (!host.isNullOrBlank()) return host
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        // Priority 4: WifiManager fallback
        return try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ip = wm.connectionInfo.ipAddress
            if (ip != 0) {
                String.format(
                    Locale.US,
                    "%d.%d.%d.%d",
                    ip and 0xff,
                    ip shr 8 and 0xff,
                    ip shr 16 and 0xff,
                    ip shr 24 and 0xff
                )
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun getAllDeviceIpAddresses(context: Context): Set<String> {
        val ips = mutableSetOf("127.0.0.1", "localhost", "0.0.0.0", "::1")
        val srvIp = ServerForegroundService.ipAddressState.value
        if (srvIp.isNotBlank()) ips.add(srvIp)
        val boundHost = ServerForegroundService.boundHostState.value
        if (boundHost.isNotBlank()) ips.add(boundHost)

        getDeviceIpAddress(context)?.let { if (it.isNotBlank()) ips.add(it) }
        try {
            val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val linkProps = cm?.getLinkProperties(cm.activeNetwork)
            linkProps?.linkAddresses?.forEach { linkAddr ->
                val host = linkAddr.address.hostAddress?.substringBefore('%')
                if (!host.isNullOrBlank()) ips.add(host)
            }
        } catch (_: Exception) {}
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces != null && interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    val hostAddr = addr.hostAddress ?: continue
                    val cleanIp = hostAddr.substringBefore('%')
                    ips.add(cleanIp)
                }
            }
        } catch (_: Exception) {}
        return ips
    }

    private fun categorizeMedia(fileName: String, mime: String, sizeBytes: Long): String {
        val lower = fileName.lowercase(Locale.ROOT)
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)

        return when {
            mime.startsWith("video/") || ext in listOf("mp4", "mkv", "avi", "mov", "webm", "flv", "m4v", "ts", "wmv") -> {
                if (sizeBytes > 300 * 1024 * 1024 ||
                    lower.contains("1080p") || lower.contains("720p") || lower.contains("2160p") ||
                    lower.contains("bluray") || lower.contains("webrip") || lower.contains("movie")
                ) {
                    "Movies"
                } else {
                    "Videos"
                }
            }
            mime.startsWith("audio/") || ext in listOf("mp3", "flac", "wav", "aac", "m4a", "ogg", "wma", "opus") -> "Music"
            mime.startsWith("image/") || ext in listOf("jpg", "jpeg", "png", "webp", "gif", "heic", "bmp") -> "Photos"
            mime.startsWith("text/") || mime.contains("pdf") || mime.contains("document") ||
            ext in listOf("pdf", "epub", "doc", "docx", "txt", "mobi", "xlsx", "pptx", "md") -> "Documents"
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

    private fun saveMountedShares(context: Context, list: List<MountedShare>) {
        val prefs = context.getSharedPreferences("remote_media_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("network_mounted_shares_json", gson.toJson(list)).apply()
    }

    private fun saveFiles(context: Context, list: List<NetworkVirtualMediaFile>) {
        val prefs = context.getSharedPreferences("remote_media_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("network_virtual_files_json", gson.toJson(list)).apply()
    }
}
