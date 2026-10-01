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
 * shape to the macOS bridge, so [MediaControllerBase] decodes both. Commands
 * run the same script with a verb. At 1 Hz the process cost is negligible and
 * never touches the UI thread.
 */
internal class WindowsSmtcController : MediaControllerBase() {

    override val supportsSeek = true

    @Volatile
    private var script: File? = null

    override fun start() {
        val ps1 = NativeResources.extract("windows/smtc.ps1", ".ps1")
        if (ps1 == null) return
        script = ps1

        ioScope.launch {
            while (isActive) {
                val json = runCatching { invoke(null) }.getOrNull()
                if (!json.isNullOrBlank()) publish(json)
                kotlinx.coroutines.delay(1_000)
            }
        }
    }

    override fun playPause() = invokeUnit("toggle")
    override fun next() = invokeUnit("next")
    override fun previous() = invokeUnit("previous")
    override fun seekTo(positionMs: Long) = invokeUnit("seek $positionMs")

    private fun invokeUnit(argument: String) {
        ioScope.launch { runCatching { invoke(argument) } }
    }

    private fun invoke(argument: String?): String? {
        val file = script ?: return null
        val command = buildList {
            add("powershell.exe")
            add("-NoProfile"); add("-NonInteractive"); add("-ExecutionPolicy"); add("Bypass")
            add("-File"); add(file.absolutePath)
            if (argument != null) add(argument)
        }
        val process = ProcessBuilder(command).redirectErrorStream(false).start()
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor(5, TimeUnit.SECONDS)
        process.destroy()
        return output.lineSequence().firstOrNull { it.startsWith("{") }
    }
}
