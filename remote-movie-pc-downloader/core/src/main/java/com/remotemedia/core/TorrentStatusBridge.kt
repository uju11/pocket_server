package com.remotemedia.core

/**
 * Bridge between engine-torrent and network server to avoid circular module dependencies.
 */
object TorrentStatusBridge {
    var getStreamStatus: ((String) -> Map<String, Any>)? = null
    var getSeedingStatus: (() -> Map<String, Any>)? = null
    var setSeedingEnabled: ((Boolean) -> Unit)? = null
}
