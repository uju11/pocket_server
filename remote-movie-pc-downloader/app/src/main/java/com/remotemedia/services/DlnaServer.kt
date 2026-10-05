package com.remotemedia.services

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.wifi.WifiManager
import com.remotemedia.core.FederatedMediaManager
import com.remotemedia.core.HostMediaManager
import com.remotemedia.core.MediaThumbnailManager
import com.remotemedia.core.NetworkShareManager
import com.remotemedia.core.LogTag
import com.remotemedia.core.Logger
import com.remotemedia.core.StorageManager
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.autohead.AutoHeadResponse
import io.ktor.server.plugins.partialcontent.PartialContent
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondFile
import io.ktor.server.response.respondOutputStream
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.util.Collections
import java.util.UUID

/**
 * Universal Plug and Play (UPnP AV) & DLNA 1.5 Media Server.
 *
 * Implements full UPnP Device Architecture v1.0 / DLNA DMS-1.50 standards:
 * - SSDP Broadcaster on 239.255.255.250:1900 advertising upnp:rootdevice, MediaServer:1, and ContentDirectory.
 * - XML Device Description with custom branding, iconList, ContentDirectory, and ConnectionManager services.
 * - SOAP Browse handler supporting BrowseMetadata and BrowseDirectChildren for Smart TVs, Windows PCs, and VLC.
 * - High-speed video streaming with HTTP 206 Partial Content (Range requests) and DLNA streaming headers.
 */
object DlnaServer {

    private var serverEngine: ApplicationEngine? = null
    private var ssdpJob = SupervisorJob()
    private var ssdpScope = CoroutineScope(Dispatchers.IO + ssdpJob)
    private var multicastLock: WifiManager.MulticastLock? = null
    private val uuid = UUID.nameUUIDFromBytes("pocketnode-universal-upnp-dlna".toByteArray()).toString()

