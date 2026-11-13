package com.remotemedia.storage

import android.content.Context
import java.io.File
import java.io.RandomAccessFile
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * M2.1 — Resumable chunked upload manager.
 * Protocol:
 *   POST /api/drive/upload/init?name=foo.mp4&size=1234567
 *     → { "upload_id": "<uuid>", "chunk_size": 2097152 }
 *   POST /api/drive/upload/chunk?upload_id=<uuid>&offset=<bytes>  body=raw bytes
 *     → { "received": <bytes_so_far> }
 *   POST /api/drive/upload/complete?upload_id=<uuid>
 *     → { "path": "<relative_path>" }
 *   DELETE /api/drive/upload/cancel?upload_id=<uuid>
 *     → 200
 */
object DriveUploadManager {

    const val CHUNK_SIZE = 2 * 1024 * 1024  // 2 MB suggested chunks

    data class UploadSession(
        val uploadId: String,
        val fileName: String,
        val totalSize: Long,
        val tempFile: File,
        var bytesReceived: Long = 0L
    )

    private val sessions = ConcurrentHashMap<String, UploadSession>()
    private lateinit var tempDir: File
    private lateinit var baseDir: File

    fun init(context: Context) {
        tempDir = File(context.cacheDir, "uploads_tmp").also { it.mkdirs() }
        baseDir = StorageManager.getBaseDir()
    }

    // ── Public API ────────────────────────────────────────────────

    fun initSession(fileName: String, totalSize: Long): UploadSession {
        val id = UUID.randomUUID().toString()
        val safe = fileName.replace(Regex("[^a-zA-Z0-9._\\- ]"), "_")
        val temp = File(tempDir, "$id.tmp")
        // Pre-allocate if size known and reasonable (< 4 GB)
        if (totalSize in 1..4L * 1024 * 1024 * 1024) {
            RandomAccessFile(temp, "rw").use { it.setLength(totalSize) }
        }
        val session = UploadSession(id, safe, totalSize, temp)
        sessions[id] = session
        return session
    }

    /** Write [bytes] at [offset]. Returns updated bytes received. */
    @Synchronized
    fun writeChunk(uploadId: String, offset: Long, bytes: ByteArray): Long {
        val s = sessions[uploadId] ?: error("unknown upload_id")
        RandomAccessFile(s.tempFile, "rw").use { raf ->
            raf.seek(offset)
            raf.write(bytes)
        }
        // Track high-water mark (assumes chunks arrive in order or idempotently)
        s.bytesReceived = maxOf(s.bytesReceived, offset + bytes.size)
        return s.bytesReceived
    }

    /** Move temp file → baseDir/<fileName>, return relative path. */
    fun complete(uploadId: String): String {
        val s = sessions.remove(uploadId) ?: error("unknown upload_id")
        val dest = uniqueDest(baseDir, s.fileName)
        s.tempFile.renameTo(dest)
        return dest.name
    }

    fun cancel(uploadId: String) {
        sessions.remove(uploadId)?.tempFile?.delete()
    }

    fun getSession(uploadId: String): UploadSession? = sessions[uploadId]

    // ── Helpers ───────────────────────────────────────────────────

    private fun uniqueDest(dir: File, name: String): File {
        var f = File(dir, name)
        var n = 1
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext  = if (dot > 0) name.substring(dot) else ""
        while (f.exists()) { f = File(dir, "$base($n)$ext"); n++ }
        return f
    }
}
