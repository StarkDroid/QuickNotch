package com.starkdroid.quicknotch.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starkdroid.quicknotch.generated.resources.Res
import com.starkdroid.quicknotch.generated.resources.music_audio_device
import com.starkdroid.quicknotch.generated.resources.music_battery
import com.starkdroid.quicknotch.generated.resources.music_volume
import com.starkdroid.quicknotch.media.AudioDeviceBattery
import com.starkdroid.quicknotch.media.MediaSession
import com.starkdroid.quicknotch.media.PlaybackState
import com.starkdroid.quicknotch.media.SystemBattery
import com.starkdroid.quicknotch.model.NotchStyle
import com.starkdroid.quicknotch.model.VisualiserStyle
import com.starkdroid.quicknotch.weather.WeatherData
import com.starkdroid.quicknotch.model.WaveformTint
import com.starkdroid.quicknotch.model.NotchSurface
import org.jetbrains.compose.resources.stringResource

/**
 * The collapsed island's content: three branches by state — live media (vinyl
 * disc + marquee title + level rings, each gated by its own collapsed-only
 * toggle), idle clock (date/time + optional rings), else the idle dots. The
 * drag-handle style shows only a slim grabber line.
 */
@Composable
internal fun QuickView(
    style: NotchStyle,
    surface: NotchSurface,
    media: MediaSession?,
    volumePercent: Int?,
    /** Master mute flag; null when unreadable, which reads as "not muted". */
    volumeMuted: Boolean? = null,
    battery: AudioDeviceBattery?,
    deviceBattery: SystemBattery?,
    notificationCount: Int?,
    showVolume: Boolean,
    showBattery: Boolean,
    showDeviceBattery: Boolean,
    /**
     * Equalizer bars under the title (live media only, collapsed only). The
     * caller also hides the outside progress border while this is on, so the
     * bars get the pill's full attention.
     */
    showWaveform: Boolean = false,
    /** Album-art-derived or white; see [WaveformTint]. */
    waveformColor: Color = WaveformWhite,
    /** Which bar geometry the collapsed visualiser draws; see [VisualiserStyle]. */
    visualiserStyle: VisualiserStyle = VisualiserStyle.WAVEFORM,
    /**
     * Waveform-only layout: the song name is dropped and the analyser takes the
     * whole title frame, so the live pill reads as disc + bars + level rings.
     */
    waveformHidesTitle: Boolean = false,
    /**
     * Calendar element (collapsed view only): [calendarEvent] is the live
     * event's title for the ticker — null unless one is running right now — and
     * [calendarEventCount] is how many events the day has, for the badged
     * calendar glyph. Gated by the widget toggle in settings, which is also
     * what decides whether a connection exists at all.
     */
    showCalendar: Boolean = false,
    calendarEvent: String? = null,
    calendarEventCount: Int = 0,
    /**
     * Current-conditions chip (collapsed idle pill only): condition glyph +
     * temperature left of the date. Null until a forecast loads, which hides
     * the chip rather than showing a false reading.
     */
    showWeather: Boolean = false,
    weather: WeatherData? = null,
    fahrenheit: Boolean = false,
    /** Shared-element morph modifiers for the live-activity disc/title/rings. */
    discModifier: Modifier = Modifier,
    titleModifier: Modifier = Modifier,
    volumeRingModifier: Modifier = Modifier,
    batteryRingModifier: Modifier = Modifier,
    clock: ClockPrefs,
    /**
     * Reports the measured width of the wrapping branches (idle clock, idle
     * dots) so the overlay can hug the pill to its content. The fill branches
     * (live media, ticker) never report: measuring them would read the island
     * back into itself. The overlay clears staleness itself by only consuming
     * the value while a wrapping branch is showing.
     */
    onWrapWidthChange: (Dp?) -> Unit = {},
) {
    Box(modifier = Modifier.fillMaxSize().clipToBounds(), contentAlignment = Alignment.Center) {
        if (style == NotchStyle.DRAG_HANDLE) {
            // A single slim grabber line — the whole point of the handle is to
            // stay out of the way until it is pulled open.
            Box(
                modifier = Modifier
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(handleTint(surface))
            )
        } else if (media != null) {
            // Collapsed live activity: the disc hugs the left end, the title
            // sits centred (marqueed when long) and the system volume degree
            // plus the audio device's battery cap the right — each behind its
            // own collapsed-only toggle. The pill is not interactive, so there
            // is no transport control here; play/pause lives in the expanded
            // page.
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    // 6dp of edge air: at 2dp the disc and the right-hand
                    // rings sat visually welded to the pill's outline.
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                // Same vinyl treatment as the expanded page's disc — art on a
                // grooved disc with a spindle hub, gently rotating while
                // playing — so the two morph without a style change mid-flight.
                VinylDisc(
                    art = media.artwork,
                    playing = media.state == PlaybackState.PLAYING,
                    size = 20.dp,
                    modifier = discModifier
                )
                // The title shares the exact 22dp frame the level rings use, so
                // the disc, text and rings all sit on one vertical centreline.
                // With the waveform on, that same frame stacks the title over the
                // bars — 13sp line + 1.5dp + 7dp of bars still fits inside it, so
                // the group stays on the shared centreline and the collapsed
                // footprint (and every morph key) is untouched.
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(22.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (showWaveform && waveformHidesTitle) {
                        // No title, no band split: the analyser owns the frame,
                        // 18dp of it (a hair under the full 22 so the capsules
                        // still breathe inside the pill). The Box centre-aligns
                        // it, so the bars sit on the same centreline as the disc
                        // and the rings.
                        MusicWaveform(
                            playing = media.state == PlaybackState.PLAYING,
                            color = waveformColor,
                            style = visualiserStyle,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(18.dp)
                        )
                    } else if (showWaveform) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            MarqueeText(
                                text = media.title,
                                color = Color.White.copy(alpha = 0.92f),
                                fontSize = 11.sp,
                                lineHeight = 13.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = titleModifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(1.5.dp))
                            MusicWaveform(
                                playing = media.state == PlaybackState.PLAYING,
                                color = waveformColor,
                                style = visualiserStyle,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(7.dp)
                            )
                        }
                    } else {
                        MarqueeText(
                            text = media.title,
                            color = Color.White.copy(alpha = 0.92f),
                            fontSize = 11.sp,
                            lineHeight = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = titleModifier.fillMaxWidth()
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (showCalendar) {
                        CalendarDayIndicator(count = calendarEventCount)
                    }
                    if (showVolume && volumePercent != null) {
                        // Mirror the expanded page exactly: a muted system
                        // reads as level 0 with a dimmed glyph and ring, so the
                        // collapsed pill and the music player never disagree
                        // about whether you can hear anything.
                        val mutedNow = volumeMuted == true
                        LevelRing(
                            level = if (mutedNow) 0 else volumePercent,
                            icon = volumePainter(),
                            contentDescription = stringResource(Res.string.music_volume),
                            modifier = volumeRingModifier.size(22.dp),
                            tint = if (mutedNow) LevelRingGlyph.copy(alpha = 0.4f) else LevelRingGlyph,
                            ringColor = if (mutedNow) LevelRingGlyph.copy(alpha = 0.3f) else LevelRingGlyph
                        )
                    }
                    if (showBattery && battery != null) {
                        LevelRing(
                            level = battery.levelPercent,
                            icon = batteryPainter(battery.isAudio),
                        badgeCount = battery.connectedCount.takeIf { it > 1 },
                            contentDescription = stringResource(
                                Res.string.music_battery,
                                battery.name?.takeIf { it.isNotBlank() }
                                    ?: stringResource(Res.string.music_audio_device)
                            ),
                            modifier = batteryRingModifier.size(22.dp),
                            lowTint = BatteryLowTint,
                            lowThreshold = BatteryLowThreshold
                        )
                    }
                }
            }
        } else if (calendarEvent != null) {
            // A calendar event takes the middle slot while it is live, so an
            // idle pill still reads as the clock-or-dots row it always was —
            // just with the meeting's name scrolling in the space the clock
            // used. Sits below the media branch deliberately: music playing
            // always wins the pill, and the ticker waits its turn.
            MarqueeText(
                text = calendarEvent,
                color = LevelRingGlyph,
                fontSize = 11.sp,
                lineHeight = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp)
            )
        } else if (clock.showDate || clock.showTime) {
            // Idle clock: date / time replace the dots while nothing plays.
            // Depending on the chosen format and pill width the whole phrase
            // usually fits; the pill clips rather than stretching.
            WrapWidthReporter(onWrapWidthChange = onWrapWidthChange) {
                IdleClockRow(
                    clock = clock,
                    tint = islandTint(surface),
                    battery = if (showBattery) battery else null,
                    deviceBattery = if (showDeviceBattery) deviceBattery else null,
                    notificationCount = notificationCount,
                    calendarEventCount = if (showCalendar) calendarEventCount else 0,
                    weather = if (showWeather) weather else null,
                    fahrenheit = fahrenheit,
                )
            }
        } else {
            // Idle dots; the notification bell, when readable, caps the row.
            WrapWidthReporter(onWrapWidthChange = onWrapWidthChange) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                val dot = islandTint(surface)
                Box(Modifier.size(5.dp).clip(CircleShape).background(dot.copy(alpha = 0.28f)))
                Box(
                    Modifier
                        .size(width = 18.dp, height = 5.dp)
                        .clip(CircleShape)
                        .background(dot.copy(alpha = 0.16f))
                )
                Box(Modifier.size(5.dp).clip(CircleShape).background(dot.copy(alpha = 0.28f)))
                if (notificationCount != null) {
                    NotificationIndicator(count = notificationCount, tint = dot.copy(alpha = 0.8f))
                }
                if (showCalendar) {
                    CalendarDayIndicator(count = calendarEventCount)
                }
                }
            }
        }
    }
}

