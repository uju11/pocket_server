package com.remotemedia

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.remotemedia.core.FederatedMediaManager
import com.remotemedia.core.HostMediaManager
import com.remotemedia.core.MeshShareManager
import com.remotemedia.core.NetworkShareManager
import com.remotemedia.core.LogLevel
import com.remotemedia.core.LogTag
import com.remotemedia.core.Logger
import com.remotemedia.core.StorageManager
import com.remotemedia.core.GpuComputeManager
import com.remotemedia.download.TelegramBotEngine
import com.remotemedia.download.TorrentEngine
import com.remotemedia.services.DlnaServer
import com.remotemedia.services.JackettServer
import com.remotemedia.services.JellyfinDiscoveryResponder
import com.remotemedia.services.JellyfinServer
import com.remotemedia.services.TelegramLocalServer
import com.remotemedia.services.WebHttpServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.NetworkInterface
import java.util.Collections

class ServerForegroundService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    // mDNS (ZeroConf / Bonjour) Manager
    private var nsdManager: NsdManager? = null
    private var nsdHttpListener: NsdManager.RegistrationListener? = null
    private var nsdJellyfinListener: NsdManager.RegistrationListener? = null

    fun registerMdnsServices(webPort: Int, jellyfinPort: Int, customHost: String = "pocketnode.local") {
        try {
            unregisterMdnsServices()
            nsdManager = getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return

            val cleanHost = customHost.removeSuffix(".local").trim()
            val serviceBase = if (cleanHost.isNotBlank() && !cleanHost.matches(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+"))) cleanHost else "PocketNode"

            val httpService = NsdServiceInfo().apply {
                serviceName = serviceBase
                serviceType = "_http._tcp."
                port = webPort
            }
            nsdHttpListener = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                    Logger.i(LogTag.SYSTEM, "mDNS registered: http://${serviceInfo.serviceName}.local:$webPort")
                }
                override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    Logger.w(LogTag.SYSTEM, "mDNS HTTP registration failed: $errorCode")
                }
                override fun onServiceUnregistered(arg0: NsdServiceInfo) {}
                override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
            }
            nsdManager?.registerService(httpService, NsdManager.PROTOCOL_DNS_SD, nsdHttpListener)

            val jellyfinService = NsdServiceInfo().apply {
                serviceName = if (serviceBase.contains("jelly", ignoreCase = true)) serviceBase else "$serviceBase-Jellyfin"
                serviceType = "_jellyfin._tcp."
                port = jellyfinPort
            }
            nsdJellyfinListener = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                    Logger.i(LogTag.JELLYFIN, "mDNS registered: http://${serviceInfo.serviceName}.local:$jellyfinPort")
                }
                override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    Logger.w(LogTag.JELLYFIN, "mDNS Jellyfin registration failed: $errorCode")
                }
                override fun onServiceUnregistered(arg0: NsdServiceInfo) {}
                override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
            }
            nsdManager?.registerService(jellyfinService, NsdManager.PROTOCOL_DNS_SD, nsdJellyfinListener)
        } catch (e: Exception) {
            Logger.w(LogTag.SYSTEM, "mDNS registration notice: ${e.message}")
        }
    }

    fun unregisterMdnsServices() {
        try {
            nsdHttpListener?.let { nsdManager?.unregisterService(it) }
            nsdJellyfinListener?.let { nsdManager?.unregisterService(it) }
        } catch (e: Exception) {
            // Ignore unregister errors on shutdown
        } finally {
            nsdHttpListener = null
            nsdJellyfinListener = null
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        StorageManager.init(this)
        com.remotemedia.core.PlaybackProgressManager.init(this)
        com.remotemedia.services.JellyfinManager.init(this)
        HostMediaManager.init(this)
        NetworkShareManager.init(this)
        MeshShareManager.init(this) // M6: Client Mesh sharing
        FederatedMediaManager.init(this) // M5 & M6: init after source managers
        GpuComputeManager.init(this)
        com.remotemedia.download.YtDlpManager.init(this)
        acquireLocks()
        _serverStartTimeMs = System.currentTimeMillis()
        _isRunning.value = true
        Logger.i(LogTag.SYSTEM, "Foreground Service created.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            ACTION_STOP -> {
                stopServer()
                return START_NOT_STICKY
            }
            ACTION_RESTART -> {
                restartAllServers()
                return START_STICKY
            }
            ACTION_TOGGLE_JELLYFIN -> {
                toggleJellyfin(intent.getBooleanExtra("enable", !_jellyfinRunning.value))
                return START_STICKY
            }
            ACTION_TOGGLE_DLNA -> {
                toggleDlna(intent.getBooleanExtra("enable", !_dlnaRunning.value))
                return START_STICKY
            }
            ACTION_TOGGLE_TORRENT -> {
                toggleTorrent(intent.getBooleanExtra("enable", !_torrentRunning.value))
                return START_STICKY
            }
            ACTION_TOGGLE_TELEGRAM -> {
                toggleTelegram(intent.getBooleanExtra("enable", !_telegramRunning.value))
                return START_STICKY
            }
            ACTION_TOGGLE_WEB -> {
                toggleWeb(intent.getBooleanExtra("enable", !_webRunning.value))
                return START_STICKY
            }
        }

        val notification = buildNotification("Initializing servers...")
        startForeground(NOTIFICATION_ID, notification)

        startAllServers()

        return START_STICKY
    }

    private fun startAllServers() {
        serviceScope.launch {
            val prefs = getSharedPreferences("remote_media_prefs", Context.MODE_PRIVATE)
            var jellyfinPort = prefs.getInt("jellyfin_port", 8096)
            if (jellyfinPort == 8097) {
                jellyfinPort = 8096
                prefs.edit().putInt("jellyfin_port", 8096).apply()
            }
            val webPort = prefs.getInt("web_port", 8080)
            val bindingMode = prefs.getString("binding_mode", "AUTO") ?: "AUTO"
            var customBoundIp = prefs.getString("custom_bound_ip", "") ?: ""
            if (customBoundIp.equals("pn.jelly", ignoreCase = true) || customBoundIp.equals("pn.jelly.local", ignoreCase = true)) {
                customBoundIp = "pocketnode"
                prefs.edit().putString("custom_bound_ip", "pocketnode").apply()
            }

            val ipAddress = getLocalIpAddress() ?: "127.0.0.1"
            _ipAddressState.value = ipAddress
            _jellyfinPortState.value = jellyfinPort
            _webPortState.value = webPort
            _bindingModeState.value = bindingMode

            val effectiveHost = when (bindingMode) {
                "CUSTOM", "STATIC" -> if (customBoundIp.isNotBlank()) customBoundIp else ipAddress
                "MDNS"   -> if (customBoundIp.isNotBlank()) {
                    if (customBoundIp.endsWith(".local", ignoreCase = true)) customBoundIp else "$customBoundIp.local"
                } else "pocketnode.local"
                else     -> ipAddress
            }
            _boundHostState.value = effectiveHost

            Logger.i(LogTag.SYSTEM, "Active Device Wi-Fi IP: $ipAddress | Bound Host: $effectiveHost ($bindingMode)")

            // 1. Web HTTP Server
            WebHttpServer.start(webPort)
            _webRunning.value = true

            // 2. Jellyfin API Server
            JellyfinServer.start(jellyfinPort)
            _jellyfinRunning.value = true

            // 3. Jellyfin UDP Discovery Responder (port 7359)
            JellyfinDiscoveryResponder.start(effectiveHost, jellyfinPort)

            // 4. mDNS (ZeroConf / Bonjour) Responder
            registerMdnsServices(webPort, jellyfinPort, effectiveHost)

            // 5. DLNA Server (Port 8090)
            DlnaServer.start(this@ServerForegroundService, 8090)
            _dlnaRunning.value = true

            // 6. Torrent Engine
            TorrentEngine.init(this@ServerForegroundService)
            _torrentRunning.value = true

            // 7. Telegram Bot Engine & Embedded 2GB Local Server
            val tgLocalEnabled = prefs.getBoolean("tg_local_server_enabled", false)
            if (tgLocalEnabled) {
                val apiId = prefs.getString("telegram_api_id", "") ?: ""
                val apiHash = prefs.getString("telegram_api_hash", "") ?: ""
                val ok = TelegramLocalServer.start(this@ServerForegroundService, 8081, apiId, apiHash)
                _telegramLocalServerRunning.value = ok
            }

            val tgStarted = TelegramBotEngine.start(this@ServerForegroundService)
            _telegramRunning.value = tgStarted

            // 8. Embedded Jackett Server (Port 9117)
            JackettServer.start(9117)
            _jackettRunning.value = true


            updateNotification("Server ACTIVE at http://$effectiveHost:$webPort")
            updateAppLauncherIcon(true)
            Logger.i(LogTag.SYSTEM, "All servers active! Web: http://$effectiveHost:$webPort | Jellyfin: http://$effectiveHost:$jellyfinPort | mDNS: http://pocketnode.local:$jellyfinPort")
        }
    }

    private fun stopServer() {
        serviceScope.launch {
            Logger.i(LogTag.SYSTEM, "Stopping all servers...")
            unregisterMdnsServices()
            JellyfinDiscoveryResponder.stop()
            TelegramLocalServer.stop(); _telegramLocalServerRunning.value = false
            TelegramBotEngine.stop();  _telegramRunning.value = false
            JackettServer.stop();      _jackettRunning.value  = false
            TorrentEngine.stop();      _torrentRunning.value  = false
            DlnaServer.stop();         _dlnaRunning.value     = false
            JellyfinServer.stop();     _jellyfinRunning.value = false
            WebHttpServer.stop();      _webRunning.value      = false
            GpuComputeManager.setGpuComputeEnabled(false)
            releaseLocks()
            _serverStartTimeMs = 0L
            _isRunning.value = false
            updateAppLauncherIcon(false)
            Logger.i(LogTag.SYSTEM, "All servers stopped.")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    // ── Individual Service Control ────────────────────────────────────────────
    // Called from UI to toggle a single service without touching the others.

    fun toggleJellyfin(enable: Boolean) {
        serviceScope.launch {
            if (enable) {
                JellyfinServer.start(_jellyfinPortState.value)
                JellyfinDiscoveryResponder.start(_ipAddressState.value, _jellyfinPortState.value)
                _jellyfinRunning.value = true
                Logger.i(LogTag.JELLYFIN, "Jellyfin restarted on port ${_jellyfinPortState.value}")
            } else {
                JellyfinDiscoveryResponder.stop()
                JellyfinServer.stop()
                _jellyfinRunning.value = false
                Logger.i(LogTag.JELLYFIN, "Jellyfin stopped individually.")
            }
        }
    }

    fun toggleDlna(enable: Boolean) {
        serviceScope.launch {
            if (enable) { DlnaServer.start(this@ServerForegroundService, 8090); _dlnaRunning.value = true }
            else        { DlnaServer.stop(); _dlnaRunning.value = false }
            Logger.i(LogTag.DLNA, "DLNA ${if (enable) "started" else "stopped"}")
        }
    }

    fun toggleTorrent(enable: Boolean) {
        serviceScope.launch {
            if (enable) { TorrentEngine.init(this@ServerForegroundService); _torrentRunning.value = true }
            else        { TorrentEngine.stop(); _torrentRunning.value = false }
            Logger.i(LogTag.TORRENT, "Torrent engine ${if (enable) "started" else "stopped"}")
        }
    }

    fun toggleTelegram(enable: Boolean) {
        serviceScope.launch {
            if (enable) {
                val ok = TelegramBotEngine.start(this@ServerForegroundService)
                _telegramRunning.value = ok
            } else {
                TelegramBotEngine.stop()
                _telegramRunning.value = false
            }
            Logger.i(LogTag.TELEGRAM, "Telegram bot ${if (enable) "started" else "stopped"}")
        }
    }

    fun toggleWeb(enable: Boolean) {
        serviceScope.launch {
            if (enable) { WebHttpServer.start(_webPortState.value); _webRunning.value = true }
            else        { WebHttpServer.stop(); _webRunning.value = false }
            Logger.i(LogTag.HTTP, "Web panel ${if (enable) "started" else "stopped"}")
        }
    }

    fun toggleJackett(enable: Boolean) {
        serviceScope.launch {
            if (enable) {
                JackettServer.start(9117)
                _jackettRunning.value = true
            } else {
                JackettServer.stop()
                _jackettRunning.value = false
            }
            Logger.i(LogTag.SYSTEM, "Jackett service ${if (enable) "enabled" else "disabled"}")
        }
    }

    fun toggleTelegramLocalServer(enable: Boolean, apiId: String = "", apiHash: String = "") {
        serviceScope.launch {
            val prefs = getSharedPreferences("remote_media_prefs", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("tg_local_server_enabled", enable).apply()

            if (enable) {
                val effectiveApiId = apiId.ifBlank { prefs.getString("telegram_api_id", "") ?: "" }
                val effectiveApiHash = apiHash.ifBlank { prefs.getString("telegram_api_hash", "") ?: "" }
                val ok = TelegramLocalServer.start(this@ServerForegroundService, 8081, effectiveApiId, effectiveApiHash)
                _telegramLocalServerRunning.value = ok
                TelegramBotEngine.stop()
                TelegramBotEngine.start(this@ServerForegroundService)
            } else {
                TelegramLocalServer.stop()
                _telegramLocalServerRunning.value = false
                TelegramBotEngine.stop()
                TelegramBotEngine.start(this@ServerForegroundService)
            }
            Logger.i(LogTag.TELEGRAM, "Telegram Local 2GB Server ${if (enable) "started on port 8081" else "stopped"}")
        }
    }

    fun toggleSubService(name: String, enable: Boolean) {
        when (name) {
            "jellyfin" -> toggleJellyfin(enable)
            "web" -> toggleWeb(enable)
            "dlna" -> toggleDlna(enable)
            "torrent" -> toggleTorrent(enable)
            "telegram" -> toggleTelegram(enable)
            "telegram_local_server" -> toggleTelegramLocalServer(enable)
            "jackett" -> toggleJackett(enable)
        }
    }

    fun restartSubService(name: String) {
        serviceScope.launch {
            when (name) {
                "jellyfin" -> { toggleJellyfin(false); delay(600); toggleJellyfin(true) }
                "web" -> { toggleWeb(false); delay(600); toggleWeb(true) }
                "dlna" -> { toggleDlna(false); delay(600); toggleDlna(true) }
                "torrent" -> { toggleTorrent(false); delay(600); toggleTorrent(true) }
                "telegram" -> { toggleTelegram(false); delay(600); toggleTelegram(true) }
                "telegram_local_server" -> { toggleTelegramLocalServer(false); delay(600); toggleTelegramLocalServer(true) }
                "jackett" -> { toggleJackett(false); delay(600); toggleJackett(true) }
            }
        }
    }

    fun restartAllServers() {
        serviceScope.launch {
            Logger.i(LogTag.SYSTEM, "Restarting all servers...")
            updateNotification("Restarting server daemons...")

            unregisterMdnsServices()
            JellyfinDiscoveryResponder.stop()
            try { TelegramBotEngine.stop() } catch (e: Exception) { Logger.e(LogTag.TELEGRAM, "Error stopping telegram: ${e.message}") }
            try { TorrentEngine.stop() } catch (e: Exception) { Logger.e(LogTag.TORRENT, "Error stopping torrent: ${e.message}") }
            try { DlnaServer.stop() } catch (e: Exception) { Logger.e(LogTag.DLNA, "Error stopping dlna: ${e.message}") }
            try { JellyfinServer.stop() } catch (e: Exception) { Logger.e(LogTag.JELLYFIN, "Error stopping jellyfin: ${e.message}") }
            try { WebHttpServer.stop() } catch (e: Exception) { Logger.e(LogTag.HTTP, "Error stopping web: ${e.message}") }

            kotlinx.coroutines.delay(400)

            startAllServers()
            Logger.i(LogTag.SYSTEM, "All servers restarted successfully.")
        }
    }

    private fun updateAppLauncherIcon(serverRunning: Boolean) {
        // NOTE: Dynamically modifying activity-alias component enabled settings causes
        // Android OS ActivityManager to kill the active application process.
        // We log status instead of modifying PackageManager settings during runtime.
        Logger.i(LogTag.SYSTEM, "Server status: ${if (serverRunning) "ACTIVE" else "STANDBY"}")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) instance = null
        stopServer()
        serviceJob.cancel()
    }

    private fun acquireLocks() {
        val prefs = getSharedPreferences("remote_media_prefs", Context.MODE_PRIVATE)
        val keepWakeLock = prefs.getBoolean("keep_wake_lock", true)
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (e: Exception) {
            Logger.e(LogTag.SYSTEM, "Error releasing prior wakelock: ${e.message}")
        }
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RemoteMediaServer::WakeLock").apply {
            setReferenceCounted(false)
            if (keepWakeLock) {
                acquire()
                Logger.i(LogTag.SYSTEM, "WakeLock acquired INDEFINITELY (keep_wake_lock = true)")
            } else {
                acquire(10 * 60 * 1000L /*10 mins fallback*/)
                Logger.i(LogTag.SYSTEM, "WakeLock acquired with 10m fallback (keep_wake_lock = false)")
            }
        }

        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        try {
            if (wifiLock?.isHeld == true) wifiLock?.release()
        } catch (_: Exception) {}
        wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "RemoteMediaServer::WifiLock").apply {
            setReferenceCounted(false)
            acquire()
        }

        try {
            if (multicastLock?.isHeld == true) multicastLock?.release()
        } catch (_: Exception) {}
        multicastLock = wifiManager.createMulticastLock("RemoteMediaServer::MulticastLock").apply {
            setReferenceCounted(false)
            acquire()
            Logger.i(LogTag.SYSTEM, "MulticastLock acquired for TV Jellyfin discovery & DLNA")
        }
    }

    private fun releaseLocks() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
            if (wifiLock?.isHeld == true) wifiLock?.release()
            if (multicastLock?.isHeld == true) multicastLock?.release()
        } catch (e: Exception) {
            Logger.e(LogTag.SYSTEM, "Error releasing locks: ${e.message}")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Remote Media Server Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows status of Jellyfin, Web, Torrent, and DLNA background servers."
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, ServerForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Remote Media Server")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_upload)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_delete, "Stop Server", stopPendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(contentText: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification(contentText))
    }

    companion object {
        const val CHANNEL_ID = "remote_media_server_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_RESTART = "ACTION_RESTART"
        const val ACTION_TOGGLE_JELLYFIN = "ACTION_TOGGLE_JELLYFIN"
        const val ACTION_TOGGLE_DLNA     = "ACTION_TOGGLE_DLNA"
        const val ACTION_TOGGLE_TORRENT  = "ACTION_TOGGLE_TORRENT"
        const val ACTION_TOGGLE_TELEGRAM = "ACTION_TOGGLE_TELEGRAM"
        const val ACTION_TOGGLE_WEB      = "ACTION_TOGGLE_WEB"

        var instance: ServerForegroundService? = null
            private set

        fun getLocalIpAddress(context: Context? = null): String? {
            try {
                // 1. Try WifiManager first for exact Wi-Fi IP
                val ctx = context?.applicationContext ?: instance?.applicationContext
                val wifiManager = ctx?.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                val ipInt = wifiManager?.connectionInfo?.ipAddress ?: 0
                if (ipInt != 0) {
                    return String.format(
                        "%d.%d.%d.%d",
                        ipInt and 0xff,
                        ipInt shr 8 and 0xff,
                        ipInt shr 16 and 0xff,
                        ipInt shr 24 and 0xff
                    )
                }

                // 2. Iterate interfaces, prioritizing wlan0 / Wi-Fi
                val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
                val wifiInterface = interfaces.firstOrNull { it.name.lowercase().contains("wlan") }
                if (wifiInterface != null) {
                    val addresses = Collections.list(wifiInterface.inetAddresses)
                    val ip = addresses.firstOrNull { !it.isLoopbackAddress && it.hostAddress?.contains(":") == false }
                    if (ip != null) return ip.hostAddress
                }

                for (netInterface in interfaces) {
                    val addresses = Collections.list(netInterface.inetAddresses)
                    for (address in addresses) {
                        if (!address.isLoopbackAddress && address.hostAddress?.contains(":") == false) {
                            return address.hostAddress
                        }
                    }
                }
            } catch (e: Exception) {
                Logger.e(LogTag.SYSTEM, "Failed to get local IP: ${e.message}")
            }
            return null
        }

        private var _serverStartTimeMs = 0L
        val serverStartTimeMs: Long get() = _serverStartTimeMs

        // Master
        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        // Network
        private val _ipAddressState = MutableStateFlow("127.0.0.1")
        val ipAddressState: StateFlow<String> = _ipAddressState.asStateFlow()

        private val _boundHostState = MutableStateFlow("pocketnode.local")
        val boundHostState: StateFlow<String> = _boundHostState.asStateFlow()

        private val _bindingModeState = MutableStateFlow("AUTO")
        val bindingModeState: StateFlow<String> = _bindingModeState.asStateFlow()

        private val _jellyfinPortState = MutableStateFlow(8096)
        val jellyfinPortState: StateFlow<Int> = _jellyfinPortState.asStateFlow()

        private val _webPortState = MutableStateFlow(8080)
        val webPortState: StateFlow<Int> = _webPortState.asStateFlow()

        fun initFromPrefs(context: Context) {
            val prefs = context.getSharedPreferences("remote_media_prefs", Context.MODE_PRIVATE)
            var jellyfinPort = prefs.getInt("jellyfin_port", 8096)
            if (jellyfinPort == 8097) {
                jellyfinPort = 8096
                prefs.edit().putInt("jellyfin_port", 8096).apply()
            }
            val webPort = prefs.getInt("web_port", 8080)
            val bindingMode = prefs.getString("binding_mode", "AUTO") ?: "AUTO"
            var customBoundIp = prefs.getString("custom_bound_ip", "") ?: ""

            if (customBoundIp.equals("pn.jelly", ignoreCase = true) || customBoundIp.equals("pn.jelly.local", ignoreCase = true)) {
                customBoundIp = "pocketnode"
                prefs.edit().putString("custom_bound_ip", "pocketnode").apply()
            }

            val currentIp = getLocalIpAddress()
            if (currentIp != null) {
                _ipAddressState.value = currentIp
            }

            _jellyfinPortState.value = jellyfinPort
            _webPortState.value = webPort
            _bindingModeState.value = bindingMode

            val effectiveHost = when (bindingMode) {
                "CUSTOM", "STATIC" -> if (customBoundIp.isNotBlank()) customBoundIp else (currentIp ?: "pocketnode.local")
                "MDNS"   -> if (customBoundIp.isNotBlank()) {
                    if (customBoundIp.endsWith(".local", ignoreCase = true)) customBoundIp else "$customBoundIp.local"
                } else "pocketnode.local"
                else     -> currentIp ?: "pocketnode.local"
            }
            _boundHostState.value = effectiveHost
        }

        fun updateNetworkBinding(context: Context, mode: String, customHostOrIp: String, jellyfinPort: Int, webPort: Int) {
            val prefs = context.getSharedPreferences("remote_media_prefs", Context.MODE_PRIVATE)

            var cleanHost = customHostOrIp.trim()
                .removePrefix("http://")
                .removePrefix("https://")
                .substringBefore("/")
            if (cleanHost.equals("pn.jelly", ignoreCase = true) || cleanHost.equals("pn.jelly.local", ignoreCase = true)) {
                cleanHost = "pocketnode"
            }
            var effectiveJellyfinPort = jellyfinPort
            if (cleanHost.contains(":")) {
                val splitPort = cleanHost.substringAfter(":").toIntOrNull()
                cleanHost = cleanHost.substringBefore(":")
                if (splitPort != null && splitPort in 1..65535) {
                    effectiveJellyfinPort = splitPort
                }
            }

            prefs.edit()
                .putString("binding_mode", mode)
                .putString("custom_bound_ip", cleanHost)
                .putInt("jellyfin_port", effectiveJellyfinPort)
                .putInt("web_port", webPort)
                .apply()

            _bindingModeState.value = mode
            _jellyfinPortState.value = effectiveJellyfinPort
            _webPortState.value = webPort

            val detectedIp = getLocalIpAddress(context) ?: _ipAddressState.value
            _ipAddressState.value = detectedIp

            val effectiveHost = when (mode) {
                "CUSTOM", "STATIC" -> if (cleanHost.isNotBlank()) cleanHost else detectedIp
                "MDNS"   -> if (cleanHost.isNotBlank()) {
                    if (cleanHost.endsWith(".local", ignoreCase = true)) cleanHost else "$cleanHost.local"
                } else "pocketnode.local"
                else     -> detectedIp
            }
            _boundHostState.value = effectiveHost

            if (_isRunning.value) {
                JellyfinDiscoveryResponder.start(effectiveHost, effectiveJellyfinPort)
                instance?.registerMdnsServices(webPort, effectiveJellyfinPort, effectiveHost)
                instance?.updateNotification("Server ACTIVE at http://$effectiveHost:$effectiveJellyfinPort")
            }
        }

        // Per-service running states — observable by UI
        private val _jellyfinRunning = MutableStateFlow(false)
        val jellyfinRunning: StateFlow<Boolean> = _jellyfinRunning.asStateFlow()

        private val _dlnaRunning = MutableStateFlow(false)
        val dlnaRunning: StateFlow<Boolean> = _dlnaRunning.asStateFlow()

        private val _torrentRunning = MutableStateFlow(false)
        val torrentRunning: StateFlow<Boolean> = _torrentRunning.asStateFlow()

        private val _telegramRunning = MutableStateFlow(false)
        val telegramRunning: StateFlow<Boolean> = _telegramRunning.asStateFlow()

        private val _telegramLocalServerRunning = MutableStateFlow(false)
        val telegramLocalServerRunning: StateFlow<Boolean> = _telegramLocalServerRunning.asStateFlow()

        private val _webRunning = MutableStateFlow(false)
        val webRunning: StateFlow<Boolean> = _webRunning.asStateFlow()

        private val _jackettRunning = MutableStateFlow(false)
        val jackettRunning: StateFlow<Boolean> = _jackettRunning.asStateFlow()


        // Sub-service toggles
        fun toggleJellyfin(enable: Boolean) { instance?.toggleJellyfin(enable) }
        fun toggleDlna(enable: Boolean) { instance?.toggleDlna(enable) }
        fun toggleTorrent(enable: Boolean) { instance?.toggleTorrent(enable) }
        fun toggleTelegram(enable: Boolean) { instance?.toggleTelegram(enable) }
        fun toggleTelegramLocalServer(enable: Boolean, apiId: String = "", apiHash: String = "") {
            instance?.toggleTelegramLocalServer(enable, apiId, apiHash)
        }
        fun toggleWeb(enable: Boolean) { instance?.toggleWeb(enable) }
        fun toggleJackett(enable: Boolean) { instance?.toggleJackett(enable) }

        fun restartJackett() {
            instance?.let { s ->
                s.toggleJackett(false)
                s.serviceScope.launch {
                    delay(600)
                    s.toggleJackett(true)
                }
            }
        }


        // Sub-service restarts
        fun restartJellyfin() {
            instance?.let { s ->
                s.toggleJellyfin(false)
                s.serviceScope.launch {
                    kotlinx.coroutines.delay(600)
                    s.toggleJellyfin(true)
                }
            }
        }

        fun restartDlna() {
            instance?.let { s ->
                s.toggleDlna(false)
                s.serviceScope.launch {
                    kotlinx.coroutines.delay(600)
                    s.toggleDlna(true)
                }
            }
        }

        fun restartTorrent() {
            instance?.let { s ->
                s.toggleTorrent(false)
                s.serviceScope.launch {
                    kotlinx.coroutines.delay(600)
                    s.toggleTorrent(true)
                }
            }
        }

        fun restartTelegram() {
            instance?.let { s ->
                s.toggleTelegram(false)
                s.serviceScope.launch {
                    kotlinx.coroutines.delay(600)
                    s.toggleTelegram(true)
                }
            }
        }

        fun restartWeb() {
            instance?.let { s ->
                s.toggleWeb(false)
                s.serviceScope.launch {
                    kotlinx.coroutines.delay(600)
                    s.toggleWeb(true)
                }
            }
        }

        fun updateWakeLock() {
            instance?.acquireLocks()
        }
    }
}
