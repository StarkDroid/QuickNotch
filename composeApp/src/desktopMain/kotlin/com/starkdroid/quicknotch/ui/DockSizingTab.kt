package com.starkdroid.quicknotch.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starkdroid.quicknotch.generated.resources.*
import com.starkdroid.quicknotch.media.SystemBattery
import com.starkdroid.quicknotch.model.ClockDateFormat
import com.starkdroid.quicknotch.model.NotchConfig
import com.starkdroid.quicknotch.model.NotchSurface
import com.starkdroid.quicknotch.theme.AccentIndigoLight
import com.starkdroid.quicknotch.theme.AccentRed
import com.starkdroid.quicknotch.theme.AppTypography
import com.starkdroid.quicknotch.theme.TextPrimary
import com.starkdroid.quicknotch.theme.TextSecondary
import java.util.Locale
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun DockSizingTab(
    config: NotchConfig,
    onUpdate: ((NotchConfig) -> NotchConfig) -> Unit,
    onResetDefaults: () -> Unit
) {
    // A desktop has no battery; only offer the device-battery toggle where it
    // can actually do something.
    val hasDeviceBattery by SystemBattery.hasBattery.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        // Tab Header with Reset Defaults action (the tab label already names it)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
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

        // Section 1: Appearance — silhouette picker, then the surface theme
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = FlatSurface),
                border = BorderStroke(1.dp, FlatBorder),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SectionHeader(Icons.Default.Palette, stringResource(Res.string.notch_style))
                    Spacer(modifier = Modifier.height(8.dp))
                    NotchStylePicker(
                        selected = config.style,
                        onSelect = { style -> onUpdate { it.copy(style = style) } }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    AppearanceDropdown(
                        label = stringResource(Res.string.surface_theme),
                        options = NotchSurface.entries.toList(),
                        selected = config.surface,
                        optionLabel = { it.label() },
                        onSelect = { surface -> onUpdate { it.copy(surface = surface) } },
                        modifier = Modifier.fillMaxWidth()
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
                    SectionHeader(Icons.Default.ZoomIn, stringResource(Res.string.fisheye_title))
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
                            String.format(Locale.ROOT, "%.2f", config.dockScaleFactor.coerceIn(1.10f, 1.45f))
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
                    SectionHeader(Icons.Default.Straighten, stringResource(Res.string.geometry_title))
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

        // Section 4: Collapsed Pill Indicators (moved here from the Now Playing
        // widget accordion — they tune the collapsed pill, not the music widget).
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = FlatSurface),
                border = BorderStroke(1.dp, FlatBorder),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SectionHeader(Icons.Default.Sensors, stringResource(Res.string.pill_indicators_title))
                    Text(
                        text = stringResource(
                            if (hasDeviceBattery) Res.string.pill_indicators_desc_laptop
                            else Res.string.pill_indicators_desc
                        ),
                        style = AppTypography.bodySmall,
                        color = TextSecondary,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    BehaviorSwitchRow(
                        title = stringResource(Res.string.pill_show_volume_title),
                        subtitle = stringResource(Res.string.pill_show_volume_desc),
                        checked = config.widgets.showCollapsedVolume,
                        onCheckedChange = { checked ->
                            onUpdate {
                                it.copy(widgets = it.widgets.copy(showCollapsedVolume = checked))
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = FlatBorder.copy(alpha = 0.6f))
                    Spacer(modifier = Modifier.height(10.dp))

                    BehaviorSwitchRow(
                        title = stringResource(Res.string.pill_show_audio_battery_title),
                        subtitle = stringResource(Res.string.pill_show_audio_battery_desc),
                        checked = config.widgets.showCollapsedBattery,
                        onCheckedChange = { checked ->
                            onUpdate {
                                it.copy(widgets = it.widgets.copy(showCollapsedBattery = checked))
                            }
                        }
                    )

                    if (hasDeviceBattery) {
                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = FlatBorder.copy(alpha = 0.6f))
                        Spacer(modifier = Modifier.height(10.dp))

                        BehaviorSwitchRow(
                            title = stringResource(Res.string.pill_show_laptop_battery_title),
                            subtitle = stringResource(Res.string.pill_show_laptop_battery_desc),
                            checked = config.widgets.showCollapsedDeviceBattery,
                            onCheckedChange = { checked ->
                                onUpdate {
                                    it.copy(widgets = it.widgets.copy(showCollapsedDeviceBattery = checked))
                                }
                            }
                        )
                    }
                }
            }
        }

        // Section 5: Idle clock in the collapsed pill
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = FlatSurface),
                border = BorderStroke(1.dp, FlatBorder),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SectionHeader(Icons.Default.Schedule, stringResource(Res.string.clock_title))
                    Text(
                        text = stringResource(Res.string.clock_desc),
                        style = AppTypography.bodySmall,
                        color = TextSecondary,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(Res.string.clock_show_date),
                            style = AppTypography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary
                        )
                        MinimalistRectSwitch(
                            checked = config.clockDate,
                            onCheckedChange = { checked -> onUpdate { it.copy(clockDate = checked) } }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(Res.string.clock_show_time),
                            style = AppTypography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary
                        )
                        MinimalistRectSwitch(
                            checked = config.clockTime,
                            onCheckedChange = { checked -> onUpdate { it.copy(clockTime = checked) } }
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = FlatBorder.copy(alpha = 0.6f))
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(Res.string.clock_24h),
                            style = AppTypography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary
                        )
                        MinimalistRectSwitch(
                            checked = config.clock24Hour,
                            onCheckedChange = { checked -> onUpdate { it.copy(clock24Hour = checked) } }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    AppearanceDropdown(
                        label = stringResource(Res.string.clock_date_format),
                        options = ClockDateFormat.entries.toList(),
                        selected = config.clockDateFormat,
                        optionLabel = { formatClockDate(it) },
                        onSelect = { format -> onUpdate { it.copy(clockDateFormat = format) } },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/**
 * Card section header: a small accent icon beside the title. Used across every
 * Dock & Sizing card so each section reads as one scannable unit.
 */
@Composable
private fun SectionHeader(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = AccentIndigoLight,
            modifier = Modifier.size(15.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = AppTypography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
    }
}