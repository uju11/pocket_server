# UI Updates Tracker — PocketNode Redesign

## Goal
Redesign the Android app UI to match the PocketNode terminal/hacker aesthetic shown in the reference screenshots.

## Design Reference
- Dark obsidian background (#0B0C0E / #111317)
- Orange accent for active/selected elements (TacticalOrange = #FF5722)
- Green for status/toggles (StatusGreen = #00C853)
- Yellow badge for version tag (StatusGreen badge bg)
- Monospace font throughout
- Tab navigation: General | Jellyfin | Telegram | Jackett | Web
- Top status bar: TTY:01 // HH:MM:SS UTC  •signal [5G] / battery% [icon]
- App header: PN orange box logo, PocketNode v2.4 green badge, // CORE_SYSTEM : CONFIG_DECK, >_CLI (yellow border), CFG (orange border)

---

## Screens / Tabs

### Top Status Bar & App Header Bar
- [x] TTY:01 + live UTC clock + signal dot + battery
- [x] PN orange logo badge, app name, v2.4 green badge
- [x] >_ CLI terminal button (yellow border)
- [x] ⚙ CFG settings button (orange border)

### Tab Navigation
- [x] Scrollable horizontal tab bar: General, Jellyfin, Telegram, Jackett, Web
- [x] Active tab: orange pill background, white text
- [x] Inactive tabs: dark bg with [ Tab ] bracket styling
- [x] Active dot indicator for Telegram and Jackett tabs

### General Tab (GeneralTabScreen.kt)
- [x] STORAGE_DIRECTORY section with EXT4/R-W green badge
- [x] Storage path display box with 📁 icon
- [x] Free storage text + BROWSE button
- [x] DAEMON_ORCHESTRATION section with SYS_AUTO orange badge
- [x] Boot Auto-Start toggle (green switch)
- [x] Keep WakeLock Active toggle (green switch)
- [x] Storage Quota Alert with % badge + orange slider
- [x] SAVE CONFIG (orange) + REVERT buttons
- [x] I_O_PIPELINE_BANDWIDTH card with write speed badge
- [x] Bandwidth sparkline chart (orange area fill + line)
- [x] IOPS / BLOCK / LATENCY stats row
- [x] MicroSD/RAID + ZRAM Swap info cards at bottom
- [x] Chart expands to full-screen dialog on tap
- [x] Footer with Xmedia attribution + Buy me a coffee button

### Jellyfin Tab (JellyfinTabScreen.kt)
- [x] DAEMON_STATUS with RUNNING/v10.9.8 green badge
- [x] 2x2 stats grid: BIND_PORT, SYS_UPTIME, MEMORY_RES, ENCRYPTION
- [x] Force SSL/HTTPS toggle
- [x] MEDIA_LIBRARIES section with 2 ACTIVE yellow badge
- [x] Collection label (UTF-8 STRICT) + folder path + BROWSE button
- [x] + ADD MEDIA LIBRARY orange bordered button
- [x] Mounted Repositories list (2 repos with path + meta + size)
- [x] USER_MANAGEMENT section with AUTH_LOCAL badge
- [x] Admin user row with SU, ROOT, FULL ACCESS badges
- [x] Provision Username input field
- [x] Hardware Transcoding toggle (VAAPI/NVENC note)
- [x] + CREATE USER ACCOUNT yellow button
- [x] DEPLOY & RESTART JELLYFIN orange button + REVERT

### Telegram Tab (TelegramTabScreen.kt)
- [x] BOT_CREDENTIALS with ONLINE/200 OK green badge
- [x] Bot API Token field (SHA256_ENC badge, eye toggle icon)
- [x] Ident Handle field with orange border + copy icon
- [x] Admin Chat ID with VERIFIED green badge + copy icon
- [x] TEST BOT WEBHOOK button
- [x] NOTIFICATION_TRIGGERS with 4 RULES badge
- [x] Crash & Daemon Offline toggle
- [x] Thermal Overheat Alert toggle
- [x] Storage Low Threshold toggle
- [x] Service Unit State Change toggle (off)
- [x] REMOTE_EXECUTION with SECURE green badge
- [x] Allow Remote Exec toggle
- [x] Command 2FA Security PIN field (REQUIRED red badge, lock icon)
- [x] Authorized Master ID field with shield icon
- [x] SAVE CONFIG + SEND TEST PING buttons

### Jackett Tab (JackettTabScreen.kt)
- [x] DAEMON_BINDING with TORZNAB/ACTIVE green badge
- [x] Jackett Daemon Endpoint with PORT:9117 orange badge
- [x] Endpoint URL + [LOCAL] tag
- [x] Torznab API Key with AUTH:SHA256 yellow label + key icon
- [x] COPY KEY + VERIFY KEY buttons
- [x] INDEXER_POOLS with 14 ACTIVE yellow badge
- [x] TRACKERS 14 (OK) + SEARCH CACHE 30 Mins cards
- [x] Auto-Sync Torznab Feeds toggle
- [x] Download Client Binding dropdown
- [x] WORKER_ORCHESTRATION with READY green badge
- [x] Worker Threads progress bar (3/8 concurrent)
- [x] Force Relay via VPN/Tunnel toggle
- [x] TEST ALL 14 INDEXERS button
- [x] SAVE & REHASH JACKETT orange button + REVERT

### Web Tab (WebTabScreen.kt)
- [x] Placeholder card with WEB_PANEL header and centered 🌐

### Main App Shell (MainActivity.kt)
- [x] Tab-based navigation replacing old screen-stack
- [x] Live UTC clock updating every second
- [x] Metrics (battery, charging) from SystemMetricsManager
- [x] Onboarding screen preserved
- [x] Terminal screen accessible via CLI button

---

## Files Modified/Created

| File | Status | Notes |
|------|--------|-------|
| Color.kt | ✅ Done | Added StatusGreen, StatusOrange, StatusRed, PocketYellowBright, TacticalSubtle, PocketOrangeLight |
| MainActivity.kt | ✅ Done | Tab-based navigation, live clock, PocketNode shell |
| PocketNodeComponents.kt | ✅ Created | Shared components: status bar, header, tab bar, section header, card, toggle row, switch, input, badge, action button |
| GeneralTabScreen.kt | ✅ Created | Storage, daemon toggles, quota slider, bandwidth chart |
| JellyfinTabScreen.kt | ✅ Created | Daemon status, media libraries, user management |
| TelegramTabScreen.kt | ✅ Created | Bot credentials, notification triggers, remote execution |
| JackettTabScreen.kt | ✅ Created | Daemon binding, indexer pools, worker orchestration |
| WebTabScreen.kt | ✅ Created | Placeholder |
| MainScreen.kt | ⚠️ Kept | Old dashboard screen (no longer used in nav, can be repurposed) |
| SettingsScreen.kt | ⚠️ Kept | Old settings (no longer used in nav) |
| JellyfinSettingsScreen.kt | ⚠️ Kept | Old jellyfin settings (no longer used in nav) |

---

## Pending / Not Yet Implemented (Placeholders)

- [ ] Chart expand dialog: real-time data (currently dummy sine wave data)
- [ ] Storage quota slider: persistence to SharedPreferences
- [ ] Jellyfin DEPLOY & RESTART: actually call JellyfinManager
- [ ] Jackett SAVE & REHASH: actual backend call
- [ ] Telegram TEST BOT WEBHOOK: actual webhook ping
- [ ] Jackett COPY KEY / VERIFY KEY: actual clipboard + validation
- [ ] BROWSE buttons: file picker intent
- [ ] Real signal strength from Android TelephonyManager
- [ ] Web tab full content
- [ ] Download Client Binding: actual dropdown/dialog
- [ ] Provision Username: form submission

## To Resume Next Session
1. Review this file
2. Look at compile errors (build in Android Studio, check Logcat)
3. Fix any remaining compile issues
4. Then tackle the "Pending / Not Yet Implemented" items above

