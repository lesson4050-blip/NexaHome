package com.orbit.tv.launcher.features.iptv.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

import com.orbit.tv.launcher.features.home.data.WatchHistoryDao
import com.orbit.tv.launcher.features.home.data.WatchHistoryEntity

@Database(entities = [ChannelEntity::class, ProgramEntity::class, WatchHistoryEntity::class], version = 2, exportSchema = false)
abstract class OrbitDatabase : RoomDatabase() {

    abstract fun iptvDao(): IptvDao
    abstract fun watchHistoryDao(): WatchHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: OrbitDatabase? = null

        fun getInstance(context: Context): OrbitDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    OrbitDatabase::class.java,
                    "orbit_tv_launcher.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
