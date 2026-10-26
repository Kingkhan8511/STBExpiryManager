package com.cableops.stbexpiry.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.cableops.stbexpiry.data.local.dao.RecordDao
import com.cableops.stbexpiry.data.model.ExpiryRecord

/**
 * Room database singleton for the STB Expiry Manager.
 *
 * Version history:
 *  1 → Initial schema with [ExpiryRecord] table.
 */
@Database(
    entities = [ExpiryRecord::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun recordDao(): RecordDao

    companion object {
        private const val DATABASE_NAME = "stb_expiry.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Returns the singleton database instance, creating it on first access.
         * Thread-safe via double-checked locking with [INSTANCE] marked [@Volatile].
         */
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME
            )
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
