package com.remotemedia.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import com.remotemedia.ServerForegroundService
import com.remotemedia.core.GpuComputeManager
import com.remotemedia.services.JellyfinClusterManager
import com.remotemedia.services.RemoteJellyfinServer
import com.remotemedia.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun JellyfinTabScreen() {
    val scrollState = rememberScrollState()
    val isRunning by ServerForegroundService.jellyfinRunning.collectAsState()
    val jellyfinPort by ServerForegroundService.jellyfinPortState.collectAsState()
    val webPort by ServerForegroundService.webPortState.collectAsState()
    val boundHost by ServerForegroundService.boundHostState.collectAsState()
    val activeIp by ServerForegroundService.ipAddressState.collectAsState()
    val currentMode by ServerForegroundService.bindingModeState.collectAsState()
    val hwTranscodeEnabled by GpuComputeManager.hardwareTranscodeEnabled.collectAsState()
    val codecCaps by GpuComputeManager.codecCapabilities.collectAsState()
    val context = LocalContext.current
    var forceSSL by remember { mutableStateOf(false) }

    val prefs = remember { context.getSharedPreferences("remote_media_prefs", Context.MODE_PRIVATE) }
    var selectedMode by remember { mutableStateOf(prefs.getString("binding_mode", "AUTO") ?: "AUTO") }
    val savedCustomIp = prefs.getString("custom_bound_ip", "") ?: ""
    val initialCustomIp = if (savedCustomIp.equals("pn.jelly", ignoreCase = true) || savedCustomIp.equals("pn.jelly.local", ignoreCase = true)) "pocketnode" else savedCustomIp
    var customIpInput by remember { mutableStateOf(initialCustomIp) }
    var portInput by remember { mutableStateOf((if (prefs.getInt("jellyfin_port", 8096) == 8097) 8096 else prefs.getInt("jellyfin_port", 8096)).toString()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TacticalCanvas)
            .verticalScroll(scrollState)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ── DAEMON_STATUS ──────────────────────────────────────────────────
        PocketCard {
            SectionHeader(
                title = "DAEMON_STATUS",
                badge = if (isRunning) "● RUNNING" else "○ STOPPED",
                badgeColor = if (isRunning) StatusGreen else TacticalRed
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                JellyStatBox("BIND_PORT", "$jellyfinPort (HTTP)", modifier = Modifier.weight(1f))
                JellyStatBox("DISCOVERY", "UDP :7359", modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                JellyStatBox("MEMORY_RES", "512 MB", modifier = Modifier.weight(1f))
                JellyStatBox("PROTOCOL", "JELLYFIN REST", modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(10.dp))
            PocketToggleRow(
                title = "Force SSL / HTTPS",
                subtitle = "Redirect plain HTTP sockets to 8920",
                checked = forceSSL,
                onCheckedChange = { forceSSL = it }
            )
        }

        // ── MEDIACODEC HARDWARE TRANSCODING ───────────────────────────────
        PocketCard {
            SectionHeader(
                title = "HW_TRANSCODING",
                badge = if (hwTranscodeEnabled) "MEDIACODEC ACTIVE" else "STANDBY",
                badgeColor = if (hwTranscodeEnabled) StatusGreen else TacticalMuted
            )
            Spacer(modifier = Modifier.height(10.dp))

            // Probed Codec Chips
            Text("PROBED SILICON HW CODECS", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    "H.264" to (codecCaps?.supportsH264Hw ?: true),
                    "HEVC" to (codecCaps?.supportsH265Hw ?: true),
                    "AV1" to (codecCaps?.supportsAv1Hw ?: false),
                    "VP9" to (codecCaps?.supportsVp9Hw ?: true)
                ).forEach { (name, supported) ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(TacticalSurface, RoundedCornerShape(3.dp))
                            .border(1.dp, if (supported) StatusGreen.copy(alpha = 0.5f) else TacticalBorder, RoundedCornerShape(3.dp))
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "$name ${if (supported) "✓" else "—"}",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = if (supported) StatusGreen else TacticalMuted
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            PocketToggleRow(
                title = "MediaCodec Transcoding",
                subtitle = "Hardware video encode/decode via SoC DSP. Cuts CPU usage ~70%.",
                checked = hwTranscodeEnabled,
                onCheckedChange = { GpuComputeManager.setHardwareTranscode(it, context) }
            )
        }

        // ── MEDIA_LIBRARIES ───────────────────────────────────────────────
        PocketCard {
            SectionHeader(title = "MEDIA_LIBRARIES", badge = "2 ACTIVE", badgeColor = TacticalYellow)
            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("COLLECTION_LABEL", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                Text("UTF-8 STRICT", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("Movies & 4K Cinema", fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalText)
            Spacer(modifier = Modifier.height(8.dp))
            Text("POSIX FOLDER PATH", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("/data/server/media/movies", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = TacticalText, modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .border(1.dp, TacticalBorder, RoundedCornerShape(3.dp))
                        .clickable { /* TODO */ }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text("📁 BROWSE", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TacticalText)
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            // Add Media Library dashed
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, TacticalOrange, RoundedCornerShape(4.dp))
                    .clickable { /* TODO */ }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("+ ADD MEDIA LIBRARY", fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalOrange)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text("MOUNTED REPOSITORIES", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
            Spacer(modifier = Modifier.height(6.dp))
            JellyRepoRow(path = "/mnt/storage/tvshows", meta = "EXT4 // RO_PERM: GID=1001", size = "3.4 TB")
            Spacer(modifier = Modifier.height(6.dp))
            JellyRepoRow(path = "/data/media/audio", meta = "ZFS_POOL // FLAG_LOSSLESS", size = "410 GB")
        }

        // ── USER_MANAGEMENT ────────────────────────────────────────────────
        PocketCard {
            SectionHeader(title = "USER_MANAGEMENT", badge = "AUTH_LOCAL", badgeColor = TacticalMuted)
            Spacer(modifier = Modifier.height(10.dp))
            // User Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TacticalSurface, RoundedCornerShape(4.dp))
                    .border(1.dp, TacticalBorder, RoundedCornerShape(4.dp))
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .background(TacticalMuted, RoundedCornerShape(3.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) { Text("SU", fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = Color.White) }
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("Admin", fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalText)
                            Box(modifier = Modifier.background(TacticalRed, RoundedCornerShape(3.dp)).padding(horizontal = 4.dp, vertical = 1.dp)) {
                                Text("ROOT", fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = Color.White)
                            }
                        }
                        Text("UID: 0000 // TTY_CONSOLE", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(modifier = Modifier.border(1.dp, TacticalBorder, RoundedCornerShape(3.dp)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                        Text("FULL ACCESS", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = TacticalText)
                    }
                    Text("⚙", fontSize = 14.sp, color = TacticalMuted)
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text("PROVISION USERNAME", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
            Spacer(modifier = Modifier.height(4.dp))
            PocketInputField(value = "guest_user", onValueChange = {}, label = "")
            Spacer(modifier = Modifier.height(10.dp))
            var userCanTranscode by remember { mutableStateOf(true) }
            PocketToggleRow(
                title = "Allow Media Transcoding",
                subtitle = "Grant user permission to request transcode streams",
                checked = userCanTranscode,
                onCheckedChange = { userCanTranscode = it }
            )
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PocketYellowBright, RoundedCornerShape(4.dp))
                    .border(1.dp, PocketYellowBright, RoundedCornerShape(4.dp))
                    .clip(RoundedCornerShape(4.dp))
                    .clickable {
                        android.widget.Toast.makeText(context, "User account provisioned", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "+ CREATE USER ACCOUNT",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = TacticalBg
                )
            }
        }

        // ── JELLYFIN_FEDERATION_CLUSTERS (Multi-Server Aggregation) ─────────────
        val clusterServers by JellyfinClusterManager.servers.collectAsState()
        val isClusterScanning by JellyfinClusterManager.isScanning.collectAsState()
        val isClusterSyncing by JellyfinClusterManager.isSyncing.collectAsState()
        val clusterItems by JellyfinClusterManager.remoteItems.collectAsState()
        val coroutineScope = rememberCoroutineScope()
        var showAddClusterDialog by remember { mutableStateOf(false) }

        PocketCard {
            val onlineCount = clusterServers.count { it.isOnline && it.isEnabled }
            val totalCount = clusterServers.size
            SectionHeader(
                title = "JELLYFIN_CLUSTERS",
                badge = if (onlineCount > 0) "● $onlineCount / $totalCount NODES ONLINE" else "○ 0 NODES ONLINE",
                badgeColor = if (onlineCount > 0) StatusGreen else TacticalMuted
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Combine PC, NAS, and remote Jellyfin servers into your phone as a single point gateway. All titles merge into your unified library.",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = TacticalMuted
            )
            Spacer(modifier = Modifier.height(10.dp))

            // Action row: Auto-Discovery & Sync All
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Auto-Discover LAN button
                Box(
                    modifier = Modifier
                        .weight(1.1f)
                        .background(if (isClusterScanning) TacticalCanvas else TacticalSurface, RoundedCornerShape(4.dp))
                        .border(1.dp, if (isClusterScanning) TacticalYellow else TacticalCyan, RoundedCornerShape(4.dp))
                        .clickable(enabled = !isClusterScanning) {
                            coroutineScope.launch {
                                val found = JellyfinClusterManager.autoDiscoverServers(context)
                                Toast.makeText(context, if (found.isNotEmpty()) "Discovered ${found.size} Jellyfin server(s) on LAN!" else "No new Jellyfin servers found on UDP 7359", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (isClusterScanning) "SCANNING LAN..." else "🔍 AUTO-DISCOVER PC",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = if (isClusterScanning) TacticalYellow else TacticalCyan
                    )
                }

                // Sync all button
                Box(
                    modifier = Modifier
                        .weight(0.9f)
                        .background(if (isClusterSyncing) TacticalCanvas else TacticalSurface, RoundedCornerShape(4.dp))
                        .border(1.dp, if (isClusterSyncing) TacticalYellow else TacticalBorder, RoundedCornerShape(4.dp))
                        .clickable(enabled = !isClusterSyncing) {
                            coroutineScope.launch {
                                JellyfinClusterManager.syncAllServers(context)
                                Toast.makeText(context, "Cluster synchronization completed!", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (isClusterSyncing) "SYNCING..." else "↻ SYNC ALL",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = if (isClusterSyncing) TacticalYellow else TacticalText
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Server entries list
            if (clusterServers.isEmpty()) {
                Text(
                    "No external Jellyfin servers configured yet.",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TacticalMuted
                )
            } else {
                clusterServers.forEach { server ->
                    val serverItemsCount = clusterItems.count { it.serverId == server.id }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(TacticalSurface, RoundedCornerShape(4.dp))
                            .border(1.dp, if (server.isOnline) StatusGreen.copy(alpha = 0.5f) else TacticalBorder, RoundedCornerShape(4.dp))
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(if (server.isOnline) StatusGreen else TacticalMuted, CircleShape)
                                )
                                Text(
                                    server.name,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = TacticalText
                                )
                                if (server.serverVersion.isNotBlank()) {
                                    Text("v${server.serverVersion}", fontSize = 8.sp, color = TacticalMuted)
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "http://${server.host}:${server.port} • 👤 ${server.username.ifBlank { "Admin / All" }} • $serverItemsCount titles",
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                color = if (server.isOnline) TacticalCyan else TacticalMuted
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            // Sync single button
                            Box(
                                modifier = Modifier
                                    .border(0.8.dp, TacticalBorder, RoundedCornerShape(3.dp))
                                    .clickable {
                                        coroutineScope.launch {
                                            val ok = JellyfinClusterManager.syncServer(server, context)
                                            Toast.makeText(context, if (ok) "Synced ${server.name}!" else "Failed connecting to ${server.host}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                Text("↻", fontSize = 11.sp, color = TacticalText)
                            }
                            // Delete button
                            Box(
                                modifier = Modifier
                                    .border(0.8.dp, TacticalRed.copy(alpha = 0.5f), RoundedCornerShape(3.dp))
                                    .clickable { JellyfinClusterManager.removeServer(server.id, context) }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                Text("✕", fontSize = 11.sp, color = TacticalRed)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Add server button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.2.dp, TacticalCyan, RoundedCornerShape(4.dp))
                    .clickable { showAddClusterDialog = true }
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "+ LINK NEW JELLYFIN SERVER (PC / NAS)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = TacticalCyan
                )
            }
        }

        // Add Cluster Server Dialog
        if (showAddClusterDialog) {
            var newServerName by remember { mutableStateOf("Desktop PC Jellyfin") }
            var newServerHost by remember { mutableStateOf("192.168.1.100") }
            var newServerPort by remember { mutableStateOf("8096") }
            var newServerUser by remember { mutableStateOf("") }
            var newServerPass by remember { mutableStateOf("") }
            var newServerUserId by remember { mutableStateOf("") }
            var newServerApiKey by remember { mutableStateOf("") }
            var discoveredUsers by remember { mutableStateOf<List<com.remotemedia.services.JellyfinPublicUser>>(emptyList()) }
            var isLoadingUsers by remember { mutableStateOf(false) }

            AlertDialog(
                onDismissRequest = { showAddClusterDialog = false },
                containerColor = TacticalCanvas,
                title = {
                    Text("LINK JELLYFIN ACCOUNT", fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalCyan)
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Connect to a specific Jellyfin user account to isolate your libraries, watch progress, and ratings.",
                            fontSize = 9.5.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TacticalMuted
                        )

                        Text("SERVER FRIENDLY NAME", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                        BasicTextField(
                            value = newServerName,
                            onValueChange = { newServerName = it },
                            textStyle = TextStyle(color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                            modifier = Modifier.fillMaxWidth().background(TacticalSurface, RoundedCornerShape(4.dp)).border(1.dp, TacticalBorder, RoundedCornerShape(4.dp)).padding(8.dp)
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(modifier = Modifier.weight(2f)) {
                                Text("IP / HOSTNAME", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                                BasicTextField(
                                    value = newServerHost,
                                    onValueChange = { newServerHost = it },
                                    textStyle = TextStyle(color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                                    modifier = Modifier.fillMaxWidth().background(TacticalSurface, RoundedCornerShape(4.dp)).border(1.dp, TacticalBorder, RoundedCornerShape(4.dp)).padding(8.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("PORT", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                                BasicTextField(
                                    value = newServerPort,
                                    onValueChange = { newServerPort = it },
                                    textStyle = TextStyle(color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                                    modifier = Modifier.fillMaxWidth().background(TacticalSurface, RoundedCornerShape(4.dp)).border(1.dp, TacticalBorder, RoundedCornerShape(4.dp)).padding(8.dp)
                                )
                            }
                        }

                        // Auto-detect PC accounts button
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(TacticalSurface, RoundedCornerShape(4.dp))
                                .border(1.dp, TacticalCyan.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                .clickable(enabled = !isLoadingUsers) {
                                    isLoadingUsers = true
                                    coroutineScope.launch {
                                        val p = newServerPort.toIntOrNull() ?: 8096
                                        val users = JellyfinClusterManager.fetchPublicUsers(newServerHost.trim(), p)
                                        discoveredUsers = users
                                        isLoadingUsers = false
                                        if (users.isEmpty()) {
                                            Toast.makeText(context, "No public accounts found. Enter username manually below.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                .padding(vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (isLoadingUsers) "DETECTING PC ACCOUNTS..." else "👥 DETECT ACCOUNTS ON PC",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = TacticalCyan
                            )
                        }

                        // Discovered Account Pills
                        if (discoveredUsers.isNotEmpty()) {
                            Text("SELECT ACCOUNT:", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = TacticalYellow)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                discoveredUsers.forEach { user ->
                                    val isSelected = newServerUser == user.name
                                    Box(
                                        modifier = Modifier
                                            .background(if (isSelected) TacticalCyan.copy(alpha = 0.2f) else TacticalSurface, RoundedCornerShape(4.dp))
                                            .border(1.dp, if (isSelected) TacticalCyan else TacticalBorder, RoundedCornerShape(4.dp))
                                            .clickable {
                                                newServerUser = user.name
                                                newServerUserId = user.id
                                            }
                                            .padding(horizontal = 8.dp, vertical = 5.dp)
                                    ) {
                                        Text(
                                            "👤 ${user.name}",
                                            fontSize = 10.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            fontFamily = FontFamily.Monospace,
                                            color = if (isSelected) TacticalCyan else TacticalText
                                        )
                                    }
                                }
                            }
                        }

                        Text("USERNAME / ACCOUNT", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                        BasicTextField(
                            value = newServerUser,
                            onValueChange = { newServerUser = it },
                            textStyle = TextStyle(color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                            modifier = Modifier.fillMaxWidth().background(TacticalSurface, RoundedCornerShape(4.dp)).border(1.dp, TacticalBorder, RoundedCornerShape(4.dp)).padding(8.dp)
                        )

                        Text("PASSWORD (LEAVE BLANK IF NONE)", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                        BasicTextField(
                            value = newServerPass,
                            onValueChange = { newServerPass = it },
                            textStyle = TextStyle(color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                            modifier = Modifier.fillMaxWidth().background(TacticalSurface, RoundedCornerShape(4.dp)).border(1.dp, TacticalBorder, RoundedCornerShape(4.dp)).padding(8.dp)
                        )

                        Text("OPTIONAL: API TOKEN / KEY", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                        BasicTextField(
                            value = newServerApiKey,
                            onValueChange = { newServerApiKey = it },
                            textStyle = TextStyle(color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                            modifier = Modifier.fillMaxWidth().background(TacticalSurface, RoundedCornerShape(4.dp)).border(1.dp, TacticalBorder, RoundedCornerShape(4.dp)).padding(8.dp)
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val portInt = newServerPort.toIntOrNull() ?: 8096
                        val newServer = RemoteJellyfinServer(
                            id = "server_${System.currentTimeMillis()}",
                            name = newServerName.trim().ifBlank {
                                if (newServerUser.isNotBlank()) "PC (${newServerUser.trim()})" else "Remote Jellyfin"
                            },
                            host = newServerHost.trim(),
                            port = portInt,
                            apiKey = newServerApiKey.trim(),
                            username = newServerUser.trim(),
                            password = newServerPass.trim(),
                            userId = newServerUserId.trim(),
                            isEnabled = true
                        )
                        JellyfinClusterManager.addServer(newServer, context)
                        showAddClusterDialog = false
                        Toast.makeText(context, "Linking account ${newServer.username.ifBlank { "Default" }} on ${newServer.host}...", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("SAVE & CONNECT", color = TacticalCyan, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddClusterDialog = false }) {
                        Text("CANCEL", color = TacticalMuted, fontFamily = FontFamily.Monospace)
                    }
                }
            )
        }

        // ── PERSISTENT ADDRESS & NETWORK BINDING ──────────────────────────
        val cleanHost = customIpInput.trim()
            .removePrefix("http://")
            .removePrefix("https://")
            .substringBefore("/")
        val hostOnly = if (cleanHost.contains(":")) cleanHost.substringBefore(":") else cleanHost
        val parsedPortFromInput = if (cleanHost.contains(":")) cleanHost.substringAfter(":").toIntOrNull() ?: (portInput.toIntOrNull() ?: 8096) else (portInput.toIntOrNull() ?: 8096)

        val previewHost = when (selectedMode) {
            "CUSTOM", "STATIC" -> if (hostOnly.isNotBlank()) hostOnly else activeIp
            "MDNS"   -> if (hostOnly.isNotBlank()) {
                if (hostOnly.endsWith(".local", ignoreCase = true)) hostOnly else "$hostOnly.local"
            } else "pocketnode.local"
            else     -> activeIp
        }
        val previewUrl = "http://$previewHost:$parsedPortFromInput"

        PocketCard {
            SectionHeader(
                title = "NETWORK_BINDING",
                badge = when (selectedMode) {
                    "CUSTOM", "STATIC" -> if (hostOnly.isNotBlank()) "CUSTOM: $hostOnly" else "CUSTOM: UNSET"
                    "MDNS"   -> "mDNS: $previewHost"
                    else     -> "DHCP: $activeIp"
                },
                badgeColor = if (selectedMode == "CUSTOM" || selectedMode == "STATIC" || selectedMode == "MDNS") TacticalYellow else TacticalOrange
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Bind a custom URL, domain, or zero-config mDNS hostname (e.g. pocketnode.local) to access media reliably across all devices.",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = TacticalMuted
            )
            Spacer(modifier = Modifier.height(10.dp))

            // Mode Selector Pills
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    Triple("CUSTOM", "CUSTOM URL", "Domain / IP"),
                    Triple("MDNS", "mDNS (.local)", "ZeroConf"),
                    Triple("AUTO", "DYNAMIC DHCP", "Auto Wi-Fi")
                ).forEach { (modeKey, title, sub) ->
                    val isSelected = selectedMode == modeKey || (modeKey == "CUSTOM" && selectedMode == "STATIC")
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .background(
                                if (isSelected) TacticalOrange.copy(alpha = 0.18f) else TacticalSurface,
                                RoundedCornerShape(4.dp)
                            )
                            .border(
                                1.dp,
                                if (isSelected) TacticalOrange else TacticalBorder,
                                RoundedCornerShape(4.dp)
                            )
                            .clickable { selectedMode = modeKey }
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                title,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = if (isSelected) TacticalOrange else TacticalText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                sub,
                                fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace,
                                color = TacticalMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Mode specific inputs
            if (selectedMode == "CUSTOM" || selectedMode == "STATIC" || selectedMode == "MDNS") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (selectedMode == "MDNS") "mDNS HOSTNAME PREFIX" else "CUSTOM ADDRESS / HOSTNAME / URL",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = TacticalMuted
                        )
                        Text(
                            "[ USE CURRENT IP ]",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = TacticalYellow,
                            modifier = Modifier.clickable { customIpInput = activeIp }
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(TacticalSurface, RoundedCornerShape(4.dp))
                            .border(1.dp, TacticalBorder, RoundedCornerShape(4.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        BasicTextField(
                            value = customIpInput,
                            onValueChange = { customIpInput = it.trim() },
                            textStyle = TextStyle(
                                color = TacticalText,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { innerTextField ->
                                if (customIpInput.isEmpty()) {
                                    Text(
                                        if (selectedMode == "MDNS") "e.g. pocketnode (resolves as pocketnode.local)" else "e.g. pocketnode.local or IP",
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = TacticalMuted
                                    )
                                }
                                innerTextField()
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Preset Quick Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("PRESETS:", fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                        val presets = if (selectedMode == "MDNS") {
                            listOf("pocketnode", "homemedia", "media")
                        } else {
                            listOf("pocketnode.local", "pocketnode.lan", "homemedia.local")
                        }
                        presets.forEach { preset ->
                            Box(
                                modifier = Modifier
                                    .background(TacticalBorder.copy(alpha = 0.4f), RoundedCornerShape(3.dp))
                                    .border(1.dp, TacticalBorder, RoundedCornerShape(3.dp))
                                    .clickable { customIpInput = preset }
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Text(preset, fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = TacticalYellow)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Intelligent Helper / Explainer Box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(TacticalSurface, RoundedCornerShape(4.dp))
                            .border(
                                1.dp,
                                if (previewHost.endsWith(".local")) StatusGreen.copy(alpha = 0.5f) else TacticalOrange.copy(alpha = 0.5f),
                                RoundedCornerShape(4.dp)
                            )
                            .padding(8.dp)
                    ) {
                        Column {
                            if (previewHost.endsWith(".local")) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(StatusGreen))
                                    Text("ZERO-CONFIG mDNS ACTIVE", fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = StatusGreen)
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    "Android broadcasts 'http://$previewHost:$parsedPortFromInput' on your Wi-Fi. Smart TVs, PCs, and phones will automatically resolve it without changing any router settings!",
                                    fontSize = 8.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = TacticalText
                                )
                            } else if (previewHost.contains(".") && !previewHost.matches(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+"))) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(TacticalYellow))
                                    Text("CUSTOM LOCAL DOMAIN", fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalYellow)
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    "PocketNode will announce 'http://$previewHost:$parsedPortFromInput' via Jellyfin auto-discovery. To open it in your PC/TV browser, map '$previewHost' to this phone ($activeIp) in your router DNS or PC hosts file.",
                                    fontSize = 8.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = TacticalText
                                )
                            } else {
                                Text(
                                    "Static IP Mode: Jellyfin will advertise http://$previewHost:$parsedPortFromInput. Tip: Reserve this IP in your router's DHCP reservation settings so it never changes.",
                                    fontSize = 8.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = TacticalMuted
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Port input
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("JELLYFIN PORT", fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .background(TacticalSurface, RoundedCornerShape(4.dp))
                            .border(1.dp, TacticalBorder, RoundedCornerShape(4.dp))
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        BasicTextField(
                            value = portInput,
                            onValueChange = { portInput = it.filter { ch -> ch.isDigit() }.take(5) },
                            textStyle = TextStyle(
                                color = TacticalText,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("AUTO UDP DISCOVERY", fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .background(TacticalSurface, RoundedCornerShape(4.dp))
                            .border(1.dp, TacticalBorder, RoundedCornerShape(4.dp))
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text("PORT :7359", fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalText)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Effective Preview Box with 1-tap Copy
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TacticalBg, RoundedCornerShape(4.dp))
                    .border(1.dp, TacticalOrange.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                    .clickable {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        clipboard?.setPrimaryClip(ClipData.newPlainText("Jellyfin URL", previewUrl))
                        android.widget.Toast.makeText(context, "Copied: $previewUrl", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("TARGET CLIENT URL (CLICK TO COPY)", fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                        Text(previewUrl, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalOrange)
                    }
                    Text("📋 COPY", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalYellow)
                }
            }
        }

        // ── Action Buttons ────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1.2f)
                    .background(TacticalYellow, RoundedCornerShape(4.dp))
                    .border(1.dp, TacticalYellow, RoundedCornerShape(4.dp))
                    .clip(RoundedCornerShape(4.dp))
                    .clickable {
                        val finalPort = parsedPortFromInput
                        ServerForegroundService.updateNetworkBinding(
                            context = context,
                            mode = selectedMode,
                            customHostOrIp = customIpInput.trim(),
                            jellyfinPort = finalPort,
                            webPort = webPort
                        )
                        ServerForegroundService.instance?.restartSubService("jellyfin")
                        android.widget.Toast.makeText(context, "Bound to $previewUrl & restarted", android.widget.Toast.LENGTH_LONG).show()
                    }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "💾  [ SAVE & RESTART ]",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = TacticalBg
                )
            }
            Box(
                modifier = Modifier
                    .weight(0.8f)
                    .background(TacticalRed.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                    .border(1.dp, TacticalRed.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                    .clip(RoundedCornerShape(4.dp))
                    .clickable {
                        selectedMode = "AUTO"
                        customIpInput = ""
                        portInput = "8096"
                        forceSSL = false
                        ServerForegroundService.updateNetworkBinding(
                            context = context,
                            mode = "AUTO",
                            customHostOrIp = "",
                            jellyfinPort = 8096,
                            webPort = webPort
                        )
                        ServerForegroundService.instance?.restartSubService("jellyfin")
                        android.widget.Toast.makeText(context, "Reverted to dynamic DHCP IP", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "↺  [ REVERT ]",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = TacticalRed
                )
            }
        }

        // Footer
        PocketNodeFooter()

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun JellyStatBox(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(TacticalSurface, RoundedCornerShape(4.dp))
            .border(1.dp, TacticalBorder, RoundedCornerShape(4.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(label, fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
        Text(value, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalText)
    }
}

@Composable
private fun JellyRepoRow(path: String, meta: String, size: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(TacticalSurface, RoundedCornerShape(4.dp))
            .border(1.dp, TacticalBorder, RoundedCornerShape(4.dp))
            .padding(10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("📁", fontSize = 14.sp)
            Column {
                Text(path, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalText)
                Text(meta, fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
            }
        }
        Text(size, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalText)
    }
}
