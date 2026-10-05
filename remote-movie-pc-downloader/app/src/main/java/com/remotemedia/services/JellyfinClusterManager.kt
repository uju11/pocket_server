package com.remotemedia.services

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.remotemedia.core.FederatedMediaManager
import com.remotemedia.core.LogTag
import com.remotemedia.core.Logger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.InputStream
import java.io.OutputStreamWriter
import java.net.*
import java.util.UUID

data class JellyfinPublicUser(
    val id: String,
    val name: String,
    val hasPassword: Boolean = false
)

data class RemoteJellyfinServer(
    val id: String = UUID.randomUUID().toString(),
    val name: String,               // e.g. "My PC Jellyfin", "NAS Server"
    val host: String,               // e.g. "192.168.1.100"
    val port: Int = 8096,           // Default 8096
    val apiKey: String = "",        // Jellyfin User Token or API Key
    val username: String = "",      // Target account username
    val password: String = "",      // Optional user password
    val userId: String = "",        // Scoped User ID / GUID
    val accessToken: String = "",   // Scoped token from authentication
    val isOnline: Boolean = false,
    val serverVersion: String = "",
    val serverName: String = "",
    val itemCount: Int = 0,
    val lastSyncTime: Long = 0L,
    val isEnabled: Boolean = true
)

data class RemoteJellyfinMedia(
    val id: String,                 // Upstream Jellyfin Item ID
    val serverId: String,           // Remote server ID in cluster
    val serverName: String,
    val serverHost: String,
    val serverPort: Int,
    val apiKey: String,
    val name: String,
    val category: String = "Movies",
    val mimeType: String = "video/mp4",
    val sizeBytes: Long = 0L,
    val runTimeTicks: Long = 0L,
    val overview: String = "",
    val year: String = "2024",
    val streamUrl: String = ""
)

/**
 * JellyfinClusterManager
 *
 * Manages upstream Jellyfin servers (such as a Windows PC, Mac, Linux NAS running Jellyfin).
 * Aggregates all their remote libraries into the phone's unified media gateway so the phone
 * acts as a single point of access for all Jellyfin instances.
 */
object JellyfinClusterManager {

    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _servers = MutableStateFlow<List<RemoteJellyfinServer>>(emptyList())
    val servers: StateFlow<List<RemoteJellyfinServer>> = _servers.asStateFlow()

    private val _remoteItems = MutableStateFlow<List<RemoteJellyfinMedia>>(emptyList())
    val remoteItems: StateFlow<List<RemoteJellyfinMedia>> = _remoteItems.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        val prefs = context.getSharedPreferences("remote_media_prefs", Context.MODE_PRIVATE)
        val json = prefs.getString("jellyfin_cluster_servers_json", null)

        val loadedServers = if (!json.isNullOrBlank()) {
            runCatching {
                val type = object : TypeToken<List<RemoteJellyfinServer>>() {}.type
                gson.fromJson<List<RemoteJellyfinServer>>(json, type)
            }.getOrDefault(emptyList())
        } else {
            // Provide a default example / template for PC Jellyfin
            listOf(
                RemoteJellyfinServer(
                    id = "pc-jellyfin-default",
                    name = "Desktop PC Jellyfin",
                    host = "192.168.1.100",
                    port = 8096,
                    isEnabled = false
                )
            )
        }
        _servers.value = loadedServers

