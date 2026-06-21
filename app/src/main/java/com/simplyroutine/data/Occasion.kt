package com.simplyroutine.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(tableName = "occasions")
data class Occasion(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val date: Long,
    val repeatType: String = RepeatType.YEARLY.name,
    val repeatInterval: Int = 1,
    val repeatDays: Int = 0,
) {
    val localDate: LocalDate get() = LocalDate.ofEpochDay(date)
    val recurrence: RepeatType get() = RepeatType.valueOf(repeatType)
}

fun List<Occasion>.forDate(date: LocalDate): List<Occasion> =
    expandOccasionsForDate(this, date)
