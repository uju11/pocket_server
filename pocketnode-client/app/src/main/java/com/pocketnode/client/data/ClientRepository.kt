package com.pocketnode.client.data

import com.google.gson.Gson
import com.google.gson.JsonArray
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL

object ClientRepository {

    // ── Server connection ──────────────────────────────────────────────────────
    // Host/port of the PC running bot.py (the LAN bridge on port 8765).
    // Change this if your PC's IP is different.
    var serverBaseUrl: String = "http://192.168.1.100:8765"

    private val repoScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val gson = Gson()

    init {
        repoScope.launch { refreshFromServer() }
    }

    // ── Metadata Scanner State ─────────────────────────────────────────────
    private val _scanState = MutableStateFlow(ScanState())
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    /** Triggers a full local media scan, updating scanState live. */
    fun runMetadataScan(context: android.content.Context) {
        if (_scanState.value.status == ScanStatus.SCANNING) return
        repoScope.launch(Dispatchers.IO) {
            _scanState.value = ScanState(
                status = ScanStatus.SCANNING,
                log = listOf(ScanLogEntry(message = "[SCAN] Starting media discovery..."))
            )
            try {
                val logAcc = mutableListOf<ScanLogEntry>()
                logAcc += ScanLogEntry(message = "[SCAN] Querying MediaStore...")
                _scanState.value = _scanState.value.copy(log = logAcc.toList(), currentPath = "MediaStore")

                val localItems = LocalMediaScanner.scanDownloadedVideos(context)
                logAcc += ScanLogEntry(message = "[SCAN] Found ${localItems.size} video files in storage.")
                _scanState.value = _scanState.value.copy(
                    scannedCount = localItems.size,
                    progressPercent = 0.5f,
                    log = logAcc.toList(),
                    currentPath = "Merging library..."
                )

                val current = _libraryMovies.value.toMutableList()
                val existingIds = current.map { it.id }.toSet()
                val newOnes = localItems.filter { it.id !in existingIds }
                if (newOnes.isNotEmpty()) {
                    _libraryMovies.value = newOnes + current
                    logAcc += ScanLogEntry(message = "[SCAN] Added ${newOnes.size} new items to library.")
                } else {
                    logAcc += ScanLogEntry(message = "[SCAN] Library already up-to-date.")
                }

                logAcc += ScanLogEntry(message = "[SCAN] Fetching Telegram catalog...")
                _scanState.value = _scanState.value.copy(progressPercent = 0.75f, log = logAcc.toList(), currentPath = "Telegram")
                try { fetchTelegramCatalog() } catch (_: Exception) {
                    logAcc += ScanLogEntry(message = "[WARN] Telegram server unreachable.", isError = true)
                }

                logAcc += ScanLogEntry(message = "[SCAN] Fetching trending torrents...")
                _scanState.value = _scanState.value.copy(progressPercent = 0.9f, log = logAcc.toList(), currentPath = "Torrents")
                try { fetchTrendingTorrents() } catch (_: Exception) {
                    logAcc += ScanLogEntry(message = "[WARN] Torrent index server unreachable.", isError = true)
                }

                logAcc += ScanLogEntry(message = "[SCAN] ✓ Scan complete — ${localItems.size} files, ${newOnes.size} new.")
                _scanState.value = ScanState(
                    status = ScanStatus.DONE,
                    scannedCount = localItems.size,
                    newFoundCount = newOnes.size,
                    lastScanTs = System.currentTimeMillis(),
                    progressPercent = 1f,
                    log = logAcc.toList()
                )
            } catch (e: Exception) {
                _scanState.value = _scanState.value.copy(
                    status = ScanStatus.ERROR,
                    log = _scanState.value.log + ScanLogEntry(message = "[ERROR] ${e.message}", isError = true)
                )
            }
        }
    }

    fun setScheduledScanInterval(hours: Int) {
        _scanState.value = _scanState.value.copy(scheduledIntervalHours = hours)
    }

