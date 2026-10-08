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
import androidx.compose.ui.graphics.Brush
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
import com.starkdroid.quicknotch.theme.TextPrimary
import org.jetbrains.compose.resources.stringResource
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

// ── Palette ──────────────────────────────────────────────────────────────────

internal val LiveAccent = Color(0xFF38BDF8)
internal val VinylDiscFill = Color(0xFF14161C)
internal val VinylRing = Color(0xFF343A45)

/** The playhead fill sweeps accent cyan into a pale sky tip at the head. */
private val ProgressGradientStart = Color(0xFF0EA5E9)
private val ProgressGradientEnd = Color(0xFF7DD3FC)

// ── Media state helpers ──────────────────────────────────────────────────────

/**
 * A widget only renders when its feature is enabled and its conditions hold:
 * the now-playing page needs a live session and a capsule-friendly style
 * (the drag handle is deliberately excluded from every live-activity surface,
 * both collapsed and expanded).
 */
internal fun isStyleEligibleForLiveActivity(config: NotchConfig): Boolean =
    config.style != NotchStyle.DRAG_HANDLE

private
 val progressBrush = Brush.horizontalGradient(
    colors = listOf(ProgressGradientStart, LiveAccent, ProgressGradientEnd)
)

internal fun isMusicLive(config: NotchConfig, session: MediaSession?): Boolean =
    config.widgets.nowPlayingEnabled &&
        isStyleEligibleForLiveActivity(config) &&
        session?.isActive == true

// ── Glyph tints ──────────────────────────────────────────────────────────────

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

// ── Vinyl disc ───────────────────────────────────────────────────────────────

/**
 * The spinning record the live activity is named after: the track's album art
 * clipped to a disc that rotates while playing — frozen at its current angle
 * when paused. The rotation accumulates real frame deltas and only runs while
 * playing: an `InfiniteTransition` here would keep invalidating the draw phase
 * even with a paused track, burning a constant redraw of the whole overlay for
 * nothing. Turns are pushed at [PillAnimationFps], not at display rate.
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
        // Accumulate frame deltas so pause/resume is seamless: cancelling the
        // effect (spinning → false) simply stops the disc where it is, and a
        // later resume continues from that exact angle. Real deltas are still
        // summed across the skipped frames, so the [PillAnimationFps] write cap
        // changes how often the pill repaints, never how fast it turns.
        LaunchedEffect(spinning) {
            // A `delay` tick, deliberately NOT `withFrameNanos`: awaiting the
            // frame clock subscribes the whole scene to display-rate frames, so
            // the pill kept taking 60 frames a second however rarely this loop
            // wrote (measured: even a 10 writes/s cap still cost ~16% of a core —
            // the subscription, not the write, was the cost). Monotonic deltas
            // keep the turn rate exact regardless of scheduler jitter.
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

// ── Marquee title ────────────────────────────────────────────────────────────

/**
 * A single-line label that marquees when its text is wider than the available
 * space: centred normally, then slowly scrolling right-to-left with a small
 * gap before the loop. The rate scales with the overflow so short titles crawl
 * and long ones glide.
 *
 * The scrolling transition exists only while the text actually overflows —
 * a fitting title must not keep a 60fps animation alive (and, on the software
 * renderer, a full-window redraw loop) just to sit centred.
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

    // Same clock shape as the vinyl and the waveform: the transition used to run
    // at display rate (an InfiniteTransition cannot be throttled), so a long
    // title forced a full overlay repaint every frame. Travelling by real frame
    // deltas and pushing at [PillAnimationFps] keeps the exact same speed while
    // halving the repaints.
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
            // graphicsLayer, not Modifier.offset { }: the offset lambda runs in
            // the placement phase, so every tick of the scroll re-laid-out the
            // pill's row. A layer translation is a draw-phase change — the same
            // pixels, one invalidation instead of a layout pass. The scrollX
            // state is likewise read inside the layer lambda, so each 30 Hz
            // tick invalidates only the draw of this Text, never composition.
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

// ── Time + progress ──────────────────────────────────────────────────────────

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
 * A playhead strip: a slim rounded track, an accent-cyan gradient fill, and —
 * when seeking is available — a round head ringed in the live accent that
 * rides the tip of the fill. Dragging or tapping (when the backend supports
 * seeking) retargets the OS player; on macOS the strip is display-only,
 * matching MediaRemote's limitations.
 */
