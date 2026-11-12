# S2 Spike: Torrent Range Streaming

**Goal:** libtorrent4j sequential + piece deadlines behind an HTTP range endpoint, played in ExoPlayer.  
**Done when:** first frame in ~15s on a healthy torrent, seek works.

## How to run (JVM, not Android)

```
cd spikes/torrent-stream
./gradlew run --args="<magnet_uri>"
```

Then open `http://localhost:9090/stream` in VLC or point ExoPlayer at it:
- VLC: `vlc http://localhost:9090/stream`
- curl range test: `curl -v -r 0-65535 http://localhost:9090/stream -o /dev/null`

## Design

```
magnet URI
  → libtorrent4j SessionManager (sequential + piece deadlines)
  → wait for metadata (<35s timeout)
  → pick largest video file, set file priority TOP, deselect others
  → boost header pieces (first 6) + trailer pieces (last 6) to TOP_PRIORITY
  → expose HTTP /stream endpoint that:
      - Returns Content-Length + Accept-Ranges + Content-Type
      - Handles Range header → 206 Partial Content
      - Per-read: check havePiece(); if not, setPieceDeadline(piece, 0); wait up to 45s
      - Advance sliding window of 16 pieces ahead with piecePriority(TOP)
```

## Outcome (fill after manual test)

- [ ] Metadata resolved in: __ s
- [ ] First frame in: __ s
- [ ] Seek (jump to 50%): works / broken
- [ ] Notes:
