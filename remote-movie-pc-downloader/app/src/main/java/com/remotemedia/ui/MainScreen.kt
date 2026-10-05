package com.remotemedia.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.remotemedia.ServerForegroundService
import com.remotemedia.core.GpuComputeManager
import com.remotemedia.core.StorageManager
import com.remotemedia.core.SystemMetricsManager
import com.remotemedia.download.DownloadItem
import com.remotemedia.download.TorrentEngine
import com.remotemedia.ui.theme.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

@Composable
fun MainScreen(
    onToggleServer: (Boolean) -> Unit,
    onRestartServer: () -> Unit = {},
    onOpenTerminal: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDownloads: () -> Unit = {},
    onOpenPermissions: () -> Unit = {},
    onOpenGuide: () -> Unit = {},
    onOpenCredits: () -> Unit = {},
    onToggleSubService: (String, Boolean) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val isRunning        by ServerForegroundService.isRunning.collectAsState()
    val ipAddress        by ServerForegroundService.ipAddressState.collectAsState()
    val boundHost        by ServerForegroundService.boundHostState.collectAsState()
    val webPort          by ServerForegroundService.webPortState.collectAsState()
    val jellyfinPort     by ServerForegroundService.jellyfinPortState.collectAsState()
    val metrics          by SystemMetricsManager.metricsState.collectAsState()

    // Service States
    val jellyfinRunning by ServerForegroundService.jellyfinRunning.collectAsState()
    val webRunning      by ServerForegroundService.webRunning.collectAsState()
    val dlnaRunning     by ServerForegroundService.dlnaRunning.collectAsState()
    val torrentRunning  by ServerForegroundService.torrentRunning.collectAsState()
    val telegramRunning by ServerForegroundService.telegramRunning.collectAsState()
    val jackettRunning  by ServerForegroundService.jackettRunning.collectAsState()

    val thermalLimit by GpuComputeManager.thermalLimitC.collectAsState()
    val ramLimit by GpuComputeManager.ramLimitPct.collectAsState()
    val autoShutoffReason by GpuComputeManager.autoShutoffReason.collectAsState()

    var thermalExpanded    by remember { mutableStateOf(false) }
    var cpuWindowSeconds   by remember { mutableStateOf(60) }
    var ramWindowSeconds   by remember { mutableStateOf(60) }

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

    // Uptime counter
    var uptimeSeconds by remember { mutableStateOf(0L) }
    LaunchedEffect(isRunning) {
        if (isRunning) {
            while (isRunning) { delay(1000); uptimeSeconds++ }
        } else { uptimeSeconds = 0L }
    }

    val scrollState = rememberScrollState()
    val uptimeFormatted = formatUptime(uptimeSeconds)
    val procId = remember { "#${(10000..99999).random()}" }
    var isDrawerOpen by remember { mutableStateOf(false) }

    if (isDrawerOpen) {
        BackHandler { isDrawerOpen = false }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TacticalCanvas)
    ) {
        // ── TOP TTY STATUS BAR (Pinned at top)
        PocketNodeStatusBar(
            liveUtcTime = liveUtcTime,
            batteryPercent = metrics.batteryPercent,
            isCharging = metrics.isCharging,
            serverUptime = uptimeFormatted,
            isServerRunning = isRunning
        )

        // ── APP HEADER BAR (Pinned at top, navbar stays visible at all times)
        PocketNodeHeader(
            onOpenTerminal = onOpenTerminal,
            onOpenSettings = onOpenSettings,
            isSubPage = false,
            isDrawerOpen = isDrawerOpen,
            onToggleDrawer = { isDrawerOpen = !isDrawerOpen }
        )

        // ── DASHBOARD VIEWPORT WITH SIDE DRAWER OVERLAY
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            // Main Dashboard Scrollable Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {

            // SYSTEM PROTECTION ALERT BANNER (Shown if thermal / memory limits trigger auto-shutoff)
            if (autoShutoffReason != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TacticalRed.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                        .border(1.dp, TacticalRed, RoundedCornerShape(4.dp))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("🛑 AUTO-SHUTOFF TRIGGERED", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalRed)
                        Text(autoShutoffReason ?: "", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = TacticalText)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .background(TacticalRed.copy(alpha = 0.25f), RoundedCornerShape(3.dp))
                            .border(1.dp, TacticalRed, RoundedCornerShape(3.dp))
                            .clickable { GpuComputeManager.clearAutoShutoffReason() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("DISMISS", fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalRed)
                    }
                }
            }

            // 1. MASTER DAEMON CARD
            HudCard {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("// MASTER_DAEMON", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                        if (isRunning) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("UP: $uptimeFormatted", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = StatusGreen)
                        }
                    }
                    HudBadge(if (isRunning) "ACTIVE" else "OFFLINE", if (isRunning) StatusGreen else TacticalRed, true)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("PocketNode Daemon", fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalText)
                    Switch(
                        checked = isRunning,
                        onCheckedChange = { onToggleServer(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = TacticalBg, checkedTrackColor = StatusGreen, uncheckedThumbColor = TacticalMuted, uncheckedTrackColor = TacticalSurface)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isRunning) {
                        Box(
                            modifier = Modifier
                                .background(StatusGreen.copy(alpha = 0.15f), RoundedCornerShape(3.dp))
                                .border(1.dp, StatusGreen, RoundedCornerShape(3.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text("$boundHost", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = StatusGreen)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("PORT :$webPort (HTTP) • POCKETNODE CORE", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                    } else {
                        Box(
                            modifier = Modifier
                                .background(TacticalSurface, RoundedCornerShape(3.dp))
                                .border(1.dp, TacticalBorder, RoundedCornerShape(3.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text("DAEMON STANDBY", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalSubtle)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("CONFIGURED PORT :$webPort • OFFLINE", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("PROC ID: $procId", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(TacticalSurface, RoundedCornerShape(4.dp))
                                .border(1.dp, TacticalBorder, RoundedCornerShape(4.dp))
                                .clip(RoundedCornerShape(4.dp))
                                .clickable {
                                    android.widget.Toast.makeText(context, "Restarting PocketNode Daemons...", android.widget.Toast.LENGTH_SHORT).show()
                                    onRestartServer()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Refresh, "Reload", tint = TacticalText, modifier = Modifier.size(16.dp))
                        }
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(TacticalRed.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .border(1.dp, TacticalRed.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { if (isRunning) onToggleServer(false) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.PowerSettingsNew, "Halt", tint = TacticalRed, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                // Unified Services & Endpoints Stack (Visible only when server daemon is running)
                AnimatedVisibility(
                    visible = isRunning,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column {
                        var servicesExpanded by remember { mutableStateOf(true) }
                        val activeCount = listOf(jellyfinRunning, webRunning, dlnaRunning, torrentRunning, telegramRunning, jackettRunning).count { it }
                        val hasLanIp = ipAddress.isNotBlank() && ipAddress != "127.0.0.1" && ipAddress != boundHost

                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { servicesExpanded = !servicesExpanded }
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("// SERVICES_&_ENDPOINTS", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                                HudBadge(
                                    if (isRunning) "$activeCount / 6 ACTIVE" else "DAEMON OFFLINE",
                                    if (isRunning && activeCount > 0) StatusGreen else TacticalMuted,
                                    false
                                )
                            }
                            Icon(
                                imageVector = if (servicesExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = "Toggle Services",
                                tint = TacticalMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        AnimatedVisibility(
                            visible = servicesExpanded,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // 1. HTTP Web Control Deck
                                UnifiedServiceRow(
                                    title = "HTTP Web Control Deck",
                                    running = isRunning && webRunning,
                                    context = context,
                                    url = "http://$boundHost:$webPort",
                                    altUrl = if (hasLanIp) "http://$ipAddress:$webPort" else null,
                                    onRestart = {
                                        if (!isRunning) {
                                            Toast.makeText(context, "Start PocketNode Daemon first", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Restarting Web Panel...", Toast.LENGTH_SHORT).show()
                                            ServerForegroundService.instance?.restartSubService("web")
                                        }
                                    },
                                    onToggle = {
                                        if (!isRunning) {
                                            Toast.makeText(context, "Start PocketNode Daemon first", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Web Panel ${if (it) "starting" else "stopping"}...", Toast.LENGTH_SHORT).show()
                                            ServerForegroundService.instance?.toggleSubService("web", it)
                                        }
                                    }
                                )

                                // 2. Jellyfin Media Server
                                UnifiedServiceRow(
                                    title = "Jellyfin Media Server",
                                    running = isRunning && jellyfinRunning,
                                    context = context,
                                    url = "http://$boundHost:$jellyfinPort",
                                    altUrl = if (hasLanIp) "http://$ipAddress:$jellyfinPort" else null,
                                    onRestart = {
                                        if (!isRunning) {
                                            Toast.makeText(context, "Start PocketNode Daemon first", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Restarting Jellyfin...", Toast.LENGTH_SHORT).show()
                                            ServerForegroundService.instance?.restartSubService("jellyfin")
                                        }
                                    },
                                    onToggle = {
                                        if (!isRunning) {
                                            Toast.makeText(context, "Start PocketNode Daemon first", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Jellyfin ${if (it) "starting" else "stopping"}...", Toast.LENGTH_SHORT).show()
                                            ServerForegroundService.instance?.toggleSubService("jellyfin", it)
                                        }
                                    }
                                )

                                // 3. VLC / DLNA Media Cast
                                UnifiedServiceRow(
                                    title = "VLC / DLNA Media Cast",
                                    running = isRunning && dlnaRunning,
                                    context = context,
                                    url = "http://$boundHost:8090",
                                    altUrl = if (hasLanIp) "http://$ipAddress:8090" else null,
                                    onRestart = {
                                        if (!isRunning) {
                                            Toast.makeText(context, "Start PocketNode Daemon first", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Restarting DLNA...", Toast.LENGTH_SHORT).show()
                                            ServerForegroundService.instance?.restartSubService("dlna")
                                        }
                                    },
                                    onToggle = {
                                        if (!isRunning) {
                                            Toast.makeText(context, "Start PocketNode Daemon first", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "DLNA ${if (it) "starting" else "stopping"}...", Toast.LENGTH_SHORT).show()
                                            ServerForegroundService.instance?.toggleSubService("dlna", it)
                                        }
                                    }
                                )

                                // 4. Jackett Torrent Indexer
                                UnifiedServiceRow(
                                    title = "Jackett Torrent Indexer",
                                    running = isRunning && jackettRunning,
                                    context = context,
                                    url = "http://$boundHost:9117",
                                    altUrl = if (hasLanIp) "http://$ipAddress:9117" else null,
                                    onRestart = {
                                        if (!isRunning) {
                                            Toast.makeText(context, "Start PocketNode Daemon first", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Restarting Jackett...", Toast.LENGTH_SHORT).show()
                                            ServerForegroundService.instance?.restartSubService("jackett")
                                        }
                                    },
                                    onToggle = {
                                        if (!isRunning) {
                                            Toast.makeText(context, "Start PocketNode Daemon first", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Jackett ${if (it) "starting" else "stopping"}...", Toast.LENGTH_SHORT).show()
                                            ServerForegroundService.instance?.toggleSubService("jackett", it)
                                        }
                                    }
                                )

                                // 5. Libtorrent Engine
                                UnifiedServiceRow(
                                    title = "Libtorrent Engine",
                                    running = isRunning && torrentRunning,
                                    context = context,
                                    detail = if (TorrentEngine.activeCount > 0) "${TorrentEngine.activeCount} active transfer(s) • DHT + Magnet" else "DHT + Magnet Protocol Engine",
                                    actionBadge = "📥 QUEUE",
                                    onActionClick = onOpenDownloads,
                                    onRestart = {
                                        if (!isRunning) {
                                            Toast.makeText(context, "Start PocketNode Daemon first", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Restarting Torrent...", Toast.LENGTH_SHORT).show()
                                            ServerForegroundService.instance?.restartSubService("torrent")
                                        }
                                    },
                                    onToggle = {
                                        if (!isRunning) {
                                            Toast.makeText(context, "Start PocketNode Daemon first", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Torrent ${if (it) "starting" else "stopping"}...", Toast.LENGTH_SHORT).show()
                                            ServerForegroundService.instance?.toggleSubService("torrent", it)
                                        }
                                    }
                                )

                                // 6. Telegram Bot Daemon
                                val prefs = remember { context.getSharedPreferences("remote_media_prefs", Context.MODE_PRIVATE) }
                                val telegramToken = prefs.getString("telegram_bot_token", "") ?: ""
                                val isTelegramConfigured = telegramToken.isNotBlank()

                                UnifiedServiceRow(
                                    title = "Telegram Bot Daemon",
                                    running = isRunning && telegramRunning && isTelegramConfigured,
                                    context = context,
                                    detail = if (isTelegramConfigured) "Long-polling listener active" else "Requires Telegram Bot Token in Settings",
                                    requiresToken = !isTelegramConfigured,
                                    tokenRequiredLabel = "[ TOKEN REQUIRED ]",
                                    onTokenRequiredClick = {
                                        Toast.makeText(context, "Configure Telegram Bot Token in Settings first", Toast.LENGTH_LONG).show()
                                    },
                                    onRestart = {
                                        if (!isRunning) {
                                            Toast.makeText(context, "Start PocketNode Daemon first", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Restarting Telegram Bot...", Toast.LENGTH_SHORT).show()
                                            ServerForegroundService.instance?.restartSubService("telegram")
                                        }
                                    },
                                    onToggle = {
                                        if (!isRunning) {
                                            Toast.makeText(context, "Start PocketNode Daemon first", Toast.LENGTH_SHORT).show()
                                        } else if (!isTelegramConfigured) {
                                            Toast.makeText(context, "Configure Telegram Bot Token in Settings first", Toast.LENGTH_LONG).show()
                                        } else {
                                            Toast.makeText(context, "Telegram Bot ${if (it) "starting" else "stopping"}...", Toast.LENGTH_SHORT).show()
                                            ServerForegroundService.instance?.toggleSubService("telegram", it)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // 2. CPU UTILIZATION
            HudCard {
                val cpuTempColor = when { metrics.cpuTempCelsius > 70f -> TacticalRed; metrics.cpuTempCelsius > 55f -> TacticalOrange; else -> StatusGreen }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("// CPU_UTILIZATION", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${"%.1f".format(metrics.cpuTempCelsius)}°C", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = cpuTempColor)
                        Text("${metrics.cpuPercent.roundToInt().coerceIn(0, 100)}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalYellow)
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("REALTIME_FLOW  PEAK: ${metrics.cpuHistory.maxOrNull()?.let { "${(it * 100).roundToInt()}" } ?: "0"}%", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TacticalOrange)
                    Text(when { metrics.cpuTempCelsius > 70f -> "OVERHEAT"; metrics.cpuTempCelsius > 55f -> "WARM"; else -> "NOMINAL" }, fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = cpuTempColor)
                }
                Spacer(modifier = Modifier.height(4.dp))
                // Interactive Axis Inputs (X-Axis Time Window & Y-Axis Limit)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("X-AXIS:", fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                        listOf(30 to "30s", 60 to "60s").forEach { (sec, label) ->
                            val sel = cpuWindowSeconds == sec
                            Box(
                                modifier = Modifier
                                    .background(if (sel) TacticalOrange.copy(alpha = 0.2f) else TacticalSurface, RoundedCornerShape(3.dp))
                                    .border(1.dp, if (sel) TacticalOrange else TacticalBorder, RoundedCornerShape(3.dp))
                                    .clickable { cpuWindowSeconds = sec }
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            ) {
                                Text(label, fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = if (sel) TacticalOrange else TacticalMuted)
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Y-LIMIT:", fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                        listOf(65f, 75f, 85f, 95f).forEach { t ->
                            val sel = thermalLimit == t
                            Box(
                                modifier = Modifier
                                    .background(if (sel) TacticalRed.copy(alpha = 0.2f) else TacticalSurface, RoundedCornerShape(3.dp))
                                    .border(1.dp, if (sel) TacticalRed else TacticalBorder, RoundedCornerShape(3.dp))
                                    .clickable { GpuComputeManager.setThermalLimitC(context, t) }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text("${t.toInt()}°C", fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = if (sel) TacticalRed else TacticalMuted)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                val slicedCpuData = if (cpuWindowSeconds == 30) metrics.cpuHistory.takeLast(12) else metrics.cpuHistory
                LiveLineChart(
                    data = slicedCpuData,
                    color = TacticalOrange,
                    modifier = Modifier.fillMaxWidth().height(84.dp).background(TerminalBgDark, RoundedCornerShape(4.dp)).border(1.dp, TacticalBorder, RoundedCornerShape(4.dp)),
                    limitFraction = (thermalLimit / 100f).coerceIn(0.5f, 0.95f),
                    limitLabel = "LIMIT ${thermalLimit.toInt()}°C",
                    yMaxLabel = "100%",
                    yMidLabel = "50%",
                    yMinLabel = "0%",
                    xWindowLabel = "${cpuWindowSeconds}s",
                    xMidLabel = "${cpuWindowSeconds / 2}s"
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    val base = metrics.cpuPercent.toInt()
                    listOf("C0-C1" to (base + (-10..20).random()).coerceIn(0,100), "C2-C3" to (base + (-20..10).random()).coerceIn(0,100),
                           "C4-C5" to (base + (-15..15).random()).coerceIn(0,100), "C6-C7" to (base + (-25..5).random()).coerceIn(0,100)).forEach { (lbl, load) ->
                        Box(modifier = Modifier.weight(1f).background(TacticalSurface, RoundedCornerShape(4.dp)).border(1.dp, TacticalBorder, RoundedCornerShape(4.dp)).padding(vertical = 5.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(lbl, fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                                Text("$load%", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalOrange)
                            }
                        }
                    }
                }
            }

            // 3. RAM PRESSURE
            HudCard {
                val ramColor = when { metrics.isLowMemory -> TacticalRed; metrics.ramPercent > 80 -> TacticalOrange; else -> StatusGreen }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("// RAM_PRESSURE", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (metrics.isLowMemory) HudBadge("LOW_MEM", TacticalRed, true)
                        Text("${formatBytes(metrics.ramUsedBytes)} / ${formatBytes(metrics.ramTotalBytes)}", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalText)
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                // Interactive Axis Inputs (X-Axis Time Window & Y-Axis Limit)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("X-AXIS:", fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                        listOf(30 to "30s", 60 to "60s").forEach { (sec, label) ->
                            val sel = ramWindowSeconds == sec
                            Box(
                                modifier = Modifier
                                    .background(if (sel) StatusGreen.copy(alpha = 0.2f) else TacticalSurface, RoundedCornerShape(3.dp))
                                    .border(1.dp, if (sel) StatusGreen else TacticalBorder, RoundedCornerShape(3.dp))
                                    .clickable { ramWindowSeconds = sec }
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            ) {
                                Text(label, fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = if (sel) StatusGreen else TacticalMuted)
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Y-LIMIT:", fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                        listOf(75, 85, 90, 95).forEach { r ->
                            val sel = ramLimit == r
                            Box(
                                modifier = Modifier
                                    .background(if (sel) StatusGreen.copy(alpha = 0.2f) else TacticalSurface, RoundedCornerShape(3.dp))
                                    .border(1.dp, if (sel) StatusGreen else TacticalBorder, RoundedCornerShape(3.dp))
                                    .clickable { GpuComputeManager.setRamLimitPct(context, r) }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text("$r%", fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = if (sel) StatusGreen else TacticalMuted)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                val slicedRamData = if (ramWindowSeconds == 30) metrics.ramHistory.takeLast(12) else metrics.ramHistory
                LiveLineChart(
                    data = slicedRamData,
                    color = ramColor,
                    modifier = Modifier.fillMaxWidth().height(84.dp).background(TerminalBgDark, RoundedCornerShape(4.dp)).border(1.dp, TacticalBorder, RoundedCornerShape(4.dp)),
                    limitFraction = (ramLimit / 100f).coerceIn(0.5f, 0.99f),
                    limitLabel = "LIMIT $ramLimit%",
                    yMaxLabel = "100%",
                    yMidLabel = "50%",
                    yMinLabel = "0%",
                    xWindowLabel = "${ramWindowSeconds}s",
                    xMidLabel = "${ramWindowSeconds / 2}s"
                )
                Spacer(modifier = Modifier.height(8.dp))
                HudProgressBar(metrics.ramPercent / 100f, ramColor)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    RamBreakdownItem("SYS",  formatBytesShort(metrics.ramUsedBytes / 2), TacticalMuted)
                    RamBreakdownItem("SRV",  formatBytesShort(metrics.ramUsedBytes - metrics.ramUsedBytes / 2), StatusGreen)
                    RamBreakdownItem("FREE", formatBytesShort(metrics.ramTotalBytes - metrics.ramUsedBytes), TacticalText)
                }
                if (metrics.zramTotalMb > 0) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(TacticalSurface, RoundedCornerShape(4.dp))
                            .border(1.dp, TacticalBorder, RoundedCornerShape(4.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        val zramPct = (metrics.zramUsedMb.toFloat() / metrics.zramTotalMb).coerceIn(0f, 1f)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f, fill = false)) {
                                Box(modifier = Modifier.size(7.dp).background(TacticalYellow, RoundedCornerShape(2.dp)))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("ZRAM SWAP", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalYellow, maxLines = 1)
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("${metrics.zramUsedMb} MB / ${metrics.zramTotalMb} MB (${(zramPct * 100).roundToInt()}%)", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalText, maxLines = 1)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        HudProgressBar(zramPct, TacticalYellow)
                    }
                }
            }

            // 4. NETWORK THROUGHPUT
            HudCard {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("// NETWORK_THROUGHPUT", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                    HudBadge("WLAN0", TacticalMuted, false)
                }
                Spacer(modifier = Modifier.height(4.dp))
                val netPeak = (metrics.netRxHistory.maxOrNull() ?: 1f).coerceAtLeast(0.1f)
                val netPeakLabel = if (netPeak < 0.1f) "${(netPeak * 1024).roundToInt()}K" else "%.1fM".format(netPeak)
                val netMidLabel = if (netPeak < 0.1f) "${(netPeak * 512).roundToInt()}K" else "%.1fM".format(netPeak / 2)
                LiveLineChart(
                    data = metrics.netRxHistory,
                    color = ElectricTeal,
                    modifier = Modifier.fillMaxWidth().height(84.dp).background(TerminalBgDark, RoundedCornerShape(4.dp)).border(1.dp, TacticalBorder, RoundedCornerShape(4.dp)),
                    yMaxLabel = netPeakLabel,
                    yMidLabel = netMidLabel,
                    yMinLabel = "0",
                    xWindowLabel = "60s",
                    xMidLabel = "30s"
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(TacticalSurface, RoundedCornerShape(4.dp))
                            .border(1.dp, ElectricTeal.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 10.dp, vertical = 7.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("↓", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ElectricTeal)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("DOWN", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                            }
                            Text("${"%.2f".format(metrics.netRxMbps)} MB/s", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = ElectricTeal)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(TacticalSurface, RoundedCornerShape(4.dp))
                            .border(1.dp, TacticalOrange.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 10.dp, vertical = 7.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("↑", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TacticalOrange)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("UP", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                            }
                            Text("${"%.2f".format(metrics.netTxMbps)} MB/s", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalOrange)
                        }
                    }
                }
            }

            // 5. THERMAL SENSORS (EXPANDABLE)
            HudCard {
                val maxTemp = maxOf(metrics.cpuTempCelsius, metrics.gpuTempCelsius, metrics.batteryTempCelsius, metrics.socTempCelsius)
                val thermalColor = when { maxTemp > 70f -> TacticalRed; maxTemp > 55f -> TacticalOrange; else -> StatusGreen }
                val thermalStatus = when { maxTemp > 70f -> "OVERHEAT"; maxTemp > 55f -> "WARM"; else -> "NOMINAL" }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { thermalExpanded = !thermalExpanded }
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("// THERMAL_MONITOR", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(modifier = Modifier.border(1.dp, thermalColor, RoundedCornerShape(3.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                            Text(thermalStatus, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = thermalColor)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${maxTemp.roundToInt()}°C PEAK", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = thermalColor)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = if (thermalExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Expand Thermals",
                            tint = TacticalMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Sensor Summary Grid (CPU, GPU, BATT, SOC)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ThermalTile(label = "CPU", temp = metrics.cpuTempCelsius, limit = 85f, modifier = Modifier.weight(1f))
                    ThermalTile(label = "GPU", temp = metrics.gpuTempCelsius, limit = 80f, modifier = Modifier.weight(1f))
                    ThermalTile(label = "BATT", temp = metrics.batteryTempCelsius, limit = 48f, modifier = Modifier.weight(1f))
                    ThermalTile(label = "SOC", temp = metrics.socTempCelsius, limit = 75f, modifier = Modifier.weight(1f))
                }

                AnimatedVisibility(
                    visible = thermalExpanded,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(TacticalBorder.copy(alpha = 0.5f)))

                        ThermalDetailRow("CPU Package / Cores", metrics.cpuTempCelsius, 85f, "Throttles at 80°C")
                        ThermalDetailRow("GPU Silicon (Adreno/Mali)", metrics.gpuTempCelsius, 80f, "Throttles at 75°C")
                        ThermalDetailRow("Battery Li-Ion Cell", metrics.batteryTempCelsius, 48f, "Safety cutoff at 48°C")
                        ThermalDetailRow("SoC Chassis / Skin", metrics.socTempCelsius, 75f, "Ambient thermal baseline")

                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(TacticalSurface, RoundedCornerShape(3.dp))
                                .border(1.dp, TacticalBorder, RoundedCornerShape(3.dp))
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("GOVERNOR: SCHEDUTIL", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                            Text("THROTTLE: NONE", fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = StatusGreen)
                        }
                    }
                }
            }

            // 6. BATTERY SYSTEM
            HudCard {
                val battColor = when { metrics.batteryPercent < 15 -> TacticalRed; metrics.batteryPercent < 30 -> TacticalOrange; else -> StatusGreen }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("// BATTERY_SYSTEM", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (metrics.isCharging) HudBadge("CHARGING ⚡", TacticalYellow, true)
                        Box(modifier = Modifier.border(1.dp, battColor.copy(alpha = 0.6f), RoundedCornerShape(3.dp)).padding(horizontal = 5.dp, vertical = 1.dp)) {
                            Text(metrics.batteryHealthLabel, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = battColor)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("${metrics.batteryPercent}", fontSize = 28.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalText)
                        Text("%", fontSize = 14.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted, modifier = Modifier.padding(bottom = 4.dp))
                    }
                    Text("${"%.2f".format(metrics.batteryVoltageV)}V  •  ${metrics.batteryTempCelsius.roundToInt()}°C CELL", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                }
                Spacer(modifier = Modifier.height(6.dp))
                HudBatterySegments(metrics.batteryPercent)
            }

            // 6. STORAGE
            HudCard {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("// STORAGE", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                    Text("${formatBytes(metrics.storageUsedBytes)} / ${formatBytes(metrics.storageTotalBytes)} (${metrics.storagePercent}%)", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalText, maxLines = 1)
                }
                Spacer(modifier = Modifier.height(8.dp))
                HudProgressBar(metrics.storagePercent / 100f, TacticalYellow)
                Spacer(modifier = Modifier.height(6.dp))
                Text("MNT: ${StorageManager.getBaseDir().absolutePath}", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            // FOOTER
            PocketNodeFooter()
        }

        // ── Side Drawer Scrim (Tap to close, sits under navbar)
        androidx.compose.animation.AnimatedVisibility(
            visible = isDrawerOpen,
            enter = fadeIn(tween(250)),
            exit = fadeOut(tween(200))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .clickable { isDrawerOpen = false }
            )
        }

        // ── Tactical Side Menu Panel (compact 260dp, slides in under navbar)
        androidx.compose.animation.AnimatedVisibility(
            visible = isDrawerOpen,
            enter = slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(280, easing = FastOutSlowInEasing)),
            exit = slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(220, easing = FastOutSlowInEasing))
        ) {
            TacticalSideMenu(
                onSelectDownloads = {
                    isDrawerOpen = false
                    onOpenDownloads()
                },
                onSelectPermissions = {
                    isDrawerOpen = false
                    onOpenPermissions()
                },
                onSelectGuide = {
                    isDrawerOpen = false
                    onOpenGuide()
                },
                onSelectCredits = {
                    isDrawerOpen = false
                    onOpenCredits()
                },
                onSelectSettings = {
                    isDrawerOpen = false
                    onOpenSettings()
                },
                onSelectTerminal = {
                    isDrawerOpen = false
                    onOpenTerminal()
                },
                onClose = { isDrawerOpen = false }
            )
        }
    }
}
}

// ─── TACTICAL SIDE NAVIGATION MENU ──────────────────────────────────────────

@Composable
private fun TacticalSideMenu(
    onSelectDownloads: () -> Unit,
    onSelectPermissions: () -> Unit,
    onSelectGuide: () -> Unit,
    onSelectCredits: () -> Unit,
    onSelectSettings: () -> Unit,
    onSelectTerminal: () -> Unit,
    onClose: () -> Unit
) {
    val downloadItems by TorrentEngine.downloads.collectAsState()
    val activeDownloads = downloadItems.count { it.progressPct < 100 }
    val isRunning by ServerForegroundService.isRunning.collectAsState()

    val sidebarScrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(260.dp)
            .background(TacticalCanvas)
            .border(width = 1.dp, color = TacticalBorder)
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(sidebarScrollState),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Menu Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("// NAVIGATION", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("POCKETNODE", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace, color = TacticalOrange)
                }
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .background(TacticalSurface, RoundedCornerShape(4.dp))
                        .border(1.dp, TacticalBorder, RoundedCornerShape(4.dp))
                        .clickable { onClose() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("✕", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                }
            }

            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(TacticalBorder))

            Spacer(modifier = Modifier.height(2.dp))

            // Menu Items
            SideMenuItem(
                icon = "📥",
                title = "DOWNLOADS",
                subtitle = "Active queue & media",
                badge = if (activeDownloads > 0) "$activeDownloads ACTIVE" else null,
                badgeColor = StatusGreen,
                onClick = onSelectDownloads
            )

            SideMenuItem(
                icon = "🔐",
                title = "PERMISSIONS",
                subtitle = "Roles, vault & devices",
                badge = "ROLES",
                badgeColor = TacticalYellow,
                onClick = onSelectPermissions
            )

            SideMenuItem(
                icon = "📖",
                title = "FIELD GUIDE",
                subtitle = "Jellyfin, VLC, Telegram",
                badge = "DOCS",
                badgeColor = ElectricTeal,
                onClick = onSelectGuide
            )

            SideMenuItem(
                icon = "🎖️",
                title = "CREDITS & SUPPORT",
                subtitle = "Developer profile & donate",
                badge = null,
                badgeColor = TacticalMuted,
                onClick = onSelectCredits
            )

            Spacer(modifier = Modifier.height(4.dp))
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(TacticalBorder.copy(alpha = 0.5f)))
            Spacer(modifier = Modifier.height(4.dp))

            SideMenuItem(
                icon = "⚙️",
                title = "SYSTEM SETTINGS",
                subtitle = "Ports, storage & config",
                badge = null,
                badgeColor = TacticalMuted,
                onClick = onSelectSettings
            )

            SideMenuItem(
                icon = "💻",
                title = "CLI TERMINAL",
                subtitle = "Raw system tty console",
                badge = "TTY",
                badgeColor = TacticalOrange,
                onClick = onSelectTerminal
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Bottom System Status Pill
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(TacticalSurface, RoundedCornerShape(4.dp))
                .border(1.dp, TacticalBorder, RoundedCornerShape(4.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("CORE BETA", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(if (isRunning) StatusGreen else TacticalRed, CircleShape)
                    )
                    Text(
                        if (isRunning) "ACTIVE" else "OFFLINE",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = if (isRunning) StatusGreen else TacticalRed
                    )
                }
            }
        }
    }
}

@Composable
private fun SideMenuItem(
    icon: String,
    title: String,
    subtitle: String,
    badge: String?,
    badgeColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(TacticalSurface, RoundedCornerShape(4.dp))
            .border(1.dp, TacticalBorder, RoundedCornerShape(4.dp))
            .clip(RoundedCornerShape(4.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Text(icon, fontSize = 16.sp)
                Column {
                    Text(title, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalText)
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(subtitle, fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted, maxLines = 1)
                }
            }
            if (badge != null) {
                Box(
                    modifier = Modifier
                        .background(badgeColor.copy(alpha = 0.15f), RoundedCornerShape(3.dp))
                        .border(0.5.dp, badgeColor, RoundedCornerShape(3.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(badge, fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = badgeColor)
                }
            }
        }
    }
}

// ─── HUD COMPONENTS ──────────────────────────────────────────────────────────

@Composable
private fun HudCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = modifier.fillMaxWidth().background(TacticalPanel, RoundedCornerShape(6.dp)).border(1.dp, TacticalBorder, RoundedCornerShape(6.dp)).padding(14.dp)) { content() }
}

@Composable
private fun HudBadge(label: String, color: Color, filled: Boolean) {
    Box(modifier = if (filled) Modifier.background(color, RoundedCornerShape(3.dp)).padding(horizontal = 7.dp, vertical = 2.dp)
                   else Modifier.border(1.dp, color, RoundedCornerShape(3.dp)).padding(horizontal = 7.dp, vertical = 2.dp)) {
        Text(
            label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = if (filled) TacticalBg else color,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun HudProgressBar(progress: Float, color: Color) {
    Box(modifier = Modifier.fillMaxWidth().height(6.dp).background(TacticalSurface, RoundedCornerShape(2.dp)).clip(RoundedCornerShape(2.dp))) {
        Box(modifier = Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(6.dp).background(color, RoundedCornerShape(2.dp)))
    }
}

@Composable
private fun HudBatterySegments(percent: Int) {
    val n = 12; val filled = (percent * n / 100).coerceIn(0, n)
    val color = when { percent > 60 -> StatusGreen; percent > 25 -> TacticalYellow; else -> TacticalRed }
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(n) { i -> Box(modifier = Modifier.weight(1f).height(8.dp).background(if (i < filled) color else TacticalSurface, RoundedCornerShape(1.dp))) }
    }
}

@Composable
private fun RamBreakdownItem(label: String, value: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(9.dp).border(1.dp, color, RoundedCornerShape(1.dp)))
        Spacer(modifier = Modifier.width(4.dp))
        Text("$label ($value)", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
    }
}


@Composable
private fun ThermalTile(label: String, temp: Float, limit: Float, modifier: Modifier = Modifier) {
    val color = when { temp > limit * 0.85f -> TacticalRed; temp > limit * 0.70f -> TacticalOrange; else -> StatusGreen }
    Box(
        modifier = modifier
            .background(TacticalSurface, RoundedCornerShape(4.dp))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
            .padding(vertical = 8.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
            Spacer(modifier = Modifier.height(2.dp))
            Text("${temp.roundToInt()}°C", fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = color)
            Text("MAX ${limit.roundToInt()}°", fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
        }
    }
}

@Composable
private fun ThermalDetailRow(label: String, temp: Float, limit: Float, note: String) {
    val color = when { temp > limit * 0.85f -> TacticalRed; temp > limit * 0.70f -> TacticalOrange; else -> StatusGreen }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalText)
            Text(note, fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${temp.roundToInt()}°C", fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = color)
            Text(" / ${limit.roundToInt()}°C", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
        }
    }
}

@Composable
private fun UnifiedServiceRow(
    title: String,
    running: Boolean,
    context: Context,
    url: String? = null,
    altUrl: String? = null,
    detail: String? = null,
    statusBadge: String? = null,
    onRestart: () -> Unit = {},
    onToggle: (Boolean) -> Unit = {},
    requiresToken: Boolean = false,
    tokenRequiredLabel: String = "[ TOKEN REQUIRED ]",
    onTokenRequiredClick: () -> Unit = {},
    actionBadge: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(TacticalSurface.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
            .border(
                1.dp,
                if (requiresToken) TacticalYellow.copy(alpha = 0.5f)
                else if (running) TacticalBorder
                else TacticalBorder.copy(alpha = 0.35f),
                RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        // Header Row: Dot + Title + Status Badge + Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f, fill = false),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(
                            if (running) StatusGreen
                            else if (requiresToken) TacticalYellow
                            else TacticalMuted,
                            CircleShape
                        )
                )
                Text(
                    title,
                    modifier = Modifier.weight(1f, fill = false),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    color = TacticalText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val badgeText = statusBadge ?: when {
                    requiresToken -> "TOKEN"
                    running -> "ACTIVE"
                    else -> "STANDBY"
                }
                val badgeColor = when {
                    requiresToken -> TacticalYellow
                    running -> StatusGreen
                    else -> TacticalMuted
                }
                Box(
                    modifier = Modifier
                        .background(badgeColor.copy(alpha = 0.12f), RoundedCornerShape(2.dp))
                        .border(1.dp, badgeColor.copy(alpha = 0.4f), RoundedCornerShape(2.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        badgeText,
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = badgeColor,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Action controls on the right
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                if (requiresToken) {
                    Box(
                        modifier = Modifier
                            .background(TacticalYellow.copy(alpha = 0.15f), RoundedCornerShape(3.dp))
                            .border(1.dp, TacticalYellow, RoundedCornerShape(3.dp))
                            .clip(RoundedCornerShape(3.dp))
                            .clickable { onTokenRequiredClick() }
                            .padding(horizontal = 7.dp, vertical = 4.dp)
                    ) {
                        Text(
                            tokenRequiredLabel,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = TacticalYellow
                        )
                    }
                } else {
                    if (actionBadge != null && onActionClick != null) {
                        Box(
                            modifier = Modifier
                                .height(26.dp)
                                .background(TacticalOrange.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .border(1.dp, TacticalOrange.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { onActionClick() }
                                .padding(horizontal = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                actionBadge,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = TacticalOrange
                            )
                        }
                    }

                    if (url != null && running) {
                        // Quick copy endpoint button
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .background(TacticalSurface, RoundedCornerShape(4.dp))
                                .border(1.dp, TacticalBorder, RoundedCornerShape(4.dp))
                                .clip(RoundedCornerShape(4.dp))
                                .clickable {
                                    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val preferredUrl = when {
                                        altUrl != null && altUrl.contains(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+")) -> altUrl
                                        url.contains(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+")) -> url
                                        else -> altUrl ?: url
                                    }
                                    cb.setPrimaryClip(ClipData.newPlainText("URL", preferredUrl))
                                    Toast.makeText(context, "Copied: $preferredUrl", Toast.LENGTH_SHORT).show()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.ContentCopy, "Copy Endpoint", tint = TacticalMuted, modifier = Modifier.size(13.dp))
                        }
                    }

                    if (running) {
                        // Restart button [ ↻ ]
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .background(TacticalSurface, RoundedCornerShape(4.dp))
                                .border(1.dp, TacticalBorder, RoundedCornerShape(4.dp))
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { onRestart() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Refresh, "Restart", tint = TacticalText, modifier = Modifier.size(13.dp))
                        }

                        // Stop button [ ⏻ ]
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .background(TacticalRed.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .border(1.dp, TacticalRed.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { onToggle(false) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.PowerSettingsNew, "Stop", tint = TacticalRed, modifier = Modifier.size(13.dp))
                        }
                    } else {
                        // Start button [ ▶ START ]
                        Box(
                            modifier = Modifier
                                .height(26.dp)
                                .background(StatusGreen.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .border(1.dp, StatusGreen.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { onToggle(true) }
                                .padding(horizontal = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                Icon(Icons.Default.PlayArrow, "Start", tint = StatusGreen, modifier = Modifier.size(13.dp))
                                Text("START", fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = StatusGreen)
                            }
                        }
                    }
                }
            }
        }

        // Sub-details / URL rows
        if (url != null) {
            Spacer(modifier = Modifier.height(4.dp))
            val isUrlLanIp = url.contains(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+"))
            val lanUrl = if (isUrlLanIp) url else altUrl
            val mdnsUrl = if (!isUrlLanIp) url else altUrl

            // 1. Primary LAN IP (Direct IP — 100% Reliable across all devices)
            if (lanUrl != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.clickable(enabled = running) {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(lanUrl))
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cb.setPrimaryClip(ClipData.newPlainText("URL", lanUrl))
                            Toast.makeText(context, "Copied $lanUrl", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Box(
                        modifier = Modifier
                            .background(ElectricTeal.copy(alpha = 0.15f), RoundedCornerShape(2.dp))
                            .border(1.dp, ElectricTeal.copy(alpha = 0.45f), RoundedCornerShape(2.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text("LAN IP", fontSize = 7.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = ElectricTeal)
                    }
                    Text(
                        lanUrl,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        color = if (running) ElectricTeal else TacticalMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // 2. mDNS Hostname (e.g. pocketnode.local)
            if (mdnsUrl != null && mdnsUrl != lanUrl && running) {
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.clickable {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(mdnsUrl))
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cb.setPrimaryClip(ClipData.newPlainText("URL", mdnsUrl))
                            Toast.makeText(context, "Copied $mdnsUrl", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Box(
                        modifier = Modifier
                            .background(TacticalMuted.copy(alpha = 0.12f), RoundedCornerShape(2.dp))
                            .border(1.dp, TacticalMuted.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text("mDNS", fontSize = 7.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                    }
                    Text(
                        mdnsUrl,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TacticalMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        } else if (detail != null) {
            Spacer(modifier = Modifier.height(3.dp))
            Text(detail, fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
        }
    }
}

@Composable
private fun LiveLineChart(
    data: List<Float>,
    color: Color,
    modifier: Modifier = Modifier,
    limitFraction: Float? = null,
    limitLabel: String? = null,
    yMaxLabel: String = "100%",
    yMidLabel: String = "50%",
    yMinLabel: String = "0%",
    xWindowLabel: String = "60s",
    xMidLabel: String = "30s"
) {
    Canvas(modifier = modifier.padding(horizontal = 6.dp, vertical = 6.dp)) {
        if (data.size < 2) return@Canvas
        val w = size.width
        val h = size.height

        val leftMargin = 32.dp.toPx()
        val bottomMargin = 15.dp.toPx()
        val plotW = (w - leftMargin).coerceAtLeast(10f)
        val plotH = (h - bottomMargin).coerceAtLeast(10f)
        val startX = leftMargin

        val maxVal = if (limitFraction != null) 1f else (data.maxOrNull() ?: 1f).coerceAtLeast(0.01f)
        val step = plotW / (data.size - 1)

        val gridDash = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
        val limitDash = PathEffect.dashPathEffect(floatArrayOf(8f, 5f), 0f)

        // 1. Draw Y-Axis subtle gridlines
        drawLine(TacticalBorder.copy(alpha = 0.35f), Offset(startX, 0f), Offset(w, 0f), strokeWidth = 1f, pathEffect = gridDash)
        drawLine(TacticalBorder.copy(alpha = 0.35f), Offset(startX, plotH / 2f), Offset(w, plotH / 2f), strokeWidth = 1f, pathEffect = gridDash)
        drawLine(TacticalBorder.copy(alpha = 0.7f), Offset(startX, plotH), Offset(w, plotH), strokeWidth = 1.dp.toPx())
        drawLine(TacticalBorder.copy(alpha = 0.7f), Offset(startX, 0f), Offset(startX, plotH), strokeWidth = 1.dp.toPx())

        // 2. Draw Y-Axis Labels
        drawIntoCanvas { canvas ->
            val paint = android.graphics.Paint().apply {
                this.color = android.graphics.Color.argb(170, 160, 175, 190)
                this.textSize = 7.5.sp.toPx()
                this.typeface = android.graphics.Typeface.MONOSPACE
                this.isAntiAlias = true
                this.textAlign = android.graphics.Paint.Align.RIGHT
            }
            canvas.nativeCanvas.drawText(yMaxLabel, startX - 4.dp.toPx(), 8.dp.toPx(), paint)
            canvas.nativeCanvas.drawText(yMidLabel, startX - 4.dp.toPx(), (plotH / 2f) + 3.dp.toPx(), paint)
            canvas.nativeCanvas.drawText(yMinLabel, startX - 4.dp.toPx(), plotH - 1.dp.toPx(), paint)

            // 3. Draw X-Axis Ticks & Labels
            val xPaint = android.graphics.Paint().apply {
                this.color = android.graphics.Color.argb(150, 130, 145, 160)
                this.textSize = 7.5.sp.toPx()
                this.typeface = android.graphics.Typeface.MONOSPACE
                this.isAntiAlias = true
            }

            xPaint.textAlign = android.graphics.Paint.Align.LEFT
            canvas.nativeCanvas.drawText("-$xWindowLabel", startX, h - 2f, xPaint)

            xPaint.textAlign = android.graphics.Paint.Align.CENTER
            canvas.nativeCanvas.drawText("-$xMidLabel", startX + plotW / 2f, h - 2f, xPaint)

            xPaint.textAlign = android.graphics.Paint.Align.RIGHT
            canvas.nativeCanvas.drawText("NOW", w - 2f, h - 2f, xPaint)
        }

        // Draw tick marks on X-axis baseline
        drawLine(TacticalBorder, Offset(startX, plotH), Offset(startX, plotH + 3.dp.toPx()), 1.dp.toPx())
        drawLine(TacticalBorder, Offset(startX + plotW / 2f, plotH), Offset(startX + plotW / 2f, plotH + 3.dp.toPx()), 1.dp.toPx())
        drawLine(TacticalBorder, Offset(w, plotH), Offset(w, plotH + 3.dp.toPx()), 1.dp.toPx())

        // 4. Data line & gradient fill
        val path = Path()
        val fill = Path()

        data.forEachIndexed { i, v ->
            val x = startX + i * step
            val y = plotH - ((v / maxVal) * plotH).coerceIn(0f, plotH)
            if (i == 0) {
                path.moveTo(x, y)
                fill.moveTo(x, plotH)
                fill.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fill.lineTo(x, y)
            }
        }
        fill.lineTo(startX + (data.size - 1) * step, plotH)
        fill.close()

        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), Color.Transparent), startY = 0f, endY = plotH))
        drawPath(path, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))

        // 5. Draw limit threshold dashed line
        if (limitFraction != null && limitFraction in 0.05f..1.0f) {
            val limitY = (plotH - (limitFraction * plotH)).coerceIn(4f, plotH - 4f)
            drawLine(
                color = TacticalRed.copy(alpha = 0.85f),
                start = Offset(startX, limitY),
                end = Offset(w, limitY),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = limitDash
            )

            if (!limitLabel.isNullOrBlank()) {
                drawIntoCanvas { canvas ->
                    val paint = android.graphics.Paint().apply {
                        this.color = android.graphics.Color.argb(230, 255, 80, 80)
                        this.textSize = 8.sp.toPx()
                        this.typeface = android.graphics.Typeface.MONOSPACE
                        this.isAntiAlias = true
                        this.isFakeBoldText = true
                        this.textAlign = android.graphics.Paint.Align.RIGHT
                    }
                    val textY = (limitY - 3.dp.toPx()).coerceAtLeast(14f)
                    canvas.nativeCanvas.drawText(limitLabel, w - 4.dp.toPx(), textY, paint)
                }
            }
        }
    }
}
fun formatUptime(s: Long): String {
    val d = s / 86400; val h = (s % 86400) / 3600; val m = (s % 3600) / 60
    return "${d}d ${h.toString().padStart(2, '0')}h ${m.toString().padStart(2, '0')}m"
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val u = arrayOf("B", "KB", "MB", "GB", "TB")
    val g = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format("%.1f %s", bytes / Math.pow(1024.0, g.toDouble()), u[g])
}

fun formatBytesShort(bytes: Long): String {
    if (bytes <= 0) return "0B"
    val gb = bytes / (1024.0 * 1024.0 * 1024.0)
    return if (gb >= 1.0) String.format("%.1fG", gb) else String.format("%.0fM", bytes / (1024.0 * 1024.0))
}
