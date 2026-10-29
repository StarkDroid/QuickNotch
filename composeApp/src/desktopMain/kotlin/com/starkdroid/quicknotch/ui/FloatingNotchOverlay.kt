package com.starkdroid.quicknotch.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.starkdroid.quicknotch.calendar.CalendarState
import com.starkdroid.quicknotch.calendar.GoogleCalendar
import com.starkdroid.quicknotch.calendar.calendarUpcomingCount
import com.starkdroid.quicknotch.datasource.AppLauncher
import com.starkdroid.quicknotch.datasource.BlurRadiusMax
import com.starkdroid.quicknotch.datasource.BlurRadiusMin
import com.starkdroid.quicknotch.datasource.HostPlatform
import com.starkdroid.quicknotch.datasource.MacAppKit
import com.starkdroid.quicknotch.datasource.MacContentBlur
import com.starkdroid.quicknotch.datasource.SystemAccent
import com.starkdroid.quicknotch.datasource.SystemMonitor
import com.starkdroid.quicknotch.datasource.WindowsBackdropBlur
import com.starkdroid.quicknotch.media.AudioBattery
import com.starkdroid.quicknotch.media.AudioDeviceBattery
import com.starkdroid.quicknotch.media.MediaController
import com.starkdroid.quicknotch.media.NotificationCount
import com.starkdroid.quicknotch.media.SystemBattery
import com.starkdroid.quicknotch.model.FavoritesRepository
import com.starkdroid.quicknotch.model.NotchStyle
import com.starkdroid.quicknotch.model.NotchSurface
import com.starkdroid.quicknotch.model.WaveformTint
import com.starkdroid.quicknotch.system.FpsProbe
import com.starkdroid.quicknotch.system.GameDetector
import com.starkdroid.quicknotch.system.SystemMetrics
import com.starkdroid.quicknotch.weather.Weather
import com.starkdroid.quicknotch.weather.WeatherState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.awt.Point
import kotlin.math.roundToInt

/**
 * THE overlay: one composable that hosts the island, the quickview pill, the
 * widget pages (music + pinned apps) and the collapse/expand choreography.
 *
 * The pieces live in focused sibling files — [QuickView] (collapsed pill),
 * the widget pages/dock (`DockContent.kt`), the media widgets
 * (`MediaWidgets.kt`), the shared-element morph (`NowPlayingMorph.kt`), the
 * hover machinery (`OverlayHover.kt`) and the motion constants
 * (`NotchMotion.kt`). This file only owns state and choreography.
 *
 * The native window keeps one fixed (expanded) footprint for its whole life
 * (see `NotchOverlayWindow` in main.kt), so opening/collapsing never resizes a
 * transparent window — expanding is purely drawn growth inside it (pitfall 17).
 */
/**
 * Collapsed island width: hug the measured wrapping content when a hugging
 * branch is showing (falling back to the configured width until the first
 * measure lands), the configured width everywhere else. Bounded below against
 * degenerate measures and above by the native window, so the island can never
 * outgrow its window or collapse into a sliver.
 */
private fun hugCollapsedWidth(
    hugVisible: Boolean,
    hugWidth: Dp?,
    formula: DpSize,
    window: DpSize
): Dp {
    if (!hugVisible) return formula.width
    return hugWidth?.let { (it + DockLayout.HugPadding).coerceIn(DockLayout.HugMinWidth, window.width) }
        ?: formula.width
}

