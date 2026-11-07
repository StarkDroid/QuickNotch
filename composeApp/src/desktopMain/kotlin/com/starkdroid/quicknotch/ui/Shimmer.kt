package com.starkdroid.quicknotch.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.unit.dp

private val ShimmerBase = Color(0xFF262830)
private val ShimmerHighlight = Color(0xFF3A3F4C)

/**
 * Sweeping skeleton highlight. The animated value is read in
 * drawWithContent so only the draw phase invalidates.
 */
@Composable
fun Modifier.shimmer(
    baseColor: Color = ShimmerBase,
    highlightColor: Color = ShimmerHighlight
): Modifier {
    // Drive the band by a real-time, rate-capped tick (the other pill
    // animations share PillAnimationFps), not an InfiniteTransition: a
    // frame-clock subscription forces this icon to read display rate even
    // when nothing else is animating.
    var progress by remember { mutableFloatStateOf(-1f) }
    LaunchedEffect(Unit) {
        val periodNanos = 1_100_000_000L
        var start = System.nanoTime()
        while (true) {
            delay(PillAnimationStepMillis)
            val t = ((System.nanoTime() - start) % periodNanos).toDouble() / periodNanos
            progress = (-1f + t * 3f).toFloat()
            if (start + periodNanos < System.nanoTime() - 1_000_000_000L) {
                start = System.nanoTime()
            }
        }
    }
    return drawWithContent {
        val bandStart = size.width * (progress - 1f)
        val brush = Brush.linearGradient(
            colors = listOf(baseColor, highlightColor, baseColor),
            start = Offset(bandStart, 0f),
            end = Offset(bandStart + size.width, 0f),
            tileMode = TileMode.Clamp
        )
        drawRect(brush)
        drawContent()
    }
}

/**
 * Icon-sized shimmer slot; the band paints the base colour, so no
 * background is applied.
 */
@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    shape: CornerBasedShape = RoundedCornerShape(6.dp),
    baseColor: Color = ShimmerBase,
    highlightColor: Color = ShimmerHighlight
) {
    Box(
        modifier = modifier
            .clip(shape)
            .shimmer(baseColor, highlightColor)
    )
}
