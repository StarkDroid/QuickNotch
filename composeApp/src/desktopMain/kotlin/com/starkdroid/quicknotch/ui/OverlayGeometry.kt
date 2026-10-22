package com.starkdroid.quicknotch.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.starkdroid.quicknotch.media.AudioDeviceBattery
import com.starkdroid.quicknotch.media.SystemBattery
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

    /**
     * Collapsed-pill width slider bounds. The floor hugs a clock-only pill
     * instead of stranding ~15dp of dead air on each side of it; indicator
     * slots widen the footprint from whatever base is chosen, so a low base
     * never clips.
     */
    const val QuickViewWidthMinDp: Int = 110
    const val QuickViewWidthMaxDp: Int = 190

    /**
     * Degenerate-measurement guard for the content-hugging pill: the island
     * never contracts past this, so a zero-size first frame (or a future
     * empty branch) never collapses it into a sliver. Well under every real
     * branch's width, so it only ever binds on garbage input.
     */
    val HugMinWidth: Dp = 32.dp

    /**
     * Breathing room the hugging island adds around the measured row, total
     * across both ends (on top of the row's own insets) — enough that edge
     * components never kiss the pill border, little enough that no dead air
     * reads.
     */
    val HugPadding: Dp = 16.dp

    /** Width of a single icon slot along the dock axis. */
    fun iconSlot(baseIconSize: Dp): Dp = baseIconSize + IconPadding * 2

    /**
     * Along-axis footprint of the now-playing page when it is live. Sized so
     * the vinyl, metadata and transport controls fit without clipping inside
     * the expanded dock; the apps page is narrower, so the window widens to
     * this whenever the music page is active.
     */
    /** Five date tiles need their own room even when no dock or music page does. */
    val CalendarPageWidth = 348.dp
    val NowPlayingWidth: Dp = 356.dp
    /**
     * Current conditions + 7 day tiles + today's high/low, precip and UV need
     * the same room as the music page, so the window never clips the outlook.
     */
    val WeatherPageWidth: Dp = 356.dp
    /**
     * Six ring tiles (CPU, GPU, memory, storage, download, upload) each with a
     * value and a caption — the same room the music and weather pages claim, so
     * adding the System page never widens the window beyond its usual maximum.
     */
    val SystemPageWidth: Dp = 356.dp

    /**
     * Collapsed pill length once a live session is showing, wide enough for
     * art + marquee title + play/pause in the island's slim cross-section.
     */
    val LiveCollapsedWidth: Dp = 236.dp

    /**
     * Extra quickview length per indicator shown in the idle pill (22dp ring or
     * bell + 6dp spacing). The idle pill widens by this per indicator so
     * date/time + device battery + laptop battery + notifications all coexist
     * without clipping, mirroring how the live pill already grows to its rings.
     */
    val QuickViewIndicatorSlot: Dp = 28.dp
}

/**
 * The window footprint (in dp) for both the quickview and the expanded
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
 * [indicatorSlots] is the count of indicators actually rendered in the idle
 * quickview next to the clock (connected-device + laptop battery rings, plus the
 * notification bell); the collapsed footprint grows by one slot per indicator so
 * nothing is ever clipped by the configured pill width.
 */
