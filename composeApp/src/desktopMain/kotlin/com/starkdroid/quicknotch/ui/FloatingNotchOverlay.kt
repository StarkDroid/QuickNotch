package com.starkdroid.quicknotch.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starkdroid.quicknotch.datasource.AppLauncher
import com.starkdroid.quicknotch.datasource.Win32ShellIconLoader
import com.starkdroid.quicknotch.model.AppItem
import com.starkdroid.quicknotch.model.FavoritesRepository
import com.starkdroid.quicknotch.model.NotchConfig
import com.starkdroid.quicknotch.model.NotchPosition
import com.starkdroid.quicknotch.theme.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun FloatingNotchOverlay(
    repository: FavoritesRepository,
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val config by repository.config.collectAsState()
    val favorites by repository.favorites.collectAsState()
    val scope = rememberCoroutineScope()

    var collapseJob by remember { mutableStateOf<Job?>(null) }
    val iconBitmaps = remember { mutableStateMapOf<String, ImageBitmap>() }

    LaunchedEffect(favorites) {
        favorites.forEach { app ->
            if (!iconBitmaps.containsKey(app.id)) {
                launch {
                    val bmp = Win32ShellIconLoader.loadIcon(app)
                    iconBitmaps[app.id] = bmp
                }
            }
        }
    }

    val isVertical = config.position == NotchPosition.LEFT_CENTER || config.position == NotchPosition.RIGHT_CENTER
    val isBottom = config.position in listOf(
        NotchPosition.BOTTOM_CENTER,
        NotchPosition.BOTTOM_LEFT,
        NotchPosition.BOTTOM_RIGHT
    )

    // Animated dimensions matching strictly the notch size
    val targetWidth = if (isVertical) {
        if (isExpanded) config.expandedNotchHeightDp.dp else config.collapsedPillHeightDp.dp
    } else {
        if (isExpanded) {
            (favorites.size * (config.baseIconSizeDp + 18) + 140).dp.coerceIn(360.dp, 980.dp)
        } else {
            config.collapsedPillWidthDp.dp
        }
    }

    val targetHeight = if (isVertical) {
        if (isExpanded) {
            (favorites.size * (config.baseIconSizeDp + 18) + 140).dp.coerceIn(320.dp, 800.dp)
        } else {
            config.collapsedPillWidthDp.dp
        }
    } else {
        if (isExpanded) config.expandedNotchHeightDp.dp else config.collapsedPillHeightDp.dp
    }

    val animatedWidth by animateDpAsState(
        targetValue = targetWidth,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f),
        label = "notchWidth"
    )

    val animatedHeight by animateDpAsState(
        targetValue = targetHeight,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f),
        label = "notchHeight"
    )

    // Notch edge shape tailored to each position: contact edge has 0.dp radius to stick seamlessly to bezel
    val notchShape = when {
        isVertical -> {
            if (config.position == NotchPosition.LEFT_CENTER) {
                if (isExpanded) {
                    RoundedCornerShape(topEnd = 22.dp, bottomEnd = 22.dp, topStart = 0.dp, bottomStart = 0.dp)
                } else {
                    RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp, topStart = 0.dp, bottomStart = 0.dp)
                }
            } else {
                if (isExpanded) {
                    RoundedCornerShape(topStart = 22.dp, bottomStart = 22.dp, topEnd = 0.dp, bottomEnd = 0.dp)
                } else {
                    RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp, topEnd = 0.dp, bottomEnd = 0.dp)
                }
            }
        }
        isBottom -> {
            if (isExpanded) {
                RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 0.dp, bottomEnd = 0.dp)
            } else {
                RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 0.dp, bottomEnd = 0.dp)
            }
        }
        else -> {
            if (isExpanded) {
                RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp, topStart = 0.dp, topEnd = 0.dp)
            } else {
                RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp, topStart = 0.dp, topEnd = 0.dp)
            }
        }
    }

    val borderBrush = when {
        config.position == NotchPosition.LEFT_CENTER -> {
            Brush.horizontalGradient(
                colors = listOf(
                    Color.Transparent,
                    AccentIndigo.copy(alpha = if (isExpanded) 0.5f else 0.2f),
                    Color.White.copy(alpha = 0.35f)
                )
            )
        }
        config.position == NotchPosition.RIGHT_CENTER -> {
            Brush.horizontalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.35f),
                    AccentIndigo.copy(alpha = if (isExpanded) 0.5f else 0.2f),
                    Color.Transparent
                )
            )
        }
        isBottom -> {
            Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.35f),
                    AccentIndigo.copy(alpha = if (isExpanded) 0.5f else 0.2f),
                    Color.Transparent
                )
            )
        }
        else -> {
            Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    AccentIndigo.copy(alpha = if (isExpanded) 0.5f else 0.2f),
                    Color.White.copy(alpha = 0.35f)
                )
            )
        }
    }

    // Outer container aligns notch flush to the contact edge with zero spacing
    val containerAlignment = when {
        config.position == NotchPosition.LEFT_CENTER -> Alignment.CenterStart
        config.position == NotchPosition.RIGHT_CENTER -> Alignment.CenterEnd
        isBottom -> Alignment.BottomCenter
        else -> Alignment.TopCenter
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = containerAlignment
    ) {
        Box(
            modifier = Modifier
                .width(animatedWidth)
                .height(animatedHeight)
                .shadow(elevation = if (isExpanded) 14.dp else 5.dp, shape = notchShape)
                .clip(notchShape)
                .background(
                    Brush.verticalGradient(
                        colors = if (isBottom) {
                            listOf(
                                NotchSurface.copy(alpha = 0.92f),
                                GlassPillBackground.copy(alpha = 0.95f),
                                NotchDeepBlack.copy(alpha = 0.98f)
                            )
                        } else {
                            listOf(
                                NotchDeepBlack.copy(alpha = 0.98f),
                                GlassPillBackground.copy(alpha = 0.95f),
                                NotchSurface.copy(alpha = 0.92f)
                            )
                        }
                    )
                )
                .border(
                    BorderStroke(1.2.dp, borderBrush),
                    shape = notchShape
                )
                .onPointerEvent(PointerEventType.Enter) {
                    collapseJob?.cancel()
                    onExpandedChange(true)
                }
                .onPointerEvent(PointerEventType.Exit) {
                    collapseJob?.cancel()
                    collapseJob = scope.launch {
                        delay(config.collapseDelayMs)
                        onExpandedChange(false)
                    }
                }
                .onPointerEvent(PointerEventType.Move) {
                    collapseJob?.cancel()
                    if (!isExpanded) onExpandedChange(true)
                },
            contentAlignment = Alignment.Center
        ) {
            if (!isExpanded) {
                if (isVertical) {
                    CollapsedVerticalPill(onOpenSettings = onOpenSettings)
                } else {
                    CollapsedHorizontalPill(onOpenSettings = onOpenSettings)
                }
            } else {
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
private fun CollapsedHorizontalPill(
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(AccentGreen.copy(alpha = 0.85f))
                .border(1.dp, Color.White.copy(alpha = 0.6f), CircleShape)
        )

        Text(
            text = "QuickNotch",
            style = AppTypography.labelMedium,
            color = TextSecondary,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.4.sp
        )

        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(AccentIndigo.copy(alpha = 0.85f))
        )
    }
}

