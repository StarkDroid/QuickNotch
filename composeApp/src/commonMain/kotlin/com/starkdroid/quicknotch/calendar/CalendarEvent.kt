package com.starkdroid.quicknotch.calendar

import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlinx.datetime.toInstant

/**
 * The parts of a calendar day that need no platform: the event itself and the
 * window its ticker is allowed on. Pure functions over a `now` handed in by the
 * caller, so there is no clock in here and nothing to stub.
 */

/**
 * How early before an event the ticker appears, so a 10:00 meeting is already
 * on screen at 9:58 rather than popping in as it starts.
 */
const val TICKER_LEAD_MS = 2 * 60 * 1000L

/** How long the ticker lingers after an event ends, so a one-minute entry is
 *  still readable instead of blinking past. */
const val TICKER_LINGER_MS = 30 * 60 * 1000L

/**
 * One event on the user's primary calendar, reduced to what the notch shows.
 * Times are epoch milliseconds.
 */
data class CalendarEvent(
    val id: String,
    val summary: String,
    val startMillis: Long,
    val endMillis: Long,
    val allDay: Boolean
) {
    /**
     * The ticker window: [TICKER_LEAD_MS] before the start through
     * [TICKER_LINGER_MS] after the end. All-day entries never tick — "Team
     * offsite" scrolling across the pill all morning is noise, not information.
     */
    fun isTickerLiveAt(nowMillis: Long): Boolean =
        !allDay &&
            startMillis - TICKER_LEAD_MS <= nowMillis &&
            endMillis + TICKER_LINGER_MS > nowMillis
}

/** Wall-clock now, in the units the event model uses. */
fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

/** Today in the system zone, as the API's `timeMin`/`timeMax` window. */
fun todayWindowMillis(
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
    today: LocalDate = Clock.System.todayIn(timeZone)
): Pair<Long, Long> {
    // atStartOfDayIn hands back the instant of local midnight, which is exactly
    // the timeMin/timeMax Google wants, so no local-timezone arithmetic here.
    val start = today.atStartOfDayIn(timeZone).toEpochMilliseconds()
    val end = today.plus(1, DateTimeUnit.DAY).atStartOfDayIn(timeZone).toEpochMilliseconds()
    return start to end
}

/** `2026-09-30T10:00:00+05:30` -> epoch millis; null when Google sends junk. */
fun parseEventMillis(value: String, allDay: Boolean): Long? = runCatching {
    if (allDay) {
        // An all-day start is a bare date; it means midnight local time.
        LocalDate.parse(value).atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
    } else {
        Instant.parse(value).toEpochMilliseconds()
    }
}.getOrNull()
