package com.remotemedia.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
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
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import com.remotemedia.ServerForegroundService
import com.remotemedia.download.TorrentEngine
import com.remotemedia.download.TorrentTrackersManager
import com.remotemedia.services.JackettServer
import com.remotemedia.ui.theme.*

@Composable
fun TorrentTabScreen() {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val trackers by TorrentTrackersManager.trackersState.collectAsState()
    val indexers by TorrentTrackersManager.indexersState.collectAsState()

    var newTrackerUrl by remember { mutableStateOf("") }

    val activeTrackersCount = trackers.count { it.isEnabled }
    val activeIndexersCount = indexers.count { it.isEnabled }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TacticalCanvas)
            .verticalScroll(scrollState)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── 0. EMBEDDED JACKETT SERVER (PORT 9117) ──────────────────────────
        val jackettRunning by ServerForegroundService.jackettRunning.collectAsState()
        val jackettKey = remember { JackettServer.getApiKey() }

        PocketCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "// EMBEDDED_JACKETT_SERVER",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = TacticalMuted
                    )
                    Text(
                        "Native Torznab Engine (Port 9117)",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TacticalMuted.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                StatusBadge(
                    text = if (jackettRunning) "ONLINE • 9117" else "STANDBY",
                    color = if (jackettRunning) StatusGreen else TacticalMuted
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text("Torznab API Key", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TacticalText)
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(TacticalSurface, RoundedCornerShape(4.dp))
                        .border(1.dp, TacticalBorder, RoundedCornerShape(4.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Text(jackettKey, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = TacticalOrange, maxLines = 1)
                }
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Jackett API Key", jackettKey)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "API Key copied to clipboard!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TacticalPanel),
                    border = BorderStroke(1.dp, TacticalBorder),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text("COPY", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Torznab Feed: http://<phone-ip>:9117/api/v2.0/indexers/all/results/torznab/api?apikey=$jackettKey&t=search",
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                color = TacticalMuted
            )
        }

        // ── 1. SEARCH INDEXERS (BUILT-IN 24/7) ──────────────────────────────
        PocketCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "// SEARCH_INDEXERS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = TacticalMuted
                    )
                    Text(
                        "Direct scrapers (No PC / Jackett required)",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TacticalMuted.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                StatusBadge(
                    text = "$activeIndexersCount / ${indexers.size} ACTIVE",
                    color = if (activeIndexersCount > 0) StatusGreen else TacticalRed
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            indexers.forEach { indexer ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .background(TacticalSurface, RoundedCornerShape(4.dp))
                        .border(1.dp, TacticalBorder, RoundedCornerShape(4.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(if (indexer.isEnabled) StatusGreen else TacticalMuted)
                            )
                            Text(
                                indexer.name,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = TacticalText
                            )
                            Text(
                                "[${indexer.domain}]",
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                color = TacticalOrange
                            )
                        }
                        Text(
                            indexer.description,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TacticalMuted
                        )
                    }

                    Switch(
                        checked = indexer.isEnabled,
                        onCheckedChange = { TorrentTrackersManager.toggleIndexer(indexer.id, it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = TacticalOrange,
                            uncheckedThumbColor = TacticalMuted,
                            uncheckedTrackColor = TacticalSurface
                        )
                    )
                }
            }
        }

        // ── 2. TORRENT ANNOUNCE TRACKERS ────────────────────────────────────
        PocketCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "// SWARM_ANNOUNCE_TRACKERS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = TacticalMuted
                    )
                    Text(
                        "Injected into magnets for max peer discovery & speed",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TacticalMuted.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                StatusBadge(
                    text = "$activeTrackersCount TRACKERS",
                    color = StatusGreen
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Add Tracker Input Field
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedTextField(
                    value = newTrackerUrl,
                    onValueChange = { newTrackerUrl = it },
                    placeholder = {
                        Text(
                            "udp://tracker.example.com:1337/announce",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TacticalMuted
                        )
                    },
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TacticalText
                    ),
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TacticalOrange,
                        unfocusedBorderColor = TacticalBorder,
                        focusedContainerColor = TacticalSurface,
                        unfocusedContainerColor = TacticalSurface
                    )
                )

                Button(
                    onClick = {
                        if (newTrackerUrl.isNotBlank()) {
                            val success = TorrentTrackersManager.addTracker(newTrackerUrl)
                            if (success) {
                                newTrackerUrl = ""
                                Toast.makeText(context, "Tracker added!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Tracker already exists or invalid", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TacticalOrange),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.Black, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("ADD", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = Color.Black)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Action row: Reset to default trackers
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Row(
                    modifier = Modifier
                        .clickable {
                            TorrentTrackersManager.resetTrackersToDefaults()
                            Toast.makeText(context, "Reset to default public trackers", Toast.LENGTH_SHORT).show()
                        }
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reset", tint = TacticalMuted, modifier = Modifier.size(12.dp))
                    Text("RESET DEFAULTS", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Trackers List
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                trackers.forEach { tracker ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(TacticalSurface, RoundedCornerShape(3.dp))
                            .border(1.dp, TacticalBorder, RoundedCornerShape(3.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Protocol Badge
                        val badgeColor = when (tracker.protocol) {
                            "UDP" -> StatusGreen
                            "HTTPS", "HTTP" -> TacticalOrange
                            "WSS" -> TacticalCyan
                            else -> TacticalMuted
                        }
                        Box(
                            modifier = Modifier
                                .border(1.dp, badgeColor.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
                                .background(badgeColor.copy(alpha = 0.1f))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                tracker.protocol,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = badgeColor
                            )
                        }

                        // URL Text
                        Text(
                            tracker.url,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (tracker.isEnabled) TacticalText else TacticalMuted,
                            modifier = Modifier.weight(1f),
                            maxLines = 1
                        )

                        // Enable / Disable Checkbox or small switch
                        Checkbox(
                            checked = tracker.isEnabled,
                            onCheckedChange = { TorrentTrackersManager.toggleTracker(tracker.url, it) },
                            colors = CheckboxDefaults.colors(
                                checkedColor = TacticalOrange,
                                uncheckedColor = TacticalMuted,
                                checkmarkColor = Color.Black
                            ),
                            modifier = Modifier.size(24.dp)
                        )

                        // Delete button
                        IconButton(
                            onClick = { TorrentTrackersManager.removeTracker(tracker.url) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = TacticalRed.copy(alpha = 0.7f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }

        // ── 3. ENGINE HARDWARE STATS ────────────────────────────────────────
        PocketCard {
            Text(
                "// NATIVE_BITTORRENT_STACK",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = TacticalMuted
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Core Protocol Engine", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TacticalText)
                Text("libtorrent4j (C++ native)", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = StatusGreen)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("DHT + PeX Swarm Discovery", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TacticalText)
                Text("ENABLED (Decentralized)", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TacticalCyan)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Active Download Queue", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TacticalText)
                Text("${TorrentEngine.activeCount} active / ${TorrentEngine.completedCount} finished", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TacticalOrange)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
