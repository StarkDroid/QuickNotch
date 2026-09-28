package com.starkdroid.quicknotch.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
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
import kotlin.math.roundToInt

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
 * at 60fps even with a paused track, burning a constant software-rendered
 * redraw of the whole overlay for nothing.
 */
@Composable
internal fun VinylDisc(
    art: ImageBitmap?,
    playing: Boolean,
    size: Dp,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    var angle by remember { mutableFloatStateOf(0f) }
    if (playing) {
        // Accumulate frame deltas so pause/resume is seamless: cancelling the
        // effect (playing → false) simply stops the disc where it is, and a
        // later resume continues from that exact angle.
        LaunchedEffect(playing) {
            var last = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                angle += (now - last) / 1e9f * (360f / 12f) // one turn per 12s
                last = now
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
    lineHeight: TextUnit = TextUnit.Unspecified
) {
    val measurer = rememberTextMeasurer()
    val measured = remember(text, color, fontSize, fontWeight, lineHeight, measurer) {
        measurer.measure(
            AnnotatedString(text),
            style = TextStyle(
                color = color,
                fontSize = fontSize,
                fontWeight = fontWeight,
                lineHeight = lineHeight
            )
        )
    }
    val fullWidthPx = measured.size.width

    var viewportPx by remember { mutableIntStateOf(0) }
    val overflowPx = (fullWidthPx - viewportPx).coerceAtLeast(0)
    val needsMarquee = overflowPx > 0
    val gapPx = 48

    // Scroll rate keeps a roughly constant pixel speed regardless of length.
    val travelPx = overflowPx + gapPx
    val durationMs = (travelPx * 1000 / 30).coerceIn(2500, 20000)
    val scrollX = if (needsMarquee) {
        val transition = rememberInfiniteTransition(label = "titleMarquee")
        val x by transition.animateFloat(
            initialValue = 0f,
            targetValue = -travelPx.toFloat(),
            animationSpec = infiniteRepeatable(tween(durationMs, easing = LinearEasing), RepeatMode.Restart),
            label = "titleScrollX"
        )
        x
    } else {
        0f
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds()
            .onSizeChanged { viewportPx = it.width },
        contentAlignment = Alignment.CenterStart
    ) {
        val offsetX = if (needsMarquee) {
            scrollX // centred start; scrolls once text overflows
        } else {
            ((viewportPx - fullWidthPx) / 2).coerceAtLeast(0).toFloat()
        }
        Text(
            text = AnnotatedString(text),
            style = TextStyle(
                color = color,
                fontSize = fontSize,
                fontWeight = fontWeight,
                lineHeight = lineHeight
            ),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            modifier = Modifier.offset { IntOffset(offsetX.roundToInt(), 0) }
        )
    }
}

// ── Time + progress ──────────────────────────────────────────────────────────

internal fun formatMs(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
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
 * A playhead strip: a hairline track, an accent-cyan gradient fill, and —
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
    val barHeight = 3.dp
    val headSize = 8.dp
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
                            Brush.horizontalGradient(
                                colors = listOf(ProgressGradientStart, LiveAccent, ProgressGradientEnd)
                            )
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

@OptIn(ExperimentalMaterial3Api::class)
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
    // tightly packed 30/34dp row (2dp apart) those targets overlapped each
    // other and the neighbouring buttons, so clicks near an edge landed on the
    // wrong control. Pin the targets to the visible buttons.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
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
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(LiveAccent.copy(alpha = if (enabled) 0.92f else 0.35f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = enabled,
                    role = Role.Button
                ) {
                    pendingPlay = !(state == PlaybackState.PLAYING)
                    media.playPause()
                },
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
    Box(
        modifier = Modifier
            .size(size)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
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
