package com.starkdroid.quicknotch.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starkdroid.quicknotch.datasource.AppIconLoader
import com.starkdroid.quicknotch.datasource.AppLauncher
import com.starkdroid.quicknotch.model.AppItem
import com.starkdroid.quicknotch.model.FavoritesRepository
import com.starkdroid.quicknotch.model.NotchConfig
import com.starkdroid.quicknotch.model.NotchPosition
import com.starkdroid.quicknotch.theme.TextPrimary
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos

// ── Palette ──────────────────────────────────────────────────────────────────

private val IslandFill = Color(0xFF0A0A0A)
private val IslandStroke = Color.White.copy(alpha = 0.14f)
private val ControlIdle = Color.White.copy(alpha = 0.28f)
private val ControlActive = Color.White.copy(alpha = 0.92f)

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

// The dock reveal is a pure layer animation (alpha + scale) over an
// always-composed, always-expanded-size dock — nothing is measured or laid out
// while the window is growing, which is what removed the open-time frame drops.
private val DockFadeIn = tween<Float>(170, easing = FastOutSlowInEasing)
private val DockFadeOut = tween<Float>(90)

private fun dockRevealSpring() = spring(
    dampingRatio = 0.9f,
    stiffness = 300f,
    visibilityThreshold = 0.001f
)

/** Distance from an icon centre (in multiples of icon size) the wave reaches. */
private const val FisheyeRadiusFactor = 1.5f

/** Cross-axis travel of a fully magnified icon, in dp. */
private val IconLift = 8.dp

/** Label font size as a fraction of the expanded dock height (≈11sp at 78dp). */
private const val DockLabelFontSizeFactor = 0.145f

/**
 * Hard ceiling on how long the choreography waits for the size spring to settle
 * (in either direction) before acting anyway, so an interrupted animation can
 * never leave the notch stuck. Comfortably past the spring's settle time, and
 * past the dock fade that can delay the collapse spring starting.
 */
private const val IslandSettleTimeoutMs = 650L