fun computeNotchMetrics(
    config: NotchConfig,
    favoriteCount: Int,
    musicActive: Boolean = false,
    indicatorSlots: Int = 0,
    calendarActive: Boolean = false,
    weatherActive: Boolean = false,
    systemActive: Boolean = false
): NotchMetrics {
    val calendarActiveWidth = if (calendarActive) DockLayout.CalendarPageWidth else 0.dp
    val base = config.baseIconSizeDp.dp
    val count = favoriteCount.coerceAtLeast(0)

    val appsAlong =
        if (config.widgets.pinnedAppsEnabled) {
            DockLayout.ContentPadding +
                DockLayout.iconSlot(base) * count +
                DockLayout.AlongSafety
        } else 0.dp
    val musicAlong = if (musicActive) DockLayout.NowPlayingWidth else 0.dp
    val weatherAlong = if (weatherActive) DockLayout.WeatherPageWidth else 0.dp
    val systemAlong = if (systemActive) DockLayout.SystemPageWidth else 0.dp
    val alongExpanded = maxOf(appsAlong, musicAlong, calendarActiveWidth, weatherAlong, systemAlong)
    val alongCollapsed = maxOf(
        config.quickViewWidthDp.dp + DockLayout.CollapsedMargin +
            DockLayout.QuickViewIndicatorSlot * indicatorSlots.coerceAtLeast(0),
        if (musicActive) DockLayout.LiveCollapsedWidth else 0.dp
    )

    val islandDepth = config.expandedNotchHeightDp.dp + DockLayout.ExpandedDepthMargin

    // A drag handle collapses to a slim, unobtrusive bar rather than the taller
    // pill, so it recedes into the edge when idle.
    val collapsedHeightDp =
        if (config.style == NotchStyle.DRAG_HANDLE) DockLayout.DragHandleHeightDp
        else config.quickViewHeightDp
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
 * How many indicator slots the idle quickview actually renders right now: the
 * connected-device ring, the laptop ring (each gated by its own collapsed-only
 * toggle), the notification bell (shown whenever the unread count is
 * readable AND its toggle is on) and the weather chip (condition glyph + temperature,
 * two slots wide). The window sizer and the overlay content MUST
 * agree on this — it widens the collapsed footprint by one slot per indicator
 * so nothing clips against the window edge. Gated to the idle branches: the
 * drag-handle style renders no indicators and a live media pill grows to its
 * own (fixed) width.
 */
fun quickViewIndicatorSlots(
    config: NotchConfig,
    musicLive: Boolean,
    battery: AudioDeviceBattery?,
    deviceBattery: SystemBattery?,
    notificationCount: Int?,
    calendarEventCount: Int,
    weatherVisible: Boolean = false
): Int {
    if (musicLive || config.style == NotchStyle.DRAG_HANDLE) return 0
    val notificationSlots =
        if (notificationCount != null && config.widgets.showCollapsedNotifications) 1 else 0
    // The mail badge claims a slot only when it actually draws: a connected
    // calendar with an empty day shows nothing, so it must not widen the pill.
    val calendarSlots = if (config.widgets.showCalendar && calendarEventCount > 0) 1 else 0
    // The weather chip (glyph + temperature) is ~48dp wide, so it claims two
    // slots — but only while a forecast is loaded; an unlocated feed draws
    // nothing and must not widen the pill (pitfall 15, again).
    val weatherSlots = if (config.widgets.showWeather && weatherVisible) 2 else 0
    // Dots branch (no clock): only the bell and the mail badge sit beside the dots.
    if (!(config.clockDate || config.clockTime)) return notificationSlots + calendarSlots
    return notificationSlots + calendarSlots + weatherSlots + listOfNotNull(
        battery.takeIf { config.widgets.showCollapsedBattery },
        deviceBattery.takeIf { config.widgets.showCollapsedDeviceBattery }
    ).size
}

/**
 * Computes the top-left screen coordinate (in AWT points) for the overlay
 * window, hugging the top edge of the chosen screen and centred along it. On
 * macOS the notch hugs the very top of the panel ([hugMenuBar]) so it reads as
 * a real top-edge notch. [edgeGapPx] floats capsule styles off the edge by
 * insetting the anchored coordinate, leaving the gap as ordinary desktop
 * (click-through) rather than dead window area.
 */
fun overlayScreenLocation(
    windowWidthPx: Int,
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

/**
 * Rounds only the two corners facing away from the anchored edge; the corners
 * touching the screen edge stay square so the notch reads as flush with the
 * display, like a hardware notch.
 */
internal fun notchShape(expanded: Boolean, style: NotchStyle): CornerBasedShape {
    if (style.capsule) {
        // Round all four corners; a generous radius clamps to a stadium at the
        // slim collapsed height and a soft pill when expanded.
        val corner = if (expanded) 18.dp else 14.dp
        return RoundedCornerShape(corner)
    }
    val corner = if (expanded) 18.dp else 13.dp
    return RoundedCornerShape(bottomStart = corner, bottomEnd = corner)
}

/**
 * The island's bottom-corner radius as a value, for everything that must agree
 * with [notchShape] without being a shape: the outline progress ring and the
 * mac blur panel. (The Windows capture doesn't need it — its frame is cropped
 * to the island rect and clipped by the shape itself.)
 */
internal fun notchCornerRadius(expanded: Boolean, style: NotchStyle): Dp {
    if (style.capsule) return if (expanded) 18.dp else 14.dp
    return if (expanded) 18.dp else 13.dp
}
