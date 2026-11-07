package com.starkdroid.quicknotch.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.starkdroid.quicknotch.calendar.CalendarDateGroup
import com.starkdroid.quicknotch.calendar.CalendarState
import com.starkdroid.quicknotch.calendar.todayDate
import com.starkdroid.quicknotch.calendar.upcomingDateGroups
import com.starkdroid.quicknotch.calendar.GoogleCalendar
import com.starkdroid.quicknotch.datasource.AppLauncher
import com.starkdroid.quicknotch.generated.resources.Res
import com.starkdroid.quicknotch.generated.resources.calendar_cancel
import com.starkdroid.quicknotch.generated.resources.calendar_waiting
import com.starkdroid.quicknotch.generated.resources.calendar_connect
import com.starkdroid.quicknotch.generated.resources.calendar_connected_as
import com.starkdroid.quicknotch.generated.resources.calendar_disconnect
import com.starkdroid.quicknotch.generated.resources.calendar_error_api_disabled
import com.starkdroid.quicknotch.generated.resources.calendar_error_auth
import com.starkdroid.quicknotch.generated.resources.calendar_error_cancelled
import com.starkdroid.quicknotch.generated.resources.calendar_error_client_credentials
import com.starkdroid.quicknotch.generated.resources.calendar_error_not_configured
import com.starkdroid.quicknotch.generated.resources.calendar_error_not_verified
import com.starkdroid.quicknotch.generated.resources.calendar_error_network
import com.starkdroid.quicknotch.generated.resources.calendar_error_revoked
import com.starkdroid.quicknotch.generated.resources.calendar_hint
import com.starkdroid.quicknotch.generated.resources.calendar_no_events
import com.starkdroid.quicknotch.generated.resources.calendar_open_day
import com.starkdroid.quicknotch.generated.resources.calendar_today
import com.starkdroid.quicknotch.generated.resources.calendar_upcoming_count
import com.starkdroid.quicknotch.generated.resources.calendar_last_sync
import com.starkdroid.quicknotch.generated.resources.calendar_never_synced
import com.starkdroid.quicknotch.generated.resources.calendar_refresh
import com.starkdroid.quicknotch.generated.resources.calendar_section_title
import com.starkdroid.quicknotch.generated.resources.calendar
import com.starkdroid.quicknotch.theme.AccentSolid
import com.starkdroid.quicknotch.theme.AppTypography
import com.starkdroid.quicknotch.theme.NotchBorder
import com.starkdroid.quicknotch.theme.SettingsElevated
import com.starkdroid.quicknotch.theme.TextMuted
import com.starkdroid.quicknotch.theme.TextPrimary
import com.starkdroid.quicknotch.theme.TextSecondary
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Calendar glyph with the day's event count; hidden when empty, so it
 * claims no indicator slot.
 */
@Composable
internal fun CalendarDayIndicator(
    count: Int,
    modifier: Modifier = Modifier
) {
    if (count <= 0) return
    Box(modifier = modifier.size(22.dp), contentAlignment = Alignment.Center) {
        Icon(
            painter = painterResource(Res.drawable.calendar),
            contentDescription = stringResource(Res.string.calendar_upcoming_count, count.toString()),
            tint = LevelRingGlyph,
            modifier = Modifier.size(17.dp)
        )
        CountBadge(count = count, modifier = Modifier.align(Alignment.TopEnd).offset(x = 2.dp))
    }
}

/**
 * Connect, show the account and sync age, refresh. The widget toggle lives
 * on the accordion header.
 */
@Composable
internal fun CalendarWidget(
    state: CalendarState,
    refreshing: Boolean,
    onConnect: () -> Unit,
    onCancel: () -> Unit,
    onDisconnect: () -> Unit,
    onRefresh: () -> Unit
) {
    CalendarConnectRow(
        state = state,
        refreshing = refreshing,
        onConnect = onConnect,
        onCancel = onCancel,
        onDisconnect = onDisconnect,
        onRefresh = onRefresh
    )
}

