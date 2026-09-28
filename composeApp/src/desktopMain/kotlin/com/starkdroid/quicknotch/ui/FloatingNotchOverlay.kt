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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.starkdroid.quicknotch.datasource.AppLauncher
import com.starkdroid.quicknotch.datasource.HostPlatform
import com.starkdroid.quicknotch.datasource.MacAppKit
import com.starkdroid.quicknotch.datasource.SystemAccent
import com.starkdroid.quicknotch.media.AudioBattery
import com.starkdroid.quicknotch.media.AudioDeviceBattery
import com.starkdroid.quicknotch.media.MediaController
import com.starkdroid.quicknotch.media.NotificationCount
import com.starkdroid.quicknotch.media.PlaybackState
import com.starkdroid.quicknotch.media.SystemBattery
import com.starkdroid.quicknotch.model.FavoritesRepository
import com.starkdroid.quicknotch.model.NotchSurface
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
        SystemAccent.refresh()
        while (true) {
            delay(SystemAccentPollMs)
            SystemAccent.refresh()
        }
    }

    // Position interpolation clock. The media bridge samples once per second;
    // advancing a local clock between samples lets the vinyl and the progress
    // strip move at frame time instead of stepping once per heartbeat.
    var nowMs by remember { mutableStateOf(System.nanoTime() / 1_000_000) }
    LaunchedEffect(musicLive, liveSession?.state) {
        while (musicLive && liveSession?.state == PlaybackState.PLAYING) {
            delay(250)
            nowMs = System.nanoTime() / 1_000_000
        }
    }

    var showDock by pager.dockVisible

    // Connectivity is polled every second in the background; re-probe the
    // moment the notch opens or the music goes live so the battery chip never
    // shows a stale read (or a ghost after the device disconnected).
    LaunchedEffect(isExpanded, musicLive) {
        AudioBattery.poke()
        SystemBattery.poke()
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
    var islandCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    // Publishes the island's bounds in window-client pixels for the native
    // click-through (see WindowPassthrough); null (not measured yet) means
    // "whole window interactive", the fail-soft default.
    //
    // Called straight from `onGloballyPositioned` — NOT from an effect keyed on
    // the state: the coordinates object is a stable instance across layout
    // passes, so a keyed effect would run exactly once and freeze the touch
    // target at whatever the island's first size was (the collapsed pill) —
    // leaving everything outside it click-through once the notch expanded.
    // The callback itself fires on every position/size change, which is
    // precisely when the target must be republished.
    //
    // `localToWindow` already returns PHYSICAL pixels (Compose px = dp ×
    // density), so no further scaling happens here — multiplying again used to
    // double-scale the touch target on every display above 100%, turning the
    // transparent margin into an invisible click-swallowing band.
    fun publishTouchTarget(coords: LayoutCoordinates?) {
        if (coords == null || !coords.isAttached) {
            onTouchTargetChange(null)
            return
        }
        val windowOffset = coords.localToWindow(Offset.Zero)
        val sizePx = coords.size
        onTouchTargetChange(
            java.awt.Rectangle(
                windowOffset.x.roundToInt(),
                windowOffset.y.roundToInt(),
                sizePx.width,
                sizePx.height
            )
        )
    }
    fun setHovered(hovered: Boolean) {
        collapseJob?.cancel()
        if (hovered) {
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
    // pages; the wheel moves between them. The set is derived only from things
    // that rarely change, and a page entering/leaving mid-expansion clamps the
    // index instead of jittering.
    //
    // The now-playing page leads while a track is live: expanding a collapsed
    // notch that is already showing media should continue into the detailed
    // music view, not the pinned-apps dock. The apps page stays available behind
    // it for the wheel.
    val pages = remember(config.widgets.pinnedAppsEnabled, musicLive) {
        buildList {
            if (musicLive) add(WidgetPage.Music)
            if (config.widgets.pinnedAppsEnabled) add(WidgetPage.Apps)
        }
    }
    // Page switching is owned by a shared [OverlayPager]: the overlay renders
    // from it, and (on Windows) an AWT-level wheel listener in `Main` drives it
    // from outside the Compose pointer pipeline. One wheel notch = one page.
    LaunchedEffect(pages.size) {
        pager.pageCount = pages.size
        if (pager.pageIndex.value >= pages.size) {
            pager.pageIndex.value = (pages.size - 1).coerceAtLeast(0)
        }
        pager.resetAccum()
    }
    val pagePosition by animateFloatAsState(pager.pageIndex.value.toFloat(), pageSpring(), label = "pagePosition")

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
    val indicatorSlots = quickViewIndicatorSlots(config, musicLive, battery, deviceBattery, notificationCount)

    val metrics = remember(config, favorites.size, musicLive, indicatorSlots) {
        computeNotchMetrics(config, favorites.size, musicLive, indicatorSlots)
    }
    // Cached per surface choice *and* per accent value: the brushes are built
    // once and the accented style is rebuilt only when the OS accent changes,
    // so nothing here allocates while the island animates.
    val surface = remember(config.surface, accent) {
        IslandSurface.styleFor(config.surface, accent)
    }

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
    val targetSize = metrics.islandFor(islandExpanded)
    val animatedWidth by animateDpAsState(targetSize.width, notchSizeSpring(), label = "notchWidth")
    val animatedHeight by animateDpAsState(targetSize.height, notchSizeSpring(), label = "notchHeight")
    val shape = notchShape(islandExpanded, config.style)

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
                    animatedWidth >= island.width - 1.dp && animatedHeight >= island.height - 1.dp
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
            val collapsed = metrics.islandFor(false)
            withTimeoutOrNull(IslandSettleTimeoutMs) {
                snapshotFlow {
                    animatedWidth <= collapsed.width + 1.dp && animatedHeight <= collapsed.height + 1.dp
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
            Box(
                modifier = Modifier
                    .width(animatedWidth)
                    .height(animatedHeight)
                    .onGloballyPositioned { coords ->
                        islandCoordinates = coords
                        publishTouchTarget(coords)
                    }
                    .clip(shape)
                    .islandSurface(config.surface)
                    .border(surface.borderWidth.dp, surface.border, shape),
                contentAlignment = Alignment.Center
            ) {
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
                AnimatedVisibility(
                    visible = !showDock,
                    enter = quickEnter,
                    exit = quickExit
                ) {
                    val collapsedScope = this
                    QuickView(
                        style = config.style,
                        surface = config.surface,
                        media = if (musicLive) liveSession else null,
                        volumePercent = volumePercent,
                        battery = battery,
                        deviceBattery = deviceBattery,
                        notificationCount =
                            if (config.widgets.showCollapsedNotifications) notificationCount else null,
                        showVolume = config.widgets.showCollapsedVolume,
                        showBattery = config.widgets.showCollapsedBattery,
                        showDeviceBattery = config.widgets.showCollapsedDeviceBattery,
                        discModifier = sharedScope.nowPlayingSharedElement(NowPlayingDiscKey, collapsedScope),
                        titleModifier = sharedScope.nowPlayingSharedElement(NowPlayingTitleKey, collapsedScope),
                        volumeRingModifier = sharedScope.nowPlayingSharedElement(NowPlayingVolumeKey, collapsedScope),
                        batteryRingModifier = sharedScope.nowPlayingSharedElement(NowPlayingBatteryKey, collapsedScope),
                        clock = remember(config.clockDate, config.clockTime, config.clock24Hour, config.clockDateFormat) {
                            ClockPrefs(
                                showDate = config.clockDate,
                                showTime = config.clockTime,
                                clock24Hour = config.clock24Hour,
                                dateFormat = config.clockDateFormat
                            )
                        }
                    )
                }
                // Live progress hugs the pill's inner border (the edge facing
                // the nearest screen centre), thinning while the pill itself is
                // thin.
                val liveNow = if (musicLive) liveSession else null
                if (!isExpanded && liveNow != null) {
                    LiveProgressStrip(fraction = liveNow.progressFraction(nowMs))
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
                        nowMs = nowMs,
                        battery = battery,
                        volumePercent = volumePercent,
                        volumeMuted = volumeMuted,
                        density = density,
                        interactive = showDock,
                        discModifier = sharedScope.nowPlayingSharedElement(NowPlayingDiscKey, dockScope),
                        titleModifier = sharedScope.nowPlayingSharedElement(NowPlayingTitleKey, dockScope),
                        volumeRingModifier = sharedScope.nowPlayingSharedElement(NowPlayingVolumeKey, dockScope),
                        batteryRingModifier = sharedScope.nowPlayingSharedElement(NowPlayingBatteryKey, dockScope),
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
                    .size(animatedWidth + glow * 2, animatedHeight + glow)
                    .onGloballyPositioned { hoverCoordinates = it }
                    .sharingHover { setHovered(it) }
            ) { /* Transparent hover area; draws nothing but stays on top for hit testing. */ }
        }
    }
}

// How often the System Accent surface re-reads the OS accent so a desktop
// accent change recolours the notch without a restart.
private const val SystemAccentPollMs = 3000L
