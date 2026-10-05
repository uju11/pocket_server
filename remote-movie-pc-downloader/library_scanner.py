import os
import sys
import re
import shutil
import logging
import asyncio
from typing import Dict, List, Optional, Tuple, Any

# Ensure UTF-8 stdout on Windows
if sys.stdout and hasattr(sys.stdout, 'reconfigure'):
    try:
        sys.stdout.reconfigure(encoding='utf-8', errors='replace')
    except Exception:
        pass

# Configure logging so scanner output is always visible in the terminal
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s [%(levelname)s] %(message)s',
    handlers=[logging.StreamHandler(sys.stdout)]
)

# Video file extensions to process
VIDEO_EXTENSIONS = {
    '.mp4', '.mkv', '.avi', '.mov', '.wmv', '.flv',
    '.webm', '.m4v', '.ts', '.m2ts', '.vob', '.iso'
}

# Companion files to move alongside video when moving loose files
COMPANION_EXTENSIONS = {
    '.srt', '.sub', '.idx', '.vtt', '.smi', '.ssa', '.ass',
    '.nfo', '.jpg', '.jpeg', '.png', '.webp', '.tbn'
}

# Trickplay / Jellyfin metadata signatures to strictly ignore
TRICKPLAY_IGNORES = [
    'trickplay',
    '.trickplay',
    '.bif',
    'extrafanart',
    'extrathumbs',
    'metadata'
]

