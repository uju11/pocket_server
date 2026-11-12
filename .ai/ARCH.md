# ARCH (module map — verified 2026-11-12 against actual file tree)
Facts verified:
- 100% Kotlin; single Android Gradle module; minSdk 26, targetSdk 35
- HTTP server: NanoHTTPD (WebHttpServer.kt) — NOT Ktor
- Jellyfin-compatible REST on :8096 (JellyfinServer.kt + JellyfinWebUiBuilder.kt)
- DLNA/UPnP: DlnaServer.kt (NanoHTTPD-based SSDP + description XML)
- Telegram: bot.py (Python, long-poll) + TelegramBotEngine.kt / TdlibDownloadEngine.kt
- Torrent: TorrentEngine.kt + TorrentStreamManager.kt (libtorrent4j, sequential mode)
- Jackett API proxy: JackettServer.kt (:9117 Torznab)
- Storage/media: StorageManager.kt (scan, CRUD, share paths)
- Network: NetworkShareManager.kt (Samba/SMB-lite), MeshShareManager.kt (WIP)
- Background service: ServerForegroundService.kt (PARTIAL_WAKE_LOCK + wifiLock + multicast)
- S3 instrumentation: UptimeLogger.kt (5-min heartbeat → files/uptime_log.txt)

## Module map — SERVER (remote-movie-pc-downloader/)
| Path (under app/src/main/java/com/remotemedia/) | Purpose |
|---|---|
| **Entry** | |
| MainActivity.kt | Compose UI host, permission launcher, starts service |
| ServerForegroundService.kt | Foreground service; calls startAllServers / stopServer |
| receivers/BootReceiver.kt | Auto-start on device boot |
| **core/** | |
| StorageManager.kt | File library scan, media CRUD, shared-paths registry |
| HostMediaManager.kt | Aggregates local + federated media lists |
| FederatedMediaManager.kt | Hub-mode federation (WIP) |
| PlaybackProgressManager.kt | Per-user watch-state store (in-memory) |
| NetworkShareManager.kt | Samba-like LAN share |
| MeshShareManager.kt | Device-to-device mesh share (WIP) |
| SystemMetricsManager.kt | CPU/mem/battery/uptime stats |
| GpuComputeManager.kt | GPU capability probe |
| MediaThumbnailManager.kt | Thumbnail generation/cache |
| UptimeLogger.kt | 5-min heartbeat → files/uptime_log.txt (S3) |
| PermissionsManager.kt | Runtime permission helpers |
| Logger.kt | In-app log ring buffer |
| **services/** | |
| WebHttpServer.kt | NanoHTTPD core HTTP server (port 8080) |
| JellyfinServer.kt | Jellyfin REST adapter (:8096, byte-range streaming) |
| JellyfinWebUiBuilder.kt | Embedded Jellyfin web-UI HTML builder |
| JellyfinManager.kt | Jellyfin config & state singleton |
| JellyfinClusterManager.kt | Multi-device Jellyfin cluster (WIP) |
| JellyfinDiscoveryResponder.kt | mDNS / UDP discovery responder |
| DlnaServer.kt | DLNA/UPnP SSDP broadcaster + description endpoint |
| JackettServer.kt | Torznab/Jackett API (:9117) |
| TelegramLocalServer.kt | Telegram TDLib local server wrapper |
| **download/** | |
| TorrentEngine.kt | libtorrent4j session, add/remove/prioritise |
| TorrentStreamManager.kt | Sequential range-streaming (setPieceDeadline) |
| TorrentTrackersManager.kt | Tracker list management |
| TelegramBotEngine.kt | Bot API long-poll download |
| TdlibDownloadEngine.kt | TDLib 2GB+ file download |
| TelegramMediaCatalog.kt | Telegram channel media index |
| YtDlpManager.kt | yt-dlp subprocess wrapper |
| **ui/** | Jetpack Compose server-app screens (GeneralTab, JellyfinTab, TorrentTab, Telegram, Terminal, Settings, Onboarding, …) |
| **bot.py** | Python Telegram bot (long-poll, admin whitelist) — lives at repo root of server module |

## Module map — CLIENT (pocketnode-client/)
| Path (under app/src/main/java/com/pocketnode/client/) | Purpose |
|---|---|
| MainActivity.kt | Compose NavHost entry |
| ui/MainClientScreen.kt | Tab shell (bottom nav) |
| ui/screens/WatchTabScreen.kt | Watch / library browser |
| ui/screens/LibraryTabScreen.kt | Local library list |
| ui/screens/TransfersTabScreen.kt | Active downloads / transfers |
| ui/screens/TorrentStreamScreen.kt | Torrent streaming UI (ExoPlayer) |
| ui/screens/TelegramStreamScreen.kt | Telegram stream UI |
| ui/screens/PlayerScreen.kt | Full-screen ExoPlayer player |
| ui/screens/SettingsTabScreen.kt | App settings |
| ui/screens/MediaScannerScreen.kt | Local media scan results |
| ui/screens/ScheduleWatchModal.kt | Watch-party scheduling modal |
| ui/screens/StorageDiagnosticsModal.kt | Storage debug info modal |
| ui/components/TacticalComponents.kt | Shared Compose components |
| data/ClientModels.kt | DTOs / data models |
| data/ClientRepository.kt | Server API calls (HTTP) |
| data/LocalMediaScanner.kt | On-device media scan |
| data/PosterResolver.kt | Poster image URL resolution |

## Other modules
| Path | Purpose |
|---|---|
| pocketnode-website/ | Static marketing site (HTML/CSS) |
| spikes/ | Throwaway spike code (S1 relay, S2 torrent-stream, S3 uptime, S4 clock-sync) |

## Entry points
- **Server**: `ServerForegroundService.startAllServers()` → WebHttpServer + JellyfinServer + DlnaServer + JackettServer + (Telegram)
- **Client**: `MainActivity` → Compose `NavHost` via `MainClientScreen`
- **Boot**: `BootReceiver` → starts `ServerForegroundService`

