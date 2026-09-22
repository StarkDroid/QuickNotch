package com.starkdroid.quicknotch.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starkdroid.quicknotch.datasource.AppIconLoader
import com.starkdroid.quicknotch.datasource.HostPlatform
import com.starkdroid.quicknotch.datasource.InstalledAppsScanner
import com.starkdroid.quicknotch.datasource.StartupManager
import com.starkdroid.quicknotch.generated.resources.*
import com.starkdroid.quicknotch.media.MediaController
import com.starkdroid.quicknotch.media.MediaSession
import com.starkdroid.quicknotch.model.AppItem
import com.starkdroid.quicknotch.model.FavoritesRepository
import com.starkdroid.quicknotch.model.NotchConfig
import com.starkdroid.quicknotch.model.NotchPosition
import com.starkdroid.quicknotch.model.NotchStyle
import com.starkdroid.quicknotch.model.NotchSurface
import com.starkdroid.quicknotch.theme.*
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
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
private val FlatCard = Color(0xFF121622)

@Composable
fun SettingsScreen(
    repository: FavoritesRepository,
    media: MediaController,
    onClose: () -> Unit
) {
    val config by repository.config.collectAsState()
    val favorites by repository.favorites.collectAsState()
    val liveSession by media.session.collectAsState()

    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf(
        stringResource(Res.string.tab_dock_sizing),
        stringResource(Res.string.tab_widgets),
        stringResource(Res.string.tab_behavior)
    )
    val tabIcons = listOf(Icons.Default.Tune, Icons.Default.Apps, Icons.Default.ToggleOn)

    var showAboutDialog by remember { mutableStateOf(false) }

    var scannedApps by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var isScanning by remember { mutableStateOf(false) }

    // Generated once; a static tiled shader keeps scrolling cheap on the
    // software renderer instead of re-sampling noise every frame.
    val noiseBrush = remember { NoiseGenerator.getNoiseBrush() }

    LaunchedEffect(Unit) {
        // With a warm cache the scan returns without suspending, so skip the
        // spinner entirely instead of flashing it for a frame.
        if (InstalledAppsScanner.isScanPending) isScanning = true
        scannedApps = InstalledAppsScanner.scanAllInstalledApps()
        isScanning = false
    }

    // Warm the shared icon cache in the background as soon as the scan lands, so
    // a row scrolling into view reads a ready bitmap synchronously instead of
    // starting a load (and visibly popping) mid-scroll. Deliberately sequential:
    // each item holds at most one of the loader's permits, so loads demanded by
    // rows the user is actually looking at always jump ahead of the backlog.
    LaunchedEffect(scannedApps) {
        if (scannedApps.isEmpty()) return@LaunchedEffect
        delay(350)
        scannedApps.forEach { app -> AppIconLoader.loadIcon(app) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(FlatBg)
            .background(noiseBrush)
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
                                    style = NotchStyle.DYNAMIC_ISLAND,
                                    surface = NotchSurface.SOLID,
                                    dockScaleFactor = 1.35f,
                                    baseIconSizeDp = 38,
                                    collapsedPillWidthDp = 150,
                                    collapsedPillHeightDp = 30,
                                    expandedNotchHeightDp = 78
                                )
                            }
                        }
                    )
                    1 -> WidgetsTab(
                        config = config,
                        favorites = favorites,
                        scannedApps = scannedApps,
                        searchQuery = searchQuery,
                        isScanning = isScanning,
                        liveSession = liveSession,
                        media = media,
                        onSearchQueryChange = { searchQuery = it },
                        onAddFavorite = { repository.addFavorite(it) },
                        onRemoveFavorite = { repository.removeFavorite(it) },
                        onMoveUp = { i -> if (i > 0) repository.reorderFavorites(i, i - 1) },
                        onMoveDown = { i -> if (i < favorites.size - 1) repository.reorderFavorites(i, i + 1) },
                        onUpdate = { repository.updateConfig(it) },
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
            Image(
                painter = painterResource(Res.drawable.quicknotch_icon),
                contentDescription = stringResource(Res.string.app_name),
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = stringResource(Res.string.app_name),
                    style = AppTypography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = stringResource(Res.string.settings_tagline),
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
                contentDescription = stringResource(Res.string.about),
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
                    text = stringResource(Res.string.dock_sizing_header),
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
                    Text(stringResource(Res.string.reset_defaults), style = AppTypography.labelSmall, color = AccentRed)
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
                            text = stringResource(Res.string.screen_edge_positioning),
                            style = AppTypography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = stringResource(Res.string.screen_edge_positioning_desc),
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
                                    text = config.position.label(),
                                    style = AppTypography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    EdgePositionSelector(
                        selectedPosition = config.position,
                        onSelectPosition = { pos -> onUpdate { it.copy(position = pos) } }
                    )
                }
            }
        }

        // Section 1b: Appearance — silhouette and material, side by side
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
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    AppearanceDropdown(
                        label = stringResource(Res.string.notch_style),
                        options = NotchStyle.entries.toList(),
                        selected = config.style,
                        optionLabel = { it.label() },
                        onSelect = { style -> onUpdate { it.copy(style = style) } },
                        modifier = Modifier.weight(1f)
                    )
                    AppearanceDropdown(
                        label = stringResource(Res.string.surface_theme),
                        options = NotchSurface.entries.toList(),
                        selected = config.surface,
                        optionLabel = { it.label() },
                        onSelect = { surface -> onUpdate { it.copy(surface = surface) } },
                        modifier = Modifier.weight(1f)
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
                        text = stringResource(Res.string.fisheye_title),
                        style = AppTypography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        text = stringResource(Res.string.fisheye_desc),
                        style = AppTypography.bodySmall,
                        color = TextSecondary,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    MinimalistSliderRow(
                        title = stringResource(Res.string.peak_scale_title),
                        valueLabel = stringResource(
                            Res.string.value_scale,
                            String.format("%.2f", config.dockScaleFactor.coerceIn(1.10f, 1.45f))
                        ),
                        value = config.dockScaleFactor.coerceIn(1.10f, 1.45f),
                        valueRange = 1.10f..1.45f,
                        onValueChange = { onUpdate { cfg -> cfg.copy(dockScaleFactor = it) } }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    MinimalistSliderRow(
                        title = stringResource(Res.string.base_icon_size_title),
                        valueLabel = stringResource(
                            Res.string.value_dp,
                            config.baseIconSizeDp.coerceIn(32, 44).toString()
                        ),
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
                                text = stringResource(Res.string.show_labels_title),
                                style = AppTypography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = TextPrimary
                            )
                            Text(
                                text = stringResource(Res.string.show_labels_desc),
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
                        text = stringResource(Res.string.geometry_title),
                        style = AppTypography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        text = stringResource(Res.string.geometry_desc),
                        style = AppTypography.bodySmall,
                        color = TextSecondary,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    MinimalistSliderRow(
                        title = stringResource(Res.string.collapsed_width_title),
                        valueLabel = stringResource(
                            Res.string.value_dp,
                            config.collapsedPillWidthDp.coerceIn(140, 190).toString()
                        ),
                        value = config.collapsedPillWidthDp.toFloat().coerceIn(140f, 190f),
                        valueRange = 140f..190f,
                        onValueChange = { onUpdate { cfg -> cfg.copy(collapsedPillWidthDp = it.toInt()) } }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    MinimalistSliderRow(
                        title = stringResource(Res.string.expanded_height_title),
                        valueLabel = stringResource(
                            Res.string.value_dp,
                            config.expandedNotchHeightDp.coerceIn(70, 86).toString()
                        ),
                        value = config.expandedNotchHeightDp.toFloat().coerceIn(70f, 86f),
                        valueRange = 70f..86f,
                        onValueChange = { onUpdate { cfg -> cfg.copy(expandedNotchHeightDp = it.toInt()) } }
                    )
                }
            }
        }
    }
}