/** Status line + the one action that applies to the current state. */
@Composable
private fun CalendarConnectRow(
    state: CalendarState,
    refreshing: Boolean,
    onConnect: () -> Unit,
    onCancel: () -> Unit,
    onDisconnect: () -> Unit,
    onRefresh: () -> Unit
) {
    val connected = state as? CalendarState.Connected
    val error = state as? CalendarState.Error
    val busy = state is CalendarState.Connecting

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = when {
                    // While the browser is open the heading says what is being
                    // waited on. The old "Connect Google Calendar" left the user
                    // with a spinner and no idea whether anything was happening.
                    busy -> stringResource(Res.string.calendar_connect)
                    connected != null -> stringResource(Res.string.calendar_connected_as, connected.account)
                    error != null -> stringResource(Res.string.calendar_section_title)
                    else -> stringResource(Res.string.calendar_section_title)
                },
                style = AppTypography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = when {
                    busy -> stringResource(Res.string.calendar_waiting)
                    error != null -> stringResource(
                        when (error.reason) {
                            GoogleCalendar.REASON_NETWORK -> Res.string.calendar_error_network
                            GoogleCalendar.REASON_CLIENT_CREDENTIALS -> Res.string.calendar_error_client_credentials
                            GoogleCalendar.REASON_NOT_CONFIGURED -> Res.string.calendar_error_not_configured
                            GoogleCalendar.REASON_NOT_VERIFIED -> Res.string.calendar_error_not_verified
                            GoogleCalendar.REASON_CANCELLED -> Res.string.calendar_error_cancelled
                            GoogleCalendar.REASON_REVOKED -> Res.string.calendar_error_revoked
                            else -> Res.string.calendar_error_auth
                        }
                    )
                    connected != null && connected.staleError != null ->
                        stringResource(connected.staleError.localized())
                    connected != null && connected.fetchedAtMillis > 0L -> stringResource(
                        Res.string.calendar_last_sync,
                        relativeTime(connected.fetchedAtMillis)
                    )
                    connected != null -> stringResource(Res.string.calendar_never_synced)
                    else -> stringResource(Res.string.calendar_hint)
                },
                style = AppTypography.bodySmall,
                color = if (error != null || (connected?.staleError != null)) TextMuted else TextSecondary
            )
        }
        Spacer(modifier = Modifier.width(10.dp))

        if (busy) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(15.dp),
                        color = AccentSolid,
                        strokeWidth = 2.dp
                    )
                }
                CalendarActionChip(text = stringResource(Res.string.calendar_cancel), onClick = onCancel, tinted = true)
            }
        } else if (connected != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // The chip becomes the spinner for exactly as long as the fetch
                // runs, so pressing Refresh is never a silent no-op and never a
                // second concurrent request: the datasource serializes those.
                if (refreshing) {
                    Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(15.dp),
                            color = AccentSolid,
                            strokeWidth = 2.dp
                        )
                    }
                } else {
                    CalendarActionChip(
                        text = stringResource(Res.string.calendar_refresh),
                        onClick = onRefresh
                    )
                }
                CalendarActionChip(
                    text = stringResource(Res.string.calendar_disconnect),
                    onClick = onDisconnect,
                    tinted = true
                )
            }
        } else {
            CalendarActionChip(text = stringResource(Res.string.calendar_connect), onClick = onConnect)
        }
    }
}

/** Small flat action chip; [tinted] marks the destructive-ish disconnect. */
@Composable
private fun CalendarActionChip(
    text: String,
    onClick: () -> Unit,
    tinted: Boolean = false
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (tinted) SettingsElevated else AccentSolid)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            text = text,
            style = AppTypography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (tinted) TextSecondary else Color(0xFF1A1B25)
        )
    }
}

/** "just now" / "12 min ago" / a clock time once it is old enough to be a time. */
private fun relativeTime(millis: Long): String {
    val elapsed = System.currentTimeMillis() - millis
    val minutes = elapsed / 60_000L
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        else -> DateTimeFormatter.ofPattern("HH:mm")
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(millis))
    }
}

/** One place that knows which failure a reason key stands for. */
@Composable
private fun String.localized(): StringResource = when (this) {
    GoogleCalendar.REASON_NETWORK -> Res.string.calendar_error_network
    GoogleCalendar.REASON_CANCELLED -> Res.string.calendar_error_cancelled
    GoogleCalendar.REASON_CLIENT_CREDENTIALS -> Res.string.calendar_error_client_credentials
    GoogleCalendar.REASON_NOT_CONFIGURED -> Res.string.calendar_error_not_configured
    GoogleCalendar.REASON_NOT_VERIFIED -> Res.string.calendar_error_not_verified
    GoogleCalendar.REASON_API_DISABLED -> Res.string.calendar_error_api_disabled
    else -> Res.string.calendar_error_auth
}
    

/**
 * Upcoming days as a row of tiles, each a link into Google Calendar.
 * Tiles take the pointer chain only when the page is interactive.
 */
@Composable
internal fun CalendarNotchPage(
    state: CalendarState?,
    refreshing: Boolean,
    interactive: Boolean
) {
    // Sampled here rather than passed down: the page is the only drawer of the
    // schedule, and the tick is what makes its tiles drop a spent date without
    // waiting for the next fetch.
    val tick by GoogleCalendar.minuteTick.collectAsState()
    val groups = remember(state, tick) {
        (state as? CalendarState.Connected)?.events.orEmpty()
            .upcomingDateGroups(nowMillis = tick)
    }
    val openDay = rememberDayOpener()
    when {
        groups.isEmpty() && refreshing -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(15.dp),
                color = AccentSolid,
                strokeWidth = 2.dp
            )
        }
        groups.isEmpty() -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(Res.string.calendar_no_events),
                style = AppTypography.bodySmall,
                color = TextMuted
            )
        }
        else -> Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = DockLayout.ContentPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Today holds the left slot in the big size -- the schedule's "now"
            // anchor -- and the five tiles to its right are strictly future
            // dates, month included, because the next events can be months out.
            CalendarTodayBlock(interactive = interactive, openDay = openDay)
            Box(
                modifier = Modifier
                    .padding(horizontal = 10.dp)
                    .width(1.dp)
                    .height(34.dp)
                    .background(NotchBorder)
            )
            groups.forEachIndexed { index, group ->
                if (index > 0) {
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(30.dp)
                            .background(NotchBorder)
                    )
                }
                CalendarDateTile(
                    group = group,
                    modifier = Modifier.weight(1f),
                    interactive = interactive,
                    openDay = openDay
                )
            }
        }
    }
}

