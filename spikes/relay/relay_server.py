"""
S1 Relay Tunnel spike.

The host dials OUT to this relay (WebSocket upgrade on /host).
Clients reach the host by sending HTTP requests to /proxy/{path}.
The relay tunnels request+response over the persistent WebSocket.

Usage (VPS):
    pip install fastapi uvicorn websockets
    uvicorn relay_server:app --host 0.0.0.0 --port 8765

Usage (host device):
    python host_connector.py wss://<vps>:8765/host

Done-when: curl via relay returns host /ping (see test_relay.sh).
"""

import asyncio
import json
import uuid
from typing import Optional

import uvicorn
from fastapi import FastAPI, WebSocket, WebSocketDisconnect, Request
from fastapi.responses import Response

app = FastAPI()

# --- state ------------------------------------------------------------------
_host_ws: Optional[WebSocket] = None          # one host at a time (spike)
_pending: dict[str, asyncio.Future] = {}       # request_id -> Future[dict]


# --- host endpoint ----------------------------------------------------------
@app.websocket("/host")
async def host_endpoint(ws: WebSocket):
    global _host_ws
    await ws.accept()
    _host_ws = ws
    print("Host connected")
    try:
        while True:
            raw = await ws.receive_text()
            msg = json.loads(raw)
            rid = msg.get("request_id")
            if rid and rid in _pending:
                _pending[rid].set_result(msg)
    except WebSocketDisconnect:
        print("Host disconnected")
    finally:
        _host_ws = None


# --- client proxy endpoint --------------------------------------------------
@app.api_route("/proxy/{path:path}", methods=["GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS"])
async def proxy(request: Request, path: str):
    if _host_ws is None:
        return Response("Host not connected", status_code=502)

    rid = str(uuid.uuid4())
    body = await request.body()
    envelope = {
        "request_id": rid,
        "method": request.method,
        "path": "/" + path,
        "query": str(request.url.query),
        "headers": dict(request.headers),
        "body": body.decode("utf-8", errors="replace"),
    }

    fut: asyncio.Future = asyncio.get_event_loop().create_future()
    _pending[rid] = fut
    try:
        await _host_ws.send_text(json.dumps(envelope))
        response_msg = await asyncio.wait_for(fut, timeout=15)
    except asyncio.TimeoutError:
        return Response("Upstream timeout", status_code=504)
    finally:
        _pending.pop(rid, None)

    return Response(
        content=response_msg.get("body", ""),
        status_code=response_msg.get("status", 200),
        headers=response_msg.get("headers", {}),
        media_type=response_msg.get("content_type", "text/plain"),
    )


# --- healthcheck ------------------------------------------------------------
@app.get("/ping")
async def relay_ping():
    return {"relay": "ok", "host_connected": _host_ws is not None}


if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8765)