@Composable
private fun CollapsedVerticalPill(
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(AccentGreen.copy(alpha = 0.85f))
                .border(1.dp, Color.White.copy(alpha = 0.6f), CircleShape)
        )

        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(AccentIndigo.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Apps,
                contentDescription = null,
                tint = AccentCyan,
                modifier = Modifier.size(11.dp)
            )
        }

        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(AccentIndigo.copy(alpha = 0.85f))
        )
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
    val itemPositions = remember { mutableStateMapOf<Int, Float>() }
    var dockPointerX by remember { mutableStateOf<Float?>(null) }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(AccentIndigo.copy(alpha = 0.2f))
                .border(1.dp, AccentIndigo.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Apps,
                contentDescription = "Apps",
                tint = AccentCyan,
                modifier = Modifier.size(18.dp)
            )
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .onPointerEvent(PointerEventType.Move) { event ->
                    dockPointerX = event.changes.firstOrNull()?.position?.x
                }
                .onPointerEvent(PointerEventType.Exit) {
                    dockPointerX = null
                },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = if (isBottom) Alignment.Bottom else Alignment.CenterVertically
        ) {
            favorites.forEachIndexed { index, app ->
                val centerX = itemPositions[index]
                val (scale, offsetY) = calculateHorizontalFisheye(
                    pointerX = dockPointerX,
                    centerX = centerX,
                    radius = config.dockMagnificationRadius,
                    peakScale = config.dockScaleFactor,
                    isBottom = isBottom
                )

                HorizontalDockItemIcon(
                    app = app,
                    iconBitmap = iconBitmaps[app.id],
                    scale = scale,
                    offsetY = offsetY,
                    baseSizeDp = config.baseIconSizeDp,
                    showTooltip = config.showTooltips && (scale > 1.25f),
                    isBottom = isBottom,
                    onPositioned = { x -> itemPositions[index] = x },
                    onClick = { onLaunch(app) }
                )
            }
        }

        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(NotchCard)
                .border(1.dp, NotchBorder, CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = TextPrimary,
                modifier = Modifier.size(17.dp)
            )
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
    val itemPositions = remember { mutableStateMapOf<Int, Float>() }
    var dockPointerY by remember { mutableStateOf<Float?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 14.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(AccentIndigo.copy(alpha = 0.2f))
                .border(1.dp, AccentIndigo.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Apps,
                contentDescription = "Apps",
                tint = AccentCyan,
                modifier = Modifier.size(18.dp)
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .onPointerEvent(PointerEventType.Move) { event ->
                    dockPointerY = event.changes.firstOrNull()?.position?.y
                }
                .onPointerEvent(PointerEventType.Exit) {
                    dockPointerY = null
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            favorites.forEachIndexed { index, app ->
                val centerY = itemPositions[index]
                val (scale, offsetX) = calculateVerticalFisheye(
                    pointerY = dockPointerY,
                    centerY = centerY,
                    radius = config.dockMagnificationRadius,
                    peakScale = config.dockScaleFactor,
                    isLeft = isLeft
                )

                VerticalDockItemIcon(
                    app = app,
                    iconBitmap = iconBitmaps[app.id],
                    scale = scale,
                    offsetX = offsetX,
                    baseSizeDp = config.baseIconSizeDp,
                    showTooltip = config.showTooltips && (scale > 1.25f),
                    isLeft = isLeft,
                    onPositioned = { y -> itemPositions[index] = y },
                    onClick = { onLaunch(app) }
                )
            }
        }

        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(NotchCard)
                .border(1.dp, NotchBorder, CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = TextPrimary,
                modifier = Modifier.size(17.dp)
            )
        }
    }
}

