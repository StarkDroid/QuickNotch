package com.starkdroid.quicknotch.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The four screen-edge anchors the notch can occupy. Corners and free-form
 * dragging are intentionally unsupported: a fixed, centred anchor keeps the
 * overlay perfectly still while it expands and collapses. User-facing labels
 * live in `composeResources/values/strings.xml`.
 */
@Serializable
enum class NotchPosition {
    TOP_CENTER,
    RIGHT_CENTER,
    BOTTOM_CENTER,
    LEFT_CENTER;

    /** True when the notch grows along the vertical axis (left / right edges). */
    val isVertical: Boolean
        get() = this == LEFT_CENTER || this == RIGHT_CENTER
}

/**
 * The physical silhouette of the notch. All three share the same expand/collapse
 * choreography; they differ in how the body meets the screen edge.
 */
@Serializable
enum class NotchStyle(
    /** Transparent gap between the anchored screen edge and the body (points). */
    val edgeGapDp: Int,
    /** Round every corner into a capsule, vs. keeping the edge side square. */
    val capsule: Boolean
) {
    /** Flush to the edge, inner corners rounded — the Dynamic-Island default. */
    DYNAMIC_ISLAND(0, false),

    /** Detached stadium-shaped pill floating a few points off the edge. */
    FLOATING_PILL(14, true),

    /** A slim, unobtrusive bar that expands into the full dock on hover. */
    DRAG_HANDLE(10, true)
}

/**
 * The body's material. Both entries resolve to statically cached brushes, so
 * neither adds per-frame shader work on the software renderer. [TRANSLUCENT]
 * is a deliberately texture-free glass: a tinted body that lets the desktop
 * bleed through the transparent window plus a smooth light sheen. A literal
 * blur of the pixels behind a borderless window is unreachable from Compose
 * without a native screen-capture loop.
 */
@Serializable
enum class NotchSurface {
    /** Fully opaque black — the default. */
    SOLID,

    /** Translucent, smooth glass. (Serialised as the former name "BLUR".) */
    @SerialName("BLUR")
    TRANSLUCENT
}

/**
 * Persistent configuration for the floating notch and its dock.
 *
 * The fisheye magnification radius is no longer user-tunable: it is derived
 * from [baseIconSizeDp] so the wave always tracks the cursor with icon-level
 * precision (see `fisheyeRadiusPx`).
 */
@Serializable
data class NotchConfig(
    val position: NotchPosition = NotchPosition.TOP_CENTER,
    val style: NotchStyle = NotchStyle.DYNAMIC_ISLAND,
    val surface: NotchSurface = NotchSurface.SOLID,
    val dockScaleFactor: Float = 1.35f,
    val baseIconSizeDp: Int = 38,
    val collapsedPillWidthDp: Int = 150,
    val collapsedPillHeightDp: Int = 30,
    val expandedNotchHeightDp: Int = 78,
    val autoStartOnBoot: Boolean = false,
    val alwaysOnTop: Boolean = true,
    val showTooltips: Boolean = true,
    val hideOnAppLaunch: Boolean = false,
    val collapseDelayMs: Long = 400L,
    val themeDark: Boolean = true
)