# Massively expanded adult studio names, release networks, and sub-labels (case-insensitive)
ADULT_STUDIOS = {
    # TeamSkeet Network & sub-labels
    'momcomesfirst', 'mom comes first', 'sislovesme', 'sis loves me', 'familystrokes', 'family strokes',
    'pervmom', 'perv mom', 'dadcrush', 'dad crush', 'daughterswap', 'daughter swap',
    'brattysis', 'bratty sis', 'stepmomlessons', 'stepmom lessons', 'step mom lessons',
    'shoplyfter', 'shop lyfter', 'teenfidelity', 'teen fidelity', 'fosterlove', 'foster love',
    'badmilfs', 'bad milfs', 'rubratings', 'rub ratings', 'exxxtrasmall', 'exxxtra small',
    'myfirstsexteacher', 'my first sex teacher', 'shescaught', 'shes caught', 'hijabihookups',
    'hijabi hookups', 'familyhookups', 'family hookups', 'stepsiblings', 'step siblings',
    'stepsiblingstherepy', 'tittyattack', 'titty attack', 'povjunkies', 'pov junkies',
    'tiny4k', 'tiny 4k', 'lustery', 'analangels', 'anal angels', 'teamskeet', 'team skeet',
    'freeusefantasy', 'freeuse fantasy', 'hornyhost', 'horny host', 'whosyourdaddy',
    'pervdad', 'perv dad', 'pervsister', 'perv sister', 'pervstepmom', 'perv stepmom',
    
    # Aylo / MindGeek Network & sub-labels
    'brazzers', 'realitykings', 'reality kings', 'mofos', 'digitalplayground', 'digital playground',
    'twistys', 'babes', 'fakehub', 'fake hub', 'faketaxi', 'fake taxi', 'fakedriving', 'fake driving',
    'fakeagent', 'fake agent', 'fakehostel', 'fake hostel', 'rkprime', 'rk prime', 'rkduos',
    'realityduos', 'reality duos', 'sweetsinner', 'sweet sinner', 'bangbros', 'bang bros',
    'men.com', 'whywaittilclear', 'dirtywivesclub', 'dirty wives club', 'cumfiesta', 'cum fiesta',
    'bravoteens', 'bravo teens', 'monstersofcock', 'monsters of cock', 'bigtitcreampie',
    'passion-hd', 'passionhd', 'passion hd', 'squirted', 'letstryanal', 'lets try anal',
    'submissivex', 'strangethreeway', 'pornfidelity', 'porn fidelity', 'whokissedwho',
    'teeneerotica', 'doctoradventures', 'doctor adventures', 'dontbreakme', 'dont break me',

    # Strike 3 Holdings / Vixen Media Group
    'blacked', 'blackedraw', 'blacked raw', 'tushy', 'tushyraw', 'tushy raw',
    'vixen', 'deeper', 'slayed', 'glow', 'strike3', 'strike 3',

    # Gamma Entertainment / Adult Time / Pure Taboo / Mile High
    'puretaboo', 'pure taboo', 'girlsway', 'girls way', 'fantasymassage', 'fantasy massage',
    '21sextury', '21 sextury', 'burningangel', 'burning angel', 'wicked', 'wicked pictures',
    'wickedpictures', 'evilangel', 'evil angel', 'julesjordan', 'jules jordan',
    'hardx', 'hard x', 'darkx', 'dark x', 'lesbea', 'eroticax', 'erotica x',
    'sweetheartvideo', 'sweetheart video', 'milehighmedia', 'mile high media',
    'allgirlmassage', 'all girl massage', 'straponlesbians', 'strapon lesbians',
    'girlfriendsfilms', 'girlfriends films', 'devilsfilm', 'devils film',
    'diabolicvideo', 'diabolic video', 'archangel', 'sensualjane', 'sensual jane',
    'adulttime', 'adult time', 'twistyshard', 'fillyfilms', 'filly films',
    'bravomodels', 'bravo models', 'transfixed', 'transsensual', 'trans sensual',

    # Nubiles Network & sub-labels
    'nubiles', 'nubilefilms', 'nubile films', 'mom4k', 'mom 4k', 'petited', 'petite hd',
    'driverxxx', 'driver xxx', 'analthreesomes', 'anal threesomes', 'badteenspunished',
    'bad teens punished', 'nubiles-porn', 'nubiles porn', 'bitchyfemales', 'bitchy females',
    'brattymilf', 'bratty milf', 'familyfunxxx', 'family fun xxx', 'stepdaughterlessons',
    'stepdaughter lessons', 'stepsisterxxx', 'step sister xxx', 'taboopov', 'taboo pov',
    'momsincontrol', 'moms in control', 'princesscum', 'princess cum', 'hotmilfspov',

    # Kink.com Network & Fetish
    'kink', 'kink.com', 'fuckingmachines', 'fucking machines', 'waterbondage', 'water bondage',
    'devicebondage', 'device bondage', 'hogtied', 'boundgangbangs', 'bound gangbangs',
    'electrosluts', 'electro sluts', 'hardcoregangbang', 'hardcore gangbang',
    'ultimatesurrender', 'ultimate surrender', 'everythingbutt', 'everything butt',
    'sexandsubmission', 'sex and submission', 'meninpain', 'men in pain', 'tsplayhouse',
    'ts playhouse', 'kinkuniversity', 'kink university', 'primalfetish', 'primal fetish',

    # Independent / European / JAV / Creator Sites
    'manyvids', 'onlyfans', 'fansly', 'clips4sale', 'pornhub', 'xvideos', 'redtube',
    'youporn', 'xhamster', 'chaturbate', 'cam4', 'stripchat', 'bongacams', 'spizoo',
    'sexart', 'sex art', 'metart', 'met-art', 'x-art', 'xart', 'vivthomas', 'viv thomas',
    'femjoy', 'alsscan', 'alettaoceanlive', 'badoink', 'badoinkvr', 'badoink vr',
    'wankz', 'wankzvr', 'wankz vr', 'sexmex', 'danno', 'lubed', 'score', 'sapphix',
    'hegre', 'hegreart', 'suicidegirls', 'suicide girls', 'abbywinters', 'abby winters',
    'trikepatrol', 'trike patrol', 'massagecreep', 'massage creep', 'assparade', 'ass parade',
    'milfhunter', 'milf hunter', 'bigwetbutts', 'big wet butts', 'youngerlust', 'younger lust',
    'naughtybookworms', 'naughty bookworms', 'bangingbeauties', 'banging beauties',
    'povlife', 'pov life', 'propertyofsex', 'property of sex', 'lezbehonest', 'lez be honest',
    'girlcore', 'mofosnetwork', 'mofos network', 'wankitnow', 'wank it now',
    'mommysgirl', 'mommys girl', 'mommy is girl', 'familytherapyxxx', 'family therapy xxx',
    'mylf', 'povd', 'pov d', 'wowporn', 'wow porn', 'dorcel', 'marc dorcel', 'private',
    'zero tolerance', 'zerotolerance', 'hustler', 'penthouse', 'playboy', 'sexpositive',
    'sweet sinner', 'sweetsinner', 'elegantangel', 'elegant angel', 'primal fetish'
}

# Explicit keywords and tags (checked with word boundaries)
ADULT_KEYWORDS = [
    r'\b18\+\b',
    r'\bxxx\b',                # standalone xxx
    r'\w+xxx\b',               # xxx as a suffix: FamilyTherapyXXX, MomXXX, etc.
    r'\bxxx\w+',               # xxx as a prefix: xxxproposal, etc.
    r'\bporn\b',
    r'\bporno\b',
    r'\bnsfw\b',
    r'\bhentai\b',
    r'\bjav\b',
    r'\buncensored\b',
    r'\bcensored\b',
    r'\berotic\b',
    r'\berotica\b',
    r'\bcreampie\b',
    r'\bcreampies\b',
    r'\bblowjob\b',
    r'\bblowjobs\b',
    r'\bhandjob\b',
    r'\bhandjobs\b',
    r'\bhardcore\b',
    r'\bgangbang\b',
    r'\bgangbangs\b',
    r'\bmilf\b',
    r'\bmilfs\b',
    r'\bdilf\b',
    r'\bdilfs\b',
    r'\bswinger\b',
    r'\bswingers\b',
    r'\btreesome\b',
    r'\bthreesome\b',
    r'\borgasm\b',
    r'\bbusty\b',
    r'\bbdsm\b',
    r'\bfetish\b',
    r'\bcumshot\b',
    r'\bcumshots\b',
    r'\bdeepthroat\b',
    r'\bbareback\b',
    r'\bsquirting\b',
    r'\bsquirt\b',
    r'\bgloryhole\b',
    r'\bdildo\b',
    r'\bstrapon\b',
    r'\bstrap-on\b',
    r'\banal\b'
]

