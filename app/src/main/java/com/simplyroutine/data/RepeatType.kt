package com.simplyroutine.data

enum class RepeatType {
    NONE,          // one-off on a specific date
    DAILY,         // every day from start date
    WEEKDAYS,      // Mon–Fri from start date
    WEEKENDS,      // Sat–Sun from start date
    EVERY_N_DAYS,  // every repeatInterval days from start date
    EVERY_N_WEEKS, // every repeatInterval weeks, on days given by repeatDays bitmask (bit 0=Mon … bit 6=Sun)
    MONTHLY,       // every month on the same day-of-month as start date
    YEARLY,        // every year on the same month+day as start date
}
