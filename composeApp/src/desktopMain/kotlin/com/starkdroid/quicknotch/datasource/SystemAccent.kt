package com.starkdroid.quicknotch.datasource

import androidx.compose.ui.graphics.Color
import com.starkdroid.quicknotch.media.NativeResources
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Reads the OS accent colour so the notch glass can tint itself to match the
 * desktop — and keeps that tint live. macOS exposes the accent index via
 * `defaults read -g AppleAccentColor`; Windows keeps the DWM accent as an ABGR
 * DWORD in the registry, read through the packaged `accent.ps1` bridge
 * (mirroring the other PowerShell probes).
 *
 * A failed probe yields null rather than an error — the caller then falls back
 * to the accent-free translucent glass, so the notch never breaks because the
 * desktop colour was unreadable. The value is published through [accent] as a
 * [StateFlow][kotlinx.coroutines.flow.StateFlow]; a poller in the overlay
 * re-reads the OS while the System Accent surface is chosen, so changing the
 * macOS/Windows accent recolours the notch in place. Reads are deduped by the
 * StateFlow, so composables only recompose when the colour actually changes.
 */
object SystemAccent {

    private val _accent = MutableStateFlow<Color?>(null)
    /** Live accent colour; [refresh] publishes new OS values as they are read. */
    val accent: StateFlow<Color?> = _accent.asStateFlow()

    /** Latest known accent colour, or null when the OS didn't report one. Never throws. */
    fun current(): Color? {
        _accent.value?.let { return it }
        refresh()
        return _accent.value
    }

    /**
     * Re-reads the OS accent and publishes it. A failed probe keeps the
     * previous value (the indicator only ever shows real data), matching the
     * pass-through-failure behaviour of the other bridges.
     */
    fun refresh() {
        val fresh = if (HostPlatform.isMac) readFromMacOS() else readFromWindows()
        if (fresh != null) _accent.value = fresh
    }

    /** Drop the read state so the next [current]/[refresh] re-reads the OS. */
    fun invalidate() {
        _accent.value = null
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
