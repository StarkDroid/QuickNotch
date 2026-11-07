package com.starkdroid.quicknotch.datasource

import com.sun.jna.Function
import com.sun.jna.Memory
import com.sun.jna.NativeLibrary
import com.starkdroid.quicknotch.media.NativeResources
import java.util.concurrent.ConcurrentHashMap

/**
 * JVM front for `gamewatch-bridge.dylib`, which reads window owner names and bounds.
 * Window titles are permission-gated on modern macOS, owner names are not.
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

    // One reused buffer: the poller is single-threaded, so a steady-state poll allocates nothing.
    private val ownerBuffer: Memory by lazy { Memory(OwnerBufferBytes.toLong()) }

    /** Owning app's name while a fullscreen window covers the display; null otherwise, never throws. */
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
