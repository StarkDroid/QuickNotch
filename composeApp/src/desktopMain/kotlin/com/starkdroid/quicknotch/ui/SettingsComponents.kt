package com.starkdroid.quicknotch.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starkdroid.quicknotch.model.NotchStyle
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

/** Static gradient for the mini-notch silhouettes in the style picker. */
private val miniPillBrush = Brush.verticalGradient(
    listOf(Color(0xFF2A3448), Color(0xFF11161F))
)

/**
 * Three-across visual picker for the notch silhouette. Each cell is a miniature
 * screen edge with the style drawn against it, so users select exactly the
 * shape they get — flush-and-squared, floating capsule, or slim bar. Pure
 * Compose shapes, so no drawable resources are involved.
 */
@Composable
internal fun NotchStylePicker(
    selected: NotchStyle,
    onSelect: (NotchStyle) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        NotchStyle.entries.forEach { style ->
            val isSelected = style == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) FlatSurfaceSelected else FlatCard)
                    .border(
                        width = 1.dp,
                        color = if (isSelected) AccentIndigoLight.copy(alpha = 0.9f) else FlatBorder,
                        shape = RoundedCornerShape(10.dp)
                    )
                    .clickable { onSelect(style) }
                    .padding(top = 8.dp, bottom = 10.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    NotchMiniPreview(style)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = style.label(),
                        style = AppTypography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) Color.White else TextSecondary
                    )
                }

                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(AccentIndigoLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color(0xFF0C0E14),
                            modifier = Modifier.size(10.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * A tiny screen edge + notch silhouette for [NotchStylePicker]. A hairline at
 * the top suggests the screen edge; the style body forecasts exactly how the
 * real pill meets — or floats off — that edge.
 */
@Composable
private fun NotchMiniPreview(style: NotchStyle) {
    Box(
        modifier = Modifier
            .width(56.dp)
            .height(38.dp)
    ) {
        // Screen edge hairline the styles anchor to or float away from.
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(2.dp)
                .background(FlatBorder)
        )

        val topPadding: Dp
        val shape: RoundedCornerShape
        when (style) {
            NotchStyle.DYNAMIC_ISLAND -> {
                topPadding = 3.dp
                shape = RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp)
            }
            NotchStyle.FLOATING_PILL -> {
                topPadding = 8.dp
                shape = RoundedCornerShape(percent = 50)
            }
            NotchStyle.DRAG_HANDLE -> {
                topPadding = 9.dp
                shape = RoundedCornerShape(percent = 50)
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = topPadding)
                .fillMaxWidth(
                    when (style) {
                        NotchStyle.DYNAMIC_ISLAND -> 0.64f
                        NotchStyle.FLOATING_PILL -> 0.72f
                        NotchStyle.DRAG_HANDLE -> 0.46f
                    }
                )
                .height(
                    when (style) {
                        NotchStyle.DYNAMIC_ISLAND -> 26.dp
                        NotchStyle.FLOATING_PILL -> 18.dp
                        NotchStyle.DRAG_HANDLE -> 7.dp
                    }
                )
                .clip(shape)
                .background(miniPillBrush)
        )
    }
}