package com.pocketnode.client.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.pocketnode.client.data.ClientRepository
import com.pocketnode.client.data.MediaItemDto
import com.pocketnode.client.data.PosterResolver
import com.pocketnode.client.ui.theme.*

@Composable
fun LibraryTabScreen(
    onPlayMovie: (title: String, streamUrl: String, formatBadge: String) -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val movies by ClientRepository.libraryMovies.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("4K HDR") }

    val filterChips = listOf("4K HDR", "RECENTLY ADDED", "FAMILY FRIENDLY", "UNWATCHED")

    // Automatically discover any downloaded videos on device storage
    LaunchedEffect(Unit) {
        ClientRepository.syncLocalDownloadedVideos(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TacticalBg)
            .verticalScroll(scrollState)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ── HEADER: LIBRARY & STORAGE VAULT ──────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "LIBRARY & STORAGE VAULT",
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                color = TacticalTextWhite
            )
            Text(
                "NODE_01",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = TacticalRed
            )
        }

        // ── STORAGE BAR BREAKDOWN ─────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(TacticalPanel)
                .border(1.dp, TacticalBorder, RoundedCornerShape(6.dp))
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("AVAILABLE SPACE", fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("16.4 TB", fontSize = 16.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, color = TacticalTextWhite)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Free of 24.0 TB", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                    }
                }
                Box(
                    modifier = Modifier
                        .background(Color(0xFF1E2838), RoundedCornerShape(3.dp))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text("68.3% FREE", fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = ElectricBlue)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Multi-segment storage bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
            ) {
                Box(modifier = Modifier.weight(0.48f).fillMaxHeight().background(TacticalRed))
                Box(modifier = Modifier.weight(0.17f).fillMaxHeight().background(Color(0xFF58A6FF)))
                Box(modifier = Modifier.weight(0.08f).fillMaxHeight().background(GoldYellow))
                Box(modifier = Modifier.weight(0.27f).fillMaxHeight().background(Color(0xFF2C3240)))
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(5.dp).background(TacticalRed, RoundedCornerShape(1.dp)))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Movies (4.8T)", fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(5.dp).background(Color(0xFF58A6FF), RoundedCornerShape(1.dp)))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Series (1.7T)", fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(5.dp).background(GoldYellow, RoundedCornerShape(1.dp)))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Audio (0.8T)", fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(5.dp).background(Color(0xFF2C3240), RoundedCornerShape(1.dp)))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Archive", fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                }
            }
        }

        // ── ROOT DIRECTORIES GRID ─────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("ROOT DIRECTORIES", fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalMuted)
            Text("ALL MOUNTS VALID", fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalRed)
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DirectoryCard(
                    modifier = Modifier.weight(1f),
                    index = "01",
                    icon = "🎞️",
                    name = "/movies",
                    itemCount = "842 items",
                    codecTag = "4K HDR & REMUX"
                )
                DirectoryCard(
                    modifier = Modifier.weight(1f),
                    index = "02",
                    icon = "📺",
                    name = "/series",
                    itemCount = "64 shows",
                    codecTag = "1,280 EPISODES"
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DirectoryCard(
                    modifier = Modifier.weight(1f),
                    index = "03",
                    icon = "🎵",
                    name = "/music",
                    itemCount = "3,800 albums",
                    codecTag = "LOSSLESS FLAC"
                )
                DirectoryCard(
                    modifier = Modifier.weight(1f),
                    index = "04",
                    icon = "📁",
                    name = "/images & other",
                    itemCount = "14k files",
                    codecTag = "PERSONAL VAULT"
                )
            }
        }

        // ── MULTI-TARGET ROUTING READY ALERT ──────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF2A1015))
                .border(1.dp, TacticalRed.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(TacticalRedDark, RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Devices, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text("MULTI-TARGET ROUTING READY", fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalRed)
                Text(
                    "Download directly to this mobile client or push media stream instantly to Home TV / iPad target nodes.",
                    fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted
                )
            }
        }

        // ── SEARCH & FILTER CHIPS ─────────────────────────────────────────────
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search movies, shows, or storage paths...", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TacticalMuted, modifier = Modifier.size(16.dp)) },
            trailingIcon = { Icon(Icons.Default.Tune, contentDescription = "Filter", tint = TacticalText, modifier = Modifier.size(16.dp)) },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = TacticalCanvas,
                unfocusedContainerColor = TacticalCanvas,
                focusedBorderColor = TacticalRed,
                unfocusedBorderColor = TacticalBorder,
                focusedTextColor = TacticalTextWhite,
                unfocusedTextColor = TacticalText
            ),
            shape = RoundedCornerShape(4.dp),
            singleLine = true
        )

        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(filterChips) { chip ->
                val isSelected = selectedFilter == chip
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isSelected) Color(0xFF1E2838) else TacticalCanvas)
                        .border(1.dp, if (isSelected) ElectricBlue else TacticalBorder, RoundedCornerShape(4.dp))
                        .clickable { selectedFilter = chip }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        chip,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = if (isSelected) ElectricBlue else TacticalMuted
                    )
                }
            }
        }

        // ── ACTIVE INDEX LISTING ──────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("ACTIVE INDEX: /MOVIES", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalTextWhite)
            Text("SORT: DATE ADDED (DESC)", fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            movies.forEach { movie ->
                MovieLibraryCard(
                    movie = movie,
                    onPlay = {
                        val targetUrl = movie.streamUrl.ifBlank { movie.title }
                        onPlayMovie(movie.title, targetUrl, movie.formatBadge)
                    }
                )
            }
        }

        // ── BOTTOM STATUS BAR ─────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E2430), RoundedCornerShape(4.dp))
                .border(0.5.dp, Color(0xFF2C3648), RoundedCornerShape(4.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🖧", fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("SMB://HOME-VAULT.LAN:445/EXPORTS", fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalText)
                }
                Text("PING 3ms", fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalRed)
            }
        }
    }
}