/**
 * Reports a wrapping row's measured width to the island sizer. The wrapper
 * sizes itself to the content (both branches wrap rather than fill), so the
 * reported width is the content width including the row's own insets — the
 * island adds nothing on top and the pill hugs with exactly the row's
 * side padding on each end.
 */
@Composable
private fun WrapWidthReporter(
    onWrapWidthChange: (Dp?) -> Unit,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    Box(
        modifier = Modifier.onSizeChanged { size ->
            onWrapWidthChange(with(density) { size.width.toDp() })
        },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/**
 * 250 ms interpolation clock for the media progress displays. Localizing this
 * state keeps the 4 Hz tick from recomposing the whole overlay tree — only
 * the composable that owns the state (the pill strip, the music page's
 * transport/progress cluster) recomposes on each tick.
 */
@Composable
internal fun rememberMediaNowMs(playing: Boolean): Long {
    var nowMs by remember { mutableStateOf(System.nanoTime() / 1_000_000L) }
    LaunchedEffect(playing) {
        while (playing) {
            delay(250)
            nowMs = System.nanoTime() / 1_000_000L
        }
    }
    return nowMs
}

/** Playback progress tracing the island's own outline, owning its 250 ms clock. */
@Composable
internal fun NotchProgressOutlineForSession(
    session: MediaSession,
    /** Album-art color (or the live accent with no cover); the border's ink. */
    accent: Color,
    /** Bottom-corner radius of the island shape; mirrors [notchShape]. */
    radius: Dp,
    /** Capsule styles round the top corners too; the flush style keeps them square. */
    roundTop: Boolean,
    modifier: Modifier = Modifier
) {
    val nowMs = rememberMediaNowMs(session.state == PlaybackState.PLAYING)
    val target = session.progressFraction(nowMs).coerceIn(0f, 1f)
    // Gradual sweep: the 250 ms clock steps discretely, so ease each step into
    // the next. Read ONLY inside the draw lambda below, so the animation
    // schedules redraws of this one Canvas and never recomposes anything —
    // not even this leaf.
    val eased by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(300),
        label = "progressOutline"
    )
    // Plain overlay, no pointer modifiers: contributes no hit node (pitfall 24),
    // and draws nothing of its own besides the outline stroke.
    Box(modifier = modifier) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val f = eased
            if (f <= 0f) return@Canvas
            // Stroke sits half-in/half-out of the path by default, and the
            // island clips everything outside its shape — so inset the path by
            // half the stroke and the whole border stays visible, hugging the
            // static hairline from the inside.
            val strokePx = 2.dp.toPx()
            val inset = strokePx / 2f
            val r = radius.toPx().coerceAtLeast(0f)
            val w = size.width
            val h = size.height
            val top = if (roundTop) r else 0f
            // Perimeter from the top-left corner, clockwise down the left side,
            // around the bottom and up to the top-right: the notch's visible
            // outline, so playback fills the island's own border. Angle
            // convention is Android's: 0° at 3 o'clock, increasing CLOCKWISE
            // (y-down) — so both bottom quarters run 9→6 and 6→3 o'clock with
            // a NEGATIVE sweep. A +90 here bulges the arcs to the wrong side
            // and the following lineTo cuts diagonally through the middle.
            val path = Path().apply {
                moveTo(inset, top)
                lineTo(inset, h - inset - r)
                arcTo(
                    rect = Rect(inset, h - inset - 2f * r, inset + 2f * r, h - inset),
                    startAngleDegrees = 180f,
                    sweepAngleDegrees = -90f,
                    forceMoveTo = false
                )
                lineTo(w - inset - r, h - inset)
                arcTo(
                    rect = Rect(w - inset - 2f * r, h - inset - 2f * r, w - inset, h - inset),
                    startAngleDegrees = 90f,
                    sweepAngleDegrees = -90f,
                    forceMoveTo = false
                )
                lineTo(w - inset, top)
            }
            // Analytic length (straights + two quarter arcs): dashing needs the
            // real perimeter, and there is no public path measurer to ask.
            val straights = 2f * (h - inset - r - top) + (w - 2f * inset - 2f * r)
            val total = (straights + Math.PI.toFloat() * r).coerceAtLeast(1f)
            drawPath(
                path = path,
                color = accent,
                style = Stroke(
                    width = strokePx,
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(total * f, total),
                        0f
                    )
                )
            )
        }
    }
}