// Enum labels resolve from composeResources at the UI layer, keeping the model
// enums free of user-facing text.
@Composable
internal fun NotchPosition.label(): String = stringResource(
    when (this) {
        NotchPosition.TOP_CENTER -> Res.string.edge_top_center
        NotchPosition.RIGHT_CENTER -> Res.string.edge_right_center
        NotchPosition.BOTTOM_CENTER -> Res.string.edge_bottom_center
        NotchPosition.LEFT_CENTER -> Res.string.edge_left_center
    }
)

@Composable
internal fun NotchStyle.label(): String = stringResource(
    when (this) {
        NotchStyle.DYNAMIC_ISLAND -> Res.string.style_dynamic_island
        NotchStyle.FLOATING_PILL -> Res.string.style_floating_pill
        NotchStyle.DRAG_HANDLE -> Res.string.style_drag_handle
    }
)

@Composable
internal fun NotchSurface.label(): String = stringResource(
    when (this) {
        NotchSurface.SOLID -> Res.string.surface_solid
        NotchSurface.TRANSLUCENT -> Res.string.surface_translucent
    }
)

/**
 * A miniature screen showing the four supported edge anchors. Only edge centres
 * are selectable — corners and free-form placement were removed so the notch
 * always sits perfectly centred on one side and never has to be dragged.
 */
