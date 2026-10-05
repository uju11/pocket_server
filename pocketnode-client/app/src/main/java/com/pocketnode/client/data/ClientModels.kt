package com.pocketnode.client.data

enum class UserRole(val label: String, val badge: String, val description: String) {
    ADMIN("ADMIN", "ADMIN", "Full server & client administration"),
    GUEST("GUEST", "GUEST", "Standard household streaming & downloads"),
    KIDS("KIDS", "KIDS_SAFE", "Whitelisted family-friendly media only")
}

data class ClientSession(
    val role: UserRole = UserRole.ADMIN,
    val username: String = "Host Admin",
    val serverHost: String = "192.168.1.50",
    val serverPort: Int = 8080,
    val jellyfinPort: Int = 8097,
    val isConnected: Boolean = true,
    val latencyMs: Int = 4,
    val nodeName: String = "HOMELAB-01",
    val isIncognito: Boolean = false
)

data class MediaItemDto(
    val id: String,
    val title: String,
    val year: String = "2024",
    val category: String = "Movies", // Movies, Series, Music, Images
    val formatTag: String = "4K UHD • HEVC • DTS-HD 7.1",
    val formatBadge: String = "4K REMUX",
    val sizeBytes: Long = 18_400_000_000L,
    val sizeFormatted: String = "18.4 GB",
    val progressPercent: Float = 0f,
    val progressStr: String = "Unplayed",
    val durationStr: String = "2h 45m",
    val seedOrigin: String = "Seeded from Local Node",
    val posterUrl: String = "",
    val streamUrl: String = "",
    val bitstreamCeiling: String = "92 Mbps direct-play",
    val positionMs: Long = 0L
)

data class ActiveWatchPartyDto(
    val roomId: String = "ASLR-90",
    val movieTitle: String = "SOLARIS",
    val movieYear: String = "1972",
    val director: String = "Andrei Tarkovsky",
    val duration: String = "2h 47m",
    val viewerCount: Int = 3,
    val additionalViewers: Int = 3,
    val isWalkieTalkieActive: Boolean = true,
    val syncedTime: String = "00:44:12",
    val posterUrl: String = ""
)

data class LanViewerFeedDto(
    val id: String,
    val userName: String,
    val deviceTarget: String, // e.g. "LIVING ROOM TV", "STUDIO MAC"
    val deviceIcon: String, // TV or Laptop icon
    val movieTitle: String,
    val streamType: String, // e.g. "DIRECT STREAM // 4K REMUX - 00:38:10"
    val statusMsg: String? = null,
    val canMirror: Boolean = true,
    val canSyncAudio: Boolean = false
)

data class ScheduledWatchDto(
    val id: String,
    val dateDay: String = "03",
    val dateMonth: String = "OCT",
    val dateDayOfWeek: String = "FRI",
    val sessionType: String = "HOUSE SCREENING",
    val timeSlot: String = "20:30 CST",
    val movieTitle: String = "INTERSTELLAR (IMAX EDITI...)",
    val hostNote: String = "Host: Alex • Projector Left Setup",
    val rsvpCount: Int = 4,
    val isRsvpd: Boolean = true
)

data class HouseholdPickDto(
    val id: String,
    val recommenderName: String = "Alex",
    val timestamp: String = "YESTERDAY",
    val movieTitle: String = "THE ZONE OF INTEREST",
    val movieYear: String = "2023",
    val metadataLine: String = "JONATHAN GLAZER • 1H 45M • 4K HDR",
    val quoteReview: String = "\"Masterclass in sound design. Watch with headphones or the studio system only.\"",
    val posterUrl: String = ""
)

data class CacheVolumeDto(
    val id: String,
    val title: String,
    val description: String,
    val sizeBytes: Long,
    val sizeFormatted: String,
    val isSelected: Boolean = true,
    val hasSubItem: Boolean = false,
    val subItemTitle: String = "",
    val subItemDetail: String = ""
)

data class DownloadItem(
    val id: String,
    val title: String,
    val sizeFormatted: String,
    val progress: Float,
    val speedFormatted: String,
    val status: String,
    val targetDevice: String,
    val localFilePath: String = ""
)

data class RemoteEndpoint(
    val id: String,
    val name: String,
    val ip: String,
    val isOnline: Boolean
)

data class MediaItem(
    val id: String,
    val title: String,
    val year: String = "2024",
    val category: String = "Movies",
    val formatTag: String = "4K UHD • HEVC • DTS-HD 7.1",
    val formatBadge: String = "4K REMUX",
    val sizeBytes: Long = 18_400_000_000L,
    val sizeFormatted: String = "18.4 GB",
    val progressPercent: Float = 0f,
    val progressStr: String = "Unplayed",
    val durationStr: String = "2h 45m",
    val streamUrl: String = "",
    val posterUrl: String = ""
)

enum class NavigationTab(val label: String, val badge: String = "") {
    WATCH("WATCH"),
    LIBRARY("LIBRARY"),
    TORRENTS("TORRENTS"),
    TELEGRAM("TELEGRAM"),
    SCANNER("SCANNER"),
    TRANSFERS("TRANSFERS"),
    SETTINGS("SETTINGS")
}

data class TelegramMediaDto(
    val id: String,
    val fileId: String,
    val fileName: String,
    val cleanTitle: String,
    val year: String = "",
    val quality: String = "1080p",
    val fileSize: Long = 0L,
    val sizeFormatted: String = "",
    val mimeType: String = "video/mp4",
    val dateReceived: Long = System.currentTimeMillis(),
    val streamUrl: String = "",
    val posterUrl: String = "",
    val source: String = "Telegram",
    val channelName: String = ""
)

data class TrendingTorrentDto(
    val id: String,
    val title: String,
    val year: String = "",
    val genre: String = "",
    val rating: String = "8.5",
    val quality: String = "1080p",
    val size: String = "2.1 GB",
    val seeds: Int = 100,
    val magnetUri: String = "",
    val posterUrl: String = ""
)

data class UnifiedSearchResult(
    val id: String,
    val title: String,
    val source: String, // "TORRENT", "TELEGRAM", "SANDBOX"
    val size: String = "",
    val quality: String = "1080p",
    val year: String = "",
    val posterUrl: String = "",
    val streamUrl: String = "",
    val magnetUri: String = "",
    val fileId: String = "",
    val isLocal: Boolean = false,
    val seeds: Int = 0
)


// ── Metadata Scanner Models ───────────────────────────────────────────────

enum class ScanStatus { IDLE, SCANNING, DONE, ERROR }

data class ScanLogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val message: String,
    val isError: Boolean = false
)

data class ScanState(
    val status: ScanStatus = ScanStatus.IDLE,
    val scannedCount: Int = 0,
    val newFoundCount: Int = 0,
    val lastScanTs: Long = 0L,
    val progressPercent: Float = 0f,
    val currentPath: String = "",
    val log: List<ScanLogEntry> = emptyList(),
    val scheduledIntervalHours: Int = 0  // 0 = disabled
)