@Composable
internal fun TrackProgress(
    fraction: Float,
    durationMs: Long,
    seekable: Boolean,
    enabled: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
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
                        .background(
                            progressBrush
                        )
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
                            .border(2.dp, LiveAccent, CircleShape)
                    )
                }
            }
        }
    }
}

// ── Transport ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
internal fun TransportControls(
    state: PlaybackState,
    enabled: Boolean,
    media: MediaController
) {
    val tint = TextPrimary.copy(alpha = if (enabled) 0.9f else 0.3f)
    // Optimistic play/pause: the OS player reacts through the media bridge's
    // next poll (up to ~2s), and without this the icon sat on the old state
    // for that whole window — read as "the button is laggy". The flip happens
    // the instant the button is pressed and reconciles when the session state
    // changes (or after 3s, if a command was lost).
    var pendingPlay by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(state) { pendingPlay = null }
    LaunchedEffect(pendingPlay) {
        if (pendingPlay != null) {
            delay(3_000)
            pendingPlay = null
        }
    }
    val showPlaying = pendingPlay ?: (state == PlaybackState.PLAYING)
    // Material3 inflates every IconButton to a 48dp touch target — on this
    // tightly packed 30/36dp row (4dp apart) those targets overlapped each
    // other and the neighbouring buttons, so clicks near an edge landed on the
    // wrong control. Pin the targets to the visible buttons.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Plain sized-Box clickables instead of IconButton: Material3's
        // IconButton inflates its touch target to 48dp (overflowing its slot
        // symmetrically), which on this tightly packed row made the drawn
        // buttons and the hit geometry disagree — clicks near an edge landed
        // in the neighbour's inflated zone or nowhere at all. With these, the
        // hit area is exactly the drawn button.
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
                // clip BEFORE clickable: the hit area is exactly the drawn
                // circle (clip also clips pointer input), so the whole icon
                // plus its background is one clickable surface.
                .clip(CircleShape)
                .background(
                    LiveAccent.copy(
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
 * One flat transport button: a fixed-size hit target with a centred glyph —
 * hit area = drawn area, no Material touch-target inflation.
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
                // The pointer modifiers exist ONLY while the page is
                // interactive. `clickable(enabled = false)` still registers a
                // hit-testable node, so an inactive page (alpha 0, composed
                // above the music page) would keep intercepting pointer input
                // in its slot; dropping the modifiers entirely guarantees an
                // inactive page contributes no hit node at all.
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


// ── Waveform ─────────────────────────────────────────────────────────────────

/** Default bar tint: the same brightness the collapsed title uses. */
internal val WaveformWhite = Color(0xEBFFFFFF)

private const val TAU = 6.2831855f

/**
 * Fraction of the band every bar keeps once the track is paused: a flat line
 * across the middle of the lane, the way a real analyser bottoms out at
 * silence. It is the resting state the bars fall to, not a placeholder.
 */
private const val WaveformFlatFraction = 0.12f

/** Seconds for the bars to fall to (or climb back from) the flat line. */
private const val WaveformRampSec = 0.25f

/**
 * The collapsed pill's faux analyser: side-by-side capsules that each grow and
 * shrink around their vertical centre, so every bar breathes from both ends the
 * way a real spectrum does.
 *
 * Deliberately NOT an FFT. Nothing in the media pipeline can hear the audio —
 * `MediaRemote` (and SMTC on Windows) publish metadata only, and reading the
 * system mix would need a ScreenCaptureKit stream plus a permission grant, so
 * this is motion driven by playback state instead. It reads like a visualiser
 * and costs nothing the pill was not already paying:
 *
 * - One coroutine for the whole bar set (not one per bar) and one state write
 *   per update, read inside the draw scope: the node invalidates its draw phase
 *   only, exactly like [shimmer] — no recomposition, no extra layer.
 * - The clock shares the notch-wide [PillAnimationFps] tick: bars look fluid at
 *   30 updates a second, and every update is exactly one overlay repaint.
 * - `playing && animate` gates the loop, so a paused track freezes the bars
 *   (same contract as [VinylDisc], resumed from the frozen phase so it never
 *   jumps). Nothing freezes while the settings window merely has focus — a
 *   top-centred notch stays visible above it, so a still pill reads as broken.
 *
 * Heights come from two slow sines per band plus a shared loudness envelope,
 * all parameters hashed deterministically from the band index: the pattern is
 * stable across frames (it reads as one coherent wave, not per-bar noise),
 * bass-weighted like a real spectrum, and allocates nothing per frame.
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
    barPitch: Dp = 6.dp
) {
    // The bar count is resolved in composition (not in the draw scope, and not
    // from an onSizeChanged write): the band table and the draw loop must be
    // built from the *same* value, or a width change lets the loop index past a
    // table that has not been rebuilt yet — an ArrayIndexOutOfBounds that takes
    // the whole app with it.
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
    modifier: Modifier = Modifier
) {
    var phase by remember { mutableFloatStateOf(0f) }
    // 0 = flat line (paused), 1 = full motion. A paused track does not merely
    // freeze: the bars fall to an even line, which is what a real level meter
    // does when the signal stops and the only honest way to say "no audio".
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
            val dynamic = (shape.level(index, t) * envelope).coerceIn(0.14f, 1f)
            // At loud = 0 every bar collapses onto the same height, which is
            // what draws the flat line; at 1 it is the untouched analyser shape.
            val level = WaveformFlatFraction + (dynamic - WaveformFlatFraction) * loud
            val barHeight = level * size.height
            drawRoundRect(
                color = color.copy(alpha = color.alpha * (0.58f + 0.42f * level)),
                topLeft = Offset(
                    x = index * pitch + (pitch - barWidth) / 2f,
                    y = (size.height - barHeight) / 2f
                ),
                size = Size(barWidth, barHeight),
                cornerRadius = radius
            )
        }
    }
}

/**
 * Precomputed band parameters: two frequencies, their phases, and a bass-leaning
 * weight per bar. All derived from a cheap deterministic hash of the index, so
 * the set is identical on every machine and every launch.
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
            freqA[index] = TAU * (0.42f + 0.85f * hash(index, 1))
            freqB[index] = TAU * (1.25f + 1.7f * hash(index, 2))
            phaseA[index] = TAU * hash(index, 3)
            phaseB[index] = TAU * hash(index, 4)
            // Left bands (bass) sit taller, jittered so it is a slope, not a ramp.
            weight[index] = (1.04f - 0.44f * position) * (0.78f + 0.44f * hash(index, 5))
        }
    }

    /** Instantaneous bar height for [index] at time [t] (seconds), pre-envelope. */
    fun level(index: Int, t: Float): Float {
        val sum = sin(freqA[index] * t + phaseA[index]) * 0.62f +
            sin(freqB[index] * t + phaseB[index]) * 0.38f
        return (abs(sum) * 0.86f + 0.14f) * weight[index]
    }

    /** Shared slow wander: the whole row rises and falls like loudness does. */
    fun envelope(t: Float): Float =
        0.7f + 0.3f * sin(TAU * 0.19f * t + 0.9f) * sin(TAU * 0.055f * t + 0.3f)

    private fun hash(index: Int, salt: Int): Float {
        val x = sin((index + 1) * 12.9898 + salt * 78.233) * 43758.5453
        return (x - floor(x)).toFloat()
    }
}
