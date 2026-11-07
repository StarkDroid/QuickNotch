package com.starkdroid.quicknotch.calendar

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.todayIn
import kotlinx.datetime.toLocalDateTime

/**
 * The parts of a calendar day that need no platform: the event itself and the
 * window its ticker is allowed on. Pure functions over a `now` handed in by the
 * caller, so there is no clock in here and nothing to stub.
 */

/**
 * The ticker appears this early before an event and leaves when the event
 * ends or the notch expands.
 */
const val TICKER_LEAD_MS = 2 * 60 * 1000L

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
     * TICKER_LEAD_MS before the start until the event ends; all-day entries
     * never tick.
     */
    fun isTickerLiveAt(nowMillis: Long): Boolean =
        !allDay &&
            startMillis - TICKER_LEAD_MS <= nowMillis &&
            endMillis > nowMillis
}

/** Wall-clock now, in the units the event model uses. */
fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

/** Today, in the system zone. */
fun todayDate(
    timeZone: TimeZone = TimeZone.currentSystemDefault()
): LocalDate = Clock.System.todayIn(timeZone)

/**
 * Fetch window starts at local midnight; no upper bound is schedule mode.
 * One fetch feeds the badge, the ticker and the page.
 */
fun fetchFromMillis(
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
    today: LocalDate = Clock.System.todayIn(timeZone)
): Long = today.atStartOfDayIn(timeZone).toEpochMilliseconds()

/** One date that has events coming, with how many. */
data class CalendarDateGroup(
    val date: LocalDate,
    val count: Int,
    val isToday: Boolean
)

/**
 * Today's events that are still upcoming -- running counts, ended does not.
 * This is the pill badge's number, and it matches the count Google Calendar
 * itself shows for the day.
 */
fun List<CalendarEvent>.todayUpcomingCount(
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
    today: LocalDate = Clock.System.todayIn(timeZone),
    nowMillis: Long = Clock.System.now().toEpochMilliseconds()
): Int = count { event ->
    Instant.fromEpochMilliseconds(event.startMillis)
        .toLocalDateTime(timeZone).date == today && event.endMillis >= nowMillis
}

/**
 * The next maxDates dates with an event, one per date and no empty days.
 */
fun List<CalendarEvent>.upcomingDateGroups(
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
    today: LocalDate = Clock.System.todayIn(timeZone),
    nowMillis: Long = Clock.System.now().toEpochMilliseconds(),
    maxDates: Int = 4
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
