/* Copyright Urban Airship and Contributors */

package com.urbanairship.debug.event.persistence

import android.arch.persistence.room.Database
import android.arch.persistence.room.Room
import android.arch.persistence.room.RoomDatabase
import android.content.Context
import android.support.annotation.RestrictTo

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
                        EventDatabase::class.java, "event.db")
                        .fallbackToDestructiveMigration()
                        .build()
    }
}
