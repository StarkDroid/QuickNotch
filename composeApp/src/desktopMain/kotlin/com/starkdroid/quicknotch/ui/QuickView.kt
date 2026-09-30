package com.starkdroid.quicknotch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
     * caller also hides the edge progress strip while this is on, so the bars
     * get that band to themselves.
     */
    showWaveform: Boolean = false,
    /** Album-art-derived or white; see [WaveformTint]. */
    waveformColor: Color = WaveformWhite,
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
    /** Shared-element morph modifiers for the live-activity disc/title/rings. */
    discModifier: Modifier = Modifier,
    titleModifier: Modifier = Modifier,
    volumeRingModifier: Modifier = Modifier,
    batteryRingModifier: Modifier = Modifier,
    clock: ClockPrefs
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
                    .padding(horizontal = 12.dp, vertical = 4.dp)
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
            IdleClockRow(
                clock = clock,
                tint = islandTint(surface),
                battery = if (showBattery) battery else null,
                deviceBattery = if (showDeviceBattery) deviceBattery else null,
                notificationCount = notificationCount,
                calendarEventCount = if (showCalendar) calendarEventCount else 0
            )
        } else {
            // Idle dots; the notification bell, when readable, caps the row.
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

/**
 * Progress bar drawn against the pill's "inner" border — for a top-centred
 * notch that is the bottom edge (nearest the screen centre) — growing toward
 * the playhead as the track advances. Hidden behind the expanding island once
 * the dock opens (the expanded page shows its own progress control).
 */
@Composable
internal fun BoxScope.LiveProgressStrip(
    fraction: Float
) {
    val settlePadding = 2.dp
    Box(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(vertical = settlePadding)
            .fillMaxWidth(fraction.coerceIn(0f, 1f))
            .height(2.5.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(LiveAccent.copy(alpha = 0.95f))
    )
}
