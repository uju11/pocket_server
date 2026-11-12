# S3 Spike: Background Survival (24h manual test)

**Goal:** foreground-service host stays reachable 24h on the real device.  
**Done when:** `uptime_log.txt` on device has unbroken 5-min ticks for 24h AND `remote_ping_log.txt` from PC shows no gaps > 10 min.

## Device side (automatic)
`UptimeLogger` in `ServerForegroundService` writes to:
```
/data/data/com.remotemedia/files/uptime_log.txt
```
Pull it after 24h:
```
adb shell run-as com.remotemedia cat files/uptime_log.txt
```

## PC side — run `ping_remote.sh` on the same LAN or over relay
```bash
# usage: ./ping_remote.sh <host> <port>
# e.g.:  ./ping_remote.sh 192.168.1.42 8080
bash spikes/background-survival/ping_remote.sh 192.168.1.42 8080
```
Logs to `remote_ping_log.txt` in the same directory.

## Battery settings that must be applied on device (try in order)
1. **Disable battery optimisation** for the app: Settings → Apps → PocketNode → Battery → Unrestricted
2. **Keep screen on** is NOT needed (foreground service + wakelock is sufficient)
3. If the device has MIUI/OxygenOS/ColorOS: also whitelist in the vendor power manager
4. Wi-Fi sleep policy: Settings → Wi-Fi → Advanced → Keep Wi-Fi on during sleep → Always

## Outcome (fill after 24h)
- [ ] Device uptime_log.txt: ____ ticks, any gaps? ____
- [ ] PC ping log: ____ OK, ____ FAIL, longest gap: ____ min
- [ ] Battery settings that worked: ____
- [ ] Notes:
