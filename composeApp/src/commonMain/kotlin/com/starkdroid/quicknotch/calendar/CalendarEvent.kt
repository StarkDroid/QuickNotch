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
import kotlinx.datetime.toLocalDateTime

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

/**
 * The window the app fetches: from local midnight today through [days] days
 * ahead. One fetch serves everything -- the pill's today badge, the ticker and
 * the upcoming-days page -- so there is exactly one thing to keep in sync with
 * Google's bounds instead of three queries drifting apart.
 */
fun upcomingWindowMillis(
    days: Int = 30,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
    today: LocalDate = Clock.System.todayIn(timeZone)
): Pair<Long, Long> {
    // atStartOfDayIn hands back the instant of local midnight, which is exactly
    // the timeMin/timeMax Google wants, so no local-timezone arithmetic here.
    val start = today.atStartOfDayIn(timeZone).toEpochMilliseconds()
    val end = today.plus(days.toLong(), DateTimeUnit.DAY).atStartOfDayIn(timeZone).toEpochMilliseconds()
    return start to end
}

/** One date that has events coming, with how many. */
data class CalendarDateGroup(
    val date: LocalDate,
    val count: Int,
    val isToday: Boolean
)

/** How many of these events fall on today. */
fun List<CalendarEvent>.todayCount(
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
    today: LocalDate = Clock.System.todayIn(timeZone)
): Int = count { event ->
    Instant.fromEpochMilliseconds(event.startMillis)
        .toLocalDateTime(timeZone).date == today
}

/**
 * The next [maxDates] dates that actually have an event coming, soonest first.
 * Gaps are the point -- a quiet calendar should not show five empty days -- and
 * an event still running counts as upcoming, so a meeting that started an hour
 * ago keeps its date on the page until it ends.
 */
fun List<CalendarEvent>.upcomingDateGroups(
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
    today: LocalDate = Clock.System.todayIn(timeZone),
    nowMillis: Long = Clock.System.now().toEpochMilliseconds(),
    maxDates: Int = 5
): List<CalendarDateGroup> {
    val upcoming = filter { it.endMillis >= nowMillis }
    return upcoming
        .groupBy { Instant.fromEpochMilliseconds(it.startMillis).toLocalDateTime(timeZone).date }
        .toList()
        .sortedBy { (date, _) -> date }
        .take(maxDates)
        .map { (date, events) -> CalendarDateGroup(date, events.size, date == today) }
}

/** `2026-09-30T10:00:00+05:30` -> epoch millis; null when Google sends junk. */
fun parseEventMillis(value: String, allDay: Boolean): Long? = runCatching {
    if (allDay) {
        // An all-day start is a bare date; it means midnight local time.
        LocalDate.parse(value).atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
    } else {
        // Google always includes seconds, but a value like `10:00+05:30` (no
        // seconds) is valid ISO-8601 that this parser rejects -- normalise
        // rather than silently dropping the event.
        val normalised = if (value.length >= 19 || !Regex("""T\d{2}:\d{2}[+-Z]""").containsMatchIn(value)) {
            value
        } else {
            value.replaceFirst(Regex("""(T\d{2}:\d{2})([+-Z])"""), "$1:00$2")
        }
        Instant.parse(normalised).toEpochMilliseconds()
    }
}.getOrNull()
