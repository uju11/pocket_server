#!/usr/bin/env python3
"""
M0.2 smoke tests — records current Jellyfin & qBittorrent API behavior.
Run against a live server: python smoke_tests.py <host> [port_web] [port_jf]
Defaults: host=localhost, port_web=8080, port_jf=8096

Each test prints PASS / FAIL / SKIP. Exit code = number of failures.
Tests are READ-ONLY (GET + harmless discovery POSTs only).
"""

import sys
import json
import urllib.request
import urllib.error

HOST = sys.argv[1] if len(sys.argv) > 1 else "localhost"
PORT_WEB = int(sys.argv[2]) if len(sys.argv) > 2 else 8080
PORT_JF = int(sys.argv[3]) if len(sys.argv) > 3 else 8096

BASE_WEB = f"http://{HOST}:{PORT_WEB}"
BASE_JF = f"http://{HOST}:{PORT_JF}"

failures = 0
passes = 0
skips = 0


def get(url: str, timeout: int = 5):
    req = urllib.request.Request(url, headers={"User-Agent": "smoke-test/1.0"})
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            return r.status, r.read(), dict(r.headers)
    except urllib.error.HTTPError as e:
        return e.code, e.read(), {}
    except Exception as e:
        return None, str(e).encode(), {}


def post_form(url: str, data: dict, timeout: int = 5):
    import urllib.parse
    body = urllib.parse.urlencode(data).encode()
    req = urllib.request.Request(url, data=body, method="POST",
                                 headers={"Content-Type": "application/x-www-form-urlencoded"})
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            return r.status, r.read(), dict(r.headers)
    except urllib.error.HTTPError as e:
        return e.code, e.read(), {}
    except Exception as e:
        return None, str(e).encode(), {}


def check(name: str, cond: bool, detail: str = ""):
    global failures, passes
    if cond:
        print(f"  PASS  {name}")
        passes += 1
    else:
        print(f"  FAIL  {name}  ({detail})")
        failures += 1


def skip(name: str, reason: str = ""):
    global skips
    print(f"  SKIP  {name}  ({reason})")
    skips += 1


# ══════════════════════════════════════════════════════════════════
# WebHttpServer :8080 — core HTTP API
# ══════════════════════════════════════════════════════════════════
print(f"\n── WebHttpServer {BASE_WEB} ──")

# GET / → HTML dashboard
status, body, hdrs = get(f"{BASE_WEB}/")
check("GET / → 200 HTML", status == 200 and b"<html" in body.lower(),
      f"status={status}")

# GET /ping → "pong"
status, body, hdrs = get(f"{BASE_WEB}/ping")
check("GET /ping → 200 pong", status == 200 and body.strip() == b"pong",
      f"status={status} body={body[:20]}")

# GET /capabilities → JSON with api_version + features
status, body, hdrs = get(f"{BASE_WEB}/capabilities")
try:
    obj = json.loads(body)
    check("GET /capabilities → has api_version",
          status == 200 and "api_version" in obj,
          f"keys={list(obj.keys())}")
    check("GET /capabilities → has features dict",
          isinstance(obj.get("features"), dict),
          f"features={obj.get('features')}")
except Exception:
    check("GET /capabilities → has api_version", False, f"body={body[:60]}")

# GET /api/files → JSON array
status, body, hdrs = get(f"{BASE_WEB}/api/files")
check("GET /api/files → 200 JSON array", status == 200 and body.strip().startswith(b"["),
      f"status={status} body={body[:60]}")

# GET /api/torrents/trending → JSON array
status, body, hdrs = get(f"{BASE_WEB}/api/torrents/trending")
check("GET /api/torrents/trending → 200 JSON", status == 200,
      f"status={status}")

# qBittorrent v2 API
print(f"\n── qBittorrent v2 API (via WebHttpServer) ──")

# POST /api/v2/auth/login → "Ok."
status, body, hdrs = post_form(f"{BASE_WEB}/api/v2/auth/login",
                                {"username": "admin", "password": "adminadmin"})
check("POST /api/v2/auth/login → 200", status == 200, f"status={status}")
check("POST /api/v2/auth/login body contains 'Ok'",
      status == 200 and b"Ok" in body, f"body={body[:60]}")

# GET /api/v2/app/version → non-empty string
status, body, hdrs = get(f"{BASE_WEB}/api/v2/app/version")
check("GET /api/v2/app/version → 200", status == 200, f"status={status}")
check("GET /api/v2/app/version non-empty", len(body.strip()) > 0, f"body={body[:30]}")

