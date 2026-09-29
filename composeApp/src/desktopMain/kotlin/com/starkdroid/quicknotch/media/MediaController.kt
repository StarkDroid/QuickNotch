package com.starkdroid.quicknotch.media

import androidx.compose.ui.graphics.Color
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
import kotlin.math.abs
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
    // The waveform tint rides the artwork cache: one histogram pass per track,
    // on the poller's worker thread, never again until the cover changes.
    private var artworkTintCache: Color? = null

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

        val art = decodeArtwork(snapshot)
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
            artwork = art.bitmap,
            artTint = art.tint,
            contentId = snapshot.contentId,
            canSeek = supportsSeek
        )
    }

    /** Bridges may omit artwork for an unchanged track id; the cache carries it. */
    private fun decodeArtwork(snapshot: MediaSnapshot): DecodedArtwork {
        val b64 = snapshot.artworkBase64
        if (b64.isNullOrBlank()) {
            return artworkCache.takeIf { artworkKey == snapshot.contentId }
                .let { DecodedArtwork(it, artworkTintCache.takeIf { artworkKey == snapshot.contentId }) }
        }
        if (artworkKey == snapshot.contentId && artworkCache != null) {
            return DecodedArtwork(artworkCache, artworkTintCache)
        }

        val decoded = runCatching {
            val img = decodeArtBytes(Base64.getMimeDecoder().decode(b64))
                ?: return@runCatching null
            // The histogram reads the *downscaled* image we are about to ship, so
            // the tint is sampled from exactly the pixels the pill draws rather
            // than from a 1200px original that is thrown away a moment later.
            val small = scaleToMax(img, MediaJson.artworkMaxPx)
            small.toComposeImageBitmap() to dominantArtTint(small)
        }.getOrNull()

        if (decoded != null) {
            artworkKey = snapshot.contentId
            artworkCache = decoded.first
            artworkTintCache = decoded.second
            return DecodedArtwork(decoded.first, decoded.second)
        }
        return DecodedArtwork(
            artworkCache.takeIf { artworkKey == snapshot.contentId },
            artworkTintCache.takeIf { artworkKey == snapshot.contentId }
        )
    }

    override fun stop() {
        ioScope.cancel()
    }
}

/**
 * Dominant colour of a cover, for the waveform tint.
 *
 * A coarse 4-bit-per-channel histogram over a sampled grid (at most ~56×56
 * pixels regardless of the art's size), scored by `population × chroma` with a
 * penalty for luma far from mid-grey: that picks the colour the cover actually
 * *means* rather than the background black or a big pale field. Near-black and
 * near-white bins are skipped outright, and a cover with no chroma at all
 * returns null so the caller keeps its white default. The winner is then pushed
 * into a legible band (never washed out, never so dark it merges with the
 * island) because a dark cover's dominant colour would otherwise be invisible
 * against the notch.
 */
private fun dominantArtTint(img: BufferedImage): Color? {
    val w = img.width
    val h = img.height
    if (w <= 0 || h <= 0) return null
    val stride = maxOf(1, maxOf(w, h) / 56)

    val population = IntArray(4096)
    val sumRed = IntArray(4096)
    val sumGreen = IntArray(4096)
    val sumBlue = IntArray(4096)
    var y = 0
    while (y < h) {
        var x = 0
        while (x < w) {
            val argb = img.getRGB(x, y)
            val r = (argb shr 16) and 0xFF
            val g = (argb shr 8) and 0xFF
            val b = argb and 0xFF
            val bin = ((r shr 4) shl 8) or ((g shr 4) shl 4) or (b shr 4)
            population[bin]++
            sumRed[bin] += r
            sumGreen[bin] += g
            sumBlue[bin] += b
            x += stride
        }
        y += stride
    }

    var bestBin = -1
    var bestScore = 0f
    for (bin in population.indices) {
        val n = population[bin]
        if (n < 3) continue
        val r = sumRed[bin] / n
        val g = sumGreen[bin] / n
        val b = sumBlue[bin] / n
        val luma = 0.299f * r + 0.587f * g + 0.114f * b
        if (luma < 26f || luma > 238f) continue
        val chroma = maxOf(r, g, b) - minOf(r, g, b)
        if (chroma < 14f) continue
        val score = n * (chroma + 10f) * (1f - abs(luma - 140f) / 320f)
        if (score > bestScore) {
            bestScore = score
            bestBin = bin
        }
    }
    if (bestBin < 0) return null

    val n = population[bestBin]
    // `java.awt.Color` is spelled out: the imported `Color` is the Compose one.
    val hsb = FloatArray(3)
    java.awt.Color.RGBtoHSB(sumRed[bestBin] / n, sumGreen[bestBin] / n, sumBlue[bestBin] / n, hsb)
    hsb[1] = hsb[1].coerceAtLeast(0.5f)
    hsb[2] = hsb[2].coerceIn(0.72f, 0.95f)
    val rgb = java.awt.Color.HSBtoRGB(hsb[0], hsb[1], hsb[2])
    return Color(
        red = ((rgb shr 16) and 0xFF) / 255f,
        green = ((rgb shr 8) and 0xFF) / 255f,
        blue = (rgb and 0xFF) / 255f
    )
}