@Composable
private fun EdgePositionSelector(
    selectedPosition: NotchPosition,
    onSelectPosition: (NotchPosition) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(width = 132.dp, height = 92.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(FlatCard)
            .border(1.dp, FlatBorder, RoundedCornerShape(10.dp))
    ) {
        EdgeAnchor(Alignment.TopCenter, NotchPosition.TOP_CENTER, true, selectedPosition, onSelectPosition)
        EdgeAnchor(Alignment.BottomCenter, NotchPosition.BOTTOM_CENTER, true, selectedPosition, onSelectPosition)
        EdgeAnchor(Alignment.CenterStart, NotchPosition.LEFT_CENTER, false, selectedPosition, onSelectPosition)
        EdgeAnchor(Alignment.CenterEnd, NotchPosition.RIGHT_CENTER, false, selectedPosition, onSelectPosition)
    }
}

@Composable
private fun BoxScope.EdgeAnchor(
    alignment: Alignment,
    position: NotchPosition,
    horizontal: Boolean,
    selectedPosition: NotchPosition,
    onSelectPosition: (NotchPosition) -> Unit
) {
    val isSelected = selectedPosition == position
    Box(
        modifier = Modifier
            .align(alignment)
            .padding(9.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSelected) AccentIndigo.copy(alpha = 0.26f) else FlatSurfaceElevated)
            .border(
                if (isSelected) 1.5.dp else 1.dp,
                if (isSelected) AccentIndigoLight else FlatBorder,
                RoundedCornerShape(6.dp)
            )
            .clickable { onSelectPosition(position) }
            .padding(
                horizontal = if (horizontal) 11.dp else 5.dp,
                vertical = if (horizontal) 5.dp else 11.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(
                    width = if (horizontal) 20.dp else 4.dp,
                    height = if (horizontal) 4.dp else 20.dp
                )
                .clip(RoundedCornerShape(2.dp))
                .background(if (isSelected) AccentCyan else TextMuted.copy(alpha = 0.45f))
        )
    }
}

