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
[ ] M0.3 | Split into modules: core, network, storage, media, adapters, sources, engine-torrent, telegram (move only, no behavior change) | app builds, M0.2 passes
  [ ] M0.3a | Create Gradle submodule skeletons: :core, :network, :storage, :media, :adapters, :sources, :engine-torrent, :telegram; update settings.gradle.kts | project syncs
  [ ] M0.3b | Move core/ classes → :core module; update imports in dependents | compiles
  [ ] M0.3c | Move services/ → :adapters + :network; move download/ → :engine-torrent + :telegram + :sources; move remaining core subsets | compiles, M0.2 passes
[x] M0.4 | API versioning + /capabilities endpoint | returns version + feature flags

## M1 Users and pairing
[ ] M1.1 users + password hashing + roles (admin/user/guest)
[ ] M1.2 per-device tokens (issue/list/revoke)
[ ] M1.3 QR pairing (server id, one-time token, endpoints)
[ ] M1.4 watch_state(user, item, position, duration, played, updated_at) table + API

## Later (expand when reached)
M2 Storage layer + drive API (resumable upload, share links, thumbnails, WebDAV)
M3 Network manager (LAN auto-switch, relay, later P2P hole punching)
M4 Streaming app v1 (library, custom player, progress, downloads, public/private)
M5 Torrent streaming + source plugins (Torznab, Telegram, Internet Archive, TMDB)
M6 Watch party (host-authoritative WebSocket) + web PWA with guest links
M7 Drive app, hub federation, health dashboard
M8 Docs, demo video, CI, security notes, tagged release

