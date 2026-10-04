package com.starkdroid.quicknotch.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starkdroid.quicknotch.theme.AccentSolid
import com.starkdroid.quicknotch.theme.AccentSoft
import com.starkdroid.quicknotch.theme.AppTypography
import com.starkdroid.quicknotch.theme.SettingsBorder
import com.starkdroid.quicknotch.theme.SettingsBorderStrong
import com.starkdroid.quicknotch.theme.SettingsCard
import com.starkdroid.quicknotch.theme.SettingsCardHover
import com.starkdroid.quicknotch.theme.SettingsElevated
import com.starkdroid.quicknotch.theme.SettingsSelected
import com.starkdroid.quicknotch.theme.SwitchThumbOff
import com.starkdroid.quicknotch.theme.SwitchThumbOn
import com.starkdroid.quicknotch.theme.SwitchTrackOff
import com.starkdroid.quicknotch.theme.SwitchTrackOn
import com.starkdroid.quicknotch.theme.TextMuted
import com.starkdroid.quicknotch.theme.TextPrimary
import com.starkdroid.quicknotch.theme.TextSecondary

// ── Shared interaction helpers ───────────────────────────────────────────────
// Every hoverable settings/onboarding control used to redeclare the same two
// booleans, the same four pointer handlers and the same ripple-free clickable.
// They share one state shape and three helpers instead.

/** Hover + press for one interactive element. */
internal class HoverPressState {
    var hovered by mutableStateOf(false)
    var pressed by mutableStateOf(false)
}

@Composable
internal fun rememberHoverPressState(): HoverPressState = remember { HoverPressState() }

/** Feeds cursor enter/exit/press/release into [state]. */
@OptIn(ExperimentalComposeUiApi::class)
internal fun Modifier.trackHoverPress(state: HoverPressState): Modifier = this
    .onPointerEvent(PointerEventType.Enter) { state.hovered = true }
    .onPointerEvent(PointerEventType.Exit) { state.hovered = false }
    .onPointerEvent(PointerEventType.Press) { state.pressed = true }
    .onPointerEvent(PointerEventType.Release) { state.pressed = false }

/**
 * Clickable without a ripple — settings/onboarding signal interaction through
 * background/border/scale animation instead, so a Material ripple would
 * double-announce every press.
 */
@Composable
internal fun Modifier.plainClickable(onClick: () -> Unit): Modifier = this.clickable(
    interactionSource = remember { MutableInteractionSource() },
    indication = null,
    onClick = onClick
)

/** Spring dip on press, optional lift on hover. One tuning for all controls. */
@Composable
internal fun hoverPressScale(
    state: HoverPressState,
    hoveredScale: Float = 1f,
    pressedScale: Float = 0.96f
): Float {
    val target = if (state.pressed) pressedScale else if (state.hovered) hoveredScale else 1f
    val scale by animateFloatAsState(
        targetValue = target,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 520f),
        label = "hoverPressScale"
    )
    return scale
}

/**
 * One settings card: solid surface, 16dp radius, hairline border. The single
 * container every tab section uses, so Appearance / Widgets / Behavior read as
 * one system instead of three card styles.
 */
@Composable
internal fun SettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SettingsCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, SettingsBorder),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp), content = content)
    }
}

/**
 * Page header for the content pane: 19sp semibold title over a 12.5sp
 * secondary description. Rendered per tab so the sidebar selection always has
 * a matching headline.
 */
@Composable
internal fun SettingsPageHeader(title: String, description: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = AppTypography.titleMedium,
            fontWeight = FontWeight.Bold,
            fontSize = 19.sp,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = description,
            style = AppTypography.bodySmall,
            color = TextSecondary,
            fontSize = 12.5.sp,
            lineHeight = 17.sp
        )
    }
}

