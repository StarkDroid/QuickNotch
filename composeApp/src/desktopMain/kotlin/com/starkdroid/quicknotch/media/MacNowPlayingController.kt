package com.starkdroid.quicknotch.media

import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * macOS now-playing via the system MediaRemote service — the same "now
 * playing" surface the media keys use, so any registered source (Music,
 * Spotify, browsers, VLC, IINA, …) is covered, not just apps we script.
 *
 * Since macOS 15.4 the MediaRemote daemon only serves processes whose bundle
 * id starts with "com.apple.", so a plain helper binary is silently denied.
 * The compiled helper (`composeApp/native/macos/nowplaying_bridge.m`) is a
 * supervisor that runs the private-class API (`MRNowPlayingRequest`,
 * `MRNowPlayingController`) inside an `osascript -l JavaScript` subprocess —
 * which is entitled — and streams one canonical JSON snapshot per change on
 * stdout. Transport commands spawn a short-lived helper invocation the same
 * way.
 *
 * If the helper is absent (non-mac build host) the controller simply never
 * publishes a session.
 */
internal class MacNowPlayingController : MediaControllerBase() {

    override val supportsSeek = false

    @Volatile
    private var bridge: File? = null

    @Volatile
    private var watcher: Process? = null

    override fun start() {
        val binary = NativeResources.extract("macos/nowplaying-bridge", "")
        if (binary == null || !binary.canExecute()) return
        bridge = binary

        ioScope.launch {
            // Restart-on-crash loop: the helper is trivial but MediaRemote is
            // an OS service and sessions can vanish with it.
            while (isActive) {
                runCatching {
                    val process = ProcessBuilder(binary.absolutePath)
                        .redirectErrorStream(false)
                        .start()
                    watcher = process
                    process.inputStream.bufferedReader().use { reader ->
                        var line = reader.readLine()
                        while (line != null) {
                            if (line.startsWith("{")) publish(line)
                            line = reader.readLine()
                        }
                    }
                    process.waitFor()
                }
                watcher = null
                kotlinx.coroutines.delay(2_000)
            }
        }
    }

    override fun stop() {
        watcher?.destroy()
        super.stop()
    }

    override fun playPause() = send("toggleplaypause")
    override fun next() = send("nexttrack")
    override fun previous() = send("previoustrack")
    override fun seekTo(positionMs: Long) = Unit

    /**
     * Transport commands spawn a short-lived `osascript` helper (~100-300ms,
     * 2s deadline) — that MUST NOT run on the calling thread: clicks arrive on
     * the Compose UI thread, and blocking it made every press freeze the
     * overlay + settings windows for the spawn duration (very visible on the
     * SOFTWARE renderer, which paints both windows on that same thread). The
     * command is fire-and-forget on the controller's IO scope; the session
     * feed reflects the outcome on its own.
     */
    private fun send(command: String) {
        val binary = bridge ?: return
        ioScope.launch {
            runCatching {
                // The bridge reports the command's outcome as its exit status, and
                // the osascript it supervises needs a moment; a generous timeout
                // plus destroyForcibly keeps a wedged helper from lingering.
                val helper = ProcessBuilder(binary.absolutePath, "--cmd", command).start()
                if (!helper.waitFor(4, TimeUnit.SECONDS)) helper.destroyForcibly()
            }
        }
    }
}
