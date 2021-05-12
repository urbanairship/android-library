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
@Database(entities = [MessageEntity::class], version = 2, exportSchema = false)
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@TypeConverters(Converters::class)
internal abstract class ChatDatabase : RoomDatabase() {

    abstract fun chatDao(): ChatDao

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
