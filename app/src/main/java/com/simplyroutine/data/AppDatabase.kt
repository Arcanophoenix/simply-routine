package com.simplyroutine.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Event::class, Task::class, Occasion::class], version = 11, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao
    abstract fun taskDao(): TaskDao
    abstract fun occasionDao(): OccasionDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN syncId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE tasks ADD COLUMN shared INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE tasks SET syncId = 'legacy-' || id WHERE syncId = ''")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN frequencyUnit TEXT NOT NULL DEFAULT 'days'")
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS occasions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, date INTEGER NOT NULL, repeatType TEXT NOT NULL DEFAULT 'YEARLY', repeatInterval INTEGER NOT NULL DEFAULT 1, repeatDays INTEGER NOT NULL DEFAULT 0)")
            }
        }

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "timekeeper.db")
                    .addMigrations(MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
