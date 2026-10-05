package com.remotemedia.ui

import com.remotemedia.ServerForegroundService

import android.content.Context
import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.remotemedia.core.*
import com.remotemedia.ui.theme.*

@Composable
fun StorageDirectoryCard(
    storagePath: String,
    freeGb: Double,
    totalGb: Double,
    onBrowseSaf: () -> Unit
) {
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
            Text("// STORAGE_DIRECTORY", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalMuted)
            Box(
                modifier = Modifier
                    .border(1.dp, StatusGreen, RoundedCornerShape(3.dp))
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
                Text("EXT4 / R-W", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = StatusGreen)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Active path display
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(TacticalSurface, RoundedCornerShape(4.dp))
                .border(1.dp, TacticalBorder, RoundedCornerShape(4.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("📁", fontSize = 14.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                storagePath,
                fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TacticalText,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Android Privacy Notice about /Download
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(TacticalOrange.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
                .border(1.dp, TacticalOrange.copy(alpha = 0.35f), RoundedCornerShape(4.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Text("ℹ️", fontSize = 12.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text(
                        "Android Privacy Notice: Android blocks selecting root /Download directly.",
                        fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalOrange
                    )
                    Text(
                        "Use [+ CREATE NEW FOLDER] inside Download or select another folder (like /Movies).",
                        fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted, lineHeight = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "%.1f GB FREE".format(freeGb),
                    fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted
                )
                Text(
                    "%.1f GB TOTAL".format(totalGb),
                    fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = TacticalSubtle
                )
            }
            Box(
                modifier = Modifier
                    .background(TacticalOrange, RoundedCornerShape(4.dp))
                    .clickable { onBrowseSaf() }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text("📂  [ SAF BROWSE ]", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = Color.White)
            }
        }
    }
}

@Composable
fun HostDeviceSharingCard(
    hostSharingEnabled: Boolean,
    sharedFolders: List<HostSharedFolder>,
    virtualFiles: List<HostVirtualMediaFile>,
    isScanningHostMedia: Boolean,
    onToggleSharing: (Boolean) -> Unit,
    onAddFolder: () -> Unit,
    onRescan: () -> Unit,
    onRemoveFolder: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(TacticalPanel, RoundedCornerShape(6.dp))
            .border(1.dp, if (hostSharingEnabled) ElectricTeal.copy(alpha = 0.5f) else TacticalBorder, RoundedCornerShape(6.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "// HOST_MEDIA_SHARING",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = TacticalMuted,
                modifier = Modifier.weight(1f, fill = false),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .border(1.dp, if (hostSharingEnabled) StatusGreen else TacticalSubtle, RoundedCornerShape(3.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    if (hostSharingEnabled) "V-MOUNT (${sharedFolders.size})" else "MOUNT_OFF",
                    fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
                    color = if (hostSharingEnabled) StatusGreen else TacticalSubtle,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text("Share Phone Media with Server", fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalText)
                Text("Zero-copy virtual mount. Exposes phone folders (Downloads, DCIM, Movies) to streaming clients without moving or duplicating files.", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
            }
            Switch(
                checked = hostSharingEnabled,
                onCheckedChange = onToggleSharing,
                colors = SwitchDefaults.colors(checkedThumbColor = StatusGreen, checkedTrackColor = StatusGreen.copy(alpha = 0.3f))
            )
        }

        if (hostSharingEnabled) {
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = TacticalBorder.copy(alpha = 0.5f), thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1.15f)
                        .background(ElectricTeal.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                        .border(1.dp, ElectricTeal, RoundedCornerShape(4.dp))
                        .clickable { onAddFolder() }
                        .padding(horizontal = 8.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("+ ADD PHONE FOLDER", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = ElectricTeal, maxLines = 1)
                }

                Box(
                    modifier = Modifier
                        .weight(0.95f)
                        .background(TacticalSurface, RoundedCornerShape(4.dp))
                        .border(1.dp, TacticalBorder, RoundedCornerShape(4.dp))
                        .clickable(enabled = !isScanningHostMedia) { onRescan() }
                        .padding(horizontal = 8.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (isScanningHostMedia) "⟳ SCANNING..." else "⟳ RESCAN NOW",
                        fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
                        color = if (isScanningHostMedia) TacticalYellow else TacticalText,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            val totalHostSize = sharedFolders.sumOf { it.totalSizeBytes }
            val totalHostFiles = virtualFiles.size
            val moviesCount = virtualFiles.count { it.category == "Movies" }
            val musicCount = virtualFiles.count { it.category == "Music" }
            val videosCount = virtualFiles.count { it.category == "Videos" }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TacticalSurface, RoundedCornerShape(4.dp))
                    .border(1.dp, TacticalBorder.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "INDEXED: $totalHostFiles (${formatHostSize(totalHostSize)})",
                    fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = StatusGreen, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 1
                )
                Text(
                    "🎬 $moviesCount • 🎵 $musicCount • 🎥 $videosCount",
                    fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (sharedFolders.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TacticalSurface.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .border(1.dp, TacticalBorder, RoundedCornerShape(4.dp))
                        .padding(14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "// NO PHONE FOLDERS SHARED\nTap [+ ADD PHONE FOLDER] to grant read-only virtual mount access to any local folder.",
                        fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TacticalSubtle, lineHeight = 14.sp
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (folder in sharedFolders) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(TacticalSurface, RoundedCornerShape(4.dp))
                                .border(1.dp, TacticalBorder, RoundedCornerShape(4.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text("📁 ${folder.displayName}", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${folder.fileCount} files • ${formatHostSize(folder.totalSizeBytes)} • SAF Virtual", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Box(
                                modifier = Modifier
                                    .border(1.dp, TacticalRed.copy(alpha = 0.7f), RoundedCornerShape(3.dp))
                                    .clickable { onRemoveFolder(folder.id) }
                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                            ) {
                                Text("✕ REMOVE", fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalRed)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LanNetworkSharesCard(
    isScanningNetwork: Boolean,
    scanProgress: Float,
    discoveredShares: List<DiscoveredShare>,
    mountedShares: List<MountedShare>,
    networkFiles: List<NetworkVirtualMediaFile>,
    onScan: () -> Unit,
    onAddShareManually: () -> Unit,
    onConnectShare: (DiscoveredShare) -> Unit,
    onUnmountShare: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(TacticalPanel, RoundedCornerShape(6.dp))
            .border(1.dp, if (mountedShares.isNotEmpty()) ElectricTeal.copy(alpha = 0.5f) else TacticalBorder, RoundedCornerShape(6.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("// DISCOVERED_NETWORK_SHARES", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = ElectricTeal)
                Text("UPnP, DLNA, SMB & NFS Discovery", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
            }
            Box(
                modifier = Modifier
                    .border(1.dp, if (isScanningNetwork) TacticalYellow else if (mountedShares.isNotEmpty()) StatusGreen else TacticalSubtle, RoundedCornerShape(3.dp))
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
                Text(
                    if (isScanningNetwork) "SCANNING (${(scanProgress * 100).toInt()}%)"
                    else if (mountedShares.isNotEmpty()) "MOUNTED (${mountedShares.size})"
                    else "IDLE",
                    fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
                    color = if (isScanningNetwork) TacticalYellow else if (mountedShares.isNotEmpty()) StatusGreen else TacticalSubtle
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            "Auto-detects Windows SMB/Samba, Mac, NAS, and UPnP / DLNA media servers (e.g. Windows Media Player) on your Wi-Fi subnet. Mount and stream media directly with zero local storage footprint.",
            fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted, lineHeight = 14.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(ElectricTeal.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                    .border(1.dp, ElectricTeal, RoundedCornerShape(4.dp))
                    .clickable(enabled = !isScanningNetwork) { onScan() }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(if (isScanningNetwork) "⟳ SCANNING SUBNET..." else "⟳ SCAN LOCAL SUBNET", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = ElectricTeal)
            }

            Box(
                modifier = Modifier
                    .background(TacticalSurface, RoundedCornerShape(4.dp))
                    .border(1.dp, TacticalBorder, RoundedCornerShape(4.dp))
                    .clickable { onAddShareManually() }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("+ ADD SHARE", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalText)
            }
        }

        // Discovered Shares list
        Spacer(modifier = Modifier.height(14.dp))
        Text("// LAN DISCOVERED TARGETS", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalMuted)
        Spacer(modifier = Modifier.height(6.dp))

        if (discoveredShares.isEmpty() && !isScanningNetwork) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TacticalSurface.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                    .border(1.dp, TacticalBorder, RoundedCornerShape(4.dp))
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "// NO LAN TARGETS DETECTED YET\nTap [SCAN LOCAL SUBNET] to discover Windows PCs, Macs, and NAS drives on your Wi-Fi, or tap [+ ADD SHARE] to connect manually.",
                    fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TacticalSubtle, lineHeight = 14.sp
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (disc in discoveredShares) {
                    val isMounted = mountedShares.any { it.host == disc.host }
                    val isUpnp = disc.protocol.contains("UPnP")
                    val icon = if (isUpnp) "📻" else "🖥️"

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(TacticalSurface, RoundedCornerShape(4.dp))
                            .border(1.dp, if (isMounted) StatusGreen.copy(alpha = 0.4f) else if (isUpnp) ElectricTeal.copy(alpha = 0.4f) else TacticalBorder, RoundedCornerShape(4.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "$icon ${disc.name}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = if (isUpnp) ElectricTeal else TacticalText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "${disc.host}:${disc.port} • Protocol: ${disc.protocol}",
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                color = if (isUpnp) StatusGreen else TacticalMuted
                            )
                        }

                        if (isMounted) {
                            Box(
                                modifier = Modifier
                                    .border(1.dp, StatusGreen, RoundedCornerShape(3.dp))
                                    .padding(horizontal = 7.dp, vertical = 3.dp)
                            ) {
                                Text("MOUNTED", fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = StatusGreen)
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .background(ElectricTeal.copy(alpha = 0.15f), RoundedCornerShape(3.dp))
                                    .border(1.dp, ElectricTeal, RoundedCornerShape(3.dp))
                                    .clickable { onConnectShare(disc) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(if (isUpnp) "UPnP LINK" else "CONNECT", fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = ElectricTeal)
                            }
                        }
                    }
                }
            }
        }
    }
}

fun formatHostSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    return String.format(java.util.Locale.US, "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

@Composable
fun CrossDevicePeerSharingCard(
    isMeshEnabled: Boolean,
    meshPeers: List<MeshPeerNode>,
    meshFileCount: Int,
    onToggleMesh: (Boolean) -> Unit,
    onClearAllPeers: () -> Unit,
    onRemovePeer: (String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val ipAddress by ServerForegroundService.ipAddressState.collectAsState()
    val webPort by ServerForegroundService.webPortState.collectAsState()
    val isRunning by ServerForegroundService.isRunning.collectAsState()
    val isWebRunning by ServerForegroundService.webRunning.collectAsState()

    val lanMeshUrl = if (ipAddress.isNotBlank() && ipAddress != "127.0.0.1" && !ipAddress.contains("pocketnode")) {
        "http://$ipAddress:$webPort/mesh"
    } else {
        "http://pocketnode.local:$webPort/mesh"
    }
    val mdnsMeshUrl = "http://pocketnode.local:$webPort/mesh"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(TacticalPanel, RoundedCornerShape(6.dp))
            .border(1.dp, if (isMeshEnabled) TacticalYellow.copy(alpha = 0.5f) else TacticalBorder, RoundedCornerShape(6.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "// PEER_MESH_SHARING",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = TacticalMuted,
                modifier = Modifier.weight(1f, fill = false),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .border(1.dp, if (isMeshEnabled) TacticalYellow else TacticalSubtle, RoundedCornerShape(3.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    if (isMeshEnabled) "MESH (${meshPeers.size})" else "MESH_OFF",
                    fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
                    color = if (isMeshEnabled) TacticalYellow else TacticalSubtle,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Master Mesh toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    "Cross-Device Peer Ingestion",
                    fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace, color = TacticalText
                )
                Text(
                    "Allow iPhones, PCs & Macs to share folders directly with PocketNode over Wi-Fi without uploading to the phone.",
                    fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted
                )
            }
            Switch(
                checked = isMeshEnabled,
                onCheckedChange = onToggleMesh,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = TacticalYellow,
                    checkedTrackColor = TacticalYellow.copy(alpha = 0.3f),
                    uncheckedThumbColor = TacticalMuted,
                    uncheckedTrackColor = TacticalSurface
                )
            )
        }

        if (isMeshEnabled) {
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = TacticalBorder.copy(alpha = 0.5f), thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(12.dp))

            // Pairing Instructions & URL Box
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TacticalSurface, RoundedCornerShape(4.dp))
                    .border(1.dp, TacticalBorder, RoundedCornerShape(4.dp))
                    .padding(10.dp)
            ) {
                Text(
                    "💡 TO SHARE A FOLDER FROM IPHONE / PC / MAC:",
                    fontSize = 9.sp, fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace, color = TacticalYellow
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Open the Mesh portal on client browsers to broadcast media across the home network with zero phone storage used. Note: Use http:// (cleartext, NOT https://):",
                    fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted
                )
                if (!isRunning || !isWebRunning) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(TacticalRed.copy(alpha = 0.15f), RoundedCornerShape(3.dp))
                            .border(1.dp, TacticalRed.copy(alpha = 0.6f), RoundedCornerShape(3.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text(
                            "⚠️ POCKETNODE WEB SERVER IS OFFLINE — START POCKETNODE DAEMON FIRST ON DASHBOARD",
                            fontSize = 8.5.sp, fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace, color = TacticalRed
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                
                // Primary: LAN Direct IP URL
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TacticalBg, RoundedCornerShape(4.dp))
                        .border(1.dp, TacticalBorder, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
                        Text("LAN DIRECT IP (RECOMMENDED):", fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                        Text(
                            lanMeshUrl,
                            fontSize = 10.sp, fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace, color = ElectricTeal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Box(
                        modifier = Modifier
                            .background(ElectricTeal.copy(alpha = 0.15f), RoundedCornerShape(3.dp))
                            .border(1.dp, ElectricTeal.copy(alpha = 0.7f), RoundedCornerShape(3.dp))
                            .clickable {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("PocketNode LAN Mesh URL", lanMeshUrl))
                                android.widget.Toast.makeText(context, "Copied LAN Mesh URL: $lanMeshUrl", android.widget.Toast.LENGTH_SHORT).show()
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "📋 COPY IP",
                            fontSize = 9.sp, fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace, color = ElectricTeal
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Secondary: mDNS Host URL
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TacticalBg, RoundedCornerShape(4.dp))
                        .border(1.dp, TacticalBorder, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
                        Text("BONJOUR / mDNS ZERO-CONF:", fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                        Text(
                            mdnsMeshUrl,
                            fontSize = 10.sp, fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace, color = TacticalText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Box(
                        modifier = Modifier
                            .background(TacticalSurface, RoundedCornerShape(3.dp))
                            .border(1.dp, TacticalBorder, RoundedCornerShape(3.dp))
                            .clickable {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("PocketNode mDNS Mesh URL", mdnsMeshUrl))
                                android.widget.Toast.makeText(context, "Copied mDNS URL: $mdnsMeshUrl", android.widget.Toast.LENGTH_SHORT).show()
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "📋 COPY",
                            fontSize = 9.sp, fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace, color = TacticalMuted
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Connected Peers Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "// CONNECTED CLIENT PEERS (${meshPeers.size})",
                    fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace, color = TacticalMuted
                )
                if (meshPeers.isNotEmpty()) {
                    Text(
                        "[ CLEAR ALL ]",
                        fontSize = 9.sp, fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace, color = TacticalRed,
                        modifier = Modifier.clickable { onClearAllPeers() }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (meshPeers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TacticalSurface, RoundedCornerShape(4.dp))
                        .border(1.dp, TacticalBorder, RoundedCornerShape(4.dp))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "NO CLIENT PEERS ACTIVE — WAITING FOR CONNECTIONS",
                        fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = TacticalSubtle
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    meshPeers.forEach { peer ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(TacticalSurface, RoundedCornerShape(4.dp))
                                .border(1.dp, if (peer.isOnline) StatusGreen.copy(alpha = 0.3f) else TacticalBorder, RoundedCornerShape(4.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f).padding(end = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(peer.deviceType.icon, fontSize = 16.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            peer.deviceName,
                                            fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace, color = TacticalText,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .background(
                                                    if (peer.isOnline) StatusGreen.copy(alpha = 0.15f) else TacticalSubtle.copy(alpha = 0.15f),
                                                    RoundedCornerShape(2.dp)
                                                )
                                                .padding(horizontal = 4.dp, vertical = 1.dp)
                                        ) {
                                            Text(
                                                if (peer.isOnline) "ONLINE" else "OFFLINE",
                                                fontSize = 8.sp, fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace,
                                                color = if (peer.isOnline) StatusGreen else TacticalSubtle
                                            )
                                        }
                                    }
                                    Text(
                                        "${peer.deviceType.label} • ${peer.ipAddress} • ${peer.fileCount} files",
                                        fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .background(TacticalRed.copy(alpha = 0.1f), RoundedCornerShape(3.dp))
                                    .border(1.dp, TacticalRed.copy(alpha = 0.3f), RoundedCornerShape(3.dp))
                                    .clickable { onRemovePeer(peer.id) }
                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    "REMOVE",
                                    fontSize = 9.sp, fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace, color = TacticalRed
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