/**
 * One sidebar navigation row: icon chip + label with an animated selected
 * pill. Hover lifts the background, press dips the scale — the whole row is
 * the target, not just the label.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun SidebarNavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hoverPress = rememberHoverPressState()
    val hovered = hoverPress.hovered
    val pressed = hoverPress.pressed
    val bg by animateColorAsState(
        targetValue = when {
            selected -> SettingsSelected
            hovered -> SettingsCardHover
            else -> Color.Transparent
        },
        animationSpec = tween(140),
        label = "navBg"
    )
    val border by animateColorAsState(
        targetValue = if (selected) AccentSolid.copy(alpha = 0.55f)
        else if (hovered) SettingsBorderStrong else Color.Transparent,
        animationSpec = tween(140),
        label = "navBorder"
    )
    val scale = hoverPressScale(hoverPress, pressedScale = 0.97f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(10.dp))
            .trackHoverPress(hoverPress)
            .plainClickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selected) AccentSoft else SettingsElevated)
                    .border(
                        1.dp,
                        if (selected) AccentSolid.copy(alpha = 0.4f) else SettingsBorder,
                        RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) AccentSolid else TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = label,
                style = AppTypography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                fontSize = 13.5.sp,
                color = if (selected) TextPrimary else TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (false) {
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(AccentSolid)
                )
            }
        }
    }
}

/**
 * Primary action button: solid accent fill, ink text, hover lightens the fill
 * and press dips the scale. Used for Continue/Finish, Browse-adjacent confirms
 * and the grant-access call to action.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hoverPress = rememberHoverPressState()
    val hovered = hoverPress.hovered
    val pressed = hoverPress.pressed
    val bg by animateColorAsState(
        targetValue = if (hovered) Color(0xFF939BFF) else AccentSolid,
        animationSpec = tween(120),
        label = "primaryBg"
    )
    val scale = hoverPressScale(hoverPress)
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .trackHoverPress(hoverPress)
            .plainClickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = AppTypography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF14161F)
        )
    }
}

/**
 * Quiet ghost button: transparent until hovered, then a soft solid wash.
 * Used for Skip/Back/Cancel-adjacent actions.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hoverPress = rememberHoverPressState()
    val hovered = hoverPress.hovered
    val bg by animateColorAsState(
        targetValue = if (hovered) SettingsCardHover else Color.Transparent,
        animationSpec = tween(120),
        label = "ghostBg"
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .trackHoverPress(hoverPress)
            .plainClickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = AppTypography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = TextSecondary
        )
    }
}

/**
 * A minimal, flat selection dropdown (label above, current value, popup menu)
 * shared by the style and surface pickers. Popup rows are plain composables, so
 * opening it costs one small popup — no preview textures are ever rendered.
 * Hover highlights the field border; the chevron rotates open.
 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class)
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
    val hoverPress = rememberHoverPressState()
    val hovered = hoverPress.hovered
    val chevron by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 500f),
        label = "dropdownChevron"
    )
    val border by animateColorAsState(
        targetValue = when {
            expanded -> AccentSolid.copy(alpha = 0.6f)
            hovered -> SettingsBorderStrong
            else -> SettingsBorder
        },
        animationSpec = tween(140),
        label = "dropdownBorder"
    )
    Column(modifier = modifier) {
        Text(
            text = label,
            style = AppTypography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(SettingsElevated)
                .border(1.dp, border, RoundedCornerShape(10.dp))
                .trackHoverPress(hoverPress)
                .plainClickable(onClick = { expanded = true })
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
                    fontSize = 12.5.sp,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier
                        .size(16.dp)
                        .graphicsLayer { rotationZ = chevron }
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(SettingsElevated)
            ) {
                options.forEach { option ->
                    val isSelected = option == selected
                    val rowHover = rememberHoverPressState()
                    val rowHovered = rowHover.hovered
                    val rowBg by animateColorAsState(
                        targetValue = when {
                            isSelected -> AccentSoft
                            rowHovered -> SettingsCardHover
                            else -> Color.Transparent
                        },
                        animationSpec = tween(110),
                        label = "dropdownRow"
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(rowBg)
                            .trackHoverPress(rowHover)
                            .plainClickable(
                                onClick = {
                                    onSelect(option)
                                    expanded = false
                                }
                            )
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = optionLabel(option),
                            style = AppTypography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) AccentSolid else TextSecondary
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = AccentSolid,
                                modifier = Modifier.size(14.dp)
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
            Text(title, style = AppTypography.bodySmall, fontSize = 12.5.sp, color = TextSecondary)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(SettingsElevated)
                    .border(1.dp, SettingsBorder, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
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
        Spacer(modifier = Modifier.height(4.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.height(22.dp),
            thumb = {
                Box(
                    modifier = Modifier
                        .size(15.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFE8ECF4))
                        .border(1.dp, Color(0xFF8A94A8), RoundedCornerShape(8.dp))
                )
            },
            track = { sliderState ->
                SliderDefaults.Track(
                    sliderState = sliderState,
                    modifier = Modifier.height(4.dp),
                    colors = SliderDefaults.colors(
                        activeTrackColor = AccentSolid,
                        inactiveTrackColor = SettingsBorder
                    )
                )
            }
        )
    }
}

/**
 * Flat rectangular switch: solid on/off tracks (no gradients), spring thumb,
 * hover border lift and press dip. 38×22 with a 7dp radius.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun MinimalistRectSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val hoverPress = rememberHoverPressState()
    val hovered = hoverPress.hovered
    val pressed = hoverPress.pressed
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 18.dp else 3.dp,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 550f),
        label = "switchOffset"
    )
    val track by animateColorAsState(
        targetValue = if (checked) SwitchTrackOn else SwitchTrackOff,
        animationSpec = tween(150),
        label = "switchTrack"
    )
    val border by animateColorAsState(
        targetValue = when {
            checked -> AccentSolid.copy(alpha = 0.7f)
            hovered -> SettingsBorderStrong
            else -> SettingsBorder
        },
        animationSpec = tween(140),
        label = "switchBorder"
    )
    val scale = hoverPressScale(hoverPress, pressedScale = 0.93f)
    Box(
        modifier = modifier
            .width(38.dp)
            .height(22.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(7.dp))
            .background(track)
            .border(1.dp, border, RoundedCornerShape(7.dp))
            .trackHoverPress(hoverPress)
            .plainClickable(onClick = { onCheckedChange(!checked) })
            .padding(vertical = 3.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(width = 15.dp, height = 14.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (checked) SwitchThumbOn else SwitchThumbOff)
        )
    }
}


// ── Shared section chrome (used by multiple tabs) ────────────────────────────

/**
 * Card section header: icon chip beside title, optional subtitle underneath.
 * Used across every settings card so each section reads as one scannable unit.
 */
