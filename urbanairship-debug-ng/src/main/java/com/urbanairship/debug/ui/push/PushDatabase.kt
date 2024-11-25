package com.urbanairship.debug.ui.push

import android.content.Context
import androidx.annotation.RestrictTo
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * PushItem database.
 * @hide
 */
@Database(entities = [PushEntity::class], version = 1)
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal abstract class PushDatabase : RoomDatabase() {

    abstract fun pushDao(): PushDao

    companion object {

        fun create(context: Context) =
            Room.databaseBuilder(context.applicationContext,
                PushDatabase::class.java, "com.urbanairship.debug.push.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
