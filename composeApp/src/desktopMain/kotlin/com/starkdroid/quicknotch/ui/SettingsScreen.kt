package com.starkdroid.quicknotch.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starkdroid.quicknotch.datasource.InstalledAppsScanner
import com.starkdroid.quicknotch.datasource.Win32ShellIconLoader
import com.starkdroid.quicknotch.datasource.WindowsStartupManager
import com.starkdroid.quicknotch.model.AppItem
import com.starkdroid.quicknotch.model.FavoritesRepository
import com.starkdroid.quicknotch.model.NotchConfig
import com.starkdroid.quicknotch.model.NotchPosition
import com.starkdroid.quicknotch.theme.*
import java.io.File
import java.util.UUID
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

// Flat Minimalist Color Tokens
private val FlatBg = Color(0xFF0C0E14)
private val FlatSurface = Color(0xFF131722)
private val FlatSurfaceElevated = Color(0xFF181E2E)
private val FlatSurfaceSelected = Color(0xFF1B2236)
private val FlatBorder = Color(0xFF22283A)
private val FlatBorderActive = Color(0xFF6366F1)
private val FlatCard = Color(0xFF121622)

@Composable
fun SettingsScreen(
    repository: FavoritesRepository,
    onClose: () -> Unit
) {
    val config by repository.config.collectAsState()
    val favorites by repository.favorites.collectAsState()

    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Dock & Sizing", "Pinned Apps", "Behavior")
    val tabIcons = listOf(Icons.Default.Tune, Icons.Default.Apps, Icons.Default.ToggleOn)

    var showAboutDialog by remember { mutableStateOf(false) }

    var scannedApps by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var isScanning by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isScanning = true
        scannedApps = InstalledAppsScanner.scanAllInstalledApps()
        isScanning = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(FlatBg)
            .background(NoiseGenerator.getNoiseBrush())
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                SettingsTopBar(
                    onOpenAbout = { showAboutDialog = true }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 24.dp)
            ) {
                // Minimalist Flat Tab Selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(FlatSurface)
                        .border(1.dp, FlatBorder, RoundedCornerShape(10.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    tabs.forEachIndexed { index, title ->
                        val isSelected = selectedTab == index
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) FlatSurfaceElevated else Color.Transparent)
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) FlatBorder.copy(alpha = 0.9f) else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { selectedTab = index }
                                .padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = tabIcons[index],
                                    contentDescription = title,
                                    tint = if (isSelected) AccentIndigoLight else TextMuted,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = title,
                                    style = AppTypography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isSelected) Color.White else TextSecondary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                when (selectedTab) {
                    0 -> DockSizingTab(
                        config = config,
                        onUpdate = { repository.updateConfig(it) },
                        onResetDefaults = {
                            repository.updateConfig {
                                it.copy(
                                    dockScaleFactor = 1.65f,
                                    dockMagnificationRadius = 130f,
                                    baseIconSizeDp = 38,
                                    collapsedPillWidthDp = 160,
                                    collapsedPillHeightDp = 30,
                                    expandedNotchHeightDp = 78
                                )
                            }
                        }
                    )
                    1 -> PinnedAppsTab(
                        favorites = favorites,
                        scannedApps = scannedApps,
                        searchQuery = searchQuery,
                        isScanning = isScanning,
                        onSearchQueryChange = { searchQuery = it },
                        onAddFavorite = { repository.addFavorite(it) },
                        onRemoveFavorite = { repository.removeFavorite(it) },
                        onMoveUp = { i -> if (i > 0) repository.reorderFavorites(i, i - 1) },
                        onMoveDown = { i -> if (i < favorites.size - 1) repository.reorderFavorites(i, i + 1) },
                        onResetDefaults = { repository.resetToDefaults() }
                    )
                    2 -> BehaviorTab(
                        config = config,
                        onUpdate = { repository.updateConfig(it) }
                    )
                }
            }
        }

        if (showAboutDialog) {
            AboutDialog(onDismiss = { showAboutDialog = false })
        }
    }
}

