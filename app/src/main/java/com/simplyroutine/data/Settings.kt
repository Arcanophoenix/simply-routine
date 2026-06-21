package com.simplyroutine.data

data class Settings(
    val dayStartMinutes: Int = 360,   // 6:00 AM
    val dayEndMinutes: Int = 1320,    // 10:00 PM
    val tapToAdd: Boolean = true,
    val showNextDayEvent: Boolean = false,
    val timeFormat: String = "system", // "system", "12h", "24h"
    val resetScrollOnWeekChange: Boolean = false,
    val defaultAlertMinutes: Int = -1,
    val tourSeen: Boolean = false,
    val showNotification: Boolean = true,
    val householdId: String? = null,
    val widgetHideCompleted: Boolean = false,
    val widgetHideDaysOut: Int = 0,   // 0 = show all; otherwise hide tasks due in more than N days
)

val WIDGET_DAYS_OUT_OPTIONS = listOf(0, 7, 14, 30, 60)
fun widgetDaysOutLabel(days: Int) = if (days == 0) "Show all" else "Due within $days days"