// ── Overlay ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun FloatingNotchOverlay(
    repository: FavoritesRepository,
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onWindowExpandedChange: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val config by repository.config.collectAsState()
    val favorites by repository.favorites.collectAsState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val iconBitmaps = remember { mutableStateMapOf<String, ImageBitmap>() }
    LoadFavoriteIcons(favorites, iconBitmaps)

    var showDock by remember { mutableStateOf(false) }

    // Collapse is debounced so a cursor briefly clipping the edge does not
    // immediately retract the dock.
    var collapseJob by remember { mutableStateOf<Job?>(null) }
    fun setHovered(hovered: Boolean) {
        collapseJob?.cancel()
        if (hovered) {
            onExpandedChange(true)
        } else {
            collapseJob = scope.launch {
                delay(config.collapseDelayMs)
                onExpandedChange(false)
            }
        }
    }

    val metrics = remember(config, favorites.size) { computeNotchMetrics(config, favorites.size) }

    // The dock is never composited away or re-measured — it lives in the
    // window-sized root so it is permanently laid out at the expanded
    // footprint. Reveal is then just an alpha+scale layer animation, which is
    // why the open no longer drops frames: the grow spring, the native resize
    // and the dock's first draw are fully decoupled.
    val dockAlpha by animateFloatAsState(if (showDock) 1f else 0f, if (showDock) DockFadeIn else DockFadeOut, label = "dockAlpha")
    val dockScale by animateFloatAsState(if (showDock) 1f else 0.97f, dockRevealSpring(), label = "dockScale")

    // The island keeps the dock as its *sibling*, so the fading dock is not
    // clipped by the shrinking pill; the pill holds its expanded size until the
    // dock fade is done, then contracts.
    val islandExpanded = isExpanded || dockAlpha > 0.01f
    val targetSize = metrics.sizeFor(islandExpanded)
    val animatedWidth by animateDpAsState(targetSize.width, notchSizeSpring(), label = "notchWidth")
    val animatedHeight by animateDpAsState(targetSize.height, notchSizeSpring(), label = "notchHeight")
    val shape = notchShape(config.position, islandExpanded)

    // Open: grow the native window first, hold the dock hidden until the size
    // spring has actually settled, then fade it in. Close: fade the dock out,
    // let the island spring back, then shrink the window. Waiting on the spring
    // itself (with a timeout) rather than fixed delays keeps the window from
    // ever resizing while content is being drawn or measured, and guarantees
    // the notch never gets stuck open-but-empty.
    LaunchedEffect(isExpanded) {
        if (isExpanded) {
            onWindowExpandedChange(true)
            val expanded = metrics.sizeFor(true)
            withTimeoutOrNull(IslandSettleTimeoutMs) {
                snapshotFlow {
                    animatedWidth >= expanded.width - 1.dp && animatedHeight >= expanded.height - 1.dp
                }.first { settled -> settled }
            }
            if (isExpanded) showDock = true
        } else {
            showDock = false
            val collapsed = metrics.sizeFor(false)
            withTimeoutOrNull(IslandSettleTimeoutMs) {
                snapshotFlow {
                    animatedWidth <= collapsed.width + 1.dp && animatedHeight <= collapsed.height + 1.dp
                }.first { settled -> settled }
            }
            onWindowExpandedChange(false)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onPointerEvent(PointerEventType.Enter) { setHovered(true) }
            .onPointerEvent(PointerEventType.Exit) { setHovered(false) },
        contentAlignment = containerAlignment(config.position)
    ) {
        // Island background + collapsed pill: the only thing that actually
        // resizes. The dock is a sibling, not a child, so its layout is stable.
        Box(
            modifier = Modifier
                .width(animatedWidth)
                .height(animatedHeight)
                .clip(shape)
                .background(IslandFill)
                .border(0.5.dp, IslandStroke, shape),
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = !isExpanded,
                enter = fadeIn(tween(120)),
                exit = fadeOut(tween(80))
            ) {
                CollapsedIsland(vertical = config.position.isVertical)
            }
        }

        // The dock stays composed permanently (with input gated off while hidden) so
        // its first measure/layout never coincides with the open animation; all
        // the reveal does is fade a layer. Alpha 0 also skips the draw entirely.
        Box(
            modifier = Modifier
                .size(metrics.expanded.width, metrics.expanded.height)
                .clip(notchShape(config.position, expanded = true))
                .graphicsLayer {
                    alpha = dockAlpha
                    scaleX = dockScale
                    scaleY = dockScale
                },
            contentAlignment = Alignment.Center
        ) {
            Dock(
                favorites = favorites,
                config = config,
                iconBitmaps = iconBitmaps,
                density = density,
                interactive = showDock,
                onLaunch = { app ->
                    scope.launch {
                        AppLauncher.launch(app)
                        if (config.hideOnAppLaunch) onExpandedChange(false)
                    }
                },
                onOpenSettings = onOpenSettings
            )
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
    onLaunch: (AppItem) -> Unit,
    onOpenSettings: () -> Unit
) {
    val vertical = config.position.isVertical
    val towardStart = config.position == NotchPosition.LEFT_CENTER ||
        config.position == NotchPosition.TOP_CENTER

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
    // meant for the collapsed pill or launch apps from under the cursor.
    val pointerModifier = if (interactive) {
        Modifier
            .onPointerEvent(PointerEventType.Move) { change ->
                pointer.value = change.changes.firstOrNull()?.position
                    ?.let { if (vertical) it.y else it.x }
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
                vertical = vertical,
                towardStart = towardStart,
                baseSizeDp = config.baseIconSizeDp,
                enabled = interactive,
                onPositioned = { centers[index] = it },
                onClick = { onLaunch(app) }
            )
        }
        SettingsButton(onClick = onOpenSettings, enabled = interactive)
    }

    if (vertical) {
        Row(
            modifier = Modifier.fillMaxSize().padding(dockPadding(config.position)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (towardStart) Arrangement.Start else Arrangement.End
        ) {
            if (!towardStart && config.showTooltips) DockLabel(closestName, config)
            Column(
                modifier = pointerModifier,
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) { icons() }
            if (towardStart && config.showTooltips) DockLabel(closestName, config)
        }
    } else {
        // Centred along the depth axis instead of pinned to the anchored edge:
        // that removes the dead strip that used to pile up under the icons
        // (labels off) or above the label (labels on), and keeps the label's
        // descenders clear of the window border.
        Column(
            modifier = Modifier.fillMaxSize().padding(dockPadding(config.position)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (towardStart && config.showTooltips) DockLabel(closestName, config)
            Row(
                modifier = pointerModifier,
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) { icons() }
            if (!towardStart && config.showTooltips) DockLabel(closestName, config)
        }
    }
}

/**
 * Insets the dock away from the anchored screen edge so icons never touch the
 * display border, and leaves extra room on the inner edge for the
 * magnification lift.
 */
private fun dockPadding(position: NotchPosition): PaddingValues = when (position) {
    NotchPosition.TOP_CENTER -> PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 10.dp)
    NotchPosition.BOTTOM_CENTER -> PaddingValues(start = 8.dp, end = 8.dp, top = 10.dp, bottom = 8.dp)
    NotchPosition.LEFT_CENTER -> PaddingValues(start = 6.dp, end = 12.dp, top = 8.dp, bottom = 8.dp)
    NotchPosition.RIGHT_CENTER -> PaddingValues(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp)
}

@Composable
private fun DockIcon(
    app: AppItem,
    iconBitmap: ImageBitmap?,
    center: Float?,
    pointer: State<Float?>,
    radiusPx: Float,
    peakScale: Float,
    liftPx: Float,
    vertical: Boolean,
    towardStart: Boolean,
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
    val liftSign = if (towardStart) 1f else -1f

    Box(
        modifier = Modifier
            .padding(DockLayout.IconPadding)
            .size(baseSizeDp.dp)
            .onGloballyPositioned { coordinates ->
                val position = coordinates.positionInParent()
                onPositioned(
                    if (vertical) position.y + coordinates.size.height / 2f
                    else position.x + coordinates.size.width / 2f
                )
            }
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
                val lift = (animatedScale - 1f) * liftPx * liftSign
                if (vertical) translationX = lift else translationY = lift
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
private fun SettingsButton(onClick: () -> Unit, enabled: Boolean) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        modifier = Modifier
            .padding(start = 6.dp)
            .size(22.dp)
            .then(if (enabled) Modifier.hoverable(interaction) else Modifier)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Settings,
            contentDescription = "Settings",
            tint = if (hovered && enabled) ControlActive else ControlIdle,
            modifier = Modifier.size(15.dp)
        )
    }
}

