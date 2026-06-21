package com.simplyroutine.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface OccasionDao {
    @Query("SELECT * FROM occasions ORDER BY date, title")
    fun getAllOccasions(): Flow<List<Occasion>>

    @Query("SELECT * FROM occasions ORDER BY date, title")
    suspend fun getAllOccasionsOnce(): List<Occasion>

    @Insert
    suspend fun insert(occasion: Occasion): Long

    @Update
    suspend fun update(occasion: Occasion)

    @Delete
    suspend fun delete(occasion: Occasion)
}
