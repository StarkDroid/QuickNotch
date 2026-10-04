package com.starkdroid.quicknotch.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starkdroid.quicknotch.generated.resources.Res
import com.starkdroid.quicknotch.generated.resources.weather_chip
import com.starkdroid.quicknotch.generated.resources.weather_clear
import com.starkdroid.quicknotch.generated.resources.weather_cloud
import com.starkdroid.quicknotch.generated.resources.weather_cond_clear
import com.starkdroid.quicknotch.generated.resources.weather_cond_cloudy
import com.starkdroid.quicknotch.generated.resources.weather_cond_fog
import com.starkdroid.quicknotch.generated.resources.weather_cond_night
import com.starkdroid.quicknotch.generated.resources.weather_cond_partly
import com.starkdroid.quicknotch.generated.resources.weather_cond_partly_night
import com.starkdroid.quicknotch.generated.resources.weather_cond_rain
import com.starkdroid.quicknotch.generated.resources.weather_cond_snow
import com.starkdroid.quicknotch.generated.resources.weather_cond_storm
import com.starkdroid.quicknotch.generated.resources.weather_failed_hint
import com.starkdroid.quicknotch.generated.resources.weather_fog
import com.starkdroid.quicknotch.generated.resources.weather_high_low
import com.starkdroid.quicknotch.generated.resources.weather_locating
import com.starkdroid.quicknotch.generated.resources.weather_never_synced
import com.starkdroid.quicknotch.generated.resources.weather_night
import com.starkdroid.quicknotch.generated.resources.weather_partly
import com.starkdroid.quicknotch.generated.resources.weather_partly_night
import com.starkdroid.quicknotch.generated.resources.weather_precip
import com.starkdroid.quicknotch.generated.resources.weather_rain
import com.starkdroid.quicknotch.generated.resources.weather_refresh
import com.starkdroid.quicknotch.generated.resources.weather_show_title
import com.starkdroid.quicknotch.generated.resources.weather_snow
import com.starkdroid.quicknotch.generated.resources.weather_storm
import com.starkdroid.quicknotch.generated.resources.weather_today
import com.starkdroid.quicknotch.generated.resources.weather_unavailable
import com.starkdroid.quicknotch.generated.resources.weather_unit_celsius
import com.starkdroid.quicknotch.generated.resources.weather_unit_fahrenheit
import com.starkdroid.quicknotch.generated.resources.weather_units_title
import com.starkdroid.quicknotch.generated.resources.weather_updated
import com.starkdroid.quicknotch.generated.resources.weather_uv
import com.starkdroid.quicknotch.theme.AccentSolid
import com.starkdroid.quicknotch.theme.AppTypography
import com.starkdroid.quicknotch.theme.NotchBorder
import com.starkdroid.quicknotch.theme.SettingsBorder
import com.starkdroid.quicknotch.theme.SettingsElevated
import com.starkdroid.quicknotch.theme.TextMuted
import com.starkdroid.quicknotch.theme.TextPrimary
import com.starkdroid.quicknotch.theme.TextSecondary
import com.starkdroid.quicknotch.theme.WeatherClear
import com.starkdroid.quicknotch.theme.WeatherCloudy
import com.starkdroid.quicknotch.theme.WeatherFog
import com.starkdroid.quicknotch.theme.WeatherMoon
import com.starkdroid.quicknotch.theme.WeatherPartly
import com.starkdroid.quicknotch.theme.WeatherRain
import com.starkdroid.quicknotch.theme.WeatherSnow
import com.starkdroid.quicknotch.theme.WeatherStorm
import com.starkdroid.quicknotch.weather.WeatherData
import com.starkdroid.quicknotch.weather.WeatherDay
import com.starkdroid.quicknotch.weather.WeatherState
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * WMO weather-code groups to the packaged condition glyph. Solid
 * `currentColor` silhouettes like every other ring/pill glyph, so they tint
 * fully under `ColorFilter`-style SrcIn painting. Clear/partly resolve to the
 * moon variants after dark via the feed's `is_day` flag.
 */
internal enum class WeatherCondition {
    CLEAR, PARTLY, CLOUDY, FOG, RAIN, SNOW, STORM;

