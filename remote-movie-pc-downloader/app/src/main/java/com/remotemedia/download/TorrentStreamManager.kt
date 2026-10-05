package com.remotemedia.download

import com.remotemedia.core.LogTag
import com.remotemedia.core.Logger
import com.remotemedia.core.StorageManager
import kotlinx.coroutines.delay
import org.libtorrent4j.Priority
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.TorrentInfo
import java.io.File
import java.io.OutputStream
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap

data class TorrentStreamSession(
    val infoHash: String,
    val title: String,
    val fileName: String,
    val fileSize: Long,
    val fileOffset: Long,
    val startPiece: Int,
    val endPiece: Int,
    val pieceLength: Int,
    val targetFile: File,
    val th: TorrentHandle
)

data class TorrentStreamStatus(
    val readyToPlay: Boolean,
    val hasMetadata: Boolean,
    val title: String,
    val fileName: String,
    val fileSize: Long,
    val fileSizeFormatted: String,
    val seeds: Int,
    val peers: Int,
    val downloadSpeed: String,
    val progressPercent: Int,
    val bufferedPercent: Int,
    val status: String
)

/**
 * High-performance Torrent-to-HTTP Streaming Manager (Stremio-style engine).
 * Orchestrates piece prioritization, sequential fetching, metadata resolution,
 * and HTTP 206 Partial Content byte streaming directly to ExoPlayer/media players.
 */
object TorrentStreamManager {

    private val activeSessions = ConcurrentHashMap<String, TorrentStreamSession>()
    private val videoExtensions = listOf(".mp4", ".mkv", ".avi", ".mov", ".webm", ".ts", ".m4v", ".flv", ".wmv")

    /**
     * Resolves swarm metadata and sets up a sequential stream session.
     */
    suspend fun prepareStream(
        magnetUri: String,
        title: String? = null,
        preferredFileIndex: Int = -1,
        timeoutMs: Long = 35000L
    ): TorrentStreamSession? {
        val hashMatch = Regex("urn:btih:([a-zA-Z0-9]+)", RegexOption.IGNORE_CASE).find(magnetUri)
        val hash = hashMatch?.groupValues?.getOrNull(1)?.lowercase() ?: ""

        if (hash.isNotBlank() && activeSessions.containsKey(hash)) {
            val existing = activeSessions[hash]!!
            if (existing.th.isValid) {
                return existing
            }
        }

        val th = TorrentEngine.getOrAddTorrentForStreaming(magnetUri, title) ?: run {
            Logger.e(LogTag.TORRENT, "Failed to get or add torrent for streaming")
            return null
        }

        val startWait = System.currentTimeMillis()
        Logger.i(LogTag.TORRENT, "Waiting for swarm metadata for torrent stream...")

        while (!th.status().hasMetadata()) {
            if (System.currentTimeMillis() - startWait > timeoutMs) {
                Logger.w(LogTag.TORRENT, "Swarm metadata timeout (${timeoutMs}ms) for torrent: $magnetUri")
                return null
            }
            delay(250)
        }

        val ti: TorrentInfo = th.torrentFile() ?: run {
            Logger.e(LogTag.TORRENT, "TorrentInfo is null despite hasMetadata being true")
            return null
        }

        val fs = ti.files()
        val numFiles = fs.numFiles()
        if (numFiles <= 0) return null

        var bestIndex = -1
        var bestSize = -1L

        for (i in 0 until numFiles) {
            val name = fs.fileName(i)
            val size = fs.fileSize(i)
            val isVideo = videoExtensions.any { name.endsWith(it, ignoreCase = true) }

            if (preferredFileIndex in 0 until numFiles && i == preferredFileIndex) {
                bestIndex = i
                bestSize = size
                break
            }

            if (isVideo && size > bestSize) {
                bestSize = size
                bestIndex = i
            }
        }

        if (bestIndex == -1) {
            for (i in 0 until numFiles) {
                val size = fs.fileSize(i)
                if (size > bestSize) {
                    bestSize = size
                    bestIndex = i
                }
            }
        }

        if (bestIndex == -1 || bestSize <= 0) {
            Logger.w(LogTag.TORRENT, "No valid media file found inside torrent.")
            return null
        }

        var fileOffset = 0L
        for (k in 0 until bestIndex) {
            fileOffset += fs.fileSize(k)
        }

        val pieceLength = ti.pieceLength()
        val startPiece = (fileOffset / pieceLength).toInt()
        val endPiece = ((fileOffset + bestSize - 1) / pieceLength).toInt()
        val selectedFileName = fs.fileName(bestIndex)
        val selectedFilePath = fs.filePath(bestIndex)

        // 1. Stremio Strategy: Deselect non-video files and focus bandwidth
        for (i in 0 until numFiles) {
            if (i == bestIndex) {
                th.filePriority(i, Priority.TOP_PRIORITY)
            } else {
                th.filePriority(i, Priority.IGNORE)
            }
        }

        // 3. Stremio Piece Priority: Prioritize header pieces & trailer pieces (e.g. moov atom for MP4 / EBML for MKV)
        val headerPiecesEnd = minOf(startPiece + 6, endPiece)
        for (p in startPiece..headerPiecesEnd) {
            th.piecePriority(p, Priority.TOP_PRIORITY)
        }

        val trailerPiecesStart = maxOf(startPiece, endPiece - 6)
        for (p in trailerPiecesStart..endPiece) {
            th.piecePriority(p, Priority.TOP_PRIORITY)
        }

        val saveDir = StorageManager.getMoviesDir()
        val targetFile = File(saveDir, selectedFilePath)

        val resolvedHash = try { th.infoHash().toHex().lowercase() } catch (_: Exception) { hash }

        val session = TorrentStreamSession(
            infoHash = resolvedHash,
            title = title?.ifBlank { selectedFileName } ?: selectedFileName,
            fileName = selectedFileName,
            fileSize = bestSize,
            fileOffset = fileOffset,
            startPiece = startPiece,
            endPiece = endPiece,
            pieceLength = pieceLength,
            targetFile = targetFile,
            th = th
        )

        activeSessions[resolvedHash] = session
        if (hash.isNotBlank()) activeSessions[hash] = session

        Logger.i(LogTag.TORRENT, "✅ Torrent stream session ready: '$selectedFileName' (${formatSize(bestSize)}) Pieces: $startPiece..$endPiece")
        return session
    }