# Compound taboo and adult studio root patterns that catch dynamic / unlisted studio sites
COMPOUND_ADULT_PATTERNS = [
    # Mom / Taboo combinations (e.g. momcomesfirst, mompov, momsboy, momteaches, pervmom)
    re.compile(r'\bmom[a-z]*(?:comes|first|next|help|sex|fuck|xxx|boy|son|love|stroke|perv|lust|lesson|teaches|caught|swap|room|bed|milf)\b', re.IGNORECASE),
    # Step combinations (e.g. stepmomlessons, stepsister, stepdaughter, stepimstuck, stepbro)
    re.compile(r'\bstep[a-z]*(?:mom|sis|sister|brother|bro|dad|daughter|son|family|parent|taboo|lessons?|stuck|temptation|fantasy)\b', re.IGNORECASE),
    # Sister taboo combinations (e.g. sislovesme, sistertaboo, siscaught, brattysis)
    re.compile(r'\bsis(?:ter)?[a-z]*(?:loves?|fuck|seduce|caught|help|taboo|swap|stroke|xxx|temptation)\b', re.IGNORECASE),
    # Family taboo combinations (e.g. familystrokes, familytherapyxxx, familyhookups, familyaffair)
    re.compile(r'\bfamily[a-z]*(?:therapy|strokes?|taboo|hookups?|fun|swap|affair|secrets?|creampie|xxx)\b', re.IGNORECASE),
    # Daughter / Dad taboo combinations (e.g. daughterswap, dadcrush)
    re.compile(r'\bdaughter[a-z]*(?:swap|lessons?|taboo|crush|xxx|love|caught)\b', re.IGNORECASE),
    re.compile(r'\bdad[a-z]*(?:crush|taboo|lessons?|love|xxx|caught|swap)\b', re.IGNORECASE),
    # Perv / Bratty prefixes
    re.compile(r'\bperv[a-z]*(?:mom|dad|sis|sister|daughter|family|step|boy|girl)\b', re.IGNORECASE),
    re.compile(r'\bbratt?y[a-z]*(?:sis|sister|teen|milf|girl|sub)\b', re.IGNORECASE),
    # Fake / Casting adult studio series (e.g. fakehub, faketaxi, fakeagent, castingcouch)
    re.compile(r'\b(?:fake|casting)[a-z]*(?:hub|taxi|agent|driving|hostel|audition|couch|clinic|massage)\b', re.IGNORECASE),
    # Masturbation / Wank studio keywords
    re.compile(r'\b(?:wank|wankz|wankitnow|masturbat\w+)\b', re.IGNORECASE),
]

# Standard adult scene release format: "Studio - Performer - Title (Date)" or "Studio.YY.MM.DD..."
ADULT_SCENE_DATE_REGEX = re.compile(
    r'\b(?:19|20)\d{2}[-._](?:0[1-9]|1[0-2])[-._](?:0[1-9]|[12]\d|3[01])\b|\b(?:0[1-9]|[12]\d|3[01])[-._](?:0[1-9]|1[0-2])[-._](?:19|20)\d{2}\b',
    re.IGNORECASE
)

# Non-JAV prefixes and media tag keywords that should never be treated as JAV studio codes
NON_JAV_PREFIXES = {
    'part', 'vol', 'disc', 'cd', 'dvd', 'hevc', 'x264', 'x265', 'h264', 'h265',
    'aac', 'dts', 'bluray', 'bdrip', 'webrip', 'webdl', 'remux', 'repack', 'proper',
    'sub', 'idx', 'srt', 'audio', 'video', 'ep', 'episode', 'season', 'fps',
    'hdrip', 'dvdrip', 'ch', 'rus', 'eng', 'ita', 'fra', 'ger', 'spa', 'hin',
    'hindi', 'tam', 'tel', 'kan', 'mal', 'kor', 'chi', 'jpn', 'uncut', 'sample',
    'trailer', 'scene', 'track', 'bit', '10bit', '8bit', 'esub', 'esu', 'subs'
}

