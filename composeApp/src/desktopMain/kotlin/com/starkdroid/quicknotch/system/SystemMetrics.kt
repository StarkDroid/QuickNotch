package com.starkdroid.quicknotch.system

import com.starkdroid.quicknotch.datasource.HostPlatform
import com.starkdroid.quicknotch.media.NativeResources
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * One reading of the machine's own health, as the System widget page draws it:
 * CPU, GPU (when the OS exposes one), memory, the boot volume and the network
 * throughput since the previous sample.
 *
 * Every field is nullable on purpose. A metric the current OS will not tell us
 * (GPU utilization on a Mac without `Device Utilization %`, the very first
 * network sample, a failed probe) stays null and the page draws a dim ring with
 * a dash instead of inventing a number. The poller merges each fresh sample
 * over the last one, so a transient failure keeps the previous value rather
 * than blanking the notch.
 */
data class SystemSnapshot(
    val cpuPercent: Int? = null,
    /** Human name of the primary GPU, e.g. "Apple M1" / "NVIDIA GeForce RTX 3060". */
    val gpuName: String? = null,
    val gpuPercent: Int? = null,
    val ramUsedBytes: Long? = null,
    val ramTotalBytes: Long? = null,
    val storagePercent: Int? = null,
    val downBytesPerSec: Long = 0L,
    val upBytesPerSec: Long = 0L,
    /**
     * Round-trip time of one TCP handshake to a public resolver, in ms. Used by
     * the gaming HUD ("how is my connection to the match server") and kept off
     * the System page, which is about load rather than reachability. Null until
     * the first probe answers, and on a machine with no route out.
     */
    val pingMs: Int? = null
) {
    /** Memory in use as 0-100, or null until both sides of the ratio are known. */
    val ramPercent: Int?
        get() {
            val used = ramUsedBytes ?: return null
            val total = ramTotalBytes ?: return null
            if (total <= 0L) return null
            return ((used.toDouble() / total) * 100.0).roundToInt().coerceIn(0, 100)
        }

    /**
     * Whether a graphics engine is actually reporting. A GPU name alone is not
     * enough on a machine with a display adapter the OS has no counters for, so
     * the page only claims a GPU when a utilization reading exists.
     */
    val gpuAvailable: Boolean get() = gpuPercent != null

    /**
     * Whether this snapshot carries any reading at all. A probe can answer with
     * an empty object (every counter on the machine failed), and such a
     * snapshot must never reach the page: it would put a System page on the
     * wheel that shows nothing but dashes.
     */
    val hasAnyReading: Boolean
        get() = cpuPercent != null || gpuPercent != null || ramUsedBytes != null ||
            storagePercent != null
}

/**
 * The dynamic half of a probe: only what the OS reported *this* tick. Null
 * fields mean "no reading now" (or "not available on this platform"), which the
 * poller fills in from the previous snapshot.
 */
internal data class SystemSample(
    val cpuPercent: Int? = null,
    val gpuPercent: Int? = null,
    val ramUsedBytes: Long? = null,
    val ramTotalBytes: Long? = null,
    val storagePercent: Int? = null,
    val downBytesPerSec: Long? = null,
    val upBytesPerSec: Long? = null,
    val pingMs: Int? = null
)

/**
 * Polls a handful of cheap OS readings into one [StateFlow] the notch observes.
 *
 * Cost is the whole design constraint here, because the notch polls for as long
 * as the app runs: macOS runs exactly one `/bin/sh` per tick that shells out to
 * `ps`, `vm_stat`, `netstat` and `ioreg` (≈40 ms of work together), and Windows
 * runs one `system-metrics.ps1` per tick that samples PDH counters once. Batch
 * modelling static facts (total RAM, GPU name, core count) is read once at
 * start, never per tick. The loop is gated on [setEnabled], so a user who
 * turns the widget off pays literally nothing.
 */
object SystemMetrics {

    /** macOS ticks fast enough to feel live; a Windows PDH sample itself takes ~1 s. */
    private val pollMs: Long = if (HostPlatform.isWindows) 4_000L else 2_000L
    private const val ProbeTimeoutMs = 12_000L