    /** Polls the bot's LAN bridge for live Telegram catalog + trending torrents.
     *  Retries every 60 s so the UI always shows fresh data. */
    private suspend fun refreshFromServer() {
        while (true) {
            try { fetchResumeFromServer() } catch (_: Exception) {}
            try { fetchTelegramCatalog() } catch (_: Exception) {}
            try { fetchTrendingTorrents() } catch (_: Exception) {}
            delay(15_000L)
        }
    }

    private fun getEffectiveBaseUrl(): String {
        val s = _session.value
        return if (s.serverHost.isNotBlank()) "http://${s.serverHost}:${s.serverPort}" else serverBaseUrl
    }

    private fun fetchTelegramCatalog() {
        val base = getEffectiveBaseUrl()
        val endpoints = listOf("$base/api/telegram_catalog", "$base/api/telegram/media", "$serverBaseUrl/api/telegram_catalog")
        for (ep in endpoints) {
            try {
                val url = URL(ep)
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 8_000
                conn.readTimeout = 15_000
                conn.requestMethod = "GET"
                val code = conn.responseCode
                if (code == 200) {
                    val body = conn.inputStream.bufferedReader().readText().trim()
                    val arr = if (body.startsWith("{")) {
                        val json = gson.fromJson(body, com.google.gson.JsonObject::class.java)
                        json.getAsJsonArray("items") ?: json.getAsJsonArray("result")
                    } else if (body.startsWith("[")) {
                        gson.fromJson(body, JsonArray::class.java)
                    } else null

                    if (arr != null) {
                        val items = arr.mapNotNull { el ->
                            try {
                                val obj = el.asJsonObject
                                val title = obj.get("cleanTitle")?.asString ?: obj.get("title")?.asString ?: ""
                                val poster = obj.get("posterUrl")?.asString ?: ""
                                val resolvedPoster = if (poster.isNotBlank()) poster else PosterResolver.getPoster(title)

                                TelegramMediaDto(
                                    id           = obj.get("id")?.asString ?: return@mapNotNull null,
                                    fileId       = obj.get("fileId")?.asString ?: "",
                                    fileName     = obj.get("fileName")?.asString ?: "",
                                    cleanTitle   = title,
                                    year         = obj.get("year")?.asString ?: "",
                                    quality      = obj.get("quality")?.asString ?: "1080p",
                                    fileSize     = obj.get("fileSize")?.asLong ?: 0L,
                                    sizeFormatted= obj.get("sizeFormatted")?.asString ?: "",
                                    mimeType     = obj.get("mimeType")?.asString ?: "video/mp4",
                                    streamUrl    = obj.get("streamUrl")?.asString ?: "",
                                    posterUrl    = resolvedPoster,
                                    source       = obj.get("source")?.asString ?: "Saved Messages",
                                    channelName  = obj.get("channelName")?.asString ?: ""
                                )
                            } catch (_: Exception) { null }
                        }
                        if (items.isNotEmpty()) {
                            _telegramMovies.value = items
                            conn.disconnect()
                            return
                        }
                    }
                }
                conn.disconnect()
            } catch (_: Exception) {}
        }
    }

    private fun fetchTrendingTorrents() {
        val base = getEffectiveBaseUrl()
        val endpoints = listOf("$base/api/torrents/trending", "$base/api/trending", "$serverBaseUrl/api/trending")
        for (ep in endpoints) {
            try {
                val url = URL(ep)
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 10_000
                conn.readTimeout = 25_000
                conn.requestMethod = "GET"
                val code = conn.responseCode
                if (code == 200) {
                    val body = conn.inputStream.bufferedReader().readText().trim()
                    val arr = if (body.startsWith("{")) {
                        val json = gson.fromJson(body, com.google.gson.JsonObject::class.java)
                        json.getAsJsonArray("items") ?: json.getAsJsonArray("result")
                    } else if (body.startsWith("[")) {
                        gson.fromJson(body, JsonArray::class.java)
                    } else null

                    if (arr != null) {
                        val items = arr.mapNotNull { el ->
                            try {
                                val obj = el.asJsonObject
                                val title = obj.get("title")?.asString ?: ""
                                val poster = obj.get("posterUrl")?.asString ?: ""
                                val resolvedPoster = if (poster.isNotBlank()) poster else PosterResolver.getPoster(title)

                                TrendingTorrentDto(
                                    id         = obj.get("id")?.asString ?: return@mapNotNull null,
                                    title      = title,
                                    year       = obj.get("year")?.asString ?: "",
                                    genre      = obj.get("genre")?.asString ?: "",
                                    rating     = obj.get("rating")?.asString ?: "N/A",
                                    quality    = obj.get("quality")?.asString ?: "1080p",
                                    size       = obj.get("size")?.asString ?: "",
                                    seeds      = obj.get("seeds")?.asInt ?: 0,
                                    magnetUri  = obj.get("magnetUri")?.asString ?: "",
                                    posterUrl  = resolvedPoster
                                )
                            } catch (_: Exception) { null }
                        }
                        if (items.isNotEmpty()) {
                            _trendingTorrents.value = items
                            conn.disconnect()
                            return
                        }
                    }
                }
                conn.disconnect()
            } catch (_: Exception) {}
        }
    }

