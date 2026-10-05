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
import com.pocketnode.client.data.TrendingTorrentDto
import com.pocketnode.client.ui.theme.*
import java.net.URLEncoder

@Composable
fun TorrentStreamScreen(
    onPlayMovie: (title: String, streamUrl: String, formatBadge: String) -> Unit,
    onToast: (String) -> Unit
) {
    val session by ClientRepository.session.collectAsState()
    val torrents by ClientRepository.trendingTorrents.collectAsState()
    var selectedFilter by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }
    val serverBase = remember(session.serverHost, session.serverPort) {
        "http://${session.serverHost}:${session.serverPort}"
    }
    val filters = listOf("All", "4K", "1080p", "Sci-Fi", "Action", "Animation")

    val isCustomMagnet = remember(searchQuery) {
        searchQuery.trim().startsWith("magnet:?", ignoreCase = true)
    }

    val filtered = remember(torrents, selectedFilter, searchQuery) {
        torrents.filter { t ->
            val matchFilter = if (selectedFilter == "All") true
            else t.quality.contains(selectedFilter, ignoreCase = true) || t.genre.contains(selectedFilter, ignoreCase = true)
            val matchSearch = if (searchQuery.isBlank() || isCustomMagnet) true
            else t.title.contains(searchQuery, ignoreCase = true) || t.genre.contains(searchQuery, ignoreCase = true)
            matchFilter && matchSearch
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(JellyfinBg),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Header
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(listOf(Color(0xFF0E0A18), JellyfinBg))
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
                                imageVector = Icons.Filled.CloudDownload,
                                contentDescription = null,
                                tint = AmberOrange,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                "TORRENT STREAM (STREMIO)",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                color = Color.White,
                                letterSpacing = 1.2.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Sequential swarm streaming & direct magnet playback",
                            fontSize = 12.sp,
                            color = TacticalMuted
                        )
                    }

                    // Refresh button
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(JellyfinCard)
                            .border(1.dp, JellyfinBorder, CircleShape)
                            .clickable {
                                ClientRepository.refreshTrendingTorrents()
                                onToast("Refreshing torrent index...")
                            }
                            .padding(8.dp)
                    ) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = "Refresh",
                            tint = AmberOrange,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Search & Custom Magnet Input Box
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(JellyfinCard)
                    .border(
                        1.dp,
                        if (isCustomMagnet) AmberOrange else JellyfinBorder,
                        RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isCustomMagnet) Icons.Filled.Link else Icons.Outlined.Search,
                    contentDescription = null,
                    tint = if (isCustomMagnet) AmberOrange else TacticalSubtle,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(10.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (searchQuery.isEmpty()) {
                        Text(
                            "Search torrents or paste magnet:? link...",
                            color = TacticalSubtle,
                            fontSize = 13.sp
                        )
                    }
                    androidx.compose.foundation.text.BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontSize = 13.sp,
                            color = TacticalTextWhite
                        ),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(AmberOrange),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (searchQuery.isNotEmpty()) {
                    Icon(
                        Icons.Filled.Close,
                        null,
                        tint = TacticalMuted,
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { searchQuery = "" }
                    )
                }
            }
        }

        // Custom Magnet Stream Card (shown when user pastes a magnet link!)
        if (isCustomMagnet) {
            item {
                val customMagnetUri = searchQuery.trim()
                val extractedTitle = remember(customMagnetUri) {
                    val dnMatch = Regex("dn=([^&]+)").find(customMagnetUri)
                    dnMatch?.groupValues?.getOrNull(1)?.let {
                        try { java.net.URLDecoder.decode(it, "UTF-8") } catch (_: Exception) { it }
                    } ?: "Direct Magnet Stream"
                }
                val customStreamUrl = remember(customMagnetUri, serverBase) {
                    val encoded = try { URLEncoder.encode(customMagnetUri, "UTF-8") } catch (_: Exception) { customMagnetUri }
                    val encodedTitle = try { URLEncoder.encode(extractedTitle, "UTF-8") } catch (_: Exception) { extractedTitle }
                    "$serverBase/stream/torrent?magnet=$encoded&title=$encodedTitle"
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF201306))
                        .border(1.5.dp, AmberOrange, RoundedCornerShape(14.dp))
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Bolt, null, tint = AmberOrange, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "CUSTOM MAGNET LINK DETECTED",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = AmberOrange,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Text(
                            extractedTitle,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            customMagnetUri.take(65) + "...",
                            fontSize = 10.sp,
                            color = TacticalMuted,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SmallActionBtn(
                                icon = Icons.Filled.Download,
                                label = "SAVE TO SERVER",
                                color = StatusGreen,
                                onClick = {
                                    onToast("Queuing magnet download on PocketNode server... ⏳")
                                    ClientRepository.downloadTorrentToServer(customMagnetUri, extractedTitle) { ok, msg ->
                                        onToast(msg)
                                    }
                                }
                            )

                            SmallActionBtn(
                                icon = Icons.Filled.PlayArrow,
                                label = "STREAM (STREMIO)",
                                color = AmberOrange,
                                onClick = {
                                    onToast("Connecting to swarm for magnet...")
                                    onPlayMovie(extractedTitle, customStreamUrl, "DIRECT MAGNET")
                                }
                            )
                        }
                    }
                }
            }
        }

        // Filter chips
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                items(filters) { filter ->
                    val isSelected = filter == selectedFilter
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSelected) AmberOrange.copy(alpha = 0.2f) else JellyfinCard)
                            .border(1.dp, if (isSelected) AmberOrange else JellyfinBorder, RoundedCornerShape(20.dp))
                            .clickable { selectedFilter = filter }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text(
                            filter,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) AmberOrange else TacticalMuted
                        )
                    }
                }
            }
        }

        // Stats row
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(6.dp).background(AmberOrange, CircleShape))
                Text(
                    "${filtered.size} titles trending",
                    fontSize = 12.sp,
                    color = TacticalMuted,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    "MAGNET READY",
                    fontSize = 10.sp,
                    color = StatusGreen,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Torrent cards
        if (filtered.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.CloudOff, null, tint = TacticalSubtle, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("No torrents available", fontSize = 14.sp, color = TacticalMuted)
                        Text("Check server connection", fontSize = 12.sp, color = TacticalSubtle)
                    }
                }
            }
        } else {
            items(filtered, key = { it.id }) { torrent ->
                TorrentCard(
                    torrent = torrent,
                    serverBase = serverBase,
                    onPlayMovie = onPlayMovie,
                    onToast = onToast
                )
            }
        }
    }
}

