package com.simplyroutine.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    @Query("SELECT * FROM events ORDER BY date, startMinutes")
    fun getAllEvents(): Flow<List<Event>>

    @Query("SELECT * FROM events ORDER BY date, startMinutes")
    suspend fun getAllEventsOnce(): List<Event>

    @Query("SELECT * FROM events WHERE date = :epochDay ORDER BY startMinutes")
    suspend fun getEventsForDayOnce(epochDay: Long): List<Event>

    @Query("SELECT * FROM events WHERE id = :id LIMIT 1")
    suspend fun getEventById(id: Int): Event?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: Event): Long

    @Update
    suspend fun update(event: Event)

    @Delete
    suspend fun delete(event: Event)
}