    private val _session = MutableStateFlow(ClientSession())
    val session: StateFlow<ClientSession> = _session.asStateFlow()

    private val savedProgressMap = java.util.concurrent.ConcurrentHashMap<String, Long>()

    private val _continueWatching = MutableStateFlow<MediaItemDto?>(null)
    val continueWatching: StateFlow<MediaItemDto?> = _continueWatching.asStateFlow()

    fun updatePlaybackProgress(
        id: String,
        title: String,
        streamUrl: String,
        positionMs: Long,
        durationMs: Long,
        formatBadge: String = "1080p • Direct Play"
    ) {
        if (durationMs <= 0L) return
        val percent = (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        val cleanKey = title.lowercase().replace(Regex("[^a-z0-9]"), "")

        if (percent >= 0.92f) {
            savedProgressMap.remove(cleanKey)
            if (_continueWatching.value?.title.equals(title, ignoreCase = true)) {
                _continueWatching.value = null
            }
            return
        }

        if (positionMs < 3000L) return

        savedProgressMap[cleanKey] = positionMs

        val posSec = positionMs / 1000L
        val durSec = durationMs / 1000L
        val posStr = formatSecondsToTime(posSec)
        val durStr = formatSecondsToTime(durSec)
        val pctInt = (percent * 100).toInt()

        val item = MediaItemDto(
            id = id.ifBlank { "media_${title.hashCode()}" },
            title = title,
            year = "",
            category = "Movies",
            formatTag = "$formatBadge • Resuming at $posStr",
            formatBadge = formatBadge,
            sizeBytes = 0L,
            sizeFormatted = "--",
            progressPercent = percent,
            progressStr = "$posStr / $durStr • $pctInt% COMPLETE",
            durationStr = durStr,
            posterUrl = PosterResolver.getPoster(title),
            streamUrl = streamUrl,
            positionMs = positionMs
        )
        _continueWatching.value = item

        // Send progress report to server in background
        repoScope.launch {
            try {
                val base = getEffectiveBaseUrl()
                val url = URL("$base/api/playback/progress")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                val body = """{"id":"$id","title":"${title.replace("\"", "\\\"")}","streamUrl":"${streamUrl.replace("\"", "\\\"")}","positionSeconds":$posSec,"durationSeconds":$durSec}"""
                conn.outputStream.write(body.toByteArray())
                conn.responseCode
                conn.disconnect()
            } catch (_: Exception) {}
        }
    }

    fun getSavedProgressMs(title: String, streamUrl: String = ""): Long {
        val cleanKey = title.lowercase().replace(Regex("[^a-z0-9]"), "")
        return savedProgressMap[cleanKey] ?: if (_continueWatching.value?.title.equals(title, ignoreCase = true)) {
            _continueWatching.value?.positionMs ?: 0L
        } else 0L
    }

    private fun formatSecondsToTime(sec: Long): String {
        val m = sec / 60
        val s = sec % 60
        return String.format(java.util.Locale.US, "%02d:%02d", m, s)
    }

    private fun fetchResumeFromServer() {
        try {
            val base = getEffectiveBaseUrl()
            val url = URL("$base/api/playback/last")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 4000
            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().readText().trim()
                if (body.startsWith("{") && body.length > 2) {
                    val obj = gson.fromJson(body, com.google.gson.JsonObject::class.java)
                    val title = obj.get("title")?.asString ?: ""
                    val streamUrl = obj.get("streamUrl")?.asString ?: ""
                    val posSec = obj.get("positionSeconds")?.asLong ?: 0L
                    val durSec = obj.get("durationSeconds")?.asLong ?: 0L
                    val pct = obj.get("percent")?.asFloat ?: 0f
                    if (title.isNotBlank() && posSec > 0L) {
                        val posStr = formatSecondsToTime(posSec)
                        val durStr = formatSecondsToTime(durSec)
                        val pctInt = (pct * 100).toInt()
                        val item = MediaItemDto(
                            id = obj.get("itemId")?.asString ?: "last_played",
                            title = title,
                            year = "",
                            category = "Movies",
                            formatTag = "Direct Play • Resuming at $posStr",
                            formatBadge = "1080p • Direct Play",
                            progressPercent = pct,
                            progressStr = "$posStr / $durStr • $pctInt% COMPLETE",
                            durationStr = durStr,
                            posterUrl = PosterResolver.getPoster(title),
                            streamUrl = streamUrl,
                            positionMs = posSec * 1000L
                        )
                        _continueWatching.value = item
                        val cleanKey = title.lowercase().replace(Regex("[^a-z0-9]"), "")
                        savedProgressMap[cleanKey] = posSec * 1000L
                    }
                }
            }
            conn.disconnect()
        } catch (_: Exception) {}
    }

