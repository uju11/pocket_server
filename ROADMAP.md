# PocketNode — Code Review & Roadmap

This document records a read-through of the repository and a proposed plan. The server core was read closely; the client, Python bot and website were sampled. Nothing was built or run, so every finding comes from reading the code.

## What's in the repo

| Part | What it is | Size |
|---|---|---|
| `remote-movie-pc-downloader/app` | Android server: Ktor web server, Jellyfin emulation, qBittorrent-compatible API, Jackett, DLNA, torrent engine, Telegram bot | ~30k lines Kotlin |
| `pocketnode-client` | Android/TV client (Compose + ExoPlayer) | ~9k lines Kotlin |
| `remote-movie-pc-downloader/*.py` | Older PC version: Telethon bot, library scanner, tray app, Docker stack | ~3.5k lines Python |
| `index.html`, `torrent-lab.html` | Landing page and WebTorrent demo on Vercel | ~2k lines |

## Security problems to fix first

1. **Path traversal in file serving.** `isPathSafe` in [`StorageManager.kt`](remote-movie-pc-downloader/app/src/main/java/com/remotemedia/core/StorageManager.kt#L203) falls back to a non-normalised absolute-path check, so `/download?path=../../…` passes. It also returns `true` when an exception is thrown. Use the canonical path only, compare with a trailing separator, and fail closed.
2. **No authentication on any server.** [`WebHttpServer.kt`](remote-movie-pc-downloader/app/src/main/java/com/remotemedia/services/WebHttpServer.kt#L62) binds to `0.0.0.0` with CORS `anyHost()`. The qBittorrent login always answers "Ok", and the Jellyfin login in [`JellyfinServer.kt`](remote-movie-pc-downloader/app/src/main/java/com/remotemedia/services/JellyfinServer.kt#L773) never checks a password. Anyone on the Wi-Fi can browse files and start downloads.
3. **Hardcoded Telegram admin ID.** [`TelegramBotEngine.kt`](remote-movie-pc-downloader/app/src/main/java/com/remotemedia/download/TelegramBotEngine.kt#L117) sets the admin to a fixed ID when none is saved. Every fresh install therefore trusts that one account, and the auto-pair branch below it can never run.
4. **State-changing GET endpoints.** `GET /api/torrent/download` and `GET /api/telegram/download` start downloads, so with open CORS any web page the user visits can trigger them.
5. **Mesh registration lets a caller make the server fetch any URL.** `/api/mesh/register` accepts an arbitrary `streamUrl`, which `/meshmedia/stream` then fetches and returns. The `authKey` field is stored but never checked.
6. **Unescaped names in the web dashboard.** File names, share names and mesh device names go straight into HTML and inline `onclick` handlers, so a crafted file name runs script in the browser.
7. **SMB passwords stored in plain text** in SharedPreferences ([`NetworkShareManager.kt`](remote-movie-pc-downloader/app/src/main/java/com/remotemedia/core/NetworkShareManager.kt#L655)), with `allowBackup="true"` on the app.
8. **The Python LAN bridge** in `bot.py` listens on `0.0.0.0:8765`; whether it has any authentication was not checked.

## Bugs and build issues

- **Build is tied to one Windows PC.** [`app/build.gradle.kts`](remote-movie-pc-downloader/app/build.gradle.kts#L7) sets the build directory to a path under `C:/Users/...`.
- **Website calls a route that doesn't exist.** `torrent-lab.html` (line 927) calls `/api/torrent/stream`, but the server route is `/stream/torrent`.
- **Unpinned script.** The same page loads `webtorrent@latest` with no version or integrity hash.
- **Client has a hardcoded server address.** [`ClientRepository.kt`](pocketnode-client/app/src/main/java/com/pocketnode/client/data/ClientRepository.kt#L21) defaults to `http://192.168.1.100:8765`, there is no discovery, and no persistence code was found in the client.
- **Client polls every 15 seconds** forever (the comment says 60), even in the background.
- **Placeholder data is shipped as real.** "Trending" is a fixed list of four demo torrents, `PosterResolver` is a fixed map of about 20 titles, and the client contains `sample_*` catalogue items.
- **SMB, mesh and host-media streams ignore Range requests**, so seeking won't work on those sources.
- **Client declares `RECORD_AUDIO`**, but no speech-recognition code that uses it was found.
- **Duplicate website files.** `index.html`, `torrent-lab.html` and `vercel.json` exist both at the root and in `pocketnode-website/`, currently byte-identical, and will drift.
- **READMEs describe the old setup.** They cover the PC bot with `U:\movies` paths; the Android server has no setup documentation.

## Code improvements

- **Split the large files.** `JellyfinWebUiBuilder.kt` (3,000 lines of HTML inside Kotlin strings), `TelegramBotEngine.kt`, `WebHttpServer.kt` and `WatchTabScreen.kt` are each over 2,000 lines. Move the web UI to asset files and break routes into per-feature modules.
- **Replace global singletons.** There are 27 `object` singletons and no ViewModels. Introduce ViewModels, a repository layer and dependency injection so the code can be tested.
- **Stop swallowing errors.** There are about 190 broad `catch` blocks, 70 of which discard the exception.
- **Use one HTTP client.** 18 places use raw `HttpURLConnection` while Ktor client is already a dependency; `runBlocking` in `FederatedMediaManager` blocks a thread.
- **Move storage off SharedPreferences JSON blobs** to Room or DataStore, and encrypt credentials.
- **Remove duplicated logic.** The GET/POST handler pairs are copy-pasted, and the Range-parsing code appears more than once.
- **Add tests and CI.** There are no Kotlin tests and no GitHub Actions; the Python side has six test functions.
- **Prepare release builds.** Minification is off, `versionCode` is 1, there is no signing config, and the two apps use different Kotlin and Gradle plugin versions.

## Roadmap

### Phase 0 — Make it safe (about a week)

- [ ] Fix the path traversal and escape all HTML output.
- [ ] Add a pairing token (QR code shown in the app) required on every server, with real password checks for the Jellyfin and qBittorrent logins.
- [ ] Remove the hardcoded admin ID and make first-message pairing require a code shown in the app.
- [ ] Make download endpoints POST-only and restrict CORS.
- [ ] Fix the build directory and the website route.

### Phase 1 — Foundation (2–4 weeks)

- [ ] One Gradle project with shared modules for API models, so client and server stop drifting.
- [ ] ViewModels, a database, encrypted credentials.
- [ ] CI that builds both APKs, runs tests and publishes signed releases.
- [ ] Tests for path safety, Range parsing and the torrent stream session.
- [ ] Retire or clearly separate the Python PC version.

### Phase 2 — Core experience (1–2 months)

- [ ] Server discovery in the client (mDNS) plus QR pairing, with saved servers.
- [ ] Real metadata from TMDb: posters, backdrops, seasons and episodes, replacing the fixed lists.
- [ ] Subtitles: embedded tracks, external `.srt`, and online search.
- [ ] Range support for SMB and mesh sources; cleanup of stream sessions and cache limits.
- [ ] Download queue with storage quotas, low-space handling and Wi-Fi-only or charging-only rules.

### Phase 3 — New features (3+ months)

- [ ] Remote access without port forwarding (Tailscale or WireGuard guide, or a relay) with HTTPS.
- [ ] Multiple users with profiles, per-user watch history and parental controls.
- [ ] Transcoding or remuxing through the bundled FFmpeg for devices that can't direct-play.
- [ ] Watch parties: the client already imports WebSockets and has a schedule screen, but the server has no WebSocket route.
- [ ] RSS and watchlist auto-download, with Trakt sync.
- [ ] Chromecast support and a proper Android TV launcher integration (the client manifest has no Leanback entry).
- [ ] Battery and thermal safeguards: pause torrents when hot or below a battery level.
- [ ] Backup and restore of server configuration.

## A note on content

The project's main use is torrent and Telegram media downloading. Keep the bundled catalogue to openly licensed films (as it is now) and avoid shipping indexer lists, which also matters for any future Play Store listing.
