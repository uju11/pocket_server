# STATE (update after every task, keep < 40 lines)
Updated: 2026-11-12
Phase: M1
IN PROGRESS: none
Last done: M1.3 (PairingManager: stable server_id, 5-min OTP; GET /pair/offer, POST /pair/redeem)
Next: M1.4
Blockers: S3 outcome requires 24h manual device test — fill spikes/background-survival/README.md after test; M0.3 blocked (Gradle multi-module Android refactor needs build verification)
Notes (<=10 lines, only what a fresh session must know):
- Repo root: remote-movie-pc-downloader/ (server app, Kotlin/Android)
- Client app: pocketnode-client/ (Jetpack Compose, ExoPlayer)
- Website: pocketnode-website/ (static)
- Spikes complete: S1 relay, S2 torrent-stream, S3 uptime-logger, S4 clock-sync
- M0.1: fill ARCH.md module map from code (modules + entry points)
