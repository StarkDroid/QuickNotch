package com.starkdroid.quicknotch.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.draw.scale
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
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val config by repository.config.collectAsState()
    val favorites by repository.favorites.collectAsState()
    val scope = rememberCoroutineScope()

    var isExpanded by remember { mutableStateOf(false) }
    var pointerX by remember { mutableStateOf<Float?>(null) }
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

    val targetWidth = if (isExpanded) {
        (favorites.size * (config.baseIconSizeDp + 18) + 140).dp.coerceIn(360.dp, 980.dp)
    } else {
        config.collapsedPillWidthDp.dp
    }

    val targetHeight = if (isExpanded) {
        config.expandedNotchHeightDp.dp
    } else {
        config.collapsedPillHeightDp.dp
    }

    val animatedWidth by animateDpAsState(
        targetValue = targetWidth,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
        label = "notchWidth"
    )

    val animatedHeight by animateDpAsState(
        targetValue = targetHeight,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
        label = "notchHeight"
    )

    val notchShape = if (isExpanded) {
        RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp, topStart = 4.dp, topEnd = 4.dp)
    } else {
        RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp, topStart = 0.dp, topEnd = 0.dp)
    }

    Box(
        modifier = modifier
            .fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier
                .width(animatedWidth)
                .height(animatedHeight)
                .shadow(elevation = if (isExpanded) 16.dp else 6.dp, shape = notchShape)
                .clip(notchShape)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            NotchDeepBlack.copy(alpha = 0.98f),
                            GlassPillBackground.copy(alpha = 0.95f),
                            NotchSurface.copy(alpha = 0.92f)
                        )
                    )
                )
                .border(
                    BorderStroke(
                        width = 1.2.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.35f),
                                AccentIndigo.copy(alpha = if (isExpanded) 0.5f else 0.2f),
                                Color.White.copy(alpha = 0.08f)
                            )
                        )
                    ),
                    shape = notchShape
                )
                .onPointerEvent(PointerEventType.Enter) {
                    collapseJob?.cancel()
                    isExpanded = true
                }
                .onPointerEvent(PointerEventType.Exit) {
                    pointerX = null
                    collapseJob?.cancel()
                    collapseJob = scope.launch {
                        delay(config.collapseDelayMs)
                        isExpanded = false
                    }
                }
                .onPointerEvent(PointerEventType.Move) { event ->
                    collapseJob?.cancel()
                    isExpanded = true
                    pointerX = event.changes.firstOrNull()?.position?.x
                },
            contentAlignment = Alignment.Center
        ) {
            if (!isExpanded) {
                CollapsedDynamicIslandPill(onOpenSettings = onOpenSettings)
            } else {
                ExpandedDynamicDock(
                    favorites = favorites,
                    config = config,
                    iconBitmaps = iconBitmaps,
                    pointerX = pointerX,
                    onLaunch = { app ->
                        scope.launch {
                            AppLauncher.launch(app)
                            if (config.hideOnAppLaunch) {
                                isExpanded = false
                            }
                        }
                    },
                    onOpenSettings = onOpenSettings
                )
            }
        }
    }
}

@Composable
private fun CollapsedDynamicIslandPill(
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Camera / Sensor Aesthetic Glow
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
private fun ExpandedDynamicDock(
    favorites: List<AppItem>,
    config: NotchConfig,
    iconBitmaps: Map<String, ImageBitmap>,
    pointerX: Float?,
    onLaunch: (AppItem) -> Unit,
    onOpenSettings: () -> Unit
) {
    val itemPositions = remember { mutableStateMapOf<Int, Float>() }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left Status Icon
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

        // Center: App Icons with Mac Dock Fisheye Wave
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.Bottom
        ) {
            favorites.forEachIndexed { index, app ->
                val centerX = itemPositions[index]
                val (scale, offsetY) = calculateFisheyeEffect(
                    pointerX = pointerX,
                    centerX = centerX,
                    radius = config.dockMagnificationRadius,
                    peakScale = config.dockScaleFactor
                )

                DockItemIcon(
                    app = app,
                    iconBitmap = iconBitmaps[app.id],
                    scale = scale,
                    offsetY = offsetY,
                    baseSizeDp = config.baseIconSizeDp,
                    showTooltip = config.showTooltips && (scale > 1.25f),
                    onPositioned = { x -> itemPositions[index] = x },
                    onClick = { onLaunch(app) }
                )
            }
        }

        // Right Settings Button
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
private fun DockItemIcon(
    app: AppItem,
    iconBitmap: ImageBitmap?,
    scale: Float,
    offsetY: Float,
    baseSizeDp: Int,
    showTooltip: Boolean,
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
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom,
            modifier = Modifier.offset(y = animatedOffsetY.dp)
        ) {
            // Tooltip Badge
            if (showTooltip) {
                Box(
                    modifier = Modifier
                        .padding(bottom = 6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(TooltipGlassBackground)
                        .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = app.name,
                        style = AppTypography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // App Icon
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
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(AccentIndigo)
                    )
                }
            }
        }
    }
}

private fun calculateFisheyeEffect(
    pointerX: Float?,
    centerX: Float?,
    radius: Float,
    peakScale: Float
): Pair<Float, Float> {
    if (pointerX == null || centerX == null) {
        return 1.0f to 0.0f
    }

    val distance = abs(pointerX - centerX)
    if (distance >= radius) {
        return 1.0f to 0.0f
    }

    // Authentic cosine-squared bell curve
    val normalized = (distance / radius) * (PI.toFloat() / 2f)
    val factor = cos(normalized) * cos(normalized)

    val scale = 1.0f + (peakScale - 1.0f) * factor
    val liftOffsetY = -(scale - 1.0f) * 16.0f

    return scale to liftOffsetY
}
