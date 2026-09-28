package com.starkdroid.quicknotch.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starkdroid.quicknotch.datasource.AppIconLoader
import com.starkdroid.quicknotch.datasource.AppLauncher
import com.starkdroid.quicknotch.datasource.HostPlatform
import com.starkdroid.quicknotch.datasource.MacAppKit
import com.starkdroid.quicknotch.datasource.SystemAccent
import com.starkdroid.quicknotch.generated.resources.*
import com.starkdroid.quicknotch.media.AudioBattery
import com.starkdroid.quicknotch.media.AudioDeviceBattery
import com.starkdroid.quicknotch.media.MediaController
import com.starkdroid.quicknotch.media.MediaSession
import com.starkdroid.quicknotch.media.NotificationCount
import com.starkdroid.quicknotch.media.PlaybackState
import com.starkdroid.quicknotch.media.SystemBattery
import com.starkdroid.quicknotch.model.AppItem
import com.starkdroid.quicknotch.model.FavoritesRepository
import com.starkdroid.quicknotch.model.NotchConfig
import com.starkdroid.quicknotch.model.NotchStyle
import com.starkdroid.quicknotch.model.NotchSurface
import com.starkdroid.quicknotch.theme.TextPrimary
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import java.awt.MouseInfo
import java.awt.Point
import org.jetbrains.compose.resources.stringResource

// ── Palette ──────────────────────────────────────────────────────────────────

private val LiveAccent = Color(0xFF38BDF8)
private val VinylDiscFill = Color(0xFF14161C)
private val VinylRing = Color(0xFF343A45)

/** The playhead fill sweeps accent cyan into a pale sky tip at the head. */
private val ProgressGradientStart = Color(0xFF0EA5E9)
private val ProgressGradientEnd = Color(0xFF7DD3FC)

// ── Motion ───────────────────────────────────────────────────────────────────
// Near-critically-damped springs mirror AppKit's default view animation: a
// fast, confident response that settles without a rubbery bounce, so the notch
// never overshoots its window footprint (which would clip) yet still feels
// organic rather than linear.

private fun notchSizeSpring() = spring(
    dampingRatio = 0.9f,
    stiffness = 340f,
    visibilityThreshold = Dp.VisibilityThreshold
)

private fun iconSpring() = spring(
    dampingRatio = 0.8f,
    stiffness = 1000f,
    visibilityThreshold = 0.001f
)

private fun pageSpring() = spring(
    dampingRatio = 0.9f,
    stiffness = 900f,
    visibilityThreshold = 0.001f
)

// The dock reveal is a pure layer animation (alpha + scale) over an
// always-composed, always-expanded-size dock — nothing is measured or laid out
// while the window is growing, which is what removed the open-time frame drops.
private val DockFadeIn = tween<Float>(170, easing = FastOutSlowInEasing)
private val DockFadeOut = tween<Float>(90)

// The idle "curtain" expansion moves: the quickview slides down the growing
// island while the pinned apps scale up from behind it, as if the dock surges
// forward under the sliding pill. Live music deliberately skips both — its
// content flies to the detail page through the shared-element morph, so the
// pill keeps its plain fade (sliding the source would fight the flight).
//
// The motion is deliberately gradual: the pill glides most of the way down the
// expanded island ({ it * 3 } ≈ three of its own heights, carrying it off the
// bottom edge) over a long, eased tween while the dock rises on a slow spring,
// so the whole cross reads as one unbroken descent rather than a snap-and-pop.
private val QuickViewSlideIn = tween<IntOffset>(360, easing = FastOutSlowInEasing)
private val QuickViewSlideOut = tween<IntOffset>(620, easing = FastOutSlowInEasing)
private val QuickViewFadeOut = tween<Float>(420)
/** Entry size for the idle dock: far enough below 1 to read as "coming to the
 * front", not so small it feels like a locket pop. Growth is anchored near the
 * top edge where the pill sits, so the apps rise from behind the quickview. */
private const val IdleDockEntryScale = 0.82f
private val IdleDockOrigin = TransformOrigin(0.5f, 0.08f)
private val IdleDockFadeIn = tween<Float>(300, easing = FastOutSlowInEasing)
private val IdleDockFadeOut = tween<Float>(180)

private fun dockRevealSpring() = spring(
    dampingRatio = 0.9f,
    stiffness = 300f,
    visibilityThreshold = 0.001f
)

/** The idle dock's slower, near-critically-damped rise (no bounce, no snap) —
 * deliberately separate from [dockRevealSpring] so the music page keeps its
 * faster, slightly springier 0.97 pop. */
private fun idleDockRevealSpring() = spring(
    dampingRatio = 0.92f,
    stiffness = 150f,
    visibilityThreshold = 0.001f
)

/**
 * Bounds spring for shared-element morphs. The quickview's disc/title and
 * their detailed-view counterparts travel on the same near-critically-damped
 * curve as the notch itself, so the two ends of the transition read as one
 * continuous motion rather than a crossfade.
 */
