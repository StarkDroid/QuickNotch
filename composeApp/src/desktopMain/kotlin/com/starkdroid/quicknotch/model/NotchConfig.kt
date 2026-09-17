package com.starkdroid.quicknotch.model

import kotlinx.serialization.Serializable

/**
 * Screen edge alignment positions for the Dynamic Island / Dock notch.
 */
@Serializable
enum class NotchPosition(val displayName: String) {
    TOP_CENTER("Top Center"),
    BOTTOM_CENTER("Bottom Center"),
    TOP_LEFT("Top Left"),
    TOP_RIGHT("Top Right"),
    LEFT_CENTER("Left Side"),
    RIGHT_CENTER("Right Side"),
    BOTTOM_LEFT("Bottom Left"),
    BOTTOM_RIGHT("Bottom Right")
}

/**
 * Configuration options for the QuickNotch floating overlay and dock.
 */
@Serializable
data class NotchConfig(
    val position: NotchPosition = NotchPosition.TOP_CENTER,
    val dockScaleFactor: Float = 1.35f,
    val dockMagnificationRadius: Float = 130f,
    val baseIconSizeDp: Int = 38,
    val collapsedPillWidthDp: Int = 160,
    val collapsedPillHeightDp: Int = 30,
    val expandedNotchHeightDp: Int = 78,
    val autoStartOnBoot: Boolean = false,
    val alwaysOnTop: Boolean = true,
    val launchOnSingleClick: Boolean = true,
    val showTooltips: Boolean = true,
    val hideOnAppLaunch: Boolean = false,
    val collapseDelayMs: Long = 400L,
    val themeDark: Boolean = true,
    val accentColorHex: String = "#6366F1"
)
