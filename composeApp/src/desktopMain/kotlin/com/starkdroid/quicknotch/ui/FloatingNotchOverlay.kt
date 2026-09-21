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
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import kotlinx.coroutines.launch
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

private val ContentEnter = fadeIn(tween(160)) + scaleIn(
    initialScale = 0.96f,
    animationSpec = tween(240, easing = FastOutSlowInEasing)
)
private val ContentExit = fadeOut(tween(90)) + scaleOut(
    targetScale = 0.98f,
    animationSpec = tween(120)
)

/** Distance from an icon centre (in multiples of icon size) the wave reaches. */
private const val FisheyeRadiusFactor = 1.5f

/** Cross-axis travel of a fully magnified icon, in dp. */
private val IconLift = 8.dp

/**
 * How long to wait after the window starts growing before the dock content is
 * revealed, so the notch reads as "open first, icons second" instead of the
 * content racing the resize and being drawn mid-grow (the source of the jank).
 */
private const val DockRevealDelayMs = 180L

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

    // The dock content is revealed *after* the window has grown, and hidden
    // *before* it shrinks. Drawing a full dock (icons, fisheye layout passes)
    // while the native window is mid-resize is what used to drop frames; the
    // short reveal delay below roughly covers the size spring's settle time.
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

    // The window footprint leads the content while expanding and trails it while
    // collapsing, so the resized native window never clips the animation. The
    // collapse delay comfortably exceeds the size spring's settle time.
    LaunchedEffect(isExpanded) {
        if (isExpanded) {
            onWindowExpandedChange(true)
            delay(DockRevealDelayMs)
            showDock = true
        } else {
            showDock = false
            delay(300)
            onWindowExpandedChange(false)
        }
    }

    val metrics = remember(config, favorites.size) { computeNotchMetrics(config, favorites.size) }
    val targetSize = metrics.sizeFor(isExpanded)
    val animatedWidth by animateDpAsState(targetSize.width, notchSizeSpring(), label = "notchWidth")
    val animatedHeight by animateDpAsState(targetSize.height, notchSizeSpring(), label = "notchHeight")
    val shape = notchShape(config.position, isExpanded)

    Box(
        modifier = modifier
            .fillMaxSize()
            .onPointerEvent(PointerEventType.Enter) { setHovered(true) }
            .onPointerEvent(PointerEventType.Exit) { setHovered(false) },
        contentAlignment = containerAlignment(config.position)
    ) {
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
            AnimatedVisibility(visible = showDock, enter = ContentEnter, exit = ContentExit) {
                Dock(
                    favorites = favorites,
                    config = config,
                    iconBitmaps = iconBitmaps,
                    density = density,
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

    val pointerModifier = Modifier
        .onPointerEvent(PointerEventType.Move) { change ->
            pointer.value = change.changes.firstOrNull()?.position
                ?.let { if (vertical) it.y else it.x }
        }
        .onPointerEvent(PointerEventType.Exit) { pointer.value = null }

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
                onPositioned = { centers[index] = it },
                onClick = { onLaunch(app) }
            )
        }
        SettingsButton(onClick = onOpenSettings)
    }

    if (vertical) {
        Row(
            modifier = Modifier.fillMaxSize().padding(dockPadding(config.position)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (towardStart) Arrangement.Start else Arrangement.End
        ) {
            if (!towardStart && config.showTooltips) DockLabel(closestName)
            Column(
                modifier = pointerModifier,
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) { icons() }
            if (towardStart && config.showTooltips) DockLabel(closestName)
        }
    } else {
        Column(
            modifier = Modifier.fillMaxSize().padding(dockPadding(config.position)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = if (towardStart) Arrangement.Top else Arrangement.Bottom
        ) {
            if (towardStart && config.showTooltips) DockLabel(closestName)
            Row(
                modifier = pointerModifier,
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) { icons() }
            if (!towardStart && config.showTooltips) DockLabel(closestName)
        }
    }
}

/**
 * Insets the dock away from the anchored screen edge so icons never touch the
 * display border, and leaves extra room on the inner edge for the
 * magnification lift.
 */
private fun dockPadding(position: NotchPosition): PaddingValues = when (position) {
    NotchPosition.TOP_CENTER -> PaddingValues(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 12.dp)
    NotchPosition.BOTTOM_CENTER -> PaddingValues(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 6.dp)
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
    onPositioned: (Float) -> Unit,
    onClick: () -> Unit
) {
    // Reading the pointer here (not in the dock body) keeps recomposition local
    // to each icon as the cursor glides across the dock.
    val targetScale = fisheyeScale(pointer.value, center, radiusPx, peakScale)
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
private fun SettingsButton(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        modifier = Modifier
            .padding(start = 6.dp)
            .size(22.dp)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Settings,
            contentDescription = "Settings",
            tint = if (hovered) ControlActive else ControlIdle,
            modifier = Modifier.size(15.dp)
        )
    }
}

@Composable
private fun DockLabel(nameState: State<String?>) {
    Box(
        modifier = Modifier.height(16.dp).padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = nameState.value.orEmpty(),
            color = TextPrimary.copy(alpha = 0.9f),
            fontSize = 11.sp,
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
