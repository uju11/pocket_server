package com.remotemedia.ui

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.remotemedia.ServerForegroundService
import com.remotemedia.core.FederatedMediaManager
import com.remotemedia.core.GpuComputeManager
import com.remotemedia.core.HostMediaManager
import com.remotemedia.core.MeshPeerNode
import com.remotemedia.core.MeshShareManager
import com.remotemedia.core.NetworkShareManager
import com.remotemedia.core.StorageManager
import com.remotemedia.core.SystemMetricsManager
import com.remotemedia.ui.theme.*
import java.io.File

@Composable
fun GeneralTabScreen(onOpenTerminal: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("remote_media_prefs", Context.MODE_PRIVATE) }
    val scrollState = rememberScrollState()

    // --- System Metrics ---
    val metrics by SystemMetricsManager.metricsState.collectAsState()

    // --- Load saved config into state ---
    var bootAutoStart by remember { mutableStateOf(prefs.getBoolean("boot_auto_start", true)) }
    var keepWakeLock  by remember { mutableStateOf(prefs.getBoolean("keep_wake_lock", true)) }
    var storageQuota  by remember { mutableStateOf(prefs.getFloat("storage_quota", 0.9f)) }
    var storagePath   by remember { mutableStateOf(StorageManager.getBaseDir().absolutePath) }

    // --- GPU Compute & Hardware Acceleration state ---
    val hwTranscodeEnabled by GpuComputeManager.hardwareTranscodeEnabled.collectAsState()
    val gpuComputeEnabled by GpuComputeManager.gpuComputeEnabled.collectAsState()
    val thermalLimit by GpuComputeManager.thermalLimitC.collectAsState()
    val ramLimit by GpuComputeManager.ramLimitPct.collectAsState()
    val codecCaps by GpuComputeManager.codecCapabilities.collectAsState()
    val autoShutoffReason by GpuComputeManager.autoShutoffReason.collectAsState()

    var localGpuComputeEnabled by remember { mutableStateOf(gpuComputeEnabled) }
    var localThermalLimit by remember { mutableStateOf(thermalLimit) }
    var localRamLimit by remember { mutableStateOf(ramLimit.toFloat()) }

    // Track unsaved changes
    val savedBootAutoStart = remember { mutableStateOf(bootAutoStart) }
    val savedKeepWakeLock  = remember { mutableStateOf(keepWakeLock) }
    val savedStorageQuota  = remember { mutableStateOf(storageQuota) }
    val savedGpuComputeEnabled = remember { mutableStateOf(gpuComputeEnabled) }
    val savedThermalLimit  = remember { mutableStateOf(thermalLimit) }
    val savedRamLimit      = remember { mutableStateOf(ramLimit.toFloat()) }

    // Keep local states aligned when backend values are loaded
    LaunchedEffect(gpuComputeEnabled, thermalLimit, ramLimit) {
        localGpuComputeEnabled = gpuComputeEnabled
        localThermalLimit = thermalLimit
        localRamLimit = ramLimit.toFloat()
        savedGpuComputeEnabled.value = gpuComputeEnabled
        savedThermalLimit.value = thermalLimit
        savedRamLimit.value = ramLimit.toFloat()
    }

    val hasChanges = (bootAutoStart != savedBootAutoStart.value ||
                      keepWakeLock  != savedKeepWakeLock.value  ||
                      storageQuota  != savedStorageQuota.value  ||
                      localGpuComputeEnabled != savedGpuComputeEnabled.value ||
                      localThermalLimit != savedThermalLimit.value ||
                      localRamLimit != savedRamLimit.value)

    // --- Storage stats ---
    val baseDirFile = File(storagePath)
    val statFs = remember(storagePath) {
        runCatching { StatFs(storagePath) }.getOrNull()
    }
    val freeBytes  = statFs?.availableBytes ?: 0L
    val totalBytes = statFs?.totalBytes ?: 0L
    val freeGb  = freeBytes  / (1024.0 * 1024.0 * 1024.0)
    val totalGb = totalBytes / (1024.0 * 1024.0 * 1024.0)

    // --- ZRAM / swap info (best-effort from /proc/meminfo) ---
    val swapInfo = remember {
        runCatching {
            val lines = File("/proc/meminfo").readLines()
            val swapTotal = lines.firstOrNull { it.startsWith("SwapTotal") }
                ?.split(":") ?.getOrNull(1)?.trim()?.replace("kB","")?.trim()?.toLongOrNull() ?: 0L
            val swapFree  = lines.firstOrNull { it.startsWith("SwapFree") }
                ?.split(":") ?.getOrNull(1)?.trim()?.replace("kB","")?.trim()?.toLongOrNull() ?: 0L
            val used = (swapTotal - swapFree) / 1024L  // MB
            val total = swapTotal / 1024L
            if (total > 0L) "${used}MB / ${total}MB active" else "Not enabled"
        }.getOrDefault("Unavailable")
    }

    // --- External storage ---
    val externalPath = remember {
        Environment.getExternalStorageDirectory()?.absolutePath ?: "/sdcard"
    }
    val externalStatFs = remember {
        runCatching { StatFs(externalPath) }.getOrNull()
    }
    val externalFreeGb = (externalStatFs?.availableBytes ?: 0L) / (1024.0 * 1024.0 * 1024.0)

    // --- SAF folder picker ---
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            // Persist read/write permission across reboots
            context.contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            // Convert content URI to a real path if possible
            val realPath = it.path?.let { p ->
                when {
                    p.contains("/primary:") -> "/storage/emulated/0/${p.substringAfter("/primary:")}"
                    p.contains(":") -> "/storage/${p.replace(":", "/")}"
                    else -> p
                }
            } ?: it.toString()

            val ok = StorageManager.setCustomStorageDir(context, realPath)
            if (ok) {
                storagePath = realPath
                prefs.edit().putString("custom_storage_path", realPath).apply()
            }
        }
    }

    // --- Host Mobile Media Sharing (Milestone 3) ---
    val hostSharingEnabled by HostMediaManager.isSharingEnabled.collectAsState()
    val sharedFolders by HostMediaManager.sharedFolders.collectAsState()
    val virtualFiles by HostMediaManager.virtualFiles.collectAsState()
    val isScanningHostMedia by HostMediaManager.isScanning.collectAsState()

    val hostMediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            HostMediaManager.addSharedFolder(context, it)
        }
    }

    // --- LAN Network Shares Auto-Discovery (Milestone 4) ---
    val isScanningNetwork by NetworkShareManager.isScanning.collectAsState()
    val scanProgress by NetworkShareManager.scanProgress.collectAsState()
    val discoveredShares by NetworkShareManager.discoveredShares.collectAsState()
    val mountedShares by NetworkShareManager.mountedShares.collectAsState()
    val networkFiles by NetworkShareManager.networkFiles.collectAsState()

    var showMountDialog by remember { mutableStateOf(false) }
    var targetShareHost by remember { mutableStateOf("") }
    var targetShareName by remember { mutableStateOf("") }
    var targetSharePort by remember { mutableStateOf("445") }
    var targetShareUser by remember { mutableStateOf("") }
    var targetSharePass by remember { mutableStateOf("") }
    var targetShareDomain by remember { mutableStateOf("") }
    var isMountingInProgress by remember { mutableStateOf(false) }
    var mountErrorMsg by remember { mutableStateOf<String?>(null) }
    var showManualPathDialog by remember { mutableStateOf(false) }
    var manualPathInput by remember { mutableStateOf(storagePath) }

    // --- Unified Federation Engine (Milestone 5) ---
    val federatedLibrary by FederatedMediaManager.federatedLibrary.collectAsState()
    val combinedViewEnabled by FederatedMediaManager.combinedViewEnabled.collectAsState()
    val federatedMovies by FederatedMediaManager.movies.collectAsState()
    val federatedMusic by FederatedMediaManager.music.collectAsState()
    val federatedVideos by FederatedMediaManager.videos.collectAsState()
    val federatedPhotos by FederatedMediaManager.photos.collectAsState()
    val federatedDocs by FederatedMediaManager.docs.collectAsState()
    val sandboxCount by FederatedMediaManager.sandboxCount.collectAsState()
    val hostFedCount by FederatedMediaManager.hostCount.collectAsState()
    val networkFedCount by FederatedMediaManager.networkCount.collectAsState()
    val meshFedCount by FederatedMediaManager.meshCount.collectAsState()

    // --- Client Mesh Peer Sharing (Milestone 6) ---
    val isMeshEnabled by MeshShareManager.isMeshEnabled.collectAsState()
    val meshPeers by MeshShareManager.peers.collectAsState()
    val meshFileCount by MeshShareManager.meshFileCount.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TacticalCanvas)
            .verticalScroll(scrollState)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {

        // ── CARD 1: STORAGE_DIRECTORY ──────────────────────────────────────────
        StorageDirectoryCard(
            storagePath = storagePath,
            freeGb = freeGb,
            totalGb = totalGb,
            onBrowseSaf = { folderPickerLauncher.launch(null) }
        )

        // ── CARD 1B: HOST_DEVICE_MEDIA_SHARING ──────────────────────────────────
        HostDeviceSharingCard(
            hostSharingEnabled = hostSharingEnabled,
            sharedFolders = sharedFolders,
            virtualFiles = virtualFiles,
            isScanningHostMedia = isScanningHostMedia,
            onToggleSharing = { HostMediaManager.setSharingEnabled(context, it) },
            onAddFolder = { hostMediaPickerLauncher.launch(null) },
            onRescan = { HostMediaManager.triggerRescan(context) },
            onRemoveFolder = { HostMediaManager.removeSharedFolder(context, it) }
        )

        // ── CARD 1C: DISCOVERED_NETWORK_SHARES (LAN PC / MAC / NAS / UPnP) ──────
        LanNetworkSharesCard(
            isScanningNetwork = isScanningNetwork,
            scanProgress = scanProgress,
            discoveredShares = discoveredShares,
            mountedShares = mountedShares,
            networkFiles = networkFiles,
            onScan = { NetworkShareManager.scanLocalSubnet(context) },
            onAddShareManually = {
                targetShareHost = ""
                targetShareName = ""
                targetSharePort = "445"
                targetShareUser = ""
                targetSharePass = ""
                targetShareDomain = ""
                mountErrorMsg = null
                showMountDialog = true
            },
            onConnectShare = { disc ->
                targetShareHost = disc.host
                targetShareName = ""
                targetSharePort = disc.port.toString()
                targetShareUser = ""
                targetSharePass = ""
                targetShareDomain = ""
                mountErrorMsg = null
                showMountDialog = true
            },
            onUnmountShare = { shareId ->
                NetworkShareManager.unmountShare(context, shareId)
            }
        )


        // ── CARD 1C: FEDERATED_COMBINED_VIEW ──────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(TacticalPanel, RoundedCornerShape(6.dp))
                .border(
                    1.dp,
                    if (combinedViewEnabled) ElectricTeal.copy(alpha = 0.5f) else TacticalBorder,
                    RoundedCornerShape(6.dp)
                )
                .padding(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "// FEDERATED_COMBINED_VIEW",
                        fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace, color = ElectricTeal
                    )
                    Text(
                        "M5: Unified Federation Engine",
                        fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                        color = TacticalMuted
                    )
                }
                Box(
                    modifier = Modifier
                        .border(
                            1.dp,
                            if (combinedViewEnabled) ElectricTeal else TacticalSubtle,
                            RoundedCornerShape(3.dp)
                        )
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        if (combinedViewEnabled) "ACTIVE" else "SPLIT",
                        fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = if (combinedViewEnabled) ElectricTeal else TacticalSubtle
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                "Merges files from all connected sources into a single unified catalog per category. " +
                "Files appear with origin badges [SANDBOX], [HOST], [PC-SMB], or [NFS]. " +
                "Exact duplicates (same name ± 5% size) are deduplicated automatically.",
                fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                color = TacticalMuted, lineHeight = 14.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Combined View toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Combined View Mode",
                        fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace, color = TacticalText
                    )
                    Text(
                        "Serve all sources as one unified media library",
                        fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted
                    )
                }
                Switch(
                    checked = combinedViewEnabled,
                    onCheckedChange = { FederatedMediaManager.setCombinedViewEnabled(context, it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ElectricTeal,
                        checkedTrackColor = ElectricTeal.copy(alpha = 0.3f),
                        uncheckedThumbColor = TacticalMuted,
                        uncheckedTrackColor = TacticalSurface
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = TacticalBorder.copy(alpha = 0.5f), thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(12.dp))

            // Source breakdown
            Text(
                "// SOURCE BREAKDOWN",
                fontSize = 10.sp, fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace, color = TacticalMuted
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Source rows
            @Composable
            fun SourceRow(badge: String, label: String, count: Int, badgeColor: Color) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TacticalSurface, RoundedCornerShape(4.dp))
                        .border(1.dp, TacticalBorder, RoundedCornerShape(4.dp))
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .background(badgeColor.copy(alpha = 0.15f), RoundedCornerShape(3.dp))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                badge, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace, color = badgeColor
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(label, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = TacticalText)
                    }
                    Text(
                        "$count files",
                        fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                        color = if (count > 0) StatusGreen else TacticalSubtle,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                SourceRow("[SANDBOX]", "PocketNode Storage", sandboxCount, TacticalOrange)
                SourceRow("[HOST]", "Host Phone Media", hostFedCount, ElectricTeal)
                SourceRow("[PC-SMB]", "LAN Network Shares", networkFedCount, StatusGreen)
                SourceRow("[MESH]", "Client Peer Mesh", meshFedCount, TacticalYellow)
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = TacticalBorder.copy(alpha = 0.5f), thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(12.dp))

            // Federated category breakdown
            Text(
                "// UNIFIED CATALOG — ${federatedLibrary.size} TOTAL ITEMS (DEDUPLICATED)",
                fontSize = 10.sp, fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace, color = TacticalMuted
            )
            Spacer(modifier = Modifier.height(8.dp))

            val categories = listOf(
                "Movies"    to federatedMovies.size,
                "Music"     to federatedMusic.size,
                "Videos"    to federatedVideos.size,
                "Photos"    to federatedPhotos.size,
                "Documents" to federatedDocs.size
            )

            // Category stats grid (2-column)
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                categories.chunked(2).forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        pair.forEach { (catName, catCount) ->
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(TacticalSurface, RoundedCornerShape(4.dp))
                                    .border(
                                        1.dp,
                                        if (catCount > 0) ElectricTeal.copy(alpha = 0.3f) else TacticalBorder,
                                        RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 9.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    catName,
                                    fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                                    color = if (catCount > 0) TacticalText else TacticalSubtle
                                )
                                Text(
                                    "$catCount",
                                    fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (catCount > 0) ElectricTeal else TacticalSubtle
                                )
                            }
                        }
                        // Pad odd row
                        if (pair.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            // Dedup savings summary (if any duplicates were removed)
            val rawTotal = sandboxCount + hostFedCount + networkFedCount + meshFedCount
            val dedupSaved = rawTotal - federatedLibrary.size
            if (dedupSaved > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(StatusGreen.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
                        .border(1.dp, StatusGreen.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        "// DEDUP: $dedupSaved duplicate(s) removed across sources",
                        fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = StatusGreen
                    )
                }
            }
        }

        // ── CARD 1E: CLIENT_MESH_PEER_SHARING (Milestone 6) ───────────────────
        CrossDevicePeerSharingCard(
            isMeshEnabled = isMeshEnabled,
            meshPeers = meshPeers,
            meshFileCount = meshFileCount,
            onToggleMesh = { MeshShareManager.setMeshEnabled(context, it) },
            onClearAllPeers = { MeshShareManager.clearAllPeers() },
            onRemovePeer = { MeshShareManager.removePeer(it) }
        )

        // ── CARD 2: DAEMON_ORCHESTRATION ───────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(TacticalPanel, RoundedCornerShape(6.dp))
                .border(1.dp, TacticalBorder, RoundedCornerShape(6.dp))
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("// DAEMON_ORCHESTRATION", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                Box(
                    modifier = Modifier
                        .border(1.dp, if (hasChanges) TacticalYellow else TacticalOrange, RoundedCornerShape(3.dp))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        if (hasChanges) "UNSAVED" else "SYS_AUTO",
                        fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
                        color = if (hasChanges) TacticalYellow else TacticalOrange
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            PocketTerminalToggle(
                title = "Boot Auto-Start",
                subtitle = "Launch node services on OS boot",
                checked = bootAutoStart,
                onCheckedChange = { bootAutoStart = it }
            )

            Spacer(modifier = Modifier.height(2.dp))
            HorizontalDivider(color = TacticalBorder.copy(alpha = 0.5f), thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(2.dp))

            PocketTerminalToggle(
                title = "Keep WakeLock Active",
                subtitle = "Prevent Android kernel deep sleep state",
                checked = keepWakeLock,
                onCheckedChange = { keepWakeLock = it }
            )

            Spacer(modifier = Modifier.height(2.dp))
            HorizontalDivider(color = TacticalBorder.copy(alpha = 0.5f), thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(8.dp))

            // Storage Quota
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Storage Quota Alert", fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalText)
                    Text("Warn when disk usage exceeds threshold", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                }
                Box(
                    modifier = Modifier
                        .border(1.dp, TacticalOrange, RoundedCornerShape(3.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("${(storageQuota * 100).toInt()}%", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalOrange)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Slider(
                value = storageQuota,
                onValueChange = { storageQuota = it },
                valueRange = 0.5f..0.98f,
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = TacticalOrange,
                    activeTrackColor = TacticalOrange,
                    inactiveTrackColor = TacticalSurface
                )
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("50% (SAFE)", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                Text("// ALERT THRESHOLD", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = TacticalYellow)
                Text("98% MAX", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
            }

            val usedPercent = if (totalGb > 0) (((totalGb - freeGb) / totalGb) * 100).toInt() else metrics.storagePercent
            val quotaPct = (storageQuota * 100).toInt()
            val isQuotaExceeded = usedPercent >= quotaPct
            val isNearQuota = usedPercent >= quotaPct - 5

            Spacer(modifier = Modifier.height(8.dp))

            // Real-time disk usage vs quota monitor
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TacticalSurface, RoundedCornerShape(4.dp))
                    .border(1.dp, if (isQuotaExceeded) TacticalRed else TacticalBorder, RoundedCornerShape(4.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("LIVE STORAGE USAGE", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                    Text(
                        "${"%.1f".format((totalGb - freeGb).coerceAtLeast(0.0))} GB / ${"%.1f".format(totalGb)} GB ($usedPercent%)",
                        fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
                        color = if (isQuotaExceeded) TacticalRed else if (isNearQuota) TacticalOrange else StatusGreen
                    )
                }
                Box(
                    modifier = Modifier
                        .background(
                            if (isQuotaExceeded) TacticalRed.copy(alpha = 0.2f)
                            else if (isNearQuota) TacticalOrange.copy(alpha = 0.2f)
                            else StatusGreen.copy(alpha = 0.15f),
                            RoundedCornerShape(3.dp)
                        )
                        .border(
                            1.dp,
                            if (isQuotaExceeded) TacticalRed
                            else if (isNearQuota) TacticalOrange
                            else StatusGreen,
                            RoundedCornerShape(3.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        if (isQuotaExceeded) "⚠ QUOTA EXCEEDED"
                        else if (isNearQuota) "⚡ APPROACHING LIMIT"
                        else "✓ HEALTHY",
                        fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
                        color = if (isQuotaExceeded) TacticalRed else if (isNearQuota) TacticalOrange else StatusGreen
                    )
                }
            }

            if (isQuotaExceeded) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TacticalRed.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                        .border(1.dp, TacticalRed, RoundedCornerShape(4.dp))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🛑", fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            "STORAGE QUOTA ALERT: Disk usage is $usedPercent% (Quota is $quotaPct%)",
                            fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalRed
                        )
                        Text(
                            "Storage limit reached. Delete unneeded files or increase quota to prevent write failures.",
                            fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = TacticalText
                        )
                    }
                }
            }
        }

        // ── CARD 3: GPU_COMPUTE_ENGINE ─────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(TacticalPanel, RoundedCornerShape(6.dp))
                .border(1.dp, TacticalBorder, RoundedCornerShape(6.dp))
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("// GPU_COMPUTE", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                Box(
                    modifier = Modifier
                        .border(1.dp, if (localGpuComputeEnabled) StatusGreen else TacticalMuted, RoundedCornerShape(3.dp))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        if (localGpuComputeEnabled) "ACTIVE" else "STANDBY",
                        fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
                        color = if (localGpuComputeEnabled) StatusGreen else TacticalMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // GPU Compute Toggle
            PocketTerminalToggle(
                title = "GPU Compute Engine",
                subtitle = "Enable GPU cores for AI upscaling and thumbnail generation pipelines.",
                checked = localGpuComputeEnabled,
                onCheckedChange = {
                    localGpuComputeEnabled = it
                }
            )

            // Auto-shutoff Warning Banner if triggered
            if (autoShutoffReason != null) {
                Spacer(modifier = Modifier.height(8.dp))
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
                        Text("⚠ AUTO-SHUTOFF TRIGGERED", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalRed)
                        Text(autoShutoffReason ?: "", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = TacticalText)
                    }
                    Box(
                        modifier = Modifier
                            .background(TacticalRed.copy(alpha = 0.3f), RoundedCornerShape(3.dp))
                            .border(1.dp, TacticalRed, RoundedCornerShape(3.dp))
                            .clickable { GpuComputeManager.clearAutoShutoffReason() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("DISMISS", fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalRed)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Thermal Limit Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Thermal Auto-Shutoff Limit", fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalText)
                    Text("Auto-kills GPU compute when CPU temp exceeds", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                }
                Box(
                    modifier = Modifier
                        .border(1.dp, TacticalOrange, RoundedCornerShape(3.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("${localThermalLimit.toInt()}°C", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalOrange)
                }
            }

            Slider(
                value = localThermalLimit,
                onValueChange = {
                    localThermalLimit = it
                },
                valueRange = 50f..80f,
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(thumbColor = TacticalOrange, activeTrackColor = TacticalOrange, inactiveTrackColor = TacticalSurface)
            )

            // RAM Limit Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("RAM Pressure Safety Limit", fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalText)
                    Text("Auto-kills GPU compute when RAM usage exceeds", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                }
                Box(
                    modifier = Modifier
                        .border(1.dp, TacticalOrange, RoundedCornerShape(3.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("${localRamLimit.toInt()}%", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalOrange)
                }
            }

            Slider(
                value = localRamLimit,
                onValueChange = {
                    localRamLimit = it
                },
                valueRange = 60f..95f,
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(thumbColor = TacticalOrange, activeTrackColor = TacticalOrange, inactiveTrackColor = TacticalSurface)
            )
        }

        // ── SAVE CONFIG + REVERT ───────────────────────────────────────────────
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
                        prefs.edit()
                            .putBoolean("boot_auto_start", bootAutoStart)
                            .putBoolean("keep_wake_lock", keepWakeLock)
                            .putFloat("storage_quota", storageQuota)
                            .putBoolean("gpu_compute_enabled", localGpuComputeEnabled)
                            .putFloat("thermal_limit_c", localThermalLimit)
                            .putInt("ram_limit_pct", localRamLimit.toInt())
                            .apply()
                        savedBootAutoStart.value = bootAutoStart
                        savedKeepWakeLock.value  = keepWakeLock
                        savedStorageQuota.value  = storageQuota
                        savedGpuComputeEnabled.value = localGpuComputeEnabled
                        savedThermalLimit.value  = localThermalLimit
                        savedRamLimit.value      = localRamLimit

                        GpuComputeManager.setGpuComputeEnabled(localGpuComputeEnabled, localThermalLimit, localRamLimit.toInt(), persist = true)
                        ServerForegroundService.updateWakeLock()
                        android.widget.Toast.makeText(context, "System & GPU configuration saved", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "💾  [ SAVE CONFIG ]",
                    fontSize = 12.sp,
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
                        bootAutoStart = savedBootAutoStart.value
                        keepWakeLock  = savedKeepWakeLock.value
                        storageQuota  = savedStorageQuota.value
                        localGpuComputeEnabled = savedGpuComputeEnabled.value
                        localThermalLimit = savedThermalLimit.value
                        localRamLimit     = savedRamLimit.value
                        GpuComputeManager.setGpuComputeEnabled(savedGpuComputeEnabled.value, savedThermalLimit.value, savedRamLimit.value.toInt(), persist = false)
                        android.widget.Toast.makeText(context, "Config reverted to saved state", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "↺  [ REVERT ]",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = TacticalRed
                )
            }
        }

        // ── FOOTER ─────────────────────────────────────────────────────────────
        PocketNodeFooter()
    }

    // ── MOUNT NETWORK SHARE MODAL DIALOG ───────────────────────────────────────
    if (showMountDialog) {
        val hasRequired = targetShareHost.isNotBlank() && targetShareName.isNotBlank()

        AlertDialog(
            onDismissRequest = { if (!isMountingInProgress) showMountDialog = false },
            title = {
                Text(
                    "// MOUNT SMB SHARE",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = ElectricTeal
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Connect to Windows PC, Mac, or NAS share folder over local Wi-Fi:",
                        fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted
                    )

                    // 1. Host / IP Address (Required)
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Host / IP Address", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                            Text(" *", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalOrange)
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        OutlinedTextField(
                            value = targetShareHost,
                            onValueChange = { targetShareHost = it },
                            placeholder = { Text("e.g. 192.168.1.5", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TacticalSubtle) },
                            singleLine = true,
                            isError = targetShareHost.isBlank(),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ElectricTeal,
                                errorBorderColor = TacticalOrange.copy(alpha = 0.7f)
                            )
                        )
                    }

                    // 2. Share Name (Required)
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Share Name", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                            Text(" *", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalOrange)
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        OutlinedTextField(
                            value = targetShareName,
                            onValueChange = { targetShareName = it },
                            placeholder = { Text("e.g. Movies, Public, Shared", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TacticalSubtle) },
                            singleLine = true,
                            isError = targetShareName.isBlank(),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ElectricTeal,
                                errorBorderColor = TacticalOrange.copy(alpha = 0.7f)
                            )
                        )
                    }

                    // 3. Username (OPTIONAL)
                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Username", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                            Text("(OPTIONAL - Guest)", fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = TacticalSubtle)
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        OutlinedTextField(
                            value = targetShareUser,
                            onValueChange = { targetShareUser = it },
                            placeholder = { Text("Leave blank for anonymous / guest", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TacticalSubtle) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // 4. Password (OPTIONAL)
                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Password", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                            Text("(OPTIONAL)", fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = TacticalSubtle)
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        OutlinedTextField(
                            value = targetSharePass,
                            onValueChange = { targetSharePass = it },
                            placeholder = { Text("Leave blank if no password", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TacticalSubtle) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (!hasRequired) {
                        Text(
                            "* Host IP and Share Name are mandatory. On Windows, right-click folder > Properties > Sharing to verify.",
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TacticalOrange
                        )
                    }

                    if (mountErrorMsg != null) {
                        Text(
                            mountErrorMsg ?: "",
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TacticalRed
                        )
                    }
                }
            },
            confirmButton = {
                Box(
                    modifier = Modifier
                        .background(
                            if (!hasRequired || isMountingInProgress) TacticalSurface else ElectricTeal,
                            RoundedCornerShape(4.dp)
                        )
                        .clickable(enabled = !isMountingInProgress && hasRequired) {
                            isMountingInProgress = true
                            mountErrorMsg = null
                            NetworkShareManager.mountShare(
                                context = context,
                                host = targetShareHost.trim(),
                                port = targetSharePort.toIntOrNull() ?: 445,
                                shareName = targetShareName.trim(),
                                username = targetShareUser.trim(),
                                password = targetSharePass,
                                domain = targetShareDomain.trim()
                            ) { success, error ->
                                isMountingInProgress = false
                                if (success) {
                                    showMountDialog = false
                                } else {
                                    mountErrorMsg = error ?: "Mount failed."
                                }
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        if (isMountingInProgress) "CONNECTING..."
                        else "CONNECT & MOUNT",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = if (hasRequired && !isMountingInProgress) Color.Black else TacticalMuted
                    )
                }
            },
            dismissButton = {
                Box(
                    modifier = Modifier
                        .clickable(enabled = !isMountingInProgress) { showMountDialog = false }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text("CANCEL", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                }
            },
            containerColor = TacticalPanel
        )
    }

    // ── MANUAL STORAGE PATH MODAL DIALOG ───────────────────────────────────────
    if (showManualPathDialog) {
        AlertDialog(
            onDismissRequest = { showManualPathDialog = false },
            title = {
                Text(
                    "// CUSTOM_STORAGE_PATH",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = TacticalOrange
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Enter the absolute filesystem path on your device:",
                        fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted
                    )

                    OutlinedTextField(
                        value = manualPathInput,
                        onValueChange = { manualPathInput = it },
                        label = { Text("Absolute Storage Directory Path", fontSize = 10.sp, fontFamily = FontFamily.Monospace) },
                        singleLine = false,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Common Presets (Tap to use):", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalMuted)

                    listOf(
                        "/storage/emulated/0/Download/PocketNode",
                        "/storage/emulated/0/Download/Server test folder",
                        "/storage/emulated/0/Movies/PocketNode"
                    ).forEach { preset ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(TacticalSurface, RoundedCornerShape(3.dp))
                                .border(0.5.dp, TacticalBorder, RoundedCornerShape(3.dp))
                                .clickable { manualPathInput = preset }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text(preset, fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = ElectricTeal)
                        }
                    }
                }
            },
            confirmButton = {
                Box(
                    modifier = Modifier
                        .background(TacticalOrange, RoundedCornerShape(4.dp))
                        .clickable(enabled = manualPathInput.isNotBlank()) {
                            val ok = StorageManager.setCustomStorageDir(context, manualPathInput.trim())
                            if (ok) {
                                storagePath = manualPathInput.trim()
                                prefs.edit().putString("custom_storage_path", storagePath).apply()
                                android.widget.Toast.makeText(context, "Storage path updated successfully!", android.widget.Toast.LENGTH_SHORT).show()
                                showManualPathDialog = false
                            } else {
                                android.widget.Toast.makeText(context, "Cannot write to this folder. Check directory permissions.", android.widget.Toast.LENGTH_LONG).show()
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text("SET STORAGE PATH", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = Color.White)
                }
            },
            dismissButton = {
                Box(
                    modifier = Modifier
                        .clickable { showManualPathDialog = false }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text("CANCEL", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                }
            },
            containerColor = TacticalPanel
        )
    }
}

// --- Terminal-style Toggle ---

@Composable
private fun PocketTerminalToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalText)
            Text(subtitle, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted, lineHeight = 14.sp)
        }
        Box(
            modifier = Modifier
                .width(52.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (checked) StatusGreen else TacticalSurface)
                .border(1.5.dp, if (checked) StatusGreen else TacticalBorder, RoundedCornerShape(14.dp))
                .clickable { onCheckedChange(!checked) },
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 3.dp),
                horizontalArrangement = if (checked) Arrangement.End else Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(22.dp).clip(RoundedCornerShape(11.dp)).background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Text("I", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace, color = if (checked) StatusGreen else TacticalMuted)
                }
            }
        }
    }
}

