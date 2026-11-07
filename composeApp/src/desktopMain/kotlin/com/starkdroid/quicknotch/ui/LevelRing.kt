package com.starkdroid.quicknotch.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.starkdroid.quicknotch.generated.resources.Res
import com.starkdroid.quicknotch.generated.resources.battery_full
import com.starkdroid.quicknotch.generated.resources.battery_low
import com.starkdroid.quicknotch.generated.resources.battery_mid
import com.starkdroid.quicknotch.generated.resources.bluetooth
import com.starkdroid.quicknotch.generated.resources.charging
import com.starkdroid.quicknotch.generated.resources.headphone
import com.starkdroid.quicknotch.generated.resources.notification
import com.starkdroid.quicknotch.generated.resources.volume
import com.starkdroid.quicknotch.theme.AccentRed
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

// Load indicators (CPU/GPU/memory/storage) run the same ring but warm up as
// they approach saturation, where a battery warns as it drains. One threshold
// for every load metric: above ~85% the machine is visibly working.
internal val LoadHighTint = Color(0xFFF97316)
internal const val LoadHighThreshold = 85

// The ring sweeps 288° of the circle — 360° minus a 72° gap (20% of the
// circle) held at the bottom, so the indicator never reads as a full, closed
// ring. The level still maps the whole 0..100: at 100% the sweep closes the
// entire 288° arc, and the swept angle is always level/100 * 288°.
private const val RingStartDegrees = 126f // right edge of the bottom gap ([54°..126°] is open)
private const val RingSweepDegrees = 288f
private const val RingSweepAnimationMs = 260

/**
 * Reusable ring with a glyph inside and a 20% gap at the bottom.
 * lowTint/lowThreshold flags a draining battery; sizing is the caller's.
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
    lowThreshold: Int = BatteryLowThreshold,
    /**
     * Load-metric alert tint above highThreshold; null keeps the normal
     * colour, and a low warning wins.
     */
    highTint: Color? = null,
    highThreshold: Int = LoadHighThreshold,
    /**
     * Count badge on the ring; null or <= 1 draws nothing.
     */
    badgeCount: Int? = null
) {
    val clamped = level.coerceIn(0, 100)
    val alertTint = when {
        lowTint != null && clamped <= lowThreshold -> lowTint
        highTint != null && clamped >= highThreshold -> highTint
        else -> null
    }
    val levelColor = alertTint ?: tint
    val ringStroke = alertTint ?: ringColor
    val fraction by animateFloatAsState(
        targetValue = clamped / 100f,
        animationSpec = tween(RingSweepAnimationMs),
        label = "levelRingSweep"
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val ringStyle = Stroke(width = stroke, cap = StrokeCap.Round)
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
                style = ringStyle
            )
            if (fraction > 0f) {
                drawArc(
                    color = ringStroke,
                    startAngle = RingStartDegrees,
                    sweepAngle = RingSweepDegrees * fraction,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = ringStyle
                )
            }
        }
        Icon(
            painter = icon,
            contentDescription = contentDescription,
            tint = levelColor,
            modifier = Modifier.fillMaxSize(iconFraction)
        )
        if (badgeCount != null && badgeCount > 1) {
            CountBadge(
                count = badgeCount,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 3.dp, y = (-3).dp)
            )
        }
    }
}

/**
 * Volume glyph tinted at draw time; one glyph for every level, since the
 * ring carries the level.
 */
@Composable
internal fun volumePainter(): Painter = painterResource(Res.drawable.volume)

/**
 * Headphone glyph for audio stacks, bluetooth otherwise; both come from
 * packaged resources.
 */
@Composable
internal fun batteryPainter(isAudio: Boolean): Painter =
    if (isAudio) painterResource(Res.drawable.headphone)
    else painterResource(Res.drawable.bluetooth)

/**
 * Charging bolt while charging, otherwise a battery silhouette whose fill
 * tracks the level.
 */
@Composable
internal fun systemBatteryPainter(level: Int, charging: Boolean): Painter =
    when {
        charging -> painterResource(Res.drawable.charging)
        level >= 80 -> painterResource(Res.drawable.battery_full)
        level >= 3 -> painterResource(Res.drawable.battery_mid)
        else -> painterResource(Res.drawable.battery_low)
    }

/**
 * Notification bell glyph as a painter for the quickview's badge indicator —
 * the packaged notification drawable, tinted at draw time like the rings.
 */
@Composable
internal fun notificationPainter(): Painter = painterResource(Res.drawable.notification)

/**
 * The small count pill: shape, colour and type ramp only, so the ring badge and
 * the notification badge are literally the same component. Placement is the
 * caller's job via [modifier].
 */
@Composable
internal fun CountBadge(
    count: Int,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(11.dp)
            .defaultMinSize(minWidth = 11.dp)
            .clip(RoundedCornerShape(5.5.dp))
            .background(AccentRed)
            .padding(horizontal = 3.dp)
    ) {
        Text(
            text = if (count >= 100) "99+" else count.toString(),
            color = Color.White,
            fontSize = 7.sp,
            lineHeight = 8.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}
