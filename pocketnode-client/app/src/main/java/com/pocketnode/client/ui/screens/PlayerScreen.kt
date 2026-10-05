package com.pocketnode.client.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.pocketnode.client.data.RemoteEndpoint
import com.pocketnode.client.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

data class FloatingReaction(
    val id: Long = System.currentTimeMillis() + Random.nextLong(1000),
    val emoji: String,
    val startXFraction: Float = Random.nextFloat() * 0.7f + 0.15f
)

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    title: String,
    streamUrl: String = "",
    formatBadge: String = "4K HEVC • REMUX",
    initialPositionMs: Long = 0L,
    onClose: () -> Unit,
    onToast: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var activeTitle by remember { mutableStateOf(title) }
    var currentUriString by remember { mutableStateOf(streamUrl) }
    var isLocalSource by remember { mutableStateOf(false) }
    var playbackError by remember { mutableStateOf<String?>(null) }

    val resumeTargetMs = remember(title, streamUrl, initialPositionMs) {
        if (initialPositionMs > 0L) initialPositionMs
        else ClientRepository.getSavedProgressMs(title, streamUrl)
    }
    var hasAppliedResume by remember { mutableStateOf(false) }

    // ── Torrent Stream Status (Stremio-style swarm HUD) ──────────────────
    val isTorrentStream = remember(streamUrl) { streamUrl.contains("/stream/torrent") }
    val torrentStatusApiUrl = remember(streamUrl) {
        if (!streamUrl.contains("/stream/torrent")) return@remember ""
        // Extract server base from the stream URL (e.g. http://192.168.1.x:8080)
        val base = streamUrl.substringBefore("/stream/torrent")
        val magnet = streamUrl.substringAfter("magnet=", "").substringBefore("&")
        "$base/api/torrent/stream_status?magnet=$magnet"
    }
    var torrentStatusJson by remember { mutableStateOf<String?>(null) }
    // Parsed from JSON: readyToPlay, seeds, peers, downloadSpeed, bufferedPercent, status, fileName
    var torrentReady by remember { mutableStateOf(false) }
    var torrentSeeds by remember { mutableStateOf(0) }
    var torrentPeers by remember { mutableStateOf(0) }
    var torrentSpeed by remember { mutableStateOf("0 KB/s") }
    var torrentBufferedPct by remember { mutableStateOf(0) }
    var torrentStatusLabel by remember(isTorrentStream) { mutableStateOf(if (isTorrentStream) "SEARCHING SWARM" else "BUFFERING MEDIA...") }
    var torrentFileName by remember { mutableStateOf("") }

    // Resolve initial URI (Checks if downloaded video exists on device storage first!)
    val initialUri = remember(streamUrl, title) {
        val local = LocalMediaScanner.resolveLocalVideoUri(context, streamUrl)
            ?: LocalMediaScanner.resolveLocalVideoUri(context, title)
        if (local != null) {
            isLocalSource = true
            local
        } else if (streamUrl.startsWith("http://") || streamUrl.startsWith("https://")) {
            isLocalSource = false
            Uri.parse(streamUrl)
        } else if (streamUrl.isNotBlank()) {
            Uri.parse(streamUrl)
        } else {
            // Fallback sample video
            Uri.parse("https://storage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4")
        }
    }

    // Media3 ExoPlayer with universal DataSource (supports file://, content://, and http/https)
    val exoPlayer = remember {
        val dataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(context)
        val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dataSourceFactory)

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build().apply {
                playWhenReady = true
                repeatMode = Player.REPEAT_MODE_OFF

                addListener(object : Player.Listener {
                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        if (isTorrentStream && !isLocalSource) {
                            // Swarm is still connecting or buffering, suppress error and wait for swarm
                            playbackError = null
                            torrentStatusLabel = "BUFFERING SWARM..."
                        } else {
                            playbackError = error.message ?: "Failed to read or decode video file"
                        }
                    }

                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_READY) {
                            playbackError = null
                            if (!hasAppliedResume && resumeTargetMs > 4000L) {
                                hasAppliedResume = true
                                seekTo(resumeTargetMs)
                                val m = (resumeTargetMs / 1000L) / 60
                                val s = (resumeTargetMs / 1000L) % 60
                                onToast("Resuming at ${String.format(java.util.Locale.US, "%02d:%02d", m, s)}")
                            }
                        }
                    }
                })

                // Only prepare immediately for local files or standard non-torrent HTTP streams
                if (!isTorrentStream || isLocalSource) {
                    setMediaItem(MediaItem.fromUri(initialUri))
                    prepare()
                }
            }
    }

    // Local Video File Picker Launcher (allows picking any downloaded video on device)
    val videoPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { pickedUri: Uri? ->
        if (pickedUri != null) {
            val name = pickedUri.lastPathSegment?.substringAfterLast("/") ?: "Local Video"
            activeTitle = LocalMediaScanner.cleanFileNameToTitle(name)
            currentUriString = pickedUri.toString()
            isLocalSource = true
            playbackError = null
            onToast("Playing local downloaded video: $activeTitle")

            exoPlayer.setMediaItem(MediaItem.fromUri(pickedUri))
            exoPlayer.prepare()
            exoPlayer.play()
        }
    }

    var isPlaying by remember { mutableStateOf(true) }
    var currentPositionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var bufferedPositionMs by remember { mutableStateOf(0L) }

    // HUD Visibility State
    var showControls by remember { mutableStateOf(true) }
    var lastInteractionTime by remember { mutableStateOf(System.currentTimeMillis()) }

    // Walkie-Talkie Push-to-Talk (PTT) State
    var isPttActive by remember { mutableStateOf(false) }

    // Floating Reactions
    val activeReactions = remember { mutableStateListOf<FloatingReaction>() }

    // Modals
    var showHandoffSheet by remember { mutableStateOf(false) }
    var showSubtitleDialog by remember { mutableStateOf(false) }
    var selectedSubtitle by remember { mutableStateOf("English [SDH]") }

    // Vibrator Helper
    fun triggerHaptic(durationMs: Long = 40) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        vibrator?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                it.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                it.vibrate(durationMs)
            }
        }
    }

    // Audio Ducking Logic
    fun setDucked(duck: Boolean) {
        isPttActive = duck
        triggerHaptic(if (duck) 50 else 25)
        exoPlayer.volume = if (duck) 0.25f else 1.0f // Duck movie volume to 25%
    }

    // Periodic Player Position Polling, HUD auto-hide & Live Progress Reporting
    LaunchedEffect(exoPlayer) {
        var lastSavedSec = 0L
        while (true) {
            currentPositionMs = exoPlayer.currentPosition
            durationMs = if (exoPlayer.duration > 0) exoPlayer.duration else 1L
            bufferedPositionMs = exoPlayer.bufferedPosition
            isPlaying = exoPlayer.isPlaying

            // Report progress every 3 seconds while playing
            val currentSec = currentPositionMs / 1000L
            if (isPlaying && durationMs > 5000L && Math.abs(currentSec - lastSavedSec) >= 3) {
                lastSavedSec = currentSec
                ClientRepository.updatePlaybackProgress(
                    id = "",
                    title = activeTitle,
                    streamUrl = currentUriString,
                    positionMs = currentPositionMs,
                    durationMs = durationMs,
                    formatBadge = formatBadge
                )
            }

            // Auto-hide controls after 3.5s of inactivity
            if (showControls && isPlaying && System.currentTimeMillis() - lastInteractionTime > 3500) {
                showControls = false
            }
            delay(250)
        }
    }

    // ── Torrent Stream Status Poller (Stremio-style) ─────────────────────
    LaunchedEffect(torrentStatusApiUrl) {
        if (torrentStatusApiUrl.isBlank()) return@LaunchedEffect
        while (true) {
            try {
                val url = java.net.URL(torrentStatusApiUrl)
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 4000
                conn.readTimeout = 6000
                val code = conn.responseCode
                if (code == 200) {
                    val body = conn.inputStream.bufferedReader().readText()
                    val gson = com.google.gson.Gson()
                    val obj = gson.fromJson(body, com.google.gson.JsonObject::class.java)
                    torrentReady      = obj.get("readyToPlay")?.asBoolean ?: false
                    torrentSeeds      = obj.get("seeds")?.asInt ?: 0
                    torrentPeers      = obj.get("peers")?.asInt ?: 0
                    torrentSpeed      = obj.get("downloadSpeed")?.asString ?: "0 KB/s"
                    torrentBufferedPct= obj.get("bufferedPercent")?.asInt ?: 0
                    torrentStatusLabel= obj.get("status")?.asString ?: "SEARCHING SWARM"
                    torrentFileName   = obj.get("fileName")?.asString ?: ""

                    if (torrentReady && isTorrentStream && !isLocalSource && (exoPlayer.playbackState == Player.STATE_IDLE || exoPlayer.mediaItemCount == 0)) {
                        playbackError = null
                        exoPlayer.setMediaItem(MediaItem.fromUri(initialUri))
                        exoPlayer.prepare()
                        exoPlayer.play()
                    }
                }
                conn.disconnect()
            } catch (_: Exception) {}
            delay(2000)
        }
    }

    // Auto-start playback as soon as torrent swarm is ready
    LaunchedEffect(torrentReady, isTorrentStream, isLocalSource) {
        if (isTorrentStream && !isLocalSource && torrentReady) {
            playbackError = null
            if (exoPlayer.playbackState == Player.STATE_IDLE || exoPlayer.mediaItemCount == 0) {
                exoPlayer.setMediaItem(MediaItem.fromUri(initialUri))
                exoPlayer.prepare()
                exoPlayer.play()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (durationMs > 5000L) {
                ClientRepository.updatePlaybackProgress(
                    id = "",
                    title = activeTitle,
                    streamUrl = currentUriString,
                    positionMs = exoPlayer.currentPosition,
                    durationMs = durationMs,
                    formatBadge = formatBadge
                )
            }
            exoPlayer.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        showControls = !showControls
                        lastInteractionTime = System.currentTimeMillis()
                    }
                )
            }
    ) {
        // Video Surface
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false // Use our custom tactical Jetpack Compose HUD
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Floating Reactions Particles Layer
        activeReactions.forEach { reaction ->
            key(reaction.id) {
                FloatingReactionParticle(
                    reaction = reaction,
                    onFinished = { activeReactions.remove(reaction) }
                )
            }
        }

        // ── Torrent Swarm Buffering Overlay (Stremio-style) ───────────────
        if (isTorrentStream && !isLocalSource && !torrentReady) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xE6000000)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.padding(32.dp)
                ) {
                    // Animated swarm icon
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(Color(0xFF1A1040), Color(0xFF6C3DE0))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CloudDownload,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }

                    Text(
                        "TORRENT STREAM",
                        color = Color(0xFF9B6EFF),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 2.sp
                    )

                    Text(
                        activeTitle.uppercase(),
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )

                    if (torrentFileName.isNotBlank()) {
                        Text(
                            torrentFileName,
                            color = Color(0xFF888888),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }

                    // Buffer progress bar
                    Column(
                        modifier = Modifier.fillMaxWidth(0.75f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        LinearProgressIndicator(
                            progress = { (torrentBufferedPct / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = Color(0xFF9B6EFF),
                            trackColor = Color(0xFF2A2040)
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "$torrentBufferedPct% BUFFERED",
                            color = Color(0xFF9B6EFF),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // Swarm stats row
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "$torrentSeeds",
                                color = Color(0xFF4BDE80),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
                            Text("SEEDS", color = Color(0xFF555555), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "$torrentPeers",
                                color = Color(0xFF60A5FA),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
                            Text("PEERS", color = Color(0xFF555555), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                torrentSpeed,
                                color = Color(0xFFFBBF24),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
                            Text("SPEED", color = Color(0xFF555555), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                        }
                    }

                    // Status label
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF1A1040))
                            .border(1.dp, Color(0xFF6C3DE0).copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            torrentStatusLabel,
                            color = Color(0xFF9B6EFF),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    OutlinedButton(
                        onClick = onClose,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF444444)),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color(0xFF888888), modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("CANCEL", color = Color(0xFF888888), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }

        // PTT Active Transmission Banner
        AnimatedVisibility(
            visible = isPttActive,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 70.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = TacticalAmber.copy(alpha = 0.9f)),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color.Red)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "VOICE TRANSMITTING // FILM AUDIO DUCKED (25%)",
                        color = Color.Black,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // Tactical HUD Overlay (Header, Center Play/Pause, Bottom Timeline)
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.8f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.85f)
                            )
                        )
                    )
            ) {
                // Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(ObsidianCard.copy(alpha = 0.6f))
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = TextPrimary
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = activeTitle.uppercase(),
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(if (isLocalSource) TacticalGreen.copy(alpha = 0.25f) else TacticalAmber.copy(alpha = 0.2f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (isLocalSource) "OFFLINE FILE" else formatBadge,
                                        color = if (isLocalSource) TacticalGreen else TacticalAmber,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isLocalSource) "LOCAL DIRECT PLAY • ZERO LATENCY" else "DIRECT PLAY • DOLBY ATMOS",
                                    color = TextMuted,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    // Action Buttons (Open Local File + Cast to TV)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { videoPickerLauncher.launch("video/*") },
                            colors = ButtonDefaults.buttonColors(containerColor = TacticalGreen.copy(alpha = 0.2f)),
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, TacticalGreen),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(imageVector = Icons.Default.FolderOpen, contentDescription = null, tint = TacticalGreen, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "OPEN FILE",
                                color = TacticalGreen,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Button(
                            onClick = { showHandoffSheet = true },
                            colors = ButtonDefaults.buttonColors(containerColor = TacticalCyan.copy(alpha = 0.2f)),
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, TacticalCyan),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Tv, contentDescription = null, tint = TacticalCyan, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "CAST TO TV",
                                color = TacticalCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                // Playback Error / Local File Fallback Overlay
                if (playbackError != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF141722).copy(alpha = 0.95f))
                            .border(1.dp, TacticalRed, RoundedCornerShape(10.dp))
                            .padding(18.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = TacticalRed, modifier = Modifier.size(36.dp))
                            Text(
                                "PLAYBACK NOTICE",
                                color = TacticalRed,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                "Could not stream video. If this is a downloaded file, select it from your device storage to direct play.",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                fontFamily = FontFamily.Monospace
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = { videoPickerLauncher.launch("video/*") },
                                    colors = ButtonDefaults.buttonColors(containerColor = TacticalGreen)
                                ) {
                                    Icon(Icons.Default.FolderOpen, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("SELECT DOWNLOADED VIDEO", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                }
                                OutlinedButton(
                                    onClick = {
                                        playbackError = null
                                        exoPlayer.setMediaItem(MediaItem.fromUri(Uri.parse("https://storage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4")))
                                        exoPlayer.prepare()
                                        exoPlayer.play()
                                    },
                                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
                                ) {
                                    Text("TEST DEMO", color = TextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }
                    }
                }

                // Center Controls (Rewind 10, Play/Pause, Forward 10)
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            exoPlayer.seekTo((exoPlayer.currentPosition - 10_000).coerceAtLeast(0L))
                            lastInteractionTime = System.currentTimeMillis()
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(ObsidianCard.copy(alpha = 0.7f))
                    ) {
                        Icon(imageVector = Icons.Default.Replay10, contentDescription = "Rewind", tint = TextPrimary, modifier = Modifier.size(28.dp))
                    }

                    IconButton(
                        onClick = {
                            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                            lastInteractionTime = System.currentTimeMillis()
                        },
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(TacticalAmber)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = ObsidianBase,
                            modifier = Modifier.size(38.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            exoPlayer.seekTo((exoPlayer.currentPosition + 10_000).coerceAtMost(durationMs))
                            lastInteractionTime = System.currentTimeMillis()
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(ObsidianCard.copy(alpha = 0.7f))
                    ) {
                        Icon(imageVector = Icons.Default.Forward10, contentDescription = "Fast Forward", tint = TextPrimary, modifier = Modifier.size(28.dp))
                    }
                }

                // Bottom Bar (Timeline + Reaction bar + Tools)
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 20.dp)
                ) {
                    // Timeline Slider
                    Slider(
                        value = if (durationMs > 0) currentPositionMs.toFloat() / durationMs.toFloat() else 0f,
                        onValueChange = { fraction ->
                            val target = (fraction * durationMs).toLong()
                            exoPlayer.seekTo(target)
                            lastInteractionTime = System.currentTimeMillis()
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = TacticalAmber,
                            activeTrackColor = TacticalAmber,
                            inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Time Stamps & Metadata
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatDuration(currentPositionMs),
                            color = TextPrimary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "-${formatDuration((durationMs - currentPositionMs).coerceAtLeast(0L))}",
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Quick Actions Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Quick Reactions
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf("🔥", "❤️", "😂", "🍿", "😱", "👏").forEach { emoji ->
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(ObsidianCard.copy(alpha = 0.7f))
                                        .clickable {
                                            activeReactions.add(FloatingReaction(emoji = emoji))
                                            onToast("Sent $emoji reaction to Watch Party")
                                            lastInteractionTime = System.currentTimeMillis()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(emoji, fontSize = 16.sp)
                                }
                            }
                        }

                        // Subtitle & External Player
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(
                                onClick = { showSubtitleDialog = true },
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(ObsidianCard.copy(alpha = 0.7f))
                            ) {
                                Icon(imageVector = Icons.Default.Subtitles, contentDescription = "Subtitles", tint = TacticalCyan, modifier = Modifier.size(18.dp))
                            }

                            IconButton(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(Uri.parse(streamUrl.ifBlank { "http://192.168.1.100:8080/stream?title=$title" }), "video/*")
                                    }
                                    try {
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        onToast("Install VLC or MX Player for external playback")
                                    }
                                },
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(ObsidianCard.copy(alpha = 0.7f))
                            ) {
                                Icon(imageVector = Icons.Default.OpenInNew, contentDescription = "External Player", tint = TextPrimary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }

        // Walkie-Talkie Push-to-Talk (PTT) Floating Tactical Mic Button (Always visible on right)
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp)
                .size(64.dp)
                .clip(CircleShape)
                .background(if (isPttActive) TacticalAmber else ObsidianCard.copy(alpha = 0.85f))
                .border(2.dp, if (isPttActive) Color.White else TacticalAmber, CircleShape)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            setDucked(true)
                            tryAwaitRelease()
                            setDucked(false)
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Push-to-Talk",
                    tint = if (isPttActive) ObsidianBase else TacticalAmber,
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text = if (isPttActive) "LIVE" else "HOLD PTT",
                    color = if (isPttActive) ObsidianBase else TacticalAmber,
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black
                )
            }
        }

        // Modal: Handoff / Stream on Another Device Sheet
        if (showHandoffSheet) {
            AlertDialog(
                onDismissRequest = { showHandoffSheet = false },
                title = {
                    Text(
                        text = "STREAM ON ANOTHER DEVICE",
                        color = TacticalCyan,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                },
                text = {
                    Column {
                        Text(
                            text = "Handoff '${title}' directly at ${formatDuration(currentPositionMs)} to local Wi-Fi target:",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        val devices = listOf(
                            RemoteEndpoint("1", "Living Room Shield TV", "Android TV • 192.168.1.120", true),
                            RemoteEndpoint("2", "MacBook Pro M3 (Study)", "macOS • 192.168.1.145", true),
                            RemoteEndpoint("3", "Kids Room Tablet", "Android 14 • 192.168.1.160", false)
                        )

                        devices.forEach { device ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable(enabled = device.isOnline) {
                                        exoPlayer.pause()
                                        onToast("Handoff successful! Transferred stream to ${device.name} at ${formatDuration(currentPositionMs)}")
                                        showHandoffSheet = false
                                    },
                                colors = CardDefaults.cardColors(containerColor = ObsidianBase),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (device.isOnline) TacticalCyan.copy(alpha = 0.5f) else BorderSubtle
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(device.name, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        Text(device.ip, color = TextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                    }
                                    Text(
                                        text = if (device.isOnline) "TRANSFER" else "OFFLINE",
                                        color = if (device.isOnline) TacticalCyan else TextMuted,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showHandoffSheet = false }) {
                        Text("CANCEL", color = TextMuted)
                    }
                },
                containerColor = ObsidianCard
            )
        }

        // Modal: Subtitle Selection Dialog
        if (showSubtitleDialog) {
            AlertDialog(
                onDismissRequest = { showSubtitleDialog = false },
                title = {
                    Text("SUBTITLE TRACKS", color = TacticalAmber, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                },
                text = {
                    Column {
                        listOf("None (Audio Only)", "English [SDH]", "English (Commentary)", "Spanish / Español", "French / Français").forEach { track ->
                            val isSelected = selectedSubtitle == track
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedSubtitle = track
                                        onToast("Selected subtitle: $track")
                                        showSubtitleDialog = false
                                    }
                                    .padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(track, color = if (isSelected) TacticalAmber else TextPrimary, fontSize = 13.sp)
                                if (isSelected) {
                                    Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = TacticalAmber, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSubtitleDialog = false }) {
                        Text("DONE", color = TextMuted)
                    }
                },
                containerColor = ObsidianCard
            )
        }
    }
}

@Composable
fun FloatingReactionParticle(
    reaction: FloatingReaction,
    onFinished: () -> Unit
) {
    val progress = remember { Animatable(0f) }

    LaunchedEffect(reaction) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 2400, easing = LinearEasing)
        )
        onFinished()
    }

    val yOffsetFraction = 1f - progress.value // Drifts from bottom (1.0) to top (0.0)
    val alphaFraction = (1f - progress.value).coerceIn(0f, 1f)

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val xPos = maxWidth * reaction.startXFraction
        val yPos = maxHeight * yOffsetFraction

        Text(
            text = reaction.emoji,
            fontSize = 32.sp,
            modifier = Modifier
                .offset(x = xPos, y = yPos)
                .background(Color.Transparent)
        )
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0L)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
