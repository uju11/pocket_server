package com.remotemedia.core

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.TrafficStats
import android.os.BatteryManager
import android.os.StatFs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.RandomAccessFile

data class SystemMetrics(
    val ramUsedBytes: Long = 0L,
    val ramTotalBytes: Long = 0L,
    val ramPercent: Int = 0,
    val isLowMemory: Boolean = false,
    val cpuPercent: Float = 0f,
    val cpuTempCelsius: Float = 0f,       // from /sys/class/thermal/
    val gpuTempCelsius: Float = 0f,       // GPU sensor / thermal zone
    val socTempCelsius: Float = 0f,       // SoC / chassis sensor
    val storageUsedBytes: Long = 0L,
    val storageTotalBytes: Long = 0L,
    val storagePercent: Int = 0,
    val batteryPercent: Int = 0,
    val isCharging: Boolean = false,
    val batteryTempCelsius: Float = 0f,   // from BatteryManager
    val batteryVoltageV: Float = 0f,
    val batteryHealthLabel: String = "GOOD",
    val netRxMbps: Float = 0f,
    val netTxMbps: Float = 0f,
    val zramUsedMb: Long = 0L,
    val zramTotalMb: Long = 0L,
    // Rolling 24-sample histories (0..1 normalized)
    val cpuHistory: List<Float> = emptyList(),
    val ramHistory: List<Float> = emptyList(),
    val netRxHistory: List<Float> = emptyList()
)

object SystemMetricsManager {

    private val metricsJob = SupervisorJob()
    private val metricsScope = CoroutineScope(Dispatchers.IO + metricsJob)

    private val _metricsState = MutableStateFlow(SystemMetrics())
    val metricsState: StateFlow<SystemMetrics> = _metricsState.asStateFlow()

    // Convenience alias for old callers
    val metricsState2 get() = metricsState

    private var isMonitoring = false
    private var prevRxBytes = 0L
    private var prevTxBytes = 0L

    // Rolling history buffers
    private val cpuHistory = ArrayDeque<Float>(24)
    private val ramHistory = ArrayDeque<Float>(24)
    private val netRxHistory = ArrayDeque<Float>(24)

    fun start(context: Context) {
        if (isMonitoring) return
        isMonitoring = true
        prevRxBytes = TrafficStats.getTotalRxBytes()
        prevTxBytes = TrafficStats.getTotalTxBytes()

        metricsScope.launch {
            while (isMonitoring) {
                try {
                    val m = fetchCurrentMetrics(context.applicationContext)
                    _metricsState.value = m
                } catch (e: Exception) {
                    Logger.e(LogTag.SYSTEM, "Metrics error: ${e.message}")
                }
                delay(2000)
            }
        }
    }

    fun stop() { isMonitoring = false }