# GET /api/v2/app/webapiVersion → "2.x"
status, body, hdrs = get(f"{BASE_WEB}/api/v2/app/webapiVersion")
check("GET /api/v2/app/webapiVersion → starts with '2'",
      status == 200 and body.strip().startswith(b"2"), f"body={body[:20]}")

# GET /api/v2/app/buildInfo → JSON with version key
status, body, hdrs = get(f"{BASE_WEB}/api/v2/app/buildInfo")
try:
    obj = json.loads(body)
    check("GET /api/v2/app/buildInfo → JSON with 'version' key",
          status == 200 and "version" in obj, f"keys={list(obj.keys())[:5]}")
except Exception:
    check("GET /api/v2/app/buildInfo → JSON with 'version' key",
          False, f"body={body[:60]}")

# GET /api/v2/torrents/info → JSON array
status, body, hdrs = get(f"{BASE_WEB}/api/v2/torrents/info")
check("GET /api/v2/torrents/info → 200 JSON array",
      status == 200 and body.strip().startswith(b"["),
      f"status={status} body={body[:60]}")

# GET /api/v2/app/preferences → JSON object
status, body, hdrs = get(f"{BASE_WEB}/api/v2/app/preferences")
try:
    obj = json.loads(body)
    check("GET /api/v2/app/preferences → JSON object",
          status == 200 and isinstance(obj, dict), f"keys={list(obj.keys())[:5]}")
except Exception:
    check("GET /api/v2/app/preferences → JSON object",
          False, f"body={body[:60]}")

# GET /api/v2/transfer/info → JSON with dl_info_speed
status, body, hdrs = get(f"{BASE_WEB}/api/v2/transfer/info")
try:
    obj = json.loads(body)
    check("GET /api/v2/transfer/info → has dl_info_speed",
          status == 200 and "dl_info_speed" in obj, f"keys={list(obj.keys())[:6]}")
except Exception:
    check("GET /api/v2/transfer/info → has dl_info_speed",
          False, f"body={body[:60]}")

# Playback progress API
print(f"\n── Playback progress API ──")
status, body, hdrs = get(f"{BASE_WEB}/api/playback/last")
check("GET /api/playback/last → 200", status == 200, f"status={status}")

# ══════════════════════════════════════════════════════════════════
# JellyfinServer :8096 — Jellyfin-compatible REST
# ══════════════════════════════════════════════════════════════════
print(f"\n── JellyfinServer {BASE_JF} ──")

# GET / (Accept: application/json) → JSON system info
status, body, hdrs = get(f"{BASE_JF}/")
check("GET / → 200", status == 200, f"status={status}")

# GET /System/Info/Public → JSON with ServerName
status, body, hdrs = get(f"{BASE_JF}/System/Info/Public")
try:
    obj = json.loads(body)
    check("GET /System/Info/Public → has ServerName",
          status == 200 and "ServerName" in obj,
          f"keys={list(obj.keys())[:6]}")
    check("GET /System/Info/Public ServerName non-empty",
          bool(obj.get("ServerName")), f"ServerName={obj.get('ServerName')}")
except Exception:
    check("GET /System/Info/Public → has ServerName", False, f"body={body[:60]}")

# GET /System/Info → JSON (may require auth header; accept 200 or 401)
status, body, hdrs = get(f"{BASE_JF}/System/Info")
check("GET /System/Info → 200 or 401",
      status in (200, 401), f"status={status}")

# GET /Items → 200 JSON
status, body, hdrs = get(f"{BASE_JF}/Items?IncludeItemTypes=Movie&Recursive=true&Limit=5")
check("GET /Items?IncludeItemTypes=Movie → 200",
      status == 200, f"status={status}")
try:
    obj = json.loads(body)
    check("GET /Items response has Items array", "Items" in obj,
          f"keys={list(obj.keys())[:6]}")
except Exception:
    check("GET /Items response has Items array", False, f"body={body[:60]}")

# GET /Users → JSON array (may be empty)
status, body, hdrs = get(f"{BASE_JF}/Users")
check("GET /Users → 200 JSON array",
      status == 200 and body.strip().startswith(b"["),
      f"status={status} body={body[:40]}")

# GET /Library/MediaFolders → JSON
status, body, hdrs = get(f"{BASE_JF}/Library/MediaFolders")
check("GET /Library/MediaFolders → 200",
      status == 200, f"status={status}")

# ══════════════════════════════════════════════════════════════════
print(f"\n── Summary ──")
total = passes + failures + skips
print(f"  PASS={passes}  FAIL={failures}  SKIP={skips}  TOTAL={total}")
if failures == 0:
    print("  ALL PASS")
sys.exit(failures)
