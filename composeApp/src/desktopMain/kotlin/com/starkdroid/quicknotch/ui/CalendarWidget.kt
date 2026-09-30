package com.starkdroid.quicknotch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.starkdroid.quicknotch.calendar.CalendarDateGroup
import com.starkdroid.quicknotch.calendar.CalendarState
import com.starkdroid.quicknotch.calendar.todayDate
import com.starkdroid.quicknotch.calendar.upcomingDateGroups
import com.starkdroid.quicknotch.calendar.calendarUpcomingCount
import com.starkdroid.quicknotch.calendar.GoogleCalendar
import com.starkdroid.quicknotch.generated.resources.Res
import com.starkdroid.quicknotch.generated.resources.calendar_cancel
import com.starkdroid.quicknotch.generated.resources.calendar_waiting
import com.starkdroid.quicknotch.generated.resources.calendar_connect
import com.starkdroid.quicknotch.generated.resources.calendar_connected_as
import com.starkdroid.quicknotch.generated.resources.calendar_disconnect
import com.starkdroid.quicknotch.generated.resources.calendar_error_api_disabled
import com.starkdroid.quicknotch.generated.resources.calendar_error_auth
import com.starkdroid.quicknotch.generated.resources.calendar_error_cancelled
import com.starkdroid.quicknotch.generated.resources.calendar_error_client_type
import com.starkdroid.quicknotch.generated.resources.calendar_error_not_verified
import com.starkdroid.quicknotch.generated.resources.calendar_error_network
import com.starkdroid.quicknotch.generated.resources.calendar_error_revoked
import com.starkdroid.quicknotch.generated.resources.calendar_hint
import com.starkdroid.quicknotch.generated.resources.calendar_no_events
import com.starkdroid.quicknotch.generated.resources.calendar_today
import com.starkdroid.quicknotch.generated.resources.calendar_upcoming_count
import com.starkdroid.quicknotch.generated.resources.calendar_last_sync
import com.starkdroid.quicknotch.generated.resources.calendar_never_synced
import com.starkdroid.quicknotch.generated.resources.calendar_refresh
import com.starkdroid.quicknotch.generated.resources.calendar_section_title
import com.starkdroid.quicknotch.generated.resources.calendar_today_count
import com.starkdroid.quicknotch.generated.resources.calendar
import com.starkdroid.quicknotch.theme.AccentIndigoLight
import com.starkdroid.quicknotch.theme.AppTypography
import com.starkdroid.quicknotch.theme.NotchBorder
import com.starkdroid.quicknotch.theme.TextMuted
import com.starkdroid.quicknotch.theme.TextPrimary
import com.starkdroid.quicknotch.theme.TextSecondary
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The QuickView calendar element: a calendar glyph badged with how many events
 * the day has. Shares the ring-badge look ([CountBadge]) so it sits happily
 * beside the notification bell. Hidden entirely when the day has nothing,
 * rather than showing a lone glyph — and it claims an indicator slot only when
 * it draws (see `quickViewIndicatorSlots`), so an empty day leaves the pill
 * exactly as narrow as it was.
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
 * The Widgets-tab calendar widget: connect Google Calendar, see the account and
 * sync age, refresh on demand, and choose whether the notch shows the day at
 * all. Deliberately one place for all of it — the connection is the only way to
 * get events, so splitting "connect" from "show it" would just be two chores for
 * one result.
 */
