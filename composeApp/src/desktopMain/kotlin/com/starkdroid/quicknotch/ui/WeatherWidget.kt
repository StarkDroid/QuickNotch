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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
import com.starkdroid.quicknotch.generated.resources.weather_location_auto
import com.starkdroid.quicknotch.generated.resources.weather_location_no_results
import com.starkdroid.quicknotch.generated.resources.weather_location_search_hint
import com.starkdroid.quicknotch.generated.resources.weather_location_title
import com.starkdroid.quicknotch.generated.resources.weather_location_use_auto
import com.starkdroid.quicknotch.generated.resources.weather_never_synced
import com.starkdroid.quicknotch.generated.resources.weather_night
import com.starkdroid.quicknotch.generated.resources.weather_partly
import com.starkdroid.quicknotch.generated.resources.weather_partly_night
import com.starkdroid.quicknotch.generated.resources.weather_precip
import com.starkdroid.quicknotch.generated.resources.weather_rain
import com.starkdroid.quicknotch.generated.resources.weather_refresh
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
import com.starkdroid.quicknotch.weather.Weather
import com.starkdroid.quicknotch.weather.WeatherData
import com.starkdroid.quicknotch.weather.WeatherDay
import com.starkdroid.quicknotch.weather.WeatherPlace
import com.starkdroid.quicknotch.weather.WeatherState
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * WMO groups to packaged condition glyphs; clear and partly resolve to the
 * moon variant after dark.
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


/** Display temperature: the feed is always metric, Fahrenheit is a render conversion. */
internal fun weatherTemp(celsius: Double, fahrenheit: Boolean): String {
    val value = if (fahrenheit) celsius * 9.0 / 5.0 + 32.0 else celsius
    return "${value.roundToInt()}°"
}

/**
 * Scale letter appended after [weatherTemp] (which already carries the degree
 * mark): "21°" + "F", never "°F", or the mark prints twice.
 */
internal fun weatherTempUnitLetter(fahrenheit: Boolean): String = if (fahrenheit) "F" else "C"

/**
 * Condition glyph and temperature, left of the date; hidden until a
 * forecast loads.
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
            tint = Color.Unspecified,
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
 * Current conditions plus one tile per outlook day, mirroring the
 * calendar page. Loading and failure never block the page.
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
    val unit = weatherTempUnitLetter(fahrenheit)
    Column(modifier = Modifier.width(132.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = condition.painter(data.now.isDay),
                contentDescription = null,
                tint = Color.Unspecified,
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
            tint = Color.Unspecified,
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
 * Live status, refresh, location picker and unit. The widget toggle lives
 * on the accordion header.
 */
