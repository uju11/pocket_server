package com.pocketnode.client.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.pocketnode.client.data.ClientRepository
import com.pocketnode.client.data.PosterResolver
import com.pocketnode.client.data.TelegramMediaDto
import com.pocketnode.client.data.TrendingTorrentDto
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import com.pocketnode.client.data.UnifiedSearchResult
import com.pocketnode.client.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun WatchTabScreen(
    onOpenScheduleModal: () -> Unit,
    onPlayMovie: (title: String, streamUrl: String, formatBadge: String) -> Unit
) {
    val scrollState = rememberScrollState()
    val session by ClientRepository.session.collectAsState()
    val continueWatching by ClientRepository.continueWatching.collectAsState()
    val activeParty by ClientRepository.activeParty.collectAsState()
    val libraryMovies by ClientRepository.libraryMovies.collectAsState()
    val telegramMovies by ClientRepository.telegramMovies.collectAsState()
    val trendingTorrents by ClientRepository.trendingTorrents.collectAsState()

    val context = LocalContext.current
    fun showToast(msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All Media") }
    val filterChips = listOf("All Media", "Movies", "TV Shows", "Pixar & Anime", "4K Remux")

    var searchResults by remember { mutableStateOf<List<UnifiedSearchResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var selectedResultForAction by remember { mutableStateOf<UnifiedSearchResult?>(null) }

    LaunchedEffect(searchQuery) {
        if (searchQuery.isBlank()) {
            searchResults = emptyList()
            isSearching = false
        } else {
            isSearching = true
            delay(280)
            searchResults = ClientRepository.searchUnified(searchQuery)
            isSearching = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(JellyfinBg)
            .verticalScroll(scrollState)
            .padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // ── 01. SEARCH & CAST BAR ─────────────────────────────────────────────
        Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF121424))
                    .border(1.dp, Color(0xFF222744), RoundedCornerShape(24.dp))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = "Search",
                    tint = Color(0xFF64748B),
                    modifier = Modifier.size(20.dp)
                )

                Spacer(modifier = Modifier.width(10.dp))

                Box(modifier = Modifier.weight(1f)) {
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = "Search 5,800+ titles, cast, tags...",
                            color = Color(0xFF64748B),
                            fontSize = 13.sp
                        )
                    }
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = Color.White,
                            fontSize = 13.sp
                        ),
                        cursorBrush = SolidColor(NeonPurpleLight),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (searchQuery.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Clear",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { searchQuery = "" }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }

                Spacer(modifier = Modifier.width(8.dp))

                Icon(
                    imageVector = Icons.Outlined.Mic,
                    contentDescription = "Voice Search",
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier
                        .size(20.dp)
                        .clickable { /* Voice search trigger */ }
                )

                Spacer(modifier = Modifier.width(12.dp))

                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1B1F38))
                        .clickable { /* Cast / Airplay selector */ },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Cast,
                        contentDescription = "Cast",
                        tint = Color(0xFF60A5FA),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        if (searchQuery.isNotBlank()) {
            UnifiedSearchResultsSection(
                query = searchQuery,
                results = searchResults,
                isSearching = isSearching,
                onSelectResult = { selectedResultForAction = it },
                onStreamDirect = { res ->
                    onPlayMovie(res.title, res.streamUrl, "${res.source} • ${res.quality}")
                },
                onDownloadToServer = { res ->
                    if (res.source == "TORRENT") {
                        showToast("Queuing torrent to server... ⏳")
                        ClientRepository.downloadTorrentToServer(res.magnetUri, res.title) { ok, msg ->
                            showToast(msg)
                        }
                    } else if (res.source == "TELEGRAM") {
                        showToast("Requesting Telegram file download on server... ⏳")
                        ClientRepository.downloadTelegramToServer(res.fileId, res.title) { ok, msg ->
                            showToast(msg)
                        }
                    } else {
                        showToast("File is already in server sandbox! 📁")
                    }
                }
            )
        } else {

        // ── 02. CATEGORY FILTER CHIPS ─────────────────────────────────────────
        LazyRow(
            contentPadding = PaddingValues(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filterChips) { filter ->
                val isSelected = selectedFilter == filter
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .then(
                            if (isSelected) {
                                Modifier.background(
                                    Brush.horizontalGradient(
                                        listOf(Color(0xFFB845FF), Color(0xFF8B2CF5))
                                    )
                                )
                            } else {
                                Modifier
                                    .background(Color(0xFF121526))
                                    .border(1.dp, Color(0xFF222744), RoundedCornerShape(20.dp))
                            }
                        )
                        .clickable { selectedFilter = filter }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = filter,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) Color.White else Color(0xFF94A3B8)
                    )
                }
            }
        }

        // ── 03. SERVER STATUS & HARDWARE TRANSCODE BANNER ─────────────────────
        Box(modifier = Modifier.padding(horizontal = 14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF101322))
                    .border(1.dp, Color(0xFF1E233E), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(NeonCyan, CircleShape)
                    )
                    Text(
                        text = "Home-Vault-01 • Jellyfin Cluster Active (2 Nodes)",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFE2E8F0)
                    )
                }

                Icon(
                    imageVector = Icons.Outlined.Sync,
                    contentDescription = "Sync",
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier
                        .size(17.dp)
                        .clickable { /* Refresh telemetry */ }
                )
            }
        }

        // ── 04. CINEMATIC FEATURED HERO CARD (RATATOUILLE) ────────────────────
        Box(modifier = Modifier.padding(horizontal = 14.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.dp, Color(0xFF222744), RoundedCornerShape(20.dp))
            ) {
                // Paris Twilight Cinematic Canvas with Eiffel Tower silhouette
                Canvas(modifier = Modifier.fillMaxSize()) {
                    // Sky gradient: twilight purple to deep obsidian
                    drawRect(
                        brush = Brush.verticalGradient(
                            listOf(
                                Color(0xFF1C1838), // twilight purple top
                                Color(0xFF2D1E40), // sunset rose
                                Color(0xFF1F1B38), // evening haze
                                Color(0xFF0C0E1A)  // deep obsidian bottom
                            )
                        )
                    )

                    // Warm sunset glow in center
                    drawCircle(
                        color = Color(0xFFFFB366).copy(alpha = 0.25f),
                        radius = size.width * 0.45f,
                        center = Offset(size.width * 0.55f, size.height * 0.42f)
                    )

                    // Eiffel Tower stylized geometric silhouette
                    val cx = size.width * 0.65f
                    val topY = size.height * 0.08f
                    val spireBottomY = size.height * 0.25f
                    val middleDeckY = size.height * 0.38f
                    val groundY = size.height * 0.60f

                    // Spire
                    drawLine(
                        color = Color(0xFFFFD59E).copy(alpha = 0.85f),
                        start = Offset(cx, topY),
                        end = Offset(cx, spireBottomY),
                        strokeWidth = 3f
                    )
                    // Tower Legs / A-Frame
                    drawLine(
                        color = Color(0xFFFFC078).copy(alpha = 0.75f),
                        start = Offset(cx - 15f, spireBottomY),
                        end = Offset(cx - 45f, groundY),
                        strokeWidth = 4f
                    )
                    drawLine(
                        color = Color(0xFFFFC078).copy(alpha = 0.75f),
                        start = Offset(cx + 15f, spireBottomY),
                        end = Offset(cx + 45f, groundY),
                        strokeWidth = 4f
                    )
                    // Crossbeams
                    drawLine(
                        color = Color(0xFFFFB366).copy(alpha = 0.9f),
                        start = Offset(cx - 25f, middleDeckY),
                        end = Offset(cx + 25f, middleDeckY),
                        strokeWidth = 4f
                    )

                    // Bottom vignette overlay for ultra readable typography
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0xFF090A14).copy(alpha = 0.5f),
                                Color(0xFF090A14).copy(alpha = 0.95f),
                                Color(0xFF090A14)
                            ),
                            startY = size.height * 0.35f,
                            endY = size.height
                        )
                    )
                }

                // Dynamic Hero Movie: prioritizes live continue watching / last played media, then first library movie
                val heroMovie = continueWatching ?: libraryMovies.firstOrNull()
                val heroTitle = heroMovie?.title ?: "Select Media"
                val heroBackdrop = heroMovie?.posterUrl?.ifBlank { null } ?: PosterResolver.getBackdrop(heroTitle)

                if (heroBackdrop.isNotBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                            .data(heroBackdrop)
                            .crossfade(true)
                            .build(),
                        contentDescription = heroTitle,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color(0xFF090A14).copy(alpha = 0.4f),
                                        Color(0xFF090A14).copy(alpha = 0.85f),
                                        Color(0xFF090A14)
                                    ),
                                    startY = 120f
                                )
                            )
                    )
                }

                // Hero Content Overlay
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Bottom
                ) {
                    // Metadata Badges Row
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HeroBadge(text = if (heroMovie != null && heroMovie.progressPercent > 0) "IN PROGRESS" else "READY")
                        HeroBadge(text = heroMovie?.durationStr ?: "Direct Play")

                        // Score Badge with Star
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF2A173B))
                                .padding(horizontal = 6.dp, vertical = 2.5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text("★", fontSize = 10.sp, color = Color(0xFFE879F9))
                            Text(if (heroMovie != null && heroMovie.progressPercent > 0) "${(heroMovie.progressPercent * 100).toInt()}%" else "1080P", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE879F9))
                        }

                        // 4K UHD Badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF0E253F))
                                .border(0.8.dp, Color(0xFF0284C7), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(heroMovie?.formatBadge ?: "DIRECT STREAM", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                        }

                        HeroBadge(text = "Dolby Atmos")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Title & Year
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = heroTitle,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (!heroMovie?.year.isNullOrBlank()) {
                            Text(
                                text = heroMovie?.year ?: "",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Normal,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Synopsis / Progress
                    Text(
                        text = if (heroMovie != null && heroMovie.progressPercent > 0) heroMovie.progressStr else (heroMovie?.formatTag ?: "Lossless direct play stream from your home media vault."),
                        fontSize = 11.5.sp,
                        color = Color(0xFF94A3B8),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Direct Play Specs
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("⚡", fontSize = 12.sp, color = NeonCyan)
                        Text(
                            text = if (heroMovie != null && heroMovie.progressPercent > 0) "Resuming from ${heroMovie.progressStr.substringBefore(" •")} • Auto-Sync" else "Direct Play • Home-Vault High Bitrate Stream",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF38BDF8),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Action Buttons Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val isResumable = heroMovie != null && heroMovie.progressPercent > 0
                        val btnLabel = if (isResumable) "Resume (${(heroMovie!!.progressPercent * 100).toInt()}%)" else "Stream Now"
                        val streamTarget = heroMovie?.streamUrl?.ifBlank { "http://192.168.1.100:8080/video/${heroMovie.title}" } ?: ""

                        // Main Resume Gradient Pill Button
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color(0xFFC04CFD), Color(0xFF9333EA))
                                    )
                                )
                                .clickable {
                                    if (heroMovie != null) {
                                        onPlayMovie(heroMovie.title, streamTarget, heroMovie.formatBadge)
                                    } else {
                                        showToast("No media available in library yet.")
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = btnLabel,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        // Bookmark Button
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF16192E))
                                .border(1.dp, Color(0xFF262C4E), RoundedCornerShape(12.dp))
                                .clickable { /* Add to bookmarks */ },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.BookmarkBorder,
                                contentDescription = "Bookmark",
                                tint = Color(0xFFCBD5E1),
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        // Info Button
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF16192E))
                                .border(1.dp, Color(0xFF262C4E), RoundedCornerShape(12.dp))
                                .clickable { onOpenScheduleModal() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = "Media Info",
                                tint = Color(0xFFCBD5E1),
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }
            }
        }

        // ── 05. CONTINUE WATCHING SECTION ─────────────────────────────────────
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PlayCircle,
                        contentDescription = null,
                        tint = NeonPurpleLight,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Continue Watching",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Text(
                    text = "See All >",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = NeonPurpleLight,
                    modifier = Modifier.clickable { /* Open all in progress */ }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyRow(
                contentPadding = PaddingValues(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Card 1: Ratatouille
                item {
                    ContinueWatchingCard(
                        title = "Ratatouille",
                        subtitle = "Direct Play • 1080p",
                        badge = "Direct Play",
                        timeLeft = "42m left",
                        progress = 0.65f,
                        gradientColors = listOf(Color(0xFF221638), Color(0xFF14172B)),
                        thumbnailUrl = PosterResolver.getBackdrop("Ratatouille"),
                        onClick = { onPlayMovie("Ratatouille", "ratatouille", "Direct Play • 1080p") }
                    )
                }

                // Card 2: Severance
                item {
                    ContinueWatchingCard(
                        title = "Severance",
                        subtitle = "S2:E4 \"Woe\" • Dolby...",
                        badge = null,
                        timeLeft = "9m left",
                        progress = 0.85f,
                        gradientColors = listOf(Color(0xFF1A2633), Color(0xFF111E26)),
                        thumbnailUrl = PosterResolver.getBackdrop("Severance"),
                        onClick = { onPlayMovie("Severance", "severance", "Dolby Vision • 4K") }
                    )
                }

                // Card 3: Interstellar
                item {
                    ContinueWatchingCard(
                        title = "Interstellar",
                        subtitle = "IMAX Edition • 4K HDR",
                        badge = "4K IMAX",
                        timeLeft = "1h 12m left",
                        progress = 0.44f,
                        gradientColors = listOf(Color(0xFF131E33), Color(0xFF0C1322)),
                        thumbnailUrl = PosterResolver.getBackdrop("Interstellar"),
                        onClick = { onPlayMovie("Interstellar", "interstellar", "4K IMAX HDR") }
                    )
                }
            }
        }

        // ── 06. YOUR LIBRARIES (2x2 GRID) ─────────────────────────────────────
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Folder,
                        contentDescription = null,
                        tint = NeonPurpleLight,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Your Libraries",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Text(
                    text = "HOME VAULT",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF64748B),
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 2x2 Grid of Library Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LibraryCategoryCard(
                    modifier = Modifier.weight(1f),
                    title = "Movies",
                    subtitle = "1,248 titles • ...",
                    icon = Icons.Outlined.LocalMovies,
                    iconBg = LibMoviesBg,
                    iconTint = LibMoviesIcon,
                    onClick = { /* Open Movies */ }
                )
                LibraryCategoryCard(
                    modifier = Modifier.weight(1f),
                    title = "TV Series",
                    subtitle = "184 series • 3...",
                    icon = Icons.Outlined.Tv,
                    iconBg = LibTvBg,
                    iconTint = LibTvIcon,
                    onClick = { /* Open TV Series */ }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LibraryCategoryCard(
                    modifier = Modifier.weight(1f),
                    title = "Pixar & Ani...",
                    subtitle = "64 titles • Vault",
                    icon = Icons.Outlined.Animation,
                    iconBg = LibAnimeBg,
                    iconTint = LibAnimeIcon,
                    onClick = { /* Open Animation */ }
                )
                LibraryCategoryCard(
                    modifier = Modifier.weight(1f),
                    title = "Hi-Fi Music",
                    subtitle = "4,520 lossles...",
                    icon = Icons.Outlined.Album,
                    iconBg = LibMusicBg,
                    iconTint = LibMusicIcon,
                    onClick = { /* Open Hi-Fi Music */ }
                )
            }
        }

        // ── 07. RECENTLY ADDED SECTION ────────────────────────────────────────
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = NeonPurpleLight,
                        modifier = Modifier.size(19.dp)
                    )
                    Text(
                        text = "Recently Added",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Text(
                    text = "Explore >",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = NeonPurpleLight,
                    modifier = Modifier.clickable { /* Explore full catalog */ }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyRow(
                contentPadding = PaddingValues(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Poster 1: Oppenheimer
                item {
                    RecentlyAddedPosterCard(
                        title = "Oppenheimer",
                        subtitle = "2023 • Biography",
                        badge = "4K HDR",
                        rating = "8.9",
                        artGradient = listOf(Color(0xFF4A1A05), Color(0xFF1B0E05), Color(0xFF0A0706)),
                        accentGlow = Color(0xFFFF5722),
                        posterUrl = PosterResolver.getPoster("Oppenheimer"),
                        onClick = { onPlayMovie("Oppenheimer", "oppenheimer", "4K HDR") }
                    )
                }

                // Poster 2: Spider-Verse
                item {
                    RecentlyAddedPosterCard(
                        title = "Spider-Verse",
                        subtitle = "2023 • Animation",
                        badge = "4K DV",
                        rating = "8.7",
                        artGradient = listOf(Color(0xFF26103A), Color(0xFF140D26), Color(0xFF090814)),
                        accentGlow = Color(0xFFC04CFD),
                        posterUrl = PosterResolver.getPoster("Spider-Verse"),
                        onClick = { onPlayMovie("Spider-Verse", "spider_verse", "4K DV") }
                    )
                }

                // Poster 3: Interstellar
                item {
                    RecentlyAddedPosterCard(
                        title = "Interstellar",
                        subtitle = "2014 • Sci-Fi",
                        badge = "IMAX",
                        rating = "8.7",
                        artGradient = listOf(Color(0xFF0A2239), Color(0xFF081829), Color(0xFF060B12)),
                        accentGlow = Color(0xFF38BDF8),
                        posterUrl = PosterResolver.getPoster("Interstellar"),
                        onClick = { onPlayMovie("Interstellar", "interstellar", "IMAX 1080p") }
                    )
                }

                // Dynamic posters from libraryMovies
                items(libraryMovies) { movie ->
                    RecentlyAddedPosterCard(
                        title = movie.title,
                        subtitle = "${movie.year} • ${movie.category}",
                        badge = movie.formatBadge.take(7),
                        rating = "8.4",
                        artGradient = listOf(Color(0xFF191D33), Color(0xFF111424), Color(0xFF0A0C17)),
                        accentGlow = Color(0xFF8B2CF5),
                        posterUrl = movie.posterUrl.ifBlank { PosterResolver.getPoster(movie.title) },
                        onClick = { onPlayMovie(movie.title, movie.streamUrl.ifBlank { movie.title }, movie.formatBadge) }
                    )
                }
            }
        }

        // ── 08. TRENDING & RECOMMENDATIONS (TORRENTS) ─────────────────────────
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Whatshot,
                        contentDescription = null,
                        tint = Color(0xFFFF6D00),
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "Trending & Recommendations",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Sequential BitTorrent Stream • Zero Waiting",
                            fontSize = 10.5.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF2B1404))
                        .border(1.dp, Color(0xFFFF6D00).copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "P2P STREAM",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF9100)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyRow(
                contentPadding = PaddingValues(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(trendingTorrents) { item ->
                    TrendingTorrentCard(
                        item = item,
                        onClick = {
                            val baseUrl = ClientRepository.serverBaseUrl
                            val streamUrl = "$baseUrl/stream/torrent?title=${item.title}&magnet=${item.magnetUri}"
                            onPlayMovie(item.title, streamUrl, "TORRENT • ${item.quality}")
                        }
                    )
                }
            }
        }

        // ── 09. MOVIES & FILES IN TELEGRAM (SAVED & BOT-FORWARDED) ───────────
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CloudDownload,
                        contentDescription = null,
                        tint = Color(0xFF2AABEE),
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "Movies & Files in Telegram",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Saved & Bot Forwarded • Direct Cloud Stream",
                            fontSize = 10.5.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0C243B))
                        .border(1.dp, Color(0xFF2AABEE).copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${telegramMovies.size} FILES IN CLOUD",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF38BDF8)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyRow(
                contentPadding = PaddingValues(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(telegramMovies) { item ->
                    TelegramMediaCard(
                        item = item,
                        onClick = {
                            val baseUrl = ClientRepository.serverBaseUrl
                            val streamUrl = baseUrl + item.streamUrl
                            onPlayMovie(item.cleanTitle, streamUrl, "TELEGRAM • ${item.quality}")
                        }
                    )
                }
            }
        }
        } // end of else (normal home layout)

        selectedResultForAction?.let { result ->
            UnifiedResultActionModal(
                result = result,
                onDismiss = { selectedResultForAction = null },
                onStream = {
                    selectedResultForAction = null
                    onPlayMovie(result.title, result.streamUrl, "${result.source} • ${result.quality}")
                },
                onSaveToServer = {
                    selectedResultForAction = null
                    if (result.source == "TORRENT") {
                        showToast("Queuing torrent to server download queue... ⏳")
                        ClientRepository.downloadTorrentToServer(result.magnetUri, result.title) { ok, msg ->
                            showToast(msg)
                        }
                    } else if (result.source == "TELEGRAM") {
                        showToast("Queuing Telegram download on server... ⏳")
                        ClientRepository.downloadTelegramToServer(result.fileId, result.title) { ok, msg ->
                            showToast(msg)
                        }
                    } else {
                        showToast("Already saved on server storage! 📁")
                    }
                }
            )
        }
    }
}

