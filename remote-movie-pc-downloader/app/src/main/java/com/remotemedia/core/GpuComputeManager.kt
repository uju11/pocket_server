package com.remotemedia.core

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * GpuComputeManager
 *
 * Manages hardware GPU/codec acceleration for the PocketNode server:
 *
 *  1. MediaCodec hardware transcoding — uses the SoC's dedicated video codec
 *     block (separate from GPU cores) for H.264/H.265/AV1 encode+decode.
 *     This cuts CPU usage ~70% for Jellyfin streams.
 *
 *  2. GPU Compute flag — signals other subsystems (AI upscaling, thumbnail gen)
 *     that GPU compute is available and enabled.
 *
 *  3. Thermal / Memory safety guard — continuously monitors CPU temperature
 *     and RAM pressure. If either exceeds the configured threshold, GPU compute
 *     is automatically disabled and the user is notified.
 *
 * MediaCodec (hardware transcoding) is ALWAYS available independent of the
 * GPU compute flag — it uses dedicated silicon, not GPU cores, so it doesn't
 * cause thermal issues.
 */
object GpuComputeManager {

    // ── Thresholds ──────────────────────────────────────────────────────────
    const val DEFAULT_TEMP_LIMIT_C    = 65f    // °C — auto shutoff above this
    const val DEFAULT_RAM_LIMIT_PCT   = 85     // % RAM — auto shutoff above this

    // ── Notification ────────────────────────────────────────────────────────
    private const val CHANNEL_ID  = "gpu_compute_channel"
    private const val NOTIF_ID_WARN = 2001
    private const val NOTIF_ID_OFF  = 2002

    // ── State ────────────────────────────────────────────────────────────────
    private val _hardwareTranscodeEnabled = MutableStateFlow(false)
    /** Whether MediaCodec hardware transcoding is enabled for Jellyfin. */
    val hardwareTranscodeEnabled: StateFlow<Boolean> = _hardwareTranscodeEnabled.asStateFlow()

    private val _gpuComputeEnabled = MutableStateFlow(false)
    /** Whether GPU compute (AI upscaling, thumbnail gen) is active. */
    val gpuComputeEnabled: StateFlow<Boolean> = _gpuComputeEnabled.asStateFlow()

    private val _thermalLimitC = MutableStateFlow(DEFAULT_TEMP_LIMIT_C)
    val thermalLimitC: StateFlow<Float> = _thermalLimitC.asStateFlow()

    private val _ramLimitPct = MutableStateFlow(DEFAULT_RAM_LIMIT_PCT)
    val ramLimitPct: StateFlow<Int> = _ramLimitPct.asStateFlow()

    /** Capabilities detected from MediaCodecList at runtime. */
    data class CodecCapabilities(
        val supportsH264Hw: Boolean,
        val supportsH265Hw: Boolean,
        val supportsAv1Hw: Boolean,
        val supportsVp9Hw: Boolean,
        val decoderNames: List<String>,
        val encoderNames: List<String>
    )

    private val _codecCaps = MutableStateFlow<CodecCapabilities?>(null)
    val codecCapabilities: StateFlow<CodecCapabilities?> = _codecCaps.asStateFlow()

    private val _autoShutoffReason = MutableStateFlow<String?>(null)
    /** Non-null when GPU compute was auto-disabled, contains the reason string. */
    val autoShutoffReason: StateFlow<String?> = _autoShutoffReason.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var thermalWatchJob: Job? = null
    private var appContext: Context? = null

    // ── Init ─────────────────────────────────────────────────────────────────

    fun init(context: Context) {
        appContext = context.applicationContext
        createNotificationChannel(context)
        probeCodecCapabilities()

        // Load persisted GPU compute settings
        val prefs = context.getSharedPreferences("remote_media_prefs", Context.MODE_PRIVATE)
        val savedGpu = prefs.getBoolean("gpu_compute_enabled", false)
        val savedThermal = prefs.getFloat("thermal_limit_c", DEFAULT_TEMP_LIMIT_C)
        val savedRam = prefs.getInt("ram_limit_pct", DEFAULT_RAM_LIMIT_PCT)
        val savedHw = prefs.getBoolean("hw_transcode_enabled", true)

        _hardwareTranscodeEnabled.value = savedHw
        _thermalLimitC.value = savedThermal
        _ramLimitPct.value = savedRam

        if (savedGpu) {
            setGpuComputeEnabled(true, savedThermal, savedRam, persist = false)
        }

        Logger.i(LogTag.SYSTEM, "GpuComputeManager initialised. GPU: $savedGpu, Thermal: ${savedThermal}°C, RAM: $savedRam%, H/W Transcode: $savedHw")
    }

    // ── MediaCodec Hardware Transcoding ──────────────────────────────────────

