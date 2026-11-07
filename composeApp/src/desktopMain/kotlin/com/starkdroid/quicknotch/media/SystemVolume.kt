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
 * Master volume and mute read from the OS, since neither media feed
 * exposes them. One process answers both; a failed read hides the chip.
 */
internal object SystemVolume {

    private const val PollIntervalMs = 2_000L
    private const val ProcTimeoutMs = 3_000L

    private val _percent = MutableStateFlow<Int?>(null)
    val percent: StateFlow<Int?> = _percent.asStateFlow()

    private val _muted = MutableStateFlow<Boolean?>(null)
    val muted: StateFlow<Boolean?> = _muted.asStateFlow()

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
                refresh()
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

    /**
     * Forces an immediate re-read (used after [toggleMute] so the ring shows
     * the new state without waiting for the next poll tick).
     */
    fun poke() {
        scope?.launch { refresh() }
    }

    /**
     * Flips the device mute flag (fire-and-forget on IO, then pokes a re-read).
     * No-op where volume itself is unreadable.
     */
    fun toggleMute() {
        val scriptPath = script?.absolutePath ?: if (HostPlatform.isMac) "" else return
        val target = _muted.value?.not() ?: return
        scope?.launch {
            runCatching {
                when {
                    HostPlatform.isWindows -> runProcess(
                        listOf(
                            "powershell.exe",
                            "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                            "-File", scriptPath,
                            "-Action", if (target) "mute" else "unmute"
                        )
                    )

                    else -> runProcess(
                        listOf(
                            "osascript", "-e",
                            "set volume output muted ${if (target) "true" else "false"}"
                        )
                    )
                }
            }
            poke()
        }
    }

    private fun refresh() {
        val state = runCatching { query() }.getOrNull()
        _percent.value = state?.first?.coerceIn(0, 100)
        _muted.value = state?.second
    }

    /** `"<percent>,<muted>"` from one process spawn, or null when unreadable. */
    private fun query(): Pair<Int, Boolean>? = when {
        HostPlatform.isWindows -> queryWindows()
        HostPlatform.isMac -> queryMac()
        else -> null
    }

    private fun queryWindows(): Pair<Int, Boolean>? {
        val file = script ?: return null
        return parseState(
            runProcess(
                listOf(
                    "powershell.exe",
                    "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                    "-File", file.absolutePath
                )
            )
        )
    }

    private fun queryMac(): Pair<Int, Boolean>? = parseState(
        runProcess(
            listOf(
                "osascript", "-e",
                "(output volume of (get volume settings) as string) & \",\" & (output muted of (get volume settings) as string)"
            )
        )
    )

    private fun parseState(output: String?): Pair<Int, Boolean>? {
        val parts = output?.trim()?.split(",") ?: return null
        if (parts.size != 2) return null
        val percent = parts[0].trim().toIntOrNull() ?: return null
        val muted = when (parts[1].trim().lowercase()) {
            "true", "1", "yes" -> true
            "false", "0", "no" -> false
            else -> return null
        }
        return percent to muted
    }

    private fun runProcess(command: List<String>): String? {
        return runCatching {
            val process = ProcessBuilder(command).redirectErrorStream(true).start()
            if (!process.waitFor(ProcTimeoutMs, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly()
                process.waitFor(2, TimeUnit.SECONDS)
                return@runCatching null
            }
            process.inputStream.bufferedReader().readText().trim()
        }.getOrNull()
    }
}