# Explicit Japanese Adult Video (JAV) special provider prefixes
JAV_SPECIAL_REGEX = re.compile(
    r'\b(?:fc2[-_]?(?:ppv)?|carib(?:beancom)?|1pondo|heyzo|10musume|pacopacomama|kin8tengoku|gachinco)[-_][0-9]{4,8}\b',
    re.IGNORECASE
)

# Standard JAV code regex: e.g. SSIS-123, IPX-456, MIDE-890, SNIS-099 (MUST have '-' or '_' separator, NO space)
JAV_CODE_REGEX = re.compile(
    r'\b([a-zA-Z]{2,6})[-_]([0-9]{3,5})\b',
    re.IGNORECASE
)

# Standard mainstream TV / Movie patterns
TV_EPISODE_REGEX = re.compile(r'\b(?:[sS]\d{1,2}[eE]\d{1,2}|\d{1,2}[xX]\d{1,2})\b')
YEAR_REGEX = re.compile(r'\b(19\d\d|20\d\d)\b')


def is_trickplay_or_ignored(path_str: str) -> bool:
    """Checks if a file or path is a Jellyfin trickplay file, metadata, or should be ignored."""
    low = path_str.lower().replace('\\', '/')
    for ign in TRICKPLAY_IGNORES:
        if f"/{ign}/" in low or low.endswith(f"/{ign}") or ign in os.path.basename(low):
            return True
    if low.endswith('.bif'):
        return True
    return False


def is_video_file(filepath: str) -> bool:
    """Returns True if the file has a valid video extension and is not a trickplay file."""
    if is_trickplay_or_ignored(filepath):
        return False
    _, ext = os.path.splitext(filepath)
    return ext.lower() in VIDEO_EXTENSIONS


def normalize_title_for_search(name: str) -> str:
    """Cleans up filenames by removing resolution, codec, release groups, and symbols."""
    # Remove file extension
    base, _ = os.path.splitext(name)
    # Replace dots, underscores, dashes, brackets with spaces
    cleaned = re.sub(r'[\._\-\[\]\(\)]+', ' ', base)
    # Remove common video quality tags
    cleaned = re.sub(r'(?i)\b(2160p|1080p|720p|480p|4k|uhd|bluray|blu-ray|webrip|web-dl|hdrip|x264|x265|hevc|aac\d?\.?\d?|dts|remux|10bit|8bit|hin|hindi|eng|esub|esu)\b', ' ', cleaned)
    # Clean whitespace
    cleaned = re.sub(r'\s+', ' ', cleaned).strip()
    return cleaned


def is_adult_by_heuristics(item_name: str) -> Tuple[bool, str]:
    """
    Evaluates filename or directory name against known adult studios, compound taboo roots,
    JAV codes, and explicit keywords.
    Returns (is_adult, reason).
    """
    low_name = item_name.lower()
    
    # Skip TV episodes (e.g. S01E02 or 1x02)
    if TV_EPISODE_REGEX.search(low_name):
        return False, ""

    # 1. Check Special JAV Providers (FC2-PPV, CARIB, etc.)
    special_jav = JAV_SPECIAL_REGEX.search(low_name)
    if special_jav:
        return True, f"JAV Special Pattern: {special_jav.group(0)}"

    # 2. Check Standard JAV Code Regex (e.g. SSIS-123, IPX-456)
    for match in JAV_CODE_REGEX.finditer(low_name):
        prefix = match.group(1).lower()
        number_str = match.group(2)
        
        # Disallow non-JAV prefixes (e.g. part-01, h264-1080, etc.)
        if prefix in NON_JAV_PREFIXES:
            continue

        # Disallow release years (e.g. 1900 - 2099 are release years, not JAV code numbers)
        try:
            num_val = int(number_str)
            if 1900 <= num_val <= 2099:
                continue
        except ValueError:
            pass

        return True, f"JAV Code Pattern: {match.group(0)}"

    # 3. Check Adult Studios Database (350+ brand names & sub-labels)
    for studio in ADULT_STUDIOS:
        pattern = r'(?:\b|[\._\-\s])' + re.escape(studio) + r'(?:\b|[\._\-\s])'
        if re.search(pattern, low_name):
            return True, f"Adult Studio: {studio.title()}"

    # 4. Check Compound Taboo & Adult Root Patterns (Dynamic studio & scene matcher)
    for compound_pat in COMPOUND_ADULT_PATTERNS:
        match = compound_pat.search(low_name)
        if match:
            return True, f"Compound Adult Pattern: {match.group(0)}"

    # 5. Check Explicit Keywords
    # Special guard: "xXx (2002)" Vin Diesel film has standalone "xxx" in filename.
    # We only whitelist the standalone \bxxx\b pattern — suffix patterns like
    # \w+xxx (FamilyTherapyXXX) should still be matched.
    is_xxx_film = bool(
        re.search(r'\bxxx\b', low_name) and
        re.search(r'\b(2002|return of xander cage|state of the union|vin diesel)\b', low_name)
    )

    for kw_pattern in ADULT_KEYWORDS:
        # Skip standalone \bxxx\b check ONLY for the 2002 Vin Diesel film
        if kw_pattern == r'\bxxx\b' and is_xxx_film:
            continue
        if re.search(kw_pattern, low_name, re.IGNORECASE):
            return True, f"Explicit Keyword Pattern: {kw_pattern}"

    return False, ""


