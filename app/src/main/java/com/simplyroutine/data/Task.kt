package com.simplyroutine.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val frequencyDays: Int,
    val lastCompleted: Long? = null,   // LocalDate.toEpochDay(), null = never done
) {
    val lastCompletedDate: LocalDate? get() = lastCompleted?.let { LocalDate.ofEpochDay(it) }
}

fun Task.urgencyScore(today: LocalDate): Float {
    val lastDone = lastCompletedDate ?: return Float.MAX_VALUE
    val daysSince = ChronoUnit.DAYS.between(lastDone, today).toFloat().coerceAtLeast(0f)
    return daysSince / frequencyDays.coerceAtLeast(1).toFloat()
}