    /**
     * Enable or disable MediaCodec hardware transcoding.
     * Persisted to SharedPreferences and read by JellyfinServer at start.
     */
    fun setHardwareTranscode(enabled: Boolean, context: Context = appContext!!) {
        _hardwareTranscodeEnabled.value = enabled
        context.getSharedPreferences("remote_media_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("hw_transcode_enabled", enabled).apply()
        Logger.i(LogTag.SYSTEM, "Hardware transcoding ${if (enabled) "ENABLED" else "DISABLED"}")
    }

    /** Returns the FFmpeg hwaccel argument string for Jellyfin's process builder. */
    fun getFFmpegHwAccelArgs(): List<String> {
        if (!_hardwareTranscodeEnabled.value) return emptyList()
        return listOf(
            "-hwaccel", "mediacodec",
            "-hwaccel_output_format", "mediacodec"
        )
    }

    /** Returns Jellyfin encoding.xml snippet to write into the config folder. */
    fun getJellyfinEncodingConfig(): String {
        val hwType = if (_hardwareTranscodeEnabled.value) "mediacodec" else ""
        return """<?xml version="1.0" encoding="utf-8"?>
<EncodingOptions xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:xsd="http://www.w3.org/2001/XMLSchema">
  <EnableThrottling>false</EnableThrottling>
  <HardwareAccelerationType>$hwType</HardwareAccelerationType>
  <EnableHardwareEncoding>${_hardwareTranscodeEnabled.value}</EnableHardwareEncoding>
  <AllowHevcEncoding>${_codecCaps.value?.supportsH265Hw ?: false}</AllowHevcEncoding>
  <AllowAv1Encoding>${_codecCaps.value?.supportsAv1Hw ?: false}</AllowAv1Encoding>
  <EncoderPreset>veryfast</EncoderPreset>
  <TranscodingTempPath>/data/local/tmp/jellyfin_transcode</TranscodingTempPath>
  <EnableAudioVbr>true</EnableAudioVbr>
</EncodingOptions>"""
    }

    // ── GPU Compute ──────────────────────────────────────────────────────────

    fun setGpuComputeEnabled(enabled: Boolean, tempLimit: Float = _thermalLimitC.value, ramLimit: Int = _ramLimitPct.value, persist: Boolean = true) {
        _thermalLimitC.value  = tempLimit
        _ramLimitPct.value    = ramLimit
        _autoShutoffReason.value = null

        if (persist) {
            appContext?.getSharedPreferences("remote_media_prefs", Context.MODE_PRIVATE)
                ?.edit()
                ?.putBoolean("gpu_compute_enabled", enabled)
                ?.putFloat("thermal_limit_c", tempLimit)
                ?.putInt("ram_limit_pct", ramLimit)
                ?.apply()
        }

        if (enabled) {
            _gpuComputeEnabled.value = true
            startThermalWatch()
            Logger.i(LogTag.SYSTEM, "GPU compute ENABLED. Auto-shutoff: >${tempLimit}°C or >${ramLimit}% RAM")
        } else {
            _gpuComputeEnabled.value = false
            stopThermalWatch()
            Logger.i(LogTag.SYSTEM, "GPU compute DISABLED.")
        }
    }

    fun setThermalLimitC(context: Context, tempLimit: Float) {
        _thermalLimitC.value = tempLimit
        context.getSharedPreferences("remote_media_prefs", Context.MODE_PRIVATE)
            .edit().putFloat("thermal_limit_c", tempLimit).apply()
        Logger.i(LogTag.SYSTEM, "Thermal limit adjusted to ${tempLimit}°C")
    }

    fun setRamLimitPct(context: Context, ramLimit: Int) {
        _ramLimitPct.value = ramLimit
        context.getSharedPreferences("remote_media_prefs", Context.MODE_PRIVATE)
            .edit().putInt("ram_limit_pct", ramLimit).apply()
        Logger.i(LogTag.SYSTEM, "RAM limit adjusted to $ramLimit%")
    }

    // ── Thermal / Memory Watchdog ────────────────────────────────────────────

    private fun startThermalWatch() {
        thermalWatchJob?.cancel()
        thermalWatchJob = scope.launch {
            while (_gpuComputeEnabled.value) {
                delay(5_000) // check every 5 seconds
                val metrics = SystemMetricsManager.metricsState.value

                val tempC    = metrics.cpuTempCelsius
                val ramPct   = metrics.ramPercent
                val isLowMem = metrics.isLowMemory

                when {
                    tempC > _thermalLimitC.value -> {
                        val reason = "CPU temp ${tempC.toInt()}°C exceeded limit ${_thermalLimitC.value.toInt()}°C"
                        autoShutoff(reason)
                    }
                    ramPct > _ramLimitPct.value || isLowMem -> {
                        val reason = "RAM pressure $ramPct% exceeded limit ${_ramLimitPct.value}%${if (isLowMem) " (LOW_MEM)" else ""}"
                        autoShutoff(reason)
                    }
                    tempC > _thermalLimitC.value - 10f -> {
                        // Warn at 10°C below shutoff threshold
                        sendWarningNotification(tempC)
                    }
                }
            }
        }
    }

    private fun stopThermalWatch() {
        thermalWatchJob?.cancel()
        thermalWatchJob = null
    }

    private fun autoShutoff(reason: String) {
        _gpuComputeEnabled.value = false
        _autoShutoffReason.value = reason
        stopThermalWatch()
        Logger.w(LogTag.SYSTEM, "GPU compute AUTO-SHUTOFF: $reason")
        sendShutoffNotification(reason)
    }

    fun clearAutoShutoffReason() {
        _autoShutoffReason.value = null
    }

    // ── Codec Detection ──────────────────────────────────────────────────────

    private fun probeCodecCapabilities() {
        scope.launch(Dispatchers.IO) {
            try {
                val list = MediaCodecList(MediaCodecList.ALL_CODECS)
                val infos = list.codecInfos

                val decoders = infos.filter { !it.isEncoder }
                val encoders = infos.filter { it.isEncoder }

                fun Boolean.or(b: Boolean) = this || b

                fun hasHwCodec(codecInfoList: List<android.media.MediaCodecInfo>, mime: String): Boolean {
                    return codecInfoList.any { info ->
                        info.supportedTypes.any { it.equals(mime, ignoreCase = true) } &&
                        !info.name.contains("sw", ignoreCase = true) &&   // exclude software fallbacks
                        !info.name.contains("omx.google", ignoreCase = true)
                    }
                }

                val caps = CodecCapabilities(
                    supportsH264Hw = hasHwCodec(decoders, MediaFormat.MIMETYPE_VIDEO_AVC),
                    supportsH265Hw = hasHwCodec(decoders, MediaFormat.MIMETYPE_VIDEO_HEVC),
                    supportsAv1Hw  = hasHwCodec(decoders, "video/av01"),
                    supportsVp9Hw  = hasHwCodec(decoders, MediaFormat.MIMETYPE_VIDEO_VP9),
                    decoderNames   = decoders.filter { !it.name.contains("omx.google") }.map { it.name }.take(8),
                    encoderNames   = encoders.filter { !it.name.contains("omx.google") }.map { it.name }.take(8)
                )
                _codecCaps.value = caps
                Logger.i(LogTag.SYSTEM, "Codec probe: H264=${caps.supportsH264Hw} H265=${caps.supportsH265Hw} AV1=${caps.supportsAv1Hw} VP9=${caps.supportsVp9Hw}")
            } catch (e: Exception) {
                Logger.e(LogTag.SYSTEM, "Codec probe error: ${e.message}")
            }
        }
    }

    // ── Notifications ────────────────────────────────────────────────────────

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "GPU Compute Alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Alerts when GPU compute is throttled or auto-disabled due to thermal/memory limits."
            }
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun sendWarningNotification(tempC: Float) {
        val ctx = appContext ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (androidx.core.content.ContextCompat.checkSelfPermission(
                        ctx,
                        android.Manifest.permission.POST_NOTIFICATIONS
                    ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    Logger.w(LogTag.SYSTEM, "Notification permission missing for thermal warning.")
                    return
                }
            }
            val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
            val n = NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle("⚠ GPU Compute — Thermal Warning")
                .setContentText("CPU temp ${tempC.toInt()}°C — approaching shutoff limit. Reduce workload.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
            nm.notify(NOTIF_ID_WARN, n)
        } catch (e: Throwable) {
            Logger.e(LogTag.SYSTEM, "Failed to send thermal warning notification: ${e.message}")
        }
    }

    private fun sendShutoffNotification(reason: String) {
        val ctx = appContext ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (androidx.core.content.ContextCompat.checkSelfPermission(
                        ctx,
                        android.Manifest.permission.POST_NOTIFICATIONS
                    ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    Logger.w(LogTag.SYSTEM, "Notification permission missing for auto-shutoff: $reason")
                    return
                }
            }
            val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
            val n = NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle("🛑 GPU Compute Auto-Disabled")
                .setContentText(reason)
                .setStyle(NotificationCompat.BigTextStyle().bigText("GPU compute was automatically disabled to protect the device.\n\nReason: $reason\n\nRe-enable manually from PocketNode once the device cools down."))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setAutoCancel(false)
                .build()
            nm.notify(NOTIF_ID_OFF, n)
        } catch (e: Throwable) {
            Logger.e(LogTag.SYSTEM, "Failed to send shutoff notification: ${e.message}")
        }
    }
}
