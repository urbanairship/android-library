/* Copyright Airship and Contributors */

package com.urbanairship.debug.push.persistence

import android.content.Context
import androidx.annotation.RestrictTo
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * PushItem database.
 * @hide
 */
@Database(entities = [PushEntity::class], version = 2)
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
abstract class PushDatabase : RoomDatabase() {

    abstract fun pushDao(): PushDao

    companion object {

        fun create(context: Context) =
                Room.databaseBuilder(context.applicationContext,
                        PushDatabase::class.java, "com.urbanairship.debug.push.db")
                        .fallbackToDestructiveMigration()
                        .build()
    }
}