    companion object {
        fun of(code: Int): WeatherCondition = when (code) {
            0 -> CLEAR
            1, 2 -> PARTLY
            3 -> CLOUDY
            45, 48 -> FOG
            51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> RAIN
            71, 73, 75, 77, 85, 86 -> SNOW
            else -> if (code >= 95) STORM else CLOUDY
        }
    }
}

@Composable
internal fun WeatherCondition.painter(isDay: Boolean = true): Painter = painterResource(
    when (this) {
        WeatherCondition.CLEAR -> if (isDay) Res.drawable.weather_clear else Res.drawable.weather_night
        WeatherCondition.PARTLY ->
            if (isDay) Res.drawable.weather_partly else Res.drawable.weather_partly_night
        WeatherCondition.CLOUDY -> Res.drawable.weather_cloud
        WeatherCondition.FOG -> Res.drawable.weather_fog
        WeatherCondition.RAIN -> Res.drawable.weather_rain
        WeatherCondition.SNOW -> Res.drawable.weather_snow
        WeatherCondition.STORM -> Res.drawable.weather_storm
    }
)

@Composable
internal fun WeatherCondition.label(isDay: Boolean = true): String = stringResource(
    when (this) {
        WeatherCondition.CLEAR ->
            if (isDay) Res.string.weather_cond_clear else Res.string.weather_cond_night
        WeatherCondition.PARTLY ->
            if (isDay) Res.string.weather_cond_partly else Res.string.weather_cond_partly_night
        WeatherCondition.CLOUDY -> Res.string.weather_cond_cloudy
        WeatherCondition.FOG -> Res.string.weather_cond_fog
        WeatherCondition.RAIN -> Res.string.weather_cond_rain
        WeatherCondition.SNOW -> Res.string.weather_cond_snow
        WeatherCondition.STORM -> Res.string.weather_cond_storm
    }
)

/**
 * Glyph tint for one condition in signature colours (Google-style):
 * amber sun, pale moon, blue rain, etc., on every surface alike.
 */
internal fun weatherConditionTint(
    condition: WeatherCondition,
    isDay: Boolean,
): Color {
    return when (condition) {
        WeatherCondition.CLEAR -> if (isDay) WeatherClear else WeatherMoon
        WeatherCondition.PARTLY -> if (isDay) WeatherPartly else WeatherMoon
        WeatherCondition.CLOUDY -> WeatherCloudy
        WeatherCondition.FOG -> WeatherFog
        WeatherCondition.RAIN -> WeatherRain
        WeatherCondition.SNOW -> WeatherSnow
        WeatherCondition.STORM -> WeatherStorm
    }
}

/** Display temperature: the feed is always metric, Fahrenheit is a render conversion. */
internal fun weatherTemp(celsius: Double, fahrenheit: Boolean): String {
    val value = if (fahrenheit) celsius * 9.0 / 5.0 + 32.0 else celsius
    return "${value.roundToInt()}°"
}

internal fun weatherTempUnit(fahrenheit: Boolean): String = if (fahrenheit) "°F" else "°C"

/**
 * The quickview weather chip: condition glyph + temperature, drawn first in
 * the idle clock row so it sits left of the date. Shown only while a forecast
 * is loaded — an unlocated/failed feed hides the chip rather than showing a
 * false "--°".
 */
