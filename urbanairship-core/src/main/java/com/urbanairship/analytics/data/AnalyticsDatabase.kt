package com.urbanairship.analytics.data

import android.content.Context
import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import androidx.room.Database
import androidx.room.Room.databaseBuilder
import androidx.room.Room.inMemoryDatabaseBuilder
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.urbanairship.UALog
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.db.RetryingSQLiteOpenHelper
import com.urbanairship.json.JsonTypeConverters
import java.io.File

/**
 * Analytics database.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@Database(entities = [EventEntity::class], version = 4)
@TypeConverters(JsonTypeConverters::class)
internal abstract class AnalyticsDatabase : RoomDatabase() {

    abstract val eventDao: EventDao

    fun exists(context: Context): Boolean {
        val helper = openHelper
        // null databaseName means it is an in-memory database. Lets assume the database exists when in-memory.
        return helper.databaseName == null || context.getDatabasePath(helper.databaseName).exists()
    }

    companion object {

        private const val DATABASE_DIR = "com.urbanairship.databases"
        private const val DATABASE_NAME = "ua_analytics.db"

        private val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            private val OLD_TABLE_NAME = "events"
            private val OLD_ID = "_id"
            private val OLD_TYPE = "type"
            private val OLD_EVENT_ID = "event_id"
            private val OLD_TIME = "time"
            private val OLD_DATA = "data"
            private val OLD_SESSION_ID = "session_id"
            private val OLD_EVENT_SIZE = "event_size"

            private val NEW_TABLE_NAME = "events_new"
            private val NEW_ID = "id"
            private val NEW_TYPE = "type"
            private val NEW_EVENT_ID = "eventId"
            private val NEW_TIME = "time"
            private val NEW_DATA = "data"
            private val NEW_SESSION_ID = "sessionId"
            private val NEW_EVENT_SIZE = "eventSize"

            override fun migrate(db: SupportSQLiteDatabase) {
                // Create new table
                db.execSQL(
                    ("CREATE TABLE $NEW_TABLE_NAME ($NEW_ID INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, $NEW_TYPE TEXT, $NEW_EVENT_ID TEXT, $NEW_TIME TEXT, $NEW_DATA TEXT, $NEW_SESSION_ID TEXT, $NEW_EVENT_SIZE INTEGER NOT NULL);")
                )

                // Copy data from existing table
                db.execSQL(
                    ("INSERT INTO $NEW_TABLE_NAME ($NEW_ID, $NEW_TYPE, $NEW_EVENT_ID, $NEW_TIME, $NEW_DATA, $NEW_SESSION_ID, $NEW_EVENT_SIZE) SELECT $OLD_ID, $OLD_TYPE, $OLD_EVENT_ID, $OLD_TIME, $OLD_DATA, $OLD_SESSION_ID, $OLD_EVENT_SIZE FROM $OLD_TABLE_NAME")
                )

                // Drop existing table
                db.execSQL("DROP TABLE $OLD_TABLE_NAME")

                // Rename the new table to match the old
                db.execSQL("ALTER TABLE $NEW_TABLE_NAME RENAME TO $OLD_TABLE_NAME")
            }
        }

        internal val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            private val TABLE_NAME = "events"

            private val ID = "id"
            private val EVENT_ID = "eventId"
            private val INDEX_EVENT_ID = "index_events_eventId"

            override fun migrate(db: SupportSQLiteDatabase) {
                // Clean up duplicate events, if any are present.
                db.execSQL("DELETE FROM $TABLE_NAME WHERE $ID NOT IN (SELECT MIN($ID) FROM $TABLE_NAME GROUP BY $EVENT_ID)")

                // Add unique index on eventId.
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `$INDEX_EVENT_ID` ON `$TABLE_NAME` (`$EVENT_ID`)")
            }
        }

        private val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            private val NEW_TABLE_NAME = "events_new"
            private val OLD_TABLE_NAME = "events"

            override fun migrate(db: SupportSQLiteDatabase) {
                // Create new table with updated schema
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `${NEW_TABLE_NAME}` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `type` TEXT NOT NULL,
                        `eventId` TEXT NOT NULL,
                        `time` TEXT NOT NULL,
                        `data` TEXT NOT NULL,
                        `sessionId` TEXT,
                        `eventSize` INTEGER NOT NULL
                    )
                """.trimIndent())

                // Copy data from existing table
                db.execSQL(
                    """
                    INSERT INTO $NEW_TABLE_NAME (`id`, `type`, `eventId`, `time`, `data`, `sessionId`, `eventSize`)
                    SELECT `id`, `type`, `eventId`, `time`, `data`, `sessionId`, `eventSize` FROM $OLD_TABLE_NAME"
                    WHERE `type` IS NOT NULL AND `eventId` IS NOT NULL AND `time` IS NOT NULL AND `data` IS NOT NULL AND `eventSize` IS NOT NULL
                    """.trimIndent()
                )

                // Drop existing table
                db.execSQL("DROP TABLE $OLD_TABLE_NAME")

                // Rename the new table to match the old
                db.execSQL("ALTER TABLE $NEW_TABLE_NAME RENAME TO $OLD_TABLE_NAME")
            }
        }

        fun createDatabase(
            context: Context, config: AirshipRuntimeConfig
        ): AnalyticsDatabase {
            // Attempt to migrate an existing analytics db by moving it to the new location. The 1 -> 2
            // migration will handle updating the events schema and records when it runs.
            val path = migrateExistingDbIfExists(context, config)
            val retryingOpenHelperFactory =
                RetryingSQLiteOpenHelper.Factory(FrameworkSQLiteOpenHelperFactory(), true)

            return databaseBuilder(context, AnalyticsDatabase::class.java, path)
                .openHelperFactory(retryingOpenHelperFactory)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .fallbackToDestructiveMigration(true)
                .build()
        }

        private fun migrateExistingDbIfExists(
            context: Context, config: AirshipRuntimeConfig
        ): String {
            val oldDbDir = File(ContextCompat.getNoBackupFilesDir(context), DATABASE_DIR)
            val oldName = config.configOptions.appKey + "_" + DATABASE_NAME
            val oldDb = File(oldDbDir, oldName)

            val newName = config.configOptions.appKey + "_analytics"
            val newDb = File(ContextCompat.getNoBackupFilesDir(context), newName)

            if (oldDb.exists() && !newDb.exists()) {
                if (!oldDb.renameTo(newDb)) {
                    UALog.w("Failed to move analytics db: ${oldDb.path} -> ${newDb.path}")
                }
            }

            return newDb.absolutePath
        }

        @VisibleForTesting
        fun createInMemoryDatabase(context: Context): AnalyticsDatabase {
            return inMemoryDatabaseBuilder(context, AnalyticsDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        }
    }
}