/**
 * A minimal, flat selection dropdown (label above, current value, popup menu)
 * shared by the style and surface pickers. Popup rows are plain composables, so
 * opening it costs one small popup — no preview textures are ever rendered.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> AppearanceDropdown(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = modifier) {
        Text(
            text = label,
            style = AppTypography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(FlatCard)
                .border(1.dp, FlatBorder, RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .padding(horizontal = 10.dp, vertical = 9.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = optionLabel(selected),
                    style = AppTypography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(15.dp)
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(FlatSurfaceElevated)
            ) {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelect(option)
                                expanded = false
                            }
                            .background(if (option == selected) FlatSurfaceSelected else Color.Transparent)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = optionLabel(option),
                            style = AppTypography.labelSmall,
                            color = if (option == selected) AccentIndigoLight else TextSecondary
                        )
                        if (option == selected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = AccentIndigoLight,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WidgetsTab(
    config: NotchConfig,
    favorites: List<AppItem>,
    scannedApps: List<AppItem>,
    searchQuery: String,
    isScanning: Boolean,
    liveSession: MediaSession?,
    media: MediaController,
    onSearchQueryChange: (String) -> Unit,
    onAddFavorite: (AppItem) -> Unit,
    onRemoveFavorite: (String) -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
    onUpdate: ((NotchConfig) -> NotchConfig) -> Unit,
    onResetDefaults: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = stringResource(Res.string.widgets_header),
                    style = AppTypography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary
                )
                Text(
                    text = stringResource(Res.string.widgets_header_desc),
                    style = AppTypography.bodySmall,
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            item(key = "pinnedApps") {
                WidgetAccordion(
                    icon = Icons.Default.Apps,
                    title = stringResource(Res.string.widget_apps_title),
                    subtitle = stringResource(Res.string.widget_apps_desc),
                    enabled = config.widgets.pinnedAppsEnabled,
                    onToggleEnabled = { enabled ->
                        onUpdate { it.copy(widgets = it.widgets.copy(pinnedAppsEnabled = enabled)) }
                    }
                ) {
                    PinnedAppsTab(
                        favorites = favorites,
                        scannedApps = scannedApps,
                        searchQuery = searchQuery,
                        isScanning = isScanning,
                        onSearchQueryChange = onSearchQueryChange,
                        onAddFavorite = onAddFavorite,
                        onRemoveFavorite = onRemoveFavorite,
                        onMoveUp = onMoveUp,
                        onMoveDown = onMoveDown,
                        onResetDefaults = onResetDefaults,
                        fixedHeight = true
                    )
                }
            }
            item(key = "nowPlaying") {
                WidgetAccordion(
                    icon = Icons.Default.MusicNote,
                    title = stringResource(Res.string.widget_music_title),
                    subtitle = stringResource(Res.string.widget_music_desc),
                    enabled = config.widgets.nowPlayingEnabled,
                    onToggleEnabled = { enabled ->
                        onUpdate { it.copy(widgets = it.widgets.copy(nowPlayingEnabled = enabled)) }
                    }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp)
                            .padding(bottom = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        NowPlayingStatus(config = config, liveSession = liveSession)

                        BehaviorSwitchRow(
                            title = stringResource(Res.string.widget_music_play_pause),
                            subtitle = stringResource(Res.string.widget_music_play_pause_desc),
                            checked = config.widgets.musicShowPlayPause,
                            onCheckedChange = { enabled ->
                                onUpdate { it.copy(widgets = it.widgets.copy(musicShowPlayPause = enabled)) }
                            }
                        )

                        Text(
                            text = stringResource(Res.string.widget_scroll_hint),
                            style = AppTypography.bodySmall,
                            color = TextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )

                        if (!isStyleEligibleForLiveActivity(config)) {
                            Text(
                                text = stringResource(Res.string.widget_style_warning),
                                style = AppTypography.bodySmall,
                                color = TextMuted,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WidgetAccordion(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = FlatSurface),
        border = BorderStroke(1.dp, FlatBorder),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(FlatSurfaceElevated)
                        .border(1.dp, FlatBorder, RoundedCornerShape(9.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = AccentIndigoLight,
                        modifier = Modifier.size(19.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = AppTypography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        text = subtitle,
                        style = AppTypography.bodySmall,
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
                MinimalistRectSwitch(checked = enabled, onCheckedChange = onToggleEnabled)
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(19.dp)
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(tween(160)),
                exit = shrinkVertically() + fadeOut(tween(120))
            ) {
                Box(modifier = Modifier.fillMaxWidth()) { content() }
            }
        }
    }
}

/**
 * The widget's live status pill: a green dot when a media session is actively
 * feeding the notch, otherwise the rendered state the user can compare against.
 */
