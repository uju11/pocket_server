import os
import sys
import time
import logging
import re
import asyncio
import subprocess
import urllib.parse
import httpx
from dotenv import load_dotenv
from telethon import TelegramClient, events, Button
from telethon.tl.types import MessageMediaWebPage, MessageMediaUnsupported
from library_scanner import scan_and_organize_libraries

# Load environment variables
load_dotenv()

# Pending interactive search selections map
PENDING_SEARCHES = {}

# Active Telegram media downloads tracker
# { chat_id: { filename, file_size, received, started_at, pct } }
ACTIVE_DOWNLOADS = {}

def format_size(size_bytes: int) -> str:
    if not size_bytes:
        return ""
    if size_bytes >= 1073741824:
        return f"{size_bytes / 1073741824:.2f} GB"
    elif size_bytes >= 1048576:
        return f"{size_bytes / 1048576:.1f} MB"
    return f"{size_bytes} B"

def extract_movie_key(title: str) -> str:
    clean = re.sub(r'[\._\-\[\]\(\)]+', ' ', title)
    clean = re.sub(r'\s+', ' ', clean).strip().lower()
    return clean




# Setup logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s [%(levelname)s] %(message)s',
    handlers=[
        logging.StreamHandler(sys.stdout)
    ]
)

# Load configuration
TELEGRAM_API_ID = os.getenv("TELEGRAM_API_ID")
TELEGRAM_API_HASH = os.getenv("TELEGRAM_API_HASH")
TELEGRAM_BOT_TOKEN = os.getenv("TELEGRAM_BOT_TOKEN")
TELEGRAM_CHANNELS_STR = os.getenv("TELEGRAM_CHANNELS", "")
JACKETT_URL = os.getenv("JACKETT_URL", "http://localhost:9117").rstrip('/')
JACKETT_API_KEY = os.getenv("JACKETT_API_KEY")
QBITTORRENT_URL = os.getenv("QBITTORRENT_URL", "http://localhost:8080").rstrip('/')
QBITTORRENT_USERNAME = os.getenv("QBITTORRENT_USERNAME", "admin")
QBITTORRENT_PASSWORD = os.getenv("QBITTORRENT_PASSWORD", "adminadmin")
DOWNLOAD_DIR = os.getenv("DOWNLOAD_DIR", "D:\\jelly\\New folder")
DIRECT_DOWNLOAD_DIR = os.getenv("DIRECT_DOWNLOAD_DIR", "D:\\jelly\\New folder")
JELLYFIN_URL = os.getenv("JELLYFIN_URL", "http://localhost:8096")
MOVIES_DIR = os.getenv("MOVIES_DIR", "U:\\movies")
ADULT_DIR = os.getenv("ADULT_DIR", "D:\\jelly\\New folder")
TMDB_API_KEY = os.getenv("TMDB_API_KEY", "")
try:
    AUTO_SCAN_INTERVAL_MINS = int(os.getenv("AUTO_SCAN_INTERVAL_MINS", "30"))
except ValueError:
    AUTO_SCAN_INTERVAL_MINS = 30

# Validate required variables
if not TELEGRAM_API_ID or not TELEGRAM_API_HASH:
    logging.critical("CRITICAL: TELEGRAM_API_ID and TELEGRAM_API_HASH must be set in the environment.")
    sys.exit(1)

try:
    TELEGRAM_API_ID = int(TELEGRAM_API_ID)
except ValueError:
    logging.critical("CRITICAL: TELEGRAM_API_ID must be a valid integer.")
    sys.exit(1)

# Helper to parse channels list
def parse_channels(channels_str):
    if not channels_str:
        return []
    channels = []
    for ch in channels_str.split(','):
        ch = ch.strip()
        if not ch:
            continue
        # Check if it's an ID
        if ch.startswith('-100') or ch.isdigit() or (ch.startswith('-') and ch[1:].isdigit()):
            try:
                channels.append(int(ch))
            except ValueError:
                channels.append(ch)
        else:
            channels.append(ch)
    return channels

TELEGRAM_CHANNELS = parse_channels(TELEGRAM_CHANNELS_STR)
logging.info(f"Configured Telegram channels for search: {TELEGRAM_CHANNELS}")

# --- qBittorrent Client ---
class QBittorrentClient:
    def __init__(self, base_url, username, password):
        self.base_url = base_url
        self.username = username
        self.password = password
        self.client = httpx.AsyncClient(timeout=30.0, follow_redirects=True)
        self.authenticated = False

    async def login(self):
        url = f"{self.base_url}/api/v2/auth/login"
        data = {"username": self.username, "password": self.password}
        try:
            response = await self.client.post(url, data=data)
            if response.status_code in (200, 204):
                logging.info("Successfully authenticated with qBittorrent.")
                self.authenticated = True
                return True
            else:
                logging.error(f"qBittorrent auth failed with code {response.status_code}: {response.text}")
                return False
        except Exception as e:
            logging.error(f"Error connecting to qBittorrent at {url}: {e}")
            return False

    async def add_torrent(self, torrent_url, save_path):
        if not self.authenticated:
            success = await self.login()
            if not success:
                logging.error("Cannot add torrent: qBittorrent authentication failed.")
                return False

        url = f"{self.base_url}/api/v2/torrents/add"
        # We pass both urls and savepath parameters. qBittorrent uses form-encoded parameters.
        data = {
            "urls": torrent_url,
            "savepath": save_path
        }
        try:
            response = await self.client.post(url, data=data)
            if response.status_code in (200, 202):
                logging.info(f"Torrent successfully added to qBittorrent. Savepath: {save_path}")
                return "added"
            elif response.status_code == 409:
                logging.info(f"Torrent is already present in qBittorrent download list. Savepath: {save_path}")
                return "exists"
            elif response.status_code == 403:
                logging.warning("qBittorrent returned authorization error (403). Re-authenticating...")
                self.authenticated = False
                if await self.login():
                    response = await self.client.post(url, data=data)
                    if response.status_code in (200, 202):
                        logging.info("Torrent successfully added after re-authentication.")
                        return "added"
                    elif response.status_code == 409:
                        logging.info("Torrent is already present in qBittorrent download list.")
                        return "exists"
            logging.error(f"Failed to add torrent to qBittorrent: Status {response.status_code}, Response: {response.text}")
            return False
        except Exception as e:
            logging.error(f"Exception raised when adding torrent to qBittorrent: {e}")
            return False

    async def close(self):
        await self.client.aclose()


# --- Jackett Search Client ---
async def fetch_single_jackett_query(client, url, q):
    try:
        params = {"apikey": JACKETT_API_KEY, "Query": q}
        resp = await client.get(url, params=params, timeout=75.0)
        if resp.status_code == 200:
            res = resp.json().get("Results", [])
            logging.info(f"Jackett query '{q}' returned {len(res)} results.")
            return res
        else:
            logging.error(f"Jackett returned status {resp.status_code} for query '{q}': {resp.text[:200]}")
    except httpx.ReadTimeout:
        logging.warning(f"Jackett query for '{q}' timed out after 75s (some slow indexers or challenges may have delayed response).")
    except Exception as e:
        logging.error(f"Error querying Jackett for '{q}': {repr(e)}")
    return []

async def search_jackett(movie_name: str, quality: str = None) -> list:
    """
    Search Jackett for torrents matching movie_name and quality.
    """
    if not JACKETT_API_KEY:
        logging.warning("JACKETT_API_KEY is not set. Skipping Jackett search.")
        return []

    url = f"{JACKETT_URL}/api/v2.0/indexers/all/results"
    base_query = f"{movie_name} {quality}" if quality else movie_name

    async with httpx.AsyncClient(timeout=80.0) as client:
        results = await fetch_single_jackett_query(client, url, base_query)
        
        # If quality was specified but returned no results, retry with just movie_name
        if not results and quality:
            logging.info(f"No results for '{base_query}'. Retrying Jackett search for base title '{movie_name}'...")
            results = await fetch_single_jackett_query(client, url, movie_name)

    logging.info(f"Total Jackett results for '{movie_name}': {len(results)}")
    return results


# --- DuckDuckGo Spelling Suggestions ---
async def get_spelling_suggestion(query: str) -> str:
    """
    Fetch autocomplete spelling suggestions using DuckDuckGo keyless API.
    """
    # Suggest spelling for the movie name portion specifically
    search_query = query.split(":::")[0].strip()
    url = "https://ac.duckduckgo.com/ac/"
    params = {"q": search_query, "type": "json"}
    try:
        async with httpx.AsyncClient(timeout=10.0) as client:
            response = await client.get(url, params=params)
            if response.status_code == 200:
                suggestions = response.json()
                if suggestions and isinstance(suggestions, list):
                    # The first suggestion's phrase
                    first_phrase = suggestions[0].get("phrase", "")
                    if first_phrase.lower() != search_query.lower():
                        return first_phrase
    except Exception as e:
        logging.error(f"Failed to fetch spelling suggestion from DuckDuckGo: {e}")
    return None


