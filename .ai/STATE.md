# STATE (update after every task, keep < 40 lines)
Updated: 2026-11-12
Phase: Spikes
IN PROGRESS: none
Last done: S3 (UptimeLogger + ping script committed 4d59438)
Next: S4
Blockers: S3 outcome requires 24h manual device test — fill spikes/background-survival/README.md after test
Notes (<=10 lines, only what a fresh session must know):
- Repo root: remote-movie-pc-downloader/ (server app, Kotlin/Android)
- Client app: pocketnode-client/ (Jetpack Compose, ExoPlayer)
- Website: pocketnode-website/ (static)
- Spikes go in /spikes or branch spike/*
- S3 instrumentation: UptimeLogger ticks every 5m to files/uptime_log.txt; pull with adb after 24h
- S4: clock sync WebSocket spike, two clients, drift measurement
