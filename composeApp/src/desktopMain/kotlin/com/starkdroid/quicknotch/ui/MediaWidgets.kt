package com.starkdroid.quicknotch.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starkdroid.quicknotch.generated.resources.Res
import com.starkdroid.quicknotch.generated.resources.music_next
import com.starkdroid.quicknotch.generated.resources.music_pause
import com.starkdroid.quicknotch.generated.resources.music_play
import com.starkdroid.quicknotch.generated.resources.music_previous
import com.starkdroid.quicknotch.media.MediaController
import com.starkdroid.quicknotch.media.MediaSession
import com.starkdroid.quicknotch.media.PlaybackState
import com.starkdroid.quicknotch.model.NotchConfig
import com.starkdroid.quicknotch.model.NotchStyle
import com.starkdroid.quicknotch.model.NotchSurface
import com.starkdroid.quicknotch.model.VisualiserStyle
import com.starkdroid.quicknotch.theme.TextPrimary
import org.jetbrains.compose.resources.stringResource
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin


internal val LiveAccent = Color(0xFF38BDF8)
internal val VinylDiscFill = Color(0xFF14161C)
internal val VinylRing = Color(0xFF343A45)


/**
 * A live-activity surface needs its feature on and a style that is not the drag handle,
 * which is excluded from every one of them, collapsed and expanded.
 */
internal fun isStyleEligibleForLiveActivity(config: NotchConfig): Boolean =
    config.style != NotchStyle.DRAG_HANDLE

internal fun isMusicLive(config: NotchConfig, session: MediaSession?): Boolean =
    config.widgets.nowPlayingEnabled &&
        isStyleEligibleForLiveActivity(config) &&
        session?.isActive == true


// Glyph colours brighten slightly over the translucent surfaces so they
// stay legible, and stay near-white on the opaque one.
internal fun handleTint(surface: NotchSurface): Color =
    if (surface == NotchSurface.TRANSLUCENT || surface == NotchSurface.SYSTEM_ACCENT)
        Color.White.copy(alpha = 0.42f)
    else Color.White.copy(alpha = 0.30f)

internal fun islandTint(surface: NotchSurface): Color =
    if (surface == NotchSurface.TRANSLUCENT || surface == NotchSurface.SYSTEM_ACCENT)
        Color.White.copy(alpha = 0.72f)
    else Color.White


/**
 * The album art clipped to a disc that turns while playing and freezes on pause: turns are
 * pushed at [PillAnimationFps], since an InfiniteTransition would redraw a paused track forever.
 */
@Composable
internal fun VinylDisc(
    art: ImageBitmap?,
    playing: Boolean,
    size: Dp,
    modifier: Modifier = Modifier,
    // Callers can stop the turn entirely (the settings preview does); the rate
    // itself is capped by [PillAnimationFps].
    animate: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    var angle by remember { mutableFloatStateOf(0f) }
    val spinning = playing && animate
    if (spinning) {
        // Accumulate frame deltas so pause/resume is seamless: the effect stops on cancel and
        // skipped frames still count, so PillAnimationFps caps writes, never speed.
        LaunchedEffect(spinning) {
            // A `delay` tick, not `withFrameNanos`: awaiting the frame clock subscribes the whole
            // scene to display rate, which cost ~16% of a core even at a 10 writes/s cap.
            var last = System.nanoTime()
            while (true) {
                delay(PillAnimationStepMillis)
                val now = System.nanoTime()
                val dt = (now - last) / 1e9f
                last = now
                angle += dt * (360f / 12f) // one turn per 12s
            }
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { rotationZ = angle }
            .clip(CircleShape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(role = Role.Button) { onClick() }
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(VinylDiscFill)
                .border(1.dp, VinylRing, CircleShape)
        )
        if (art != null) {
            Image(
                bitmap = art,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
            )
        } else {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(size * 0.5f)
            )
        }
        Box(
            modifier = Modifier
                .size(size * 0.22f)
                .clip(CircleShape)
                .background(Color(0xFF07080C))
                .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
        )
    }
}


