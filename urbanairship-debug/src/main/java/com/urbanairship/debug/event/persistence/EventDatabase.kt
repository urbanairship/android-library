/* Copyright Airship and Contributors */

package com.urbanairship.debug.event.persistence

import android.content.Context
import androidx.annotation.RestrictTo
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Event database.
 * @hide
 */
@Database(entities = [EventEntity::class], version = 1, exportSchema = false)
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
abstract class EventDatabase : RoomDatabase() {

    abstract fun eventDao(): EventDao

    companion object {

        fun create(context: Context) =
                Room.databaseBuilder(context.applicationContext,
                        EventDatabase::class.java, "com.urbanairship.debug.event.db")
                        .fallbackToDestructiveMigration()
                        .build()
    }
}