private fun sharedBoundsSpring() = spring<Rect>(dampingRatio = 0.9f, stiffness = 320f)

/** Distance from an icon centre (in multiples of icon size) the wave reaches. */
private const val FisheyeRadiusFactor = 1.5f

/** Cross-axis travel of a fully magnified icon, in dp. */
private val IconLift = 8.dp

/**
 * Extra hover tolerance around the island footprint (the quickview or the
 * expanded dock). The hover hit area is the island plus this glow — never the
 * whole native window — so the notch retracts as soon as the cursor leaves the
 * visible island instead of staying glued open.
 */
private val HoverGlow = 10.dp

/** Label font size as a fraction of the expanded dock height (≈11sp at 78dp). */
private const val DockLabelFontSizeFactor = 0.145f

/**
 * Hard ceiling on how long the choreography waits for the size spring to settle
 * (in either direction) before acting anyway, so an interrupted animation can
 * never leave the notch stuck. Comfortably past the spring's settle time, and
 * past the dock fade that can delay the collapse spring starting.
 */
private const val IslandSettleTimeoutMs = 650L

// ── Hover input sharing ────────────────────────────────────────────────────

/**
 * Hover tracking lives on a small transparent sibling Box that sits on top of
 * the dock. On macOS the dock Box carries a scroll handler and is therefore
 * pointer-eligible, so a plain [Modifier.onPointerEvent] hover area would be
 * shadowed by it: when siblings overlap, hit testing only adds the highest-z
 * eligible one, and the dock is drawn above the hover area.
 *
 * This node opts into [sharePointerInputWithSiblings], so once the hover area
 * is hit the hit test keeps walking down through the dock subtree and both
 * stay on the pointer path — hover Enter/Exit still fires while the dock keeps
 * its scroll handling and icon clicks. See the sibling ordering in
 * [FloatingNotchOverlay] for why the hover area has to be on top for this to
 * work.
 */
private class SharingHoverNode(
    var onHoverChanged: (Boolean) -> Unit
) : Modifier.Node(), PointerInputModifierNode {

    override fun onPointerEvent(event: PointerEvent, pass: PointerEventPass, bounds: IntSize) {
        when (event.type) {
            PointerEventType.Enter -> onHoverChanged(true)
            PointerEventType.Exit -> onHoverChanged(false)
            else -> Unit
        }
    }

    override fun onCancelPointerInput() {
        // Callback-only hover tracking holds no gesture state to reset.
    }

    override fun sharePointerInputWithSiblings(): Boolean = true
}

private class SharingHoverElement(
    private val onHoverChanged: (Boolean) -> Unit
) : ModifierNodeElement<SharingHoverNode>() {
    override fun create(): SharingHoverNode = SharingHoverNode(onHoverChanged)
    override fun update(node: SharingHoverNode) {
        node.onHoverChanged = onHoverChanged
    }

    override fun equals(other: Any?): Boolean =
        other is SharingHoverElement && other.onHoverChanged == onHoverChanged

    override fun hashCode(): Int = onHoverChanged.hashCode()
}

private fun Modifier.sharingHover(onHoverChanged: (Boolean) -> Unit): Modifier =
    this.then(SharingHoverElement(onHoverChanged))

/**
 * True while the cursor is inside the notch's current hover footprint
 * (island + glow, in screen coordinates). Collapse is gated on this rather
 * than on an Exit event alone because undecorated layered overlay windows
 * re-state their hover region when they resize under a stationary cursor on
 * macOS and Windows alike — with media ticking its progress under the pointer,
 * trusting the raw event made the notch flap open/closed while hovering.
 *
 * [awtDrift] is the live difference between the window's real CG origin and
 * AWT's cached `locationOnScreen` (see `MacAppKit.screenOriginDelta`): Compose's
 * `localToScreen` rides the stale AWT position, which the above-menu-bar
 * placement invalidates by exactly the menu-bar height. Null means no drift.
 *
 * Unknown footprint or pointer position errs toward keeping the notch open.
 */
private fun cursorOverNotch(coordinates: LayoutCoordinates?, awtDrift: Point?): Boolean {
    val coords = coordinates ?: return true
    if (!coords.isAttached) return true
    val pointer = MouseInfo.getPointerInfo()?.location ?: return true
    val topLeft = coords.localToScreen(Offset.Zero)
    val size = coords.size
    val bottomRight = coords.localToScreen(
        Offset(size.width.toFloat(), size.height.toFloat())
    )
    val drift = awtDrift
    val shiftedTopLeft = if (drift != null) Offset(topLeft.x + drift.x, topLeft.y + drift.y) else topLeft
    val shiftedBottomRight = if (drift != null) Offset(bottomRight.x + drift.x, bottomRight.y + drift.y) else bottomRight
    return Rect(shiftedTopLeft, shiftedBottomRight).contains(
        Offset(pointer.x.toFloat(), pointer.y.toFloat())
    )
}