// ── REUSABLE DESIGN COMPONENTS FOR JELLYFIN STREAMING UI ─────────────────────

@Composable
fun HeroBadge(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF1A1D2E))
            .padding(horizontal = 6.dp, vertical = 2.5.dp)
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFFCBD5E1)
        )
    }
}

@Composable
fun ContinueWatchingCard(
    title: String,
    subtitle: String,
    badge: String?,
    timeLeft: String,
    progress: Float,
    gradientColors: List<Color>,
    onClick: () -> Unit,
    thumbnailUrl: String = ""
) {
    Column(
        modifier = Modifier
            .width(215.dp)
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(125.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.verticalGradient(gradientColors))
                .border(1.dp, Color(0xFF222744), RoundedCornerShape(14.dp))
        ) {
            // Stylized backdrop content (always drawn as base layer)
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawLine(
                    color = Color.White.copy(alpha = 0.04f),
                    start = Offset(0f, 0f),
                    end = Offset(size.width * 0.45f, size.height * 0.5f),
                    strokeWidth = 2f
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.04f),
                    start = Offset(size.width, 0f),
                    end = Offset(size.width * 0.55f, size.height * 0.5f),
                    strokeWidth = 2f
                )
            }

            // Overlay real thumbnail when URL is available
            if (thumbnailUrl.isNotBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(thumbnailUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = title,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                // Re-apply bottom gradient scrim so badges remain readable
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f)),
                                startY = 40f
                            )
                        )
                )
            }

            // Top Right Badge (Direct Play)
            if (badge != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF0E253F).copy(alpha = 0.9f))
                        .border(0.6.dp, Color(0xFF0284C7), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(badge, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                }
            }

            // Time left overlay bottom left
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Schedule,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(11.dp)
                )
                Text(
                    text = timeLeft,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
            }

            // Bottom cyan progress bar
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Color(0xFF1E2338))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .fillMaxHeight()
                        .background(NeonCyan)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = subtitle,
            fontSize = 11.sp,
            color = Color(0xFF94A3B8),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun LibraryCategoryCard(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF121424))
            .border(1.dp, Color(0xFF202540), RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconBg)
                .border(0.8.dp, iconTint.copy(alpha = 0.35f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 10.5.sp,
                color = Color(0xFF64748B),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun RecentlyAddedPosterCard(
    title: String,
    subtitle: String,
    badge: String,
    rating: String,
    artGradient: List<Color>,
    accentGlow: Color,
    onClick: () -> Unit,
    posterUrl: String = ""
) {
    Column(
        modifier = Modifier
            .width(122.dp)
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Brush.verticalGradient(artGradient))
                .border(1.dp, Color(0xFF222744), RoundedCornerShape(12.dp))
        ) {
            // Atmospheric art glow (base layer)
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = accentGlow.copy(alpha = 0.28f),
                    radius = size.width * 0.7f,
                    center = Offset(size.width * 0.5f, size.height * 0.35f)
                )
            }

            // Real poster image overlay when URL provided
            if (posterUrl.isNotBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(posterUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = title,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                // Scrim so badges remain readable over the image
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
                                startY = 80f
                            )
                        )
                )
            }

            // Top Right Badge (4K HDR / 4K DV / IMAX)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .border(0.5.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(3.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text(badge, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }

            // Bottom Rating Pill Overlay
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text("★", fontSize = 10.sp, color = Color(0xFFE879F9))
                Text(
                    text = rating,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = title,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = subtitle,
            fontSize = 10.5.sp,
            color = Color(0xFF64748B),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun TrendingTorrentCard(
    item: TrendingTorrentDto,
    onClick: () -> Unit
) {
    val gradientColors = when {
        item.title.contains("Dune", ignoreCase = true) -> listOf(Color(0xFF421E06), Color(0xFF1D0D03), Color(0xFF0A0706))
        item.title.contains("Oppenheimer", ignoreCase = true) -> listOf(Color(0xFF4A1408), Color(0xFF1E0803), Color(0xFF090606))
        item.title.contains("Spider", ignoreCase = true) -> listOf(Color(0xFF320E4A), Color(0xFF160621), Color(0xFF09040D))
        item.title.contains("Interstellar", ignoreCase = true) -> listOf(Color(0xFF0B243B), Color(0xFF071421), Color(0xFF040A10))
        item.title.contains("Deadpool", ignoreCase = true) -> listOf(Color(0xFF3C0E16), Color(0xFF180609), Color(0xFF080406))
        else -> listOf(Color(0xFF241638), Color(0xFF120B1C), Color(0xFF09060E))
    }

    Column(
        modifier = Modifier
            .width(136.dp)
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Brush.verticalGradient(gradientColors))
                .border(1.dp, Color(0xFF262035), RoundedCornerShape(12.dp))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = Color(0xFFFF6D00).copy(alpha = 0.22f),
                    radius = size.width * 0.65f,
                    center = Offset(size.width * 0.5f, size.height * 0.35f)
                )
            }

            // Real poster image overlay
            val torrentPoster = PosterResolver.getPoster(item.title, item.posterUrl)
            if (torrentPoster.isNotBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                        .data(torrentPoster)
                        .crossfade(true)
                        .build(),
                    contentDescription = item.title,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f)),
                                startY = 80f
                            )
                        )
                )
            }

            // Top Left Badge: TORRENT
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF2B1404).copy(alpha = 0.85f))
                    .border(0.6.dp, Color(0xFFFF9100).copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text("TORRENT", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF9100))
            }

            // Top Right Badge: Seeds count
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .border(0.5.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text("⚡ ${item.seeds}s", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
            }

            // Bottom Left: Rating Pill
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text("★", fontSize = 10.sp, color = Color(0xFFFFD700))
                Text(
                    text = item.rating,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            // Bottom Right: Size Pill
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.Black.copy(alpha = 0.8f))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(item.size, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE2E8F0))
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = item.title,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "${item.year} • ${item.genre}",
            fontSize = 10.5.sp,
            color = Color(0xFF94A3B8),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = item.quality,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFFFF9100)
        )
    }
}

