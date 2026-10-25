package com.starkdroid.quicknotch.system

import com.starkdroid.quicknotch.datasource.AppLog
import com.starkdroid.quicknotch.datasource.HostPlatform
import com.starkdroid.quicknotch.media.NativeResources
import java.io.File
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

/**
 * Frame rate for the gaming HUD, from Intel's PresentMon on Windows.
 *
 * There is no cross-platform API for another process's frame rate: the OS
 * compositor does not publish one, and both major platforms refuse to hand out
 * another app's GPU timings. PresentMon solves it the only way that works — it
 * opens an ETW session on the graphics driver's present events — and ships as a
 * single self-contained executable, so it can live beside the app and be run
 * for the few seconds a sample needs.
 *
 * macOS has no equivalent through a supported API, so this probe is a permanent
 * no-op there: the HUD simply has one fewer cell. Same on a Windows install
 * whose binary is missing — the HUD drops the FPS cell rather than showing a
 * fabricated number.
 *
 * ## Why a 2 s capture every 6 s
 *
 * PresentMon reports one row per presented frame; a rate needs a window of
 * frames to average. Two seconds smooths shader-compile hitches while still
 * tracking the scene, and sleeping between captures means the ETW overhead only
 * exists while a sample is being taken.
 *
 * ## Unverified surface
 *
 * The binary is not bundled in this repository (it is a third-party release
 * artifact — see `native/windows/README-presentmon.md`). PresentMon 2.x renamed
 * every long option from `-process_id` to `--process_id` and rejects the other
 * spelling, so the probe tries one style, and only if that yields no CSV
 * header does it retry with the other and remember which worked. The parser is
 * header-driven and case-insensitive, so either version's columns read. Every
 * failure path — missing binary, refused ETW session, unexpected output —
 * yields "no FPS", never a wrong number.
 */
object FpsProbe {

    private const val CaptureSeconds = 2
    private const val CaptureTimeoutMs = 6_000L
    private const val PauseBetweenCapturesMs = 4_000L

    private val _fps = MutableStateFlow<Int?>(null)
    val fps: StateFlow<Int?> = _fps.asStateFlow()

    /** Which long-option spelling this PresentMon build accepts. */
    private enum class ArgStyle(val prefix: String) { DOUBLE("--"), SINGLE("-") }

    @Volatile
    private var style: ArgStyle? = null

    @Volatile
    private var executable: File? = null

    @Volatile
    private var resolved = false

    @Volatile
    private var missingLogged = false

    /** Resolves the packaged binary once; a missing file is a normal install. */
    private fun executable(): File? {
        if (!resolved) {
            executable = if (HostPlatform.isWindows) {
                NativeResources.extract("windows/PresentMon.exe", ".exe").also { found ->
                    if (found == null && !missingLogged) {
                        missingLogged = true
                        AppLog.line("PresentMon.exe not bundled; gaming HUD runs without FPS")
                    }
                }
            } else {
                null
            }
            resolved = true
        }
        return executable
    }

    /**
     * Samples the session's frame rate until the caller's coroutine is
     * cancelled (a new game, or no game at all). The reading is cleared on the
     * way out so a game exit never leaves a stale number in the HUD.
     */
    suspend fun follow(session: GameSession?) {
        try {
            val pid = session?.pid
            val exe = if (pid != null) executable() else null
            if (pid == null || exe == null) return
            while (currentCoroutineContext().isActive) {
                capture(exe, pid)?.let { _fps.value = it }
                delay(PauseBetweenCapturesMs)
            }
        } finally {
            _fps.value = null
        }
    }

    private fun capture(exe: File, pid: Int): Int? {
        val preferred = style ?: ArgStyle.DOUBLE
        readWith(exe, pid, preferred)?.let { return it }
        // A build that rejected the option prints an error instead of a CSV;
        // fall back to the other spelling once and remember the winner.
        val alternate = if (preferred == ArgStyle.DOUBLE) ArgStyle.SINGLE else ArgStyle.DOUBLE
        return readWith(exe, pid, alternate)?.also { style = alternate }
            ?: run {
                style = preferred
                null
            }
    }

    private fun readWith(exe: File, pid: Int, style: ArgStyle): Int? {
        val p = style.prefix
        val output = SystemMetrics.runProcess(
            listOf(
                exe.absolutePath,
                "${p}process_id", pid.toString(),
                "${p}output_stdout",
                "${p}stop_existing_session",
                "${p}timed", CaptureSeconds.toString(),
                "${p}terminate_after_timed"
            ),
            timeoutMs = CaptureTimeoutMs
        ) ?: return null
        return averageFps(output).also { if (it != null) this.style = style }
    }

    /**
     * PresentMon prints a CSV whose first line is the header. The frame-time
     * column has been `msBetweenPresents` since 1.x (2.x prints the same name),
     * so one case-insensitive lookup finds it; rows with a non-positive or
     * unparsable frame time (the first row of a session, a dropped frame) are
     * skipped rather than counted as zero.
     */
    internal fun averageFps(csv: String): Int? {
        val lines = csv.lineSequence().filter { it.isNotBlank() }.toList()
        val header = lines.firstOrNull { it.contains(',') }
        val frameColumn = header?.split(',')?.indexOfFirst {
            it.trim().equals("msBetweenPresents", ignoreCase = true)
        } ?: -1
        val frameTimes = if (frameColumn >= 0) {
            lines.dropWhile { it != header }.drop(1).mapNotNull { row ->
                row.split(',').getOrNull(frameColumn)?.trim()?.toDoubleOrNull()
            }.filter { it > 0.0 && it < 10_000.0 }
        } else {
            emptyList()
        }
        // Two rows minimum: one frame time is a point, not a rate.
        val mean = if (frameTimes.size >= 2) frameTimes.average() else null
        return mean?.takeIf { it > 0.0 }?.let { (1_000.0 / it).roundToInt().coerceIn(1, 1_000) }
    }
}
