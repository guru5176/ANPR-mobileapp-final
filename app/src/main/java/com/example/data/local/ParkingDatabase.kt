package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        VehicleLogEntity::class,
        PassEntity::class,
        CreditEntity::class,
        RateConfig::class,
        SyncQueueEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class ParkingDatabase : RoomDatabase() {
    abstract fun parkingDao(): ParkingDao

    companion object {
        @Volatile
        private var INSTANCE: ParkingDatabase? = null

        fun getInstance(context: Context): ParkingDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ParkingDatabase::class.java,
                    "motovault_anpr_parking.db"
                ).fallbackToDestructiveMigration(true).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