@Composable
internal fun SectionHeader(icon: ImageVector, title: String, subtitle: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SettingsElevated)
                .border(1.dp, SettingsBorder, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AccentSolid,
                modifier = Modifier.size(14.dp)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = title,
                style = AppTypography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = TextPrimary
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = AppTypography.bodySmall,
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        }
    }
}

/**
 * One cell in the quickview indicators grid (Volume / Audio Battery /
 * Laptop Battery): a square toggle that previews the level-ring glyph and its
 * level, with the label centered underneath. Hover lifts it, press dips it,
 * and the border animates to the accent when on.
 */
@OptIn(ExperimentalComposeUiApi::class)
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
 * badge-free: a hardcoded count here would misrepresent the live Notification
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
 * with the caller-provided preview sitting above the centered label. Hover
 * lifts the cell slightly, press dips it, and the whole cell toggles on click.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun IndicatorCell(
    title: String,
    checked: Boolean,
    onClick: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    preview: @Composable () -> Unit
) {
    val hoverPress = rememberHoverPressState()
    val hovered = hoverPress.hovered
    val pressed = hoverPress.pressed
    val bg by animateColorAsState(
        targetValue = when {
            checked -> SettingsSelected
            hovered -> SettingsCardHover
            else -> SettingsCard
        },
        animationSpec = tween(130),
        label = "indicatorBg"
    )
    val border by animateColorAsState(
        targetValue = when {
            checked -> AccentSolid.copy(alpha = 0.65f)
            hovered -> SettingsBorderStrong
            else -> SettingsBorder
        },
        animationSpec = tween(130),
        label = "indicatorBorder"
    )
    val scale = hoverPressScale(hoverPress, hoveredScale = 1.02f)
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(12.dp))
            .trackHoverPress(hoverPress)
            .plainClickable(onClick = { onClick(!checked) })
            .padding(vertical = 12.dp, horizontal = 6.dp),
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
 * solid button on the right that deep-links straight to the Privacy &
 * Security pane. Rendered only while the store is unreadable.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun NotificationGrantRow(
    hint: String,
    button: String,
    onGrant: () -> Unit
) {
    val hoverPress = rememberHoverPressState()
    val hovered = hoverPress.hovered
    val btnBg by animateColorAsState(
        targetValue = if (hovered) Color(0xFF939BFF) else AccentSolid,
        animationSpec = tween(120),
        label = "grantBg"
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = hint,
            style = AppTypography.bodySmall,
            fontSize = 12.sp,
            color = TextSecondary,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(btnBg)
                .trackHoverPress(hoverPress)
                .plainClickable(onClick = onGrant)
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text(
                text = button,
                style = AppTypography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF14161F)
            )
        }
    }
}

