package com.starkdroid.quicknotch.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starkdroid.quicknotch.generated.resources.*
import com.starkdroid.quicknotch.media.AudioDeviceBattery
import com.starkdroid.quicknotch.model.ClockDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/**
 * The user-tunable pieces of the collapsed-pill idle clock. Derived from
 * [com.starkdroid.quicknotch.model.NotchConfig] at the overlay call site so
 * the pill and the settings tab stay in lock-step.
 */
@Immutable
internal data class ClockPrefs(
    val showDate: Boolean = true,
    val showTime: Boolean = true,
    val clock24Hour: Boolean = false,
    val dateFormat: ClockDateFormat = ClockDateFormat.DD_MMM
)

/** Renders a date in the selected layout, in the system's locale. */
internal fun formatClockDate(format: ClockDateFormat, now: LocalDateTime = LocalDateTime.now()): String =
    now.toLocalDate().format(DateTimeFormatter.ofPattern(format.pattern, Locale.getDefault()))

/** Renders the time in 12- or 24-hour style with the system's locale. */
internal fun formatClockTime(clock24Hour: Boolean, now: LocalDateTime = LocalDateTime.now()): String =
    now.toLocalTime().format(DateTimeFormatter.ofPattern(if (clock24Hour) "HH:mm" else "h:mm a", Locale.getDefault()))

/**
 * The collapsed-pill idle content shown in place of the dots while nothing is
 * playing: a live date and/or time, centred, marquee-free. The display refreshes
 * exactly on the minute boundary so "9:41" flips at :00 rather than drifting
 * a few seconds off the real minute.
 */
@Composable
internal fun IdleClockRow(
    clock: ClockPrefs,
    tint: Color,
    modifier: Modifier = Modifier,
    battery: AudioDeviceBattery? = null
) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            val next = LocalDateTime.now()
            now = next
            // Sleep until the next minute boundary (+ a small fudge factor so
            // the wake-up lands just after :00, never just before).
            val msToNextMinute = 60_000L - next.second * 1000L - next.nano / 1_000_000
            delay((msToNextMinute + 150L).coerceIn(1_000L, 60_000L))
        }
    }

    // Date, separator, time and the battery ring all occupy the same
    // fixed-height frame, so their vertical centres always coincide no matter
    // what the font's line box reports — the default typography inflates the
    // text box and would otherwise push the glyphs below the ring's centre.
    val indicatorFrame = 22.dp
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .then(Modifier.widthIn(max = 190.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        if (clock.showDate) {
            ClockTextFrame(
                text = formatClockDate(clock.dateFormat, now),
                color = tint.copy(alpha = 0.92f),
                weight = FontWeight.Medium
            )
        }
        if (clock.showDate && clock.showTime) {
            ClockTextFrame(
                text = "\u00B7",
                color = tint.copy(alpha = 0.45f),
                weight = FontWeight.Medium
            )
        }
        if (clock.showTime) {
            ClockTextFrame(
                text = formatClockTime(clock.clock24Hour, now),
                color = tint,
                weight = FontWeight.SemiBold
            )
        }
        // Connected device battery right beside the date/time stamps, gated
        // by the same collapsed-pill toggle as the music-state battery.
        if (battery != null) {
            LevelRing(
                level = battery.levelPercent,
                icon = batteryGlyph(battery.levelPercent),
                contentDescription = stringResource(
                    Res.string.music_battery,
                    battery.name?.takeIf { it.isNotBlank() }
                        ?: stringResource(Res.string.music_audio_device)
                ),
                modifier = Modifier.size(indicatorFrame),
                lowTint = BatteryLowTint,
                lowThreshold = BatteryLowThreshold
            )
        }
    }
}

/**
 * Renders a clock fragment centred inside the same fixed-height frame that the
 * battery ring occupies, so the date, separator and time all share one
 * vertical centreline with the ring regardless of the font's reported line
 * box. The explicit tight line height keeps the default typography from
 * inflating the box and pushing the glyphs off centre.
 */
@Composable
private fun ClockTextFrame(
    text: String,
    color: Color,
    weight: FontWeight,
    frameHeight: Dp = 22.dp
) {
    Box(
        modifier = Modifier.height(frameHeight),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            fontWeight = weight,
            maxLines = 1
        )
    }
}