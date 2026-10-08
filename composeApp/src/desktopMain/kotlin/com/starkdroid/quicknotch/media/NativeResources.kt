package com.starkdroid.quicknotch.media

import com.starkdroid.quicknotch.datasource.AppLog
import com.starkdroid.quicknotch.datasource.HostPlatform
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Extracts a packaged native helper (compiled macOS bridge, Windows PowerShell
 * script) from the jar resources to a temp file so it can be executed. The
 * macOS binary only exists when the project was built on a macOS host; a
 * missing resource is a normal outcome on other hosts and yields no session.
 */
internal object NativeResources {

    private val extractedCache = ConcurrentHashMap<String, File?>()

    fun extract(resourcePath: String, suffix: String): File? =
        extractedCache.computeIfAbsent(resourcePath) {
            try {
                val stream = NativeResources::class.java.classLoader
                    ?.getResourceAsStream(resourcePath) ?: return@computeIfAbsent null
                val file = File.createTempFile("quicknotch-", suffix).apply { deleteOnExit() }
                stream.use { input -> file.outputStream().use(input::copyTo) }
                file.setExecutable(true)
                file
            } catch (_: Throwable) {
                null
            }
        }

    /**
     * Kills bridge processes orphaned by previous runs (their ppid is 1). The
     * JVM can die without ever disposing the composition — SIGTERM and kill -9
     * alike skip `media.stop()` — and a leaked nowplaying bridge then keeps
     * polling MediaRemote forever (one osascript spawn every 2s per instance;
     * a dev machine accumulated ~90 over one uptime, ~19 live JXA polls at
     * ~10% CPU each, and the machine-wide stall that caused is what surfaced
     * as "the notch performance tanks"). The bridges now watch getppid()
     * themselves (nowplaying_bridge.m / btwatch_bridge.m), but this
     * retro-sweeps the leaks of already-shipped versions on startup.
     *
     * Only ppid == 1 is ever targeted, so this process's own children (fresh
     * bridges, ppid == our pid) and every other app's processes are never
     * touched; the `/quicknotch-` path fragment matches the extracted temp
     * binaries (`quicknotch-<random>`) and the JXA helper script
     * (`quicknotch-bridge/nowplaying.jxa`) alike. Any in-flight osascript
     * under a swept bridge is a one-shot that finishes on its own.
     */
    fun sweepOrphanedBridges() {
        if (!HostPlatform.isMac) return
        try {
            val victims = orphanedBridgePids()
            if (victims.isNotEmpty()) {
                killProcesses(victims)
                AppLog.line("QuickNotch: swept ${victims.size} orphaned bridge process(es)")
            }
        } catch (_: Throwable) {
            // Fail-soft: a sweep that cannot run just leaves old orphans for
            // the next launch; never block app startup on it.
        }
    }

    /** ppid == 1 processes whose command carries the `/quicknotch-` path fragment. */
    private fun orphanedBridgePids(): List<Int> {
        val ps = ProcessBuilder("ps", "-eo", "pid=,ppid=,command=")
            .redirectErrorStream(true)
            .start()
        val pids = ps.inputStream.bufferedReader().useLines { lines ->
            lines.mapNotNull { line ->
                val parts = line.trim().split(Regex("\\s+"), limit = 3)
                if (parts.size < 3 || parts[1] != "1") return@mapNotNull null
                if (!parts[2].contains("/quicknotch-")) return@mapNotNull null
                parts[0].toIntOrNull()
            }.toList()
        }
        val exited = ps.waitFor(3, TimeUnit.SECONDS)
        if (!exited) ps.destroyForcibly()
        return if (exited) pids else emptyList()
    }

    private fun killProcesses(pids: List<Int>) {
        val kill = ProcessBuilder(listOf("kill", "-TERM") + pids.map(Int::toString))
            .redirectErrorStream(true)
            .start()
        kill.inputStream.bufferedReader().readText()
        if (!kill.waitFor(3, TimeUnit.SECONDS)) kill.destroyForcibly()
    }
}
