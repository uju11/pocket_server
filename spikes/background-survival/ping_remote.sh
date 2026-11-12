#!/usr/bin/env bash
# S3 spike: PC-side reachability probe. Runs every 5 min, logs to remote_ping_log.txt.
# usage: ./ping_remote.sh <host> <port>
HOST=${1:-192.168.1.42}
PORT=${2:-8080}
LOG="$(dirname "$0")/remote_ping_log.txt"
URL="http://$HOST:$PORT/ping"

echo "=== START $(date '+%Y-%m-%d %H:%M:%S') pinging $URL ===" | tee -a "$LOG"

while true; do
    TS=$(date '+%Y-%m-%d %H:%M:%S')
    HTTP=$(curl -s -o /dev/null -w "%{http_code}" --max-time 10 "$URL" 2>/dev/null)
    if [ "$HTTP" = "200" ]; then
        echo "[$TS] OK  (HTTP $HTTP)" | tee -a "$LOG"
    else
        echo "[$TS] FAIL (HTTP $HTTP)" | tee -a "$LOG"
    fi
    sleep 300  # 5 min
done
