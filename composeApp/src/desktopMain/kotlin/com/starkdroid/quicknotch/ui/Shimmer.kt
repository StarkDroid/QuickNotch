package com.starkdroid.quicknotch.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

private val ShimmerBase = Color(0xFF262830)
private val ShimmerHighlight = Color(0xFF3A3F4C)

/**
 * A lightweight skeleton placeholder that sweeps a soft highlight across its
 * bounds while real content (e.g. an app icon) loads. Animates only the band
 * position, so it stays cheap enough for the software renderer; the animation
 * stops as soon as this composable leaves the tree.
 */
@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    shape: CornerBasedShape = RoundedCornerShape(6.dp),
    baseColor: Color = ShimmerBase,
    highlightColor: Color = ShimmerHighlight
) {
    var sizePx by remember { mutableStateOf(IntSize.Zero) }
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmerProgress"
    )
    val brush = if (sizePx.width > 0) {
        Brush.linearGradient(
            colors = listOf(baseColor, highlightColor, baseColor),
            start = Offset(sizePx.width * (progress - 1f), 0f),
            end = Offset(sizePx.width * progress, 0f),
            tileMode = TileMode.Clamp
        )
    } else {
        SolidColor(baseColor)
    }
    Box(
        modifier = modifier
            .onSizeChanged { sizePx = it }
            .clip(shape)
            .background(brush)
    )
}