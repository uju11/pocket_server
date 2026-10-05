package com.pocketnode.client.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketnode.client.ui.theme.*

data class CacheItem(
    val id: String,
    val title: String,
    val detail: String,
    val sizeBytes: Long,
    val sizeFormatted: String,
    val isWatched: Boolean
)

@Composable
fun StorageDiagnosticsModal(
    onDismiss: () -> Unit,
    onPurgeCompleted: (String) -> Unit
) {
    val items = remember {
        mutableStateListOf(
            CacheItem("1", "Dune: Part Two (4K HDR Remux)", "Finished watching • Saved 2 days ago", 3_800_000_000L, "3.8 GB", true),
            CacheItem("2", "Severance S02E01 (1080p)", "Watched 85% • Stream cache", 1_200_000_000L, "1.2 GB", false),
            CacheItem("3", "Segment Chunk Pre-Buffer", "Ring buffer cache for active streams", 850_000_000L, "850 MB", false),
            CacheItem("4", "Subtitles (.srt / .vtt) & Posters", "Offline cache for offline browsing", 180_000_000L, "180 MB", false)
        )
    }

    val selectedIds = remember { mutableStateListOf("1", "2") }
    var cacheCapGb by remember { mutableStateOf(10f) }
    var evictFinishedImmediately by remember { mutableStateOf(true) }
    var wifiOnlyCaching by remember { mutableStateOf(true) }

    val scrollState = rememberScrollState()

    // Dialog container
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.88f)
                .clickable(enabled = false) {} // Prevent click-through
                .border(1.dp, TacticalAmber.copy(alpha = 0.5f), RoundedCornerShape(14.dp)),
            colors = CardDefaults.cardColors(containerColor = ObsidianBase),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "STORAGE & DEVICE",
                            color = TacticalAmber,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "FREE UP MEMORY & CACHE",
                            color = TextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(ObsidianCard)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState)
                ) {
                    // Footprint Analysis
                    Text(
                        text = "DEVICE FOOTPRINT ANALYSIS",
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = ObsidianCard),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("5.03 GB Cache", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                Text("48.2 GB Free on Device", color = TacticalGreen, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            }
                            Spacer(modifier = Modifier.height(8.dp))

                            // Multi-segment progress bar
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(ObsidianSurface)
                            ) {
                                Box(modifier = Modifier.weight(0.6f).fillMaxHeight().background(TacticalAmber))
                                Box(modifier = Modifier.weight(0.2f).fillMaxHeight().background(TacticalCyan))
                                Box(modifier = Modifier.weight(0.1f).fillMaxHeight().background(TacticalGreen))
                                Box(modifier = Modifier.weight(0.1f).fillMaxHeight().background(TextMuted))
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                LegendItem("Downloaded Media (3.8G)", TacticalAmber)
                                LegendItem("Chunks (850M)", TacticalCyan)
                                LegendItem("Art (180M)", TacticalGreen)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Items list
                    Text(
                        text = "SELECT VOLUMES TO EVICT",
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    items.forEach { item ->
                        val isSelected = selectedIds.contains(item.id)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    if (isSelected) selectedIds.remove(item.id)
                                    else selectedIds.add(item.id)
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) TacticalAmber.copy(alpha = 0.08f) else ObsidianCard
                            ),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) TacticalAmber.copy(alpha = 0.5f) else BorderSubtle
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { checked ->
                                            if (checked) selectedIds.add(item.id)
                                            else selectedIds.remove(item.id)
                                        },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = TacticalAmber,
                                            checkmarkColor = ObsidianBase,
                                            uncheckedColor = TextMuted
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = item.title,
                                            color = TextPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = item.detail,
                                            color = TextMuted,
                                            fontSize = 10.sp
                                        )
                                    }
                                }

                                Text(
                                    text = item.sizeFormatted,
                                    color = if (isSelected) TacticalAmber else TextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Auto-Prune Rules (Matching Image 2)
                    Text(
                        text = "AUTOMATED HYGIENE RULES",
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = ObsidianCard),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Maximum Cache Ceiling", color = TextPrimary, fontSize = 12.sp)
                                Text("${cacheCapGb.toInt()} GB", color = TacticalAmber, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            }
                            Slider(
                                value = cacheCapGb,
                                onValueChange = { cacheCapGb = it },
                                valueRange = 2f..30f,
                                colors = SliderDefaults.colors(
                                    thumbColor = TacticalAmber,
                                    activeTrackColor = TacticalAmber,
                                    inactiveTrackColor = ObsidianSurface
                                )
                            )

                            Divider(color = BorderSubtle, modifier = Modifier.padding(vertical = 6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Evict on finished stream", color = TextPrimary, fontSize = 12.sp)
                                    Text("Immediately purge when credits roll", color = TextMuted, fontSize = 10.sp)
                                }
                                Switch(
                                    checked = evictFinishedImmediately,
                                    onCheckedChange = { evictFinishedImmediately = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = ObsidianBase,
                                        checkedTrackColor = TacticalAmber
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Wi-Fi Only High Bitrate Caching", color = TextPrimary, fontSize = 12.sp)
                                    Text("Halt pre-buffer backgrounding on metered 5G", color = TextMuted, fontSize = 10.sp)
                                }
                                Switch(
                                    checked = wifiOnlyCaching,
                                    onCheckedChange = { wifiOnlyCaching = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = ObsidianBase,
                                        checkedTrackColor = TacticalAmber
                                    )
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Bottom CTA Buttons (Matching Image 2)
                val totalSelectedBytes = items.filter { selectedIds.contains(it.id) }.sumOf { it.sizeBytes }
                val totalSelectedGb = (totalSelectedBytes / 1_000_000_000.0)

                Button(
                    onClick = {
                        val freedStr = String.format("%.1f GB", totalSelectedGb)
                        items.removeAll { selectedIds.contains(it.id) }
                        selectedIds.clear()
                        onPurgeCompleted("Successfully purged $freedStr cache memory!")
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TacticalAmber),
                    shape = RoundedCornerShape(8.dp),
                    enabled = selectedIds.isNotEmpty()
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = null,
                        tint = ObsidianBase,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "FAST CLEAN (SELECTED: ${String.format("%.1f", totalSelectedGb)} GB)",
                        color = ObsidianBase,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {
                        items.clear()
                        selectedIds.clear()
                        onPurgeCompleted("Purged all non-essential cache (5.03 GB freed)")
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
                ) {
                    Text(
                        text = "PURGE ALL NON-ESSENTIAL (5.03 GB)",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, color = TextMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
    }
}
