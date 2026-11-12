# ARCH (module map: path -> one-line purpose; mark exists / missing)
Seed facts (verify against code, then correct):
- 100% Kotlin; Ktor (CIO) embedded server; Android foreground service; minSdk 26, target 35
- libtorrent4j is the only download engine
- Jellyfin REST re-implementation on :8096 (mDNS discovery, byte-range streaming, embedded web UI)
- qBittorrent Web API v2 compatibility layer; DLNA/UPnP broadcaster
- Telegram bot (Bot API long-polling + TDLib for 2GB+ files), admin-ID whitelist
- Jackett/Torznab server on :9117; client app = Jetpack Compose + ExoPlayer
- WIP: hub mode, torrent streaming, tunnels, device-folder share. Host-folder share works.

## Module map (exists unless marked [missing])

### remote-movie-pc-downloader/ — SERVER app
| Path | Purpose |
|------|---------|
| app/src/main/java/com/remotemedia/MainActivity.kt | App entry, permission launcher, starts foreground service |
| app/src/main/java/com/remotemedia/ServerForegroundService.kt | Foreground service; starts/stops Ktor server |
| app/src/main/java/com/remotemedia/core/StorageManager.kt | File/library scan, media paths |
| app/src/main/java/com/remotemedia/core/Logger.kt | In-app log ring buffer |
| app/src/main/java/com/remotemedia/core/NetworkManager.kt | LAN IP detection, mDNS helpers |
| app/src/main/java/com/remotemedia/core/MediaTypeUtils.kt | MIME/extension helpers |
| app/src/main/java/com/remotemedia/core/PlaybackState.kt | In-memory playback position store |
| app/src/main/java/com/remotemedia/core/PermissionsHelper.kt | Runtime permission wrappers |
| app/src/main/java/com/remotemedia/core/SystemInfo.kt | CPU/mem/battery stats |
| app/src/main/java/com/remotemedia/core/FederationManager.kt | Hub-mode federation (WIP) |
| app/src/main/java/com/remotemedia/core/GpuCompat.kt | GPU capability probe |
| app/src/main/java/com/remotemedia/core/HostMetrics.kt | Uptime/request counters |
| app/src/main/java/com/remotemedia/core/MeshShare.kt | Device-folder mesh share (WIP) |
| app/src/main/java/com/remotemedia/download/TorrentManager.kt | libtorrent4j session, add/remove torrents |
| app/src/main/java/com/remotemedia/download/TelegramDownloader.kt | Bot API + TDLib download |
| app/src/main/java/com/remotemedia/download/TelegramConfig.kt | Telegram credentials store |
| app/src/main/java/com/remotemedia/download/YtDlpWrapper.kt | yt-dlp subprocess wrapper |
| app/src/main/java/com/remotemedia/services/JellyfinService.kt | Jellyfin REST adapter (:8096) |
| app/src/main/java/com/remotemedia/services/JellyfinRoutes*.kt | Jellyfin route groups |
| app/src/main/java/com/remotemedia/services/WebDAVService.kt | WebDAV adapter |
| app/src/main/java/com/remotemedia/services/TelegramService.kt | Telegram bot long-poll loop |
| app/src/main/java/com/remotemedia/services/DlnaService.kt | DLNA/UPnP broadcaster |
| app/src/main/java/com/remotemedia/services/JackettService*.kt | Torznab/Jackett (:9117) |
| app/src/main/java/com/remotemedia/ui/ | Jetpack Compose server UI screens |
| app/src/main/java/com/remotemedia/receivers/ | BroadcastReceivers (boot, etc.) |

### pocketnode-client/ — CLIENT app [exists]
| Path | Purpose |
|------|---------|
| app/src/main/java/com/pocketnode/client/MainActivity.kt | Client entry point |
| app/src/main/java/com/pocketnode/client/ui/MainClientScreen.kt | Nav host / tab shell |
| app/src/main/java/com/pocketnode/client/ui/screens/ | Per-tab screens (Transfers, Storage, …) |

### pocketnode-website/ — static marketing site [exists]
### spikes/ — throwaway spike code [created, empty]

## Entry points
- Server: ServerForegroundService → starts Ktor CIO engine
- Client: MainActivity → Compose NavHost