@Composable
private fun SettingsTopBar(
    onOpenAbout: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(FlatSurfaceElevated)
                    .border(1.dp, FlatBorder, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Widgets,
                    contentDescription = "QuickNotch",
                    tint = AccentIndigoLight,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "QuickNotch",
                    style = AppTypography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "Dynamic Island & Floating Dock Customizer",
                    style = AppTypography.bodySmall,
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        }

        IconButton(
            onClick = onOpenAbout,
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(FlatSurfaceElevated)
                .border(1.dp, FlatBorder, RoundedCornerShape(8.dp))
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "About",
                tint = TextSecondary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun DockSizingTab(
    config: NotchConfig,
    onUpdate: ((NotchConfig) -> NotchConfig) -> Unit,
    onResetDefaults: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        // Tab Header with Reset Defaults action
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Dock Layout & Sizing",
                    style = AppTypography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary
                )
                TextButton(
                    onClick = onResetDefaults,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null,
                        tint = AccentRed,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reset Defaults", style = AppTypography.labelSmall, color = AccentRed)
                }
            }
        }

        // Section 1: Minimalist 3x3 Screen Edge Positioning Grid
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = FlatSurface),
                border = BorderStroke(1.dp, FlatBorder),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Screen Edge Positioning",
                            style = AppTypography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "Click an edge or corner on the 3×3 grid to position the notch or dock",
                            style = AppTypography.bodySmall,
                            color = TextSecondary,
                            fontSize = 12.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(FlatSurfaceElevated)
                                .border(1.dp, AccentIndigo.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(AccentCyan)
                                )
                                Spacer(modifier = Modifier.width(7.dp))
                                Text(
                                    text = config.position.displayName,
                                    style = AppTypography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Minimalist3x3PositionGrid(
                        selectedPosition = config.position,
                        onSelectPosition = { pos -> onUpdate { it.copy(position = pos) } }
                    )
                }
            }
        }

        // Section 2: Icon Fish-eye Magnification
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = FlatSurface),
                border = BorderStroke(1.dp, FlatBorder),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Icon Fish-eye Magnification",
                        style = AppTypography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Cursor glide wave effect and magnification curve",
                        style = AppTypography.bodySmall,
                        color = TextSecondary,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    MinimalistSliderRow(
                        title = "Magnification Peak Scale",
                        valueLabel = "${String.format("%.2f", config.dockScaleFactor.coerceIn(1.15f, 1.90f))}x",
                        value = config.dockScaleFactor.coerceIn(1.15f, 1.90f),
                        valueRange = 1.15f..1.90f,
                        onValueChange = { onUpdate { cfg -> cfg.copy(dockScaleFactor = it) } }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    MinimalistSliderRow(
                        title = "Magnification Wave Radius",
                        valueLabel = "${config.dockMagnificationRadius.coerceIn(90f, 160f).toInt()} px",
                        value = config.dockMagnificationRadius.coerceIn(90f, 160f),
                        valueRange = 90f..160f,
                        onValueChange = { onUpdate { cfg -> cfg.copy(dockMagnificationRadius = it) } }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    MinimalistSliderRow(
                        title = "Base Icon Size",
                        valueLabel = "${config.baseIconSizeDp.coerceIn(32, 44)} dp",
                        value = config.baseIconSizeDp.toFloat().coerceIn(32f, 44f),
                        valueRange = 32f..44f,
                        onValueChange = { onUpdate { cfg -> cfg.copy(baseIconSizeDp = it.toInt()) } }
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = FlatBorder.copy(alpha = 0.6f))
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Show App Labels",
                                style = AppTypography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = TextPrimary
                            )
                            Text(
                                text = "Display app name labels when hovering over icons",
                                style = AppTypography.labelSmall,
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                        MinimalistRectSwitch(
                            checked = config.showTooltips,
                            onCheckedChange = { checked ->
                                onUpdate { it.copy(showTooltips = checked) }
                            }
                        )
                    }
                }
            }
        }

        // Section 3: Notch Dimensions & Geometry
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = FlatSurface),
                border = BorderStroke(1.dp, FlatBorder),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Notch Geometry",
                        style = AppTypography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Collapsed pill and expanded dock dimensions",
                        style = AppTypography.bodySmall,
                        color = TextSecondary,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    MinimalistSliderRow(
                        title = "Collapsed Pill Width",
                        valueLabel = "${config.collapsedPillWidthDp.coerceIn(140, 190)} dp",
                        value = config.collapsedPillWidthDp.toFloat().coerceIn(140f, 190f),
                        valueRange = 140f..190f,
                        onValueChange = { onUpdate { cfg -> cfg.copy(collapsedPillWidthDp = it.toInt()) } }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    MinimalistSliderRow(
                        title = "Expanded Dock Height",
                        valueLabel = "${config.expandedNotchHeightDp.coerceIn(70, 86)} dp",
                        value = config.expandedNotchHeightDp.toFloat().coerceIn(70f, 86f),
                        valueRange = 70f..86f,
                        onValueChange = { onUpdate { cfg -> cfg.copy(expandedNotchHeightDp = it.toInt()) } }
                    )
                }
            }
        }
    }
}

