package com.starkdroid.quicknotch.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.starkdroid.quicknotch.model.NotchConfig
import com.starkdroid.quicknotch.model.NotchPosition
import com.starkdroid.quicknotch.model.NotchStyle
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

    /**
     * Extra transparent depth reserved inside the expanded footprint for left /
     * right docks, so a label bubble can float *outside* the island itself
     * instead of being squeezed into the notch's narrow cross-section.
     */
    val LabelBubbleReserve: Dp = 152.dp

    /** Gap between the notch's inner border and the floating bubble. */
    val BubbleGap: Dp = 6.dp

    /** Collapsed cross-axis thickness (dp) of the slim drag-handle style. */
    const val DragHandleHeightDp: Int = 8

    /** Width of a single icon slot along the dock axis. */
    fun iconSlot(baseIconSize: Dp): Dp = baseIconSize + IconPadding * 2
}

/**
 * The window footprint (in dp, already oriented for the active screen edge) for
 * both the collapsed pill and the expanded dock.
 */
@Immutable
data class NotchMetrics(
    /** Native window footprint when collapsed (the island; no bubble). */
    val collapsed: DpSize,
    /** Native window footprint when expanded (island + any bubble gutter). */
    val expanded: DpSize,
    /** Visible island when expanded — excludes the bubble gutter. */
    val island: DpSize,
    /** Visible island when collapsed. */
    val islandCollapsed: DpSize,
    /** Box the dock fills: island plus any bubble gutter. */
    val dockArea: DpSize,
    /** Distance from the dock box's outer edge to the island's inner border. */
    val bubbleInset: Dp = 0.dp,
    /**
     * How far the window is floated off the anchored screen edge (points). The
     * gap is applied to the window *position*, not the footprint, so the island
     * hugs the window edge and the gap stays ordinary click-through desktop.
     */
    val edgeGapDp: Int = 0
) {
    fun sizeFor(isExpanded: Boolean): DpSize = if (isExpanded) expanded else collapsed
    fun islandFor(isExpanded: Boolean): DpSize = if (isExpanded) island else islandCollapsed
}

/**
 * Derives the overlay window footprint from configuration and pin count. The
 * expanded "along" length hugs the actual dock content instead of a fixed
 * minimum, which removes the dead space that used to flank the icons. For
 * left / right docks with tooltips on, the cross axis also grows by
 * [DockLayout.LabelBubbleReserve] so the label can bubble out of the notch.
 */
fun computeNotchMetrics(config: NotchConfig, favoriteCount: Int): NotchMetrics {
    val base = config.baseIconSizeDp.dp
    val count = favoriteCount.coerceAtLeast(0)
    val vertical = config.position.isVertical

    val alongExpanded = DockLayout.ContentPadding +
        DockLayout.iconSlot(base) * count +
        DockLayout.SettingsButton +
        DockLayout.AlongSafety
    val alongCollapsed = config.collapsedPillWidthDp.dp + DockLayout.CollapsedMargin

    val islandDepth = config.expandedNotchHeightDp.dp + DockLayout.ExpandedDepthMargin
    val bubbleReserve =
        if (vertical && config.showTooltips) DockLayout.LabelBubbleReserve else 0.dp
    val dockDepth = islandDepth + bubbleReserve

    // A drag handle collapses to a slim, unobtrusive bar rather than the taller
    // pill, so it recedes into the edge when idle.
    val collapsedHeightDp =
        if (config.style == NotchStyle.DRAG_HANDLE) DockLayout.DragHandleHeightDp
        else config.collapsedPillHeightDp
    val islandCollapsedDepth = collapsedHeightDp.dp + DockLayout.CollapsedMargin

    val islandExpanded =
        if (vertical) DpSize(islandDepth, alongExpanded) else DpSize(alongExpanded, islandDepth)
    val islandCollapsed =
        if (vertical) DpSize(islandCollapsedDepth, alongCollapsed) else DpSize(alongCollapsed, islandCollapsedDepth)
    val dockArea =
        if (vertical) DpSize(dockDepth, alongExpanded) else DpSize(alongExpanded, dockDepth)

    return NotchMetrics(
        collapsed = islandCollapsed,
        expanded = dockArea,
        island = islandExpanded,
        islandCollapsed = islandCollapsed,
        dockArea = dockArea,
        bubbleInset = if (bubbleReserve > 0.dp) islandDepth else 0.dp,
        edgeGapDp = config.style.edgeGapDp
    )
}

/**
 * Computes the top-left screen coordinate (in AWT points) that anchors the
 * overlay window to the chosen edge, centred along that edge. On macOS a top
 * notch hugs the very top of the panel ([hugMenuBar]) so it reads as a real
 * Dynamic Island. [edgeGapPx] floats capsule styles off the edge by insetting
 * the anchored coordinate, leaving the gap as ordinary desktop (click-through)
 * rather than dead window area.
 */
fun overlayScreenLocation(
    position: NotchPosition,
    windowWidthPx: Int,
    windowHeightPx: Int,
    screen: Rectangle,
    insets: Insets,
    hugMenuBar: Boolean,
    edgeGapPx: Int = 0
): Point {
    val left = screen.x + insets.left
    val top = screen.y + if (hugMenuBar && position == NotchPosition.TOP_CENTER) 0 else insets.top
    val right = screen.x + screen.width - insets.right
    val bottom = screen.y + screen.height - insets.bottom

    val centerX = left + ((right - left) - windowWidthPx) / 2
    val centerY = top + ((bottom - top) - windowHeightPx) / 2

    return when (position) {
        NotchPosition.TOP_CENTER -> Point(centerX, top + edgeGapPx)
        NotchPosition.BOTTOM_CENTER -> Point(centerX, bottom - edgeGapPx - windowHeightPx)
        NotchPosition.LEFT_CENTER -> Point(left + edgeGapPx, centerY)
        NotchPosition.RIGHT_CENTER -> Point(right - edgeGapPx - windowWidthPx, centerY)
    }
}
