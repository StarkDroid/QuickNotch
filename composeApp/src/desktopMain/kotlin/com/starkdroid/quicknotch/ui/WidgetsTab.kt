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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starkdroid.quicknotch.datasource.AppIconLoader
import com.starkdroid.quicknotch.datasource.HostPlatform
import com.starkdroid.quicknotch.calendar.CalendarState
import com.starkdroid.quicknotch.generated.resources.*
import com.starkdroid.quicknotch.media.MediaController
import com.starkdroid.quicknotch.media.MediaSession
import com.starkdroid.quicknotch.model.AppItem
import com.starkdroid.quicknotch.model.FavoritesRepository
import com.starkdroid.quicknotch.model.NotchConfig
import com.starkdroid.quicknotch.theme.*
import java.io.File
import java.util.UUID
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WidgetsTab(
    repository: FavoritesRepository,
    media: MediaController,
    scannedApps: List<AppItem>,
    searchQuery: String,
    isScanning: Boolean,
    onSearchQueryChange: (String) -> Unit,
    calendarState: CalendarState,
    calendarRefreshing: Boolean,
    onConnectCalendar: () -> Unit,
    onCancelCalendar: () -> Unit,
    onDisconnectCalendar: () -> Unit,
    onRefreshCalendar: () -> Unit
) {
    // The tab owns its state subscriptions: config drives the accordions and
    // the label switch; favorites drive the pinned list. Neither the settings
    // screen nor the other tabs recompose for these.
    val config by repository.config.collectAsState()
    val favorites by repository.favorites.collectAsState()
    val onUpdate: ((NotchConfig) -> NotchConfig) -> Unit = { transform ->
        repository.updateConfig(transform)
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
                enabled = config.widgets.pinnedAppsEnabled,
                onToggleEnabled = { enabled ->
                    onUpdate { it.copy(widgets = it.widgets.copy(pinnedAppsEnabled = enabled)) }
                }
            ) {
                Column {
                    PinnedAppsTab(
                        favorites = favorites,
                        scannedApps = scannedApps,
                        searchQuery = searchQuery,
                        isScanning = isScanning,
                        onSearchQueryChange = onSearchQueryChange,
                        onAddFavorite = { repository.addFavorite(it) },
                        onRemoveFavorite = { repository.removeFavorite(it) },
                        onMove = { from, to -> repository.reorderFavorites(from, to) },
                        onResetDefaults = { repository.resetToDefaults() },
                        fixedHeight = true
                    )

                    HorizontalDivider(color = FlatBorder.copy(alpha = 0.6f))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        BehaviorSwitchRow(
                            icon = Icons.AutoMirrored.Filled.Label,
                            title = stringResource(Res.string.show_labels_title),
                            checked = config.showTooltips,
                            onCheckedChange = { checked ->
                                onUpdate { it.copy(showTooltips = checked) }
                            }
                        )
                    }
                }
            }
        }
        item(key = "nowPlaying") {
            WidgetAccordion(
                icon = Icons.Default.MusicNote,
                title = stringResource(Res.string.widget_music_title),
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
                    NowPlayingStatus(config = config, media = media)

                    Text(
                        text = stringResource(Res.string.widget_scroll_hint),
                        style = AppTypography.bodySmall,
                        color = TextSecondary,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )

                    HorizontalDivider(color = FlatBorder)

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
        item(key = "calendar") {
            Card(
                colors = CardDefaults.cardColors(containerColor = FlatSurface),
                border = BorderStroke(1.dp, FlatBorder),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SectionHeader(Icons.Default.CalendarMonth, stringResource(Res.string.calendar_section_title))
                    Spacer(modifier = Modifier.height(14.dp))
                    CalendarWidget(
                        state = calendarState,
                        refreshing = calendarRefreshing,
                        showInQuickView = config.widgets.showCalendar,
                        onShowInQuickViewChange = { enabled ->
                            onUpdate { it.copy(widgets = it.widgets.copy(showCalendar = enabled)) }
                        },
                        onConnect = onConnectCalendar,
                        onCancel = onCancelCalendar,
                        onDisconnect = onDisconnectCalendar,
                        onRefresh = onRefreshCalendar
                    )
                }
            }
        }

    }
}

/**
 * The widget's live status pill: a green dot when a media session is actively
 * feeding the notch, otherwise the rendered state the user can compare against.
 * Collects the session itself — the one state that changes every media
 * heartbeat — so only this tiny row recomposes when a track plays.
 */
@Composable
internal fun NowPlayingStatus(
    config: NotchConfig,
    media: MediaController
) {
    val liveSession by media.session.collectAsState()
    // Local copy so the smart cast below compiles (delegated state can't).
    val session = liveSession
    val live = isMusicLive(config, session)
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
            text = if (live && session != null) {
                stringResource(Res.string.widget_now_playing, session.title, session.artist.ifBlank { stringResource(Res.string.widget_unknown_artist) })
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