private enum class PositionCellType {
    TOP_LEFT, TOP_CENTER, TOP_RIGHT,
    LEFT_CENTER, CENTER_DISABLED, RIGHT_CENTER,
    BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT
}

@Composable
private fun Minimalist3x3PositionGrid(
    selectedPosition: NotchPosition,
    onSelectPosition: (NotchPosition) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(FlatCard)
            .border(1.dp, FlatBorder, RoundedCornerShape(10.dp))
            .padding(6.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            // Row 1: Top-Left, Top-Center, Top-Right
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                PositionGridCell(
                    isSelected = selectedPosition == NotchPosition.TOP_LEFT,
                    onClick = { onSelectPosition(NotchPosition.TOP_LEFT) },
                    positionType = PositionCellType.TOP_LEFT
                )
                PositionGridCell(
                    isSelected = selectedPosition == NotchPosition.TOP_CENTER,
                    onClick = { onSelectPosition(NotchPosition.TOP_CENTER) },
                    positionType = PositionCellType.TOP_CENTER
                )
                PositionGridCell(
                    isSelected = selectedPosition == NotchPosition.TOP_RIGHT,
                    onClick = { onSelectPosition(NotchPosition.TOP_RIGHT) },
                    positionType = PositionCellType.TOP_RIGHT
                )
            }

            // Row 2: Left-Center, Center (Screen), Right-Center
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                PositionGridCell(
                    isSelected = selectedPosition == NotchPosition.LEFT_CENTER,
                    onClick = { onSelectPosition(NotchPosition.LEFT_CENTER) },
                    positionType = PositionCellType.LEFT_CENTER
                )
                PositionGridCell(
                    isSelected = false,
                    onClick = {},
                    positionType = PositionCellType.CENTER_DISABLED
                )
                PositionGridCell(
                    isSelected = selectedPosition == NotchPosition.RIGHT_CENTER,
                    onClick = { onSelectPosition(NotchPosition.RIGHT_CENTER) },
                    positionType = PositionCellType.RIGHT_CENTER
                )
            }

            // Row 3: Bottom-Left, Bottom-Center, Bottom-Right
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                PositionGridCell(
                    isSelected = selectedPosition == NotchPosition.BOTTOM_LEFT,
                    onClick = { onSelectPosition(NotchPosition.BOTTOM_LEFT) },
                    positionType = PositionCellType.BOTTOM_LEFT
                )
                PositionGridCell(
                    isSelected = selectedPosition == NotchPosition.BOTTOM_CENTER,
                    onClick = { onSelectPosition(NotchPosition.BOTTOM_CENTER) },
                    positionType = PositionCellType.BOTTOM_CENTER
                )
                PositionGridCell(
                    isSelected = selectedPosition == NotchPosition.BOTTOM_RIGHT,
                    onClick = { onSelectPosition(NotchPosition.BOTTOM_RIGHT) },
                    positionType = PositionCellType.BOTTOM_RIGHT
                )
            }
        }
    }
}

