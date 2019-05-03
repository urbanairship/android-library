/* Copyright Airship and Contributors */

package com.urbanairship.debug.push.persistence

import android.arch.persistence.room.Database
import android.arch.persistence.room.Room
import android.arch.persistence.room.RoomDatabase
import android.content.Context
import android.support.annotation.RestrictTo
import com.urbanairship.debug.push.persistence.PushEntity

/**
 * PushItem database.
 * @hide
 */
@Database(entities = [PushEntity::class], version = 2, exportSchema = false)
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
