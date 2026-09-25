package com.starkdroid.quicknotch.datasource

import androidx.compose.ui.graphics.Color
import com.starkdroid.quicknotch.media.NativeResources
import java.util.concurrent.TimeUnit

/**
 * Reads the OS accent colour so the notch glass can tint itself to match the
 * desktop. macOS exposes the accent index via `defaults read -g AppleAccentColor`;
 * Windows keeps the DWM accent as an ABGR DWORD in the registry, read through
 * the packaged `accent.ps1` bridge (mirroring the other PowerShell probes).
 *
 * A failed probe yields null rather than an error — the caller then falls back
 * to the accent-free translucent glass, so the notch never breaks because the
 * desktop colour was unreadable.
 */
object SystemAccent {

    private val lock = Any()
    @Volatile private var cached: Color? = null

    /** Cached accent colour, or null when the OS didn't report one. Never throws. */
    fun current(): Color? {
        cached?.let { return it }
        synchronized(lock) {
            cached?.let { return it }
            val color = if (HostPlatform.isMac) readFromMacOS() else readFromWindows()
            cached = color
            return color
        }
    }

    /** Drop the cache so the next [current] re-reads the OS (Settings previews). */
    fun invalidate() {
        cached = null
    }

    private fun readFromMacOS(): Color? {
        return runCatching {
            val process = ProcessBuilder("/usr/bin/defaults", "read", "-g", "AppleAccentColor")
                .redirectErrorStream(false).start()
            val output = process.inputStream.bufferedReader().readText().trim()
            process.waitFor(3, TimeUnit.SECONDS)
            process.destroy()
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
            val output = process.inputStream.bufferedReader().readText().trim()
            process.waitFor(3, TimeUnit.SECONDS)
            process.destroy()
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