    /**
     * Streams the requested byte range from the torrent storage to the HTTP output stream.
     * Implements sequential sliding-window piece boosting and waits on downloading pieces.
     */
    suspend fun streamBytes(
        session: TorrentStreamSession,
        rangeStart: Long,
        rangeEnd: Long,
        outputStream: OutputStream
    ) {
        val th = session.th
        val pieceLength = session.pieceLength
        val fileOffset = session.fileOffset
        val fileSize = session.fileSize

        val safeEnd = minOf(rangeEnd, fileSize - 1)
        var currentOffset = rangeStart
        val buffer = ByteArray(64 * 1024) // 64 KB chunks

        val startPieceForRange = ((fileOffset + rangeStart) / pieceLength).toInt()

        // Boost sliding window from requested seek point
        val windowEnd = minOf(startPieceForRange + 12, session.endPiece)
        for (p in startPieceForRange..windowEnd) {
            th.piecePriority(p, Priority.TOP_PRIORITY)
        }

        var lastBoostedPiece = windowEnd

        while (currentOffset <= safeEnd) {
            val torrentByte = fileOffset + currentOffset
            val pieceIndex = (torrentByte / pieceLength).toInt()

            // Advance sliding priority window
            if (pieceIndex + 12 > lastBoostedPiece && lastBoostedPiece < session.endPiece) {
                val newWindowEnd = minOf(pieceIndex + 16, session.endPiece)
                for (nextP in (lastBoostedPiece + 1)..newWindowEnd) {
                    th.piecePriority(nextP, Priority.TOP_PRIORITY)
                }
                lastBoostedPiece = newWindowEnd
            }

            // Ensure current piece is TOP_PRIORITY
            if (!th.havePiece(pieceIndex)) {
                th.piecePriority(pieceIndex, Priority.TOP_PRIORITY)

                // Wait for piece to arrive with timeout
                val pieceWaitStart = System.currentTimeMillis()
                while (!th.havePiece(pieceIndex)) {
                    val actualFile = resolveActualTargetFile(session)
                    // Check if file already contains data at this offset
                    if (actualFile.exists() && actualFile.length() > currentOffset + 1024) {
                        break
                    }
                    if (System.currentTimeMillis() - pieceWaitStart > 45000L) {
                        Logger.w(LogTag.TORRENT, "Torrent streaming piece wait timeout at piece $pieceIndex")
                        break
                    }
                    delay(40)
                }
            }

            // Flush libtorrent cache to ensure newly downloaded piece blocks hit disk
            runCatching { th.flushCache() }

            // Read from downloaded file on disk
            try {
                val actualFile = resolveActualTargetFile(session)
                if (!actualFile.exists()) {
                    delay(50)
                    continue
                }

                RandomAccessFile(actualFile, "r").use { raf ->
                    val fileLength = raf.length()
                    if (fileLength <= currentOffset) {
                        delay(40)
                        return@use
                    }

                    raf.seek(currentOffset)

                    val pieceEndByte = ((pieceIndex + 1).toLong() * pieceLength) - fileOffset
                    val bytesLeftInPiece = maxOf(1L, pieceEndByte - currentOffset)
                    val bytesLeftInRange = safeEnd - currentOffset + 1
                    val maxCanReadFromFile = fileLength - currentOffset

                    val toRead = minOf(
                        buffer.size.toLong(),
                        bytesLeftInPiece,
                        bytesLeftInRange,
                        maxCanReadFromFile
                    ).toInt()

                    if (toRead > 0) {
                        val bytesRead = raf.read(buffer, 0, toRead)
                        if (bytesRead > 0) {
                            outputStream.write(buffer, 0, bytesRead)
                            currentOffset += bytesRead
                        } else {
                            delay(30)
                        }
                    } else {
                        delay(30)
                    }
                }
            } catch (e: Exception) {
                val msg = e.message ?: ""
                if (msg.contains("Broken pipe", ignoreCase = true) || msg.contains("Connection reset", ignoreCase = true)) {
                    // Player paused or seeked — normal stream termination
                    break
                }
                Logger.w(LogTag.TORRENT, "Stream chunk read warning: ${e.message}")
                delay(50)
            }
        }
    }

