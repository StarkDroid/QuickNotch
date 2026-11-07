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

/** Root of the overlay: state and choreography; drawing lives in the sibling files. */
/** Hugs the measured width, bounded by the fixed native window. */
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
     * Reports the touch target in window-client pixels so the window's transparent
     * margin stays click-through; null until measured.
     */
    onTouchTargetChange: (java.awt.Rectangle?) -> Unit = {},
    /**
     * Host AWT window; on macOS used to reconcile the above-menu-bar screen-origin drift.
     */
    awtWindow: java.awt.Window? = null
) {
    val config by repository.config.collectAsState()

    // Calendar reads are rare (a handful a day); collected here because the pill is the only drawer.
    val calendarState by GoogleCalendar.state.collectAsState()
    val calendarRefreshing by GoogleCalendar.refreshing.collectAsState()
    val calendarTick by GoogleCalendar.minuteTick.collectAsState()
    val calendarTicker by GoogleCalendar.ticker.collectAsState()
    val calendarEventCount =
        if (config.widgets.showCalendar) calendarUpcomingCount(calendarState, calendarTick) else 0
    // Weather feeds the chip and the outlook page; the controller polls on its own, so observing never fetches.
    val weatherState by Weather.state.collectAsState()
    val weatherFetching by Weather.fetching.collectAsState()
    val weatherData = (weatherState as? WeatherState.Ready)?.data
    // A failed feed with no cache stays off the wheel, the same rule the calendar page follows.
    val weatherReady = config.widgets.showWeather &&
        (weatherData != null || weatherFetching || weatherState is WeatherState.Loading)
    // The System page joins the wheel only once there is a reading, never as a dead stop.
    val systemState by SystemMetrics.state.collectAsState()
    val systemReady = config.widgets.showSystem && systemState != null
    // The HUD takes the pill's place while a fullscreen game runs; FPS has its own probe
    // so a PresentMon capture never delays a system sample.
    val game by GameDetector.game.collectAsState()
    val fps by FpsProbe.fps.collectAsState()
    // Windows-only, matching the HUD's switches (hidden elsewhere).
    val gameHudVisible = HostPlatform.isWindows && game != null && config.gameHudEnabled
    val favorites by repository.favorites.collectAsState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    // AWT screen coordinates go stale once macOS parks the island above the menu bar, so track the drift.
    // The native read must stay off this dispatcher: a synchronous EDT to AppKit hop can deadlock both.
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

    // Unread notification count; null (store unreadable, e.g. no Full Disk Access) hides the bell.
    val notificationCount by NotificationCount.count.collectAsState()

    // Re-read the OS accent while the System Accent surface is chosen; the flow dedupes,
    // so only a real colour change rebuilds.
    val accent by SystemAccent.accent.collectAsState()
    LaunchedEffect(config.surface) {
        if (config.surface != NotchSurface.SYSTEM_ACCENT) return@LaunchedEffect
        while (true) {
            withContext(Dispatchers.IO) { SystemAccent.refresh() }
            delay(SystemAccentPollMs)
        }
    }

    var showDock by pager.dockVisible

    // Re-probe on open so the battery chip is never stale. Not keyed on musicLive:
    // a macOS probe launches system_profiler (~150ms) and stuttered on every track start.
    LaunchedEffect(isExpanded) {
        if (isExpanded) {
            AudioBattery.poke()
            SystemBattery.poke()
            SystemMetrics.poke()
        }
    }

    // Collapse is debounced so a cursor briefly clipping the edge does not retract the dock.
    var collapseJob by remember { mutableStateOf<Job?>(null) }

    // Hover zone (island + glow): fires Enter/Exit only; the native gate and the collapse
    // hysteresis use the tighter island footprint.
    var hoverCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    // The drawn island, not the wider hover box, is the native touch target: the window's
    // margin must stay click-through right up to the pill's edge.
    // Published inline from onGloballyPositioned: the coordinates instance is stable, so a
    // keyed effect would run once and freeze the target at the collapsed size.
    var islandCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    // Installed before the publish below so one onGloballyPositioned feed drives both the
    // click-through region and the Windows blur capture.
    val windowsBlur = remember(awtWindow) { WindowsBackdropBlur.install(awtWindow) }
    val macBlur = remember(awtWindow) { MacContentBlur(awtWindow) }
    DisposableEffect(awtWindow) {
        onDispose { windowsBlur?.close() }
    }
    val calendarConnected = calendarState is CalendarState.Connected
    // The page rides the wheel only while the widget is on and a connection backs it.
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
            // System sits between the dock and the outlook: a glance at the machine, not part of the day pair.
            if (systemReady) add(WidgetPage.System)
            if (weatherReady) add(WidgetPage.Weather)
            // Only with a connection behind it: an empty page would be a dead stop.
            if (calendarReady) add(WidgetPage.Calendar)
        }
    }

    fun setHovered(hovered: Boolean) {
        collapseJob?.cancel()
        if (hovered) {
            // Expanding over a ticker consumes the notification and lands on the calendar page.
            if (calendarTicker != null) {
                GoogleCalendar.dismissTicker()
                pages.indexOf(WidgetPage.Calendar).takeIf { it >= 0 }?.let { index ->
                    pager.pageIndex.value = index
                }
            }
            onExpandedChange(true)
        } else {
            collapseJob = scope.launch {
                // An Exit is untrustworthy while content changes under a stationary cursor, so verify
                // the real pointer, and re-poll: a one-shot check of a detached footprint wedges it open.
                while (true) {
                    delay(config.collapseDelayMs)
                    // Gate on the drawn island, the same rect the native click-through keeps interactive.
                    val coords = islandCoordinates
                    when {
                        // Footprint unknown or detached mid-resize: keep polling rather than wedge.
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

    // Pages are always composed; the wheel moves between them and the pager is shared
    // with the Windows AWT listener. One wheel notch = one page.
    LaunchedEffect(pages.size) {
        pager.pageCount = pages.size
        if (pager.pageIndex.value >= pages.size) {
            pager.pageIndex.value = (pages.size - 1).coerceAtLeast(0)
        }
        pager.resetAccum()
    }
    val pagePosition = animateFloatAsState(pager.pageIndex.value.toFloat(), pageSpring(), label = "pagePosition")

    // macOS gets scroll through Compose, Windows only through AWT, so this handler is skipped there.
    // Compose reports rotations, not pixels: scale and sum both axes, Shift+scroll lands on x.
    val composeScrollModifier =
        if (HostPlatform.isWindows) Modifier
        else Modifier.onPointerEvent(PointerEventType.Scroll) { event ->
            val rotations = event.changes.fold(0f) { acc, change ->
                acc + change.scrollDelta.x + change.scrollDelta.y
            }
            pager.consumeWheel(rotations * pager.stepPx)
        }

    // Indicators widen the pill one slot each; derived through the helper the native sizer uses
    // so both agree, and gated to the idle branches.
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
    // Blurred means real backdrop blur where the bridge answers (macOS panel, Windows captured
    // frost) and painted glass where it does not; only the scrim differs between the two.

    // The dock reveal rides MutableTransitionState so the same visibility transition feeds
    // the shared-element morph and the hold-expanded gate below.
    val dockState = remember { MutableTransitionState(false) }
    dockState.targetState = showDock

    // The dock is the island's sibling, so the fading dock is not clipped by the shrinking pill.
    val islandExpanded = isExpanded || dockState.currentState || dockState.targetState

    // Applies to the collapsed HUD only; the expanded dock keeps the picked material. Not a
    // NotchSurface entry, so the three materials keep their meaning everywhere else.
    val gameTransparent = gameHudVisible && config.transparentInGame && !islandExpanded
    val blurNative = windowsBlur != null || macBlur.available
    // Both carriers are switched off, not hidden, or the panel/frost would paint back the body.
    val blurActive = !gameTransparent && config.surface == NotchSurface.TRANSLUCENT && blurNative
    val blurIntensity = config.blurRadius.coerceIn(BlurRadiusMin, BlurRadiusMax)
    // Windows paints its frost inside the window, so the painted body must get out of the way;
    // macOS keeps the painted scrim over the native panel.
    val frostActive = blurActive && windowsBlur != null
    val surface = remember(config.surface, accent, blurActive, blurIntensity, gameTransparent) {
        IslandSurface.styleFor(config.surface, accent, blurActive, blurIntensity, gameTransparent)
    }

    // Wrapping branches report their measured width and the island shrinks to exactly that;
    // fill branches keep the configured width. Hugging is drawn growth, the window never moves.
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
    // Kept as State (not `by`): a composition-phase read recomposes this whole overlay on every
    // spring frame, the dominant jank source. These are read only in layout/snapshot phases.
    val animatedWidth = animateDpAsState(targetSize.width, notchSizeSpring(), label = "notchWidth")
    val animatedHeight = animateDpAsState(targetSize.height, notchSizeSpring(), label = "notchHeight")
    val shape = notchShape(islandExpanded, config.style)

    // Publishes the island rect in window-client pixels for click-through; null means the whole
    // window stays interactive. localToWindow is already physical px: scaling again double-scaled it.
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

    // The progress outline straddles the island edge, so its outer half needs a margin that exists
    // before the island is laid out.
    val liveNow = if (musicLive) liveSession else null
    // Needs a live track, a collapsed pill and its toggle; the HUD leaves the ring nothing to belong to.
    val showOutline = !isExpanded && liveNow != null && config.widgets.showProgressBorder &&
        !gameHudVisible
    val outlinePad = if (showOutline) OutlinePad else 0.dp
    // Mirrors notchShape(): capsule rounds every corner, the flush style only the two facing the screen.
    val outlineRadius = notchCornerRadius(islandExpanded, config.style)

    // Native blur follows the surface, not a toggle; both carriers fail soft to the painted surfaces.
    LaunchedEffect(blurActive, blurIntensity, islandExpanded, config.style) {
        windowsBlur?.setBlur(blurActive, blurIntensity)
        if (blurActive) {
            // Per-frame geometry without per-frame composition: a snapshotFlow relays each spring tick.
            // Keying the effect on the animated sizes restarted it and recomposed the overlay at 60 Hz.
            snapshotFlow { DpSize(animatedWidth.value, animatedHeight.value) }
                .collect { size ->
                    macBlur.apply(true, size, islandExpanded, config.style, blurIntensity)
                }
        } else {
            macBlur.apply(false, DpSize.Zero, islandExpanded, config.style, blurIntensity)
        }
    }

    // Hold the dock hidden until the size spring settles, then fade it in, so it never appears
    // mid-measure. The native window keeps its footprint, so there is no resize flash.
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
            // Snap the pager to its leading page before the dock fades, so the next expansion
            // re-opens the live activity and the morph reads the now-playing page's bounds.
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
        // Shared-element scope: both surfaces render matching disc/title keys beneath it.
        val sharedScope = this
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            // Only the island resizes; the outer wrapper reserves the progress outline's margin,
            // which the island clip would cut. The pad is zero whenever the outline is off.
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
                    // Windows frost replaces the painted body, so the chain background would double-tint.
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
                // The captured strip, already inpainted and blurred on the daemon thread, drawn 1:1
                // under the scrim. Its own restart scope, so a 30 fps tick redraws this leaf only.
                val frostBlur = if (frostActive) windowsBlur else null
                if (frostBlur != null) {
                    WindowsFrost(
                        blur = frostBlur,
                        intensity = blurIntensity,
                        modifier = Modifier.matchParentSize()
                    )
                }
                // Keep the quickview up until the dock is visible so the two surfaces cross at once;
                // idle slides down like a curtain while the dock scales up from behind it.
                val quickEnter =
                    if (musicLive) fadeIn(tween(120))
                    else fadeIn(tween(120)) + slideInVertically(QuickViewSlideIn) { it * 3 }
                val quickExit =
                    if (musicLive) fadeOut(tween(80))
                    else fadeOut(QuickViewFadeOut) + slideOutVertically(QuickViewSlideOut) { it * 3 }
                // Cover's dominant colour when asked for, white otherwise and with no artwork.
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
                        // The HUD takes the pill's slot; hovering still expands the dock, so no pointer target.
                        GameHud(
                            game = activeGame,
                            system = systemState,
                            fps = fps,
                            metrics = config.widgets.gameMetrics,
                            transparent = gameTransparent
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
                            // Collapsed-only: the ticker borrows the media branch's slot, so the morph is unaffected.
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
            // A sibling of the clipped island so the outer half survives; ink is the cover tint,
            // cyan with no cover. Shown only collapsed, the expanded page has its own seekable progress.
            if (showOutline && liveNow != null) {
                NotchProgressOutlineForSession(
                    session = liveNow,
                    accent = liveSession?.artTint ?: LiveAccent,
                    // Mirrors notchShape(): capsule rounds every corner, the flush style only the
                    // two facing the screen.
                    spec = NotchOutlineSpec(
                        radius = outlineRadius,
                        roundTop = config.style.capsule,
                        outerPad = outlinePad
                    ),
                    modifier = Modifier.matchParentSize()
                )
            }
            }

            // Dock reveal: a fade + scale layer whose visibility doubles as the shared-element scope.
            // Idle uses a pronounced curtain entry from the top edge; live music keeps the gentle pop.
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

            // Hover target is the island plus a small glow, never the whole window, so the notch
            // retracts when the cursor leaves. Must stay the last sibling, sharing pointer input
            // with the dock beneath so its scroll handling and icon clicks keep working.
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
 * Windows backdrop frost: the captured strip behind the island, already inpainted and blurred,
 * drawn 1:1 under the scrim. The frame is read in this leaf only, so a capture tick redraws the
 * island and never recomposes the overlay.
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
        // Scrim above the frost: bright wallpapers would otherwise wash the white quickview out.
        Box(
            modifier = Modifier
                .matchParentSize()
                .islandSurface(NotchSurface.TRANSLUCENT, null, true, intensity)
        )
    }
}

/**
 * Sizes the node from the given animated states, reading them in the LAYOUT phase: a spring tick
 * re-measures this node only instead of recomposing the island and dock tree every frame.
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

// How often the System Accent surface re-reads the OS accent.
private const val SystemAccentPollMs = 3000L

/**
 * Transparent margin the wrapper reserves while the progress outline shows, one stroke width
 * each side, so the ring straddles the edge exactly.
 */
private val OutlinePad = 2.dp