    private val _activeParty = MutableStateFlow<ActiveWatchPartyDto?>(
        ActiveWatchPartyDto(
            roomId = "ASLR-90",
            movieTitle = "SOLARIS",
            movieYear = "1972",
            director = "Andrei Tarkovsky",
            duration = "2h 47m",
            viewerCount = 3,
            additionalViewers = 3,
            isWalkieTalkieActive = true,
            syncedTime = "00:44:12",
            posterUrl = PosterResolver.getPoster("Solaris")
        )
    )
    val activeParty: StateFlow<ActiveWatchPartyDto?> = _activeParty.asStateFlow()

    private val _lanFeeds = MutableStateFlow<List<LanViewerFeedDto>>(
        listOf(
            LanViewerFeedDto(
                id = "feed_maya",
                userName = "Maya",
                deviceTarget = "LIVING ROOM TV",
                deviceIcon = "📺",
                movieTitle = "Past Lives (2023)",
                streamType = "DIRECT STREAM // 4K REMUX - 00:38:10",
                statusMsg = "REQUEST SENT TO MAYA... AWAITING HANDSHAKE...",
                canMirror = true,
                canSyncAudio = false
            ),
            LanViewerFeedDto(
                id = "feed_kenji",
                userName = "Kenji",
                deviceTarget = "STUDIO MAC",
                deviceIcon = "💻",
                movieTitle = "Blade Runner 2049",
                streamType = "TRANSCODE // 1080P SDR - 01:14:02",
                statusMsg = null,
                canMirror = false,
                canSyncAudio = true
            )
        )
    )
    val lanFeeds: StateFlow<List<LanViewerFeedDto>> = _lanFeeds.asStateFlow()

    private val _scheduledWatches = MutableStateFlow<List<ScheduledWatchDto>>(
        listOf(
            ScheduledWatchDto(
                id = "sched_interstellar",
                dateDay = "03",
                dateMonth = "OCT",
                dateDayOfWeek = "FRI",
                sessionType = "HOUSE SCREENING",
                timeSlot = "20:30 CST",
                movieTitle = "INTERSTELLAR (IMAX EDITI...)",
                hostNote = "Host: Alex • Projector Left Setup",
                rsvpCount = 4,
                isRsvpd = true
            )
        )
    )
    val scheduledWatches: StateFlow<List<ScheduledWatchDto>> = _scheduledWatches.asStateFlow()

