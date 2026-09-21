package com.starkdroid.quicknotch.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.starkdroid.quicknotch.model.NotchConfig
import com.starkdroid.quicknotch.model.NotchPosition
import java.awt.Insets
import java.awt.Point
import java.awt.Rectangle

/**
 * Static layout constants shared by the dock renderer and the window sizer so
 * the overlay window always matches the rendered content pixel-for-pixel.
 *
 * Along the dock axis every icon occupies `baseIconSize + 2 * IconPadding`.
 * [AlongSafety] absorbs the fisheye magnification of the end icons so they
 * never clip against the window edge, keeping the expanded footprint tight.
 */
internal object DockLayout {
    val IconPadding: Dp = 4.dp
    val SettingsButton: Dp = 28.dp
    val ContentPadding: Dp = 16.dp
    val AlongSafety: Dp = 8.dp
    val ExpandedDepthMargin: Dp = 6.dp
    val CollapsedMargin: Dp = 4.dp

    /** Width of a single icon slot along the dock axis. */
    fun iconSlot(baseIconSize: Dp): Dp = baseIconSize + IconPadding * 2
}

/**
 * The window footprint (in dp, already oriented for the active screen edge) for
 * both the collapsed pill and the expanded dock.
 */
@Immutable
data class NotchMetrics(
    val collapsed: DpSize,
    val expanded: DpSize
) {
    fun sizeFor(isExpanded: Boolean): DpSize = if (isExpanded) expanded else collapsed
}

/**
 * Derives the overlay window footprint from configuration and pin count. The
 * expanded "along" length hugs the actual dock content instead of a fixed
 * minimum, which removes the dead space that used to flank the icons.
 */
fun computeNotchMetrics(config: NotchConfig, favoriteCount: Int): NotchMetrics {
    val base = config.baseIconSizeDp.dp
    val count = favoriteCount.coerceAtLeast(0)

    val alongExpanded = DockLayout.ContentPadding +
        DockLayout.iconSlot(base) * count +
        DockLayout.SettingsButton +
        DockLayout.AlongSafety
    val alongCollapsed = config.collapsedPillWidthDp.dp + DockLayout.CollapsedMargin
    val depthExpanded = config.expandedNotchHeightDp.dp + DockLayout.ExpandedDepthMargin
    val depthCollapsed = config.collapsedPillHeightDp.dp + DockLayout.CollapsedMargin

    return if (config.position.isVertical) {
        NotchMetrics(
            collapsed = DpSize(depthCollapsed, alongCollapsed),
            expanded = DpSize(depthExpanded, alongExpanded)
        )
    } else {
        NotchMetrics(
            collapsed = DpSize(alongCollapsed, depthCollapsed),
            expanded = DpSize(alongExpanded, depthExpanded)
        )
    }
}

/**
 * Computes the top-left screen coordinate (in AWT points) that anchors the
 * overlay window to the chosen edge, centred along that edge. On macOS a top
 * notch hugs the very top of the panel ([hugMenuBar]) so it reads as a real
 * Dynamic Island rather than floating below the menu bar.
 */
fun overlayScreenLocation(
    position: NotchPosition,
    windowWidthPx: Int,
    windowHeightPx: Int,
    screen: Rectangle,
    insets: Insets,
    hugMenuBar: Boolean
): Point {
    val left = screen.x + insets.left
    val top = screen.y + if (hugMenuBar && position == NotchPosition.TOP_CENTER) 0 else insets.top
    val right = screen.x + screen.width - insets.right
    val bottom = screen.y + screen.height - insets.bottom

    val centerX = left + ((right - left) - windowWidthPx) / 2
    val centerY = top + ((bottom - top) - windowHeightPx) / 2

    return when (position) {
        NotchPosition.TOP_CENTER -> Point(centerX, top)
        NotchPosition.BOTTOM_CENTER -> Point(centerX, bottom - windowHeightPx)
        NotchPosition.LEFT_CENTER -> Point(left, centerY)
        NotchPosition.RIGHT_CENTER -> Point(right - windowWidthPx, centerY)
    }
}