@OptIn(ExperimentalComposeUiApi::class, ExperimentalSharedTransitionApi::class)
@Composable
internal fun FloatingNotchOverlay(
    repository: FavoritesRepository,
    media: MediaController,
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    /** System master-volume percentage (0-100); null hides the volume chip. */
    volumePercent: Int? = null,
    /** Device mute flag; null when unreadable (no mute affordance). */
    volumeMuted: Boolean? = null,
    /** Connected audio device battery (0-100); null hides the battery chip. */
    battery: AudioDeviceBattery? = null,
    /** The machine's own (laptop) battery; null hides the quickview device ring. */
    deviceBattery: SystemBattery? = null,
    /** Shared widget-pager state (also driven by the Windows AWT wheel listener). */
    pager: OverlayPager,
    /**
     * Reports the overlay's current touch-target rectangle in window-client
     * pixels (the hover box = island + glow), or null until it has been sized.
     * Lets the native window keep its transparent margin click-through, so the
     * fixed footprint never swallows clicks on the app/desktop around the pill
     * (see `WindowPassthrough`).
     */
    onTouchTargetChange: (java.awt.Rectangle?) -> Unit = {},
    /**
     * The host AWT window (undecorated transparent overlay). Used on macOS to
     * reconcile the screen-origin drift caused by the above-menu-bar placement.
     */
    awtWindow: java.awt.Window? = null
) {
    val config by repository.config.collectAsState()

    // Two reads for the calendar, both rare: a connect/refresh emits a handful
    // of times a day and the ticker only changes on an event boundary. Collected
    // here because the pill is the only thing that draws them.
    val calendarState by GoogleCalendar.state.collectAsState()
    val calendarRefreshing by GoogleCalendar.refreshing.collectAsState()
    val calendarTick by GoogleCalendar.minuteTick.collectAsState()
    val calendarTicker by GoogleCalendar.ticker.collectAsState()
    val calendarEventCount =
        if (config.widgets.showCalendar) calendarUpcomingCount(calendarState, calendarTick) else 0
    // Weather for the quickview chip and the expanded outlook page. Collected
    // here because the pill and the pager are the only drawers; the controller
    // polls on its own half-hourly loop, so observing never triggers a fetch.
    val weatherState by Weather.state.collectAsState()
    val weatherFetching by Weather.fetching.collectAsState()
    val weatherData = (weatherState as? WeatherState.Ready)?.data
    // The page exists while a forecast is showing or arriving — never as an
    // empty stop: a failed feed with no cache stays off the wheel entirely,
    // the same rule the calendar page follows.
    val weatherReady = config.widgets.showWeather &&
        (weatherData != null || weatherFetching || weatherState is WeatherState.Loading)
    // The System page joins the wheel only once there is a reading to draw: a
    // widget that is switched off (or whose very first probe failed) must never
    // become a dead stop between the dock and the outlook.
    val systemState by SystemMetrics.state.collectAsState()
    val systemReady = config.widgets.showSystem && systemState != null
    // Gaming: the detector publishes the fullscreen app it found (or null) and
    // the HUD takes the pill's place while it lasts. FPS comes from a separate
    // probe because a PresentMon capture takes seconds — it must never delay a
    // system sample.
    val game by GameDetector.game.collectAsState()
    val fps by FpsProbe.fps.collectAsState()
    val gameHudVisible = game != null && config.widgets.showGameHud
    val favorites by repository.favorites.collectAsState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    // AWT's `locationOnScreen` goes stale as soon as the macOS window policy
    // parks the island above the menu bar with direct Cocoa moves (AWT's y=0
    // sits at the bottom of the menu bar and never learns about the real frame).
    // Compose's `localToScreen` inherits that stale number, so screen-space
    // collapse gating would find the island ~30pt below where it actually is.
    // Track the live drift off-frame and shift the hover box by it; null =
    // no drift known (non-mac, bridge missing) → gate on AWT's coordinates.
    //
    // The native read must NOT run on this composable's dispatcher: it
    // dispatches synchronously onto AppKit's main queue, and a synchronous
    // cross-queue hop from the UI thread is exactly the EDT↔AppKit ping-pong
    // that can deadlock both when AWT is mid-upcall — the app-wide beachball.
    // Polling on Dispatchers.IO keeps the hop on a worker while the resulting
    // state write re-enters composition thread-safely.
    var screenOriginDrift by remember { mutableStateOf<Point?>(null) }
    LaunchedEffect(awtWindow) {
        if (awtWindow != null && HostPlatform.isMac) {
            while (true) {
                screenOriginDrift = withContext(Dispatchers.IO) {
                    runCatching { MacAppKit.screenOriginDelta(awtWindow!!) }.getOrNull()
                }
                delay(350)
            }
        }
    }

    val iconBitmaps = remember { mutableStateMapOf<String, ImageBitmap>() }
    LoadFavoriteIcons(favorites, iconBitmaps)

    val liveSession by media.session.collectAsState()
    val musicLive = isMusicLive(config, liveSession)

    // Unread notification count for the quickview's bell + badge; null (the OS
    // store unreadable, e.g. without Full Disk Access) hides the indicator.
    val notificationCount by NotificationCount.count.collectAsState()

    // The System Accent surface tints itself to the desktop accent. While that
    // surface is chosen, re-read the OS accent every few seconds so changing
    // the macOS/Windows accent recolours the notch in place (deduped by the
    // StateFlow, so only an actual colour change triggers a rebuild).
    val accent by SystemAccent.accent.collectAsState()
    LaunchedEffect(config.surface) {
        if (config.surface != NotchSurface.SYSTEM_ACCENT) return@LaunchedEffect
        while (true) {
            withContext(Dispatchers.IO) { SystemAccent.refresh() }
            delay(SystemAccentPollMs)
        }
    }

    var showDock by pager.dockVisible

    // Re-probe connectivity when the dock OPENS, so the battery chip never shows
    // a stale read. Deliberately not keyed on musicLive: on macOS a probe is a
    // `system_profiler` launch (~150ms of system-wide CPU) and firing one on
    // every track start was a visible source of machine-wide stutter. The 10s
    // poll plus the event-driven connect/disconnect bridge already keep the ring
    // correct without it (pitfall 31).
    LaunchedEffect(isExpanded) {
        if (isExpanded) {
            AudioBattery.poke()
            SystemBattery.poke()
            SystemMetrics.poke()
        }
    }

    // Collapse is debounced so a cursor briefly clipping the edge does not
    // immediately retract the dock.
    var collapseJob by remember { mutableStateOf<Job?>(null) }

    // Screen-anchored footprint of the hover zone (island + glow), kept alive by
    // the hover Box below. It only fires hover Enter/Exit; the native gate and
    // the collapse hysteresis both key off the tighter island footprint, so the
    // outer glow band stays click-through for whatever sits underneath.
    var hoverCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    // The DRAWN island's footprint. This — not the wider hover box — is the
    // native touch target: the fixed (expanded) window keeps a transparent
    // margin around the collapsed pill for its whole life (pitfall 17: resizing
    // a transparent window on macOS flashes), and the margin must stay
    // click-through right up to the pill's edge so it never blocks the UI
    // underneath (taskbar/menu-bar items sitting next to the notch).
    //
    // Published straight from `onGloballyPositioned` on the island itself —
    // NOT from an effect keyed on the state: the coordinates object is a
    // stable instance across layout passes, so a keyed effect would run
    // exactly once and freeze the touch target at whatever the island's first
    // size was (the collapsed pill) — leaving everything outside it
    // click-through once the notch expanded. The callback itself fires on
    // every position/size change, which is precisely when the target (and the
    // Windows blur capture's island rect below) must be republished.
    var islandCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    // Installed before the touch-target publish below so that one
    // `onGloballyPositioned` feed can drive both the native click-through
    // region and (on Windows) the backdrop-blur capture daemon.
    val windowsBlur = remember(awtWindow) { WindowsBackdropBlur.install(awtWindow) }
    val macBlur = remember(awtWindow) { MacContentBlur(awtWindow) }
    DisposableEffect(awtWindow) {
        onDispose { windowsBlur?.close() }
    }
    val calendarConnected = calendarState is CalendarState.Connected
    // The page rides the wheel only while the widget is switched on *and* a
    // connection backs it — the same rule the pill's calendar indicator and
    // the native sizer follow (a toggled-off calendar must neither draw nor
    // reserve a page).
    val calendarReady = config.widgets.showCalendar && calendarConnected
    val pages = remember(
        config.widgets.pinnedAppsEnabled,
        musicLive,
        calendarReady,
        weatherReady,
        systemReady
    ) {
        buildList {
            if (musicLive) add(WidgetPage.Music)
            if (config.widgets.pinnedAppsEnabled) add(WidgetPage.Apps)
            // System sits between the dock and the outlook: it is a "glance at
            // the machine" page, not part of the day (weather/calendar) pair.
            if (systemReady) add(WidgetPage.System)
            if (weatherReady) add(WidgetPage.Weather)
            // Last so the wheel reaches it after the dock, and only when there
            // is a connection behind it -- an empty page would be a dead stop.
            if (calendarReady) add(WidgetPage.Calendar)
        }
    }

    fun setHovered(hovered: Boolean) {
        collapseJob?.cancel()
        if (hovered) {
            // A ticker on the pill is a notification, not a status: expanding
            // the notch over it consumes the notification -- it will not come
            // back for this event after the next collapse -- and the expansion
            // lands directly on the calendar page the user was just teased
            // with. Collapsing snaps the pager back to page 0 as usual, so the
            // next expansion opens wherever the notch always opens.
            if (calendarTicker != null) {
                GoogleCalendar.dismissTicker()
                pages.indexOf(WidgetPage.Calendar).takeIf { it >= 0 }?.let { index ->
                    pager.pageIndex.value = index
                }
            }
            onExpandedChange(true)
        } else {
            collapseJob = scope.launch {
                // Debounce the release, then watch the *real* cursor until it
                // has truly left the notch. Transparent overlay windows re-state
                // their hover region while resizing under a stationary cursor,
                // so an Exit event alone is not trustworthy while content keeps
                // changing under the pointer (e.g. a playing track ticking its
                // progress) — collapsing straight away makes the notch flap
                // open/closed. The gate below verifies the true pointer position
                // and only ever suppresses a collapse, never forces an expand.
                //
                // It must *re-poll* rather than decide once: a single suppressed
                // check is enough to wedge the notch open. During a fast expand
                // the native window resizes and the hover footprint node can be
                // transiently null or detached at the moment the check runs, so
                // the first gate reprieve is often a stale one — and with a
                // one-shot check nothing ever re-evaluates it (the pointer is
                // already long gone, so no new Exit arrives). Keep watching until
                // the cursor is confirmed out.
                while (true) {
                    delay(config.collapseDelayMs)
                    // The gate uses the DRAWN island's footprint — the same
                    // rectangle the native click-through keeps interactive — so
                    // the collapse hysteresis can never disagree with the
                    // native gate (a cursor parked in the click-through glow
                    // band used to hold the notch open while the window there
                    // was already ignoring the mouse).
                    val coords = islandCoordinates
                    when {
                        // Unknown or still-detached footprint (window mid-resize):
                        // keep the notch open without flapping, but keep polling —
                        // this path no longer wedges.
                        coords == null || !coords.isAttached -> continue
                        // Cursor confirmed out of the footprint: retract.
                        !cursorOverNotch(coords, screenOriginDrift) -> {
                            onExpandedChange(false)
                            break
                        }
                    }
                }
            }
        }
    }

    // Expanded content is a set of always-composed, always-expanded widget
    // pages; the wheel moves between them. Page switching is owned by a shared
    // [OverlayPager]: the overlay renders
    // from it, and (on Windows) an AWT-level wheel listener in `Main` drives it
    // from outside the Compose pointer pipeline. One wheel notch = one page.
    LaunchedEffect(pages.size) {
        pager.pageCount = pages.size
        if (pager.pageIndex.value >= pages.size) {
            pager.pageIndex.value = (pages.size - 1).coerceAtLeast(0)
        }
        pager.resetAccum()
    }
    val pagePosition = animateFloatAsState(pager.pageIndex.value.toFloat(), pageSpring(), label = "pagePosition")

    // Wheel events reach the Compose pointer pipeline on macOS (trackpad and
    // mouse alike), and the dock box fills the expanded window, so this one
    // handler covers the whole notch there. Windows receives no scroll through
    // that pipeline on its unfocused overlay window — AWT does — so the Compose
    // handler is skipped on Windows to avoid every notch being counted twice.
    //
    // Compose reports scroll in `preciseWheelRotation` units (one wheel notch =
    // 1.0 rotation), not pixels, and the pager accumulator is in the same pixel
    // space the Windows AWT listener feeds it with rotation × stepPx. So scale
    // the same way here: without it, a whole trackpad swipe lands far below the
    // step threshold and several swipes are needed for one page flip. Sum both
    // axes because Shift+scroll on macOS is reported on the x axis, which keeps
    // this path insensitive to Shift exactly like the Windows listener is.
    val composeScrollModifier =
        if (HostPlatform.isWindows) Modifier
        else Modifier.onPointerEvent(PointerEventType.Scroll) { event ->
            val rotations = event.changes.fold(0f) { acc, change ->
                acc + change.scrollDelta.x + change.scrollDelta.y
            }
            pager.consumeWheel(rotations * pager.stepPx)
        }

    // Indicators shown beside the idle clock also widen the quickview (one
    // indicator slot each), so date/time + device + laptop battery + the
    // notification bell coexist instead of clipping. Derived through the same
    // helper the native window sizer in main.kt uses, so both ever agree on
    // the footprint. Gated to the idle branches: the drag-handle style renders
    // no indicators and a live media pill grows to its own fixed width.
    val indicatorSlots = quickViewIndicatorSlots(
        config, musicLive, battery, deviceBattery, notificationCount, calendarEventCount,
        weatherVisible = weatherData != null
    )

    val metrics = remember(
        config,
        favorites.size,
        musicLive,
        indicatorSlots,
        calendarReady,
        weatherReady,
        systemReady,
        gameHudVisible
    ) {
        computeNotchMetrics(
            config,
            favorites.size,
            musicLive,
            indicatorSlots,
            calendarReady,
            weatherReady,
            systemReady,
            gameHudVisible
        )
    }
    // Cached per surface choice *and* per accent value: the brushes are built
    // once and the accented style is rebuilt only when the OS accent changes,
    // so nothing here allocates while the island animates.
    // (windowsBlur / macBlur are installed near the touch-target publish
    // above — the Windows capture daemon tracks the island rect from the same
    // feed.)
    //
    // The Blurred surface means real backdrop blur wherever the bridge
    // answers — a compositor panel below the window on macOS, a captured +
    // GPU/CPU-blurred strip drawn inside the window on Windows — falling back
    // to painted glass where it does not. Only the scrim differs between the
    // two, so it rides the same remember key as the surface.

    // The dock reveal is a layer animation (fade + scale) over the always
    // expanded-size dock. Driving it through [MutableTransitionState] rather
    // than raw alpha/scale floats lets the same visibility transition feed the
    // shared-element morph (the disc/title fly between pill and detail) and the
    // island's "hold expanded until the dock is done" gate below.
    val dockState = remember { MutableTransitionState(false) }
    dockState.targetState = showDock

    // The island keeps the dock as its *sibling*, so the fading dock is not
    // clipped by the shrinking pill; the pill holds its expanded size until the
    // dock fade is done, then contracts. Everything sits top-centred inside the
    // fixed (expanded) native window, so the collapsed pill nests into the
    // window's top edge and needs no gutter.
    val islandExpanded = isExpanded || dockState.currentState || dockState.targetState

    // "Transparent in games" applies to the collapsed gaming HUD only: the
    // expanded dock keeps whatever material the user picked, so opening it over
    // a game still reads as a panel rather than floating numbers. Deliberately
    // not a NotchSurface entry — the three user-picked materials keep their
    // meaning everywhere else.
    val gameTransparent = gameHudVisible && config.transparentInGame && !islandExpanded
    val blurNative = windowsBlur != null || macBlur.available
    // No native blur behind a transparent island: the panel/frost would paint
    // the body we just removed. Both carriers are switched off, not merely
    // hidden, so the macOS helper panel stops compositing too.
    val blurActive = !gameTransparent && config.surface == NotchSurface.TRANSLUCENT && blurNative
    val blurIntensity = config.blurRadius.coerceIn(BlurRadiusMin, BlurRadiusMax)
    // Windows paints its frost INSIDE the window (the capture leaf below), so
    // the painted body must get out of the way while frost is live; macOS
    // keeps the painted scrim over the native panel. Same rule, two carriers.
    val frostActive = blurActive && windowsBlur != null
    val surface = remember(config.surface, accent, blurActive, blurIntensity, gameTransparent) {
        IslandSurface.styleFor(config.surface, accent, blurActive, blurIntensity, gameTransparent)
    }

    // Content-hugging collapsed pill: the wrapping branches (idle clock, idle
    // dots) report their measured width, and the island shrinks to exactly
    // that — the row already carries its own 2dp side insets, so the pill ends
    // up with ~4px of black on each end and no slider dead air. Fill branches
    // (live media, ticker, gaming HUD) keep the configured width; the native
    // window never moves either way, so hugging is pure drawn growth like every
    // expansion.
    var hugWidth by remember { mutableStateOf<Dp?>(null) }
    val hugVisible = !musicLive && calendarTicker?.summary == null && !gameHudVisible &&
        config.style != NotchStyle.DRAG_HANDLE
    val formulaCollapsed = metrics.islandFor(false)
    val collapsedWidth = hugCollapsedWidth(
        hugVisible = hugVisible,
        hugWidth = hugWidth,
        formula = formulaCollapsed,
        window = metrics.window
    )
    val targetSize = if (islandExpanded) metrics.islandFor(true)
    else formulaCollapsed.copy(width = collapsedWidth)
    // Kept as State (not `by`): every composition-phase read of these values
    // recomposes this whole overlay scope on each spring frame, and the
    // measured cost of that during open/close was the app's dominant jank
    // source. The animated values are read only in layout/snapshot phases
    // (`animatedSize` modifier, snapshotFlow below), so a spring tick now
    // re-measures the island instead of re-running this body.
    val animatedWidth = animateDpAsState(targetSize.width, notchSizeSpring(), label = "notchWidth")
    val animatedHeight = animateDpAsState(targetSize.height, notchSizeSpring(), label = "notchHeight")
    val shape = notchShape(islandExpanded, config.style)

    // Publishes the island's bounds in window-client pixels for the native
    // click-through (see WindowPassthrough); null (not measured yet) means
    // "whole window interactive", the fail-soft default. Lives here — below
    // the shape inputs — because the same feed also drives the Windows
    // backdrop-blur capture: the daemon strips the screen behind exactly this
    // rect every frame, so the frost tracks the pill's stadium and the dock's
    // rounded box through the same animated layout.
    //
    // `localToWindow` already returns PHYSICAL pixels (Compose px = dp ×
    // density), so no further scaling happens here — multiplying again used to
    // double-scale the touch target on every display above 100%, turning the
    // transparent margin into an invisible click-swallowing band.
    fun publishTouchTarget(coords: LayoutCoordinates?) {
        if (coords == null || !coords.isAttached) {
            onTouchTargetChange(null)
            windowsBlur?.updateIsland(null)
            return
        }
        val windowOffset = coords.localToWindow(Offset.Zero)
        val sizePx = coords.size
        val rect = java.awt.Rectangle(
            windowOffset.x.roundToInt(),
            windowOffset.y.roundToInt(),
            sizePx.width,
            sizePx.height
        )
        onTouchTargetChange(rect)
        windowsBlur?.updateIsland(rect)
    }

    // Playback progress straddles the island's own outline — half over the
    // island, half over the transparent margin — filling the notch's border
    // with the playhead instead of reading as a bar. Shown while collapsed
    // (the expanded page carries its own seekable progress), beside the
    // visualiser or without it. Hoisted here because the outline's outer half
    // needs a transparent margin around the island (see the padded wrapper
    // below), and that margin must exist before the island is laid out.
    val liveNow = if (musicLive) liveSession else null
    // Outline progress needs a live track, a collapsed pill and its own
    // toggle on. It rides the island's edge, so it coexists with the
    // visualiser (bars inside, ring outside) — the two toggles are
    // independent. The gaming HUD replaces the pill's content, so the
    // playback ring has nothing to belong to while it shows.
    val showOutline = !isExpanded && liveNow != null && config.widgets.showProgressBorder &&
        !gameHudVisible
    val outlinePad = if (showOutline) OutlinePad else 0.dp
    // Mirrors notchShape(): capsule rounds every corner, the flush style only
    // the two facing the screen.
    val outlineRadius = notchCornerRadius(islandExpanded, config.style)

    // The native blur follows the surface, not a toggle: picking Blurred
    // enables it, any other surface clears it. The mac panel tracks the island
    // while it animates; Windows runs its capture daemon against the same
    // island feed (the frost leaf below draws what it strips). Both fail soft
    // to the painted surfaces.
    LaunchedEffect(blurActive, blurIntensity, islandExpanded, config.style) {
        windowsBlur?.setBlur(blurActive, blurIntensity)
        if (blurActive) {
            // Per-frame geometry without per-frame composition: the effect's
            // keys stay state-stable, and a snapshotFlow inside relays each
            // spring tick to the helper. Keying the effect on the animated
            // sizes (the old shape) restarted the coroutine — and recomposed
            // the whole overlay — sixty times a second mid-animation.
            snapshotFlow { DpSize(animatedWidth.value, animatedHeight.value) }
                .collect { size ->
                    macBlur.apply(true, size, islandExpanded, config.style, blurIntensity)
                }
        } else {
            macBlur.apply(false, DpSize.Zero, islandExpanded, config.style, blurIntensity)
        }
    }

    // Open: hold the dock hidden until the size spring has actually settled,
    // then fade it in. Close: fade the dock out and let the island spring back.
    // Waiting on the spring itself (with a timeout) rather than fixed delays
    // guarantees the dock never appears mid-measure and the notch never gets
    // stuck open-but-empty. The native window stays at its one fixed footprint
    // the entire time (see NotchOverlayWindow) — "expanding" is purely drawn
    // growth inside that fixed window, so the overlay's native surface is never
    // resized and there is no resize flash on any platform.
    LaunchedEffect(isExpanded) {
        if (isExpanded) {
            val island = metrics.islandFor(true)
            withTimeoutOrNull(IslandSettleTimeoutMs) {
                snapshotFlow {
                    animatedWidth.value >= island.width - 1.dp &&
                        animatedHeight.value >= island.height - 1.dp
                }.first { settled -> settled }
            }
            if (isExpanded) showDock = true
        } else {
            // Snap the pager back to its leading page before the dock fades so
            // the next expansion re-opens the live activity (music while a
            // track is playing, otherwise the apps dock) instead of wherever
            // the wheel was left — and so the shared-element morph reads the
            // now-playing page's real bounds as the pill returns.
            pager.pageIndex.value = 0
            showDock = false
            withTimeoutOrNull(IslandSettleTimeoutMs) {
                snapshotFlow {
                    animatedWidth.value <= collapsedWidth + 1.dp &&
                        animatedHeight.value <= formulaCollapsed.height + 1.dp
                }.first { settled -> settled }
            }
        }
    }

    SharedTransitionLayout(modifier = modifier.fillMaxSize()) {
        // Shared-element scope for the now-playing morph: the quickview and
        // the detailed page render matching disc/title keys beneath it, so
        // Compose can fly each element between the two surfaces.
        val sharedScope = this
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            // Island background + quickview: the only thing that actually
            // resizes. The dock is a sibling, not a child, so its layout is
            // stable.
            //
            // The outer wrapper exists for the progress outline below: the
            // ring straddles the island's edge (half over the island, half
            // over the desktop, like the macOS border), and its outer half
            // needs a transparent margin to paint into — the island itself is
            // clipped to its shape, so anything outside would be cut. The pad
            // is zero whenever the outline is off, so every other mode lays
            // out exactly as before; the touch target still feeds from the
            // inner island's coordinates, never the margin.
            Box(
                modifier = Modifier.animatedIslandSize(animatedWidth, animatedHeight) { w, h ->
                    DpSize(w + outlinePad * 2, h + outlinePad)
                },
                contentAlignment = Alignment.TopCenter
            ) {
            Box(
                modifier = Modifier
                    .animatedIslandSize(animatedWidth, animatedHeight)
                    .onGloballyPositioned { coords ->
                        islandCoordinates = coords
                        publishTouchTarget(coords)
                    }
                    .clip(shape)
                    // Windows frost replaces the painted body: the captured
                    // backdrop is drawn by the leaf below and the scrim rides
                    // above it inside the same leaf, so the modifier-chain
                    // background would only double-tint. macOS and painted
                    // fallbacks keep the chain background exactly as before.
                    .then(
                        if (frostActive) {
                            Modifier
                        } else {
                            Modifier.islandSurface(
                                config.surface,
                                accent,
                                blurActive,
                                blurIntensity,
                                gameTransparent
                            )
                        }
                    )
                    .border(surface.borderWidth.dp, surface.border, shape),
                contentAlignment = Alignment.Center
            ) {
                // Windows only (frostActive ⇒ windowsBlur != null): the
                // captured backdrop, already inpainted + Gaussian-blurred on
                // the daemon thread and cropped to this island's rect, drawn
                // 1:1 under the legibility scrim. Its own restart scope, so a
                // 30 fps capture tick re-draws this leaf and nothing else.
                val frostBlur = if (frostActive) windowsBlur else null
                if (frostBlur != null) {
                    WindowsFrost(
                        blur = frostBlur,
                        intensity = blurIntensity,
                        modifier = Modifier.matchParentSize()
                    )
                }
                // Keep the quickview on screen until the dock is actually
                // visible, so the two surfaces cross at the same instant. Live
                // music does this so Compose can morph the shared disc/title
                // between them (the pill retracts with a plain fade); the idle
                // pill crosses too, but as a "curtain": it slides DOWN the
                // growing island while the pinned apps scale up from behind it,
                // which reads as the pill being pushed past the dock coming to
                // the front.
                val quickEnter =
                    if (musicLive) fadeIn(tween(120))
                    else fadeIn(tween(120)) + slideInVertically(QuickViewSlideIn) { it * 3 }
                val quickExit =
                    if (musicLive) fadeOut(tween(80))
                    else fadeOut(QuickViewFadeOut) + slideOutVertically(QuickViewSlideOut) { it * 3 }
                // Waveform tint: the cover's dominant colour when asked for
                // (derived once per track in the media layer), white otherwise
                // and whenever there is no artwork to read.
                val waveformColor = if (
                    config.widgets.waveformTint == WaveformTint.ALBUM_ART
                ) {
                    liveSession?.artTint ?: WaveformWhite
                } else {
                    WaveformWhite
                }
                AnimatedVisibility(
                    visible = !showDock,
                    enter = quickEnter,
                    exit = quickExit
                ) {
                    val activeGame = game
                    if (gameHudVisible && activeGame != null) {
                        // The HUD takes the pill's whole slot: same island, same
                        // collapse behaviour, one line of live numbers instead
                        // of clock/media. Hovering still expands the dock, so it
                        // installs no pointer target of its own.
                        GameHud(
                            game = activeGame,
                            system = systemState,
                            fps = fps,
                            transparent = gameTransparent,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        val collapsedScope = this
                        QuickView(
                            style = config.style,
                            surface = config.surface,
                            media = if (musicLive) liveSession else null,
                            volumePercent = volumePercent,
                            volumeMuted = volumeMuted,
                            battery = battery,
                            deviceBattery = deviceBattery,
                            notificationCount =
                                if (config.widgets.showCollapsedNotifications) notificationCount else null,
                            showVolume = config.widgets.showCollapsedVolume,
                            showBattery = config.widgets.showCollapsedBattery,
                            showDeviceBattery = config.widgets.showCollapsedDeviceBattery,
                            // Calendar stays out of the morph: it is a collapsed-only
                            // element, and the ticker borrows the media branch's slot
                            // rather than a shared key, so nothing about the now-playing
                            // morph changes when a meeting starts.
                            showCalendar = config.widgets.showCalendar,
                            calendarEvent = calendarTicker?.summary,
                            calendarEventCount = calendarEventCount,
                            showWeather = config.widgets.showWeather,
                            weather = weatherData,
                            fahrenheit = config.widgets.weatherFahrenheit,
                            showWaveform = config.widgets.showCollapsedWaveform,
                            waveformColor = waveformColor,
                            visualiserStyle = config.widgets.visualiserStyle,
                            waveformHidesTitle =
                                config.widgets.showCollapsedWaveform && config.widgets.waveformHidesTitle,
                            discModifier = sharedScope.nowPlayingSharedElement(NowPlayingDiscKey, collapsedScope),
                            titleModifier = sharedScope.nowPlayingSharedElement(NowPlayingTitleKey, collapsedScope),
                            volumeRingModifier =
                                sharedScope.nowPlayingSharedElement(NowPlayingVolumeKey, collapsedScope),
                            batteryRingModifier =
                                sharedScope.nowPlayingSharedElement(NowPlayingBatteryKey, collapsedScope),
                            clock = remember(config.clockDate, config.clockTime, config.clock24Hour, config.clockDateFormat) {
                                ClockPrefs(
                                    showDate = config.clockDate,
                                    showTime = config.clockTime,
                                    clock24Hour = config.clock24Hour,
                                    dateFormat = config.clockDateFormat
                                )
                            },
                            onWrapWidthChange = { hugWidth = it }
                        )
                    }
                }
            }
            // Playback progress straddles the island's own outline — half
            // over the island, half over the transparent margin the wrapper
            // reserves — filling the notch's border with the playhead instead
            // of reading as a bar. A sibling of the clipped island (not a
            // child), so the outer half is never cut. The ink is the cover's
            // dominant colour (accent cyan with no cover), matching the
            // expanded player's tinting. The expanded page carries its own
            // seekable progress, so the ring only shows while collapsed.
            // Plain overlay, no pointer modifiers.
            if (showOutline && liveNow != null) {
                NotchProgressOutlineForSession(
                    session = liveNow,
                    accent = liveSession?.artTint ?: LiveAccent,
                    // Mirrors notchShape(): capsule rounds every corner,
                    // the flush style only the two facing the screen.
                    spec = NotchOutlineSpec(
                        radius = outlineRadius,
                        roundTop = config.style.capsule,
                        outerPad = outlinePad
                    ),
                    modifier = Modifier.matchParentSize()
                )
            }
            }

            // The dock is revealed with a fade + scale layer; its visibility
            // state doubles as the shared-element transition scope, so the
            // now-playing morph runs in lockstep with the reveal and the
            // island's "hold expanded" gate above.
            //
            // Idle expansion uses a pronounced entry: the pinned apps scale up
            // from a small size (+ fade) anchored near the top edge, rising from
            // behind the quickview that is sliding down past them — the "curtain"
            // cross. Live music keeps the gentle 0.97 pop (scale centred), since
            // the dock there carries the music page that the pill's content is
            // actively morphing into.
            val dockReveal = dockRevealSpring()
            val dockEntryScale = if (musicLive) 0.97f else IdleDockEntryScale
            val dockOrigin = if (musicLive) TransformOrigin.Center else IdleDockOrigin
            val dockEnter =
                if (musicLive) {
                    fadeIn(DockFadeIn) + scaleIn(
                        initialScale = dockEntryScale,
                        transformOrigin = dockOrigin,
                        animationSpec = dockReveal
                    )
                } else {
                    fadeIn(IdleDockFadeIn) + scaleIn(
                        initialScale = IdleDockEntryScale,
                        transformOrigin = IdleDockOrigin,
                        animationSpec = idleDockRevealSpring()
                    )
                }
            val dockExit =
                if (musicLive) {
                    fadeOut(DockFadeOut) + scaleOut(
                        targetScale = dockEntryScale,
                        transformOrigin = dockOrigin,
                        animationSpec = dockReveal
                    )
                } else {
                    fadeOut(IdleDockFadeOut) + scaleOut(
                        targetScale = IdleDockEntryScale,
                        transformOrigin = IdleDockOrigin,
                        animationSpec = idleDockRevealSpring()
                    )
                }
            Box(
                modifier = composeScrollModifier
                    .size(metrics.dockArea.width, metrics.dockArea.height),
                contentAlignment = Alignment.Center
            ) {
                AnimatedVisibility(
                    visibleState = dockState,
                    enter = dockEnter,
                    exit = dockExit
                ) {
                    val dockScope = this
                    WidgetPages(
                        pages = pages,
                        pagePosition = pagePosition,
                        favorites = favorites,
                        config = config,
                        iconBitmaps = iconBitmaps,
                        session = liveSession,
                        media = media,
                        battery = battery,
                        volumePercent = volumePercent,
                        volumeMuted = volumeMuted,
                        density = density,
                        interactive = showDock,
                        discModifier = sharedScope.nowPlayingSharedElement(NowPlayingDiscKey, dockScope),
                        titleModifier = sharedScope.nowPlayingSharedElement(NowPlayingTitleKey, dockScope),
                        volumeRingModifier = sharedScope.nowPlayingSharedElement(NowPlayingVolumeKey, dockScope),
                        batteryRingModifier = sharedScope.nowPlayingSharedElement(NowPlayingBatteryKey, dockScope),
                        calendarState = calendarState,
                        calendarRefreshing = calendarRefreshing,
                        weatherState = weatherState,
                        weatherFetching = weatherFetching,
                        fahrenheit = config.widgets.weatherFahrenheit,
                        system = systemState,
                        onRefreshWeather = { Weather.refresh() },
                        onOpenSystemMonitor = {
                            scope.launch { SystemMonitor.open() }
                        },
                        onLaunch = { app ->
                            scope.launch {
                                AppLauncher.launch(app)
                                if (config.hideOnAppLaunch) onExpandedChange(false)
                            }
                        }
                    )
                }
            }

            // Hover target is the island footprint plus a small glow — never the
            // whole native window (the notch is top-centred, so the window and
            // the island coincide). With the entire window as the hit target, the
            // notch stayed glued open until the cursor had travelled far past the
            // visible island; hugging the island means the notch retracts the
            // moment the cursor leaves it.
            //
            // This box must stay the LAST sibling (topmost z-order): when
            // siblings overlap, hit testing only considers the highest-z
            // pointer-eligible one. On macOS the dock box carries a scroll
            // handler, so sitting below it the hover area was shadowed and never
            // entered. Sharing
            // ([SharingHoverNode.sharePointerInputWithSiblings]) then walks the
            // hit test back down to the dock subtree, so the hover tracks while
            // the dock keeps its scroll handling and icon clicks. The box draws
            // nothing, so being on top has no visual effect.
            val glow = HoverGlow
            Box(
                modifier = Modifier
                    .animatedIslandSize(animatedWidth, animatedHeight) { w, h ->
                        DpSize(w + glow * 2, h + glow)
                    }
                    .onGloballyPositioned { hoverCoordinates = it }
                    .sharingHover { setHovered(it) }
            ) { /* Transparent hover area; draws nothing but stays on top for hit testing. */ }
        }
    }
}

