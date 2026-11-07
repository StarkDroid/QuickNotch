package com.starkdroid.quicknotch.model

import androidx.compose.runtime.Immutable
import com.starkdroid.quicknotch.weather.WeatherPlace
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
 * Island materials, built once as brushes. TRANSLUCENT is real OS blur
 * with a painted fallback; it serialises as BLUR.
 */
@Serializable
enum class NotchSurface {
    /** Fully opaque black — the default. */
    SOLID,

    /** Frosted glass: native backdrop blur plus a dark scrim. */
    @SerialName("BLUR")
    TRANSLUCENT,

    /** Glass tinted with the desktop's accent colour (macOS/Windows). */
    @SerialName("SYSTEM_ACCENT")
    SYSTEM_ACCENT
}

/**
 * Album-art tint per track, or white; a missing cover falls back to white.
 */
@Serializable
enum class WaveformTint {
    /** Dominant colour of the album artwork, pushed into a legible band. */
    ALBUM_ART,

    /** Plain white bars. */
    WHITE
}

/**
 * Which shape the collapsed pill's media visualiser takes. [WAVEFORM] is the
 * original centred capsules; [BARS] is sharp solid rectangles at the same
 * pitch, anchored to the bottom edge.
 */
@Serializable
enum class VisualiserStyle {
    /** Rounded capsules growing from their vertical centre. */
    WAVEFORM,

    /** Sharp solid bars rising from the bottom edge. */
    BARS
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
     * Equalizer bars in the title slot, replacing the edge progress strip
     * while on. The expanded page never draws them.
     */
    val showCollapsedWaveform: Boolean = true,
    /**
     * Google Calendar in the collapsed pill: a mail glyph badged with today's
     * event count, and the event title on a ticker while an event is live.
     * Requires a connection (see the Calendar widget in the Widgets tab).
     */
    val showCalendar: Boolean = false,
    /**
     * Weather in the pill and as a wheel page; IP-located, so no setup.
     */
    val showWeather: Boolean = true,
    /**
     * System page with CPU, GPU, memory, storage and network rings; the poller
     * runs only while this is on.
     */
    val showSystem: Boolean = true,
    /**
     * Manual place for the weather feed; null means IP location. Old configs
     * load null.
     */
    val weatherPlace: WeatherPlace? = null,
    /** Display temperatures in Fahrenheit instead of Celsius. */
    val weatherFahrenheit: Boolean = false,
    /**
     * Tints condition glyphs per condition instead of the text tint.
     */
    val waveformTint: WaveformTint = WaveformTint.ALBUM_ART,
    /**
     * Which visualiser shape the collapsed bars take. Defaults to the
     * original waveform, so existing configs render unchanged.
     */
    val visualiserStyle: VisualiserStyle = VisualiserStyle.WAVEFORM,
    /**
     * Trace the island's own outline with playback progress (top-left corner,
     * around the bottom, to the top-right) while collapsed. Shown only while
     * the visualiser is off; the expanded page carries its own progress.
     */
    val showProgressBorder: Boolean = true,
    /**
     * Drops the song title while the waveform is on, giving the bars the full
     * title frame.
     */
    val waveformHidesTitle: Boolean = false,
    /**
     * Replaces the collapsed pill with FPS, CPU, GPU, latency and download
     * while a game runs; detection runs only while on.
     */
    val showGameHud: Boolean = true,
    /**
     * Each HUD cell can be switched off; with all five off the pill returns.
     * FPS and GPU still drop when unavailable.
     */
    val gameMetrics: GameMetrics = GameMetrics()
)

/**
 * The five cells of the gaming HUD, each independently toggleable from the
 * System widget. Defaults keep every cell on, so existing configs load and
 * render exactly as they did.
 */
@Immutable
@Serializable
data class GameMetrics(
    val fps: Boolean = true,
    val cpu: Boolean = true,
    val gpu: Boolean = true,
    val ping: Boolean = true,
    val net: Boolean = true
) {
    /** At least one cell requested; false means there is nothing to draw. */
    val any: Boolean
        get() = fps || cpu || gpu || ping || net
}

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
 * Persistent configuration. The fisheye radius is derived from
 * baseIconSizeDp, not user-tunable.
 */
@Immutable
@Serializable
data class NotchConfig(
    val style: NotchStyle = NotchStyle.NOTCH,

    /**
     * False until onboarding finishes; the overlay does not exist before that.
     */
    val onboardingDone: Boolean = false,
    val surface: NotchSurface = NotchSurface.SOLID,
    /**
     * Drops the island body, rim and shadow while the gaming HUD shows; other
     * surfaces are untouched.
     */
    val transparentInGame: Boolean = false,
    /**
     * Backdrop blur intensity for the Blurred surface (macOS compositor blur
     * radius; on Windows it scales the acrylic tint instead, since DWM has no
     * radius knob). Old files load the default.
     */
    val blurRadius: Int = 22,
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
) {
    /**
     * True when the HUD is on and at least one metric is selected; every
     * consumer gates on this.
     */
    val gameHudEnabled: Boolean
        get() = widgets.showGameHud && widgets.gameMetrics.any
}
