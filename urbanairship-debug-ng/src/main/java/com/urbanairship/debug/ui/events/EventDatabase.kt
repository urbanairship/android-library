/* Copyright Airship and Contributors */

package com.urbanairship.debug.ui.events

import android.content.Context
import androidx.annotation.RestrictTo
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Event database.
 * @hide
 */
@Database(entities = [EventEntity::class], version = 1)
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal abstract class EventDatabase : RoomDatabase() {

    abstract fun eventDao(): EventDao

    companion object {

        fun create(context: Context) =
            Room.databaseBuilder(context.applicationContext,
                EventDatabase::class.java, "com.urbanairship.debug.events.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