/**
 * Windows backdrop frost: the captured strip behind the island, already
 * inpainted (our own scrim/content replaced from the real band flanking it)
 * and Gaussian-blurred on the capture daemon, cropped to this island's rect.
 * Drawn 1:1 as the island's bottom layer, then the legibility scrim — the
 * same legibility rule as macOS, where the native panel shows through and
 * only the scrim is painted.
 *
 * The frame lives in a [StateFlow] read inside this leaf only: a 30 fps
 * capture tick re-draws the island and never recomposes the overlay body.
 * Frame null (daemon warming up, surface just flipped, native failure) draws
 * nothing, so the island falls through to whatever background the caller left
 * in the chain — painted glass on failure, a bare clipped shape for the
 * frame-or-two before the first capture lands.
 */
@Composable
private fun WindowsFrost(
    blur: WindowsBackdropBlur,
    intensity: Int,
    modifier: Modifier = Modifier
) {
    val frame by blur.frame.collectAsState()
    Box(modifier = modifier) {
        frame?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.matchParentSize()
            )
        }
        // Scrim above the frost: bright wallpapers would otherwise wash the
        // white quickview out. Deliberately light and intensity-driven —
        // the frost supplies the texture, this only buys contrast.
        Box(
            modifier = Modifier
                .matchParentSize()
                .islandSurface(NotchSurface.TRANSLUCENT, null, true, intensity)
        )
    }
}

