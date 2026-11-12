# S4 Spike: Clock Sync (Watch Party)

**Goal:** two clients over WebSocket, play/pause/seek; drift measured, ~300 ms or better.  
**Done when:** `python client.py` on two terminals shows drift < 300 ms consistently.

## Run

```bash
cd spikes/clock-sync
pip install websockets   # one-time

# terminal 1 — server
python server.py

# terminal 2 — client A
python client.py ws://localhost:8765 A

# terminal 3 — client B
python client.py ws://localhost:8765 B

# Then type commands into server terminal:
#   play   → broadcasts PLAY + server_time
#   pause  → broadcasts PAUSE
#   seek 42.5 → broadcasts SEEK to position 42.5s
```

## Protocol (NTP-lite round-trip)
```
Client → {"type":"ping","t0":<local_ms>}
Server → {"type":"pong","t0":<echo>,"ts":<server_ms>}
Client computes: offset = ((ts - t0) - rtt/2)
              adjusted_server_time = now_ms + offset

Server broadcasts: {"type":"play|pause|seek","server_time":<ms>,"position":<s>}
Client uses adjusted_server_time to schedule local playback start.
```

## Outcome (fill after manual test)
- [ ] Measured drift client A vs B: ____ ms
- [ ] Measured drift after seek: ____ ms
- [ ] Network: LAN / relay
- [ ] Notes:
