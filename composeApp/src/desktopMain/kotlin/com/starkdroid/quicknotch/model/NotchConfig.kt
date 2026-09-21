package com.starkdroid.quicknotch.model

import kotlinx.serialization.Serializable

/**
 * The four screen-edge anchors the notch can occupy. Corners and free-form
 * dragging are intentionally unsupported: a fixed, centred anchor keeps the
 * overlay perfectly still while it expands and collapses.
 */
@Serializable
enum class NotchPosition(val displayName: String) {
    TOP_CENTER("Top Center"),
    RIGHT_CENTER("Right Center"),
    BOTTOM_CENTER("Bottom Center"),
    LEFT_CENTER("Left Center");

    /** True when the notch grows along the vertical axis (left / right edges). */
    val isVertical: Boolean
        get() = this == LEFT_CENTER || this == RIGHT_CENTER
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
