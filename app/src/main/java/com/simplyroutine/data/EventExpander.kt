package com.simplyroutine.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

fun expandEventsForDate(events: List<Event>, date: LocalDate): List<Event> =
    events
        .filter { occursOnDate(it.localDate, it.recurrence, it.repeatInterval, it.repeatDays, it, date) }
        .map { it.copy(date = date.toEpochDay()) }
        .sortedBy { it.startMinutes }

fun expandOccasionsForDate(occasions: List<Occasion>, date: LocalDate): List<Occasion> =
    occasions.filter { occursOnDate(it.localDate, it.recurrence, it.repeatInterval, it.repeatDays, null, date) }

// event is passed only to read exceptDates; pass null for occasions (which have none)
fun occursOnDate(
    start: LocalDate,
    repeatType: RepeatType,
    repeatInterval: Int,
    repeatDays: Int,
    event: Event?,
    date: LocalDate,
): Boolean {
    if (date < start) return false
    if (event != null && event.exceptDates.isNotBlank()) {
        val epochStr = date.toEpochDay().toString()
        if (event.exceptDates.split(',').any { it.trim() == epochStr }) return false
    }
    return when (repeatType) {
        RepeatType.NONE -> date == start
        RepeatType.DAILY -> true
        RepeatType.WEEKDAYS -> date.dayOfWeek.value in 1..5
        RepeatType.WEEKENDS -> date.dayOfWeek.value in 6..7
        RepeatType.EVERY_N_DAYS -> ChronoUnit.DAYS.between(start, date) % repeatInterval == 0L
        RepeatType.EVERY_N_WEEKS -> {
            val startMonday = start.with(DayOfWeek.MONDAY)
            val thisMonday = date.with(DayOfWeek.MONDAY)
            val weeks = ChronoUnit.WEEKS.between(startMonday, thisMonday)
            weeks % repeatInterval == 0L && (repeatDays and (1 shl (date.dayOfWeek.value - 1))) != 0
        }
        RepeatType.MONTHLY -> date.dayOfMonth == start.dayOfMonth
        RepeatType.YEARLY -> date.monthValue == start.monthValue && date.dayOfMonth == start.dayOfMonth
    }
}