@Composable
private fun HorizontalDockItemIcon(
    app: AppItem,
    iconBitmap: ImageBitmap?,
    scale: Float,
    offsetY: Float,
    baseSizeDp: Int,
    showTooltip: Boolean,
    isBottom: Boolean,
    onPositioned: (Float) -> Unit,
    onClick: () -> Unit
) {
    val animatedScale by animateFloatAsState(
        targetValue = scale,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 500f),
        label = "iconScale"
    )

    val animatedOffsetY by animateFloatAsState(
        targetValue = offsetY,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 500f),
        label = "iconOffset"
    )

    Box(
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .onGloballyPositioned { coordinates ->
                val bounds = coordinates.boundsInParent()
                onPositioned(bounds.center.x)
            },
        contentAlignment = if (isBottom) Alignment.BottomCenter else Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = if (isBottom) Arrangement.Bottom else Arrangement.Center,
            modifier = Modifier.offset(y = animatedOffsetY.dp)
        ) {
            if (isBottom && showTooltip) {
                TooltipBadge(app.name)
            }

            Box(
                modifier = Modifier
                    .size((baseSizeDp * animatedScale).dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center
            ) {
                if (iconBitmap != null) {
                    Image(
                        bitmap = iconBitmap,
                        contentDescription = app.name,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(AccentIndigo))
                }
            }

            if (!isBottom && showTooltip) {
                TooltipBadge(app.name)
            }
        }
    }
}