@Composable
fun TelegramMediaCard(
    item: TelegramMediaDto,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(220.dp)
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF0C243B), Color(0xFF08192A), Color(0xFF040A12))
                    )
                )
                .border(1.dp, Color(0xFF163E63), RoundedCornerShape(14.dp))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = Color(0xFF0284C7).copy(alpha = 0.28f),
                    radius = size.width * 0.45f,
                    center = Offset(size.width * 0.5f, size.height * 0.5f)
                )
                // Faint horizon lines
                drawLine(
                    color = Color(0xFF38BDF8).copy(alpha = 0.08f),
                    start = Offset(0f, size.height * 0.7f),
                    end = Offset(size.width, size.height * 0.7f),
                    strokeWidth = 1f
                )
            }

            // Real backdrop thumbnail overlay
            val tgBackdrop = PosterResolver.getBackdrop(item.cleanTitle, item.posterUrl)
            if (tgBackdrop.isNotBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                        .data(tgBackdrop)
                        .crossfade(true)
                        .build(),
                    contentDescription = item.cleanTitle,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                                startY = 30f
                            )
                        )
                )
            }

            // Top Left: Quality / Format Pill
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF081F33).copy(alpha = 0.9f))
                    .border(0.6.dp, Color(0xFF0284C7), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = item.quality,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF38BDF8)
                )
            }

            // Top Right: ✈️ TELEGRAM STREAM Badge
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF0F3252).copy(alpha = 0.95f))
                    .border(0.8.dp, Color(0xFF2AABEE), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "✈️ TELEGRAM",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF7DD3FC)
                )
            }

            // Bottom Left: File Size Pill
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.CloudDone,
                    contentDescription = null,
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = item.sizeFormatted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            // Bottom Right: Instant Play Circle
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF0284C7), Color(0xFF7C3AED))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Stream",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = item.cleanTitle,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "${if (item.year.isNotBlank()) "${item.year} • " else ""}${item.fileName.takeLast(24)}",
            fontSize = 10.5.sp,
            color = Color(0xFF94A3B8),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(4.dp))
        // Stream Action Pill Button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xFF0284C7), Color(0xFF7C3AED))
                    )
                )
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = "Stream Cloud (No Wait)",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

