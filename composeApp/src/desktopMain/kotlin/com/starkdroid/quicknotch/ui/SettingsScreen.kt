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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starkdroid.quicknotch.datasource.AppIconLoader
import com.starkdroid.quicknotch.datasource.HostPlatform
import com.starkdroid.quicknotch.datasource.InstalledAppsScanner
import com.starkdroid.quicknotch.calendar.GoogleCalendar
import com.starkdroid.quicknotch.generated.resources.*
import com.starkdroid.quicknotch.media.MediaController
import com.starkdroid.quicknotch.model.AppItem
import com.starkdroid.quicknotch.model.ClockDateFormat
import com.starkdroid.quicknotch.model.FavoritesRepository
import com.starkdroid.quicknotch.model.NotchConfig
import com.starkdroid.quicknotch.model.NotchStyle
import com.starkdroid.quicknotch.model.NotchSurface
import com.starkdroid.quicknotch.model.WaveformTint
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
    // Deliberately NO screen-level config/favorites/session collects here: the
    // settings window used to recompose end-to-end on every media heartbeat
    // (a new MediaSession object every ~2s) and on every slider-drag frame,
    // which read as low-fps scrolling. Each tab collects exactly the state it
    // renders (see the tab signatures), so a media tick only touches the tiny
    // now-playing status row and a slider drag only touches its own card.
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf(
        stringResource(Res.string.tab_dock_sizing),
        stringResource(Res.string.tab_widgets),
        stringResource(Res.string.tab_behavior)
    )
    val tabIcons = listOf(Icons.Default.Tune, Icons.Default.Apps, Icons.Default.ToggleOn)

    var showAboutDialog by remember { mutableStateOf(false) }

    // The calendar connection is a process-wide, file-backed thing (tokens on
    // disk), not a screen concern: the widget reads its state, the notch reads
    // the same flow, and only the actions are handed down.
    val calendarState by GoogleCalendar.state.collectAsState()
    val calendarRefreshing by GoogleCalendar.refreshing.collectAsState()

    // Restore the appearance/sizing/clock defaults. This is the same set the tab
    // used to own; it now lives here because the button sits in the top bar, and
    // the screen already holds the repository. Widgets and Behavior keep their
    // own state (pinned apps, toggles) — resetting the notch's look must not
    // silently undo a user's widget choices.
    val onResetDefaults: () -> Unit = {
        repository.updateConfig {
            it.copy(
                style = NotchStyle.DYNAMIC_ISLAND,
                surface = NotchSurface.SOLID,
                dockScaleFactor = 1.35f,
                baseIconSizeDp = 38,
                quickViewWidthDp = 150,
                quickViewHeightDp = 30,
                expandedNotchHeightDp = 78,
                clockDate = true,
                clockTime = true,
                clock24Hour = false,
                clockDateFormat = ClockDateFormat.DD_MMM
            )
        }
    }

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
                    onOpenAbout = { showAboutDialog = true },
                    // Reset only exists on the page it affects: the defaults it
                    // restores are the appearance/sizing/clock ones, so offering
                    // it on Widgets or Behavior would be a lie.
                    onReset = if (selectedTab == 0) onResetDefaults else null
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
                        .background(FlatSurface, RoundedCornerShape(10.dp))
                        .border(1.dp, FlatBorder, RoundedCornerShape(10.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    tabs.forEachIndexed { index, title ->
                        val isSelected = selectedTab == index
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    color = if (isSelected) FlatSurfaceElevated else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
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
                    0 -> DockSizingTab(repository = repository)
                    1 -> WidgetsTab(
                        repository = repository,
                        media = media,
                        scannedApps = scannedApps,
                        searchQuery = searchQuery,
                        isScanning = isScanning,
                        onSearchQueryChange = { searchQuery = it },
                        calendarState = calendarState,
                        calendarRefreshing = calendarRefreshing,
                        onConnectCalendar = GoogleCalendar::connect,
                        onCancelCalendar = GoogleCalendar::cancelConnect,
                        onDisconnectCalendar = GoogleCalendar::disconnect,
                        onRefreshCalendar = GoogleCalendar::refresh
                    )
                    2 -> BehaviorTab(repository = repository)
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
    onOpenAbout: () -> Unit,
    /** Null hides the reset button (any tab but Appearance). */
    onReset: (() -> Unit)?
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

        // The window-level actions share one chrome so they read as a pair: the
        // same 32dp square, elevated surface and hairline border as About, at
        // the same 8dp corner radius.
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onReset != null) {
                TopBarActionButton(
                    onClick = onReset,
                    contentDescription = stringResource(Res.string.reset_defaults)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = AccentRed,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
            TopBarActionButton(
                onClick = onOpenAbout,
                contentDescription = stringResource(Res.string.about)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/** One square top-bar action: 32dp, elevated fill, hairline border, 8dp radius. */
@Composable
private fun TopBarActionButton(
    onClick: () -> Unit,
    contentDescription: String,
    icon: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(FlatSurfaceElevated)
            .border(1.dp, FlatBorder, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        icon()
    }
}

// Enum labels resolve from composeResources at the UI layer, keeping the model
// enums free of user-facing text.
@Composable
internal fun NotchStyle.label(): String = stringResource(
    when (this) {
        NotchStyle.DYNAMIC_ISLAND -> Res.string.style_default
        NotchStyle.FLOATING_PILL -> Res.string.style_floating_pill
        NotchStyle.DRAG_HANDLE -> Res.string.style_drag_handle
    }
)

@Composable
internal fun NotchSurface.label(): String = stringResource(
    when (this) {
        NotchSurface.SOLID -> Res.string.surface_solid
        NotchSurface.TRANSLUCENT -> Res.string.surface_translucent
        NotchSurface.SYSTEM_ACCENT -> Res.string.surface_system_accent
    }
)

@Composable
internal fun WaveformTint.label(): String = stringResource(
    when (this) {
        WaveformTint.ALBUM_ART -> Res.string.waveform_tint_album_art
        WaveformTint.WHITE -> Res.string.waveform_tint_white
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