package com.starkdroid.quicknotch.media

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.starkdroid.quicknotch.datasource.HostPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.util.Base64
import javax.imageio.ImageIO

/**
 * One system-wide "now playing" feed for the whole app. Exactly one instance
 * exists (created by [MediaControllerFactory]) and only ever polls in the
 * background; the UI observes [session] and issues transport commands.
 */
interface MediaController {
    val session: StateFlow<MediaSession?>

    /** Whether the current backend supports arbitrary seeking. */
    val supportsSeek: Boolean

    fun start()
    fun stop()
    fun playPause()
    fun next()
    fun previous()
    fun seekTo(positionMs: Long)
}

object MediaControllerFactory {
    fun create(): MediaController = when {
        HostPlatform.isMac -> MacNowPlayingController()
        HostPlatform.isWindows -> WindowsSmtcController()
        else -> object : MediaController {
            override val session = MutableStateFlow<MediaSession?>(null).asStateFlow()
            override val supportsSeek = false
            override fun start() {}
            override fun stop() {}
            override fun playPause() {}
            override fun next() {}
            override fun previous() {}
            override fun seekTo(positionMs: Long) {}
        }
    }
}

internal object MediaJson {
    val parser = Json { ignoreUnknownKeys = true; isLenient = true }
    const val artworkMaxPx = 256
}

/**
 * Shared snapshot decoding for every backend: JSON parsing, monotonic
 * stamping, and artwork decoding cached by content id so a base64 blob is
 * rasterized exactly once per track. All decoding runs on the caller's worker
 * thread; the flow is published to the UI.
 */
internal abstract class MediaControllerBase : MediaController {
    protected val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _session = MutableStateFlow<MediaSession?>(null)
    override val session: StateFlow<MediaSession?> = _session.asStateFlow()

    private var artworkKey: String? = null
    private var artworkCache: ImageBitmap? = null

    /** Raw `pos`/`id` of the last published PLAYING sample, used to detect
     *  bridges whose timeline position is pinned instead of advancing. */
    private var previousPlayingRawPosition: Long? = null
    private var previousPlayingRawId: String? = null

    protected fun publish(jsonLine: String) {
        val snapshot = runCatching {
            MediaJson.parser.decodeFromString(MediaSnapshot.serializer(), jsonLine)
        }.getOrNull() ?: return

        val state = when (snapshot.state.lowercase()) {
            "playing" -> PlaybackState.PLAYING
            "paused" -> PlaybackState.PAUSED
            "stopped" -> PlaybackState.STOPPED
            else -> PlaybackState.NONE
        }

        if (state == PlaybackState.NONE || snapshot.title.isBlank()) {
            previousPlayingRawPosition = null
            previousPlayingRawId = null
            _session.value = null
            return
        }

        // Browsers (Chrome/Edge) register with SMTC but do not pump timeline
        // position updates while a track plays: `Position` stays pinned at its
        // last change (the play-start offset) for the whole track, so every
        // poll re-delivers the same raw value. Snapping the playhead to it each
        // second resets the progress bar to ~0 and the extrapolation clock with
        // it. When a playing sample repeats the previous playing sample's raw
        // position for the same content, treat it as that pinned value and
        // keep advancing from where the previous sample extrapolated to —
        // players that genuinely advance (Spotify, Music, foobar) and seeks
        // (which do update `Position`) never match and keep their raw value.
        val sampledAt = System.nanoTime() / 1_000_000
        val positionMs = if (
            state == PlaybackState.PLAYING &&
            snapshot.positionMs == previousPlayingRawPosition &&
            snapshot.contentId == previousPlayingRawId
        ) {
            val previous = _session.value
            if (previous != null && previous.state == PlaybackState.PLAYING) {
                previous.positionMs + (sampledAt - previous.sampledAt)
            } else {
                snapshot.positionMs
            }
        } else {
            snapshot.positionMs
        }
        previousPlayingRawPosition =
            if (state == PlaybackState.PLAYING) snapshot.positionMs else null
        previousPlayingRawId =
            if (state == PlaybackState.PLAYING) snapshot.contentId else null

        _session.value = MediaSession(
            source = snapshot.source,
            sourceId = snapshot.sourceId,
            title = snapshot.title,
            artist = snapshot.artist,
            album = snapshot.album,
            state = state,
            positionMs = positionMs,
            durationMs = snapshot.durationMs,
            sampledAt = sampledAt,
            artwork = decodeArtwork(snapshot),
            contentId = snapshot.contentId,
            canSeek = supportsSeek
        )
    }

    /** Bridges may omit artwork for an unchanged track id; the cache carries it. */
    private fun decodeArtwork(snapshot: MediaSnapshot): ImageBitmap? {
        val b64 = snapshot.artworkBase64
        if (b64.isNullOrBlank()) {
            return artworkCache.takeIf { artworkKey == snapshot.contentId }
        }
        if (artworkKey == snapshot.contentId && artworkCache != null) return artworkCache

        val bitmap = runCatching {
            val img = ImageIO.read(ByteArrayInputStream(Base64.getMimeDecoder().decode(b64)))
                ?: return@runCatching null
            img.toComposeImageBitmap().downscaleTo(MediaJson.artworkMaxPx)
        }.getOrNull()

        if (bitmap != null) {
            artworkKey = snapshot.contentId
            artworkCache = bitmap
        }
        return bitmap ?: artworkCache.takeIf { artworkKey == snapshot.contentId }
    }

    override fun stop() {
        ioScope.cancel()
    }
}

private fun ImageBitmap.downscaleTo(maxPx: Int): ImageBitmap {
    if (width <= maxPx && height <= maxPx) return this
    val awt = toAwtImage()
    val ratio = maxPx.toFloat() / maxOf(awt.width, awt.height)
    val w = (awt.width * ratio).toInt().coerceAtLeast(1)
    val h = (awt.height * ratio).toInt().coerceAtLeast(1)
    val out = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
    val g = out.createGraphics()
    try {
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.drawImage(awt, 0, 0, w, h, null)
    } finally {
        g.dispose()
    }
    return out.toComposeImageBitmap()
}
