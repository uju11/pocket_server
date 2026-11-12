"""
host_connector.py – runs on the Android/PC host device.

Connects outward to the relay, then forwards every incoming request
to the local HTTP server (default http://localhost:8080) and sends
the response back over the same WebSocket.

Usage:
    python host_connector.py wss://<vps>:8765/host
    python host_connector.py wss://<vps>:8765/host http://localhost:8080
"""

import asyncio
import json
import sys
import urllib.request

import websockets


RELAY_URL = sys.argv[1] if len(sys.argv) > 1 else "ws://localhost:8765/host"
LOCAL_BASE = sys.argv[2] if len(sys.argv) > 2 else "http://localhost:8080"


async def handle_request(ws, msg: dict):
    path = msg.get("path", "/")
    query = msg.get("query", "")
    url = LOCAL_BASE + path + (f"?{query}" if query else "")
    method = msg.get("method", "GET")
    body = msg.get("body", "").encode()
    req_headers = {k: v for k, v in msg.get("headers", {}).items()
                   if k.lower() not in ("host", "content-length")}

    try:
        req = urllib.request.Request(url, data=body or None, headers=req_headers, method=method)
        with urllib.request.urlopen(req, timeout=10) as resp:
            resp_body = resp.read().decode("utf-8", errors="replace")
            resp_status = resp.status
            content_type = resp.headers.get("Content-Type", "text/plain")
    except Exception as e:
        resp_body = str(e)
        resp_status = 502
        content_type = "text/plain"

    reply = {
        "request_id": msg["request_id"],
        "status": resp_status,
        "body": resp_body,
        "content_type": content_type,
        "headers": {},
    }
    await ws.send(json.dumps(reply))


async def main():
    print(f"Connecting to relay {RELAY_URL} → forwarding to {LOCAL_BASE}")
    async with websockets.connect(RELAY_URL) as ws:
        print("Connected. Waiting for proxied requests…")
        async for raw in ws:
            msg = json.loads(raw)
            asyncio.create_task(handle_request(ws, msg))


if __name__ == "__main__":
    asyncio.run(main())