// ── Shared-element morph keys ────────────────────────────────────────────────
// The collapsed live-activity elements and their detailed now-playing page
// counterparts share these keys, so Compose morphs each pair's bounds instead
// of cutting between the two surfaces when the notch opens or closes.

private const val NowPlayingDiscKey = "nowPlayingDisc"
private const val NowPlayingTitleKey = "nowPlayingTitle"
private const val NowPlayingVolumeKey = "nowPlayingVolume"
private const val NowPlayingBatteryKey = "nowPlayingBattery"

// How often the System Accent surface re-reads the OS accent so a desktop
// accent change recolours the notch without a restart.
private const val SystemAccentPollMs = 3000L

/**
 * Flies one quickview element into its expanded-page counterpart (the vinyl
 * disc, the marquee title, the level rings) under one shared moving frame.
 * The pair are different composables, but they render the *same content* at
 * different sizes — exactly the contract of
 * [SharedTransitionScope.sharedElement], which renders ONLY the target content
 * inside the transforming bounds.
 *
 * That single-content guarantee is why this must not be
 * [SharedTransitionScope.sharedBounds]: sharedBounds deliberately keeps BOTH
 * the entering and the exiting content visible inside the travelling frame
 * (its enter/exit only style them), so the pill copy and the page copy
 * double-draw over each other mid-flight — two covers, two titles (the
 * marquees also scroll out of register), rings printed twice — before the
 * morph settles. `sharedElement` suppresses the source and moves the target,
 * so there is always exactly one album cover and one title on screen.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SharedTransitionScope.nowPlayingSharedElement(
    key: String,
    animatedVisibilityScope: AnimatedVisibilityScope
): Modifier = Modifier.sharedElement(
    state = rememberSharedContentState(key),
    animatedVisibilityScope = animatedVisibilityScope,
    boundsTransform = object : BoundsTransform {
        override fun transform(initialBounds: Rect, targetBounds: Rect) = sharedBoundsSpring()
    }
)

// ── Overlay ──────────────────────────────────────────────────────────────────

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
    var screenOriginDrift by remember { mutableStateOf<Point?>(null) }
    LaunchedEffect(awtWindow) {
        if (awtWindow != null && HostPlatform.isMac) {
            while (true) {
                screenOriginDrift = runCatching { MacAppKit.screenOriginDelta(awtWindow) }
                    .getOrNull()
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
    // the hover Box below so collapse can be gated on the *real* cursor position.
    var hoverCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    // The fixed native window's transparent margin must not swallow clicks on
    // whatever app or desktop sits around the collapsed pill (see
    // WindowPassthrough). The true touch target is exactly the hover box, so
    // publish its bounds in window-client pixels; null (not measured yet) means
    // "whole window interactive", the old behaviour.
    LaunchedEffect(hoverCoordinates) {
        val coords = hoverCoordinates
        if (coords == null || !coords.isAttached) {
            onTouchTargetChange(null)
        } else {
            // Window-client *pixels* for the hover box, scaled to physical
            // pixels (Retina etc.) which is what the native hit-test wants.
            val windowOffset = coords.localToWindow(Offset.Zero)
            val sizePx = coords.size
            val scale = density.density
            val left = windowOffset.x * scale
            val top = windowOffset.y * scale
            val right = (windowOffset.x + sizePx.width) * scale
            val bottom = (windowOffset.y + sizePx.height) * scale
            onTouchTargetChange(
                java.awt.Rectangle(
                    left.roundToInt(),
                    top.roundToInt(),
                    (right - left).roundToInt(),
                    (bottom - top).roundToInt()
                )
            )
        }
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
                    val coords = hoverCoordinates
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
    var pageIndex by pager.pageIndex
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

@Composable
private fun LoadFavoriteIcons(
    favorites: List<AppItem>,
    iconBitmaps: MutableMap<String, ImageBitmap>
) {
    LaunchedEffect(favorites) {
        favorites.forEach { app ->
            if (app.id !in iconBitmaps) {
                launch { iconBitmaps[app.id] = AppIconLoader.loadIcon(app) }
            }
        }
    }
}

// ── Dock ─────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun Dock(
    favorites: List<AppItem>,
    config: NotchConfig,
    iconBitmaps: Map<String, ImageBitmap>,
    density: Density,
    interactive: Boolean,
    onLaunch: (AppItem) -> Unit
) {
    // Pointer position and icon centres share the icon container's local pixel
    // space, so the fisheye tracks the cursor with exact accuracy.
    val centers = remember { mutableStateMapOf<Int, Float>() }
    val pointer = remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(interactive) {
        if (!interactive) pointer.value = null
    }
    val radiusPx = with(density) { config.baseIconSizeDp.dp.toPx() * FisheyeRadiusFactor }
    val liftPx = with(density) { IconLift.toPx() }

    // Held as State (not delegated) so only the label reads it: crossing between
    // icons recomposes the label alone, never the whole dock.
    val closestName: State<String?> = remember(favorites, radiusPx) {
        derivedStateOf {
            val p = pointer.value ?: return@derivedStateOf null
            var bestDistance = radiusPx
            var name: String? = null
            favorites.forEachIndexed { index, app ->
                val center = centers[index] ?: return@forEachIndexed
                val distance = abs(p - center)
                if (distance < bestDistance) {
                    bestDistance = distance
                    name = app.name
                }
            }
            name
        }
    }

    // The dock lives in the tree even while hidden, so pointer tracking and icon
    // clicks must be gated explicitly: an invisible dock must not swallow events
    // meant for the quickview or launch apps from under the cursor.
    val pointerModifier = if (interactive) {
        Modifier
            .onPointerEvent(PointerEventType.Move) { change ->
                pointer.value = change.changes.firstOrNull()?.position?.x
            }
            .onPointerEvent(PointerEventType.Exit) { pointer.value = null }
    } else {
        // Hidden dock: no tracking, and child clickables are disabled below,
        // so events fall through untouched.
        Modifier
    }

    val icons: @Composable () -> Unit = {
        favorites.forEachIndexed { index, app ->
            DockIcon(
                app = app,
                iconBitmap = iconBitmaps[app.id],
                center = centers[index],
                pointer = pointer,
                radiusPx = radiusPx,
                peakScale = config.dockScaleFactor,
                liftPx = liftPx,
                baseSizeDp = config.baseIconSizeDp,
                enabled = interactive,
                onPositioned = { centers[index] = it },
                onClick = { onLaunch(app) }
            )
        }
    }

    // Centred along the depth axis instead of pinned to the anchored edge:
    // that removes the dead strip that used to pile up under the icons
    // (labels off) or above the label (labels on), and keeps the label's
    // descenders clear of the window border.
    Column(
        modifier = Modifier.fillMaxSize().padding(dockPadding()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (config.showTooltips) DockLabel(closestName, config)
        Row(
            modifier = pointerModifier,
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) { icons() }
    }
}

/**
 * Insets the dock away from the top screen edge so icons never touch the
 * display border, and leaves extra room on the inner edge for the
 * magnification lift.
 */
