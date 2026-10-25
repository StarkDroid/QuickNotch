package com.starkdroid.quicknotch.datasource

import com.sun.jna.Function
import com.sun.jna.Memory
import com.sun.jna.NativeLibrary
import com.starkdroid.quicknotch.media.NativeResources
import java.util.concurrent.ConcurrentHashMap

/**
 * Thin JVM front for `gamewatch_bridge.m` (compiled to `gamewatch-bridge.dylib`
 * on macOS hosts), which answers one question from CoreGraphics:
 *
 *   "Is a non-shell application covering the main display right now — and which?"
 *
 * That is the only fullscreen signal macOS exposes without Screen Recording or
 * Accessibility permission. Window *titles* are gated on permission on modern
 * macOS, but owner names and bounds are not, so the bridge reads those and
 * nothing else: a layer-0 window within 2pt of the display bounds, owned by a
 * process that is not ours and not a system shell (Dock, menu bar, Window
 * Server, Finder, …).
 *
 * In-process and allocation-light: polling costs a few microseconds and spawns
 * nothing, so the detector can run every 1.5 s for the app's whole lifetime.
 * The dylib only exists when the project was built on a macOS host; elsewhere
 * every call answers "no game" (fail soft, never throws).
 */
internal object MacGameWatch {

    private const val OwnerBufferBytes = 256

    private val bridge: NativeLibrary? by lazy {
        runCatching {
            NativeResources.extract("macos/gamewatch-bridge.dylib", ".dylib")
                ?.let { NativeLibrary.getInstance(it.absolutePath) }
        }.getOrNull()
    }

    private val functions = ConcurrentHashMap<String, Function?>()

    private fun fn(name: String): Function? = functions.computeIfAbsent(name) {
        bridge?.let { lib -> runCatching { lib.getFunction(it) }.getOrNull() }
    }

    // One reused buffer: the poller is single-threaded (one coroutine on IO) and
    // the bridge only writes into it synchronously, so there is nothing to race
    // with. Keeping it here means a steady-state poll allocates nothing.
    private val ownerBuffer: Memory by lazy { Memory(OwnerBufferBytes.toLong()) }

    /**
     * The owning app's name while a fullscreen window is on screen, null when
     * the desktop is showing normally. Never throws: a missing bridge, a
     * refused window-server connection or a malformed answer all read as "no
     * game", which simply leaves the notch in its ordinary quickview.
     */
    fun fullscreenOwner(): String? = try {
        val found = fn("qn_fullscreen_game")?.invokeInt(arrayOf(ownerBuffer, OwnerBufferBytes)) ?: 0
        if (found == 1) {
            ownerBuffer.getString(0).takeIf { it.isNotBlank() }
        } else {
            null
        }
    } catch (_: Throwable) {
        null
    }
}