    private val _householdPicks = MutableStateFlow<List<HouseholdPickDto>>(
        listOf(
            HouseholdPickDto(
                id = "pick_zone",
                recommenderName = "Alex",
                timestamp = "YESTERDAY",
                movieTitle = "THE ZONE OF INTEREST",
                movieYear = "2023",
                metadataLine = "JONATHAN GLAZER • 1H 45M • 4K HDR",
                quoteReview = "\"Masterclass in sound design. Watch with headphones or the studio system only.\"",
                posterUrl = PosterResolver.getPoster("The Zone of Interest")
            )
        )
    )
    val householdPicks: StateFlow<List<HouseholdPickDto>> = _householdPicks.asStateFlow()

    private val _libraryMovies = MutableStateFlow<List<MediaItemDto>>(
        listOf(
            MediaItemDto(
                id = "dune_part_two_2024",
                title = "Dune: Part Two",
                year = "2024",
                category = "Movies",
                formatTag = "4K IMAX • HDR10+ • Dolby Atmos",
                formatBadge = "4K REMUX",
                sizeBytes = 28_700_000_000L,
                sizeFormatted = "28.7 GB",
                progressPercent = 0.24f,
                progressStr = "Offline Ready • Direct Play",
                posterUrl = PosterResolver.getPoster("Dune: Part Two")
            ),
            MediaItemDto(
                id = "avengers_endgame",
                title = "Avengers: Endgame",
                year = "2019",
                category = "Movies",
                formatTag = "4K UHD • HEVC • DTS-HD 7.1",
                formatBadge = "MATROSKA REMUX",
                sizeBytes = 18_400_000_000L,
                sizeFormatted = "18.4 GB",
                progressPercent = 0f,
                progressStr = "Unplayed • Seeded from Local Node",
                posterUrl = PosterResolver.getPoster("Avengers: Endgame")
            ),
            MediaItemDto(
                id = "past_lives",
                title = "Past Lives",
                year = "2023",
                category = "Movies",
                formatTag = "1080P WEB-DL • AAC 5.1",
                formatBadge = "H.264 MP4",
                sizeBytes = 4_200_000_000L,
                sizeFormatted = "4.2 GB",
                progressPercent = 1f,
                progressStr = "Finished (2 weeks ago)",
                posterUrl = PosterResolver.getPoster("Past Lives")
            ),
            MediaItemDto(
                id = "solaris_1972",
                title = "Solaris",
                year = "1972",
                category = "Movies",
                formatTag = "1080P REMUX • MONO LPCM",
                formatBadge = "CRITERION RESTORATION",
                sizeBytes = 12_100_000_000L,
                sizeFormatted = "12.1 GB",
                progressPercent = 0.44f,
                progressStr = "Paused at 01:14:22",
                posterUrl = PosterResolver.getPoster("Solaris")
            )
        )
    )
    val libraryMovies: StateFlow<List<MediaItemDto>> = _libraryMovies.asStateFlow()

    private val _cacheVolumes = MutableStateFlow<List<CacheVolumeDto>>(
        listOf(
            CacheVolumeDto(
                id = "vol_stream",
                title = "Video Cache & Stream Buffers",
                description = "Temporary stream segments from recent 4K HEVC playback sessions. Safe to purge anytime.",
                sizeBytes = 3_400_000_000L,
                sizeFormatted = "3.4 GB",
                isSelected = true
            ),
            CacheVolumeDto(
                id = "vol_thumbs",
                title = "Poster & Artwork Thumbnails",
                description = "High-resolution poster previews and actor photography. Automatically re-indexes as you browse.",
                sizeBytes = 1_800_000_000L,
                sizeFormatted = "1.8 GB",
                isSelected = true
            ),
            CacheVolumeDto(
                id = "vol_old_downloads",
                title = "Old Offline Downloads",
                description = "1 fully watched item ready for decommissioning.",
                sizeBytes = 1_400_000_000L,
                sizeFormatted = "1.4 GB",
                isSelected = false,
                hasSubItem = true,
                subItemTitle = "Dune: Part Two",
                subItemDetail = "Watched 100% • Stored 14d ago"
            ),
            CacheVolumeDto(
                id = "vol_subs",
                title = "Subtitle & Audio Track Temp",
                description = "Synchronized '.ass' subtitle fonts, audio normalization waveform cache.",
                sizeBytes = 210_000_000L,
                sizeFormatted = "210 MB",
                isSelected = false
            )
        )
    )
    val cacheVolumes: StateFlow<List<CacheVolumeDto>> = _cacheVolumes.asStateFlow()

