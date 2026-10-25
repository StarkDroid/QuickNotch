package com.starkdroid.quicknotch.system

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** The one-time facts `system-metrics.ps1 -Static` reports. */
@Serializable
private data class WindowsStaticInfo(
    @SerialName("ramTotalBytes") val ramTotalBytes: Long = 0L,
    @SerialName("gpuName") val gpuName: String? = null
)

/** One PDH sample from `system-metrics.ps1`. */
@Serializable
private data class WindowsCounters(
    @SerialName("cpuPercent") val cpuPercent: Int? = null,
    @SerialName("ramAvailableBytes") val ramAvailableBytes: Long? = null,
    @SerialName("gpuPercent") val gpuPercent: Int? = null,
    @SerialName("netInBps") val netInBps: Long? = null,
    @SerialName("netOutBps") val netOutBps: Long? = null,
    @SerialName("diskUsedPercent") val diskUsedPercent: Int? = null
)

private val windowsJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

/**
 * Windows half of the system monitor.
 *
 * One `system-metrics.ps1` spawn per tick collects everything through PDH
 * (`Get-Counter`, sampled over one second — CPU and the network rates are
 * deltas, so they need two PDH samples and Get-Counter supplies both) plus
 * `DriveInfo` for the boot volume. RAM *total* is a static fact and is read
 * once at startup, so the per-tick script never pays for a second WMI query
 * beyond the GPU-engine counter set.
 *
 * GPU utilization is the 3D engine's `Utilization Percentage` summed across GPU
 * engine instances and clamped to 100 — the same number Task Manager shows as
 * "GPU", approximated. Machines without GPU performance counters (no WDDM 2.0
 * engine provider) simply get no GPU tile.
 */
internal object WindowsSystemProbe {

    @Volatile
    private var totalRamBytes: Long = 0L

    /** Static facts once per run; returns the primary GPU name when one exists. */
    fun readStatic(script: File?): String? {
        val info = runScript(script, static = true)
            ?.decode(WindowsStaticInfo.serializer())
            ?: return null
        totalRamBytes = info.ramTotalBytes.coerceAtLeast(0L)
        return info.gpuName?.takeIf { it.isNotBlank() }
    }

    /** One tick: CPU, memory, GPU, disk and network rates. */
    fun sample(script: File?): SystemSample? {
        val counters = runScript(script, static = false)
            ?.decode(WindowsCounters.serializer())
            ?: return null
        val total = totalRamBytes.takeIf { it > 0L }
        val available = counters.ramAvailableBytes
        return SystemSample(
            cpuPercent = counters.cpuPercent?.coerceIn(0, 100),
            gpuPercent = counters.gpuPercent?.coerceIn(0, 100),
            ramUsedBytes = if (total != null && available != null) {
                (total - available).coerceIn(0L, total)
            } else {
                null
            },
            ramTotalBytes = total,
            storagePercent = counters.diskUsedPercent?.coerceIn(0, 100),
            downBytesPerSec = counters.netInBps?.coerceAtLeast(0L),
            upBytesPerSec = counters.netOutBps?.coerceAtLeast(0L)
        )
    }

    private fun runScript(script: File?, static: Boolean): String? =
        script?.let { file ->
            SystemMetrics.runProcess(powershell(file, static))
                ?.lineSequence()
                // Get-Counter warnings ride stderr (merged into stdout); the
                // payload is the one line that opens a JSON object.
                ?.firstOrNull { it.trimStart().startsWith("{") }
        }
}

private fun powershell(file: File, static: Boolean): List<String> =
    listOf(
        "powershell.exe",
        "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
        "-File", file.absolutePath
    ) + if (static) listOf("-Static") else emptyList()

private fun <T> String.decode(serializer: kotlinx.serialization.DeserializationStrategy<T>): T? =
    runCatching { windowsJson.decodeFromString(serializer, this) }.getOrNull()
