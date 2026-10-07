package com.starkdroid.quicknotch.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ToggleOn
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starkdroid.quicknotch.calendar.GoogleCalendar
import com.starkdroid.quicknotch.datasource.AppIconLoader
import com.starkdroid.quicknotch.datasource.HostPlatform
import com.starkdroid.quicknotch.datasource.InstalledAppsScanner
import com.starkdroid.quicknotch.generated.resources.Res
import com.starkdroid.quicknotch.generated.resources.about
import com.starkdroid.quicknotch.generated.resources.about_copyright
import com.starkdroid.quicknotch.generated.resources.about_description
import com.starkdroid.quicknotch.generated.resources.about_header
import com.starkdroid.quicknotch.generated.resources.about_product_name
import com.starkdroid.quicknotch.generated.resources.about_tag_compose
import com.starkdroid.quicknotch.generated.resources.about_tag_kotlin
import com.starkdroid.quicknotch.generated.resources.about_tag_macos
import com.starkdroid.quicknotch.generated.resources.about_tag_windows
import com.starkdroid.quicknotch.generated.resources.about_version
import com.starkdroid.quicknotch.generated.resources.app_name
import com.starkdroid.quicknotch.generated.resources.app_version
import com.starkdroid.quicknotch.generated.resources.close
import com.starkdroid.quicknotch.generated.resources.quicknotch_icon
import com.starkdroid.quicknotch.generated.resources.reset_defaults
import com.starkdroid.quicknotch.generated.resources.settings_appearance_desc
import com.starkdroid.quicknotch.generated.resources.settings_behavior_desc
import com.starkdroid.quicknotch.generated.resources.settings_tagline
import com.starkdroid.quicknotch.generated.resources.settings_widgets_desc
import com.starkdroid.quicknotch.generated.resources.style_default
import com.starkdroid.quicknotch.generated.resources.style_drag_handle
import com.starkdroid.quicknotch.generated.resources.style_floating_pill
import com.starkdroid.quicknotch.generated.resources.surface_solid
import com.starkdroid.quicknotch.generated.resources.surface_system_accent
import com.starkdroid.quicknotch.generated.resources.surface_blurred
import com.starkdroid.quicknotch.generated.resources.waveform_tint_album_art
import com.starkdroid.quicknotch.generated.resources.waveform_tint_white
import com.starkdroid.quicknotch.generated.resources.tab_behavior
import com.starkdroid.quicknotch.generated.resources.tab_dock_sizing
import com.starkdroid.quicknotch.generated.resources.tab_widgets
import com.starkdroid.quicknotch.media.MediaController
import com.starkdroid.quicknotch.model.AppItem
import com.starkdroid.quicknotch.model.ClockDateFormat
import com.starkdroid.quicknotch.model.FavoritesRepository
import com.starkdroid.quicknotch.model.NotchStyle
import com.starkdroid.quicknotch.model.NotchSurface
import com.starkdroid.quicknotch.model.WaveformTint
import com.starkdroid.quicknotch.theme.AccentRed
import com.starkdroid.quicknotch.theme.AccentSolid
import com.starkdroid.quicknotch.theme.AppTypography
import com.starkdroid.quicknotch.theme.SettingsBg
import com.starkdroid.quicknotch.theme.SettingsBorder
import com.starkdroid.quicknotch.theme.SettingsCard
import com.starkdroid.quicknotch.theme.SettingsCardHover
import com.starkdroid.quicknotch.theme.SettingsElevated
import com.starkdroid.quicknotch.theme.SettingsSidebar
import com.starkdroid.quicknotch.theme.TextMuted
import com.starkdroid.quicknotch.theme.TextPrimary
import com.starkdroid.quicknotch.theme.TextSecondary
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
    val tabDescriptions = listOf(
        stringResource(Res.string.settings_appearance_desc),
        stringResource(Res.string.settings_widgets_desc),
        stringResource(Res.string.settings_behavior_desc)
    )

    var showAboutDialog by remember { mutableStateOf(false) }

    // The calendar connection is a process-wide, file-backed thing (tokens on
    // disk), not a screen concern: the widget reads its state, the notch reads
    // the same flow, and only the actions are handed down.
    val calendarState by GoogleCalendar.state.collectAsState()
    val calendarRefreshing by GoogleCalendar.refreshing.collectAsState()
    val calendarTick by GoogleCalendar.minuteTick.collectAsState()

    // Restore the appearance/sizing/clock defaults. This is the same set the tab
    // used to own; it now lives here because the button sits in the content
    // header, and the screen already holds the repository. Widgets and Behavior
    // keep their own state (pinned apps, toggles) — resetting the notch's look
    // must not silently undo a user's widget choices.
    val onResetDefaults: () -> Unit = {
        repository.updateConfig {
            it.copy(
                style = NotchStyle.NOTCH,
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
            .background(SettingsBg)
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // ── Sidebar ──────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .width(232.dp)
                    .fillMaxHeight()
                    .background(SettingsSidebar)
                    .padding(horizontal = 14.dp, vertical = 18.dp)
            ) {
                // Brand block.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(Res.drawable.quicknotch_icon),
                        contentDescription = stringResource(Res.string.app_name),
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(11.dp))
                    )
                    Spacer(modifier = Modifier.width(11.dp))
                    Column {
                        Text(
                            text = stringResource(Res.string.app_name),
                            style = AppTypography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = TextPrimary
                        )
                        Text(
                            text = stringResource(Res.string.settings_tagline),
                            style = AppTypography.labelSmall,
                            color = TextMuted,
                            fontSize = 10.5.sp,
                            maxLines = 2
                        )
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                tabs.forEachIndexed { index, title ->
                    SidebarNavItem(
                        icon = tabIcons[index],
                        label = title,
                        selected = selectedTab == index,
                        onClick = { selectedTab = index }
                    )
                    if (index < tabs.lastIndex) Spacer(modifier = Modifier.height(6.dp))
                }

                Spacer(modifier = Modifier.weight(1f))

                // Sidebar footer: platform tag + about entry.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(SettingsCard)
                        .border(1.dp, SettingsBorder, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Column {
                        Text(
                            text = if (HostPlatform.isMac) "macOS · Metal" else "Windows · Direct3D",
                            style = AppTypography.labelSmall,
                            color = TextMuted,
                            fontSize = 10.5.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(Res.string.about_version, stringResource(Res.string.app_version)),
                            style = AppTypography.labelSmall,
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                SidebarNavItem(
                    icon = Icons.Default.Info,
                    label = stringResource(Res.string.about),
                    selected = false,
                    onClick = { showAboutDialog = true }
                )
            }

            // Hairline sidebar divider — solid, no gradient.
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(SettingsBorder.copy(alpha = 0.7f))
            )

            // ── Content pane ─────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 26.dp, vertical = 20.dp)
            ) {
                // Content header: animated title per tab + contextual actions.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AnimatedContent(
                        targetState = selectedTab,
                        transitionSpec = {
                            (fadeIn(tween(180)) togetherWith fadeOut(tween(140)))
                        },
                        label = "settings-header",
                        modifier = Modifier.weight(1f)
                    ) { tab ->
                        SettingsPageHeader(
                            title = tabs[tab],
                            description = tabDescriptions[tab]
                        )
                    }
                    if (selectedTab == 0) {
                        ContentHeaderReset(onReset = onResetDefaults)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Tab body with a gentle directional slide so switching tabs
                // feels like moving through the sidebar, not swapping cards.
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = {
                        val forward = targetState > initialState
                        val slide = if (forward) 28 else -28
                        (slideInHorizontally(tween(240)) { slide } + fadeIn(tween(200))) togetherWith
                            (slideOutHorizontally(tween(200)) { -slide } + fadeOut(tween(140)))
                    },
                    label = "settings-tabs",
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) { tab ->
                    when (tab) {
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
                            calendarTick = calendarTick,
                            onConnectCalendar = GoogleCalendar::connect,
                            onCancelCalendar = GoogleCalendar::cancelConnect,
                            onDisconnectCalendar = GoogleCalendar::disconnect,
                            onRefreshCalendar = GoogleCalendar::refresh
                        )
                        else -> BehaviorTab(repository = repository)
                    }
                }
            }
        }

        if (showAboutDialog) {
            AboutDialog(onDismiss = { showAboutDialog = false })
        }
    }
}