# --- Telethon Channel Downloader Fallback ---
async def search_and_download_telegram(client: TelegramClient, movie_name: str, quality: str = None) -> str:
    """
    Searches preconfigured Telegram channels for the movie.
    If matching media document is found, downloads it to DOWNLOAD_DIR.
    """
    if not TELEGRAM_CHANNELS:
        logging.warning("No fallback Telegram channels configured. Skipping Telegram search.")
        return None

    words = [w.strip().lower() for w in movie_name.split() if w.strip()]
    if not words:
        return None

    logging.info(f"Starting Telegram search fallback for '{movie_name}' across {len(TELEGRAM_CHANNELS)} channels...")
    
    for channel in TELEGRAM_CHANNELS:
        try:
            logging.info(f"Resolving channel entity: {channel}")
            channel_entity = await client.get_entity(channel)
        except Exception as e:
            logging.warning(f"Could not resolve channel {channel}: {e}. Skipping...")
            continue

        logging.info(f"Searching channel {channel_entity.title if hasattr(channel_entity, 'title') else channel} for '{movie_name}'...")
        
        # Iterate messages matching movie name (server-side search)
        try:
            async for message in client.iter_messages(channel_entity, search=movie_name, limit=50):
                if not message.file:
                    continue

                filename = message.file.name or ""
                filename_lower = filename.lower()
                caption = (message.message or "").lower()

                # Verify all keywords from the movie title match the file name
                if not all(word in filename_lower for word in words):
                    continue

                # If quality is specified, ensure it is in the filename or post caption
                if quality:
                    quality_lower = quality.lower()
                    if quality_lower not in filename_lower and quality_lower not in caption:
                        continue

                # Found a matching media item!
                logging.info(f"Match found in channel: {filename}")
                os.makedirs(DOWNLOAD_DIR, exist_ok=True)
                download_path = os.path.join(DOWNLOAD_DIR, filename)

                # Track download progress
                last_logged_percentage = 0.0
                def progress_callback(received, total):
                    nonlocal last_logged_percentage
                    if not total:
                        return
                    pct = (received / total) * 100
                    if pct - last_logged_percentage >= 10.0 or received == total:
                        logging.info(f"Telegram Download Progress for {filename}: {pct:.1f}% ({received}/{total} bytes)")
                        last_logged_percentage = pct

                logging.info(f"Starting direct Telegram media download to {download_path}...")
                await client.download_media(message, file=download_path, progress_callback=progress_callback)
                logging.info(f"Finished downloading Telegram media file to {download_path}")
                return download_path

        except Exception as e:
            logging.error(f"Error during search/download on channel {channel}: {e}")
            continue

    logging.info("Telegram fallback search complete. No matches found.")
    return None


