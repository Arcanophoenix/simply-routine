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
)
