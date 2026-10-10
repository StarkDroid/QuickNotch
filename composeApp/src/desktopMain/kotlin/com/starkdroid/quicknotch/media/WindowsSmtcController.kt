package com.starkdroid.quicknotch.media

import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Windows now-playing via SMTC (System Media Transport Controls) — the OS
 * media session every well-behaved player registers with (Start-menu media
 * keys, volume flyout, notifications). Reaching WinRT from the JVM would mean
 * COM vtable plumbing, so we bridge through PowerShell's supported WinRT type
 * activation; the whole script is a plain text resource, no compilation step.
 * It runs under Windows PowerShell 5.1 (the engine we launch, whose WinRT
 * projection is the one that exposes System Media Transport Controls);
 * PowerShell 7 dropped that projection, so `pwsh` is not used.
 *
 * One-shot poll per second: the script prints a single JSON line, identical in
 * shape to the macOS nowplaying-bridge, so [MediaControllerBase] decodes both. Commands
 * run the same script with a verb. At 1 Hz the process cost is negligible and
 * never touches the UI thread.
 *
 * The status poll tells the script which track the app already holds art for,
 * so the thumbnail transfer happens once per track instead of once per second
 * (see `smtc.ps1`): steady-state polls carry metadata only, and the per-id art
 * cache in [MediaControllerBase] covers the omission. The gate is three-valued —
 * '1' art held, '2' a fetch already came back empty, '0' fetch wanted — so a
 * source that publishes no art (stub thumbnails, metadata-only players) is
 * asked exactly once instead of every second.
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
     * What the next status poll may skip: the thumbnail when the session
     * already shows art for this exact track ('1'), or when a fetch for it
     * already came back empty ('2'). Passed as separate argv elements (never
     * one joined string), so ids containing spaces arrive intact.
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
     * One script spawn, bounded on every side. The stdout drain runs on
     * [bridgeReader] so a wedge (WinRT stalls do happen) surfaces as a timeout
     * here instead of a coroutine parked forever on a pipe read: after
     * [BRIDGE_TIMEOUT_MS] the give-up path kills the process, which closes its
     * pipe and frees the reader thread for the next poll. Draining *while*
     * waiting is not optional — an art transfer pushes a few hundred KB of
     * base64 through the pipe, well past the ~64 KB kernel buffer, so a
     * "wait, then read" order would deadlock every illustrated track.
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
         * Single daemon thread shared by every spawn: it only ever drains a
         * pipe to EOF, and the [WindowsSmtcController] poll loop plus transport
         * commands are serialized behind it (a poll is at most one second
         * apart, and a killed wedge frees the thread immediately).
         */
        private val reader = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "quicknotch-smtc-io").apply { isDaemon = true }
        }
    }
}
