package com.starkdroid.quicknotch.media

import com.starkdroid.quicknotch.datasource.AppLog
import com.starkdroid.quicknotch.datasource.HostPlatform
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Extracts a packaged helper from the jar to a temp file. A missing macOS
 * resource is normal on other hosts.
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
     * Kills bridge processes with ppid 1 left by earlier runs; a leaked
     * nowplaying bridge polls MediaRemote forever. Only /quicknotch- paths
     * are touched.
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