/**
 * u/0 is Google's default-account path; authuser=<email> silently opens
 * another account when that one is not signed in.
 */
private fun googleDayUrl(date: kotlinx.datetime.LocalDate): String =
    "https://calendar.google.com/calendar/u/0/r/day/" +
        "${date.year}/${date.monthNumber}/${date.dayOfMonth}"

/**
 * AppLauncher.browse waits for the spawn, so a click handler must hand it
 * to the IO scope or freeze the Compose thread.
 */
@Composable
private fun rememberDayOpener(): (kotlinx.datetime.LocalDate) -> Unit {
    val scope = rememberCoroutineScope()
    return remember(scope) {
        { date: kotlinx.datetime.LocalDate ->
            scope.launch(Dispatchers.IO) { AppLauncher.browse(googleDayUrl(date)) }
        }
    }
}

/**
 * Capsule hover and spring scale for a clickable date. The pointer chain
 * exists only while interactive, since a disabled clickable still hit-tests.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun Modifier.dateLink(
    interactive: Boolean,
    onClick: () -> Unit,
    // Deliberately not named `contentDescription`: inside the semantics lambda
    // that name would resolve against SemanticsPropertyReceiver too, and both
    // sides are String, so a mis-resolution would compile and only ever show up
    // as every tile announcing the wrong date.
    linkLabel: String
): Modifier {
    var hovered by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (hovered) 1.05f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 380f),
        label = "calendarDayHover"
    )
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .then(
            if (interactive) {
                Modifier
                    .onPointerEvent(PointerEventType.Enter) { hovered = true }
                    .onPointerEvent(PointerEventType.Exit) { hovered = false }
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (hovered) LiveAccent.copy(alpha = 0.10f) else Color.Transparent
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClick = onClick
                    )
            } else {
                Modifier
            }
        )
        .then(
            // A11y only: the capsule and scale are decoration, the label is what
            // a screen reader announces for the link.
            if (interactive) {
                Modifier.semantics { contentDescription = linkLabel }
            } else {
                Modifier
            }
        )
}

/**
 * Today's date, weekday and month; no badge, since the ticker already
 * announced a running event.
 */
@Composable
private fun CalendarTodayBlock(
    interactive: Boolean,
    openDay: (kotlinx.datetime.LocalDate) -> Unit
) {
    val today = remember {
        com.starkdroid.quicknotch.calendar.todayDate()
    }
    val weekday = remember(today) { formatDate(today, "EEE") }
    val month = remember(today) { formatDate(today, "MMM") }
    val label = stringResource(Res.string.calendar_today)
    Row(
        modifier = Modifier
            .dateLink(
                interactive = interactive,
                onClick = { openDay(today) },
                linkLabel = stringResource(Res.string.calendar_open_day, label)
            )
            // Grows the hit area past the glyphs themselves, so the date is
            // easy to hit without the capsule crowding the row.
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = today.dayOfMonth.toString(),
            style = AppTypography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = LiveAccent
        )
        Spacer(modifier = Modifier.width(7.dp))
        Column {
            Text(
                text = weekday,
                style = AppTypography.labelSmall,
                color = TextPrimary
            )
            Text(
                text = month,
                style = AppTypography.labelSmall,
                color = TextMuted
            )
        }
    }
}

// Resets only appearance, sizing and clock. Widgets and Behavior keep their
// own state, so a reset never undoes a user's widget choices.
@Composable
private fun CalendarDateTile(
    group: CalendarDateGroup,
    modifier: Modifier = Modifier,
    interactive: Boolean,
    openDay: (kotlinx.datetime.LocalDate) -> Unit
) {
    // Today never needs a month -- the big block beside it carries the date --
    // and it takes the accent so the schedule's "now" reads at a glance.
    val label = if (group.isToday) {
        stringResource(Res.string.calendar_today)
    } else {
        remember(group.date) { formatDate(group.date, "d MMM") }
    }
    Column(
        modifier = modifier
            .dateLink(
                interactive = interactive,
                onClick = { openDay(group.date) },
                linkLabel = stringResource(Res.string.calendar_open_day, label)
            )
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = AppTypography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (group.isToday) LiveAccent else TextPrimary
        )
        Spacer(modifier = Modifier.height(4.dp))
        CountBadge(count = group.count)
    }
}

/** Locale-aware date formatting for the notch's schedule row. */
private fun formatDate(
    date: kotlinx.datetime.LocalDate,
    pattern: String
): String = java.time.LocalDate.of(date.year, date.monthNumber, date.dayOfMonth)
    .format(java.time.format.DateTimeFormatter.ofPattern(pattern, java.util.Locale.getDefault()))
    
