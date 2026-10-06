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
    /** Flush to the edge, inner corners rounded — the default. */
    @SerialName("DYNAMIC_ISLAND")
    NOTCH(0, false),

    /** Detached stadium-shaped pill floating a few points off the edge. */
    FLOATING_PILL(14, true),

    /** A slim, unobtrusive bar that expands into the full dock on hover. */
    DRAG_HANDLE(10, true)
}

/**
 * The body's material. All entries resolve to brushes built once and cached, so
 * none is rebuilt per frame while the island animates. [TRANSLUCENT] and
 * [SYSTEM_ACCENT] are deliberately texture-free glass: a tinted body that lets
 * the desktop bleed through the transparent window plus a smooth light sheen.
 * A literal blur of the pixels behind a borderless window is unreachable from
 * Compose without a native screen-capture loop.
 */
@Serializable
enum class NotchSurface {
    /** Fully opaque black — the default. */
    SOLID,

    /** Translucent, smooth glass. (Serialised as the former name "BLUR".) */
    @SerialName("BLUR")
    TRANSLUCENT,

    /** Glass tinted with the desktop's accent colour (macOS/Windows). */
    @SerialName("SYSTEM_ACCENT")
    SYSTEM_ACCENT
}

/**
 * What colour the collapsed pill's waveform bars take. [ALBUM_ART] derives a
 * tint from the current cover once per track (see `dominantArtTint`); [WHITE]
 * keeps them a constant white. A missing or unreadable cover falls back to
 * white rather than inventing a colour.
 */
@Serializable
enum class WaveformTint {
    /** Dominant colour of the album artwork, pushed into a legible band. */
    ALBUM_ART,

    /** Plain white bars. */
    WHITE
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
    /** System (laptop) battery indicator; quickview only, beside the clock. */
    val showCollapsedDeviceBattery: Boolean = true,
    /**
     * Unread-notification indicator (bell + red badge, quickview only). The
     * slot also drives the permission/test-notification helpers in settings.
     */
    val showCollapsedNotifications: Boolean = true,
    /**
     * Equalizer bars under the collapsed title while a track is live. Lives in
     * the title's own slot (no new indicator slot, so the footprint math is
     * untouched) and hides the edge progress strip for as long as it is on.
     * Never rendered in the expanded music page.
     */
    val showCollapsedWaveform: Boolean = true,
    /**
     * Google Calendar in the collapsed pill: a mail glyph badged with today's
     * event count, and the event title on a ticker while an event is live.
     * Requires a connection (see the Calendar widget in the Widgets tab).
     */
    val showCalendar: Boolean = false,
    /**
     * Weather in the collapsed pill (condition glyph + temperature, left of
     * the date) and as an expanded wheel page (today plus the 7-day outlook
     * with UV index and precipitation chances). Zero-config: the position is
     * IP-geolocated, so no account or setup is needed.
     */
    val showWeather: Boolean = true,
    /** Display temperatures in Fahrenheit instead of Celsius. */
    val weatherFahrenheit: Boolean = false,
    /**
     * Paint condition glyphs in per-condition colours (amber sun, blue rain,
     * …) instead of the monochrome text tint. Purely a render choice — the
     * same glyphs, only the tint changes.
     */
    /** Album-art-derived or constant white; see [WaveformTint]. */
    val waveformTint: WaveformTint = WaveformTint.ALBUM_ART,
    /**
     * While the waveform is on, drop the song name from the collapsed pill: the
     * live view then reads as disc + analyser + level rings only, and the bars
     * get the whole title frame (18dp instead of a 7dp band). Meaningless with
     * the waveform off, so the settings row appears only inside that section.
     */
    val waveformHidesTitle: Boolean = false
)

/**
 * Date layouts for the idle clock shown in the quickview (while nothing
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
    val style: NotchStyle = NotchStyle.NOTCH,

    /**
     * Set once the first-run onboarding has been walked through (or skipped
     * through): the notch overlay does not exist until then, because the user
     * is still configuring the pill it would draw. Defaults false, so a config
     * written before this field existed runs onboarding once on next launch.
     */
    val onboardingDone: Boolean = false,
    val surface: NotchSurface = NotchSurface.SOLID,
    /**
     * Real backdrop blur behind the island (NSVisualEffectView on macOS, DWM
     * acrylic on Windows) with a dark scrim for legibility, instead of the
     * painted translucent glass. Defaults off; old files load fine.
     */
    val blurBackground: Boolean = false,
    val widgets: WidgetsConfig = WidgetsConfig(),
    val dockScaleFactor: Float = 1.35f,
    val baseIconSizeDp: Int = 38,
    /** Quickview (collapsed pill) footprint; serialised under the old names so existing files load. */
    @SerialName("collapsedPillWidthDp")
    val quickViewWidthDp: Int = 136,
    @SerialName("collapsedPillHeightDp")
    val quickViewHeightDp: Int = 30,
    val expandedNotchHeightDp: Int = 78,
    val autoStartOnBoot: Boolean = false,
    val alwaysOnTop: Boolean = true,
    val showTooltips: Boolean = true,
    val hideOnAppLaunch: Boolean = false,
    val collapseDelayMs: Long = 400L,
    // Idle clock in the quickview: replaces the idle dots whenever either
    // piece is on. Old config files load fine (missing fields take these
    // defaults via kotlinx.serialization).
    val clockDate: Boolean = true,
    val clockTime: Boolean = true,
    val clock24Hour: Boolean = false,
    val clockDateFormat: ClockDateFormat = ClockDateFormat.DD_MMM,
    val themeDark: Boolean = true
)
