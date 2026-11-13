# STATE (update after every task, keep < 40 lines)
Updated: 2026-11-13
Phase: Complete (v1.0.0 + post-release fixes)
IN PROGRESS: none
Last done: fix(review) — 4 bugs fixed in f411e0e
Next: None (all milestones complete)
Blockers: S3 outcome requires 24h manual device test — fill spikes/background-survival/README.md after test
Notes (<=10 lines, only what a fresh session must know):
- Repo root: remote-movie-pc-downloader/ (server app, Kotlin/Android)
- Client app: pocketnode-client/ (Jetpack Compose, ExoPlayer)
- Website: pocketnode-website/ (static)
- Spikes complete: S1 relay, S2 torrent-stream, S3 uptime-logger, S4 clock-sync
- Modules: :app :core :network :storage :media :adapters :sources :engine-torrent :telegram
- M1 complete: UserManager, TokenManager, PairingManager, WatchStateManager
- Fixes applied post-v1.0.0: WebDAV path traversal (isPathSafe), WebDAV PUT ByteReadChannel→ByteArray, TorrentEngine init seedingOutgoingConnections guard, WatchPartyHub double-send eliminated