    /** Latency is probed every other tick: 4 s on macOS, 8 s on Windows. */
    private const val PingEveryTicks = 2L

    private var sampleTick = 0L

    private val _state = MutableStateFlow<SystemSnapshot?>(null)
    val state: StateFlow<SystemSnapshot?> = _state.asStateFlow()

    private var scope: CoroutineScope? = null
    private var pollJob: Job? = null

    /** Widget toggle; a disabled poller does no work at all. */
    @Volatile
    private var enabled = false

    /** Static facts read once per run by the platform probe. */
    @Volatile
    private var gpuName: String? = null

    @Volatile
    private var windowsScript: File? = null

    /** Previous cumulative network counters, for the macOS throughput delta. */
    private var lastNet: NetCounters? = null

    internal data class NetCounters(val inBytes: Long, val outBytes: Long, val nanos: Long)

    fun start() {
        stop()
        if (HostPlatform.isWindows) {
            windowsScript = NativeResources.extract("windows/system-metrics.ps1", ".ps1")
        }
        val active = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = active
        // Static facts are read once, off the poll loop: on macOS the GPU name
        // comes from `system_profiler` (~300 ms), which is far too slow to pay
        // on every tick.
        active.launch { gpuName = staticGpuName() }
        pollJob = active.launch {
            while (isActive) {
                if (enabled) sample()
                delay(pollMs)
            }
        }
    }

    fun stop() {
        pollJob?.cancel()
        pollJob = null
        scope?.cancel()
        scope = null
        lastNet = null
        _state.value = null
    }

    /**
     * Turns polling on or off with the widget toggle. Enabling takes a sample
     * immediately so the page is populated the moment it joins the wheel;
     * disabling clears the readout so a toggled-off widget can never page.
     */
    fun setEnabled(value: Boolean) {
        if (enabled == value) return
        enabled = value
        if (value) {
            poke()
        } else {
            lastNet = null
            _state.value = null
        }
    }

    /** Forces an immediate sample, e.g. when the dock opens. */
    fun poke() {
        val active = scope ?: return
        if (!enabled) return
        active.launch { sample() }
    }

    private fun sample() {
        if (!enabled) return
        // Latency is one TCP handshake, not a per-tick cost worth paying: a
        // round trip does not change every two seconds, and the HUD only needs
        // to catch a connection going bad. Every other tick keeps it fresh
        // enough (4 s on macOS, 8 s on Windows) while halving the sockets.
        sampleTick++
        val ping = if (sampleTick % PingEveryTicks == 1L) probePingMs() else null
        val fresh = if (HostPlatform.isWindows) WindowsSystemProbe.sample(windowsScript) else macSample()
        if (fresh != null) emit(fresh.copy(pingMs = ping))
    }

    /** macOS: one shell round-trip for CPU, memory, network, disk and GPU. */
    private fun macSample(): SystemSample? {
        val probe = MacSystemProbe.read() ?: return null
        val net = probe.netInBytes?.let { inBytes -> probe.netOutBytes?.let { out -> inBytes to out } }
        val (down, up) = netRates(net)
        return SystemSample(
            cpuPercent = probe.cpuPercent,
            gpuPercent = probe.gpuPercent,
            ramUsedBytes = probe.ramUsedBytes,
            ramTotalBytes = probe.ramTotalBytes,
            storagePercent = probe.storagePercent,
            downBytesPerSec = down,
            upBytesPerSec = up
        )
    }

