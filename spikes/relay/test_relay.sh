#!/usr/bin/env bash
# test_relay.sh – local smoke test for S1 (no VPS needed)
# Requires: python3, pip packages fastapi uvicorn websockets
# Done-when: last curl prints {"pong":"from-host"}

set -e
RELAY_PORT=8765
HOST_PORT=19999
RELAY_PID="" HOST_PID="" MOCK_PID=""

cleanup() {
  [ -n "$RELAY_PID" ] && kill "$RELAY_PID" 2>/dev/null || true
  [ -n "$HOST_PID"  ] && kill "$HOST_PID"  2>/dev/null || true
  [ -n "$MOCK_PID"  ] && kill "$MOCK_PID"  2>/dev/null || true
}
trap cleanup EXIT

# 1. Start mock local HTTP server (Python one-liner)
python3 -c "
import http.server, json
class H(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        body=json.dumps({'pong':'from-host'}).encode()
        self.send_response(200)
        self.send_header('Content-Type','application/json')
        self.send_header('Content-Length',str(len(body)))
        self.end_headers()
        self.wfile.write(body)
    def log_message(self,*a): pass
http.server.HTTPServer(('127.0.0.1',$HOST_PORT),H).serve_forever()
" &
MOCK_PID=$!
sleep 0.5

# 2. Start relay
python3 relay_server.py &
RELAY_PID=$!
sleep 1

# 3. Start host connector
python3 host_connector.py "ws://127.0.0.1:$RELAY_PORT/host" "http://127.0.0.1:$HOST_PORT" &
HOST_PID=$!
sleep 1

# 4. Verify relay /ping shows host connected
echo "--- relay /ping ---"
curl -sf "http://127.0.0.1:$RELAY_PORT/ping"
echo ""

# 5. Proxy a request through relay → host connector → mock server
echo "--- proxied /ping via relay ---"
RESULT=$(curl -sf "http://127.0.0.1:$RELAY_PORT/proxy/ping")
echo "$RESULT"

if echo "$RESULT" | grep -q "from-host"; then
  echo "PASS: S1 relay tunnel works"
else
  echo "FAIL: unexpected response"
  exit 1
fi