# --- Telegram Command Listener Event Handler Setup ---
# --- Telegram Command Listener Event Handler Setup ---
# --- Telegram Command Listener Event Handler Setup ---
def setup_handlers(client: TelegramClient, me_id: int = None, is_bot_mode: bool = False, admin_id: int = None):

    async def schedule_auto_delete(message, delay_seconds=60):
        await asyncio.sleep(delay_seconds)
        try:
            await message.delete()
        except Exception:
            pass

    def render_page_content(results, movie_name, page_num=0, items_per_page=5):
        total_items = len(results)
        total_pages = (total_items + items_per_page - 1) // items_per_page
        page_num = max(0, min(page_num, total_pages - 1))
        
        start_idx = page_num * items_per_page
        end_idx = min(start_idx + items_per_page, total_items)
        page_items = results[start_idx:end_idx]
        
        msg_lines = [f"🎬 **Results for '{movie_name}' (Page {page_num + 1}/{total_pages}):**\n"]
        buttons = []
        
        for i, t in enumerate(page_items):
            item_idx = start_idx + i
            title = t.get("Title", "Unknown")
            seeders = t.get("Seeders", 0)
            size_str = format_size(t.get("Size", 0))
            display_num = item_idx + 1
            
            msg_lines.append(f"**{display_num}.** `{title}`\n   📊 {seeders} seeders | 💾 {size_str}\n")
            btn_label = f"{display_num}. {title[:32]}..." if len(title) > 35 else f"{display_num}. {title}"
            buttons.append([Button.inline(btn_label, data=f"sel_{item_idx}".encode('utf-8'))])
            
        nav_row = []
        if page_num > 0:
            nav_row.append(Button.inline("⏪ Prev", data=f"page_{page_num - 1}".encode('utf-8')))
        nav_row.append(Button.inline("❌ Cancel", data=b"cancel"))
        if page_num < total_pages - 1:
            nav_row.append(Button.inline("Next ⏩", data=f"page_{page_num + 1}".encode('utf-8')))
            
        buttons.append(nav_row)
        msg_lines.append(f"👇 **Reply with a number (1-{total_items}), or type:**")
        msg_lines.append("• **`next`** (or **`n`**) -> Next Page")
        msg_lines.append("• **`prev`** (or **`p`**) -> Previous Page")
        msg_lines.append("• **`cancel`** -> Cancel Search")
        msg_lines.append("\n⏱️ *Menu auto-deletes in 180 seconds.*")
        
        return "\n".join(msg_lines), buttons, page_num

    async def execute_download(reply_msg, torrent):
        magnet_link = torrent.get('MagnetUri') or torrent.get('Link')
        title = torrent.get('Title')
        seeders = torrent.get('Seeders', 0)
        
        await reply_msg.edit(
            f"📥 Selected torrent ({seeders} seeders):\n`{title}`\n\nAdding to qBittorrent...",
            buttons=None
        )
        
        qb = QBittorrentClient(QBITTORRENT_URL, QBITTORRENT_USERNAME, QBITTORRENT_PASSWORD)
        result = await qb.add_torrent(magnet_link, DOWNLOAD_DIR)
        await qb.close()
        
        if result == "added" or result is True:
            done_msg = await reply_msg.edit(
                f"✅ Successfully added torrent to qBittorrent:\n`{title}`\n\nSaving to: `{DOWNLOAD_DIR}`\n\n⏱️ *Message auto-deletes in 15 seconds.*",
                buttons=None
            )
            asyncio.create_task(schedule_auto_delete(done_msg, 15))
        elif result == "exists":
            done_msg = await reply_msg.edit(
                f"ℹ️ Torrent is already present in qBittorrent queue:\n`{title}`\n\nSaving to: `{DOWNLOAD_DIR}`\n\n⏱️ *Message auto-deletes in 15 seconds.*",
                buttons=None
            )
            asyncio.create_task(schedule_auto_delete(done_msg, 15))
        else:
            await reply_msg.edit("⚠️ Failed to push torrent to qBittorrent.", buttons=None)

    @client.on(events.CallbackQuery)
    async def handle_callback(event):
        data = event.data.decode('utf-8')
        chat_id = event.chat_id
        
        if data == "cancel":
            if chat_id in PENDING_SEARCHES:
                timer = PENDING_SEARCHES[chat_id].get("timer")
                if timer:
                    timer.cancel()
                del PENDING_SEARCHES[chat_id]
            msg = await event.edit("❌ Search cancelled.", buttons=None)
            asyncio.create_task(schedule_auto_delete(msg, 5))
            return

        if data.startswith("page_"):
            page_num = int(data.split("_")[1])
            pending = PENDING_SEARCHES.get(chat_id)
            if pending:
                results = pending.get("results", [])
                movie_name = pending.get("movie_name", "")
                text, buttons, new_page = render_page_content(results, movie_name, page_num)
                pending["page"] = new_page
                await event.edit(text, buttons=buttons)
            else:
                msg = await event.edit("⚠️ Selection expired. Please search again.", buttons=None)
                asyncio.create_task(schedule_auto_delete(msg, 5))
            return

        if data.startswith("sel_"):
            idx = int(data.split("_")[1])
            pending = PENDING_SEARCHES.get(chat_id)
            if pending and idx < len(pending.get("results", [])):
                timer = pending.get("timer")
                if timer:
                    timer.cancel()
                selected_torrent = pending["results"][idx]
                reply_msg = pending["reply_msg"]
                del PENDING_SEARCHES[chat_id]
                await execute_download(reply_msg, selected_torrent)
            else:
                msg = await event.edit("⚠️ Selection expired. Please search again.", buttons=None)
                asyncio.create_task(schedule_auto_delete(msg, 5))

    # --- Shared File / Forwarded Media Auto-Downloader ---
    @client.on(events.NewMessage(func=lambda e: e.is_private and bool(e.file)))
    async def handle_media_message(event):
        """Auto-download any file shared/forwarded to Saved Messages."""
        if event.out:
            return

        if is_bot_mode:
            if admin_id and event.sender_id != admin_id:
                return
            if me_id and event.sender_id == me_id:
                return
        else:
            if me_id and event.sender_id != me_id:
                return

        # Skip webpage URL previews and unsupported media — these are not real files
        if isinstance(event.message.media, (MessageMediaWebPage, MessageMediaUnsupported)):
            return

        file = event.message.file
        # Derive a sensible filename
        filename = (file.name or "").strip()
        if not filename:
            ext = ""
            if file.mime_type:
                ext_map = {
                    "video/mp4": ".mp4", "video/x-matroska": ".mkv",
                    "video/x-msvideo": ".avi", "video/quicktime": ".mov",
                    "audio/mpeg": ".mp3", "audio/flac": ".flac",
                    "audio/x-wav": ".wav", "application/zip": ".zip",
                    "application/x-rar-compressed": ".rar",
                    "application/pdf": ".pdf",
                }
                ext = ext_map.get(file.mime_type, "")
            filename = f"telegram_{event.message.id}{ext}"

        file_size = file.size or 0
        chat_id = event.chat_id
        started_at = time.monotonic()
        logging.info(f"Auto-download triggered: '{filename}' ({format_size(file_size)})")

        # Register in global tracker so /status can query it
        ACTIVE_DOWNLOADS[chat_id] = {
            "filename": filename,
            "file_size": file_size,
            "received": 0,
            "pct": 0.0,
            "started_at": started_at,
        }

        reply_msg = await event.reply(
            f"📥 **File detected in Saved Messages!**\n"
            f"📄 `{filename}`\n"
            f"💾 Size: {format_size(file_size)}\n"
            f"⏳ Starting download... (send `status` anytime for a progress update)"
        )

        os.makedirs(DOWNLOAD_DIR, exist_ok=True)
        download_path = os.path.join(DOWNLOAD_DIR, filename)

        # --- Resume support ---
        # Telethon's iter_download offset must be aligned to CHUNK_SIZE (512 KB).
        # Any extra bytes beyond the aligned boundary are truncated and re-downloaded.
        CHUNK_SIZE = 512 * 1024  # 512 KB — matches Telethon's default request_size

        existing_bytes = os.path.getsize(download_path) if os.path.exists(download_path) else 0
        resume_offset = (existing_bytes // CHUNK_SIZE) * CHUNK_SIZE  # align down

        if file_size and resume_offset >= file_size:
            # File is already fully present on disk
            ACTIVE_DOWNLOADS.pop(chat_id, None)
            logging.info(f"File already complete on disk: {download_path}")
            done_msg = await reply_msg.edit(
                f"\u2705 **Already downloaded!**\n"
                f"\ud83d\udcc4 `{filename}`\n"
                f"\ud83d\udcbe Size: {format_size(file_size)}\n"
                f"\ud83d\udcc2 Saved to: `{DOWNLOAD_DIR}`\n\n"
                f"\u23f1\ufe0f *This message auto-deletes in 15 seconds.*"
            )
            asyncio.create_task(schedule_auto_delete(done_msg, 15))
            return

        if resume_offset > 0:
            logging.info(
                f"Resuming '{filename}' from {format_size(resume_offset)} "
                f"(had {format_size(existing_bytes)}, aligned to chunk boundary)"
            )
            await reply_msg.edit(
                f"\u23e9 **Resuming download...**\n"
                f"\ud83d\udcc4 `{filename}`\n"
                f"\ud83d\udcbe Already downloaded: {format_size(resume_offset)} / {format_size(file_size)} "
                f"({resume_offset / file_size * 100:.1f}%)\n"
                f"\u23f3 Continuing... (send `status` anytime)"
            )

        # Update tracker with resume starting point
        ACTIVE_DOWNLOADS[chat_id]["received"] = resume_offset
        ACTIVE_DOWNLOADS[chat_id]["pct"] = (resume_offset / file_size * 100) if file_size else 0.0

        # Progress callback — called manually in the iter_download loop below
        last_pct = [ACTIVE_DOWNLOADS[chat_id]["pct"]]
        last_log_ts = [0.0]
        last_edit_ts = [0.0]

        async def progress_callback(received, total):
            if not total:
                return
            pct = (received / total) * 100
            now = time.monotonic()

            # Always keep global tracker up-to-date (used by /status)
            if chat_id in ACTIVE_DOWNLOADS:
                ACTIVE_DOWNLOADS[chat_id]["received"] = received
                ACTIVE_DOWNLOADS[chat_id]["pct"] = pct

            # Log every 5% OR every 30 s — whichever fires first
            should_log = (pct - last_pct[0] >= 5.0 or now - last_log_ts[0] >= 30.0 or received == total)
            # Throttle Telegram message edits to once every 5 s (avoid FloodWait)
            should_edit = should_log and (now - last_edit_ts[0] >= 5.0)

            if should_log:
                last_pct[0] = pct
                last_log_ts[0] = now
                bar_filled = int(pct / 10)
                bar = "\u2588" * bar_filled + "\u2591" * (10 - bar_filled)
                logging.info(
                    f"Telegram DL [{bar}] {pct:.1f}% \u2014 "
                    f"{format_size(received)} / {format_size(file_size)}  '{filename}'"
                )
                if should_edit:
                    last_edit_ts[0] = now
                    resume_note = f" *(resumed from {format_size(resume_offset)})*" if resume_offset > 0 else ""
                    try:
                        await reply_msg.edit(
                            f"\ud83d\udce5 **Downloading from Telegram...**{resume_note}\n"
                            f"\ud83d\udcc4 `{filename}`\n"
                            f"\n[{bar}] {pct:.1f}%\n"
                            f"\ud83d\udcbe {format_size(received)} / {format_size(file_size)}"
                        )
                    except Exception as edit_err:
                        logging.warning(f"Could not update progress message: {edit_err}")

        try:
            # Open file: truncate-to-aligned-boundary if resuming, else create fresh
            if resume_offset > 0:
                try:
                    with open(download_path, 'r+b') as f:
                        f.seek(resume_offset)
                        f.truncate()
                except OSError as trunc_err:
                    logging.error(f"Failed to truncate '{filename}' for resume: {trunc_err}")
                    # Fall back to starting fresh if truncate fails
                    resume_offset = 0
                    ACTIVE_DOWNLOADS[chat_id]["received"] = 0
                    ACTIVE_DOWNLOADS[chat_id]["pct"] = 0.0

            file_handle = open(download_path, 'ab' if resume_offset > 0 else 'wb')

            received_total = resume_offset
            try:
                async for chunk in client.iter_download(
                    event.message,
                    offset=resume_offset,
                    request_size=CHUNK_SIZE,
                    file_size=file_size,
                ):
                    file_handle.write(chunk)
                    received_total += len(chunk)
                    await progress_callback(received_total, file_size)
            finally:
                file_handle.close()

            ACTIVE_DOWNLOADS.pop(chat_id, None)
            logging.info(f"Successfully downloaded to {download_path}")
            done_msg = await reply_msg.edit(
                f"\u2705 **Download complete!**\n"
                f"\ud83d\udcc4 `{filename}`\n"
                f"\ud83d\udcbe Size: {format_size(file_size)}\n"
                f"\ud83d\udcc2 Saved to: `{DOWNLOAD_DIR}`\n\n"
                f"\u23f1\ufe0f *This message auto-deletes in 15 seconds.*"
            )
            asyncio.create_task(schedule_auto_delete(done_msg, 15))
            # Trigger automatic library organization scan
            asyncio.create_task(scan_and_organize_libraries(MOVIES_DIR, ADULT_DIR, TMDB_API_KEY))

        except OSError as e:
            import errno as errno_mod
            ACTIVE_DOWNLOADS.pop(chat_id, None)
            logging.error(f"OSError downloading '{filename}': [Errno {e.errno}] {e}")

            # Give the user a specific, actionable error message based on errno
            if e.errno == errno_mod.EIO:          # 5 — hardware / mount I/O error
                cause = (
                    "💾 **Disk I/O error** — the storage drive may be:\n"
                    "• Disconnected (USB/NAS drive unplugged)\n"
                    "• In a bad state (restart Docker Desktop and reconnect the drive)\n"
                    f"• Target path: `{DOWNLOAD_DIR}`"
                )
            elif e.errno == errno_mod.ENOSPC:     # 28 — no space left
                cause = (
                    "💾 **Disk full!** — not enough space on the target drive.\n"
                    f"• Free up space at `{DOWNLOAD_DIR}` and try again."
                )
            elif e.errno == errno_mod.EACCES:     # 13 — permission denied
                cause = (
                    "🔒 **Permission denied** — the bot cannot write to the target folder.\n"
                    f"• Check write permissions on `{DOWNLOAD_DIR}`."
                )
            elif e.errno == errno_mod.ENOENT:     # 2 — path doesn't exist
                cause = (
                    "📂 **Path not found** — the download folder doesn't exist inside the container.\n"
                    f"• Expected path: `{DOWNLOAD_DIR}`\n"
                    "• Check the Docker volume mount in `docker-compose.yml`."
                )
            else:
                cause = f"OS error [{e.errno}]: `{e}`"

            await reply_msg.edit(
                f"❌ **Download failed!**\n"
                f"📄 `{filename}`\n\n"
                f"{cause}\n\n"
                f"💡 Partial file kept — re-forward the file to resume once the issue is fixed."
            )

        except Exception as e:
            ACTIVE_DOWNLOADS.pop(chat_id, None)
            logging.error(f"Error downloading '{filename}': {e}")
            await reply_msg.edit(
                f"\u274c **Download failed!**\n"
                f"\ud83d\udcc4 `{filename}`\n"
                f"Error: `{e}`\n\n"
                f"\ud83d\udca1 Partial file kept at `{download_path}` \u2014 re-forward the file to resume."
            )

    @client.on(events.NewMessage(pattern=r'(?i).+'))
    async def handle_new_message(event):
        if not event.is_private:
            return
            
        if event.out:
            return

        sender = await event.get_sender()
        if sender and getattr(sender, 'bot', False):
            return

        if is_bot_mode:
            if admin_id and event.sender_id != admin_id and event.chat_id != admin_id:
                return
            if me_id and event.sender_id == me_id:
                return
        else:
            if me_id and event.sender_id != me_id:
                return

        text = event.message.text.strip()
        if not text:
            return

        # Guard against automated bot responses or notification loops
        if any(text.startswith(p) for p in ("🔍", "❌", "✅", "📥", "🎉", "🟢", "📂", "🎬", "⚠️", "⏱️", "📊", "•", "ℹ️", "🔴", "🟡")) or "auto-deletes in" in text.lower():
            return

        chat_id = event.chat_id

        # 0a. STATUS CHECK — send "status" or "/status" to get live download progress
        if text.lower().strip() in ("status", "/status", "progress", "/progress"):
            if chat_id in ACTIVE_DOWNLOADS:
                dl = ACTIVE_DOWNLOADS[chat_id]
                pct = dl["pct"]
                received = dl["received"]
                file_size = dl["file_size"]
                filename = dl["filename"]
                elapsed = time.monotonic() - dl["started_at"]
                bar_filled = int(pct / 10)
                bar = "█" * bar_filled + "░" * (10 - bar_filled)
                elapsed_str = f"{int(elapsed // 60)}m {int(elapsed % 60)}s"
                # Estimate remaining time
                if received > 0 and file_size > received:
                    speed = received / elapsed  # bytes/s
                    remaining_bytes = file_size - received
                    eta_s = remaining_bytes / speed
                    eta_str = f"{int(eta_s // 60)}m {int(eta_s % 60)}s"
                else:
                    eta_str = "calculating..."
                status_msg = await event.reply(
                    f"📊 **Download Status**\n"
                    f"📄 `{filename}`\n\n"
                    f"[{bar}] **{pct:.1f}%**\n"
                    f"💾 {format_size(received)} / {format_size(file_size)}\n"
                    f"⏱️ Elapsed: {elapsed_str}\n"
                    f"⏳ ETA: ~{eta_str}"
                )
                asyncio.create_task(schedule_auto_delete(status_msg, 30))
            else:
                status_msg = await event.reply("✅ No active downloads right now.")
                asyncio.create_task(schedule_auto_delete(status_msg, 10))
            try:
                await event.delete()
            except Exception:
                pass
            return

        # 0b. JELLYFIN STATUS — send "jelly" to get server status and IP
        if text.lower().strip() in ("jelly", "/jelly", "jellyfin", "/jellyfin"):
            import socket
            JELLYFIN_SERVICE_NAME = os.getenv("JELLYFIN_SERVICE_NAME", "JellyfinServer")

            # Resolve local IP
            try:
                hostname = socket.gethostname()
                local_ip = socket.gethostbyname(hostname)
            except Exception:
                local_ip = "unknown"

            jellyfin_url_clean = JELLYFIN_URL.rstrip('/')
            health_url = f"{jellyfin_url_clean}/health"

            async def check_jellyfin_health():
                """Returns (status_str, health_text, is_online)."""
                try:
                    async with httpx.AsyncClient(timeout=8.0) as hclient:
                        resp = await hclient.get(health_url)
                    if resp.status_code == 200:
                        return "🟢 Online", (resp.text.strip()[:120] or "Healthy"), True
                    return f"🟡 Responded ({resp.status_code})", resp.text.strip()[:120], True
                except httpx.ConnectError:
                    return "🔴 Offline / unreachable", "Could not connect", False
                except httpx.TimeoutException:
                    return "🟡 Timed out", "No response within 8s", False
                except Exception as jex:
                    return "❓ Unknown", str(jex)[:120], False

            async def fetch_jellyfin_info():
                """Returns (server_name, server_version)."""
                try:
                    async with httpx.AsyncClient(timeout=8.0) as hclient:
                        info_resp = await hclient.get(f"{jellyfin_url_clean}/System/Info/Public")
                    if info_resp.status_code == 200:
                        info = info_resp.json()
                        return info.get("ServerName", ""), info.get("Version", "")
                except Exception:
                    pass
                return "", ""

            server_status, health_text, is_online = await check_jellyfin_health()

            # ── Auto-start if offline ─────────────────────────────────────────
            if not is_online:
                jelly_msg = await event.reply(
                    f"🔴 **Jellyfin is offline!**\n"
                    f"🏠 Local IP: `{local_ip}`\n"
                    f"━━━━━━━━━━━━━━━━━━━\n"
                    f"🚀 Starting Windows service `{JELLYFIN_SERVICE_NAME}`...\n"
                    f"⏳ Please wait..."
                )
                try:
                    await event.delete()
                except Exception:
                    pass

                # Start the Windows service
                try:
                    start_proc = await asyncio.create_subprocess_exec(
                        r"C:\Windows\System32\sc.exe", "start", JELLYFIN_SERVICE_NAME,
                        stdout=subprocess.PIPE,
                        stderr=subprocess.STDOUT,
                    )
                    sc_stdout, _ = await start_proc.communicate()
                    sc_output = sc_stdout.decode(errors='replace').strip()
                    logging.info(f"[sc start {JELLYFIN_SERVICE_NAME}] {sc_output}")

                    if start_proc.returncode not in (0, 1056):  # 1056 = already running
                        await jelly_msg.edit(
                            f"❌ **Failed to start Jellyfin service.**\n"
                            f"`sc` exit code: `{start_proc.returncode}`\n"
                            f"Output: `{sc_output[:300]}`"
                        )
                        return
                except FileNotFoundError:
                    await jelly_msg.edit(
                        "❌ `C:\\Windows\\System32\\sc.exe` not found on this system.\n"
                        "Please start Jellyfin manually."
                    )
                    return
                except Exception as sc_err:
                    await jelly_msg.edit(f"❌ Error starting service: `{sc_err}`")
                    return

                # Poll until Jellyfin is back up (max 45 seconds)
                await jelly_msg.edit(
                    f"🟡 **Starting Jellyfin...**\n"
                    f"🏠 Local IP: `{local_ip}`\n"
                    f"━━━━━━━━━━━━━━━━━━━\n"
                    f"⏳ Waiting for server to come online (up to 45s)..."
                )
                poll_interval = 5
                max_polls = 9  # 9 × 5s = 45s
                came_online = False
                for attempt in range(1, max_polls + 1):
                    await asyncio.sleep(poll_interval)
                    server_status, health_text, is_online = await check_jellyfin_health()
                    if is_online:
                        came_online = True
                        break
                    elapsed = attempt * poll_interval
                    try:
                        await jelly_msg.edit(
                            f"🟡 **Starting Jellyfin...**\n"
                            f"🏠 Local IP: `{local_ip}`\n"
                            f"━━━━━━━━━━━━━━━━━━━\n"
                            f"⏳ Still starting... ({elapsed}s elapsed, checking every {poll_interval}s)"
                        )
                    except Exception:
                        pass

                if not came_online:
                    await jelly_msg.edit(
                        f"⚠️ **Jellyfin did not come online within 45 seconds.**\n"
                        f"🏠 Local IP: `{local_ip}`\n"
                        f"Service `{JELLYFIN_SERVICE_NAME}` was started but is still not responding.\n"
                        f"Check Jellyfin logs for errors."
                    )
                    return

            # ── Server is online — show status ───────────────────────────────
            server_name, server_version = await fetch_jellyfin_info()

            version_line = f"\n🏷️ Version: `{server_version}`" if server_version else ""
            name_line = f"\n🖥️ Server: `{server_name}`" if server_name else ""

            was_started = not is_online  # False at this point means it came up via auto-start
            status_header = "🚀 **Jellyfin started successfully!**" if health_text == "Could not connect" else "🎬 **Jellyfin Status**"
            # Correct header: if we went through the auto-start path, say so
            # We track via a flag set before the block

            jelly_text = (
                f"🎬 **Jellyfin Status**\n"
                f"━━━━━━━━━━━━━━━━━━━\n"
                f"📡 Status: {server_status}\n"
                f"🌐 URL: `{jellyfin_url_clean}`\n"
                f"🏠 Local IP: `{local_ip}`{name_line}{version_line}\n"
                f"💬 Health: `{health_text}`\n"
                f"━━━━━━━━━━━━━━━━━━━\n"
                f"⏱️ *Auto-deletes in 30 seconds.*"
            )

            if "jelly_msg" in dir():
                # We already have a message from the startup sequence — edit it
                await jelly_msg.edit(jelly_text)
            else:
                jelly_msg = await event.reply(jelly_text)
                try:
                    await event.delete()
                except Exception:
                    pass

            asyncio.create_task(schedule_auto_delete(jelly_msg, 30))
            return

        # 0c. AUTO LIBRARY SCAN & ORGANIZER — send "scan" or "/scan"
        if text.lower().strip() in ("scan", "/scan", "organize", "/organize"):
            scan_msg = await event.reply(
                "🔍 **Starting Library Scan...**\n"
                f"🎬 Movies: `{MOVIES_DIR}`\n"
                f"🔞 Adult: `{ADULT_DIR}`\n"
                "⏳ Initializing scan..."
            )
            try:
                await event.delete()
            except Exception:
                pass

            last_update_time = [0.0]

            async def scan_progress_update(phase: str, current: int, total: int, current_item: str, moved_count: int):
                import time
                now = time.time()
                # Throttle updates to avoid Telegram flood limits (every 2.5s or on last item)
                if now - last_update_time[0] >= 2.5 or current == total:
                    last_update_time[0] = now
                    pct = int((current / total * 100)) if total > 0 else 0
                    short_item = (current_item[:40] + '...') if len(current_item) > 43 else current_item
                    # Clean filename for display
                    short_item = short_item.replace('`', "'")
                    prog_text = (
                        "🔍 **Library Scan in Progress...**\n"
                        "━━━━━━━━━━━━━━━━━━━━━━━\n"
                        f"📂 **Folder:** `{phase}`\n"
                        f"📊 **Progress:** {current}/{total} ({pct}%)\n"
                        f"🔎 **Checking:** `{short_item}`\n"
                        f"📦 **Items Moved:** {moved_count}"
                    )
                    try:
                        await scan_msg.edit(prog_text)
                    except Exception as prog_err:
                        logging.debug(f"[Bot] Scan progress edit skipped: {prog_err}")

            try:
                results = await scan_and_organize_libraries(
                    MOVIES_DIR,
                    ADULT_DIR,
                    TMDB_API_KEY,
                    progress_callback=scan_progress_update
                )

                moved_adult = results.get("moved_to_adult", [])
                moved_movies = results.get("moved_to_movies", [])
                errors = results.get("errors", [])
                movies_scanned = results.get("scanned_movies_count", 0)
                adult_scanned = results.get("scanned_adult_count", 0)

                report_lines = [
                    "📊 **Library Scan & Organization Report**",
                    "━━━━━━━━━━━━━━━━━━━━━━━",
                    f"🎬 **Movies Scanned:** {movies_scanned}",
                    f"🔞 **Adult Media Scanned:** {adult_scanned}",
                    ""
                ]

                if moved_adult:
                    report_lines.append(f"📦 **Moved to Adult Folder ({len(moved_adult)}):**")
                    for item in moved_adult[:10]:
                        safe_name = str(item['item']).replace('`', "'")
                        safe_reason = str(item['reason']).replace('`', "'")
                        report_lines.append(f"• `{safe_name}`\n  ↳ `{safe_reason}`")
                    if len(moved_adult) > 10:
                        report_lines.append(f"• _...and {len(moved_adult) - 10} more_")
                    report_lines.append("")

                if moved_movies:
                    report_lines.append(f"🎬 **Moved to Movies Folder ({len(moved_movies)}):**")
                    for item in moved_movies[:10]:
                        safe_name = str(item['item']).replace('`', "'")
                        safe_reason = str(item['reason']).replace('`', "'")
                        report_lines.append(f"• `{safe_name}`\n  ↳ `{safe_reason}`")
                    if len(moved_movies) > 10:
                        report_lines.append(f"• _...and {len(moved_movies) - 10} more_")
                    report_lines.append("")

                if not moved_adult and not moved_movies and not errors:
                    report_lines.append("✅ **All media files are correctly organized!**")

                if errors:
                    report_lines.append("⚠️ **Errors / Warnings:**")
                    for err in errors[:5]:
                        safe_err = str(err).replace('`', "'")
                        report_lines.append(f"• `{safe_err}`")

                report_lines.append("\n⏱️ *Auto-deletes in 60 seconds.*")
                final_text = "\n".join(report_lines)

                try:
                    await scan_msg.edit(final_text)
                except Exception as edit_err:
                    logging.warning(f"[Bot] Markdown scan report edit failed: {edit_err}, falling back to plain text")
                    try:
                        await scan_msg.edit(final_text, parse_mode=None)
                    except Exception:
                        pass

                asyncio.create_task(schedule_auto_delete(scan_msg, 60))
            except Exception as scan_exc:
                logging.exception(f"[Bot] Exception during /scan handler:")
                try:
                    await scan_msg.edit(f"❌ **Scan Error:** `{str(scan_exc)}`")
                except Exception:
                    pass
            return

        # 0c. DIRECT MAGNET LINK SUPPORT: e.g. "/magnet ::: magnet:?xt=..." or "magnet:?xt=..."
        if text.startswith("magnet:?") or text.startswith("/magnet") or (":::" in text and "magnet:?xt=" in text):
            magnet_link = text
            if ":::" in text:
                magnet_link = text.split(":::", 1)[1].strip()
            
            magnet_link = re.sub(r'^/magnet\s*', '', magnet_link).strip()
            if not magnet_link.startswith("magnet:?"):
                return
            
            reply_msg = await event.reply("📥 Adding magnet link directly to qBittorrent...")
            qb = QBittorrentClient(QBITTORRENT_URL, QBITTORRENT_USERNAME, QBITTORRENT_PASSWORD)
            result = await qb.add_torrent(magnet_link, DOWNLOAD_DIR)
            await qb.close()
            
            if result == "added" or result is True:
                done_msg = await reply_msg.edit(
                    f"✅ Magnet link successfully added to qBittorrent!\nSaving to: `{DOWNLOAD_DIR}`\n\n⏱️ *Message auto-deletes in 15 seconds.*"
                )
                asyncio.create_task(schedule_auto_delete(done_msg, 15))
            elif result == "exists":
                done_msg = await reply_msg.edit(
                    f"ℹ️ Magnet link is already present in qBittorrent queue!\nSaving to: `{DOWNLOAD_DIR}`\n\n⏱️ *Message auto-deletes in 15 seconds.*"
                )
                asyncio.create_task(schedule_auto_delete(done_msg, 15))
            else:
                await reply_msg.edit("❌ Failed to add magnet link to qBittorrent.")
            return

        # 0c. DIRECT URL DOWNLOAD: "link ::: <url>" — downloads via yt-dlp Python API
        if text.lower().startswith("link") and ":::" in text:
            url_part = text.split(":::", 1)[1].strip()

            if not url_part:
                err = await event.reply(
                    "❌ No URL provided.\n"
                    "Usage: `link ::: https://youtube.com/watch?v=...`"
                )
                asyncio.create_task(schedule_auto_delete(err, 15))
                return

            # Auto-prepend https:// if no protocol given (e.g. youtu.be/... or youtube.com/...)
            if not url_part.startswith("http://") and not url_part.startswith("https://"):
                url_part = "https://" + url_part
                logging.info(f"Auto-prepended https:// to URL: {url_part}")

            os.makedirs(DIRECT_DOWNLOAD_DIR, exist_ok=True)
            reply_msg = await event.reply(
                f"🔗 **Direct URL Download**\n"
                f"🌐 URL: `{url_part}`\n"
                f"📂 Saving to: `{DIRECT_DOWNLOAD_DIR}`\n"
                f"⏳ Starting download..."
            )

            async def run_yt_dlp_download(url, dest_dir, reply):
                import yt_dlp as ytdlp_module
                output_template = os.path.join(dest_dir, "%(title)s.%(ext)s")
                loop = asyncio.get_event_loop()
                last_edit_ts = [0.0]

                def progress_hook(d):
                    if d.get("status") == "downloading":
                        pct   = d.get("_percent_str", "?").strip()
                        speed = d.get("_speed_str",   "?").strip()
                        eta   = d.get("_eta_str",     "?").strip()
                        logging.info(f"[yt-dlp] {pct} speed={speed} eta={eta}")
                        now = time.monotonic()
                        if now - last_edit_ts[0] >= 5.0:
                            last_edit_ts[0] = now
                            asyncio.run_coroutine_threadsafe(
                                reply.edit(
                                    f"📥 **Downloading...**\n"
                                    f"🌐 `{url}`\n"
                                    f"📊 **{pct}** | ⚡ {speed} | ⏳ ETA: {eta}\n"
                                    f"📂 Saving to: `{dest_dir}`"
                                ),
                                loop
                            )
                    elif d.get("status") == "finished":
                        logging.info(f"[yt-dlp] Finished downloading: {d.get('filename', '')}")

                def do_download():
                    ydl_opts = {
                        "outtmpl": output_template,
                        "no_playlist": True,
                        "progress_hooks": [progress_hook],
                        "quiet": False,
                        "no_warnings": False,
                    }
                    with ytdlp_module.YoutubeDL(ydl_opts) as ydl:
                        ydl.download([url])

                try:
                    await loop.run_in_executor(None, do_download)
                    done_msg = await reply.edit(
                        f"✅ **Download complete!**\n"
                        f"🌐 `{url}`\n"
                        f"📂 Saved to: `{dest_dir}`\n\n"
                        f"⏱️ *Message auto-deletes in 15 seconds.*"
                    )
                    asyncio.create_task(schedule_auto_delete(done_msg, 15))
                    # Trigger automatic library organization scan
                    asyncio.create_task(scan_and_organize_libraries(MOVIES_DIR, ADULT_DIR, TMDB_API_KEY))
                except ytdlp_module.utils.DownloadError as de:
                    err_msg = str(de).strip()
                    logging.error(f"[yt-dlp] DownloadError: {err_msg}")
                    await reply.edit(
                        f"❌ **Download failed!**\n"
                        f"🌐 `{url}`\n"
                        f"ℹ️ `{err_msg[:400]}`"
                    )
                except Exception as exc:
                    logging.error(f"[yt-dlp] Unexpected error: {exc}")
                    await reply.edit(f"❌ **Download error:** `{str(exc)[:300]}`")

            asyncio.create_task(run_yt_dlp_download(url_part, DIRECT_DOWNLOAD_DIR, reply_msg))
            return



        # 1. PENDING SEARCH INTERACTIVE COMMANDS (Number selection, Next, Prev, Cancel)
        if chat_id in PENDING_SEARCHES:
            t_low = text.lower()
            pending = PENDING_SEARCHES[chat_id]
            results = pending.get("results", [])
            movie_name = pending.get("movie_name", "")
            current_page = pending.get("page", 0)
            reply_msg = pending.get("reply_msg")

            if t_low in ("next", "n", ">", ">>"):
                text_c, buttons_c, new_p = render_page_content(results, movie_name, current_page + 1)
                pending["page"] = new_p
                await reply_msg.edit(text_c, buttons=buttons_c)
                try:
                    await event.delete()
                except Exception:
                    pass
                return

            if t_low in ("prev", "p", "<", "<<"):
                text_c, buttons_c, new_p = render_page_content(results, movie_name, current_page - 1)
                pending["page"] = new_p
                await reply_msg.edit(text_c, buttons=buttons_c)
                try:
                    await event.delete()
                except Exception:
                    pass
                return

            if t_low in ("cancel", "c", "exit", "stop"):
                timer = pending.get("timer")
                if timer:
                    timer.cancel()
                del PENDING_SEARCHES[chat_id]
                msg = await reply_msg.edit("❌ Search cancelled.", buttons=None)
                asyncio.create_task(schedule_auto_delete(msg, 5))
                try:
                    await event.delete()
                except Exception:
                    pass
                return

            if text.isdigit():
                idx = int(text) - 1
                if 0 <= idx < len(results):
                    timer = pending.get("timer")
                    if timer:
                        timer.cancel()
                    selected_torrent = results[idx]
                    del PENDING_SEARCHES[chat_id]
                    try:
                        await event.delete()
                    except Exception:
                        pass
                    await execute_download(reply_msg, selected_torrent)
                    return

        # Require ::: format to start a new search
        if ":::" not in text:
            return

        # Parse "Movie Name ::: Quality" or "Movie Name ::: search"
        parts = text.split(":::", 1)
        movie_name = parts[0].strip()
        if not movie_name or movie_name.startswith(("•", "*", "-", "#")):
            return
        raw_quality = parts[1].strip() if len(parts) > 1 else None
        
        quality = None
        if raw_quality and raw_quality.lower() not in ("search", "all", "any", "full", "none", ""):
            quality = raw_quality

        reply_msg = await event.reply(
            f"🔍 Processing query: **{movie_name}**" + (f" (Quality: {quality})" if quality else "") + "..."
        )
        
        try:
            # 1. Search Jackett
            await reply_msg.edit(f"🔍 Searching Jackett for torrents...")
            torrent_results = await search_jackett(movie_name, quality)
            
            # Filter and sort results by seeders
            valid_results = [r for r in torrent_results if r.get('Seeders', 0) > 0]
            valid_results.sort(key=lambda x: x.get('Seeders', 0), reverse=True)
            
            # Deduplicate exact duplicate links while retaining all distinct releases/parts
            distinct_results = []
            seen_links = set()
            for r in valid_results:
                link = r.get("MagnetUri") or r.get("Link") or r.get("Title")
                if link not in seen_links:
                    seen_links.add(link)
                    distinct_results.append(r)

            top_results = distinct_results if distinct_results else valid_results

            if len(top_results) == 1:
                # Only 1 match found - download immediately
                await execute_download(reply_msg, top_results[0])
                return
            elif len(top_results) > 1:
                # Multiple matches found - render Paginated Menu with Next/Prev
                text_content, buttons, current_page = render_page_content(top_results, movie_name, page_num=0)
                
                async def auto_delete_task():
                    await asyncio.sleep(180)
                    if chat_id in PENDING_SEARCHES and PENDING_SEARCHES[chat_id]["reply_msg"].id == reply_msg.id:
                        del PENDING_SEARCHES[chat_id]
                        try:
                            await reply_msg.delete()
                        except Exception:
                            pass

                PENDING_SEARCHES[chat_id] = {
                    "results": top_results,
                    "movie_name": movie_name,
                    "page": current_page,
                    "reply_msg": reply_msg,
                    "timer": asyncio.create_task(auto_delete_task())
                }
                
                await reply_msg.edit(
                    text_content,
                    buttons=buttons
                )
                return

            # 2. Telegram Fallback Search
            await reply_msg.edit(f"⚠️ Torrent not found or low seeders. Searching Telegram fallback channels...")
            downloaded_file = await search_and_download_telegram(client, movie_name, quality)
            
            if downloaded_file:
                filename = os.path.basename(downloaded_file)
                done_msg = await reply_msg.edit(
                    f"✅ Successfully downloaded via Telegram fallback:\n`{filename}`\n\nSaved to: `{DOWNLOAD_DIR}`\n\n⏱️ *Message auto-deletes in 15 seconds.*",
                    buttons=None
                )
                asyncio.create_task(schedule_auto_delete(done_msg, 15))
                return

            # 3. Spelling Suggestion Fallback
            await reply_msg.edit("🔍 Searching for spelling suggestions...")
            suggestion = await get_spelling_suggestion(movie_name)
            
            if suggestion:
                err_msg = await reply_msg.edit(
                    f"❌ No matches found for **{movie_name}**.\n\nDid you mean: **{suggestion}**?\n"
                    f"Please try searching again with the corrected name.\n\n⏱️ *Message auto-deletes in 30 seconds.*",
                    buttons=None
                )
                asyncio.create_task(schedule_auto_delete(err_msg, 30))
            else:
                err_msg = await reply_msg.edit(
                    f"❌ No matches found for **{movie_name}** in Jackett or preconfigured Telegram channels.\n\n⏱️ *Message auto-deletes in 30 seconds.*",
                    buttons=None
                )
                asyncio.create_task(schedule_auto_delete(err_msg, 30))

        except Exception as e:
            logging.exception("Error during command execution:")
            await reply_msg.edit(f"❌ An unexpected error occurred: {e}", buttons=None)


async def periodic_library_scanner_task():
    """Background loop to periodically scan and organize movie / adult folders."""
    if AUTO_SCAN_INTERVAL_MINS <= 0:
        logging.info("[Scanner] Background periodic scanner is disabled.")
        return
    logging.info(f"[Scanner] Background scanner enabled (Interval: {AUTO_SCAN_INTERVAL_MINS} minutes).")
    while True:
        try:
            await asyncio.sleep(AUTO_SCAN_INTERVAL_MINS * 60)
            logging.info("[Scanner] Running scheduled background library scan...")
            res = await scan_and_organize_libraries(MOVIES_DIR, ADULT_DIR, TMDB_API_KEY)
            if res.get("moved_to_adult") or res.get("moved_to_movies"):
                logging.info(
                    f"[Scanner] Background scan organized: "
                    f"{len(res.get('moved_to_adult', []))} -> Adult, "
                    f"{len(res.get('moved_to_movies', []))} -> Movies"
                )
        except asyncio.CancelledError:
            break
        except Exception as e:
            logging.error(f"[Scanner] Error during background scan: {e}")


async def start_lan_http_bridge(client=None, port: int = 8765):
    """
    Lightweight HTTP server on port 8765 allowing the Android mobile bot
    to delegate video URL downloads (yt-dlp), Telegram media files (up to 2GB via MTProto),
    and status queries to this PC.
    """
    async def handle_client(reader: asyncio.StreamReader, writer: asyncio.StreamWriter):
        try:
            line = await reader.readline()
            if not line:
                writer.close()
                return
            req_line = line.decode('utf-8', errors='ignore').strip()
            # Read and discard remaining headers
            while True:
                h = await reader.readline()
                if not h or h == b'\r\n' or h == b'\n':
                    break

            parts = req_line.split()
            if len(parts) >= 2:
                method, path = parts[0], parts[1]
                parsed = urllib.parse.urlparse(path)
                qs = urllib.parse.parse_qs(parsed.query)

                if parsed.path == "/api/status":
                    body = '{"status":"online","service":"PC yt-dlp & Telegram MTProto Bridge"}\n'
                    resp = (
                        "HTTP/1.1 200 OK\r\n"
                        "Content-Type: application/json\r\n"
                        f"Content-Length: {len(body)}\r\n"
                        "Access-Control-Allow-Origin: *\r\n"
                        "\r\n" + body
                    )
                    writer.write(resp.encode('utf-8'))
                    await writer.drain()

                elif parsed.path == "/api/download_link":
                    target_url = qs.get("url", [""])[0].strip()
                    if target_url:
                        logging.info(f"[LAN Bridge] Received delegated video URL from mobile: {target_url}")
                        os.makedirs(DIRECT_DOWNLOAD_DIR, exist_ok=True)

                        def run_bg_ytdlp(v_url, dest_dir):
                            try:
                                import yt_dlp as ytdlp_module
                                output_tmpl = os.path.join(dest_dir, "%(title)s.%(ext)s")
                                ydl_opts = {
                                    "outtmpl": output_tmpl,
                                    "no_playlist": True,
                                    "quiet": False,
                                    "no_warnings": False,
                                }
                                with ytdlp_module.YoutubeDL(ydl_opts) as ydl:
                                    ydl.download([v_url])
                                logging.info(f"[LAN Bridge] Successfully completed download for: {v_url}")
                                asyncio.run(scan_and_organize_libraries(MOVIES_DIR, ADULT_DIR, TMDB_API_KEY))
                            except Exception as dl_err:
                                logging.error(f"[LAN Bridge] Failed downloading delegated URL '{v_url}': {dl_err}")

                        loop = asyncio.get_event_loop()
                        loop.run_in_executor(None, run_bg_ytdlp, target_url, DIRECT_DOWNLOAD_DIR)

                        body = '{"status":"accepted","url":"' + target_url + '"}\n'
                        resp = (
                            "HTTP/1.1 200 OK\r\n"
                            "Content-Type: application/json\r\n"
                            f"Content-Length: {len(body)}\r\n"
                            "Access-Control-Allow-Origin: *\r\n"
                            "\r\n" + body
                        )
                        writer.write(resp.encode('utf-8'))
                        await writer.drain()
                    else:
                        body = '{"error":"missing url parameter"}\n'
                        resp = (
                            "HTTP/1.1 400 Bad Request\r\n"
                            "Content-Type: application/json\r\n"
                            f"Content-Length: {len(body)}\r\n"
                            "Access-Control-Allow-Origin: *\r\n"
                            "\r\n" + body
                        )
                        writer.write(resp.encode('utf-8'))
                        await writer.drain()

                elif parsed.path == "/api/download_telegram":
                    chat_id_val = qs.get("chat_id", ["0"])[0].strip()
                    msg_id_val = qs.get("message_id", ["0"])[0].strip()
                    fname_hint = qs.get("file_name", [""])[0].strip()

                    if client and chat_id_val and msg_id_val:
                        logging.info(f"[LAN Bridge] Received delegated Telegram 2GB download: chat={chat_id_val}, msg={msg_id_val}, name={fname_hint}")

                        async def run_bg_tg_download(cid_str, mid_str, hint):
                            try:
                                cid = int(cid_str)
                                mid = int(mid_str)
                                tg_msg = await client.get_messages(cid, ids=mid)
                                if tg_msg and tg_msg.file:
                                    out_name = hint or tg_msg.file.name or f"telegram_{mid}.mkv"
                                    os.makedirs(DOWNLOAD_DIR, exist_ok=True)
                                    out_path = os.path.join(DOWNLOAD_DIR, out_name)
                                    logging.info(f"[LAN Bridge] Starting MTProto 2GB download: {out_name} ({format_size(tg_msg.file.size)})")
                                    await client.download_media(tg_msg, file=out_path)
                                    logging.info(f"[LAN Bridge] Successfully completed MTProto download for: {out_path}")
                                    await scan_and_organize_libraries(MOVIES_DIR, ADULT_DIR, TMDB_API_KEY)
                                else:
                                    logging.warning(f"[LAN Bridge] Message {mid} in chat {cid} not found or has no file attached.")
                            except Exception as dl_err:
                                logging.error(f"[LAN Bridge] Failed Telegram delegated download: {dl_err}")

                        asyncio.create_task(run_bg_tg_download(chat_id_val, msg_id_val, fname_hint))

                        body = '{"status":"accepted","mode":"mtproto_2gb","chat_id":"' + chat_id_val + '","message_id":"' + msg_id_val + '"}\n'
                        resp = (
                            "HTTP/1.1 200 OK\r\n"
                            "Content-Type: application/json\r\n"
                            f"Content-Length: {len(body)}\r\n"
                            "Access-Control-Allow-Origin: *\r\n"
                            "\r\n" + body
                        )
                        writer.write(resp.encode('utf-8'))
                        await writer.drain()
                    else:
                        body = '{"error":"missing client, chat_id, or message_id"}\n'
                        resp = (
                            "HTTP/1.1 400 Bad Request\r\n"
                            "Content-Type: application/json\r\n"
                            f"Content-Length: {len(body)}\r\n"
                            "Access-Control-Allow-Origin: *\r\n"
                            "\r\n" + body
                        )
                        writer.write(resp.encode('utf-8'))
                        await writer.drain()

                elif parsed.path == "/api/telegram_catalog":
                    # ── Return JSON list of all locally saved video files ────────
                    import json as _json, base64 as _b64
                    catalog = []
                    VIDEO_EXTS = {".mp4", ".mkv", ".avi", ".mov", ".wmv", ".ts", ".m4v", ".webm"}
                    seen_names = set()
                    for search_dir in {DOWNLOAD_DIR, DIRECT_DOWNLOAD_DIR}:
                        if not os.path.isdir(search_dir):
                            continue
                        for fname in os.listdir(search_dir):
                            fpath = os.path.join(search_dir, fname)
                            if not os.path.isfile(fpath):
                                continue
                            _, ext = os.path.splitext(fname.lower())
                            if ext not in VIDEO_EXTS or fname in seen_names:
                                continue
                            seen_names.add(fname)
                            fsize = os.path.getsize(fpath)
                            clean = re.sub(r'[\._\-\[\]\(\)]+', ' ', fname)
                            clean = re.sub(r'\b(19|20)\d{2}\b.*', '', clean).strip()
                            clean = re.sub(r'\s+', ' ', clean).strip()
                            year_m = re.search(r'\b(19\d{2}|20\d{2})\b', fname)
                            year = year_m.group(0) if year_m else ""
                            qual_m = re.search(r'\b(2160p|1080p|720p|480p|BluRay|WEB-DL|HDRip|REMUX|4K)\b', fname, re.I)
                            quality = qual_m.group(0) if qual_m else "1080p"
                            fid = _b64.urlsafe_b64encode(fpath.encode()).decode().rstrip("=")
                            mime = "video/x-matroska" if ext == ".mkv" else "video/mp4"
                            catalog.append({
                                "id": f"tg_{fid[:32]}",
                                "fileId": fid,
                                "fileName": fname,
                                "cleanTitle": clean[:60].strip(),
                                "year": year,
                                "quality": quality,
                                "fileSize": fsize,
                                "sizeFormatted": format_size(fsize),
                                "mimeType": mime,
                                "streamUrl": f"/stream/telegram?file_id={urllib.parse.quote(fid)}"
                            })
                    catalog.sort(key=lambda x: x["fileSize"], reverse=True)
                    body = _json.dumps(catalog, ensure_ascii=False)
                    resp = (
                        "HTTP/1.1 200 OK\r\n"
                        "Content-Type: application/json; charset=utf-8\r\n"
                        f"Content-Length: {len(body.encode('utf-8'))}\r\n"
                        "Access-Control-Allow-Origin: *\r\n"
                        "\r\n" + body
                    )
                    writer.write(resp.encode('utf-8'))
                    await writer.drain()

                elif parsed.path == "/stream/telegram" or parsed.path == "/stream/torrent":
                    # ── Byte-range streaming proxy for a local video file ────────
                    import mimetypes as _mt, base64 as _b64
                    is_telegram = (parsed.path == "/stream/telegram")
                    fpath = ""
                    VIDEO_EXTS2 = {".mp4", ".mkv", ".avi", ".mov", ".ts", ".m4v", ".webm"}

                    if is_telegram:
                        fid = qs.get("file_id", [""])[0].strip()
                        if fid:
                            try:
                                padding = 4 - (len(fid) % 4)
                                decoded_path = _b64.urlsafe_b64decode(fid + ("=" * (padding % 4))).decode()
                                if os.path.isfile(decoded_path):
                                    fpath = decoded_path
                            except Exception:
                                pass
                        if not fpath:
                            title_hint = qs.get("title", [""])[0].lower().replace("%20", " ").replace("+", " ")
                            for sd in {DOWNLOAD_DIR, DIRECT_DOWNLOAD_DIR}:
                                if not os.path.isdir(sd):
                                    continue
                                for fn in os.listdir(sd):
                                    _, ex = os.path.splitext(fn.lower())
                                    if ex in VIDEO_EXTS2 and (not title_hint or title_hint[:10] in fn.lower()):
                                        fpath = os.path.join(sd, fn)
                                        break
                                if fpath:
                                    break
                    else:
                        title_hint = qs.get("title", [""])[0].lower().replace("%20", " ").replace("+", " ")
                        words = [w for w in title_hint.split() if len(w) > 3][:3]
                        for sd in {DOWNLOAD_DIR, DIRECT_DOWNLOAD_DIR}:
                            if not os.path.isdir(sd):
                                continue
                            for fn in os.listdir(sd):
                                _, ex = os.path.splitext(fn.lower())
                                if ex not in VIDEO_EXTS2:
                                    continue
                                if not words or any(w in fn.lower() for w in words):
                                    fpath = os.path.join(sd, fn)
                                    break
                            if fpath:
                                break

                    if not fpath or not os.path.isfile(fpath):
                        msg_not_found = b"HTTP/1.1 404 Not Found\r\nContent-Type: application/json\r\nContent-Length: 57\r\nAccess-Control-Allow-Origin: *\r\n\r\n{\"error\":\"File not found. Download it first via the bot.\"}"
                        writer.write(msg_not_found)
                        await writer.drain()
                        logging.warning(f"[Stream] Not found: path={parsed.path} qs={dict(qs)}")
                    else:
                        # Read remaining headers to find Range
                        range_header = None
                        while True:
                            hl = await reader.readline()
                            if not hl or hl in (b'\r\n', b'\n'):
                                break
                            hstr = hl.decode('utf-8', errors='ignore').strip()
                            if hstr.lower().startswith("range:"):
                                range_header = hstr.split(":", 1)[1].strip()
                        file_size = os.path.getsize(fpath)
                        mime, _ = _mt.guess_type(fpath)
                        mime = mime or "video/mp4"
                        start, end, status = 0, file_size - 1, "200 OK"
                        if range_header and range_header.startswith("bytes="):
                            rng = range_header[6:]
                            p2 = rng.split("-")
                            try:
                                start = int(p2[0]) if p2[0] else 0
                                end = int(p2[1]) if len(p2) > 1 and p2[1] else file_size - 1
                            except ValueError:
                                pass
                            status = "206 Partial Content"
                        chunk_len = end - start + 1
                        headers = (
                            f"HTTP/1.1 {status}\r\n"
                            f"Content-Type: {mime}\r\n"
                            f"Content-Length: {chunk_len}\r\n"
                            f"Content-Range: bytes {start}-{end}/{file_size}\r\n"
                            "Accept-Ranges: bytes\r\nAccess-Control-Allow-Origin: *\r\nConnection: close\r\n\r\n"
                        )
                        writer.write(headers.encode('utf-8'))
                        await writer.drain()
                        SEND_BUF = 512 * 1024
                        try:
                            with open(fpath, 'rb') as vf:
                                vf.seek(start)
                                remaining = chunk_len
                                while remaining > 0:
                                    data = vf.read(min(SEND_BUF, remaining))
                                    if not data:
                                        break
                                    writer.write(data)
                                    await writer.drain()
                                    remaining -= len(data)
                        except Exception as se:
                            logging.error(f"[Stream] Error serving {fpath}: {se}")
                        logging.info(f"[Stream] Served {os.path.basename(fpath)} bytes {start}-{end}")

                elif parsed.path == "/api/trending":
                    # ── Jackett trending proxy ───────────────────────────────────
                    import json as _json
                    query = qs.get("q", ["popular movies 2024"])[0].strip() or "popular movies 2024"
                    out = []
                    try:
                        raw = await search_jackett(query)
                        raw.sort(key=lambda x: x.get("Seeders", 0), reverse=True)
                        seen_l = set()
                        deduped = []
                        for r in raw:
                            link = r.get("MagnetUri") or r.get("Link") or r.get("Title", "")
                            if link and link not in seen_l:
                                seen_l.add(link)
                                deduped.append(r)
                        for i, r in enumerate(deduped[:20]):
                            t2 = r.get("Title", "Unknown")
                            ym = re.search(r'\b(19\d{2}|20\d{2})\b', t2)
                            qm = re.search(r'\b(2160p|1080p|720p|4K UHD|REMUX|BluRay|WEB-DL)\b', t2, re.I)
                            out.append({
                                "id": f"trend_{i}",
                                "title": t2[:80],
                                "year": ym.group(0) if ym else "",
                                "genre": r.get("CategoryDesc", "Movie"),
                                "rating": "N/A",
                                "quality": qm.group(0) if qm else "1080p",
                                "size": format_size(r.get("Size", 0)),
                                "seeds": r.get("Seeders", 0),
                                "magnetUri": r.get("MagnetUri") or r.get("Link") or ""
                            })
                    except Exception as te2:
                        logging.error(f"[LAN Bridge] /api/trending error: {te2}")
                    body = _json.dumps(out, ensure_ascii=False)
                    resp = (
                        "HTTP/1.1 200 OK\r\n"
                        "Content-Type: application/json; charset=utf-8\r\n"
                        f"Content-Length: {len(body.encode('utf-8'))}\r\n"
                        "Access-Control-Allow-Origin: *\r\n"
                        "\r\n" + body
                    )
                    writer.write(resp.encode('utf-8'))
                    await writer.drain()

                else:
                    body = '{"error":"not found"}\n'
                    resp = (
                        "HTTP/1.1 404 Not Found\r\n"
                        "Content-Type: application/json\r\n"
                        f"Content-Length: {len(body)}\r\n"
                        "Access-Control-Allow-Origin: *\r\n"
                        "\r\n" + body
                    )
                    writer.write(resp.encode('utf-8'))
                    await writer.drain()
        except Exception as bridge_err:
            logging.error(f"[LAN Bridge] Request handling error: {bridge_err}")
        finally:
            try:
                writer.close()
                await writer.wait_closed()
            except Exception:
                pass

    try:
        server = await asyncio.start_server(handle_client, '0.0.0.0', port)
        logging.info(f"[LAN Bridge] PC Downloader HTTP Bridge listening on port {port} for mobile delegation.")
    except Exception as e:
        logging.warning(f"[LAN Bridge] Could not start HTTP Bridge on port {port}: {e}")


async def main():
    logging.info("Starting Hybrid Media Downloader Bot...")
    
    # Initialize the client. The session file will be written locally.
    client = TelegramClient('media_downloader_session', TELEGRAM_API_ID, TELEGRAM_API_HASH)
    
    is_bot = bool(TELEGRAM_BOT_TOKEN)
    if is_bot:
        logging.info("Logging in using provided Bot Token...")
        await client.start(bot_token=TELEGRAM_BOT_TOKEN)
    else:
        logging.info("Logging in using User Account...")
        await client.start()
        
    me = await client.get_me()
    logging.info(f"Bot started successfully as: {me.first_name} (@{me.username if me.username else ''})")
    
    admin_id_raw = os.getenv("ADMIN_USER_ID") or os.getenv("TELEGRAM_CHAT_ID")
    admin_id = None
    if admin_id_raw:
        try:
            admin_id = int(admin_id_raw.strip())
        except ValueError:
            admin_id = None

    # Setup message handlers restricted to account owner / admin
    setup_handlers(client, me.id, is_bot_mode=is_bot, admin_id=admin_id)
    
    # Start periodic background library scanner
    asyncio.create_task(periodic_library_scanner_task())

    # Start LAN HTTP Bridge for mobile bot video delegation & 2GB Telegram downloads
    asyncio.create_task(start_lan_http_bridge(client=client, port=8765))
    
    # Run the client until disconnected
    await client.run_until_disconnected()

if __name__ == '__main__':
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        logging.info("Application stopped by user.")