    fun start(context: Context, port: Int = 8090) {
        if (serverEngine != null) return

        try {
            // Acquire MulticastLock to ensure Android Wi-Fi driver receives UDP SSDP multicasts
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            multicastLock = wifiManager.createMulticastLock("PocketNodeDlnaMulticast").apply {
                setReferenceCounted(false)
                acquire()
            }

            val ip = getLocalIpAddress() ?: "127.0.0.1"

            // 1. Ktor UPnP Device Description & ContentDirectory XML Server
            serverEngine = embeddedServer(CIO, port = port) {
                install(PartialContent)
                install(AutoHeadResponse)

                routing {
                    // UPnP Root Device Description XML (Queried by Windows Explorer, Smart TVs, and VLC)
                    get("/description.xml") {
                        val xml = buildDeviceDescriptionXml(ip, port)
                        call.response.header("Server", "Android/14 UPnP/1.0 DLNADOC/1.50 PocketNode/2.0")
                        call.response.header("Access-Control-Allow-Origin", "*")
                        call.respondText(xml, ContentType.Text.Xml)
                    }

                    // UPnP ContentDirectory SCPD XML
                    get("/ContentDirectory.xml") {
                        val xml = buildContentDirectoryXml()
                        call.response.header("Server", "Android/14 UPnP/1.0 DLNADOC/1.50 PocketNode/2.0")
                        call.response.header("Access-Control-Allow-Origin", "*")
                        call.respondText(xml, ContentType.Text.Xml)
                    }

                    // UPnP ConnectionManager SCPD XML (Required by DLNA Specification)
                    get("/ConnectionManager.xml") {
                        val xml = buildConnectionManagerXml()
                        call.response.header("Server", "Android/14 UPnP/1.0 DLNADOC/1.50 PocketNode/2.0")
                        call.response.header("Access-Control-Allow-Origin", "*")
                        call.respondText(xml, ContentType.Text.Xml)
                    }

                    // UPnP Device Icons for Windows Explorer & Smart TV Connected Devices
                    get("/icon.png") {
                        val iconBytes = getCustomOrGeneratedIcon(context, 48)
                        call.response.header(HttpHeaders.CacheControl, "public, max-age=3600")
                        call.respondBytes(iconBytes, ContentType.Image.PNG)
                    }
                    get("/icon-large.png") {
                        val iconBytes = getCustomOrGeneratedIcon(context, 120)
                        call.response.header(HttpHeaders.CacheControl, "public, max-age=3600")
                        call.respondBytes(iconBytes, ContentType.Image.PNG)
                    }

                    // Video Still Card / Thumbnail Endpoint for DLNA Players (VLC, Smart TVs)
                    get("/thumb/{id}") {
                        val id = call.parameters["id"] ?: ""
                        val fedItem = FederatedMediaManager.getById(id)
                        val directFile = if (fedItem != null) FederatedMediaManager.getDirectFile(fedItem) else null
                        val bytes = MediaThumbnailManager.getThumbnail(context, id, directFile)
                        call.response.header(HttpHeaders.CacheControl, "public, max-age=86400")
                        call.respondBytes(bytes, ContentType.Image.JPEG)
                    }

                    // UPnP Control SOAP Endpoint for Browsing Media (Smart TVs, Windows, VLC)
                    post("/ContentDirectory/control") {
                        val requestBody = call.receiveText()
                        Logger.i(LogTag.DLNA, "DLNA ContentDirectory Browse request received")
                        val responseXml = handleContentDirectorySoap(requestBody, ip, port)
                        call.response.header("Server", "Android/14 UPnP/1.0 DLNADOC/1.50 PocketNode/2.0")
                        call.response.header("EXT", "")
                        call.respondText(responseXml, ContentType.Text.Xml)
                    }

                    // UPnP ConnectionManager SOAP Endpoint
                    post("/ConnectionManager/control") {
                        val requestBody = call.receiveText()
                        val responseXml = handleConnectionManagerSoap(requestBody)
                        call.response.header("Server", "Android/14 UPnP/1.0 DLNADOC/1.50 PocketNode/2.0")
                        call.response.header("EXT", "")
                        call.respondText(responseXml, ContentType.Text.Xml)
                    }

                    // Event Subscriptions dummy endpoints
                    get("/ContentDirectory/event") { call.respondText("", ContentType.Text.Plain, HttpStatusCode.OK) }
                    get("/ConnectionManager/event") { call.respondText("", ContentType.Text.Plain, HttpStatusCode.OK) }

                    // Direct Stream for DLNA Clients (Storage Movies)
                    get("/media/{fileName}") {
                        val fileName = call.parameters["fileName"] ?: ""
                        val file = StorageManager.resolveRelativePath("movies/$fileName")
                            ?: StorageManager.resolveRelativePath(fileName)

                        if (file != null && file.exists() && file.isFile) {
                            Logger.i(LogTag.DLNA, "DLNA streaming media to client: ${file.name}")
                            setDlnaHeaders(call, file.name)
                            call.respondFile(file)
                        } else {
                            call.respondText("Media not found", status = HttpStatusCode.NotFound)
                        }
                    }

                    // Direct Stream for Host Mobile Shared Files
                    get("/hostmedia/{fileId}") {
                        val fileId = call.parameters["fileId"] ?: ""
                        val vFile = HostMediaManager.getVirtualFileById(fileId)
                        if (vFile != null) {
                            setDlnaHeaders(call, vFile.name)
                            val direct = HostMediaManager.getDirectFile(vFile)
                            if (direct != null && direct.exists()) {
                                Logger.i(LogTag.DLNA, "DLNA streaming host direct file: ${vFile.name}")
                                call.respondFile(direct)
                            } else {
                                val stream = HostMediaManager.openInputStream(context, vFile)
                                if (stream != null) {
                                    Logger.i(LogTag.DLNA, "DLNA streaming host SAF stream: ${vFile.name}")
                                    call.respondOutputStream(ContentType.Video.MP4, HttpStatusCode.OK) {
                                        stream.use { it.copyTo(this) }
                                    }
                                } else {
                                    call.respondText("Media not found", status = HttpStatusCode.NotFound)
                                }
                            }
                        } else {
                            call.respondText("Media not found", status = HttpStatusCode.NotFound)
                        }
                    }

                    // Direct Stream for LAN Network Shared Files (PC / NAS SMB)
                    get("/netmedia/{fileId}") {
                        val fileId = call.parameters["fileId"] ?: ""
                        val nFile = NetworkShareManager.getVirtualFileById(fileId)
                        if (nFile != null) {
                            setDlnaHeaders(call, nFile.name)
                            val stream = NetworkShareManager.openInputStream(nFile)
                            if (stream != null) {
                                Logger.i(LogTag.DLNA, "DLNA streaming LAN network media (SMB): ${nFile.name}")
                                call.respondOutputStream(ContentType.Video.MP4, HttpStatusCode.OK) {
                                    stream.use { it.copyTo(this) }
                                }
                            } else {
                                call.respondText("Media not found", status = HttpStatusCode.NotFound)
                            }
                        } else {
                            call.respondText("Media not found", status = HttpStatusCode.NotFound)
                        }
                    }

                    // Direct Stream for Federated Media (M5 Combined View)
                    get("/fedmedia/{fileId}") {
                        val fileId = call.parameters["fileId"] ?: ""
                        val fItem = FederatedMediaManager.getById(fileId)
                        if (fItem != null) {
                            setDlnaHeaders(call, fItem.name)
                            val direct = FederatedMediaManager.getDirectFile(fItem)
                            if (direct != null && direct.exists()) {
                                Logger.i(LogTag.DLNA, "DLNA streaming federated direct file: ${fItem.name}")
                                call.respondFile(direct)
                            } else {
                                val stream = FederatedMediaManager.openInputStream(context, fItem)
                                if (stream != null) {
                                    Logger.i(LogTag.DLNA, "DLNA streaming federated stream: ${fItem.name}")
                                    val cType = runCatching { ContentType.parse(fItem.mimeType) }
                                        .getOrDefault(ContentType.Video.MP4)
                                    call.respondOutputStream(cType, HttpStatusCode.OK) {
                                        stream.use { it.copyTo(this) }
                                    }
                                } else {
                                    call.respondText("Media not found", status = HttpStatusCode.NotFound)
                                }
                            }
                        } else {
                            call.respondText("Media not found", status = HttpStatusCode.NotFound)
                        }
                    }
                }
            }.start(wait = false)

            // 2. SSDP Multicast Broadcaster & M-SEARCH Listener
            startSsdpBroadcaster(ip, port)

            Logger.i(LogTag.DLNA, "Universal UPnP & DLNA Media Server running on port $port")
        } catch (e: Exception) {
            Logger.e(LogTag.DLNA, "Failed to start DLNA Server: ${e.message}")
        }
    }