@Composable
private fun PositionGridCell(
    isSelected: Boolean,
    onClick: () -> Unit,
    positionType: PositionCellType,
    modifier: Modifier = Modifier
) {
    val isCenter = positionType == PositionCellType.CENTER_DISABLED
    val borderColor = when {
        isSelected -> AccentIndigoLight
        isCenter -> FlatBorder.copy(alpha = 0.35f)
        else -> FlatBorder
    }
    val bgColor = when {
        isSelected -> AccentIndigo.copy(alpha = 0.22f)
        isCenter -> Color(0xFF0E111B)
        else -> FlatSurfaceElevated
    }

    val indicatorColor = if (isSelected) AccentCyan else TextMuted.copy(alpha = 0.45f)

    Box(
        modifier = modifier
            .size(width = 46.dp, height = 36.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .border(if (isSelected) 1.5.dp else 1.dp, borderColor, RoundedCornerShape(6.dp))
            .then(if (!isCenter) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        when (positionType) {
            PositionCellType.TOP_LEFT -> {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 5.dp, top = 4.dp)
                        .width(12.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(bottomStart = 2.dp, bottomEnd = 2.dp))
                        .background(indicatorColor)
                )
            }
            PositionCellType.TOP_CENTER -> {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 4.dp)
                        .width(18.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(bottomStart = 2.dp, bottomEnd = 2.dp))
                        .background(indicatorColor)
                )
            }
            PositionCellType.TOP_RIGHT -> {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 5.dp, top = 4.dp)
                        .width(12.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(bottomStart = 2.dp, bottomEnd = 2.dp))
                        .background(indicatorColor)
                )
            }
            PositionCellType.LEFT_CENTER -> {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 4.dp)
                        .width(4.dp)
                        .height(16.dp)
                        .clip(RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp))
                        .background(indicatorColor)
                )
            }
            PositionCellType.CENTER_DISABLED -> {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(TextMuted.copy(alpha = 0.25f))
                )
            }
            PositionCellType.RIGHT_CENTER -> {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 4.dp)
                        .width(4.dp)
                        .height(16.dp)
                        .clip(RoundedCornerShape(topStart = 2.dp, bottomStart = 2.dp))
                        .background(indicatorColor)
                )
            }
            PositionCellType.BOTTOM_LEFT -> {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 5.dp, bottom = 4.dp)
                        .width(12.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                        .background(indicatorColor)
                )
            }
            PositionCellType.BOTTOM_CENTER -> {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 4.dp)
                        .width(18.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                        .background(indicatorColor)
                )
            }
            PositionCellType.BOTTOM_RIGHT -> {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 5.dp, bottom = 4.dp)
                        .width(12.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                        .background(indicatorColor)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MinimalistSliderRow(
    title: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = AppTypography.bodySmall, color = TextSecondary)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(FlatSurfaceElevated)
                    .border(1.dp, FlatBorder, RoundedCornerShape(4.dp))
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
                Text(
                    text = valueLabel,
                    style = AppTypography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    fontSize = 11.sp
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.height(20.dp),
            thumb = {
                Box(
                    modifier = Modifier
                        .size(width = 8.dp, height = 14.dp)
                        .clip(RoundedCornerShape(2.5.dp))
                        .background(Color(0xFFE2E8F0))
                        .border(0.5.dp, Color(0xFF64748B), RoundedCornerShape(2.5.dp))
                )
            },
            track = { sliderState ->
                SliderDefaults.Track(
                    sliderState = sliderState,
                    modifier = Modifier.height(2.5.dp),
                    colors = SliderDefaults.colors(
                        activeTrackColor = Color(0xFFA0AEC0),
                        inactiveTrackColor = Color(0xFF1E2436)
                    )
                )
            }
        )
    }
}

