package com.starkdroid.quicknotch.media

import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Windows now playing from SMTC through Windows PowerShell 5.1. The
 * status poll passes the held art id so a track's thumbnail is fetched
 * once, and a three-valued gate stops artless sources being asked again.
 */
internal class WindowsSmtcController : MediaControllerBase() {

    override val supportsSeek = true

    @Volatile
    private var script: File? = null

    /** Content id the last poll reported, and the art gate for the next one
     *  ('0' fetch wanted / '1' held / '2' tried, none available). */
    private var statusId = ""
    private var statusHaveArt = "0"

    override fun start() {
        val ps1 = NativeResources.extract("windows/smtc.ps1", ".ps1")
        if (ps1 == null) return
        script = ps1

        ioScope.launch {
            while (isActive) {
                val json = runCatching { invoke(statusArguments()) }.getOrNull()
                if (!json.isNullOrBlank()) publish(json)
                val current = session.value
                if (current != null) {
                    // Art landed → skip transfers. Still missing on a *repeat*
                    // poll of the same id → the fetch just ran and came back
                    // empty, so escalate to '2' and stop asking; a fresh id
                    // starts over at '0' (one retry covers sources whose
                    // thumbnail lands a beat behind their metadata).
                    statusHaveArt = when {
                        current.artwork != null -> "1"
                        statusId == current.contentId -> "2"
                        else -> "0"
                    }
                    statusId = current.contentId
                } else {
                    statusId = ""
                    statusHaveArt = "0"
                }
                kotlinx.coroutines.delay(1_000)
            }
        }
    }

    /**
     * The next poll's art gate: 1 held, 2 fetch came back empty, 0 wanted.
     * Passed as separate argv.
     */
    private fun statusArguments(): List<String> =
        listOf("status", statusId, statusHaveArt)

    override fun playPause() = invokeUnit(listOf("toggle"))
    override fun next() = invokeUnit(listOf("next"))
    override fun previous() = invokeUnit(listOf("previous"))
    override fun seekTo(positionMs: Long) = invokeUnit(listOf("seek $positionMs"))

    private fun invokeUnit(arguments: List<String>) {
        ioScope.launch { runCatching { invoke(arguments) } }
    }

    /**
     * One script spawn with a deadline and a concurrent stdout drain; art
     * transfers exceed the pipe buffer, so wait-then-read would deadlock.
     */
    private fun invoke(arguments: List<String>): String? {
        val file = script ?: return null
        val command = buildList {
            add("powershell.exe")
            add("-NoProfile"); add("-NonInteractive"); add("-ExecutionPolicy"); add("Bypass")
            add("-File"); add(file.absolutePath)
            addAll(arguments)
        }
        // stderr merges into stdout because the reader drains exactly one
        // stream: a child blocked on a full stderr pipe would otherwise hang
        // until the timeout on every poll that emits a warning.
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val read = reader.submit(Callable {
            process.inputStream.bufferedReader().use { it.readText() }
        })
        val output = runCatching { read.get(BRIDGE_TIMEOUT_MS, TimeUnit.MILLISECONDS) }.getOrNull()
        // A null output is the give-up path: the killed child closes its own
        // stdout pipe, which in turn frees the shared reader thread for the
        // next spawn (a `waitFor` here would park on the very read that
        // already outlived its deadline).
        if (output == null) {
            process.destroyForcibly()
        }
        runCatching { process.waitFor(1, TimeUnit.SECONDS) }
        return output?.lineSequence()?.firstOrNull { it.startsWith("{") }
    }

    companion object {
        /** Deadline for one script run — spawn + WinRT query + art transfer. */
        private const val BRIDGE_TIMEOUT_MS = 4_000L

        /**
         * One daemon thread drains every spawn; a killed wedge frees it for the
         * next poll.
         */
        private val reader = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "quicknotch-smtc-io").apply { isDaemon = true }
        }
    }
}
