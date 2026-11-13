# STATE (update after every task, keep < 40 lines)
Updated: 2026-11-13
Phase: M1 → M2
IN PROGRESS: none
Last done: M3.2 (RelayClient.kt: stub for relay WS, disabled by default, configure/start/stop/heartbeat)
Next: M3.3
Blockers: S3 outcome requires 24h manual device test — fill spikes/background-survival/README.md after test
Notes (<=10 lines, only what a fresh session must know):
- Repo root: remote-movie-pc-downloader/ (server app, Kotlin/Android)
- Client app: pocketnode-client/ (Jetpack Compose, ExoPlayer)
- Website: pocketnode-website/ (static)
- Spikes complete: S1 relay, S2 torrent-stream, S3 uptime-logger, S4 clock-sync
- Modules: :app :core :network :storage :media :adapters :sources :engine-torrent :telegram
- M1 complete: UserManager, TokenManager, PairingManager, WatchStateManager
