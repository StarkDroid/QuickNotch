package com.starkdroid.quicknotch.datasource

import androidx.compose.ui.graphics.Color
import com.starkdroid.quicknotch.media.NativeResources
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Reads the OS accent colour (macOS `defaults` index, Windows DWM DWORD via
 * `accent.ps1`); a failed probe yields null. */
object SystemAccent {

    private val _accent = MutableStateFlow<Color?>(null)
    /** Live accent colour; [refresh] publishes new OS values as they are read. */
    val accent: StateFlow<Color?> = _accent.asStateFlow()

    /** Re-reads the OS accent; a failed probe keeps the previous value. */
    fun refresh() {
        val fresh = if (HostPlatform.isMac) readFromMacOS() else readFromWindows()
        if (fresh != null) _accent.value = fresh
    }

    private fun readFromMacOS(): Color? {
        return runCatching {
            val process = ProcessBuilder("/usr/bin/defaults", "read", "-g", "AppleAccentColor")
                .redirectErrorStream(false).start()
            if (!process.waitFor(3, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                process.waitFor(2, TimeUnit.SECONDS)
                return@runCatching null
            }
            val output = process.inputStream.bufferedReader().readText().trim()
            if (output.isBlank()) null else accentFromMacIndex(output.toInt())
        }.getOrNull()
    }

    private fun readFromWindows(): Color? {
        val ps1 = NativeResources.extract("windows/accent.ps1", ".ps1") ?: return null
        return runCatching {
            val process = ProcessBuilder(
                "powershell.exe", "-NoProfile", "-NonInteractive",
                "-ExecutionPolicy", "Bypass", "-File", ps1.absolutePath
            ).redirectErrorStream(false).start()
            if (!process.waitFor(3, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                process.waitFor(2, TimeUnit.SECONDS)
                return@runCatching null
            }
            val output = process.inputStream.bufferedReader().readText().trim()
            if (output == "ERROR") null else accentFromHexRgb(output)
        }.getOrNull()
    }

    /** Maps the macOS `AppleAccentColor` index to the matching system accent. */
    private fun accentFromMacIndex(index: Int): Color = when (index) {
        -1 -> Color(0xFF8E8E93)   // Graphite
        0 -> Color(0xFFFF453A)    // Red
        1 -> Color(0xFFFF9F0A)    // Orange
        2 -> Color(0xFFFFD60A)    // Yellow
        3 -> Color(0xFF30D158)    // Green
        5 -> Color(0xFFBF5AF2)    // Purple
        6 -> Color(0xFFFF375F)    // Pink
        else -> Color(0xFF0A84FF) // 4 = Blue (also the unknown default)
    }

    /** Parses an "RRGGBB" hex string into an opaque colour. */
    private fun accentFromHexRgb(hex: String): Color? = runCatching {
        val value = hex.trim().toLong(16)
        Color(0xFF000000.toInt() or ((value and 0xFFFFFF).toInt()))
    }.getOrNull()
}