private fun dockPadding(): PaddingValues =
    PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 10.dp)

@Composable
private fun DockIcon(
    app: AppItem,
    iconBitmap: ImageBitmap?,
    center: Float?,
    pointer: State<Float?>,
    radiusPx: Float,
    peakScale: Float,
    liftPx: Float,
    baseSizeDp: Int,
    enabled: Boolean,
    onPositioned: (Float) -> Unit,
    onClick: () -> Unit
) {
    // Reading the pointer here (not in the dock body) keeps recomposition local
    // to each icon as the cursor glides across the dock.
    val targetScale = if (enabled) fisheyeScale(pointer.value, center, radiusPx, peakScale) else 1f
    val animatedScale by animateFloatAsState(targetScale, iconSpring(), label = "dockScale")
    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .padding(DockLayout.IconPadding)
            .size(baseSizeDp.dp)
            .onGloballyPositioned { coordinates ->
                val position = coordinates.positionInParent()
                onPositioned(position.x + coordinates.size.width / 2f)
            }
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
                // Magnified icons lift toward the screen centre (down for a
                // top-centred notch).
                translationY = (animatedScale - 1f) * liftPx
            }
            .clip(RoundedCornerShape(11.dp))
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Crossfade(
            targetState = iconBitmap,
            animationSpec = tween(220),
            label = "dockIconFade"
        ) { bitmap ->
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = app.name,
                    contentScale = ContentScale.Fit,
                    filterQuality = FilterQuality.High,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Shimmer while the icon loads in the background, so a cold dock
                // never flashes a wall of empty grey squares.
                ShimmerBox(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(11.dp)
                )
            }
        }
    }
}