    private val _telegramMovies = MutableStateFlow<List<TelegramMediaDto>>(
        listOf(
            TelegramMediaDto(
                id = "tg_apocalypto_2006",
                fileId = "sample_apocalypto_2006",
                fileName = "Apocalypto 2006 BluRay 1080p Maya DD 5.1 x264 MSubs - mk.mkv",
                cleanTitle = "Apocalypto",
                year = "2006",
                quality = "1080p BluRay",
                fileSize = 2834677760L,
                sizeFormatted = "2.64 GB",
                mimeType = "video/x-matroska",
                streamUrl = "/stream/telegram?file_id=sample_apocalypto_2006",
                posterUrl = PosterResolver.getPoster("Apocalypto")
            ),
            TelegramMediaDto(
                id = "tg_ratatouille_2007",
                fileId = "sample_ratatouille_2007",
                fileName = "Ratatouille.2007.1080p.BluRay.x264.Dual.Audio.mp4",
                cleanTitle = "Ratatouille",
                year = "2007",
                quality = "1080p Remux",
                fileSize = 1932735283L,
                sizeFormatted = "1.80 GB",
                mimeType = "video/mp4",
                streamUrl = "/stream/telegram?file_id=sample_ratatouille_2007",
                posterUrl = PosterResolver.getPoster("Ratatouille")
            )
        )
    )
    val telegramMovies: StateFlow<List<TelegramMediaDto>> = _telegramMovies.asStateFlow()

    private val _trendingTorrents = MutableStateFlow<List<TrendingTorrentDto>>(
        listOf(
            TrendingTorrentDto(
                id = "trend_1",
                title = "Dune: Part Two",
                year = "2024",
                genre = "Sci-Fi • Action",
                rating = "8.6",
                quality = "4K UHD",
                size = "5.8 GB",
                seeds = 320,
                magnetUri = "magnet:?xt=urn:btih:dune2part2024&dn=Dune+Part+Two+2024+1080p",
                posterUrl = PosterResolver.getPoster("Dune: Part Two")
            ),
            TrendingTorrentDto(
                id = "trend_2",
                title = "Oppenheimer",
                year = "2023",
                genre = "Biography • Drama",
                rating = "8.9",
                quality = "4K HDR",
                size = "4.2 GB",
                seeds = 240,
                magnetUri = "magnet:?xt=urn:btih:oppenheimer2023&dn=Oppenheimer+2023+1080p",
                posterUrl = PosterResolver.getPoster("Oppenheimer")
            ),
            TrendingTorrentDto(
                id = "trend_3",
                title = "Spider-Man: Across the Spider-Verse",
                year = "2023",
                genre = "Animation • Action",
                rating = "8.7",
                quality = "4K DV",
                size = "3.1 GB",
                seeds = 190,
                magnetUri = "magnet:?xt=urn:btih:spiderverse2023&dn=Spider-Man+Across+the+Spider-Verse",
                posterUrl = PosterResolver.getPoster("Spider-Man: Across the Spider-Verse")
            ),
            TrendingTorrentDto(
                id = "trend_4",
                title = "Interstellar",
                year = "2014",
                genre = "Sci-Fi • Adventure",
                rating = "8.7",
                quality = "IMAX 1080p",
                size = "2.8 GB",
                seeds = 165,
                magnetUri = "magnet:?xt=urn:btih:interstellar2014&dn=Interstellar+2014+1080p",
                posterUrl = PosterResolver.getPoster("Interstellar")
            ),
            TrendingTorrentDto(
                id = "trend_5",
                title = "Deadpool & Wolverine",
                year = "2024",
                genre = "Action • Comedy",
                rating = "7.8",
                quality = "1080p BluRay",
                size = "2.4 GB",
                seeds = 410,
                magnetUri = "magnet:?xt=urn:btih:deadpoolwolverine2024&dn=Deadpool+and+Wolverine",
                posterUrl = PosterResolver.getPoster("Deadpool & Wolverine")
            )
        )
    )
    val trendingTorrents: StateFlow<List<TrendingTorrentDto>> = _trendingTorrents.asStateFlow()

