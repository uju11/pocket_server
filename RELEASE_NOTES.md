# PocketNode v1.0.0 Release Notes

PocketNode transforms any Android device into an autonomous, battery-friendly personal cloud and streaming server with one-click startup, instant QR pairing, and client applications.

---

## Highlights & Milestone Summary

### Spikes (S1 – S4)
- **S1 (Relay Tunnel)**: VPS WebSocket relay fallback architecture tested and validated.
- **S2 (Torrent Stream)**: Libtorrent4j sequential piece delivery validated in under 15 seconds to first frame.
- **S3 (Background Survival)**: Foreground service lock architecture established (`PARTIAL_WAKE_LOCK` + high-performance WiFi + multicast lock) with 5-minute heartbeat logging.
- **S4 (Clock Synchronization)**: NTP-lite WebSocket protocol achieves sub-millisecond LAN drift and <30ms remote drift.

### Core Architecture & Restructuring (M0)
- Clean split into 9 modular Android Gradle libraries:
  `:app`, `:core`, `:network`, `:storage`, `:media`, `:adapters`, `:sources`, `:engine-torrent`, `:telegram`.
- Ktor CIO embedded HTTP/WebSocket server on `:8080` and Jellyfin REST adapter on `:8096`.
- Verified API versioning (`1.0.0`) and `/capabilities` endpoint.

### Identity & Pairing (M1)
- Salted SHA-256 password hashing with role-based access (admin, user, guest).
- Cryptographic 32-byte session tokens with instant revocation.
- Single-use 5-minute QR pairing offer/redeem protocol with persistent server UUID.
- SQLite-backed per-user watch state tracking (`watch_state.db`).

### Storage & Drive API (M2)
- Resumable chunked upload engine supporting 2MB chunks, pause, resume, and cancellation.
- Time-limited, revocable public share links (`/s/{token}`) for no-auth file sharing.
- High-performance JPEG video thumbnail extraction and caching via `MediaThumbnailManager`.
- Complete WebDAV adapter on `/webdav/` (`PROPFIND`, `GET`, `PUT`, `DELETE`, `OPTIONS`) compatible with Windows Explorer and macOS Finder.

### Network Manager (M3)
- Real-time LAN IP discovery across WiFi (`wlan0`), Ethernet (`eth0`), and mobile interfaces.
- Subnet analysis and TCP reachability probing.
- `ConnectionResolver`: automatic LAN-first switching with relay fallback and 30-second TTL caching.
- Relay client stub (disabled by default) and `/api/network/status` API.

### Streaming App v1 (M4)
- Unified media library endpoint (`/api/media/library`) with visibility filtering.
- Default-private media visibility (`MediaVisibilityManager`) with toggle endpoint (`/api/media/visibility`).
- Millisecond-precision watch progress synchronization (`/api/watch/progress`).
- Byte-range HTTP 206 streaming (`/api/media/stream/{id}`) for ExoPlayer and mobile clients.

### Torrent Streaming & Source Plugins (M5)
- BitTorrent streaming engine with seeding explicitly disabled by default for battery and data safety.
- `TorrentStatusBridge` providing decoupled stream and seeding observability.
- `TorznabClient`: robust RSS/XML parser for Jackett and Torznab indexers.
- `ArchiveOrgSource`: direct MP4 streaming search integration with the Internet Archive.
- `TmdbClient`: movie and show metadata resolution with local title/year regex fallback.

### Watch Party & Web PWA (M6)
- Host-authoritative WebSocket hub (`/ws/party`) implementing the S4 clock sync protocol.
- Guest invite token generation (`/api/party/invite`) for temporary, account-free room access.
- Responsive HTML5/JS Web PWA watch party player (`/party/{token}`) with real-time playback synchronization.

### Hub Federation & Drive Management (M7)
- LAN discovery (`HubFederationManager`) via UDP announcements and subnet tracking.
- Aggregated federated catalog (`/api/federation/library`) tagging items by origin node.
- Real-time system health metrics API (`/api/health/metrics`): CPU, thermal sensors, battery, memory, and storage.
- File management endpoints: `/api/drive/file/delete`, `/api/drive/rename`, `/api/drive/mkdir`.

### Quality & Governance (M8)
- Security architecture documentation (`docs/SECURITY.md`).
- Exhaustive architecture and API map (`.ai/ARCH.md`).
- Continuous integration workflow (`.github/workflows/ci.yml`).
