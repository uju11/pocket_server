"""
S4 spike — clock-sync server (asyncio + websockets).
Maintains a host-authoritative position clock.
Broadcasts play/pause/seek to all connected clients.
"""
import asyncio
import json
import time
import websockets

PORT = 8765
clients: set = set()
state = {"playing": False, "position": 0.0, "server_time": 0.0}


def now_ms() -> float:
    return time.monotonic() * 1000.0


def current_position() -> float:
    if state["playing"]:
        return state["position"] + (now_ms() - state["server_time"]) / 1000.0
    return state["position"]


async def broadcast(msg: dict):
    if not clients:
        return
    data = json.dumps(msg)
    await asyncio.gather(*[c.send(data) for c in clients], return_exceptions=True)


async def handler(ws):
    clients.add(ws)
    peer = ws.remote_address
    print(f"[+] {peer} connected  ({len(clients)} total)")
    # Send current state immediately so late-joiners sync up
    await ws.send(json.dumps({
        "type": "state",
        "playing": state["playing"],
        "position": current_position(),
        "server_time": now_ms(),
    }))
    try:
        async for raw in ws:
            msg = json.loads(raw)
            if msg.get("type") == "ping":
                await ws.send(json.dumps({"type": "pong", "t0": msg["t0"], "ts": now_ms()}))
    except websockets.ConnectionClosed:
        pass
    finally:
        clients.discard(ws)
        print(f"[-] {peer} disconnected ({len(clients)} total)")


async def console():
    """Read commands from stdin: play / pause / seek <pos>"""
    loop = asyncio.get_event_loop()
    reader = asyncio.StreamReader()
    proto = asyncio.StreamReaderProtocol(reader)
    await loop.connect_read_pipe(lambda: proto, asyncio.get_event_loop()._selector._map[0].fileobj
                                 if hasattr(asyncio.get_event_loop(), '_selector') else __import__('sys').stdin)
    # Fallback: use run_in_executor for blocking input
    while True:
        cmd = await loop.run_in_executor(None, input, "> ")
        cmd = cmd.strip()
        if cmd == "play":
            state["position"] = current_position()
            state["server_time"] = now_ms()
            state["playing"] = True
            await broadcast({"type": "play", "position": state["position"], "server_time": state["server_time"]})
            print(f"  → PLAY at {state['position']:.2f}s")
        elif cmd == "pause":
            state["position"] = current_position()
            state["server_time"] = now_ms()
            state["playing"] = False
            await broadcast({"type": "pause", "position": state["position"], "server_time": state["server_time"]})
            print(f"  → PAUSE at {state['position']:.2f}s")
        elif cmd.startswith("seek "):
            pos = float(cmd.split()[1])
            state["position"] = pos
            state["server_time"] = now_ms()
            await broadcast({"type": "seek", "position": pos, "server_time": state["server_time"]})
            print(f"  → SEEK to {pos:.2f}s")
        else:
            print("  commands: play | pause | seek <seconds>")


async def main():
    print(f"[server] Clock-sync spike on ws://0.0.0.0:{PORT}")
    async with websockets.serve(handler, "0.0.0.0", PORT):
        await console()


if __name__ == "__main__":
    asyncio.run(main())