@Composable
private fun NowPlayingStatus(
    config: NotchConfig,
    liveSession: MediaSession?
) {
    val live = isMusicLive(config, liveSession)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(FlatCard)
            .border(1.dp, FlatBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(if (live) AccentGreen else TextMuted.copy(alpha = 0.4f))
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (live && liveSession != null) {
                stringResource(Res.string.widget_now_playing, liveSession.title, liveSession.artist.ifBlank { "—" })
            } else {
                stringResource(Res.string.widget_no_session)
            },
            style = AppTypography.bodySmall,
            color = if (live) TextPrimary else TextSecondary,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
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
    onResetDefaults: () -> Unit,
    fixedHeight: Boolean = false
) {
    val filteredApps = remember(scannedApps, searchQuery) {
        if (searchQuery.isBlank()) scannedApps else scannedApps.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }
    // Precomputed once per favorites change so each row's "already pinned" check
    // is O(1) during scrolling instead of rescanning the list.
    val pinnedPaths = remember(favorites) {
        favorites.mapTo(HashSet()) { it.executablePath.lowercase() }
    }
    // Swing's file chooser is not a composable surface, so its strings are
    // resolved here and captured as plain values.
    val pickMacTitle = stringResource(Res.string.pick_mac_title)
    val pickMacFilter = stringResource(Res.string.pick_mac_filter)
    val pickWindowsFilter = stringResource(Res.string.pick_win_filter)

    // Embedded in the widgets accordion the content gets a fixed height so the
    // inner LazyColumns are never measured against an unbounded constraint.
    Row(
        modifier = (if (fixedHeight) {
            Modifier.fillMaxWidth().height(430.dp)
        } else {
            Modifier.fillMaxSize()
        }).padding(bottom = if (fixedHeight) 0.dp else 16.dp),
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
                            text = stringResource(Res.string.pinned_items),
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
                        Text(stringResource(Res.string.reset), style = AppTypography.labelSmall, color = AccentRed)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (favorites.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(Res.string.no_apps_pinned),
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
                        itemsIndexed(favorites, key = { _, item -> item.id }, contentType = { _, _ -> "pinnedRow" }) { index, item ->
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
                    text = stringResource(Res.string.add_applications),
                    style = AppTypography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Hand-rolled compact search field: Material's OutlinedTextField
                    // enforces a 56dp minimum height, so pinning it to 44dp clipped
                    // the placeholder. BasicTextField has no such floor.
                    val searchFocus = remember { FocusRequester() }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(FlatCard)
                            .border(1.dp, FlatBorder, RoundedCornerShape(8.dp))
                            .clickable { searchFocus.requestFocus() }
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(color = TextPrimary, fontSize = 13.sp),
                            cursorBrush = SolidColor(AccentIndigo),
                            modifier = Modifier
                                .fillMaxHeight()
                                .focusRequester(searchFocus),
                            decorationBox = { innerTextField ->
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Search,
                                        contentDescription = null,
                                        tint = TextMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(modifier = Modifier.weight(1f)) {
                                        if (searchQuery.isEmpty()) {
                                            Text(
                                                text = stringResource(Res.string.search_hint),
                                                color = TextMuted,
                                                fontSize = 12.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        innerTextField()
                                    }
                                    if (searchQuery.isNotEmpty()) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        IconButton(
                                            onClick = { onSearchQueryChange("") },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = stringResource(Res.string.clear),
                                                tint = TextMuted,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        )
                    }

                    Button(
                        onClick = {
                            pickCustomApplication(
                                macDialogTitle = pickMacTitle,
                                macFilterDescription = pickMacFilter,
                                windowsFilterDescription = pickWindowsFilter
                            )?.let(onAddFavorite)
                        },
                        modifier = Modifier.height(44.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = FlatSurfaceElevated),
                        border = BorderStroke(1.dp, FlatBorder),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(Res.string.browse), color = TextPrimary, style = AppTypography.labelMedium)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (isScanning) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AccentIndigo, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                    }
                } else if (filteredApps.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(Res.string.no_matches), style = AppTypography.bodySmall, color = TextMuted)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        // Stable keys let LazyColumn recycle rows without
                        // discarding their (already loaded) icon state on scroll.
                        items(filteredApps, key = { it.executablePath }, contentType = { "availableRow" }) { app ->
                            AvailableAppRow(
                                app = app,
                                isPinned = app.executablePath.lowercase() in pinnedPaths,
                                onAdd = { onAddFavorite(app) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Resolves an app icon with no visible pop-in while scrolling: the shared
 * in-memory cache is read synchronously on first composition, and only a cold
 * miss falls back to a background load. Combined with stable LazyColumn keys,
 * rows keep their icons when recycled, so the list stays smooth.
 */
@Composable
private fun rememberAppIcon(app: AppItem): ImageBitmap? {
    var iconBitmap by remember(app.id) { mutableStateOf(AppIconLoader.getCachedIcon(app.id)) }
    LaunchedEffect(app.id) {
        if (iconBitmap == null) {
            iconBitmap = AppIconLoader.loadIcon(app)
        }
    }
    return iconBitmap
}

private fun pickCustomApplication(
    macDialogTitle: String,
    macFilterDescription: String,
    windowsFilterDescription: String
): AppItem? {
    val chooser = JFileChooser()
    if (HostPlatform.isMac) {
        chooser.fileSelectionMode = JFileChooser.FILES_AND_DIRECTORIES
        chooser.fileFilter = object : javax.swing.filechooser.FileFilter() {
            override fun accept(f: File): Boolean = f.isDirectory
            override fun getDescription(): String = macFilterDescription
        }
        chooser.dialogTitle = macDialogTitle
    } else {
        chooser.fileFilter = FileNameExtensionFilter(windowsFilterDescription, "exe", "lnk")
    }
    val ret = chooser.showOpenDialog(null)
    if (ret != JFileChooser.APPROVE_OPTION) return null
    val file = chooser.selectedFile ?: return null
    return AppItem(
        id = "custom_${UUID.randomUUID()}",
        name = file.nameWithoutExtension,
        executablePath = file.absolutePath,
        iconPath = file.absolutePath,
        isCustom = true
    )
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
    val iconBitmap = rememberAppIcon(item)

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
                key(item.id) {
                    Crossfade(
                        targetState = iconBitmap,
                        animationSpec = tween(220),
                        label = "pinnedIconFade"
                    ) { bitmap ->
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap,
                                contentDescription = item.name,
                                modifier = Modifier.size(30.dp)
                            )
                        } else {
                            ShimmerBox(
                                modifier = Modifier.fillMaxSize(),
                                shape = RoundedCornerShape(7.dp)
                            )
                        }
                    }
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
                    stringResource(Res.string.move_up),
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
                    stringResource(Res.string.move_down),
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
                    stringResource(Res.string.remove),
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
    val iconBitmap = rememberAppIcon(app)

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
                // key() on the app id: LazyColumn reuses slots while scrolling, and
                // without this the Crossfade would animate from the *previous*
                // row's icon into the new one — visible as scroll jitter.
                key(app.id) {
                    Crossfade(
                        targetState = iconBitmap,
                        animationSpec = tween(220),
                        label = "availableIconFade"
                    ) { bitmap ->
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap,
                                contentDescription = app.name,
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            ShimmerBox(
                                modifier = Modifier.fillMaxSize(),
                                shape = RoundedCornerShape(6.dp)
                            )
                        }
                    }
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
                Text(stringResource(Res.string.pinned_badge), style = AppTypography.labelSmall, color = AccentGreen, fontSize = 11.sp)
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
                Icon(Icons.Default.Add, stringResource(Res.string.add), tint = AccentCyan, modifier = Modifier.size(13.dp))
                Spacer(modifier = Modifier.width(3.dp))
                Text(stringResource(Res.string.add), color = AccentCyan, style = AppTypography.labelSmall, fontSize = 11.sp)
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
                        text = stringResource(Res.string.behavior_header),
                        style = AppTypography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )

                    BehaviorSwitchRow(
                        title = stringResource(
                            if (HostPlatform.isMac) Res.string.autostart_title_mac
                            else Res.string.autostart_title_windows
                        ),
                        subtitle = stringResource(
                            if (HostPlatform.isMac) Res.string.autostart_desc_mac
                            else Res.string.autostart_desc_windows
                        ),
                        checked = config.autoStartOnBoot,
                        onCheckedChange = { checked ->
                            StartupManager.setAutoStartEnabled(checked)
                            onUpdate { it.copy(autoStartOnBoot = checked) }
                        }
                    )

                    HorizontalDivider(color = FlatBorder)

                    BehaviorSwitchRow(
                        title = stringResource(Res.string.always_on_top_title),
                        subtitle = stringResource(Res.string.always_on_top_desc),
                        checked = config.alwaysOnTop,
                        onCheckedChange = { checked ->
                            onUpdate { it.copy(alwaysOnTop = checked) }
                        }
                    )

                    HorizontalDivider(color = FlatBorder)

                    BehaviorSwitchRow(
                        title = stringResource(Res.string.collapse_on_launch_title),
                        subtitle = stringResource(Res.string.collapse_on_launch_desc),
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
        animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.82f, stiffness = 550f),
        label = "switchOffset"
    )

    // Deep high-contrast track with rich depth
    val trackBg = if (checked) {
        Brush.horizontalGradient(
            colors = listOf(Color(0xFF3730A3), Color(0xFF4F46E5), Color(0xFF6366F1))
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(Color(0xFF07090E), Color(0xFF0C1018))
        )
    }

    val trackBorder = if (checked) {
        Color(0xFF6366F1)
    } else {
        Color(0xFF1E2638)
    }

    val thumbBg = if (checked) Color(0xFFFFFFFF) else Color(0xFF526075)
    val thumbBorder = if (checked) Color(0x26000000) else Color(0x33FFFFFF)

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
                .shadow(if (checked) 2.dp else 1.dp, RoundedCornerShape(3.5.dp))
                .clip(RoundedCornerShape(3.5.dp))
                .background(thumbBg)
                .border(0.5.dp, thumbBorder, RoundedCornerShape(3.5.dp))
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
                        text = stringResource(Res.string.about_header),
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
                            contentDescription = stringResource(Res.string.close),
                            tint = TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Image(
                    painter = painterResource(Res.drawable.quicknotch_icon),
                    contentDescription = stringResource(Res.string.app_name),
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(Res.string.about_product_name),
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
                        text = stringResource(Res.string.about_version, stringResource(Res.string.app_version)),
                        style = AppTypography.labelSmall,
                        color = AccentCyan,
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(Res.string.about_description),
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
                    val tags = if (HostPlatform.isMac) {
                        listOf(
                            stringResource(Res.string.about_tag_kotlin),
                            stringResource(Res.string.about_tag_compose),
                            stringResource(Res.string.about_tag_macos)
                        )
                    } else {
                        listOf(
                            stringResource(Res.string.about_tag_kotlin),
                            stringResource(Res.string.about_tag_compose),
                            stringResource(Res.string.about_tag_windows)
                        )
                    }
                    tags.forEach { tag ->
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
                    text = stringResource(Res.string.about_copyright),
                    style = AppTypography.labelSmall,
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }
        }
    }
}
