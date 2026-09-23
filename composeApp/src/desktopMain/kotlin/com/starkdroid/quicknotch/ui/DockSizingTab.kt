package com.starkdroid.quicknotch.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starkdroid.quicknotch.generated.resources.*
import com.starkdroid.quicknotch.model.ClockDateFormat
import com.starkdroid.quicknotch.model.NotchConfig
import com.starkdroid.quicknotch.model.NotchStyle
import com.starkdroid.quicknotch.model.NotchSurface
import com.starkdroid.quicknotch.theme.AccentCyan
import com.starkdroid.quicknotch.theme.AccentIndigo
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

        // Section 4: Idle clock in the collapsed pill
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = FlatSurface),
                border = BorderStroke(1.dp, FlatBorder),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(Res.string.clock_title),
                        style = AppTypography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
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