package com.starkdroid.quicknotch.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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

private val IslandFill = Color(0xFF0A0A0A)
private val IslandStroke = Color.White.copy(alpha = 0.14f)
private val ControlIdle = Color.White.copy(alpha = 0.28f)
private val ControlActive = Color.White.copy(alpha = 0.92f)

private val NotchMotion = tween<androidx.compose.ui.unit.Dp>(
    durationMillis = 240,
    easing = FastOutSlowInEasing
)
private val ContentEnter = fadeIn(tween(140)) + scaleIn(
    initialScale = 0.97f,
    animationSpec = tween(220, easing = FastOutSlowInEasing)
)
private val ContentExit = fadeOut(tween(90)) + scaleOut(
    targetScale = 0.98f,
    animationSpec = tween(120)
)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun FloatingNotchOverlay(
    repository: FavoritesRepository,
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onWindowExpandedChange: (Boolean) -> Unit,
    onDragReposition: (Float, Float) -> Unit,
    onDragRepositionEnd: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val config by repository.config.collectAsState()
    val favorites by repository.favorites.collectAsState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current.density

    var collapseJob by remember { mutableStateOf<Job?>(null) }
    var dragging by remember { mutableStateOf(false) }
    val iconBitmaps = remember { mutableStateMapOf<String, ImageBitmap>() }

    LaunchedEffect(favorites) {
        favorites.forEach { app ->
            if (!iconBitmaps.containsKey(app.id)) {
                launch {
                    iconBitmaps[app.id] = AppIconLoader.loadIcon(app)
                }
            }
        }
    }

    val isVertical = config.position.isVertical
    val isBottom = config.position.isBottom
    val alongExpanded = (favorites.size * (config.baseIconSizeDp + 14) + 88).coerceIn(300, 900).dp
    val targetWidth = if (isVertical) {
        if (isExpanded) config.expandedNotchHeightDp.dp else config.collapsedPillHeightDp.dp
    } else {
        if (isExpanded) alongExpanded else config.collapsedPillWidthDp.dp
    }
    val targetHeight = if (isVertical) {
        if (isExpanded) alongExpanded else config.collapsedPillWidthDp.dp
    } else {
        if (isExpanded) config.expandedNotchHeightDp.dp else config.collapsedPillHeightDp.dp
    }

    val animatedWidth by animateDpAsState(targetWidth, NotchMotion, label = "notchWidth")
    val animatedHeight by animateDpAsState(targetHeight, NotchMotion, label = "notchHeight")

    LaunchedEffect(isExpanded) {
        if (isExpanded) {
            onWindowExpandedChange(true)
        } else {
            delay(250)
            onWindowExpandedChange(false)
        }
    }

    val corner = if (isExpanded) 18.dp else 13.dp
    val notchShape = when {
        isVertical && config.position == NotchPosition.LEFT_CENTER ->
            RoundedCornerShape(topEnd = corner, bottomEnd = corner)
        isVertical -> RoundedCornerShape(topStart = corner, bottomStart = corner)
        isBottom -> RoundedCornerShape(topStart = corner, topEnd = corner)
        else -> RoundedCornerShape(bottomStart = corner, bottomEnd = corner)
    }

    val containerAlignment = when {
        isVertical && config.position == NotchPosition.LEFT_CENTER -> Alignment.CenterStart
        isVertical -> Alignment.CenterEnd
        isBottom -> Alignment.BottomCenter
        else -> Alignment.TopCenter
    }

    val transformOrigin = when {
        isVertical && config.position == NotchPosition.LEFT_CENTER -> TransformOrigin(0f, 0.5f)
        isVertical -> TransformOrigin(1f, 0.5f)
        isBottom -> TransformOrigin(0.5f, 1f)
        else -> TransformOrigin(0.5f, 0f)
    }

    fun setHovered(hovered: Boolean) {
        if (dragging) return
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

    Box(modifier = modifier.fillMaxSize(), contentAlignment = containerAlignment) {
        Box(
            modifier = Modifier
                .width(animatedWidth)
                .height(animatedHeight)
                .graphicsLayer { this.transformOrigin = transformOrigin }
                .clip(notchShape)
                .background(IslandFill)
                .border(0.5.dp, IslandStroke, notchShape)
                .onPointerEvent(PointerEventType.Enter) { setHovered(true) }
                .onPointerEvent(PointerEventType.Exit) { setHovered(false) }
                .pointerInput(density) {
                    detectDragGestures(
                        onDragStart = { dragging = true },
                        onDragEnd = {
                            dragging = false
                            onDragRepositionEnd()
                        },
                        onDragCancel = {
                            dragging = false
                            onDragRepositionEnd()
                        }
                    ) { change, dragAmount ->
                        change.consume()
                        onDragReposition(dragAmount.x / density, dragAmount.y / density)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = !isExpanded,
                enter = fadeIn(tween(120)),
                exit = fadeOut(tween(80))
            ) {
                CollapsedIsland(vertical = isVertical)
            }
            AnimatedVisibility(
                visible = isExpanded,
                enter = ContentEnter,
                exit = ContentExit
            ) {
                if (isVertical) {
                    ExpandedVerticalDock(
                        favorites = favorites,
                        config = config,
                        iconBitmaps = iconBitmaps,
                        isLeft = config.position == NotchPosition.LEFT_CENTER,
                        onLaunch = { app ->
                            scope.launch {
                                AppLauncher.launch(app)
                                if (config.hideOnAppLaunch) onExpandedChange(false)
                            }
                        },
                        onOpenSettings = onOpenSettings
                    )
                } else {
                    ExpandedHorizontalDock(
                        favorites = favorites,
                        config = config,
                        iconBitmaps = iconBitmaps,
                        isBottom = isBottom,
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
}

@Composable
private fun CollapsedIsland(vertical: Boolean) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = if (vertical) Modifier.graphicsLayer { rotationZ = 90f } else Modifier
        ) {
            Box(Modifier.size(5.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.28f)))
            Box(Modifier.size(width = 18.dp, height = 5.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.16f)))
            Box(Modifier.size(5.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.28f)))
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ExpandedHorizontalDock(
    favorites: List<AppItem>,
    config: NotchConfig,
    iconBitmaps: Map<String, ImageBitmap>,
    isBottom: Boolean,
    onLaunch: (AppItem) -> Unit,
    onOpenSettings: () -> Unit
) {
    val centers = remember { mutableStateMapOf<Int, Float>() }
    var pointerX by remember { mutableStateOf<Float?>(null) }
    val closestName = remember(pointerX, centers.toMap(), favorites, config) {
        var best = 1.08f
        var name: String? = null
        favorites.forEachIndexed { index, app ->
            val scale = dockScale(pointerX, centers[index], config.dockMagnificationRadius, config.dockScaleFactor)
            if (scale > best) {
                best = scale
                name = app.name
            }
        }
        name
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                start = 10.dp,
                end = 8.dp,
                top = if (isBottom) 10.dp else 6.dp,
                bottom = if (isBottom) 6.dp else 10.dp
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = if (isBottom) Arrangement.Bottom else Arrangement.Top
    ) {
        if (!isBottom && config.showTooltips) {
            DockLabel(closestName)
        }
        Row(
            modifier = Modifier
                .weight(1f, fill = false)
                .onPointerEvent(PointerEventType.Move, PointerEventPass.Main) {
                    pointerX = it.changes.firstOrNull()?.position?.x
                }
                .onPointerEvent(PointerEventType.Exit, PointerEventPass.Main) {
                    pointerX = null
                },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            favorites.forEachIndexed { index, app ->
                val center = centers[index]
                val scale = dockScale(pointerX, center, config.dockMagnificationRadius, config.dockScaleFactor)
                DockIcon(
                    app = app,
                    iconBitmap = iconBitmaps[app.id],
                    scale = scale,
                    lift = if (isBottom) -(scale - 1f) * 10f else (scale - 1f) * 8f,
                    liftVertical = false,
                    baseSizeDp = config.baseIconSizeDp,
                    onPositioned = { centers[index] = it },
                    onClick = { onLaunch(app) }
                )
            }
            QuietIconButton(onClick = onOpenSettings)
        }
        if (isBottom && config.showTooltips) {
            DockLabel(closestName)
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ExpandedVerticalDock(
    favorites: List<AppItem>,
    config: NotchConfig,
    iconBitmaps: Map<String, ImageBitmap>,
    isLeft: Boolean,
    onLaunch: (AppItem) -> Unit,
    onOpenSettings: () -> Unit
) {
    val centers = remember { mutableStateMapOf<Int, Float>() }
    var pointerY by remember { mutableStateOf<Float?>(null) }
    val closestName = remember(pointerY, centers.toMap(), favorites, config) {
        var best = 1.08f
        var name: String? = null
        favorites.forEachIndexed { index, app ->
            val scale = dockScale(pointerY, centers[index], config.dockMagnificationRadius, config.dockScaleFactor)
            if (scale > best) {
                best = scale
                name = app.name
            }
        }
        name
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (isLeft) Arrangement.Start else Arrangement.End
    ) {
        if (!isLeft && config.showTooltips) {
            DockLabel(closestName)
        }
        Column(
            modifier = Modifier
                .onPointerEvent(PointerEventType.Move, PointerEventPass.Main) {
                    pointerY = it.changes.firstOrNull()?.position?.y
                }
                .onPointerEvent(PointerEventType.Exit, PointerEventPass.Main) {
                    pointerY = null
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            favorites.forEachIndexed { index, app ->
                val center = centers[index]
                val scale = dockScale(pointerY, center, config.dockMagnificationRadius, config.dockScaleFactor)
                DockIcon(
                    app = app,
                    iconBitmap = iconBitmaps[app.id],
                    scale = scale,
                    lift = if (isLeft) (scale - 1f) * 8f else -(scale - 1f) * 8f,
                    liftVertical = true,
                    baseSizeDp = config.baseIconSizeDp,
                    onPositioned = { centers[index] = it },
                    onClick = { onLaunch(app) }
                )
            }
            QuietIconButton(onClick = onOpenSettings)
        }
        if (isLeft && config.showTooltips) {
            DockLabel(closestName)
        }
    }
}

@Composable
private fun DockIcon(
    app: AppItem,
    iconBitmap: ImageBitmap?,
    scale: Float,
    lift: Float,
    liftVertical: Boolean,
    baseSizeDp: Int,
    onPositioned: (Float) -> Unit,
    onClick: () -> Unit
) {
    val hovering = scale > 1.02f
    val animatedScale by animateFloatAsState(
        targetValue = scale,
        animationSpec = if (hovering) {
            tween(durationMillis = 16)
        } else {
            spring(dampingRatio = 0.9f, stiffness = 700f)
        },
        label = "dockScale"
    )
    val animatedLift by animateFloatAsState(
        targetValue = lift,
        animationSpec = if (hovering) tween(16) else spring(dampingRatio = 0.9f, stiffness = 700f),
        label = "dockLift"
    )
    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .padding(4.dp)
            .size(baseSizeDp.dp)
            .onGloballyPositioned { coordinates ->
                val pos = coordinates.positionInParent()
                onPositioned(
                    if (liftVertical) pos.y + coordinates.size.height / 2f
                    else pos.x + coordinates.size.width / 2f
                )
            }
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
                if (liftVertical) translationX = animatedLift * density else translationY = animatedLift * density
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
        if (iconBitmap != null) {
            Image(
                bitmap = iconBitmap,
                contentDescription = app.name,
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.High,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(Modifier.fillMaxSize().background(Color(0xFF2C2C2E)))
        }
    }
}

@Composable
private fun QuietIconButton(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        modifier = Modifier
            .padding(start = 6.dp)
            .size(22.dp)
            .hoverable(interaction)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            ),
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
private fun DockLabel(text: String?) {
    Box(
        modifier = Modifier.height(16.dp).padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text.orEmpty(),
            color = TextPrimary.copy(alpha = 0.9f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun dockScale(
    pointer: Float?,
    center: Float?,
    radius: Float,
    peakScale: Float
): Float {
    if (pointer == null || center == null) return 1f
    val distance = abs(pointer - center)
    if (distance >= radius) return 1f
    val normalized = (distance / radius) * (PI.toFloat() / 2f)
    val factor = cos(normalized) * cos(normalized)
    return 1f + (peakScale - 1f) * factor
}
