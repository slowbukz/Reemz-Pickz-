package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [SavedParlayEntity::class, MarketCacheEntity::class],
    version = 2,
    exportSchema = false
)
abstract class CorrEdgeDatabase : RoomDatabase() {
    abstract fun corrEdgeDao(): CorrEdgeDao

    companion object {
        @Volatile
        private var INSTANCE: CorrEdgeDatabase? = null

        fun getInstance(context: Context): CorrEdgeDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    CorrEdgeDatabase::class.java,
                    "corredge_quant.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
