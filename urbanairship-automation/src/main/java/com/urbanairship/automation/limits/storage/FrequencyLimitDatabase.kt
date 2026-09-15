/* Copyright Airship and Contributors */

package com.urbanairship.automation.limits.storage

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import androidx.room.Database
import androidx.room.Room
import androidx.room.Room.databaseBuilder
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.util.TimeTypeConverters
import java.io.File

/**
 * @hide
 */
@Database(entities = [ConstraintEntity::class, OccurrenceEntity::class], version = 1)
@TypeConverters(TimeTypeConverters::class)
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
            ).fallbackToDestructiveMigrationOnDowngrade(true).build()
        }

        @VisibleForTesting
        internal fun createInMemoryDatabase(context: Context): FrequencyLimitDatabase =
            Room.inMemoryDatabaseBuilder(context, FrequencyLimitDatabase::class.java)
                .allowMainThreadQueries()
                .build()
    }
}