@Composable
private fun TorrentCard(
    torrent: TrendingTorrentDto,
    serverBase: String,
    onPlayMovie: (String, String, String) -> Unit,
    onToast: (String) -> Unit
) {
    val context = LocalContext.current
    var isExpanded by remember { mutableStateOf(false) }

    // Build the HTTP stream URL for the server's torrent-streaming endpoint
    val torrentStreamUrl = remember(torrent.magnetUri, serverBase) {
        if (torrent.magnetUri.isBlank()) ""
        else {
            val encoded = try { URLEncoder.encode(torrent.magnetUri, "UTF-8") } catch (_: Exception) { torrent.magnetUri }
            val encodedTitle = try { URLEncoder.encode(torrent.title, "UTF-8") } catch (_: Exception) { torrent.title }
            "$serverBase/stream/torrent?magnet=$encoded&title=$encodedTitle"
        }
    }

    val seedColor = when {
        torrent.seeds > 300 -> StatusGreen
        torrent.seeds > 100 -> TacticalYellow
        else -> AmberOrange
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
                if (torrent.posterUrl.isNotBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(torrent.posterUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = torrent.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Movie, null, tint = TacticalSubtle, modifier = Modifier.size(28.dp))
                    }
                }

                // Quality badge
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(AmberOrange.copy(alpha = 0.9f))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(torrent.quality, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color.White, fontFamily = FontFamily.Monospace)
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Rank badge
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (torrent.year.isNotBlank()) {
                        Text(
                            torrent.year,
                            fontSize = 10.sp,
                            color = TacticalSubtle,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    if (torrent.genre.isNotBlank()) {
                        Text("•", fontSize = 10.sp, color = TacticalSubtle)
                        Text(
                            torrent.genre,
                            fontSize = 10.sp,
                            color = TacticalSubtle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Text(
                    torrent.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TacticalTextWhite,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // Rating & seeds
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Star, null, tint = TacticalYellow, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(3.dp))
                        Text(torrent.rating, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TacticalYellow)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(6.dp).background(seedColor, CircleShape))
                        Spacer(Modifier.width(4.dp))
                        Text("${torrent.seeds} seeds", fontSize = 11.sp, color = seedColor, fontFamily = FontFamily.Monospace)
                    }
                    Text(torrent.size, fontSize = 11.sp, color = TacticalMuted, fontFamily = FontFamily.Monospace)
                }

                if (isExpanded) {
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Download independently to home server
                        SmallActionBtn(
                            icon = Icons.Filled.Download,
                            label = "SAVE TO SERVER",
                            color = AmberOrange,
                            onClick = {
                                if (torrent.magnetUri.isBlank()) {
                                    onToast("No magnet URI for ${torrent.title}")
                                } else {
                                    onToast("Adding ${torrent.title} to server downloads... ⏳")
                                    ClientRepository.downloadTorrentToServer(torrent.magnetUri, torrent.title) { ok, msg ->
                                        onToast(msg)
                                    }
                                }
                            }
                        )
                        // Stream directly via server
                        SmallActionBtn(
                            icon = Icons.Filled.PlayArrow,
                            label = "STREAM",
                            color = NeonPurple,
                            onClick = {
                                if (torrentStreamUrl.isBlank()) {
                                    onToast("No magnet URI — cannot stream ${torrent.title}")
                                } else {
                                    onToast("Connecting to swarm... ⏳ ${torrent.title}")
                                    onPlayMovie(torrent.title, torrentStreamUrl, torrent.quality)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SmallActionBtn(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
            Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color, fontFamily = FontFamily.Monospace)
        }
    }
}