/**
 * Sizes the node from the given animated states, reading them in the LAYOUT
 * phase rather than in composition. The island's springs update every frame
 * while the notch opens or closes, and a composition-phase read at the
 * overlay root (`.width(animatedWidth)` or as effect keys) recomposed the
 * entire island + dock tree sixty times a second — measured as the dominant
 * cost of open/close (98 of 235 render-thread samples landed in recompose).
 * A State read inside the layout lambda invalidates only this node's
 * measure/place, so a spring tick re-measures the island and nothing above.
 */
private fun Modifier.animatedIslandSize(
    width: State<Dp>,
    height: State<Dp>,
    transform: (Dp, Dp) -> DpSize = { w, h -> DpSize(w, h) }
): Modifier = layout { measurables, constraints ->
    val target = transform(width.value, height.value)
    val w = target.width.roundToPx().coerceIn(constraints.minWidth, constraints.maxWidth)
    val h = target.height.roundToPx().coerceIn(constraints.minHeight, constraints.maxHeight)
    val child = measurables.measure(
        constraints.copy(minWidth = w, maxWidth = w, minHeight = h, maxHeight = h)
    )
    layout(w, h) { child.place(0, 0) }
}

// How often the System Accent surface re-reads the OS accent so a desktop
// accent change recolours the notch without a restart.
private const val SystemAccentPollMs = 3000L

/**
 * Transparent margin the island wrapper reserves around the island while the
 * progress outline shows, so the ring's outer half has something to paint
 * into. One stroke width each side: the ring straddles the edge exactly.
 */
private val OutlinePad = 2.dp

