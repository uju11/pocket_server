package com.remotemedia.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import com.remotemedia.ServerForegroundService
import com.remotemedia.core.LogTag
import com.remotemedia.core.Logger

/**
 * BootReceiver
 *
 * Listens for system boot completion and automatically launches PocketNode daemon
 * services if the "boot_auto_start" setting is enabled in preferences.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Logger.i(LogTag.SYSTEM, "BootReceiver received action: $action")

        val validActions = listOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON"
        )

        if (action in validActions) {
            val prefs = context.getSharedPreferences("remote_media_prefs", Context.MODE_PRIVATE)
            val autoStart = prefs.getBoolean("boot_auto_start", true)

            if (autoStart) {
                Logger.i(LogTag.SYSTEM, "Boot auto-start enabled. Launching ServerForegroundService...")
                val serviceIntent = Intent(context, ServerForegroundService::class.java).apply {
                    this.action = ServerForegroundService.ACTION_START
                }
                try {
                    ContextCompat.startForegroundService(context, serviceIntent)
                    Logger.i(LogTag.SYSTEM, "ServerForegroundService started successfully on boot.")
                } catch (e: Exception) {
                    Logger.e(LogTag.SYSTEM, "Failed to start service on boot: ${e.message}")
                }
            } else {
                Logger.i(LogTag.SYSTEM, "Boot auto-start is disabled in settings. Skipping daemon launch.")
            }
        }
    }
}