/** Content-header reset chip — only on Appearance, the page it affects. */
@Composable
private fun ContentHeaderReset(onReset: () -> Unit) {
    val hoverPress = rememberHoverPressState()
    val scale = hoverPressScale(hoverPress, pressedScale = 0.95f)
    Box(
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(9.dp))
            .background(if (hoverPress.hovered) SettingsCardHover else SettingsCard)
            .border(1.dp, SettingsBorder, RoundedCornerShape(9.dp))
            .trackHoverPress(hoverPress)
            .plainClickable(onClick = onReset)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                tint = AccentRed,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(Res.string.reset_defaults),
                style = AppTypography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondary
            )
        }
    }
}

// Enum labels resolve from composeResources at the UI layer, keeping the model
// enums free of user-facing text.
@Composable
internal fun NotchStyle.label(): String = stringResource(
    when (this) {
        NotchStyle.NOTCH -> Res.string.style_default
        NotchStyle.FLOATING_PILL -> Res.string.style_floating_pill
        NotchStyle.DRAG_HANDLE -> Res.string.style_drag_handle
    }
)

@Composable
internal fun NotchSurface.label(): String = stringResource(
    when (this) {
        NotchSurface.SOLID -> Res.string.surface_solid
        NotchSurface.TRANSLUCENT -> Res.string.surface_blurred
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
    // Entrance: fade the scrim, spring the card from 96% scale.
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val cardScale by animateFloatAsState(
        targetValue = if (entered) 1f else 0.96f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 420f),
        label = "aboutScale"
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .plainClickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .width(440.dp)
                .graphicsLayer { scaleX = cardScale; scaleY = cardScale }
                .plainClickable(onClick = {}),
            colors = CardDefaults.cardColors(containerColor = SettingsCard),
            border = androidx.compose.foundation.BorderStroke(1.dp, SettingsBorder),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                modifier = Modifier.padding(26.dp),
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
                        fontSize = 14.sp,
                        color = TextPrimary
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(Res.string.close),
                            tint = TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Image(
                    painter = painterResource(Res.drawable.quicknotch_icon),
                    contentDescription = stringResource(Res.string.app_name),
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(14.dp))
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(Res.string.about_product_name),
                    style = AppTypography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(AccentSolid.copy(alpha = 0.14f))
                        .border(1.dp, AccentSolid.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = stringResource(Res.string.about_version, stringResource(Res.string.app_version)),
                        style = AppTypography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = AccentSolid,
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(Res.string.about_description),
                    style = AppTypography.bodySmall,
                    fontSize = 12.5.sp,
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
                                .clip(RoundedCornerShape(6.dp))
                                .background(SettingsElevated)
                                .border(1.dp, SettingsBorder, RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(tag, style = AppTypography.labelSmall, color = TextMuted, fontSize = 10.5.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

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
