package com.starkdroid.quicknotch.system

import java.io.File
import kotlin.math.roundToInt

/**
 * One macOS probe, already parsed: CPU and GPU load, memory in use, boot-volume
 * fill and the *cumulative* interface byte counters (the poller turns those into
 * a rate by subtracting the previous tick).
 */
internal data class MacProbe(
    val cpuPercent: Int? = null,
    val gpuPercent: Int? = null,
    val ramUsedBytes: Long? = null,
    val ramTotalBytes: Long? = null,
    val storagePercent: Int? = null,
    val netInBytes: Long? = null,
    val netOutBytes: Long? = null
)

// The virtual-memory buckets that count as "in use" for the ring: active +
// wired + compressed. Free/inactive/speculative pages are reclaimable, so
// including them would report a machine that is always at 90%.
private val RamUsedKeys = listOf(
    "Pages active:",
    "Pages wired down:",
    "Pages occupied by compressor:"
)

private val GpuUtilisation = Regex("\"Device Utilization %\"=(\\d+)")

/**
 * macOS half of the system monitor. Everything comes from one `/bin/sh` round
 * trip — measured at ~40 ms for the whole set, versus ~5 separate process
 * spawns if each reading were its own command. `netstat` is numeric (`-n`):
 * without it, macOS resolves every interface address through DNS and the probe
 * blocked for 5 s in testing.
 *
 * There is no public CPU/GPU API reachable from the JVM here, so the readings
 * are the same ones a shell-minded user would run: `ps` sums to the machine
 * percentage, `vm_stat` + `hw.memsize` give memory, `df` gives the data
 * volume's fill (the read-only system volume always looks nearly empty) and
 * `ioreg`'s IOAccelerator statistics give GPU utilization where the driver
 * publishes it.
 */
internal object MacSystemProbe {

    /** Runs the combined probe and parses every section it printed. */
    fun read(): MacProbe? {
        val raw = SystemMetrics.runProcess(listOf("/bin/sh", "-c", probeScript)) ?: return null
        val blocks = sections(raw)
        val sys = blocks["SYS"].orEmpty()
        val cores = sys.getOrNull(0)?.toIntOrNull() ?: 0
        val totalRam = sys.getOrNull(1)?.toLongOrNull()
        val net = parseNetCounters(blocks["NET"].orEmpty())
        return MacProbe(
            cpuPercent = cpuPercent(blocks["PS"].orEmpty(), cores),
            gpuPercent = parseGpuPercent(blocks["GPU"].orEmpty()),
            ramUsedBytes = ramUsedBytes(blocks["VM"].orEmpty(), totalRam),
            ramTotalBytes = totalRam,
            storagePercent = parseStoragePercent(blocks["DISK"].orEmpty()),
            netInBytes = net?.first,
            netOutBytes = net?.second
        )
    }

    /** Primary GPU name, read once per run: `system_profiler` costs ~300 ms. */
    fun gpuName(): String? =
        SystemMetrics.runProcess(
            listOf("system_profiler", "SPDisplaysDataType", "-detailLevel", "mini"),
            timeoutMs = 8_000L
        )
            ?.lineSequence()
            ?.map { it.trim() }
            ?.firstOrNull { it.startsWith("Chipset Model:") }
            ?.substringAfter(':')
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
}

/**
 * The single shell invocation: every `echo @@NAME` is a section marker the
 * parser splits on, so one process feeds all five readings.
 */
private val probeScript: String by lazy {
    val volume = if (File("/System/Volumes/Data").canRead()) "/System/Volumes/Data" else "/"
    """
    echo @@SYS
    sysctl -n hw.ncpu hw.memsize
    echo @@PS
    ps -A -o %cpu=
    echo @@VM
    vm_stat
    echo @@NET
    netstat -ibn
    echo @@DISK
    df -k $volume
    echo @@GPU
    ioreg -r -d 1 -c IOAccelerator
    """.trimIndent()
}

/** Splits `@@NAME`-marked output into trimmed, empty-line-free blocks. */
private fun sections(raw: String): Map<String, List<String>> =
    raw.split("@@")
        .mapNotNull { block ->
            val newline = block.indexOf('\n')
            if (newline < 0) return@mapNotNull null
            block.substring(0, newline).trim() to block.substring(newline + 1)
                .lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        }
        .toMap()

/**
 * `ps %cpu` is per process, where 100 means one full core. Summing gives
 * "cores × 100"-scale load, so dividing by the core count lands back on the
 * machine-wide 0-100 the ring draws. It is the decaying recent average `ps`
 * reports, which is exactly the calm reading a glanceable ring wants.
 */
private fun cpuPercent(lines: List<String>, cores: Int): Int? {
    if (cores <= 0 || lines.isEmpty()) return null
    val total = lines.sumOf { it.toDoubleOrNull() ?: 0.0 }
    return (total / cores).roundToInt().coerceIn(0, 100)
}

private fun ramUsedBytes(lines: List<String>, totalBytes: Long?): Long? {
    val pageSize = lines.firstOrNull()
        ?.let { Regex("page size of (\\d+) bytes").find(it) }
        ?.groupValues
        ?.get(1)
        ?.toLongOrNull()
    if (totalBytes == null || pageSize == null) return null
    val usedPages = lines.sumOf { line ->
        if (RamUsedKeys.none { line.startsWith(it) }) return@sumOf 0L
        line.substringAfter(':').trim().removeSuffix(".").toLongOrNull() ?: 0L
    }
    return if (usedPages > 0L) (usedPages * pageSize).coerceAtMost(totalBytes) else null
}

/**
 * Cumulative interface byte counters, summed over every real interface.
 * `netstat -ib` prints several rows per interface (one per address family) and
 * only the first carries the counters, so names are de-duplicated. Loopback is
 * skipped: browser↔app traffic on `lo0` is not the network the user is watching.
 */
private fun parseNetCounters(lines: List<String>): Pair<Long, Long>? {
    val seen = mutableSetOf<String>()
    var inbound = 0L
    var outbound = 0L
    lines.forEach { line ->
        val parts = line.split(Regex("\\s+"))
        if (parts.size < 10) return@forEach
        val name = parts[0]
        if (name == "lo0" || !seen.add(name)) return@forEach
        val inNow = parts[6].toLongOrNull() ?: return@forEach
        val outNow = parts[9].toLongOrNull() ?: return@forEach
        inbound += inNow
        outbound += outNow
    }
    return if (inbound > 0L || outbound > 0L) inbound to outbound else null
}

private fun parseStoragePercent(lines: List<String>): Int? {
    val parts = lines.firstOrNull { it.startsWith("/") }
        ?.split(Regex("\\s+"))
        ?: return null
    val total = parts.getOrNull(1)?.toLongOrNull() ?: 0L
    val used = parts.getOrNull(2)?.toLongOrNull() ?: 0L
    return if (total > 0L && used > 0L) {
        ((used.toDouble() / total) * 100.0).roundToInt().coerceIn(0, 100)
    } else {
        null
    }
}

/**
 * Highest `Device Utilization %` across the IOAccelerator nodes. Drivers that
 * publish no such key (some discrete cards, older macOS) simply yield null and
 * the GPU tile stays off the page.
 */
private fun parseGpuPercent(lines: List<String>): Int? =
    lines
        .mapNotNull { GpuUtilisation.find(it)?.groupValues?.get(1)?.toIntOrNull() }
        .maxOrNull()
        ?.coerceIn(0, 100)
