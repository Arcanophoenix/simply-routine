package com.simplyroutine.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.simplyroutine.data.Event
import com.simplyroutine.data.RepeatType
import com.simplyroutine.data.parseAlertMinutes
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

object AlertScheduler {

    // Unique PendingIntent request code per (event, minutesBefore) pair.
    // Supports event IDs up to ~214 000 and custom alerts up to 9 999 min.
    fun requestCode(eventId: Int, minutesBefore: Int) = eventId * 10_000 + minutesBefore.coerceIn(0, 9_999)

    // Notification ID for a fired alert — offset avoids collision with the foreground service (ID 1).
    fun notificationId(eventId: Int, minutesBefore: Int) = 20_000 + requestCode(eventId, minutesBefore)

    fun schedule(context: Context, event: Event) {
        parseAlertMinutes(event.alertMinutes).forEach { scheduleOne(context, event, it) }
    }

    fun cancel(context: Context, event: Event) {
        parseAlertMinutes(event.alertMinutes).forEach { cancelOne(context, event, it) }
    }

    fun rescheduleAll(context: Context, events: List<Event>) = events.forEach { schedule(context, it) }

    fun scheduleOne(context: Context, event: Event, minutesBefore: Int) {
        val nextDate = nextOccurrenceOnOrAfter(event, LocalDate.now()) ?: return
        val alertMs = alertEpochMillis(nextDate, event.startMinutes, minutesBefore)
        if (alertMs <= System.currentTimeMillis()) {
            if (event.recurrence != RepeatType.NONE) {
                val after = nextOccurrenceOnOrAfter(event, nextDate.plusDays(1)) ?: return
                scheduleExact(context, event, minutesBefore, alertEpochMillis(after, event.startMinutes, minutesBefore))
            }
            return
        }
        scheduleExact(context, event, minutesBefore, alertMs)
    }

    private fun cancelOne(context: Context, event: Event, minutesBefore: Int) {
        val pi = PendingIntent.getBroadcast(
            context, requestCode(event.id, minutesBefore), alertIntent(context, event, minutesBefore),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        context.getSystemService(AlarmManager::class.java).cancel(pi)
        pi.cancel()
    }

    private fun scheduleExact(context: Context, event: Event, minutesBefore: Int, triggerMs: Long) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            context, requestCode(event.id, minutesBefore), alertIntent(context, event, minutesBefore),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi)
        }
    }

    fun alertIntent(context: Context, event: Event, minutesBefore: Int) =
        Intent(context, AlertReceiver::class.java).apply {
            putExtra("event_id", event.id)
            putExtra("event_title", event.title)
            putExtra("event_color", event.color)
            putExtra("start_minutes", event.startMinutes)
            putExtra("minutes_before", minutesBefore)
        }

    private fun alertEpochMillis(date: LocalDate, startMinutes: Int, minutesBefore: Int): Long {
        val total = startMinutes - minutesBefore
        val alertDate = date.plusDays(Math.floorDiv(total, 1440).toLong())
        val minOfDay = Math.floorMod(total, 1440)
        return alertDate.atTime(LocalTime.of(minOfDay / 60, minOfDay % 60))
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    fun nextOccurrenceOnOrAfter(event: Event, from: LocalDate): LocalDate? {
        val start = event.localDate
        if (event.recurrence == RepeatType.NONE) {
            return if (from <= start && !isExcepted(event, start)) start else null
        }
        val searchFrom = if (from >= start) from else start
        for (i in 0..365) {
            val date = searchFrom.plusDays(i.toLong())
            if (occursOn(event, date)) return date
        }
        return null
    }

    private fun isExcepted(event: Event, date: LocalDate): Boolean {
        if (event.exceptDates.isBlank()) return false
        val s = date.toEpochDay().toString()
        return event.exceptDates.split(',').any { it.trim() == s }
    }

    private fun occursOn(event: Event, date: LocalDate): Boolean {
        val start = event.localDate
        if (date < start || isExcepted(event, date)) return false
        return when (event.recurrence) {
            RepeatType.NONE -> date == start
            RepeatType.DAILY -> true
            RepeatType.WEEKDAYS -> date.dayOfWeek.value in 1..5
            RepeatType.WEEKENDS -> date.dayOfWeek.value in 6..7
            RepeatType.EVERY_N_DAYS -> ChronoUnit.DAYS.between(start, date) % event.repeatInterval == 0L
            RepeatType.EVERY_N_WEEKS -> {
                val startMon = start.with(DayOfWeek.MONDAY)
                val thisMon = date.with(DayOfWeek.MONDAY)
                ChronoUnit.WEEKS.between(startMon, thisMon) % event.repeatInterval == 0L &&
                    (event.repeatDays and (1 shl (date.dayOfWeek.value - 1))) != 0
            }
        }
    }
}
