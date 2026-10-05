package com.pocketnode.client.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.pocketnode.client.data.ClientRepository
import com.pocketnode.client.data.TelegramMediaDto
import com.pocketnode.client.ui.theme.*

@Composable
fun TelegramStreamScreen(
    onPlayMovie: (title: String, streamUrl: String, formatBadge: String) -> Unit,
    onToast: (String) -> Unit
) {
    val session by ClientRepository.session.collectAsState()
    val telegramMovies by ClientRepository.telegramMovies.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var selectedQuality by remember { mutableStateOf("All") }
    var selectedSource by remember { mutableStateOf("All") }

    val sources = listOf("All Sources", "Saved Messages", "Bot Forwarded")
    val qualities = listOf("All", "4K", "1080p", "720p", "Remux", "BluRay")

    val filtered = remember(telegramMovies, searchQuery, selectedQuality, selectedSource) {
        telegramMovies.filter { m ->
            val matchSearch = searchQuery.isBlank() ||
                m.cleanTitle.contains(searchQuery, ignoreCase = true) ||
                m.fileName.contains(searchQuery, ignoreCase = true)
            val matchQuality = selectedQuality == "All" ||
                m.quality.contains(selectedQuality, ignoreCase = true) ||
                m.fileName.contains(selectedQuality, ignoreCase = true)
            val matchSource = when (selectedSource) {
                "Saved Messages" -> m.source.contains("Saved", ignoreCase = true) || m.channelName.contains("Saved", ignoreCase = true)
                "Bot Forwarded" -> m.source.contains("Forward", ignoreCase = true) || m.source.contains("Channel", ignoreCase = true) || (!m.source.contains("Saved", ignoreCase = true) && m.channelName.isNotBlank())
                else -> true
            }
            matchSearch && matchQuality && matchSource
        }
    }

    val serverBase = session.serverHost.let { "http://$it:${session.serverPort}" }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(JellyfinBg),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // ── Header ─────────────────────────────────────────────────────────
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(listOf(Color(0xFF050C18), JellyfinBg))
                    )
                    .padding(horizontal = 18.dp, vertical = 20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Send,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                "TELEGRAM STREAM",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                color = Color.White,
                                letterSpacing = 1.5.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Saved messages & forwarded files • Stream without downloading",
                            fontSize = 12.sp,
                            color = TacticalMuted
                        )
                    }

                    // Refresh Button
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(JellyfinCard)
                            .border(1.dp, JellyfinBorder, CircleShape)
                            .clickable {
                                ClientRepository.refreshTelegramCatalog()
                                onToast("Syncing Telegram media catalog...")
                            }
                            .padding(8.dp)
                    ) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = "Sync",
                            tint = NeonCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // ── Search ─────────────────────────────────────────────────────────
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(JellyfinCard)
                    .border(1.dp, JellyfinBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Search, null, tint = TacticalSubtle, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (searchQuery.isEmpty()) {
                        Text("Search Telegram files...", color = TacticalSubtle, fontSize = 13.sp)
                    }
                    androidx.compose.foundation.text.BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontSize = 13.sp,
                            color = TacticalTextWhite
                        ),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(NeonCyan),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (searchQuery.isNotEmpty()) {
                    Icon(
                        Icons.Filled.Close,
                        null,
                        tint = TacticalMuted,
                        modifier = Modifier.size(16.dp).clickable { searchQuery = "" }
                    )
                }
            }
        }

        // ── Source Filter (Saved Messages vs Bot Forwarded) ────────────────
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            ) {
                items(sources) { src ->
                    val sel = (src == "All Sources" && selectedSource == "All") || src == selectedSource
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (sel) NeonCyan.copy(alpha = 0.2f) else JellyfinCard)
                            .border(1.dp, if (sel) NeonCyan else JellyfinBorder, RoundedCornerShape(20.dp))
                            .clickable { selectedSource = if (src == "All Sources") "All" else src }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            val icon = when (src) {
                                "Saved Messages" -> Icons.Filled.Bookmark
                                "Bot Forwarded" -> Icons.Filled.Shortcut
                                else -> Icons.Filled.AllInclusive
                            }
                            Icon(icon, null, tint = if (sel) NeonCyan else TacticalMuted, modifier = Modifier.size(12.dp))
                            Text(
                                src,
                                fontSize = 11.sp,
                                fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                color = if (sel) NeonCyan else TacticalMuted,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }

        // ── Quality filter chips ───────────────────────────────────────────
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                items(qualities) { q ->
                    val sel = q == selectedQuality
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (sel) NeonCyan.copy(alpha = 0.15f) else JellyfinCard)
                            .border(1.dp, if (sel) NeonCyan else JellyfinBorder, RoundedCornerShape(20.dp))
                            .clickable { selectedQuality = q }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            q,
                            fontSize = 11.sp,
                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                            color = if (sel) NeonCyan else TacticalMuted
                        )
                    }
                }
            }
        }

        // ── Stats bar ──────────────────────────────────────────────────────
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(6.dp).background(NeonCyan, CircleShape))
                Text(
                    "${filtered.size} files available",
                    fontSize = 12.sp,
                    color = TacticalMuted,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "BOT BRIDGE ACTIVE",
                    fontSize = 10.sp,
                    color = NeonCyan,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // ── Empty state ────────────────────────────────────────────────────
        if (filtered.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.ChatBubbleOutline, null, tint = TacticalSubtle, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("No Telegram files found", fontSize = 14.sp, color = TacticalMuted)
                        Text("Forward videos to your channel to see them here", fontSize = 12.sp, color = TacticalSubtle)
                    }
                }
            }
        } else {
            items(filtered, key = { it.id }) { media ->
                TelegramMediaCard(
                    media = media,
                    serverBase = serverBase,
                    onPlayMovie = onPlayMovie,
                    onToast = onToast
                )
            }
        }
    }
}

