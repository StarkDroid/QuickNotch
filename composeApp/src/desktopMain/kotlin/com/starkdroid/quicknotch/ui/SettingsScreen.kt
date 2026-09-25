package com.starkdroid.quicknotch.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import com.starkdroid.quicknotch.model.NotchStyle
import com.starkdroid.quicknotch.model.NotchSurface
import com.starkdroid.quicknotch.theme.*
import java.util.Locale
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
                // Pill-segmented tab selector, mirrored on the web module's chip+"segmented"
                // control language: one raised pill runs the whole strip, the
                // active segment is a solid indigo pill sitting inside it.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(999.dp))
                        .background(FlatSurface)
                        .border(1.dp, FlatBorder, RoundedCornerShape(999.dp))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    tabs.forEachIndexed { index, title ->
                        val isSelected = selectedTab == index
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(999.dp))
                                .shadow(
                                    elevation = if (isSelected) 4.dp else 0.dp,
                                    shape = RoundedCornerShape(999.dp),
                                    ambientColor = WebIndigo.copy(alpha = 0.5f),
                                    spotColor = WebIndigo.copy(alpha = 0.4f)
                                )
                                .background(if (isSelected) WebIndigo else Color.Transparent)
                                .clickable { selectedTab = index }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = tabIcons[index],
                                    contentDescription = title,
                                    tint = if (isSelected) Color.White else TextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(7.dp))
                                Text(
                                    text = title,
                                    style = AppTypography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
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
                        onMove = { from, to -> repository.reorderFavorites(from, to) },
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
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .shadow(
                        elevation = 3.dp,
                        shape = RoundedCornerShape(10.dp),
                        ambientColor = WebIndigo.copy(alpha = 0.35f),
                        spotColor = WebIndigo.copy(alpha = 0.3f)
                    )
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = stringResource(Res.string.app_name),
                    style = AppTypography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    fontSize = 17.sp
                )
                Text(
                    text = stringResource(Res.string.settings_tagline).uppercase(Locale.ROOT),
                    style = AppTypography.bodySmall,
                    color = TextMuted,
                    fontSize = 9.sp,
                    letterSpacing = 0.8.sp
                )
            }
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .clickable(onClick = onOpenAbout)
                .background(FlatSurfaceElevated)
                .border(1.dp, FlatBorder, RoundedCornerShape(999.dp))
                .padding(horizontal = 6.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = stringResource(Res.string.about),
                tint = WebIndigoLight,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// Enum labels resolve from composeResources at the UI layer, keeping the model
// enums free of user-facing text.
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
            shape = RoundedCornerShape(20.dp)
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
                    Column {
                        EyebrowText(stringResource(Res.string.eyebrow_about))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(Res.string.about_header),
                            style = AppTypography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(Res.string.close),
                            tint = TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Image(
                    painter = painterResource(Res.drawable.quicknotch_icon),
                    contentDescription = stringResource(Res.string.app_name),
                    modifier = Modifier
                        .size(58.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .shadow(
                            elevation = 6.dp,
                            shape = RoundedCornerShape(16.dp),
                            ambientColor = WebIndigo.copy(alpha = 0.35f),
                            spotColor = WebIndigo.copy(alpha = 0.3f)
                        )
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = stringResource(Res.string.about_product_name),
                    style = AppTypography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(8.dp))

                SettingsChip(
                    text = stringResource(Res.string.about_version, stringResource(Res.string.app_version)),
                    tint = WebCyan
                )

                Spacer(modifier = Modifier.height(14.dp))

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
                        SettingsChip(
                            text = tag,
                            tint = TextSecondary,
                            tintSoft = FlatSurfaceElevated
                        )
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