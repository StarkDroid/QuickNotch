package com.starkdroid.quicknotch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
    battery: AudioDeviceBattery?,
    deviceBattery: SystemBattery?,
    notificationCount: Int?,
    showVolume: Boolean,
    showBattery: Boolean,
    showDeviceBattery: Boolean,
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
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(22.dp),
                    contentAlignment = Alignment.Center
                ) {
                    MarqueeText(
                        text = media.title,
                        color = Color.White.copy(alpha = 0.92f),
                        fontSize = 11.sp,
                        lineHeight = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = titleModifier.fillMaxWidth()
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (showVolume && volumePercent != null) {
                        LevelRing(
                            level = volumePercent,
                            icon = volumePainter(),
                            contentDescription = stringResource(Res.string.music_volume),
                            modifier = volumeRingModifier.size(22.dp)
                        )
                    }
                    if (showBattery && battery != null) {
                        LevelRing(
                            level = battery.levelPercent,
                            icon = batteryPainter(battery.isAudio),
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
        } else if (clock.showDate || clock.showTime) {
            // Idle clock: date / time replace the dots while nothing plays.
            // Depending on the chosen format and pill width the whole phrase
            // usually fits; the pill clips rather than stretching.
            IdleClockRow(
                clock = clock,
                tint = islandTint(surface),
                battery = if (showBattery) battery else null,
                deviceBattery = if (showDeviceBattery) deviceBattery else null,
                notificationCount = notificationCount
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