@Composable
internal fun WeatherSettings(
    state: WeatherState,
    fetching: Boolean,
    manualPlace: WeatherPlace?,
    onSelectPlace: (WeatherPlace?) -> Unit,
    fahrenheit: Boolean,
    onFahrenheitChange: (Boolean) -> Unit,
    onRefresh: () -> Unit
) {
    val data = (state as? WeatherState.Ready)?.data
    Column(modifier = Modifier.fillMaxWidth()) {
        WeatherStatusRow(
            state = state,
            data = data,
            fetching = fetching,
            onRefresh = onRefresh
        )

        Spacer(modifier = Modifier.height(12.dp))
        WeatherLocationSection(
            manualPlace = manualPlace,
            autoCity = if (manualPlace == null) data?.place.orEmpty() else "",
            onSelectPlace = onSelectPlace
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
 * Live status (place + sync age) with the manual refresh at its side. The chip
 * becomes the spinner for exactly as long as the fetch runs, so pressing
 * refresh is never a silent no-op.
 */
@Composable
private fun WeatherStatusRow(
    state: WeatherState,
    data: WeatherData?,
    fetching: Boolean,
    onRefresh: () -> Unit
) {
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
}

/**
 * Pins the feed to a searched place; reset returns to IP location, and
 * search is debounced.
 */
@Composable
private fun WeatherLocationSection(
    manualPlace: WeatherPlace?,
    autoCity: String,
    onSelectPlace: (WeatherPlace?) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<WeatherPlace>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    // Restarting on every keystroke cancels the in-flight wait, so only a
    // pause actually queries.
    LaunchedEffect(query) {
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            results = emptyList()
            searching = false
            searched = false
            return@LaunchedEffect
        }
        delay(450)
        searching = true
        results = Weather.searchPlaces(trimmed)
        searching = false
        searched = true
    }
    fun pick(place: WeatherPlace?) {
        onSelectPlace(place)
        query = ""
        results = emptyList()
        searched = false
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        WeatherLocationHeader(
            manualPlace = manualPlace,
            autoCity = autoCity,
            onReset = { pick(null) }
        )
        Spacer(modifier = Modifier.height(8.dp))
        WeatherLocationField(
            query = query,
            onQueryChange = { query = it },
            searching = searching
        )
        if (query.trim().length >= 2) {
            Spacer(modifier = Modifier.height(8.dp))
            WeatherLocationResults(
                searching = searching,
                searched = searched,
                results = results,
                manualPlace = manualPlace,
                onPick = ::pick
            )
        }
    }
}

/**
 * Title over the active place (the manual pick, else the auto city), with the
 * reset chip while a manual place is pinned.
 */
@Composable
private fun WeatherLocationHeader(
    manualPlace: WeatherPlace?,
    autoCity: String,
    onReset: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(Res.string.weather_location_title),
                style = AppTypography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = manualPlace?.name ?: autoCity.ifBlank {
                    stringResource(Res.string.weather_location_auto)
                },
                style = AppTypography.bodySmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (manualPlace != null) {
            Spacer(modifier = Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(SettingsElevated)
                    .border(1.dp, SettingsBorder, RoundedCornerShape(8.dp))
                    .plainClickable(onClick = onReset)
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Text(
                    text = stringResource(Res.string.weather_location_use_auto),
                    style = AppTypography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary
                )
            }
        }
    }
}

/**
 * Compact search box in the pinned-apps style (Material's text field enforces
 * a 56dp floor, so this is a hand-rolled 40dp BasicTextField instead). The
 * whole box focuses the field; a spinner replaces the clear button mid-search.
 */
@Composable
private fun WeatherLocationField(
    query: String,
    onQueryChange: (String) -> Unit,
    searching: Boolean
) {
    val focus = remember { FocusRequester() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(SettingsElevated)
            .border(1.dp, SettingsBorder, RoundedCornerShape(10.dp))
            .plainClickable(onClick = { focus.requestFocus() })
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(color = TextPrimary, fontSize = 13.sp),
            cursorBrush = SolidColor(AccentSolid),
            modifier = Modifier.fillMaxHeight().focusRequester(focus),
            decorationBox = { inner ->
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text(
                                text = stringResource(Res.string.weather_location_search_hint),
                                color = TextMuted,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        inner()
                    }
                    if (searching || query.isNotEmpty()) {
                        WeatherLocationFieldTrailing(
                            searching = searching,
                            onClear = { onQueryChange("") }
                        )
                    }
                }
            }
        )
    }
}

/** Spinner mid-search, clear button when there is text, else nothing. */
@Composable
private fun WeatherLocationFieldTrailing(
    searching: Boolean,
    onClear: () -> Unit
) {
    Spacer(modifier = Modifier.width(4.dp))
    if (searching) {
        CircularProgressIndicator(
            modifier = Modifier.size(12.dp),
            color = AccentSolid,
            strokeWidth = 2.dp
        )
    } else {
        Box(
            modifier = Modifier
                .size(24.dp)
                .plainClickable(onClick = onClear),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

/** Search outcomes: spinner, empty note, or the place rows. */
@Composable
private fun WeatherLocationResults(
    searching: Boolean,
    searched: Boolean,
    results: List<WeatherPlace>,
    manualPlace: WeatherPlace?,
    onPick: (WeatherPlace) -> Unit
) {
    when {
        searching && results.isEmpty() -> Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(15.dp),
                color = AccentSolid,
                strokeWidth = 2.dp
            )
        }
        results.isEmpty() -> {
            if (searched) {
                Text(
                    text = stringResource(Res.string.weather_location_no_results),
                    style = AppTypography.bodySmall,
                    color = TextMuted,
                    fontSize = 11.5.sp
                )
            }
        }
        else -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            results.forEach { place ->
                WeatherLocationResultRow(
                    place = place,
                    selected = place == manualPlace,
                    onClick = { onPick(place) }
                )
            }
        }
    }
}

/** One geocoded place: label row with a check on the active pick. */
@Composable
private fun WeatherLocationResultRow(
    place: WeatherPlace,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) AccentSolid.copy(alpha = 0.12f) else SettingsElevated)
            .border(1.dp, if (selected) AccentSolid else SettingsBorder, RoundedCornerShape(10.dp))
            .plainClickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = place.name,
            style = AppTypography.bodySmall,
            color = TextPrimary,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = AccentSolid,
                modifier = Modifier.size(14.dp)
            )
        }
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
