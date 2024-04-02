/* Copyright Airship and Contributors */
package com.urbanairship.automation.rewrite.limits.storage

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import androidx.room.Database
import androidx.room.Room
import androidx.room.Room.databaseBuilder
import androidx.room.RoomDatabase
import com.urbanairship.config.AirshipRuntimeConfig
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asExecutor

/**
 * @hide
 */
@Database(entities = [ConstraintEntity::class, OccurrenceEntity::class], version = 1)
internal abstract class FrequencyLimitDatabase : RoomDatabase() {
    abstract val dao: FrequencyLimitDao

    companion object {
        fun createDatabase(context: Context, config: AirshipRuntimeConfig): FrequencyLimitDatabase {
            val name = config.configOptions.appKey + "_frequency_limits"
            val path = File(ContextCompat.getNoBackupFilesDir(context), name).absolutePath
            return databaseBuilder(
                context,
                FrequencyLimitDatabase::class.java,
                path
            ).fallbackToDestructiveMigrationOnDowngrade().build()
        }

        @VisibleForTesting
        internal fun createInMemoryDatabase(context: Context): FrequencyLimitDatabase =
            Room.inMemoryDatabaseBuilder(context, FrequencyLimitDatabase::class.java)
                .allowMainThreadQueries()
                .build()
    }
}
