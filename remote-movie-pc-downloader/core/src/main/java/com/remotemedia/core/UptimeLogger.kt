package com.remotemedia.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * S3 spike: writes a heartbeat line to <filesDir>/uptime_log.txt every 5 min.
 * After a 24h manual test, inspect the file to confirm continuous uptime.
 * Throwaway — will be replaced by proper health metrics in M4+.
 */
object UptimeLogger {

    private const val INTERVAL_MS = 5 * 60 * 1000L // 5 minutes
    private val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var job: Job? = null
    private var logFile: File? = null
    private var startMs = 0L

    fun start(filesDir: File) {
        if (job?.isActive == true) return
        logFile = File(filesDir, "uptime_log.txt").also { f ->
            if (!f.exists()) f.createNewFile()
        }
        startMs = System.currentTimeMillis()
        logFile?.appendText("=== START ${fmt.format(Date(startMs))} ===\n")
        job = scope.launch {
            var tick = 0
            while (isActive) {
                delay(INTERVAL_MS)
                tick++
                val uptimeMins = (System.currentTimeMillis() - startMs) / 60_000
                val line = "[TICK $tick] ${fmt.format(Date())} | uptime=${uptimeMins}m\n"
                logFile?.appendText(line)
                Logger.i(LogTag.SYSTEM, "UptimeLogger: $line".trimEnd())
            }
        }
        Logger.i(LogTag.SYSTEM, "UptimeLogger started — log: ${logFile?.absolutePath}")
    }

    fun stop() {
        job?.cancel()
        job = null
        logFile?.appendText("=== STOP  ${fmt.format(Date())} ===\n")
        Logger.i(LogTag.SYSTEM, "UptimeLogger stopped")
    }

    fun logFilePath(): String = logFile?.absolutePath ?: "(not started)"
}
