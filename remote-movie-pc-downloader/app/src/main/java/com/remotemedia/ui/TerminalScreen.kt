package com.remotemedia.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.remotemedia.ServerForegroundService
import com.remotemedia.core.LogTag
import com.remotemedia.core.Logger
import com.remotemedia.core.StorageManager
import com.remotemedia.core.SystemMetricsManager
import com.remotemedia.ui.theme.ElectricTeal
import com.remotemedia.ui.theme.StatusGreen
import com.remotemedia.ui.theme.TacticalBg
import com.remotemedia.ui.theme.TacticalBorder
import com.remotemedia.ui.theme.TacticalCanvas
import com.remotemedia.ui.theme.TacticalGreen
import com.remotemedia.ui.theme.TacticalMuted
import com.remotemedia.ui.theme.TacticalOrange
import com.remotemedia.ui.theme.TacticalPanel
import com.remotemedia.ui.theme.TacticalRed
import com.remotemedia.ui.theme.TacticalSubtle
import com.remotemedia.ui.theme.TacticalSurface
import com.remotemedia.ui.theme.TacticalText
import com.remotemedia.ui.theme.TacticalYellow
import com.remotemedia.ui.theme.TerminalBgDark
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.Locale
import kotlin.math.roundToInt

// --- Sidebar Tab Metadata ---

private data class SidebarTab(
    val tag: LogTag,
    val shortName: String,
    val subLabel: String,
    val dotColor: Color?
)

private val SIDEBAR_TABS = listOf(
    SidebarTab(LogTag.ALL,      "All",      "all ev",  TacticalOrange),
    SidebarTab(LogTag.SYSTEM,   "System",   "sysd",    TacticalMuted),
    SidebarTab(LogTag.JELLYFIN, "Jelly",    "media",   ElectricTeal),
    SidebarTab(LogTag.TORRENT,  "Torrent",  "qbitt",   TacticalGreen),
    SidebarTab(LogTag.TELEGRAM, "Telegram", "bot-io",  TacticalYellow),
    SidebarTab(LogTag.DLNA,     "DLNA",     "upnp",    Color(0xFFB877FF)),
    SidebarTab(LogTag.HTTP,     "HTTP",     "traefik", TacticalOrange),
)

// Real, working quick command suggestions (Server daemons + Android Linux toybox/toolbox commands)
private val QUICK_COMMANDS = listOf(
    "help",
    "status",
    "df -h",
    "free -m",
    "uname -a",
    "top -n 1",
    "ip a",
    "uptime",
    "cat /proc/cpuinfo",
    "getprop ro.product.model",
    "ps -A",
    "ls /storage/emulated/0",
    "metrics",
    "storage",
    "restart",
    "clear"
)

// Helper: Read real dropped packets from /proc/net/dev
private fun getDroppedPacketsCount(): Long {
    return try {
        val file = File("/proc/net/dev")
        if (!file.exists()) return 0L
        var dropped = 0L
        file.forEachLine { line ->
            if (line.contains(":") && !line.trim().startsWith("lo:")) {
                val parts = line.substringAfter(":").trim().split("\\s+".toRegex())
                if (parts.size >= 12) {
                    dropped += (parts[3].toLongOrNull() ?: 0L) + (parts[11].toLongOrNull() ?: 0L)
                }
            }
        }
        dropped
    } catch (_: Exception) { 0L }
}

// Helper: Format duration
private fun formatDuration(seconds: Long): String {
    if (seconds <= 0) return "00m 00s"
    val d = seconds / 86400
    val h = (seconds % 86400) / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (d > 0) "${d}d ${h.toString().padStart(2, '0')}h ${m.toString().padStart(2, '0')}m"
    else "${h.toString().padStart(2, '0')}h ${m.toString().padStart(2, '0')}m ${s.toString().padStart(2, '0')}s"
}

// --- Terminal Screen ---

