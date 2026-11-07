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
 * Layout constants shared by the dock renderer and the window sizer, so
 * the window matches the content. AlongSafety absorbs end-icon fisheye.
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
     * Collapsed-pill width bounds; indicator slots widen from whatever base is
     * chosen.
     */
    const val QuickViewWidthMinDp: Int = 110
    const val QuickViewWidthMaxDp: Int = 190

    /**
     * Guard against a zero-size first frame; well under every real branch width.
     */
    val HugMinWidth: Dp = 32.dp

    /**
     * Breathing room around the measured row, total across both ends.
     */
    val HugPadding: Dp = 16.dp

    /** Width of a single icon slot along the dock axis. */
    fun iconSlot(baseIconSize: Dp): Dp = baseIconSize + IconPadding * 2

    /** Along-axis width of the live music page, wide enough for vinyl, metadata and transport. */
    /** Five date tiles need their own room when no dock or music page does. */
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
     * Fixed HUD width. The window reserves it always, so starting a game
     * never resizes the native window.
     */
    val GameHudWidth: Dp = 264.dp

    /**
     * Minimum collapsed depth while the HUD shows: value over caption needs a
     * touch more than the 30dp pill, and a user-shrunk quickview must not clip
     * the second line.
     */
    val GameHudHeight: Dp = 30.dp

    /**
     * Extra pill length per indicator: 22dp ring or bell plus 6dp spacing.
     */
    val QuickViewIndicatorSlot: Dp = 28.dp
}

/**
 * The window footprint (in dp) for both the quickview and the expanded
 * dock.
 */
@Immutable
data class NotchMetrics(
    /** Visible island when expanded. */
    val island: DpSize,
    /** Visible island when collapsed. */
    val islandCollapsed: DpSize,
    /** Box the dock fills (the island itself for a top-centred notch). */
    val dockArea: DpSize,
    /**
     * Window footprint, at least the widest branch (dock or HUD), so a game
     * never resizes the window.
     */
    val window: DpSize = dockArea,
    /**
     * How far the window is floated off the anchored screen edge (points). The
     * gap is applied to the window *position*, not the footprint, so the island
     * hugs the window edge and the gap stays ordinary click-through desktop.
     */
    val edgeGapDp: Int = 0
) {
    fun islandFor(isExpanded: Boolean): DpSize = if (isExpanded) island else islandCollapsed
}

/**
 * Derives the window footprint from config, pin count and whether music
 * is live. The expanded length hugs content, and indicatorSlots widens
 * the collapsed pill per rendered indicator.
 */
fun computeNotchMetrics(
    config: NotchConfig,
    favoriteCount: Int,
    musicActive: Boolean = false,
    indicatorSlots: Int = 0,
    calendarActive: Boolean = false,
    weatherActive: Boolean = false,
    systemActive: Boolean = false,
    gameActive: Boolean = false
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
        if (musicActive) DockLayout.LiveCollapsedWidth else 0.dp,
        if (gameActive) DockLayout.GameHudWidth else 0.dp
    )

    val islandDepth = config.expandedNotchHeightDp.dp + DockLayout.ExpandedDepthMargin

    // A drag handle collapses to a slim, unobtrusive bar rather than the taller
    // pill, so it recedes into the edge when idle.
    val collapsedHeightDp =
        if (config.style == NotchStyle.DRAG_HANDLE) DockLayout.DragHandleHeightDp
        else config.quickViewHeightDp
    val islandCollapsedDepth = collapsedHeightDp.dp + DockLayout.CollapsedMargin
    /** Collapsible widget card: header with icon, title, switch and chevron over an animated body. */
    val collapsedDepth = if (gameActive) DockLayout.GameHudHeight else islandCollapsedDepth

    val islandExpanded = DpSize(alongExpanded, islandDepth)
    val islandCollapsed = DpSize(alongCollapsed, collapsedDepth)
    val dockArea = DpSize(alongExpanded, islandDepth)
    // The window reserves the widest footprint the island can take, so a game
    // starting (HUD) or a track starting (live pill) is drawn growth only.
    val window = DpSize(maxOf(alongExpanded, DockLayout.GameHudWidth), islandDepth)

    return NotchMetrics(
        island = islandExpanded,
        islandCollapsed = islandCollapsed,
        dockArea = dockArea,
        window = window,
        edgeGapDp = config.style.edgeGapDp
    )
}

/**
 * Number of indicators the idle quickview renders; both the window sizer
 * and the content must use this or the right icon clips.
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
 * Top-centre position in AWT points. On macOS the notch hugs the true
 * top; edgeGapPx insets capsule styles.
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
 * Corner radius as a value for the outline ring and the Mac blur panel,
 * matching notchShape.
 */
internal fun notchCornerRadius(expanded: Boolean, style: NotchStyle): Dp {
    if (style.capsule) return if (expanded) 18.dp else 14.dp
    return if (expanded) 18.dp else 13.dp
}
