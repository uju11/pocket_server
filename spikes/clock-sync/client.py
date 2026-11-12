"""
S4 spike — clock-sync client.
usage: python client.py <ws_url> <label>
  e.g. python client.py ws://localhost:8765 A

Performs NTP-lite offset estimation, then prints local vs. adjusted position
every second so you can compare two terminal windows for drift.
"""
import asyncio
import json
import sys
import time
import websockets

LABEL = sys.argv[2] if len(sys.argv) > 2 else "X"
URL   = sys.argv[1] if len(sys.argv) > 1 else "ws://localhost:8765"
PING_COUNT = 5   # pings to average for offset estimation


def now_ms() -> float:
    return time.monotonic() * 1000.0


async def measure_offset(ws) -> float:
    """NTP-lite: send PING_COUNT pings, return median clock offset in ms."""
    offsets = []
    for _ in range(PING_COUNT):
        t0 = now_ms()
        await ws.send(json.dumps({"type": "ping", "t0": t0}))
        raw = await ws.recv()
        t2 = now_ms()
        msg = json.loads(raw)
        if msg.get("type") == "pong":
            rtt = t2 - msg["t0"]
            offset = msg["ts"] - (msg["t0"] + rtt / 2)
            offsets.append(offset)
        await asyncio.sleep(0.05)
    offsets.sort()
    median = offsets[len(offsets) // 2]
    print(f"[{LABEL}] NTP-lite offsets={[f'{o:.1f}' for o in offsets]} median={median:.1f}ms")
    return median


async def main():
    print(f"[{LABEL}] Connecting to {URL} …")
    async with websockets.connect(URL) as ws:
        offset_ms = await measure_offset(ws)

        # Playback state (mirrors server)
        playing = False
        position = 0.0
        ref_local_ms = now_ms()

        def adjusted_server_now() -> float:
            return now_ms() + offset_ms

        def local_position() -> float:
            if playing:
                return position + (now_ms() - ref_local_ms) / 1000.0
            return position

        async def recv_loop():
            nonlocal playing, position, ref_local_ms
            async for raw in ws:
                msg = json.loads(raw)
                t = msg.get("type")
                if t == "pong":
                    continue  # handled in measure_offset
                srv_time = msg.get("server_time", now_ms() + offset_ms)
                latency = adjusted_server_now() - srv_time
                if t in ("state", "play"):
                    playing = True
                    position = msg["position"]
                    # schedule: position already advancing from server_time
                    ref_local_ms = now_ms() - latency
                    print(f"[{LABEL}] PLAY  pos={position:.3f}s latency={latency:.1f}ms")
                elif t == "pause":
                    playing = False
                    position = msg["position"] + latency / 1000.0
                    print(f"[{LABEL}] PAUSE pos={position:.3f}s latency={latency:.1f}ms")
                elif t == "seek":
                    position = msg["position"]
                    ref_local_ms = now_ms() - latency
                    print(f"[{LABEL}] SEEK  pos={position:.3f}s latency={latency:.1f}ms")

        async def display_loop():
            while True:
                await asyncio.sleep(1)
                pos = local_position()
                adj = adjusted_server_now()
                print(f"[{LABEL}] tick  local_pos={pos:.3f}s  adj_server={adj:.0f}ms  playing={playing}")

        await asyncio.gather(recv_loop(), display_loop())


if __name__ == "__main__":
    asyncio.run(main())