@Composable
private fun PinnedAppsTab(
    favorites: List<AppItem>,
    scannedApps: List<AppItem>,
    searchQuery: String,
    isScanning: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onAddFavorite: (AppItem) -> Unit,
    onRemoveFavorite: (String) -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
    onResetDefaults: () -> Unit
) {
    val filteredApps = remember(scannedApps, searchQuery) {
        if (searchQuery.isBlank()) scannedApps else scannedApps.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Left Column: Current Pinned Apps
        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            colors = CardDefaults.cardColors(containerColor = FlatSurface),
            border = BorderStroke(1.dp, FlatBorder),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Pinned Items",
                            style = AppTypography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(FlatSurfaceElevated)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${favorites.size}",
                                style = AppTypography.labelSmall,
                                color = AccentCyan,
                                fontSize = 11.sp
                            )
                        }
                    }

                    TextButton(
                        onClick = onResetDefaults,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = AccentRed, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reset", style = AppTypography.labelSmall, color = AccentRed)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (favorites.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No apps pinned.\nAdd apps from the list on the right.",
                            style = AppTypography.bodySmall,
                            color = TextMuted,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        itemsIndexed(favorites) { index, item ->
                            PinnedAppRow(
                                item = item,
                                canMoveUp = index > 0,
                                canMoveDown = index < favorites.size - 1,
                                onMoveUp = { onMoveUp(index) },
                                onMoveDown = { onMoveDown(index) },
                                onRemove = { onRemoveFavorite(item.id) }
                            )
                        }
                    }
                }
            }
        }

        // Right Column: Search & Add Apps
        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            colors = CardDefaults.cardColors(containerColor = FlatSurface),
            border = BorderStroke(1.dp, FlatBorder),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Add Applications",
                    style = AppTypography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = { Text("Search installed apps...", color = TextMuted, fontSize = 12.sp) },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchQueryChange("") }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextMuted, modifier = Modifier.size(14.dp))
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = FlatSurfaceElevated,
                            unfocusedContainerColor = FlatCard,
                            focusedBorderColor = AccentIndigo,
                            unfocusedBorderColor = FlatBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )

                    Button(
                        onClick = {
                            val chooser = JFileChooser()
                            chooser.fileFilter = FileNameExtensionFilter("Executables & Shortcuts (*.exe, *.lnk)", "exe", "lnk")
                            val ret = chooser.showOpenDialog(null)
                            if (ret == JFileChooser.APPROVE_OPTION) {
                                val file = chooser.selectedFile
                                onAddFavorite(
                                    AppItem(
                                        id = "custom_${UUID.randomUUID()}",
                                        name = file.nameWithoutExtension,
                                        executablePath = file.absolutePath,
                                        iconPath = file.absolutePath,
                                        isCustom = true
                                    )
                                )
                            }
                        },
                        modifier = Modifier.height(44.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = FlatSurfaceElevated),
                        border = BorderStroke(1.dp, FlatBorder),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Browse", color = TextPrimary, style = AppTypography.labelMedium)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (isScanning) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AccentIndigo, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                    }
                } else if (filteredApps.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No matching applications found", style = AppTypography.bodySmall, color = TextMuted)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        items(filteredApps.size) { i ->
                            val app = filteredApps[i]
                            val isAlreadyPinned = favorites.any { it.executablePath.equals(app.executablePath, ignoreCase = true) }

                            AvailableAppRow(
                                app = app,
                                isPinned = isAlreadyPinned,
                                onAdd = { onAddFavorite(app) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PinnedAppRow(
    item: AppItem,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit
) {
    var iconBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(item) {
        iconBitmap = Win32ShellIconLoader.loadIcon(item)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(FlatCard)
            .border(1.dp, FlatBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(FlatSurfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                if (iconBitmap != null) {
                    Image(
                        bitmap = iconBitmap!!,
                        contentDescription = item.name,
                        modifier = Modifier.size(30.dp)
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(AccentIndigo.copy(alpha = 0.3f)))
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = AppTypography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = File(item.executablePath).name,
                    style = AppTypography.labelSmall,
                    color = TextMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            IconButton(
                onClick = onMoveUp,
                enabled = canMoveUp,
                modifier = Modifier.size(26.dp)
            ) {
                Icon(
                    Icons.Default.ArrowUpward,
                    "Move Up",
                    tint = if (canMoveUp) TextSecondary else TextMuted.copy(alpha = 0.3f),
                    modifier = Modifier.size(15.dp)
                )
            }
            IconButton(
                onClick = onMoveDown,
                enabled = canMoveDown,
                modifier = Modifier.size(26.dp)
            ) {
                Icon(
                    Icons.Default.ArrowDownward,
                    "Move Down",
                    tint = if (canMoveDown) TextSecondary else TextMuted.copy(alpha = 0.3f),
                    modifier = Modifier.size(15.dp)
                )
            }
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(26.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    "Remove",
                    tint = AccentRed.copy(alpha = 0.85f),
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

@Composable
private fun AvailableAppRow(
    app: AppItem,
    isPinned: Boolean,
    onAdd: () -> Unit
) {
    var iconBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(app) {
        iconBitmap = Win32ShellIconLoader.loadIcon(app)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(FlatCard)
            .border(1.dp, FlatBorder.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .padding(horizontal = 9.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(FlatSurfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                if (iconBitmap != null) {
                    Image(
                        bitmap = iconBitmap!!,
                        contentDescription = app.name,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(AccentIndigo.copy(alpha = 0.3f)))
                }
            }
            Spacer(modifier = Modifier.width(9.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.name,
                    style = AppTypography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = File(app.executablePath).name,
                    style = AppTypography.labelSmall,
                    color = TextMuted,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (isPinned) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(AccentGreen.copy(alpha = 0.12f))
                    .border(1.dp, AccentGreen.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Text("Pinned", style = AppTypography.labelSmall, color = AccentGreen, fontSize = 11.sp)
            }
        } else {
            Button(
                onClick = onAdd,
                modifier = Modifier.height(26.dp),
                colors = ButtonDefaults.buttonColors(containerColor = FlatSurfaceElevated),
                border = BorderStroke(1.dp, FlatBorder),
                shape = RoundedCornerShape(5.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Icon(Icons.Default.Add, "Add", tint = AccentCyan, modifier = Modifier.size(13.dp))
                Spacer(modifier = Modifier.width(3.dp))
                Text("Add", color = AccentCyan, style = AppTypography.labelSmall, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun BehaviorTab(
    config: NotchConfig,
    onUpdate: ((NotchConfig) -> NotchConfig) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = FlatSurface),
                border = BorderStroke(1.dp, FlatBorder),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "System & Startup Behaviors",
                        style = AppTypography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )

                    BehaviorSwitchRow(
                        title = "Start on Windows Boot",
                        subtitle = "Launch QuickNotch automatically on user sign-in",
                        checked = config.autoStartOnBoot,
                        onCheckedChange = { checked ->
                            WindowsStartupManager.setAutoStartEnabled(checked)
                            onUpdate { it.copy(autoStartOnBoot = checked) }
                        }
                    )

                    HorizontalDivider(color = FlatBorder)

                    BehaviorSwitchRow(
                        title = "Always on Top",
                        subtitle = "Keep the floating notch and dock above other windows",
                        checked = config.alwaysOnTop,
                        onCheckedChange = { checked ->
                            onUpdate { it.copy(alwaysOnTop = checked) }
                        }
                    )

                    HorizontalDivider(color = FlatBorder)

                    BehaviorSwitchRow(
                        title = "Show App Labels",
                        subtitle = "Display app name labels when hovering over icons in the notch",
                        checked = config.showTooltips,
                        onCheckedChange = { checked ->
                            onUpdate { it.copy(showTooltips = checked) }
                        }
                    )

                    HorizontalDivider(color = FlatBorder)

                    BehaviorSwitchRow(
                        title = "Collapse on Launch",
                        subtitle = "Automatically collapse dock back into pill after launching an app",
                        checked = config.hideOnAppLaunch,
                        onCheckedChange = { checked ->
                            onUpdate { it.copy(hideOnAppLaunch = checked) }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun BehaviorSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = AppTypography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = TextPrimary
            )
            Text(
                text = subtitle,
                style = AppTypography.bodySmall,
                color = TextSecondary,
                fontSize = 12.sp
            )
        }
        MinimalistRectSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun MinimalistRectSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val thumbOffset by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (checked) 16.dp else 2.dp,
        animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.85f, stiffness = 600f),
        label = "switchOffset"
    )
    val trackBg = if (checked) AccentIndigo else FlatSurfaceElevated
    val trackBorder = if (checked) AccentIndigoLight else FlatBorder
    val thumbBg = if (checked) Color.White else TextMuted

    Box(
        modifier = modifier
            .width(36.dp)
            .height(20.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(trackBg)
            .border(1.dp, trackBorder, RoundedCornerShape(5.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 2.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(width = 16.dp, height = 14.dp)
                .clip(RoundedCornerShape(3.5.dp))
                .background(thumbBg)
        )
    }
}

@Composable
private fun AboutDialog(
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .width(420.dp)
                .clickable(enabled = false) {},
            colors = CardDefaults.cardColors(containerColor = FlatSurface),
            border = BorderStroke(1.dp, FlatBorder),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "About QuickNotch",
                        style = AppTypography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(FlatSurfaceElevated)
                        .border(1.dp, FlatBorder, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Widgets,
                        contentDescription = "QuickNotch",
                        tint = AccentIndigoLight,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "QuickNotch Desktop",
                    style = AppTypography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(6.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(AccentIndigo.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "Version 1.0.0",
                        style = AppTypography.labelSmall,
                        color = AccentCyan,
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Dynamic Island & Dock floating app launcher for Windows 10 & 11 built with Kotlin Multiplatform and Jetpack Compose Desktop.",
                    style = AppTypography.bodySmall,
                    color = TextSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    listOf("Kotlin", "Compose Desktop", "Win32 JNA").forEach { tag ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(FlatSurfaceElevated)
                                .border(1.dp, FlatBorder, RoundedCornerShape(4.dp))
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Text(tag, style = AppTypography.labelSmall, color = TextMuted, fontSize = 10.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "© 2025 StarkDroid. All rights reserved.",
                    style = AppTypography.labelSmall,
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }
        }
    }
}