async def check_tpdb_scene_metadata(title: str) -> Tuple[Optional[bool], str]:
    """
    Queries ThePornDB / MetadataAPI (open adult metadata database) to check if a scene
    or title exists in the adult scene catalog.
    Returns (is_adult, reason).
    """
    clean_title = normalize_title_for_search(title)
    if not clean_title or len(clean_title) < 3:
        return None, "Title too short for TPDB query"

    try:
        import httpx
        url = "https://metadataapi.net/api/scenes"
        params = {"q": clean_title}
        headers = {"User-Agent": "MediaRemoteDownloader/2.0"}
        async with httpx.AsyncClient(timeout=4.0) as client:
            resp = await client.get(url, params=params, headers=headers)
            if resp.status_code == 200:
                data = resp.json()
                results = data.get("data", [])
                if results:
                    top_scene = results[0]
                    scene_title = top_scene.get("title", clean_title)
                    site_name = top_scene.get("site", {}).get("name", "Adult Site")
                    return True, f"ThePornDB verified scene: '{scene_title}' ({site_name})"
                else:
                    return None, f"ThePornDB: No scene found for '{clean_title}'"
            else:
                return None, f"ThePornDB API returned status {resp.status_code}"
    except Exception as e:
        return None, f"ThePornDB API query error: {e}"


async def check_tmdb_adult_metadata(title: str, tmdb_api_key: Optional[str] = None) -> Tuple[Optional[bool], str]:
    """
    Queries The Movie Database (TMDb) API to check if a title is marked as adult.
    Returns (is_adult, reason).
    """
    if not tmdb_api_key:
        return None, "No TMDb API key provided"

    clean_title = normalize_title_for_search(title)
    if not clean_title:
        return None, "Empty title after normalization"

    try:
        import httpx
        url = "https://api.themoviedb.org/3/search/movie"
        params = {
            "api_key": tmdb_api_key,
            "query": clean_title,
            "include_adult": "true"
        }
        async with httpx.AsyncClient(timeout=6.0) as client:
            resp = await client.get(url, params=params)
            if resp.status_code == 200:
                data = resp.json()
                results = data.get("results", [])
                if results:
                    top = results[0]
                    is_adult = top.get("adult", False)
                    movie_title = top.get("title", clean_title)
                    if is_adult:
                        return True, f"TMDb 'adult' flag is true for '{movie_title}'"
                    else:
                        return False, f"TMDb found standard movie: '{movie_title}' (Adult=False)"
                else:
                    return None, f"TMDb: No results found for '{clean_title}'"
            else:
                return None, f"TMDb API returned status {resp.status_code}"
    except Exception as e:
        return None, f"TMDb API query error: {e}"


async def is_adult_media(item_name: str, tmdb_api_key: Optional[str] = None) -> Tuple[bool, str]:
    """
    5-Layer Hybrid Detection Engine:
    1. Tests fast heuristics (400+ adult studios, compound taboo roots, JAV codes, explicit keywords).
    2. If inconclusive, queries TMDb metadata (if API key is present) to check adult flag or confirm mainstream movie.
    3. If still inconclusive, queries ThePornDB / MetadataAPI to check adult scene catalog.
    """
    # Layer 1: Fast Heuristics & Studio / Compound Patterns
    is_adult, reason = is_adult_by_heuristics(item_name)
    if is_adult:
        return True, reason

    # Layer 2: TMDb Mainstream / Adult Metadata (if API key configured)
    if tmdb_api_key:
        tmdb_adult, tmdb_reason = await check_tmdb_adult_metadata(item_name, tmdb_api_key)
        if tmdb_adult is True:
            return True, tmdb_reason
        elif tmdb_adult is False:
            # Verified standard movie
            return False, tmdb_reason

    # Layer 3: ThePornDB Scene Metadata
    tpdb_adult, tpdb_reason = await check_tpdb_scene_metadata(item_name)
    if tpdb_adult is True:
        return True, tpdb_reason

    return False, "Standard / Non-adult media"


