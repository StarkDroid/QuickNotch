package com.starkdroid.quicknotch.media

import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * macOS now playing from MediaRemote through an osascript helper, the
 * only context the daemon entitles since 15.4. Cover art comes from
 * MacArtworkFetcher; without the helpers no session is published.
 */
internal class MacNowPlayingController : MediaControllerBase() {

    override val supportsSeek = false

    @Volatile
    private var bridge: File? = null

    @Volatile
    private var watcher: Process? = null

    private val artworkFetcher = MacArtworkFetcher(
        ioScope = ioScope,
        currentId = { session.value?.contentId },
        onArtwork = ::publishArtwork
    )

    override fun start() {
        val binary = NativeResources.extract("macos/nowplaying-bridge", "")
        if (binary == null || !binary.canExecute()) return
        bridge = binary
        artworkFetcher.start()

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
                            if (line.startsWith("{")) {
                                // publish returns null for unplayable lines, so
                                // a non-null artless snapshot is always a live
                                // track still missing its cover.
                                val snapshot = publish(line)
                                if (snapshot != null && snapshot.artworkBase64.isNullOrBlank()) {
                                    artworkFetcher.fetch(snapshot.contentId)
                                }
                            }
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
     * Transport commands spawn an osascript helper and must not run on the
     * UI thread, which would freeze both windows for the spawn.
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