    fun syncLocalDownloadedVideos(context: android.content.Context) {
        repoScope.launch(Dispatchers.IO) {
            val localItems = LocalMediaScanner.scanDownloadedVideos(context)
            if (localItems.isNotEmpty()) {
                val current = _libraryMovies.value.toMutableList()
                val existingIds = current.map { it.id }.toSet()
                val newOnes = localItems.filter { it.id !in existingIds }
                if (newOnes.isNotEmpty()) {
                    _libraryMovies.value = newOnes + current
                }
            }
        }
    }

    fun switchRole(role: UserRole) {
        _session.value = _session.value.copy(role = role)
    }

    fun toggleIncognito() {
        val next = !_session.value.isIncognito
        _session.value = _session.value.copy(isIncognito = next)
    }

    fun toggleCacheVolume(id: String) {
        _cacheVolumes.value = _cacheVolumes.value.map {
            if (it.id == id) it.copy(isSelected = !it.isSelected) else it
        }
    }

    fun cleanSelectedCache() {
        val selectedIds = _cacheVolumes.value.filter { it.isSelected }.map { it.id }.toSet()
        _cacheVolumes.value = _cacheVolumes.value.map {
            if (it.id in selectedIds) it.copy(sizeBytes = 0L, sizeFormatted = "0 B", isSelected = false) else it
        }
    }

    fun scheduleWatchParty(movie: String, timeSlot: String, endpoints: List<String>) {
        val newEvent = ScheduledWatchDto(
            id = "sched_${System.currentTimeMillis()}",
            dateDay = "01",
            dateMonth = "OCT",
            dateDayOfWeek = "TODAY",
            sessionType = "HOUSE SCREENING",
            timeSlot = timeSlot,
            movieTitle = movie,
            hostNote = "Broadcast to ${endpoints.size} endpoints",
            rsvpCount = endpoints.size,
            isRsvpd = true
        )
        _scheduledWatches.value = listOf(newEvent) + _scheduledWatches.value
    }

    fun refreshTelegramCatalog() {
        repoScope.launch(Dispatchers.IO) {
            fetchTelegramCatalog()
        }
    }

    fun refreshTrendingTorrents() {
        repoScope.launch(Dispatchers.IO) {
            fetchTrendingTorrents()
        }
    }

    suspend fun searchUnified(query: String): List<UnifiedSearchResult> {
        val q = query.trim()
        if (q.isBlank()) return emptyList()

        val base = getEffectiveBaseUrl()
        val serverResults = try {
            val encodedQ = java.net.URLEncoder.encode(q, "UTF-8")
            val url = URL("$base/api/search?q=$encodedQ")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 10000
            conn.requestMethod = "GET"
            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().readText()
                val json = gson.fromJson(body, com.google.gson.JsonObject::class.java)
                val arr = json.getAsJsonArray("results")
                val list = mutableListOf<UnifiedSearchResult>()
                if (arr != null) {
                    for (el in arr) {
                        try {
                            val obj = el.asJsonObject
                            val rawStream = obj.get("streamUrl")?.asString ?: ""
                            val fullStream = if (rawStream.startsWith("http")) rawStream else "$base$rawStream"
                            list.add(
                                UnifiedSearchResult(
                                    id = obj.get("id")?.asString ?: "",
                                    title = obj.get("title")?.asString ?: "",
                                    source = obj.get("source")?.asString ?: "TORRENT",
                                    size = obj.get("size")?.asString ?: "",
                                    quality = obj.get("quality")?.asString ?: "1080p",
                                    year = obj.get("year")?.asString ?: "",
                                    posterUrl = obj.get("posterUrl")?.asString ?: "",
                                    streamUrl = fullStream,
                                    magnetUri = obj.get("magnetUri")?.asString ?: "",
                                    fileId = obj.get("fileId")?.asString ?: "",
                                    isLocal = obj.get("isLocal")?.asBoolean ?: false,
                                    seeds = obj.get("seeds")?.asInt ?: 0
                                )
                            )
                        } catch (_: Exception) {}
                    }
                }
                list
            } else emptyList()
        } catch (_: Exception) {
            emptyList()
        }