/**
 * The switch row used across the Behavior tab and the widget accordions:
 * icon chip, title (+ optional subtitle), and a [MinimalistRectSwitch].
 * The whole row highlights softly on hover.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun BehaviorSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector? = null,
    subtitle: String? = null
) {
    val hoverPress = rememberHoverPressState()
    val hovered = hoverPress.hovered
    val rowBg by animateColorAsState(
        targetValue = if (hovered) SettingsCardHover else Color.Transparent,
        animationSpec = tween(120),
        label = "switchRowBg"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(rowBg)
            .trackHoverPress(hoverPress)
            .plainClickable(onClick = { onCheckedChange(!checked) })
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SettingsElevated)
                    .border(1.dp, SettingsBorder, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = AccentSolid,
                    modifier = Modifier.size(15.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = AppTypography.bodyLarge,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = AppTypography.bodySmall,
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        }
        MinimalistRectSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

/**
 * A collapsible widget card: header row (icon chip, title, enable switch,
 * rotating chevron) over an animated content area. Used by the Widgets tab.
 * The header lifts on hover and the chevron springs 180° when opened.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun WidgetAccordion(
    icon: ImageVector,
    title: String,
    enabled: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val hoverPress = rememberHoverPressState()
    val hovered = hoverPress.hovered
    val chevron by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 480f),
        label = "accordionChevron"
    )
    val border by animateColorAsState(
        targetValue = if (expanded) SettingsBorderStrong else SettingsBorder,
        animationSpec = tween(150),
        label = "accordionBorder"
    )
    val headerBg by animateColorAsState(
        targetValue = if (hovered) SettingsCardHover else Color.Transparent,
        animationSpec = tween(120),
        label = "accordionHeaderBg"
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SettingsCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, border),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(headerBg)
                    .trackHoverPress(hoverPress)
                    .plainClickable(onClick = { expanded = !expanded })
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(SettingsElevated)
                        .border(1.dp, SettingsBorder, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = AccentSolid,
                        modifier = Modifier.size(19.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    style = AppTypography.titleSmall,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                MinimalistRectSwitch(checked = enabled, onCheckedChange = onToggleEnabled)
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier
                        .size(20.dp)
                        .graphicsLayer { rotationZ = chevron }
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(
                    animationSpec = spring(dampingRatio = 0.85f, stiffness = 380f)
                ) + fadeIn(tween(180)),
                exit = shrinkVertically(
                    animationSpec = spring(dampingRatio = 0.9f, stiffness = 420f)
                ) + fadeOut(tween(140))
            ) {
                Box(modifier = Modifier.fillMaxWidth()) { content() }
            }
        }
    }
}

/**
 * Staggered entrance for tab sections: fades + rises 10dp with a per-index
 * delay so a tab lands as a cascade instead of a flat pop. Pure enter
 * transition — no exit, sections never leave except on tab switch.
 */
@Composable
internal fun TabEntrance(
    index: Int,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(220, delayMillis = index * 55)) +
            expandVertically(
                animationSpec = spring(dampingRatio = 0.9f, stiffness = 420f),
                expandFrom = Alignment.Top
            ),
        exit = fadeOut(tween(120)),
        label = "tabEntrance$index"
    ) {
        content()
    }
}