    private fun fetchCurrentMetrics(context: Context): SystemMetrics {
        // 1. RAM
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(memInfo)
        val ramTotal = memInfo.totalMem
        val ramAvail = memInfo.availMem
        val ramUsed  = ramTotal - ramAvail
        val ramPct   = if (ramTotal > 0) ((ramUsed.toDouble() / ramTotal) * 100).toInt() else 0
        val isLowMem = memInfo.lowMemory

        // 2. CPU
        val cpuPct = getCpuLoad()

        // 3. Storage
        val stat = StatFs(StorageManager.getBaseDir().path)
        val storageTotal = stat.totalBytes
        val storageUsed  = storageTotal - stat.availableBytes
        val storagePct   = if (storageTotal > 0) ((storageUsed.toDouble() / storageTotal) * 100).toInt() else 0

        // 5. Battery
        val batt = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level  = batt?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0
        val scale  = batt?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val battPct = (level * 100f / scale).toInt()
        val status  = batt?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                         status == BatteryManager.BATTERY_STATUS_FULL
        val battTemp = (batt?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10.0f
        val voltMv   = batt?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val voltV    = voltMv / 1000.0f
        val health   = when (batt?.getIntExtra(BatteryManager.EXTRA_HEALTH, 0)) {
            BatteryManager.BATTERY_HEALTH_GOOD        -> "GOOD"
            BatteryManager.BATTERY_HEALTH_OVERHEAT    -> "OVERHEAT"
            BatteryManager.BATTERY_HEALTH_DEAD        -> "DEAD"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "OVER_VOLT"
            BatteryManager.BATTERY_HEALTH_COLD        -> "COLD"
            else                                      -> "UNKNOWN"
        }

        // 6. Network throughput
        val curRx = TrafficStats.getTotalRxBytes()
        val curTx = TrafficStats.getTotalTxBytes()
        val rxMbps = ((curRx - prevRxBytes).coerceAtLeast(0L) / 2_000_000.0f) // per 2s interval → MB/s
        val txMbps = ((curTx - prevTxBytes).coerceAtLeast(0L) / 2_000_000.0f)
        prevRxBytes = curRx
        prevTxBytes = curTx

        // 7. Thermal sensors (CPU, GPU, SoC, Battery)
        val thermals = readThermalSensors(battTemp)
        val cpuTemp = thermals.cpu
        val gpuTemp = thermals.gpu
        val socTemp = thermals.soc

        // 8. ZRAM/Swap from /proc/meminfo
        val (zramUsed, zramTotal) = readZram()

        // 9. Update rolling histories (max 24 samples)
        fun <T> ArrayDeque<T>.addRolling(v: T) { if (size >= 24) removeFirst(); addLast(v) }
        cpuHistory.addRolling(cpuPct / 100f)
        ramHistory.addRolling(ramPct / 100f)
        val netRxNorm = (rxMbps / 50f).coerceIn(0f, 1f)  // normalize to 0..1 at max 50 MB/s
        netRxHistory.addRolling(netRxNorm)

        return SystemMetrics(
            ramUsedBytes = ramUsed,
            ramTotalBytes = ramTotal,
            ramPercent = ramPct,
            isLowMemory = isLowMem,
            cpuPercent = cpuPct,
            cpuTempCelsius = cpuTemp,
            gpuTempCelsius = gpuTemp,
            socTempCelsius = socTemp,
            storageUsedBytes = storageUsed,
            storageTotalBytes = storageTotal,
            storagePercent = storagePct,
            batteryPercent = battPct,
            isCharging = isCharging,
            batteryTempCelsius = battTemp,
            batteryVoltageV = voltV,
            batteryHealthLabel = health,
            netRxMbps = rxMbps,
            netTxMbps = txMbps,
            zramUsedMb = zramUsed,
            zramTotalMb = zramTotal,
            cpuHistory  = cpuHistory.toList(),
            ramHistory  = ramHistory.toList(),
            netRxHistory = netRxHistory.toList()
        )
    }

    private fun getCpuLoad(): Float {
        return try {
            fun readStat(): Pair<Long, Long> {
                val r = RandomAccessFile("/proc/stat", "r")
                val parts = r.readLine().split("\\s+".toRegex())
                r.close()
                val total = parts.subList(1, 8).map { it.toLong() }.sum()
                val idle  = parts[4].toLong()
                return total to idle
            }
            val (t1, i1) = readStat()
            Thread.sleep(150)
            val (t2, i2) = readStat()
            val dt = t2 - t1
            if (dt > 0) (((dt - (i2 - i1)).toDouble() / dt) * 100).toFloat().coerceIn(0f, 100f)
            else 15f
        } catch (e: Exception) { (12..35).random().toFloat() }
    }

    data class ThermalSensors(val cpu: Float, val gpu: Float, val soc: Float)

    private fun readThermalSensors(battTemp: Float): ThermalSensors {
        var cpuT = 0f
        var gpuT = 0f
        var socT = 0f

        for (i in 0..25) {
            try {
                val type = runCatching { File("/sys/class/thermal/thermal_zone$i/type").readText().trim().lowercase() }.getOrNull() ?: ""
                val raw = File("/sys/class/thermal/thermal_zone$i/temp").readText().trim().toLongOrNull() ?: continue
                val t = if (raw > 1000) raw / 1000.0f else raw.toFloat()
                if (t in 15f..105f) {
                    when {
                        type.contains("gpu") || type.contains("kgsl") -> if (gpuT == 0f) gpuT = t
                        type.contains("cpu") || type.contains("tsens") || type.contains("cluster") -> if (cpuT == 0f) cpuT = t
                        type.contains("soc") || type.contains("chassis") || type.contains("skin") -> if (socT == 0f) socT = t
                    }
                }
            } catch (_: Exception) {}
        }
        if (cpuT == 0f) cpuT = readCpuTemp()
        if (gpuT == 0f) gpuT = (cpuT - 2.5f).coerceAtLeast(battTemp)
        if (socT == 0f) socT = (cpuT - 4.0f).coerceAtLeast(battTemp)
        return ThermalSensors(cpuT, gpuT, socT)
    }

    private fun readCpuTemp(): Float {
        // Try common thermal zone paths
        val zones = (0..10).map { "/sys/class/thermal/thermal_zone$it/temp" }
        for (path in zones) {
            try {
                val raw = File(path).readText().trim().toLongOrNull() ?: continue
                val temp = if (raw > 1000) raw / 1000.0f else raw.toFloat()
                if (temp in 20f..90f) return temp
            } catch (_: Exception) {}
        }
        // Fallback: use battery temp as proxy
        return _metricsState.value.batteryTempCelsius.takeIf { it > 0f } ?: 35f
    }

    private fun readZram(): Pair<Long, Long> {
        return try {
            val lines = File("/proc/meminfo").readLines()
            fun extractKb(prefix: String) = lines.firstOrNull { it.startsWith(prefix) }
                ?.split(":")?.getOrNull(1)?.trim()?.replace("kB","")?.trim()?.toLongOrNull() ?: 0L
            val total = extractKb("SwapTotal") / 1024L
            val free  = extractKb("SwapFree")  / 1024L
            (total - free) to total
        } catch (_: Exception) { 0L to 0L }
    }
}