def get_unique_destination_path(dest_path: str) -> str:
    """
    If dest_path already exists, appends a counter to prevent overwriting or data loss.
    e.g. 'movie.mp4' -> 'movie (1).mp4'
    """
    if not os.path.exists(dest_path):
        return dest_path

    base_dir = os.path.dirname(dest_path)
    base_name = os.path.basename(dest_path)
    
    if os.path.isdir(dest_path):
        counter = 1
        while True:
            new_name = f"{base_name} ({counter})"
            new_path = os.path.join(base_dir, new_name)
            if not os.path.exists(new_path):
                return new_path
            counter += 1
    else:
        name_part, ext_part = os.path.splitext(base_name)
        counter = 1
        while True:
            new_name = f"{name_part} ({counter}){ext_part}"
            new_path = os.path.join(base_dir, new_name)
            if not os.path.exists(new_path):
                return new_path
            counter += 1


def move_media_item(src_path: str, dest_dir: str) -> Tuple[bool, str, str]:
    """
    Moves a media item (dedicated subfolder or loose video + companion files) to dest_dir.
    Returns (success, moved_source_name, target_dest_path).
    """
    try:
        os.makedirs(dest_dir, exist_ok=True)
        item_name = os.path.basename(src_path)
        
        if os.path.isdir(src_path):
            # Move entire subfolder
            target_path = get_unique_destination_path(os.path.join(dest_dir, item_name))
            shutil.move(src_path, target_path)
            logging.info(f"[Scanner] Moved directory '{src_path}' -> '{target_path}'")
            return True, item_name, target_path
        else:
            # Move loose video file
            target_path = get_unique_destination_path(os.path.join(dest_dir, item_name))
            shutil.move(src_path, target_path)
            logging.info(f"[Scanner] Moved video file '{src_path}' -> '{target_path}'")

            # Look for matching companion files in the same directory (same base name)
            src_dir = os.path.dirname(src_path)
            base_name, _ = os.path.splitext(item_name)
            
            try:
                for sibling in os.listdir(src_dir):
                    sib_base, sib_ext = os.path.splitext(sibling)
                    if sib_base == base_name and sib_ext.lower() in COMPANION_EXTENSIONS:
                        sib_src = os.path.join(src_dir, sibling)
                        if os.path.isfile(sib_src) and not is_trickplay_or_ignored(sib_src):
                            sib_dest = get_unique_destination_path(os.path.join(dest_dir, sibling))
                            shutil.move(sib_src, sib_dest)
                            logging.info(f"[Scanner] Moved companion file '{sib_src}' -> '{sib_dest}'")
            except Exception as comp_err:
                logging.warning(f"[Scanner] Error moving companion files for '{item_name}': {comp_err}")

            return True, item_name, target_path
    except Exception as e:
        logging.error(f"[Scanner] Error moving '{src_path}' to '{dest_dir}': {e}")
        return False, os.path.basename(src_path), str(e)


def collect_library_items(root_dir: str) -> List[Tuple[str, bool, str]]:
    """
    Scans a library directory and yields items to evaluate.
    Returns list of tuples: (item_path, is_directory, name_to_evaluate)
    - If a subfolder contains video files and is not trickplay, returns the subfolder.
    - If a loose video file is in root_dir, returns the file.
    - Ignores trickplay directories (.bif, trickplay) and non-video assets.
    """
    if not os.path.exists(root_dir):
        return []

    collected = []
    try:
        entries = os.listdir(root_dir)
    except Exception as e:
        logging.error(f"[Scanner] Could not list directory '{root_dir}': {e}")
        return []

    for entry in entries:
        full_path = os.path.join(root_dir, entry)
        if is_trickplay_or_ignored(full_path):
            continue

        if os.path.isdir(full_path):
            # Check if this subfolder contains any video files
            has_video = False
            for dirpath, dirnames, filenames in os.walk(full_path):
                # Filter out trickplay subdirectories during walk
                dirnames[:] = [d for d in dirnames if not is_trickplay_or_ignored(os.path.join(dirpath, d))]
                for fname in filenames:
                    f_full = os.path.join(dirpath, fname)
                    if is_video_file(f_full):
                        has_video = True
                        break
                if has_video:
                    break

            if has_video:
                collected.append((full_path, True, entry))
        elif os.path.isfile(full_path):
            if is_video_file(full_path):
                collected.append((full_path, False, entry))

    return collected


