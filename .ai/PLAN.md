# PLAN. Format: [ ] id | task | done when.  Expand a milestone into tasks ONLY when you reach it (use the "Expand" prompt).

## Product context
Android device = personal cloud + streaming host (one-click on). Clients: streaming app (Netflix-like), drive app (Mega-like), web PWA. Auto LAN/remote switching, per-user progress, watch party, torrent/Telegram/other sources, per-item public/private, download to client or to host. Streams to Jellyfin/VLC/DLNA/WebDAV clients too.

## Spikes (throwaway code in /spikes or branch spike/*; time-boxed; record the outcome in DECISIONS.md, then stop)
[x] S1 | Relay tunnel: host dials out to a tiny relay on a VPS (Python asyncio/FastAPI unless there is a reason not to); phone on mobile data reaches host GET /ping through it | curl via relay returns host /ping
[x] S2 | Torrent range streaming: libtorrent4j sequential + piece deadlines behind an HTTP range endpoint, played in ExoPlayer | first frame in ~15s on a healthy torrent, seek works
[x] S3 | Background survival: foreground-service host stays reachable 24h on the real device (manual test) | log of uptime + the battery settings that worked
[x] S4 | Clock sync: two clients over WebSocket, play/pause/seek | drift measured, ~300 ms or better

## M0 Restructure
[x] M0.1 | Fill ARCH.md module map from the code | modules + entry points listed
[x] M0.2 | Smoke tests recording current Jellyfin/qBittorrent API behavior | tests pass on current code
[x] M0.3 | Split into modules: core, network, storage, media, adapters, sources, engine-torrent, telegram (move only, no behavior change) | app builds, M0.2 passes
  [x] M0.3a | Create Gradle submodule skeletons: :core, :network, :storage, :media, :adapters, :sources, :engine-torrent, :telegram; update settings.gradle.kts | project syncs
  [x] M0.3b | Move core/ classes → :core module; update imports in dependents | compiles
  [x] M0.3c | Move services/ → :adapters + :network; move download/ → :engine-torrent + :telegram + :sources; move remaining core subsets | compiles, M0.2 passes
[x] M0.4 | API versioning + /capabilities endpoint | returns version + feature flags

## M1 Users and pairing
[x] M1.1 users + password hashing + roles (admin/user/guest)
[x] M1.2 per-device tokens (issue/list/revoke)
[x] M1.3 QR pairing (server id, one-time token, endpoints)
[x] M1.4 watch_state(user, item, position, duration, played, updated_at) table + API

## Later (expand when reached)
## M2 Storage layer + drive API
[x] M2.1 | Resumable upload endpoint (POST /api/drive/upload, chunked, resume token) | file lands in StorageManager base dir
[x] M2.2 | Share links (POST /api/drive/share → token; GET /s/{token} → file download) | public link serves file without auth
[x] M2.3 | Thumbnails (GET /api/drive/thumb?path=… → JPEG; backed by MediaThumbnailManager) | returns JPEG for image+video files
[x] M2.4 | WebDAV adapter (PROPFIND / GET / PUT / DELETE on /webdav/) | maps to StorageManager; mounts in Windows/macOS Finder
## M3 Network manager (LAN auto-switch, relay, later P2P hole punching)
[x] M3.1 | NetworkManager.kt in :network — detect LAN IP (wlan0/eth0 via NetworkInterface), expose getLocalIp() + isReachable(host,port) | unit-testable pure functions, no Android service deps
[x] M3.2 | Relay client stub in :network — RelayClient.kt: dial relay WS (URL from prefs), register as host, forward /ping heartbeat; disabled by default | relay URL configurable; seeding off by default
[x] M3.3 | URL resolver in :core — ConnectionResolver.kt: given peer server_id, return best reachable URL (LAN first, relay fallback); cache TTL 30s | returns LAN URL when on same subnet
[x] M3.4 | Expose network info via API — GET /api/network/status (local_ip, relay_connected, relay_url) | returns JSON; relay_connected false when stub disabled
## M4 Streaming app v1 (library, custom player, progress, downloads, public/private)
[x] M4.1 | Item visibility store in :core — MediaVisibilityManager.kt: per-item public/private flag (default private); set/get/filter | unit-testable, items default to private
[x] M4.2 | Host media library API — GET /api/media/library with visibility filter (guest gets public only; user gets all) | returns JSON with id, title, size, mime, is_public
[x] M4.3 | Watch progress sync API — POST /api/watch/progress (user, item_id, position_ms, duration_ms) | updates WatchStateManager SQLite
[ ] M4.4 | Media direct stream route — GET /api/media/stream/{id} with HTTP Range support | serves 206 Partial Content or 200 OK for ExoPlayer
M5 Torrent streaming + source plugins (Torznab, Telegram, Internet Archive, TMDB)
M6 Watch party (host-authoritative WebSocket) + web PWA with guest links
M7 Drive app, hub federation, health dashboard
M8 Docs, demo video, CI, security notes, tagged release

