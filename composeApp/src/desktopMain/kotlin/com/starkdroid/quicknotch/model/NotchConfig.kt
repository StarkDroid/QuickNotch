package com.starkdroid.quicknotch.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
 * Per-widget enable flags, edited from the Widgets tab. Defaults keep the
 * historical behaviour (pinned apps always present); a media page additionally
 * requires a live session and a capsule-friendly [NotchStyle].
 */
@Immutable
@Serializable
data class WidgetsConfig(
    val pinnedAppsEnabled: Boolean = true,
    val nowPlayingEnabled: Boolean = true,
    /** Collapsed-pill indicator toggles; the expanded music page always shows volume + audio battery. */
    val showCollapsedVolume: Boolean = true,
    val showCollapsedBattery: Boolean = true,
    /** System (laptop) battery indicator; collapsed pill only, beside the clock. */
    val showCollapsedDeviceBattery: Boolean = true
)

/**
 * Date layouts for the idle clock shown in the collapsed pill (while nothing
 * is playing). The pattern is [java.time.format.DateTimeFormatter] syntax and
 * the dropdown renders a live example, so users pick the exact look they get.
 */
@Serializable
enum class ClockDateFormat(val pattern: String) {
    /** 23 Sep */
    DD_MMM("d MMM"),
    /** Sep 23 */
    MMM_DD("MMM d"),
    /** Wed, 23 Sep */
    EEE_DD_MMM("EEE, d MMM"),
    /** 23/09/26 */
    DD_MM_YY("dd/MM/yy"),
    /** 09/23/26 */
    MM_DD_YY("MM/dd/yy"),
    /** 2026-09-23 */
    YYYY_MM_DD("yyyy-MM-dd")
}

/**
 * Persistent configuration for the floating notch and its dock.
 *
 * The fisheye magnification radius is no longer user-tunable: it is derived
 * from [baseIconSizeDp] so the wave always tracks the cursor with icon-level
 * precision (see `fisheyeRadiusPx`).
 */
@Immutable
@Serializable
data class NotchConfig(
    val style: NotchStyle = NotchStyle.DYNAMIC_ISLAND,
    val surface: NotchSurface = NotchSurface.SOLID,
    val widgets: WidgetsConfig = WidgetsConfig(),
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
    // Idle clock in the collapsed pill: replaces the idle dots whenever either
    // piece is on. Old config files load fine (missing fields take these
    // defaults via kotlinx.serialization).
    val clockDate: Boolean = true,
    val clockTime: Boolean = true,
    val clock24Hour: Boolean = false,
    val clockDateFormat: ClockDateFormat = ClockDateFormat.DD_MMM,
    val themeDark: Boolean = true
)
