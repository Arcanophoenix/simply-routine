package com.simplyroutine.data

// Used in SettingsScreen for the single "default alert" setting (includes -1 = No alert)
val ALERT_OPTIONS = listOf(-1, 0, 5, 10, 15, 30, 60, 120, 1440)

// Used in AddEventDialog per-alert rows (no "No alert" — remove the row to delete)
val ALERT_OPTIONS_POSITIVE = listOf(0, 5, 10, 15, 30, 60, 120, 1440)

fun alertLabel(minutes: Int): String = when {
    minutes < 0  -> "No alert"
    minutes == 0 -> "At event time"
    minutes < 60 -> "$minutes min before"
    minutes == 60 -> "1 hour before"
    minutes < 1440 -> "${minutes / 60} hours before"
    else -> "1 day before"
}

fun parseAlertMinutes(str: String): List<Int> =
    if (str.isBlank()) emptyList()
    else str.split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it >= 0 }.distinct().sorted()