@Composable
private fun VerticalDockItemIcon(
    app: AppItem,
    iconBitmap: ImageBitmap?,
    scale: Float,
    offsetX: Float,
    baseSizeDp: Int,
    showTooltip: Boolean,
    isLeft: Boolean,
    onPositioned: (Float) -> Unit,
    onClick: () -> Unit
) {
    val animatedScale by animateFloatAsState(
        targetValue = scale,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 500f),
        label = "iconScale"
    )

    val animatedOffsetX by animateFloatAsState(
        targetValue = offsetX,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 500f),
        label = "iconOffsetX"
    )

    Box(
        modifier = Modifier
            .padding(vertical = 4.dp)
            .onGloballyPositioned { coordinates ->
                val bounds = coordinates.boundsInParent()
                onPositioned(bounds.center.y)
            },
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.offset(x = animatedOffsetX.dp)
        ) {
            if (!isLeft && showTooltip) {
                TooltipBadge(app.name)
                Spacer(modifier = Modifier.width(6.dp))
            }

            Box(
                modifier = Modifier
                    .size((baseSizeDp * animatedScale).dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center
            ) {
                if (iconBitmap != null) {
                    Image(
                        bitmap = iconBitmap,
                        contentDescription = app.name,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(AccentIndigo))
                }
            }

            if (isLeft && showTooltip) {
                Spacer(modifier = Modifier.width(6.dp))
                TooltipBadge(app.name)
            }
        }
    }
}

@Composable
private fun TooltipBadge(text: String) {
    Text(
        text = text,
        style = AppTypography.labelSmall.copy(
            shadow = androidx.compose.ui.graphics.Shadow(
                color = Color.Black.copy(alpha = 0.8f),
                blurRadius = 4f
            )
        ),
        color = Color.White,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
    )
}

private fun calculateHorizontalFisheye(
    pointerX: Float?,
    centerX: Float?,
    radius: Float,
    peakScale: Float,
    isBottom: Boolean
): Pair<Float, Float> {
    if (pointerX == null || centerX == null) {
        return 1.0f to 0.0f
    }

    val distance = abs(pointerX - centerX)
    if (distance >= radius) {
        return 1.0f to 0.0f
    }

    val normalized = (distance / radius) * (PI.toFloat() / 2f)
    val factor = cos(normalized) * cos(normalized)

    val scale = 1.0f + (peakScale - 1.0f) * factor
    val liftOffsetY = if (isBottom) -(scale - 1.0f) * 16.0f else (scale - 1.0f) * 6.0f

    return scale to liftOffsetY
}

private fun calculateVerticalFisheye(
    pointerY: Float?,
    centerY: Float?,
    radius: Float,
    peakScale: Float,
    isLeft: Boolean
): Pair<Float, Float> {
    if (pointerY == null || centerY == null) {
        return 1.0f to 0.0f
    }

    val distance = abs(pointerY - centerY)
    if (distance >= radius) {
        return 1.0f to 0.0f
    }

    val normalized = (distance / radius) * (PI.toFloat() / 2f)
    val factor = cos(normalized) * cos(normalized)

    val scale = 1.0f + (peakScale - 1.0f) * factor
    val liftOffsetX = if (isLeft) (scale - 1.0f) * 16.0f else -(scale - 1.0f) * 16.0f

    return scale to liftOffsetX
}