@Composable
internal fun CalendarWidget(
    state: CalendarState,
    refreshing: Boolean,
    tick: Long,
    showInQuickView: Boolean,
    onShowInQuickViewChange: (Boolean) -> Unit,
    onConnect: () -> Unit,
    onCancel: () -> Unit,
    onDisconnect: () -> Unit,
    onRefresh: () -> Unit
) {
    val connected = state as? CalendarState.Connected
    Column(modifier = Modifier.fillMaxWidth()) {
        CalendarConnectRow(
            state = state,
            refreshing = refreshing,
            onConnect = onConnect,
            onCancel = onCancel,
            onDisconnect = onDisconnect,
            onRefresh = onRefresh
        )

        // The notch toggle only exists once there is something to show. Offering
        // it before the OAuth handshake has finished is a switch that controls
        // nothing: the user flips it, sees no calendar, and concludes the
        // feature is broken rather than not connected yet.
        if (connected != null) {
            Spacer(modifier = Modifier.height(12.dp))

            // A small switch row rather than an indicator cell: the calendar is
            // not a level ring, so there is nothing meaningful to preview.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(FlatCard)
                    .border(1.dp, FlatBorder, RoundedCornerShape(10.dp))
                    .clickable { onShowInQuickViewChange(!showInQuickView) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(
                            Res.string.calendar_upcoming_count,
                            calendarUpcomingCount(state, tick).toString()
                        ),
                        style = AppTypography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        text = stringResource(Res.string.calendar_hint),
                        style = AppTypography.bodySmall,
                        color = TextSecondary
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                // Reuse the flat switch the rest of settings uses, so the notch
                // and the settings window agree on what "on" looks like.
                MinimalistRectSwitch(
                    checked = showInQuickView,
                    onCheckedChange = { onShowInQuickViewChange(it) }
                )
            }
        }
    }
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
                            GoogleCalendar.REASON_CLIENT_TYPE -> Res.string.calendar_error_client_type
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
                        color = AccentIndigoLight,
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
                            color = AccentIndigoLight,
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
            .clip(RoundedCornerShape(6.dp))
            .background(if (tinted) FlatSurfaceElevated else AccentIndigoLight)
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
    GoogleCalendar.REASON_CLIENT_TYPE -> Res.string.calendar_error_client_type
    GoogleCalendar.REASON_NOT_VERIFIED -> Res.string.calendar_error_not_verified
    GoogleCalendar.REASON_API_DISABLED -> Res.string.calendar_error_api_disabled
    else -> Res.string.calendar_error_auth
}
    

/**
 * The expanded notch's calendar page: the next four dates that actually have an
 * event coming, soonest first, one tile each with its month-labelled date over
 * a count badge. Rows cannot fit the island's ~84dp depth, so the dates run as
 * a single row of tiles -- the wheel flips to this page like any other.
 *
 * Loading is never blocking: while a fetch runs with nothing to show yet the
 * page is a small spinner, and an empty-but-settled calendar says so in words.
 */
@Composable
internal fun CalendarNotchPage(
    state: CalendarState?,
    refreshing: Boolean
) {
    // Sampled here rather than passed down: the page is the only drawer of the
    // schedule, and the tick is what makes its tiles drop a spent date without
    // waiting for the next fetch.
    val tick by GoogleCalendar.minuteTick.collectAsState()
    val groups = remember(state, tick) {
        (state as? CalendarState.Connected)?.events.orEmpty()
            .upcomingDateGroups(nowMillis = tick)
    }
    when {
        groups.isEmpty() && refreshing -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(15.dp),
                color = AccentIndigoLight,
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
            CalendarTodayBlock()
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
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * The schedule view's left anchor: the current date in the big size, the
 * weekday and month beside it. Plain date -- no badge -- because the badge
 * belongs to the upcoming dates and today's running event, if any, is what the
 * pill's ticker already announced.
 */
@Composable
private fun CalendarTodayBlock() {
    val today = remember {
        com.starkdroid.quicknotch.calendar.todayDate()
    }
    val weekday = remember(today) { formatDate(today, "EEE") }
    val month = remember(today) { formatDate(today, "MMM") }
    Row(verticalAlignment = Alignment.CenterVertically) {
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

/**
 * One upcoming date in the schedule row: the day with its month -- "10 Oct",
 * "20 Nov" -- over the count badge, centered. Dates here are never today; the
 * big left block owns that.
 */
@Composable
private fun CalendarDateTile(
    group: CalendarDateGroup,
    modifier: Modifier = Modifier
) {
    // Today never needs a month -- the big block beside it carries the date --
    // and it takes the accent so the schedule's "now" reads at a glance.
    val label = if (group.isToday) {
        stringResource(Res.string.calendar_today)
    } else {
        remember(group.date) { formatDate(group.date, "d MMM") }
    }
    Column(
        modifier = modifier,
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
    