package com.starkdroid.quicknotch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.MusicNote
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
import com.starkdroid.quicknotch.generated.resources.calendar_hint
import com.starkdroid.quicknotch.generated.resources.calendar_section_title
import com.starkdroid.quicknotch.generated.resources.weather_hint
import com.starkdroid.quicknotch.generated.resources.weather_section_title
import com.starkdroid.quicknotch.generated.resources.show_labels_title
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
import com.starkdroid.quicknotch.theme.AccentGreen
import com.starkdroid.quicknotch.theme.AppTypography
import com.starkdroid.quicknotch.theme.SettingsBorder
import com.starkdroid.quicknotch.theme.SettingsCard
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
    calendarTick: Long,
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
                SettingsCard {
                    SectionHeader(
                        Icons.Default.CalendarMonth,
                        stringResource(Res.string.calendar_section_title),
                        stringResource(Res.string.calendar_hint)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    CalendarWidget(
                        state = calendarState,
                        refreshing = calendarRefreshing,
                        tick = calendarTick,
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
        item(key = "weather") {
            // The tab owns this subscription like every other: config drives
            // the toggles, the controller's flow drives the status row.
            val weatherState by Weather.state.collectAsState()
            val weatherFetching by Weather.fetching.collectAsState()
            TabEntrance(index = 3) {
                SettingsCard {
                    SectionHeader(
                        Icons.Default.WbSunny,
                        stringResource(Res.string.weather_section_title),
                        stringResource(Res.string.weather_hint)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    WeatherSettings(
                        state = weatherState,
                        fetching = weatherFetching,
                        showWeather = config.widgets.showWeather,
                        onShowWeatherChange = { enabled ->
                            onUpdate { it.copy(widgets = it.widgets.copy(showWeather = enabled)) }
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