        // Start background health probe and library sync
        scope.launch {
            syncAllServers(context)
        }
    }

    fun addServer(server: RemoteJellyfinServer, context: Context) {
        val updated = _servers.value.filter { it.id != server.id } + server
        _servers.value = updated
        saveState(context)
        scope.launch { syncServer(server, context) }
    }

    fun updateServer(server: RemoteJellyfinServer, context: Context) {
        val updated = _servers.value.map { if (it.id == server.id) server else it }
        _servers.value = updated
        saveState(context)
        scope.launch { syncServer(server, context) }
    }

    fun removeServer(serverId: String, context: Context) {
        _servers.value = _servers.value.filter { it.id != serverId }
        _remoteItems.value = _remoteItems.value.filter { it.serverId != serverId }
        saveState(context)
        FederatedMediaManager.refresh()
    }

    fun toggleServer(serverId: String, context: Context) {
        val target = _servers.value.find { it.id == serverId } ?: return
        val updated = target.copy(isEnabled = !target.isEnabled)
        updateServer(updated, context)
    }

    private fun saveState(context: Context) {
        val prefs = context.getSharedPreferences("remote_media_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("jellyfin_cluster_servers_json", gson.toJson(_servers.value))
            .apply()
    }

    /**
     * Auto-discovers any Jellyfin servers running on the local network (PC, NAS, etc.)
     * Uses UDP port 7359 broadcast protocol and fast local subnet ping probing.
     */
    suspend fun autoDiscoverServers(context: Context): List<RemoteJellyfinServer> = withContext(Dispatchers.IO) {
        _isScanning.value = true
        val discoveredList = mutableListOf<RemoteJellyfinServer>()

        try {
            // 1. UDP Discovery broadcast on port 7359
            val socket = DatagramSocket().apply {
                broadcast = true
                soTimeout = 1200
            }
            val requestText = "who is JellyfinServer?"
            val reqData = requestText.toByteArray()
            val broadcastAddr = InetAddress.getByName("255.255.255.255")
            val packet = DatagramPacket(reqData, reqData.size, broadcastAddr, 7359)
            socket.send(packet)

            val receiveBuf = ByteArray(2048)
            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < 1500) {
                try {
                    val respPacket = DatagramPacket(receiveBuf, receiveBuf.size)
                    socket.receive(respPacket)
                    val jsonStr = String(respPacket.data, 0, respPacket.length)
                    val obj = runCatching { gson.fromJson(jsonStr, JsonObject::class.java) }.getOrNull()
                    if (obj != null) {
                        val address = obj.get("Address")?.asString ?: ""
                        val name = obj.get("Name")?.asString ?: "Remote Jellyfin"
                        val id = obj.get("Id")?.asString ?: UUID.randomUUID().toString()

                        if (address.isNotBlank()) {
                            val uri = URI(address)
                            val host = uri.host ?: respPacket.address.hostAddress ?: ""
                            val port = if (uri.port > 0) uri.port else 8096

                            val newServer = RemoteJellyfinServer(
                                id = "discovered_$id",
                                name = name,
                                host = host,
                                port = port,
                                serverName = name,
                                isOnline = true,
                                isEnabled = true
                            )
                            discoveredList.add(newServer)
                        }
                    }
                } catch (e: SocketTimeoutException) {
                    break
                } catch (e: Exception) {
                    break
                }
            }
            socket.close()

            // 2. Subnet Port Probe (Probes common host on port 8096 e.g., 192.168.1.1, router gateway, local PC)
            val localIp = runCatching {
                NetworkInterface.getNetworkInterfaces().asSequence()
                    .flatMap { it.inetAddresses.asSequence() }
                    .firstOrNull { !it.isLoopbackAddress && it is Inet4Address }
                    ?.hostAddress
            }.getOrNull()

            if (localIp != null && localIp.contains(".")) {
                val prefix = localIp.substringBeforeLast(".")
                val targetsToProbe = listOf(
                    "$prefix.1",    // Router / default gateway
                    "$prefix.2",    // Common PC IP
                    "$prefix.10",
                    "$prefix.50",
                    "$prefix.100",  // Common DHCP PC IP
                    "$prefix.101",
                    "$prefix.150",
                    "$prefix.200"
                ).filter { it != localIp }

                val probeJobs = targetsToProbe.map { ip ->
                    async {
                        runCatching {
                            val testSocket = Socket()
                            testSocket.connect(InetSocketAddress(ip, 8096), 250)
                            testSocket.close()

                            // If port 8096 is open, query /System/Info/Public to verify
                            val infoUrl = URL("http://$ip:8096/System/Info/Public")
                            val conn = infoUrl.openConnection() as HttpURLConnection
                            conn.connectTimeout = 500
                            conn.readTimeout = 500
                            if (conn.responseCode == 200) {
                                val body = conn.inputStream.bufferedReader().use { it.readText() }
                                val json = gson.fromJson(body, JsonObject::class.java)
                                val sName = json.get("ServerName")?.asString ?: "PC Jellyfin"
                                val sVersion = json.get("Version")?.asString ?: ""
                                val sId = json.get("Id")?.asString ?: ip

                                RemoteJellyfinServer(
                                    id = "probe_$sId",
                                    name = sName,
                                    host = ip,
                                    port = 8096,
                                    serverName = sName,
                                    serverVersion = sVersion,
                                    isOnline = true,
                                    isEnabled = true
                                )
                            } else null
                        }.getOrNull()
                    }
                }
                val probedResults = probeJobs.awaitAll().filterNotNull()
                discoveredList.addAll(probedResults)
            }

            // Merge discovered servers with existing servers
            val currentServers = _servers.value.toMutableList()
            for (disc in discoveredList) {
                val exists = currentServers.any { it.host == disc.host && it.port == disc.port }
                if (!exists) {
                    currentServers.add(disc)
                }
            }
            _servers.value = currentServers
            saveState(context)

            // Trigger sync for all newly discovered servers
            for (disc in discoveredList) {
                launch { syncServer(disc, context) }
            }

        } catch (e: Exception) {
            Logger.e(LogTag.JELLYFIN, "Jellyfin auto-discovery error: ${e.message}")
        } finally {
            _isScanning.value = false
        }

        discoveredList
    }

    /**
     * Connects to all enabled Jellyfin servers, verifies connectivity, and synchronizes media.
     */
    suspend fun syncAllServers(context: Context) = withContext(Dispatchers.IO) {
        _isSyncing.value = true
        try {
            val current = _servers.value
            val syncJobs = current.filter { it.isEnabled }.map { server ->
                async { syncServer(server, context) }
            }
            syncJobs.awaitAll()
        } finally {
            _isSyncing.value = false
        }
    }

    /**
     * Synchronizes a single upstream Jellyfin server via REST API.
     */
    suspend fun syncServer(server: RemoteJellyfinServer, context: Context): Boolean = withContext(Dispatchers.IO) {
        val baseUrl = "http://${server.host}:${server.port}"
        var isOnline = false
        var version = server.serverVersion
        var sName = server.serverName
        var token = server.accessToken

        try {
            // 1. Check System Info
            val infoUrl = URL("$baseUrl/System/Info/Public")
            val infoConn = infoUrl.openConnection() as HttpURLConnection
            infoConn.connectTimeout = 1500
            infoConn.readTimeout = 1500
            if (infoConn.responseCode in 200..299) {
                isOnline = true
                val infoBody = infoConn.inputStream.bufferedReader().use { it.readText() }
                val infoObj = runCatching { gson.fromJson(infoBody, JsonObject::class.java) }.getOrNull()
                if (infoObj != null) {
                    sName = infoObj.get("ServerName")?.asString ?: server.name
                    version = infoObj.get("Version")?.asString ?: ""
                }
            }
            infoConn.disconnect()

            var activeUserId = server.userId
            // 2. Authenticate if username is configured and we don't have a token
            if (server.username.isNotBlank() && token.isBlank()) {
                val authUrl = URL("$baseUrl/Users/AuthenticateByName")
                val authConn = authUrl.openConnection() as HttpURLConnection
                authConn.requestMethod = "POST"
                authConn.connectTimeout = 3000
                authConn.readTimeout = 3000
                authConn.setRequestProperty("Content-Type", "application/json")
                authConn.setRequestProperty("X-Emby-Authorization", "MediaBrowser Client=\"PocketNodeGateway\", Device=\"PhoneServer\", DeviceId=\"phone-gateway-01\", Version=\"1.0\"")
                authConn.doOutput = true

                val authPayload = JsonObject().apply {
                    addProperty("Username", server.username)
                    addProperty("Pw", server.password)
                }
                OutputStreamWriter(authConn.outputStream).use { it.write(authPayload.toString()) }

                if (authConn.responseCode in 200..299) {
                    val authBody = authConn.inputStream.bufferedReader().use { it.readText() }
                    val authObj = runCatching { gson.fromJson(authBody, JsonObject::class.java) }.getOrNull()
                    token = authObj?.get("AccessToken")?.asString ?: ""
                    val userObj = authObj?.getAsJsonObject("User")
                    if (userObj != null) {
                        activeUserId = userObj.get("Id")?.asString ?: activeUserId
                    }
                }
                authConn.disconnect()
            }

            val activeToken = if (token.isNotBlank()) token else server.apiKey

            // If userId is still unset, attempt /Users/Me lookup using active token
            if (activeUserId.isBlank() && activeToken.isNotBlank()) {
                runCatching {
                    val meUrl = URL("$baseUrl/Users/Me")
                    val meConn = meUrl.openConnection() as HttpURLConnection
                    meConn.connectTimeout = 2000
                    meConn.readTimeout = 2000
                    meConn.setRequestProperty("X-Emby-Token", activeToken)
                    meConn.setRequestProperty("X-MediaBrowser-Token", activeToken)
                    if (meConn.responseCode in 200..299) {
                        val meBody = meConn.inputStream.bufferedReader().use { it.readText() }
                        val meObj = gson.fromJson(meBody, JsonObject::class.java)
                        activeUserId = meObj.get("Id")?.asString ?: ""
                    }
                    meConn.disconnect()
                }
            }

            // 3. Query Remote Media Items - SCOPED TO THIS SPECIFIC USER ACCOUNT
            val itemsUrlStr = if (activeUserId.isNotBlank()) {
                "$baseUrl/Users/$activeUserId/Items?Recursive=true&IncludeItemTypes=Movie,Series,Episode,Video&Fields=Path,Overview,RunTimeTicks,Size,ProductionYear"
            } else {
                "$baseUrl/Items?Recursive=true&IncludeItemTypes=Movie,Series,Episode,Video&Fields=Path,Overview,RunTimeTicks,Size,ProductionYear"
            }
            val itemsUrl = URL(itemsUrlStr)
            val itemsConn = itemsUrl.openConnection() as HttpURLConnection
            itemsConn.connectTimeout = 3000
            itemsConn.readTimeout = 4000

            if (activeToken.isNotBlank()) {
                itemsConn.setRequestProperty("X-Emby-Token", activeToken)
                itemsConn.setRequestProperty("X-MediaBrowser-Token", activeToken)
            }

            val newItems = mutableListOf<RemoteJellyfinMedia>()
            if (itemsConn.responseCode in 200..299) {
                val itemsBody = itemsConn.inputStream.bufferedReader().use { it.readText() }
                val rootObj = runCatching { gson.fromJson(itemsBody, JsonObject::class.java) }.getOrNull()
                val itemsArray = rootObj?.getAsJsonArray("Items")

                if (itemsArray != null) {
                    for (i in 0 until itemsArray.size()) {
                        val itemObj = itemsArray.get(i).asJsonObject
                        val itemId = itemObj.get("Id")?.asString ?: continue
                        val itemName = itemObj.get("Name")?.asString ?: "Untitled"
                        val type = itemObj.get("Type")?.asString ?: "Movie"
                        val overview = itemObj.get("Overview")?.asString ?: ""
                        val runTime = itemObj.get("RunTimeTicks")?.asLong ?: 0L
                        val size = itemObj.get("Size")?.asLong ?: 0L
                        val year = itemObj.get("ProductionYear")?.asString ?: "2024"
                        val container = itemObj.get("Container")?.asString ?: "mp4"

                        val streamUrl = "$baseUrl/Videos/$itemId/stream?static=true" +
                                (if (activeToken.isNotBlank()) "&api_key=$activeToken" else "")

                        newItems.add(
                            RemoteJellyfinMedia(
                                id = itemId,
                                serverId = server.id,
                                serverName = sName.ifBlank { server.name },
                                serverHost = server.host,
                                serverPort = server.port,
                                apiKey = activeToken,
                                name = itemName,
                                category = if (type.equals("Audio", ignoreCase = true)) "Music" else "Movies",
                                mimeType = "video/$container",
                                sizeBytes = size,
                                runTimeTicks = runTime,
                                overview = overview,
                                year = year,
                                streamUrl = streamUrl
                            )
                        )
                    }
                }
            }
            itemsConn.disconnect()

            // Update items for this server
            val remainingItems = _remoteItems.value.filter { it.serverId != server.id }
            _remoteItems.value = remainingItems + newItems

            // Update server entry state with scoped user ID
            val updatedServer = server.copy(
                isOnline = isOnline,
                serverName = sName,
                serverVersion = version,
                accessToken = token,
                userId = activeUserId,
                itemCount = newItems.size,
                lastSyncTime = System.currentTimeMillis()
            )
            _servers.value = _servers.value.map { if (it.id == server.id) updatedServer else it }
            saveState(context)

            // Trigger federation rebuild so new items show everywhere
            FederatedMediaManager.refresh()
            Logger.i(LogTag.JELLYFIN, "Synced Jellyfin cluster node [${server.name}]: ${newItems.size} items discovered.")
            return@withContext isOnline

        } catch (e: Exception) {
            Logger.w(LogTag.JELLYFIN, "Sync failed for Jellyfin server [${server.name}] (${server.host}): ${e.message}")
            val offlineServer = server.copy(isOnline = false)
            _servers.value = _servers.value.map { if (it.id == server.id) offlineServer else it }
            return@withContext false
        }
    }