async def scan_and_organize_libraries(
    movies_dir: str,
    adult_dir: str,
    tmdb_api_key: Optional[str] = None,
    progress_callback: Optional[Any] = None
) -> Dict[str, Any]:
    """
    Scans both movies_dir and adult_dir:
    1. If adult video/folder is in movies_dir -> Move to adult_dir.
    2. If standard non-adult movie/folder is in adult_dir -> Move to movies_dir.
    Returns structured scan results dictionary.
    Optionally calls async progress_callback(phase_str, current_idx, total_count, item_name, moved_count).
    """
    results = {
        "movies_dir": movies_dir,
        "adult_dir": adult_dir,
        "scanned_movies_count": 0,
        "scanned_adult_count": 0,
        "moved_to_adult": [],
        "moved_to_movies": [],
        "errors": []
    }

    if not movies_dir or not adult_dir:
        results["errors"].append("Invalid directory configuration (movies_dir or adult_dir is empty).")
        return results

    if not os.path.exists(movies_dir):
        results["errors"].append(f"Movies directory does not exist or is inaccessible: '{movies_dir}'")

    if not os.path.exists(adult_dir):
        results["errors"].append(f"Adult directory does not exist or is inaccessible: '{adult_dir}'")

    if os.path.abspath(movies_dir).lower() == os.path.abspath(adult_dir).lower():
        results["errors"].append("movies_dir and adult_dir point to the same directory. Scan skipped.")
        return results

    logging.info(f"[Scanner] Starting Library Scan. Movies: '{movies_dir}', Adult: '{adult_dir}'")

    # 1. Scan Movies Directory (Look for Adult content)
    movie_items = collect_library_items(movies_dir)
    results["scanned_movies_count"] = len(movie_items)
    logging.info(f"[Scanner] Found {len(movie_items)} items in Movies folder to evaluate.")

    for idx, (item_path, is_dir, item_name) in enumerate(movie_items, 1):
        if progress_callback:
            try:
                res_cb = progress_callback("Movies folder", idx, len(movie_items), item_name, len(results["moved_to_adult"]))
                if asyncio.iscoroutine(res_cb):
                    await res_cb
            except Exception as cb_err:
                logging.debug(f"[Scanner] Progress callback error: {cb_err}")

        logging.info(f"[Scanner] [{idx}/{len(movie_items)}] Checking: '{item_name}'")
        names_to_check = [item_name]
        if is_dir:
            try:
                for f in os.listdir(item_path):
                    if is_video_file(os.path.join(item_path, f)):
                        names_to_check.append(f)
            except Exception:
                pass

        is_adult = False
        detected_reason = ""
        for name in names_to_check:
            is_adult, detected_reason = await is_adult_media(name, tmdb_api_key)
            if is_adult:
                break

        if is_adult:
            logging.info(f"[Scanner] ADULT detected: '{item_name}' -> Moving to Adult folder. Reason: {detected_reason}")
            success, moved_name, target_dest = move_media_item(item_path, adult_dir)
            if success:
                logging.info(f"[Scanner] MOVED: '{moved_name}' -> '{target_dest}'")
                results["moved_to_adult"].append({
                    "item": moved_name,
                    "reason": detected_reason,
                    "target": target_dest
                })
            else:
                logging.error(f"[Scanner] FAILED to move '{moved_name}': {target_dest}")
                results["errors"].append(f"Failed to move '{moved_name}' to adult folder: {target_dest}")
        else:
            logging.info(f"[Scanner] OK (mainstream): '{item_name}'")

    # 2. Scan Adult Directory (Look for Mainstream non-adult movies accidentally placed there)
    adult_items = collect_library_items(adult_dir)
    results["scanned_adult_count"] = len(adult_items)
    logging.info(f"[Scanner] Found {len(adult_items)} items in Adult folder to evaluate.")

    for idx, (item_path, is_dir, item_name) in enumerate(adult_items, 1):
        if progress_callback:
            try:
                total_moved = len(results["moved_to_adult"]) + len(results["moved_to_movies"])
                res_cb = progress_callback("Adult folder", idx, len(adult_items), item_name, total_moved)
                if asyncio.iscoroutine(res_cb):
                    await res_cb
            except Exception as cb_err:
                logging.debug(f"[Scanner] Progress callback error: {cb_err}")

        logging.info(f"[Scanner] [{idx}/{len(adult_items)}] Checking adult folder: '{item_name}'")
        names_to_check = [item_name]
        if is_dir:
            try:
                for f in os.listdir(item_path):
                    if is_video_file(os.path.join(item_path, f)):
                        names_to_check.append(f)
            except Exception:
                pass

        is_adult = False
        detected_reason = ""
        for name in names_to_check:
            is_adult, detected_reason = await is_adult_media(name, tmdb_api_key)
            if is_adult:
                break

        # If it is NOT adult media, it belongs in the mainstream movies directory
        if not is_adult:
            reason = "Standard / Non-adult movie"
            if tmdb_api_key:
                tmdb_adult, tmdb_reason = await check_tmdb_adult_metadata(item_name, tmdb_api_key)
                if tmdb_adult is True:
                    # TMDB verified it is adult, so keep it in adult folder
                    logging.info(f"[Scanner] TMDb confirms adult, keeping in adult folder: '{item_name}'")
                    continue
                elif tmdb_adult is False:
                    reason = tmdb_reason

            logging.info(f"[Scanner] NON-ADULT in Adult folder: '{item_name}' -> Moving to Movies folder. ({reason})")
            success, moved_name, target_dest = move_media_item(item_path, movies_dir)
            if success:
                logging.info(f"[Scanner] MOVED: '{moved_name}' -> '{target_dest}'")
                results["moved_to_movies"].append({
                    "item": moved_name,
                    "reason": reason,
                    "target": target_dest
                })
            else:
                logging.error(f"[Scanner] FAILED to move '{moved_name}': {target_dest}")
                results["errors"].append(f"Failed to move '{moved_name}' to movies folder: {target_dest}")
        else:
            logging.info(f"[Scanner] OK (adult content): '{item_name}'")

    logging.info(
        f"[Scanner] Scan complete. Scanned Movies: {results['scanned_movies_count']}, "
        f"Scanned Adult: {results['scanned_adult_count']}, "
        f"Moved to Adult: {len(results['moved_to_adult'])}, "
        f"Moved to Movies: {len(results['moved_to_movies'])}"
    )

    return results


