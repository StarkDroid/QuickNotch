package com.starkdroid.quicknotch.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starkdroid.quicknotch.theme.AppTypography
import com.starkdroid.quicknotch.theme.TextMuted
import com.starkdroid.quicknotch.theme.TextPrimary
import com.starkdroid.quicknotch.theme.TextSecondary
import java.util.Locale

// ---------------------------------------------------------------------------
// Settings-scoped palette. Mirrors the web module's design language (flat,
// gradient-free accents, indigo family, soft borders) but scoped to the
// settings window so nothing leaks into the notch overlay's own palette.
// ---------------------------------------------------------------------------
internal val FlatBg = Color(0xFF0C0E14)
internal val FlatSurface = Color(0xFF131722)
internal val FlatSurfaceElevated = Color(0xFF181E2E)
internal val FlatSurfaceSelected = Color(0xFF1B2236)
internal val FlatBorder = Color(0xFF22283A)
internal val FlatCard = Color(0xFF121622)

// Web-aligned accent tokens (values copied from web/src/app.css).
internal val WebIndigo = Color(0xFF4F46E5)         // --indigo
internal val WebIndigoLight = Color(0xFF6366F1)    // --indigo-light
internal val WebIndigoSoft = Color(0x244F46E5)     // indigo at ~14% for soft chips
internal val WebCyan = Color(0xFF0891B2)
internal val WebPurple = Color(0xFF7E22CE)
internal val WebGreen = Color(0xFF16A34A)
internal val WebRed = Color(0xFFDC2626)
internal val WebRedSoft = Color(0x24DC2626)

/**
 * Small uppercase "eyebrow" label used to kick off every settings section,
 * matching the web module's mono dashed section markers.
 */
@Composable
internal fun EyebrowText(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(3.dp)
                .clip(CircleShape)
                .background(WebIndigo)
        )
        Text(
            text = text.uppercase(Locale.ROOT),
            style = AppTypography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = TextMuted,
            fontSize = 10.sp,
            letterSpacing = 1.2.sp
        )
    }
}

/**
 * Settings "section card": a flat bordered surface that opens with an eyebrow,
 * a title and a hint body, then hosts the content. Replaces the repeated
 * raw Card-in-LazyColumn blocks with one web-consistent container.
 */
@Composable
internal fun SettingsSectionCard(
    eyebrow: String,
    title: String,
    body: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = FlatSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, FlatBorder)
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
            EyebrowText(eyebrow)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = AppTypography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            if (body != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = body,
                    style = AppTypography.bodySmall,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
    }
}

/**
 * Settings page header: eyebrow + bold title (and an optional action slot on
 * the right, e.g. the reset-defaults button). Scrolls with the list.
 */
@Composable
internal fun SettingsPageHeader(
    eyebrow: String,
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            EyebrowText(eyebrow)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                style = AppTypography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }
        action?.invoke()
    }
}

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
            color = TextPrimary,
            fontSize = 13.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(FlatCard)
                .border(1.dp, FlatBorder, RoundedCornerShape(12.dp))
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 10.dp)
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
                            color = if (option == selected) WebIndigoLight else TextSecondary,
                            fontSize = 12.sp
                        )
                        if (option == selected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = WebIndigoLight,
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
            Text(title, style = AppTypography.bodySmall, color = TextSecondary, fontSize = 12.sp)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(FlatSurfaceElevated)
                    .border(1.dp, FlatBorder, RoundedCornerShape(999.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
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
                        .size(width = 10.dp, height = 16.dp)
                        .shadow(2.dp, RoundedCornerShape(999.dp))
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xFFF1F5F9))
                        .border(0.5.dp, WebIndigoLight, RoundedCornerShape(999.dp))
                )
            },
            track = { sliderState ->
                SliderDefaults.Track(
                    sliderState = sliderState,
                    modifier = Modifier.height(3.dp),
                    colors = SliderDefaults.colors(
                        activeTrackColor = WebIndigo,
                        inactiveTrackColor = Color(0xFF1E2436),
                        thumbColor = WebIndigoLight
                    )
                )
            }
        )
    }
}

/**
 * A pill-shaped flat toggle matching the web module's switches — solid indigo
 * when on, hairline-bordered surface when off. Gradient-free by design.
 */
@Composable
internal fun MinimalSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 20.dp else 2.dp,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 550f),
        label = "switchOffset"
    )

    val trackBg = if (checked) WebIndigo else FlatSurfaceElevated
    val trackBorder = if (checked) WebIndigoLight else FlatBorder
    val thumbBg = if (checked) Color(0xFFFFFFFF) else Color(0xFF9AA7BF)
    val thumbBorder = if (checked) Color(0x20000000) else Color(0x33FFFFFF)

    Box(
        modifier = modifier
            .width(40.dp)
            .height(22.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(trackBg)
            .border(1.dp, trackBorder, RoundedCornerShape(999.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 2.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(width = 16.dp, height = 16.dp)
                .shadow(if (checked) 2.dp else 1.dp, CircleShape)
                .clip(CircleShape)
                .background(thumbBg)
                .border(0.5.dp, thumbBorder, CircleShape)
        )
    }
}

/**
 * Small pill chip tinted with a color (used for the "Pinned" badge and the
 * about dialog tags). Soft tinted fill + colored accent text, like web chips.
 */
@Composable
internal fun SettingsChip(
    text: String,
    tint: Color,
    modifier: Modifier = Modifier,
    tintSoft: Color = tint.copy(alpha = 0.14f)
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(tintSoft)
            .border(1.dp, tint.copy(alpha = 0.35f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            style = AppTypography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = tint,
            fontSize = 11.sp
        )
    }
}