@Composable
fun TerminalScreen(
    onBack: () -> Unit = {},
    showHeader: Boolean = true
) {
    val logs by Logger.logsState.collectAsState()
    var selectedTag by remember { mutableStateOf(LogTag.ALL) }
    var commandText by remember { mutableStateOf("") }
    var isPromptExpanded by remember { mutableStateOf(false) }
    val commandHistory = remember { mutableStateListOf<String>() }
    var historyIndex by remember { mutableStateOf(-1) }

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    // Live Metrics for Real Telemetry
    val metrics by SystemMetricsManager.metricsState.collectAsState()
    val isServerRunning by ServerForegroundService.isRunning.collectAsState()

    // 1. Throughput (real RX + TX throughput)
    val totalThroughputMbps = metrics.netRxMbps + metrics.netTxMbps
    val throughputFormatted = if (totalThroughputMbps < 0.1f) {
        "${(totalThroughputMbps * 1024).roundToInt()} KB/s"
    } else {
        "%.2f MB/s".format(totalThroughputMbps)
    }

    // 2. Real Dropped Packets counter
    var droppedPackets by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            droppedPackets = getDroppedPacketsCount()
            delay(2000)
        }
    }
    val droppedFormatted = "$droppedPackets pkts"

    // 3. Real Live Uptime (PocketNode Server Daemon uptime if active, or OFFLINE)
    var uptimeFormatted by remember { mutableStateOf("OFFLINE") }
    LaunchedEffect(isServerRunning) {
        while (true) {
            val srvStart = ServerForegroundService.serverStartTimeMs
            uptimeFormatted = if (isServerRunning && srvStart > 0) {
                val srvSecs = (System.currentTimeMillis() - srvStart) / 1000L
                "NODE " + formatDuration(srvSecs)
            } else {
                "OFFLINE"
            }
            delay(1000)
        }
    }

    val filteredLogs = if (selectedTag == LogTag.ALL) logs
                       else logs.filter { it.tag == selectedTag }
    val listState = rememberLazyListState()

    LaunchedEffect(filteredLogs.size) {
        if (filteredLogs.isNotEmpty()) {
            listState.animateScrollToItem(filteredLogs.size - 1)
        }
    }

    val cursorAlpha by rememberInfiniteTransition(label = "cursor").animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(520), RepeatMode.Reverse),
        label = "blink"
    )

    // Command Execution Engine (Built-in Server Daemons + Termux/Linux Subsystem)
    fun executeCommand(rawInput: String) {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return

        // Update history
        if (commandHistory.isEmpty() || commandHistory.last() != trimmed) {
            commandHistory.add(trimmed)
        }
        historyIndex = commandHistory.size

        // Echo command to log
        Logger.i(LogTag.SYSTEM, "> $trimmed")
        commandText = ""

        val parts = trimmed.split("\\s+".toRegex())
        val baseCmd = parts[0].lowercase(Locale.ROOT)

        when (baseCmd) {
            "help", "?" -> {
                Logger.i(LogTag.SYSTEM, "═════════════════════════════════════════════════════════")
                Logger.i(LogTag.SYSTEM, "// POCKETNODE DAEMON CLI BETA - COMMAND REFERENCE")
                Logger.i(LogTag.SYSTEM, "═════════════════════════════════════════════════════════")
                Logger.i(LogTag.SYSTEM, "[ SERVER DAEMON CONTROLS ]")
                Logger.i(LogTag.SYSTEM, "  help / ?        - Display this comprehensive manual")
                Logger.i(LogTag.SYSTEM, "  status          - Show server runtime, bound IP, ports & component health")
                Logger.i(LogTag.SYSTEM, "  start           - Spin up all PocketNode background services")
                Logger.i(LogTag.SYSTEM, "  stop            - Gracefully stop all listening services & release ports")
                Logger.i(LogTag.SYSTEM, "  restart         - Hot-reload server daemons with zero app kill")
                Logger.i(LogTag.SYSTEM, "  metrics         - Live CPU, RAM, thermal sensors, and battery telemetry")
                Logger.i(LogTag.SYSTEM, "  storage         - Show active storage root, capacity, and mounts")
                Logger.i(LogTag.SYSTEM, "  jellyfin        - Show Jellyfin media server endpoint & state")
                Logger.i(LogTag.SYSTEM, "  web             - Show HTTP web control deck endpoint & state")
                Logger.i(LogTag.SYSTEM, "  dlna            - Show DLNA media cast engine status")
                Logger.i(LogTag.SYSTEM, "  torrent         - Show BitTorrent engine background state")
                Logger.i(LogTag.SYSTEM, "  telegram        - Show Telegram bot daemon status")
                Logger.i(LogTag.SYSTEM, "  clear / cls     - Flush terminal console log buffer")
                Logger.i(LogTag.SYSTEM, "")
                Logger.i(LogTag.SYSTEM, "[ HARDWARE & SYSTEM INSPECTION (TERMUX SHELL) ]")
                Logger.i(LogTag.SYSTEM, "  uname -a        - Linux kernel version, architecture & build date")
                Logger.i(LogTag.SYSTEM, "  df -h           - Mounted filesystem disk free space")
                Logger.i(LogTag.SYSTEM, "  free -m         - Memory, swap & ZRAM utilization (MB)")
                Logger.i(LogTag.SYSTEM, "  top -n 1        - Real-time snapshot of active CPU processes")
                Logger.i(LogTag.SYSTEM, "  ps -A           - Running Android processes & task IDs")
                Logger.i(LogTag.SYSTEM, "  uptime          - System elapsed time & load averages")
                Logger.i(LogTag.SYSTEM, "  ip a / ifconfig - Active network interfaces and IP addresses")
                Logger.i(LogTag.SYSTEM, "  netstat -tlpn   - Active TCP/UDP network listening sockets")
                Logger.i(LogTag.SYSTEM, "  cat /proc/cpuinfo - SoC chipset & core specifications")
                Logger.i(LogTag.SYSTEM, "  cat /proc/meminfo - Detailed low-level Linux memory breakdown")
                Logger.i(LogTag.SYSTEM, "  getprop         - Read Android OS system & hardware properties")
                Logger.i(LogTag.SYSTEM, "  getprop ro.product.model - Display device hardware model")
                Logger.i(LogTag.SYSTEM, "  env             - Current process environment variables")
                Logger.i(LogTag.SYSTEM, "  whoami / id     - Current UID/GID & SELinux context")
                Logger.i(LogTag.SYSTEM, "  ping -c 3 <ip>  - Test network connectivity & latency")
                Logger.i(LogTag.SYSTEM, "  ls -la <path>   - Directory contents listing")
                Logger.i(LogTag.SYSTEM, "═════════════════════════════════════════════════════════")
                Logger.i(LogTag.SYSTEM, "* Note: You can type any valid Linux shell command directly.")
            }
            "clear", "cls" -> {
                Logger.clear()
            }
            "status" -> {
                val isRun = ServerForegroundService.isRunning.value
                val host = ServerForegroundService.boundHostState.value
                val webP = ServerForegroundService.webPortState.value
                val jfP = ServerForegroundService.jellyfinPortState.value
                Logger.i(LogTag.SYSTEM, "── [ POCKETNODE DAEMON STATUS ] ──")
                Logger.i(LogTag.SYSTEM, "State       : ${if (isRun) "ONLINE [ACTIVE]" else "OFFLINE [STANDBY]"}")
                Logger.i(LogTag.SYSTEM, "Bound Host  : $host")
                Logger.i(LogTag.SYSTEM, "Web Deck    : http://$host:$webP [${if (ServerForegroundService.webRunning.value) "RUNNING" else "OFF"}]")
                Logger.i(LogTag.SYSTEM, "Jellyfin    : http://$host:$jfP [${if (ServerForegroundService.jellyfinRunning.value) "RUNNING" else "OFF"}]")
                Logger.i(LogTag.SYSTEM, "DLNA Cast   : http://$host:8090 [${if (ServerForegroundService.dlnaRunning.value) "RUNNING" else "OFF"}]")
                Logger.i(LogTag.SYSTEM, "Torrent Eng : [${if (ServerForegroundService.torrentRunning.value) "ACTIVE" else "IDLE"}]")
                Logger.i(LogTag.SYSTEM, "Telegram    : [${if (ServerForegroundService.telegramRunning.value) "ONLINE" else "STANDBY"}]")
            }
            "uptime" -> {
                val isRun = ServerForegroundService.isRunning.value
                val srvStart = ServerForegroundService.serverStartTimeMs
                val srvSecs = if (isRun && srvStart > 0) (System.currentTimeMillis() - srvStart) / 1000L else 0L
                val sysSecs = SystemClock.elapsedRealtime() / 1000L
                Logger.i(LogTag.SYSTEM, "── [ SYSTEM & DAEMON UPTIME ] ──")
                Logger.i(LogTag.SYSTEM, "PocketNode Daemon : ${if (isRun) "ONLINE [Uptime: ${formatDuration(srvSecs)}]" else "OFFLINE [DAEMON STOPPED]"}")
                Logger.i(LogTag.SYSTEM, "Android OS Uptime : ${formatDuration(sysSecs)} (Boot: SystemClock)")
            }
            "restart" -> {
                Logger.i(LogTag.SYSTEM, "Initiating daemon soft-reload...")
                ServerForegroundService.instance?.restartAllServers()
            }
            "stop" -> {
                Logger.i(LogTag.SYSTEM, "Halting all servers...")
                val intent = Intent(context, ServerForegroundService::class.java).apply { action = ServerForegroundService.ACTION_STOP }
                context.startService(intent)
            }
            "start" -> {
                Logger.i(LogTag.SYSTEM, "Starting server daemons...")
                val intent = Intent(context, ServerForegroundService::class.java).apply { action = ServerForegroundService.ACTION_START }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
            }
            "metrics" -> {
                val m = SystemMetricsManager.metricsState.value
                Logger.i(LogTag.SYSTEM, "CPU: ${m.cpuPercent.roundToInt()}% (${"%.1f".format(m.cpuTempCelsius)}°C) | GPU: ${"%.1f".format(m.gpuTempCelsius)}°C | SoC: ${"%.1f".format(m.socTempCelsius)}°C")
                Logger.i(LogTag.SYSTEM, "RAM: ${m.ramUsedBytes / 1048576}MB / ${m.ramTotalBytes / 1048576}MB (${m.ramPercent}%) | LowMem: ${m.isLowMemory}")
                Logger.i(LogTag.SYSTEM, "ZRAM: ${m.zramUsedMb}MB / ${m.zramTotalMb}MB")
                Logger.i(LogTag.SYSTEM, "Battery: ${m.batteryPercent}% (${m.batteryHealthLabel}, ${"%.1f".format(m.batteryTempCelsius)}°C, ${"%.2f".format(m.batteryVoltageV)}V, Charging: ${m.isCharging})")
                Logger.i(LogTag.SYSTEM, "Network: RX ${"%.2f".format(m.netRxMbps)} MB/s | TX ${"%.2f".format(m.netTxMbps)} MB/s")
            }
            "storage" -> {
                val dir = try { StorageManager.getBaseDir() } catch (_: Exception) { File("/storage/emulated/0") }
                val free = dir.usableSpace / (1024.0 * 1024.0 * 1024.0)
                val total = dir.totalSpace / (1024.0 * 1024.0 * 1024.0)
                Logger.i(LogTag.STORAGE, "Active Root : ${dir.absolutePath}")
                Logger.i(LogTag.STORAGE, "Capacity    : %.2f GB Free / %.2f GB Total (%.1f%% used)".format(free, total, if (total > 0) (1.0 - free/total)*100 else 0.0))
                Logger.i(LogTag.STORAGE, "Writable    : ${dir.canWrite()} | Exists: ${dir.exists()}")
            }
            "jellyfin" -> {
                Logger.i(LogTag.JELLYFIN, "Jellyfin: http://${ServerForegroundService.boundHostState.value}:${ServerForegroundService.jellyfinPortState.value} | Running: ${ServerForegroundService.jellyfinRunning.value}")
            }
            "web" -> {
                Logger.i(LogTag.HTTP, "Web Deck: http://${ServerForegroundService.boundHostState.value}:${ServerForegroundService.webPortState.value} | Running: ${ServerForegroundService.webRunning.value}")
            }
            "dlna" -> {
                Logger.i(LogTag.DLNA, "DLNA Cast: http://${ServerForegroundService.boundHostState.value}:8090 | Running: ${ServerForegroundService.dlnaRunning.value}")
            }
            "torrent" -> {
                Logger.i(LogTag.TORRENT, "Torrent Engine: Active = ${ServerForegroundService.torrentRunning.value}")
            }
            "telegram" -> {
                Logger.i(LogTag.TELEGRAM, "Telegram Bot: Active = ${ServerForegroundService.telegramRunning.value}")
            }
            else -> {
                // Real Linux Subsystem Execution (Termux / Toybox / Shell)
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val process = ProcessBuilder("/system/bin/sh", "-c", trimmed)
                            .redirectErrorStream(true)
                            .start()

                        val reader = BufferedReader(InputStreamReader(process.inputStream))
                        var count = 0
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            count++
                            Logger.i(LogTag.SYSTEM, line!!)
                            if (count > 200) {
                                Logger.w(LogTag.SYSTEM, "[Output truncated at 200 lines]")
                                break
                            }
                        }
                        reader.close()
                        val exit = withTimeoutOrNull(4000) { process.waitFor() } ?: -1
                        if (count == 0) {
                            Logger.i(LogTag.SYSTEM, "[Command finished with exit code $exit]")
                        }
                    } catch (e: Exception) {
                        Logger.e(LogTag.SYSTEM, "sh: ${e.message ?: "Execution failed"}")
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TacticalBg)
    ) {
        if (showHeader) {
            PocketNodeHeader(
                onOpenTerminal = {},
                onOpenSettings = {},
                isSubPage = true,
                onBack = onBack,
                pageTitle = "TERMINAL"
            )
        }

        // PATH BAR
        Row(
            modifier = Modifier.fillMaxWidth().background(TacticalSurface).padding(horizontal = 12.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("/var/log/pocketnode/master.log", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (isServerRunning) "200 OK" else "STANDBY", fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = if (isServerRunning) StatusGreen else TacticalYellow)
                Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(if (isServerRunning) StatusGreen else TacticalYellow))
            }
        }

        HorizontalDivider(color = TacticalBorder, thickness = 1.dp)

        // TABS LABEL
        Box(modifier = Modifier.fillMaxWidth().background(TacticalBg).padding(horizontal = 12.dp, vertical = 4.dp)) {
            Text("// LOG_STREAMS", fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalMuted)
        }

        // MAIN AREA: SIDEBAR + LOG OUTPUT (Dynamically adjusts if command prompt is expanded)
        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {

            // Left Sidebar
            Column(modifier = Modifier.width(72.dp).fillMaxHeight().background(TacticalBg)) {
                SIDEBAR_TABS.forEach { tab ->
                    val isSelected = selectedTag == tab.tag
                    val count = if (tab.tag == LogTag.ALL) logs.size else logs.count { it.tag == tab.tag }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (isSelected) TacticalOrange else Color.Transparent)
                            .clickable { selectedTag = tab.tag }
                            .padding(horizontal = 9.dp, vertical = 8.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (!isSelected && tab.dotColor != null && count > 0) {
                                    Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(tab.dotColor))
                                }
                                Text(
                                    tab.shortName,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (isSelected) Color.White else TacticalText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                tab.subLabel,
                                fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace,
                                color = if (isSelected) Color.White.copy(alpha = 0.65f) else TacticalMuted,
                                maxLines = 1
                            )
                        }
                    }
                    if (!isSelected) {
                        HorizontalDivider(color = TacticalBorder.copy(alpha = 0.4f), thickness = 0.5.dp)
                    }
                }
            }

            Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(TacticalBorder))

            // Log Output Area
            Box(
                modifier = Modifier.weight(1f).fillMaxHeight().background(TerminalBgDark).padding(horizontal = 9.dp, vertical = 8.dp)
            ) {
                if (filteredLogs.isEmpty()) {
                    Text("No stdout log output recorded.\nType 'help' in dispatcher below to see commands.", color = TacticalMuted, fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace, modifier = Modifier.align(Alignment.Center))
                } else {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(filteredLogs, key = { it.id }) { entry ->
                            val tagColor = when (entry.tag) {
                                LogTag.SYSTEM   -> TacticalMuted
                                LogTag.JELLYFIN -> ElectricTeal
                                LogTag.TORRENT  -> TacticalGreen
                                LogTag.TELEGRAM -> TacticalYellow
                                LogTag.DLNA     -> Color(0xFFB877FF)
                                LogTag.HTTP     -> TacticalOrange
                                LogTag.STORAGE  -> TacticalYellow
                                else            -> TacticalMuted
                            }
                            LogEntryRow(timestamp = entry.timestamp, tagName = entry.tag.name, tagColor = tagColor, message = entry.message)
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = TacticalBorder, thickness = 1.dp)

        // REAL TELEMETRY STATS FOOTER (Real throughput, real dropped pkts, real uptime)
        Row(
            modifier = Modifier.fillMaxWidth().background(TacticalCanvas).padding(horizontal = 16.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TerminalStatItem(label = "THROUGHPUT", value = throughputFormatted, valueColor = ElectricTeal)
            Box(modifier = Modifier.width(1.dp).height(26.dp).background(TacticalBorder))
            TerminalStatItem(label = "DROPPED", value = droppedFormatted, valueColor = if (droppedPackets > 0) TacticalYellow else TacticalText)
            Box(modifier = Modifier.width(1.dp).height(26.dp).background(TacticalBorder))
            TerminalStatItem(label = "UPTIME", value = uptimeFormatted, valueColor = if (isServerRunning) StatusGreen else TacticalRed)
        }

        HorizontalDivider(color = TacticalBorder, thickness = 1.dp)

        // COMMAND DISPATCHER (With Expandable Prompt & Termux Shell Support)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(TacticalPanel)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Header with Expand Toggle (Icon button)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(">> // COMMAND_DISPATCHER", fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(if (isPromptExpanded) ElectricTeal.copy(alpha = 0.2f) else TacticalSurface, RoundedCornerShape(4.dp))
                            .border(1.dp, if (isPromptExpanded) ElectricTeal else TacticalBorder, RoundedCornerShape(4.dp))
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { isPromptExpanded = !isPromptExpanded },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPromptExpanded) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                            contentDescription = if (isPromptExpanded) "Minimize Prompt" else "Expand Prompt",
                            tint = if (isPromptExpanded) ElectricTeal else TacticalMuted,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
                Text("ROOT@POCKETNODE:~#", fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = StatusGreen)
            }

            // Command Input Box (Single-line when collapsed, Multi-line with height when expanded)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = if (isPromptExpanded) 80.dp else 40.dp)
                        .background(TerminalBgDark, RoundedCornerShape(5.dp))
                        .border(1.dp, if (isPromptExpanded) ElectricTeal.copy(alpha = 0.7f) else TacticalBorder, RoundedCornerShape(5.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = if (isPromptExpanded) Alignment.Top else Alignment.CenterVertically
                ) {
                    Text(">_", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = StatusGreen)
                    Spacer(modifier = Modifier.width(7.dp))
                    BasicTextField(
                        value = commandText,
                        onValueChange = { commandText = it },
                        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = TacticalText),
                        cursorBrush = SolidColor(StatusGreen),
                        modifier = Modifier.weight(1f),
                        singleLine = !isPromptExpanded,
                        maxLines = if (isPromptExpanded) 5 else 1,
                        keyboardOptions = KeyboardOptions(imeAction = if (isPromptExpanded) ImeAction.Default else ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            if (commandText.isNotBlank()) executeCommand(commandText)
                        }),
                        decorationBox = { inner ->
                            Box {
                                if (commandText.isEmpty()) {
                                    Text("enter server or termux shell command (e.g. help, df -h, ip a)...", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                                }
                                inner()
                            }
                        }
                    )
                    Text("█", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = StatusGreen, modifier = Modifier.alpha(cursorAlpha))
                }

                // Redesigned Tactical EXEC Button
                Box(
                    modifier = Modifier
                        .height(if (isPromptExpanded) 80.dp else 40.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    StatusGreen.copy(alpha = 0.22f),
                                    TacticalSurface
                                )
                            ),
                            RoundedCornerShape(5.dp)
                        )
                        .border(1.dp, StatusGreen.copy(alpha = 0.8f), RoundedCornerShape(5.dp))
                        .clip(RoundedCornerShape(5.dp))
                        .clickable {
                            if (commandText.isNotBlank()) {
                                executeCommand(commandText)
                            } else {
                                Toast.makeText(context, "Type a command or pick a suggestion below", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Execute",
                            tint = StatusGreen,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            "RUN",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = StatusGreen
                        )
                    }
                }
            }

            // Expanded Accessory Toolbar (History, Clear, Paste)
            if (isPromptExpanded) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            modifier = Modifier
                                .background(TacticalSurface, RoundedCornerShape(3.dp))
                                .border(1.dp, TacticalBorder, RoundedCornerShape(3.dp))
                                .clickable {
                                    clipboardManager.getText()?.let { commandText += it.text }
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("📋 PASTE", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = TacticalText)
                        }

                        Box(
                            modifier = Modifier
                                .background(TacticalSurface, RoundedCornerShape(3.dp))
                                .border(1.dp, TacticalBorder, RoundedCornerShape(3.dp))
                                .clickable { commandText = "" }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("✕ CLEAR", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            modifier = Modifier
                                .background(TacticalSurface, RoundedCornerShape(3.dp))
                                .border(1.dp, TacticalBorder, RoundedCornerShape(3.dp))
                                .clickable(enabled = commandHistory.isNotEmpty()) {
                                    if (commandHistory.isNotEmpty()) {
                                        historyIndex = (historyIndex - 1).coerceAtLeast(0)
                                        commandText = commandHistory[historyIndex]
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("↑ PREV", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = if (commandHistory.isNotEmpty()) TacticalText else TacticalMuted)
                        }

                        Box(
                            modifier = Modifier
                                .background(TacticalSurface, RoundedCornerShape(3.dp))
                                .border(1.dp, TacticalBorder, RoundedCornerShape(3.dp))
                                .clickable(enabled = commandHistory.isNotEmpty()) {
                                    if (commandHistory.isNotEmpty()) {
                                        if (historyIndex < commandHistory.size - 1) {
                                            historyIndex++
                                            commandText = commandHistory[historyIndex]
                                        } else {
                                            historyIndex = commandHistory.size
                                            commandText = ""
                                        }
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("↓ NEXT", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = if (commandHistory.isNotEmpty()) TacticalText else TacticalMuted)
                        }
                    }
                }
            }

            // Real, Working Command Suggestions
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                QUICK_COMMANDS.forEach { cmd ->
                    Box(
                        modifier = Modifier
                            .background(TacticalSurface, RoundedCornerShape(3.dp))
                            .border(1.dp, TacticalBorder, RoundedCornerShape(3.dp))
                            .clickable {
                                commandText = cmd
                                executeCommand(cmd)
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(cmd, fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = if (cmd == "help" || cmd == "status") ElectricTeal else TacticalText)
                    }
                }
            }
        }
    }
}

// --- Sub-Composables ---

@Composable
private fun LogEntryRow(timestamp: String, tagName: String, tagColor: Color, message: String) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(timestamp, fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
            Box(
                modifier = Modifier
                    .background(tagColor.copy(alpha = 0.14f), RoundedCornerShape(3.dp))
                    .border(1.dp, tagColor.copy(alpha = 0.45f), RoundedCornerShape(3.dp))
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            ) {
                Text(tagName, fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = tagColor)
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(message, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TacticalText, lineHeight = 14.sp)
    }
}

@Composable
private fun TerminalStatItem(label: String, value: String, valueColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = valueColor)
    }
}
