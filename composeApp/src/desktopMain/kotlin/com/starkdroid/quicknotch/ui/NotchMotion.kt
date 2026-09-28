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

// ── Overlay motion ───────────────────────────────────────────────────────────
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
    dampingRatio = 0.9f,
    stiffness = 900f,
    visibilityThreshold = 0.001f
)

// The dock reveal is a pure layer animation (alpha + scale) over an
// always-composed, always-expanded-size dock — nothing is measured or laid out
// while the window is growing, which is what removed the open-time frame drops.
internal val DockFadeIn = tween<Float>(170, easing = FastOutSlowInEasing)
internal val DockFadeOut = tween<Float>(90)

// The idle "curtain" expansion moves: the quickview slides down the growing
// island while the pinned apps scale up from behind it, as if the dock surges
// forward under the sliding pill. Live music deliberately skips both — its
// content flies to the detail page through the shared-element morph, so the
// pill keeps its plain fade (sliding the source would fight the flight).
//
// The motion is deliberately gradual: the pill glides most of the way down the
// expanded island ({ it * 3 } ≈ three of its own heights, carrying it off the
// bottom edge) over a long, eased tween while the dock rises on a slow spring,
// so the whole cross reads as one unbroken descent rather than a snap-and-pop.
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
 * Bounds spring for shared-element morphs. The quickview's disc/title and
 * their detailed-view counterparts travel on the same near-critically-damped
 * curve as the notch itself, so the two ends of the transition read as one
 * continuous motion rather than a crossfade.
 */
internal fun sharedBoundsSpring() = spring<Rect>(dampingRatio = 0.9f, stiffness = 320f)

/** Distance from an icon centre (in multiples of icon size) the wave reaches. */
internal const val FisheyeRadiusFactor = 1.5f

/** Cross-axis travel of a fully magnified icon, in dp. */
internal val IconLift = 8.dp

/**
 * Extra hover tolerance around the island footprint (the quickview or the
 * expanded dock). The hover hit area is the island plus this glow — never the
 * whole native window — so the notch retracts as soon as the cursor leaves the
 * visible island instead of staying glued open.
 */
internal val HoverGlow = 10.dp

/** Label font size as a fraction of the expanded dock height (≈11sp at 78dp). */
internal const val DockLabelFontSizeFactor = 0.145f

/**
 * Hard ceiling on how long the choreography waits for the size spring to settle
 * (in either direction) before acting anyway, so an interrupted animation can
 * never leave the notch stuck. Comfortably past the spring's settle time, and
 * past the dock fade that can delay the collapse spring starting.
 */
internal const val IslandSettleTimeoutMs = 650L

/**
 * Cosine-squared falloff that peaks at [peakScale] when the cursor is exactly
 * over an icon's centre and eases back to 1.0 at [radiusPx]. Because the peak
 * sits on the centre and the radius scales with icon size, the magnified icon
 * always lines up with the true cursor position.
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
