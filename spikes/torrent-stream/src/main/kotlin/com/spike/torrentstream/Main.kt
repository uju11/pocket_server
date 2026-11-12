package com.spike.torrentstream

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.libtorrent4j.AlertListener
import org.libtorrent4j.Priority
import org.libtorrent4j.SessionManager
import org.libtorrent4j.SettingsPack
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.alerts.Alert
import org.libtorrent4j.swig.libtorrent
import org.libtorrent4j.swig.error_code
import java.io.File
import java.io.RandomAccessFile
import java.net.InetSocketAddress

private val VIDEO_EXTS = listOf(".mp4", ".mkv", ".avi", ".mov", ".webm", ".ts", ".m4v")
private val SAVE_DIR = File(System.getProperty("java.io.tmpdir"), "torrent-spike")
private const val HTTP_PORT = 9090
private const val META_TIMEOUT_MS = 35_000L
private const val PIECE_WAIT_MS = 45_000L

fun main(args: Array<String>) {
    val magnet = args.firstOrNull()
        ?: error("Usage: ./gradlew run --args=\"<magnet_uri>\"")

    SAVE_DIR.mkdirs()
    println("[spike] Save dir: ${SAVE_DIR.absolutePath}")

    // ── 1. Init libtorrent session ──────────────────────────────────────────
    val session = SessionManager()
    val settings = SettingsPack().apply {
        setEnableDht(true)
        setEnableLsd(true)
        setDhtBootstrapNodes(
            "router.bittorrent.com:6881,dht.transmissionbt.com:6881," +
            "router.utorrent.com:6881,dht.libtorrent.org:25401"
        )
        listenInterfaces("0.0.0.0:6881,[::]:6881")
        alertQueueSize(5000)
    }
    session.addListener(object : AlertListener {
        override fun types(): IntArray? = null
        override fun alert(a: Alert<*>) {
            val m = a.message()
            if (m.contains("error", true) || m.contains("finished", true))
                println("[lt] $m")
        }
    })
    session.start(org.libtorrent4j.SessionParams(settings))
    if (!session.isDhtRunning) session.startDht()
    println("[spike] libtorrent4j session started, DHT running=${session.isDhtRunning}")

    // ── 2. Add magnet with SEQUENTIAL_DOWNLOAD ─────────────────────────────
    val ec = error_code()
    val params = libtorrent.parse_magnet_uri(magnet, ec)
    check(ec.value() == 0) { "Bad magnet: ${ec.message()}" }
    params.setSave_path(SAVE_DIR.absolutePath)
    params.setFlags(params.getFlags().or_(org.libtorrent4j.TorrentFlags.SEQUENTIAL_DOWNLOAD))

    val addEc = error_code()
    val swigHandle = session.swig().add_torrent(params, addEc)
    check(swigHandle != null && swigHandle.is_valid()) { "add_torrent failed: ${addEc.message()}" }
    val th = TorrentHandle(swigHandle)
    th.resume(); th.forceReannounce(); th.forceDHTAnnounce()
    println("[spike] Torrent added, waiting for metadata…")

    // ── 3. Wait for metadata ───────────────────────────────────────────────
    val metaStart = System.currentTimeMillis()
    while (!th.status().hasMetadata()) {
        Thread.sleep(300)
        val elapsed = System.currentTimeMillis() - metaStart
        if (elapsed % 3000 < 300) print(".")
        if (elapsed > META_TIMEOUT_MS) error("Metadata timeout after ${META_TIMEOUT_MS}ms")
    }
    println()
    println("[spike] Metadata resolved in ${System.currentTimeMillis() - metaStart}ms")

    val ti = th.torrentFile()!!
    val fs = ti.files()
    val n = fs.numFiles()

    // Pick largest video file
    var bestIdx = -1; var bestSize = -1L
    for (i in 0 until n) {
        val name = fs.fileName(i); val size = fs.fileSize(i)
        if (VIDEO_EXTS.any { name.endsWith(it, true) } && size > bestSize) {
            bestIdx = i; bestSize = size
        }
    }
    if (bestIdx == -1) { // fallback: largest file
        for (i in 0 until n) { val s = fs.fileSize(i); if (s > bestSize) { bestSize = s; bestIdx = i } }
    }
    check(bestIdx >= 0) { "No playable file found in torrent" }

    val fileName = fs.fileName(bestIdx)
    val filePath = fs.filePath(bestIdx)
    val pieceLen = ti.pieceLength()

    // Compute byte offset of this file within the torrent
    var fileOffset = 0L
    for (k in 0 until bestIdx) fileOffset += fs.fileSize(k)

    val startPiece = (fileOffset / pieceLen).toInt()
    val endPiece = ((fileOffset + bestSize - 1) / pieceLen).toInt()

    // Deselect other files, TOP_PRIORITY for ours
    for (i in 0 until n) {
        th.filePriority(i, if (i == bestIdx) Priority.TOP_PRIORITY else Priority.IGNORE)
    }
    // Boost header + trailer pieces
    for (p in startPiece..minOf(startPiece + 6, endPiece)) th.piecePriority(p, Priority.TOP_PRIORITY)
    for (p in maxOf(startPiece, endPiece - 6)..endPiece) th.piecePriority(p, Priority.TOP_PRIORITY)

    println("[spike] Streaming '$fileName' (${bestSize / 1024 / 1024} MB), pieces $startPiece..$endPiece")

    // Resolve disk path (file may be in a subfolder)
    fun resolveFile(): File {
        val candidates = listOf(
            File(SAVE_DIR, filePath),
            File(SAVE_DIR, fileName)
        )
        candidates.forEach { if (it.exists() && it.length() > 0) return it }
        SAVE_DIR.walkTopDown().maxDepth(4).firstOrNull { it.isFile && it.name == fileName }?.let { return it }
        return File(SAVE_DIR, filePath)
    }

    // ── 4. HTTP range server ───────────────────────────────────────────────
    val server = HttpServer.create(InetSocketAddress(HTTP_PORT), 0)
    server.createContext("/stream") { ex -> handleStream(ex, th, startPiece, endPiece, pieceLen, fileOffset, bestSize, ::resolveFile) }
    server.createContext("/status") { ex ->
        val s = th.status()
        val body = "peers=${s.numPeers()} seeds=${s.numSeeds()} " +
                   "speed=${s.downloadPayloadRate()/1024}KB/s progress=${(s.progress()*100).toInt()}%\n"
        ex.sendResponseHeaders(200, body.length.toLong())
        ex.responseBody.use { it.write(body.toByteArray()) }
    }
    server.executor = null
    server.start()

    println("[spike] HTTP server on http://localhost:$HTTP_PORT/stream")
    println("[spike] Status:  http://localhost:$HTTP_PORT/status")
    println("[spike] Press Ctrl+C to stop")
    Runtime.getRuntime().addShutdownHook(Thread { session.stop() })
    Thread.currentThread().join() // park main thread
}

