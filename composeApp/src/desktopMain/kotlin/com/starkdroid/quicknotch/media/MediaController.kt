package com.starkdroid.quicknotch.media

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.starkdroid.quicknotch.datasource.HostPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
 * How long an empty player holds the last session. The hold ends on a
 * timer because the bridge de-dups lines and may never send another.
 */
private const val EMPTY_GRACE_MS = 4_000L

/**
 * JSON parsing, monotonic stamping and per-id art decoding shared by every
 * backend.
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
    // The poller and the detached artwork fetcher share the cache above from
    // different IO coroutines; every read/write below takes this lock.
    private val artLock = Any()

    /** Raw `pos`/`id` of the last published PLAYING sample, used to detect
     *  bridges whose timeline position is pinned instead of advancing. */
    private var previousPlayingRawPosition: Long? = null
    private var previousPlayingRawId: String? = null

    /**
     * Last playable snapshot and its arrival time, held for EMPTY_GRACE_MS
     * so a track change does not unmount the music page.
     */
    private var lastSnapshot: MediaSnapshot? = null
    private var lastPlayableAtMs: Long = 0L

    /**
     * Serialises session writes from the poller and the reap timer. Lock order
     * is publishLock then artLock.
     */
    private val publishLock = Any()

    /** Pending one-shot reap for the current hold; see [armHoldExpiry]. */
    private var holdExpiryJob: Job? = null

    /**
     * Parses one bridge line under publishLock. A playable line refreshes
     * the hold; anything else serves the held snapshot and arms the reap.
     */
    private fun playableSnapshot(jsonLine: String): MediaSnapshot? {
        val snapshot = runCatching {
            MediaJson.parser.decodeFromString(MediaSnapshot.serializer(), jsonLine)
        }.getOrNull()
        val playable = snapshot != null &&
            stateOf(snapshot) != PlaybackState.NONE && snapshot.title.isNotBlank()
        val nowMs = System.nanoTime() / 1_000_000
        return if (playable) {
            holdExpiryJob?.cancel()
            holdExpiryJob = null
            lastSnapshot = snapshot
            lastPlayableAtMs = nowMs
            snapshot
        } else if (lastSnapshot != null && nowMs - lastPlayableAtMs < EMPTY_GRACE_MS) {
            armHoldExpiry()
            lastSnapshot
        } else {
            holdExpiryJob?.cancel()
            holdExpiryJob = null
            reapHeldSnapshot()
            null
        }
    }

    /**
     * Arms the clock-driven reap. The bridge de-dups identical lines, so a
     * force-quit would otherwise leave the pill on the dead track forever.
     */
    private fun armHoldExpiry() {
        if (holdExpiryJob?.isActive == true) return
        val deadlineMs = lastPlayableAtMs + EMPTY_GRACE_MS
        holdExpiryJob = ioScope.launch {
            val waitMs = deadlineMs - (System.nanoTime() / 1_000_000)
            if (waitMs > 0) delay(waitMs)
            synchronized(publishLock) {
                val nowMs = System.nanoTime() / 1_000_000
                if (lastSnapshot != null && nowMs - lastPlayableAtMs >= EMPTY_GRACE_MS) {
                    reapHeldSnapshot()
                }
            }
        }
    }

    /**
     * The only place the empty session is written, so the reap timer and an
     * expired line cannot drift. Caller holds publishLock.
     */
    private fun reapHeldSnapshot() {
        previousPlayingRawPosition = null
        previousPlayingRawId = null
        _session.value = null
        lastSnapshot = null
    }

    /**
     * Writes one snapshot under publishLock so the reap cannot fall between
     * the hold check and the write; returns it when a session was produced.
     */
    protected fun publish(jsonLine: String): MediaSnapshot? = synchronized(publishLock) {
        val snapshot = playableSnapshot(jsonLine)
        if (snapshot == null) return null
        val state = stateOf(snapshot)

        // Chrome and Edge pin Position while playing, so a repeated raw value
        // for the same content is extrapolated instead of snapping the playhead
        // back to the start.
        val sampledAt = System.nanoTime() / 1_000_000
        val positionMs = extrapolatedPosition(state, snapshot, sampledAt)

        // Bridges may omit artwork for an unchanged track id; the cache carries it.
        val art = decodeArtworkBytes(snapshot.artworkBase64, snapshot.contentId)
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
        snapshot
    }

    /**
     * Pins the last PLAYING sample's raw position/id so the next sample can
     * tell a genuinely advancing timeline from a pinned one, and extrapolates
     * the playhead forward when it is pinned.
     */
    private fun extrapolatedPosition(
        state: PlaybackState,
        snapshot: MediaSnapshot,
        sampledAt: Long
    ): Long {
        val pinned = state == PlaybackState.PLAYING &&
            snapshot.positionMs == previousPlayingRawPosition &&
            snapshot.contentId == previousPlayingRawId
        val positionMs = if (pinned) {
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
        return positionMs
    }

    /**
     * Fills in late cover art, dropping bytes when the track already moved on
     * or another source supplied art.
     */
    protected fun publishArtwork(contentId: String, base64: String) {
        if (base64.isBlank() || contentId.isBlank()) return
        val art = decodeArtworkBytes(base64, contentId)
        if (art.bitmap == null) return
        // Read-check-write under the publish lock: a late cover that raced the
        // hold-expiry reap must not graft art onto a session that just cleared.
        synchronized(publishLock) {
            val current = _session.value
            if (current != null && current.contentId == contentId && current.artwork == null) {
                _session.value = current.copy(artwork = art.bitmap, artTint = art.tint)
            }
        }
    }

    /**
     * Decodes base64 art straight to display size, cached by contentId; a
     * blank or failed decode keeps the placeholder.
     */
    private fun decodeArtworkBytes(base64: String?, contentId: String): DecodedArtwork {
        if (base64.isNullOrBlank()) return cachedArtwork(contentId)
        // Cache read fast-path, shared by the poller and the artwork fetcher.
        val cached = synchronized(artLock) {
            if (artworkKey == contentId && artworkCache != null) {
                DecodedArtwork(artworkCache, artworkTintCache)
            } else {
                null
            }
        }
        return cached ?: decodeAndStore(base64, contentId) ?: cachedArtwork(contentId)
    }

    /** Full decode plus one cache write; null when the bytes do not rasterize. */
    private fun decodeAndStore(base64: String, contentId: String): DecodedArtwork? {
        val decoded = runCatching {
            decodeArtBytes(Base64.getMimeDecoder().decode(base64))?.let { img ->
                // The histogram reads the *downscaled* image we are about to
                // ship, so the tint is sampled from exactly the pixels the pill
                // draws rather than from a 1200px original that is thrown away
                // a moment later.
                val small = scaleToMax(img, MediaJson.artworkMaxPx)
                small.toComposeImageBitmap() to dominantArtTint(small)
            }
        }.getOrNull() ?: return null
        synchronized(artLock) {
            artworkKey = contentId
            artworkCache = decoded.first
            artworkTintCache = decoded.second
        }
        return DecodedArtwork(decoded.first, decoded.second)
    }

    private fun cachedArtwork(contentId: String): DecodedArtwork {
        synchronized(artLock) {
            val bitmap = artworkCache.takeIf { artworkKey == contentId }
            val tint = artworkTintCache.takeIf { artworkKey == contentId }
            return DecodedArtwork(bitmap, tint)
        }
    }

    override fun stop() {
        ioScope.cancel()
    }
}

/** Wire `state` string → [PlaybackState]; anything unknown reads as NONE. */
private fun stateOf(snapshot: MediaSnapshot): PlaybackState =
    when (snapshot.state.lowercase()) {
        "playing" -> PlaybackState.PLAYING
        "paused" -> PlaybackState.PAUSED
        "stopped" -> PlaybackState.STOPPED
        else -> PlaybackState.NONE
    }

/**
 * Picks a cover's dominant colour by population and chroma, skipping
 * near-black and near-white bins, then keeps it legible against the island.
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
 * Decodes art straight to 256 px with sourceSubsampling; a full decode
 * and rescale was the heaviest work on track start.
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
