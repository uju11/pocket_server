package com.remotemedia.download

import android.content.Context
import android.content.SharedPreferences
import com.remotemedia.core.LogTag
import com.remotemedia.core.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

data class TorrentTracker(
    val url: String,
    val isEnabled: Boolean = true,
    val isCustom: Boolean = false
) {
    val protocol: String
        get() = when {
            url.startsWith("udp://", ignoreCase = true) -> "UDP"
            url.startsWith("wss://", ignoreCase = true) -> "WSS"
            url.startsWith("https://", ignoreCase = true) -> "HTTPS"
            url.startsWith("http://", ignoreCase = true) -> "HTTP"
            else -> "TRACKER"
        }
}

data class TorrentIndexer(
    val id: String,
    val name: String,
    val domain: String,
    val description: String,
    val isEnabled: Boolean = true
)

object TorrentTrackersManager {

    private const val PREFS_NAME = "remote_media_prefs"
    private const val KEY_TRACKERS_JSON = "torrent_trackers_list_json"
    private const val KEY_INDEXERS_JSON = "torrent_indexers_list_json"

    // Tier-1 Ultra High-Speed Announce Trackers
    val DEFAULT_TRACKERS = listOf(
        "udp://tracker.opentrackers.org:1337/announce",
        "udp://open.stealth.si:80/announce",
        "udp://tracker.torrent.eu.org:451/announce",
        "udp://explodie.org:6969/announce",
        "udp://tracker.coppersurfer.tk:6969/announce",
        "udp://p4p.arenabg.com:1337/announce",
        "udp://movies.zsw.ca:6969/announce",
        "udp://tracker.dler.org:6969/announce",
        "udp://tracker.cyberia.is:6969/announce",
        "udp://open.demonii.com:1337/announce",
        "udp://tracker.openbittorrent.com:6969/announce",
        "udp://exodus.desync.com:6969/announce",
        "udp://tracker.tiny-vps.com:6969/announce",
        "udp://tracker.moeking.me:6969/announce",
        "udp://opentracker.i2p.rocks:6969/announce",
        "udp://open.tracker.cl:1337/announce",
        "udp://9.rarbg.com:2810/announce",
        "udp://tracker.bitSearch.to:1337/announce",
        "udp://tracker.altrosky.nl:6969/announce",
        "http://tracker.openbittorrent.com:80/announce"
    )

    // Expanded Big Collection of 16+ Torrent Indexer Engines
    val DEFAULT_INDEXERS = listOf(
        TorrentIndexer("yts", "YTS Movie Index", "yts.mx", "HQ 720p / 1080p / 4K Compact Movies", true),
        TorrentIndexer("tpb", "The Pirate Bay", "apibay.org", "Universal Movies, Series, Software & Games", true),
        TorrentIndexer("solid", "SolidTorrents", "solidtorrents.net", "Global DHT Fast Indexer", true),
        TorrentIndexer("torrents-csv", "Torrents.csv", "torrents-csv.com", "Open Source Torrent Database", true),
        TorrentIndexer("knaben", "Knaben Database", "knaben.eu", "P2P Multi-Source Indexing Engine", true),
        TorrentIndexer("bitsearch", "BitSearch", "bitsearch.to", "Fast Global DHT Search Engine", true),
        TorrentIndexer("1337x", "1337x Media", "1337x.to", "Verified Scene & Web-DL Uploads", true),
        TorrentIndexer("tgx", "TorrentGalaxy", "torrentgalaxy.to", "Fast 1080p/4K REMUX & TV Shows", true),
        TorrentIndexer("eztv", "EZTV TV Series", "eztv.re", "Direct TV Episode Scraper", true),
        TorrentIndexer("nyaa", "Nyaa Anime", "nyaa.si", "Anime, Donghua & Asian Media", true),
        TorrentIndexer("limetorrents", "LimeTorrents", "limetorrents.lol", "Movies, TV & Lossless Audio", true),
        TorrentIndexer("rarbg", "RARBG Scene Mirror", "rarbgget.org", "High Bitrate Scene Releases", true),
        TorrentIndexer("torlock", "Torlock Index", "torlock.com", "Verified Fast Torrents", true),
        TorrentIndexer("rutor", "Rutor Multilingual", "rutor.info", "Dual Audio & International Media", true),
        TorrentIndexer("kickass", "KickassTorrents", "katcr.to", "Universal Public Indexer", true),
        TorrentIndexer("jackett", "Jackett Torznab", "localhost:9117", "Custom Torznab Aggregator Integration", true)
    )

    private val _trackersState = MutableStateFlow<List<TorrentTracker>>(emptyList())
    val trackersState: StateFlow<List<TorrentTracker>> = _trackersState.asStateFlow()