@Composable
internal fun WeatherQuickChip(
    data: WeatherData,
    fahrenheit: Boolean,
    tint: Color,
    modifier: Modifier = Modifier
) {
    val condition = WeatherCondition.of(data.now.code)
    val description = weatherChipDescription(data, fahrenheit)
    Row(
        modifier = modifier.semantics {
            contentDescription = description
        },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = condition.painter(data.now.isDay),
            contentDescription = null,
            tint = weatherConditionTint(condition, data.now.isDay),
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = weatherTemp(data.now.temperature, fahrenheit),
            color = tint,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

/** Accessibility label for the chip: "21°, Clear sky". */
@Composable
internal fun weatherChipDescription(data: WeatherData, fahrenheit: Boolean): String =
    stringResource(
        Res.string.weather_chip,
        weatherTemp(data.now.temperature, fahrenheit),
        WeatherCondition.of(data.now.code).label(data.now.isDay)
    )

/**
 * The expanded notch's weather page: current conditions on the left (glyph,
 * temperature, place, today's high/low plus precipitation chance and UV
 * index), then one tile per outlook day. Mirrors the calendar page's shape —
 * a schedule in a single row that fits the island's depth.
 *
 * Loading is never blocking: with nothing cached yet the page is a spinner,
 * and a failed feed with no cache says so with a tap-to-retry chip instead
 * of a dead stop.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun WeatherNotchPage(
    state: WeatherState,
    fetching: Boolean,
    fahrenheit: Boolean,
    interactive: Boolean,
    onRefresh: () -> Unit
) {
    val snapshot = state as? WeatherState.Ready
    val data = snapshot?.data
    when {
        data == null && fetching -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(15.dp),
                color = AccentSolid,
                strokeWidth = 2.dp
            )
        }
        data == null -> {
            // Failed with nothing cached: an honest error plus a retry that
            // only exists while the page is the one under the cursor (an
            // alpha-0 clickable would steal hits from the page beneath it).
            WeatherErrorPage(
                interactive = interactive,
                onRefresh = onRefresh
            )
        }
        else -> Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = DockLayout.ContentPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WeatherCurrentBlock(data = data, fahrenheit = fahrenheit)
            Box(
                modifier = Modifier
                    .padding(horizontal = 10.dp)
                    .width(1.dp)
                    .height(40.dp)
                    .background(NotchBorder)
            )
            data.days.take(7).forEachIndexed { index, day ->
                if (index > 0) Spacer(modifier = Modifier.width(2.dp))
                WeatherDayTile(
                    day = day,
                    today = index == 0,
                    fahrenheit = fahrenheit,
                                        modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/** Failed-with-no-cache branch of the page: honest error + tap-to-retry. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun WeatherErrorPage(interactive: Boolean, onRefresh: () -> Unit) {
    var hovered by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (hovered) 1.04f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 420f),
        label = "weatherRetryHover"
    )
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = DockLayout.ContentPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(Res.string.weather_failed_hint),
            style = AppTypography.bodySmall,
            color = TextMuted,
            fontSize = 11.5.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(RoundedCornerShape(8.dp))
                .background(SettingsElevated)
                .border(1.dp, SettingsBorder, RoundedCornerShape(8.dp))
                .then(
                    if (interactive) {
                        Modifier
                            .onPointerEvent(PointerEventType.Enter) { hovered = true }
                            .onPointerEvent(PointerEventType.Exit) { hovered = false }
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onRefresh
                            )
                    } else Modifier
                )
                .padding(horizontal = 12.dp, vertical = 7.dp)
        ) {
            Text(
                text = stringResource(Res.string.weather_refresh),
                style = AppTypography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondary
            )
        }
    }
}

/** Left anchor: glyph + temperature over place, high/low and precip/UV. */
@Composable
private fun WeatherCurrentBlock(data: WeatherData, fahrenheit: Boolean) {
    val condition = WeatherCondition.of(data.now.code)
    val unit = weatherTempUnit(fahrenheit)
    Column(modifier = Modifier.width(132.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = condition.painter(data.now.isDay),
                contentDescription = null,
                tint = weatherConditionTint(condition, data.now.isDay),
                modifier = Modifier.size(30.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = weatherTemp(data.now.temperature, fahrenheit) + unit,
                    style = AppTypography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    fontSize = 21.sp,
                    color = TextPrimary,
                    maxLines = 1
                )
                if (data.place.isNotBlank()) {
                    Text(
                        text = data.place,
                        style = AppTypography.labelSmall,
                        color = TextSecondary,
                        fontSize = 10.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        val today = data.days.firstOrNull()
        if (today?.high != null && today.low != null) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(
                    Res.string.weather_high_low,
                    weatherTemp(today.high, fahrenheit),
                    weatherTemp(today.low, fahrenheit)
                ),
                style = AppTypography.bodySmall,
                color = TextSecondary,
                fontSize = 10.5.sp,
                maxLines = 1
            )
        }
        val extras = listOfNotNull(
            today?.precipitationProbability?.let {
                stringResource(Res.string.weather_precip, it)
            },
            today?.uvIndexMax?.let {
                stringResource(Res.string.weather_uv, it.roundToInt().toString())
            }
        )
        if (extras.isNotEmpty()) {
            Text(
                text = extras.joinToString(" · "),
                style = AppTypography.bodySmall,
                color = TextMuted,
                fontSize = 10.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** One outlook day: weekday over glyph over high/low. Outlook days are daytime. */
@Composable
private fun WeatherDayTile(
    day: WeatherDay,
    today: Boolean,
    fahrenheit: Boolean,
        modifier: Modifier = Modifier
) {
    val condition = WeatherCondition.of(day.code)
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (today) stringResource(Res.string.weather_today)
            else weatherDow(day.dateIso),
            style = AppTypography.labelSmall,
            fontWeight = if (today) FontWeight.SemiBold else FontWeight.Normal,
            color = if (today) TextPrimary else TextMuted,
            fontSize = 10.sp,
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(3.dp))
        Icon(
            painter = condition.painter(isDay = true),
            contentDescription = condition.label(isDay = true),
            tint = weatherConditionTint(condition, isDay = true),
            modifier = Modifier.size(15.dp)
        )
        Spacer(modifier = Modifier.height(3.dp))
        if (day.high != null) {
            Text(
                text = weatherTemp(day.high, fahrenheit),
                style = AppTypography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                fontSize = 11.sp,
                maxLines = 1
            )
        }
        if (day.low != null) {
            Text(
                text = weatherTemp(day.low, fahrenheit),
                style = AppTypography.labelSmall,
                color = TextMuted,
                fontSize = 10.sp,
                maxLines = 1
            )
        }
    }
}

/** Short weekday name in the system locale ("Mon"), from an ISO date. */
private fun weatherDow(dateIso: String): String = runCatching {
    LocalDate.parse(dateIso).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
}.getOrNull() ?: dateIso

/**
 * The Widgets-tab weather card: live status (place + sync age), manual
 * refresh, the notch toggle, the temperature unit, and the icon style
 * No connection flow —
 * the feed locates itself, so there is nothing to connect.
 */
@Composable
internal fun WeatherSettings(
    state: WeatherState,
    fetching: Boolean,
    showWeather: Boolean,
    onShowWeatherChange: (Boolean) -> Unit,
    fahrenheit: Boolean,
    onFahrenheitChange: (Boolean) -> Unit,
    onRefresh: () -> Unit
) {
    val data = (state as? WeatherState.Ready)?.data
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when {
                        data != null && data.place.isNotBlank() -> data.place
                        state is WeatherState.Failed ->
                            stringResource(Res.string.weather_unavailable)
                        else -> stringResource(Res.string.weather_locating)
                    },
                    style = AppTypography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (data != null && data.fetchedAtMillis > 0L) {
                        stringResource(Res.string.weather_updated, weatherAgo(data.fetchedAtMillis))
                    } else {
                        stringResource(Res.string.weather_never_synced)
                    },
                    style = AppTypography.bodySmall,
                    color = TextSecondary
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            if (fetching) {
                Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(15.dp),
                        color = AccentSolid,
                        strokeWidth = 2.dp
                    )
                }
            } else {
                CalendarActionChipShim(
                    text = stringResource(Res.string.weather_refresh),
                    onClick = onRefresh
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        BehaviorSwitchRow(
            title = stringResource(Res.string.weather_show_title),
            checked = showWeather,
            onCheckedChange = onShowWeatherChange
        )
        Spacer(modifier = Modifier.height(12.dp))
        AppearanceDropdown(
            label = stringResource(Res.string.weather_units_title),
            options = listOf(false, true),
            selected = fahrenheit,
            optionLabel = {
                stringResource(
                    if (it) Res.string.weather_unit_fahrenheit
                    else Res.string.weather_unit_celsius
                )
            },
            onSelect = onFahrenheitChange,
            modifier = Modifier.fillMaxWidth()
        )

    }
}

/**
 * The refresh chip the calendar card uses, minus the calendar coupling. Kept
 * local (not shared chrome) because it is one call site: promoting it would
 * pretend at reuse that does not exist.
 */
@Composable
private fun CalendarActionChipShim(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(AccentSolid)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            text = text,
            style = AppTypography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF1A1B25)
        )
    }
}

/** "just now" / "12 min ago" / a clock time once it is old enough to be a time. */
internal fun weatherAgo(millis: Long): String {
    val elapsed = System.currentTimeMillis() - millis
    val minutes = elapsed / 60_000L
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        else -> java.time.format.DateTimeFormatter.ofPattern("HH:mm")
            .withZone(java.time.ZoneId.systemDefault())
            .format(java.time.Instant.ofEpochMilli(millis))
    }
}
