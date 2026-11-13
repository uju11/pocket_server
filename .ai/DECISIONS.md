# DECISIONS (append-only, 1-3 lines each: date, choice, why)

2026-11-12 S1: Chose FastAPI+WebSocket for relay (single process, async, easy to deploy on any VPS with pip). Host connector uses stdlib urllib — no extra deps on Android/Python host side. Spike code is throwaway; real integration will use the same protocol but embedded in the server app.

2026-11-12 S2: Chose stdlib com.sun.net.httpserver (JVM built-in) for spike HTTP layer — zero extra deps, good enough for a throwaway range-streaming test. libtorrent4j JVM (linux-x86_64 native) used on desktop; same 2.1.0-30 version as Android app. setPieceDeadline(0) used for urgent pieces instead of piecePriority only — more reliable head-of-line delivery. Production TorrentStreamManager already implements this design; spike validates it end-to-end.

2026-11-12 S3: Added UptimeLogger (5-min heartbeat to files/uptime_log.txt) hooked into startAllServers/stopServer. PC-side ping_remote.sh curls /ping every 5m. Service already had PARTIAL_WAKE_LOCK + WIFI_MODE_FULL_HIGH_PERF + MulticastLock — no lock changes needed. Outcome column requires 24h manual device test.

2026-11-12 S4: NTP-lite WebSocket clock sync. 5-ping median offset estimation. Simulated LAN drift max=1.66ms, mean=1.16ms (PASS <300ms). Real-device estimate ≤30ms. Protocol (server-authoritative time + per-client offset) is the design to embed in M6 watch-party server.

2026-11-13 M6.2: Added io.ktor:ktor-server-websockets:2.3.12 for /ws/party real-time watch party synchronization as proven in S4 spike.