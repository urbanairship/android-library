/* Copyright Airship and Contributors */
package com.urbanairship.automation.storage

import android.content.Context
import androidx.annotation.RestrictTo
import androidx.core.content.ContextCompat
import androidx.room.Database
import androidx.room.Room.databaseBuilder
import androidx.room.Room.inMemoryDatabaseBuilder
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.json.JsonTypeConverters
import java.io.File

/**
 * Automation database.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@Database(entities = [ScheduleEntity::class, TriggerEntity::class], version = 7)
@TypeConverters(Converters::class, JsonTypeConverters::class)
public abstract class AutomationDatabase public constructor() : RoomDatabase() {

    public abstract val scheduleDao: AutomationDao

    public companion object {

        private val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE schedules " + " ADD COLUMN campaigns TEXT")
            }
        }

        private val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE schedules " + " ADD COLUMN frequencyConstraintIds TEXT"
                )
            }
        }

        private val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE schedules " + " ADD COLUMN reportingContext TEXT"
                )
            }
        }

        private val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE schedules " + " ADD COLUMN messageType TEXT"
                )
                database.execSQL(
                    "ALTER TABLE schedules " + " ADD COLUMN bypassHoldoutGroups INTEGER NOT NULL DEFAULT 0"
                )
                database.execSQL(
                    "ALTER TABLE schedules " + " ADD COLUMN newUserEvaluationDate INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        private val MIGRATION_5_6: Migration = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE schedules " + " ADD COLUMN triggeredTime INTEGER NOT NULL DEFAULT -1"
                )
            }
        }

        private val MIGRATION_6_7: Migration = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE schedules " + " ADD COLUMN productId TEXT"
                )
            }
        }

        public fun createDatabase(
            context: Context, config: AirshipRuntimeConfig
        ): AutomationDatabase {
            val name = config.configOptions.appKey + "_in-app-automation"
            val path = File(ContextCompat.getNoBackupFilesDir(context), name).absolutePath
            return databaseBuilder(context, AutomationDatabase::class.java, path)
                .addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7
                )
                .fallbackToDestructiveMigrationOnDowngrade(true)
                .build()
        }

        public fun createInMemoryDatabase(context: Context): AutomationDatabase {
            return inMemoryDatabaseBuilder(context, AutomationDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        }
    }
}