def run_sync_scan(movies_dir: str, adult_dir: str, tmdb_api_key: Optional[str] = None) -> Dict[str, Any]:
    """Synchronous helper wrapper around scan_and_organize_libraries for non-async callers (like tray_app)."""
    try:
        loop = asyncio.get_event_loop()
        if loop.is_running():
            import concurrent.futures
            with concurrent.futures.ThreadPoolExecutor() as executor:
                return executor.submit(lambda: asyncio.run(scan_and_organize_libraries(movies_dir, adult_dir, tmdb_api_key))).result()
        else:
            return loop.run_until_complete(scan_and_organize_libraries(movies_dir, adult_dir, tmdb_api_key))
    except RuntimeError:
        return asyncio.run(scan_and_organize_libraries(movies_dir, adult_dir, tmdb_api_key))


if __name__ == "__main__":
    from dotenv import load_dotenv
    load_dotenv()

    m_dir = os.getenv("MOVIES_DIR", "U:\\movies")
    a_dir = os.getenv("ADULT_DIR", "D:\\jelly\\New folder")
    tmdb_key = os.getenv("TMDB_API_KEY", "")

    print()
    print("=" * 60)
    print("  LIBRARY SCANNER & ADULT VIDEO ORGANIZER")
    print("=" * 60)
    print(f"  Movies folder : {m_dir}")
    print(f"  Adult  folder : {a_dir}")
    if tmdb_key:
        print("  TMDb API key  : configured")
    else:
        print("  TMDb API key  : not set (heuristics-only mode)")
    print("=" * 60)
    print()

    # Check directories exist
    for label, path in [("Movies", m_dir), ("Adult", a_dir)]:
        if not os.path.exists(path):
            print(f"  WARNING: {label} folder does not exist: {path}")
            print(f"  Skipping. Please check your .env configuration.")
            sys.exit(1)

    print("  Scanning... (this may take a moment)")
    print()

    res = run_sync_scan(m_dir, a_dir, tmdb_key)

    moved_to_adult = res.get("moved_to_adult", [])
    moved_to_movies = res.get("moved_to_movies", [])
    errors = res.get("errors", [])

    # Summary
    print("=" * 60)
    print("  SCAN COMPLETE")
    print("=" * 60)
    print(f"  Movies scanned : {res.get('scanned_movies_count', 0)}")
    print(f"  Adult  scanned : {res.get('scanned_adult_count', 0)}")
    print(f"  Moved -> Adult : {len(moved_to_adult)}")
    print(f"  Moved -> Movies: {len(moved_to_movies)}")
    print(f"  Errors         : {len(errors)}")
    print()

    if moved_to_adult:
        print("-" * 60)
        print(f"  Moved {len(moved_to_adult)} item(s) to Adult folder:")
        print("-" * 60)
        for i, item in enumerate(moved_to_adult, 1):
            print(f"  [{i}] {item['item']}")
            print(f"      Reason : {item['reason']}")
            print(f"      Dest   : {item['target']}")
        print()

    if moved_to_movies:
        print("-" * 60)
        print(f"  Moved {len(moved_to_movies)} item(s) back to Movies folder:")
        print("-" * 60)
        for i, item in enumerate(moved_to_movies, 1):
            print(f"  [{i}] {item['item']}")
            print(f"      Reason : {item['reason']}")
            print(f"      Dest   : {item['target']}")
        print()

    if not moved_to_adult and not moved_to_movies and not errors:
        print("  All media files are already correctly organized!")
        print()

    if errors:
        print("-" * 60)
        print(f"  Errors / Warnings ({len(errors)}):")
        print("-" * 60)
        for err in errors:
            print(f"  ! {err}")
        print()

    print("=" * 60)