// ── UNIFIED SEARCH RESULTS & ACTIONS (TORRENT / TELEGRAM / SANDBOX) ──────────

@Composable
private fun UnifiedSearchResultsSection(
    query: String,
    results: List<UnifiedSearchResult>,
    isSearching: Boolean,
    onSelectResult: (UnifiedSearchResult) -> Unit,
    onStreamDirect: (UnifiedSearchResult) -> Unit,
    onDownloadToServer: (UnifiedSearchResult) -> Unit
) {
    var sourceFilter by remember { mutableStateOf("ALL") }

    val filtered = remember(results, sourceFilter) {
        if (sourceFilter == "ALL") results
        else results.filter { it.source.equals(sourceFilter, ignoreCase = true) }
    }

    val torrentCount = results.count { it.source == "TORRENT" }
    val telegramCount = results.count { it.source == "TELEGRAM" }
    val sandboxCount = results.count { it.source == "SANDBOX" }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Status & Source Filter Tabs
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isSearching) "SEARCHING ALL SOURCES..." else "${results.size} MATCHES FOUND",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = if (isSearching) NeonCyan else Color(0xFF94A3B8)
            )

            if (isSearching) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    color = NeonCyan,
                    strokeWidth = 2.dp
                )
            }
        }

        // Filter Pills
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val tabs = listOf(
                "ALL" to results.size,
                "TORRENT" to torrentCount,
                "TELEGRAM" to telegramCount,
                "SANDBOX" to sandboxCount
            )
            items(tabs) { (tab, count) ->
                val isSelected = sourceFilter == tab
                val accent = when (tab) {
                    "TORRENT" -> AmberOrange
                    "TELEGRAM" -> NeonCyan
                    "SANDBOX" -> StatusGreen
                    else -> NeonPurpleLight
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isSelected) accent.copy(alpha = 0.2f) else Color(0xFF14172B))
                        .border(1.dp, if (isSelected) accent else Color(0xFF222744), RoundedCornerShape(20.dp))
                        .clickable { sourceFilter = tab }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "$tab ($count)",
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) accent else Color(0xFF94A3B8),
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        if (results.isEmpty() && !isSearching) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.SearchOff,
                        contentDescription = null,
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "No matches found for \"$query\"",
                        color = Color(0xFF94A3B8),
                        fontSize = 13.sp
                    )
                    Text(
                        "Try another title or paste a magnet link in Torrents tab",
                        color = Color(0xFF64748B),
                        fontSize = 11.sp
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                filtered.forEach { item ->
                    UnifiedSearchCard(
                        item = item,
                        onClick = { onSelectResult(item) },
                        onStream = { onStreamDirect(item) },
                        onDownload = { onDownloadToServer(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun UnifiedSearchCard(
    item: UnifiedSearchResult,
    onClick: () -> Unit,
    onStream: () -> Unit,
    onDownload: () -> Unit
) {
    val context = LocalContext.current
    val accentColor = when (item.source) {
        "TORRENT" -> AmberOrange
        "TELEGRAM" -> NeonCyan
        "SANDBOX" -> StatusGreen
        else -> NeonPurpleLight
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF101323))
            .border(1.dp, Color(0xFF1E233E), RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Poster / Icon
            Box(
                modifier = Modifier
                    .width(60.dp)
                    .height(84.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1A1F38))
            ) {
                if (item.posterUrl.isNotBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(item.posterUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = item.title,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        val icon = when (item.source) {
                            "TORRENT" -> Icons.Filled.CloudDownload
                            "TELEGRAM" -> Icons.Filled.Send
                            else -> Icons.Filled.Folder
                        }
                        Icon(icon, null, tint = accentColor.copy(alpha = 0.6f), modifier = Modifier.size(24.dp))
                    }
                }

                // Source Badge
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(3.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(accentColor.copy(alpha = 0.9f))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        item.source.take(4),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Details & Actions
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (item.quality.isNotBlank()) {
                        Text(
                            text = item.quality,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentColor,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (item.year.isNotBlank()) {
                        Text(item.year, fontSize = 11.sp, color = Color(0xFF64748B), fontFamily = FontFamily.Monospace)
                    }
                    if (item.size.isNotBlank()) {
                        Text(item.size, fontSize = 11.sp, color = Color(0xFF94A3B8), fontFamily = FontFamily.Monospace)
                    }
                    if (item.source == "TORRENT" && item.seeds > 0) {
                        Text("${item.seeds} seeds", fontSize = 10.sp, color = StatusGreen, fontFamily = FontFamily.Monospace)
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Action Buttons Row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // STREAM Button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                Brush.horizontalGradient(listOf(Color(0xFF0284C7), Color(0xFF7C3AED)))
                            )
                            .clickable { onStream() }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(12.dp))
                            Text("STREAM", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = Color.White, fontFamily = FontFamily.Monospace)
                        }
                    }

                    // SAVE TO SERVER Button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (item.isLocal) StatusGreen.copy(alpha = 0.15f)
                                else accentColor.copy(alpha = 0.15f)
                            )
                            .border(
                                1.dp,
                                if (item.isLocal) StatusGreen.copy(alpha = 0.4f)
                                else accentColor.copy(alpha = 0.4f),
                                RoundedCornerShape(6.dp)
                            )
                            .clickable { onDownload() }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(
                                if (item.isLocal) Icons.Filled.Check else Icons.Filled.Download,
                                null,
                                tint = if (item.isLocal) StatusGreen else accentColor,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                if (item.isLocal) "ON SERVER" else "SAVE TO SERVER",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (item.isLocal) StatusGreen else accentColor,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnifiedResultActionModal(
    result: UnifiedSearchResult,
    onDismiss: () -> Unit,
    onStream: () -> Unit,
    onSaveToServer: () -> Unit
) {
    val context = LocalContext.current
    val accentColor = when (result.source) {
        "TORRENT" -> AmberOrange
        "TELEGRAM" -> NeonCyan
        "SANDBOX" -> StatusGreen
        else -> NeonPurpleLight
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F1222),
        tonalElevation = 8.dp,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header with poster and metadata
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(80.dp)
                        .height(115.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF1E233E))
                ) {
                    if (result.posterUrl.isNotBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(result.posterUrl).crossfade(true).build(),
                            contentDescription = result.title,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Movie, null, tint = accentColor.copy(alpha = 0.5f), modifier = Modifier.size(32.dp))
                        }
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Source badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(accentColor.copy(alpha = 0.2f))
                            .border(1.dp, accentColor, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${result.source} SOURCE",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentColor,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Text(
                        text = result.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = listOfNotNull(
                            result.year.ifBlank { null },
                            result.quality.ifBlank { null },
                            result.size.ifBlank { null }
                        ).joinToString(" • "),
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8),
                        fontFamily = FontFamily.Monospace
                    )

                    if (result.source == "TORRENT" && result.seeds > 0) {
                        Text(
                            text = "🟢 ${result.seeds} active seeders in swarm",
                            fontSize = 11.sp,
                            color = StatusGreen,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // Description / mode explanation
            val explanation = when (result.source) {
                "TORRENT" -> "Sequential Stremio-style swarm streaming. Tapping Save to Server downloads the full video directly into your home movies vault in the background without needing any external client."
                "TELEGRAM" -> "Direct on-the-fly CDN proxy streaming without disk writes. Tapping Save to Server downloads the complete original file to your home server disk independently."
                else -> "Stored locally in your PocketNode Sandbox. Instant direct playback from disk."
            }
            Text(
                text = explanation,
                fontSize = 12.sp,
                color = Color(0xFF64748B),
                lineHeight = 16.sp
            )

            // Primary & Secondary Actions
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // STREAM DIRECT (PRIMARY)
                Button(
                    onClick = onStream,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    contentPadding = PaddingValues(0.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xFF0284C7), Color(0xFF7C3AED))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Text(
                                "STREAM DIRECT (NO WAIT)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                // SAVE TO SERVER (SECONDARY)
                OutlinedButton(
                    onClick = onSaveToServer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (result.isLocal) StatusGreen else accentColor),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            if (result.isLocal) Icons.Filled.Check else Icons.Filled.Download,
                            null,
                            tint = if (result.isLocal) StatusGreen else accentColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            if (result.isLocal) "ALREADY IN SANDBOX (LOCAL)" else "SAVE TO HOME SERVER (INDEPENDENT)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (result.isLocal) StatusGreen else accentColor,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}
