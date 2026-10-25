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
 * Watches for a fullscreen game and publishes it as one [StateFlow], which is
 * the entire trigger for the gaming quickview: while this is non-null and the
 * island is collapsed, the performance HUD replaces the pill.
 *
 * Two very different probes behind one contract:
 *
 *  - **macOS** asks `MacGameWatch` (CoreGraphics, in-process) every 1.5 s. A
 *    layer-0 non-shell window covering the main display is the signal — the
 *    same thing the user sees when a game takes the screen.
 *  - **Windows** keeps one long-lived `game-detect.ps1` running and reads the
 *    line it prints per tick. Spawning the script per probe was the obvious
 *    shape and the wrong one: its `Add-Type` P/Invoke surface costs ~1 s to
 *    compile in PowerShell 5.1, so a per-tick spawn would burn a fifth of a
 *    core for the app's whole life. One process, one compile, one cheap loop —
 *    and the script exits by itself once this JVM is gone (see its header).
 *
 * A game that drops out for a single probe (a loading screen recreating its
 * window, an alt-tab) does not clear the session: [MissingProbesBeforeIdle]
 * consecutive misses are required, which is what keeps the HUD from blinking
 * mid-match. The detector costs nothing while disabled — the Behavior toggle
 * owns [setEnabled], and a switched-off detector runs no probe and reports no
 * game.
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
     * Starts the Windows watcher once and translates its protocol into
     * sessions. `1|name|pid` is a game; `0||` is a miss, held for
     * [MissingProbesBeforeIdle] lines exactly like the macOS probe. A watcher
     * that cannot start (script missing, PowerShell blocked by policy) leaves
     * the detector silent rather than retrying in a loop.
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
