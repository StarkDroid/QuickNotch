package com.starkdroid.quicknotch.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.starkdroid.quicknotch.generated.resources.*
import org.jetbrains.compose.resources.painterResource

/**
 * Shared palette for the level rings: a soft white glyph/arc over a faint
 * track, flipping to a warm amber when a level (e.g. a draining battery) drops
 * to [BatteryLowThreshold] or below.
 */
internal val LevelRingGlyph = Color.White.copy(alpha = 0.95f)
internal val LevelRingTrack = Color.White.copy(alpha = 0.16f)
internal val BatteryLowTint = Color(0xFFF59E0B)
internal const val BatteryLowThreshold = 20

// The ring sweeps 288° of the circle — 360° minus a 72° gap (20% of the
// circle) held at the bottom, so the indicator never reads as a full, closed
// ring. The level still maps the whole 0..100: at 100% the sweep closes the
// entire 288° arc, and the swept angle is always level/100 * 288°.
private const val RingStartDegrees = 126f // right edge of the bottom gap ([54°..126°] is open)
private const val RingSweepDegrees = 288f
private const val RingSweepAnimationMs = 260

/**
 * The single, reusable level indicator used for both volume and battery
 * everywhere in the notch: a flat [icon] glyph centred inside a circular
 * progress ring that is left 20% open at the bottom. [level] (0–100) maps to
 * the full 288° arc. A [lowTint]/[lowThreshold] pair lets callers flag
 * low-level states (e.g. an amber, alert-icon battery at ≤20%) without
 * forking this component.
 *
 * Sizing is entirely the caller's job via [modifier]; the ring fills the box
 * and the glyph occupies [iconFraction] of its diameter. The sweep settles
 * with a short animation so live value changes read smoothly.
 */
@Composable
internal fun LevelRing(
    level: Int,
    icon: Painter,
    contentDescription: String,
    modifier: Modifier = Modifier,
    tint: Color = LevelRingGlyph,
    ringColor: Color = LevelRingGlyph,
    trackColor: Color = LevelRingTrack,
    strokeWidth: Dp = 2.5.dp,
    iconFraction: Float = 0.48f,
    lowTint: Color? = null,
    lowThreshold: Int = BatteryLowThreshold
) {
    val clamped = level.coerceIn(0, 100)
    val levelColor = if (lowTint != null && clamped <= lowThreshold) lowTint else tint
    val ringStroke = if (lowTint != null && clamped <= lowThreshold) lowTint else ringColor
    val fraction by animateFloatAsState(
        targetValue = clamped / 100f,
        animationSpec = tween(RingSweepAnimationMs),
        label = "levelRingSweep"
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2f
            val arcTopLeft = Offset(inset, inset)
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = trackColor,
                startAngle = RingStartDegrees,
                sweepAngle = RingSweepDegrees,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            if (fraction > 0f) {
                drawArc(
                    color = ringStroke,
                    startAngle = RingStartDegrees,
                    sweepAngle = RingSweepDegrees * fraction,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
        }
        Icon(
            painter = icon,
            contentDescription = contentDescription,
            tint = levelColor,
            modifier = Modifier.fillMaxSize(iconFraction)
        )
    }
}

/** Volume glyph that flips with the level: silent, low, then loud. */
internal fun volumeGlyph(level: Int): ImageVector = when {
    level <= 0 -> Icons.AutoMirrored.Filled.VolumeOff
    level < 50 -> Icons.AutoMirrored.Filled.VolumeDown
    else -> Icons.AutoMirrored.Filled.VolumeUp
}

/** Volume glyph as a painter the level ring can draw (silent/low/loud). */
@Composable
internal fun volumePainter(level: Int): Painter = rememberVectorPainter(volumeGlyph(level))

/**
 * Battery/device glyph as a painter: the headphone drawable for a connected
 * audio device, the bluetooth drawable for any other connected device. Both
 * come from packaged compose resources — no icon data lives in code.
 */
@Composable
internal fun batteryPainter(isAudio: Boolean): Painter =
    if (isAudio) painterResource(Res.drawable.headphone)
    else painterResource(Res.drawable.bluetooth)

/**
 * System (laptop) battery glyph: the charging bolt while the OS is filling
 * the battery, otherwise a battery silhouette whose fill tracks the level
 * (full ≥ 80%, mid ≥ 3%, a low sliver below 3%). All four come from packaged
 * compose resources.
 */
@Composable
internal fun systemBatteryPainter(level: Int, charging: Boolean): Painter =
    when {
        charging -> painterResource(Res.drawable.charging)
        level >= 80 -> painterResource(Res.drawable.battery_full)
        level >= 3 -> painterResource(Res.drawable.battery_mid)
        else -> painterResource(Res.drawable.battery_low)
    }