/**
 * Marquees when the text is wider than the space, at a rate scaled to the overflow.
 * The transition exists only while it overflows, so a fitting title keeps no loop alive.
 */
@Composable
internal fun MarqueeText(
    text: String,
    color: Color,
    fontSize: TextUnit,
    fontWeight: FontWeight,
    modifier: Modifier = Modifier,
    lineHeight: TextUnit = TextUnit.Unspecified,
    // Callers can hold the scroll still (the settings preview does); the travel
    // itself ticks at [PillAnimationFps] in the draw phase.
    animate: Boolean = true
) {
    val measurer = rememberTextMeasurer()
    val textStyle = remember(color, fontSize, fontWeight, lineHeight) {
        TextStyle(
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            lineHeight = lineHeight
        )
    }
    val measured = remember(text, textStyle, measurer) {
        measurer.measure(AnnotatedString(text), style = textStyle)
    }
    val fullWidthPx = measured.size.width

    var viewportPx by remember { mutableIntStateOf(0) }
    val overflowPx = (fullWidthPx - viewportPx).coerceAtLeast(0)
    val needsMarquee = overflowPx > 0
    val gapPx = 48

    // Scroll rate keeps a roughly constant pixel speed regardless of length.
    val travelPx = overflowPx + gapPx
    val durationMs = (travelPx * 1000 / 30).coerceIn(2500, 20000)
    val speedPxPerSecond = travelPx * 1000f / durationMs

    // Real frame deltas written at [PillAnimationFps]: the old InfiniteTransition ran at display
    // rate, so a long title forced a full overlay repaint every frame.
    var scrollX by remember(text, travelPx) { mutableFloatStateOf(0f) }
    LaunchedEffect(needsMarquee, animate, travelPx, speedPxPerSecond) {
        if (!needsMarquee || !animate) {
            scrollX = 0f
            return@LaunchedEffect
        }
        // See the vinyl: a delayed tick, never a frame-clock subscription.
        var x = 0f
        var last = System.nanoTime()
        while (true) {
            delay(PillAnimationStepMillis)
            val now = System.nanoTime()
            val dt = (now - last) / 1e9f
            last = now
            x -= speedPxPerSecond * dt
            if (-x >= travelPx) x += travelPx // restart the loop without a jump
            scrollX = x
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds()
            .onSizeChanged { viewportPx = it.width },
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = AnnotatedString(text),
            style = textStyle,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            // graphicsLayer, not Modifier.offset { }: placement-phase offsets re-laid-out the row
            // every tick. scrollX is read inside the layer lambda, so a tick invalidates only this draw.
            modifier = Modifier.graphicsLayer {
                translationX = if (needsMarquee) {
                    scrollX
                } else {
                    ((viewportPx - fullWidthPx) / 2).coerceAtLeast(0).toFloat()
                }
            }
        )
    }
}


internal fun formatMs(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "${minutes}:${seconds.toString().padStart(2, '0')}"
}

