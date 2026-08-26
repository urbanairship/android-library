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
import com.urbanairship.json.JsonTypeConverters
import java.io.File

/**
 * @hide
 */
@Database(entities = [LedgerEventEntity::class], version = 1)
@TypeConverters(JsonTypeConverters::class)
internal abstract class LedgerDatabase : RoomDatabase() {
    abstract val dao: LedgerDao

    companion object {
        fun createDatabase(context: Context, config: AirshipRuntimeConfig): LedgerDatabase {
            val name = config.configOptions.appKey + "_ledger"
            val path = File(ContextCompat.getNoBackupFilesDir(context), name).absolutePath
            return databaseBuilder(
                context,
                LedgerDatabase::class.java,
                path
            ).fallbackToDestructiveMigrationOnDowngrade(true).build()
        }

        @VisibleForTesting
        internal fun createInMemoryDatabase(context: Context): LedgerDatabase =
            Room.inMemoryDatabaseBuilder(context, LedgerDatabase::class.java)
                .allowMainThreadQueries()
                .build()
    }
}