    private val _indexersState = MutableStateFlow<List<TorrentIndexer>>(emptyList())
    val indexersState: StateFlow<List<TorrentIndexer>> = _indexersState.asStateFlow()

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        loadTrackers()
        loadIndexers()
    }

    private fun getPrefs(): SharedPreferences? =
        appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun loadTrackers() {
        val prefs = getPrefs() ?: return
        val jsonStr = prefs.getString(KEY_TRACKERS_JSON, null)
        if (jsonStr.isNullOrBlank()) {
            val list = DEFAULT_TRACKERS.map { TorrentTracker(it, isEnabled = true, isCustom = false) }
            _trackersState.value = list
            saveTrackers(list)
            return
        }

        try {
            val array = JSONArray(jsonStr)
            val list = mutableListOf<TorrentTracker>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    TorrentTracker(
                        url = obj.getString("url"),
                        isEnabled = obj.optBoolean("isEnabled", true),
                        isCustom = obj.optBoolean("isCustom", false)
                    )
                )
            }
            _trackersState.value = list
        } catch (e: Exception) {
            Logger.e(LogTag.TORRENT, "Error parsing trackers: ${e.message}")
            resetTrackersToDefaults()
        }
    }

    private fun saveTrackers(list: List<TorrentTracker>) {
        val prefs = getPrefs() ?: return
        try {
            val array = JSONArray()
            list.forEach {
                val obj = JSONObject()
                obj.put("url", it.url)
                obj.put("isEnabled", it.isEnabled)
                obj.put("isCustom", it.isCustom)
                array.put(obj)
            }
            prefs.edit().putString(KEY_TRACKERS_JSON, array.toString()).apply()
        } catch (e: Exception) {
            Logger.e(LogTag.TORRENT, "Error saving trackers: ${e.message}")
        }
    }

    private fun loadIndexers() {
        val prefs = getPrefs() ?: return
        val jsonStr = prefs.getString(KEY_INDEXERS_JSON, null)
        if (jsonStr.isNullOrBlank()) {
            _indexersState.value = DEFAULT_INDEXERS
            saveIndexers(DEFAULT_INDEXERS)
            return
        }

        try {
            val array = JSONArray(jsonStr)
            val map = mutableMapOf<String, Boolean>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                map[obj.getString("id")] = obj.optBoolean("isEnabled", true)
            }
            val merged = DEFAULT_INDEXERS.map { indexer ->
                val enabled = map[indexer.id] ?: indexer.isEnabled
                indexer.copy(isEnabled = enabled)
            }
            _indexersState.value = merged
        } catch (e: Exception) {
            _indexersState.value = DEFAULT_INDEXERS
        }
    }

    private fun saveIndexers(list: List<TorrentIndexer>) {
        val prefs = getPrefs() ?: return
        try {
            val array = JSONArray()
            list.forEach {
                val obj = JSONObject()
                obj.put("id", it.id)
                obj.put("isEnabled", it.isEnabled)
                array.put(obj)
            }
            prefs.edit().putString(KEY_INDEXERS_JSON, array.toString()).apply()
        } catch (e: Exception) {
            Logger.e(LogTag.TORRENT, "Error saving indexers: ${e.message}")
        }
    }

    fun addTracker(rawUrl: String): Boolean {
        val trimmed = rawUrl.trim()
        if (trimmed.isBlank()) return false
        val current = _trackersState.value.toMutableList()
        if (current.any { it.url.equals(trimmed, ignoreCase = true) }) {
            return false // already exists
        }
        current.add(0, TorrentTracker(trimmed, isEnabled = true, isCustom = true))
        _trackersState.value = current
        saveTrackers(current)
        Logger.i(LogTag.TORRENT, "Added custom tracker: $trimmed")
        return true
    }

    fun removeTracker(url: String) {
        val updated = _trackersState.value.filterNot { it.url.equals(url, ignoreCase = true) }
        _trackersState.value = updated
        saveTrackers(updated)
        Logger.i(LogTag.TORRENT, "Removed tracker: $url")
    }

    fun toggleTracker(url: String, isEnabled: Boolean) {
        val updated = _trackersState.value.map {
            if (it.url.equals(url, ignoreCase = true)) it.copy(isEnabled = isEnabled) else it
        }
        _trackersState.value = updated
        saveTrackers(updated)
    }

    fun toggleIndexer(id: String, isEnabled: Boolean) {
        val updated = _indexersState.value.map {
            if (it.id == id) it.copy(isEnabled = isEnabled) else it
        }
        _indexersState.value = updated
        saveIndexers(updated)
    }

    fun resetTrackersToDefaults() {
        val defaults = DEFAULT_TRACKERS.map { TorrentTracker(it, isEnabled = true, isCustom = false) }
        _trackersState.value = defaults
        saveTrackers(defaults)
        Logger.i(LogTag.TORRENT, "Reset trackers to default list.")
    }

    fun getActiveTrackerUrls(): List<String> =
        _trackersState.value.filter { it.isEnabled }.map { it.url }

    fun isIndexerEnabled(id: String): Boolean =
        _indexersState.value.find { it.id == id }?.isEnabled ?: true

    /**
     * Augments a magnet link with all currently active announce trackers so libtorrent
     * connects immediately to active swarms and seeds.
     */
    fun augmentMagnet(rawMagnetUri: String): String {
        if (!rawMagnetUri.startsWith("magnet:?", ignoreCase = true)) return rawMagnetUri

        val activeTrackers = getActiveTrackerUrls()
        if (activeTrackers.isEmpty()) return rawMagnetUri

        val sb = StringBuilder(rawMagnetUri)
        for (tr in activeTrackers) {
            val encoded = try {
                URLEncoder.encode(tr, "UTF-8")
            } catch (e: Exception) {
                tr
            }
            if (!rawMagnetUri.contains(encoded, ignoreCase = true) && !rawMagnetUri.contains(tr, ignoreCase = true)) {
                sb.append("&tr=").append(encoded)
            }
        }
        return sb.toString()
    }
}