@Suppress("NAME_SHADOWING")
fun handleStream(
    ex: HttpExchange,
    th: TorrentHandle,
    startPiece: Int,
    endPiece: Int,
    pieceLen: Int,
    fileOffset: Long,
    fileSize: Long,
    resolveFile: () -> File
) {
    val rangeHeader = ex.requestHeaders.getFirst("Range") // e.g. "bytes=0-65535"
    val (rangeStart, rangeEnd) = if (!rangeHeader.isNullOrBlank() && rangeHeader.startsWith("bytes=")) {
        val parts = rangeHeader.removePrefix("bytes=").split("-")
        val s = parts.getOrNull(0)?.toLongOrNull() ?: 0L
        val e = parts.getOrNull(1)?.toLongOrNull() ?: (fileSize - 1)
        s to minOf(e, fileSize - 1)
    } else {
        0L to (fileSize - 1)
    }

    val contentLength = rangeEnd - rangeStart + 1
    val statusCode = if (!rangeHeader.isNullOrBlank()) 206 else 200
    val ext = resolveFile().extension.lowercase()
    val mime = when (ext) {
        "mp4" -> "video/mp4"; "mkv" -> "video/x-matroska"
        "avi" -> "video/x-msvideo"; "webm" -> "video/webm"
        else -> "video/octet-stream"
    }
    ex.responseHeaders["Accept-Ranges"] = listOf("bytes")
    ex.responseHeaders["Content-Type"] = listOf(mime)
    if (statusCode == 206) ex.responseHeaders["Content-Range"] = listOf("bytes $rangeStart-$rangeEnd/$fileSize")
    ex.sendResponseHeaders(statusCode, contentLength)

    println("[http] ${ex.requestMethod} range=$rangeStart-$rangeEnd (${contentLength/1024}KB)")
    val t0 = System.currentTimeMillis()

    ex.responseBody.use { out ->
        var pos = rangeStart
        val buf = ByteArray(64 * 1024)
        var lastBoost = -1

        while (pos <= rangeEnd) {
            val torrentByte = fileOffset + pos
            val pieceIdx = (torrentByte / pieceLen).toInt()

            // Sliding-window boost: 16 pieces ahead
            val windowEnd = minOf(pieceIdx + 16, endPiece)
            if (windowEnd > lastBoost) {
                for (p in (lastBoost + 1)..windowEnd) th.piecePriority(p, Priority.TOP_PRIORITY)
                lastBoost = windowEnd
            }

            // Wait for current piece
            if (!th.havePiece(pieceIdx)) {
                th.setPieceDeadline(pieceIdx, 0) // urgent
                val waitStart = System.currentTimeMillis()
                while (!th.havePiece(pieceIdx)) {
                    Thread.sleep(40)
                    val file = resolveFile()
                    if (file.exists() && file.length() > pos + 1024) break // data already on disk
                    if (System.currentTimeMillis() - waitStart > PIECE_WAIT_MS) {
                        println("[http] piece $pieceIdx wait timeout — aborting")
                        return@use
                    }
                }
            }
            runCatching { th.flushCache() }

            val file = resolveFile()
            if (!file.exists()) { Thread.sleep(50); continue }

            try {
                RandomAccessFile(file, "r").use { raf ->
                    val fileLen = raf.length()
                    if (fileLen <= pos) { Thread.sleep(40); return@use }
                    raf.seek(pos)
                    val pieceEnd = ((pieceIdx + 1).toLong() * pieceLen) - fileOffset
                    val toRead = minOf(
                        buf.size.toLong(),
                        pieceEnd - pos,
                        rangeEnd - pos + 1,
                        fileLen - pos
                    ).toInt()
                    if (toRead <= 0) { Thread.sleep(30); return@use }
                    val n = raf.read(buf, 0, toRead)
                    if (n > 0) { out.write(buf, 0, n); pos += n }
                    else Thread.sleep(30)
                }
            } catch (e: Exception) {
                val m = e.message ?: ""
                if (m.contains("Broken pipe", true) || m.contains("Connection reset", true)) return@use
                println("[http] read error: $m")
                Thread.sleep(50)
            }
        }
        val elapsed = System.currentTimeMillis() - t0
        println("[http] served $rangeStart-$rangeEnd in ${elapsed}ms")
    }
}
