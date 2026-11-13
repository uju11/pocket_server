# PocketNode Security Policy & Architecture

## 1. Core Principles
- **No Internet-Reachable Endpoints Without Auth**: All core APIs require either valid bearer tokens (`TokenManager`) or scoped temporary access tokens.
- **Default Private Visibility**: Every media file is private by default (`MediaVisibilityManager`). Guest sessions can only see and stream files explicitly marked public.
- **Zero Seeding by Default**: BitTorrent seeding is explicitly disabled on launch (`TorrentEngine.activeSeeds(0)`). Seeding must be intentionally toggled on.
- **No Secrets in Repository**: No credentials, private keys, or API tokens are hardcoded.

## 2. Authentication & Session Management
- **Password Storage**: Passwords are salted (16-byte cryptographically secure random salt) and hashed using SHA-256 (`UserManager`).
- **Session Tokens**: 32-byte cryptographically secure random tokens issued per device upon successful login (`TokenManager`).
- **Revocation**: Tokens can be listed and revoked individually or cleared per user. Revoked tokens are immediately rejected.

## 3. Pairing Protocol
- **Server Identity**: Stable UUID generated once on initial install and persisted in private app preferences (`PairingManager.serverId`).
- **One-Time Pairing**: 16-byte URL-safe base64 one-time passwords (OTP) with a strict 5-minute TTL.
- **Redemption**: Single-use; consumed immediately upon client device registration.

## 4. Storage & Filesystem Isolation
- **Path Traversal Prevention**: All relative file paths requested via Drive, WebDAV, or streaming endpoints are resolved through `StorageManager.resolveRelativePath()`, verifying canonical path containment inside `baseDir`.
- **Scoped Storage**: Operates within Android application sandbox or user-selected SAF DocumentTree.
- **Share Links**: Single-file public download links (`/s/{token}`) are gated by 16-byte random tokens with configurable TTL and explicit revocation.

## 5. Watch Party & Federation
- **Authoritative Host**: Playback state (play/pause/seek) is host-authoritative. Clients cannot manipulate host timeline directly without host synchronization.
- **Guest Invites**: Watch party guest links (`/party/{token}`) provide time-limited, session-scoped access strictly to the active party room and media stream without providing administrative or library access.
- **Local Network Federation**: Discovery broadcasts operate strictly within local IPv4 broadcast scope.