@Composable
internal fun TrackTimeLabel(ms: Long, frameHeight: Dp = 28.dp) {
    // Fixed-height frame matching the progress strip beside it, so the elapsed
    // / duration stamps stay optically centred on the playhead line.
    Box(
        modifier = Modifier.height(frameHeight),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = formatMs(ms),
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 10.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Playhead strip: a track, an album-coloured fill and a head when seeking is available; macOS
 * stays display-only, matching MediaRemote. The accent is a plain Color, never a per-frame brush.
 */
@Composable
internal fun TrackProgress(
    fraction: Float,
    durationMs: Long,
    seekable: Boolean,
    enabled: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = LiveAccent
) {
    val canSeek = seekable && enabled
    val barHeight = 4.dp
    val headSize = 10.dp
    val laneInset = headSize / 2
    val clamped = fraction.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .height(16.dp)
            .then(
                if (canSeek) {
                    Modifier.pointerInput(durationMs) {
                        detectTapGestures { position ->
                            val ratio = (position.x / size.width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                            onSeek((durationMs.toFloat() * ratio).roundToInt().toLong())
                        }
                    }.pointerInput(durationMs) {
                        detectDragGestures { change, _ ->
                            val ratio = (change.position.x / size.width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                            onSeek((durationMs.toFloat() * ratio).roundToInt().toLong())
                        }
                    }
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        // Track + fill share an inset lane so the head can sit flush with the
        // 0% and 100% marks without spilling off the leading/trailing edges.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = laneInset),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(barHeight)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.14f))
            )
            if (clamped > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(clamped)
                        .height(barHeight)
                        .clip(CircleShape)
                        .background(accent)
                )
            }
        }
        // The head tracks the fill tip inside the same inset lane.
        if (canSeek) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = laneInset),
                contentAlignment = Alignment.CenterStart
            ) {
                Box(Modifier.fillMaxWidth(clamped)) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .size(headSize)
                            .clip(CircleShape)
                            .background(Color.White)
                            .border(2.dp, accent, CircleShape)
                    )
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
internal fun TransportControls(
    state: PlaybackState,
    enabled: Boolean,
    media: MediaController,
    /**
     * Cover's dominant colour (or the live accent with no cover); a plain Color that changes
     * per track, never per frame.
     */
    accent: Color = LiveAccent
) {
    val tint = TextPrimary.copy(alpha = if (enabled) 0.9f else 0.3f)
    // Optimistic play/pause: the bridge only reports back on its next poll (up to ~2s), so the
    // flip lands on press and reconciles with the session state, or after 3s if the command was lost.
    var pendingPlay by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(state) { pendingPlay = null }
    LaunchedEffect(pendingPlay) {
        if (pendingPlay != null) {
            delay(3_000)
            pendingPlay = null
        }
    }
    val showPlaying = pendingPlay ?: (state == PlaybackState.PLAYING)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Plain sized-Box clickables instead of IconButton: Material3 inflates every IconButton
        // to a 48dp target, which on this 4dp-apart row made clicks land on the neighbour.
        TransportButton(
            onClick = {
                media.previous()
            },
            enabled = enabled,
            size = 30.dp,
            iconSize = 19.dp,
            icon = Icons.Default.SkipPrevious,
            contentDescription = stringResource(Res.string.music_previous),
            tint = tint
        )
        var playHovered by remember { mutableStateOf(false) }
        val playScale by animateFloatAsState(
            targetValue = if (playHovered && enabled) 1.12f else 1f,
            animationSpec = spring(dampingRatio = 0.6f, stiffness = 380f),
            label = "playHover"
        )
        Box(
            modifier = Modifier
                .size(36.dp)
                .graphicsLayer {
                    scaleX = playScale
                    scaleY = playScale
                }
                // clip before clickable: the hit area is exactly the drawn circle (clip clips input too).
                .clip(CircleShape)
                .background(
                    accent.copy(
                        alpha = if (enabled) (if (playHovered) 1f else 0.92f) else 0.35f
                    )
                )
                .then(
                    if (enabled) {
                        Modifier
                            .onPointerEvent(PointerEventType.Enter) { playHovered = true }
                            .onPointerEvent(PointerEventType.Exit) { playHovered = false }
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                role = Role.Button
                            ) {
                                pendingPlay = !(state == PlaybackState.PLAYING)
                                media.playPause()
                            }
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (showPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = stringResource(
                    if (showPlaying) Res.string.music_pause else Res.string.music_play
                ),
                tint = Color.Black.copy(alpha = 0.85f),
                modifier = Modifier.size(22.dp)
            )
        }
        TransportButton(
            onClick = {
                media.next()
            },
            enabled = enabled,
            size = 30.dp,
            iconSize = 19.dp,
            icon = Icons.Default.SkipNext,
            contentDescription = stringResource(Res.string.music_next),
            tint = tint
        )
    }
}

/**
 * One flat transport button: hit area equals drawn area, no Material touch-target inflation.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TransportButton(
    onClick: () -> Unit,
    enabled: Boolean,
    size: Dp,
    iconSize: Dp,
    icon: ImageVector,
    contentDescription: String,
    tint: Color
) {
    var hovered by remember { mutableStateOf(false) }
    // Slight spring scale-up on hover: the affordance that the button is
    // clickable, matching the play button's hover.
    val scale by animateFloatAsState(
        targetValue = if (hovered && enabled) 1.16f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 380f),
        label = "transportHover"
    )
    Box(
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(
                // Pointer modifiers exist only while the page is interactive: clickable(enabled = false)
                // still hit-tests, so an inactive page would keep intercepting input in its slot.
                if (enabled) {
                    Modifier
                        .onPointerEvent(PointerEventType.Enter) { hovered = true }
                        .onPointerEvent(PointerEventType.Exit) { hovered = false }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Button,
                            onClick = onClick
                        )
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(iconSize)
        )
    }
}



/** Default bar tint: the same brightness the collapsed title uses. */
internal val WaveformWhite = Color(0xEBFFFFFF)

private const val TAU = 6.2831855f

/**
 * Fraction of the band every bar keeps when paused: a flat line across the middle, the resting
 * state of a real analyser at silence.
 */
private const val WaveformFlatFraction = 0.12f

/** Seconds for the bars to fall to (or climb back from) the flat line. */
private const val WaveformRampSec = 0.25f

/**
 * Faux analyser: not an FFT, since the media pipeline carries metadata only, so the motion is
 * driven by playback state. One coroutine for the bar set, one state write read in the draw scope.
 */
@Composable
internal fun MusicWaveform(
    playing: Boolean,
    animate: Boolean = true,
    modifier: Modifier = Modifier,
    color: Color = WaveformWhite,
    /** 0 derives the count from the available width, so the lane reads as an
     *  analyser at any slot size; pass a fixed number for a compact preview. */
    bars: Int = 0,
    barPitch: Dp = 6.dp,
    style: VisualiserStyle = VisualiserStyle.WAVEFORM
) {
    // Resolve the bar count in composition, not from an onSizeChanged write: the band table and
    // the draw loop must share one value, or the index runs past a stale table.
    BoxWithConstraints(modifier) {
        val count = if (bars > 0) {
            bars
        } else {
            val pitchPx = with(LocalDensity.current) { barPitch.toPx() }
            (constraints.maxWidth / pitchPx).roundToInt().coerceIn(8, 40)
        }
        val shape = remember(count) { WaveformBands(count) }
        WaveformCanvas(
            count = count,
            shape = shape,
            playing = playing,
            animate = animate,
            color = color,
            style = style,
            modifier = Modifier.matchParentSize()
        )
    }
}

@Composable
private fun WaveformCanvas(
    count: Int,
    shape: WaveformBands,
    playing: Boolean,
    animate: Boolean,
    color: Color,
    style: VisualiserStyle,
    modifier: Modifier = Modifier
) {
    var phase by remember { mutableFloatStateOf(0f) }
    // 0 = flat line (paused), 1 = full motion. A paused track falls to an even line, which is
    // what a real level meter does at silence.
    var presence by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(animate, playing) {
        if (!animate) {
            // Frozen preview (the settings cell): show the playing shape, held.
            if (playing) presence = 1f
            return@LaunchedEffect
        }
        val target = if (playing) 1f else 0f
        var seconds = phase
        var level = presence
        var last = System.nanoTime()
        while (true) {
            // See the vinyl: a delayed tick, never a frame-clock subscription.
            delay(PillAnimationStepMillis)
            val now = System.nanoTime()
            val dt = (now - last) / 1_000_000_000f
            last = now
            seconds += dt
            phase = seconds
            level += (target - level) * (dt / WaveformRampSec).coerceIn(0f, 1f)
            if (target == 0f && level <= 0.01f) {
                // Flat and quiet: stop ticking entirely, so a paused track costs
                // nothing at all. Waking on the next play restarts from here.
                presence = 0f
                return@LaunchedEffect
            }
            presence = level
        }
    }
    Canvas(modifier) {
        val t = phase
        val envelope = shape.envelope(t)
        val pitch = size.width / count
        val barWidth = pitch * 0.46f
        val radius = CornerRadius(barWidth / 2f, barWidth / 2f)
        val loud = presence
        for (index in 0 until count) {
            val dynamic = (shape.level(index, t) * envelope).coerceIn(0.08f, 1f)
            // At loud = 0 every bar collapses onto one height, which draws the flat line; the
            // bottom-anchored style settles onto a thin floor strip instead.
            val level = WaveformFlatFraction + (dynamic - WaveformFlatFraction) * loud
            val barHeight = level * size.height
            val x = index * pitch + (pitch - barWidth) / 2f
            when (style) {
                // Capsules keep a level-softened alpha so the row breathes;
                // solid bars draw fully opaque — every bar at full strength.
                VisualiserStyle.WAVEFORM -> drawRoundRect(
                    color = color.copy(alpha = color.alpha * (0.58f + 0.42f * level)),
                    topLeft = Offset(
                        x = x,
                        y = (size.height - barHeight) / 2f
                    ),
                    size = Size(barWidth, barHeight),
                    cornerRadius = radius
                )
                VisualiserStyle.BARS -> drawRect(
                    color = color,
                    topLeft = Offset(
                        x = x,
                        y = size.height - barHeight
                    ),
                    size = Size(barWidth, barHeight)
                )
            }
        }
    }
}

/**
 * Precomputed band parameters (two frequencies, phases, a bass weight), hashed from the index so
 * the pattern is identical on every machine.
 */
private class WaveformBands(private val count: Int) {
    private val freqA = FloatArray(count)
    private val freqB = FloatArray(count)
    private val phaseA = FloatArray(count)
    private val phaseB = FloatArray(count)
    private val weight = FloatArray(count)

    init {
        for (index in 0 until count) {
            val position = (index + 1) / count.toFloat()
            // Fast per-band partials so the row visibly dances instead of
            // swaying: ~0.9–2.5 Hz + ~2.2–5.2 Hz per bar, hashed per index.
            freqA[index] = TAU * (0.9f + 1.6f * hash(index, 1))
            freqB[index] = TAU * (2.2f + 3.0f * hash(index, 2))
            phaseA[index] = TAU * hash(index, 3)
            phaseB[index] = TAU * hash(index, 4)
            // Left bands (bass) sit taller, jittered so it is a slope, not a ramp.
            weight[index] = (1.12f - 0.60f * position) * (0.70f + 0.60f * hash(index, 5))
        }
    }

    /** Instantaneous bar height for [index] at time [t] (seconds), pre-envelope. */
    fun level(index: Int, t: Float): Float {
        val sum = sin(freqA[index] * t + phaseA[index]) * 0.62f +
            sin(freqB[index] * t + phaseB[index]) * 0.38f
        // Deep valleys + near-full peaks: the row punches rather than breathes.
        return (abs(sum) * 0.94f + 0.06f) * weight[index]
    }

    /** Shared fast wander: the whole row rises and falls like loudness does. */
    fun envelope(t: Float): Float =
        0.62f + 0.38f * sin(TAU * 0.42f * t + 0.9f) * sin(TAU * 0.13f * t + 0.3f)

    private fun hash(index: Int, salt: Int): Float {
        val x = sin((index + 1) * 12.9898 + salt * 78.233) * 43758.5453
        return (x - floor(x)).toFloat()
    }
}
