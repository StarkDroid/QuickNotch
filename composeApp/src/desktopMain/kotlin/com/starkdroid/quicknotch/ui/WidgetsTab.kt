package com.starkdroid.quicknotch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starkdroid.quicknotch.calendar.CalendarState
import com.starkdroid.quicknotch.generated.resources.Res
import com.starkdroid.quicknotch.generated.resources.calendar_section_title
import com.starkdroid.quicknotch.generated.resources.progress_border_title
import com.starkdroid.quicknotch.generated.resources.visualiser_section_title
import com.starkdroid.quicknotch.generated.resources.visualiser_style_title
import com.starkdroid.quicknotch.generated.resources.waveform_hide_title
import com.starkdroid.quicknotch.generated.resources.waveform_tint_title
import com.starkdroid.quicknotch.generated.resources.weather_section_title
import com.starkdroid.quicknotch.generated.resources.show_labels_title
import com.starkdroid.quicknotch.generated.resources.system_disabled
import com.starkdroid.quicknotch.generated.resources.system_hint
import com.starkdroid.quicknotch.generated.resources.system_preview
import com.starkdroid.quicknotch.generated.resources.system_section_title
import com.starkdroid.quicknotch.generated.resources.system_unknown
import com.starkdroid.quicknotch.generated.resources.system_waiting
import com.starkdroid.quicknotch.generated.resources.widget_apps_title
import com.starkdroid.quicknotch.generated.resources.widget_music_title
import com.starkdroid.quicknotch.generated.resources.widget_no_session
import com.starkdroid.quicknotch.generated.resources.widget_now_playing
import com.starkdroid.quicknotch.generated.resources.widget_scroll_hint
import com.starkdroid.quicknotch.generated.resources.widget_style_warning
import com.starkdroid.quicknotch.generated.resources.widget_unknown_artist
import com.starkdroid.quicknotch.media.MediaController
import com.starkdroid.quicknotch.model.AppItem
import com.starkdroid.quicknotch.model.FavoritesRepository
import com.starkdroid.quicknotch.model.NotchConfig
import com.starkdroid.quicknotch.model.VisualiserStyle
import com.starkdroid.quicknotch.model.WaveformTint
import com.starkdroid.quicknotch.system.SystemMetrics
import com.starkdroid.quicknotch.system.SystemSnapshot
import com.starkdroid.quicknotch.theme.AccentGreen
import com.starkdroid.quicknotch.theme.AppTypography
import com.starkdroid.quicknotch.theme.NotchBorder
import com.starkdroid.quicknotch.theme.NotchDeepBlack
import com.starkdroid.quicknotch.theme.SettingsBorder
import com.starkdroid.quicknotch.theme.SettingsElevated
import com.starkdroid.quicknotch.theme.TextMuted
import com.starkdroid.quicknotch.theme.TextPrimary
import com.starkdroid.quicknotch.theme.TextSecondary
import com.starkdroid.quicknotch.weather.Weather
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
            TabEntrance(index = 0) {
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

                        HorizontalDivider(color = SettingsBorder.copy(alpha = 0.7f))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp)
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
        }
        item(key = "nowPlaying") {
            TabEntrance(index = 1) {
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
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp
                        )

                        HorizontalDivider(color = SettingsBorder.copy(alpha = 0.7f))

                        // Visualiser lives inside Now Playing: it is the
                        // collapsed face of this widget, not a widget of its
                        // own — the toggle, shape, colour, preview and title
                        // switch all sit together here.
                        BehaviorSwitchRow(
                            icon = Icons.Default.BarChart,
                            title = stringResource(Res.string.visualiser_section_title),
                            checked = config.widgets.showCollapsedWaveform,
                            onCheckedChange = { checked ->
                                onUpdate { it.copy(widgets = it.widgets.copy(showCollapsedWaveform = checked)) }
                            }
                        )

                        // Mini-pill preview: the real bars component at a frozen
                        // phase, sitting in a dark stadium the size of the
                        // collapsed live pill (~220×34dp vs the real 236×34dp)
                        // and parked left — a notch sample, not a full-width
                        // banner, so the picker never drifts from what the pill
                        // draws.
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(
                                modifier = Modifier
                                    .width(220.dp)
                                    .height(34.dp)
                                    .clip(RoundedCornerShape(17.dp))
                                    .background(NotchDeepBlack)
                                    .border(1.dp, NotchBorder, RoundedCornerShape(17.dp))
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                VinylDisc(
                                    art = null,
                                    playing = true,
                                    animate = false,
                                    size = 20.dp
                                )
                                MusicWaveform(
                                    playing = true,
                                    animate = false,
                                    color = if (config.widgets.showCollapsedWaveform) WaveformWhite
                                    else WaveformWhite.copy(alpha = 0.42f),
                                    style = config.widgets.visualiserStyle,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(18.dp)
                                )
                            }
                        }

                        AppearanceDropdown(
                            label = stringResource(Res.string.visualiser_style_title),
                            options = VisualiserStyle.entries.toList(),
                            selected = config.widgets.visualiserStyle,
                            optionLabel = { it.label() },
                            onSelect = { style ->
                                onUpdate { it.copy(widgets = it.widgets.copy(visualiserStyle = style)) }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        AppearanceDropdown(
                            label = stringResource(Res.string.waveform_tint_title),
                            options = WaveformTint.entries.toList(),
                            selected = config.widgets.waveformTint,
                            optionLabel = { it.label() },
                            onSelect = { tint ->
                                onUpdate { it.copy(widgets = it.widgets.copy(waveformTint = tint)) }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Hiding the title only means something while the bars
                        // are on screen, so it lives with them rather than as a
                        // dead switch elsewhere.
                        BehaviorSwitchRow(
                            icon = Icons.Default.VisibilityOff,
                            title = stringResource(Res.string.waveform_hide_title),
                            checked = config.widgets.waveformHidesTitle,
                            onCheckedChange = { checked ->
                                onUpdate { it.copy(widgets = it.widgets.copy(waveformHidesTitle = checked)) }
                            }
                        )

                        // The outline progress ring rides the island's edge
                        // beside the bars (or without them), so its toggle
                        // sits with the bars rather than as a dead switch
                        // elsewhere.
                        BehaviorSwitchRow(
                            icon = Icons.Default.Timeline,
                            title = stringResource(Res.string.progress_border_title),
                            checked = config.widgets.showProgressBorder,
                            onCheckedChange = { checked ->
                                onUpdate { it.copy(widgets = it.widgets.copy(showProgressBorder = checked)) }
                            }
                        )

                        HorizontalDivider(color = SettingsBorder.copy(alpha = 0.7f))

                        if (!isStyleEligibleForLiveActivity(config)) {
                            Text(
                                text = stringResource(Res.string.widget_style_warning),
                                style = AppTypography.bodySmall,
                                color = TextMuted,
                                fontSize = 11.5.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        }
        item(key = "calendar") {
            TabEntrance(index = 2) {
                WidgetAccordion(
                    icon = Icons.Default.CalendarMonth,
                    title = stringResource(Res.string.calendar_section_title),
                    enabled = config.widgets.showCalendar,
                    onToggleEnabled = { enabled ->
                        onUpdate { it.copy(widgets = it.widgets.copy(showCalendar = enabled)) }
                    }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp)
                            .padding(bottom = 14.dp)
                    ) {
                        CalendarWidget(
                            state = calendarState,
                            refreshing = calendarRefreshing,
                            onConnect = onConnectCalendar,
                            onCancel = onCancelCalendar,
                            onDisconnect = onDisconnectCalendar,
                            onRefresh = onRefreshCalendar
                        )
                    }
                }
            }
        }
        item(key = "weather") {
            // The tab owns this subscription like every other: config drives
            // the toggles, the controller's flow drives the status row.
            val weatherState by Weather.state.collectAsState()
            val weatherFetching by Weather.fetching.collectAsState()
            TabEntrance(index = 3) {
                WidgetAccordion(
                    icon = Icons.Default.WbSunny,
                    title = stringResource(Res.string.weather_section_title),
                    enabled = config.widgets.showWeather,
                    onToggleEnabled = { enabled ->
                        onUpdate { it.copy(widgets = it.widgets.copy(showWeather = enabled)) }
                    }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp)
                            .padding(bottom = 14.dp)
                    ) {
                        WeatherSettings(
                            state = weatherState,
                            fetching = weatherFetching,
                            manualPlace = config.widgets.weatherPlace,
                            onSelectPlace = { place ->
                                onUpdate { it.copy(widgets = it.widgets.copy(weatherPlace = place)) }
                            },
                            fahrenheit = config.widgets.weatherFahrenheit,
                            onFahrenheitChange = { enabled ->
                                onUpdate { it.copy(widgets = it.widgets.copy(weatherFahrenheit = enabled)) }
                            },
                            onRefresh = Weather::refresh
                        )
                    }
                }
            }
        }
        item(key = "system") {
            // Same ownership rule: this accordion collects the readings itself,
            // so the settings screen and the other tabs never recompose for a
            // network tick.
            val systemState by SystemMetrics.state.collectAsState()
            TabEntrance(index = 4) {
                WidgetAccordion(
                    icon = Icons.Default.Memory,
                    title = stringResource(Res.string.system_section_title),
                    enabled = config.widgets.showSystem,
                    onToggleEnabled = { enabled ->
                        onUpdate { it.copy(widgets = it.widgets.copy(showSystem = enabled)) }
                    }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp)
                            .padding(bottom = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SystemStatusRow(
                            snapshot = systemState,
                            enabled = config.widgets.showSystem
                        )
                        Text(
                            text = stringResource(Res.string.system_hint),
                            style = AppTypography.bodySmall,
                            color = TextSecondary,
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp
                        )
                    }
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
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SettingsElevated)
            .border(1.dp, SettingsBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Pulsing dot when live: solid green with a soft outer ring, flat grey
        // otherwise. No animation loop — the dot is state, not motion.
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (live) AccentGreen else TextMuted.copy(alpha = 0.4f))
                .then(
                    if (live) Modifier.border(
                        2.dp,
                        AccentGreen.copy(alpha = 0.25f),
                        CircleShape
                    ) else Modifier
                )
        )
        Spacer(modifier = Modifier.width(9.dp))
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

/**
 * Live readout for the System widget accordion: the exact numbers the notch
 * page is drawing right now (CPU, memory, download). A switched-off widget has
 * no reading at all (the poller is stopped, not idling), and a fresh one has an
 * honest "collecting the first sample" line, so the row never implies data it
 * does not have. Same pill chrome as the Now Playing status above it.
 */
@Composable
internal fun SystemStatusRow(snapshot: SystemSnapshot?, enabled: Boolean) {
    val unknown = stringResource(Res.string.system_unknown)
    val live = enabled && snapshot != null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SettingsElevated)
            .border(1.dp, SettingsBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (live) AccentGreen else TextMuted.copy(alpha = 0.4f))
        )
        Spacer(modifier = Modifier.width(9.dp))
        Text(
            text = when {
                !enabled -> stringResource(Res.string.system_disabled)
                snapshot == null -> stringResource(Res.string.system_waiting)
                else -> stringResource(
                    Res.string.system_preview,
                    snapshot.cpuPercent?.let { "$it%" } ?: unknown,
                    snapshot.ramPercent?.let { "$it%" } ?: unknown,
                    systemRate(snapshot.downBytesPerSec)
                )
            },
            style = AppTypography.bodySmall,
            color = if (live) TextPrimary else TextSecondary,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
