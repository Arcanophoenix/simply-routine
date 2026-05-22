package com.simplyroutine.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(tableName = "events")
data class Event(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val title: String,
    val date: Long,           // LocalDate.toEpochDay() — start date (or only date for NONE)
    val startMinutes: Int,    // minutes from midnight (0–1439)
    val endMinutes: Int,      // minutes from midnight (0–1440)
    val color: Int = 0xFF4CAF50.toInt(),
    val repeatType: String = RepeatType.NONE.name,
    val repeatInterval: Int = 1,   // N for EVERY_N_DAYS / EVERY_N_WEEKS
    val repeatDays: Int = 0,       // bitmask for EVERY_N_WEEKS: bit 0=Mon, bit 1=Tue, … bit 6=Sun
    val exceptDates: String = "",  // comma-separated epoch days excluded from recurrence
    val parentId: Int = 0,         // non-zero when this is a detached occurrence of another event
    val alertMinutes: String = "",     // comma-separated minutes-before values, e.g. "15,30"; empty = no alerts
) {
    val localDate: LocalDate get() = LocalDate.ofEpochDay(date)
    val recurrence: RepeatType get() = RepeatType.valueOf(repeatType)

    fun withException(date: LocalDate): Event {
        val existing = if (exceptDates.isBlank()) emptyList() else exceptDates.split(',')
        val epochStr = date.toEpochDay().toString()
        return if (epochStr in existing) this
        else copy(exceptDates = (existing + epochStr).joinToString(","))
    }

    fun withoutException(date: LocalDate): Event {
        if (exceptDates.isBlank()) return this
        val epochStr = date.toEpochDay().toString()
        val remaining = exceptDates.split(',').filter { it.trim() != epochStr }
        return copy(exceptDates = remaining.joinToString(","))
    }
}
