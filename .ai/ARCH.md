# ARCH (PocketNode Module & Architecture Map)

Verified against codebase post-M0 through M7 implementation.

## Project Structure
- 100% Kotlin, Gradle multi-module Android project (minSdk 26, targetSdk 35).
- Server App: `remote-movie-pc-downloader/`
- Client App: `pocketnode-client/` (Jetpack Compose, ExoPlayer)
- Static Website: `pocketnode-website/`
- Validated Spikes: `spikes/` (S1 relay, S2 torrent-stream, S3 uptime, S4 clock-sync)

---

## Server Submodules (:app + 8 library modules)

| Module | Namespace | Purpose & Key Classes |
|---|---|---|
| `:app` | `com.remotemedia` | Main UI host, `MainActivity`, `ServerForegroundService`, `BootReceiver` |
| `:core` | `com.remotemedia.core` | Auth (`UserManager`), Tokens (`TokenManager`), Pairing (`PairingManager`), Visibility (`MediaVisibilityManager`), URL resolution (`ConnectionResolver`), Metrics (`SystemMetricsManager`), Bridge (`TorrentStatusBridge`), `Logger` |
| `:storage` | `com.remotemedia.storage` | Storage isolation (`StorageManager`), SQLite watch history (`WatchStateManager`), Resumable chunk upload (`DriveUploadManager`), Public links (`ShareLinkManager`) |
| `:network` | `com.remotemedia.network` | Ktor CIO HTTP/WS server (`WebHttpServer`), LAN IP & reachability (`NetworkManager`), Relay stub (`RelayClient`), Watch party sync (`WatchPartyHub`), LAN discovery (`HubFederationManager`) |
| `:media` | `com.remotemedia.media` | Media catalog (`HostMediaManager`), Federation (`FederatedMediaManager`), Thumbnails (`MediaThumbnailManager`), `YtDlpManager` |
| `:adapters` | `com.remotemedia.adapters` | Jellyfin REST adapter (`JellyfinServer` :8096), DLNA/UPnP (`DlnaServer`), `JellyfinWebUiBuilder`, `JellyfinManager` |
| `:sources` | `com.remotemedia.sources` | Torznab/Jackett (`TorznabClient`, `JackettServer` :9117), Internet Archive (`ArchiveOrgSource`), TMDB resolver (`TmdbClient`) |
| `:engine-torrent` | `com.remotemedia.enginetorrent` | Libtorrent4j engine (`TorrentEngine`), Sequential streaming (`TorrentStreamManager`), `TorrentTrackersManager` |
| `:telegram` | `com.remotemedia.telegram` | Telegram bot & catalog (`TelegramBotEngine`, `TdlibDownloadEngine`, `TelegramMediaCatalog`, `TelegramLocalServer`) |

---

## Port Allocation

| Port | Protocol / Service | Handled By |
|---|---|---|
| `:8080` | HTTP / WebSocket (Core REST, Drive, WebDAV, Watch Party, qBit v2) | `network/WebHttpServer.kt` |
| `:8096` | HTTP (Jellyfin-compatible REST + streaming) | `adapters/JellyfinServer.kt` |
| `:9117` | HTTP (Torznab/Jackett search proxy) | `sources/JackettServer.kt` |
| `:8998` | UDP (Federation LAN Discovery) | `network/HubFederationManager.kt` |
| `:6881` | TCP/UDP (BitTorrent swarm & DHT) | `engine-torrent/TorrentEngine.kt` |

---

## API Endpoints Map (WebHttpServer on :8080)

### 1. System & Capabilities
- `GET /ping` → `{"status":"ok","time":...}`
- `GET /capabilities` → API version (`1.0.0`) & feature flags
- `GET /api/health/metrics` → CPU%, temp, RAM, battery, free/total storage

### 2. Authentication & Users
- `POST /api/auth/login` → issues 32-byte session token
- `POST /api/auth/logout` → revokes session token
- `GET /api/users` → list users & roles (admin/user/guest)
- `GET /api/tokens` → list active session tokens
- `POST /api/tokens/revoke` → revoke specific token

### 3. Pairing
- `GET /pair/offer` → generates 5-minute single-use OTP & server ID
- `POST /pair/redeem` → validates OTP and pairs client

### 4. Media & Streaming
- `GET /api/media/library` → media catalog (guest gets public only; auth user gets all)
- `POST /api/media/visibility` → set per-item public/private flag (default private)
- `GET /api/media/stream/{id}` → direct media stream with HTTP 206 Range support
- `POST /api/watch/state` → upsert watch progress (user, item, sec, played)
- `GET /api/watch/state` → retrieve watch progress by user/item
- `POST /api/watch/progress` → sync ExoPlayer/client ms progress to SQLite

### 5. Drive & File Storage
- `POST /api/drive/upload/init` → create resumable chunk upload session
- `POST /api/drive/upload/chunk` → upload file chunk with byte offset
- `POST /api/drive/upload/complete` → finalize upload into storage directory
- `POST /api/drive/upload/cancel` → abort upload session
- `GET /api/drive/upload/status` → query upload progress
- `POST /api/drive/share` → create temporary public download link token
- `GET /s/{token}` → unauthenticated direct download of shared file
- `GET /api/drive/thumb` → JPEG thumbnail extraction via MediaThumbnailManager
- `POST /api/drive/file/delete` → delete file/folder
- `POST /api/drive/rename` → rename file
- `POST /api/drive/mkdir` → create directory

### 6. WebDAV Adapter
- `PROPFIND /webdav/{...}` → XML Multi-Status directory/file listing
- `GET /webdav/{...}` → download file
- `PUT /webdav/{...}` → write/upload file
- `DELETE /webdav/{...}` → delete file
- `OPTIONS /webdav/{...}` → DAV 1 compliance headers

### 7. Torrent Engine & Sources
- `GET /api/torrent/stream/status` → readyToPlay, swarm metadata, speeds
- `GET /api/torrent/seeding` → query seeding status (default false)
- `POST /api/torrent/seeding` → toggle seeding on/off
- `GET /api/v2/torrents/info` → qBittorrent compatibility listing
- `GET /api/v2/transfer/info` → transfer speeds

### 8. Watch Party
- `GET /ws/party?party_id=...` → WebSocket clock sync (<300ms drift) & state broadcast
- `POST /api/party/invite` → create guest invite token & shareable URL
- `GET /api/party/invite` → query invite metadata
- `GET /party/{token}` → responsive HTML5/JS watch party PWA player

### 9. Hub Federation & Network
- `GET /api/network/status` → local LAN IP, relay connection state, relay URL
- `GET /api/federation/peers` → list discovered PocketNode peers on LAN
- `GET /api/federation/library` → aggregated catalog tagged by origin node
