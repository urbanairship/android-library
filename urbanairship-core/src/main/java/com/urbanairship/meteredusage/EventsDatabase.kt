/* Copyright Airship and Contributors */

package com.urbanairship.meteredusage

import android.content.Context
import androidx.annotation.RestrictTo
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * @hide
 */
@Database(entities = [MeteredUsageEventEntity::class], version = 1)
@RestrictTo(RestrictTo.Scope.LIBRARY)
internal abstract class EventsDatabase : RoomDatabase() {
    abstract fun eventsDao(): EventsDao

    companion object {
        private const val DB_NAME = "ua_metered_usage.db"

        fun persistent(context: Context): EventsDatabase =
            try {
                Room.databaseBuilder(context.applicationContext, EventsDatabase::class.java, DB_NAME)
                    .fallbackToDestructiveMigration(true)
                    .build()
            } catch (ex: Exception) {
                ex.printStackTrace()
                throw ex
            }

        fun inMemory(context: Context): EventsDatabase =
            try {
                Room.inMemoryDatabaseBuilder(context, EventsDatabase::class.java)
                    .allowMainThreadQueries()
                    .build()
            } catch (ex: Exception) {
                ex.printStackTrace()
                throw ex
            }
    }
}
