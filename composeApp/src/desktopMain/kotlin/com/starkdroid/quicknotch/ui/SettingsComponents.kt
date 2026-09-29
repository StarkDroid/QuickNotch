package com.starkdroid.quicknotch.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starkdroid.quicknotch.theme.AccentIndigoLight
import com.starkdroid.quicknotch.theme.AppTypography
import com.starkdroid.quicknotch.theme.TextMuted
import com.starkdroid.quicknotch.theme.TextPrimary
import com.starkdroid.quicknotch.theme.TextSecondary

// Flat Minimalist Color Tokens (shared by the settings window and its tabs)
internal val FlatBg = Color(0xFF0C0E14)
internal val FlatSurface = Color(0xFF131722)
internal val FlatSurfaceElevated = Color(0xFF181E2E)
internal val FlatSurfaceSelected = Color(0xFF1B2236)
internal val FlatBorder = Color(0xFF22283A)
internal val FlatCard = Color(0xFF121622)

/**
 * A minimal, flat selection dropdown (label above, current value, popup menu)
 * shared by the style and surface pickers. Popup rows are plain composables, so
 * opening it costs one small popup — no preview textures are ever rendered.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T> AppearanceDropdown(
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
internal fun MinimalistSliderRow(
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

@Composable
internal fun MinimalistRectSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 16.dp else 2.dp,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 550f),
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


// ── Shared section chrome (used by multiple tabs) ────────────────────────────

/**
 * Card section header: a small accent icon beside the title. Used across every
 * Dock & Sizing card so each section reads as one scannable unit.
 */
@Composable
internal fun SectionHeader(icon: ImageVector, title: String) {
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

/**
 * One cell in the quickview indicators grid (Volume / Audio Battery /
 * Laptop Battery): a square toggle that previews the level-ring glyph and its
 * level, with the label centered underneath. The on-state reads as an
 * accent-cased selected cell with a full-bright ring; off is a neutral card
 * with a dimmed ring.
 */
@Composable
internal fun IndicatorToggleBox(
    title: String,
    icon: Painter,
    level: Int,
    checked: Boolean,
    onClick: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    IndicatorCell(title = title, checked = checked, onClick = onClick, modifier = modifier) {
        LevelRing(
            level = level,
            icon = icon,
            contentDescription = title,
            modifier = Modifier.size(42.dp),
            tint = if (checked) LevelRingGlyph else LevelRingGlyph.copy(alpha = 0.45f),
            ringColor = if (checked) LevelRingGlyph else LevelRingGlyph.copy(alpha = 0.35f),
            trackColor = if (checked) LevelRingTrack else LevelRingTrack.copy(alpha = 0.7f),
            strokeWidth = 2.5.dp,
            iconFraction = 0.5f
        )
    }
}

/**
 * The notifications entry of the same quickview indicators grid: identical
 * chrome to [IndicatorToggleBox] but the preview is the real bell glyph — a
 * notification has no level percentage to show inside a ring. Deliberately
 * badge-free: a hardcoded "3" here would misrepresent the live Notification
 * Center count the notch itself reads; the real badge only ever renders on the
 * island.
 */
@Composable
internal fun NotificationToggleBox(
    title: String,
    checked: Boolean,
    onClick: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    IndicatorCell(title = title, checked = checked, onClick = onClick, modifier = modifier) {
        Box(modifier = Modifier.size(42.dp), contentAlignment = Alignment.Center) {
            Icon(
                painter = notificationPainter(),
                contentDescription = null,
                tint = if (checked) LevelRingGlyph else LevelRingGlyph.copy(alpha = 0.45f),
                modifier = Modifier.size(29.dp)
            )
        }
    }
}

@Composable
internal fun WaveformToggleBox(
    title: String,
    checked: Boolean,
    onClick: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    IndicatorCell(title = title, checked = checked, onClick = onClick, modifier = modifier) {
        Box(modifier = Modifier.size(42.dp), contentAlignment = Alignment.Center) {
            // The real component, parked at its frozen phase: the preview is the
            // widget, not a redrawn copy of it, so it can never drift from what
            // the pill actually draws.
            MusicWaveform(
                playing = true,
                animate = false,
                color = if (checked) WaveformWhite else WaveformWhite.copy(alpha = 0.42f),
                bars = 7,
                modifier = Modifier
                    .size(width = 34.dp, height = 14.dp)
            )
        }
    }
}

/**
 * Shared chrome for one indicator-grid cell: the accent-outlined square toggle
 * with the caller-provided preview sitting above the centered label. The whole
 * cell toggles on the next click.
 */
@Composable
private fun IndicatorCell(
    title: String,
    checked: Boolean,
    onClick: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    preview: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (checked) FlatSurfaceSelected else FlatCard)
            .border(
                width = 1.dp,
                color = if (checked) AccentIndigoLight.copy(alpha = 0.9f) else FlatBorder,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable { onClick(!checked) }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            preview()
            Spacer(modifier = Modifier.height(7.dp))
            Text(
                text = title,
                style = AppTypography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = if (checked) TextPrimary else TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Helper row under the indicators grid for the one thing that still needs the
 * user's action: granting QuickNotch Full Disk Access so the quickview can read
 * the macOS Notification Center count. A short hint on the left, a compact
 * rectangular button on the right that deep-links straight to the Privacy &
 * Security pane. Rendered only while the store is unreadable.
 */
@Composable
internal fun NotificationGrantRow(
    hint: String,
    button: String,
    onGrant: () -> Unit
) {
    val darkText = Color(0xFF1A1B25)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = hint,
            style = AppTypography.bodySmall,
            color = TextSecondary,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(AccentIndigoLight)
                .clickable(onClick = onGrant)
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text(
                text = button,
                style = AppTypography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = darkText
            )
        }
    }
}

/**
 * The switch row used across the Behavior tab and the widget accordions:
 * optional leading icon chip, title, and a [MinimalistRectSwitch].
 */
@Composable
internal fun BehaviorSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(FlatSurfaceElevated)
                    .border(1.dp, FlatBorder, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = AccentIndigoLight,
                    modifier = Modifier.size(14.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
        }
        Text(
            text = title,
            style = AppTypography.bodyLarge,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary,
            modifier = Modifier.weight(1f)
        )
        MinimalistRectSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

/**
 * A collapsible widget card: header row (icon chip, title, enable switch,
 * expand chevron) over an animated content area. Used by the Widgets tab.
 */
@Composable
internal fun WidgetAccordion(
    icon: ImageVector,
    title: String,
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
                Text(
                    text = title,
                    style = AppTypography.titleSmall,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
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
