package com.starkdroid.quicknotch.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starkdroid.quicknotch.datasource.AppIconLoader
import com.starkdroid.quicknotch.datasource.HostPlatform
import com.starkdroid.quicknotch.datasource.InstalledAppsScanner
import com.starkdroid.quicknotch.generated.resources.*
import com.starkdroid.quicknotch.media.MediaController
import com.starkdroid.quicknotch.model.AppItem
import com.starkdroid.quicknotch.model.ClockDateFormat
import com.starkdroid.quicknotch.model.FavoritesRepository
import com.starkdroid.quicknotch.model.NotchConfig
import com.starkdroid.quicknotch.model.NotchPosition
import com.starkdroid.quicknotch.model.NotchStyle
import com.starkdroid.quicknotch.model.NotchSurface
import com.starkdroid.quicknotch.theme.*
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

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
                                    expandedNotchHeightDp = 78,
                                    clockDate = true,
                                    clockTime = true,
                                    clock24Hour = false,
                                    clockDateFormat = ClockDateFormat.DD_MMM
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