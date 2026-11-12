# S1 – Relay Tunnel Spike

## What it is
A tiny VPS relay that lets the host device (behind NAT/CGNAT) dial **outward** via WebSocket so mobile clients can reach it without port-forwarding.

## Files
| File | Role |
|---|---|
| `relay_server.py` | FastAPI/WebSocket relay — runs on the VPS |
| `host_connector.py` | Runs on host device; dials relay, forwards requests locally |
| `test_relay.sh` | Local smoke-test (no VPS needed) |

## Quick start (VPS)
```bash
pip install fastapi uvicorn websockets
uvicorn relay_server:app --host 0.0.0.0 --port 8765
```

## Quick start (host device)
```bash
pip install websockets
python host_connector.py wss://your-vps:8765/host
```

## Client usage
Any HTTP client hits `https://your-vps:8765/proxy/<path>` and the relay tunnels it to the host's local server.

## Done-when
`bash test_relay.sh` → prints `PASS: S1 relay tunnel works`.

## Outcome (fill in after running)
- [ ] Test passed on local machine
- [ ] Tested on real VPS with mobile data client
- Notes: _record latency, reconnect behaviour, any issues_
