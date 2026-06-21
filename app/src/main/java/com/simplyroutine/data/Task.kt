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
    val frequencyUnit: String = "days",  // "days", "months", "none"
    val lastCompleted: Long? = null,
    val syncId: String = java.util.UUID.randomUUID().toString(),
    val shared: Boolean = false,
) {
    val lastCompletedDate: LocalDate? get() = lastCompleted?.let { LocalDate.ofEpochDay(it) }
}

fun Task.urgencyScore(today: LocalDate): Float {
    if (frequencyUnit == "none") return -1.0f
    val lastDone = lastCompletedDate ?: return Float.MAX_VALUE
    val daysSince = ChronoUnit.DAYS.between(lastDone, today).toFloat().coerceAtLeast(0f)
    return when (frequencyUnit) {
        "months" -> {
            val dueDate = lastDone.plusMonths(frequencyDays.toLong())
            val totalDays = ChronoUnit.DAYS.between(lastDone, dueDate).toFloat().coerceAtLeast(1f)
            daysSince / totalDays
        }
        else -> daysSince / frequencyDays.coerceAtLeast(1).toFloat()
    }
}