data class RemoteStreamResponse(
    val inputStream: InputStream,
    val statusCode: Int,
    val contentLength: Long,
    val contentRange: String?,
    val contentType: String?
)

    /**
     * Opens a streaming connection to an upstream Jellyfin server for a federated video item.
     * Supports HTTP Range headers for scrubbing and seek functionality.
     */
    fun openRemoteStreamResponse(item: RemoteJellyfinMedia, rangeHeader: String? = null): RemoteStreamResponse? {
        return runCatching {
            val url = URL(item.streamUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 6000
            conn.readTimeout = 15000
            if (rangeHeader != null) {
                conn.setRequestProperty("Range", rangeHeader)
            }
            val token = item.apiKey.ifBlank {
                _servers.value.find { it.id == item.serverId }?.let { it.accessToken.ifBlank { it.apiKey } } ?: ""
            }
            if (token.isNotBlank()) {
                conn.setRequestProperty("X-Emby-Token", token)
                conn.setRequestProperty("Authorization", "MediaBrowser Token=\"$token\"")
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else (conn.errorStream ?: conn.inputStream)
            RemoteStreamResponse(
                inputStream = stream,
                statusCode = code,
                contentLength = conn.contentLengthLong,
                contentRange = conn.getHeaderField("Content-Range"),
                contentType = conn.contentType
            )
        }.onFailure {
            Logger.e(LogTag.JELLYFIN, "Failed to connect to remote Jellyfin stream: ${item.streamUrl} - ${it.message}")
        }.getOrNull()
    }

    /**
     * Backward-compatible helper returning raw InputStream.
     */
    fun openRemoteStream(item: RemoteJellyfinMedia, rangeHeader: String? = null): InputStream? {
        return openRemoteStreamResponse(item, rangeHeader)?.inputStream
    }

    /**
     * Fetches public user accounts on the remote Jellyfin server.
     * Allows selecting a specific account when multiple users exist on the PC.
     */
    suspend fun fetchPublicUsers(host: String, port: Int = 8096): List<JellyfinPublicUser> = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL("http://$host:$port/Users/Public")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 2500
            conn.readTimeout = 2500
            if (conn.responseCode in 200..299) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val array = gson.fromJson(body, com.google.gson.JsonArray::class.java)
                val users = mutableListOf<JellyfinPublicUser>()
                for (i in 0 until array.size()) {
                    val obj = array.get(i).asJsonObject
                    val uid = obj.get("Id")?.asString ?: ""
                    val uname = obj.get("Name")?.asString ?: ""
                    val hasPw = obj.get("HasPassword")?.asBoolean ?: false
                    if (uid.isNotBlank() && uname.isNotBlank()) {
                        users.add(JellyfinPublicUser(id = uid, name = uname, hasPassword = hasPw))
                    }
                }
                users
            } else emptyList()
        }.getOrDefault(emptyList())
    }
}