@Composable
private fun TelegramMediaCard(
    media: TelegramMediaDto,
    serverBase: String,
    onPlayMovie: (String, String, String) -> Unit,
    onToast: (String) -> Unit
) {
    val context = LocalContext.current
    var isExpanded by remember { mutableStateOf(false) }

    val fullStreamUrl = remember(media.streamUrl) {
        if (media.streamUrl.startsWith("http")) media.streamUrl
        else "$serverBase${media.streamUrl}"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(JellyfinCard)
            .border(1.dp, JellyfinBorder, RoundedCornerShape(14.dp))
            .clickable { isExpanded = !isExpanded }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Poster
            Box(
                modifier = Modifier
                    .width(68.dp)
                    .height(96.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(JellyfinBorder)
            ) {
                if (media.posterUrl.isNotBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(media.posterUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = media.cleanTitle,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Send, null, tint = NeonCyan.copy(alpha = 0.5f), modifier = Modifier.size(28.dp))
                    }
                }

                // Quality badge
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(NeonCyan.copy(alpha = 0.9f))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(media.quality, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color(0xFF050C18), fontFamily = FontFamily.Monospace)
                }
            }

            // Info column
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (media.year.isNotBlank()) {
                        Text(media.year, fontSize = 10.sp, color = TacticalSubtle, fontFamily = FontFamily.Monospace)
                    }
                    Text("•", fontSize = 10.sp, color = TacticalSubtle)
                    Icon(Icons.Filled.Send, null, tint = NeonCyan, modifier = Modifier.size(11.dp))
                    Text("Telegram", fontSize = 10.sp, color = NeonCyan, fontFamily = FontFamily.Monospace)
                }

                Text(
                    media.cleanTitle,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TacticalTextWhite,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    media.sizeFormatted.ifBlank { "Unknown size" },
                    fontSize = 11.sp,
                    color = TacticalMuted,
                    fontFamily = FontFamily.Monospace
                )

                // File name (truncated)
                Text(
                    media.fileName,
                    fontSize = 10.sp,
                    color = TacticalSubtle,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (isExpanded) {
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Stream button (primary)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    Brush.horizontalGradient(listOf(Color(0xFF006B8F), NeonCyan.copy(alpha = 0.7f)))
                                )
                                .clickable {
                                    if (fullStreamUrl.isBlank()) {
                                        onToast("Stream URL unavailable — check server")
                                    } else {
                                        onPlayMovie(media.cleanTitle, fullStreamUrl, media.quality)
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(14.dp))
                                Text("STREAM", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White, fontFamily = FontFamily.Monospace)
                            }
                        }

                        // Save / Download to Home Server independently
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF0C2920))
                                .border(1.dp, StatusGreen.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                .clickable {
                                    onToast("Queuing server download for ${media.cleanTitle}... 📥")
                                    ClientRepository.downloadTelegramToServer(media.fileId, media.fileName, media.fileSize) { ok, msg ->
                                        onToast(msg)
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 7.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                Icon(Icons.Filled.Download, null, tint = StatusGreen, modifier = Modifier.size(13.dp))
                                Text("SAVE TO SERVER", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = StatusGreen, fontFamily = FontFamily.Monospace)
                            }
                        }

                        // Copy stream URL
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(JellyfinBorder.copy(alpha = 0.5f))
                                .border(1.dp, JellyfinBorder, RoundedCornerShape(8.dp))
                                .clickable { onToast("Stream URL: $fullStreamUrl") }
                                .padding(horizontal = 10.dp, vertical = 7.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                Icon(Icons.Filled.ContentCopy, null, tint = TacticalMuted, modifier = Modifier.size(13.dp))
                                Text("URL", fontSize = 10.sp, color = TacticalMuted, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
            }
        }
    }
}
