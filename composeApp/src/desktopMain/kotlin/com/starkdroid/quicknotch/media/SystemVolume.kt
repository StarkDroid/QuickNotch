package com.starkdroid.quicknotch.media

import com.starkdroid.quicknotch.datasource.HostPlatform
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
import java.util.concurrent.TimeUnit

/**
 * System master-volume percentage (0-100), polled at a low cadence for the
 * collapsed music pill.
 *
 * Neither backend's "now playing" feed exposes volume — SMTC and MediaRemote
 * carry none — so this is read straight from the OS, independent of the media
 * session flow. Windows uses a tiny PowerShell helper over the CoreAudio COM
 * API (the same master volume the flyout shows); macOS uses AppleScript's
 * `output volume`. A failed or misparsed query simply leaves the value null
 * and the pill hides the chip.
 */
internal object SystemVolume {

    private const val PollIntervalMs = 2_000L
    private const val ProcTimeoutMs = 3_000L

    private val _percent = MutableStateFlow<Int?>(null)
    val percent: StateFlow<Int?> = _percent.asStateFlow()

    @Volatile
    private var script: File? = null
    private var scope: CoroutineScope? = null
    private var job: Job? = null

    fun start() {
        if (HostPlatform.isWindows) {
            script = NativeResources.extract("windows/volume.ps1", ".ps1")
        }
        stop()
        val active = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = active
        job = active.launch {
            while (isActive) {
                _percent.value = runCatching {
                    query()?.coerceIn(0, 100)
                }.getOrNull()
                delay(PollIntervalMs)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        scope?.cancel()
        scope = null
    }

    private fun query(): Int? = when {
        HostPlatform.isWindows -> queryWindows()
        HostPlatform.isMac -> queryMac()
        else -> null
    }

    private fun queryWindows(): Int? {
        val file = script ?: return null
        return runProcess(
            listOf(
                "powershell.exe",
                "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                "-File", file.absolutePath
            )
        )?.toIntOrNull()
    }

    private fun queryMac(): Int? =
        runProcess(listOf("osascript", "-e", "output volume of (get volume settings)"))
            ?.toIntOrNull()

    private fun runProcess(command: List<String>): String? {
        return runCatching {
            val process = ProcessBuilder(command).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText().trim()
            process.waitFor(ProcTimeoutMs, TimeUnit.MILLISECONDS)
            process.destroy()
            output
        }.getOrNull()
    }
}