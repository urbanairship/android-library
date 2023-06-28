/* Copyright Airship and Contributors */

package com.urbanairship.liveupdate.data

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.urbanairship.config.AirshipRuntimeConfig
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asExecutor

@Database(
    version = 1,
    entities = [LiveUpdateState::class, LiveUpdateContent::class]
)
@TypeConverters(Converters::class)
internal abstract class LiveUpdateDatabase : RoomDatabase() {

    abstract fun liveUpdateDao(): LiveUpdateDao

    companion object {
        fun createDatabase(context: Context, config: AirshipRuntimeConfig): LiveUpdateDatabase {
            val name = config.configOptions.appKey + "_live_updates"
            val path = File(ContextCompat.getNoBackupFilesDir(context), name).absolutePath
            return Room.databaseBuilder(context, LiveUpdateDatabase::class.java, path)
                    .fallbackToDestructiveMigration()
                    .build()
        }

        @VisibleForTesting
        internal fun createInMemoryDatabase(context: Context, dispatcher: CoroutineDispatcher): LiveUpdateDatabase =
            Room.inMemoryDatabaseBuilder(context, LiveUpdateDatabase::class.java)
                .allowMainThreadQueries()
                .setTransactionExecutor(dispatcher.asExecutor())
                .setQueryExecutor(dispatcher.asExecutor())
                .build()
    }
}