@Composable
private fun DockLabel(nameState: State<String?>, config: NotchConfig) {
    val labelSize = (config.expandedNotchHeightDp * DockLabelFontSizeFactor).sp
    val lineHeight = (labelSize.value * 1.15f).sp
    // The slot is always as tall as one line even with no name showing, so the
    // icons never shift when the label appears; the snug line height plus the
    // vertical padding guarantee descenders clear the window border.
    val slotHeight = with(LocalDensity.current) { lineHeight.toDp() }
    Box(
        modifier = Modifier
            .heightIn(min = slotHeight)
            .padding(horizontal = 8.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = nameState.value.orEmpty(),
            color = TextPrimary.copy(alpha = 0.9f),
            fontSize = labelSize,
            lineHeight = lineHeight,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun QuickView(
    style: NotchStyle,
    surface: NotchSurface,
    media: MediaSession?,
    volumePercent: Int?,
    battery: AudioDeviceBattery?,
    deviceBattery: SystemBattery?,
    notificationCount: Int?,
    showVolume: Boolean,
    showBattery: Boolean,
    showDeviceBattery: Boolean,
    /** Shared-element morph modifiers for the live-activity disc/title/rings. */
    discModifier: Modifier = Modifier,
    titleModifier: Modifier = Modifier,
    volumeRingModifier: Modifier = Modifier,
    batteryRingModifier: Modifier = Modifier,
    clock: ClockPrefs
) {
    Box(modifier = Modifier.fillMaxSize().clipToBounds(), contentAlignment = Alignment.Center) {
        if (style == NotchStyle.DRAG_HANDLE) {
            // A single slim grabber line — the whole point of the handle is to
            // stay out of the way until it is pulled open.
            Box(
                modifier = Modifier
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(handleTint(surface))
            )
        } else if (media != null) {
            // Collapsed live activity: the disc hugs the left end, the title
            // sits centred (marqueed when long) and the system volume degree
            // plus the audio device's battery cap the right — each behind its
            // own collapsed-only toggle. The pill is not interactive, so there
            // is no transport control here; play/pause lives in the expanded
            // page.
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                // Same vinyl treatment as the expanded page's disc — art on a
                // grooved disc with a spindle hub, gently rotating while
                // playing — so the two morph without a style change mid-flight.
                VinylDisc(
                    art = media.artwork,
                    playing = media.state == PlaybackState.PLAYING,
                    size = 20.dp,
                    modifier = discModifier
                )
                // The title shares the exact 22dp frame the level rings use, so
                // the disc, text and rings all sit on one vertical centreline.
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(22.dp),
                    contentAlignment = Alignment.Center
                ) {
                    MarqueeText(
                        text = media.title,
                        color = Color.White.copy(alpha = 0.92f),
                        fontSize = 11.sp,
                        lineHeight = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = titleModifier.fillMaxWidth()
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (showVolume && volumePercent != null) {
                        LevelRing(
                            level = volumePercent,
                            icon = volumePainter(),
                            contentDescription = stringResource(Res.string.music_volume),
                            modifier = volumeRingModifier.size(22.dp)
                        )
                    }
                    if (showBattery && battery != null) {
                        LevelRing(
                            level = battery.levelPercent,
                            icon = batteryPainter(battery.isAudio),
                            contentDescription = stringResource(
                                Res.string.music_battery,
                                battery.name?.takeIf { it.isNotBlank() }
                                    ?: stringResource(Res.string.music_audio_device)
                            ),
                            modifier = batteryRingModifier.size(22.dp),
                            lowTint = BatteryLowTint,
                            lowThreshold = BatteryLowThreshold
                        )
                    }
                }
            }
        } else if (clock.showDate || clock.showTime) {
            // Idle clock: date / time replace the dots while nothing plays.
            // Depending on the chosen format and pill width the whole phrase
            // usually fits; the pill clips rather than stretching.
            IdleClockRow(
                clock = clock,
                tint = islandTint(surface),
                battery = if (showBattery) battery else null,
                deviceBattery = if (showDeviceBattery) deviceBattery else null,
                notificationCount = notificationCount
            )
        } else {
            // Idle dots; the notification bell, when readable, caps the row.
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val dot = islandTint(surface)
                Box(Modifier.size(5.dp).clip(CircleShape).background(dot.copy(alpha = 0.28f)))
                Box(
                    Modifier
                        .size(width = 18.dp, height = 5.dp)
                        .clip(CircleShape)
                        .background(dot.copy(alpha = 0.16f))
                )
                Box(Modifier.size(5.dp).clip(CircleShape).background(dot.copy(alpha = 0.28f)))
                if (notificationCount != null) {
                    NotificationIndicator(count = notificationCount, tint = dot.copy(alpha = 0.8f))
                }
            }
        }
    }
}

/**
 * Progress bar drawn against the pill's "inner" border — for a top-centred
 * notch that is the bottom edge (nearest the screen centre) — growing toward
 * the playhead as the track advances. Hidden behind the expanding island once
 * the dock opens (the expanded page shows its own progress control).
 */
@Composable
private fun BoxScope.LiveProgressStrip(
    fraction: Float
) {
    val settlePadding = 2.dp
    Box(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(vertical = settlePadding)
            .fillMaxWidth(fraction.coerceIn(0f, 1f))
            .height(2.5.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(LiveAccent.copy(alpha = 0.95f))
    )
}

// ── Widget pages ─────────────────────────────────────────────────────────────

private enum class WidgetPage { Apps, Music }

/**
 * Renders every active widget page as an always-composed sibling so the pager
 * never measures the music page while the window is animating. Inactive pages
 * are pushed aside with a small alpha+slide layer transform (never a
 * subtraction/recomposition), and only the page under the pointer receives
 * input.
 */
@Composable
private fun WidgetPages(
    pages: List<WidgetPage>,
    pagePosition: Float,
    favorites: List<AppItem>,
    config: NotchConfig,
    iconBitmaps: Map<String, ImageBitmap>,
    session: MediaSession?,
    media: MediaController,
    nowMs: Long,
    battery: AudioDeviceBattery?,
    volumePercent: Int?,
    density: Density,
    interactive: Boolean,
    discModifier: Modifier,
    titleModifier: Modifier,
    volumeRingModifier: Modifier,
    batteryRingModifier: Modifier,
    onLaunch: (AppItem) -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        pages.forEachIndexed { index, page ->
            val relative = index - pagePosition
            val isActive = abs(relative) < 0.5f
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = (1f - abs(relative)).coerceIn(0f, 1f)
                        translationY = 0.35f * relative * size.height
                    }
            ) {
                when (page) {
                    WidgetPage.Apps -> Dock(
                        favorites = favorites,
                        config = config,
                        iconBitmaps = iconBitmaps,
                        density = density,
                        interactive = interactive && isActive,
                        onLaunch = onLaunch
                    )
                    WidgetPage.Music -> if (session != null) {
                        MusicPage(
                            session = session,
                            media = media,
                            nowMs = nowMs,
                            battery = battery,
                            volumePercent = volumePercent,
                            interactive = interactive && isActive,
                            discModifier = discModifier,
                            titleModifier = titleModifier,
                            volumeRingModifier = volumeRingModifier,
                            batteryRingModifier = batteryRingModifier
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MusicPage(
    session: MediaSession,
    media: MediaController,
    nowMs: Long,
    battery: AudioDeviceBattery?,
    volumePercent: Int?,
    interactive: Boolean,
    discModifier: Modifier,
    titleModifier: Modifier,
    volumeRingModifier: Modifier,
    batteryRingModifier: Modifier
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        MusicContent(
            session = session,
            media = media,
            nowMs = nowMs,
            battery = battery,
            volumePercent = volumePercent,
            interactive = interactive,
            discModifier = discModifier,
            titleModifier = titleModifier,
            volumeRingModifier = volumeRingModifier,
            batteryRingModifier = batteryRingModifier
        )
    }
}

/**
 * Art pinned to the left end of the strip, the metadata held in the middle,
 * the progress/timestamps strip hugging the bottom edge, and the level rings
 * stacked above the transport buttons on the right — the two clusters drift
 * slightly toward the bottom so the controls read as a settled base under the
 * text. Every transport control stays visible here; collapsing the play/pause
 * only affects the slim quickview.
 */
@Composable
private fun MusicContent(
    session: MediaSession,
    media: MediaController,
    nowMs: Long,
    battery: AudioDeviceBattery?,
    volumePercent: Int?,
    interactive: Boolean,
    discModifier: Modifier,
    titleModifier: Modifier,
    volumeRingModifier: Modifier,
    batteryRingModifier: Modifier
) {
    val playing = session.state == PlaybackState.PLAYING
    val duration = session.durationMs.coerceAtLeast(0L)
    val elapsed = session.displayedPositionMs(nowMs).coerceAtLeast(0L)
        .coerceAtMost(if (duration > 0L) duration else Long.MAX_VALUE)

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        VinylDisc(
            art = session.artwork,
            playing = playing,
            size = 56.dp,
            modifier = discModifier,
            onClick = {
                AppLauncher.openMediaProvider(session.sourceId, session.source)
            }
        )

        // Title/artist float in the space above the progress strip, and the
        // strip hugs the bottom edge — mirroring the transport cluster's own
        // bottom bias so both flanks settle at the same baseline.
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    MarqueeText(
                        text = session.title,
                        color = Color.White.copy(alpha = 0.95f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = titleModifier
                    )
                    if (session.artist.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = session.artist,
                            color = Color.White.copy(alpha = 0.55f),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TrackTimeLabel(elapsed)
                TrackProgress(
                    fraction = session.progressFraction(nowMs),
                    durationMs = duration,
                    seekable = media.supportsSeek,
                    enabled = interactive,
                    onSeek = media::seekTo,
                    modifier = Modifier.weight(1f)
                )
                TrackTimeLabel(duration)
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Level indicators sit on top of the media controls, with the whole
        // cluster riding near the bottom edge. Both rings use the exact same
        // shapes as the quickview's, so volume and battery read
        // identically everywhere — the collapsed-only toggles govern just the
        // pill.
        Column(
            modifier = Modifier.fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(1f))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (volumePercent != null) {
                    LevelRing(
                        level = volumePercent,
                        icon = volumePainter(),
                        contentDescription = stringResource(Res.string.music_volume),
                        modifier = volumeRingModifier.size(28.dp)
                    )
                }
                if (battery != null) {
                    LevelRing(
                        level = battery.levelPercent,
                        icon = batteryPainter(battery.isAudio),
                        contentDescription = stringResource(
                            Res.string.music_battery,
                            battery.name?.takeIf { it.isNotBlank() }
                                ?: stringResource(Res.string.music_audio_device)
                        ),
                        modifier = batteryRingModifier.size(28.dp),
                        lowTint = BatteryLowTint,
                        lowThreshold = BatteryLowThreshold
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            TransportControls(
                state = session.state,
                enabled = interactive,
                media = media
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/**
 * A single-line label that marquees when its text is wider than the available
 * space: centred normally, then slowly scrolling right-to-left with a small
 * gap before the loop. The rate scales with the overflow so short titles crawl
 * and long ones glide.
 */
@Composable
private fun MarqueeText(
    text: String,
    color: Color,
    fontSize: TextUnit,
    fontWeight: FontWeight,
    modifier: Modifier = Modifier,
    lineHeight: TextUnit = TextUnit.Unspecified
) {
    val measurer = rememberTextMeasurer()
    val measured = remember(text, color, fontSize, fontWeight, lineHeight, measurer) {
        measurer.measure(
            AnnotatedString(text),
            style = TextStyle(
                color = color,
                fontSize = fontSize,
                fontWeight = fontWeight,
                lineHeight = lineHeight
            )
        )
    }
    val fullWidthPx = measured.size.width

    var viewportPx by remember { mutableIntStateOf(0) }
    val overflowPx = (fullWidthPx - viewportPx).coerceAtLeast(0)
    val needsMarquee = overflowPx > 0
    val gapPx = 48

    // Scroll rate keeps a roughly constant pixel speed regardless of length.
    val travelPx = overflowPx + gapPx
    val durationMs = (travelPx * 1000 / 30).coerceIn(2500, 20000)
    val transition = rememberInfiniteTransition(label = "titleMarquee")
    val scrollX by transition.animateFloat(
        initialValue = 0f,
        targetValue = -travelPx.toFloat(),
        animationSpec = infiniteRepeatable(tween(durationMs, easing = LinearEasing), RepeatMode.Restart),
        label = "titleScrollX"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds()
            .onSizeChanged { viewportPx = it.width },
        contentAlignment = Alignment.CenterStart
    ) {
        val offsetX = if (needsMarquee) {
            scrollX // centred start; scrolls once text overflows
        } else {
            ((viewportPx - fullWidthPx) / 2).coerceAtLeast(0).toFloat()
        }
        Text(
            text = AnnotatedString(text),
            style = TextStyle(
                color = color,
                fontSize = fontSize,
                fontWeight = fontWeight,
                lineHeight = lineHeight
            ),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            modifier = Modifier.offset { IntOffset(offsetX.roundToInt(), 0) }
        )
    }
}

@Composable
private fun TrackTimeLabel(ms: Long, frameHeight: Dp = 28.dp) {
    // Fixed-height frame matching the progress strip beside it, so the elapsed
    // / duration stamps stay optically centred on the playhead line.
    Box(
        modifier = Modifier.height(frameHeight),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = formatMs(ms),
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 10.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * A playhead strip: a hairline track, an accent-cyan gradient fill, and —
 * when seeking is available — a round head ringed in the live accent that
 * rides the tip of the fill. Dragging or tapping (when the backend supports
 * seeking) retargets the OS player; on macOS the strip is display-only,
 * matching MediaRemote's limitations.
 */
@Composable
private fun TrackProgress(
    fraction: Float,
    durationMs: Long,
    seekable: Boolean,
    enabled: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val canSeek = seekable && enabled
    val barHeight = 3.dp
    val headSize = 8.dp
    val laneInset = headSize / 2
    val clamped = fraction.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .height(16.dp)
            .then(
                if (canSeek) {
                    Modifier.pointerInput(durationMs) {
                        detectTapGestures { position ->
                            val ratio = (position.x / size.width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                            onSeek((durationMs.toFloat() * ratio).roundToInt().toLong())
                        }
                    }.pointerInput(durationMs) {
                        detectDragGestures { change, _ ->
                            val ratio = (change.position.x / size.width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                            onSeek((durationMs.toFloat() * ratio).roundToInt().toLong())
                        }
                    }
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        // Track + fill share an inset lane so the head can sit flush with the
        // 0% and 100% marks without spilling off the leading/trailing edges.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = laneInset),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(barHeight)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.14f))
            )
            if (clamped > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(clamped)
                        .height(barHeight)
                        .clip(CircleShape)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(ProgressGradientStart, LiveAccent, ProgressGradientEnd)
                            )
                        )
                )
            }
        }
        // The head tracks the fill tip inside the same inset lane.
        if (canSeek) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = laneInset),
                contentAlignment = Alignment.CenterStart
            ) {
                Box(Modifier.fillMaxWidth(clamped)) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .size(headSize)
                            .clip(CircleShape)
                            .background(Color.White)
                            .border(2.dp, LiveAccent, CircleShape)
                    )
                }
            }
        }
    }
}

@Composable
private fun TransportControls(
    state: PlaybackState,
    enabled: Boolean,
    media: MediaController
) {
    val tint = TextPrimary.copy(alpha = if (enabled) 0.9f else 0.3f)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        IconButton(
            onClick = { media.previous() },
            enabled = enabled,
            modifier = Modifier.size(30.dp)
        ) {
            Icon(
                imageVector = Icons.Default.SkipPrevious,
                contentDescription = stringResource(Res.string.music_previous),
                tint = tint,
                modifier = Modifier.size(19.dp)
            )
        }
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(LiveAccent.copy(alpha = if (enabled) 0.92f else 0.35f)),
            contentAlignment = Alignment.Center
        ) {
            IconButton(
                onClick = { media.playPause() },
                enabled = enabled,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = if (state == PlaybackState.PLAYING) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = stringResource(
                        if (state == PlaybackState.PLAYING) Res.string.music_pause else Res.string.music_play
                    ),
                    tint = Color.Black.copy(alpha = 0.85f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        IconButton(
            onClick = { media.next() },
            enabled = enabled,
            modifier = Modifier.size(30.dp)
        ) {
            Icon(
                imageVector = Icons.Default.SkipNext,
                contentDescription = stringResource(Res.string.music_next),
                tint = tint,
                modifier = Modifier.size(19.dp)
            )
        }
    }
}

/**
 * The spinning record the live activity is named after: the track's album art
 * clipped to a disc that rotates while playing (frozen at its current angle
 * when paused, rather than letting an infinite animation drive it).
 */
@Composable
private fun VinylDisc(
    art: ImageBitmap?,
    playing: Boolean,
    size: Dp,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val rotation by rememberInfiniteTransition(label = "vinyl").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 12000, easing = LinearEasing), RepeatMode.Restart),
        label = "vinylAngle"
    )
    var frozenAngle by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(playing) { if (!playing) frozenAngle = rotation }
    val angle = if (playing) rotation else frozenAngle

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { rotationZ = angle }
            .clip(CircleShape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(role = Role.Button) { onClick() }
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(VinylDiscFill)
                .border(1.dp, VinylRing, CircleShape)
        )
        if (art != null) {
            Image(
                bitmap = art,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
            )
        } else {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(size * 0.5f)
            )
        }
        Box(
            modifier = Modifier
                .size(size * 0.22f)
                .clip(CircleShape)
                .background(Color(0xFF07080C))
                .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
        )
    }
}

private fun formatMs(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}

/**
 * A widget only renders when its feature is enabled and its conditions hold:
 * the now-playing page needs a live session and a capsule-friendly style
 * (the drag handle is deliberately excluded from every live-activity surface,
 * both collapsed and expanded).
 */
internal fun isStyleEligibleForLiveActivity(config: NotchConfig): Boolean =
    config.style != NotchStyle.DRAG_HANDLE

internal fun isMusicLive(config: NotchConfig, session: MediaSession?): Boolean =
    config.widgets.nowPlayingEnabled &&
        isStyleEligibleForLiveActivity(config) &&
        session?.isActive == true

// Glyph colours brighten slightly over the translucent surfaces so they
// stay legible, and stay near-white on the opaque one.
private fun handleTint(surface: NotchSurface): Color =
    if (surface == NotchSurface.TRANSLUCENT || surface == NotchSurface.SYSTEM_ACCENT)
        Color.White.copy(alpha = 0.42f)
    else Color.White.copy(alpha = 0.30f)

private fun islandTint(surface: NotchSurface): Color =
    if (surface == NotchSurface.TRANSLUCENT || surface == NotchSurface.SYSTEM_ACCENT)
        Color.White.copy(alpha = 0.72f)
    else Color.White

// ── Geometry helpers ─────────────────────────────────────────────────────────

/**
 * Cosine-squared falloff that peaks at [peakScale] when the cursor is exactly
 * over an icon's centre and eases back to 1.0 at [radiusPx]. Because the peak
 * sits on the centre and the radius scales with icon size, the magnified icon
 * always lines up with the true cursor position.
 */
private fun fisheyeScale(
    pointer: Float?,
    center: Float?,
    radiusPx: Float,
    peakScale: Float
): Float {
    if (pointer == null || center == null || radiusPx <= 0f) return 1f
    val distance = abs(pointer - center)
    if (distance >= radiusPx) return 1f
    val normalized = (distance / radiusPx) * (PI.toFloat() / 2f)
    val factor = cos(normalized) * cos(normalized)
    return 1f + (peakScale - 1f) * factor
}

/**
 * Rounds only the two corners facing away from the anchored edge; the corners
 * touching the screen edge stay square so the notch reads as flush with the
 * display, like the hardware Dynamic Island.
 */
private fun notchShape(expanded: Boolean, style: NotchStyle): CornerBasedShape {
    if (style.capsule) {
        // Round all four corners; a generous radius clamps to a stadium at the
        // slim collapsed height and a soft pill when expanded.
        val corner = if (expanded) 18.dp else 14.dp
        return RoundedCornerShape(corner)
    }
    val corner = if (expanded) 18.dp else 13.dp
    return RoundedCornerShape(bottomStart = corner, bottomEnd = corner)
}
