package com.pocketnode.client.ui.screens

import android.text.format.DateFormat
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketnode.client.data.*
import com.pocketnode.client.ui.theme.*
import java.util.Date

@Composable
fun MediaScannerScreen() {
    val context = LocalContext.current
    val scanState by ClientRepository.scanState.collectAsState()
    val libraryMovies by ClientRepository.libraryMovies.collectAsState()
    val telegramMovies by ClientRepository.telegramMovies.collectAsState()
    val trendingTorrents by ClientRepository.trendingTorrents.collectAsState()
    val logListState = rememberLazyListState()

    val isScanning = scanState.status == ScanStatus.SCANNING

    // Auto-scroll log to bottom on new entries
    LaunchedEffect(scanState.log.size) {
        if (scanState.log.isNotEmpty()) {
            logListState.animateScrollToItem(scanState.log.size - 1)
        }
    }

    // Spinning animation for the scan icon
    val spinAngle by rememberInfiniteTransition(label = "spin").animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "spin_angle"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(JellyfinBg)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        // ── Header ─────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF0D0A1A), Color(0xFF090A14))
                    )
                )
                .padding(horizontal = 18.dp, vertical = 20.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.ManageSearch,
                        contentDescription = null,
                        tint = NeonPurple,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        "METADATA SCANNER",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        color = Color.White,
                        letterSpacing = 1.5.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Scans device storage, Telegram catalog, and torrent index",
                    fontSize = 12.sp,
                    color = TacticalMuted
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Library Stats Row ──────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.VideoLibrary,
                iconTint = NeonPurple,
                label = "Library",
                value = "${libraryMovies.size}"
            )
            StatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.Send,
                iconTint = NeonCyan,
                label = "Telegram",
                value = "${telegramMovies.size}"
            )
            StatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.CloudDownload,
                iconTint = AmberOrange,
                label = "Torrents",
                value = "${trendingTorrents.size}"
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Scan Progress Card ─────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(JellyfinCard)
                .border(1.dp, JellyfinBorder, RoundedCornerShape(14.dp))
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.SyncAlt,
                            contentDescription = null,
                            tint = if (isScanning) NeonCyan else TacticalMuted,
                            modifier = Modifier
                                .size(18.dp)
                                .then(if (isScanning) Modifier.rotate(spinAngle) else Modifier)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when (scanState.status) {
                                ScanStatus.IDLE -> "READY TO SCAN"
                                ScanStatus.SCANNING -> "SCANNING..."
                                ScanStatus.DONE -> "SCAN COMPLETE"
                                ScanStatus.ERROR -> "SCAN FAILED"
                            },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = when (scanState.status) {
                                ScanStatus.IDLE -> TacticalMuted
                                ScanStatus.SCANNING -> NeonCyan
                                ScanStatus.DONE -> StatusGreen
                                ScanStatus.ERROR -> AmberOrange
                            }
                        )
                    }

                    if (scanState.lastScanTs > 0L) {
                        Text(
                            text = "Last: ${DateFormat.format("HH:mm", Date(scanState.lastScanTs))}",
                            fontSize = 10.sp,
                            color = TacticalSubtle,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Progress bar
                if (isScanning || scanState.status == ScanStatus.DONE) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (scanState.currentPath.isNotBlank()) {
                            Text(
                                text = "→ ${scanState.currentPath}",
                                fontSize = 11.sp,
                                color = TacticalMuted,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        LinearProgressIndicator(
                            progress = { scanState.progressPercent },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = if (scanState.status == ScanStatus.DONE) StatusGreen else NeonPurple,
                            trackColor = JellyfinBorder
                        )
                        if (scanState.scannedCount > 0) {
                            Text(
                                text = "${scanState.scannedCount} files scanned  •  ${scanState.newFoundCount} new",
                                fontSize = 11.sp,
                                color = TacticalMuted
                            )
                        }
                    }
                }

                // Action buttons
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Manual Scan Button
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isScanning) JellyfinBorder
                                else Brush.horizontalGradient(listOf(NeonPurpleDark, NeonPurple))
                            )
                            .clickable(enabled = !isScanning) {
                                ClientRepository.runMetadataScan(context)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (isScanning) Icons.Filled.HourglassTop else Icons.Filled.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (isScanning) "SCANNING..." else "SCAN NOW",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // ── Schedule Section ───────────────────────────────────────────────
        SectionHeader(icon = Icons.Filled.Schedule, title = "SCHEDULED SCAN")

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(JellyfinCard)
                .border(1.dp, JellyfinBorder, RoundedCornerShape(14.dp))
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Auto-scan interval",
                    fontSize = 12.sp,
                    color = TacticalMuted,
                    fontFamily = FontFamily.Monospace
                )

                val intervals = listOf(0 to "Disabled", 1 to "Every 1h", 6 to "Every 6h", 12 to "Every 12h", 24 to "Daily")

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    intervals.forEach { (hours, label) ->
                        val isSelected = scanState.scheduledIntervalHours == hours
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) NeonPurple.copy(alpha = 0.2f) else JellyfinBorder.copy(alpha = 0.3f))
                                .border(
                                    1.dp,
                                    if (isSelected) NeonPurple else JellyfinBorder,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { ClientRepository.setScheduledScanInterval(hours) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) NeonPurpleLight else TacticalMuted,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                if (scanState.scheduledIntervalHours > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(StatusGreen, CircleShape)
                        )
                        Text(
                            "Next scan in ${scanState.scheduledIntervalHours}h — auto-discovers new downloads & Telegram files",
                            fontSize = 10.sp,
                            color = StatusGreen
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // ── Scan Paths Section ────────────────────────────────────────────
        SectionHeader(icon = Icons.Filled.FolderOpen, title = "SCAN LOCATIONS")

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val paths = listOf(
                "📁 /storage/emulated/0/Movies" to "Local Movies folder",
                "📁 /storage/emulated/0/Download" to "Downloads folder",
                "📁 Android MediaStore (Videos)" to "System video index",
                "📡 Telegram Bot Bridge" to "http://192.168.1.100:8765/api/telegram_catalog",
                "🌊 YTS / 1337x Index" to "http://192.168.1.100:8765/api/trending"
            )
            paths.forEach { (path, desc) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(JellyfinCard)
                        .border(1.dp, JellyfinBorder, RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(path, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TacticalText)
                        Text(desc, fontSize = 10.sp, color = TacticalSubtle, fontFamily = FontFamily.Monospace)
                    }
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                if (scanState.status == ScanStatus.DONE) StatusGreen else TacticalBorder,
                                CircleShape
                            )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // ── Live Scan Log ─────────────────────────────────────────────────
        if (scanState.log.isNotEmpty()) {
            SectionHeader(icon = Icons.Filled.Terminal, title = "SCAN LOG")

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .padding(horizontal = 14.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF07080F))
                    .border(1.dp, JellyfinBorder, RoundedCornerShape(12.dp))
            ) {
                LazyColumn(
                    state = logListState,
                    contentPadding = PaddingValues(10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(scanState.log) { entry ->
                        Text(
                            text = entry.message,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = when {
                                entry.isError -> AmberOrange
                                entry.message.contains("✓") -> StatusGreen
                                entry.message.contains("[WARN]") -> TacticalYellow
                                else -> NeonCyan
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // ── Discovered Media Preview ──────────────────────────────────────
        if (scanState.status == ScanStatus.DONE && libraryMovies.isNotEmpty()) {
            SectionHeader(icon = Icons.Filled.CheckCircle, title = "DISCOVERED MEDIA")

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                libraryMovies.take(6).forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(JellyfinCard)
                            .border(1.dp, JellyfinBorder, RoundedCornerShape(10.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(NeonPurple.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Movie,
                                contentDescription = null,
                                tint = NeonPurpleLight,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                item.title,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TacticalTextWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "${item.formatBadge} • ${item.sizeFormatted}",
                                fontSize = 10.sp,
                                color = TacticalMuted,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Text(
                            item.category,
                            fontSize = 9.sp,
                            color = NeonCyanMuted,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                if (libraryMovies.size > 6) {
                    Text(
                        "+ ${libraryMovies.size - 6} more in Library",
                        fontSize = 11.sp,
                        color = TacticalMuted,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    iconTint: Color,
    label: String,
    value: String
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(JellyfinCard)
            .border(1.dp, JellyfinBorder, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.Black, color = Color.White)
            Text(label, fontSize = 10.sp, color = TacticalMuted, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun SectionHeader(icon: ImageVector, title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = NeonPurple, modifier = Modifier.size(16.dp))
        Text(
            text = title,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = TacticalMuted,
            letterSpacing = 1.sp
        )
    }
}
