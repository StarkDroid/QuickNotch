package com.starkdroid.quicknotch.media

import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
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
 * cache in [MediaControllerBase] covers the omission.
 */
internal class WindowsSmtcController : MediaControllerBase() {

    override val supportsSeek = true

    @Volatile
    private var script: File? = null

    /** Content id (and whether it already has art) the last poll reported. */
    private var statusId = ""
    private var statusHaveArt = false

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
                    statusId = current.contentId
                    statusHaveArt = current.artwork != null
                } else {
                    statusId = ""
                    statusHaveArt = false
                }
                kotlinx.coroutines.delay(1_000)
            }
        }
    }

    /**
     * What the next status poll may skip: the thumbnail when the session
     * already shows art for this exact track. Passed as separate argv elements
     * (never one joined string), so ids containing spaces arrive intact.
     */
    private fun statusArguments(): List<String> =
        listOf("status", statusId, if (statusHaveArt) "1" else "0")

    override fun playPause() = invokeUnit(listOf("toggle"))
    override fun next() = invokeUnit(listOf("next"))
    override fun previous() = invokeUnit(listOf("previous"))
    override fun seekTo(positionMs: Long) = invokeUnit(listOf("seek $positionMs"))

    private fun invokeUnit(arguments: List<String>) {
        ioScope.launch { runCatching { invoke(arguments) } }
    }

    private fun invoke(arguments: List<String>): String? {
        val file = script ?: return null
        val command = buildList {
            add("powershell.exe")
            add("-NoProfile"); add("-NonInteractive"); add("-ExecutionPolicy"); add("Bypass")
            add("-File"); add(file.absolutePath)
            addAll(arguments)
        }
        val process = ProcessBuilder(command).redirectErrorStream(false).start()
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor(5, TimeUnit.SECONDS)
        process.destroy()
        return output.lineSequence().firstOrNull { it.startsWith("{") }
    }
}