@Composable
fun DirectoryCard(
    modifier: Modifier = Modifier,
    index: String,
    icon: String,
    name: String,
    itemCount: String,
    codecTag: String
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(TacticalPanel)
            .border(1.dp, TacticalBorder, RoundedCornerShape(6.dp))
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(icon, fontSize = 16.sp)
            Text(index, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalRed)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(name, fontSize = 12.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, color = TacticalTextWhite)
        Text(itemCount, fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
        Text(codecTag, fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalText)
    }
}

@Composable
fun MovieLibraryCard(
    movie: MediaItemDto,
    onPlay: () -> Unit
) {
    val context = LocalContext.current
    val posterUrl = movie.posterUrl.ifBlank { PosterResolver.getPoster(movie.title) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(TacticalPanel)
            .border(1.dp, TacticalBorder, RoundedCornerShape(6.dp))
            .padding(10.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Poster Box
            Box(
                modifier = Modifier
                    .size(64.dp, 94.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF1B202C))
                    .border(1.dp, Color(0xFF2B3346), RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (posterUrl.isNotBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(posterUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = movie.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text("🎬", fontSize = 24.sp)
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(movie.formatBadge, fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalRed)
                    Text(movie.sizeFormatted, fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text("${movie.title} (${movie.year})", fontSize = 13.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, color = TacticalTextWhite)
                Text(movie.formatTag, fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(5.dp).background(if (movie.progressPercent > 0) Color(0xFF58A6FF) else TacticalRed, CircleShape))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(movie.progressStr, fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = TacticalMuted)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Actions Row 1
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onPlay,
                modifier = Modifier.weight(1f).height(34.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TacticalRedDark),
                shape = RoundedCornerShape(4.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (movie.progressPercent > 0) "RESUME" else "STREAM", fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = Color.White)
            }
            Button(
                onClick = { /* Save to device */ },
                modifier = Modifier.weight(1f).height(34.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E2838)),
                shape = RoundedCornerShape(4.dp)
            ) {
                Icon(Icons.Default.Download, contentDescription = null, tint = ElectricBlue, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("SAVE DEVICE", fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = ElectricBlue)
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Actions Row 2
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { /* Push to TV */ }
            ) {
                Icon(Icons.Outlined.Cast, contentDescription = null, tint = TacticalMuted, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("PUSH TO LIVING ROOM TV", fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalText)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { /* Party */ }
                ) {
                    Icon(Icons.Default.Group, contentDescription = null, tint = TacticalMuted, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("PARTY", fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TacticalText)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.Default.MoreVert, contentDescription = "More", tint = TacticalMuted, modifier = Modifier.size(16.dp).clickable { })
            }
        }
    }
}
