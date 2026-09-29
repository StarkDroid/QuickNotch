package com.starkdroid.quicknotch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.starkdroid.quicknotch.calendar.CalendarState
import com.starkdroid.quicknotch.calendar.GoogleCalendar
import com.starkdroid.quicknotch.generated.resources.Res
import com.starkdroid.quicknotch.generated.resources.calendar_connect
import com.starkdroid.quicknotch.generated.resources.calendar_connected_as
import com.starkdroid.quicknotch.generated.resources.calendar_disconnect
import com.starkdroid.quicknotch.generated.resources.calendar_error_auth
import com.starkdroid.quicknotch.generated.resources.calendar_error_cancelled
import com.starkdroid.quicknotch.generated.resources.calendar_error_network
import com.starkdroid.quicknotch.generated.resources.calendar_error_revoked
import com.starkdroid.quicknotch.generated.resources.calendar_hint
import com.starkdroid.quicknotch.generated.resources.calendar_last_sync
import com.starkdroid.quicknotch.generated.resources.calendar_never_synced
import com.starkdroid.quicknotch.generated.resources.calendar_refresh
import com.starkdroid.quicknotch.generated.resources.calendar_section_title
import com.starkdroid.quicknotch.generated.resources.calendar_today_count
import com.starkdroid.quicknotch.generated.resources.calendar
import com.starkdroid.quicknotch.theme.AccentIndigoLight
import com.starkdroid.quicknotch.theme.AppTypography
import com.starkdroid.quicknotch.theme.TextMuted
import com.starkdroid.quicknotch.theme.TextPrimary
import com.starkdroid.quicknotch.theme.TextSecondary
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
            contentDescription = stringResource(Res.string.calendar_today_count, count.toString()),
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
    showInQuickView: Boolean,
    onShowInQuickViewChange: (Boolean) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onRefresh: () -> Unit
) {
    val todayCount = (state as? CalendarState.Connected)?.events?.size ?: 0
    Column(modifier = Modifier.fillMaxWidth()) {
        CalendarConnectRow(
            state = state,
            todayCount = todayCount,
            onConnect = onConnect,
            onDisconnect = onDisconnect,
            onRefresh = onRefresh
        )

        Spacer(modifier = Modifier.height(12.dp))

        // A small switch row rather than an indicator cell: the calendar is not
        // a level ring, so there is nothing meaningful to preview.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(FlatCard)
                .border(1.dp, FlatBorder, RoundedCornerShape(10.dp))
                .clickable(enabled = state is CalendarState.Connected) { onShowInQuickViewChange(!showInQuickView) }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.calendar_today_count, todayCount.toString()),
                    style = AppTypography.titleSmall, // the day at a glance
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

/** Status line + the one action that applies to the current state. */
@Composable
private fun CalendarConnectRow(
    state: CalendarState,
    todayCount: Int,
    onConnect: () -> Unit,
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
                    busy -> stringResource(Res.string.calendar_hint)
                    error != null -> stringResource(
                        when (error.reason) {
                            GoogleCalendar.REASON_NETWORK -> Res.string.calendar_error_network
                            GoogleCalendar.REASON_CANCELLED -> Res.string.calendar_error_cancelled
                            GoogleCalendar.REASON_REVOKED -> Res.string.calendar_error_revoked
                            else -> Res.string.calendar_error_auth
                        }
                    )
                    connected != null && connected.fetchedAtMillis > 0L -> stringResource(
                        Res.string.calendar_last_sync,
                        relativeTime(connected.fetchedAtMillis)
                    )
                    connected != null -> stringResource(Res.string.calendar_never_synced)
                    else -> stringResource(Res.string.calendar_hint)
                },
                style = AppTypography.bodySmall,
                color = if (error != null) TextMuted else TextSecondary
            )
        }
        Spacer(modifier = Modifier.width(10.dp))

        if (busy) {
            Box(modifier = Modifier.size(30.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = AccentIndigoLight,
                    strokeWidth = 2.dp
                )
            }
        } else if (connected != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CalendarActionChip(
                    text = stringResource(Res.string.calendar_refresh),
                    onClick = onRefresh
                )
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
