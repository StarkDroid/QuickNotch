package com.starkdroid.quicknotch.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos

// Near-critically-damped springs mirror AppKit's default view animation: a
// fast, confident response that settles without a rubbery bounce, so the notch
// never overshoots its window footprint (which would clip) yet still feels
// organic rather than linear.

internal fun notchSizeSpring() = spring(
    dampingRatio = 0.9f,
    stiffness = 340f,
    visibilityThreshold = Dp.VisibilityThreshold
)

internal fun iconSpring() = spring(
    dampingRatio = 0.8f,
    stiffness = 1000f,
    visibilityThreshold = 0.001f
)

internal fun pageSpring() = spring(
    // Critically damped and quick (~200ms): a flick lands on the next widget
    // decisively instead of visibly travelling through the pages, which reads
    // as scrolling rather than paging.
    dampingRatio = 1.0f,
    stiffness = 1600f,
    visibilityThreshold = 0.001f
)

// The dock reveal is a pure layer animation (alpha + scale) over an
// always-composed, always-expanded-size dock — nothing is measured or laid out
// while the window is growing, which is what removed the open-time frame drops.
internal val DockFadeIn = tween<Float>(170, easing = FastOutSlowInEasing)
internal val DockFadeOut = tween<Float>(90)

// Idle expansion slides the quickview down while the dock rises behind
// it. Live music skips this because the shared-element morph carries the
// content.
internal val QuickViewSlideIn = tween<IntOffset>(360, easing = FastOutSlowInEasing)
internal val QuickViewSlideOut = tween<IntOffset>(620, easing = FastOutSlowInEasing)
internal val QuickViewFadeOut = tween<Float>(420)

/** Entry size for the idle dock: far enough below 1 to read as "coming to the
 * front", not so small it feels like a locket pop. Growth is anchored near the
 * top edge where the pill sits, so the apps rise from behind the quickview. */
internal const val IdleDockEntryScale = 0.82f
internal val IdleDockOrigin = TransformOrigin(0.5f, 0.08f)
internal val IdleDockFadeIn = tween<Float>(300, easing = FastOutSlowInEasing)
internal val IdleDockFadeOut = tween<Float>(180)

internal fun dockRevealSpring() = spring(
    dampingRatio = 0.9f,
    stiffness = 300f,
    visibilityThreshold = 0.001f
)

/** The idle dock's slower, near-critically-damped rise (no bounce, no snap) —
 * deliberately separate from [dockRevealSpring] so the music page keeps its
 * faster, slightly springier 0.97 pop. */
internal fun idleDockRevealSpring() = spring(
    dampingRatio = 0.92f,
    stiffness = 150f,
    visibilityThreshold = 0.001f
)

/**
 * The same near-critically-damped spring as the notch, so both ends of a
 * morph read as one move.
 */
internal fun sharedBoundsSpring() = spring<Rect>(dampingRatio = 0.9f, stiffness = 320f)

/** Distance from an icon centre (in multiples of icon size) the wave reaches. */
internal const val FisheyeRadiusFactor = 1.5f

/** Cross-axis travel of a fully magnified icon, in dp. */
internal val IconLift = 8.dp

/**
 * Extra tolerance around the island; the hover footprint is the island plus
 * this glow, never the window.
 */
internal val HoverGlow = 10.dp

/** Label font size as a fraction of the expanded dock height (≈11sp at 78dp). */
internal const val DockLabelFontSizeFactor = 0.145f

/**
 * Hard limit on waiting for the size spring, so an interrupted animation
 * cannot leave the notch stuck.
 */
internal const val IslandSettleTimeoutMs = 650L

/**
 * Cosine-squared falloff peaking at the cursor and easing to 1.0 at
 * radiusPx.
 */
internal fun fisheyeScale(
    pointer: Float?,
    center: Float?,
    radiusPx: Float,
    peakScale: Float
): Float {
    if (pointer == null || center == null || radiusPx <= 0f) return 1f
    val distance = abs(pointer - center)
    if (distance >= radiusPx) return 1f
    val normalized = (distance / radiusPx) * (PI.toFloat() / 2f)
    val factor = cos(normalized) * cos(normalized)
    return 1f + (peakScale - 1f) * factor
}

/**
 * Continuous animations are capped at this rate; a full overlay repaint
 * is the cost, and freezing them looked like a bug.
 */
internal const val PillAnimationFps = 30f

/** The matching [kotlinx.coroutines.delay] step, in milliseconds. */
internal val PillAnimationStepMillis = (1000L / PillAnimationFps).toLong()