@Composable
private fun DockLabel(nameState: State<String?>, config: NotchConfig) {
    // Font scales with the expanded dock height so the label keeps its
    // proportion as the dock grows.
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
private fun CollapsedIsland(vertical: Boolean) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = if (vertical) Modifier.graphicsLayer { rotationZ = 90f } else Modifier
        ) {
            Box(Modifier.size(5.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.28f)))
            Box(
                Modifier
                    .size(width = 18.dp, height = 5.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.16f))
            )
            Box(Modifier.size(5.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.28f)))
        }
    }
}

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

private fun containerAlignment(position: NotchPosition): Alignment = when (position) {
    NotchPosition.TOP_CENTER -> Alignment.TopCenter
    NotchPosition.BOTTOM_CENTER -> Alignment.BottomCenter
    NotchPosition.LEFT_CENTER -> Alignment.CenterStart
    NotchPosition.RIGHT_CENTER -> Alignment.CenterEnd
}

/**
 * Rounds only the two corners facing away from the anchored edge; the corners
 * touching the screen edge stay square so the notch reads as flush with the
 * display, like the hardware Dynamic Island.
 */
private fun notchShape(position: NotchPosition, expanded: Boolean): CornerBasedShape {
    val corner = if (expanded) 18.dp else 13.dp
    return when (position) {
        NotchPosition.TOP_CENTER -> RoundedCornerShape(bottomStart = corner, bottomEnd = corner)
        NotchPosition.BOTTOM_CENTER -> RoundedCornerShape(topStart = corner, topEnd = corner)
        NotchPosition.LEFT_CENTER -> RoundedCornerShape(topEnd = corner, bottomEnd = corner)
        NotchPosition.RIGHT_CENTER -> RoundedCornerShape(topStart = corner, bottomStart = corner)
    }
}