    /**
     * Cumulative interface counters to a per-second rate for **both** directions
     * from one delta: [lastNet] may only advance once per sample, or the second
     * direction would subtract a counter from itself and read as a permanent
     * zero. The first sample has nothing to subtract from, and a counter that
     * went backwards (an interface re-appearing, a sleep/wake cycle) is not a
     * negative rate, so both yield null and the previous rate stands.
     */
    private fun netRates(net: Pair<Long, Long>?): Pair<Long?, Long?> {
        val counters = net ?: return null to null
        val now = System.nanoTime()
        val previous = lastNet
        lastNet = NetCounters(counters.first, counters.second, now)
        val elapsedSeconds = if (previous != null) (now - previous.nanos) / 1_000_000_000.0 else 0.0
        return if (previous == null || elapsedSeconds < 0.2) {
            null to null
        } else {
            val down = counters.first - previous.inBytes
            val up = counters.second - previous.outBytes
            val downRate = if (down < 0L) null else (down / elapsedSeconds).toLong()
            val upRate = if (up < 0L) null else (up / elapsedSeconds).toLong()
            downRate to upRate
        }
    }

    /** Merges a fresh sample over the previous snapshot: "failed probe keeps last value". */
    private fun emit(sample: SystemSample) {
        // A probe that was already running when the widget was switched off must
        // not resurrect the readout the toggle just cleared: the settings row
        // would then show a frozen sample that never ticks again.
        if (!enabled) return
        val previous = _state.value
        val merged = SystemSnapshot(
            cpuPercent = sample.cpuPercent ?: previous?.cpuPercent,
            gpuName = gpuName ?: previous?.gpuName,
            gpuPercent = sample.gpuPercent ?: previous?.gpuPercent,
            ramUsedBytes = sample.ramUsedBytes ?: previous?.ramUsedBytes,
            ramTotalBytes = sample.ramTotalBytes ?: previous?.ramTotalBytes,
            storagePercent = sample.storagePercent ?: previous?.storagePercent,
            downBytesPerSec = sample.downBytesPerSec ?: previous?.downBytesPerSec ?: 0L,
            upBytesPerSec = sample.upBytesPerSec ?: previous?.upBytesPerSec ?: 0L,
            pingMs = sample.pingMs ?: previous?.pingMs
        )
        // Nothing at all means the probe answered with an empty payload (every
        // counter failed): publishing it would put a page of dashes on the wheel.
        if (merged.hasAnyReading) _state.value = merged
    }

    private fun staticGpuName(): String? =
        if (HostPlatform.isWindows) WindowsSystemProbe.readStatic(windowsScript)
        else MacSystemProbe.gpuName()

    /**
     * Runs a one-shot helper and returns its trimmed stdout, or null when it
     * times out / cannot start. Every spawn in this file goes through here so
     * a wedged helper can never pin a poller thread: `destroyForcibly` on the
     * timeout, and stderr merged so one stream drains.
     */
    internal fun runProcess(command: List<String>, timeoutMs: Long = ProbeTimeoutMs): String? =
        runCatching {
            val process = ProcessBuilder(command).redirectErrorStream(true).start()
            if (!process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly()
                process.waitFor(2, TimeUnit.SECONDS)
                return@runCatching null
            }
            process.inputStream.bufferedReader().readText()
        }.getOrNull()
}

/** Public resolver used for the latency probe (Cloudflare, TCP 443). */
private const val PingHost = "1.1.1.1"
private const val PingPort = 443
private const val PingTimeoutMs = 1_000

/**
 * One TCP connect to a public resolver and back, in whole milliseconds.
 * Chosen over ICMP because a raw socket needs root/Administrator while a TCP
 * handshake is an ordinary outbound connection — and because a game's own
 * traffic is TCP/UDP to a server, so a completed handshake measures the thing
 * the player actually cares about (route + queueing) rather than a host that
 * may deprioritise echo requests.
 *
 * File-level rather than a member of [SystemMetrics]: the poller already sits
 * on detekt's function-count ceiling, and this shares nothing with it but the
 * constants above.
 *
 * Fail-soft: no route, a firewall, a captive portal or a 1 s timeout all return
 * null and the previous reading stands.
 */
private fun probePingMs(): Int? = runCatching {
    val started = System.nanoTime()
    Socket().use { socket ->
        socket.connect(InetSocketAddress(PingHost, PingPort), PingTimeoutMs)
    }
    ((System.nanoTime() - started) / 1_000_000L).toInt().coerceAtLeast(0)
}.getOrNull()