/**
 * Decode album art straight to the size we actually use.
 *
 * Artwork arrives as a 900–1200px JPEG, and the naive path (decode it, wrap it
 * as a Compose bitmap, unwrap it again, scale it, wrap it once more) measured
 * 19–49ms of pure copying *per track* — 131ms on a cold first track. That is
 * the single heaviest thing the app does when a track starts, and it is enough
 * to make every other app on the machine feel like it stuttered.
 *
 * ImageIO's `sourceSubsampling` lets the decoder skip pixels while reading, so a
 * 1200px JPEG destined for a 256px slot costs a fraction of a full decode and
 * never materialises the full-size bitmap at all. When the format cannot
 * subsample (PNG, and anything whose reader rejects the hint) we fall back to a
 * plain decode plus one bilinear step.
 */
private fun decodeArtBytes(bytes: ByteArray): BufferedImage? {
    val stream = ImageIO.createImageInputStream(ByteArrayInputStream(bytes))
    if (stream == null) return ImageIO.read(ByteArrayInputStream(bytes))
    try {
        val readers = ImageIO.getImageReaders(stream)
        if (!readers.hasNext()) return ImageIO.read(ByteArrayInputStream(bytes))
        val reader = readers.next()
        try {
            reader.input = stream
            val width = reader.getWidth(0)
            val height = reader.getHeight(0)
            val longest = maxOf(width, height)
            if (longest > MediaJson.artworkMaxPx) {
                // Integer subsampling keeps the result an exact fraction of the
                // source, which is what the DCT-scaling JPEG path wants.
                val factor = (longest + MediaJson.artworkMaxPx - 1) / MediaJson.artworkMaxPx
                val param = reader.defaultReadParam.apply {
                    setSourceSubsampling(factor.coerceIn(1, 8).toInt(), factor.coerceIn(1, 8).toInt(), 0, 0)
                }
                reader.read(0, param)?.let { return it }
            }
            return reader.read(0)
        } finally {
            reader.dispose()
        }
    } catch (_: Exception) {
        return null
    } finally {
        runCatching { stream.close() }
    }
}

/** One bilinear step down to [maxPx] on the long edge; identity when small. */
private fun scaleToMax(img: BufferedImage, maxPx: Int): BufferedImage {
    val longest = maxOf(img.width, img.height)
    if (longest <= maxPx) return img
    val ratio = maxPx.toFloat() / longest
    val w = (img.width * ratio).toInt().coerceAtLeast(1)
    val h = (img.height * ratio).toInt().coerceAtLeast(1)
    val out = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
    val g = out.createGraphics()
    try {
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.drawImage(img, 0, 0, w, h, null)
    } finally {
        g.dispose()
    }
    return out
}

/** Artwork plus its derived tint, as one cacheable unit. */
private data class DecodedArtwork(val bitmap: ImageBitmap?, val tint: Color?)
