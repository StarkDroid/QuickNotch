package com.starkdroid.quicknotch.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.starkdroid.quicknotch.model.NotchConfig
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
    val ContentPadding: Dp = 16.dp
    val AlongSafety: Dp = 8.dp
    val ExpandedDepthMargin: Dp = 6.dp
    val CollapsedMargin: Dp = 4.dp

    /** Collapsed cross-axis thickness (dp) of the slim drag-handle style. */
    const val DragHandleHeightDp: Int = 8

    /** Width of a single icon slot along the dock axis. */
    fun iconSlot(baseIconSize: Dp): Dp = baseIconSize + IconPadding * 2

    /**
     * Along-axis footprint of the now-playing page when it is live. Sized so
     * the vinyl, metadata and transport controls fit without clipping inside
     * the expanded dock; the apps page is narrower, so the window widens to
     * this whenever the music page is active.
     */
    val NowPlayingWidth: Dp = 356.dp

    /**
     * Collapsed pill length once a live session is showing, wide enough for
     * art + marquee title + play/pause in the island's slim cross-section.
     */
    val LiveCollapsedWidth: Dp = 236.dp

    /**
     * Extra collapsed-pill length per additional indicator shown in the idle
     * clock (22dp ring + 6dp spacing). The idle pill widens by this per visible
     * ring so date/time + device battery + laptop battery all coexist without
     * clipping, mirroring how the live pill already grows to its rings.
     */
    val CollapsedClockIndicatorSlot: Dp = 28.dp
}

/**
 * The window footprint (in dp) for both the collapsed pill and the expanded
 * dock.
 */
@Immutable
data class NotchMetrics(
    /** Native window footprint when collapsed. */
    val collapsed: DpSize,
    /** Native window footprint when expanded. */
    val expanded: DpSize,
    /** Visible island when expanded. */
    val island: DpSize,
    /** Visible island when collapsed. */
    val islandCollapsed: DpSize,
    /** Box the dock fills (the island itself for a top-centred notch). */
    val dockArea: DpSize,
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
 * Derives the overlay window footprint from configuration, pin count and
 * whether the now-playing live activity is currently on. The expanded "along"
 * length hugs the actual widget content instead of a fixed minimum, which
 * removes the dead space that used to flank the icons. When the now-playing
 * page is live it is wider than the apps page, so the window widens to that
 * page — the apps page keeps its own (narrower) layout, centred.
 *
 * [collapsedRings] is the count of level-ring indicators actually rendered in
 * the idle pill next to the clock (connected-device + laptop battery); the
 * collapsed footprint grows by one indicator slot per ring so the rings are
 * never clipped by the configured pill width.
 */
fun computeNotchMetrics(
    config: NotchConfig,
    favoriteCount: Int,
    musicActive: Boolean = false,
    collapsedRings: Int = 0
): NotchMetrics {
    val base = config.baseIconSizeDp.dp
    val count = favoriteCount.coerceAtLeast(0)

    val appsAlong =
        if (config.widgets.pinnedAppsEnabled) {
            DockLayout.ContentPadding +
                DockLayout.iconSlot(base) * count +
                DockLayout.AlongSafety
        } else 0.dp
    val musicAlong = if (musicActive) DockLayout.NowPlayingWidth else 0.dp
    val alongExpanded = maxOf(appsAlong, musicAlong)
    val alongCollapsed = maxOf(
        config.collapsedPillWidthDp.dp + DockLayout.CollapsedMargin +
            DockLayout.CollapsedClockIndicatorSlot * collapsedRings.coerceAtLeast(0),
        if (musicActive) DockLayout.LiveCollapsedWidth else 0.dp
    )

    val islandDepth = config.expandedNotchHeightDp.dp + DockLayout.ExpandedDepthMargin

    // A drag handle collapses to a slim, unobtrusive bar rather than the taller
    // pill, so it recedes into the edge when idle.
    val collapsedHeightDp =
        if (config.style == NotchStyle.DRAG_HANDLE) DockLayout.DragHandleHeightDp
        else config.collapsedPillHeightDp
    val islandCollapsedDepth = collapsedHeightDp.dp + DockLayout.CollapsedMargin

    val islandExpanded = DpSize(alongExpanded, islandDepth)
    val islandCollapsed = DpSize(alongCollapsed, islandCollapsedDepth)
    val dockArea = DpSize(alongExpanded, islandDepth)

    return NotchMetrics(
        collapsed = islandCollapsed,
        expanded = dockArea,
        island = islandExpanded,
        islandCollapsed = islandCollapsed,
        dockArea = dockArea,
        edgeGapDp = config.style.edgeGapDp
    )
}

/**
 * Computes the top-left screen coordinate (in AWT points) for the overlay
 * window, hugging the top edge of the chosen screen and centred along it. On
 * macOS the notch hugs the very top of the panel ([hugMenuBar]) so it reads as
 * a real Dynamic Island. [edgeGapPx] floats capsule styles off the edge by
 * insetting the anchored coordinate, leaving the gap as ordinary desktop
 * (click-through) rather than dead window area.
 */
fun overlayScreenLocation(
    windowWidthPx: Int,
    windowHeightPx: Int,
    screen: Rectangle,
    insets: Insets,
    hugMenuBar: Boolean,
    edgeGapPx: Int = 0
): Point {
    val left = screen.x + insets.left
    val top = screen.y + if (hugMenuBar) 0 else insets.top
    val right = screen.x + screen.width - insets.right

    val centerX = left + ((right - left) - windowWidthPx) / 2

    return Point(centerX, top + edgeGapPx)
}