    /**
     * Resolves the actual downloaded media file on disk even across nested subfolders.
     */
    fun resolveActualTargetFile(session: TorrentStreamSession): File {
        if (session.targetFile.exists() && session.targetFile.length() > 0) return session.targetFile

        val candidates = listOf(
            session.targetFile,
            File(StorageManager.getMoviesDir(), session.fileName),
            File(StorageManager.getMoviesDir(), session.targetFile.name),
            File(StorageManager.getSandboxFolder(), session.fileName),
            File(StorageManager.getSandboxFolder(), session.targetFile.name)
        )
        for (c in candidates) {
            if (c.exists() && c.length() > 0) return c
        }

        // Search subdirectories within movies dir
        val saveDir = StorageManager.getMoviesDir()
        if (saveDir.exists()) {
            val matches = saveDir.walkTopDown().maxDepth(4).filter {
                it.isFile && (it.name == session.fileName || it.name == session.targetFile.name)
            }
            val match = matches.firstOrNull()
            if (match != null) return match
        }

        return session.targetFile
    }

    /**
     * Returns live status for player HUD (Stremio overlay).
     */
    fun getStreamStatus(hashOrMagnet: String): TorrentStreamStatus {
        val hash = if (hashOrMagnet.startsWith("magnet:", ignoreCase = true)) {
            Regex("urn:btih:([a-zA-Z0-9]+)", RegexOption.IGNORE_CASE).find(hashOrMagnet)?.groupValues?.getOrNull(1)?.lowercase() ?: ""
        } else {
            hashOrMagnet.lowercase()
        }

        var session = activeSessions[hash]
        var th = session?.th ?: try {
            TorrentEngine.getSessionManager()?.find(org.libtorrent4j.Sha1Hash.parseHex(hash))
        } catch (_: Exception) { null }

        if (th == null && hashOrMagnet.startsWith("magnet:", ignoreCase = true)) {
            // Proactively add to engine so swarm metadata begins downloading immediately
            th = TorrentEngine.getOrAddTorrentForStreaming(hashOrMagnet)
        }

        if (th == null || !th.isValid) {
            return TorrentStreamStatus(
                readyToPlay = false,
                hasMetadata = false,
                title = session?.title ?: "Connecting to swarm...",
                fileName = session?.fileName ?: "",
                fileSize = session?.fileSize ?: 0L,
                fileSizeFormatted = if (session != null) formatSize(session.fileSize) else "--",
                seeds = 0,
                peers = 0,
                downloadSpeed = "0 KB/s",
                progressPercent = 0,
                bufferedPercent = 0,
                status = "SEARCHING SWARM"
            )
        }

        val status = th.status()
        val hasMeta = status.hasMetadata()
        val seeds = status.numSeeds()
        val peers = status.numPeers()
        val downRate = status.downloadPayloadRate().toLong()
        val pct = (status.progress() * 100).toInt().coerceIn(0, 100)

        val actualFile = if (session != null) resolveActualTargetFile(session) else null
        val fileHasData = actualFile != null && actualFile.exists() && actualFile.length() > 32 * 1024

        val readyToPlay = hasMeta && ((session != null && th.havePiece(session.startPiece)) || pct > 0 || fileHasData)

        val statusStr = when {
            readyToPlay -> "STREAMING DIRECT"
            !hasMeta -> "DOWNLOADING METADATA"
            seeds == 0 && peers == 0 -> "CONNECTING TO PEERS"
            else -> "BUFFERING SWARM"
        }

        return TorrentStreamStatus(
            readyToPlay = readyToPlay,
            hasMetadata = hasMeta,
            title = session?.title ?: (th.getName() ?: "Torrent Stream"),
            fileName = session?.fileName ?: (th.getName() ?: ""),
            fileSize = session?.fileSize ?: status.totalWanted(),
            fileSizeFormatted = formatSize(session?.fileSize ?: status.totalWanted()),
            seeds = seeds,
            peers = peers,
            downloadSpeed = formatSpeed(downRate),
            progressPercent = pct,
            bufferedPercent = pct,
            status = statusStr
        )
    }

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format(java.util.Locale.US, "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }

    private fun formatSpeed(bytesPerSec: Long): String {
        if (bytesPerSec <= 0) return "0 KB/s"
        val kb = bytesPerSec / 1024.0
        val mb = kb / 1024.0
        return if (mb >= 1.0) String.format(java.util.Locale.US, "%.1f MB/s", mb)
        else String.format(java.util.Locale.US, "%.0f KB/s", kb)
    }
}
