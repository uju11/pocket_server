package com.remotemedia

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.remotemedia.core.FederatedMediaManager
import com.remotemedia.core.HostMediaManager
import com.remotemedia.core.MeshShareManager
import com.remotemedia.core.NetworkShareManager
import com.remotemedia.core.PermissionsManager
import com.remotemedia.core.StorageManager
import com.remotemedia.core.SystemMetricsManager
import com.remotemedia.download.TorrentTrackersManager
import com.remotemedia.services.JackettServer
import com.remotemedia.services.JellyfinManager
import com.remotemedia.ui.*
import com.remotemedia.ui.theme.RemoteMediaTheme
import com.remotemedia.ui.theme.TacticalCanvas
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        StorageManager.init(this)
        com.remotemedia.core.PlaybackProgressManager.init(this)
        HostMediaManager.init(this)
        NetworkShareManager.init(this)
        MeshShareManager.init(this) // M6: Client Mesh sharing
        FederatedMediaManager.init(this) // M5 & M6: init after all source managers
        JellyfinManager.init(this)
        com.remotemedia.services.JellyfinClusterManager.init(this)
        PermissionsManager.init(this)
        TorrentTrackersManager.init(this)
        JackettServer.init(this)
        com.remotemedia.download.YtDlpManager.init(this)
        SystemMetricsManager.start(this)
        ServerForegroundService.initFromPrefs(this)
        checkNotificationPermission()

        val prefs = getSharedPreferences("remote_media_prefs", Context.MODE_PRIVATE)

        setContent {
            RemoteMediaTheme {
                var isFirstLaunchCompleted by remember {
                    mutableStateOf(prefs.getBoolean("is_first_launch_completed", false))
                }
                var showTerminal by remember { mutableStateOf(false) }
                var showSettings by remember { mutableStateOf(false) }
                var showDownloads by remember { mutableStateOf(false) }
                var showPermissions by remember { mutableStateOf(false) }
                var showGuide by remember { mutableStateOf(false) }
                var showCredits by remember { mutableStateOf(false) }
                var selectedTab by remember { mutableStateOf(0) }

                val isRunning by ServerForegroundService.isRunning.collectAsState()

                // Live UTC Clock
                var liveUtcTime by remember { mutableStateOf("") }
                LaunchedEffect(Unit) {
                    while (true) {
                        val sdf = SimpleDateFormat("HH:mm:ss 'UTC'", Locale.getDefault()).apply {
                            timeZone = TimeZone.getTimeZone("UTC")
                        }
                        liveUtcTime = sdf.format(Date())
                        delay(1000)
                    }
                }

                // Server uptime counter (shown in status bar instead of clock)
                var uptimeSeconds by remember { mutableStateOf(0L) }
                LaunchedEffect(isRunning) {
                    if (isRunning) { while (isRunning) { delay(1000); uptimeSeconds++ } }
                    else { uptimeSeconds = 0L }
                }
                val uptimeFormatted: String = run {
                    val d = uptimeSeconds / 86400
                    val h = (uptimeSeconds % 86400) / 3600
                    val m = (uptimeSeconds % 3600) / 60
                    "${d}d ${h.toString().padStart(2,'0')}h ${m.toString().padStart(2,'0')}m"
                }

                val metrics by SystemMetricsManager.metricsState.collectAsState()

                if (!isFirstLaunchCompleted) {
                    OnboardingScreen(
                        onFinishOnboarding = { isFirstLaunchCompleted = true }
                    )
                } else if (showTerminal) {
                    BackHandler { showTerminal = false }

                    // PocketNode Terminal Console (CLI)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(TacticalCanvas)
                    ) {
                        PocketNodeStatusBar(
                            liveUtcTime = liveUtcTime,
                            batteryPercent = metrics.batteryPercent,
                            isCharging = metrics.isCharging,
                            serverUptime = uptimeFormatted,
                            isServerRunning = isRunning
                        )

                        PocketNodeHeader(
                            onOpenTerminal = {},
                            onOpenSettings = { showTerminal = false; showSettings = true },
                            isSubPage = true,
                            onBack = { showTerminal = false },
                            pageTitle = "CLI / TTY"
                        )

                        TerminalScreen(
                            onBack = { showTerminal = false },
                            showHeader = false
                        )
                    }
                } else if (showSettings) {
                    // Back handler returns to HUD dashboard
                    BackHandler { showSettings = false }

                    // PocketNode Config Deck (Settings UI)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(TacticalCanvas)
                    ) {
                        PocketNodeStatusBar(
                            liveUtcTime = liveUtcTime,
                            batteryPercent = metrics.batteryPercent,
                            isCharging = metrics.isCharging,
                            serverUptime = uptimeFormatted,
                            isServerRunning = isRunning
                        )

                        PocketNodeHeader(
                            onOpenTerminal = { showSettings = false; showTerminal = true },
                            onOpenSettings = { showSettings = false },
                            isSubPage = true,
                            onBack = { showSettings = false },
                            pageTitle = "SETTINGS"
                        )

                        // Horizontal tab bar
                        PocketNodeTabBar(
                            selectedTab = selectedTab,
                            onTabSelected = { selectedTab = it },
                            activeIndicators = mapOf(2 to true, 3 to true)
                        )

                        // Tab Content
                        Box(modifier = Modifier.fillMaxSize()) {
                            when (selectedTab) {
                                0 -> GeneralTabScreen(onOpenTerminal = { showTerminal = true })
                                1 -> JellyfinTabScreen()
                                2 -> TelegramTabScreen()
                                3 -> TorrentTabScreen()
                                4 -> WebTabScreen()
                            }
                        }
                    }
                } else if (showDownloads) {
                    BackHandler { showDownloads = false }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(TacticalCanvas)
                    ) {
                        PocketNodeStatusBar(
                            liveUtcTime = liveUtcTime,
                            batteryPercent = metrics.batteryPercent,
                            isCharging = metrics.isCharging,
                            serverUptime = uptimeFormatted,
                            isServerRunning = isRunning
                        )

                        PocketNodeHeader(
                            onOpenTerminal = { showDownloads = false; showTerminal = true },
                            onOpenSettings = { showDownloads = false; showSettings = true },
                            isSubPage = true,
                            onBack = { showDownloads = false },
                            pageTitle = "DOWNLOADS"
                        )

                        DownloadsScreen(onBack = { showDownloads = false })
                    }
                } else if (showGuide) {
                    BackHandler { showGuide = false }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(TacticalCanvas)
                    ) {
                        PocketNodeStatusBar(
                            liveUtcTime = liveUtcTime,
                            batteryPercent = metrics.batteryPercent,
                            isCharging = metrics.isCharging,
                            serverUptime = uptimeFormatted,
                            isServerRunning = isRunning
                        )

                        PocketNodeHeader(
                            onOpenTerminal = { showGuide = false; showTerminal = true },
                            onOpenSettings = { showGuide = false; showSettings = true },
                            isSubPage = true,
                            onBack = { showGuide = false },
                            pageTitle = "FIELD GUIDE"
                        )

                        GuideScreen(
                            onReplayOnboarding = {
                                showGuide = false
                                isFirstLaunchCompleted = false
                            }
                        )
                    }
                } else if (showCredits) {
                    BackHandler { showCredits = false }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(TacticalCanvas)
                    ) {
                        PocketNodeStatusBar(
                            liveUtcTime = liveUtcTime,
                            batteryPercent = metrics.batteryPercent,
                            isCharging = metrics.isCharging,
                            serverUptime = uptimeFormatted,
                            isServerRunning = isRunning
                        )

                        PocketNodeHeader(
                            onOpenTerminal = { showCredits = false; showTerminal = true },
                            onOpenSettings = { showCredits = false; showSettings = true },
                            isSubPage = true,
                            onBack = { showCredits = false },
                            pageTitle = "CREDITS"
                        )

                        CreditsScreen()
                    }
                } else if (showPermissions) {
                    BackHandler { showPermissions = false }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(TacticalCanvas)
                    ) {
                        PocketNodeStatusBar(
                            liveUtcTime = liveUtcTime,
                            batteryPercent = metrics.batteryPercent,
                            isCharging = metrics.isCharging,
                            serverUptime = uptimeFormatted,
                            isServerRunning = isRunning
                        )

                        PocketNodeHeader(
                            onOpenTerminal = { showPermissions = false; showTerminal = true },
                            onOpenSettings = { showPermissions = false; showSettings = true },
                            isSubPage = true,
                            onBack = { showPermissions = false },
                            pageTitle = "PERMISSIONS"
                        )

                        PermissionsScreen(onBack = { showPermissions = false })
                    }
                } else {
                    // PocketNode Default Landing Screen: HUD Dashboard
                    MainScreen(
                        onToggleServer = { toggleServerService(it) },
                        onRestartServer = { restartServerService() },
                        onOpenTerminal = { showTerminal = true },
                        onOpenSettings = { showSettings = true },
                        onOpenDownloads = { showDownloads = true },
                        onOpenPermissions = { showPermissions = true },
                        onOpenGuide = { showGuide = true },
                        onOpenCredits = { showCredits = true }
                    )
                }
            }
        }
    }

    private fun restartServerService() {
        if (ServerForegroundService.instance != null) {
            ServerForegroundService.instance?.restartAllServers()
        } else {
            toggleServerService(true)
        }
    }

    private fun toggleServerService(start: Boolean) {
        val intent = Intent(this, ServerForegroundService::class.java).apply {
            action = if (start) ServerForegroundService.ACTION_START else ServerForegroundService.ACTION_STOP
        }
        if (start) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } else {
            startService(intent)
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
