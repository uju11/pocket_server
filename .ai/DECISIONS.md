# DECISIONS (append-only, 1-3 lines each: date, choice, why)

2026-11-12 S1: Chose FastAPI+WebSocket for relay (single process, async, easy to deploy on any VPS with pip). Host connector uses stdlib urllib — no extra deps on Android/Python host side. Spike code is throwaway; real integration will use the same protocol but embedded in the server app.

2026-11-12 S2: Chose stdlib com.sun.net.httpserver (JVM built-in) for spike HTTP layer — zero extra deps, good enough for a throwaway range-streaming test. libtorrent4j JVM (linux-x86_64 native) used on desktop; same 2.1.0-30 version as Android app. setPieceDeadline(0) used for urgent pieces instead of piecePriority only — more reliable head-of-line delivery. Production TorrentStreamManager already implements this design; spike validates it end-to-end.

2026-11-12 S3: Added UptimeLogger (5-min heartbeat to files/uptime_log.txt) hooked into startAllServers/stopServer. PC-side ping_remote.sh curls /ping every 5m. Service already had PARTIAL_WAKE_LOCK + WIFI_MODE_FULL_HIGH_PERF + MulticastLock — no lock changes needed. Outcome column requires 24h manual device test.