    private fun setDlnaHeaders(call: io.ktor.server.application.ApplicationCall, fileName: String) {
        call.response.header("transferMode.dlna.org", "Streaming")
        call.response.header("contentFeatures.dlna.org", "DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000")
        call.response.header("Server", "Android/14 UPnP/1.0 DLNADOC/1.50 PocketNode/2.0")
        call.response.header("Accept-Ranges", "bytes")
        call.response.header("Access-Control-Allow-Origin", "*")
    }

    fun stop() {
        ssdpJob.cancel()
        serverEngine?.stop(1000, 2000)
        serverEngine = null
        try {
            if (multicastLock?.isHeld == true) multicastLock?.release()
        } catch (e: Exception) {
            Logger.e(LogTag.DLNA, "Error releasing multicast lock: ${e.message}")
        }
        Logger.i(LogTag.DLNA, "DLNA Server stopped.")
    }

    private fun startSsdpBroadcaster(ip: String, port: Int) {
        ssdpJob = SupervisorJob()
        ssdpScope = CoroutineScope(Dispatchers.IO + ssdpJob)

        ssdpScope.launch {
            try {
                val group = InetAddress.getByName("239.255.255.250")
                val socket = MulticastSocket(null).apply {
                    reuseAddress = true
                    bind(InetSocketAddress(1900))
                }

                // Join on all available active network interfaces
                runCatching {
                    val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
                    for (ni in interfaces) {
                        if (ni.isUp && !ni.isLoopback && ni.supportsMulticast()) {
                            runCatching { socket.joinGroup(InetSocketAddress(group, 1900), ni) }
                        }
                    }
                }

                // 1. Periodic SSDP NOTIFY Announcement Loop (Broadcasts alive to PCs & Smart TVs every 10s)
                launch {
                    val targets = listOf(
                        "upnp:rootdevice" to "uuid:$uuid::upnp:rootdevice",
                        "uuid:$uuid" to "uuid:$uuid",
                        "urn:schemas-upnp-org:device:MediaServer:1" to "uuid:$uuid::urn:schemas-upnp-org:device:MediaServer:1",
                        "urn:schemas-upnp-org:service:ContentDirectory:1" to "uuid:$uuid::urn:schemas-upnp-org:service:ContentDirectory:1",
                        "urn:schemas-upnp-org:service:ConnectionManager:1" to "uuid:$uuid::urn:schemas-upnp-org:service:ConnectionManager:1"
                    )

                    while (true) {
                        try {
                            val activeIp = getLocalIpAddress() ?: ip
                            for ((nt, usn) in targets) {
                                val packetStr = buildNotifyPacket(activeIp, port, nt, usn)
                                val bytes = packetStr.toByteArray()
                                val datagram = DatagramPacket(bytes, bytes.size, group, 1900)
                                socket.send(datagram)
                            }
                        } catch (e: Exception) {
                            Logger.e(LogTag.DLNA, "SSDP Broadcast error: ${e.message}")
                        }
                        delay(10000) // Broadcast every 10 seconds
                    }
                }

                // 2. SSDP M-SEARCH Discovery Response Loop (Listens for search requests from TV/PC)
                val buffer = ByteArray(2048)
                while (true) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    val message = String(packet.data, 0, packet.length)

                    if (message.contains("M-SEARCH", ignoreCase = true)) {
                        val activeIp = getLocalIpAddress() ?: ip
                        val responses = getSearchResponses(message, activeIp, port)
                        for (resp in responses) {
                            val respBytes = resp.toByteArray()
                            val responsePacket = DatagramPacket(
                                respBytes,
                                respBytes.size,
                                packet.address,
                                packet.port
                            )
                            DatagramSocket().use { unicast ->
                                unicast.send(responsePacket)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Logger.e(LogTag.DLNA, "SSDP Multicast socket error: ${e.message}")
            }
        }
    }

    private fun buildNotifyPacket(ip: String, port: Int, nt: String, usn: String): String {
        return "NOTIFY * HTTP/1.1\r\n" +
                "HOST: 239.255.255.250:1900\r\n" +
                "CACHE-CONTROL: max-age=1800\r\n" +
                "LOCATION: http://$ip:$port/description.xml\r\n" +
                "NT: $nt\r\n" +
                "NTS: ssdp:alive\r\n" +
                "SERVER: Android/14 UPnP/1.0 DLNADOC/1.50 PocketNode/2.0\r\n" +
                "USN: $usn\r\n\r\n"
    }

    private fun getSearchResponses(query: String, ip: String, port: Int): List<String> {
        val responses = mutableListOf<String>()
        val targets = listOf(
            "upnp:rootdevice" to "uuid:$uuid::upnp:rootdevice",
            "uuid:$uuid" to "uuid:$uuid",
            "urn:schemas-upnp-org:device:MediaServer:1" to "uuid:$uuid::urn:schemas-upnp-org:device:MediaServer:1",
            "urn:schemas-upnp-org:service:ContentDirectory:1" to "uuid:$uuid::urn:schemas-upnp-org:service:ContentDirectory:1",
            "urn:schemas-upnp-org:service:ConnectionManager:1" to "uuid:$uuid::urn:schemas-upnp-org:service:ConnectionManager:1"
        )

        val isSearchAll = query.contains("ssdp:all", ignoreCase = true)

        for ((st, usn) in targets) {
            if (isSearchAll || query.contains(st, ignoreCase = true)) {
                responses.add(
                    "HTTP/1.1 200 OK\r\n" +
                    "CACHE-CONTROL: max-age=1800\r\n" +
                    "DATE: \r\n" +
                    "EXT:\r\n" +
                    "LOCATION: http://$ip:$port/description.xml\r\n" +
                    "SERVER: Android/14 UPnP/1.0 DLNADOC/1.50 PocketNode/2.0\r\n" +
                    "ST: $st\r\n" +
                    "USN: $usn\r\n\r\n"
                )
            }
        }

        // Fallback for general MediaServer searches
        if (responses.isEmpty() && (query.contains("MediaServer", ignoreCase = true) || query.contains("ContentDirectory", ignoreCase = true))) {
            responses.add(
                "HTTP/1.1 200 OK\r\n" +
                "CACHE-CONTROL: max-age=1800\r\n" +
                "EXT:\r\n" +
                "LOCATION: http://$ip:$port/description.xml\r\n" +
                "SERVER: Android/14 UPnP/1.0 DLNADOC/1.50 PocketNode/2.0\r\n" +
                "ST: urn:schemas-upnp-org:device:MediaServer:1\r\n" +
                "USN: uuid:$uuid::urn:schemas-upnp-org:device:MediaServer:1\r\n\r\n"
            )
        }

        return responses
    }

    private fun buildDeviceDescriptionXml(ip: String, port: Int): String {
        return """<?xml version="1.0" encoding="utf-8"?>
<root xmlns="urn:schemas-upnp-org:device-1-0" xmlns:dlna="urn:schemas-dlna-org:device-1-0">
    <specVersion>
        <major>1</major>
        <minor>0</minor>
    </specVersion>
    <device>
        <deviceType>urn:schemas-upnp-org:device:MediaServer:1</deviceType>
        <friendlyName>PocketNode Media Server</friendlyName>
        <manufacturer>PocketNode</manufacturer>
        <manufacturerURL>http://$ip:$port</manufacturerURL>
        <modelDescription>PocketNode UPnP/DLNA Universal Media Server</modelDescription>
        <modelName>PocketNode Server</modelName>
        <modelNumber>2.0</modelNumber>
        <modelURL>http://$ip:$port</modelURL>
        <serialNumber>PN-$uuid</serialNumber>
        <UDN>uuid:$uuid</UDN>
        <dlna:X_DLNADOC xmlns:dlna="urn:schemas-dlna-org:device-1-0">DMS-1.50</dlna:X_DLNADOC>
        <dlna:X_DLNACAP xmlns:dlna="urn:schemas-dlna-org:device-1-0">av-upload,image-upload,audio-upload</dlna:X_DLNACAP>
        <iconList>
            <icon>
                <mimetype>image/png</mimetype>
                <width>48</width>
                <height>48</height>
                <depth>24</depth>
                <url>/icon.png</url>
            </icon>
            <icon>
                <mimetype>image/png</mimetype>
                <width>120</width>
                <height>120</height>
                <depth>24</depth>
                <url>/icon-large.png</url>
            </icon>
        </iconList>
        <serviceList>
            <service>
                <serviceType>urn:schemas-upnp-org:service:ContentDirectory:1</serviceType>
                <serviceId>urn:upnp-org:serviceId:ContentDirectory</serviceId>
                <SCPDURL>/ContentDirectory.xml</SCPDURL>
                <controlURL>/ContentDirectory/control</controlURL>
                <eventSubURL>/ContentDirectory/event</eventSubURL>
            </service>
            <service>
                <serviceType>urn:schemas-upnp-org:service:ConnectionManager:1</serviceType>
                <serviceId>urn:upnp-org:serviceId:ConnectionManager</serviceId>
                <SCPDURL>/ConnectionManager.xml</SCPDURL>
                <controlURL>/ConnectionManager/control</controlURL>
                <eventSubURL>/ConnectionManager/event</eventSubURL>
            </service>
        </serviceList>
    </device>
</root>""".trimIndent()
    }

    private fun buildContentDirectoryXml(): String {
        return """<?xml version="1.0" encoding="utf-8"?>
<scpd xmlns="urn:schemas-upnp-org:service-1-0">
    <specVersion>
        <major>1</major>
        <minor>0</minor>
    </specVersion>
    <actionList>
        <action>
            <name>GetSearchCapabilities</name>
            <argumentList>
                <argument>
                    <name>SearchCaps</name>
                    <direction>out</direction>
                    <relatedStateVariable>SearchCapabilities</relatedStateVariable>
                </argument>
            </argumentList>
        </action>
        <action>
            <name>GetSortCapabilities</name>
            <argumentList>
                <argument>
                    <name>SortCaps</name>
                    <direction>out</direction>
                    <relatedStateVariable>SortCapabilities</relatedStateVariable>
                </argument>
            </argumentList>
        </action>
        <action>
            <name>GetSystemUpdateID</name>
            <argumentList>
                <argument>
                    <name>Id</name>
                    <direction>out</direction>
                    <relatedStateVariable>SystemUpdateID</relatedStateVariable>
                </argument>
            </argumentList>
        </action>
        <action>
            <name>Browse</name>
            <argumentList>
                <argument>
                    <name>ObjectID</name>
                    <direction>in</direction>
                    <relatedStateVariable>A_ARG_TYPE_ObjectID</relatedStateVariable>
                </argument>
                <argument>
                    <name>BrowseFlag</name>
                    <direction>in</direction>
                    <relatedStateVariable>A_ARG_TYPE_BrowseFlag</relatedStateVariable>
                </argument>
                <argument>
                    <name>Filter</name>
                    <direction>in</direction>
                    <relatedStateVariable>A_ARG_TYPE_Filter</relatedStateVariable>
                </argument>
                <argument>
                    <name>StartingIndex</name>
                    <direction>in</direction>
                    <relatedStateVariable>A_ARG_TYPE_Index</relatedStateVariable>
                </argument>
                <argument>
                    <name>RequestedCount</name>
                    <direction>in</direction>
                    <relatedStateVariable>A_ARG_TYPE_Count</relatedStateVariable>
                </argument>
                <argument>
                    <name>SortCriteria</name>
                    <direction>in</direction>
                    <relatedStateVariable>A_ARG_TYPE_SortCriteria</relatedStateVariable>
                </argument>
                <argument>
                    <name>Result</name>
                    <direction>out</direction>
                    <relatedStateVariable>A_ARG_TYPE_Result</relatedStateVariable>
                </argument>
                <argument>
                    <name>NumberReturned</name>
                    <direction>out</direction>
                    <relatedStateVariable>A_ARG_TYPE_Count</relatedStateVariable>
                </argument>
                <argument>
                    <name>TotalMatches</name>
                    <direction>out</direction>
                    <relatedStateVariable>A_ARG_TYPE_Count</relatedStateVariable>
                </argument>
                <argument>
                    <name>UpdateID</name>
                    <direction>out</direction>
                    <relatedStateVariable>A_ARG_TYPE_UpdateID</relatedStateVariable>
                </argument>
            </argumentList>
        </action>
    </actionList>
    <serviceStateTable>
        <stateVariable sendEvents="no">
            <name>A_ARG_TYPE_ObjectID</name>
            <dataType>string</dataType>
        </stateVariable>
        <stateVariable sendEvents="no">
            <name>A_ARG_TYPE_Result</name>
            <dataType>string</dataType>
        </stateVariable>
        <stateVariable sendEvents="no">
            <name>A_ARG_TYPE_BrowseFlag</name>
            <dataType>string</dataType>
        </stateVariable>
        <stateVariable sendEvents="no">
            <name>A_ARG_TYPE_Filter</name>
            <dataType>string</dataType>
        </stateVariable>
        <stateVariable sendEvents="no">
            <name>A_ARG_TYPE_SortCriteria</name>
            <dataType>string</dataType>
        </stateVariable>
        <stateVariable sendEvents="no">
            <name>A_ARG_TYPE_Index</name>
            <dataType>ui4</dataType>
        </stateVariable>
        <stateVariable sendEvents="no">
            <name>A_ARG_TYPE_Count</name>
            <dataType>ui4</dataType>
        </stateVariable>
        <stateVariable sendEvents="no">
            <name>A_ARG_TYPE_UpdateID</name>
            <dataType>ui4</dataType>
        </stateVariable>
        <stateVariable sendEvents="no">
            <name>SearchCapabilities</name>
            <dataType>string</dataType>
        </stateVariable>
        <stateVariable sendEvents="no">
            <name>SortCapabilities</name>
            <dataType>string</dataType>
        </stateVariable>
        <stateVariable sendEvents="yes">
            <name>SystemUpdateID</name>
            <dataType>ui4</dataType>
        </stateVariable>
    </serviceStateTable>
</scpd>""".trimIndent()
    }

    private fun buildConnectionManagerXml(): String {
        return """<?xml version="1.0" encoding="utf-8"?>
<scpd xmlns="urn:schemas-upnp-org:service-1-0">
    <specVersion>
        <major>1</major>
        <minor>0</minor>
    </specVersion>
    <actionList>
        <action>
            <name>GetProtocolInfo</name>
            <argumentList>
                <argument>
                    <name>Source</name>
                    <direction>out</direction>
                    <relatedStateVariable>SourceProtocolInfo</relatedStateVariable>
                </argument>
                <argument>
                    <name>Sink</name>
                    <direction>out</direction>
                    <relatedStateVariable>SinkProtocolInfo</relatedStateVariable>
                </argument>
            </argumentList>
        </action>
        <action>
            <name>GetCurrentConnectionIDs</name>
            <argumentList>
                <argument>
                    <name>ConnectionIDs</name>
                    <direction>out</direction>
                    <relatedStateVariable>CurrentConnectionIDs</relatedStateVariable>
                </argument>
            </argumentList>
        </action>
    </actionList>
    <serviceStateTable>
        <stateVariable sendEvents="no">
            <name>SourceProtocolInfo</name>
            <dataType>string</dataType>
        </stateVariable>
        <stateVariable sendEvents="no">
            <name>SinkProtocolInfo</name>
            <dataType>string</dataType>
        </stateVariable>
        <stateVariable sendEvents="no">
            <name>CurrentConnectionIDs</name>
            <dataType>string</dataType>
        </stateVariable>
    </serviceStateTable>
</scpd>""".trimIndent()
    }

    private fun handleConnectionManagerSoap(soapXml: String): String {
        if (soapXml.contains("GetProtocolInfo", ignoreCase = true)) {
            val protocols = "http-get:*:video/mp4:DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000," +
                            "http-get:*:video/x-matroska:DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000," +
                            "http-get:*:video/webm:*,http-get:*:video/quicktime:*,http-get:*:audio/mpeg:*,http-get:*:audio/mp4:*,http-get:*:*"
            return """<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
    <s:Body>
        <u:GetProtocolInfoResponse xmlns:u="urn:schemas-upnp-org:service:ConnectionManager:1">
            <Source>$protocols</Source>
            <Sink></Sink>
        </u:GetProtocolInfoResponse>
    </s:Body>
</s:Envelope>""".trimIndent()
        }

        return """<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
    <s:Body>
        <u:GetCurrentConnectionIDsResponse xmlns:u="urn:schemas-upnp-org:service:ConnectionManager:1">
            <ConnectionIDs>0</ConnectionIDs>
        </u:GetCurrentConnectionIDsResponse>
    </s:Body>
</s:Envelope>""".trimIndent()
    }

    private fun handleContentDirectorySoap(soapXml: String, ip: String, port: Int): String {
        FederatedMediaManager.refresh()

        val isMetadata = soapXml.contains("BrowseMetadata", ignoreCase = true)
        val objectId = Regex("""<ObjectID>(.*?)</ObjectID>""").find(soapXml)?.groupValues?.get(1) ?: "0"

        val didlBuilder = StringBuilder()
        didlBuilder.append("&lt;DIDL-Lite xmlns=&quot;urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/&quot; xmlns:dc=&quot;http://purl.org/dc/elements/1.1/&quot; xmlns:upnp=&quot;urn:schemas-upnp-org:metadata-1-0/upnp/&quot; xmlns:dlna=&quot;urn:schemas-dlna-org:metadata-1-0/&quot;&gt;")

        if (isMetadata && (objectId == "0" || objectId == "-1")) {
            // Root Container Metadata requested
            val total = getMediaItemsCount()
            didlBuilder.append("&lt;container id=&quot;0&quot; parentID=&quot;-1&quot; restricted=&quot;1&quot; childCount=&quot;$total&quot;&gt;")
            didlBuilder.append("&lt;dc:title&gt;PocketNode Media Server&lt;/dc:title&gt;")
            didlBuilder.append("&lt;upnp:class&gt;object.container.storageFolder&lt;/upnp:class&gt;")
            didlBuilder.append("&lt;/container&gt;")
            didlBuilder.append("&lt;/DIDL-Lite&gt;")

            return """<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
    <s:Body>
        <u:BrowseResponse xmlns:u="urn:schemas-upnp-org:service:ContentDirectory:1">
            <Result>${didlBuilder.toString()}</Result>
            <NumberReturned>1</NumberReturned>
            <TotalMatches>1</TotalMatches>
            <UpdateID>1</UpdateID>
        </u:BrowseResponse>
    </s:Body>
</s:Envelope>""".trimIndent()
        }

        // Enumerate items for BrowseDirectChildren
        val fedVideos = FederatedMediaManager.getByCategory("Movies") + FederatedMediaManager.getByCategory("Videos")
        var totalItems = fedVideos.size

        for (fItem in fedVideos) {
            val streamUrl = "http://$ip:$port/fedmedia/${fItem.id}"
            val thumbUrl = "http://$ip:$port/thumb/${fItem.id}"
            val cleanTitle = MediaThumbnailManager.cleanTitle(fItem.displayName)
            val mime = fItem.mimeType.ifEmpty { "video/mp4" }
            val proto = "http-get:*:$mime:DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000"

            didlBuilder.append("&lt;item id=&quot;${fItem.id}&quot; parentID=&quot;0&quot; restricted=&quot;0&quot;&gt;")
            didlBuilder.append("&lt;dc:title&gt;[${fItem.origin.badge}] $cleanTitle&lt;/dc:title&gt;")
            didlBuilder.append("&lt;upnp:class&gt;object.item.videoItem.movie&lt;/upnp:class&gt;")
            didlBuilder.append("&lt;upnp:albumArtURI&gt;$thumbUrl&lt;/upnp:albumArtURI&gt;")
            didlBuilder.append("&lt;res size=&quot;${fItem.sizeBytes}&quot; protocolInfo=&quot;$proto&quot;&gt;$streamUrl&lt;/res&gt;")
            didlBuilder.append("&lt;/item&gt;")
        }

        // Also append direct sandbox files if not present in federated
        val files = StorageManager.listFiles(StorageManager.getMoviesDir())
        for (file in files) {
            if (file.isFile && fedVideos.none { it.name == file.name }) {
                totalItems++
                val streamUrl = "http://$ip:$port/media/${file.name}"
                val cleanTitle = MediaThumbnailManager.cleanTitle(file.nameWithoutExtension)
                val proto = "http-get:*:video/mp4:DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000"

                didlBuilder.append("&lt;item id=&quot;${file.name}&quot; parentID=&quot;0&quot; restricted=&quot;0&quot;&gt;")
                didlBuilder.append("&lt;dc:title&gt;$cleanTitle&lt;/dc:title&gt;")
                didlBuilder.append("&lt;upnp:class&gt;object.item.videoItem.movie&lt;/upnp:class&gt;")
                didlBuilder.append("&lt;res size=&quot;${file.length()}&quot; protocolInfo=&quot;$proto&quot;&gt;$streamUrl&lt;/res&gt;")
                didlBuilder.append("&lt;/item&gt;")
            }
        }

        didlBuilder.append("&lt;/DIDL-Lite&gt;")

        return """<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
    <s:Body>
        <u:BrowseResponse xmlns:u="urn:schemas-upnp-org:service:ContentDirectory:1">
            <Result>${didlBuilder.toString()}</Result>
            <NumberReturned>$totalItems</NumberReturned>
            <TotalMatches>$totalItems</TotalMatches>
            <UpdateID>1</UpdateID>
        </u:BrowseResponse>
    </s:Body>
</s:Envelope>""".trimIndent()
    }

    private fun getMediaItemsCount(): Int {
        val fedVideos = FederatedMediaManager.getByCategory("Movies") + FederatedMediaManager.getByCategory("Videos")
        val files = StorageManager.listFiles(StorageManager.getMoviesDir()).filter { it.isFile }
        return (fedVideos.size + files.size).coerceAtLeast(1)
    }

    private fun getCustomOrGeneratedIcon(context: Context, size: Int): ByteArray {
        // 1. Check for custom logo file in PocketNode storage directory
        val baseDir = runCatching { StorageManager.getBaseDir() }.getOrNull()
        val candidateNames = listOf("logo.png", "icon.png", "server_icon.png", "logo.jpg", "icon.jpg")

        if (baseDir != null && baseDir.exists()) {
            for (candidate in candidateNames) {
                val f = File(baseDir, candidate)
                if (f.exists() && f.isFile && f.length() > 0) {
                    try {
                        val decoded = BitmapFactory.decodeFile(f.absolutePath)
                        if (decoded != null) {
                            val scaled = Bitmap.createScaledBitmap(decoded, size, size, true)
                            val bos = ByteArrayOutputStream()
                            scaled.compress(Bitmap.CompressFormat.PNG, 100, bos)
                            return bos.toByteArray()
                        }
                    } catch (e: Exception) {
                        Logger.w(LogTag.DLNA, "Could not load custom logo ${f.name}: ${e.message}")
                    }
                }
            }
        }

        // 2. Fallback to programmatic PocketNode logo
        return generateServerIcon(size)
    }

    private fun generateServerIcon(size: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Draw deep gradient background
        paint.color = Color.parseColor("#13162b")
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)

        // Draw glowing purple inner disc
        paint.color = Color.parseColor("#9333ea")
        canvas.drawCircle(size / 2f, size / 2f, size * 0.40f, paint)

        // Draw play triangle
        paint.color = Color.WHITE
        val path = android.graphics.Path().apply {
            val cx = size / 2f
            val cy = size / 2f
            val r = size * 0.22f
            moveTo(cx - r * 0.6f, cy - r)
            lineTo(cx + r, cy)
            lineTo(cx - r * 0.6f, cy + r)
            close()
        }
        canvas.drawPath(path, paint)

        val bos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, bos)
        return bos.toByteArray()
    }

    private fun getLocalIpAddress(): String? {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (networkInterface in interfaces) {
                if (networkInterface.isLoopback || !networkInterface.isUp) continue
                val addresses = Collections.list(networkInterface.inetAddresses)
                for (address in addresses) {
                    if (!address.isLoopbackAddress && address.hostAddress?.contains(":") == false) {
                        return address.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            Logger.e(LogTag.DLNA, "Failed to get local IP: ${e.message}")
        }
        return null
    }
}
