package com.simplyroutine.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Returns the events that occur on [date], with each event's [Event.date] set to [date].
 * One-off events are included only if their date matches exactly.
 * Recurring events are included if their recurrence rule generates an occurrence on [date].
 */
fun expandEventsForDate(events: List<Event>, date: LocalDate): List<Event> =
    events
        .filter { occursOn(it, date) }
        .map { it.copy(date = date.toEpochDay()) }
        .sortedBy { it.startMinutes }

private fun occursOn(event: Event, date: LocalDate): Boolean {
    val start = event.localDate
    if (date < start) return false
    if (event.exceptDates.isNotBlank()) {
        val epochStr = date.toEpochDay().toString()
        if (event.exceptDates.split(',').any { it.trim() == epochStr }) return false
    }
    return when (event.recurrence) {
        RepeatType.NONE -> date == start
        RepeatType.DAILY -> true
        RepeatType.WEEKDAYS -> date.dayOfWeek.value in 1..5
        RepeatType.WEEKENDS -> date.dayOfWeek.value in 6..7
        RepeatType.EVERY_N_DAYS -> {
            ChronoUnit.DAYS.between(start, date) % event.repeatInterval == 0L
        }
        RepeatType.EVERY_N_WEEKS -> {
            // Align both dates to their Monday to count whole weeks
            val startMonday = start.with(DayOfWeek.MONDAY)
            val thisMonday = date.with(DayOfWeek.MONDAY)
            val weeks = ChronoUnit.WEEKS.between(startMonday, thisMonday)
            weeks % event.repeatInterval == 0L &&
                (event.repeatDays and (1 shl (date.dayOfWeek.value - 1))) != 0
        }
    }
}
