/* Copyright Airship and Contributors */

package com.urbanairship.chat.data

import android.content.Context
import androidx.annotation.RestrictTo
import androidx.core.content.ContextCompat
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.urbanairship.config.AirshipRuntimeConfig
import java.io.File

/**
 * @hide
 */
@Database(entities = [MessageEntity::class], version = 2)
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@TypeConverters(Converters::class)
internal abstract class ChatDatabase : RoomDatabase() {

    abstract fun chatDao(): ChatDao

    fun exists(context: Context): Boolean {
        // null databaseName means it is an in-memory database. Lets assume the database exists when in-memory.
        return openHelper.databaseName == null || context.getDatabasePath(openHelper.databaseName).exists()
    }

    companion object {
        fun createDatabase(context: Context, config: AirshipRuntimeConfig): ChatDatabase {
            val name = config.configOptions.appKey + "_chat"
            val path = File(ContextCompat.getNoBackupFilesDir(context), name).absolutePath
            return Room.databaseBuilder(context, ChatDatabase::class.java, path)
                    .fallbackToDestructiveMigration()
                    .build()
        }
    }
}
