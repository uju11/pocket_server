package com.pocketnode.client.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketnode.client.data.DownloadItem
import com.pocketnode.client.data.RemoteEndpoint
import com.pocketnode.client.data.UserRole
import com.pocketnode.client.ui.components.RoleBadge
import com.pocketnode.client.ui.components.TacticalHeader
import com.pocketnode.client.ui.theme.*

@Composable
fun TransfersTabScreen(
    currentRole: UserRole,
    onRoleChange: (UserRole) -> Unit,
    incognitoMode: Boolean,
    onToggleIncognito: () -> Unit,
    onToast: (String) -> Unit,
    onPlayMovie: ((title: String, streamUrl: String, formatBadge: String) -> Unit)? = null
) {
    var filterTab by remember { mutableStateOf("ALL") } // ALL, LOCAL DEVICE, REMOTE WI-FI PUSH
    var showRequestDialog by remember { mutableStateOf(false) }
    var requestMagnetOrUrl by remember { mutableStateOf("") }

    val downloads = remember {
        mutableStateListOf(
            DownloadItem(
                id = "1",
                title = "Furiosa: A Mad Max Saga",
                sizeFormatted = "18.4 GB",
                progress = 0.68f,
                speedFormatted = "14.2 MB/s",
                status = "DOWNLOADING",
                targetDevice = "This Device (Offline Cache)"
            ),
            DownloadItem(
                id = "2",
                title = "Severance - S02 Complete Pack",
                sizeFormatted = "32.0 GB",
                progress = 0.12f,
                speedFormatted = "8.9 MB/s",
                status = "DOWNLOADING",
                targetDevice = "Living Room Shield TV"
            ),
            DownloadItem(
                id = "3",
                title = "Oppenheimer (2023) IMAX Remux",
                sizeFormatted = "42.8 GB",
                progress = 1.0f,
                speedFormatted = "Completed",
                status = "COMPLETED",
                targetDevice = "This Device (Local Play)"
            ),
            DownloadItem(
                id = "4",
                title = "Spider-Man: Across the Spider-Verse",
                sizeFormatted = "9.6 GB",
                progress = 1.0f,
                speedFormatted = "Completed",
                status = "COMPLETED",
                targetDevice = "Kids Tablet"
            )
        )
    }

    val remoteDevices = listOf(
        RemoteEndpoint("1", "Living Room Shield TV", "Android TV • 192.168.1.120", true),
        RemoteEndpoint("2", "MacBook Pro M3 (Study)", "macOS • 192.168.1.145", true),
        RemoteEndpoint("3", "Kids Room Tablet", "Android 14 • 192.168.1.160", false)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBase)
    ) {
        // Sticky Header
        TacticalHeader(
            currentRole = currentRole,
            incognitoMode = incognitoMode,
            onToggleIncognito = onToggleIncognito,
            onRoleClick = {
                val next = when (currentRole) {
                    UserRole.ADMIN -> UserRole.GUEST
                    UserRole.GUEST -> UserRole.KIDS
                    UserRole.KIDS -> UserRole.ADMIN
                }
                onRoleChange(next)
            }
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            item {
                // Screen Title
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "TRANSFERS & SYNC",
                            color = TextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "OFFLINE STORAGE & WI-FI DIRECT DISPATCH",
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    if (currentRole != UserRole.KIDS) {
                        Button(
                            onClick = { showRequestDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = TacticalAmber),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = ObsidianBase, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "REQUEST",
                                color = ObsidianBase,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            // Kids Restriction Notice
            if (currentRole == UserRole.KIDS) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        colors = CardDefaults.cardColors(containerColor = TacticalCyan.copy(alpha = 0.1f)),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, TacticalCyan.copy(alpha = 0.4f))
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = TacticalCyan, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Kids mode active: Direct file dispatch and external transfers are restricted.",
                                color = TacticalCyan,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            // Filter Tabs
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(ObsidianCard)
                        .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf("ALL", "LOCAL DEVICE", "REMOTE WI-FI PUSH").forEach { tab ->
                        val isSelected = filterTab == tab
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) TacticalAmber.copy(alpha = 0.15f) else Color.Transparent)
                                .clickable { filterTab = tab }
                                .padding(vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tab,
                                color = if (isSelected) TacticalAmber else TextMuted,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            // Live Queue Section
            val activeList = downloads.filter { it.status == "DOWNLOADING" }
            if (activeList.isNotEmpty()) {
                item {
                    Text(
                        text = "01 // ACTIVE INGESTION QUEUE (${activeList.size})",
                        color = TacticalAmber,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                items(activeList) { item ->
                    TransferCard(item = item, onAction = { onToast(it) })
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }

            // Completed Section
            val completedList = downloads.filter { it.status == "COMPLETED" }
            if (completedList.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "02 // COMPLETED & READY OFFLINE (${completedList.size})",
                        color = TacticalGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                items(completedList) { item ->
                    TransferCard(
                        item = item,
                        onAction = { onToast(it) },
                        onPlay = {
                            if (onPlayMovie != null) {
                                onPlayMovie(item.title, item.localFilePath.ifBlank { item.title }, "OFFLINE • 4K REMUX")
                            } else {
                                onToast("Playing: ${item.title}")
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }

            // Remote Target Devices (Push to Wi-Fi)
            if (currentRole != UserRole.KIDS) {
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "03 // DIRECT WI-FI PUSH TARGETS (AUTODISCOVERED)",
                        color = TacticalCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                items(remoteDevices) { device ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = ObsidianCard),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(if (device.isOnline) TacticalCyan.copy(alpha = 0.15f) else ObsidianSurface),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (device.name.contains("TV")) Icons.Default.Tv else Icons.Default.Devices,
                                        contentDescription = null,
                                        tint = if (device.isOnline) TacticalCyan else TextMuted,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(device.name, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Text(device.ip, color = TextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                }
                            }

                            Button(
                                onClick = { onToast("Push payload sent to ${device.name}") },
                                colors = ButtonDefaults.buttonColors(containerColor = TacticalCyan.copy(alpha = 0.2f)),
                                shape = RoundedCornerShape(6.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, TacticalCyan.copy(alpha = 0.5f)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                enabled = device.isOnline
                            ) {
                                Text("PUSH MEDIA", color = TacticalCyan, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(100.dp))
            }
        }
    }

    // Modal: Request Download Dialog
    if (showRequestDialog) {
        AlertDialog(
            onDismissRequest = { showRequestDialog = false },
            title = {
                Text(
                    text = "REQUEST MEDIA DOWNLOAD",
                    color = TacticalAmber,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            },
            text = {
                Column {
                    Text(
                        text = "Enter Torrent Magnet URI, Direct Video URL or Title to dispatch to host transmission engine.",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = requestMagnetOrUrl,
                        onValueChange = { requestMagnetOrUrl = it },
                        label = { Text("magnet:?xt=urn:btih:... or URL") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TacticalAmber,
                            unfocusedBorderColor = BorderSubtle,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (requestMagnetOrUrl.isNotBlank()) {
                            downloads.add(
                                DownloadItem(
                                    id = System.currentTimeMillis().toString(),
                                    title = "Requested Ingestion (${requestMagnetOrUrl.take(16)}...)",
                                    sizeFormatted = "Allocating...",
                                    progress = 0.05f,
                                    speedFormatted = "Connecting",
                                    status = "DOWNLOADING",
                                    targetDevice = "This Device (Local Cache)"
                                )
                            )
                            onToast("Dispatched download request to host daemon!")
                            requestMagnetOrUrl = ""
                            showRequestDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TacticalAmber)
                ) {
                    Text("DISPATCH", color = ObsidianBase, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRequestDialog = false }) {
                    Text("CANCEL", color = TextMuted)
                }
            },
            containerColor = ObsidianCard
        )
    }
}

@Composable
private fun TransferCard(
    item: DownloadItem,
    onAction: (String) -> Unit,
    onPlay: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = ObsidianCard),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Destination: ${item.targetDevice}",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (item.status == "COMPLETED") TacticalGreen.copy(alpha = 0.15f)
                            else TacticalAmber.copy(alpha = 0.15f)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = item.status,
                        color = if (item.status == "COMPLETED") TacticalGreen else TacticalAmber,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Progress bar
            LinearProgressIndicator(
                progress = { item.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (item.status == "COMPLETED") TacticalGreen else TacticalAmber,
                trackColor = ObsidianSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${(item.progress * 100).toInt()}% • ${item.sizeFormatted}",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.speedFormatted,
                        color = if (item.status == "COMPLETED") TacticalGreen else TacticalCyan,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    IconButton(
                        onClick = {
                            if (item.status == "COMPLETED") {
                                if (onPlay != null) onPlay()
                                else onAction("Playing ${item.title}")
                            } else {
                                onAction("Paused download of ${item.title}")
                            }
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = if (item.status == "COMPLETED") Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
