package com.starkdroid.quicknotch.system

import com.starkdroid.quicknotch.datasource.AppLog
import com.starkdroid.quicknotch.datasource.HostPlatform
import com.starkdroid.quicknotch.datasource.MacGameWatch
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

/** The fullscreen application the gaming quickview is currently serving. */
data class GameSession(
    /** Owner app name (macOS) or process name (Windows); shown to assistive tech. */
    val name: String,
    /** Foreground process id, used to attach the Windows FPS probe. */
    val pid: Int? = null
)

/**
 * One StateFlow is the gaming trigger. Windows keeps one long-lived
 * watcher; two misses clear a session.
 */
object GameDetector {

    /** macOS: cheap in-process probe. */
    private const val MacPollMs = 1_500L

    /** Probe misses tolerated before the session ends (~3 s). */
    private const val MissingProbesBeforeIdle = 2

    private const val WindowsScriptResource = "windows/game-detect.ps1"

    private val _game = MutableStateFlow<GameSession?>(null)
    val game: StateFlow<GameSession?> = _game.asStateFlow()

    private var scope: CoroutineScope? = null
    private var pollJob: Job? = null
    private var windowsProcess: Process? = null

    @Volatile
    private var enabled = false

    fun start() {
        stop()
        val active = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = active
        pollJob = if (HostPlatform.isWindows) {
            active.launch { watchWindows() }
        } else {
            active.launch { pollMac() }
        }
    }

    fun stop() {
        pollJob?.cancel()
        pollJob = null
        windowsProcess?.destroyForcibly()
        windowsProcess = null
        scope?.cancel()
        scope = null
        _game.value = null
    }

    /** The Behavior toggle: off means no probe at all and no HUD. */
    fun setEnabled(value: Boolean) {
        if (enabled == value) return
        enabled = value
        if (!value) _game.value = null
    }

    /** Logs the one-time outcome of extracting the Windows probe (diagnostics). */
    fun logAvailability() {
        if (HostPlatform.isWindows && NativeResources.extract(WindowsScriptResource, ".ps1") == null) {
            AppLog.line("game-detect.ps1 missing from resources; gaming HUD stays off")
        }
    }

    private suspend fun pollMac() {
        var misses = 0
        while (scope?.isActive == true) {
            if (enabled) {
                val owner = MacGameWatch.fullscreenOwner()
                misses = if (owner != null) {
                    publish(GameSession(name = owner))
                    0
                } else {
                    misses + 1
                }
                if (misses >= MissingProbesBeforeIdle) publish(null)
            }
            delay(MacPollMs)
        }
    }

    /**
     * Translates game-detect.ps1 lines into sessions; a watcher that cannot
     * start leaves the detector silent.
     */
    private suspend fun watchWindows() {
        val script = NativeResources.extract(WindowsScriptResource, ".ps1")
        if (script == null) {
            AppLog.line("game-detect.ps1 missing from resources; gaming HUD stays off")
            return
        }
        val process = runCatching {
            ProcessBuilder(
                "powershell.exe",
                "-NoProfile",
                "-NonInteractive",
                "-ExecutionPolicy", "Bypass",
                "-File", script.absolutePath,
                "-ParentPid", ProcessHandle.current().pid().toString()
            ).redirectErrorStream(true).start()
        }.onFailure { error ->
            AppLog.line("game-detect watcher failed to start: ${error.message}")
        }.getOrNull() ?: return
        windowsProcess = process
        try {
            runCatching { consumeWindowsLines(process.inputStream.bufferedReader()) }
                .onFailure { error ->
                    AppLog.line("game-detect watcher stopped: ${error.message}")
                }
        } finally {
            process.destroyForcibly()
            if (windowsProcess === process) windowsProcess = null
        }
    }

    /**
     * Reads one protocol line per watcher tick. Blocking on purpose: `stop()`
     * destroys the process, which closes this stream and unblocks the read.
     */
    private fun consumeWindowsLines(reader: java.io.BufferedReader) {
        var misses = 0
        while (scope?.isActive == true) {
            val line = reader.readLine() ?: return
            if (enabled) misses = applyWindowsLine(line, misses)
        }
    }

    /** One protocol line → the updated miss counter (see [consumeWindowsLines]). */
    private fun applyWindowsLine(line: String, misses: Int): Int {
        val session = parseLine(line)
        if (session != null) {
            publish(session)
            return 0
        }
        val next = misses + 1
        if (next >= MissingProbesBeforeIdle) publish(null)
        return next
    }

    /** `1|name|pid` → a session; anything else is a miss. */
    private fun parseLine(line: String): GameSession? = line
        .takeIf { it.contains('|') }
        ?.trim()
        ?.split('|')
        ?.let { parts ->
            val name = parts.getOrNull(1)?.trim().orEmpty()
            if (parts.firstOrNull()?.trim() == "1" && name.isNotEmpty()) {
                GameSession(name = name, pid = parts.getOrNull(2)?.trim()?.toIntOrNull())
            } else {
                null
            }
        }

    /** StateFlow dedupes, so an unchanged session costs no recomposition. */
    private fun publish(session: GameSession?) {
        if (_game.value?.name != session?.name || _game.value?.pid != session?.pid) {
            _game.value = session
        }
    }
}
