package com.remotemedia.core

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LogTag(val displayName: String) {
    ALL("All"),
    SYSTEM("System"),
    JELLYFIN("Jellyfin"),
    TORRENT("Torrent"),
    TELEGRAM("Telegram"),
    DLNA("DLNA"),
    HTTP("HTTP"),
    STORAGE("Storage")
}

enum class LogLevel {
    INFO, WARN, ERROR
}

data class LogEntry(
    val id: Long = System.currentTimeMillis() + (0..99999).random(),
    val timestamp: String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()),
    val tag: LogTag,
    val message: String,
    val level: LogLevel = LogLevel.INFO
)

object Logger {
    private const val MAX_LOG_CAPACITY = 1000
    private val logList = mutableListOf<LogEntry>()
    private val _logsState = MutableStateFlow<List<LogEntry>>(emptyList())
    val logsState: StateFlow<List<LogEntry>> = _logsState.asStateFlow()

    @Synchronized
    fun log(tag: LogTag, message: String, level: LogLevel = LogLevel.INFO) {
        val entry = LogEntry(tag = tag, message = message, level = level)
        
        when (level) {
            LogLevel.INFO -> Log.i(tag.name, message)
            LogLevel.WARN -> Log.w(tag.name, message)
            LogLevel.ERROR -> Log.e(tag.name, message)
        }

        if (logList.size >= MAX_LOG_CAPACITY) {
            logList.removeAt(0)
        }
        logList.add(entry)
        _logsState.value = logList.toList()
    }

    fun i(tag: LogTag, message: String) = log(tag, message, LogLevel.INFO)
    fun w(tag: LogTag, message: String) = log(tag, message, LogLevel.WARN)
    fun e(tag: LogTag, message: String) = log(tag, message, LogLevel.ERROR)

    @Synchronized
    fun clear() {
        logList.clear()
        _logsState.value = emptyList()
    }
}