        if (serverResults.isNotEmpty()) return serverResults

        // Fallback: search locally cached data
        val fallback = mutableListOf<UnifiedSearchResult>()
        _libraryMovies.value.filter { it.title.contains(q, true) }.forEach {
            fallback.add(
                UnifiedSearchResult(
                    id = it.id,
                    title = it.title,
                    source = "SANDBOX",
                    size = it.fileSizeFormatted,
                    quality = it.formatBadge,
                    year = it.year,
                    posterUrl = it.posterUrl,
                    streamUrl = it.streamUrl,
                    isLocal = true
                )
            )
        }
        _telegramMovies.value.filter { it.cleanTitle.contains(q, true) || it.fileName.contains(q, true) }.forEach {
            val fullStream = if (it.streamUrl.startsWith("http")) it.streamUrl else "$base${it.streamUrl}"
            fallback.add(
                UnifiedSearchResult(
                    id = it.id,
                    title = it.cleanTitle,
                    source = "TELEGRAM",
                    size = it.sizeFormatted,
                    quality = it.quality,
                    year = it.year,
                    posterUrl = it.posterUrl,
                    streamUrl = fullStream,
                    fileId = it.fileId,
                    isLocal = false
                )
            )
        }
        _trendingTorrents.value.filter { it.title.contains(q, true) || it.genre.contains(q, true) }.forEach {
            val encMag = try { java.net.URLEncoder.encode(it.magnetUri, "UTF-8") } catch (_: Exception) { it.magnetUri }
            val encTit = try { java.net.URLEncoder.encode(it.title, "UTF-8") } catch (_: Exception) { it.title }
            fallback.add(
                UnifiedSearchResult(
                    id = it.id,
                    title = it.title,
                    source = "TORRENT",
                    size = it.size,
                    quality = it.quality,
                    year = it.year,
                    posterUrl = it.posterUrl,
                    streamUrl = "$base/stream/torrent?magnet=$encMag&title=$encTit",
                    magnetUri = it.magnetUri,
                    seeds = it.seeds,
                    isLocal = false
                )
            )
        }
        return fallback
    }

    fun downloadTorrentToServer(magnetUri: String, title: String, onDone: (Boolean, String) -> Unit) {
        repoScope.launch(Dispatchers.IO) {
            try {
                val base = getEffectiveBaseUrl()
                val encMag = java.net.URLEncoder.encode(magnetUri, "UTF-8")
                val encTit = java.net.URLEncoder.encode(title, "UTF-8")
                val url = URL("$base/api/torrent/download?magnet=$encMag&title=$encTit")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 6000
                conn.readTimeout = 8000
                conn.requestMethod = "POST"
                val code = conn.responseCode
                if (code == 200) {
                    onDone(true, "Torrent queued on PocketNode server! 🚀")
                } else {
                    onDone(false, "Server returned HTTP $code")
                }
            } catch (e: Exception) {
                onDone(false, "Connection failed: ${e.message}")
            }
        }
    }

    fun downloadTelegramToServer(fileId: String, fileName: String, fileSize: Long = 0L, onDone: (Boolean, String) -> Unit) {
        repoScope.launch(Dispatchers.IO) {
            try {
                val base = getEffectiveBaseUrl()
                val encId = java.net.URLEncoder.encode(fileId, "UTF-8")
                val encName = java.net.URLEncoder.encode(fileName, "UTF-8")
                val url = URL("$base/api/telegram/download?file_id=$encId&name=$encName&size=$fileSize")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 6000
                conn.readTimeout = 8000
                conn.requestMethod = "POST"
                val code = conn.responseCode
                if (code == 200) {
                    onDone(true, "Downloading to PocketNode server storage! 📥")
                } else {
                    onDone(false, "Server returned HTTP $code")
                }
            } catch (e: Exception) {
                onDone(false, "Connection failed: ${e.message}")
            }
        }
    }
}
